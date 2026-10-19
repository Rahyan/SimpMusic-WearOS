#!/usr/bin/env python3
"""Create verified, independently installable ARM APKs from one minified build."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile


def run(*command):
    return subprocess.check_output([str(part) for part in command], text=True, stderr=subprocess.STDOUT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wear', type=Path, required=True)
    parser.add_argument('--companion', type=Path, required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--keystore', type=Path, required=True)
    parser.add_argument('--alias', required=True)
    parser.add_argument('--output', type=Path, default=Path('build/release'))
    parser.add_argument('--allow-test-key', action='store_true', help='Local verification only; never publish these outputs')
    args = parser.parse_args()
    for name in ['KEYSTORE_PASSWORD', 'KEY_PASSWORD']:
        if not os.environ.get(name):
            parser.error(f'Set {name} in the environment')
    if not args.keystore.is_file() or not args.keystore.stat().st_size:
        parser.error('Signing keystore is missing or empty')
    # Do not leave a previous build mixed into a new release directory.
    if args.output.exists() and any(args.output.iterdir()):
        parser.error('Output directory must be empty; choose a new directory for each release')
    tools = args.build_tools
    aapt = tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
    align = tools / ('zipalign.exe' if os.name == 'nt' else 'zipalign')
    signer = tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    for tool in [aapt, align, signer]:
        if not tool.is_file():
            parser.error(f'Missing SDK tool: {tool.name}')

    def identity(apk):
        badging = run(aapt, 'dump', 'badging', apk)
        manifest = run(aapt, 'dump', 'xmltree', apk, '--file', 'AndroidManifest.xml')
        if 'PreviewActivity' in manifest:
            raise ValueError('Debug preview activity found in release manifest')
        line = next(line for line in badging.splitlines() if line.startswith('package:'))
        info = dict(re.findall(r"(\w+)='([^']*)'", line))
        if 'application-debuggable' in badging:
            raise ValueError('Release APK must not be debuggable')
        return {field: info[field] for field in ['name', 'versionCode', 'versionName']}

    wear_identity = identity(args.wear)
    if identity(args.companion) != wear_identity:
        raise ValueError('Watch and companion package/version must match')
    if not args.allow_test_key and re.search(r'\.(test|dev|debug)(\.|$)', wear_identity['name']):
        raise ValueError('Test package cannot be packaged as a public release')
    version = wear_identity['versionName']
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', version):
        raise ValueError('Version cannot be used safely as a filename')
    verifier = Path(__file__).resolve().with_name('verify_wear_apk.py')
    run(sys.executable, verifier, args.wear, '--release', '--aapt2', aapt)
    with zipfile.ZipFile(args.wear) as source:
        abis = {name.split('/')[1] for name in source.namelist() if name.startswith('lib/') and name.endswith('.so')}
    if not abis or not abis <= {'armeabi-v7a', 'arm64-v8a'}:
        raise ValueError(f'Public watch APK needs ARM libraries only: {sorted(abis)}')
    results = {}
    certificates = set()
    # Validate everything in a temporary directory before creating public outputs.
    with tempfile.TemporaryDirectory(prefix='simpmusic-release-') as work:
        work = Path(work)

        def sign(source, filename, watch=False):
            aligned, signed = work / ('aligned-' + filename), work / filename
            run(align, '-f', '-P', '16', '4', source, aligned)
            run(signer, 'sign', '--ks', args.keystore, '--ks-key-alias', args.alias,
                '--ks-pass', 'env:KEYSTORE_PASSWORD', '--key-pass', 'env:KEY_PASSWORD', '--out', signed, aligned)
            certificate = run(signer, 'verify', '--print-certs', signed)
            if not args.allow_test_key and re.search(r'CN=Android Debug', certificate, re.I):
                raise ValueError('Android debug signing key cannot be used for a public release')
            digest = re.search(r'^Signer #1 certificate SHA-256 digest: (\w+)$', certificate, re.M)
            if digest is None:
                raise ValueError('Could not verify signing certificate')
            certificates.add(digest.group(1))
            run(align, '-c', '-P', '16', '4', signed)
            if identity(signed) != wear_identity:
                raise ValueError('Package/version changed during packaging')
            if watch:
                run(sys.executable, verifier, signed, '--release', '--aapt2', aapt)
            with signed.open('rb') as apk_file:
                sha256 = hashlib.file_digest(apk_file, 'sha256').hexdigest()
            results[filename] = {'bytes': signed.stat().st_size, 'sha256': sha256}
            return signed

        outputs = []
        for abi in sorted(abis):
            filtered = work / f'{abi}-unsigned.apk'
            with zipfile.ZipFile(args.wear) as source, zipfile.ZipFile(filtered, 'w') as target:
                for entry in source.infolist():
                    if entry.filename.startswith('lib/') and entry.filename.split('/')[1] != abi:
                        continue
                    if entry.filename.startswith('META-INF/') and entry.filename.upper().endswith(('.RSA', '.DSA', '.EC', '.SF', 'MANIFEST.MF')):
                        continue
                    target.writestr(entry, source.read(entry))
            outputs.append(sign(filtered, f'SimpMusic-WearOS-{version}-{abi}.apk', watch=True))
        if len(abis) > 1:
            outputs.append(sign(args.wear, f'SimpMusic-WearOS-{version}-arm-universal.apk', watch=True))
        outputs.append(sign(args.companion, f'SimpMusic-Companion-{version}.apk'))
        if len(certificates) != 1:
            raise ValueError('Watch and companion certificates do not match')
        manifest = {'package': wear_identity['name'], 'version_name': version,
            'version_code': int(wear_identity['versionCode']), 'certificate_sha256': certificates.pop(),
            'test_only': args.allow_test_key, 'artifacts': results}
        try:
            checkout = Path(__file__).resolve().parent
            git = ['git', '-c', 'safe.directory='+str(checkout), '-c', 'core.filemode=false', '-C', checkout]
            manifest['source_revision'] = run(*git, 'rev-parse', 'HEAD').strip()
            manifest['source_dirty'] = bool(run(*git, 'status', '--porcelain').strip())
        except (OSError, subprocess.CalledProcessError):
            manifest['source_revision'] = None
            manifest['source_dirty'] = True
        args.output.mkdir(parents=True, exist_ok=True)
        for apk in outputs:
            (args.output / apk.name).write_bytes(apk.read_bytes())
        (args.output / 'release-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
        (args.output / 'SHA256SUMS').write_text(''.join(f"{info['sha256']}  {name}\n" for name, info in results.items()), encoding='utf-8')
    print(f'Verified {len(results)} matching APKs: {args.output}')
    if args.allow_test_key:
        print('TEST ONLY: do not publish these artifacts.')


if __name__ == '__main__':
    main()
