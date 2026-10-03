# Testing

## Automated

```powershell
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Pure JVM tests cover channel/frequency mapping, RSSI classification, rolling statistics, calibration, IDW interpolation, wall intersection, propagation loss, AP candidate scoring, CSV escaping, ZIP path validation, and MIME selection.

Instrumented tests cover transactional measurement insertion, foreign-key cascade behavior, and a Compose dashboard smoke test:

```powershell
./gradlew connectedDebugAndroidTest
```

## POCO F5 field matrix

Run with the shipping device and current HyperOS/Android update:

1. Fresh install, first-run denial, retry, permanent denial and Settings recovery
2. Location services off, Wi-Fi off, airplane mode, and empty scan results
3. 2.4 GHz and 5 GHz scans; 6 GHz only if the device actually reports it
4. Compare connected BSSID/RSSI with Android system details
5. Trigger scan throttling and confirm cached timestamp/status without freezes or retry loops
6. Rotate every screen and test system/light/dark themes plus large font scaling
7. Import 12 MP PNG and JPEG floor plans; tap all corners; calibrate a known distance
8. Create 100+ points, each with many BSSID observations; confirm heatmap responsiveness
9. Background/foreground during a live meter and survey; verify wake flag clears when stopped
10. Generate and open PDF, CSV, PNG, text summary, and ZIP backup
11. Share each format to compatible installed targets; verify `content://` and receiver readability
12. Restore backup as a separate imported project and compare counts
13. Run a complete survey with mobile data and internet disabled
14. Observe battery and thermal behavior during a 60-minute field session

No test report should describe a hardware-only workflow as passed until it has actually run on the POCO F5.
