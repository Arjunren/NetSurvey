# Android Wi-Fi limitations

## Nearby scans are snapshots

`WifiManager.startScan()` is throttled. Android 9 and later commonly permit a foreground app only four active scans in two minutes, while system and other-app scans may still update the shared result cache. NetSurvey listens for `SCAN_RESULTS_AVAILABLE_ACTION`, checks whether results were updated, falls back to cached results, and displays the distinction.

The app does not poll `startScan()` in a loop. A manual refresh can fail without being an app defect.

## Permissions and system state

Current Android documentation still requires precise location permission and enabled Location services for `getScanResults()` / `startScan()` in this use case. Android 13+ also exposes Nearby Wi-Fi permission for Wi-Fi operations. OEM behavior can add restrictions. NetSurvey catches permission failures instead of crashing.

No GPS coordinate is collected or stored. Engineers manually anchor indoor positions on a floor plan.

## Connected AP versus nearby networks

The signal meter reads connection information from the active Wi-Fi transport (or the legacy connection API on older Android). It can update at a reasonable sampling interval.

Nearby BSSID RSSI values come from scan results and do not update every second unless Android actually provides a new scan. The two sources are labeled separately.

## Measurement limits

RSSI changes with device calibration, grip, orientation, people, doors, furniture, channel width, transmit power, and multipath. Channel counts are not airtime utilization. Android scan results do not normally provide a trustworthy noise floor, SNR, packet loss, AP client count, or throughput measurement.

6 GHz and reported Wi-Fi standard/width fields appear only when the device, Android release, and AP expose them. Wi-Fi RTT is not required and is not enabled in V1.
