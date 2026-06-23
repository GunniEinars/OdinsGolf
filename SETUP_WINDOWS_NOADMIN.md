# SETUP_WINDOWS_NOADMIN.md

How to build and install OdinsGolf on a **Galaxy Watch 4** from a **Windows laptop without
admin rights**. Two build paths; you only need one. Both share the same install steps.

You do **not** need an emulator — your real watch is the test device.

---

## Path A (recommended): build in the cloud with GitHub Actions

No compiler on your laptop at all.

1. **Create a GitHub repo** (free account) and upload this project.
   - Web upload works, but Git is easier. Portable Git needs no admin:
     https://git-scm.com/download/win → "portable" `.7z.exe`, extract to a user folder.
   - From the project folder:
     ```
     git init
     git add .
     git commit -m "OdinsGolf v0.1.0"
     git branch -M main
     git remote add origin https://github.com/<you>/OdinsGolf.git
     git push -u origin main
     ```
2. The workflow `.github/workflows/build.yml` runs automatically on push (also via the
   **Actions tab → Run workflow**).
3. Open the finished run → **Artifacts → `OdinsGolf-debug-apk`** → download → unzip to get
   `app-debug.apk`.
4. Install it with adb (see **Installing**, below).

> The cloud builder also runs the unit tests. If the build is red, the log tells you what failed.

---

## Path B: build locally with the Android Studio ZIP (no installer, no admin)

1. Download **Android Studio** as the **`.zip`** (not the `.exe`):
   https://developer.android.com/studio → "Download options" → the Windows **.zip**.
2. Extract to a user-writable folder, e.g. `C:\Users\<you>\android-studio`.
3. Run `android-studio\bin\studio64.exe`. When asked for the SDK location, point it at a
   user folder, e.g. `C:\Users\<you>\Android\Sdk`. The bundled JDK needs no admin.
4. **Open** this project folder. Android Studio will:
   - generate the Gradle **wrapper jar** automatically (it isn't committed here),
   - download the SDK packages it needs,
   - sync Gradle.
5. Build → **Build APK(s)**, or in the built-in terminal:
   ```
   .\gradlew.bat assembleDebug
   ```
   Output: `app\build\outputs\apk\debug\app-debug.apk`.

> Command-line only (no IDE) also works: a portable JDK 17 ZIP + the Android **command-line
> tools** ZIP + `sdkmanager` to install `platform-tools`, `platforms;android-34`,
> `build-tools;34.0.0`. Then `gradlew assembleDebug`. Path A is less hassle.

---

## Installing the APK on the Galaxy Watch 4

### 1. Get `adb` (no admin)

Download **SDK Platform-Tools for Windows** (a plain ZIP):
https://developer.android.com/tools/releases/platform-tools → extract to e.g.
`C:\Users\<you>\platform-tools`. `adb.exe` is inside. (If you used Path B, adb is already in
`...\Android\Sdk\platform-tools`.)

### 2. Enable Wireless Debugging on the watch

On the watch:
- Settings → **About watch → Software** → tap **Software version** 7× to unlock **Developer
  options**.
- Settings → **Developer options** → enable **ADB debugging** and **Wireless debugging**.
- Open **Wireless debugging** → note the watch's **IP:port**, and use **Pair new device** to get
  a 6-digit code + a (different) pairing **IP:port**.

Watch and laptop must be on the **same Wi-Fi**.

### 3. Pair and connect (in the platform-tools folder)

```
adb pair <pairing-ip>:<pairing-port>
# enter the 6-digit code shown on the watch

adb connect <watch-ip>:<port>
adb devices            # should list the watch as "device"
```

### 4. Install

```
adb install -r app-debug.apk
```

Launch **OdinsGolf** from the watch app list. Grant location when asked.

### Updating to a later build

CI signs every build with a committed stable keystore, so updates go on **in place** — no
uninstall, keeps your data:

```
adb connect <watch-ip>:<port>          # reconnect; the port changes after a reboot/toggle
adb install -r app-debug.apk
```

Tips:
- Pairing persists across Wi-Fi drops/reboots — you only `connect` again (the watch shows the
  computer under "Paired devices"). You'd only re-`pair` after a factory reset or revoking
  debugging authorizations.
- If you've connected a few times this session and adb says **"more than one device"**, target
  the watch explicitly: `adb -s <watch-ip>:<port> install -r app-debug.apk` (clear stale entries
  with `adb disconnect` first).
- A signature-mismatch error (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) only happens when moving
  between differently-signed builds — `adb uninstall com.odinsgolf` once, then install.

---

## Pulling files off the watch (survey points & exported rounds)

```
adb pull /data/data/com.odinsgolf/files/survey_setbergsvollur.json
adb pull /data/data/com.odinsgolf/files/rounds/
```

> If a direct `pull` is blocked on a non-rooted watch, use:
> `adb exec-out run-as com.odinsgolf cat files/survey_setbergsvollur.json > survey.json`

Fold captured coordinates into `app/src/main/assets/courses/setbergsvollur.json`
(see [COURSE_SCHEMA.md](COURSE_SCHEMA.md)) and rebuild so they ship in the APK.

---

## Troubleshooting

- **`adb` not recognized** — run it from inside the platform-tools folder, or add that folder to
  your user `Path` (no admin needed: search "Edit environment variables for your account").
- **Watch not listed** — re-check same Wi-Fi; Wireless debugging toggles off after reboot;
  re-`adb connect`.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** — `adb uninstall com.odinsgolf` then install again.
- **Gradle wrapper error locally** — open once in Android Studio (it creates the wrapper jar),
  or run `gradle wrapper --gradle-version 8.9` if you have a Gradle on PATH.
