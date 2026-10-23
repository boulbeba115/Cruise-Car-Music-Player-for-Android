"""Builds the test music library (SPEC §9) with FFmpeg: every format Cruise must handle, real tags and covers.

usage: python tools/make_test_library.py OUTPUT_DIR [--ffmpeg PATH]

The audio is synthetic (chords), so the library is free of copyright and safe to commit or share.
"""
import argparse
import shutil
import subprocess
from pathlib import Path

CHORDS = [(220, 277, 330), (196, 247, 294), (174, 220, 262), (247, 311, 370), (262, 330, 392)]


def run(ffmpeg, *args):
    subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def tone(index, seconds):
    a, b, c = CHORDS[index % len(CHORDS)]
    expr = (f"0.25*sin(2*PI*{a}*t)+0.15*sin(2*PI*{b}*t)*(0.6+0.4*sin(2*PI*0.5*t))|"
            f"0.25*sin(2*PI*{c}*t)+0.15*sin(2*PI*{b}*t)*(0.6+0.4*cos(2*PI*0.5*t))")
    return ["-f", "lavfi", "-i", f"aevalsrc='{expr}':s=48000:d={seconds}"]


def cover(ffmpeg, path: Path, seed: int):
    filters = ["mandelbrot=s=600x600", "testsrc2=s=600x600", "cellauto=s=600x600:rule=110", "life=s=600x600:mold=10"]
    run(ffmpeg, "-f", "lavfi", "-i", filters[seed % len(filters)], "-frames:v", "1", "-q:v", "3", str(path))


def track(ffmpeg, out: Path, index: int, seconds: int, codec_args, tags=None, art: Path | None = None):
    out.parent.mkdir(parents=True, exist_ok=True)
    args = tone(index, seconds)
    if art:
        args += ["-i", str(art), "-map", "0:a", "-map", "1:v", "-c:v", "mjpeg", "-disposition:v", "attached_pic"]
    args += codec_args
    for key, value in (tags or {}).items():
        args += ["-metadata", f"{key}={value}"]
    run(ffmpeg, *args, str(out))


def album(ffmpeg, root, artist, title, year, ext, codec_args, count, embed_art, folder_art, seed):
    folder = root / "Music" / artist / f"{year} - {title}"
    art = root / f".art_{seed}.jpg"
    cover(ffmpeg, art, seed)
    for i in range(count):
        name = f"{i + 1:02d} - Track {chr(65 + i)} of {title}"
        tags = {"title": f"Track {chr(65 + i)}", "artist": artist, "album_artist": artist, "album": title,
                "date": str(year), "track": f"{i + 1}/{count}", "genre": "Test"}
        track(ffmpeg, folder / f"{name}.{ext}", seed + i, 20, codec_args, tags, art if embed_art else None)
    if folder_art:
        shutil.copy(art, folder / "folder.jpg")
    art.unlink()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("output")
    parser.add_argument("--ffmpeg", default=r"D:\dev\ffmpeg\ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe")
    a = parser.parse_args()
    root, ff = Path(a.output), a.ffmpeg
    root.mkdir(parents=True, exist_ok=True)

    album(ff, root, "Aurora Lane", "Night Drive", 2021, "flac", ["-c:a", "flac", "-sample_fmt", "s16", "-ar", "44100"], 3, True, False, 0)
    album(ff, root, "Aurora Lane", "Coastline", 2019, "mp3", ["-c:a", "libmp3lame", "-b:a", "320k", "-id3v2_version", "3"], 3, True, False, 1)
    album(ff, root, "The Meridians", "Hi-Res Sessions", 2023, "flac", ["-c:a", "flac", "-sample_fmt", "s32", "-ar", "96000"], 2, False, True, 2)
    album(ff, root, "The Meridians", "Apple Lossless", 2022, "m4a", ["-c:a", "alac", "-ar", "44100"], 2, True, False, 3)

    mixed = root / "Music" / "Mixed Formats"
    for i, (ext, args) in enumerate([
        ("m4a", ["-c:a", "aac", "-b:a", "256k"]),
        ("ogg", ["-c:a", "libvorbis", "-q:a", "6"]),
        ("opus", ["-c:a", "libopus", "-b:a", "160k"]),
        ("wav", ["-c:a", "pcm_s16le", "-ar", "44100"]),
    ]):
        track(ff, mixed / f"{i + 1:02d} - {ext.upper()} sample.{ext}", i, 15, args,
              {"title": f"{ext.upper()} sample", "artist": "Format Lab", "album": "Mixed Formats"})
    track(ff, mixed / "05 - WavPack (Phase 6).wv", 4, 10, ["-c:a", "wavpack"])

    untagged = root / "Music" / "Compilations" / "Été 2024 (Mix)"
    for i, name in enumerate(["01 - Café del Mar", "02 - Señorita", "10 - Track ten", "2 - Track two"]):
        track(ff, untagged / f"{name}.mp3", i, 12, ["-c:a", "libmp3lame", "-b:a", "192k", "-map_metadata", "-1"])
    print("library ready:", root)


if __name__ == "__main__":
    main()
