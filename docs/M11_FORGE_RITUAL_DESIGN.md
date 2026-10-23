# Ancient Forge ritual design — order-independent sequential offering

## Intent

The Forge is a vertical ritual: **one entry**, the actual Ancient Forge viewed
from above, then **one exit**. Two items are offered sequentially through the
same entry. The Forge itself never displays either item. Instead, its opening
subtly comes alive after the first offering and stirs only inside that opening
while the ritual works.

The player screen retains the accepted Altar language: native
`minecraft:popup/background`, normal Minecraft bitmap font, 16px items and
18px slots. It has no target/material labels, status text, recipe browser,
enchantment preview or default Minecraft bowl.

The three top-down Forge views are original views derived from the project's
existing Forge model/texture: `forge-top-idle.svg`, `forge-top-kindling.svg`,
and `forge-top-active.svg`. They are mock-up references for eventual
resource-packable Forge art, not new gameplay assets.

## Geometry

Use a `224 x 220` popup. Its outer position is `(48,10)` logical / `(96,20)`
physical in the `640 x 480` GUI-scale-2 window, and `(208,70)` logical /
`(416,140)` physical in the `1280 x 720` GUI-scale-2 window. Its physical
extent is `448 x 440`; the smaller canvas retains a ten-pixel logical margin.

| Element | Panel-local geometry | Player-facing behaviour |
| --- | ---: | --- |
| Title | centred baseline `y=12` | `Ancient Forge`, normal bitmap font |
| Entry | actual shared input slot at `104,28` | receives one offering at a time; normal item tooltip |
| Forge view | `88,51`, `48 x 48` | actual Forge from above, no item render inside it |
| Exit | actual owned outcome slot at `104,107` | empty until resolution; normal item tooltip and collection |
| Inventory tray | `24,128`, `176 x 86` | one recessed, non-interactive backing for inventory and hotbar |
| Main inventory | actual slots 3--29 from `31,135`, `9 x 3` | native 18px pitch |
| Hotbar | actual slots 30--38 from `31,193`, `9 x 1` | native 18px pitch |

The tray reuses the Core workshop panel as a 2px nine-slice. Its `x=24` outer
edge makes the slots a consistent seven pixels inset, and it continues behind
the four-pixel inventory/hotbar gap. It is visual-only and does not capture
clicks. The hotbar ends at y=211, leaving three pixels before the tray ends at
y=214 and six further pixels inside the popup. The Forge view is deliberately
square and centred, so the entry -> Forge -> exit rhythm reads without
explanatory text.

## Sequential, order-independent flow

1. Place either a target **or** an ingredient in the shared entry.
2. Click the Forge view (`Offer` is its only tooltip/narration). The server
   lodges the first stack privately and empties the entry without consuming it.
   The Forge switches from idle to kindling; it does not show the lodged item.
3. Place the other item in the same entry and click the Forge view again.
4. The server considers both orientations of the exact pair and starts an
   unambiguous matching ritual. Identical output and consumption proposals can
   be coalesced; conflicting recipes cannot be chosen by offering order. The active Forge has a
   small contained stirring animation inside its opening, while neither input
   item is rendered in the Forge.
5. The server resolves once after the proposed duration of30 server ticks. A real owned
   output or approved salvage appears in the exit. A new pair cannot begin
   until that exit is collected.

If both orientations match different recipes, the server rejects the ambiguous
pair and returns both stacks unchanged. There is no arrival priority, implicit
"first means target" rule, or client-selected recipe. A correct eligible recipe
never receives a random failure.

Closing, disconnecting, power/range/session loss, station replacement, death or
server stop before commitment returns the private first stack and entry stack
exactly once through native recovery. Close is the initial safe way to retrieve
a first offered item; no extra in-game field or explanation is added.

## Visual states

| State | Entry / Forge view | Exit |
| --- | --- | --- |
| Idle | empty; Forge kindling/booting visual, no stirring | empty |
| First item | one item in entry; quiet Forge kindling visual | empty |
| First lodged | entry empty; Forge kindling | empty |
| Second item staged | second item in entry; Forge kindling | empty |
| Working | entry empty; active Forge stirs inside opening | empty |
| Success | quiet Forge kindling visual | real component-preserving item with cosmetic glint rim |
| Salvage | quiet Forge kindling visual | real configured salvage |
| Unavailable | any staged entries stay recoverable; Forge off/inert | empty |

The mock-up supports both target-first and ingredient-first orders through
outside controls and clickable sample inventory items. Both routes end in the
same sample correct output. The only in-game tooltip is `Offer` on the Forge
view; ordinary entry, exit and inventory item tooltips remain native.

## Authority and pending policy

The existing server identity, exact stack/component, station, cap and one-use
checks remain authoritative. First offer needs bounded private lodged-stack
state; the second offer binds that stack and entry to a one-time action. The
client sends no item roles, recipe identity, output or coordinate authority.

At resolution, a correct recipe preserves existing component/cap behaviour and
creates a **real owned stack** in the exit, never a derived preview. Exit close
recovery/delivery occurs exactly once.

The deterministic target-plus-material salvage policy remains pending operator
approval, as does the maxed/incompatible mapped-pair edge policy. This design
does not choose which first item is a target; roles are deferred until the pair
is evaluated in both orientations.

## Non-goals and review

This design does not implement recipe books, a codex, random outcomes, player
diagnostics, default bowl art, new runtime/configuration/salvage rules, or
Forge world-model/light changes.

* `?state=staged&size=640` renders the second-offering review state. The HTML
  controls outside the game canvas cover all eight visual states and two orders.
* No state renders an item inside the top-down Forge; stirring stays contained
  within its central opening.
* `python scripts/forge_design_preview.py` still recreates only the external,
  pinned vanilla preview assets. The Forge top-down SVGs are repository-owned
  original design material.
* `python scripts/forge_topdown_preview.py` reproducibly projects103 up faces
  per Forge state, depth-sorted from the original axis-aligned Blockbench model.
  The generated SVGs embed the unchanged original campaign texture so they work
  as local browser images without fetching external resources.
