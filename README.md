# OdinsGolf ⛳

A private, standalone **Wear OS** golf GPS app for the **Samsung Galaxy Watch 4**.
No phone, no account, no backend, no ads, no cloud. Raise your wrist → see distances →
enter your score → the watch goes quiet again.

First course: **Setbergsvöllur** (Golfklúbburinn Setberg, Hafnarfjörður, Iceland).

## What it does

- Front / **center** / back green distances (center is the hero number).
- Up to 3 hazard/target distances + green depth.
- GPS accuracy + status: `Live` / `Weak` / `Stale` / `Searching` / `Paused`.
- A lightweight vector **hole map** (tee, green, your dot, line to green) — no map tiles.
- A fast **scorecard**: strokes/putts/fairway/GIR, running totals, **to-par, Stableford and net** (Icelandic club style).
- **Survey mode** to capture real green/tee/hazard coordinates by walking the course.
- Battery-first GPS (spaced updates, pauses when not visible).

## The Setberg "9 played as 18" model

Setbergsvöllur is physically **9 holes played twice as 18** from different tees.
Hole *N* and hole *N+9* **share the same physical green** but have their **own tee, par and
stroke index** (e.g. hole 1 is par 5, replayed as hole 10 par 4; hole 8 is par 3, replayed
as hole 17 par 4). OdinsGolf models this correctly: 9 greens + 18 playing holes. See
[COURSE_SCHEMA.md](COURSE_SCHEMA.md).

## Data honesty

Tees, green centers, par and stroke index are real, derived from **OpenStreetMap**
(© OpenStreetMap contributors, ODbL). Green **front/back edges are not in OSM** — they show
`—` until you capture them in Survey mode. **The app never invents yardages from placeholder
coordinates.** See [DATA_SOURCES.md](DATA_SOURCES.md).

## Build & install (no admin rights needed)

You do **not** need to install Android Studio. The easiest path builds the APK in the cloud:

1. Push this project to a GitHub repo → **GitHub Actions** builds `app-debug.apk` automatically.
2. Download the APK artifact.
3. Install on the watch over Wireless Debugging with `adb` (a no-admin ZIP).

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

v0.1.0 — complete, buildable scaffold with real OSM geometry. Green front/back and exact
stroke index need one field-verification round (Survey mode). See PROJECT_PLAN.
