package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignCatalogTest {
    @Test
    fun speedPayloadMapsToThatLimitOnly() {
        val off80 = AlertSettings(byId = mapOf("speed:80" to false, "speed:50" to true))
        assertThat(off80.enabled(AlertKind.SPEED_LIMIT, "80")).isFalse()
        assertThat(off80.enabled(AlertKind.SPEED_LIMIT, "50")).isTrue()
    }

    @Test
    fun wildlifeNameAndSkiltnummerShareTheSameToggle() {
        val offElg = AlertSettings(byId = mapOf("wildlife:elg" to false, "wildlife:hjort" to true))
        assertThat(offElg.enabled(AlertKind.WILDLIFE, "Elg")).isFalse()
        assertThat(offElg.enabled(AlertKind.HAZARD, "146.1")).isFalse()
        assertThat(offElg.enabled(AlertKind.WILDLIFE, "Hjort")).isTrue()
        assertThat(offElg.enabled(AlertKind.HAZARD, "146.2")).isTrue()
    }

    @Test
    fun hazardNumberIsItsOwnToggle() {
        val settings = AlertSettings(byId = mapOf("hazard:100.1" to false, "hazard:110" to true))
        assertThat(settings.enabled(AlertKind.HAZARD, "100.1")).isFalse()
        assertThat(settings.enabled(AlertKind.HAZARD, "110")).isTrue()
        assertThat(settings.enabled(AlertKind.HAZARD, "110 Vegarbeid")).isTrue()
        val offTunnel = AlertSettings(byId = mapOf("hazard:122" to false))
        assertThat(offTunnel.enabled(AlertKind.HAZARD, "122|Lærdalstunnelen|24500")).isFalse()
    }

    @Test
    fun missingSignFallsBackToLegacyCategory() {
        val settings = AlertSettings(
            byId = emptyMap(),
            categoryFallback = mapOf("speedLimit" to false, "hazard" to true),
        )
        assertThat(settings.enabled(AlertKind.SPEED_LIMIT, "45")).isFalse()
        assertThat(settings.enabled(AlertKind.HAZARD, "999")).isTrue()
    }

    @Test
    fun optionIdsAreUnique() {
        val ids = SignCatalog.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun hazardLabelsMatchOfficialSignNumbers() {
        val byPayload = SignCatalog.hazards.associate { it.payload to it.label }
        assertThat(byPayload["100.1"]).isEqualTo("Farlig sving til høyre")
        assertThat(byPayload["102.1"]).isEqualTo("Farlige svinger, første til høyre")
        assertThat(byPayload["138.1"]).isEqualTo("Andreaskors")
        assertThat(byPayload["140"]).isEqualTo("Gående")
        assertThat(byPayload["142"]).isEqualTo("Barn")
        assertThat(byPayload["144"]).isEqualTo("Syklende")
        assertThat(byPayload["150"]).isEqualTo("Fly")
        assertThat(byPayload["153"]).isEqualTo("Trafikkulykke")
        assertThat(byPayload["155"]).isEqualTo("Ridende")
    }
}
