#!/usr/bin/env python3
"""Fail packaging regressions that bring phone-only extraction/export payloads into Wear."""
import argparse
import json
import subprocess
import struct
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('apk', type=Path)
parser.add_argument('--release', action='store_true')
parser.add_argument('--aapt2', type=Path)
args = parser.parse_args()
with zipfile.ZipFile(args.apk) as apk:
    native = [entry for entry in apk.infolist() if entry.filename.startswith('lib/') and entry.filename.endswith('.so')]
    forbidden = ('libavcodec', 'libavdevice', 'libavfilter', 'libavformat', 'libavutil', 'libffmpeg', 'libpython', 'libswresample', 'libswscale')
    unexpected = [entry.filename for entry in native if Path(entry.filename).name.startswith(forbidden)]
    if unexpected:
        raise SystemExit('Phone-only payloads found in Wear: ' + ', '.join(unexpected))
    if not any(Path(entry.filename).name == 'libj2v8.so' for entry in native):
        raise SystemExit('Watch SmartTube/V8 fallback library is missing')
    if args.release:
        incompatible = []
        for entry in native:
            elf = apk.read(entry)
            if elf[:4] != b'\x7fELF':
                raise SystemExit('Invalid native library: ' + entry.filename)
            if elf[4] != 2:
                continue  # The 16 KB requirement applies to 64-bit binaries.
            endian = '<' if elf[5] == 1 else '>'
            offset = struct.unpack_from(endian + 'Q', elf, 32)[0]
            stride, count = struct.unpack_from(endian + 'HH', elf, 54)
            for i in range(count):
                header = struct.unpack_from(endian + 'IIQQQQQQ', elf, offset + i * stride)
                if header[0] == 1 and (header[7] < 16384 or (header[2] - header[3]) % 16384):
                    incompatible.append(entry.filename)
                    break
        if incompatible:
            raise SystemExit('64-bit native libraries incompatible with 16 KB pages: ' + ', '.join(incompatible))
        # Incremental APK writers can leave large gaps after removing old native libraries.
        gap = args.apk.stat().st_size - sum(entry.compress_size for entry in apk.infolist())
        if gap > 2 * 1024 * 1024 and gap > args.apk.stat().st_size * .05:
            raise SystemExit('Release APK contains excessive padding; clean the Wear build and repackage')
        if args.aapt2 is None:
            raise SystemExit('--release requires --aapt2 to verify the actual manifest')
        manifest = subprocess.check_output([str(args.aapt2), 'dump', 'xmltree', str(args.apk), '--file', 'AndroidManifest.xml'], text=True)
        if 'PreviewActivity' in manifest:
            raise SystemExit('Debug preview activity found in release manifest')
    print(json.dumps({'apk_bytes': args.apk.stat().st_size, 'native_bytes': sum(entry.file_size for entry in native),
        'abis': sorted({entry.filename.split('/')[1] for entry in native}), 'phone_only_payloads': False}, indent=2))
