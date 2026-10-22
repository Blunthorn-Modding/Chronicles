# HUD dependencies and distribution policy

**Integration22 October:** dev30 adds an original Chronicle food element through the pinned public APIs. Actual26-food packets, native fallback without dependencies, real editor save/reload/window resize and duplicate-bar suppression pass the native Altar rehearsal. The ordinary classic pack now uses the same four official references and credit/license notice; the separate prototype remains historical. `workspace.pack` verifies Chronicle-owned jars and exact reference-only packaging. Loom is now1.12.7 for compatible normal development remapping; dependency versions remain unchanged. No third-party code/assets/jars are copied into Chronicle or pack overrides. [Implementation boundaries](CF12_LASTING_BARGAINS.md), [evidence](TEST_PLAN.md).

Checked 21 October 2026. The operator selects SpiffyHUD/FancyMenu for prototyping and explicitly requires adherence to their licenses. This is the client integration foundation for CF-12, not installed Altar mechanics or completion of the expanded hunger HUD.

## Pinned releases

All four jars come unmodified from the author's official Modrinth projects. Exact project/version IDs, official CDN URLs, sizes, SHA-1/SHA-512 and reviewed upstream license hashes are in [the lock](../config/hud-dependencies.lock.json). Fabric API remains the existing bootstrap dependency.

| Mod | Fabric 1.21.10 release | License evidence |
| --- | --- | --- |
| [SpiffyHUD](https://modrinth.com/mod/spiffyhud) | 3.1.0-1.21.10-fabric | [DSMSLv3](https://github.com/Keksuccino/SpiffyHUD/blob/main/LICENSE.md) |
| [FancyMenu](https://modrinth.com/mod/fancymenu) | 3.8.2-1.21.10-fabric | Jar declares DSMSLv3; current [upstream license is DSMSLv3.1](https://github.com/Keksuccino/FancyMenu/blob/master/LICENSE.md) |
| [Konkrete](https://modrinth.com/mod/konkrete) | 1.9.13-1.21.10-fabric | [Apache-2.0](https://github.com/Keksuccino/Konkrete/blob/main/LICENSE) |
| [Melody](https://modrinth.com/mod/melody) | 1.0.14-1.21.10-fabric | [MIT, copyright 2023 Keksuccino](https://github.com/Keksuccino/Melody/blob/main/LICENSE) |

Both DSMSL versions permit an external dependency/addon arrangement and prohibit the bundling/copying used by a vendored library. Our policy follows that shared restriction and the current stricter FancyMenu instructions. Public availability of their source is not permission to copy it into Chronicle.

## Required packaging behavior

- Distribute mod references pointing to the author's official Modrinth downloads. Never put these four jars in pack overrides, Chronicle jars, repository files, release attachments, mirrors or upload caches. A launcher's official-source download is the intended installation path.
- Use original Chronicle code through the documented addon API. Do not copy their source, renderer implementation, textures, example implementations or documentation into our distributed product. Do not publish a modified/recompiled build or patch their jar. API inspection does not change ownership.
- Preserve the official downloaded jars and their nested dependencies unchanged. Local download/source/license caches and disposable native installations stay outside Git and are not distributable pack contents.
- Include our own dependency credit notice and license/project links in the prototype pack. Konkrete and Melody are also external references; we distribute no copies or derivative sources from those permissive dependencies. If that policy changes, review their full notice/license obligations before distribution.
- Recheck official release metadata, license text and dependency closure before changing pins. Missing artifacts fail visibly; no unofficial mirrors or adjacent Minecraft versions as fallback. Requests outside the allowed integration require author permission; do not contact the author automatically.

[hud_dependencies.py](../scripts/hud_dependencies.py) creates the separate prototype pack. It validates the four-project set, exact official URL structure, hashes and client-only/server-unsupported references. Its package verifier rejects unexpected overrides/jars. Before embedding our own artifacts it rejects known vendor package/assets and nested jars. These checks supplement manual provenance/diff review; they cannot detect copied code that someone has renamed.

## Reproducible prototype

```powershell
python scripts/hud_dependencies.py download
python scripts/hud_dependencies.py pack
python scripts/hud_smoke.py
```

The pack is `../client/Chronicle-HUD-Prototype-1.21.10.mrpack`; it preserves the existing review pack. It includes only our two existing mod artifacts, initial client preferences and the dependency notice, plus official download references. Normal server bootstrap, production and authenticated review worlds are unchanged.

The native smoke uses a fresh `../test-runs` world and client, fixed loopback TCP25575/UDP24455 and disposable offline identity; it never changes an authenticated server's settings. It compiles only our original [test helper](../testing/hud/HudPrototypeClient.java), which is excluded from every distributed pack and Chronicle artifact. It loads the pinned dependencies using Fabric's development remapper and exercises public APIs to open the real editor, build heart/food elements, save/reload movement and resize at GUI scale 2. It changes maximum health only in that disposable fixture. It does not claim physical mouse dragging or an authenticated launcher-import test.

Like other client mods, these jars execute inside the Minecraft process with the user's filesystem/network permissions. We inspect the pinned source/API and hashes before the explicit native test. FancyMenu can use remote resources/actions when configured; this prototype configures none.

## Expanded hunger integration remains ours

Pinned SpiffyHUD `PlayerFoodBarElement` uses a fixed ten slots and clamps the food value at20. FancyMenu's maximum-hunger placeholder also returns20. Icons-per-row is layout, not capacity. Its native heart element can represent a larger native max-health value; default untextured elements use colored cells, so Minecraft-style textures/presentation are still integration work.

Implement an original Chronicle food element registered through FancyMenu's `ElementRegistry`/`ElementBuilder` API, backed by authoritative synchronized Core stat data. Do not replace upstream registered elements, override their private implementation, copy their food renderer or pretend a fixture value is live server state. Hearts may use native max-health data. The HUD dependency owns editing/layout; Core owns food capacity, regeneration, sacrifices and persistence. Forge mappings/global caps (CF-11) remain independent of this work.

Verification results and retained failed attempts belong in [TEST_PLAN](TEST_PLAN.md). No release, Altar balance acceptance or universal mod compatibility is inferred from a successful editor prototype.
