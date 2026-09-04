package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadObject
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.TravelDirection

class AlertEngine(
    graph: RoadGraph,
    private var settings: AlertSettings = AlertSettings.ALL_ON,
    private val maxQueue: Int = 2,
) {
    private val matcher = MapMatcher(graph)
    private val horizon = HorizonScanner(graph)
    private var graph = graph
    private val fired = LinkedHashSet<String>()
    private val priorityStay = PriorityRoadStayTracker()
    private var lastSpeedKmh: Int? = null
    private var lastKommune: Int? = null
    private var lastInsideWildlife = HashSet<Long>()
    private var lastInsideSectionAtk = HashSet<Long>()
    private var lastInsidePriority = HashSet<Long>()
    private var lastHorizon: List<HorizonCandidate> = emptyList()

    fun updateSettings(next: AlertSettings) {
        settings = next
    }

    /**
     * Points the engine at a reloaded road graph without clearing alert state, so a window shift
     * or a newly downloaded kommune does not re-fire signs already passed.
     */
    fun updateGraph(next: RoadGraph) {
        graph = next
        matcher.updateGraph(next)
        horizon.updateGraph(next)
    }

    fun reset() {
        matcher.reset()
        fired.clear()
        priorityStay.reset()
        lastSpeedKmh = null
        lastKommune = null
        lastInsideWildlife.clear()
        lastInsideSectionAtk.clear()
        lastInsidePriority.clear()
        lastHorizon = emptyList()
    }

    fun currentMatch(): Match? = matcher.current()

    fun currentHorizon(): List<HorizonCandidate> = lastHorizon

    fun update(fix: GpsFix): List<Alert> {
        val match = matcher.update(fix)
        if (match == null) {
            lastHorizon = emptyList()
            return emptyList()
        }
        refreshHorizon(match, fix.speedMetersPerSecond)
        val speed = fix.speedMetersPerSecond
        val driving = speed >= AlertWindows.MIN_DRIVING_SPEED_METERS_PER_SECOND
        val alerting = driving && !settings.alertsMuted
        refreshPriorityStay(match, speed, fix.timeMs)
        val alerts = ArrayList<Alert>()

        collectSpeedLimit(match, alerting, speed)?.let { alerts.add(it) }
        collectKommune(match, alerting)?.let { alerts.add(it) }
        collectIntervalEntries(
            match,
            lastInsideWildlife,
            RoadObjectType.WILDLIFE,
            AlertKind.WILDLIFE,
            alerting,
        )?.let { alerts.add(it) }
        collectIntervalEntries(
            match,
            lastInsideSectionAtk,
            RoadObjectType.SECTION_ATK,
            AlertKind.SECTION_ATK_START,
            alerting,
        )?.let { alerts.add(it) }
        collectSectionAtkExit(match, alerting)?.let { alerts.add(it) }

        if (!driving || settings.alertsMuted) {
            updatePriorityMembership(match)
            pruneFired()
            return alerts
        }

        val prioritySignReady = lastHorizon.any { candidate ->
            candidate.obj.type == RoadObjectType.PRIORITY_ROAD &&
                candidate.obj.isPoint &&
                !AlertCopy.isPriorityEnd(candidate.obj.payload) &&
                shouldFire(AlertKind.PRIORITY_ROAD, candidate.metersAhead, speed)
        }
        if (!prioritySignReady) {
            collectPriorityEnter(match, alerting)?.let { alerts.add(it) }
        } else {
            updatePriorityMembership(match)
        }

        for (candidate in lastHorizon) {
            val kind = candidate.obj.type.toAlertKind() ?: continue
            if (kind == AlertKind.WILDLIFE || kind == AlertKind.SECTION_ATK_START) continue
            if (!settings.enabled(kind, candidate.obj.payload)) continue
            if (!shouldFire(kind, candidate.metersAhead, speed)) continue
            if (kind == AlertKind.PRIORITY_ROAD &&
                !AlertCopy.isPriorityEnd(candidate.obj.payload) &&
                !priorityStay.allowAlert()
            ) {
                continue
            }
            val key = fireKey(kind, candidate.obj.nvdbId)
            if (!fired.add(key)) continue
            if (kind == AlertKind.PRIORITY_ROAD && !AlertCopy.isPriorityEnd(candidate.obj.payload)) {
                priorityStay.markAlerted()
            }
            alerts.add(
                Alert(
                    kind = kind,
                    nvdbId = candidate.obj.nvdbId,
                    metersAhead = candidate.metersAhead,
                    title = AlertCopy.titleFor(kind, candidate.obj.payload),
                    body = AlertCopy.bodyFor(kind, candidate.metersAhead, candidate.obj.payload),
                    sequenceId = candidate.obj.sequenceId,
                    objectType = candidate.obj.type,
                    payload = candidate.obj.payload,
                ),
            )
        }

        pruneFired()
        return alerts.sortedByDescending { it.kind.priority }.take(maxQueue)
    }

    private fun refreshHorizon(match: Match, speedMetersPerSecond: Double) {
        lastHorizon = horizon.scan(match, speedMetersPerSecond)
            .filter { candidate ->
                val kind = candidate.obj.type.toAlertKind() ?: return@filter false
                settings.enabled(kind, candidate.obj.payload)
            }
            .distinctBy { it.obj.nvdbId }
    }

    private fun refreshPriorityStay(match: Match, speed: Double, nowMs: Long) {
        val onPriorityRoad = priorityStretchesOn(match).any { obj ->
            insideInterval(match.position, obj)
        }
        val signInWindow = lastHorizon.any { candidate ->
            candidate.obj.type == RoadObjectType.PRIORITY_ROAD &&
                candidate.obj.isPoint &&
                !AlertCopy.isPriorityEnd(candidate.obj.payload) &&
                shouldFire(AlertKind.PRIORITY_ROAD, candidate.metersAhead, speed)
        }
        val endSignInWindow = lastHorizon.any { candidate ->
            candidate.obj.type == RoadObjectType.PRIORITY_ROAD &&
                AlertCopy.isPriorityEnd(candidate.obj.payload) &&
                shouldFire(AlertKind.PRIORITY_ROAD, candidate.metersAhead, speed)
        }
        priorityStay.onTick(onPriorityRoad, signInWindow, endSignInWindow, nowMs)
    }

    private fun shouldFire(kind: AlertKind, metersAhead: Double, speed: Double): Boolean {
        if (kind == AlertKind.SPEED_CAMERA) {
            return metersAhead in 50.0..400.0
        }
        return AlertWindows.inWindow(kind, metersAhead, speed)
    }

    private fun collectKommune(match: Match, driving: Boolean): Alert? {
        if (!settings.enabled(AlertKind.MUNICIPALITY)) return null
        val kommune = graph.links[match.linkId]?.kommune ?: 0
        if (kommune == 0) return null
        val previous = lastKommune
        lastKommune = kommune
        if (!driving || previous == null || previous == kommune) return null
        val key = "MUNICIPALITY:$kommune"
        if (!fired.add(key)) return null
        val name = graph.kommunePolygons.firstOrNull { it.kommune == kommune }?.name ?: kommune.toString()
        return Alert(
            kind = AlertKind.MUNICIPALITY,
            nvdbId = kommune.toLong(),
            metersAhead = 0.0,
            title = name,
            body = "Kommunegrense",
            sequenceId = match.sequenceId,
            objectType = RoadObjectType.MUNICIPALITY,
            payload = name,
        )
    }

    private fun collectSpeedLimit(match: Match, driving: Boolean, speed: Double): Alert? {
        val currentKmh = graph.speedAt(match.sequenceId, match.position, match.direction)
        val previousKmh = lastSpeedKmh
        lastSpeedKmh = currentKmh
        if (!driving) return null
        val lookMeters = AlertWindows.window(AlertKind.SPEED_LIMIT).maxMeters + 50.0
        val upcoming = graph.upcomingSpeedChange(
            match.sequenceId,
            match.position,
            match.direction,
            lookMeters,
        )
        if (upcoming != null &&
            upcoming.kmh != currentKmh &&
            shouldFire(AlertKind.SPEED_LIMIT, upcoming.metersAhead, speed)
        ) {
            return speedAlert(match, upcoming.kmh, upcoming.metersAhead, upcoming.atPos)
        }
        if (previousKmh != null && currentKmh != null && previousKmh != currentKmh) {
            return speedAlert(match, currentKmh, 0.0, match.position)
        }
        return null
    }

    private fun speedAlert(match: Match, kmh: Int, metersAhead: Double, atPos: Double): Alert? {
        if (!settings.enabled(AlertKind.SPEED_LIMIT, kmh.toString())) return null
        val key = "SPEED_LIMIT:$kmh:${match.sequenceId}:${(atPos * 1000).toInt()}"
        if (!fired.add(key)) return null
        return Alert(
            kind = AlertKind.SPEED_LIMIT,
            nvdbId = kmh.toLong(),
            metersAhead = metersAhead,
            title = "Fartsgrense $kmh",
            body = AlertCopy.bodyFor(AlertKind.SPEED_LIMIT, metersAhead, kmh.toString()).ifBlank {
                "$kmh km/t"
            },
            sequenceId = match.sequenceId,
            objectType = null,
            payload = kmh.toString(),
        )
    }

    private fun collectPriorityEnter(match: Match, driving: Boolean): Alert? {
        val inside = HashSet<Long>()
        var entered: Alert? = null
        for (obj in priorityStretchesOn(match)) {
            if (!insideInterval(match.position, obj)) continue
            inside.add(obj.nvdbId)
            if (!driving || !settings.enabled(AlertKind.PRIORITY_ROAD, obj.payload)) continue
            if (!priorityStay.allowAlert()) continue
            if (obj.nvdbId in lastInsidePriority) continue
            val key = fireKey(AlertKind.PRIORITY_ROAD, obj.nvdbId)
            if (!fired.add(key)) continue
            priorityStay.markAlerted()
            entered = Alert(
                kind = AlertKind.PRIORITY_ROAD,
                nvdbId = obj.nvdbId,
                metersAhead = 0.0,
                title = AlertCopy.titleFor(AlertKind.PRIORITY_ROAD, obj.payload),
                body = AlertCopy.bodyFor(AlertKind.PRIORITY_ROAD, 0.0, obj.payload),
                sequenceId = obj.sequenceId,
                objectType = RoadObjectType.PRIORITY_ROAD,
                payload = obj.payload,
            )
        }
        lastInsidePriority.clear()
        lastInsidePriority.addAll(inside)
        return entered
    }

    private fun updatePriorityMembership(match: Match) {
        val inside = HashSet<Long>()
        for (obj in priorityStretchesOn(match)) {
            if (insideInterval(match.position, obj)) {
                inside.add(obj.nvdbId)
            }
        }
        lastInsidePriority.clear()
        lastInsidePriority.addAll(inside)
    }

    private fun priorityStretchesOn(match: Match): List<RoadObject> {
        return graph.objectsOn(match.sequenceId).filter { obj ->
            obj.type == RoadObjectType.PRIORITY_ROAD &&
                !obj.isPoint &&
                !AlertCopy.isPriorityEnd(obj.payload) &&
                obj.direction.matches(match.direction)
        }
    }

    private fun insideInterval(position: Double, obj: RoadObject): Boolean {
        val lo = minOf(obj.fromPos, obj.toPos)
        val hi = maxOf(obj.fromPos, obj.toPos)
        return position + 1e-9 >= lo && position - 1e-9 <= hi
    }

    private fun collectIntervalEntries(
        match: Match,
        previousIds: HashSet<Long>,
        type: RoadObjectType,
        kind: AlertKind,
        driving: Boolean,
    ): Alert? {
        val inside = HashSet<Long>()
        var entered: Alert? = null
        for (obj in graph.objectsOn(match.sequenceId)) {
            if (obj.type != type) continue
            if (!obj.direction.matches(match.direction)) continue
            val lo = minOf(obj.fromPos, obj.toPos)
            val hi = maxOf(obj.fromPos, obj.toPos)
            if (match.position + 1e-9 < lo || match.position - 1e-9 > hi) continue
            inside.add(obj.nvdbId)
            if (!settings.enabled(kind, obj.payload)) continue
            if (driving && obj.nvdbId !in previousIds) {
                val key = fireKey(kind, obj.nvdbId)
                if (fired.add(key) && entered == null) {
                    entered = Alert(
                        kind = kind,
                        nvdbId = obj.nvdbId,
                        metersAhead = 0.0,
                        title = AlertCopy.titleFor(kind, obj.payload),
                        body = AlertCopy.bodyFor(kind, 0.0, obj.payload),
                        sequenceId = obj.sequenceId,
                        objectType = type,
                        payload = obj.payload,
                    )
                }
            }
        }
        previousIds.clear()
        previousIds.addAll(inside)
        return entered
    }

    private fun collectSectionAtkExit(match: Match, driving: Boolean): Alert? {
        if (!driving || !settings.enabled(AlertKind.SECTION_ATK_END)) return null
        val sequence = graph.sequences[match.sequenceId] ?: return null
        for (obj in graph.objectsOn(match.sequenceId)) {
            if (obj.type != RoadObjectType.SECTION_ATK) continue
            if (!obj.direction.matches(match.direction)) continue
            val exitPos = if (match.direction == TravelDirection.MED) {
                maxOf(obj.fromPos, obj.toPos)
            } else {
                minOf(obj.fromPos, obj.toPos)
            }
            val meters = kotlin.math.abs(exitPos - match.position) * sequence.lengthMeters
            if (meters > 80.0 || meters < 1.0) continue
            val key = fireKey(AlertKind.SECTION_ATK_END, obj.nvdbId)
            if (!fired.add(key)) continue
            return Alert(
                kind = AlertKind.SECTION_ATK_END,
                nvdbId = obj.nvdbId,
                metersAhead = meters,
                title = AlertCopy.titleFor(AlertKind.SECTION_ATK_END, obj.payload),
                body = AlertCopy.bodyFor(AlertKind.SECTION_ATK_END, meters, obj.payload),
                sequenceId = obj.sequenceId,
                objectType = RoadObjectType.SECTION_ATK,
                payload = obj.payload,
            )
        }
        return null
    }

    private fun pruneFired() {
        if (fired.size < 40) return
        val iterator = fired.iterator()
        repeat(10) {
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun fireKey(kind: AlertKind, nvdbId: Long) = "$kind:$nvdbId"
}
