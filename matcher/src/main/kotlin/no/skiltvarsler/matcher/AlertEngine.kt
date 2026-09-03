package no.skiltvarsler.matcher

import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.TravelDirection

class AlertEngine(
    graph: RoadGraph,
    private var settings: AlertSettings = AlertSettings.ALL_ON,
    private val maxQueue: Int = 2,
) {
    private val matcher = MapMatcher(graph)
    private val horizon = HorizonScanner(graph)
    private val graph = graph
    private val fired = LinkedHashSet<String>()
    private var lastSpeedKmh: Int? = null
    private var lastKommune: Int? = null
    private var lastInsideWildlife = HashSet<Long>()
    private var lastInsideSectionAtk = HashSet<Long>()

    fun updateSettings(next: AlertSettings) {
        settings = next
    }

    fun reset() {
        matcher.reset()
        fired.clear()
        lastSpeedKmh = null
        lastKommune = null
        lastInsideWildlife.clear()
        lastInsideSectionAtk.clear()
    }

    fun currentMatch(): Match? = matcher.current()

    fun update(fix: GpsFix): List<Alert> {
        val match = matcher.update(fix) ?: return emptyList()
        val speed = fix.speedMetersPerSecond
        val driving = speed >= AlertWindows.MIN_DRIVING_SPEED_METERS_PER_SECOND
        val alerts = ArrayList<Alert>()

        collectSpeedLimit(match, driving)?.let { alerts.add(it) }
        collectKommune(match, driving)?.let { alerts.add(it) }
        collectIntervalEntries(
            match,
            lastInsideWildlife,
            RoadObjectType.WILDLIFE,
            AlertKind.WILDLIFE,
            driving,
        )?.let { alerts.add(it) }
        collectIntervalEntries(
            match,
            lastInsideSectionAtk,
            RoadObjectType.SECTION_ATK,
            AlertKind.SECTION_ATK_START,
            driving,
        )?.let { alerts.add(it) }
        collectSectionAtkExit(match, driving)?.let { alerts.add(it) }

        if (!driving) {
            pruneFired()
            return alerts
        }

        for (candidate in horizon.scan(match, speed)) {
            val kind = candidate.obj.type.toAlertKind() ?: continue
            if (kind == AlertKind.WILDLIFE || kind == AlertKind.SECTION_ATK_START) continue
            if (!settings.enabled(kind, candidate.obj.payload)) continue
            if (!shouldFire(kind, candidate.metersAhead, speed)) continue
            val key = fireKey(kind, candidate.obj.nvdbId)
            if (!fired.add(key)) continue
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
        val name = graph.kommunePolygons.firstOrNull { it.kommune == kommune }?.name ?: kommune.toString()
        val key = "MUNICIPALITY:$kommune"
        if (!fired.add(key)) return null
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

    private fun collectSpeedLimit(match: Match, driving: Boolean): Alert? {
        val kmh = graph.speedAt(match.sequenceId, match.position, match.direction) ?: return null
        val previous = lastSpeedKmh
        lastSpeedKmh = kmh
        if (!driving || previous == null || previous == kmh) return null
        if (!settings.enabled(AlertKind.SPEED_LIMIT, kmh.toString())) return null
        val key = "SPEED_LIMIT:$kmh:${match.sequenceId}:${(match.position * 100).toInt()}"
        if (!fired.add(key)) return null
        return Alert(
            kind = AlertKind.SPEED_LIMIT,
            nvdbId = kmh.toLong(),
            metersAhead = 0.0,
            title = "Fartsgrense $kmh",
            body = "$kmh km/t",
            sequenceId = match.sequenceId,
            objectType = null,
            payload = kmh.toString(),
        )
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
