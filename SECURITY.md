# Security and privacy

- All primary project data remains in the app sandbox.
- Android backup is disabled for survey data because floor plans, SSIDs, BSSIDs, addresses, topology, and notes are sensitive.
- No analytics, ads, telemetry, remote API, login, or cloud database exists.
- Wi-Fi credentials are never requested, stored, logged, reported, or exported.
- Photo Picker and Storage Access Framework replace broad filesystem access.
- Imported floor plans are MIME/content checked, dimension-bounded, size-bounded, and copied into app-owned storage.
- Restore packages have an independent schema version, total and per-entry size limits, strict JSON decoding, and ZIP path traversal checks.
- File sharing uses a narrow cache-only `FileProvider` path, `content://` URIs, clip data, and temporary read permission.
- Old temporary exports are removed after seven days when the export service runs.
- PNG heatmaps omit BSSID/MAC, IP, serial, and confidential notes by default. CSV and backup are explicitly described as sensitive engineering exports.
- Room parameter binding prevents SQL injection. User content is never concatenated into SQL.
- Release minification is enabled; signing secrets and keystores are ignored by Git.

Threat-model follow-up for wider distribution should include fuzzed ZIP/image inputs, encrypted-at-rest requirements assessment, biometric device-credential app lock, and a third-party dependency audit.
