# Ancient Forge ritual design — proposal for operator review

## Intent

The Forge should use the Ancient Altar's player-facing language: a compact
`minecraft:popup/background` modal, normal Minecraft font, normal native item
scale and one clear ritual gesture. It is an act of offering, not a recipe
browser. The player sees two offerings, a bowl to press, and eventually a
consequence. The menu contains no target/material/result labels, status prose,
recipe tab, enchantment preview or diagnostic explanation.

Future chest-loot recipe books can provide written clues. They are not a menu
codex and are not part of this implementation slice.

This is a design proposal only. It does not authorize runtime, handler,
network, salvage, balance or asset changes.

## Compact native layout

Use the same generic native popup as the accepted Altar. The production renderer
draws `minecraft:popup/background` via its GUI-sprite nine-slice path. Normal
`minecraft:container/slot` sprites frame actual 16px item renders at their
native 18px size. The existing handler already uses 18px inventory pitch. This
proposal keeps that normal scale and reduces the surrounding empty space so the
inventory occupies more of the panel.

| Element | Panel-local geometry | Player-facing behaviour |
| --- | ---: | --- |
| Popup | `224 x 176` | dark native popup surface, exactly as the Altar framework |
| Title | centred baseline `y=12` | `Ancient Forge`, normal Minecraft pixel font |
| First offering | actual input slot 0 at `48,32` | normal item tooltip/narration; no label |
| Bowl action | native `ButtonWidget` at `102,31`, `20 x 20`; bowl item at `104,33` | visible press/focus treatment, short tooltip/narration: `Offer` |
| Second offering | actual input slot 1 at `158,32` | normal item tooltip/narration; no label |
| Outcome | owned outcome slot at `104,60` | empty until a server resolution; then a normal item tooltip and pickup |
| Main inventory | actual player slots 3--29 from `31,88`, `9 x 3` | normal 18px pitch and native scale |
| Hotbar | actual player slots 30--38 from `31,146`, `9 x 1` | normal 18px pitch and native scale |

The bowl button is deliberately visible even though its label is an item icon:
it uses the native `minecraft:widget/button`, highlighted and disabled
nine-slice sprites, so its hover/focus/pressed frame makes commitment legible.
Its only accessible name is `Offer`. It has no hover enchantment preview. The 10px gap
between outcome and inventory is the only visual separation; there is no
`Inventory` label or explanatory copy.

### GUI scale 2

| Window pixels | Logical canvas | Popup top-left, logical | Popup top-left, physical | Popup extent, physical |
| --- | ---: | ---: | ---: | ---: |
| `640 x 480` | `320 x 240` | `(48,32)` | `(96,64)` | `448 x 352` |
| `1280 x 720` | `640 x 360` | `(208,92)` | `(416,184)` | `448 x 352` |

## Visual ritual states

No state is expressed as player-facing prose. Input/item presence, the native
bowl button frame and the outcome slot tell the story.

| State | What is visible | Outcome |
| --- | --- | --- |
| Idle | empty input slots; ordinary bowl button | empty |
| Staged | two actual input stacks; subtly highlighted bowl frame | empty |
| Working | both inputs are locked; native highlight behind the bowl breathes | empty; nothing predicts an enchantment |
| Successful | both inputs are gone after resolution | one real, component-preserving final item |
| Failed ritual | target and required offering were consumed | one explicit salvage stack |
| Unavailable | staged inputs remain recoverable; bowl button is disabled | empty |

The breathing effect can use `minecraft:container/slot_highlight_back` behind
the normal bowl item with bounded alpha frames. Resource packs therefore still
control the popup, native button, slot, item and highlight art; no Forge-specific
bitmap is needed. The standalone mock-up uses exact local copies of those
pinned vanilla assets only outside Git for visual review. Run
`python scripts/forge_design_preview.py` to recreate that local asset set from
the pinned Minecraft jar without a download.

## Deliberate action, real outcome and lifecycle proposal

1. The player stages two items in actual temporary input slots. The server only
   publishes a bounded staged/unavailable state; it never previews an output.
2. Pressing the native bowl button sends a bounded `offer` action bound to the
   current handler/session/token. Client coordinates, items and desired output
   are never accepted as authority.
3. The server validates player, physical station, readiness, exact component
   snapshots, configured material count and caps. It locks inputs and publishes
   **working**. The client only animates the bowl.
4. After 30 server ticks (1.5 seconds), the server repeats all authority checks
   and resolves once. Correct eligible recipes retain the current exact
   component-preserving result and cap policy. No correct recipe receives a
   random failure unless the campaign later explicitly adds that rule.
5. On resolution the result is a **real owned stack**, not a derived preview.
   It remains in the outcome slot until collected. Closing or disconnecting
   returns/delivers that owned stack exactly once through the handler's normal
   safe recovery route.

Before resolution, power/range/session loss, station replacement, close,
disconnect, death or server stop cancels working and returns both original
inputs unchanged. Service interruption never consumes inputs or creates
salvage. After a resolution commit, its one-use authority cannot resolve again.

## Deterministic failed ritual proposal

For a deliberate wrong pair, consume **one target plus the configured offering
count** and create exactly one server-defined salvage stack. Correct eligible
recipes always win before this rule. The salvage map is server data only; it
must not be inferred from client material, item name or NBT/components.

| Target family | Proposed deterministic salvage |
| --- | --- |
| iron gear/tool | 1 iron ingot |
| gold gear/tool | 1 gold ingot |
| diamond gear/tool | 1 diamond |
| netherite gear/tool | 1 netherite scrap |
| leather gear | 1 leather |
| bow or crossbow | 1 string |
| book | 1 paper |
| stone item/block | 1 cobblestone |
| wood item/block | 1 stick |

Unknown or unsupported targets have no ritual offer available until an explicit
salvage rule is defined. This avoids arbitrary destruction and makes salvage
stable across mods and components. A target/offering pair which has a known
recipe but is already maxed or incompatible is proposed to be a **deliberate
failed attempt**: it consumes the target and offering and yields that target's
configured salvage. This is a material change from the current no-consume
behaviour and requires operator approval before implementation.

Only a single target item can be staged for resolution, retaining the current
workstation restriction. A registered offering uses its configured material
count; an unrecognized right-hand offering consumes one item in a failed ritual.
The bowl stays unavailable if that count is missing or the target has no explicit
recipe/salvage rule. These are proposed server rules, never explanatory UI text.

The outcome tooltip only identifies the final salvaged item after commitment;
the menu does not explain why it appeared.

## Ownership boundary

**Core presentation** would own the compact popup, native bowl button,
normal-slot rendering, accessibility name and client-only working animation.

**Core transaction/configuration** would need the bounded ritual lifecycle,
real outcome ownership, strict server salvage table and existing
handler/session/connection/nonce/cap/component validation. It must preserve
safe staged-input return and suppress all recipe hints from the player client.

**Quiet Below** continues to own placed Forge identity and living-network
readiness through a typed available/unavailable gate. Forge world models and
light remain independent of this menu.

## Approval points and non-goals

Operator approval is required for the deterministic target-consuming salvage
policy, including its maxed/incompatible rule and the initial exact salvage
table. The 30-tick duration is the proposed default.

This proposal does not add recipe books, a codex, random failure for correct
recipes, arbitrary salvage inference, player-facing gate diagnostics, new Forge
art, world-model/light changes, or changes to a correct recipe's existing
enchantment/cap/component behaviour.

## Review checks

* The mock-up has controls outside the game canvas for idle, staged, working,
  success, failed-salvage and unavailable states.
* The popup and slot/item art comes from the local pinned Minecraft 1.21.10
  asset extract in `../reports/forge-ritual-preview-assets`, never from assets
  committed into this repository.
* Both GUI-scale-2 canvas sizes preserve the true 16px item and 18px slot scale,
  and sample inventory stacks make the scale visible.
* Bowl hover/focus says only `Offer`; no state has explanatory prose or a recipe
  disclosure.
