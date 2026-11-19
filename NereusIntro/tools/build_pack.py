#!/usr/bin/env python3
"""
Membuat resource pack musik NereusIntro dari file audio (mp3/wav/m4a/ogg) memakai ffmpeg.

Pemakaian:
    python3 build_pack.py --whiteroom lagu_ruang_putih.mp3 --nereus lagu_nereus.mp3 [--out folder]

Hasil:
    Nereus-Intro-Java.zip      -> pack Java (namespace sendiri: nereusintro, tidak bentrok dengan pack lain)
    Nereus-Intro-Bedrock.mcpack-> pack Bedrock untuk Geyser
    (dicetak juga panjang tiap lagu dalam detik untuk diisi ke `length:` di config.yml)
"""
import argparse, json, os, subprocess, sys, zipfile, shutil, tempfile

TRACKS = {"whiteroom": "music.whiteroom", "nereus": "music.nereus"}


def to_ogg(src, dst):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vn", "-c:a", "libvorbis", "-q:a", "4",
                    "-ar", "44100", dst], check=True)


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         check=True, capture_output=True, text=True).stdout.strip()
    return float(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--whiteroom", help="lagu untuk ruang putih")
    ap.add_argument("--nereus", help="lagu untuk adegan terakhir di Nereus")
    ap.add_argument("--out", default=".")
    a = ap.parse_args()
    inputs = {k: getattr(a, k) for k in TRACKS if getattr(a, k)}
    if not inputs:
        ap.error("isi minimal satu lagu: --whiteroom dan/atau --nereus")
    os.makedirs(a.out, exist_ok=True)
    tmp = tempfile.mkdtemp()
    oggs, lengths = {}, {}
    try:
        for k, src in inputs.items():
            dst = os.path.join(tmp, k + ".ogg")
            to_ogg(src, dst)
            oggs[k] = dst
            lengths[k] = duration(dst)

        # ---- Java
        java = os.path.join(a.out, "Nereus-Intro-Java.zip")
        sounds = {}
        for k in oggs:
            sounds[TRACKS[k]] = {"category": "record",
                                 "sounds": [{"name": f"nereusintro:{k}", "stream": True,
                                             "attenuation_distance": 4096, "preload": True}]}
        with zipfile.ZipFile(java, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 75,
                       "supported_formats": {"min_inclusive": 34, "max_inclusive": 999},
                       "description": "Nereus Intro music"}}, indent=2))
            z.writestr("assets/nereusintro/sounds.json", json.dumps(sounds, indent=2))
            for k, f in oggs.items():
                z.write(f, f"assets/nereusintro/sounds/{k}.ogg")

        # ---- Bedrock
        bed = os.path.join(a.out, "Nereus-Intro-Bedrock.mcpack")
        defs = {}
        for k in oggs:
            entry = {"category": "record", "sounds": [{"name": f"sounds/nereusintro/{k}", "stream": True, "volume": 1.0}],
                     "min_distance": 0, "max_distance": 0}
            for name in (f"nereusintro:{TRACKS[k]}", f"nereusintro.{TRACKS[k]}", TRACKS[k]):
                defs[name] = entry
        manifest = {"format_version": 2,
                    "header": {"name": "Nereus Intro", "description": "Intro music",
                               "uuid": "5d1c7a0e-3b6f-4c1a-9d2e-7a4b8c0f1e21", "version": [1, 0, 0],
                               "min_engine_version": [1, 21, 0]},
                    "modules": [{"type": "resources", "uuid": "a93e4f6b-12c8-4d57-b0a3-6e9f2d8c4b17",
                                 "version": [1, 0, 0]}]}
        with zipfile.ZipFile(bed, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("manifest.json", json.dumps(manifest, indent=1))
            z.writestr("sounds/sound_definitions.json",
                       json.dumps({"format_version": "1.14.0", "sound_definitions": defs}, indent=1))
            for k, f in oggs.items():
                z.write(f, f"sounds/nereusintro/{k}.ogg")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print("Selesai:\n  ", java, "\n  ", bed)
    for k, sec in lengths.items():
        print(f"  {k}: panjang {sec:.1f} detik -> isi 'length: {sec:.1f}' pada langkah music {TRACKS[k]}")


if __name__ == "__main__":
    sys.exit(main())
