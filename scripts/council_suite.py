"""Run explicitly selected Last Council native suites and collect their evidence."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import workspace as w

SUITES = {name: (script, prefix) for name, script, prefix in [
    ('foundation', 'last_council_smoke.py', 'last-council-'),
    *[(name, f'council_{name}_smoke.py', f'council-{name}-') for name in
      ('ballots', 'succession', 'treaties', 'conflicts', 'foreign', 'rebellion', 'content')],
]}
OUTPUT_LIMIT = 256 * 1024


def artifacts():
    props = dict(line.split('=', 1) for line in (w.REPO / 'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line)
    version = props['mod_version'].strip()
    paths = [w.REPO / 'core/build/libs' / f'chronicle_core-{version}.jar',
             w.REPO / 'campaigns/last-council/build/libs' / f'last_council-{version}.jar']
    for path, expected in zip(paths, ('chronicle_core', 'last_council')):
        if not path.is_file():
            raise RuntimeError(f'Missing source-built artifact: {path}')
        metadata = w.validate_mod_target(path)
        if metadata.get('id') != expected or metadata.get('version') != version:
            raise RuntimeError(f'Unexpected artifact metadata: {path}')
    return paths


def hashes(paths):
    result = {}
    for path in paths:
        digest = hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(chunk)
        result[str(path)] = digest.hexdigest()
    return result


def reports(prefix):
    return {path for path in w.REPORTS.glob(prefix + '*/report.json') if path.is_file()}


def launch(script, output):
    """Drain child output while retaining at most OUTPUT_LIMIT bytes."""
    process = subprocess.Popen([sys.executable, str(w.REPO / 'scripts' / script)],
                               cwd=w.REPO, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    retained = 0
    truncated = False
    with output.open('wb') as target:
        while True:
            chunk = process.stdout.read(8192)
            if not chunk:
                break
            remaining = max(0, OUTPUT_LIMIT - retained)
            target.write(chunk[:remaining])
            retained += min(len(chunk), remaining)
            truncated |= len(chunk) > remaining
        if truncated:
            target.write(b'\n[output truncated]\n')
    return process.wait()


def run(selected, runner=launch):
    w.REPORTS.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    directory = w.REPORTS / ('council-suite-' + stamp)
    directory.mkdir()  # Refuse a collision rather than reuse evidence.
    report = {'result': 'FAIL', 'selected': selected, 'artifacts_sha256': {}, 'suites': [],
              'note': 'Technical suites do not establish human map or voice acceptance.'}
    try:
        if not selected or len(set(selected)) != len(selected) or any(name not in SUITES for name in selected):
            raise ValueError("Choose at least one distinct registered suite")
        paths = artifacts()
        baseline = hashes(paths)
        report['artifacts_sha256'] = baseline
        for name in selected:
            script, prefix = SUITES[name]
            item = {'name': name, 'result': 'FAIL', 'returncode': None,
                    'child_report': None, 'checks': []}
            report['suites'].append(item)
            if hashes(paths) != baseline:
                item['error'] = 'Artifact hashes changed before launch'
                break
            before = reports(prefix)
            item['returncode'] = runner(script, directory / (name + '.log'))
            fresh = reports(prefix) - before
            unchanged = hashes(paths) == baseline
            item['artifacts_unchanged'] = unchanged
            if len(fresh) == 1:
                child = next(iter(fresh))
                item['child_report'] = str(child)
                try:
                    evidence = json.loads(child.read_text(encoding='utf-8'))
                    item['checks'] = evidence.get('checks', [])
                    item['child_result'] = evidence.get('result')
                except (OSError, ValueError, AttributeError) as exc:
                    item['error'] = f'Invalid child report: {exc}'
            else:
                item['error'] = f'Expected exactly one fresh child report; found {len(fresh)}'
            if item['returncode'] == 0 and len(fresh) == 1 and item.get('child_result') == 'PASS' and unchanged:
                item['result'] = 'PASS'
            else:
                break
        if len(report['suites']) == len(selected) and all(item['result'] == 'PASS' for item in report['suites']):
            report['result'] = 'PASS'
    except KeyboardInterrupt:
        report['result'] = 'CANCELLED'
        report['error'] = 'Interrupted; child suite cleanup depends on its own handling.'
    except Exception as exc:
        report['error'] = str(exc)
    finally:
        (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f"{report['result']}: {directory / 'report.json'}")
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--list', action='store_true', help='List suites without launching')
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--suite', choices=SUITES, action='append')
    group.add_argument('--all', action='store_true')
    args = parser.parse_args(argv)
    if args.list or not (args.all or args.suite):
        print('\n'.join(SUITES))
        return 0
    selected = list(SUITES) if args.all else list(dict.fromkeys(args.suite))
    return 0 if run(selected)['result'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
