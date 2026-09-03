package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignAssetIdTest {
    @Test
    fun stopAndYieldUseNorwegianHandbookNumbers() {
        assertThat(SignAssetId.candidates(AlertKind.STOP, "", 1L).first())
            .isEqualTo("204_0.svg")
        assertThat(SignAssetId.candidates(AlertKind.YIELD, "", 1L).first())
            .isEqualTo("202_0.svg")
        assertThat(SignAssetId.candidates(AlertKind.PRIORITY_ROAD, "", 1L).first())
            .isEqualTo("206_0.svg")
    }

    @Test
    fun speedLimitMapsKmhTo362Series() {
        assertThat(SignAssetId.candidates(AlertKind.SPEED_LIMIT, "80", 80L).first())
            .isEqualTo("362_80.svg")
        assertThat(SignAssetId.candidates(AlertKind.SPEED_LIMIT, "", 50L).first())
            .isEqualTo("362_50.svg")
        assertThat(SignAssetId.candidates(AlertKind.SPEED_LIMIT, "110", 110L).first())
            .isEqualTo("362_110.svg")
        assertThat(SignAssetId.candidates(AlertKind.SPEED_LIMIT, "100", 100L).first())
            .isEqualTo("362_100.svg")
        assertThat(SignAssetId.candidates(AlertKind.SPEED_LIMIT, "110", 110L))
            .doesNotContain("110_0.svg")
    }

    @Test
    fun cameraUsesPunktAtkAndSectionUsesStrekning() {
        assertThat(SignAssetId.candidates(AlertKind.SPEED_CAMERA, "", 1L).first())
            .isEqualTo("556_0.svg")
        assertThat(SignAssetId.candidates(AlertKind.SECTION_ATK_START, "", 1L).first())
            .isEqualTo("556_2.svg")
    }

    @Test
    fun ferryTollAndHazardUseOfficialNumbers() {
        assertThat(SignAssetId.candidates(AlertKind.FERRY, "", 1L).first())
            .isEqualTo("775_0.svg")
        assertThat(SignAssetId.candidates(AlertKind.TOLL, "", 1L).first())
            .isEqualTo("792_30.svg")
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "", 1L).first())
            .isEqualTo("156_0.svg")
    }

    @Test
    fun wildlifeNameMapsTo146Series() {
        assertThat(SignAssetId.candidates(AlertKind.WILDLIFE, "Elg", 1L).first())
            .isEqualTo("146_1.svg")
        assertThat(SignAssetId.candidates(AlertKind.WILDLIFE, "Hjort", 1L).first())
            .isEqualTo("146_2.svg")
    }

    @Test
    fun hazardPrefersSkiltnummerFromPayload() {
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "146.1", 1L).first())
            .isEqualTo("146_1.svg")
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "122|Lærdalstunnelen|24500", 1L))
            .contains("122_0.svg")
    }

    @Test
    fun curveSignsUseNumberedFilesThenNamedFallbacks() {
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "100.1", 1L).take(2))
            .containsExactly("100_1.svg", "skarp-sving-til-hoeyre.svg")
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "102.1", 1L).first())
            .isEqualTo("102_1.svg")
    }

    @Test
    fun flyUses150Not138() {
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "150", 1L))
            .contains("150_0.svg")
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "138.1", 1L).first())
            .isEqualTo("138_1.svg")
        assertThat(SignAssetId.candidates(AlertKind.HAZARD, "110", 1L))
            .contains("110_0.svg")
    }
}
