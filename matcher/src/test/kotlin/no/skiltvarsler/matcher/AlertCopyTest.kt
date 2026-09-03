package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlertCopyTest {
    @Test
    fun tollUsesStationNameAndSkyttelpassPrice() {
        assertThat(AlertCopy.titleFor(AlertKind.TOLL, "792|Sørkedalsveien|42"))
            .isEqualTo("Sørkedalsveien")
        assertThat(AlertCopy.bodyFor(AlertKind.TOLL, 180.0, "792|Sørkedalsveien|42"))
            .isEqualTo("Om 180 m · 33,60 kr")
        assertThat(AlertCopy.titleFor(AlertKind.TOLL, "TOLL")).isEqualTo("Bomstasjon")
        assertThat(AlertCopy.bodyFor(AlertKind.TOLL, 120.0, "TOLL")).isEqualTo("Om 120 m")
    }

    @Test
    fun tunnelUsesNameAndLength() {
        assertThat(AlertCopy.titleFor(AlertKind.HAZARD, "122|Lærdalstunnelen|24500"))
            .isEqualTo("Lærdalstunnelen")
        assertThat(AlertCopy.bodyFor(AlertKind.HAZARD, 200.0, "122|Lærdalstunnelen|24500"))
            .isEqualTo("Om 200 m · 24,5 km")
        assertThat(AlertCopy.titleFor(AlertKind.HAZARD, "106.1")).isEqualTo("Smalere veg")
        assertThat(AlertCopy.bodyFor(AlertKind.HAZARD, 150.0, "106.1")).isEqualTo("Om 150 m")
    }

    @Test
    fun ferryAndSectionAtkUseNames() {
        assertThat(AlertCopy.titleFor(AlertKind.FERRY, "775|Moss–Horten")).isEqualTo("Moss–Horten")
        assertThat(AlertCopy.titleFor(AlertKind.SECTION_ATK_START, "556.2|Lærdalstunnelen"))
            .isEqualTo("Lærdalstunnelen")
        assertThat(AlertCopy.titleFor(AlertKind.WILDLIFE, "Elg")).isEqualTo("Viltfare — elg")
    }

    @Test
    fun formatsShortTunnelLengthInMeters() {
        assertThat(AlertCopy.formatLengthMeters(80.0)).isEqualTo("80 m")
        assertThat(AlertCopy.formatLengthMeters(24000.0)).isEqualTo("24 km")
    }
}
