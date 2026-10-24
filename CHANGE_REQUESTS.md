# Cruise — Change requests after the first car test (1.1.0 → 1.2.0)

Source: the owner's test of 1.1.0 (2026-10-23). This file turns that feedback into change requests (CRs) with
acceptance criteria and an execution plan. [SPEC.md](SPEC.md) stays the reference for everything not changed here;
when a CR is done, SPEC.md is updated to match.

**Working rule (owner, 2026-10-23):** Claude does not run tests on the emulator unless the owner says so. When a build
is ready, Claude installs it and opens it in the emulator **for the owner to test**, then waits for the owner's go. JVM unit
tests and the Robolectric screenshot tests run on the PC, not on the emulator, so they still run during the work.

Priorities: **P0** blocks the 1.2.0 release · **P1** part of 1.2.0 · **P2** the second app (after 1.2.0 ships).

Status legend: ✅ done and verified on the PC (build, unit tests, screenshot tests) · 👀 waiting for the owner's review ·
🚗 can only be confirmed in the car · 🟡 partly done · ⬜ not started. Updated 2026-10-24.

---

## Overview

| CR | Change | Priority | Status |
|---|---|---|---|
| [CR-01](#cr-01--remove-voice-commands) | Remove voice commands completely | P0 | ✅ Done |
| [CR-02](#cr-02--one-library-section-with-tabs-search-inside-it) | One Library section with tabs (Folders, Albums, Artists, Playlists…); search moves inside it | P0 | ✅ Done |
| [CR-03](#cr-03--first-start-storage-permission-and-a-song-ready-on-now-playing) | Ask for storage at once; Now Playing always has a song (last played, or a random one) | P0 | ✅ Done · 🚗 needs the car |
| [CR-04](#cr-04--folders-shows-only-folders-that-contain-music) | Folders shows only folders that contain music | P0 | ✅ Done · 🚗 needs the car |
| [CR-05](#cr-05--settings-clean-up-steering-wheel-and-diagnostics-leave-cruise) | Settings clean-up: diagnostics, car test and steering wheel leave Cruise | P0 | ✅ Done |
| [CR-06](#cr-06--sound-profiles-instead-of-an-engineers-panel) | Sound: profile cards instead of the engineer's panel | P0 | ✅ Done |
| [CR-07](#cr-07--skins) | Skins: button spacing for all; per-skin redesigns (Amber, Aurora, Midnight, Daylight, Neon, Tape, Vinyl) | P1 | ✅ Done · 👀 owner review |
| [CR-08](#cr-08--generated-placeholder-covers) | 30 generated placeholder covers for music without art | P1 | ✅ Done |
| [CR-09](#cr-09--clean-up-and-optimisation-for-low-end-units) | Full clean-up and optimisation for low-end units | P1 | 🟡 Mostly done |
| [CR-10](#cr-10--second-app-steering-wheel-control-and-car-integration-probe) | Second app: steering-wheel control + car-integration probe and report | P2 | 🟡 Built · 🚗 needs the car |
| [CR-11](#cr-11--music-from-every-storage-not-only-the-usb-stick) | Music from every storage (internal, SD card, USB), not only the USB stick | P0 | ✅ Done · 👀 owner review |
| [CR-12](#cr-12--storage-access-asked-at-start-player-shown-at-once) | Storage access asked at start, the player shown at once with a banner instead of a blocking screen | P0 | ✅ Done · 👀 owner review |
| [CR-13](#cr-13--illustrated-covers-per-skin-and-texture-backgrounds) | Illustrated placeholder covers per skin (not abstract), a different one per song; texture backgrounds | P1 | 🟡 In progress (images generating) |
| [CR-14](#cr-14--fast-scroller-with-letter-bubble-on-long-lists) | Fast scroller with a letter bubble on long lists; A–Z view of the queue | P1 | ✅ Done · 👀 owner review |

---

## CR-01 — Remove voice commands

**Status: ✅ Done** — `:feature:voice` deleted with the Vosk and JNA libraries; no mic button, no voice intents; the merged
manifest has no `RECORD_AUDIO`. The 1.2.0 APK for the unit is 5.1 MB (1.1.0: 13.9 MB).

**Why.** Recognition did not work in the car. The feature is removed, not hidden.

**Change**
- Delete the `:feature:voice` module (Vosk engine, matcher, overlay, view model, tests) and the Vosk dependency.
- Remove every entry point: the mic button on Now Playing and in search, `VoiceRequests`, the `ACTION_VOICE` intent
  filter and `MediaCommands.voice()`, the wheel's `KeyAction.Voice`, the `MEDIA_PLAY_FROM_SEARCH` intent filter
  (it only existed to feed the voice matcher), and the `RECORD_AUDIO` permission.
- Remove the voice strings (EN/FR) and the voice part of SPEC.md §10 "Version 1.1.0".

**Done when** the APK has no `RECORD_AUDIO` permission and no `org.vosk` classes, and no screen shows a mic.

## CR-02 — One Library section with tabs, search inside it

**Status: ✅ Done** — 4 destinations; Library tabs Folders · Albums · Artists · Genres · Playlists (last one remembered);
search in the header with folder, album, artist and track results; Favourites / Most played / Recently added as automatic
playlists; "go to folder / album" switches tab. Screenshot tests 15, 16, 30–34.

**Why.** Library, Folders, Playlists and Search are four ways to reach the same songs. They become one section.

**Change**
- Navigation goes from 7 destinations to 4: **Now Playing · Library · Sound · Settings**.
- **Library** has one tab row: **Folders · Albums · Artists · Genres · Playlists**. Folders is the default tab (the
  music lives on the USB stick as folders); the last tab used is remembered.
- Dropped as tabs: Tracks, Years, Quality. **Favourites, Most played and Recently added** become the first three
  cards of the Playlists tab (automatic playlists that can be played, shuffled or opened like any playlist).
- **Search** is a search field in the Library header (icon that expands to a field). While text is typed, the tab content
  is replaced by results grouped as Folders · Albums · Artists · Tracks. Clearing the field or pressing Back returns to the tab.
- "Go to folder / go to album" from a track menu switches to the matching Library tab.
- Detail pages (album, artist, genre, playlist, folder) open inside the Library section; Back goes up one level.

**Done when** the navigation shows 4 entries on every layout class, every former screen's content can be reached from the
Library tabs, and search finds folders, albums, artists and tracks.

## CR-03 — First start: storage permission and a song ready on Now Playing

**Status: ✅ Done · 🚗 needs the car** — access asked at launch (runtime dialog up to Android 10, All-files page from 11); scan starts the moment
it is granted; last session restored, or the whole library queued in shuffle from a random track (paused); three clear
empty states without a redirect. Confirm on the unit with the real stick.

**Why.** On first start Now Playing was empty and sent the user to the library. The permission had to be found in Folders.

**Change**
- On launch, if storage access is missing, ask for it straight away: the Android 8.1 runtime dialog
  (`READ_EXTERNAL_STORAGE`) on the unit; `READ_MEDIA_AUDIO` on Android 13+; the "All files access" page on Android 11–12.
  If the user refuses, Now Playing shows one card with the reason and a "Grant access" button; nothing else nags.
- Once access is granted, the library scan starts immediately (not after the 2.5 s start-up delay).
- Now Playing always opens on a song, never on an empty screen:
  - **Last session exists** → the last queue, track and position are loaded and shown (paused unless resume-on-start
    applies, as today).
  - **Fresh install / empty queue** → as soon as the scan has found tracks, the whole library is queued in shuffle
    mode starting on a random track, paused and ready, with its cover and title shown.
  - **No music at all** (no stick, empty stick) → a "Plug in the USB stick with your music" state that switches to a song
    automatically when the stick is mounted and scanned. No redirect to the library.

**Done when** a fresh install on the emulator, given a music folder, shows a random song on Now Playing without any
tap other than granting access; a second start shows the last song at its last position.

## CR-04 — Folders shows only folders that contain music

**Status: ✅ Done · 🚗 needs the car** — folders come from the library database (`FolderTree`, 6 unit tests); only music folders, single-path
chains collapsed, album folders show artist and year, play-all on every row. Screenshot test 30–31 with a scanned
fake stick that also holds `Download/` and `Documents/`. To confirm with the owner's real stick.

**Why.** The Folders tab listed the whole file system (Android, DCIM, Download…).

**Change**
- The folder tree comes from the library database (the `folder` column of every scanned track), not from `listFiles()`.
  A folder is shown only if it holds audio files itself or in a subfolder below it.
- Chains of folders with no music of their own and one subfolder are collapsed: `USB › Music › Rock` opens straight on
  `Rock`'s content, with the full path still in the breadcrumb.
- Each folder row shows its track count and the cover of its first album; folders that are one album show as an album card
  (SPEC §5.2).
- Volumes: the volume list is skipped when only one volume has music (the usual case: the USB stick).
- While the first scan runs, the tab shows scan progress and fills in as folders are found.

**Done when** a stick with `Android/`, `DCIM/`, `Download/` and a `Music/` tree shows only the `Music/` branches with music,
collapsed as described.

## CR-05 — Settings clean-up: steering wheel and diagnostics leave Cruise

**Status: ✅ Done** — Settings = Appearance + Library & playback (with backup and About). Car test, device report, key
tester and steering screen moved to `:feature:carlab`, used by Cruise Wheel; `:core:car` is no longer in Cruise; no
accessibility service, location, Bluetooth or package-query permission in Cruise.

**Why.** The help/debug tools were for the bring-up and are not for daily use. The steering-wheel takeover worked in the
car, but the owner wants it out of the music player: it becomes its own app (CR-10).

**Change**
- Settings keeps only: **Appearance** (skin, night skin with the headlights, UI scale, animation level), **Library &
  playback** (auto-scan, rescan, resume on wake / on boot, Now Playing gestures), **Backup & restore**, and an
  **About** line (version).
- Removed from Cruise and moved to the second app's code base (CR-10), not deleted: Car test, Device report, Key tester,
  field-test recorder, vendor export, benchmarks, steering mapping, call button, HiCar hand-over, accessibility helper,
  `/data/tw/music_name` writer.
- Cruise keeps ordinary media-key handling through its Media3 session (the unit sends standard media keys to the app that holds
  audio focus — docs/unit-findings.md) and the headlight broadcasts for the night skin.
- Permissions removed from Cruise: `QUERY_ALL_PACKAGES`, location, Bluetooth, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  (kept only if playback needs it), `FOREGROUND_SERVICE_LOCATION`, the accessibility service.

**Done when** Settings has the four groups above, the APK declares no accessibility service, and `:core:car` is no longer
a dependency of the Cruise app.

## CR-06 — Sound: profiles instead of an engineer's panel

**Status: ✅ Done** — 12 profile cards with their curves, Bass / Treble (new treble shelf, unit-tested) and Focus; Custom
shows the 10-band EQ and keeps its curve; old settings migrate (tested); fresh installs start on Mégane tuned.

**Why.** Ten EQ bands, width, balance, limiter, ReplayGain pre-amp and the seat picker are too much to handle while driving.

**Change**
- The Sound screen is a grid of **profile cards**: large tiles with an icon, a name, one line of description and a small
  drawing of the profile's curve. One tap applies it; the active card is highlighted. Profiles:
  Natural (no processing) · Mégane tuned · Bass boost · Vocal clarity · Highway · Night (quiet) · Rock · Pop · Electronic ·
  Jazz · Classical · Custom.
- Under the grid, three simple controls only: **Bass** and **Treble** sliders (−6…+6 dB, added on top of the profile; a
  treble high-shelf is added to the DSP chain) and **Focus** (Everyone / Driver).
- **Custom** opens the 10-band equaliser below the cards; the curve is kept when another profile is tried. Width, balance,
  loudness, limiter and ReplayGain are no longer on the
  screen; they keep sensible fixed defaults (loudness on, limiter on, ReplayGain auto).
- The hold-to-compare button stays, as a small button in the header.

**Done when** every sound change on the screen is one tap or one slider, and existing saved settings load into the
matching profile (or Custom).

## CR-07 — Skins

**Status: ✅ Done · 👀 owner review** — all items below built and rendered in the screenshot tests. After the owner's review on
2026-10-24 two were redone: **Neon City's cover** (now a rooftop LED billboard, see CR-07.4) and **Tape Deck's music bar**
(now a deck transport panel, see CR-07.6).

### CR-07.0 All skins — button spacing and layout

**Status: ✅ Done** — rail as a centred group; transport row with fixed gaps that shrinks to fit phones; secondary actions
in one centred pill.
- Transport row: buttons sized and spaced from a shared rule (play ≥ 1.35 × prev/next, ≥ 24 dp gaps, centred on the
  music bar's width instead of `SpaceEvenly` across the whole panel).
- Secondary row (favourite, lyrics, queue, info, blind mode) becomes one evenly spaced row of equal touch targets, aligned
  with the transport row; no lone icons drifting at the edges.
- Title block, music bar and controls share one left edge and one width; vertical rhythm on the 8 dp grid.
- Checked on the screen matrix of SPEC §5.1 (screenshot tests).

### CR-07.1 Amber Cockpit → new colours and a fixed needle

**Status: ✅ Done · 👀 owner review** — "Sport Cockpit": graphite / ice blue / signal red; centre-pivot needle with inertia and hub cap,
ticks and arc on the same 270°, digital time window.
- **Needle:** pivots on the gauge centre and is drawn to the tick ring, in dp (not raw pixels), so it lines up with the ticks on
  every screen size; it sweeps the same 270° arc as the ticks, with a hub cap over the cover's centre ring.
- **Colours:** no more brown and yellow. New palette "graphite & signal red": graphite background, cool white type, signal-red
  accent for the needle, arc and red zone, ice-blue secondary for the LED meter's normal zone. Carbon backdrop kept, re-tinted.
- Display name becomes "Sport Cockpit" (the stored id stays `amber`, so saved choices keep working).

### CR-07.2 Aurora — livelier liquid cover, wavy music bar

**Status: ✅ Done · 👀 owner review** — bass pulse, travelling ripples, per-band edge; wave bar that swells with the music.
- The blob around the cover moves visibly and in time with the music: more control points, bass drives the whole shape's
  pulse, mid/high bands send ripples travelling round the edge, larger amplitude; it settles slowly when paused.
- **Music bar:** a wavy line (a sine wave whose height follows the music, scrolling while playing) for the played part, a
  flat line for the rest, a round playhead.

### CR-07.3 Midnight and Daylight — better cover frame and style

**Status: ✅ Done · 👀 owner review** — pre-blurred soft shadows, bevel, spine, gloss, fixed record reflection; thicker sticker border
and calmer peel.
- **Midnight (sleeve):** soft shadow (pre-rendered once, not blurred per frame), a thin bevel and spine on the sleeve, a
  glossy highlight; the record looks real (fine groove sheen, proper label size); cover slightly larger in its area.
- **Daylight (sticker):** thicker die-cut white border with a soft contact shadow, cleaner peel corner, cover larger in its area.

### CR-07.4 Neon → full redesign "Neon City"

**Status: ✅ Done · 👀 owner review** — skyline backdrop, neon-sign title and artist (Tilt Neon font), glass-tube bar. **Cover, second
design (2026-10-24):** a rooftop LED billboard — the album on an LED screen (pixel grid, refresh sweep) turned in 3D,
a lamp bar whose light cones pulse with the music, pink neon trim, steel scaffolding on a wet roof that mirrors it,
light rain, a breathing red aviation light; songs without art get a neon record-and-note on the screen.
- **No purple, no sun.** Palette: night blue-black, electric cyan and neon red-pink, with a warm amber sign as a third colour.
- **Backdrop:** a night skyline (layered building silhouettes with lit windows), a few neon signs that flicker on rarely,
  and the city's glow reflected in a wet street at the bottom. Drawn once into a cached bitmap; only the signs and reflections move.
- **Song title and artist as neon signs:** tube-style lettering with glow; the title "lights up" with a short flicker when the
  track changes. A neon-tube font is bundled (static weights, see SPEC §5.3.1 note on variable fonts).
- **Cover:** hung like a lit sign — a neon tube frame with mounting brackets; without art, the placeholder is a neon-sign
  drawing (a record with a music note) instead of the plain square.
- Music bar: a neon tube that fills with light as the song plays.

### CR-07.5 OLED Pure — unchanged

**Status: ✅ Done** — unchanged apart from CR-07.0.
Kept as it is (owner likes it). Only CR-07.0 spacing applies.

### CR-07.6 Tape Deck — new colours, tape-style music bar

**Status: ✅ Done · 👀 owner review** — chrome colours and smoky cassette. **Music bar, fourth design (2026-10-24, after the
owner's third review):** the tape itself is the progress bar — one closed loop round both reels, like a belt (across the
top, round the right reel, back along the bottom, round the left reel); the played part lights up and travels the whole
360° loop as the song plays, visibly running; no cursor. The packs inside the loop empty and fill, the hubs turn, and the
mechanical tape counter sits in the centre, elapsed time only. Drag anywhere along the bar to seek. **Cassette label:** title on its own line and artist below, both cut with
"…" before the VU meter when too long.
- **Colours:** no brown/orange. "Chrome tape": blue-black background, smoky translucent cassette shell, cool grey surfaces,
  teal and coral-pink accents, label stripes in teal / sky / coral / white; the tape itself dark graphite.
- **Music bar = a tape transport:** two small reels at the ends of the bar; the left one empties and the right one fills as the
  song plays (sizes follow the progress), both spinning while playing; the tape between them runs past a playback head
  that marks the position; time shows on a mechanical three-digit tape counter. Drag along the tape to seek.

### CR-07.7 Vinyl Lounge — new colours, picture disc, real tonearm

**Status: ✅ Done · 👀 owner review** — ink navy, felt and spotlight; picture disc; tonearm geometry solved so the stylus follows the song
from the outer groove to the label, lifts on pause, glides back on a new track.
- **Colours and backdrop:** no brown. Ink-navy background with a felt-mat texture and a soft spotlight from above; cream type,
  record-label red accent.
- **Cover = picture disc:** the album art fills most of the record (a large circle), the groove rings are drawn on top of it,
  the centre label and spindle on top of that.
- **Tonearm like a real player:** rests off the record at the top; when playback starts it swings onto the outer edge, then moves
  toward the centre as the song progresses (angle follows the position); on pause it lifts; on a new track it returns to the
  outer edge.

## CR-08 — Generated placeholder covers

**Status: ✅ Done** — 36 generated with the owner's ComfyUI (Z-Image Turbo), 30 kept, 757 KB of WebP; chosen per folder
everywhere; Now Playing's colours follow them. Prompts in [docs/assets/placeholder-covers.md](docs/assets/placeholder-covers.md).

- Generate **30 cover images** with the local ComfyUI (`C:\doc-video\generators\ComfyUI-master`, Z-Image Turbo
  workflow): abstract, music-themed, no text, varied colours, so they suit any skin. Prompts and settings recorded in
  `docs/assets/placeholder-covers.md`.
- Shipped as 512 × 512 WebP in the app (about 1.5 MB in total).
- A track or album without art gets one of the 30, chosen from its album (or folder) key, so the choice looks random but the
  same album always shows the same cover everywhere (lists, mini player, Now Playing, every skin).
- Replaces the gradient-with-initial covers (`GeneratedCover` and `generatedCover`).

## CR-09 — Clean-up and optimisation for low-end units

**Status: 🟡 Mostly done** — done: dead modules and permissions removed; animation clocks, spin and audio levels read while
drawing, not composing; player position pushed every 5 s instead of 0.5 s (screens extrapolate); lyrics recompose per line;
time labels per second; destination switches no longer keep two screens composed; reused paths; static art (Neon City
skyline, LED grid, soft shadows, textures) drawn once per size; R8 release builds measured (Cruise 5.1 MB for the unit).
Left: a Baseline Profile and cold-start timing, which need emulator runs (waiting for the owner's go) or the unit.

Target: the owner's unit (4 × Cortex-A7, 1 GB RAM, Android 8.1).
- **Remove dead code** left by CR-01/02/05: unused screens, strings, icons, permissions, modules and dependencies.
- **Per-frame allocations:** cache `Path`, `Brush` and text layouts in the animated covers, music bars and backdrops
  (`drawWithCache` / remembered objects) instead of creating them every frame.
- **Static art drawn once:** backdrops with fixed parts (Neon City skyline, carbon, felt) render to a cached bitmap; only the
  moving layer redraws.
- **Composition:** destination switches no longer keep two full screens composed during the transition; lists use stable keys
  and immutable rows; heavy screens are not composed while hidden.
- **Images:** every cover decoded at display size; the 30 placeholders decoded at thumbnail size in lists.
- **Release build:** R8 full mode, resources shrunk, `armeabi-v7a` APK for the unit; Baseline Profile generated once the owner
  allows emulator runs.
- Measure APK size and cold-start time before and after.

---

## CR-10 — Second app: steering-wheel control and car-integration probe

**Status: 🟡 Built · 🚗 needs the car** — module `:wheel`, APK `cruise-wheel-0.1.0.apk` (1.8 MB). Everything below is built and the key
engine is unit-tested; what the MCU really sends (and whether volume and calls behave) is confirmed in the car.

Lowest priority: started after Cruise 1.2.0 is ready. Working name **Cruise Wheel** (package
`com.boulbeba.cruise.wheel`), a separate app in the same repository (new `:wheel` application module reusing
`:core:car`, `:core:designsystem` and the field-test code moved out of Cruise by CR-05).

### CR-10.1 Key override engine

**Status: 🟡 Built · 🚗 needs the car** — `KeyEngine` (tap, hold, double, combination within 350 ms; 9 tests), rules with JSON
export/import to `cruise-wheel/mapping.json`, actions (media keys, volume, mute, calls, open app, home, back, assistant,
block), foreground service from boot, HiCar hand-over for the phone button, helper closing vendor screens, rule screen
with "press the button on the wheel".
- Reads the raw wheel keys through `TWUtil` (messages 513 / 33281, docs/unit-findings.md) from a foreground service that
  starts at boot.
- **Triggers** per button: short press, long press (hold), double press, and **combinations** (two buttons together, e.g.
  volume up + volume down). Whether the MCU reports separate press and release events decides how exact combinations and
  hold durations can be; this is measured in the car with the probe (CR-10.3) and the engine falls back to "two keys within
  300 ms" if only release events exist.
- **Actions:** play/pause, next, previous, mute, volume up/down, answer / reject / hang up a call (hands-free client, existing
  `CallController`), open an app (Bluetooth phone, navigation, Cruise, any installed app), home, back, open the unit's own
  screens, send a media key to the current player, and short sequences of these.
- **Override the unit's own behaviour:** each mapped button stops doing the vendor action (HiCar hand-over flag for the phone
  button, accessibility helper closing vendor screens for the others, default-app files in `/data/tw/`). Unmapped buttons keep
  the unit's behaviour.
- Mapping screen: press a button on the wheel to select it, then pick a trigger and an action. Export/import the mapping
  to the USB stick.

### CR-10.2 Car-feature bus

**Status: 🟡 Built · 🚗 needs the car** — broadcast `com.boulbeba.cruise.wheel.CAR_EVENT` with types key, trigger, ignition, lights, source
(documented in `CarBus.kt`).
- A small, documented broadcast API (`com.boulbeba.cruise.wheel.CAR_EVENT` + extras) through which the app republishes
  what it learns from the car (keys, ignition, lights, reverse gear, speed, doors…), so Cruise and future apps use car
  data without each talking to the vendor stack.

### CR-10.3 Integration probe and report

**Status: 🟡 Built · 🚗 needs the car** — passive capture (MCU ids 0x0100–0x07FF, 0x8100–0x87FF, 0x9E00–0x9FFF, vendor broadcasts found in the
unit's own apps, settings, properties, sensors, /sys), 14 guided steps, report `cruise-wheel/report-<date>/report.html`
and `report.json` with a score.
Goal: know exactly which car features this unit exposes and how far the system can be made to feel factory-built.
- **Passive capture:** listens to every `TWUtil` message id in the known ranges, every vendor broadcast (`com.tw.*`,
  zlink, CAN-box app), sensors, GPS, sticky intents, `ro.*` / `persist.*` properties, vendor `Settings.System` keys and
  readable `/sys` nodes.
- **Guided capture:** the app asks for one action at a time (press each wheel button, lights on/off, reverse gear, open a
  door, ignition off/on, change climate, use parking sensors) and records which messages changed during that window, so each
  car feature is matched to its signal automatically.
- **Report:** writes `wheel-report/` on the USB stick: a readable HTML page and a JSON file with, for each feature
  (steering keys, ignition/ACC, reverse, headlights, illumination, speed, doors, parking sensors, climate, outside temperature,
  fuel, RPM, steering angle, trip data, handbrake, seat belt…): detected or not, the signal source, sample values, whether it
  can be read and/or controlled, and an integration score for the unit.

---

## CR-11 — Music from every storage, not only the USB stick

**Status: ✅ Done · 👀 owner review** — the scanner already read the internal storage, SD cards and USB sticks; the texts made
it look USB-only. All texts now name every source, and Folders labels each volume as Internal storage, SD card or USB
stick (from its mount name: `usbdisk0`, `extsd`…).

**Why.** Owner's test 2026-10-24: "this player always asks for the USB stick but is supposed to load music from all the
available memory sources".

**Done when** no screen asks specifically for a USB stick, and music on the internal storage plays like music on the stick.

## CR-12 — Storage access asked at start, player shown at once

**Status: ✅ Done · 👀 owner review** — access is requested when the app opens; Now Playing never shows a blocking "allow
access" page any more: a banner slides over the player ("Cruise can't read your music yet · Allow access · ×") and a toast
says so when the request is refused.

**Why.** Owner's test 2026-10-24: the access screen should be a start-up request plus a warning, not a page in the way.

## CR-13 — Illustrated covers per skin, and texture backgrounds

**Status: 🟡 In progress** — code done: covers are chosen **per song** (no more "every song in a folder gets the same
cover") from **the active skin's own set**, found by file name (`cover_<skin>_<nn>`); texture backgrounds for Vinyl
Lounge (walnut and felt), Sport Cockpit (carbon), Tape Deck (brushed metal) and Daylight (paper) installed and drawn
under each skin's lighting. Vinyl Lounge: the first texture (walnut and green felt) was rejected by the owner and replaced
by a navy art-deco wallpaper with fine gold fan lines (picked from six new candidates). Images: 160 illustrations (20 per skin, scenes in each skin's style, nothing abstract)
generating in the owner's ComfyUI; the 30 abstract covers of CR-08 are replaced when they are in.

**Why.** Owner's test 2026-10-24: one cover for every song; abstract covers too generic; wanted scenes like a cat listening
to records, many of them, matched to each skin; textured backgrounds such as Vinyl Lounge's.

## CR-14 — Fast scroller with letter bubble on long lists

**Status: ✅ Done · 👀 owner review** — a slim handle on the right edge of long lists (20 rows or more) that follows the
list; drag it, or press on the edge, to fly through the list while a big bubble beside the finger shows the letter of the
songs there (Latin accents folded, Arabic letters as they are, digits as "#"). On: the Up next queue, Folders, Albums
(year when sorted by year), Artists, Genres, album / artist / genre pages (track number on album pages) and playlists. It
replaces the old A–Z letter column. The queue gets an **A–Z** switch that lists it alphabetically — sliding then goes
through the alphabet; tapping still plays that song, and the play order is unchanged ("Play order" switches back).
Screenshot test 35.

**Why.** Owner's request 2026-10-24, with a reference screenshot: long lists such as a 70-song queue were hard to move
through.

## Execution plan

Order chosen so that removals come first (less code to change afterwards), structure next, looks after that, and the
optimisation pass last over the final code.

| Step | Work | CRs | Ends with | Status |
|---|---|---|---|---|
| 1 | Remove voice; strip Settings to four groups; detach steering wheel, calls, diagnostics and field test from the Cruise app (code kept for CR-10) | CR-01, CR-05 | Builds; unit tests green | ✅ Done |
| 2 | Library section with tabs and inline search; Folders from the database, music-only, collapsed chains; automatic playlists | CR-02, CR-04 | Builds; screenshot tests updated | ✅ Done |
| 3 | Start-up permission request, immediate scan, last-or-random song on Now Playing | CR-03 | Builds; unit tests for the start-track choice | ✅ Done |
| 4 | Sound profile cards, Bass/Treble/Focus, Custom EQ, treble shelf in the DSP | CR-06 | DSP tests extended for the treble shelf | ✅ Done |
| 5 | Shared Now Playing layout and button spacing, then skins one by one: Amber, Aurora, Midnight, Daylight, Neon City, Tape, Vinyl | CR-07 | Skin screenshot tests + contrast test pass | ✅ Done · 👀 owner review |
| 6 | Generate the 30 placeholder covers in ComfyUI, pick and convert them, wire them in | CR-08 | Covers in the APK; prompts recorded | ✅ Done |
| 7 | Clean-up and optimisation pass; release APK; update SPEC.md and README | CR-09 | **Cruise 1.2.0 APK** in `D:\dev\cruise-builds` | 🟡 Mostly done (Baseline Profile left) |
| 8 | Install 1.2.0 in the emulator and open it **for the owner to test**; wait for the owner's go and feedback | — | Owner's verdict | 👀 Opened in the emulator for the owner |
| 9 | Second app: module, key engine, mapping UI, car-event bus, probe and report | CR-10 | **Cruise Wheel** APK for a car visit | 🟡 Built · 🚗 needs the car |

Every step keeps the build, unit tests, lint and the JVM screenshot tests green.

### Needs the car later
- CR-10: whether the MCU sends press and release separately (combinations, hold), volume control path on the TW unit, the
  probe's guided capture.
- CR-03/CR-04: scan speed and folder collapse on the owner's real USB stick.
