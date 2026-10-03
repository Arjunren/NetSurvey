# Architecture

## Dependency direction

```text
Compose UI
  → MainViewModel (immutable StateFlow-derived state)
    → NetSurveyRepository / ExportService / SettingsRepository
      → Room DAO / DataStore / validated files
    → WifiDataSource interface
      → AndroidWifiDataSource (WifiManager + ConnectivityManager)

Pure domain services (RSSI, channel mapping, IDW, propagation, wall geometry,
candidate scoring, CSV/MIME/ZIP validation) depend on no Android UI types.
```

Android Wi-Fi code is isolated behind `WifiDataSource`, which makes fake scan streams suitable for unit and UI tests. Expensive interpolation, candidate search, image work, ZIP work, and report generation run on background dispatchers and use bounded inputs.

## Gradle/module structure

The initial stable build uses a single `:app` module with layered packages. A future extraction into `:core:model`, `:core:domain`, `:data`, and `:wifi` can occur without changing package ownership. One module avoids circular feature dependencies while the field workflow is still evolving.

## Database schema

Database version: **2**. Backup schema: **2**. They are intentionally independent.

```text
Project ─┬─ Building
         ├─ Floor ─┬─ FloorPlan
         │         ├─ Room
         │         └─ Wall
         ├─ SurveySession ─ SurveyPoint ─ WifiObservation
         ├─ AccessPoint
         ├─ InstallationPhoto
         ├─ ChecklistItem
         └─ Report

CoverageProfile and DeviceCalibration are reusable local configuration records.
```

Foreign keys and cascade behavior prevent orphaned project records. Saving a survey point and its observations is a Room transaction. Historical observations copy all relevant scan fields and are never joined to mutable “current network” state.

## Navigation

- Dashboard
- Projects / floors / floor-plan import
- Wi-Fi Scanner
- Signal Meter
- Survey
- Heatmaps
- Channel Analyzer
- AP Planner
- AP Inventory
- Reports & Backup
- Settings

## Permission strategy

The manifest declares only Wi-Fi state/change, active-scan location, Nearby Wi-Fi, and optional vibration permissions. The UI asks at point of use. `SecurityException` and platform scan failure become actionable UI states. Project, floor-plan, reporting, and backup features remain usable if Wi-Fi permissions are denied.

## V1 implementation plan and status

1. Foundation, Compose theme/navigation — implemented
2. Room database, migration, relationships — implemented
3. Project/floor management and floor-plan import — implemented
4. Wi-Fi abstraction, permission states, scanner, live meter — implemented
5. Survey sessions, point measurements, AP markers — implemented
6. IDW heatmaps and channel analysis — implemented
7. Advisory AP planning and inventory — implemented baseline
8. PDF/CSV/PNG export, Sharesheet, ZIP backup/restore — implemented
9. Unit, database, and UI smoke tests — implemented; device matrix remains a field activity
10. POCO F5 stabilization — requires physical-device execution described in `TESTING.md`

## V2 boundaries

The domain layer already contains wall intersection, propagation, and candidate-scoring primitives. Full walk-survey route anchoring, roaming timelines, multi-floor vertical summary, QR labels, network topology, RTT, and capacity planning remain explicitly separate V2 work. Predicted data must never enter measured observation tables.
