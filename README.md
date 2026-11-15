# ClassicPod 4.0

Lightweight iPod Classic–style music player and optional Home launcher for the **OPPO A33f** (Android 5.0, 1 GB RAM).

ClassicPod does **not** bypass the lock screen. Android boots and unlocks as usual. After unlock, ClassicPod appears only if you set it as the default Home app.

Offline MP3 / local MediaStore playback only. No ads, no internet, no extra libraries.

## Architecture

```
ClassicPod/
├── settings.gradle
├── build.gradle                          Gradle 4.10.3 + AGP 3.3.3
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle                      minSdk 21, targetSdk 22, compileSdk 28
    └── src/main/
        ├── AndroidManifest.xml           HOME launcher + MusicService
        ├── java/com/classicpod/player/
        │   ├── ClassicPodApp.java        Repository, playlists, prefs
        │   ├── MainActivity.java         Menus, click-wheel, Home UI
        │   ├── ClickWheelView.java       iPod-style wheel
        │   ├── MusicService.java         Foreground playback, notification, MediaSession
        │   ├── MusicManager.java         MediaPlayer, queue, audio focus
        │   ├── MusicRepository.java      MediaStore scan
        │   ├── PlaylistManager.java      JSON playlists on internal storage
        │   ├── Song.java / Album.java / Artist.java / Playlist.java
        │   ├── Prefs.java                Shuffle, repeat, last queue
        │   ├── ArtLoader.java            Thumbnail LRU cache
        │   ├── MenuAdapter.java / SongAdapter.java
        │   ├── BootReceiver.java         No lock-screen bypass; no auto-play
        │   └── BecomingNoisyReceiver.java
        └── res/                          Black/white iPod layouts and drawables
```

**Why compileSdk 28 with targetSdk 22?**  
The phone still runs Lollipop. `targetSdk 22` keeps install-time storage permission (no Android 6 runtime dialogs). `compileSdk 28` is the SDK pack modern Android Studio still installs; **no APIs newer than 21 are required at runtime**. Java language level is 1.7 (no lambdas).

| Version | What shipped in this tree |
|---------|---------------------------|
| 1.0 | Local library + playback + dark UI |
| 2.0 | Click wheel, artwork, artists/albums |
| 3.0 | Background play, lock-screen/notification controls, playlists, shuffle/repeat |
| 4.0 | HOME launcher, Settings recovery, RAM-conscious scanning and art cache |

## Open in Android Studio

1. Install **Android Studio** (any version that can still import Gradle 4.10 / AGP 3.3 is ideal; if import fails, see “Modern Studio” below).
2. SDK Manager → install **Android 5.0 (API 21)**, **Android 8.1 (API 28)**, **Build-Tools 28.0.3**, and **Android SDK Platform-Tools**.
3. **File → Open** → `C:\Users\riddh\Projects\ClassicPod`
4. If asked to generate a Gradle wrapper, allow it.
5. Create `local.properties` in the project root (Studio usually does this):

```
sdk.dir=C:\\Users\\riddh\\AppData\\Local\\Android\\Sdk
```

Use your real SDK path.

### Modern Studio note

If a current Android Studio refuses AGP 3.3, keep `minSdk 21` and `targetSdk 22`, and only bump the plugin as far as needed. Do **not** add AndroidX, Kotlin, or Material libraries. Do **not** raise `targetSdk` above 22 on this phone unless you add runtime permission code for `READ_EXTERNAL_STORAGE`.

## USB debugging on OPPO A33f

1. Settings → **About phone** → tap **Build number** 7 times.
2. Settings → **Developer options** → enable **USB debugging**.
3. Connect USB. On the phone, allow the RSA fingerprint.
4. If the PC does not see the device: install the OPPO/Qualcomm USB driver, set USB mode to **MTP / File transfer**, and retry.
5. Confirm:

```
adb devices
```

You should see the A33f as `device`.

## Build and install

USB debugging on, then from a PC with the Android SDK `platform-tools` on PATH:

```
cd C:\Users\riddh\Projects\ClassicPod
```

In Android Studio: **Build → Build APK(s)** (debug) or **Build → Generate Signed APK**.

Command line (after Studio has created `gradlew.bat` / wrapper JAR):

```
gradlew.bat assembleDebug
```

APK path:

```
app\build\outputs\apk\debug\app-debug.apk
```

Install:

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Copy MP3s onto internal storage (Music folder is fine). Open ClassicPod → Settings → **Scan Library**.

## Make it the Home screen

1. Open ClassicPod (or press Home after install).
2. Android will ask **Choose Home app** → ClassicPod → **Always**.
3. Or ClassicPod → **Settings → Change Home App**.

After reboot: lock screen still works. After unlock, ClassicPod is Home.

### Restore the original OPPO launcher

ClassicPod → **Settings → Restore original launcher** (or **Change Home App**) → select **OPPO Launcher** / **Launcher**.

Emergency calling: **Settings → Emergency / Phone Dialer**. Full Android settings remain available from the same screen.

Firmware can still force a vendor Home app. ClassicPod cannot override that.

## Click wheel

- **MENU** (top) — back (does not exit Home; sends ClassicPod to the background)
- **Center** — select highlighted row
- **I<< / >>I** — previous / next track
- **PLAY** (bottom) — play / pause
- **Circle the ring** — move the list highlight (on Now Playing, seek)

Long-press a song → add to playlist.  
Long-press a playlist → delete.  
Long-press inside a playlist → add songs.  
Empty playlist + center click → add songs.

## Features

- MediaStore scan of local music (`IS_MUSIC`)
- Artists, albums, songs, playlists, recently played, search
- MediaPlayer queue, shuffle, repeat off/all/one, seek bar
- Album art via MediaStore album art, RGB_565 thumbnails, 1 decode thread, LRU cache
- Foreground service **only while playing**; audio focus; pause on headset unplug; incoming-call duck/pause via audio focus
- Lock-screen / headset transport via `MediaSession` (API 21)
- Notification prev / play / next
- Playback position and queue saved in SharedPreferences
- HOME / DEFAULT / LAUNCHER intent filters

## Testing on the A33f

1. Copy a handful of MP3s, scan, play from Songs. Confirm next song starts automatically.
2. Press Home from Settings: you should return to ClassicPod if it is default Home.
3. Play, press power, unlock: lock-screen media controls should appear.
4. Pull notification shade: ClassicPod transport.
5. Receive a call: music pauses or ducks; it should resume after AUDIOFOCUS_GAIN when the call ends.
6. Unplug headset: playback pauses.
7. Leave idle paused: notification may remain; the player is not looping animations.
8. Settings → Restore launcher, then Home: original OPPO UI.

## Troubleshooting

| Symptom | What to try |
|---------|-------------|
| No songs | USB file transfer MP3s to internal storage; Scan Library; grant storage if a custom ROM raised target SDK |
| `adb devices` empty | USB debugging, MTP mode, OEM driver, another cable |
| Install parse error | APK is for `minSdk 21`; confirm the A33f is Android 5.0 |
| No lock-screen controls | Some ColorOS builds hide them; notification controls still work |
| Home still OPPO | Change Home App again; ColorOS may reset after reboot |
| Stutter | Keep library modest; avoid huge album-art JPEGs; no other heavy apps in RAM |
| Gradle import fails | Install build-tools 28.0.3; or generate wrapper in Studio |

## Drawables

All icons are XML (layer-list, shape, vector). You do not need Photoshop. Optional: replace `res/drawable/ic_launcher.xml` with 48×48 mdpi PNGs in `mipmap-*` and point `android:icon` at them.
