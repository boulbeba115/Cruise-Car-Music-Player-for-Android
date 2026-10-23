"""Builds the tag-reader test fixtures (core/data/src/test/resources/fixtures) with FFmpeg.

usage: python tools/make_tag_fixtures.py [--ffmpeg PATH]

Each file is a fraction of a second of synthetic audio carrying the tags, cover and stream properties the unit tests
check, so they stay small enough to commit.
"""
import argparse
import subprocess
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "core/data/src/test/resources/fixtures"

TAGS = {
    "title": "Café del Mar",
    "artist": "Artist A",
    "album": "Album Ä",
    "album_artist": "Various Artists",
    "date": "2019",
    "track": "3/12",
    "disc": "1/2",
    "genre": "Chillout",
}
GAIN = {
    "REPLAYGAIN_TRACK_GAIN": "-6.50 dB",
    "REPLAYGAIN_TRACK_PEAK": "0.987654",
    "REPLAYGAIN_ALBUM_GAIN": "-7.25 dB",
    "REPLAYGAIN_ALBUM_PEAK": "0.999000",
}


def run(ffmpeg, *args):
    subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def make(ffmpeg, name, seconds, rate, codec, tags, art=None, extra=()):
    args = ["-f", "lavfi", "-i", f"sine=frequency=440:sample_rate={rate}:duration={seconds}"]
    if art:
        args += ["-i", str(art), "-map", "0:a", "-map", "1:v", "-c:v", "mjpeg" if art.suffix == ".jpg" else "png",
                 "-disposition:v", "attached_pic"]
    args += ["-ac", "2", *codec, *extra]
    for k, v in tags.items():
        args += ["-metadata", f"{k}={v}"]
    run(ffmpeg, *args, str(OUT / name))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--ffmpeg", default=r"D:\dev\ffmpeg\ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe")
    ffmpeg = p.parse_args().ffmpeg
    OUT.mkdir(parents=True, exist_ok=True)
    art = OUT / "cover.jpg"
    run(ffmpeg, "-f", "lavfi", "-i", "testsrc2=s=64x64", "-frames:v", "1", "-q:v", "8", str(art))

    make(ffmpeg, "id3v24.mp3", 1, 44100, ["-c:a", "libmp3lame", "-b:a", "128k"], {**TAGS, **GAIN}, art, ["-id3v2_version", "4"])
    make(ffmpeg, "id3v23.mp3", 1, 44100, ["-c:a", "libmp3lame", "-b:a", "96k"], TAGS, None, ["-id3v2_version", "3", "-write_id3v1", "1"])
    make(ffmpeg, "vorbis.flac", 1, 44100, ["-c:a", "flac"],
         {**TAGS, **GAIN, "LYRICS": "[00:01.00]First line\n[00:03.50]Second line", "COMPILATION": "1"}, art)
    make(ffmpeg, "hires.flac", 0.3, 96000, ["-c:a", "flac", "-sample_fmt", "s32", "-bits_per_raw_sample", "24"], TAGS)
    make(ffmpeg, "aac.m4a", 1, 44100, ["-c:a", "aac", "-b:a", "96k"], TAGS, art)
    make(ffmpeg, "alac.m4a", 0.5, 44100, ["-c:a", "alac"], TAGS)
    make(ffmpeg, "vorbis.ogg", 1, 44100, ["-c:a", "libvorbis", "-q:a", "2"], {**TAGS, **GAIN})
    make(ffmpeg, "opus.opus", 1, 48000, ["-c:a", "libopus", "-b:a", "48k"], TAGS)
    make(ffmpeg, "info.wav", 0.2, 44100, ["-c:a", "pcm_s16le"], {"title": "Wave Title", "artist": "Wave Artist", "album": "Wave Album"})
    art.unlink()
    for f in sorted(OUT.iterdir()):
        print(f"{f.name:14} {f.stat().st_size:>8} bytes")


if __name__ == "__main__":
    main()
