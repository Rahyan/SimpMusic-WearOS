#!/usr/bin/env python3
"""Validate a complete watch/companion release before attaching it to a draft."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def public_identity():
    identity = json.loads(Path(__file__).with_name('release-signing.json').read_text(encoding='utf-8'))
    if identity.get('package') != 'io.github.rahyan.simpmusicwear' or not re.fullmatch(r'[0-9a-f]{64}', identity.get('certificate_sha256', '')):
        raise ValueError('Invalid public signing identity')
    return identity


def verify_contents(directory, version, source_revision, package='io.github.rahyan.simpmusicwear', allow_test_key=False):
    directory = Path(directory)
    manifest = json.loads((directory/'release-manifest.json').read_text(encoding='utf-8'))
    if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._-]*', version):
        raise ValueError('Invalid release version')
    if manifest.get('version_name') != version or manifest.get('package') != package:
        raise ValueError('Release package/version does not match')
    if type(manifest.get('version_code')) is not int or manifest['version_code'] <= 0:
        raise ValueError('Invalid version code')
    if not allow_test_key and (manifest.get('test_only') is not False or manifest.get('source_dirty') is not False):
        raise ValueError('Test-only or dirty-source artifacts cannot be released')
    if not re.fullmatch(r'[0-9a-f]{40}', source_revision) or manifest.get('source_revision') != source_revision:
        raise ValueError('Release source revision does not match')
    if not re.fullmatch(r'[0-9a-f]{64}', manifest.get('certificate_sha256', '')):
        raise ValueError('Invalid signing certificate hash')
    if not allow_test_key and manifest['certificate_sha256'] != public_identity()['certificate_sha256']:
        raise ValueError('Signing certificate does not match the retained release key')
    names = {f'SimpMusic-WearOS-{version}-{abi}.apk' for abi in ['armeabi-v7a', 'arm64-v8a', 'arm-universal']}
    names.add(f'SimpMusic-Companion-{version}.apk')
    artifacts = manifest.get('artifacts', {})
    if set(artifacts) != names:
        raise ValueError('Release must contain exactly the four expected APKs')
    if {p.name for p in directory.iterdir()} != names | {'SHA256SUMS', 'release-manifest.json'}:
        raise ValueError('Release directory has missing or unexpected files')
    sums = {}
    for line in (directory/'SHA256SUMS').read_text(encoding='utf-8').splitlines():
        match = re.fullmatch(r'([0-9a-f]{64})  ([^/\\]+)', line)
        if not match or match[2] in sums:
            raise ValueError('Malformed or duplicate checksum entry')
        sums[match[2]] = match[1]
    if set(sums) != names:
        raise ValueError('Checksums do not cover the complete release')
    for name, info in artifacts.items():
        path = directory/name
        if path.is_symlink() or not path.is_file():
            raise ValueError('Release artifacts must be regular files')
        with path.open('rb') as file:
            digest = hashlib.file_digest(file, 'sha256').hexdigest()
        if digest != info.get('sha256') or sums[name] != digest or path.stat().st_size != info.get('bytes'):
            raise ValueError('Artifact size/hash does not match: '+name)
    return manifest


def verify_apks(directory, manifest, build_tools, allow_test_key=False):
    directory, build_tools = Path(directory), Path(build_tools)
    windows = sys.platform == 'win32'
    def tool(name):
        return str(build_tools/(name+('.bat' if name == 'apksigner' else '.exe') if windows else name))
    def run(*command):
        return subprocess.check_output([str(c) for c in command], text=True, stderr=subprocess.STDOUT)
    for name in manifest['artifacts']:
        path = directory/name
        certificate = run(tool('apksigner'), 'verify', '--print-certs', path)
        digest = re.search(r'^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)$', certificate, re.M)
        if not digest or digest[1] != manifest['certificate_sha256']:
            raise ValueError('APK certificate does not match: '+name)
        if not allow_test_key and re.search(r'CN=Android Debug', certificate, re.I):
            raise ValueError('Android debug key cannot be released')
        run(tool('zipalign'), '-c', '-P', '16', '4', path)
        badging = run(tool('aapt2'), 'dump', 'badging', path)
        identity = dict(re.findall(r"(\w+)='([^']*)'", next(l for l in badging.splitlines() if l.startswith('package:'))))
        if identity.get('name') != manifest['package'] or identity.get('versionName') != manifest['version_name'] or \
                identity.get('versionCode') != str(manifest['version_code']) or 'application-debuggable' in badging:
            raise ValueError('APK identity or build type does not match: '+name)
        tree = run(tool('aapt2'), 'dump', 'xmltree', path, '--file', 'AndroidManifest.xml')
        if 'PreviewActivity' in tree:
            raise ValueError('Release contains a preview activity')
        if 'WearOS' in name:
            run(sys.executable, Path(__file__).with_name('verify_wear_apk.py'), path, '--release', '--aapt2', tool('aapt2'))
            with zipfile.ZipFile(path) as apk:
                actual = {n.split('/')[1] for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')}
            expected = {'arm64-v8a', 'armeabi-v7a'} if 'arm-universal' in name else \
                {'arm64-v8a' if name.endswith('-arm64-v8a.apk') else 'armeabi-v7a'}
            if actual != expected:
                raise ValueError('APK native ABI does not match filename: '+name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--source-revision', required=True)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--allow-test-key', action='store_true', help='Local integration checks only')
    args = parser.parse_args()
    manifest = verify_contents(args.directory, args.version, args.source_revision, allow_test_key=args.allow_test_key)
    verify_apks(args.directory, manifest, args.build_tools, args.allow_test_key)
    print('Verified release assets for '+manifest['version_name'])


if __name__ == '__main__':
    main()
