import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from verify_release import verify_contents


class ReleaseAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = '0.3.0-beta.1'
        self.revision = 'a'*40
        names = [f'SimpMusic-WearOS-{self.version}-{abi}.apk' for abi in ['armeabi-v7a', 'arm64-v8a', 'arm-universal']]
        names.append(f'SimpMusic-Companion-{self.version}.apk')
        artifacts = {}
        for name in names:
            payload = ('fixture-'+name).encode()
            (self.root/name).write_bytes(payload)
            artifacts[name] = {'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()}
        identity = json.loads((Path(__file__).resolve().parent.parent/'release-signing.json').read_text(encoding='utf-8'))
        self.manifest = {'package': 'io.github.rahyan.simpmusicwear', 'version_name': self.version,
            'version_code': 41, 'test_only': False, 'source_dirty': False,
            'source_revision': self.revision, 'certificate_sha256': identity['certificate_sha256'], 'artifacts': artifacts}
        self.save()
        (self.root/'SHA256SUMS').write_text(''.join(f"{v['sha256']}  {k}\n" for k, v in artifacts.items()), encoding='utf-8')

    def save(self):
        (self.root/'release-manifest.json').write_text(json.dumps(self.manifest), encoding='utf-8')

    def verify(self):
        return verify_contents(self.root, self.version, self.revision)

    def test_complete_matching_release(self):
        self.assertEqual(self.verify()['source_revision'], self.revision)

    def test_reject_other_release_signing_key(self):
        self.manifest['certificate_sha256'] = 'b'*64
        self.save()
        with self.assertRaisesRegex(ValueError, 'certificate does not match'):
            self.verify()

    def test_reject_upstream_and_development_packages(self):
        for package in ['com.maxrave.simpmusic', 'io.github.rahyan.simpmusicwear.dev',
                        'io.github.rahyan.simpmusicwear.test.dev']:
            with self.subTest(package=package):
                self.manifest['package'] = package
                self.save()
                with self.assertRaisesRegex(ValueError, 'package/version'):
                    self.verify()

    def test_reject_test_only_and_dirty_source(self):
        for field in ['test_only', 'source_dirty']:
            with self.subTest(field=field):
                self.manifest[field] = True
                self.save()
                with self.assertRaisesRegex(ValueError, 'Test-only or dirty-source'):
                    self.verify()
                self.manifest[field] = False

    def test_source_commit_mismatch(self):
        self.manifest['source_revision'] = 'c'*40
        self.save()
        with self.assertRaisesRegex(ValueError, 'source revision'):
            self.verify()

    def test_tampered_apk(self):
        name = next(iter(self.manifest['artifacts']))
        (self.root/name).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'size/hash'):
            self.verify()

    def test_missing_apk(self):
        (self.root/next(iter(self.manifest['artifacts']))).unlink()
        with self.assertRaisesRegex(ValueError, 'missing or unexpected'):
            self.verify()

    def test_unexpected_apk(self):
        (self.root/'leftover-debug.apk').write_bytes(b'unrelated')
        with self.assertRaisesRegex(ValueError, 'missing or unexpected'):
            self.verify()

    def test_path_traversal_manifest(self):
        info = self.manifest['artifacts'].pop(next(iter(self.manifest['artifacts'])))
        self.manifest['artifacts']['../outside.apk'] = info
        self.save()
        with self.assertRaisesRegex(ValueError, 'four expected APKs'):
            self.verify()

    def test_duplicate_checksum_entry(self):
        sums = self.root/'SHA256SUMS'
        sums.write_text(sums.read_text(encoding='utf-8')*2, encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'duplicate checksum'):
            self.verify()

    def test_false_checksum(self):
        sums = self.root/'SHA256SUMS'
        text = sums.read_text(encoding='utf-8')
        sums.write_text('0'*64+text[64:], encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'size/hash'):
            self.verify()

    def test_invalid_version_code(self):
        self.manifest['version_code'] = True
        self.save()
        with self.assertRaisesRegex(ValueError, 'version code'):
            self.verify()


if __name__ == '__main__':
    unittest.main()
