# Toolchain

Recorded in Phase 0 (2026-10-22). Large files are downloaded with the RDM GUI
(`rdm.exe /n /d <url> /p D:\dev\downloads`) so they can be tracked in its window;
checksums are verified after each download.

## Installed on the development PC

| Component | Version | Location | Source / checksum |
|---|---|---|---|
| Android Studio (zip, no installer) | Rabbit 1, 2026.2.1.8 | `D:\dev\android-studio` | `https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.2.1.8/android-studio-rabbit1-windows.zip` · SHA-256 `6e83e7ce3e0a76c4e8e78d98eb5ef538cbc9fb386c745bda425fc0bacb9de6d4` ✔ |
| JDK | JetBrains Runtime 25.0.3 (bundled with Studio) | `D:\dev\android-studio\jbr` | — |
| SDK command-line tools | 16111833 (`cmdline-tools;latest`) | `D:\dev\android-sdk\cmdline-tools\latest` | `https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip` · SHA-1 `57d04f2d75eb8e8fffc5000a987e5de4b5a63e9d` ✔ |
| Platform tools (adb) | 37.0.1 | `D:\dev\android-sdk\platform-tools` | via `sdkmanager` |
| SDK platform | android-36 (r02) | `D:\dev\android-sdk\platforms` | via `sdkmanager` |
| Build tools | 36.1.0 | `D:\dev\android-sdk\build-tools` | via `sdkmanager` |
| Gradle | 9.8.2 | wrapper cache (`%USERPROFILE%\.gradle\wrapper\dists`) | `https://services.gradle.org/distributions/gradle-9.8.2-bin.zip` · SHA-256 `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` ✔ (pre-seeded from the RDM download) |
| Emulator | 16428233 | `D:\dev\android-sdk\emulator` | `https://dl.google.com/android/repository/emulator-windows_x64-16428233.zip` · SHA-1 `488ed747e82de7e9bb5247becd1ac043c7e5e85d` ✔ |
| System image | Android 11 (API 30), Google APIs, x86_64, r16 | `D:\dev\android-sdk\system-images\android-30\google_apis\x86_64` | `https://dl.google.com/android/repository/sys-img/google_apis/x86_64-30_r16.zip` · SHA-1 `6ae21030eaadc041078444d3798e4b399f3e787d` ✔ |
| jadx (decompiler, for the head unit's vendor apps) | 1.5.6 | `D:\dev\jadx` | `https://github.com/skylot/jadx/releases/download/v1.5.6/jadx-1.5.6.zip` (RDM) |
| FFmpeg for Windows (test-library generator) | 9.0.2 essentials | `D:\dev\ffmpeg` | gyan.dev · SHA-256 `60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba` ✔ (RDM) |

Not installed yet: NDK + CMake (Phase 2).

Test music library: `python tools/make_test_library.py <dir>` (FLAC 16/44.1 and 24/96, MP3+ID3 covers, ALAC,
AAC, Vorbis, Opus, WAV, untagged accented names, folder.jpg, one WavPack file).

Tag-reader test fixtures: `python tools/make_tag_fixtures.py` regenerates `core/data/src/test/resources/fixtures`
(tiny files carrying the tags, covers, ReplayGain and lyrics that `TagReaderTest` checks).

Fonts: the bundled fonts are static weight instances cut from variable fonts with fontTools
(`pip install fonttools`, `instancer.instantiateVariableFont(font, {"wght": 700})`). Android 8.1 renders a
variable font at its default axis value (ExtraLight for Manrope), so never add a variable font as-is.

Screenshot tests: `gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` writes PNGs to
`app/build/outputs/roborazzi/`. Screens that animate continuously (Now Playing while playing) need the Compose
clock frozen (`mainClock.autoAdvance = false`) and stepped by hand — see `SkinsScreenshotTest`.

## Building

```powershell
$env:JAVA_HOME = "D:\dev\android-studio\jbr"
.\gradlew assembleDebug          # APKs in app\build\outputs\apk\debug\ (one per ABI + universal)
```

`local.properties` points at the SDK (`sdk.dir=D\:/dev/android-sdk`); it is not committed.
Opening the folder in Android Studio (`D:\dev\android-studio\bin\studio64.exe`) works too.

## Library versions

Pinned in `gradle/libs.versions.toml` to the latest stable releases on 2026-10-22, with
one deliberate exception:

- **Media3 1.9.4 instead of 1.11.1.** The prebuilt FFmpeg decoder
  (`org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1`) is compiled against Media3 1.9.0 and
  uses its internal decoder APIs, so the two must stay on the same minor version. When we build
  FFmpeg ourselves for the Tier 3 formats (Phase 6), Media3 can move to the latest release.

Static analysis (detekt) is deferred: detekt 1.23.x does not support Kotlin 2.4 yet. Android
Lint runs with the build.

## Emulator

Windows Hypervisor Platform is enabled (done 2026-10-22). The AVD
**`Cruise_HeadUnit_1280x720`** mirrors the owner's unit: 1280×720 landscape, 160 dpi, Android 11,
4 cores, 3 GB RAM. It was created by hand in `%USERPROFILE%\.android\avd` (the emulator and image
zips came through RDM, so `sdkmanager` does not list them); Android Studio's Device Manager shows it.

```powershell
.\scripts\run-emulator.ps1           # build, boot the emulator if needed, install, open Cruise
.\scripts\run-emulator.ps1 -NoBuild  # reinstall the last debug build
```
