# Ancient Forge workstation — design for review

**Historical dev.36 design:** its transactions were accepted, but the operator requested original Minecraft-style chrome rather than literal grindstone artwork. The approved [dev.37 presentation](M11_FORGE_PRESENTATION.md) supersedes the geometry and visual direction below; the real-slot workflow remains.

## Lead-review decision (approved before implementation)

Use a real vanilla-style `HandledScreen` based on the native grindstone container background. The Forge is an ordinary three-slot workstation: vertically stacked **Target** and **Ingredient** inputs feed a right-hand **Result** slot. The normal player inventory below is present because it supplies, receives, shift-clicks, and recovers the actual inputs. This replaces the held-main-hand/offhand text form.

Use the result pickup as the sole forge action **if** the later server design can reserve both input stacks in the handler and atomically consume/move them when the result is taken. That gives the player the standard workstation mental model and avoids a separate confirmation button. If the transaction cannot be safely escrowed through a handler, keep the same preview geometry but make the result non-takeable and retain one explicit `Forge` button; do not make a cosmetic result slot that commits hidden held/offhand state.

## Geometry at GUI scale 2

Use the native `minecraft:textures/gui/container/grindstone.png` background at its ordinary `176 x 166` logical container region. Its two vertically stacked inputs and right result recess match the required interaction, and its lower inventory artwork is functional rather than covered. Do not stretch it, mask unused parts, draw substitute slot frames, or add custom textures.

| Element | Panel-local logical geometry | Behavior |
| --- | --- | --- |
| Background | `176 x 166` | native grindstone container background |
| Title | native left `x=8, y=6` | `Ancient Forge` |
| Target input | item coordinate `x=49, y=19` | real slot 0; accepts one item; incompatible targets remain recoverable |
| Ingredient input | item coordinate `x=49, y=40` | real slot 1; accepts configured ingredients up to the native stack limit; consumes the configured count |
| Result | item coordinate `x=129, y=34` | handler output slot; only takeable when current preview is valid |
| Brief status | `x=8, y=72`, max `108` px | compact authoritative state, no diagnostic dump |
| Recipes | `x=120, y=60, w=48, h=20` | opens/closes bounded discovery overlay; enabled for every non-empty catalog |
| Player inventory | native positions `x=8+18c, y=84+18r`, 3×9 | real slots 3–29 |
| Hotbar | native positions `x=8+18c, y=142`, 9 slots | real slots 30–38 |

| Window | Logical canvas | Panel top-left, logical | Panel top-left, physical | Panel extent, physical |
| --- | --- | --- | --- |
| 640 x 480 | 320 x 240 | `(72, 37)` | `(144, 74)` | `352 x 332` |
| 1280 x 720 | 640 x 360 | `(232, 97)` | `(464, 194)` | `352 x 332` |

The title has its own top row. The status row replaces the native Inventory label at y=72, below the grindstone legs and above the first inventory slots; the Recipes button occupies the remaining right-side gap. All text uses Minecraft's normal nine-pixel font and ordinary native item tooltips. Hovering a real target, ingredient, or result shows its standard item/enchantment tooltip. Empty input-slot hover narration/name identifies `Target` or `Ingredient`; it is not a permanent paragraph. The screen adds no constant large tooltip.

## State contract

The client receives structured, bounded presentation state rather than parsed server English. It renders one of these concise strings and sets the result slot accordingly.

| State | Brief status | Target / ingredient | Result |
| --- | --- | --- | --- |
| Ready | `Forge ready` | accepted, standard placement | takeable preview |
| Empty target | `Add a target` | place a target | disabled/empty |
| Empty/unknown material | `Add a material` | place a material | disabled/empty |
| Ingredient short | `More material needed` | ingredient tooltip/count gives the concrete item evidence | disabled/empty |
| Enchantment cap/already stronger | `Already enchanted` | retain/recoverable | disabled/empty |
| Unpowered | `Forge is unpowered` | retain/recoverable, no commit | disabled/empty |
| Paused or ineligible | `Forge is quiet` | retain/recoverable, no commit | disabled/empty |
| Incompatible | `Cannot bind` | retain/recoverable | disabled/empty |
| Other unavailable | `Forge unavailable` | retain/recoverable | disabled/empty |
| Pending output take | `Forging...` | disable all handler mutation until answer | disabled/locked |
| Stale/range/configuration failure | `Reopen the Forge` | server returns inputs or keeps them in the active handler | disabled/empty |
| Completed | screen closes or authoritative handler refreshes | no optimistic local inventory edit | result has been taken exactly once |

The exact operator diagnosis (power reason, pause/participation cause, distance, policy revision, recipe configuration, stale-token detail) stays outside player UI. The player can understand what to change from the slot contents and compact state without a long tooltip.

## Recipe discovery

The Forge's current Quiet Below mappings are small, but Core supports up to 256 recipes, so the player must be able to discover ingredients without a permanent wall of text. `Recipes` remains available for every non-empty catalog and is disabled only for zero recipes. It opens a centered `304 x 208` logical-pixel, pack-overridable `minecraft:popup/background` overlay inside the same handler (eight logical pixels remain around it on a 320 x 240 canvas). It lists six `24`-pixel rows per page: native ingredient icon/count plus the localized effective capped enchantment level. Use white shadowed native text on the dark popup (the light workstation uses vanilla gray text). Trim labels to the row width and use ordinary hover for full text; 256 rows need at most 43 pages. Native Previous/Next and Back controls close or page the guide only. Escape/Back closes the guide, blocks all slots behind the modal, and preserves the same handler/escrow/offer. It never inserts an item or crafts automatically.

## Interaction

* Click, drag, double-click, and standard shift-click follow ordinary `ScreenHandler` semantics. Shift-clicking an eligible target or ingredient preferentially routes it to its matching empty input; all other stacks use normal player-inventory movement. Material input accepts configured ingredients and retains its native stack limit; target input holds one item. The preview rejects incompatible targets.
* Result pickup is the only acceptance gesture in the recommended escrow design. It may be clicked, shift-clicked, or collected with the standard pickup gesture only while its authoritative offer/token, live station gate, preview, and inventory destination remain valid.
* A full player inventory must not lose either input. If a result cannot be inserted, the output remains takeable only when the cursor can hold it or a standard slot move has a destination; on close, all staged inputs return through ordinary handler return/drop policy determined by root's lifecycle design.
* Repeated pickup, concurrent actions, changed inputs, range/power/pause transitions, timeout, stale packet, close, disconnect, and screen replacement must invalidate the preview before a second transaction. Server authority and one-use token checks remain mandatory.
* Escape/Close invokes the native handled-screen close flow; it does not forge. Disconnect follows normal handler cleanup. No click outside actual slots creates a request.

## Pinned native asset/API evidence

Inspected from the pinned 1.21.10 client-only jar under `.gradle/loom-cache` on 22 October 2026:

| Asset | Jar path | Verified data | Use |
| --- | --- | --- | --- |
| Grindstone container | `assets/minecraft/textures/gui/container/grindstone.png` | `256 x 256` atlas; transparent `(0,0)` | draw vanilla `176 x 166` container region at native scale |
| Grindstone error sprite | `assets/minecraft/textures/gui/sprites/container/grindstone/error.png` | `28 x 21` | native error art is available at the result arrow (`x=92,y=31`); use only when its state agrees with the concise status |
| Anvil container | `assets/minecraft/textures/gui/container/anvil.png` | inspected native atlas | rejected: baked bright-red rename strip `x=59..169,y=20..36` would be unused |
| Smithing container | `assets/minecraft/textures/gui/container/smithing.png` | `256 x 256` atlas | rejected: unused template recess for this two-input Forge |
| Native buttons/fonts | `widget/button*.png`, `font/default.json` | existing pack-overridable assets | native Recipes/Previous/Next/Back controls |

Pinned `GrindstoneScreenHandler` slot coordinates are `49,19`, `49,40`, and `129,34`, with inventory `8,84` and hotbar `8,142`. The vanilla grindstone screen draws its native `176 x 166` region from the 256 atlas through the GUI-textured path and places `container/grindstone/error` at `92,31`. Production should follow that path and let Minecraft/resource packs own the artwork. This is deliberately different from the Altar popup sprite: every baked grindstone recess is functional here.

## Review questions and acceptance checks

1. Approve result pickup as the sole action only after root confirms an escrowed handler transaction can satisfy close/full-inventory/recovery semantics. Otherwise use the defined explicit-Forge fallback.
2. Verify GUI-scale-2 geometry at both canvases, normal font readability, resource-pack replacement of the native grindstone texture, and standard item/enchantment hover tooltips.
3. Verify target + ingredient → result is visually unambiguous; no main-hand/offhand instruction or full-screen text list remains.
4. Verify all states above are concise, no raw diagnostics leak, inputs never disappear on full inventory/close, and exactly one output take creates exactly one server transaction.
5. Verify shift-click, repeated use, output taking, input replacement, range/power/pause loss, stale offers, duplicate packets, close, disconnect, and handler replacement are server-authoritative and recover safely.

This design does not authorize new recipes, configuration, enchantment balance/caps, Altar changes, or a transaction/lifecycle architecture.
