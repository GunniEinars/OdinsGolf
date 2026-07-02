# BATTERY_STRATEGY.md

GPS is by far the biggest drain on a Galaxy Watch 4. The whole app is built around using it
sparingly while still being accurate when you glance.

## Core rules

1. **High accuracy, low frequency.** We always request `PRIORITY_HIGH_ACCURACY` (golf needs real
   GPS), but the update **interval** is the battery lever:

   | Mode | Interval | Min interval | Note |
   |---|---|---|---|
   | Battery saver | 25 s | 15 s | longest battery |
   | **Normal (default)** | 12 s | 6 s | good balance |
   | Precise | 5 s | 3 s | warns: uses battery |

2. **Stop when not visible.** `LocationEngine.pause()` removes updates on `ON_PAUSE`; the last
   fix is kept so resume is instant. Status flips to `Paused`.

3. **Burst on resume.** On `ON_RESUME` we restart periodic updates **and** fire one
   `getCurrentLocation(HIGH_ACCURACY)` so the screen refreshes immediately, then settles back to
   the spaced interval.

4. **Last-known immediately, marked stale.** A fix older than 30 s is shown as `Stale`
   (`GpsState.effectiveStatus`) so you never trust an old number without knowing.

5. **No wake locks. No background service. Let the watch sleep.** Scorecard entry needs no GPS.

6. **Cheap rendering.** No continuous animations. The hole map (Compose Canvas) re-renders only
   when the hole or your position changes. A 5 s ticker refreshes the fix "age"/stale indicator,
   and it is **started on resume and stopped on pause** — so it never wakes the CPU or recomposes
   the screen while the wrist is down. The "stale" threshold is the GPS interval + 8 s (mode-aware).

7. **Keep-screen-on is OFF by default.** Optional toggle in Settings (`FLAG_KEEP_SCREEN_ON`) for
   walking to your ball; turn it off to save battery.

## Lifecycle wiring

`MainActivity` adds a `LifecycleEventObserver`:
`ON_RESUME → vm.onResume()` (start + burst), `ON_PAUSE → vm.onPause()` (pause updates).
Permission is gated before any location request; denied → `PermissionScreen`.

## Ambient / always-on — deferred (TODO)

v1 intentionally does **not** implement always-on ambient mode. For golf you raise your wrist,
glance, and drop it — the normal interactive→sleep cycle is the most battery-friendly and avoids
the complexity/drain of an always-on render loop.

To add it later:
- Add `androidx.wear:wear` `AmbientLifecycleObserver` (dependency already present).
- On entering ambient: stop GPS updates, render a minimal frozen view (hole #, last center
  distance, stale flag, score to par), no color/animation.
- Update at most once per minute in ambient; never run high-frequency GPS in ambient.
- The current pause/resume split is structured so this drops in without reworking the engine.
