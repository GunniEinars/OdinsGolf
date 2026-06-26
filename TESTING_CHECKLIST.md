# TESTING_CHECKLIST.md

## Automated (CI / local)

- [ ] `./gradlew testDebugUnitTest` passes (`GeoTest`, `ScoringTest`, `RoundModeTest`,
      `CourseDataTest` — the last parses every bundled course through the real DTO).
- [ ] `./gradlew assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] GitHub Actions run is green and uploads the APK artifact.

## Install (Galaxy Watch 4)

- [ ] Wireless Debugging paired; `adb devices` shows the watch.
- [ ] `adb install -r app-debug.apk` succeeds.
- [ ] App launches; icon appears in the app list.

## Permissions

- [ ] First launch shows the Location permission screen.
- [ ] Granting permission moves to the Distance screen and GPS starts.
- [ ] Denying shows the permission screen (no crash); re-grant works.

## Navigation (the 3-screen pager)

- [ ] Swipe left/right between **Distance → Card → Map** and back; smooth, no stickiness.
- [ ] From a pushed screen (More/Settings, Jump-to-hole) **swipe-right returns** to the dashboard.
- [ ] On the Distance page, a right-swipe does not accidentally exit the app.

## Distance screen (the glance)

- [ ] `H#·Par#` with ‹ › to change holes (persists after relaunch); big centre distance hero.
- [ ] Front/Back show numbers with a fix (approximate ±~11 m until captured); `—` with no fix.
- [ ] **Plays-like** appears (amber, with ↑/↓) only on holes that climb/drop ≥3 m.
- [ ] **Carry** lines show only for hazards ahead and within reach.
- [ ] **Stale honesty:** standing still >30 s dims the hero to grey and shows "stale fix".
- [ ] Missing geometry shows "Course geometry missing", **never a fake number**.

## Hole map (swipe to it)

- [ ] Vector hole is **playing-line-up** (tee bottom, green top): filled fairway/green/bunkers/
      water, pin flag, your dot, **150/100 (+250 on long holes)** rings; par 3 has no rings.
- [ ] Big distance top-right, hole # top-left, neither clipped by the round bezel; dims when stale.
- [ ] **Tap** toggles satellite ⇄ vector (satellite needs a connection once to cache tiles).
- [ ] Doglegs bend (centreline); no overlapping numbers.

## Scorecard (first swipe-left)

- [ ] Stroke stepper **opens on par** (dim); tap the number = par; +/- adjust; **"–" past 1 = PU**.
- [ ] Putts +/-; fairway chip only par 4/5 (–/✓/✗); GIR toggles.
- [ ] Out/In/Total + to-par correct; **Stableford correct** (verify a handicap stroke lands on
      the right SI hole); net shown when HCP > 0; PU scores 0 and shows "P".
- [ ] Survives relaunch (active round persisted). Reset clears. **Save card** writes a PNG to the
      watch Gallery with feedback; Save round → summary.

## More (Settings) + Jump to hole

- [ ] **More** opens the menu: Jump-to-hole, Course, Units (m/yd), GPS mode (battery warning on
      Precise), Play (18/Front/Back), Handicap, keep-screen-on, debug GPS, history, survey, reset.
- [ ] **Jump to hole** lists all holes (current highlighted) and returns to the dashboard on tap.
- [ ] Switching **Course → Kiðjabergsvöllur** loads it and persists.

## Survey mode

- [ ] Capture buttons disabled until a usable fix.
- [ ] Capturing FRONT/BACK makes those values appear on the Distance screen immediately.
- [ ] Capturing **green CENTER** updates **both** holes that share that green (e.g. Setberg
      hole 1 and hole 10) — the centre distance changes on both, not just the captured hole.
- [ ] `survey_setbergsvollur.json` is pullable via adb.

## Battery / lifecycle

- [ ] Lowering wrist / leaving app → GPS pauses (no drain).
- [ ] Raising wrist → quick refresh (burst) then steady spaced updates.
- [ ] A full 18-hole round completes without the phone and without killing the battery.
