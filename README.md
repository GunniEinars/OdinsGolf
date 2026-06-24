# OdinsGolf ⛳

A private, standalone **Wear OS** golf GPS app for the **Samsung Galaxy Watch 4**.
No phone, no account, no backend, no ads, no cloud. Raise your wrist → see distances →
enter your score → the watch goes quiet again.

First course: **Setbergsvöllur** (Golfklúbburinn Setberg, Hafnarfjörður, Iceland).

## What it does

- **Swipe navigation**: the on-course core is a 3-screen pager — **Distance ⇄ Map ⇄ Card** —
  flicked left/right (no bezel). Everything occasional sits behind one **More** chip.
- Front / **center** / back green distances (center is the hero number); the number **dims and
  flags "stale"** when the GPS fix is old, so it never looks live when it isn't.
- **"Plays like" distance** — elevation-adjusted (uphill plays longer), from a baked EU-DEM
  profile; shown only when the change is ≥3 m. Elevation only, no wind.
- **Hazard carry** distances — clear the bunker/water ahead, within reach of your shot.
- An offline **vector hole map**: the hole oriented playing-line-up (tee bottom, green top)
  with filled fairway/green/bunkers/water/tees, a pin flag, your position, **150/100 (and 250
  on long holes) distance-to-green rings**, and the playing line following the real centerline
  (doglegs bend). **Tap** to switch to a **satellite** layer and back.
- GPS accuracy + status: `Live` / `Weak` / `Stale` / `Searching` / `Paused`, with fix age,
  plus an optional live **debug GPS readout** (Settings → Debug GPS info).
- A fast **scorecard**: strokes start on par (tap to keep par; "–" past 1 = **pick up**),
  putts/fairway/GIR, running totals, **to-par, Stableford and net** (Icelandic club style),
  and a shareable round card PNG to the watch Gallery.
- **Decimal handicap index** (e.g. 15.7); allocation uses the rounded playing handicap.
- **Round modes**: 18 holes / Front 9 / Back 9.
- **Course picker** (Settings → Course): **Setbergsvöllur** + **Kiðjabergsvöllur**, more added as JSON.
- **Round history**: manually save rounds you want to keep to a scrollable list.
- **Survey mode** to capture real green/tee/hazard coordinates by walking the course.
- Battery-first GPS (spaced updates, pauses when not visible).
- App **emblem** as launcher icon + system splash; **bezel/rotary scrolling** on all lists.

## The Setberg "9 played as 18" model

Setbergsvöllur is physically **9 holes played twice as 18** from different tees.
Hole *N* and hole *N+9* **share the same physical green** but have their **own tee, par and
stroke index** (e.g. hole 1 is par 5, replayed as hole 10 par 4; hole 8 is par 3, replayed
as hole 17 par 4). OdinsGolf models this correctly: 9 greens + 18 playing holes. See
[COURSE_SCHEMA.md](COURSE_SCHEMA.md).

## Data honesty

Tees, green centers, hole centerlines, fairway/green/bunker/water shapes, par and stroke index
are real, derived from **OpenStreetMap** (© OpenStreetMap contributors, ODbL). Elevation is
**Copernicus EU-DEM** (via OpenTopoData); the satellite layer is **Esri World Imagery**. Green
**front/back edges aren't in OSM**, so they're **approximated** (green centre ±~11 m along the
playing line) and clearly labelled as such — a real Survey capture overrides them. The app
never copies data from any commercial golf-GPS database. See [DATA_SOURCES.md](DATA_SOURCES.md).

## Build & install (no admin rights needed)

You do **not** need to install Android Studio. The easiest path builds the APK in the cloud:

1. Push this project to a GitHub repo → **GitHub Actions** builds `app-debug.apk` automatically.
2. Download the APK artifact.
3. Install on the watch over Wireless Debugging with `adb` (a no-admin ZIP).

CI signs every build with a **committed stable debug keystore**, so after the first install,
updates go on with `adb install -r app-debug.apk` (no uninstall, keeps your data).

Full step-by-step for an admin-restricted Windows laptop is in
**[SETUP_WINDOWS_NOADMIN.md](SETUP_WINDOWS_NOADMIN.md)**.

Prefer a local build? Use the **Android Studio ZIP** (not the .exe installer) — also covered there.

## Tech stack

Kotlin · Jetpack Compose for Wear OS · Fused Location Provider · kotlinx.serialization ·
Preferences DataStore · Gradle Kotlin DSL + version catalog. Single module, no phone companion.

## Documentation

| File | What's inside |
|------|---------------|
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | Architecture, phases, what's done / next |
| [DATA_SOURCES.md](DATA_SOURCES.md) | Allowed/forbidden data, OSM attribution, Setberg research |
| [COURSE_SCHEMA.md](COURSE_SCHEMA.md) | Course JSON format + how to edit coordinates |
| [BATTERY_STRATEGY.md](BATTERY_STRATEGY.md) | GPS cadence, lifecycle, ambient TODO |
| [TESTING_CHECKLIST.md](TESTING_CHECKLIST.md) | On-watch test pass |
| [SETUP_WINDOWS_NOADMIN.md](SETUP_WINDOWS_NOADMIN.md) | Toolchain + sideload guide |
| [CHANGELOG.md](CHANGELOG.md) | Version history |

## Status

Running on a real Galaxy Watch 4. Two courses (Setberg, Kiðjaberg) with real OSM geometry
(centerlines, greens, fairways, bunkers, water) and EU-DEM elevation. Vector hole map with
satellite toggle, plays-like, hazard carry, handicap, round modes, course picker, round
history, and the scorecard (with pick-up) are all in. A unit test parses every bundled course
so a data/schema drift fails CI rather than the watch. Exact stroke index and surveyed
front/back edges are the remaining field-verification items. See [CHANGELOG.md](CHANGELOG.md).
