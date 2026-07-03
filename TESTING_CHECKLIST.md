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
- [ ] **Hole length + SI** ("412 m · SI 8") shows under the header.
- [ ] Front/Back show numbers with a fix (polygon-derived edges, asymmetric per green); `—` with no fix.
- [ ] **Pin selector** (Front/Mid/Back, active highlighted) — one tap sets the pin; the hero number
      and its label follow it (Mid = centre), and plays-like tracks the pin too. Resets to Mid on relaunch.
- [ ] **Round score** shows once you've entered a score: "N pts · thru M" (Stableford) or
      "+/-N · thru M" (stroke play); hidden before the first score.
- [ ] **Nearest-hole hint:** with the watch left on the wrong hole, walking to another hole's
      green/fairway shows an amber **"⚑ At Hole N? tap to switch"** chip; tapping switches to it and
      the chip clears. It stays hidden while you're on the selected hole, and on a shared green it
      offers the hole in your current nine (front vs back), never nags to swap 1↔10. Only fires on
      a **live** fix (never from a stale/frozen position).
- [ ] **Plays-like** appears (amber, with ↑/↓) only on holes that climb/drop ≥3 m.
- [ ] **Carry** lines show only for hazards ahead and within reach.
- [ ] **"+N shot here"** (amber) shows on holes where your playing handicap gives you a stroke
      (stroke index ≤ playing handicap); absent on the holes you don't get one.
- [ ] **Stale honesty:** standing still >30 s dims the hero to grey and shows "stale fix".
- [ ] Missing geometry shows "Course geometry missing", **never a fake number**.

## Hole map (swipe to it)

- [ ] Vector hole is **playing-line-up** (tee bottom, green top): filled fairway/green/bunkers/
      water, pin flag, your dot, **150/100 (+250 on long holes)** rings; par 3 has no rings.
- [ ] Big distance top-right, hole # top-left, neither clipped by the round bezel; dims when stale.
- [ ] **Tap** toggles satellite ⇄ vector (satellite needs a connection once to cache tiles). The
      map **always opens on vector** — the satellite choice does not persist across app launches.
- [ ] Doglegs bend (centreline); no overlapping numbers.

## Scorecard (first swipe-left)

- [ ] Stroke stepper **opens on par** (dim); tap the number = par; +/- adjust; **"–" past 1 = PU**.
- [ ] Putts +/-; fairway chip only par 4/5 (–/✓/✗); GIR toggles.
- [ ] Out/In/Total + to-par correct; **Stableford correct** (verify a handicap stroke lands on
      the right SI hole); net shown when HCP > 0; PU scores 0 and shows "P".
- [ ] **Format toggle** (More → Format): Stroke play headlines **Net total + net-to-par**;
      Stableford headlines **points**. Gross Total + to-par shows in both.
- [ ] **Course handicap** (More → Handicap): off 15.7 at Setberg the screen shows **course 17 ·
      playing 16 (95%)**; toggling More → Hcp allowance to 100% shows **playing 17**. Net/Stableford
      use the playing number.
- [ ] **Reset is a two-tap confirm**: first tap shows "Confirm reset?", second tap clears; it
      auto-disarms after ~3 s. One stray tap can't wipe the card. (Same on More → Reset scorecard.)
- [ ] Survives relaunch (active round persisted). **Save card** writes a PNG to the
      watch Gallery with feedback; Save round → summary.

## More (Settings) + Jump to hole

- [ ] **More** opens the menu: Jump-to-hole, Course, Units (m/yd), GPS mode (battery warning on
      Precise), Play (18/Front/Back), Format (Stroke/Stableford), Handicap, Hcp allowance (95/100%),
      keep-screen-on, debug GPS, history, survey, reset.
- [ ] **Jump to hole** lists all holes (current highlighted) and returns to the dashboard on tap.
- [ ] Switching **Course → Kiðjabergsvöllur** loads it and persists.

## Survey mode

- [ ] Capture buttons disabled until a usable fix.
- [ ] Capturing FRONT/BACK makes those values appear on the Distance screen immediately.
- [ ] Capturing **green CENTER** updates **both** holes that share that green (e.g. Setberg
      hole 1 and hole 10) — the centre distance changes on both, not just the captured hole.
- [ ] **Captured points list:** each capture appears under "Captured · Hole #" with its accuracy
      and a **live distance from where you stand** (a green centre reads a few metres, not tens).
- [ ] **Delete (✕)** removes a point — capture a HAZARD twice, delete one, and only one remains.
- [ ] **Re-capture** TEE/FRONT/CENTER/BACK replaces the old one (still a single entry per kind).
- [ ] **Revert:** deleting a captured point restores the built-in distance for that feature;
      **"Reset to built-in points"** (two-tap confirm) clears all captures and restores shipped data.
- [ ] Deletions/edits survive relaunch (written back to `survey_<courseId>.json`).
- [ ] `survey_setbergsvollur.json` is pullable via adb.

## GPS honesty (the "stuck at 120 m" bug)

- [ ] Walking with the wrist down / after a signal drop, a **frozen** number **dims and shows
      "stale fix"** within ~20 s (Normal) — it never stays a confident live yardage while the map
      marker is stuck. Raising the wrist / moving refreshes it.

## Startup / performance (Galaxy Watch 4)

- [ ] **Cold launch opens (shows the emblem splash, then the app) and is never killed.** All
      course/history/round JSON parses off the main thread, so the UI thread isn't blocked.
      Measured on a GW4: cold ~5.5 s, warm/raise-wrist resume ~0.5 s (was ~11 s and getting
      killed when parsing ran on the main thread).
- [ ] Enter a score, fully close the app, reopen → score + current hole restored (persisted).

## Battery / lifecycle

- [ ] Lowering wrist / leaving app → GPS pauses (no drain).
- [ ] Raising wrist → quick refresh (burst) then steady spaced updates.
- [ ] A full 18-hole round completes without the phone and without killing the battery.
