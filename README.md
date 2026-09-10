# Skilt-varsler

Road-sign and hazard alerts while you drive, on the phone and in **Android Auto**. GPS stays on the device. The phone never calls NVDB or DATEX.

The app matches your position to Norway’s official road network and warns you when something is ahead **on the road you are on, in your direction**. The app is still a work in progress, so everything is not perfect yet.

## Features

- **Direction-aware alerts** — cameras and signs on *your* link, not a parallel road or the opposite carriageway
- **Android Auto heads-up** — alerts can appear over the map while you navigate
- **Combined heads-up** — near-simultaneous signs share one notification (e.g. «Forkjørsveg · Fartsgrense 30»)
- **Official sign artwork** — Norwegian traffic-sign icons in the app and in notifications
- **Automatic map tiles** — kommuner are downloaded ahead of the car, so the next one is ready before you cross the border
- **Live situations** — accidents and roadworks from Vegvesen DATEX, refreshed while you drive
- **Through-road matching** — stop/yield on side streets you drive past stay silent; speed limits and forkjørsveg alert when you enter, not as a long-range foreshadow

### On the phone

- **Hjem** — Start / Stop a trip. Last alert and tile status. Location is used only on the device. Tracking can auto-start when the app opens.
- **Varsler** — turn each alert type on or off. Choices are remembered.
- **Statistikk** — trip summary after you stop: distance, duration, cameras, tolls, and other signs you passed.
- **Test** — synthetic sign tests and GPS replay of recorded traces (development).
- **Innstillinger** — mute heads-up, combine simultaneous alerts, auto-start, privacy.

### Alert types

| Type | Examples |
| --- | --- |
| Speed cameras | Point cameras (ATK) |
| Speed limits | Posted limit when you enter the zone or turn onto another road |
| Section ATK | Average-speed stretches, start and end |
| Tolls | Toll stations |
| Wildlife | Moose and other animal-warning signs |
| Rail crossings | Level crossings |
| Ferry | Ferry quay |
| Stop / yield | Stopp and vikeplikt on *your* road |
| Hazard signs | Official fare-skilt from NVDB |
| Priority road | Forkjørsveg at the plate or when you join the stretch |
| Municipality | Entering a kommune |
| Roadworks | Live DATEX roadworks on your route |
| Accidents | Live DATEX traffic accidents on your route |

## Download and install

Install the APK from a [Skilt-varsler app release](https://github.com/OlekOlaisen/skilt-varsler/releases/tag/v0.1.18) (`skilt-varsler-*.apk`). Do not use the **NVDB-fliser** release for the app — that one stays marked Latest so phones can download tiles from `releases/latest/download`.

1. Allow **Install unknown apps** for your browser or file manager.
2. Open the APK and install.
3. Grant **location** and **notification** permission.
4. Tap **Start** (or open the app — tracking auto-starts if you left that on). The app waits for GPS, then downloads the tile for the kommune you are in and neighbouring ones ahead.

To update, install the new APK over the one you already have.

Requires Android 10 or newer.

## Android Auto

1. Install from Play (or a trusted build).
2. Connect to the car and grant location and notification access.
3. Pin Skilt-varsler on the Android Auto launcher (Customize launcher).

During development, sideloaded builds may still require “unknown sources” in Android Auto developer settings.

Alerts can then show as heads-up over the map while you navigate. Simultaneous signs can share one heads-up; turn that off under Innstillinger if you prefer them one by one.

## Privacy

Location is used only on the phone to match roads and signs. The app does not need an account or analytics. Phones download static map tiles and a `situations.json` file from GitHub; they never contact [NVDB](https://www.vegvesen.no/nvdb) or DATEX.

Full policy: [docs/privacy-policy.md](docs/privacy-policy.md). Play Console checklist: [docs/play-console.md](docs/play-console.md).

Road data is published by Statens vegvesen under [NLOD](https://data.norge.no/nlod/no/).

## Disclaimer

Skilt-varsler is a driving aid. It does not replace road signs, navigation, or traffic rules. You remain responsible for your own driving.
