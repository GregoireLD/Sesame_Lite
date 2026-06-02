# Sesame Lite for Android

Sesame Lite stores your building access codes and notifies you automatically when you arrive at a saved location — so you never have to dig through your phone to find a code at the door.

This is a native Android port of [Sesame for iOS](https://github.com/GregoireLD/Sesame), built with Kotlin and Jetpack Compose. It is a deliberate "lite" build: core functionality is fully implemented, but some Apple-ecosystem features (iCloud sync, cross-device key sharing) are absent by design.

## Features

- **Arrival alerts** — geofencing-based notifications fire when you physically arrive at a saved location, with the code ready to copy
- **Encrypted at rest** — all sensitive fields (code, address, coordinates, notes) are encrypted with AES-256-GCM using a hardware-backed key in the Android Keystore
- **Customisable detection radius** — set how close you need to be before the alert fires (50–500 m)
- **Per-entry silencing** — disable geofencing for a specific entry without deleting it
- **Location details** — optional directions (floor, door colour, etc.) that appear in the notification alongside the code
- **Comments** — private notes for each entry
- **Search and sort** — filter by label or address; sort alphabetically or by distance to your current location
- **Cross-platform sharing** — share any entry as a QR code or link; links import correctly on iOS Sesame and vice versa
- **Import via clipboard** — paste a share link in either `https://sesame-app.com/share#…` or `sesame://import#…` form to import an entry without a network connection
- **10 languages** — Arabic (RTL), Chinese (Simplified & Traditional), English, French, German, Italian, Japanese, Korean, Spanish

## Differences from iOS Sesame

| iOS Sesame | Sesame Lite (Android) |
|---|---|
| iCloud / CloudKit sync | **Local only** — no cross-device sync |
| iCloud Keychain key (shared across devices) | Android Keystore key (device-bound) |
| Reinstalling keeps data (key syncs back) | Reinstalling loses data (key is destroyed) |
| In-app QR scanner | **Delegated to the system camera** — scan with your camera app, tap the resulting `sesame://import` link |
| macOS via iPad compatibility | Android only |

Shared entries (QR codes and links) **are** fully cross-platform — a code shared from iOS imports correctly on Android, and the other way around. Only the at-rest encrypted database is device-bound.

## Requirements

- Android 8.0 (API 26) or higher
- Google Play Services (required for geofencing)
- Background location permission ("Allow all the time") for automatic arrival alerts

## Building

```bash
git clone https://github.com/GregoireLD/Sesame  # iOS reference (optional)
# Open Sesame_Lite/ in Android Studio and run the app project
```

No API keys are required. Geocoding uses the platform's built-in `android.location.Geocoder` (requires an internet connection for address look-up only).

## Privacy

- All sensitive data is encrypted on your device before being written to disk
- Location data used for geofencing is never logged or transmitted
- Shared links encode the payload in the URL fragment (`#`), which is never sent to any server — the fragment is read client-side by the browser or directly by the app
- Your data is never our product

## Pricing

Sesame Lite is a one-time purchase on the Google Play Store — no subscription.

## Support

- Website: [sesame-app.com](https://sesame-app.com)
- Email: support@sesame-app.com
- Ko-fi: [ko-fi.com/duvalparis](https://ko-fi.com/duvalparis)

## License

See [LICENSE](LICENSE).

---

*Made with ♥ in Paris*
