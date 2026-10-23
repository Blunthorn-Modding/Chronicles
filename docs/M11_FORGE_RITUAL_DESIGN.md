# Ancient Forge ritual design — sequential offering proposal

## Intent

The Forge now follows one vertical ritual path: **one entry**, an illustrated
**basin**, then **one exit**. A player adds two items sequentially through the
same entry. It uses the Ancient Altar's compact native popup, normal Minecraft
font and normal item/slot scale; it has no labels, status prose, recipe browser,
enchantment preview or default Minecraft bowl.

The basin is original Forge illustration, not an item and not a copied vanilla
workstation. Its pixel geometry is in
[`design/forge-basin.svg`](design/forge-basin.svg). Future production art can
be a resource-packable bespoke basin sprite; this SVG is a design mock-up only.

Recipe books may later appear as written chest loot clues. They are outside this
screen design and are not a menu codex.

## Compact native layout

The panel is `224 x 212`, using the same `minecraft:popup/background`
nine-slice path as the accepted Altar. Entry, exit and player inventory use the
native `minecraft:container/slot` art at 18px, with unscaled 16px item renders.
The compact vertical sequence leaves room for a normal-sized inventory.

| Element | Panel-local geometry | Player-facing behaviour |
| --- | ---: | --- |
| Popup | `224 x 212` | dark native popup surface |
| Title | centred baseline `y=12` | `Ancient Forge`, normal bitmap font |
| Entry | actual shared input slot at `104,30` | accepts one item at a time; normal item tooltip |
| Basin | `80,56`, `64 x 32` | original illustrated stone/sculk/ember basin; click tooltip/narration says only `Offer` |
| Lodged target | `104,64`, rendered over basin | noninteractive normal 16px item render after first offer |
| Exit | actual owned outcome slot at `104,96` | empty until resolution; then normal item tooltip and collection |
| Main inventory | actual slots 3--29 from `31,126`, `9 x 3` | normal 18px pitch |
| Hotbar | actual slots 30--38 from `31,184`, `9 x 1` | normal 18px pitch |

There is no inventory label: the native grid is immediately recognisable. The
hotbar ends at panel-local y=202, leaving ten pixels above the popup's bottom.
At GUI scale 2, this is `(48,14)` logical / `(96,28)` physical in a `640 x
480` window and `(208,74)` logical / `(416,148)` physical in a `1280 x 720`
window. The popup extent is `448 x 424` physical pixels in both cases.

## Sequential ritual flow

1. Place a target in the single **entry** slot.
2. Click the basin. The server validates and lodges that target in the basin;
   it is not consumed. The entry becomes empty and the target visibly rests in
   the basin.
3. Place an ingredient in the **same entry** slot.
4. Click the basin again. Only then does the server start the ritual. It locks
   the lodged target and entry offering, and no result is predicted.
5. After the authoritative resolution, the real owned final item or salvage
   appears in the **exit** slot. Until that exit is collected, the entry cannot
   accept a new pair.

Closing, disconnecting, power/range/session loss, station replacement, death or
server stop before commitment returns both the lodged target and the entry item
exactly once via the handler's native recovery route. The initial design offers
no extra "take back" control: closing is the deliberate, safe way to recover a
lodged target before the second offering. This avoids a third interactive field
and keeps roles clear through item location.

## Visual states

| State | Entry / basin | Exit |
| --- | --- | --- |
| Idle | empty entry; quiet basin | empty |
| First item | target in entry | empty |
| Target lodged | entry empty; target item rests in basin | empty |
| Second item staged | lodged target plus ingredient in entry | empty |
| Working | both items locked; basin pulses with bounded light | empty |
| Success | basin clear | real component-preserving target in exit, with cosmetic glint rim |
| Salvage | basin clear | real configured salvage in exit |
| Unavailable | existing items remain recoverable; basin is inert | empty |

The basin's click target has only the native-tooltip/narration word `Offer`.
All ordinary items retain their normal tooltip/narration. There is no visual
text explaining a state or disclosing a recipe.

## Authority and outcome proposal

The existing server identity, exact stack/component, station, cap and one-use
checks remain authoritative. First offer must generate a separate bounded
"lodged target" authority; second offer binds that target and its entry offering
to the later commit. Client clicks never choose a result or submit item data.

The proposed working duration remains 30 server ticks. At resolution, correct
eligible recipes preserve the current component/cap behaviour and produce a
**real owned stack** in the exit, never a derived preview. Closing after commit
recovers/delivers that exit stack exactly once.

The previously proposed deterministic target-plus-material salvage policy stays
pending operator approval: a deliberate wrong pairing consumes the lodged target
and required ingredient count, then yields a result from an explicit server
salvage table. Unknown targets have no offer path. The edge policy for maxed or
incompatible mapped recipes remains pending review; this design does not settle
it. Correct eligible recipes never receive a random failure.

## Ownership and non-goals

**Core** would own the sequential handler state, lodged-target recovery, exit
ownership, compact popup/slot screen and client-only basin animation. **Quiet
Below** continues to provide only a typed physical Forge/readiness gate; world
models and light are separate work.

This design does not implement new recipes, recipe books, a codex, scrap
balance, random outcomes, player-facing diagnostics, default bowl art, world
model/light changes or the outstanding salvage policy.

## Review checks

* The HTML mock-up exposes idle, first item, target lodged, second item staged,
  working, success, salvage and unavailable states via controls outside the
  game canvas. `?state=staged&size=640` selects the staged review view.
* Click-through in the mock-up mirrors the sequential rhythm: sample inventory
  item -> entry -> basin -> lodged -> sample inventory item -> entry -> basin.
* The `224 x 212` panel fits both GUI-scale-2 canvases with true 16px items and
  18px slot frames. The original basin is 64x32 and no default bowl appears.
* `python scripts/forge_design_preview.py` recreates the external pinned vanilla
  assets used only by the local HTML preview; no upstream artwork is committed.
