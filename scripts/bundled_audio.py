"""Validate Quiet Below's bundled Event audio in source resources or a built mod jar.

The validator is intentionally offline: provenance URLs are recorded for review, but
are never fetched.  A resource root is either a resources directory or a zip-format
jar containing the same resource paths.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath


MANIFEST = "data/quiet_below/chronicle/event-sounds.json"
REGISTRY = "assets/quiet_below/sounds.json"
LANGUAGE = "assets/quiet_below/lang/en_us.json"
CREDITS = "quiet_below-sound-credits.txt"
MAX_MANIFEST = 256 * 1024
MAX_SOUNDS = 128
MAX_FILE = 8 * 1024 * 1024
MAX_TOTAL = 64 * 1024 * 1024
ENTRY_KEYS = {"id", "label", "subtitleKey", "file", "sha256", "source", "creator", "title", "license", "licenseUrl", "changes", "downloadUrl", "stream"}
EXPECTED = {
    "sinkhole_opening": "Sinkhole opening", "sculk_awakening": "Sculk awakening",
    "whisper_from_below": "Whisper from below", "city_discovery": "City discovery",
    "station_awakened": "Ancient station awakens", "altar_offering": "Altar offering",
    "sculk_growth": "Sculk growth", "heart_destroyed": "Heart destroyed",
}
AUDITED_LICENSES = {
    "sinkhole_opening": ("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    "sculk_awakening": ("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    "station_awakened": ("CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/"),
}
CC0 = ("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/")


class AudioValidationError(ValueError):
    """A bundled-audio resource violates the immutable campaign contract."""


class _DirectoryResources:
    def __init__(self, root: Path):
        self.root = root.resolve()

    def read(self, name: str) -> bytes:
        path = self._path(name)
        return path.read_bytes()

    def size(self, name: str) -> int:
        return self._path(name).stat().st_size

    def _path(self, name: str) -> Path:
        _safe_name(name)
        path = self.root.joinpath(*PurePosixPath(name).parts)
        cursor = self.root
        for part in PurePosixPath(name).parts:
            cursor = cursor / part
            if cursor.is_symlink():
                raise AudioValidationError(f"unsafe resource path: {name}")
        try:
            resolved = path.resolve(strict=True)
        except FileNotFoundError as error:
            raise AudioValidationError(f"missing resource: {name}") from error
        if not resolved.is_relative_to(self.root) or path.is_symlink() or not resolved.is_file():
            raise AudioValidationError(f"unsafe resource path: {name}")
        return resolved


class _ZipResources:
    def __init__(self, archive: Path):
        try:
            self.zip = zipfile.ZipFile(archive)
        except zipfile.BadZipFile as error:
            raise AudioValidationError(f"not a readable jar/zip: {archive}") from error
        entries = self.zip.infolist()
        self.members = {info.filename: info for info in entries}
        if len(self.members) != len(entries):
            self.zip.close()
            raise AudioValidationError("duplicate resource path in jar/zip")

    def close(self) -> None:
        self.zip.close()

    def read(self, name: str) -> bytes:
        return self.zip.read(self._member(name))

    def size(self, name: str) -> int:
        return self._member(name).file_size

    def _member(self, name: str) -> zipfile.ZipInfo:
        _safe_name(name)
        info = self.members.get(name)
        if info is None:
            raise AudioValidationError(f"missing resource: {name}")
        mode = info.external_attr >> 16
        if stat.S_ISLNK(mode) or info.is_dir():
            raise AudioValidationError(f"unsafe archive resource: {name}")
        return info


def _safe_name(name: str) -> None:
    if not isinstance(name, str):
        raise AudioValidationError(f"unsafe namespaced path: {name!r}")
    path = PurePosixPath(name)
    if "\\" in name or path.is_absolute() or ".." in path.parts or "." in path.parts:
        raise AudioValidationError(f"unsafe namespaced path: {name!r}")


def _json(reader: _DirectoryResources | _ZipResources, name: str, maximum: int = MAX_MANIFEST):
    if reader.size(name) > maximum:
        raise AudioValidationError(f"resource exceeds size limit: {name}")
    try:
        return json.loads(reader.read(name).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise AudioValidationError(f"invalid JSON: {name}") from error


def _valid_ogg_vorbis(audio: bytes) -> bool:
    """Recognize the first packet as a Vorbis identification header, never Opus."""
    if not audio.startswith(b"OggS"):
        return False
    offset = 0
    packet = bytearray()
    while offset < len(audio):
        if offset + 27 > len(audio) or audio[offset:offset + 5] != b"OggS\x00":
            return False
        segment_count = audio[offset + 26]
        table_end = offset + 27 + segment_count
        if table_end > len(audio):
            return False
        payload_size = sum(audio[offset + 27:table_end])
        payload_end = table_end + payload_size
        if payload_end > len(audio):
            return False
        cursor = table_end
        for size in audio[offset + 27:table_end]:
            packet.extend(audio[cursor:cursor + size])
            cursor += size
            if size < 255:
                return len(packet) == 30 and packet[:7] == b"\x01vorbis" and packet[7:11] == b"\x00\x00\x00\x00"
        offset = payload_end
    return False


def _nonempty_url(value: object, field: str, sound_id: str) -> None:
    if not isinstance(value, str) or not re.fullmatch(r"https://[^\s]+", value):
        raise AudioValidationError(f"{sound_id}: {field} must be an HTTPS URL")


def validate_resources(resource_root: str | Path) -> list[dict]:
    """Validate the eight immutable Quiet Below Event sounds and return their records.

    Raises :class:`AudioValidationError` for an invalid source tree or built jar.
    The returned records are suitable for packaging reports, not runtime playback.
    """
    root = Path(resource_root)
    reader: _DirectoryResources | _ZipResources
    if root.is_dir():
        reader = _DirectoryResources(root)
    elif root.is_file():
        reader = _ZipResources(root)
    else:
        raise AudioValidationError(f"resource root does not exist: {root}")
    try:
        manifest = _json(reader, MANIFEST)
        if not isinstance(manifest, dict) or set(manifest) != {"schemaVersion", "namespace", "sounds"}:
            raise AudioValidationError("event-sounds manifest has unexpected root keys")
        if manifest["schemaVersion"] != 1 or manifest["namespace"] != "quiet_below":
            raise AudioValidationError("event-sounds manifest has unsupported identity")
        sounds = manifest["sounds"]
        if not isinstance(sounds, list) or len(sounds) != len(EXPECTED) or len(sounds) > MAX_SOUNDS:
            raise AudioValidationError("event-sounds manifest must contain exactly eight sounds")
        registry = _json(reader, REGISTRY)
        language = _json(reader, LANGUAGE)
        credits = reader.read(CREDITS).decode("utf-8")
        if not credits.strip():
            raise AudioValidationError("sound credits are empty")
        seen: set[str] = set()
        total = 0
        records: list[dict] = []
        for sound in sounds:
            if not isinstance(sound, dict) or set(sound) != ENTRY_KEYS:
                raise AudioValidationError("sound entry has missing or unexpected metadata keys")
            sound_id = sound["id"]
            if not isinstance(sound_id, str) or not sound_id.startswith("quiet_below:event.") or sound_id in seen:
                raise AudioValidationError(f"invalid or duplicate sound id: {sound_id!r}")
            seen.add(sound_id)
            path = sound_id.removeprefix("quiet_below:event.")
            if path not in EXPECTED or sound["label"] != EXPECTED[path]:
                raise AudioValidationError(f"unexpected event sound mapping: {sound_id}")
            _safe_name(sound["file"])
            expected_file = f"events/{path}"
            expected_subtitle = f"subtitles.quiet_below.{path}"
            if sound["file"] != expected_file or sound["subtitleKey"] != expected_subtitle or sound["stream"] is not False:
                raise AudioValidationError(f"invalid playback mapping: {sound_id}")
            if language.get(expected_subtitle) != EXPECTED[path]:
                raise AudioValidationError(f"missing or incorrect subtitle: {expected_subtitle}")
            if not isinstance(sound["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", sound["sha256"]):
                raise AudioValidationError(f"invalid SHA256: {sound_id}")
            for field in ("source", "licenseUrl", "downloadUrl"):
                _nonempty_url(sound[field], field, sound_id)
            for field in ("creator", "title", "license", "changes"):
                if not isinstance(sound[field], str) or not sound[field].strip():
                    raise AudioValidationError(f"{sound_id}: {field} must be nonempty")
            if (sound["license"], sound["licenseUrl"]) != AUDITED_LICENSES.get(path, CC0):
                raise AudioValidationError(f"license differs from the audited source: {sound_id}")
            asset = f"assets/quiet_below/sounds/{expected_file}.ogg"
            size = reader.size(asset)
            total += size
            if size <= 0 or size > MAX_FILE or total > MAX_TOTAL:
                raise AudioValidationError(f"audio size limit exceeded: {asset}")
            audio = reader.read(asset)
            if hashlib.sha256(audio).hexdigest() != sound["sha256"]:
                raise AudioValidationError(f"SHA256 mismatch: {asset}")
            if not _valid_ogg_vorbis(audio):
                raise AudioValidationError(f"not Ogg Vorbis (or is Opus): {asset}")
            headers = list(re.finditer(r"(?m)^(?:" + "|".join(map(re.escape, EXPECTED)) + r")\s+[^\n]*\n", credits))
            header = next((h for h in headers if h.group().startswith(path + " ")), None)
            if header is None:
                raise AudioValidationError(f"missing per-clip credits: {sound_id}")
            end = next((h.start() for h in headers if h.start() > header.start()), len(credits))
            credit = credits[header.start():end]
            for label, field in (("Author", "creator"), ("Source", "source"), ("License", "license"), ("License URL", "licenseUrl"), ("Changes", "changes"), ("SHA256", "sha256")):
                if not re.search(r"(?m)^" + re.escape(label + ": " + sound[field]) + r"\s*$", credit):
                    raise AudioValidationError(f"missing or mismatched per-clip attribution {label}: {sound_id}")
            if path == "sinkhole_opening" and "Arto Koivisto" not in credit or path == "station_awakened" and "Sound effects by TunePocket" not in credit:
                raise AudioValidationError(f"source-requested attribution is missing: {sound_id}")
            key = f"event.{path}"
            expected_registry = {"subtitle": expected_subtitle, "sounds": [{"name": f"quiet_below:events/{path}", "stream": False}]}
            if not isinstance(registry, dict) or registry.get(key) != expected_registry:
                raise AudioValidationError(f"sounds.json reference mismatch: {key}")
            records.append(sound)
        if seen != {f"quiet_below:event.{path}" for path in EXPECTED}:
            raise AudioValidationError("event-sounds manifest ids do not match the required catalog")
        return records
    finally:
        if isinstance(reader, _ZipResources):
            reader.close()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("resource_roots", nargs="*", type=Path,
                        help="resources directory or built Quiet Below jar (defaults to campaign resources)")
    args = parser.parse_args(argv)
    roots = args.resource_roots or [Path(__file__).resolve().parents[1] / "campaigns/quiet-below/src/main/resources"]
    try:
        for root in roots:
            records = validate_resources(root)
            print(f"PASS: {root} ({len(records)} Ogg Vorbis Event sounds)")
    except (AudioValidationError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
