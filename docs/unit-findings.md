# Owner's head unit — findings from the car test

Collected 2026-10-22 with Cruise 0.0.3 (Settings → Car test), analysed 2026-10-23.
Raw data: `inputs/cruise-fieldtest/`, `inputs/logcat.bin`, photos in `inputs/`.

## Hardware and system

| | Displayed by the unit | **Measured** |
|---|---|---|
| Android | 13.0 | **8.1.0 (API 27)**, build `OPM2.171019.012`, `user/release-keys` |
| SoC | "Quad-SL8141E" | **Spreadtrum SC7731E**, 4 × Cortex-A7 @ 1.3 GHz, 32-bit only (`armeabi-v7a`) |
| GPU | — | Mali-T820, OpenGL ES 3.2 |
| RAM | 4 GB | **1 GB** (≈ 370 MB free), app heap 128 MB (large 256 MB) |
| Screen | 1280×720 | 1280×720 @ 160 dpi → 1280×720 dp, **app window 1280×660 dp**, **system font scale 1.15** |
| Platform | "TS7" | Vendor stack **`com.tw.*`** ("TW" platform, MCU `Ts7.4.6-100-9B1-A3A39D-241101`) + Dofun launcher (`com.dofun.*`) |
| Root / adb | — | No root, adb off, adbd stopped, `ro.secure=1` → **no remote debugging from the PC** |

## Audio

- Output: **44.1 kHz, 16-bit PCM only** (`PROPERTY_OUTPUT_SAMPLE_RATE` 44100, 960 frames/buffer). The
  analog path is SoC codec → **PT2313** volume/tone processor → amplifier. Android sees it as `WIRED_HEADPHONES`
  (a `HEADSET_PLUG state=1` broadcast arrives at boot).
- Float `AudioTrack` works at 44.1/48/96/192 kHz (the mixer converts), so the DSP chain stays float and the
  last stage resamples to **44.1 kHz** and **dithers to 16 bit** by default.
- USB audio HAL is **disabled** in `audio_policy_configuration.xml` → no USB DAC possible (DSP-18 dropped for this unit).
- Platform decoders: AAC, MP3 (vendor `OMX.sprd.mp3`), Vorbis, Opus, AMR, PCM. **No FLAC.**
  The Jellyfin FFmpeg decoder (`armeabi-v7a` included) adds FLAC, ALAC, WavPack, DSD, WMA, TrueHD, E-AC-3.
- Speaker check: left/right correct.

## Performance (measured on the unit)

| Test | Result | Consequence |
|---|---|---|
| Compose animation (64 animated bars + moving gradient) | **54 fps**, p99 33.6 ms, 9 % frames > 33 ms | Visualizer and ambient effects must be lighter by default on this class (performance mode on) |
| 10-band EQ, stereo, Kotlin | 40× real time = **2.5 % of a core** | Whole DSP chain fits comfortably in Kotlin; C++ only if a later stage needs it |
| 16-band parametric EQ | 5 % of a core | fine |
| Look-ahead limiter | 2.1 % | fine |
| FFT 2048 / hop 1024 | 8.5 % of a core | Visualizer: FFT 1024, ≤ 30 updates/s |

## Storage

- USB stick mounts at **`/storage/usbdisk0`** (exFAT, label "C:", uuid `usbdisk0`); raw mount
  `/mnt/media_rw/usbdisk0`. Cruise can read **and write its root** (results were saved to `/storage/usbdisk0/cruise-fieldtest`).
- The stock music app also hard-codes `/storage/usbdisk0` and `/storage/extsd` (SD card).
- MediaStore held 265 audio entries, none on the stick used for the test (it was empty).

## Steering-wheel keys (CAN-bus) — how they work on this unit

No key events reached Cruise during the test: **the buttons never become Android key events by
themselves.** Decompiling the vendor apps shows why:

```
steering wheel → CAN box → MCU → android.tw.john.TWUtil (vendor framework class)
      → com.tw.service (system app) → routed by the CURRENT SOURCE "au"
```

`com.tw.service` (`HandlerC0011l`) translates each key according to the current source:

| Current source (`au`) | Next-track key becomes |
|---|---|
| **0** | `InteractionController.sendKey(87)` → a **standard `KEYCODE_MEDIA_NEXT`** (and likewise for the other keys) |
| 1 (radio) | `TWUtil.write(40449, …)` |
| **3 (built-in music)** | `TWUtil.write(40451, 3)` |
| 4 / 8 / 9 / 10 | `40452` / `40456` / `40457` / `40458` (video, BT music, …) |

During the test the source was **3** (the stock music app was playing), so the keys went to it.

### The TWUtil protocol (from `com.tw.media` and `com.tw.service`)

- `android.tw.john.TWUtil` is in the system framework. **Non-system apps can use it**: `com.tw.keypad`
  (no shared system uid) opens it with `new TWUtil(17)` + `open(short[] ids)` + `start()`.
- Messages arrive on a `Handler` registered with `addHandler(name, handler)`; `msg.what` = message id.
- **Source**: message `769` (0x301) from the MCU carries the current source in `arg1`.
  An app requests a source with `write(40465, 192, source)` → the service forwards `write(769, 192, source)`;
  `write(40465, 192, 128 | source)` releases it.
- The stock music app on resume: `write(40448, 3)`, `write(40465, 192, 3)`, `write(769, 255)`;
  on destroy: `write(40704, 3, 0)`, `write(40465, 192, 131)`.
- **Music commands, message `40451` (0x9E03), `arg1`:** 1 play/pause · 2 save state · 3 next · 4 previous ·
  5 play · 6 pause · 7 duck 50 % · 8 unduck · 9 / 10 (fast-forward/rewind or similar) · 11 mute · 12 unmute.
  On ignition-off the service sends `2` then `6` (save, pause) to the current source.
- **Now-playing to the MCU** (each second): `write(40704, 3, (playing ? 128 : 0) | percent, title)`,
  `write(771, 3, …)`, and `write(1282, …)` with track number / count / time.
- **Day/night from the headlights**: `com.tw.service` broadcasts `com.zjinnova.zlink.action.OUT_DARK_START`
  / `OUT_DARK_STOP` (for the CarPlay app) — Cruise can listen to the same actions for automatic night mode.

### Plan for Cruise (Phase 1, `core:car` TW adapter)

1. Detect the platform: `Class.forName("android.tw.john.TWUtil")`.
2. When playback starts, open TWUtil by reflection and request **source 0** → keys arrive as standard media
   keys in Cruise's Media3 session (no vendor code in the key path); log the `769` replies.
3. Fallback if the MCU keeps another source: claim **source 3** like the stock player and map `40451`
   commands (incl. duck/mute/save) onto the player.
4. Optional: report now-playing to the MCU (`40704`/`771`/`1282`) so the unit's own UI shows the track.
5. Everything behind an interface; units without TWUtil use plain Media3 key handling.

## Power

- Since its last boot the unit had run 4.2 days of real time but only 4.9 h of uptime: it **deep-sleeps**
  with the ignition off and had **not rebooted in four days** (the long-sleep shutdown did not happen in
  that period).
- The ignition-off/on step of the test was not recorded (no screen-off seen); Cruise's design handles both
  sleep-resume and cold boot, verified on the emulator.
- GPS works offline: 5–6 satellites, 5–10 m accuracy.

## Steering-wheel buttons and calls (analysis 2026-10-23)

- `com.tw.service` key table (message 513 `arg2`): 5/19 next, 6/21 previous, 20 play/pause, 22/23 ff/rew,
  **24 phone** (opens `com.tw.bt` unless a CarPlay/Android Auto call is ringing), **25 hang up** (CarPlay/AA only),
  **66 sound mixer** (opens `com.tw.eq.EQActivity`), 16 home, 18 back, 32 navigation, 1/2/3 volume/mute.
- Why Bluetooth calls cannot be answered from the wheel: the service's "answer" path only broadcasts to zlink
  (CarPlay/AA). The Bluetooth app (`BTModel`) answers on key 24 only in a narrow state (`is == 2 && iF == 1`).
- `com.tw.bt` answers/ends calls with Android's hands-free client: `acceptCall(device, 0)`, `rejectCall(device)`,
  `terminateCall(device, null)`; call state from `headsetclient … AG_CALL_CHANGED`.
- The unit's learning screen (`com.tw.keypad`) only handles analog (resistive) buttons on the TS7; CAN buttons
  cannot be remapped in the unit.
- When an outside app takes audio focus, `com.tw.service` switches to **source 0** (`I.java`), where keys
  5/6/19/20/21 are sent as standard `KEYCODE_MEDIA_*` — confirmed by a `KEYCODE_MEDIA_PAUSE` in the exported log.
- Cruise 0.1.0 therefore: listens to raw keys through TWUtil, answers/rejects/hangs up through the hands-free client,
  maps 24/66 to music actions (defaults: 24 → play/pause, 66 → next folder / long: shuffle), and offers an
  accessibility helper that closes `com.tw.bt` / `com.tw.eq` when they open after a remapped press.
