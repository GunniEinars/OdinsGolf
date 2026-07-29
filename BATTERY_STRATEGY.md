# BATTERY_STRATEGY.md

GPS is by far the biggest drain on a Galaxy Watch 4. The whole app is built around using it
sparingly while still being accurate when you glance.

## Core rules

1. **High accuracy, low frequency.** We always request `PRIORITY_HIGH_ACCURACY` (golf needs real
   GPS), but the update **interval** is the battery lever:

   | Mode | Interval | Min interval | Note |
   |---|---|---|---|
   | Battery saver | 25 s | 15 s | longest battery |
   | **Normal (default)** | 6 s | 3 s | fast settle; ≈ the old Precise |
   | Precise | 3 s | 2 s | warns: uses battery |

   The **foreground** interval only streams while you're actually looking (a few seconds per
   glance), so a tight cadence makes the yardage settle fast at almost no battery cost — the
   receiver is off (or lean-warm, in Play mode) the rest of the time.

2. **Stop when not visible.** `LocationEngine.pause()` removes updates on `ON_PAUSE`; the last
   fix is kept so resume is instant. Status flips to `Paused`.

3. **Burst on resume.** On `ON_RESUME` we restart periodic updates **and** fire one
   `getCurrentLocation(HIGH_ACCURACY)` so the screen refreshes immediately, then settles back to
   the spaced interval.

4. **Last-known immediately, marked stale.** A fix older than the mode's threshold (interval + 8 s,
   e.g. Normal 14 s) is shown as `Stale` (`GpsState.effectiveStatus`) so you never trust an old
   number without knowing.

5. **No wake locks. Let the watch sleep — but keep GPS warm while you play.** Play mode runs one
   foreground service that keeps the receiver *warm* so a wrist-raise reads live in ~1–2 s (often
   instantly) instead of a cold re-acquire. It is now **automatic** (default on): armed on each
   glance and self-stopping when idle — see below. Turning it off (**battery saver**) restores the
   old behaviour: no background service, receiver powers down between glances.

6. **Cheap rendering.** No continuous animations. The hole map (Compose Canvas) re-renders only
   when the hole or your position changes. A 5 s ticker refreshes the fix "age"/stale indicator,
   and it is **started on resume and stopped on pause** — so it never wakes the CPU or recomposes
   the screen while the wrist is down. The "stale" threshold is the GPS interval + 8 s (mode-aware).

7. **Keep-screen-on is OFF by default.** Optional toggle in Settings (`FLAG_KEEP_SCREEN_ON`) for
   walking to your ball; turn it off to save battery.

## Lifecycle wiring

`MainActivity` adds a `LifecycleEventObserver`:
`ON_RESUME → vm.onResume()` (start + burst), `ON_PAUSE → vm.onPause()` (pause updates — or, in Play
mode, `stop()` the activity's own updates while the service keeps GPS warm).
Permission is gated before any location request; denied → `PermissionScreen`.

## Play mode (automatic warm GPS)

Without a warm receiver, wrist-down (`ON_PAUSE`) powers the GPS **off**; walking to your ball it
goes cold, and the next wrist-raise pays a several-second re-acquire (`TTFF`) while the last fix
shows dimmed as `Stale`. On modern Android, background location is also throttled, so nothing short
of a **foreground service** can keep the receiver hot. That's the whole reason Play mode exists —
and because non-Play glances are structurally cold (there's no "warm it a little in the background"
the OS will allow), Play mode is **on by default** and automatic rather than something you must
remember to enable.

**Play mode** is the service `PlayModeService`:
- Runs `PRIORITY_HIGH_ACCURACY` at a **warm interval** (8 s / min 4 s — `LocationEngine.PLAY_*`).
  Keeping the receiver *powered* is what kills the cold start. The resume burst reuses a cached fix
  only if it's **very fresh** (`FRESH_ENOUGH_MILLIS = 2 s`), otherwise it computes fresh — because
  **accuracy beats speed on a glance**: an earlier 8 s window let a wrist-raise ride a stale/bad
  cached position and read a wrong yardage. A fix that's implausibly off-hole is flagged, not shown
  (`Distances.isOnHole`).
- Publishes to the shared `LocationBus`, same as the in-activity engine — the UI is agnostic to which
  one is driving. The activity still runs its own responsive updates while you're looking; the service
  only adds background warmth (so the foreground refresh is never slower than your GPS mode).
- Posts an ongoing foreground notification with a **Stop** action.
- **Auto-stops when idle** — 20 min with no wrist-raise (`PlayMode.lastActiveElapsedMillis`, stamped in
  `ON_RESUME`), so it never drains in your bag; it also clears the runtime flag on stop.
- On `ON_PAUSE` in Play mode the activity `stop()`s its own updates (no `Paused` flip); the service's
  fixes stay live. On `ON_RESUME` a burst fires immediately — warm, so it returns in ~1–2 s.

### Auto policy (Option A)

Two flags separate *policy* from *runtime* (`AppSettings`):
- **`autoWarmGps`** — the user-facing policy (Settings → **Play mode**; default **on**). Off = the
  frugal "battery saver" behaviour (GPS sleeps between glances).
- **`playMode`** — the runtime "service is warm right now" flag, managed automatically, not a toggle.

`RoundViewModel.onResume()` **re-arms** the service on every glance while `autoWarmGps` is on
(`setPlayMode(true)` when not already warm; it reads the *persisted* policy via `first()` so a
battery-saver user isn't briefly warmed during the launch race). The idle watchdog stops the service
after 20 min; the next glance re-arms it. Net effect: you never accidentally play a round cold, and
it still powers down when the watch is idle. The only cost of "on by default" is that opening the app
off-course starts the warm service (and its visible notification) for up to 20 min — bounded, and one
tap of **Battery saver** avoids it.

Battery: continuous GPS with the screen off is the app's biggest single draw, but a full charge
comfortably covers 18 — a field round with warm GPS + a fast GPS mode left **~45 % after 18 holes**,
which is what makes "on by default", the tighter warm interval (8 s) and the aggressive foreground
cadence affordable.

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
