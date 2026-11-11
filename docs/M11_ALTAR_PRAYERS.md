# Ancient Altar prayer screen

## Lead-review decision (approved before implementation)

Replace the bargain worksheet with a compact, mysterious player screen. It has no player inventory, slots, prices, statistics, counts, mechanic tooltips, outcome paragraph, or explanation of reversals or source state. The player can only choose a native button:

* `Pray for more life`
* `Pray for quicker feet`

The buttons are the sole deliberate prayer action. A native `Close` button and Escape leave the Altar without praying. This is a `Screen` interaction, rather than a `HandledScreen`; closing must still use the existing bounded close/revocation path. The player UI receives bounded presentation state and never derives it from server diagnostics, terms, totals, or English strings.

## Fixed GUI-scale-2 geometry

Use the generic pack-overridable `minecraft:popup/background` GUI sprite as a `224 x 132` logical-pixel popup. It has no baked inventory, book, input, or slot artwork. The sprite's own declared nine-slice supplies the panel border; production must not cover, mask, or recreate any part of another container screen.

| Element | Panel-local logical position | Player text/behavior |
| --- | --- | --- |
| Background | `224 x 132` | Native generic popup sprite, engine nine-slice |
| Title | centered baseline `y=12` | `Ancient Altar` |
| State line | centered baseline `y=32` | one short flavor state only |
| Life prayer | `x=12, y=48, w=200, h=20` | `Pray for more life` |
| Feet prayer | `x=12, y=74, w=200, h=20` | `Pray for quicker feet` |
| Close | `x=62, y=104, w=100, h=20` | native `Close` button; Escape also closes |

The title and state use Minecraft's normal nine-pixel font. The two choice labels fit a 200-pixel normal `ButtonWidget`; no custom shadowed-black text, font scaling, hand-painted button, slot, or frame is used. The popup leaves eight logical pixels below the Close button.

| Window | Logical canvas | Panel top-left, logical | Panel top-left, physical | Panel extent, physical |
| --- | --- | --- | --- |
| 640 x 480 | 320 x 240 | `(48, 54)` | `(96, 108)` | `448 x 264` |
| 1280 x 720 | 640 x 360 | `(208, 114)` | `(416, 228)` | `448 x 264` |

The HTML mockup is an exact geometry and copy prototype, not a replacement asset or a rendering claim.

## Player-facing states

| State | State line | Buttons |
| --- | --- | --- |
| Ready | `The altar listens.` | both eligible prayers enabled |
| Pending | `The altar listens...` | both disabled; no local result |
| Unavailable | `The altar is quiet.` | both disabled |
| One exhausted | `One answer has faded.` | matching prayer disabled; the other may be enabled |
| Both exhausted | `No answer comes.` | both disabled |
| Expired/failed reply | `The altar falls silent.` | both disabled until the existing reopen/refresh route supplies a fresh offer |

There is no success result inside the menu. On authoritative success the screen closes and the player receives private chat:

* More life: `You prayed, and the sculk answered. A lasting boon for a little life force... The sculk around you pulses hungrily for more.`
* Quicker feet: `You prayed, and the sculk answered. Your feet feel light, then leaden. The sculk lingers in your steps.`

Chat naturally wraps in its ordinary chat width; the menu does not try to fit either sentence. There is no constant hover tooltip. If accessibility narration is required, native button narration may repeat only the visible button label. It must not disclose terms, stacking, numerical values, failure reasons, or campaign state.

## Verified native asset and API plan

Pinned evidence was inspected in `minecraft-clientOnly-...1.21.10+build.3-v2.jar` under `.gradle/loom-cache` on 22 October 2026. The relevant generic sprite is:

| Asset ID | Jar path | Verified source data | Production use |
| --- | --- | --- | --- |
| `minecraft:popup/background` | `assets/minecraft/textures/gui/sprites/popup/background.png` | `236 x 34` pixels; source corner pixel transparent and inner pixel `(8,8)` opaque dark gray | Draw at `224 x 132`; generic popup only, no baked container controls |
| `minecraft:popup/background` metadata | `assets/minecraft/textures/gui/sprites/popup/background.png.mcmeta` | `gui.scaling.type = nine_slice`, source `width = 236`, `height = 34`, `border = 6` | Let the GUI-sprite renderer honor this six-pixel nine-slice declaration |
| `minecraft:widget/button` | `assets/minecraft/textures/gui/sprites/widget/button.png` | `200 x 20` pixels | Native enabled `ButtonWidget` skin |
| `minecraft:widget/button_highlighted` | `assets/minecraft/textures/gui/sprites/widget/button_highlighted.png` | `200 x 20` pixels | Native hover/focus treatment |
| `minecraft:widget/button_disabled` | `assets/minecraft/textures/gui/sprites/widget/button_disabled.png` | `200 x 20` pixels | Native pending/unavailable/exhausted treatment |

Implement the popup through the 1.21.10 `DrawContext` GUI-sprite draw path using `Identifier.ofVanilla("popup/background")`; do not use a raw texture atlas source rectangle. The GUI-sprite path is what reads the `.mcmeta` scaling rule, so resource packs retain control of the nine-slice art. Use `ButtonWidget.builder(...)` for all three buttons, allowing Minecraft to choose the standard enabled, focused, and disabled widget sprite. Confirm the exact overload and render-layer parameter against the pinned mappings/javap when code is started; this design deliberately depends on the GUI-sprite path rather than assuming a texture call signature.

The client proposal/state remains bounded: choice availability (`READY`, `PENDING`, `EXHAUSTED`, `UNAVAILABLE`) and a cryptic result kind only. A click sends just the selected bounded choice and the current token. The server retains sync ID, parent/current-token chain, station gate, expected preview, one-use action, and close/disconnect authority. No screen coordinate, item, price, stat, count, or diagnostic text crosses from player UI to the action.

Full gate reasons, receipts, terms, projections, durable source state, and stale-token detail belong only in existing secure GM/operator tooling outside the player screen, narration, and player chat.

## Confirmed mechanics for GM/design reference only

These are the operator-confirmed server mechanics. They are not player-facing strings.

| Prayer | Durable terms | Successful immediate effect |
| --- | --- | --- |
| More life | Each prayer grants +1 maximum heart. Normal 10-icon food capacity is retained. Add +25% exhaustion per prayer, to three prayers / +75%. | Set current health to 1 HP, food to 0, saturation to 0. |
| Quicker feet | Each prayer costs 1 maximum heart and grants +10% base movement speed. | Apply vanilla Slowness I for 8 minutes. |

Existing lasting-bargain durability, up-to-three limit, death/reconnect handling, strict receipt persistence, source closure, and authoritative rechecks remain server behavior. They are deliberately absent from player text, narration, tooltips, and private chat beyond its cryptic flavor.

## Interaction and acceptance checks

* At GUI scale 2, verify both exact canvases above with a normal readable native font and native button focus/hover/disabled skins under a resource pack.
* Verify `popup/background` is drawn through the GUI-sprite nine-slice path at `224 x 132`, with no enchanting-table or other container atlas region drawn or masked.
* Pressing an enabled choice sends exactly one one-use server action and immediately enters Pending. It never locally changes health, food, speed, effects, counts, or exhaustion.
* Verify unavailable, power loss, pause, range, stale, duplicate, close, disconnect, replacement, and invalidated-offer paths cannot accept a prayer. The player receives only the bounded flavor state; operator tooling owns precise diagnoses.
* Verify one exhausted prayer disables only its matching button. No count, stat, cost, bonus, reversal, or source-state information appears in visible text, tooltips, narration, or chat.
* Verify success closes the menu and sends the exact life private chat above or the separate cryptic feet private chat above. GM/native tests verify actual mechanics outside player UI.
* Verify Escape and `Close` send the bounded close/revocation action, and no click outside the three native buttons can create a request.

## Runtime implementation

Production uses `drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("popup/background"), x, y, 224, 132)` and native ButtonWidget/font rendering. In pinned1.21.10, Screen.renderWithTooltip already draws the native background; render must not request a second blur. A zero-slot OfferingScreenHandler preserves native opening/closure and server identity while the client uses Screen with ScreenHandlerProvider. Success closes the popup; Escape, Close and screen replacement revoke its offer. Exact native tests and human review status are recorded in [TEST_PLAN](TEST_PLAN.md) and [M11_ALTAR_REVIEW](M11_ALTAR_REVIEW.md).
