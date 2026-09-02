# Skilt-varsler

Road-sign and hazard alerts while you drive, on the phone and in **Android Auto**. GPS stays on the device. The phone never calls NVDB.

The app matches your position to Norway’s official road network and warns you when something is ahead **on the road you are on, in your direction**. The app is still a work in progress, so everything is not perfect yet.

## Features

- **Direction-aware alerts** — cameras and signs on *your* link, not a parallel road or the opposite carriageway
- **Android Auto heads-up** — alerts can appear over the map while you navigate
- **Official sign artwork** — Norwegian traffic-sign icons in the app and in notifications
- **Automatic map tiles** — the kommune you are in is downloaded as you drive; crossing a border fetches the next one

### On the phone

- **Start / Stop** — turn tracking on or off. Location is used only on the device.
- **Last alert** — the most recent warning, with the official sign.
- **Alerts** — turn each alert type on or off. Choices are remembered.
- **Test-replay** — play a synthetic E6 speed-camera approach to check that phone (and Auto) notifications work.

### Alert types

| Type | Examples |
| --- | --- |
| Speed cameras | Point cameras (ATK) |
| Speed limits | Posted limit changes |
| Section ATK | Average-speed stretches, start and end |
| Tolls | Toll stations |
| Wildlife | Moose and other animal-warning signs |
| Rail crossings | Level crossings |
| Ferry | Ferry quay |
| Stop / yield | Stopp and vikeplikt |
| Hazard signs | Official fare-skilt from NVDB |
| Priority road | Forkjørsveg |
| Municipality | Entering a kommune |

## Download and install

Road tiles are published on the [latest release](https://github.com/OlekOlaisen/skilt-varsler/releases/latest). Install an APK from Releases when one is attached.

1. Allow **Install unknown apps** for your browser or file manager.
2. Open the APK and install.
3. Grant **location** and **notification** permission.
4. Tap **Start**. The app waits for GPS, then downloads the tile for the kommune you are in.

To update, install the new APK over the one you already have.

Requires Android 10 or newer.

## Android Auto

The app is sideloaded, so Android Auto must allow unknown sources:

1. Open Android Auto settings on the phone.
2. Enable developer settings (tap the version number repeatedly).
3. Allow unknown sources.
4. Connect to the car and grant location and notification access.
5. Pin Skilt-varsler on the Android Auto launcher (Customize launcher).

Alerts can then show as heads-up over the map while you navigate.

## Privacy

Location is used only on the phone to match roads and signs. The app does not need an account or analytics. Phones download static map tiles; they never contact [NVDB](https://www.vegvesen.no/nvdb).

Road data is published by Statens vegvesen under [NLOD](https://data.norge.no/nlod/no/).
