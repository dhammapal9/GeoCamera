# GeoStamp Camera

Android app (Kotlin + XML, Material 3) that captures photos and permanently stamps GPS coordinates, address, timestamp, map thumbnail, weather and sensor data onto the final JPEG.

## Highlights

- CameraX preview + capture pipeline with front/rear, flash, grid, timer, tap-to-focus.
- Full stamp template engine (Classic, Advance, Date & Time, Reporting, Navigation Compass, Scan Location, Travel, Journal, Compact Strip, Minimal).
- Template editor: toggle fields, change colors (JSON), add logo via Android Photo Picker, add note/hashtag/project.
- Reliable `PhotoStampRenderer` that respects EXIF orientation and keeps text within bounds.
- Fused Location + fallback (legacy LocationManager + stored last-known).
- Reverse geocoding, Plus Codes, DMS/decimal coordinate formatting.
- Google Maps SDK for preview, picker, and stamp snapshot. Graceful placeholder when the API key is missing or the device is offline.
- Weather via Open-Meteo (free, no key).
- Sensors: compass heading + magnetic field strength.
- Gallery (Room) + MediaStore export on API 29+.
- Map browser of all geotagged captures.
- ML Kit QR scanner with torch, copy/share, and URL open prompt.
- Settings (theme, photo quality, coord format, map type, keep original, shutter sound, volume action).
- Jetpack Navigation, Hilt DI, Coroutines + Flow throughout.

## Project structure

```
GPSPhoto/
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/idp/geostampcamera/
│     │   ├─ app/        (Application)
│     │   ├─ data/       (Room, repositories, Open-Meteo remote)
│     │   ├─ di/         (Hilt modules)
│     │   ├─ domain/     (models)
│     │   ├─ location/   (Fused + legacy GPS, geocoder)
│     │   ├─ camera/     (CameraController)
│     │   ├─ stamp/      (PhotoStampRenderer, StampTextBuilder)
│     │   ├─ map/        (MapSnapshotProvider)
│     │   ├─ sensors/    (compass, magnetic)
│     │   ├─ weather/    (Open-Meteo repository)
│     │   ├─ qr/         (ML Kit analyzer)
│     │   ├─ util/       (coordinate / date formatters, Plus Codes, permissions)
│     │   └─ ui/         (fragments, view models, adapters)
│     └─ res/            (layouts, drawables, navigation, menus, strings)
├─ build.gradle.kts
├─ settings.gradle.kts
└─ README.md
```

## Setup

1. Install Android Studio Giraffe or newer and Android SDK 34.
2. `cp local.properties.sample local.properties` and set:
   - `sdk.dir=/absolute/path/to/Android/Sdk`
   - `MAPS_API_KEY=<your Google Maps SDK for Android key>` (optional; map renders a placeholder without it)
   - `ADMOB_APP_ID=<your AdMob App ID>` (optional; defaults to Google's public test ID)
3. Open the project in Android Studio and let Gradle sync.
4. Run on a device/emulator with camera + Google Play Services.

### Google Maps API key

- Enable the "Maps SDK for Android" product on your Google Cloud project.
- Create an Android-restricted API key. The default `com.idp.geostampcamera` application ID is already placed in the manifest template.
- When the key is missing, the Map screen shows a friendly placeholder, and the stamp uses a procedurally drawn map tile.

### Weather

- Uses [Open-Meteo](https://open-meteo.com/). No API key required. If offline the stamp quietly omits the weather line.

### AdMob

- `ADS_ENABLED` is false in debug builds. Flip it on by changing the `buildTypes` in `app/build.gradle.kts` or by using a release build with a configured App ID.
- Only consent-driven initialisation is performed in `GeoStampApp`.

## Build / release

The repository ships without a committed Gradle wrapper jar. The first time you
open the project in Android Studio it will auto-provision a wrapper matching
`gradle/wrapper/gradle-wrapper.properties` (8.7). To generate it manually:

```bash
gradle wrapper --gradle-version 8.7
```

Then:

- `./gradlew assembleDebug`
- `./gradlew assembleRelease` — signs with the configuration you supply in `~/.gradle/gradle.properties` or by `signingConfigs.release` in your fork.
- `./gradlew testDebugUnitTest`
- `./gradlew lint`

The project uses Java 17, Kotlin 1.9.24, AGP 8.5.2, and Gradle 8.7.

## Testing

Unit tests cover coordinate formatting (decimal/DMS/hemispheres/validation), date formatting across time zones, Plus Code encoding, cardinal direction conversion, and stamp text visibility rules. See `app/src/test/`.

Please run instrumentation tests on a device for CameraX, location and sharing flows.

## Known limitations

- Terrain and Hybrid map types fall back to Normal on devices without the full Google Maps SDK.
- Open-Meteo is used instead of a paid provider; data cadence may differ by region.
- No account system — all data stays on-device.

## Privacy

- No photos, notes, or coordinates leave the device.
- Google Maps / Google Play Services and Open-Meteo receive the coordinates necessary for their requests.
- AdMob is opt-in and off by default in debug builds.

Full policy text is shown in-app from the Settings screen.
