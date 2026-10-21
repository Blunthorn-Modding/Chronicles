# Sculk vitality palettes

These are deterministic color variants of the pinned Minecraft Java 1.21.10 sculk assets, not replacement geometry. `sculk-source-hashes.json` records SHA-256 hashes of the original model, selector and texture inputs from the installed `minecraft-client.jar`. No original jar is checked in.

From the repository root, after workspace setup/build has cached the pinned client:

```powershell
python scripts/sculk_assets.py
python -m unittest discover -s scripts -p 'test_sculk_assets.py' -v
python scripts/workspace.py appearance-smoke
```

The generator runs `SculkPalette.java` with portable Java 21. It preserves pixel positions and alpha, uses the first native animation frame for each static variant, and derives 51 PNGs plus 27 child models and five stage-aware selectors. Healthy uses original animated Minecraft resources. Parent models retain native geometry, rotations and transient states. The preview goes to `../reports/sculk-vitality-palette.png`, outside Git.

`appearance-smoke` also needs the retained stopped dev.18 test fixture at `../test-fixtures/sculk-layout-dev18` for old-save and incompatible-peer checks. Its manifest hashes both named development and remapped legacy jars. This fixture is local evidence, not a distributable dependency or production-world copy. The suite refuses missing/changed artifacts. Shared test ports are TCP25575/UDP24455; run sequentially with other native suites.

Both peers must use the matching appearance layout; the configuration handshake rejects incompatible peers before PLAY. Back up and restore world and mod jars together when rolling back.

Dev.20 strengthens the fading palette: native blue-fleck positions remain visibly cool on a brown body; exhausted textures have no cool pixels. The contrast regression checks that opaque sculk/veins retain a minimum readable blue-fleck area, alongside native rendering and known positive/zero vitality checks.
