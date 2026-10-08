package no.skiltvarsler.legal

/**
 * In-app legal copy for Play policy. Keep in sync with docs/privacy-policy.md.
 */
object LegalCopy {
    const val PRIVACY_POLICY_URL =
        "https://github.com/OlekOlaisen/skilt-varsler/blob/main/docs/privacy-policy.md"

    const val SHORT_PRIVACY =
        "Posisjon brukes bare på telefonen mens du kjører, for å varsle om skilt foran deg. " +
            "Appen har ikke konto, analyse eller annonser."

    const val SHORT_DISCLAIMER =
        "Skilt-varsler er et hjelpemiddel og erstatter ikke skilting, navigasjon eller " +
            "trafikkregler. Du er alltid ansvarlig for egen kjøring."

    const val PRIVACY_TITLE = "Personvernerklæring"

    val privacySections: List<Pair<String, String>> = listOf(
        "Hvem vi er" to
            "Skilt-varsler varsler om skilt og hendelser langs norske veger. " +
            "Behandlingsansvarlig er utgiveren av appen (Olek Olaisen).",
        "Hvilke data brukes" to
            "• Posisjon mens en kjøretur er i gang, for å varsle om det som ligger foran deg.\n" +
            "• Innstillingene dine lagres på telefonen.\n" +
            "• En feilsøkingslogg, bare hvis du selv starter den under Test og deler den.",
        "Hva vi ikke samler inn" to
            "Ingen konto, innlogging, annonser eller sporing. Posisjonen sendes ikke til oss.",
        "Kart og trafikkmeldinger" to
            "Kart og trafikkmeldinger lastes ned automatisk mens du kjører. " +
            "Nedlastingen inneholder ikke posisjonen din.",
        "Android Auto" to
            "Varsler kan vises i bilen. Posisjon brukes bare på telefonen mens kjøreturen er i gang.",
        "Dine valg" to
            "Du kan stoppe kjøreturen, avslå posisjonstilgang eller slå av varsler. " +
            "Avinstaller appen for å slette det som ligger lagret på telefonen.",
        "Rettslig grunnlag" to
            "Posisjon brukes for å levere varslene du har bedt om når du kjører.",
        "Kontakt" to
            "Spørsmål om personvern: bruk kontaktinformasjonen i Google Play-oppføringen, " +
            "eller prosjektets GitHub-side.",
        "Oppdatert" to
            "Denne erklæringen er sist oppdatert 7. september 2026.",
    )
}
