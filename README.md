# NetSurvey

NetSurvey is a native, offline-first Android Wi-Fi site-survey and installation documentation tool for field engineers. It combines Android-provided Wi-Fi observations with floor-plan locations, measured RSSI heatmaps, channel analysis, AP inventory, placement advice, and local reports without an account or backend.

The primary field target is the POCO F5. The app remains useful on other Android devices and detects platform capabilities at runtime.

## What works

- Local projects, floors, survey sessions, AP inventory, and measurement history in Room
- PNG/JPEG floor-plan import through Android Photo Picker with content and size validation
- Normalized floor-plan coordinates and two-point scale calibration
- Permission-aware Wi-Fi scanner with fresh/cached status, scan age, BSSID, RSSI, band, channel, security capabilities, width, and reported Wi-Fi standard
- Connected-AP signal meter with rolling average, min/max, standard deviation, history, and editable RSSI thresholds
- Tap-to-measure point surveys that preserve raw and calibrated RSSI independently
- Measured-point RSSI heatmaps using bounded, cancellable inverse-distance weighting; interpolated areas are labeled as such
- Channel competition summary and advisory channel candidates (never mislabeled as airtime utilization)
- Bounded AP-placement candidate scoring based on actual weak points
- PDF report, CSV measurements, privacy-conscious PNG heatmap, and versioned ZIP backup/restore
- Android Sharesheet via narrow `FileProvider` `content://` access and temporary read grants
- Local DataStore settings; no login, analytics, advertising, backend, or cloud sync

## Technical honesty

RSSI is client- and environment-dependent. Android may throttle active scans; NetSurvey shows cached results and timestamps rather than simulating updates. The connected AP signal meter and nearby scan snapshots are separate data paths. Black floor-plan markers are measurements; color between points is interpolation. AP-placement and propagation results are planning aids and must be validated on site.

NetSurvey does not invent or display noise floor, SNR, airtime utilization, packet loss, throughput, latency, or AP client count when Android does not supply those measurements.

## Build

Requirements:

- Android Studio with JDK 17 or newer
- Android SDK Platform 37 when using the current Compose 1.12 stack
- Android SDK Build Tools 36.0.0 or newer

```powershell
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Install on a connected POCO F5:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing is intentionally external. Create a private keystore, provide credentials outside source control, and configure a local signing block or Android Studio signing workflow. Never commit a keystore or its passwords. See [BUILDING.md](BUILDING.md).

## Permissions

- `ACCESS_WIFI_STATE`: read Wi-Fi state and results
- `CHANGE_WIFI_STATE`: request a user-initiated scan
- `ACCESS_COARSE_LOCATION`: paired with precise location in the Android runtime request
- `ACCESS_FINE_LOCATION`: required by current Android APIs for `startScan()` / `getScanResults()`
- `NEARBY_WIFI_DEVICES`: current Android nearby Wi-Fi access
- `VIBRATE`: optional field indication setting

NetSurvey does not request broad storage permission. Photo Picker and Storage Access Framework provide imports; generated shares use app-controlled cache files.

## Project layout

The shipping artifact is one Android application module with strict package boundaries. This keeps build and navigation integration straightforward while preserving the dependency direction described in [ARCHITECTURE.md](ARCHITECTURE.md).

```text
app/src/main/java/com/arjunren/netsurvey/
├── data/          Room/DataStore repositories
├── domain/        RF math, statistics, validation
├── report/        CSV/PDF/PNG/ZIP generation and sharing
├── storage/       validated floor-plan import
├── ui/            Compose screens and ViewModel
└── wifi/          Android Wi-Fi platform adapter
```

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [WIFI_LIMITATIONS.md](WIFI_LIMITATIONS.md)
- [SECURITY.md](SECURITY.md)
- [TESTING.md](TESTING.md)
- [BUILDING.md](BUILDING.md)

## License

MIT — see [LICENSE](LICENSE).
