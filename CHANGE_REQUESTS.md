# Cruise — Change requests from the car tests (1.1.0 → 1.2.0 → 1.2.1)

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
| [CR-13](#cr-13--illustrated-covers-per-skin-and-texture-backgrounds) | Illustrated placeholder covers per skin (not abstract), a different one per song; texture backgrounds | P1 | ✅ Done · 👀 owner review |
| [CR-14](#cr-14--fast-scroller-with-letter-bubble-on-long-lists) | Fast scroller with a letter bubble on long lists; A–Z view of the queue | P1 | ✅ Done · 👀 owner review |
| [CR-15](#cr-15--neon-city-title-and-artist-on-a-banner-towed-by-a-neon-plane) | Neon City: title and artist on a banner towed by a neon plane, animated | P1 | ✅ Done · 👀 owner review |
| [CR-16](#cr-16--app-icon-car-mascot-with-headphones) | App icon: the car mascot with headphones picked from the logo attempts | P1 | ✅ Done · 👀 owner review |
| [CR-17](#cr-17--no-scan-screen-after-the-first-start) | No "finding your music" screen after the first start; rescans run silently in the background | P0 | ✅ Done · 🚗 needs the car |
| [CR-18](#cr-18--cruise-wheel-redesign-and-working-remapping) | Cruise Wheel: simpler, cleaner screens; remapping that works; more actions (apps, screen, calls) | P0 | ✅ Done · 🚗 needs the car |
| [CR-19](#cr-19--cruise-wheel-always-running) | Cruise Wheel always running: survives ignition-off, starts by itself | P0 | ✅ Done · 🚗 needs the car |
| [CR-20](#cr-20--car-report-crash-on-start-listening) | Car report: app crashed on "Start listening" | P0 | ✅ Done · 🚗 needs the car |
| [CR-21](#cr-21--phones-keep-playing-with-the-screen-off) | Phones: music keeps playing when the screen is switched off | P0 | ✅ Done · 👀 owner review |
| [CR-22](#cr-22--neon-city-painted-backdrop-and-the-skin-toned-to-match-it) | Neon City: a painted foggy teal city, and the billboard, plane and colours toned to match it | P1 | ✅ Done · 👀 owner review |
| [CR-23](#cr-23--vinyl-lounge-slower-record-lighter-grooves) | Vinyl Lounge: slower record, lighter grooves over the cover | P1 | ✅ Done · 👀 owner review |
| [CR-24](#cr-24--hands-on-record-and-tape) | Hands-on record (DJ scratch) and tape (stop, cue, rewind) | P1 | ✅ Done · 👀 owner review |
| [CR-25](#cr-25--covers-with-bars-around-the-picture) | Art with bars around the picture fills the record and the other shapes | P1 | ✅ Done · 👀 owner review |

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

**Status: ✅ Done · 👀 owner review** — covers are chosen **per song** (no more "every song in a folder gets the same
cover") from **the active skin's own set**, found by file name (`cover_<skin>_<nn>`); texture backgrounds for Vinyl
Lounge (walnut and felt), Sport Cockpit (carbon), Tape Deck (brushed metal) and Daylight (paper) installed and drawn
under each skin's lighting. Vinyl Lounge: the first texture (walnut and green felt) was rejected by the owner and replaced
by a navy art-deco wallpaper with fine gold fan lines (picked from six new candidates). Sport Cockpit: the first carbon weave (large, contrasty checks) was rejected too and replaced by
a fine, dark carbon weave with a soft red glow from below (picked from eight candidates rendered in the skin). Images: 158 illustrations installed (20 per skin generated, scenes in each skin's style,
nothing abstract; two Sport Cockpit ones left out), 512 px WebP, about 2.5 MB in all; they replace the 30 abstract covers
of CR-08. Prompts and file list in `docs/assets/placeholder-covers.md`.

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

## CR-15 — Neon City: title and artist on a banner towed by a neon plane

**Status: ✅ Done · 👀 owner review** — on Neon City the title and artist are no longer loose text: a small prop plane,
drawn in cyan neon tubes with a pink stripe, tows an advertising banner across the sky beside the billboard, the title
in pink tubes and the artist in cyan.
- **New track:** the plane flies off to the left and fades; the next one comes in from the right and slows to a cruise.
  The banner's tubes then light up from the leading edge to the free end and blink once (a single flash per track,
  SPEC §5.4).
- **While playing:** the cloth flutters (a wave running to its free end, with light and shade on the folds), the plane
  bobs and pitches gently, its propeller turns and the red light on its fin breathes. Paused, it all holds still; on
  the Saver quality it does not move at all.
- **Long names** scroll along the banner, faded at its ends (right to left for Latin text, left to right for Arabic).
- **Cost on the unit:** each line is drawn once per track, glow included, into a bitmap. Each frame only slices it into
  narrow strips shifted by the wave, so there is no text layout and no blur per frame.

Screenshot tests `20_skin_neon` and `20_skin_neon_long_title`. (Arabic shows reversed in the JVM screenshots — a limit of
the test renderer that plain text shares; devices shape it properly.)

**Why.** Owner's idea 2026-10-24: keep the billboard, and make the title and singer blend into the theme — "a neon plane
hanging an ads sign in the back … with cool animation".

## CR-16 — App icon: car mascot with headphones

**Status: ✅ Done · 👀 owner review** — ten logo concepts were drawn by two local models (Z-Image Turbo and Krea-2 Turbo,
same prompts and seeds) and put side by side; the owner picked Krea-2's car mascot. It is now the launcher icon:
- **Android 8 and later (adaptive icon):** the mascot, cut out along its white sticker outline, sits inside the 66 dp
  safe zone over a gradient of the logo's own orange, so round, squircle and square launcher masks never clip it.
- **Android 6–7:** the original rounded tile as PNG icons from mdpi to xxxhdpi.
- `docs/assets/icon.png` (512 px) for the README.

The Cruise Wheel app keeps its own icon.

**Why.** Owner's request 2026-10-24: a logo attempt through both models on one sheet to pick from.

## Second car test (1.2.0 + Cruise Wheel 0.1.0) → Cruise 1.2.1 + Cruise Wheel 0.2.0

Source: the owner's car test, 2026-10-24. Cruise works well in the car; Cruise Wheel detects the buttons but does not
carry out the mapping, does not stay running, its screens are hard to use, and it crashes on "Start listening".
APKs: `D:\dev\cruise-builds\cruise-1.2.1*.apk`, `cruise-wheel-0.2.0.apk`.

## CR-17 — No scan screen after the first start

**Status: ✅ Done · 🚗 needs the car**
- **Why it showed every time:** at ignition-off the unit force-stops every app (see CR-19), so each start was a cold
  start. Now Playing showed "Finding your music… N songs found" until the player connected, and the start-up rescan was
  presented like a first scan.
- Cruise keeps a small snapshot of the song on screen; on a cold start Now Playing opens on it at once, and play/pause,
  next, previous and seek pressed before the player is ready run as soon as it is.
- Rescans of a library that already has music (start-up, USB stick mounted) are **quiet**: no count in the Library header,
  no scan screen; they start 8 s after launch on one low-priority thread and read only new or changed files (as before).
- "Finding your music" with a count is only for the very first scan; a fresh queue is built from the known library at
  once instead of waiting for the rescan. A manual rescan (Library, Settings) still shows its progress.

## CR-18 — Cruise Wheel redesign and working remapping

**Status: ✅ Done · 🚗 needs the car**
- **Home:** the driver's buttons as cards (icon, name, what press / hold / double press do); a card **lights up when its
  button is pressed** on the wheel; an unknown button offers "New button: … Set it up"; "Add a button" listens for the
  next press (or picks from the list on devices without a wheel). Status chip: Active / Waiting for the unit / Stopped.
  A "Finish setting up" card lists what is missing (stay on at ignition-off, accessibility helper) with one-tap fixes.
- **Button screen:** Press, Hold and Double press, each with its action; the line lights up when it runs. An honest note
  says how far the unit lets the button go: *fully yours* (Phone, Hang up), *closed by the helper* (Sound mixer), or
  *shared* (media, volume…: the unit's own action cannot be switched off, so hold and double press are the free ones).
- **Action picker:** large tiles by category — Music (play/pause, next, previous, fast forward, rewind, stop), Volume,
  Calls (answer / end, reject), Apps (open the music app, open any app — with app icons), Screen (home, back, recent apps,
  notifications; voice assistant only when the unit has one), Other (do nothing, unit default).
- **Settings:** stay on at ignition-off, helper, music actions target (Cruise / whatever is playing / another app),
  phone-button call handling, export / import, and under Advanced: car report, **key log** (every raw MCU message, to
  learn what the unit sends for presses and holds), diagnostics.
- **Why the mapping did not act:** besides the service being stopped (CR-19), media actions went through Android's
  media-key routing, which goes to whichever app played last. They now go **straight to Cruise** (its Media3 session
  service; when Cruise is not running, play starts it through its media-button receiver), with the system routing as the
  fallback and for other music apps.
- Engine: a run of hold messages is one hold, and a short press reported right after a hold of the same button is its
  release, not a tap (the unit's exact behaviour is still to be read in the key log). Combinations stay in the engine
  and the mapping file but are no longer offered in the picker (no press/release events to make them reliable).
- English and French; screenshot tests for the wheel screens (`gradlew :wheel:recordRoborazziDebug`).

## CR-19 — Cruise Wheel always running

**Status: ✅ Done · 🚗 needs the car**
- **Why it stopped:** `com.tw.service` (`TWService.A()`, on the MCU's ignition-off message) **force-stops every app not
  on its own list** unless the system property `persist.tw.kill` is non-zero. A force-stopped app gets no broadcast, not
  even boot, until it is opened by hand. The property is not set on the owner's unit (device report).
- "Stay on when the car is switched off" sets `persist.tw.kill=1` (the unit runs SELinux permissive, so an app may set
  it); asked once in the setup card, re-applied whenever the service starts, undone by switching it off. If the unit
  refuses, the app says so and relies on the other starts below.
- The service also starts: with the unit (boot), when a USB stick is mounted, on the unit's ignition broadcast, **when
  Cruise starts** (explicit broadcast that reaches a force-stopped app), whenever the app's process starts (including the
  system binding the accessibility helper), and two seconds after its task is swiped away.
- The phone-button hand-over flag is renewed on ignition-on and after every phone press, not only on screen-on.

## CR-20 — Car report: crash on "Start listening"

**Status: ✅ Done · 🚗 needs the car**
- 0.1.0 opened the MCU link with **4,096 message ids** in one call (and retried on the same instance); the vendor apps
  never pass more than 23. The crash was in native code, which no Kotlin `try` can catch.
- Now: the 55 ids the unit's own apps subscribe to (read from their code), spread over three link instances of at most
  20 ids, each opened once. `TwUtilBridge.open` refuses more than 24 ids.
- The probe's message log is locked while the report is written on a background thread, and the settings/properties
  snapshots of each step no longer run on the main thread.

## CR-21 — Phones keep playing with the screen off

**Status: ✅ Done · 👀 owner review** — the owner also uses Cruise on a phone (2026-10-24): the music stopped when the
phone's screen went off.
- **Why:** the car behaviour applied everywhere: 10 s after screen-off Cruise paused "for the ignition", and a pause
  just before screen-off was resumed at screen-on.
- Cruise now tells a head unit from a phone (TW unit, Android car mode, or no battery). On a phone: no pause at
  screen-off ("Pause when the car switches off" defaults to Never), no resume at unlock, and no resume after a reboot
  (it would start music in a pocket). Head units keep the car defaults; a choice made in Settings always wins.

## CR-22 — Neon City: painted backdrop, and the skin toned to match it

**Status: ✅ Done · 👀 owner review** — owner (2026-10-24): the drawn Neon City background is "not that cool".
- **Round 1** (12 images, six scenes, Z-Image Turbo): picked a rainy rooftop over a pink-lit skyline and released it in
  1.2.1 — the owner did not like it.
- **Round 2** from three references the owner sent (a flat teal vector skyline with rooftop light beams; a foggy
  megacity with giant holographic ad pillars seen from a hill; a foggy elevated walkway): four directions × six images
  (four Z-Image Turbo, two Krea-2 Turbo; 1344 × 768; seeds 31000 + 101 × ord(direction) + 7 × n). The owner liked
  **D_5 / D_6** (Krea-2: teal fog over dark rooftops, searchlights from tower tops, one holographic column); D_5 is used,
  being darker and calmer behind the title.
  Prompt: *night panorama over a dark futuristic city drowned in teal fog, tall towers with glowing rooftop lights
  sending beams of light into the sky, one enormous glowing holographic column in the distance, faint pink neon accents,
  dark foreground rooftops in silhouette, cinematic concept art, very atmospheric, dark sky*, plus the full-bleed /
  no-text clause.
- **The skin follows the picture** (owner: the neon parts and the plane would not fit and the colours would be too
  bright):
  - Palette sampled from the backdrop: teal-black backgrounds, the searchlights' soft teal `#6FDDE2` instead of
    electric cyan, the orchid `#D66AB4` of its pink sign instead of hot pink, lamp amber.
  - Billboard: cool dark steel, the screen's light spilling softly into the haze, the scaffold and roof fading into a
    fog bank like the buildings, a soft trim instead of bright tubes, gentler lamp cones and roof reflection.
  - Plane: a real plane at night instead of a neon drawing — dark silhouette lit from below by the city, teal rim
    light, warm cockpit, red beacon, green wing light, white tail light, faint landing light. Banner: dark cloth, soft
    lettering, title pale teal and artist orchid.
  - Background motion: slow drifting fog banks instead of light streaks on a street that is no longer there.
- `bg_neon.webp` (1280 px, 26 KB); the drawn city remains only as a fallback.
- Owner's review of the first restyle: the title was still bright, and both the city and the plane should move.
  - Title muted (teal toward grey, less halo, core no longer near-white).
  - The city, while music plays: the searchlights on the painted towers sweep the sky slowly, a band of light glides
    up the holographic column, the antennas' red lights breathe, fog banks drift. Positions are measured on the picture
    and mapped through the same crop as the image, so they stay on the towers on any screen shape.
  - The plane flies: the tow sways and climbs gently with a slight bank, wind streaks rush past and fog wisps slide
    behind it. Nothing flashes; all of it stops when the music is paused and in the Saver quality.

- Owner's review on the emulator: the banner was still too bright and its black cloth did not fit; more fog wanted.
  - Banner: thin see-through cloth lit from below by the city (the holographic column shows through), quieter
    lettering, a faint edge instead of an outline.
  - Fog (second try — the owner could not see the first, and it must move like real fog, not loop back and forth):
    two fog textures made once off the main thread from fractal noise warped through itself (wisps and curls, not
    blobs), thick low down and thinning upwards; three layers drift one way only — far haze barely moving, nearer wisps
    faster — each at its own size so the whole never shows a repeat, and each thickens and thins over about a minute.
    `SkinsScreenshotTest.skin_neon_fog_motion` captures it 20 s apart.
  - The holographic column's light showed as a box beyond the tower (owner's screenshot): the flat shimmer is gone, and
    the travelling light is a soft lens clipped to the tower's measured body with its rounded cylinder top.

## CR-23 — Vinyl Lounge: slower record, lighter grooves

**Status: ✅ Done · 👀 owner review** — owner (2026-10-24): the disc spins too fast, and the rings over the cover are too
strong.
- One turn in five seconds (72°/s) instead of a real 33⅓ rpm (200°/s).
- Grooves further apart and about a third as dark, lighter gaps between songs, a softer fixed reflection: the album
  art stays the picture.

## CR-24 — Hands-on record and tape

**Status: ✅ Done · 👀 owner review** — owner (2026-10-24): move the record like a DJ, and a tape effect on the cassette.
- **Vinyl Lounge:** a finger on the record stops it — the sound brakes (speed and pitch slide down, then silence).
  Turning it forward plays from where it was grabbed at the speed and pitch of the hand (one turn = 1.8 s of music, as on
  a real record; 200°/s is normal speed); turning it backward rewinds; holding still is silence. Letting go spins it
  back up, the pitch rising to normal, if it was playing.
- **Tape Deck:** pressing the cassette stops the tape (a slower, deeper slide); pulling it right cues forward at up to
  2.6× speed with the pitch raised (the fast-forward chirp); pulling it left rewinds; letting go starts the tape with a
  little wow. The reels follow the finger.
- The player cannot play audio backwards, so backward movement rewinds silently.
- The whole-screen gestures (hold = play/pause, swipes) step aside while a finger is on the record or the cassette.
- Speed and pitch always return to normal when the finger lifts, whatever interrupts an effect.

## CR-25 — Covers with bars around the picture

**Status: ✅ Done · 👀 owner review** — owner (2026-10-24, screenshot): some songs' art did not cover the whole record.
- That art carries flat bars around the picture (common in art taken from videos). When art is decoded, rows and
  columns at the edges that are one flat colour are trimmed (at most a third of each side, slivers ignored), so every
  skin crops the picture itself. A cover whose design has a real flat border loses only that border.

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
| 10 | Second car test fixes: quiet rescans and instant Now Playing; Cruise Wheel redesign, direct music control, always running, probe crash; phones keep playing with the screen off; Neon City painted backdrop | CR-17–CR-22 | **Cruise 1.2.1** + **Cruise Wheel 0.2.0** APKs | ✅ Built · 🚗 needs the car |

Every step keeps the build, unit tests, lint and the JVM screenshot tests green.

### Needs the car later
- CR-19: whether the unit accepts `persist.tw.kill` from an app, and that both apps are still running after an
  ignition-off / on.
- CR-18: the key log while pressing and holding each button (does the unit repeat the hold message, report releases?),
  and each action from the wheel with Cruise playing.
- CR-10: whether the MCU sends press and release separately (combinations, hold), volume control path on the TW unit, the
  probe's guided capture.
- CR-03/CR-04: scan speed and folder collapse on the owner's real USB stick.
