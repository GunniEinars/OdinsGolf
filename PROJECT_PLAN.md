# PROJECT_PLAN.md

## Goal

A standalone Wear OS golf GPS app for one player on one (then a few) Icelandic courses.
Fast, outdoor-readable, battery-conscious, fully offline.

## Architecture

Single Activity → Compose for Wear OS, MVVM with one `RoundViewModel` exposing a single
`GolfUiState` via `StateFlow`. No DI framework, no Room, no background services.

```
MainActivity (permission gate + lifecycle)
  └─ OdinsGolfApp (SwipeDismissableNavHost)
       ├─ DistanceScreen   (hero center distance, F/B, hazards, GPS pill)
       ├─ HoleMapScreen    (Compose Canvas vector map)
       ├─ ScorecardScreen  (steppers, totals, Stableford/net)
       ├─ HoleSelectorScreen
       ├─ SettingsScreen
       └─ SurveyScreen     (field capture of coordinates)

RoundViewModel
  ├─ CourseRepository    (assets/courses/*.json → domain Course)
  ├─ SettingsRepository  (Preferences DataStore)
  ├─ ScorecardRepository (active round JSON in filesDir)
  ├─ SurveyRepository    (captured points + live overlay onto Course)
  └─ LocationEngine      (FusedLocationProvider, lifecycle-driven)

geo/   Geo (Haversine, bearing), CanvasProjector (cos-lat equirectangular), Distances
scoring/ Scoring (handicap allocation, Stableford, net, to-par)
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

## What still needs real-world verification

1. **Green front/back edges** — not in OSM. Walk each course once in Survey mode (capture
   FRONT/BACK on each green) or hand-edit the JSON. Until then those values show `—`.
2. **Stroke index** — OSM tags are slightly inconsistent (Setberg: holes 9 & 10 both SI 3,
   SI 10 missing; Kiðjaberg: holes 11 & 13 both SI 6, SI 4 missing). Correct from the printed
   scorecards. Par is correct (Setberg 72, Kiðjaberg 71).
3. **Tee sets** — one tee per playing hole today; multiple tee sets (red/yellow/white/blue)
   can be added later.

## Next up (high-value, low-risk)

1. **Rotary/bezel scrolling** for the Settings, Hole-selector and Course-picker lists
   (`rotaryScrollable`) — the biggest "feels native on Wear" gap.
2. **Round history** — completed rounds auto-saved to a scrollable list (date/course/score/Stableford).
3. **Persistence off the main thread** (score saves / course load on a background dispatcher).
4. **Scope the 5 s stale-tick** so the hole map doesn't recompose when nothing moved.

1. **Green front/back edges** — not in OSM. Walk the course once in Survey mode (capture FRONT/BACK
   on each green) or hand-edit the JSON. Until then those values show `—`.
2. **Stroke index** — OSM tags are internally inconsistent (holes 9 & 10 both SI 3; SI 10 missing).
   Correct from the printed Setberg scorecard in `setbergsvollur.json`. Par is correct (sums to 72).
3. **Tee sets** — OSM has 31 tee polygons; we currently use one tee per playing hole. Multiple tee
   sets (red/yellow/white/blue) can be added later.

## Future phases (later)

- Wear **Tile** with glanceable center-green distance.
- **Plays-like** elevation distance using the Galaxy Watch 4 barometer.
- Hazard carry/lay-up distances (front & back of hazard).
- Auto-advance to the nearest hole by GPS.
