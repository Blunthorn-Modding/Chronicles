# Ancient Forge presentation design

## Lead decision - approved before implementation

Replace dev.36's literal Grindstone background with an original Minecraft-style
Forge composition. It retains normal Minecraft font, item rendering, tooltips
and `ButtonWidget`, but it does not borrow an entire vanilla workstation whose
silhouette carries an unrelated meaning.

The existing real target, material, result, player inventory, transaction and
same-handler recipe guide stay unchanged. The 36 player slots are functional:
they supply and recover the two temporary inputs and support ordinary
shift-click use. The accompanying inspectable mock-up is
[`design/forge-presentation.html`](design/forge-presentation.html).

## Original workstation layout

The logical panel is `220 x 212`. It is an original light-grey, pixel-aligned
work surface with labelled target/material inputs and a result across its upper row. This fits within
the `320 x 240` logical canvas used by GUI scale 2 at a `640 x 480` window,
leaving a 14px vertical margin above and below.

| Part | Panel-local geometry | Presentation |
| --- | ---: | --- |
| Panel | `0,0,220,212` | original light-grey nine-slice panel with a 2px bevel and dark inset |
| Title | `9,8` | normal Minecraft font: `Ancient Forge` |
| Target | label centred at `53,26`; real slot 0 at `44,42` | target is a labelled 18px recessed slot |
| Material | label centred at `97,26`; real slot 1 at `88,42` | material is a labelled 18px recessed slot |
| Forge path | plus at `72,46`; arrow `120,44..148,58` | original small pixel glyphs; decorative only |
| Result | label centred at `169,26`; real slot 2 at `160,42` | derived result uses the distinct result recess |
| Status | `9,76`, maximum `202px` | normal dark text, all current structured states |
| Recipes | `150,94,62,20` | ordinary native `ButtonWidget` |
| Inventory | label `29,118`; main slots start `29,130` | real handler slots 3--29, `9 x 3` |
| Hotbar | start `29,188` | real handler slots 30--38, `9 x 1` |

The labels and status use normal dark `#404040` text on the light panel. A
standard item/enchantment tooltip still describes every actual stack; hovering
an empty first or second input still says `Target` or `Material`. No small text
or persistent diagnostic paragraph is introduced.

## Resource-packable chrome

Core supplies five original, deterministic pixel-geometry sprites. They are
not generated art and do not copy a Minecraft container bitmap. A resource pack
can reskin every Forge-specific piece of chrome by replacing these paths.

| Sprite path | Fixed source size | Scaling/use | Palette and geometry |
| --- | ---: | --- | --- |
| `chronicle_core:workshop/panel` | `24 x 24` | nine-slice to `220 x 212`, 2px border | outer `#202020`, upper/left highlight `#f0f0f0`, lower/right shade `#555555`, inner surface `#c6c6c6` |
| `chronicle_core:workshop/slot` | `18 x 18` | fixed, at both input and all inventory positions | 2px dark outer lip; light upper/left and dark lower/right edge; `#8b8b8b` recess |
| `chronicle_core:workshop/result` | `18 x 18` | fixed, at the result position | same slot construction, with restrained `#688c95` inner rim so output reads separately without a new colour language |
| `chronicle_core:workshop/plus` | `10 x 10` | fixed at `72,46` | `#686868` two-pixel plus on transparency |
| `chronicle_core:workshop/arrow` | `28 x 14` | fixed at `120,44` | `#686868` right arrow on transparency, ending before the result recess |

The source panel is a true nine-slice sprite: `2px` corners and edges are never
stretched, while the centre fills. Slots, plus and arrow are drawn at native
size. Minecraft's normal item renderer and stack count render over the real
handler slots. This provides the vanilla visual grammar of bevels, recesses and
native widgets without reusing a whole vanilla workstation.

## Guide and states

The recipe guide remains the existing `304 x 208` centred same-handler modal.
It deliberately keeps its dark native popup/background and white normal-font
text, because that popup asset is a modal surface rather than this Forge's light
work surface. It blocks all underlying slot interaction, keeps staged inputs,
and renders ingredient icon/name, count, effective enchantment and level.

The main panel consumes the existing Core payload state without parsing prose:
`READY` shows `Forge ready`; empty, short-material, unchanged and incompatible
states retain their current concise copy; `QUIET`, `UNPOWERED` and `UNAVAILABLE`
leave staged inputs recoverable and the result empty. This presentation creates
no new station state.

## GUI scale 2 proof

| Window pixels | Logical canvas | Panel top-left, logical | Panel top-left, physical | Panel extent, physical |
| --- | ---: | ---: | ---: | ---: |
| `640 x 480` | `320 x 240` | `(50,14)` | `(100,28)` | `440 x 424` |
| `1280 x 720` | `640 x 360` | `(210,74)` | `(420,148)` | `440 x 424` |

At the smaller window the hotbar ends at panel-local y=206, six pixels above the
bottom border. Recipes and status occupy separate rows.

## Ownership boundary

**Core** owns the `220 x 212` handler slot geometry, presentation sprites,
screen renderer, labels, status, widgets, tooltips and guide. It retains the
generic temporary inventory and transaction contract.

**Quiet Below** owns world presentation. Its existing `heat=0`, `heat=1` and
`heat=2` blockstate models map to off, kindling and active respectively.
Selecting those models from living-network readiness, and making the active
state emit light, is a separate Quiet Below block-state change. It must not be
derived from the UI's `READY` state, which is also affected by player distance,
participation and session state.

## Implemented network appearance - dev.37

The operator confirmed **half the configured Established threshold** for
kindling. Count only verified living, Heart-connected campaign sculk, using the
existing conservative assessment and loss adjustment. Dormant and detached
blocks do not warm the Forge.

| State | Requirement | Emitted light |
| --- | --- | ---: |
| Off (`heat=0`) | network below halfway, dormant/disconnected/unverified, no adjacent living sculk, disabled ecology, ended network, or paused/empty/held play | 0 |
| Kindling (`heat=1`) | at least half Established, below powered extent, with actual adjacent loaded living sculk | 0 |
| Active (`heat=2`) | existing Established/Extensive power proof, with actual adjacent loaded living sculk | 12 |

The Established stage retains its existing exit hysteresis; the Forge does not
introduce a competing extent classifier. All six neighboring cells are checked
through loaded chunks only. A healthy remote network alone cannot power a
station. Active appearance persists while powered, rather than flashing only
after a craft. The original idle/kindling/active Blockbench models and textures
are unchanged.

The loaded-station tracker still checks at most eight of 1,024 stations each
server tick. Appearance can therefore lag by up to 128 ticks (6.4 seconds at
20 TPS) with a full tracker. Crafting rechecks power synchronously, independently
of a temporarily stale model. Chunk loading resets saved heat to off before
revalidation; no chunk tickets, global scans or per-sculk entities are added.

## Acceptance checks

* Both GUI-scale-2 windows show every label, status, 36 inventory slots, hotbar
  and Recipes control with no overlap or clipping.
* Target/material/result map to the exact real handler slots. Native staging,
  shift-clicking and recovery remain intact.
* Replacing the five Core sprites in a resource pack reskins the whole Forge
  chrome without a special font or a copied vanilla container texture.
* The guide still isolates its modal interaction from all hidden slots.
* Tests cover the changed slot coordinates and native input/output paths;
  screenshots cover normal panel, invalid state and guide at both target sizes.
