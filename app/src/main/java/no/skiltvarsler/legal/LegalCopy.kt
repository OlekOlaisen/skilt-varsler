package no.skiltvarsler.legal

/**
 * In-app legal copy for Play policy. Keep in sync with docs/privacy-policy.md.
 */
object LegalCopy {
    const val PRIVACY_POLICY_URL =
        "https://github.com/OlekOlaisen/skilt-varsler/blob/main/docs/privacy-policy.md"

    const val SHORT_PRIVACY =
        "Posisjon brukes bare på telefonen til å treffe vegnettet og varsle. " +
            "Appen har ikke konto, analyse eller annonser. Telefonen kontakter ikke NVDB direkte."

    const val SHORT_DISCLAIMER =
        "Skilt-varsler er et hjelpemiddel og erstatter ikke skilting, navigasjon eller " +
            "trafikkregler. Du er alltid ansvarlig for egen kjøring."

    const val PRIVACY_TITLE = "Personvernerklæring"

    val privacySections: List<Pair<String, String>> = listOf(
        "Hvem vi er" to
            "Skilt-varsler er en Android-app som varsler om skilt og hendelser langs norske veger. " +
            "Behandlingsansvarlig er utgiveren av appen (Olek Olaisen / prosjektet skilt-varsler).",
        "Hvilke data brukes" to
            "• Posisjon (GPS) mens kjøretur er aktiv, for å mappe deg til vegnettet og varsle.\n" +
            "• Valgte innstillinger (hvilke varsler som er på) lagres lokalt på telefonen.\n" +
            "• Valgfritt: debug-logg du selv eksporterer fra Test-fanen.",
        "Hva vi ikke samler inn" to
            "Appen har ikke brukerkonto, innlogging, analyse-SDK, annonser eller krasjrapportering " +
            "til tredjepart. Posisjon sendes ikke til oss.",
        "Kart og trafikkmeldinger" to
            "Telefonen laster ned ferdige kartfiler og eventuelt trafikksituasjoner fra appens " +
            "utgivelseskanal (GitHub Releases). Nedlastingene er anonyme filhentinger. " +
            "Rå NVDB-data hentes ikke fra telefonen. Live veiarbeid kan komme fra Statens vegvesen " +
            "DATEX via vår byggserver, ikke direkte fra telefonen.",
        "Android Auto" to
            "Varsler kan vises i bilen via Android Auto. Samme lokasjonsbehandling gjelder: " +
            "posisjon brukes på enheten under aktiv kjøretur.",
        "Dine valg" to
            "Du kan stoppe kjøretur når som helst, avslå lokasjonstilgang, eller dempe varsler. " +
            "Avinstaller appen for å fjerne lokale innstillinger og hurtigbuffer.",
        "Rettslig grunnlag" to
            "Behandling skjer for å levere funksjonen du ber om (kontrakt/nødvendig for tjenesten) " +
            "når du starter kjøretur. Vegdata er offentlige data under NLOD der det gjelder.",
        "Kontakt" to
            "Spørsmål om personvern: bruk kontaktinformasjonen i Google Play-oppføringen, " +
            "eller prosjektets GitHub-side.",
        "Oppdatert" to
            "Denne erklæringen er sist oppdatert 7. september 2026.",
    )
}
