# CHANGELOG

All notable changes to OdinsGolf. Format loosely follows Keep a Changelog.

## [Unreleased]

### Added
- **Shot / drive tracker.** On the Distance screen, ⚑ **Mark ball** at your position, walk to where
  it finished, and the screen shows the live distance you hit it — matched to the nearest club in your
  bag (`≈ 7-iron`) so you learn your real carries. In-memory per shot; clears on the next hole. Marks
  only with a live fix (you can't mark thin air). New `Caddie.nearestClub`, unit-tested.
- **Tap-to-measure on the hole map.** Tap any point to drop an aim marker: an amber shot line from
  where you'd play (your position, else the tee) with **"aim 210 · leaves 130"** — the carry to reach
  it and what it leaves to the green — plus a green line for the remainder. Long-press to clear; it
  also clears on a hole change. Great for laying up or picking a carry on a hole you're learning. The
  map projection is now invertible (`HoleProjection.unproject`), round-trip unit-tested to <1 m.
- **Wind arrow on the hole map.** When there's live wind, a compact badge shows an arrow pointing
  **downwind** (the way it pushes your ball), rotated from the real-world bearing into the play-line-up
  map frame, with the speed in m/s. Uses the same cached Open-Meteo reading as the caddie.
- **Green depth cue.** The Distance screen shows how deep the green plays front↔back (e.g. **"green 23 m
  deep"**) under the Front/Back yardages, so the club-up/down call is explicit. Intrinsic to the green,
  so it shows even before a fix.
- **Haptic "fix is live" tick.** A single subtle buzz the moment a fresh GOOD GPS fix lands (e.g. after
  a wrist-raise re-acquire), so you know the number is trustworthy without staring. Fires only on the
  transition into a good fix, and only while the screen is on — never in your pocket.
- **Offline caddie — club advice from your bag.** Enter your club carries once (More → My bag, seeded
  and editable ±5 m), and the Distance screen shows the club for the shot: it "takes enough club" to
  the **plays-like, wind-adjusted, pin-adjusted** yardage (the smallest club that carries it), e.g.
  `→ 8-iron`, or `· layup` when it's beyond your longest. On/near the tee of a par 4/5 it instead
  suggests a **position club** that leaves a full scoring iron (`Tee 5-iron · leaves ~150 m`).
  Deterministic, uses your verified course distances — no AI, no key. New `Caddie` engine + `Bag`
  model, unit-tested. A **Full bag / Iron play** toggle (My bag) tells the caddie whether Driver +
  3-wood are in play — in iron play it never recommends the woods and caps at your longest iron.
- **Weather-aware distances (Open-Meteo — free, no API key).** When you have signal it pulls current
  wind + rain for the course (cached, so it survives signal drops), and folds a **deterministic**
  head/tailwind + rain adjustment into the plays-like number and the club pick — with a cue like
  `↑ headwind 6 m/s · rain`. Nothing sensitive ships in the APK. New `Wind` model, unit-tested.
- **Play mode (warm GPS for instant distances).** Settings → Play mode (**automatic, on by default** —
  see Changed). A foreground
  service keeps the GPS receiver *warm* during a round, so raising your wrist at the ball reads a live
  distance in ~1–2 s instead of the several-second cold re-acquire you get when GPS powers down on
  every wrist-drop. The screen still sleeps between glances; only the receiver stays engaged, at a
  lean warm interval to keep the draw down (final tuning under Changed). **Auto-stops after 20 min idle**
  and offers a **Stop** action on its ongoing
  notification, so it never drains in your bag. A "▶ Play mode" tag shows on the Distance screen while
  active. Also: the resume **burst now reuses a just-produced fix** instead of always forcing a fresh
  compute, so quick glances are snappier.
- **Pin position (Front / Middle / Back)** on the Distance screen. A light one-tap "Pin Front·Mid·Back"
  selector (active target highlighted) sets today's pin and the **hero yardage tracks it** — Middle is
  the green centre (unchanged default),
  Front/Back use that hole's real polygon-derived edges. Front↔back can be a club or two on these
  greens, so you now club to the pin, not just the centre. Plays-like (elevation) follows the pin.
  Held in memory for the outing (resets to Middle on relaunch).
- **Round score on the glance.** The Distance screen now shows where you stand without swiping to
  the card: **"14 pts · thru 8"** in Stableford, **"+3 · thru 8"** (to-par) in stroke play. Hidden
  until you enter your first score.
- **Hole length + stroke index on the tee.** A dim "412 m · SI 8" under the hole header for tee
  strategy (length is the tee→green distance in your chosen units).
- **Nearest-hole hint** on the Distance screen: on a compact layout it's easy to leave the
  watch on the wrong hole and read a real, correct-looking yardage to the *wrong* green. When
  your GPS puts you clearly at another hole's corridor (≥40 m closer than the selected one), an
  amber "⚑ At Hole N? tap to switch" chip appears — a one-tap fix that never overrides manual
  control. Nine-aware on shared greens (at the 3rd/12th green it offers the hole in your current
  nine). New pure `HoleHint` (unit-tested); it simply never fires on spread-out courses.
- **Survey: review, verify and delete captured points.** The Survey screen now lists the current
  hole's captured points with their accuracy and a **live distance from where you stand**, so a
  mis-tagged point is obvious on the spot (a green centre should read a few metres, not 90). Each
  has a ✕ to delete — fixing the blind-capture problem (e.g. a hazard tapped twice). Re-capturing
  TEE/FRONT/CENTER/BACK still just replaces it. Captures overlay a separate file on top of the
  shipped data (never overwritten), so deleting a point **reverts it to the built-in one**, and a
  two-tap **"Reset to built-in points"** reverts everything — capturing is always safely undoable.
- **Handicap-stroke cue on the current hole**: the Distance and scorecard screens show
  "+1 shot here" (or "+2") when your playing handicap gives you a stroke on this hole
  (stroke index ≤ playing handicap) — quick net-play context on the tee and when scoring.
- **Scoring format toggle — Stroke play or Stableford** (More → Format), persisted. Both record
  gross strokes per hole; the scorecard headlines the chosen result — net total + net-to-par for
  stroke play, points for Stableford — with the gross total and to-par shown either way.
- **WHS course handicap from your index.** Enter your Handicap Index (e.g. 15.7) and the app
  derives the playing handicap for the course: Course Handicap = Index × Slope/113 + (CR − Par),
  then a **handicap allowance** (More → Hcp allowance, default **95%** — the WHS singles standard;
  toggle 100% for full course handicap). Setberg's official men's-tee ratings (CR 70.8, Slope 130)
  are baked in, so 15.7 → course 17 → playing 16 at 95%. The Handicap screen shows both numbers,
  and net/Stableford use the derived playing handicap. Courses without ratings fall back to the
  rounded index.
- **Offline vector hole map** (now the default): real OSM area polygons — fairway, green,
  bunkers, water, tees — drawn as filled shapes, fully offline and battery cheap. Pulled +
  simplified by `tools/bake_geometry.mjs` and baked per hole (`FEATURES_FROM_OSM`). The hole
  is oriented **playing-line-up** (tee bottom, green top) with **range rings** every 50 around
  you, a **pin flag**, a you-are-here marker, and a big distance-to-green with a plays-like
  arrow — a golf-watch hole view. Tap the map to switch to **satellite** and back (`MapStyle`;
  held in memory only — the map always opens on the vector view).
- **"Plays like" distance (elevation)**: uphill plays longer, downhill shorter, from a baked
  **EU-DEM** elevation profile sampled along each hole (your ground height read at your
  projected position — no flaky GPS altitude, no wind guesswork). Shown only when the change
  is ≥3 m so it's never noise. Elevation only, deliberately **not** wind.
- **Hazard carry distances**: "carry Water 178" / "carry Bunker 142" for hazards ahead on the
  line of play, computed from the bunker/water polygons. Shown on the Distance screen and map.
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
- **Satellite base map** under the hole schematic: Esri World Imagery tiles in Web-Mercator,
  downloaded on demand and cached to disk (view a hole once with connectivity and it works
  offline after). Falls back to the schematic on the dark background when offline.
- Hole map enriched: green drawn as a body with front/back edge dots, hazards larger and
  numbered, white playing line + your-position marker over imagery, and a live
  Front/Centre/Back yardage strip along the bottom.
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
- **Play mode is now automatic and on by default (was opt-in).** Non-Play glances are *structurally*
  cold — the OS won't let GPS stay warm in the background without a foreground service — so leaving
  warm GPS as a toggle you had to remember meant a forgotten tap = a whole round of slow cold glances.
  Now warm GPS **re-arms itself on every glance while you play** and still auto-stops after 20 min idle;
  you never accidentally play cold. Turning the toggle off is now **battery saver** (GPS sleeps between
  glances, the old behaviour). Internally split into a user *policy* (`autoWarmGps`, default on) and a
  *runtime* flag (`playMode`); `onResume` arms the policy, the idle watchdog clears the runtime flag.
  Trade-off: opening the app off-course now warms GPS (with its visible notification) for up to 20 min —
  bounded, and one tap of Battery saver avoids it. Justified by the ~45 % post-round battery headroom.
- **Snappier live distance (GPS retuned after round 2).** The number now settles noticeably
  faster after a wrist-raise, focused on the ~180 m-and-in range you actually check. Foreground
  cadence — which only streams during the seconds you're looking, so it barely touches battery —
  was tightened: **Normal 12 s/6 s → 6 s/3 s** (now ≈ the old Precise) and **Precise 5 s/3 s →
  3 s/2 s** (quicker still). In **Play mode** the warm interval dropped **20 s/10 s → 8 s/4 s** and
  the resume burst now **reuses a warm fix up to 8 s old** (was 4 s), so a glance is usually instant
  rather than paying a fresh compute. Honesty is unchanged — a genuinely aged fix still dims and
  flags stale (Normal threshold 14 s). Plenty of battery headroom (45 % left after 18 in testing).
- **Faster, cleaner startup.** Cold start re-parses the bundled course JSON (all the polygons +
  elevation) into memory, which took a couple of seconds during which the Distance screen looked
  half-drawn (bare "—" + a stray More button). Now: (1) a clean **"Loading course…"** placeholder
  covers that window, and (2) an **essentials-first** load parses just the distance data
  (tee/green/par/SI) first so the numbers appear almost immediately, then the heavy geometry (map,
  elevation, real front/back) fills in behind it. The full parse stays authoritative — the fast path
  is best-effort and can never break loading.
- **"Next hole" on the scorecard jumps back to the Distance screen.** After entering a score, tapping
  Next hole now advances the hole *and* slides to that hole's yardage — no more scrolling up and
  swiping back. The card also resets to the top on a hole change, so returning to it lands on the
  stroke stepper.
- **Real green front/back from the green polygon.** Front/Back yardages were synthesised as a flat
  centre ±11 m. They're now derived from the OSM green **polygon** — projecting its outline onto the
  tee→centre line for the true near/far edges — giving realistic, asymmetric depths (~17–33 m at
  Setberg, matching a field survey) instead of a fixed 22 m. Falls back to the old estimate only when
  a hole has no green polygon; a real Survey capture still overrides. New `GreenEdges` helper.
- **Setberg green data field-verified (2026-07-02).** A survey walk confirmed the OSM green centres
  are accurate — G1/G3/G4/G5 matched to within GPS accuracy (~4 m mean, no systematic shift), so no
  coordinates were changed. G2 is flagged in the course notes for a clean re-capture.
- **Navigation is now a 3-screen swipe pager**: the on-course core is **Distance ⇄ Card ⇄
  Map**, swiped left/right (no bezel needed). Card is the first swipe-left (you score every
  hole); the map is one further. Everything occasional — course, units, GPS mode, handicap,
  round history, survey, jump-to-hole — moved behind a single **More** chip (Settings). The
  **Distance screen was slimmed to one glanceable page** (hero centre distance, front/back,
  plays-like, carry, GPS), dropping the old chip rows.
- **Map dogleg label removed**: the corner's "tee→corner / corner→green" number (e.g. 245/160)
  was correct but cryptic and overlapped the ring labels. The bent playing line shows the
  dogleg shape and the rings give the distances, so the corner is now just a subtle aim dot.
- **Stale-fix honesty**: the hero distance (and the map's big number) **dims and shows
  "stale"** when the GPS fix is older than 30 s, so an out-of-date yardage never looks live.
- **Opening splash**: a single system splash showing the OdinsGolf **emblem** (wordmark
  cropped off, `odins_emblem.png` via `tools/crop_emblem.mjs`) on white, held ~0.65 s. The
  emblem is roughly circular so it fills the splash's circular mask and renders big and
  uncropped — unlike the square logo+wordmark, which the mask shrank to fit. No Compose splash
  afterwards, so there is no second logo and no double take.
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

### Fixed (round 3 regression — unusable distances/map)
- **Wrong yardages and a hole squished into the corner.** After a round where every hole read
  hundreds of metres off and the map wasn't centred, root-caused to a bad GPS position feeding both
  the number *and* the map framing at once. Three fixes:
  - **The map now frames on the hole only** (tee/green/features/centerline), never on your position,
    so a wrong fix can't drag the hole into a corner — it stays centred and tap-to-measure keeps working.
  - **Glances fetch a fresh fix instead of a stale cached one.** The earlier warm-GPS retune let a
    wrist-raise reuse a fix up to 8 s old; in marginal signal that could ride a bad position. Cut to
    2 s (`FRESH_ENOUGH_MILLIS`) — a reused fix is essentially current, otherwise it computes fresh.
  - **An off-hole fix is now flagged, not trusted.** A new `Distances.isOnHole` guard rejects a fix
    that puts you materially farther from the green than the tee is (impossible on a real hole): the
    hero blanks to "—" with **"weak GPS · move & wait"** instead of a confident wrong number, on both
    the Distance screen and the map. Unit-tested; generous margin so normal play never trips it.
  - **"Location off" is now surfaced.** If location services are switched off system-wide (found off
    on the watch — it can revert on a reboot, and a degraded/network-only state gives wildly wrong
    positions), the app showed only an endless "Searching". It now detects it
    (`LocationEngine.isLocationEnabled` → `GpsStatus.LOCATION_DISABLED`) and shows a red **"Location
    off — turn it on"** instead of pretending to search.

### Fixed (course data)
- **Missing fairway bunkers on shared holes.** Setberg plays 9 fairways twice (1/10, 2/11, … 9/18),
  but the geometry bake had filed each shared bunker under only one hole of the pair — so e.g. hole 1's
  map was missing the mid-fairway bunker (OSM way 790838114) that plays from both tees. Every bunker is
  now **shared across each paired hole** (deduped; real OSM polygons, tees untouched): 8 bunkers added
  across 8 holes, leaving each pair symmetric. See DATA_SOURCES.md.

### Removed
- **Satellite hole-map layer (and its tap-to-toggle).** The offline **vector** hole map proved good
  enough on the watch, so the Esri "World Imagery" tile layer is gone — with it `TileRepository`,
  `SlippyMap`/`MapPlan`, the `MapStyle` model and the persisted map-style setting. The map is now
  purely offline (no imagery to fetch or cache) and the app is lighter. `INTERNET` stays (weather
  still uses it); the hole map no longer needs a connection at all.

### Fixed
- **Map big number no longer shows a tee→green length as if it were live.** With no GPS fix the
  hole-map hero used to display the static tee→green distance (e.g. "409") in bright white next to
  "waiting for GPS". It now blanks to a dimmed **"—"** until there's a live fix — matching the
  Distance hero's honesty.
- **Caddie no longer names a club when you're on/next to the green.** Below **35 m** (chipping/
  putting range) the club suggestion (`→ 56°`) is suppressed — it's meaningless when you're not
  clubbing to a number.
- **Saved scorecard clipped on the round display.** The shareable card is square but the watch shows
  it round (and the gallery round-crops it), so the edge labels/scores fell outside the circle
  ("OUT" → "UT", outer holes cut off). The card is now laid out **round-safe** — everything sits
  inside the inscribed circle — and the on-screen round summary's nine-row got the same extra inset.
- **Jumping distances on wrist-raise.** On a raise (especially Play mode + Precise) several GPS
  sources fire at once and the receiver's first fixes after refocusing are often low-accuracy, so the
  number bounced (a good reading → a wild spike → back). All fixes now pass through an accuracy-aware
  filter (`isBetterFix`) on the shared `LocationBus`: a good recent fix isn't replaced by a much
  less-accurate one, while genuine movement (a clearly newer fix) is always taken so it never sticks.
  Steadier, and closer to truth. Unit-tested.
- **Nearest-hole hint now only fires on a live fix.** It was computed from the last position even
  when that fix was stale/frozen — which could suggest switching to the wrong hole. It's suppressed
  unless the fix is live (the same trust rule as the hero yardage).
- **Stale/cached GPS fix could masquerade as live ("stuck at 120 m greenside").** Fixes were aged
  from when the app *received* them, so a fused/cached "last-known" fix (GPS lost lock) arrived
  looking fresh and the number stayed confident while the position was frozen on the map. Fixes are
  now aged by the GPS's own clock (`Location.elapsedRealtimeNanos`), so a stale/stuck fix correctly
  ages past the threshold, **dims and flags "stale"** instead of showing a wrong live yardage.
- **Interval-aware "stale" GPS flag.** A fix now counts as stale after the update interval + 8 s
  (Normal → 20 s, not a flat 30 s), so a fix that aged while you walked to the ball with the
  wrist down flags as "refreshing" instead of briefly masquerading as live — while a live fix
  arriving on schedule never false-dims. `GpsUpdateMode.staleAfterMillis`, surfaced via
  `GolfUiState.gpsStatus`.
- **The 5 s stale/age ticker is paused while the wrist is down.** It now starts on resume and
  stops on pause, so it can't wake the CPU (or recompose the screen) while the app isn't
  visible — a small battery win. Resume also refreshes the age immediately.
- **Active-round saves moved off the main thread.** Each score tap wrote the round JSON on the
  UI thread; now a single off-main collector persists round changes (StateFlow-conflated and
  serialised — no concurrent writes), the card is flushed synchronously on pause so a kill can't
  lose the last score, and `ScorecardRepository` read/write is `@Synchronized` so nothing can
  corrupt the file.
- **Fixed a slow-startup crash on the watch (Galaxy Watch 4).** Course JSON (~120 KB each),
  round history and the active round were parsed on the **main thread** at launch — on the
  GW4's CPU that blocked the UI thread for ~10 s, long enough for the system to kill the app
  ("won't open"). All of it now parses on a background dispatcher: the course picker list is
  lazy (parsed on first open, not at launch), history loads asynchronously, and `loadCourse`
  does its parse in `Dispatchers.Default`, publishing only the results back to the UI. The
  course-picker list also loads off the main thread (it previously parsed both course files on
  the UI thread, which would freeze the picker for a few seconds on the watch). Verified
  on the watch over adb: cold launch ~11 s → **~5.5 s** (shows the branded splash, never frozen,
  no longer killed), warm / raise-wrist resume **~0.5 s**, and zero ANR/kill entries in logcat.
- **Reset needs a confirm.** The scorecard/Settings "Reset" now arms on the first tap
  ("Confirm reset?") and only wipes the card on a second tap (auto-disarms after 3 s), so an
  accidental tap can't destroy an in-progress round.
- **Hole map always opens on the vector view.** The base-layer choice is no longer persisted:
  an accidental full-screen tap could previously switch to satellite and *stay* there across
  sessions (and show a blank map when offline). The style now lives in memory (`RoundViewModel`,
  default `VECTOR`); tapping still switches to satellite for the current outing, but every fresh
  launch returns to the reliable offline hole view.
- **Surveyed green centre now applies to the shared physical green.** Setberg plays 9 greens
  as 18 holes (hole N and N+9 share a green); a Survey CENTER capture on one hole now also
  updates its sibling, matching the shared-green model instead of only the captured hole. The
  domain `Hole` carries its `greenId`, and `SurveyRepository.overlayOnto` propagates a centre
  capture by greenId (newest wins; a hole's own capture still takes priority). Covered by
  `SurveyOverlayTest`.
- **Stroke index verified** against the official scorecards (Rástímar) for both courses, fixing
  an OSM mistag on each that skewed Stableford/net allocation: **Setberg hole 9** is SI 10 (was
  SI 3, duplicating hole 10) and **Kiðjaberg hole 13** is SI 4 (was SI 6, duplicating hole 11).
  Both now have a complete, unique 1–18 stroke index; flag is `STROKE_INDEX_VERIFIED`.
- **Map label overlap & round-display clipping**: the hole is oriented green-at-top, so the
  hole number sits top-left and the big distance top-right, flanking the green. Both are
  dropped out of the round display's clipped top corners into the wider band (and inset from
  the edge) so the full distance number is never cut off; all map overlay text has a shadow for
  legibility over the bright vector fills. Added a **250
  distance-to-green ring** on long holes (150/100 still on others, none on par 3). Dropped dead
  code (`Distances.toHazards`) and **downscaled the splash emblem 950→475 px (878 KB→242 KB)**
  to keep the app light.
- **Course failed to load ("Could not load course … Unexpected JSON token … $.holes[0].path[0]")**:
  baking centerlines wrote `path` as compact `[[lat,lon]]` arrays while the parser still
  expected `{lat,lon}` objects, so the whole course failed to parse. `path` now parses as
  `[[lat,lon]]` (matching feature rings). Added a unit test (`CourseDataTest`) that parses every
  bundled course through the real DTO pipeline, so a JSON⇄DTO schema drift fails CI instead of
  the watch.
- **Vector map accuracy**: features are now assigned to the hole whose **OSM centerline**
  they sit on (tight, mostly single-hole; shared greens/fairways attach to both holes that
  play them), so parallel holes no longer bleed in and on-line bunkers aren't missed. The
  playing line **follows the centerline** (doglegs bend), with the dogleg corner marked and
  tee→corner / corner→green distances shown. Range rings reduced to **150 & 100 to the green**
  (none on par 3). Hazard **carry** now only shows hazards on the corridor **and within reach**
  (≤240 m), so a far greenside bunker no longer shows a meaningless tee "carry"; the generic
  nearest-hazard list was dropped from the Distance screen (the map shows hazards visually).
  Off-line/ornamental water (e.g. Setberg's left pond) no longer attaches to a hole. Verified
  the EU-DEM plays-like deltas against a second model (ASTER) — they agree, incl. Kiðjaberg
  h14's real ~+39 m climb.
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
