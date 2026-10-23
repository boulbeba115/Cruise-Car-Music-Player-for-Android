<div align="center">

# Cruise

**An offline music player built for the car, not for the phone.**

Folder-first library from your USB stick · a sound engine tuned for car cabins · eight skins that change the whole look ·
offline voice commands · whole-screen gestures · steering-wheel and ignition aware.

![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer%20%2B%20FFmpeg-FF6F00)
![Offline](https://img.shields.io/badge/works-100%25%20offline-2EA44F)
![Tests](https://img.shields.io/badge/tests-passing-2EA44F)
![Voice](https://img.shields.io/badge/voice-offline%20EN%20%2B%20FR-8A7CFF)

[**Download the latest APK**](../../releases/latest) · [Features](#features) · [Skins](#eight-skins) · [Install](#install) · [Build](#build-from-source)

<img src="docs/images/skin-midnight.jpg" alt="Cruise Now Playing in the Midnight skin" width="900">

</div>

---

## Why Cruise

The stock players on Android head units are phone apps squeezed onto a dashboard: small targets, a flat song list,
no idea that the power is cut every time the engine stops. Cruise starts from the car:

- **Your music is a folder tree on a USB stick** — browse it as folders *or* as a proper library, both always in sync.
- **The driver only glances** — large type and touch targets, the most-used controls in the thumb zone, blind mode.
- **The power goes away** — it pauses when the ignition goes off, resumes on the same second when the car starts,
  and survives a full shutdown.
- **The sound should be as good as software can make it** — a clip-free DSP chain inside the player, with presets
  for real car cabins.
- **No internet** — nothing is streamed, nothing is uploaded, no account.

Developed and tested on a Renault Mégane III with an aftermarket "TS7" Android head unit (UNISOC SC7731E, Android 8.1,
1 GB RAM, 1280×720), but the UI is responsive for every screen from phones to ultra-wide bar displays.

## Features

### Playback
- Media3 / ExoPlayer with the FFmpeg extension: **MP3, FLAC (up to 24-bit/192 kHz), ALAC, AAC/M4A, Ogg Vorbis, Opus,
  WAV, MKA/WebM, AMR, MP2** — gapless.
- Background playback with a media session: notification, lock screen, steering-wheel and Bluetooth media keys.
- Queue and position saved continuously; resumes after a power cut, a reboot or a long sleep.
- Pause shortly after the ignition goes off (adjustable), resume when it comes back on.
- Smooth fades on play, pause and skip.

### Library
- Fast incremental scanner (only changed files are re-read) with pure-Kotlin tag readers for ID3v1/v2.2–2.4, FLAC and
  Vorbis comments, Ogg/Opus, MP4/ALAC atoms and WAV INFO.
- Albums, artists, genres, years, tracks, **quality** (Hi-Res / Lossless / Lossy), recently added, most played,
  favourites — each with its own sorting, plus an A–Z scrubber.
- Understands real-world sticks: `Artist/1973 - Album` folders, `CD1/CD2` discs, compilations, untagged files,
  `cover.jpg`/`folder.png`, CUE sheets as separate tracks, M3U/M3U8/PLS playlists (even ones written on a PC with
  `E:\Music\…` paths).
- **Accent-insensitive search** — "ete" finds "Été", "cafe" finds "Café".
- Playlists: create, reorder, save the queue as a playlist, export to `.m3u8` on the stick.

### Sound engine
- 10-band graphic EQ with a live spectrum and response curve, presets including **Mégane cabin** and **Highway**.
- **Automatic headroom** — boosts never clip; a look-ahead limiter catches the rest.
- Volume-dependent loudness compensation, bass shelf, stereo width, balance.
- **Driver focus** — time-aligns and balances the door speakers toward the driver (or the passenger).
- ReplayGain (track / album / automatic), TPDF dither, hold-to-compare A/B.
- Effects off = bit-exact passthrough (verified by unit tests).

### In the car
- **Whole-screen gestures on Now Playing** — no overlay, taps untouched: *hold* anywhere to play/pause (a ring fills
  under your finger), *hold and slide* to scrub, *swipe* left/right for tracks, up/down for volume.
- **Offline voice commands** in English and French: "Play Fairuz", "Play the album …", "… by …", "Shuffle my
  favourites", "Next", "Louder" / « Joue Stromae », « Mets l'album … », « Suivant », « Plus fort ». Names are matched
  by *sound*, so misheard or non-English names still find the right song.
- Synced lyrics from `.lrc` files or embedded tags, track info sheet, queue panel.
- **Blind mode** — the whole screen becomes one gesture pad.
- Night skin that switches with the headlights (on head units that broadcast it).
- **Steering wheel owned by Cruise** on "TW" head units: the phone button stops opening the Bluetooth screen and does
  what you choose (play/pause by default, calls still answered); remap any button, including a VOICE button.
- **Default music app**: answers the unit's MUSIC key (when the unit allows it), Android's music-player requests,
  audio files opened from a file manager and assistants' "play …".
- Backup and restore of every setting, playlist, favourite and play count to the USB stick.
- English and French.

## Eight skins

A skin changes everything: colours, typefaces, shapes, the animated backdrop, how the cover is presented, the animated
music bar and the navigation. Covers are drawn as objects that live with the music. Every skin passes WCAG AAA
contrast for primary text.

| | | |
|:---:|:---:|:---:|
| <img src="docs/images/skin-midnight.jpg" width="280"><br>**Midnight** — record sleeve, the disc slides out and spins | <img src="docs/images/skin-tape.jpg" width="280"><br>**Tape Deck** — cassette, the tape winds with the song | <img src="docs/images/skin-aurora.jpg" width="280"><br>**Aurora** — a living liquid cover |
| <img src="docs/images/skin-daylight.jpg" width="280"><br>**Daylight** — die-cut sticker with a peel corner | <img src="docs/images/skin-neon.jpg" width="280"><br>**Neon Drive** — synthwave grid, neon frame | <img src="docs/images/skin-amber.jpg" width="280"><br>**Amber Cockpit** — tachometer cover, LED meter |
| <img src="docs/images/skin-vinyl.jpg" width="280"><br>**Vinyl Lounge** — turntable with a swinging tonearm | <img src="docs/images/skin-oled.jpg" width="280"><br>**OLED Pure** — true black, lightest on the CPU | <img src="docs/images/settings-appearance.jpg" width="280"><br>Live previews in Settings |

## Screenshots

| Library | Synced lyrics | Sound |
|:---:|:---:|:---:|
| <img src="docs/images/library.jpg" width="280"> | <img src="docs/images/lyrics.jpg" width="280"> | <img src="docs/images/sound.jpg" width="280"> |
| **Search** | **Queue** | **Phone, portrait** |
| <img src="docs/images/search.jpg" width="280"> | <img src="docs/images/queue.jpg" width="280"> | <img src="docs/images/phone-portrait.jpg" width="200"> |

## Install

1. Download from [Releases](../../releases/latest):
   - `cruise-<version>-armeabi-v7a.apk` — most head units (32-bit, e.g. UNISOC SC7731E, Allwinner, older MediaTek).
   - `cruise-<version>-arm64-v8a.apk` — 64-bit units and phones.
   - `cruise-<version>-universal.apk` — if you are not sure (larger).
2. Copy it to a USB stick, open it with the unit's file manager and allow installing from unknown sources.
3. Open Cruise and allow access to your files (Android 11+: *All files access*). The library fills itself in the background.
4. Optional, for voice commands: download the [Vosk small models](https://alphacephei.com/vosk/models)
   `vosk-model-small-en-us-0.15` and/or `vosk-model-small-fr-0.22`, unzip them into `Cruise/voice/` on the USB stick
   and tap the microphone once — Cruise installs them and works fully offline from then on.
5. On TW head units: Settings → Steering wheel → *Make Cruise the music app*, and optionally re-learn wheel buttons in
   the unit's own Steering Wheel app (VOICE, PLAY/PAUSE, NEXT…).

Requires Android 6.0 or newer. Works without Google Play services.

## Build from source

Requirements: JDK 17+ (the JetBrains Runtime bundled with Android Studio works), Android SDK with platform 37.

```bash
git clone https://github.com/boulbeba115/Cruise-Car-Music-Player-for-Android.git
cd Cruise-Car-Music-Player-for-Android
./gradlew assembleDebug                 # debug APKs per ABI in app/build/outputs/apk/debug
./gradlew assembleRelease               # R8-shrunk release APKs
./gradlew testDebugUnitTest lintDebug   # unit + screenshot tests, lint
```

Screenshots of every screen size and skin are rendered on the JVM with Robolectric + Roborazzi:
`./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` → `app/build/outputs/roborazzi/`.

An emulator profile that mimics a 1280×720 head unit, the test-library generator and other tools are described in
[docs/toolchain.md](docs/toolchain.md).

## Architecture

Kotlin · Jetpack Compose · Hilt · Room (FTS4) · Media3 · coroutines/Flow — multi-module, with convention plugins in
`build-logic/`.

```
app/                 shell, navigation, theming, car events
core/model           media ids, folder scanning, natural sort
core/data            tag readers, CUE/LRC/M3U, Room database, scanner, artwork cache
core/audio           DSP chain (AudioProcessor), sound settings, spectrum tap
core/playback        MediaSessionService, queue persistence, sleep/wake, player connection
core/car             head-unit bridge, steering-wheel mapping, calls
core/designsystem    skins, typography, components, animated backdrops
core/ui              shared list items, covers, sheets
feature/*            nowplaying, library, folders, sound, settings, voice (Vosk)
```

The full product specification, design decisions and roadmap are in [SPEC.md](SPEC.md); measured facts about the
reference head unit are in [docs/unit-findings.md](docs/unit-findings.md).

## Roadmap

- APE, WavPack, DSF and WMA through a custom FFmpeg build
- Arabic voice commands (needs a compact Arabic speech model)
- Crossfade, parametric EQ, floating mini controller

## Privacy

Cruise has no network code and does not request the internet permission. Voice commands are recognised on the
device; the microphone is used only while the voice panel is open. Everything — library, statistics,
settings — stays on the device and, if you choose to back it up, on your own USB stick. Location is used only by the
optional *Settings → Car test* recorder (it logs vehicle speed to the USB stick for diagnosing a head unit) and only
while that test runs.
