# Google Play — sjekkliste for Skilt-varsler

Bruk dette når du oppretter Play Console-oppføringen. Appen er fortsatt under utvikling; Test-fanen kan ligge igjen i interne spor.

## Data safety (Databeskyttelse)

| Spørsmål | Anbefalt svar |
|---|---|
| Samler appen inn brukerdata? | **Nei** for analyse/konto. Posisjon behandles **på enheten** for kjernefunksjon. |
| Posisjon | Brukes til app-funksjonalitet (kjørevarsler). **Ikke** delt med tredjeparter. Midlertidig / mens tjenesten kjører. |
| App-aktivitet / analyse | Nei |
| Personlig info / konto | Nei |
| Kryptering under overføring | Kartfiler lastes over HTTPS (GitHub). |
| Kan brukeren be om sletting? | Ja — stopp kjøretur / avinstaller (ingen skyskonto). |

Lenke til personvernerklæring (må være offentlig):

`https://github.com/OlekOlaisen/skilt-varsler/blob/main/docs/privacy-policy.md`

(Når du har eget domene, erstatt med stabil HTTPS-URL og oppdater `LegalCopy.PRIVACY_POLICY_URL` + `BuildConfig`.)

## Tillatelser — begrunnelse i Play Console

| Tillatelse | Begrunnelse |
|---|---|
| Fin / omtrentlig lokasjon | Map-matching og varsler under **aktiv kjøretur** med forgrunnstjeneste |
| Forgrunnstjeneste (lokasjon) | Synlig varsel mens GPS brukes under kjøring |
| Varsler | Heads-up om skilt og hendelser |
| Internett | Laste kart- og situasjonsfiler |

**Ikke** deklarer bakgrunnslokasjon — den er fjernet med vilje.

## Android Auto

- Manifest-kategori: `POI` + malbasert Car App.
- I Play Console: merk at appen støtter Android Auto og følg Auto-retningslinjer (lite distraksjon, maler).
- Etter publisering på Play trenger brukere normalt **ikke** «ukjente kilder».

## DATEX (live veiarbeid)

Pipeline-jobben `DATEX situations` bygger `situations.json` med secrets `DATEX_USER` / `DATEX_PASSWORD` og laster opp til release-tag `nvdb-tiles`. Telefonen henter filen; den ringer ikke DATEX direkte.

Driftsmeldinger fra Vegvesen: [DATEX information and news](https://www.vegvesen.no/en/about-us/about-us/open-data/datex/information-and-news/) (RSS tilgjengelig).

## Ansvarsfraskrivelse (butikktekst)

Foreslått kort tekst i beskrivelsen:

> Skilt-varsler er et hjelpemiddel basert på offentlige vegdata. Appen erstatter ikke skilting eller navigasjon. Du er alltid ansvarlig for egen kjøring.

## Signering (AAB)

1. Opprett upload-keystore (én gang).
2. Kopier `keystore.properties.example` → `keystore.properties` (gitignored).
3. Fyll inn stier og passord.
4. Bygg: `./gradlew :app:bundleRelease`
5. Last opp `.aab` til Internal testing.

Uten `keystore.properties` signeres release fortsatt med debug (kun lokalt/dev).

## Før produksjon (senere)

- [ ] Fjern eller skjul Test-fanen for produksjonsbrukere
- [ ] Stabil personvern-URL på eget domene
- [ ] Skjermbilder, ikon 512, feature graphic
- [ ] Verifiser Auto i en bil med Play-installasjon
- [ ] DATEX live-feed i release hvis ønsket
