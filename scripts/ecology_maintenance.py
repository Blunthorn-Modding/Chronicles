"""Verify or compact a stopped campaign's ecology journal; original history is archived on apply."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import workspace as w


def world_path(path):
    world = Path(path).absolute()
    if world.resolve() != world or not world.is_dir():
        raise ValueError('Select a real, existing world directory')
    if not world.is_relative_to(w.WORK.resolve()) or world == w.WORK.resolve():
        raise ValueError('Maintenance is limited to worlds inside this MC RP workspace')
    return world


def run(world, apply=False):
    world = world_path(world)
    w.run([w.REPO / 'gradlew.bat', '--no-daemon', ':campaigns:quiet-below:writeEcologyMaintenanceLaunch'])
    launch = json.loads((w.REPO / 'campaigns/quiet-below/build/maintenance/launch.json').read_text(encoding='utf-8'))
    command = [str(w.JAVA), '-cp', os.pathsep.join(launch['classpath']), launch['mainClass'], str(world)]
    if apply:
        command.append('--apply')
    result = subprocess.run(command, env=w.env(), text=True, capture_output=True, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    report = json.loads(result.stdout)
    print(json.dumps(report, indent=2), flush=True)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', required=True, help='Stopped world directory, not the server root')
    parser.add_argument('--apply', action='store_true', help='Archive history and atomically install the verified smaller journal')
    args = parser.parse_args()
    run(args.world, args.apply)
