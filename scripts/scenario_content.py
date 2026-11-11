"""Validate or import a reviewed Scenario-content export using the offline workspace toolchain."""
from __future__ import annotations

import argparse
from pathlib import Path
import workspace as w


def source_path(value: str) -> Path:
    path = Path(value).resolve(strict=True)
    if not path.is_file():
        raise argparse.ArgumentTypeError('scenario input must be a regular file')
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('validate', 'import'):
        command = commands.add_parser(name)
        command.add_argument('input', type=source_path, help='absolute exported Scenario JSON file')
    args = parser.parse_args()
    command = [w.REPO / 'gradlew.bat', '--offline', '--no-daemon', ':campaigns:quiet-below:importScenarioContent', f'-PscenarioContentInput={args.input}']
    if args.command == 'validate':
        command.append('-PscenarioContentValidateOnly=true')
    w.run(command)


if __name__ == '__main__':
    main()
