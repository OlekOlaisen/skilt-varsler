package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.Geo
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
    private var lastSpeedSequenceId: Long? = null
    private var lastSpeedDirection: TravelDirection? = null
    private var lastKommune: Int? = null
    private var lastInsideWildlife = HashSet<Long>()
    private var lastInsideSectionAtk = HashSet<Long>()
    private var lastInsidePriority = HashSet<Long>()
    private var lastMatchSequenceId: Long? = null
    private var lastMatchDirection: TravelDirection? = null
    private var lastHorizon: List<HorizonCandidate> = emptyList()
    private var situations: List<TrafficSituation> = emptyList()

    fun updateSettings(next: AlertSettings) {
        settings = next
    }

    fun updateSituations(next: List<TrafficSituation>) {
        situations = next
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
        lastSpeedSequenceId = null
        lastSpeedDirection = null
        lastKommune = null
        lastInsideWildlife.clear()
        lastInsideSectionAtk.clear()
        lastInsidePriority.clear()
        lastMatchSequenceId = null
        lastMatchDirection = null
        lastHorizon = emptyList()
    }

    fun currentMatch(): Match? = matcher.current()

    fun isHoldingMatch(): Boolean = matcher.isHolding()

    fun currentHorizon(): List<HorizonCandidate> = lastHorizon

    fun update(fix: GpsFix): List<Alert> {
        val match = matcher.update(fix)
        val speed = fix.speedMetersPerSecond
        val driving = speed >= AlertWindows.MIN_DRIVING_SPEED_METERS_PER_SECOND
        /**
         * Mute only suppresses notifications. Alerts are still produced so trip statistics
         * and the upcoming-sign list stay accurate during a silent drive.
         */
        val alerting = driving
        if (match == null) {
            lastHorizon = emptyList()
            val alerts = ArrayList<Alert>()
            collectRoadworks(fix, alerting, speed)?.let { alerts.add(it) }
            collectAccidents(fix, alerting, speed)?.let { alerts.add(it) }
            pruneFired()
            return alerts.sortedByDescending { it.kind.priority }.take(maxQueue)
        }
        refreshHorizon(match, fix.speedMetersPerSecond)
        refreshPriorityStay(match, speed, fix.timeMs)
        val alerts = ArrayList<Alert>()

        collectSpeedLimit(match, alerting)?.let { alerts.add(it) }
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
        collectRoadworks(fix, alerting, speed)?.let { alerts.add(it) }
        collectAccidents(fix, alerting, speed)?.let { alerts.add(it) }

        if (!driving) {
            updatePriorityMembership(match)
            rememberMatch(match)
            pruneFired()
            return alerts
        }

        // Forkjørsveg: alert on stretch enter / at the plate — never as a long-range ahead warning.
        // A clear turn onto another priority arm allows a fresh alert (example 2); straight
        // sequence splits along the same road do not (example 1).
        maybeAllowPriorityAlertAfterTurn(match)
        collectPriorityEnter(match, alerting)?.let { alerts.add(it) }
        collectPriorityAtPlate(match, alerting)?.let { alerts.add(it) }

        for (candidate in lastHorizon) {
            val kind = candidate.obj.type.toAlertKind() ?: continue
            if (kind == AlertKind.WILDLIFE || kind == AlertKind.SECTION_ATK_START) continue
            // Entrance/reminder 206 plates are handled by collectPriority*; only 208 ends stay on horizon.
            if (kind == AlertKind.PRIORITY_ROAD && !AlertCopy.isPriorityEnd(candidate.obj.payload)) {
                continue
            }
            if (!settings.enabled(kind, candidate.obj.payload)) continue
            val metersAhead = correctedMetersAhead(match, fix, candidate.metersAhead)
            if (!shouldFire(kind, metersAhead, speed)) continue
            val key = fireKey(kind, candidate.obj.nvdbId)
            if (!fired.add(key)) continue
            alerts.add(
                Alert(
                    kind = kind,
                    nvdbId = candidate.obj.nvdbId,
                    metersAhead = metersAhead,
                    title = AlertCopy.titleFor(kind, candidate.obj.payload),
                    body = AlertCopy.bodyFor(kind, metersAhead, candidate.obj.payload),
                    sequenceId = candidate.obj.sequenceId,
                    objectType = candidate.obj.type,
                    payload = candidate.obj.payload,
                ),
            )
        }

        rememberMatch(match)
        pruneFired()
        return alerts.sortedByDescending { it.kind.priority }.take(maxQueue)
    }

    private fun collectRoadworks(fix: GpsFix, alerting: Boolean, speed: Double): Alert? {
        return collectLiveSituation(
            fix = fix,
            alerting = alerting,
            speed = speed,
            kind = AlertKind.ROADWORK,
            types = setOf(SituationType.ROADWORK, SituationType.CLOSURE),
        )
    }

    private fun collectAccidents(fix: GpsFix, alerting: Boolean, speed: Double): Alert? {
        return collectLiveSituation(
            fix = fix,
            alerting = alerting,
            speed = speed,
            kind = AlertKind.ACCIDENT,
            types = setOf(SituationType.ACCIDENT),
        )
    }

    private fun collectLiveSituation(
        fix: GpsFix,
        alerting: Boolean,
        speed: Double,
        kind: AlertKind,
        types: Set<SituationType>,
    ): Alert? {
        if (!alerting || !settings.enabled(kind)) {
            return null
        }
        val relevant = situations.filter { situation -> situation.type in types }
        if (relevant.isEmpty()) {
            return null
        }
        val hit = SituationIndex.nearestAhead(
            position = fix.position,
            bearingDegrees = fix.bearingDegrees,
            situations = relevant,
            maxMeters = AlertWindows.window(kind).maxMeters + 80.0,
        ) ?: return null
        if (!shouldFire(kind, hit.metersAway, speed)) {
            return null
        }
        val key = fireKey(kind, hit.situation.alertId)
        if (!fired.add(key)) {
            return null
        }
        val payload = hit.situation.payload
        return Alert(
            kind = kind,
            nvdbId = hit.situation.alertId,
            metersAhead = hit.metersAway,
            title = AlertCopy.titleFor(kind, payload),
            body = AlertCopy.bodyFor(kind, hit.metersAway, payload),
            sequenceId = 0L,
            objectType = null,
            payload = payload,
        )
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
        val nearEntrancePlate = isNearPriorityEntrancePlate(match)
        val endSignInWindow = lastHorizon.any { candidate ->
            candidate.obj.type == RoadObjectType.PRIORITY_ROAD &&
                AlertCopy.isPriorityEnd(candidate.obj.payload) &&
                shouldFire(AlertKind.PRIORITY_ROAD, candidate.metersAhead, speed)
        }
        priorityStay.onTick(onPriorityRoad, nearEntrancePlate, endSignInWindow, nowMs)
    }

    private fun isNearPriorityEntrancePlate(match: Match): Boolean {
        val sequence = graph.sequences[match.sequenceId] ?: return false
        if (sequence.lengthMeters <= 0.0) {
            return false
        }
        return graph.objectsOn(match.sequenceId).any { obj ->
            if (obj.type != RoadObjectType.PRIORITY_ROAD || !obj.isPoint) {
                return@any false
            }
            if (AlertCopy.isPriorityEnd(obj.payload) || !obj.direction.matches(match.direction)) {
                return@any false
            }
            val signedMeters = if (match.direction == TravelDirection.MED) {
                (obj.fromPos - match.position) * sequence.lengthMeters
            } else {
                (match.position - obj.fromPos) * sequence.lengthMeters
            }
            signedMeters <= PRIORITY_AT_PLATE_METERS && signedMeters >= -5.0
        }
    }

    private fun shouldFire(kind: AlertKind, metersAhead: Double, speed: Double): Boolean {
        if (kind == AlertKind.SPEED_CAMERA) {
            return metersAhead in 50.0..400.0
        }
        return AlertWindows.inWindow(kind, metersAhead, speed)
    }

    /**
     * When the matcher is holding (bridge/tunnel multipath), GPS often advances past the
     * snapped match. Horizon distance is measured from the lagging snap — shorten it so
     * we do not announce "Om 120 m" when the car is already at the plate.
     */
    private fun correctedMetersAhead(match: Match, fix: GpsFix, metersAhead: Double): Double {
        if (!matcher.isHolding()) {
            return metersAhead
        }
        val travel = travelBearing(match.sequenceId, match.position, match.direction) ?: return metersAhead
        val toGpsBearing = Geo.bearingDegrees(match.snapped, fix.position)
        if (Geo.headingDeltaDegrees(travel, toGpsBearing) > 70.0) {
            return metersAhead
        }
        val lagMeters = Geo.distanceMeters(match.snapped, fix.position)
        if (lagMeters < 15.0) {
            return metersAhead
        }
        return (metersAhead - lagMeters).coerceAtLeast(0.0)
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

    /**
     * Speed limits alert only when the matched limit changes (enter), or when turning onto
     * another road that still has the same number. Never foreshadow an upcoming zone.
     *
     * Links without NVDB speed (typical roundabout connectors) must not clear [lastSpeedKmh]:
     * otherwise exiting onto a signed road with the same number never re-alerts.
     */
    private fun collectSpeedLimit(match: Match, driving: Boolean): Alert? {
        val currentKmh = graph.speedAt(match.sequenceId, match.position, match.direction)
        if (currentKmh == null) {
            return null
        }
        val previousKmh = lastSpeedKmh
        val previousSequenceId = lastSpeedSequenceId
        val previousDirection = lastSpeedDirection
        lastSpeedKmh = currentKmh
        lastSpeedSequenceId = match.sequenceId
        lastSpeedDirection = match.direction
        if (!driving) return null
        if (previousKmh != null && previousKmh != currentKmh) {
            return speedAlert(match, currentKmh, 0.0, match.position)
        }
        // Turning onto a different road: confirm the limit even when the number is unchanged.
        if (previousSequenceId != null &&
            previousDirection != null &&
            isTurnOntoNewRoad(previousSequenceId, previousDirection, match)
        ) {
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

    /**
     * Point 206 plates alert only when you are at/just before the plate (entering),
     * not tens of metres ahead. Uses match geometry directly — horizon skips sub-metre hits.
     */
    private fun collectPriorityAtPlate(match: Match, driving: Boolean): Alert? {
        if (!driving || !priorityStay.allowAlert()) {
            return null
        }
        val sequence = graph.sequences[match.sequenceId] ?: return null
        if (sequence.lengthMeters <= 0.0) {
            return null
        }
        for (obj in graph.objectsOn(match.sequenceId)) {
            if (obj.type != RoadObjectType.PRIORITY_ROAD || !obj.isPoint) {
                continue
            }
            if (AlertCopy.isPriorityEnd(obj.payload)) {
                continue
            }
            if (!obj.direction.matches(match.direction)) {
                continue
            }
            val signedMeters = if (match.direction == TravelDirection.MED) {
                (obj.fromPos - match.position) * sequence.lengthMeters
            } else {
                (match.position - obj.fromPos) * sequence.lengthMeters
            }
            // Ahead up to the enter distance, or just past the plate (crossed it).
            if (signedMeters > PRIORITY_AT_PLATE_METERS || signedMeters < -5.0) {
                continue
            }
            if (!settings.enabled(AlertKind.PRIORITY_ROAD, obj.payload)) {
                continue
            }
            val key = fireKey(AlertKind.PRIORITY_ROAD, obj.nvdbId)
            if (!fired.add(key)) {
                continue
            }
            priorityStay.markAlerted()
            val metersAhead = signedMeters.coerceAtLeast(0.0)
            return Alert(
                kind = AlertKind.PRIORITY_ROAD,
                nvdbId = obj.nvdbId,
                metersAhead = metersAhead,
                title = AlertCopy.titleFor(AlertKind.PRIORITY_ROAD, obj.payload),
                body = AlertCopy.bodyFor(AlertKind.PRIORITY_ROAD, metersAhead, obj.payload),
                sequenceId = obj.sequenceId,
                objectType = RoadObjectType.PRIORITY_ROAD,
                payload = obj.payload,
            )
        }
        return null
    }

    private fun maybeAllowPriorityAlertAfterTurn(match: Match) {
        val previousSequenceId = lastMatchSequenceId ?: return
        val previousDirection = lastMatchDirection ?: return
        if (!isTurnOntoNewRoad(previousSequenceId, previousDirection, match)) {
            return
        }
        if (!isOnPriorityContext(match)) {
            return
        }
        priorityStay.allowAlertAfterTurn()
        lastInsidePriority.clear()
    }

    private fun isOnPriorityContext(match: Match): Boolean {
        if (priorityStretchesOn(match).any { obj -> insideInterval(match.position, obj) }) {
            return true
        }
        return isNearPriorityEntrancePlate(match)
    }

    private fun rememberMatch(match: Match) {
        lastMatchSequenceId = match.sequenceId
        lastMatchDirection = match.direction
    }

    /**
     * True when the matcher moved onto a different sequence with a clear heading change
     * (side street / turn). Straight continuation through a junction is not a new road.
     */
    private fun isTurnOntoNewRoad(
        previousSequenceId: Long,
        previousDirection: TravelDirection,
        current: Match,
    ): Boolean {
        if (previousSequenceId == current.sequenceId) {
            return false
        }
        val exitBearing = travelBearing(previousSequenceId, exitPosition(previousDirection), previousDirection)
            ?: return true
        val entryBearing = travelBearing(current.sequenceId, current.position, current.direction)
            ?: return true
        return Geo.headingDeltaDegrees(exitBearing, entryBearing) >= NEW_ROAD_TURN_DEGREES
    }

    private fun exitPosition(direction: TravelDirection): Double {
        return if (direction == TravelDirection.MED) 1.0 else 0.0
    }

    private fun travelBearing(sequenceId: Long, position: Double, direction: TravelDirection): Double? {
        val sequence = graph.sequences[sequenceId] ?: return null
        val link = sequence.links.firstOrNull { candidate ->
            position >= candidate.startPos - 1e-9 && position <= candidate.endPos + 1e-9
        } ?: sequence.links.minByOrNull { candidate ->
            kotlin.math.abs(((candidate.startPos + candidate.endPos) / 2.0) - position)
        } ?: return null
        if (link.points.size < 2) {
            return null
        }
        val span = (link.endPos - link.startPos).let { if (it == 0.0) 1.0 else it }
        val fraction = ((position - link.startPos) / span).coerceIn(0.0, 1.0)
        val index = ((link.points.lastIndex - 1) * fraction).toInt()
            .coerceIn(0, link.points.lastIndex - 1)
        val start = link.points[index]
        val end = link.points[index + 1]
        val bearing = Geo.bearingDegrees(start, end)
        return if (direction == TravelDirection.MED) {
            bearing
        } else {
            (bearing + 180.0) % 360.0
        }
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

    companion object {
        /** Alert 206 plates only when this close — entering, not foreshadowing. */
        const val PRIORITY_AT_PLATE_METERS = 20.0

        /** Heading change that counts as turning onto a different road for speed re-confirm. */
        const val NEW_ROAD_TURN_DEGREES = 40.0
    }
}
