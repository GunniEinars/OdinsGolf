# CHANGELOG

All notable changes to OdinsGolf. Format loosely follows Keep a Changelog.

## [Unreleased]

### Added
- **Stableford pick-up**: press "–" past 1 (the slot before 0) to mark a hole picked up
  ("PU") — 0 Stableford points, excluded from gross totals, shown as PU/P on the scorecard,
  summary card and hole list. Press "+" to un-pick.
- **Score stepper opens on par**: each hole shows its par as a dim hint (tap the number to
  keep par; "+"/"–" nudge from there), so par/bogey/birdie are one tap instead of many from 0.
- **Hazards from OpenStreetMap**: bunkers and water for both courses, pulled from OSM and
  assigned to the nearest hole(s) by playing line (Setberg 24, Kiðjaberg 55). Drawn on the
  hole map (numbered) and listed as distances on the Distance screen. Reproducible via
  `tools/bake_hazards.mjs`; flagged `HAZARDS_FROM_OSM` in the course JSON.
- **Approximate green front/back**: synthesized from the green centre stepped ±11 m along
  each hole's playing line (`CourseDto.toDomain`), so approach yardages show without field
  work. A real Survey capture overrides them.
- **Survey capture confirmation**: each capture confirms on screen with accuracy (e.g.
  "Hazard captured ✓ (±5 m)"); the "Known for this hole" list shows a live hazard count.
- Hole map enriched: green drawn as a body with front/back edge dots, hazards larger and
  numbered, and a live Front/Centre/Back yardage strip along the bottom.
- Decimal handicap **index** (e.g. 15.7) with a dedicated watch editor (single centered
  row: −1 / −0.1 / +0.1 / +1); stroke allocation uses the rounded playing handicap.
- **Round modes**: 18 holes / Front 9 / Back 9 — controls hole navigation, the hole
  selector list, the starting hole, and which nines the scorecard totals show.
- **Multiple courses + in-app course picker** (Settings → Course). Added
  **Kiðjabergsvöllur** (Golfklúbburinn Kiðjaberg) with real OSM geometry (18 holes, par 71).
- **App logo**: launcher icon + opening splash screen (full-size emblem on white).
- **Live GPS debug readout** (Settings → Debug GPS info): raw status, lat/lon, accuracy,
  fix age; status pill now shows "Live · ±6 m · 3s".
- Hole map now always draws the tee→green playing line (reads even without a fix);
  "you" dot + dashed line appear with a GPS fix, "waiting for GPS" hint otherwise.
- **Bezel/rotary scrolling** on all scrollable screens (rotating/touch bezel scrolls lists).
- **Round history (manual save)**: a "Save round" action on the scorecard keeps the round in
  a scrollable history list (Settings → Round history); rounds are saved only when you choose.
- **Round Summary card**: a sleek card (course, date, to-par, Stableford, net, color-coded
  Out/In mini-grid). Save round opens it; tapping a history entry reopens it.
- **Save image**: renders the summary card to a PNG in Pictures/OdinsGolf via MediaStore, so
  it appears in the watch Gallery. JSON export remains as a quiet backup.
- Tests for playing-handicap rounding and round-mode ranges.

### Changed
- **Opening splash no longer double-takes**: the OS launch screen itself shows the OdinsGolf
  logo (held ~0.75 s) and the app appears directly — replacing the previous
  blank-white-then-Compose-logo sequence. The standalone Compose splash screen was removed.
- Scorecard **"Export" is now "Save card"**: renders the round PNG to the watch Gallery with
  on-screen feedback ("Card saved to Gallery ✓" / "Save failed" / "No score yet"), plus the
  quiet JSON backup. Previously a silent JSON write that looked like nothing happened.
- App background changed from pure black to a **dark gunmetal** (#1C2026) for a more
  premium look while staying dark for OLED battery and outdoor contrast.
- App icon logo inset further (26%) so the wordmark clears the circular launcher mask.
- **CI signs every APK with a committed stable debug keystore** (`app/odins-debug.keystore`),
  so watch updates install in place with `adb install -r` (no uninstall). CI also writes
  build errors to the run Summary.
- Launch shows the **full logo uncropped** on a white full-screen splash (the OS launch
  phase is a clean blank to avoid the circular-mask clipping that affects wide logos). The
  **app icon** is inset so the whole logo fits the circular launcher mask.

### Fixed
- **Hole map rendered blank** (only the tee→green line on an empty screen) when a GPS fix was
  far from the hole — the emulator default location or a stale fix from another course blew up
  the map scale and collapsed the hole to a dot. Fixes more than 2 km from the green are now
  ignored for framing.
- Unclosed-comment compile error in CourseRepository (nested `/*` in a KDoc).
- Wrong Stableford assertion in the round-totals test.
- Permission screen showed a giant logo bleeding through (launch windowBackground had
  been set to the logo drawable; reverted to solid black).

### Notes
- Green front/back are now **approximate** (centre ±11 m along the playing line), so the
  Distance screen always shows Front/Back and the Survey "Known" list marks them present.
  Capture on the green in Survey mode if you want surveyed edges; captures override the
  approximation.
- OSM hazards are assigned to a hole when within ~55 m of its tee→green line (or near the
  green/tee), so a greenside hazard on a shared green can attach to both holes that use it.
- Leiran (Hólmsvöllur í Leiru) was evaluated but **not added**: OSM has only the course
  boundary (no hole/green/tee geometry), so it would have no distances.
- One active round is kept at a time; switching courses starts a fresh round for that course.

### Next (planned)
- Move score/course persistence off the main thread.
- Scope the 5 s stale-tick so the hole map doesn't recompose when nothing moved.
- Optional: auto-advance to the nearest hole by GPS.

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
