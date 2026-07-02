# PROJECT_PLAN.md

## Goal

A standalone Wear OS golf GPS app for one player on one (then a few) Icelandic courses.
Fast, outdoor-readable, battery-conscious, fully offline.

## Architecture

Single Activity → Compose for Wear OS, MVVM with one `RoundViewModel` exposing a single
`GolfUiState` via `StateFlow`. No DI framework, no Room, no background services.

```
MainActivity (system splash + permission gate + lifecycle)
  └─ OdinsGolfApp (SwipeDismissableNavHost)
       ├─ RoundPager (HorizontalPager)  ← the on-course core
       │    ├─ DistanceScreen  (hero centre distance, F/B, plays-like, carry, GPS)
       │    ├─ ScorecardScreen (steppers, totals, Stableford/net, pick-up)
       │    └─ HoleMapScreen   (vector hole + satellite toggle)
       └─ pushed: SettingsScreen ("More"), HoleSelectorScreen, HandicapScreen,
            CoursePickerScreen, HistoryScreen, RoundSummaryScreen, SurveyScreen

RoundViewModel  →  single GolfUiState (settings + course + gps + round + tick)
  ├─ CourseRepository    (assets/courses/*.json → domain Course)
  ├─ SettingsRepository  (Preferences DataStore)
  ├─ ScorecardRepository (active round JSON in filesDir)
  ├─ SurveyRepository    (captured points + live overlay onto Course; a green-centre
  │                       capture applies to both holes sharing that greenId)
  ├─ HistoryRepository   (saved rounds JSON)
  ├─ TileRepository      (satellite tiles, disk-cached)
  └─ LocationEngine      (FusedLocationProvider, lifecycle-driven)

geo/   Geo (Haversine, bearing, destination), Distances, Terrain (PlaysLike + Carry),
       HoleProjection (playing-line-up vector), SlippyMap/MapPlan (Web-Mercator tiles),
       CanvasProjector (cos-lat, unit-tested)
scoring/ Scoring (WHS course handicap from slope/rating + allowance, handicap allocation,
       Stableford, net, to-par; scoring format = Stroke play | Stableford)
```

Layering rule: parsing DTOs (`data/dto`) never leak into UI; the UI only sees the resolved
domain model (`data/model`). Pure math (`geo`, `scoring`) has no Android deps and is unit-tested.

## Key design decisions (and why)

- **High-accuracy GPS, spaced intervals.** Golf needs accurate fixes, so we always request
  `PRIORITY_HIGH_ACCURACY`; the *interval* (8–25 s by mode) is the battery lever, not the priority.
- **cos(latitude) projection.** At Setberg's 64°N, longitude must be scaled by ~0.44 or the
  map stretches ~2.3× sideways. Baked into `CanvasProjector` and unit-tested.
- **Shared-green model.** 9 physical greens, 18 playing holes; front/back live on the hole
  (approach-specific), center on the shared green.
- **No fake coordinates.** Missing geometry shows `—` / "geometry missing", never a number.
- **Stableford + net.** Icelandic club golf runs on Stableford and stroke index; nearly free
  to compute since we already store par + SI.
- **Ambient/always-on deferred.** Normal raise-wrist→sleep cycle is best for battery; lifecycle
  code is structured so ambient can be added later (see BATTERY_STRATEGY.md).

## Phase status

- **Phase 0 — Research & planning** ✅ Verified Wear OS approach; pulled Setberg geometry from
  OSM (relation 8318198); wrote all docs.
- **Phase 1 — Scaffold** ✅ Gradle KTS + version catalog, manifest, Compose base, CI workflow.
- **Phase 2 — Course data** ✅ Schema, real Setberg JSON, loader, missing-geometry handling.
- **Phase 3 — GPS & distances** ✅ Permission flow, Fused provider, lifecycle updates, GPS states,
  distance screen.
- **Phase 4 — Scorecard** ✅ Local state, entry UI, totals, to-par/Stableford/net, persistence, reset/export.
- **Phase 5 — Hole map** ✅ Canvas vector map with safe handling of missing geometry.
- **Phase 6 — Polish & battery** ✅ Settings, GPS modes, stale handling, survey mode. Ambient = TODO.
- **Phase 7 — On-watch iteration** ✅ Decimal handicap + editor, round modes (18/Front/Back),
  course picker + Kiðjabergsvöllur, app logo (icon + splash via SplashScreen API), live GPS
  debug readout, tee→green map line. CI: committed stable debug keystore (in-place `install -r`),
  build errors surfaced to the run Summary.

## CI / install notes

- GitHub Actions builds `app-debug.apk` on every push (no local toolchain needed).
- A committed debug keystore (`app/odins-debug.keystore`, generated once by CI) keeps the
  signature stable, so the watch updates in place with `adb install -r` — no uninstall, no
  data loss. (Only a watch factory-reset would require a one-off uninstall.) A debug keystore
  is not a secret, so committing it is fine for a private app.

- **Phase 8 — Native-feel polish** ✅ Bezel/rotary scrolling; round history with manual save;
  system SplashScreen with the emblem.
- **Phase 9 — Vector course map + offline data** ✅ Real OSM polygons (fairway/green/bunker/
  water/tee) + hole centrelines baked per hole (`tools/bake_geometry.mjs`); playing-line-up
  vector hole map with 150/100/250 rings, pin flag and satellite toggle; **plays-like**
  (EU-DEM elevation, cross-checked vs ASTER) and **hazard carry**; **par + stroke index
  verified against the official scorecards** (Rástímar — Setberg h9 SI 10, Kiðjaberg h13 SI 4).
- **Phase 10 — Navigation + glance polish** ✅ 3-screen swipe pager (Distance ⇄ Card ⇄ Map);
  slimmed one-glance Distance screen; single **More** menu; **stale-fix honesty** (old yardages
  dim + flag); round-display-safe label placement; lighter APK. `CourseDataTest` parses every
  bundled course in CI.
- **Phase 11 — Tournament-ready (Setberg)** ✅ Survey **green-centre propagates across the shared
  physical green** (hole N ⇄ N+9); hole map **always opens on the vector view** (map style held
  in memory, not persisted); **WHS course handicap** from the course's slope/rating + a handicap
  **allowance** (95% default / 100%), so entering an index (15.7 → playing 16) gives correct
  net; **Stroke play / Stableford format toggle**; **"+N shot here"** handicap-stroke cue;
  **two-tap Reset confirm**. **Startup crash fixed** — course/history/round JSON now parse **off
  the main thread** (was blocking the UI ~11 s on the GW4 and getting the app killed; now cold
  ~5.5 s, warm ~0.5 s, never killed — verified on-watch over adb). Setberg CR 70.8 / Slope 130
  baked in from the official card. On-course polish: **interval-aware "stale"** GPS flag
  (mode interval + 8 s, not a flat 30 s), the **5 s ticker paused while the wrist is down**, and
  **active-round saves off the main thread** (serialised collector + `@Synchronized` file access
  + synchronous flush on pause, so the card can't jank the UI, race the file, or lose the last
  score). Course-picker list also loaded off-main.

## What still needs real-world verification

1. **Green front/back edges** — not in OSM; **approximated** (centre ±~11 m along the playing
   line) until captured in Survey mode. Centre distances are accurate now.
2. **Tee sets** — one tee per playing hole today; multiple sets (red/yellow/white/blue) later.
   OSM's tee for **Setberg H15** is a forward tee (~66–95 m short of the 56 markers vs the card's
   439 m), so its map tee marker / plays-like origin are off on that one hole (centre distance is
   GPS-based and unaffected). Survey-capture the 56 tee to correct it. Par/SI/lengths for all 18
   holes match the official card (tee 56); CR 70.8 / Slope 130 baked in.

(Par and stroke index are now verified against the official cards — no longer open.)

## Possible next steps (optional, only if wanted)

- **Round stats** over saved history (FW %, GIR %, putts, scoring average by par).
- **Shot-distance measure** (mark ball → walk → carry) to learn club distances; **club book**
  + suggestion tying into plays-like.
- Wear **Tile** with a glanceable centre-green distance.
- **Faster cold start** (~5.5 s on the GW4): needs a **non-debuggable release build** for a
  baseline profile to take effect (a baseline profile does nothing on the installed debug APK).
  A non-minified release (signed with the committed keystore) would get the AOT win without R8
  serialization risk — deferred as a post-tournament change to avoid altering the build type.
- History *saves* off the main thread too (active-round saves already are — Phase 11). Course/
  history/round *loading*, the course-picker list, the 5 s stale-tick (now resume-scoped), and
  the active-round save are all off the main thread / scoped as of Phase 11.
