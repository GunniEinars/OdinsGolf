# TESTING_CHECKLIST.md

## Automated (CI / local)

- [ ] `./gradlew testDebugUnitTest` passes (`GeoTest`, `ScoringTest`).
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

## Distance screen

- [ ] Course name + `H#·Par#` shown; ‹ › change holes and persist after relaunch.
- [ ] Center distance is the large hero number; updates as you move.
- [ ] Front/Back show `—` (until field-captured); depth hidden until both known.
- [ ] GPS pill shows `Searching → Live`, accuracy `±N m`, goes `Stale` after ~30 s standing still.
- [ ] Units chip toggles m/yd and all numbers convert.
- [ ] A hole with missing geometry shows "Course geometry missing", **never a fake number**.

## Hole map

- [ ] Tee (T), Green (G), your dot, dashed line to green render.
- [ ] North is up; not stretched sideways (cos-lat projection).
- [ ] Missing geometry → "No geometry for this hole", no crash.

## Scorecard

- [ ] +/- strokes and putts work; this-hole to-par correct.
- [ ] Fairway chip only on par 4/5; cycles –/✓/✗. GIR toggles.
- [ ] Out/In/Total and to-par correct; Stableford points correct; net shown when HCP > 0.
- [ ] Survives relaunch (active round persisted). Reset clears. Export writes a file.

## Hole selector

- [ ] All 18 holes listed with par; entered holes show ✓; current highlighted; tap jumps + returns.

## Settings

- [ ] Units, GPS mode cycle (with battery warning on Precise), handicap +/- (wraps at 54),
      keep-screen-on toggle, debug GPS toggle, source attribution + data-quality shown.

## Survey mode

- [ ] Capture buttons disabled until a usable fix.
- [ ] Capturing FRONT/BACK makes those values appear on the Distance screen immediately.
- [ ] `survey_setbergsvollur.json` is pullable via adb.

## Battery / lifecycle

- [ ] Lowering wrist / leaving app → GPS pauses (no drain).
- [ ] Raising wrist → quick refresh (burst) then steady spaced updates.
- [ ] A full 18-hole round completes without the phone and without killing the battery.
