import json
import unittest

from draft_release import create_draft


class DraftReleaseTest(unittest.TestCase):
    def test_new_release_stays_unpublished(self):
        calls = []
        def runner(*args):
            calls.append(args)
            return '[]' if args[2] == 'list' else 'draft-url'
        result = create_draft('owner/project', '0.3.0-beta.1', 'a'*40, 'notes.md', ['watch.apk', 'phone.apk'], runner)
        self.assertEqual(result, 'draft-url')
        creation = calls[-1]
        self.assertEqual(creation[:4], ('gh', 'release', 'create', 'v0.3.0-beta.1'))
        for guard in ['--draft', '--prerelease', '--verify-tag']:
            self.assertIn(guard, creation)
        self.assertIn('watch.apk', creation)
        self.assertIn('phone.apk', creation)

    def test_existing_release_is_never_modified(self):
        for is_draft in [True, False]:
            with self.subTest(is_draft=is_draft):
                calls = []
                def runner(*args):
                    calls.append(args)
                    return json.dumps([{'tagName': 'v0.3.0-beta.1', 'isDraft': is_draft}])
                with self.assertRaisesRegex(ValueError, 'already exists'):
                    create_draft('owner/project', '0.3.0-beta.1', 'a'*40, 'notes.md', [], runner)
                self.assertEqual(len(calls), 1)
                self.assertEqual(calls[0][2], 'list')

    def test_invalid_repository_sends_no_requests(self):
        def runner(*args):
            self.fail('Must not call GitHub for invalid repository')
        with self.assertRaisesRegex(ValueError, 'Invalid GitHub repository'):
            create_draft('owner/project/extra', '0.3.0-beta.1', 'a'*40, 'notes.md', [], runner)


if __name__ == '__main__':
    unittest.main()
