# Cruise — Car Music Player for Android

**Specification and implementation plan**
Version 0.1 · 2026-10-22 · Status: draft for review

Cruise is an offline-first music player for an Android 11 tablet built into a
Renault Mégane III. It is designed for the car: the screen is in landscape, the
driver can only glance at it, the music library is a folder tree on a USB stick or
SD card, the power is cut every time the engine stops, and the audio has to sound
as good as software can make it.

> Codename **Cruise** and package `com.boulbeba.cruise` are placeholders. Rename freely.

---

## Contents

1. [Goals and principles](#1-goals-and-principles)
2. [Target environment and assumptions](#2-target-environment-and-assumptions)
3. [Sound quality: what software can and cannot do](#3-sound-quality-what-software-can-and-cannot-do)
4. [Feature list](#4-feature-list)
5. [User interface](#5-user-interface)
6. [Architecture](#6-architecture)
7. [Data model](#7-data-model)
8. [Performance budgets](#8-performance-budgets)
9. [Testing strategy](#9-testing-strategy)
10. [Implementation plan](#10-implementation-plan)
11. [Toolchain setup (using RDM)](#11-toolchain-setup-using-rdm)
12. [Risks and mitigations](#12-risks-and-mitigations)
13. [Open questions](#13-open-questions)

Priorities used throughout: **P0** must be in the first usable build · **P1** part of
v1.0 · **P2** after v1.0 · **P3** research / stretch.

---

## 1. Goals and principles

### Goals

1. **The best sound the hardware allows.** A 32-bit float signal path, no clipping,
   high-quality resampling, and a car-oriented DSP chain.
2. **Respect the folder tree.** People who keep music in folders organised it on
   purpose. The folder view is a first-class way to browse, not a hidden extra.
3. **Also a smart library.** The same files can be grouped and sorted by artist, album,
   genre, year, format, quality, play history and more.
4. **Built for driving.** Big targets, glanceable layout, every core action within
   two taps, gesture control that works without looking.
5. **Indestructible.** Survives ignition off/on, sleep, reboots, USB unplug, task
   killers and audio-focus fights. It always resumes where it stopped.
6. **Beautiful and alive.** Album-art-driven colours, fluid transitions and
   audio-reactive visuals, without hurting performance on a modest head-unit SoC.
7. **Fully offline.** No feature depends on the internet. Online features
   (cover/lyrics fetching) are opt-in extras.

### Design principles

| Principle | Consequence |
|---|---|
| Glance, don't read | Primary text ≥ 28 sp on Now Playing, lists ≥ 18 sp, no dense tables. |
| Thumb-sized everything | Primary controls ≥ 72 dp, list rows ≥ 64 dp, minimum target 56 dp. |
| Never lose the music | Playback state is persisted continuously; resume is automatic. |
| Calm motion while driving | Transitions ≤ 350 ms; ambient animation slow (≥ 4 s cycles); no flashing. |
| Performance before polish | Every visual effect has a cheaper fallback and a global "performance mode". |
| Bit-honest audio | Every DSP stage can be bypassed; a "Pure" preset bypasses all of them. |

### Non-goals

- Streaming services (Spotify, Deezer, YouTube…), radio, podcasts with feeds.
- Video playback.
- Android Auto / Automotive OS projection (the tablet *is* the head unit).
- Tag editing beyond small fixes (P2: edit title/artist/album/cover in the app).
- Publishing on Google Play (the app is sideloaded; this allows the
  "All files access" permission without Play policy review).

---

## 2. Target environment and assumptions

| Item | Assumption | Effect on design |
|---|---|---|
| OS | **`minSdk 23` (Android 6.0) and up.** The owner's unit *displays* "Android 13" but **really runs Android 8.1 (API 27)** — measured by the car test on 2026-10-22 (see `docs/unit-findings.md`). 23 is the floor set by AndroidX/Hilt | Every API above 23 behind a version check (lint `NewApi` enforces it). Storage access per version: legacy `READ_EXTERNAL_STORAGE` ≤ 28, `requestLegacyExternalStorage` on 29, All files access ≥ 30. No `RenderEffect` blur (31), no AGSL (33), no bit-perfect `AudioMixerAttributes` (34). |
| Device | Android unit fitted to the dash, **wired directly into the car and linked to it through a CAN-bus decoder**. Owner's unit (measured): **Spreadtrum SC7731E**, 4 × Cortex-A7 @ 1.3 GHz, 32-bit only, Mali-T820, **1 GB RAM** (≈ 370 MB free; the unit claims 4 GB), 128 MB app heap, vendor stack `com.tw.*` ("TW" platform) + Dofun launcher. No root, no adb | A **low-end** target: performance mode on by default for this class (no live blur, light Canvas visualizers, reduced motion), memory budget ≤ 120 MB, small image caches, strict Compose discipline. DSP measured at 2.5 % of a core for a 10-band EQ in Kotlin, so C++ is optional. **armeabi-v7a** first-class (plus arm64-v8a for other units). |
| Screen | **Any screen.** The app is fully responsive: any size, density and aspect ratio, landscape and portrait, split-screen and resizable windows. The owner's unit (QLED "HD+" panel, measured 1280×720 @ 160 dpi, app window 1280×660 dp, **system font scale 1.15**) is the **default profile** the defaults are tuned for | Layouts adapt from window size classes, not from device checks; nothing is hard-coded to one resolution. Verified on a matrix of screens ([§5.1](#51-layout-system)). QLED has strong blacks and saturated colour, so the default Midnight theme suits the owner's unit. |
| Music storage | **USB stick**, organised in folders, 1k–50k files | USB sticks mount at `/storage/usbdisk0` (exFAT) on the owner's unit; Cruise can read and write there. Direct file access, incremental scanner, removable-media aware. USB 2.0 speed assumed for scan budgets. After a cold boot the stick mounts a few seconds late, so resume waits for the volume. |
| Languages | **English by default, French** as second language | All strings in resources from day one; French translation in Phase 7. |
| Audio output | **Wired**: SoC codec at **44.1 kHz / 16-bit only** → **PT2313** analog volume/tone processor → amplifier → speakers (Android reports it as wired headphones). USB audio HAL disabled. No platform FLAC decoder | Float DSP chain, final resample to 44.1 kHz and TPDF dither to 16 bit **on by default**. FFmpeg decoder (Jellyfin, includes armeabi-v7a) is required for FLAC/ALAC/WavPack/DSD/WMA. The unit's own EQ and the PT2313 tone controls must be set flat. |
| CAN-bus | Mégane III steering-column remote → CAN box → MCU → vendor class `android.tw.john.TWUtil` → `com.tw.service`, which routes keys **by current source**: source 0 → standard Android media keys; source 3 → the stock music app (`40451` commands). Headlight day/night arrives as `com.zjinnova.zlink.action.OUT_DARK_START/STOP` broadcasts | A **TW platform adapter** (Phase 1) requests source 0 through TWUtil (reflection) so keys reach Cruise's Media3 session as normal media keys; fallback: claim source 3 and map `40451` (incl. duck / mute / save-on-ignition-off). Night theme follows the headlight broadcast. Full protocol in `docs/unit-findings.md`. |
| Power | Ignition off → unit **sleeps**; after some hours (vendor timer) it **shuts down completely** | Two resume paths: fast resume from sleep (process usually still alive) and cold-boot resume (waiting for the USB stick to mount). Persist state every few seconds. While asleep, hold no wake lock so the car battery is not drained. |
| Network | Usually none; sometimes phone hotspot | Everything offline; online extras opt-in and Wi-Fi only by default. |

---

## 3. Sound quality: what software can and cannot do

This section sets honest expectations, because "best sound" depends more on the
signal chain than on any single feature.

**Software cannot** improve the DAC, the amplifier or the speakers, or recover
detail lost in a lossy file or a lossy Bluetooth link (SBC/AAC).

**Software can — and Cruise will:**

1. **Avoid the platform resampler.** Android mixes at the HAL rate (usually 48 kHz).
   Many head units resample with a low-quality algorithm. Cruise reads the native
   output rate (`AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`) and resamples itself with
   a high-quality resampler (libsoxr, "very high" quality) so the system mixer
   receives audio at its own rate and passes it through untouched.
2. **Process in 32-bit float end to end** (decoder → DSP → `AudioTrack` in
   `ENCODING_PCM_FLOAT`). No intermediate rounding to 16 bit.
3. **Never clip.** Automatic pre-gain headroom when EQ boosts, followed by a
   look-ahead true-peak limiter as a safety net. Clipping is the most common reason
   a car EQ "sounds bad".
4. **Gapless playback** for live albums and DJ mixes, and **ReplayGain** so tracks
   from different sources play at the same loudness without touching dynamics.
5. **Correct for the car.** Equal-loudness compensation at low volume, a cabin EQ,
   driver-seat time alignment, speed-dependent gain. These are the features of
   a dedicated car DSP, done in software.
6. **Dither** when the output must be 16-bit.
7. **Not stack effects.** The onboarding tells the user to set the head unit's own
   EQ/"DSP" to flat, and the system `AudioEffect` equalizer is never used (it is low
   quality and session-dependent).

**This car's setup:** the unit is wired directly to the car, so the signal goes
digital → the unit's DAC → its amplifier → speakers, with no lossy Bluetooth hop.
Lossless files, ReplayGain, the clip-free EQ and the in-app resampler all
make an audible difference here. The limiting factors are the unit's DAC/amp
quality and the Mégane's speakers, which the cabin EQ, loudness compensation and
bass enhancer are designed to compensate for. A USB DAC (P3, DSP-18) would be the
only further upgrade.

---

## 4. Feature list

### 4.1 Playback engine

| # | Feature | Pri |
|---|---|---|
| PB-1 | Media3 ExoPlayer inside a `MediaSessionService`: background playback, foreground notification, lock-screen controls | P0 |
| PB-2 | Media keys (play/pause/next/previous, long-press seek) from steering wheel, Bluetooth and the notification | P0 |
| PB-3 | Audio focus: duck for navigation prompts (Waze/Maps), pause for calls, resume afterwards | P0 |
| PB-4 | Persistent queue and position (saved every 5 s and on every state change); resume at the exact position after sleep, reboot or power cut | P0 |
| PB-5 | Gapless playback (LAME/iTunes encoder delay, FLAC, Opus pre-skip) | P0 |
| PB-6 | Shuffle (true random without repeats, plus "shuffle albums" and "shuffle folders"), repeat one / all / off | P0 |
| PB-7 | "Previous" rule: restart the track if > 3 s played, otherwise go to the previous track | P0 |
| PB-8 | Crossfade (0–12 s), *smart*: never crossfades inside a gapless album or a continuous folder | P1 |
| PB-9 | Auto-play on start (optional): resume playback when the car turns on | P1 |
| PB-10 | Fade-in on resume and fade-out on pause (100–500 ms) to avoid clicks | P1 |
| PB-11 | Per-folder / per-file resume for long files (audiobooks, DJ mixes > 20 min) | P1 |
| PB-12 | Playback speed and pitch (for audiobooks), default off | P2 |
| PB-13 | Bluetooth AVRCP metadata and cover so the car's own display shows the track | P0 (comes from PB-1) |

### 4.2 Formats

**Tier 1 — native ExoPlayer extractors (P0):** MP3, AAC/M4A, FLAC, Ogg Vorbis,
Opus, WAV (PCM 16/24/32/float), AMR, MKA/WebM, FLAC-in-Ogg.

**Tier 2 — ExoPlayer extractors + FFmpeg decoder extension (P0):**
ALAC (in M4A), AC-3/E-AC-3, DTS, TrueHD, MP2 and any codec Android lacks on the
device. Uses the prebuilt `org.jellyfin.media3:media3-ffmpeg-decoder`, so FFmpeg does
not have to be built for this tier.

**Tier 3 — full FFmpeg demux + decode through a custom JNI engine (P1):**
APE (Monkey's Audio), WavPack (WV), DSF/DFF (DSD, converted to PCM), WMA/ASF,
AIFF/AIFC, TTA, TAK (if FFmpeg supports it), MPC (Musepack). Built as a custom
Media3 `MediaSource` + renderer backed by `libavformat`/`libavcodec`.

**Playlists and sheets:** M3U/M3U8 (relative & absolute paths), PLS (P1),
**CUE sheets** over a single FLAC/APE/WAV (virtual tracks, P1).

**High resolution:** up to 32-bit / 384 kHz decoded; resampled to the device's
native rate (see [§3](#3-sound-quality-what-software-can-and-cannot-do)).

### 4.3 Sound engine (DSP)

Signal chain, in order. Every stage runs in 32-bit float and can be bypassed
individually. Stages that are off cost nothing.

```
decoder ─▶ ReplayGain ─▶ Pre-amp/headroom ─▶ Parametric EQ ─▶ Loudness comp.
       ─▶ Bass enhancer ─▶ Stereo width / crossfeed ─▶ Balance + time alignment
       ─▶ Speed-dependent gain/compression ─▶ True-peak limiter
       ─▶ Resampler (soxr) ─▶ Dither (16-bit sinks only) ─▶ AudioTrack (float)
                                    └─▶ analysis tap (FFT for visualizers)
```

| # | Feature | Pri |
|---|---|---|
| DSP-1 | Float pipeline, `AudioTrack` float output | P0 |
| DSP-2 | ReplayGain: track / album / auto (album mode when playing an album or folder in order), pre-amp for untagged files, clip prevention from peak tags | P0 |
| DSP-3 | 10-band graphic EQ (31 Hz–16 kHz, ±12 dB) with automatic headroom | P0 |
| DSP-4 | Parametric EQ: up to 16 biquad bands (peak, low/high shelf, low/high pass, notch), with a frequency-response graph you can drag | P1 |
| DSP-5 | Look-ahead peak limiter (−0.3 dBFS ceiling, sliding-window gain so no sample passes over), on whenever the engine is on; engine off or A/B compare = bit-exact passthrough | P0 |
| DSP-6 | High-quality resampling to the native output rate (libsoxr) | P1 |
| DSP-7 | TPDF dither when the sink is 16-bit | P1 |
| DSP-8 | Loudness compensation (ISO 226 equal-loudness contours), follows the volume level | P1 |
| DSP-9 | Bass enhancer: low shelf + psychoacoustic harmonics for small door speakers | P1 |
| DSP-10 | Stereo width (mid/side) and crossfeed | P2 |
| DSP-11 | **Driver focus**: per-channel delay (0–5 ms) and level to centre the stereo image on the driver's seat; presets "Driver / Passenger / Rear / Off" | P1 |
| DSP-12 | **Speed-dependent volume** (like factory car radios): vehicle speed from the CAN-bus if the decoder exposes it, otherwise GPS speed (works offline), raises gain and adds gentle compression as road noise grows; adjustable curve | P2 |
| DSP-13 | Dynamic range compressor "Road mode" for quiet classical/jazz passages | P2 |
| DSP-14 | Presets: Flat, Pure (all bypassed), Mégane cabin, Bass boost, Vocal, Night (low volume), Highway, plus user presets; export/import as JSON | P1 |
| DSP-15 | Per-output-route profiles (Bluetooth / AUX / USB / speaker) switched automatically | P1 |
| DSP-16 | A/B compare button (instant bypass, level-matched) | P1 |
| DSP-17 | **Cabin calibration** wizard: plays pink noise, measures with the tablet mic (RTA), proposes a correction EQ | P3 |
| DSP-18 | Bit-perfect USB DAC output through a userspace USB audio driver (libusb), bypassing Android's mixer | P3 |

Implementation note: DSP stages are Media3 `AudioProcessor`s. Start in Kotlin;
move hot paths (biquads, limiter, FFT) to C++ via NDK if profiling shows > 5 % of a
core at 96 kHz.

### 4.4 Library and folder structure

| # | Feature | Pri |
|---|---|---|
| LIB-1 | Music roots chosen by the user (one or more folders on any volume). Default: auto-detect `Music/` and USB/SD roots | P0 |
| LIB-2 | Direct file access with **All files access** (`MANAGE_EXTERNAL_STORAGE`) — fast and sees every format, unlike MediaStore | P0 |
| LIB-3 | Tag reading with **TagLib** (C++ via JNI): ID3v1/v2.3/v2.4, Vorbis comments, APE tags, MP4 atoms, ASF, RIFF INFO, DSF ID3 | P0 |
| LIB-4 | Incremental scan: compares path + size + mtime; only changed files are re-read. Runs in the background with progress | P0 |
| LIB-5 | Live updates: `FileObserver` on roots while running; rescan on `MEDIA_MOUNTED` (USB stick plugged in) | P1 |
| LIB-6 | Removable volumes: library entries from an unplugged stick are hidden, not deleted; they come back instantly on re-plug (volume UUID) | P1 |
| LIB-7 | Exclusions: per-folder exclude, `.nomedia` honoured (toggle), minimum duration filter (default: skip < 10 s sounds, so album interludes stay; adjustable 0–60 s) | P0 |
| LIB-8 | Multi-value tags: several artists/genres split on `;`, `/`, `feat.`, `ft.`, `&` (configurable) | P1 |
| LIB-9 | Album grouping that does not merge different "Greatest Hits": key = album artist + album; if album artist is missing, the containing folder decides | P0 |
| LIB-10 | Compilations (`TCMP` / `COMPILATION` / "Various Artists") grouped under one album | P1 |
| LIB-11 | Files without tags still look good: title from filename (strips `01 - `, `Artist - `), album from folder name | P0 |
| LIB-12 | CUE sheet parsing into virtual tracks | P1 |
| LIB-13 | Audio analysis in the background while charging: BPM, loudness (EBU R128 → computed ReplayGain for untagged files), energy | P2 |
| LIB-14 | Duplicate finder (same tags + duration, or same audio hash) | P2 |

### 4.5 Browsing, grouping, sorting and search

**Browse views** (each is a tab in the navigation rail or a chip in Library):

| View | Grouping | Pri |
|---|---|---|
| **Folders** | Mirrors the directory tree; breadcrumb bar; folder cover; "play folder", "play folder + subfolders", "shuffle folder tree" | P0 |
| Albums | Grid with covers; album → tracks in disc/track order | P0 |
| Artists / Album artists | Toggle between "track artist" and "album artist" | P0 |
| Genres | Genre → albums → tracks | P0 |
| Tracks | All tracks | P0 |
| Years / Decades | "1990s" → years → albums | P1 |
| Composers | For classical | P1 |
| Quality | Hi-Res (> 48 kHz or > 16 bit), Lossless, Lossy, by codec, by bitrate | P1 |
| Recently added / Most played / Recently played / Never played / Favourites | Smart collections | P1 |
| Smart playlists | Rule builder: any tag, play count, rating, date added, duration, format… AND/OR | P2 |

**Sorting:** title, artist, album artist, album, year, track number, disc,
duration, date added, date modified, play count, last played, rating, file name,
path, bitrate, sample rate, random. Ascending/descending. Each view remembers its own sort.

**Sorting details that matter:** natural sort for file names (`Track 2` before
`Track 10`); locale-aware collation (accents, case); optional "ignore articles"
(`The`, `Le`, `La`, `Les`, `L'`, `A`…); `sort-*` tags honoured (`TSOP`, `ALBUMARTISTSORT`).

**Grouping inside a list:** sections by first letter, year, album, folder or
format; collapsible sections.

**Fast navigation:** large A–Z / year scrubber on the right edge with a bubble preview.

**Search (offline):** SQLite FTS over title, artist, album, album artist, genre,
composer, folder and file name; prefix and accent-insensitive matching; results
grouped (tracks / albums / artists / folders); recent searches. Search-as-you-type
on a car-sized keyboard (the system keyboard is used; the field is big).

### 4.6 Queue and playlists

| # | Feature | Pri |
|---|---|---|
| Q-1 | Queue screen: reorder by drag, swipe to remove, "play next", "add to queue", clear, save as playlist | P0 |
| Q-2 | Playlists: create, rename, reorder, delete; stored in the database | P0 |
| Q-3 | Import/export M3U8 (relative paths so they work when the stick moves to a PC) | P1 |
| Q-4 | Auto-import `.m3u/.m3u8/.pls` files found in music roots | P1 |
| Q-5 | Undo for destructive queue/playlist actions (snackbar, 5 s) | P1 |
| Q-6 | **Drive Mix** (offline "radio"): endless queue built from similar tracks (genre, artist, year, and BPM/energy when analysed) seeded from the current track | P2 |
| Q-7 | Favourite (heart) and 1–5 star rating; optionally written back to tags | P1 / P2 |

### 4.7 Artwork, metadata and lyrics (offline first)

| # | Feature | Pri |
|---|---|---|
| ART-1 | Embedded covers (ID3 APIC, FLAC PICTURE, MP4 `covr`, Vorbis `METADATA_BLOCK_PICTURE`, APE binary) | P0 |
| ART-2 | Folder images: `cover`, `folder`, `front`, `album`, `AlbumArt*` in jpg/png/webp, case-insensitive, also one level up for `CD1/CD2` folders | P0 |
| ART-3 | Thumbnail cache on disk in three sizes (128/384/1024 px), keyed by content hash so the same cover is stored once | P0 |
| ART-4 | Colour palette extraction per cover (dominant, vibrant, muted) for dynamic theming; stored in the DB | P0 |
| ART-5 | Pre-blurred background bitmap per cover (generated on a background thread; no `RenderEffect` on API 30) | P0 |
| ART-6 | Generated covers for files without art: a procedural gradient/geometric pattern seeded by the album name, with the initials — every album looks distinct | P1 |
| ART-7 | Lyrics: embedded (`USLT`, `LYRICS`) and sidecar `.lrc` files; synced (karaoke-style scrolling) and plain | P1 |
| ART-8 | Optional online fetch (off by default, Wi-Fi only): covers from Cover Art Archive (MusicBrainz), synced lyrics from LRCLIB; saved locally next to the cache, never into the user's folders unless asked | P2 |
| ART-9 | Track info sheet: codec, bit depth, sample rate, bitrate, channels, file size, path, ReplayGain values, all tags | P1 |

### 4.8 Car integration

| # | Feature | Pri |
|---|---|---|
| CAR-1 | Steering-wheel / CAN-bus keys through `MediaSession` | P0 |
| CAR-2 | **Key learning** screen: press a steering-wheel button, assign an action (next, previous, play/pause, next folder, previous folder, favourite, voice of track title…). Handles vendor keycodes and long presses | P1 |
| CAR-3 | Resume on both power paths: **wake from sleep** (screen-on / ACC-on broadcast from the CAN-bus decoder; process usually still alive → audio within ~1 s) and **cold boot** after the long-sleep shutdown (`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` → start service → wait for the USB stick's `MEDIA_MOUNTED`, up to 30 s → resume). Optional auto-play | P1 |
| CAR-4 | Survive ignition off: save on ACC-off / screen-off, `ACTION_SHUTDOWN`, and every 5 s; database in WAL mode with `synchronous=NORMAL` so a sudden shutdown cannot corrupt it | P0 |
| CAR-14 | **Battery-safe sleep**: on ACC-off, pause with a short fade, save state, release wake locks and leave the foreground state so the unit can sleep deeply; never wake the unit while parked. The car battery must not drain because of the player | P0 |
| CAR-5 | Battery-optimisation exemption prompt; foreground service type `mediaPlayback`; guidance for vendor task killers | P0 |
| CAR-6 | **Floating mini controller** over the navigation app (overlay permission): a small draggable bubble with cover, play/pause and next | P1 |
| CAR-7 | Split-screen / multi-window support with a compact layout | P1 |
| CAR-8 | Home-screen widget (cover + controls), resizable | P2 |
| CAR-9 | **Blind mode**: the whole Now Playing screen becomes a gesture pad — tap = play/pause, swipe left/right = next/previous, two-finger swipe = next/previous folder, swipe up/down = volume | P1 |
| CAR-10 | Day/night theme switch from the light sensor, or from sunrise/sunset computed offline from GPS position and time | P1 |
| CAR-11 | Driving clock / speed / time-to-end-of-queue chips on Now Playing (optional) | P2 |
| CAR-12 | Reverse-camera friendly: no audio glitch or state loss when the head unit switches to the camera app | P0 |
| CAR-13 | Text-to-speech announcement of the track title on demand (steering-wheel long press), uses the offline TTS engine if installed | P2 |

### 4.9 Visuals and animation

| # | Feature | Pri |
|---|---|---|
| VIS-1 | Dynamic theme: accent and background tinted from the current cover, cross-fading between tracks (600 ms) | P0 |
| VIS-2 | Shared-element transition: cover in a list → big cover on Now Playing | P1 |
| VIS-3 | Album **cover carousel** (3D perspective "flow") as an optional album view | P1 |
| VIS-4 | Audio-reactive visualizers fed by the DSP tap (no `RECORD_AUDIO` needed): spectrum bars, mirrored wave, radial ring around the cover, particle field. Canvas for simple ones, OpenGL ES 3 for the rich ones | P1 |
| VIS-5 | Beat-reactive ambient glow behind the cover (subtle, slow decay) | P2 |
| VIS-6 | Waveform seek bar (pre-computed waveform per track, cached), with drag-to-seek and time bubble | P1 |
| VIS-7 | "Vinyl" mode: cover rendered as a spinning record with tonearm (just for fun) | P2 |
| VIS-8 | Animated list item insertion/removal, spring-based drag in the queue, press-scale on every button | P0 |
| VIS-9 | Themes: **Midnight** (dynamic, default), **OLED Black**, **Amber Cockpit**, **Neon Drive** (synthwave), **Daylight** (high-contrast for sunlight) | P1 |
| VIS-10 | Performance mode: auto-enabled on weak devices (measured frame times), turns off blur, visualizers, carousel 3D and reduces motion | P0 |

### 4.10 Settings, data and maintenance

| # | Feature | Pri |
|---|---|---|
| SET-1 | Settings in DataStore; grouped: Library, Playback, Sound, Appearance, Car, Advanced | P0 |
| SET-2 | Backup/restore of settings, playlists, play stats, EQ presets to a JSON/zip file on the USB stick | P1 |
| SET-3 | Diagnostics screen: audio output route, native sample rate, buffer size, decoder in use, DSP CPU load, dropped frames, scanner stats; exportable log | P1 |
| SET-4 | Onboarding: storage permission, choose roots, battery exemption, set head-unit EQ flat, choose output route | P0 |
| SET-5 | Languages: English (default) and French; follows the system language, overridable in Settings | P1 |

### 4.11 Steering wheel and calls on "TW" head units

Added 2026-10-23 from the owner's request and the vendor-code analysis in `docs/unit-findings.md`. On the
owner's unit the Mégane remote cannot pause/play the stock player or answer Bluetooth calls, one button
opens the Bluetooth app (code 24) and one opens the sound mixer (code 66). Everything here is behind a
`HeadUnitAdapter` interface; units without `android.tw.john.TWUtil` use plain Media3 keys.

| # | Feature | Pri |
|---|---|---|
| SW-1 | **Raw steering-key capture** through `TWUtil` (message 513 / 33281, short and long press), with the standard media keys (codes 5/6/19/20/21 arrive as `KEYCODE_MEDIA_*` once Cruise holds audio focus) handled by Media3 | P0 |
| SW-2 | **Button mapping screen**: press a steering-wheel button, Cruise shows it, choose its short-press and long-press action: play/pause, next, previous, next/previous folder, favourite, shuffle, repeat, announce track, open Cruise, voice/nothing, "unit default" | P0 |
| SW-3 | **Smart call button**: while ringing = answer (long press = reject), during a call = hang up, otherwise = the mapped action (default play/pause). Calls are controlled through Android's Bluetooth hands-free client (`BluetoothHeadsetClient` via the profile proxy, available to apps on Android 8.1); call state from `AG_CALL_CHANGED` broadcasts | P0 |
| SW-4 | **Steering-wheel helper** (optional accessibility service, enabled once by the user): when a remapped button makes the unit open its Bluetooth app or sound mixer, the helper closes it at once and returns to the previous screen. Without the helper the remapped action still happens, the vendor screen also opens | P1 |
| SW-5 | **Now playing on the car**: send title/artist/time/progress to the MCU like the stock player (`40704`, `771`, `1282`) so the unit's own widgets — and, if the CAN box forwards it, the Mégane's original dashboard display — show the current track | P1 (experimental) |
| SW-6 | **Headlights → night theme** from `com.zjinnova.zlink.action.OUT_DARK_START/STOP` | P1 |
| SW-7 | Duck on navigation prompts / mute on calls also when the unit signals it through `40451` (7/8, 11/12) in the source-3 fallback | P1 |

### 4.12 Experience extras

| # | Feature | Pri |
|---|---|---|
| EX-1 | **Instant start**: music resumes ≤ 1 s after the unit wakes, at the exact position, with a 300 ms fade-in | P0 |
| EX-2 | **Folder radio**: endless shuffle that plays whole album folders in order, then jumps to another random folder | P1 |
| EX-3 | **Long-press favourite** from the steering wheel, with a soft confirmation chime (mixed under the music) | P1 |
| EX-4 | **Parked mode**: when the car has not moved for 2 min (GPS) and the screen is idle, Now Playing turns into a full-screen cover + clock ambient display | P2 |
| EX-5 | **Trip summary**: tracks played, minutes listened, top artists — per drive and per month, all offline | P2 |
| EX-6 | **Night listening**: automatic quieter dynamics + loudness compensation when the headlights are on | P2 |

### 4.13 Quality bar (applies to every phase)

- **Measured, not assumed**: every phase is checked on the owner's unit class (SC7731E, 1 GB RAM, Android 8.1)
  through the car test; budgets in [§8](#8-performance-budgets) use those measurements.
- **Smoothness**: no dropped frame during scrolling or navigation on the unit; decorative animation is the
  first thing to go when the frame budget is missed (automatic performance mode).
- **Memory**: ≤ 120 MB PSS on the unit; images decoded at display size; caches bounded.
- **Visual identity**: one coherent design system (tokens, type scale, motion spec), screenshot-tested on the
  screen matrix; no default-looking Material screens ship.
- **Driving UX**: every primary action ≤ 2 taps, 56 dp minimum targets, readable at arm's length at font scale 1.15.
- **Robustness**: no crash, no lost position across ignition, USB unplug, calls and reverse camera — covered by the
  recorder in every car test.

---

## 5. User interface

### 5.1 Layout system

The app is **responsive, not device-specific**. Layout is chosen at runtime from the
current window's width and height classes (Material 3 window size classes), so it reacts
to rotation, split-screen and resizing without restarting. Sizes scale with the screen
through a `Dimens` set chosen per class, never with fixed pixel values.

| Window | Typical screens | Layout |
|---|---|---|
| Landscape, height < 480 dp | 1024×600 head units, phones in landscape, split-screen | **Compact landscape**: icon-only nav rail (72 dp), single content pane, mini player bar 72 dp; Now Playing puts cover and controls side by side |
| Landscape, height 480–900 dp | 1280×720 / 1280×800 head units and tablets (**owner's default**) | **Regular landscape**: labelled nav rail, content + side Now Playing panel |
| Landscape, height > 900 dp | 1920×1200 tablets, 2K head units | **Expanded landscape**: larger covers and type, two-pane library (list + detail) |
| Portrait, width < 600 dp | Phones, narrow split-screen | **Compact portrait**: bottom navigation bar, stacked Now Playing (cover over controls), mini player above the nav bar |
| Portrait, width ≥ 600 dp | Portrait tablets, vertical "Tesla-style" head units | **Regular portrait**: nav rail, cover on top, controls and queue preview below |
| Ultra-wide (aspect > 2.4:1) | 1920×720 / 1280×480 bar-type dash screens | Three columns: nav rail · Now Playing · queue/library |

**Default profile.** The owner's unit (≈1280×720 landscape) is the profile the defaults are
tuned for: Regular landscape layout, Midnight theme, text scale 100 %, visualizer on. Other
screens get the same app with defaults that suit them (e.g. performance mode on weak devices).
A **UI scale** setting (80–140 %) lets any screen fine-tune the size of text and targets.

**Test matrix.** Screenshot tests and manual checks run on: 1024×600, 1280×720 (default),
1280×800, 1920×720, 1920×1080, 2000×1200 landscape; 1080×2400 and 1200×1920 portrait;
50/50 split-screen. Font scale 100 % and 130 %.

### 5.2 Screens

**Now Playing (home screen)** — what the driver sees 90 % of the time.

```
┌────┬──────────────────────────────────────────────────────────────────────┐
│ ♪  │                                                                      │
│    │   ┌───────────────────┐   Song Title Very Large (32sp)               │
│ ▤  │   │                   │   Artist (22sp)                              │
│    │   │     COVER ART     │   Album · 2004 · FLAC 24/96 (16sp, muted)    │
│ 🗀 │   │  (radial visual-  │                                              │
│    │   │   izer around)    │   ▁▂▃▅▇▅▃▂▁▂▃▅▇▇▅▃▂▁▁▂▃▅▃▂▁  waveform seek    │
│ ☰  │   │                   │   1:42                              -2:18    │
│    │   └───────────────────┘                                              │
│ 🔍 │                           ⇄      ⏮      ⏯       ⏭      ↻            │
│    │                          (56)   (72)   (96)    (72)   (56) dp        │
│ ≋  │                                                                      │
│ ⚙  │   [♥]  [EQ: Highway]  [Queue · 12 left · 47 min]   [Lyrics]          │
└────┴──────────────────────────────────────────────────────────────────────┘
 nav rail: Now Playing · Library · Folders · Playlists · Search · Sound · Settings
```

- Background: pre-blurred cover with palette gradient; cross-fades on track change.
- Swipe on the cover = next/previous with a card-slide animation; long-press cover = track info.
- Blind mode (CAR-9) is a toggle on this screen.

**Folders**

```
┌────┬──────────────────────────────────────────────────────────────────────┐
│    │  USB ▸ Music ▸ Rock ▸ Pink Floyd                 [Sort ▾] [▶ All] [⤨]│
│    │──────────────────────────────────────────────────────────────────────│
│    │  🗀  1973 - The Dark Side of the Moon    [cover]  10 tracks · 43 min │
│    │  🗀  1975 - Wish You Were Here           [cover]   5 tracks · 44 min │
│    │  🗀  1979 - The Wall                     [cover]  2 discs           │
│    │  ♪  Bonus - Interview.mp3                          MP3 320 · 12:04   │
│    │                                                                      │
├────┴──────────────────────────────────────────────────────────────────────┤
│ [cover] Money — Pink Floyd            ⏮  ⏯  ⏭   ━━━━━━━○──────  2:31     │
└───────────────────────────────────────────────────────────────────────────┘
```

- Tap a folder = open it; tap the folder's ▶ = play it; long-press = actions sheet
  (play, play recursive, shuffle tree, add to queue, play next, exclude, set as root).
- Folders that contain only one album show as an album card (cover, year, count).
- The breadcrumb is tappable at every level; the system back gesture goes up one level.
- Folder scroll position is remembered per folder.

**Library** — chip row at the top: Albums · Artists · Album artists · Genres ·
Tracks · Years · Composers · Quality · Collections. Under it: grid (albums) or list
(others), with the view's sort/group menu and the A–Z scrubber.

**Album / artist / genre detail** — large header with cover and palette
background, actions (play, shuffle, add to queue), then tracks grouped by disc.

**Queue** — slide-over panel from the right edge (reachable from anywhere), drag
handles, current track pinned at the top.

**Search** — big field with instant results grouped by type.

**Sound** — EQ curve editor (drag points on the curve; graphic mode = 10 big
vertical sliders), preset carousel, toggles for each DSP stage, driver-focus seat
picker (a top-down car outline where you tap the seat), live spectrum behind the curve,
A/B button, CPU load indicator.

**Settings** — grouped list, large rows.

### 5.3 Visual design tokens

| Token | Value (Midnight theme) |
|---|---|
| Background | `#0A0C10`, tinted 8 % toward the cover's dominant colour |
| Surface / elevated | `#14171D` / `#1C2028` |
| Accent | Cover's vibrant swatch, adjusted to ≥ 4.5:1 contrast on background; fallback `#4FC3F7` |
| Text primary / secondary | `#F2F4F8` / `#A8B0BD` |
| Corner radius | 20 dp cards, 28 dp cover, full-round buttons |
| Type | Title 32 sp / Artist 22 sp / List 18 sp / Meta 14 sp minimum. Font: Inter or Manrope (bundled, offline) |
| Spacing grid | 8 dp |

### 5.3.1 Skins (owner's request 2026-10-23)

A skin changes the whole look, not just colours: palette, typefaces, corner shapes, the animated backdrop, how
the cover is presented, the animated music bar, the navigation marker and glow. Settings → Appearance shows a
live miniature of each; a second skin can follow the headlights (zlink `OUT_DARK_START/STOP` broadcasts).

| Skin | Type | Cover | Music bar | Backdrop |
|---|---|---|---|---|
| Midnight (default) | Manrope | Record sleeve with holographic foil; the disc slides out and spins while playing | Waveform dancing with the music | Glow tinted by the cover |
| Neon Drive | Audiowide / Orbitron, caps | Neon tube frame pulsing on the beat | Glowing oscilloscope wave | Synthwave horizon grid, scrolling |
| Amber Cockpit | Share Tech Mono, caps | Tachometer: progress arc, music-driven needle | LED segment meter with red zone | Carbon weave, amber sweep |
| Vinyl Lounge | Playfair Display | Spinning record (33⅓ rpm), tonearm swings in/out | Groove line with stylus | Warm wood grain |
| Aurora | Space Grotesk, very round | Liquid blob whose edge ripples with the spectrum, gooey colour blobs behind | Waveform | Moving northern lights |
| Tape Deck | Space Grotesk / Manrope | Cassette: art as the label, tape winds reel to reel with the song, reels speed up as they empty | Waveform | 80s stripes, lower-right corner |
| OLED Pure | Manrope ExtraBold, big type | Minimal square | Hairline with breathing playhead | Pure black (lightest on the CPU) |
| Daylight | Manrope | Die-cut sticker: 3D tilt, foil sweep, corner that peels up on the beat | Waveform | Plain light |

Every skin passes WCAG AAA (7:1) for primary text on background, surface and elevated surface, 4.5:1 for
secondary text and on-accent (unit test `SkinContrastTest`). Fonts are bundled as static weight instances
(variable fonts render at their lightest weight on Android 8.1). Animations follow the performance mode
(Full / Balanced / Saver; Auto = Balanced on low-end units like the owner's).

### 5.4 Motion

| Use | Spec |
|---|---|
| Button press | scale 0.92, spring (damping 0.6, stiffness medium) |
| Screen transitions | shared axis X, 280 ms, emphasized easing |
| Cover → Now Playing | shared element, 350 ms |
| Track change | cover slide + background cross-fade 600 ms |
| Lists | item placement animation, 200 ms |
| Ambient (glow, visualizer idle) | ≥ 4 s cycles; no strobing; capped at 3 flashes/s (photosensitivity rule) |
| Reduced motion / performance mode | cross-fades only, ≤ 150 ms |

---

## 6. Architecture

### 6.1 Technology stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x (K2), C++17 for native | Modern, coroutines; C++ for DSP/tags/FFmpeg |
| UI | Jetpack Compose (Material 3 as a base, custom design system on top) | Animation-friendly, fast to iterate |
| Playback | AndroidX Media3: ExoPlayer, `MediaSessionService`, `MediaLibraryService` | Background play, media session, gapless, extensible audio sink |
| Extra codecs | `org.jellyfin.media3:media3-ffmpeg-decoder` (prebuilt); own FFmpeg build for Tier 3 | No FFmpeg build needed for P0 |
| Tags | TagLib 2.x via JNI | Fastest and most complete tag reader |
| Resampler | libsoxr via JNI | Reference-quality resampling |
| Database | Room (SQLite, WAL) + FTS4 | Reactive queries via Flow, migrations |
| Settings | DataStore (Proto or Preferences) | Atomic, coroutine-based |
| Images | Coil 3 with a custom fetcher for embedded art + own disk cache | Memory/disk caching, Compose support |
| Palette | AndroidX Palette | Dynamic colours |
| DI | Hilt | Standard, works with services and WorkManager |
| Background work | WorkManager (scan, analysis) + coroutines | Survives process death, constraints (charging) |
| Lists | Compose `LazyColumn`/`LazyVerticalGrid` + Paging 3 for > 5k items | Smooth scrolling on large libraries |
| Graphics | Compose Canvas; OpenGL ES 3.0 (`GLSurfaceView` in `AndroidView`) for rich visualizers | No AGSL on API 30 |
| Build | Gradle (Kotlin DSL), version catalog, AGP; NDK + CMake for native | Reproducible |
| Quality | ktlint/detekt, Android Lint, JUnit5, Turbine, Compose UI tests, Macrobenchmark + Baseline Profiles | Performance is measured, not hoped |

All versions are pinned in `gradle/libs.versions.toml` to the latest stable
releases at the start of Phase 0.

### 6.2 Module layout

```
cruise/
├─ app/                      Application, MainActivity, navigation, DI graph
├─ core/
│  ├─ model/                 Pure Kotlin data classes (Track, Album, Folder, …)
│  ├─ common/                Coroutines dispatchers, result types, logging
│  ├─ database/              Room DB, DAOs, FTS, migrations
│  ├─ datastore/             Settings
│  ├─ scanner/               File walker, TagLib JNI, CUE parser, artwork extractor
│  ├─ artwork/               Thumbnail cache, palette, blur, generated covers, Coil fetchers
│  ├─ playback/              MediaLibraryService, ExoPlayer setup, queue, resume, focus, keys
│  ├─ audio-dsp/             AudioProcessors (Kotlin) + native/ (C++: biquad, limiter, soxr, FFT)
│  ├─ ffmpeg-engine/         Tier 3 formats: JNI over libavformat/libavcodec (Phase 6)
│  ├─ designsystem/          Theme, tokens, components (big buttons, scrubber, cover, sheets)
│  └─ testing/               Fakes, test fixtures
└─ feature/
   ├─ nowplaying/            Now Playing, blind mode, visualizers, lyrics
   ├─ library/               Library views, details, sort/group
   ├─ folders/               Folder browser
   ├─ queue/                 Queue panel
   ├─ playlists/
   ├─ search/
   ├─ sound/                 EQ & DSP screen
   ├─ settings/              Settings, onboarding, diagnostics, key learning
   └─ overlay/               Floating mini controller service
```

Dependency rule: `feature/*` → `core/*`; `core/*` never depends on `feature/*`;
the UI talks to playback only through a `MediaController` wrapper (`PlayerConnection`).

### 6.3 Runtime architecture

```
┌──────────────── UI process (Activity, Compose) ───────────────┐
│ ViewModels ──▶ Repositories (Room Flows) ──▶ UI state (StateFlow)
│     │                                                          │
│     └──▶ PlayerConnection (MediaController) ◀────────┐         │
└──────────────────────────────────────────────────────┼─────────┘
                                                       │ Binder (same process)
┌──────────── PlaybackService (MediaLibraryService) ───┴─────────┐
│ ExoPlayer ── Renderers (+FFmpeg) ── DefaultAudioSink(float)    │
│                                       └─ AudioProcessor chain  │
│ QueueManager ── ResumeStore (Room+DataStore, 5 s ticks)        │
│ AudioFocus / Noisy receiver / Key mapper / Route monitor       │
└────────────────────────────────────────────────────────────────┘
┌──────────── Background ───────────────────────────────────────┐
│ ScanWorker (WorkManager) ─ FileWalker ─ TagLib JNI ─ Room       │
│ ArtworkWorker ─ thumbnails, palette, blur                       │
│ AnalysisWorker (charging only) ─ BPM, R128, waveform            │
│ VolumeMountReceiver / BootReceiver                              │
└────────────────────────────────────────────────────────────────┘
```

Single process. UI follows unidirectional data flow (state down, events up).

### 6.4 Scanner algorithm

1. Walk each root with `java.nio.file.Files.walkFileTree` (fast; one `stat` per entry),
   skipping excluded folders and `.nomedia` (if enabled).
2. For every audio file, compare (path, size, mtime) with the DB. Unchanged → skip.
3. Changed/new files go into batches of 200 → parallel tag reads (TagLib JNI,
   `Dispatchers.IO` limited to the core count) → one Room transaction per batch.
4. Missing files are marked `missing` (removed after 7 days, or immediately if
   the volume is present and the file is gone).
5. Folder rows are updated bottom-up with counts, durations and the chosen folder cover.
6. Albums/artists/genres are rebuilt incrementally from changed tracks only.
7. Artwork extraction is queued after the scan, so browsing works immediately.
8. The scan is resumable: progress is committed per batch.

---

## 7. Data model

Main tables (Room). All paths are stored relative to their root plus a
`volumeId`, so a USB stick keeps its identity when its mount path changes.

| Table | Key columns |
|---|---|
| `root` | id, volumeId, relativePath, displayName, enabled |
| `folder` | id, rootId, parentId, relativePath, name, coverArtKey, trackCount, totalDurationMs, isAlbumFolder |
| `track` | id, folderId, fileName, size, mtime, durationMs, title, titleSort, album, albumArtist, artistDisplay, trackNo, trackTotal, discNo, discTotal, year, date, genreDisplay, composer, comment, codec, container, sampleRate, bitDepth, bitrate, channels, isLossless, rgTrackGain, rgTrackPeak, rgAlbumGain, rgAlbumPeak, hasEmbeddedArt, artKey, hasLyrics, cueSheetId, cueStartMs, cueEndMs, bpm, loudnessLufs, dateAdded, missing |
| `artist`, `track_artist` (role: artist / album artist / composer) | many-to-many |
| `album` | id, title, albumArtistId, year, artKey, folderHint, isCompilation, trackCount, durationMs |
| `genre`, `track_genre` | many-to-many |
| `play_stat` | trackId, playCount, skipCount, lastPlayedAt, rating, favourite, resumePositionMs |
| `playlist`, `playlist_item` | id, name, createdAt / playlistId, position, trackId |
| `queue_item`, `queue_state` | persisted queue, current index, position, shuffle order, repeat mode |
| `artwork` | key (content hash), source (embedded/folder/generated/online), w, h, dominant, vibrant, muted colours |
| `track_fts` | FTS4 on title, artists, album, albumArtist, genre, composer, folder, fileName |

Indexes: `track(folderId)`, `track(album, albumArtist)`, `track(dateAdded)`,
`play_stat(lastPlayedAt)`, and every sort column used by a view.

---

## 8. Performance budgets

Measured on the actual car tablet (the slowest target) with Macrobenchmark where possible.

| Metric | Budget |
|---|---|
| Cold start to interactive Now Playing | < 1.2 s on a mid-range unit; **< 2.0 s on the owner's SC7731E** |
| Cold start to audio playing (auto-resume) | < 2.0 s after the app process starts; **≤ 1 s after wake from sleep** (EX-1) |
| First full scan, 10 000 files on USB 2.0 | < 90 s (I/O bound); UI usable during scan |
| Incremental rescan, nothing changed, 10 000 files | < 5 s |
| Scroll, 50 000-track list | ≥ 99 % frames within 16.7 ms (no jank) |
| Now Playing with visualizer | ≥ 55 fps (performance mode lowers the visualizer when missed); DSP chain < 15 % of one core at 44.1 kHz (measured: 10-band EQ 2.5 %) |
| Memory (steady state) | **≤ 120 MB PSS** (the owner's unit has 1 GB RAM, ≈ 370 MB free) |
| Track change gap | 0 ms with gapless material; < 50 ms otherwise |
| Data loss on power cut | ≤ 5 s of position, nothing else |
| APK size | < 40 MB per ABI split |

---

## 9. Testing strategy

| Layer | How |
|---|---|
| DSP | JVM unit tests with generated signals: EQ frequency response within ±0.1 dB of the design, limiter never exceeds the ceiling, resampler THD+N, ReplayGain math, dither statistics |
| Scanner | A fixture library generated on the PC with FFmpeg (every format, odd tags, Unicode names, CUE, missing tags, broken files) → expected DB snapshots |
| Repositories / ViewModels | JUnit + Turbine + in-memory Room |
| Playback | Instrumented tests on an API 30 emulator: gapless, focus, queue persistence, process death recovery |
| UI | Compose UI tests and screenshot tests across the screen matrix in [§5.1](#51-layout-system) |
| Performance | Macrobenchmark (startup, scroll, scan) + Baseline Profiles generated per release |
| Car soak test | 8 h continuous playback on the real tablet; 50 sleep/wake cycles; 20 hard power cuts; USB unplug during playback |
| Listening tests | Blind A/B (level-matched) of Pure vs default chain vs stock player |

---

## 10. Implementation plan

Claude writes the code, builds it and tests it on the emulator; the owner installs each
phase's APK on the car unit and drive-tests it. Every phase ends with an APK installed on
the car unit and a short drive test. Installing: copy the APK to the USB stick and open it
on the unit, or `adb` over Wi-Fi with a laptop in the car on the same phone hotspot.

### Phase 0 — Toolchain and skeleton

- Install JDK, Android SDK, NDK, CMake and an API 30 tablet emulator — big files fetched with RDM ([§11](#11-toolchain-setup-using-rdm)).
- Create the Gradle project, modules, version catalog, Hilt, detekt/ktlint, `git init`.
- Debug/release signing config; ABI splits (arm64-v8a, armeabi-v7a).
- Connect to the car tablet over `adb` and read its properties: SoC, ABI, screen size/density,
  native output sample rate and buffer size, available codecs (`MediaCodecList`).
- Log what the CAN-bus decoder sends (`getevent`, `logcat` broadcasts): steering-wheel keys,
  ACC on/off, reverse gear, vehicle speed. Record the vendor's sleep/shutdown timing.
- **One-visit car test** (Settings → Car test, added 2026-10-22 to minimise trips to the car): a guided
  10-step wizard that collects everything later phases need — device report, left/right speaker check,
  animation frame times + DSP CPU benchmark, steering keys (foreground, background media session and
  legacy MEDIA_BUTTON broadcast), USB library scan, the unit's audio/codec/key-layout configs and vendor
  apps, and a background recorder (ignition/sleep/boot, display, USB mounts, audio activity, calls,
  GPS speed) that auto-exports to `cruise-fieldtest/` on the USB stick every 2 min and on screen-off,
  holds no wake lock and stops after 72 h.
  **This report decides several defaults below.**

**Exit criteria:** an empty Compose app with a nav rail runs on the emulator and on the car tablet.

### Phase 1 — Playback core

- `PlaybackService` (Media3 `MediaLibraryService`), notification, `MediaSession`.
- ExoPlayer with float output, Jellyfin FFmpeg decoder, gapless.
- Audio focus, becoming-noisy, media keys (PB-1…PB-7), resume store (PB-4, CAR-4).
- Temporary "play this folder" picker for testing.
- Basic Now Playing screen (cover, title, controls, seek bar).

**Status (2026-10-23):** built as 0.1.0 — Media3 service + FFmpeg (ALAC verified through FFmpeg), fades, queue/position persistence, sleep-pause / wake-resume, media-key resumption, folder browser, Now Playing with embedded or folder art, mini player, TW steering-wheel bridge + mapping screen + smart call button + helper (SPEC §4.11). Verified on the emulator and with unit tests; the TW and call parts need the car test.

**Exit criteria:** plays Tier 1 + 2 formats from a folder, keeps playing with the
screen off, steering-wheel keys work, survives a power cut and resumes at the right position.

### Phase 2 — Library: scanner, database, artwork

- Onboarding with All files access and root selection (SET-4, LIB-1/2).
- TagLib JNI module (CMake) + scanner (LIB-3/4/7/9/11), Room schema + FTS.
- Artwork pipeline: embedded + folder art, hashed disk cache, palette, pre-blur (ART-1…5).
- Removable volumes and mount events (LIB-5/6).

**Exit criteria:** 10 000-file fixture scanned within budget; incremental rescan < 5 s;
covers appear in a debug list; unplug/replug the USB stick without losing data.

### Phase 3 — Browsing UI and design system

- Design system: tokens, themes (Midnight + Daylight first), big buttons, cover
  component, A–Z scrubber, sheets, snackbars.
- Responsive layouts for every window class in [§5.1](#51-layout-system) (landscape, portrait,
  ultra-wide, split-screen), UI scale setting, owner's unit as the default profile.
- Folders view (full spec in [§5.2](#52-screens)), Library views, detail pages, sort/group menus
  with per-view memory, search, queue panel, playlists (Q-1, Q-2), mini player.
- Dynamic theming from cover (VIS-1), list animations (VIS-8), performance mode (VIS-10).

**Exit criteria:** every P0 browse view usable with one hand at 1024×600; 50k-track
scroll budget met; usability drive test (passenger operates, driver watches).

### Phase 4 — Sound engine

- AudioProcessor chain scaffold with bypass and CPU metering.
- ReplayGain, pre-amp/headroom, 10-band EQ, limiter (P0); then parametric EQ, loudness
  compensation, bass enhancer, driver focus, presets, route profiles, A/B (P1).
- Native module: biquads, limiter, FFT tap; libsoxr resampler to native rate; dither.
- Sound screen with the curve editor and live spectrum.
- Crossfade (PB-8) and fades (PB-10).

**Exit criteria:** DSP unit tests green; no clipping with +12 dB EQ boosts on 0 dBFS
material; < 15 % of one core at 48 kHz on the tablet; blind A/B listening session in the car.

### Phase 5 — Visual polish and animation

- Shared-element transitions, cover carousel, waveform seek bar (VIS-2/3/6).
- Visualizers: Canvas spectrum and radial ring, then OpenGL particle field (VIS-4/5).
- Remaining themes (VIS-9), generated covers (ART-6), lyrics with sync (ART-7), track info (ART-9).
- Day/night switching (CAR-10).

**Exit criteria:** Now Playing ≥ 55 fps with visualizer on the tablet, or performance mode
kicks in automatically.

### Phase 6 — Car integration and extended formats

- Boot/wake start and auto-resume (CAR-3), key learning (CAR-2), blind mode (CAR-9),
  floating controller (CAR-6), split-screen (CAR-7).
- CUE sheets (LIB-12), M3U/PLS import/export (Q-3/4), multi-value tags, compilations (LIB-8/10).
- Tier 3 formats: build FFmpeg (avformat + avcodec, audio-only, LGPL configuration) with the
  NDK under MSYS2 (or WSL if installed), JNI engine as a Media3 `MediaSource` (ffmpeg-engine module).
- Collections, years, composers, quality views, favourites and ratings.

**Exit criteria:** APE/WV/DSF/WMA files play gaplessly; CUE albums show as albums; steering-wheel
learning works with the Mégane remote; after ignition on, the app is playing within 2 s when
the unit wakes from sleep, and within 2 s of the process starting after a full shutdown; current
draw while parked is unchanged with the app installed (no wake locks held).

### Phase 7 — Hardening and v1.0

- Baseline Profiles, R8 rules, startup optimisation.
- Soak tests ([§9](#9-testing-strategy)), crash-free run, diagnostics screen (SET-3), backup/restore (SET-2).
- French translation (SET-5).
- Release build, signed APK, install guide.

**Exit criteria:** all P0/P1 items done or explicitly deferred; budgets in [§8](#8-performance-budgets) met;
daily driving without a crash or lost position.

### Status after the full build (2026-10-23, version 1.0.0)

Built, with the plan adjusted where a simpler route met the same goal:

- **Library (Phase 2):** pure-Kotlin tag readers (ID3v1/2.2–2.4, FLAC, Vorbis/Opus, MP4/ALAC, WAV) instead of a
  TagLib JNI module — no NDK needed, tested against FFmpeg-made fixtures; incremental scanner, Room + FTS search,
  CUE virtual tracks, M3U import (PC paths like `E:\Music\…` re-anchored on the stick), thumbnail cache,
  automatic rescan on start and on USB mount.
- **Browsing (Phase 3):** Library (albums, artists, genres, years, tracks, quality, recently added, most played,
  favourites), detail pages, sorting, A–Z scrubber, search, playlists with M3U8 export, queue panel, mini player,
  track menus with "go to folder / album".
- **Sound (Phase 4):** Kotlin DSP chain inside ExoPlayer — 10-band EQ with automatic headroom, presets
  (incl. Mégane cabin and Highway), bass shelf, volume-dependent loudness, ReplayGain track/album/auto,
  stereo width, driver/passenger focus, balance, sliding-window look-ahead limiter at −0.3 dBFS, TPDF dither,
  hold-to-compare; engine off = bit-exact passthrough (unit tested).
- **Visuals (Phase 5):** 7 skins ([§5.3.1](#531-skins-owners-request-2026-10-23)), animated music bar per skin,
  spectrum from the post-DSP signal, generated covers, synced lyrics (.lrc sidecar or embedded), track info.
- **Car (Phase 6):** blind mode (whole-screen gestures), night skin with headlights, steering-wheel mapping and
  call button (§4.11), sleep pause / wake resume, boot resume.
- **Hardening (Phase 7):** backup/restore to `Cruise/cruise-backup.json` on the stick, French, R8 release build.

Not built yet (backlog, in priority order): Tier 3 formats through a custom FFmpeg/NDK build (APE, WavPack, DSF,
WMA — the files are skipped by the scanner today), crossfade (PB-8; fades between tracks exist), floating
controller overlay (CAR-6), parametric EQ and route profiles, OpenGL particle visualizer, shared-element
transitions, Baseline Profiles.

Needs the car to confirm: TW steering keys and call answering, headlight broadcasts, sleep/wake timing, real
library scan speed on the owner's USB stick.

### Version 1.1.0 (2026-10-23)

- **Covers as objects** (owner's feedback: the square cover with dashes looked flat): sleeve, liquid, sticker and
  cassette styles; new **Tape Deck** skin (8 skins). Canvas-only drawing, motion stops on pause.
- **Now Playing gestures** without an overlay (on by default, Settings → Library & playback): hold anywhere ½ s =
  play/pause (a ring fills under the finger), hold + slide = scrub with a time bubble, swipe left/right = next /
  previous, swipe up/down = volume. Ordinary taps are never intercepted; controls that handle a touch themselves
  (seek bar, lyrics, queue) keep it.
- **Steering wheel owned by Cruise** on TW units: the unit's key service (com.tw.service) opens its Bluetooth screen
  for the phone button unless a HiCar phone is "connected"; Cruise sets that hand-over flag (`action.hicar.onconnect`,
  renewed on every screen-on), so the phone button only does what Cruise maps it to — calls are still answered. The
  mixer button cannot be released that way: the accessibility helper closes the EQ screen, or the button is re-learned
  in the unit's own Steering Wheel app (com.tw.keypad), which Settings → Steering wheel now opens. New wheel action
  **Voice command**; the wheel's VOICE function (code 90) uses it by default.
- **Default music app**: the unit's MUSIC key opens the app named in `/data/tw/music_name`; Settings → Steering wheel
  can point it at Cruise (SELinux is permissive on the reference unit; whether the file is writable is checked on the
  unit). Android-side, Cruise answers `MUSIC_PLAYER`, `APP_MUSIC`, audio files from file managers (`VIEW audio/*`) and
  assistants' `MEDIA_PLAY_FROM_SEARCH`.
- **Offline voice commands** (feature/voice): Vosk small models for English and French (~40 MB each, from
  `Cruise/voice/` on the stick, installed once). "Play / joue …", "the album …", "… by …", "shuffle …", favourites,
  next / previous / pause / resume, louder / quieter. Transcripts are matched to the library by sound (phonetic key +
  edit distance + partial windows), tested against real recogniser output — e.g. "plain a while else off by" finds
  Nawal Al Zoughbi, "je vous fais russe" finds Fairuz. Music ducks while listening; the model is released after
  5 idle minutes. Arabic speech is not supported (no small Arabic model exists).

Needs the car: the HiCar hand-over on the real service, writing `/data/tw/music_name`, the microphone path on the unit
(zlink uses one), model load time on the Cortex-A7.

### After v1.0 (P2/P3 backlog)

Speed-dependent volume (DSP-12), road compressor (DSP-13), stereo width/crossfeed (DSP-10),
audio analysis + Drive Mix (LIB-13, Q-6), smart playlists, online covers/lyrics (ART-8),
widget (CAR-8), TTS announcements (CAR-13), vinyl mode (VIS-7), duplicate finder (LIB-14),
tag editing, cabin calibration (DSP-17), bit-perfect USB DAC driver (DSP-18).

---

## 11. Toolchain setup (using RDM)

The development PC (Windows 11) currently has **no JDK, Android SDK, NDK or WSL**.
MSYS2 (ucrt64) and Git Bash are present. Disk space is ample (D: 947 GB free).

Large downloads go through **Rapid Download Manager**, always through the **GUI
application** (`rdm.exe`) so every download appears in its window and can be
tracked, paused and resumed there. The headless `rdmc` client is not used.

```powershell
$rdm = "D:\boulbeba ai\my work\rdm\target\release\rdm.exe"
& $rdm /n /d <url> /p D:\dev\downloads     # starts at once, shows in the RDM window
& $rdm /d <url> /p D:\dev\downloads        # opens the Download File Info dialog to confirm first
```

If RDM is already running, the command is handed to the open window. The
setup scripts wait for each file to appear complete in the download folder
before continuing.

| Download | Size (approx.) | Source | Needed in |
|---|---|---|---|
| Android Studio (includes JetBrains Runtime 21 and SDK manager) — recommended for Compose previews and the layout inspector | ~1.2 GB | developer.android.com/studio | Phase 0 |
| *or, minimum:* Android SDK command-line tools + Temurin JDK 21 | ~150 MB + ~200 MB | dl.google.com/android/repository · api.adoptium.net | Phase 0 |
| SDK platform 30 + latest stable platform, build-tools, platform-tools (`adb`) | ~300 MB | through `sdkmanager` | Phase 0 |
| Android NDK (latest LTS) | ~1 GB | dl.google.com/android/repository/android-ndk-*-windows.zip | Phase 2 |
| CMake (SDK package) | ~50 MB | `sdkmanager "cmake;…"` | Phase 2 |
| Emulator system image: API 30, Google APIs, x86_64 | ~1 GB | `sdkmanager` (or the direct zip via RDM) | Phase 0 |
| Gradle distribution (the wrapper fetches it; RDM can pre-fetch into `~/.gradle/wrapper/dists`) | ~150 MB | services.gradle.org | Phase 0 |
| FFmpeg for Windows (to generate the test fixture library) | ~100 MB | gyan.dev builds | Phase 2 |
| TagLib, libsoxr sources (git tag tarballs) | small | GitHub | Phase 2/4 |
| FFmpeg source (for Tier 3 formats) | ~20 MB | ffmpeg.org | Phase 6 |

Exact URLs and versions are resolved and recorded in `docs/toolchain.md` at the start
of Phase 0, and checksums are verified after download.

### 11.1 Assets from the local generators

Local generators in `C:\doc-video\generators` are used for assets instead of stock
material. Everything runs offline on the PC; generated files are committed under
`app/src/main/res` or `docs/assets` with the prompt and workflow recorded next to them.

| Generator | Workflow / model | Used for |
|---|---|---|
| ComfyUI (`ComfyUI-master`) | `image_z_image_turbo.json`, `image_generation_wf.json` (Z-Image Turbo, Krea2 Turbo) | App icon concepts, onboarding illustrations, theme backdrop textures (e.g. Neon Drive skyline, Amber Cockpit), a small set of fallback cover backgrounds |
| ComfyUI | `image_editing_wf.json` (Flux Kontext) | Variations and clean-up of the chosen icon and illustrations |
| ACE-Step (`music gen`) | text-to-music | Royalty-free test tracks for the fixture library, DSP listening tests and screenshots, so no copyrighted music ends up in the repo |

Icons inside the UI are vector (Material Symbols + custom vector drawables), not generated
bitmaps. Generated images are post-processed to WebP and sized for the target density.

---

## 12. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| The head-unit vendor ROM kills background apps or ignores battery exemption | Music stops | Foreground service, vendor-specific whitelist instructions, watchdog that restarts playback on wake; test in Phase 1 |
| Steering-wheel keys arrive as vendor-specific broadcasts instead of key events | Remote doesn't work | Key-learning screen (CAR-2); Phase 0 logs what the unit sends |
| The player keeps the unit awake while parked | Car battery drained | CAR-14: release everything on ACC-off; measure parked behaviour (`dumpsys power`) in Phase 1 and Phase 6 |
| No ACC broadcast from the vendor ROM | Late save / late resume | Fall back to screen-off/screen-on; 5 s periodic save covers the gap |
| Weak GPU/CPU | Janky UI | Performance mode, Paging, Baseline Profiles, OpenGL only where needed, budgets measured on the device |
| The native output rate is not reported correctly | Double resampling | Diagnostics test tone + manual override of the output rate |
| Building FFmpeg on Windows without WSL | Tier 3 formats delayed | Tier 1+2 cover most libraries; try MSYS2; fall back to installing WSL; Tier 3 is P1, not P0 |
| Power cut during a DB write | Corrupt library | SQLite WAL + `synchronous=NORMAL`; library is rebuildable from files; user data (playlists, stats) auto-backed up daily to a file |
| Compose overhead on the tablet | Slow lists | Stable keys, immutable UI models, `derivedStateOf`, profiling from Phase 3; View-based fallback for the giant track list if needed |
| Bluetooth to the stock radio limits quality | Hi-res pointless | Documented in §3; route-specific profiles; recommend AUX/USB |
| Scope is large | Never finishes | Strict P0 → P1 order; a usable build at the end of Phase 3; P2/P3 only after v1.0 |

---

## 13. Open questions

These do not block Phase 0, but their answers change defaults. Most will be answered
by the Phase 0 device report.

**Answered (2026-10-22):**

- Audio: the unit is wired directly to the car (no Bluetooth hop).
- Car link: a CAN-bus connection is present.
- Power: ignition off → sleep; after some hours the unit shuts down completely.
- Screen: QLED HD+ panel (exact resolution read in Phase 0).
- Storage: music on a USB stick.
- Languages: English by default, French supported.

**Still open** (the Phase 0 device report answers 1–2):

1. Brand/model of the unit and of the CAN-bus decoder.
2. Exact screen resolution and density.
3. Roughly how many files, and which formats dominate (MP3, FLAC, APE…)? Any CUE + single-file rips?
4. Should the app ever write to the music files (ratings/tags, downloaded covers), or stay
   read-only? Default until decided: **read-only**.
