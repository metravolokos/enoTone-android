#!/usr/bin/env python3
"""Conservative M0 gate: scan resolved runtime dependencies and packaged APK payloads.

This is not a full class dependency analyzer; APK members include compiled manifest and DEX.
"""
import pathlib
import sys
import zipfile

FORBIDDEN = (
    "com.google.android.gms",
    "com.google.firebase",
    "com.google.android.play",
)

def suspicious(data: bytes):
    for package in FORBIDDEN:
        aliases = (package, package.replace(".", "/"))
        for alias in aliases:
            b = alias.encode("utf-8")
            if b in data or alias.encode("utf-16-le") in data:
                return package
    return None

def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: check_no_gms.py DEPENDENCIES.txt APK")
    depfile, apkfile = (pathlib.Path(x) for x in sys.argv[1:])
    dependency_hit = suspicious(depfile.read_bytes())
    if dependency_hit:
        raise SystemExit("Forbidden runtime dependency: " + dependency_hit)
    with zipfile.ZipFile(apkfile) as archive:
        for name in archive.namelist():
            filename_hit = suspicious(name.encode("utf-8"))
            if filename_hit:
                raise SystemExit("Forbidden APK entry: " + filename_hit)
            # All dex and manifest bytes are scanned, not only the manifest's human-readable text.
            hit = suspicious(archive.read(name))
            if hit:
                raise SystemExit("Forbidden APK content in " + name + ": " + hit)
    print("GMS dependency and APK byte scan: OK (heuristic gate)")

if __name__ == "__main__":
    main()
