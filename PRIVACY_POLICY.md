# GPS Map Camera - GeoTag — Privacy Policy

**Last updated:** 2026-10-14

GPS Map Camera - GeoTag is designed to run locally on your Android device and to keep the data it collects on that device.

## Data we handle

| Data | Purpose | Leaves device? |
| --- | --- | --- |
| Camera frames | Preview and photo capture | No |
| Captured photos | Local storage, stamping, sharing initiated by you | No |
| GPS coordinates | Overlay on photo, reverse geocoding, weather lookup | Only for reverse geocoding, map tiles and weather |
| Device sensors (compass, magnetometer) | Overlay on photo | No |
| Settings and templates | Local DataStore / Room | No |

## Third-party services

- **Google Maps SDK for Android** — used only when a map must be rendered. See Google's privacy terms.
- **Google Play Services (Fused Location)** — used to acquire location.
- **Open-Meteo** — queried with the camera coordinates when the Weather field is enabled. No account or key is required.
- **Google AdMob** — optional. Disabled in debug builds. When enabled Google may collect advertising identifiers.

## Permissions

- Camera — required to capture photos.
- Fine/Coarse Location — required to stamp coordinates, address, map and weather.
- Internet — required for reverse geocoding, maps and weather.
- Notifications — only requested if an in-app notification feature is enabled.

## Data retention and deletion

- Captured photos, templates and settings remain until you delete them manually or uninstall the app.
- Uninstalling removes all local data.

## Children

Not directed at children under 13.

## Changes

Updates to this policy will appear in a new release of the app.

## Contact

Open an issue on the project repository.
