package no.skiltvarsler.matcher

object AlertWindows {
    data class Window(
        val seconds: Double,
        val minMeters: Double,
        val maxMeters: Double,
    )

    fun window(kind: AlertKind): Window = when (kind) {
        AlertKind.SPEED_CAMERA -> Window(seconds = 12.0, minMeters = 200.0, maxMeters = 400.0)
        AlertKind.SECTION_ATK_START -> Window(seconds = 12.0, minMeters = 200.0, maxMeters = 400.0)
        AlertKind.SECTION_ATK_END -> Window(seconds = 8.0, minMeters = 80.0, maxMeters = 250.0)
        AlertKind.TOLL -> Window(seconds = 10.0, minMeters = 120.0, maxMeters = 300.0)
        AlertKind.WILDLIFE -> Window(seconds = 6.0, minMeters = 40.0, maxMeters = 180.0)
        AlertKind.RAILWAY -> Window(seconds = 8.0, minMeters = 60.0, maxMeters = 220.0)
        AlertKind.FERRY -> Window(seconds = 10.0, minMeters = 80.0, maxMeters = 300.0)
        AlertKind.STOP -> Window(seconds = 3.0, minMeters = 15.0, maxMeters = 60.0)
        AlertKind.YIELD -> Window(seconds = 3.0, minMeters = 15.0, maxMeters = 60.0)
        AlertKind.HAZARD -> Window(seconds = 8.0, minMeters = 80.0, maxMeters = 250.0)
        AlertKind.PRIORITY_ROAD -> Window(seconds = 5.0, minMeters = 30.0, maxMeters = 120.0)
        AlertKind.MUNICIPALITY -> Window(seconds = 4.0, minMeters = 20.0, maxMeters = 80.0)
        AlertKind.SPEED_LIMIT -> Window(seconds = 4.0, minMeters = 20.0, maxMeters = 120.0)
    }

    fun metersAhead(kind: AlertKind, speedMetersPerSecond: Double): ClosedFloatingPointRange<Double> {
        val spec = window(kind)
        val byTime = speedMetersPerSecond * spec.seconds
        val low = spec.minMeters
        val high = spec.maxMeters.coerceAtLeast(low)
        val target = byTime.coerceIn(low, high)
        val band = 40.0
        return (target - band).coerceAtLeast(0.0)..(target + band).coerceAtMost(high + band)
    }

    fun inWindow(kind: AlertKind, metersAhead: Double, speedMetersPerSecond: Double): Boolean {
        val spec = window(kind)
        val byTime = speedMetersPerSecond * spec.seconds
        val target = byTime.coerceIn(spec.minMeters, spec.maxMeters)
        val slack = 50.0
        return metersAhead in (target - slack).coerceAtLeast(0.0)..(target + slack)
    }

    fun maxLookaheadMeters(speedMetersPerSecond: Double): Double {
        return AlertKind.entries.maxOf { kind ->
            val spec = window(kind)
            (speedMetersPerSecond * spec.seconds).coerceIn(spec.minMeters, spec.maxMeters)
        } + 80.0
    }
}
