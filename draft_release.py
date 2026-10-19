#!/usr/bin/env python3
"""Create an unpublished GitHub prerelease from verified signed watch/companion assets."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import tomllib

from verify_release import verify_apks, verify_contents


def run(*command):
    return subprocess.check_output([str(c) for c in command], text=True, stderr=subprocess.STDOUT)


def create_draft(repository, version, revision, notes, assets, runner=run):
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid GitHub repository')
    tag = 'v'+version
    releases = json.loads(runner('gh', 'release', 'list', '--repo', repository,
        '--limit', '1000', '--json', 'tagName,isDraft'))
    if any(release['tagName'] == tag for release in releases):
        raise ValueError('A release already exists for this tag; review it instead of overwriting it')
    # No edit/upload/clobber path: a rerun cannot alter an existing published release or draft.
    return runner('gh', 'release', 'create', tag, *assets, '--repo', repository,
        '--draft', '--prerelease', '--verify-tag', '--target', revision,
        '--title', version, '--notes-file', notes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--build-tools', type=Path, required=True)
    parser.add_argument('--repository', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    versions = tomllib.loads((root/'gradle/libs.versions.toml').read_text(encoding='utf-8'))['versions']
    version = versions['version-name']
    git = ['git', '-c', 'core.filemode=false', '-C', root]
    revision = run(*git, 'rev-parse', 'HEAD').strip()
    manifest = verify_contents(args.directory, version, revision)
    if manifest['version_code'] != int(versions['version-code']):
        raise ValueError('Release version code does not match the source')
    verify_apks(args.directory, manifest, args.build_tools)
    if run(*git, 'status', '--porcelain').strip():
        raise ValueError('Release checkout must be clean')
    if run(*git, 'rev-parse', 'refs/tags/v'+version+'^{commit}').strip() != revision:
        raise ValueError('Existing release tag does not point to the built source revision')
    notes = root/'docs/releases'/f'{version}.md'
    if not notes.is_file():
        raise ValueError('Release notes are missing')
    assets = [args.directory/name for name in sorted(manifest['artifacts'])]
    assets += [args.directory/'SHA256SUMS', args.directory/'release-manifest.json']
    print(create_draft(args.repository, version, revision, notes, assets))


if __name__ == '__main__':
    main()
