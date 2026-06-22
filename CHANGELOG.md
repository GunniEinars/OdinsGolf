# CHANGELOG

All notable changes to OdinsGolf. Format loosely follows Keep a Changelog.

## [0.1.0] — 2026-06-22

Initial standalone Wear OS scaffold for the Galaxy Watch 4. Complete, buildable project.

### Added
- Compose for Wear OS app: Distance, Hole Map, Scorecard, Hole Selector, Settings, Survey,
  Permission screens with swipe-dismiss navigation.
- `RoundViewModel` + single `GolfUiState`; repositories for course, settings (DataStore),
  scorecard (JSON file), and field survey.
- `LocationEngine` over Fused Location Provider: high-accuracy, interval-based, lifecycle-aware
  (pause when hidden, burst on resume), GPS state model with staleness.
- Geo math: Haversine distance, bearing, cos(latitude) equirectangular `CanvasProjector`;
  Distances helper. Unit tests for geo and scoring.
- Scoring: handicap stroke allocation by stroke index, Stableford, net, to-par labels.
- **Setberg course data from OpenStreetMap** (relation 8318198): 9 shared greens, 18 playing
  holes with real tees, par and stroke index. Shared-green / different-par model.
- Survey mode: capture tee/front/center/back/hazard GPS points; live overlay; adb-pullable file.
- Battery-first behavior; optional keep-screen-on (off by default).
- GitHub Actions workflow to build the APK with no local toolchain.
- Docs: README, PROJECT_PLAN, DATA_SOURCES, COURSE_SCHEMA, BATTERY_STRATEGY, TESTING_CHECKLIST,
  SETUP_WINDOWS_NOADMIN.

### Known limitations
- Green front/back edges and exact stroke index need one field-verification round (Survey mode);
  until then front/back show `—`. Par is verified (sums to 72).
- Single course loaded (`setbergsvollur.json`); no course-picker UI yet.
- No ambient/always-on mode (deferred by design; lifecycle structured to add it later).
- One tee set per hole (OSM has 31 tee polygons for future multi-tee support).
