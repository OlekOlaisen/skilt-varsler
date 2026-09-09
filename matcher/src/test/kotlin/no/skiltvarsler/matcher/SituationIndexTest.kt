package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import no.skiltvarsler.tiles.LatLon
import org.junit.Test

class SituationIndexTest {
    @Test
    fun prefersSituationAheadInBearing() {
        val work = TrafficSituation(
            id = "ahead",
            type = SituationType.ROADWORK,
            title = "Veiarbeid",
            points = listOf(LatLon(59.9310, 10.7200)),
        )
        val behind = TrafficSituation(
            id = "behind",
            type = SituationType.ROADWORK,
            title = "Bak",
            points = listOf(LatLon(59.9280, 10.7200)),
        )
        val hit = SituationIndex.nearestAhead(
            position = LatLon(59.9300, 10.7200),
            bearingDegrees = 0.0,
            situations = listOf(work, behind),
            maxMeters = 400.0,
        )
        assertThat(hit!!.situation.id).isEqualTo("ahead")
        assertThat(hit.metersAway).isLessThan(150.0)
    }
}
