package no.skiltvarsler.matcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlertCopyTest {
    @Test
    fun tollUsesStationNameAndSkyttelpassPrice() {
        assertThat(AlertCopy.titleFor(AlertKind.TOLL, "792|Sørkedalsveien|42"))
            .isEqualTo("Sørkedalsveien")
        assertThat(AlertCopy.bodyFor(AlertKind.TOLL, 180.0, "792|Sørkedalsveien|42"))
            .isEqualTo("33,60 kr")
        assertThat(AlertCopy.titleFor(AlertKind.TOLL, "TOLL")).isEqualTo("Bomstasjon")
        assertThat(AlertCopy.bodyFor(AlertKind.TOLL, 120.0, "TOLL")).isEqualTo("")
    }

    @Test
    fun tunnelUsesNameAndLength() {
        assertThat(AlertCopy.titleFor(AlertKind.HAZARD, "122|Lærdalstunnelen|24500"))
            .isEqualTo("Lærdalstunnelen")
        assertThat(AlertCopy.bodyFor(AlertKind.HAZARD, 200.0, "122|Lærdalstunnelen|24500"))
            .isEqualTo("24,5 km")
        assertThat(AlertCopy.titleFor(AlertKind.HAZARD, "106.1")).isEqualTo("Smalere veg")
        assertThat(AlertCopy.bodyFor(AlertKind.HAZARD, 150.0, "106.1")).isEqualTo("")
        assertThat(AlertCopy.titleFor(AlertKind.HAZARD, "106.1 - Smalere veg"))
            .isEqualTo("Smalere veg")
        assertThat(ObjectPayload.parse("106.1 - Smalere veg").code).isEqualTo("106.1")
        assertThat(ObjectPayload.parse("106.1 - Smalere veg").title).isEqualTo("Smalere veg")
    }

    @Test
    fun onlyAtkAlertsIncludeApproachDistance() {
        assertThat(AlertCopy.bodyFor(AlertKind.SPEED_CAMERA, 220.0)).isEqualTo("Om 220 m")
        assertThat(AlertCopy.bodyFor(AlertKind.SECTION_ATK_START, 300.0)).isEqualTo("")
        assertThat(AlertCopy.bodyFor(AlertKind.SECTION_ATK_END, 90.0)).isEqualTo("")
        assertThat(AlertCopy.showsApproachDistance(AlertKind.SECTION_ATK_START)).isFalse()
        assertThat(AlertCopy.showsApproachDistance(AlertKind.SECTION_ATK_END)).isFalse()
        assertThat(AlertCopy.bodyFor(AlertKind.YIELD, 40.0)).isEqualTo("Ved skiltet")
        assertThat(AlertCopy.bodyFor(AlertKind.PRIORITY_ROAD, 15.0, "206")).isEqualTo("")
        assertThat(AlertCopy.showsApproachDistance(AlertKind.SPEED_CAMERA)).isTrue()
        assertThat(AlertCopy.showsApproachDistance(AlertKind.HAZARD)).isFalse()
    }

    @Test
    fun ferryAndSectionAtkUseNames() {
        assertThat(AlertCopy.titleFor(AlertKind.FERRY, "775|Moss–Horten")).isEqualTo("Moss–Horten")
        assertThat(AlertCopy.titleFor(AlertKind.SECTION_ATK_START, "556.2|Lærdalstunnelen"))
            .isEqualTo("Streknings-ATK")
        assertThat(AlertCopy.bodyFor(AlertKind.SECTION_ATK_START, 180.0, "556.2|Lærdalstunnelen"))
            .isEqualTo("Lærdalstunnelen")
        assertThat(AlertCopy.titleFor(AlertKind.SECTION_ATK_START, "556.2"))
            .isEqualTo("Streknings-ATK")
        assertThat(AlertCopy.bodyFor(AlertKind.SECTION_ATK_START, 0.0, "556.2")).isEqualTo("")
        assertThat(AlertCopy.sectionAverageBody(78)).isEqualTo("Gjennomsnittsfart 78 km/t")
        assertThat(AlertCopy.titleFor(AlertKind.SECTION_ATK_END, "556.2|Lærdalstunnelen"))
            .isEqualTo("Slutt streknings-ATK")
        assertThat(AlertCopy.titleFor(AlertKind.WILDLIFE, "Elg")).isEqualTo("Viltfare — elg")
        assertThat(AlertCopy.titleFor(AlertKind.PRIORITY_ROAD, "206")).isEqualTo("Forkjørsveg")
        assertThat(AlertCopy.titleFor(AlertKind.PRIORITY_ROAD, "208")).isEqualTo("Slutt på forkjørsveg")
    }

    @Test
    fun liveSituationsUseNorwegianTitles() {
        val roadwork = "roadwork|Vegarbeid, vegen er stengt, gyldig mellom 09:30 og 13:00"
        assertThat(AlertCopy.titleFor(AlertKind.ROADWORK, roadwork)).isEqualTo("Vegarbeid")
        assertThat(AlertCopy.bodyFor(AlertKind.ROADWORK, 200.0, roadwork))
            .isEqualTo("Vegarbeid, vegen er stengt, gyldig mellom 09:30 og 13:00")

        val closure = "closure|Vegarbeid, vegen er stengt."
        assertThat(AlertCopy.titleFor(AlertKind.ROADWORK, closure)).isEqualTo("Stengt veg")
        assertThat(AlertCopy.bodyFor(AlertKind.ROADWORK, 200.0, closure))
            .isEqualTo("Vegarbeid, vegen er stengt.")

        val accident = "accident|Trafikkulykke, ett stengt kjørefelt."
        assertThat(AlertCopy.titleFor(AlertKind.ACCIDENT, accident)).isEqualTo("Trafikkulykke")
        assertThat(AlertCopy.bodyFor(AlertKind.ACCIDENT, 200.0, accident))
            .isEqualTo("Trafikkulykke, ett stengt kjørefelt.")
    }

    @Test
    fun formatsShortTunnelLengthInMeters() {
        assertThat(AlertCopy.formatLengthMeters(80.0)).isEqualTo("80 m")
        assertThat(AlertCopy.formatLengthMeters(24000.0)).isEqualTo("24 km")
    }
}
