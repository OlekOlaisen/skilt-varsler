package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import no.skiltvarsler.tiles.RoadObjectType
import org.junit.Test

class AlertCombineTest {
    @Test
    fun mergePutsPriorityRoadBeforeSpeedLimitForTitleAndIcon() {
        val speed = alert(
            kind = AlertKind.SPEED_LIMIT,
            nvdbId = 40L,
            title = "Fartsgrense 40",
            body = "40 km/t",
        )
        val priority = alert(
            kind = AlertKind.PRIORITY_ROAD,
            nvdbId = 206L,
            title = "Forkjørsveg",
            body = "",
        )
        assertThat(AlertKind.PRIORITY_ROAD.priority).isGreaterThan(AlertKind.SPEED_LIMIT.priority)
        // Engine often emits speed before priority; sort must still prefer forkjørsveg.
        val merged = AlertCombine.merge(listOf(speed, priority))
        assertThat(merged.title).isEqualTo("Forkjørsveg · Fartsgrense 40")
        assertThat(merged.body).isEqualTo("40 km/t")
        assertThat(merged.kind).isEqualTo(AlertKind.PRIORITY_ROAD)
        assertThat(merged.nvdbId).isEqualTo(206L)
        assertThat(merged.objectType).isEqualTo(RoadObjectType.PRIORITY_ROAD)
    }

    @Test
    fun tollAndRoadworkRankAboveSpeedAndPriority() {
        assertThat(AlertKind.TOLL.priority).isGreaterThan(AlertKind.SPEED_LIMIT.priority)
        assertThat(AlertKind.ROADWORK.priority).isGreaterThan(AlertKind.SPEED_LIMIT.priority)
        assertThat(AlertKind.TOLL.priority).isGreaterThan(AlertKind.PRIORITY_ROAD.priority)
        assertThat(AlertKind.ROADWORK.priority).isGreaterThan(AlertKind.PRIORITY_ROAD.priority)

        val speed = alert(
            kind = AlertKind.SPEED_LIMIT,
            nvdbId = 40L,
            title = "Fartsgrense 40",
            body = "40 km/t",
        )
        val toll = alert(
            kind = AlertKind.TOLL,
            nvdbId = 1L,
            title = "Bomstasjon",
            body = "Om 100 m",
        )
        val merged = AlertCombine.merge(listOf(speed, toll))
        assertThat(merged.title).isEqualTo("Bomstasjon · Fartsgrense 40")
        assertThat(merged.kind).isEqualTo(AlertKind.TOLL)
    }

    @Test
    fun mergeSingleAlertUnchanged() {
        val speed = alert(
            kind = AlertKind.SPEED_LIMIT,
            nvdbId = 50L,
            title = "Fartsgrense 50",
            body = "50 km/t",
        )
        assertThat(AlertCombine.merge(listOf(speed))).isEqualTo(speed)
    }

    @Test
    fun dedupeKeepsFirstOccurrencePerKindAndId() {
        val first = alert(
            kind = AlertKind.PRIORITY_ROAD,
            nvdbId = 1L,
            title = "Forkjørsveg",
            body = "Om 10 m",
        )
        val duplicate = first.copy(body = "Om 5 m")
        val ordered = AlertCombine.dedupeAndSort(listOf(first, duplicate))
        assertThat(ordered).hasSize(1)
        assertThat(ordered.single().body).isEqualTo("Om 10 m")
    }

    private fun alert(
        kind: AlertKind,
        nvdbId: Long,
        title: String,
        body: String,
    ): Alert {
        return Alert(
            kind = kind,
            nvdbId = nvdbId,
            metersAhead = 0.0,
            title = title,
            body = body,
            sequenceId = 1L,
            objectType = RoadObjectType.PRIORITY_ROAD,
            payload = "",
        )
    }
}
