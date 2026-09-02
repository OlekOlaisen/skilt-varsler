# Skilt-varsler

Android-varsler langs **NVDB-vegnettet**. Posisjon forlater ikke telefonen. Telefonene treffer aldri NVDB — CI bygger fliser, appen laster statiske filer.

Første sannhet: *jeg er på lenke X, om 80 m kommer fotoboks Y i min retning.*

## Moduler

| Mappe | Rolle |
| --- | --- |
| `pipeline/` | Eneste NVDB-klient. Python. Vestby (3216) som første flis. |
| `tiles/` | SQLite-flisformat og veggraf. |
| `matcher/` | GPS → lenke/posisjon/retning → varsler. Ingen Android-typer. |
| `app/` | Compose, foreground service, varsler, innstillinger, test-replay, Android Auto HUN. |

## Matcher-tester

```
./gradlew :tiles:test :matcher:test
```

Replay-testen bruker en syntetisk E6-flis (atskilt løp + parallell lokalvei). Vestby har ingen ATK-punkt i NVDB; pipelinen henter likevel vegnett og fartsgrense.

## Pipeline

```
cd pipeline
pip install -r requirements.txt
python -m skiltvarsler_pipeline --kommune 3216 --out ../tiles-out
```

Sett `NVDB_X_KONTAKTPERSON` (påkrevd mot NVDB fra januar 2026). `X-Client` er `skilt-varsler-pipeline`.

Nattlig GitHub Action (`pipeline.yml`) bygger fliser og publiserer dem som GitHub Release `nvdb-tiles`. Appen henter `manifest.json` og `.sqlite` derfra — telefonen treffer aldri NVDB.

Flis-URL (standard):

`https://github.com/OlekOlaisen/skilt-varsler/releases/latest/download`

Sett repo-secret `NVDB_X_KONTAKTPERSON` (e-post). Kjør **Actions → NVDB tile pipeline** med kommunenummer (komma-separert, f.eks. `3216` eller `0301,3203,3451`).

Inneholder data under [NLOD](https://data.norge.no/nlod/no/) tilgjengeliggjort av Statens vegvesen.
