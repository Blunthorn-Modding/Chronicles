"""Offline checks for the Quiet Below M3 resource pack.

Run from the repository root:
    python campaigns/quiet-below/tools/test_m3_resources.py
"""

from __future__ import annotations

import json
import struct
import unittest
from pathlib import Path


RESOURCES = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = RESOURCES / "assets/quiet_below"
DATA = RESOURCES / "data/quiet_below"


def load(relative: str) -> dict:
    return json.loads((RESOURCES / relative).read_text(encoding="utf-8"))


def png_header(relative: str) -> tuple[int, int, int, int]:
    raw = (ASSETS / relative).read_bytes()
    if raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"{relative} is not a PNG")
    length = struct.unpack(">I", raw[8:12])[0]
    if raw[12:16] != b"IHDR" or length != 13:
        raise AssertionError(f"{relative} has no valid IHDR")
    width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", raw[16:29])
    return width, height, bit_depth, color_type


class M3ResourceTests(unittest.TestCase):
    def test_item_definitions_use_modern_models(self) -> None:
        self.assertEqual(load("assets/quiet_below/items/echo_lens.json")["model"], {
            "type": "minecraft:model", "model": "quiet_below:item/echo_lens"
        })
        self.assertEqual(load("assets/quiet_below/items/resonance_marker.json")["model"], {
            "type": "minecraft:model", "model": "quiet_below:block/resonance_marker"
        })

    def test_marker_model_and_blockstate_match(self) -> None:
        model = load("assets/quiet_below/models/block/resonance_marker.json")
        self.assertEqual(model["textures"]["1"], "quiet_below:block/resonance_marker")
        self.assertEqual(model["textures"]["particle"], "minecraft:block/polished_deepslate")
        self.assertEqual(len(model["elements"]), 36)
        for element in model["elements"]:
            self.assertTrue(all(0 <= value <= 16 for value in element["from"] + element["to"]))
        variants = load("assets/quiet_below/blockstates/resonance_marker.json")["variants"]
        self.assertEqual(set(variants), {"facing=north", "facing=east", "facing=south", "facing=west"})
        self.assertEqual([variants[key].get("y", 0) for key in variants], [0, 90, 180, 270])

    def test_pixel_textures_are_rgba_and_expected_size(self) -> None:
        self.assertEqual(png_header("textures/item/echo_lens.png"), (16, 16, 8, 6))
        self.assertEqual(png_header("textures/block/resonance_marker.png"), (64, 64, 8, 6))

    def test_recipes_and_loot_target_registered_ids(self) -> None:
        for item in ("echo_lens", "resonance_marker"):
            recipe = load(f"data/quiet_below/recipe/{item}.json")
            self.assertEqual(recipe["type"], "minecraft:crafting_shaped")
            self.assertEqual(recipe["result"], {"count": 1, "id": f"quiet_below:{item}"})
        loot = load("data/quiet_below/loot_table/blocks/resonance_marker.json")
        entry = loot["pools"][0]["entries"][0]
        self.assertEqual(entry["name"], "quiet_below:resonance_marker")
        self.assertIn({"condition": "minecraft:survives_explosion"}, entry["conditions"])

    def test_english_keys_match_the_interaction_contract(self) -> None:
        lang = load("assets/quiet_below/lang/en_us.json")
        self.assertTrue({
            "item.quiet_below.echo_lens",
            "block.quiet_below.resonance_marker",
            "message.quiet_below.marker_observation",
            "message.quiet_below.marker_lens_clue",
            "message.quiet_below.session_dormant",
        }.issubset(lang))


if __name__ == "__main__":
    unittest.main()
