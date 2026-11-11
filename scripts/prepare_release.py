"""Prepare the two owned Modrinth mod uploads. Offline; never publishes or copies worlds."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
import zipfile

import bundled_audio
import hud_dependencies
import workspace as w


def prepare(native_report: Path) -> Path:
    evidence = json.loads(native_report.read_text(encoding='utf-8'))
    if evidence.get('result') != 'PASS' or not any('actual server restart retained ACTIVE' in c for c in evidence.get('checks', [])) or not any('UI-saved default fingerprint' in c for c in evidence.get('checks', [])):
        raise ValueError('Both native default-Event follow-ups must pass before preparing uploads')
    jars = w.artifacts()
    versions = {w.validate_mod_target(jar)['version'] for jar in jars}
    if versions != {'0.1.0-alpha.1'}:
        raise ValueError('Expected matching initial-alpha artifacts')
    if evidence.get('artifacts') != {p.name: w.digest(p) for p in jars}:
        raise ValueError('Artifacts differ from the native-tested bytes')
    target = w.WORK / 'releases' / versions.pop()
    target.mkdir(parents=True, exist_ok=True)
    sources = w.REPO / 'docs/releases/0.1.0-alpha.1'
    for name in ('LICENSE', 'NOTICE'):
        shutil.copy2(w.REPO / name, target / name)
    for name in ('CHANGELOG.md', 'CORE_MODRINTH.md', 'QUIET_BELOW_MODRINTH.md', 'PUBLISHING.md', 'VERIFICATION.md'):
        shutil.copy2(sources / name, target / name)
    projects = []
    for jar in jars:
        metadata = w.validate_mod_target(jar)
        hud_dependencies.verify_owned_jar(jar)
        with zipfile.ZipFile(jar) as archive:
            if archive.testzip() is not None or metadata.get('license') != 'MIT':
                raise ValueError('Invalid artifact/license')
            for name in ('LICENSE', 'NOTICE'):
                if archive.read(name) != (w.REPO / name).read_bytes():
                    raise ValueError('Missing or outdated project notice')
            if metadata['id'] == 'quiet_below':
                bundled_audio.validate_resources(jar)
                (target / bundled_audio.CREDITS).write_bytes(archive.read(bundled_audio.CREDITS))
        shutil.copy2(jar, target / jar.name)
        projects.append({
            'title': metadata['name'], 'suggestedSlug': 'chronicle-core' if metadata['id'] == 'chronicle_core' else 'the-quiet-below',
            'file': jar.name, 'sha256': w.digest(jar), 'sha512': w.digest(jar, 'sha512'),
            'versionNumber': metadata['version'], 'versionType': 'alpha', 'gameVersions': ['1.21.10'],
            'loaders': ['fabric'], 'environment': 'client_and_server', 'license': 'MIT',
            'requiredDependencies': ['fabric-api'] + (['chronicle-core (matching 0.1.0-alpha.1)'] if metadata['id'] == 'quiet_below' else []),
            'projectId': None, 'versionId': None, 'publicationStatus': 'PENDING_OWNER_UPLOAD',
        })
    (target / 'modrinth-metadata.json').write_text(json.dumps({'projects': projects, 'nativeEvidence': str(native_report.resolve()), 'source': 'https://github.com/TheOnlyAesir/Chronicles'}, indent=2) + '\n', encoding='utf-8')
    (target / 'SHA256SUMS').write_text(''.join(f'{w.digest(p)}  {p.name}\n' for p in sorted(target.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'), encoding='utf-8')
    return target


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-report', required=True, type=Path)
    args = parser.parse_args()
    print(prepare(args.native_report))
