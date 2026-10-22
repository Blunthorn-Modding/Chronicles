# Ancient Altar workstation design

**Lead approval,22 October2026:** dedicated designer revised the initial overlapping/small-font proposal before implementation. Lead rendered the final300x222 design at640x480 and1280x720 and checked movement, unpowered, maxed and pending states. Exactly36 slots exist; pending disables acceptance and refresh. Screenshots are local evidence in `../reports/altar-design-640.png` and `altar-design-1280-maxed.png`. Layout approved for implementation; native Minecraft font/input/inventory behavior remains required verification. Core owns the reusable handled offering screen and typed data; Quiet Below keeps its trades and physical gates. No Forge or other feedback is implemented in this slice.

## Decision for lead review

Replace the current full-screen `OfferingScreen` form with one ordinary vanilla-style handled workstation panel. The panel is a fixed **300 x 222 logical-pixel** container at GUI scale 2. This merchant-width panel fits the real native 9-pixel font and keeps the actual player inventory in its conventional lower area, but has **no Altar inventory slots**: these lasting bargains exchange only durable player stats. The two upper bargain cards are selection controls and their heart, food, and boot marks are drawn labels/icons, never stack-backed `Slot`s or fake items.

The screen shows the two Altar bargains at once. Selecting a card immediately requests a fresh, one-use server offer and updates its authoritative preview. The selected card exposes concise separately supplied sacrifice and bonus lines, its count, and all three resulting totals. One button, `Make permanent bargain`, is the sole acceptance interaction. A short red permanence line directly above it is information, not a checkbox or a second confirmation step. A visible `Refresh` control lets an expired or failed offer request a new snapshot. This gives the irreversible choice enough weight without turning a workstation into a full-screen form.

The stated terms are exact:

| Bargain | Sacrifice | Bonus | Independent cap |
| --- | --- | --- | --- |
| Extra heart | +1 maximum food icon (+2 food); higher ordinary/fast regeneration threshold, with no refill | +1 maximum heart (+2 health) | 3 |
| Faster movement | -1 maximum heart (-2 health) | +10% base movement speed | 3 |

Purchases survive death and reconnecting. No player reversal exists. When the Quiet Below source ends, its matching bonus and sacrifice are both removed. No item, XP, or food stack is consumed.

## Geometry and visual language

Use a normal dark, beveled inventory background and the game's normal slot frame treatment. Keep the generic Core cards reusable: use narrated native buttons with a second `Sacrifice -> Bonus` caption, exact selected terms underneath, and a small readiness mark. No copied or vendored Minecraft textures are used. All text uses the native approximately 6-pixel-wide, 9-pixel-high font at logical size; do not shrink it, clip it, or use overflow hiding to make labels fit. The title uses the regular screen title treatment. The powered mark is teal/green; a disabled state is muted grey with amber/red status text. The design avoids a custom full-window backdrop, side navigation, scroll panes, or character sheet.

All positions below are **logical GUI pixels**, relative to the panel's top-left. The physical screen positions are derived from the normal `ScreenHandler` centring rule.

| Element | Panel-local rectangle / baseline | Semantics |
| --- | --- | --- |
| Panel | `300 x 222` | Standard workstation background; fits in the 320 x 240 GUI-scale-2 minimum |
| Title | centred baseline `y=6` | `Ancient Altar` |
| Live power/status strip | `x=12, y=18, w=276, h=9` | explicit server-owned station state and concise failure; distinct from an exhausted card |
| Extra-heart card | `x=8, y=29, w=140, h=24` | selection button; `Extra heart` + `n/3`, generic `Sacrifice -> Bonus` caption; exact food/heart terms immediately below |
| Movement card | `x=152, y=29, w=140, h=24` | selection button; `Faster movement` + `n/3`, generic `Sacrifice -> Bonus` caption; exact heart/speed terms immediately below |
| Selected terms | `x=12, y=56/66, w=276` | one native-font line each: bounded `Cost` and `Bonus` fields |
| Structured result preview | `x=12, y=76, w=276` and `y=86` | one typed Health + Food row, then typed Speed current `->` after row |
| Permanence line | centred baseline `y=96` | `Permanent; no reversal. Ends with network.` |
| Accept button | `x=52, y=108, w=150, h=20` | sole irreversible action; label fits at native font size |
| Refresh button | `x=208, y=108, w=80, h=20` | visible non-accepting request for a new server offer |
| Inventory caption | `x=8, y=130` | `Inventory`; informational only |
| Player main inventory slots | item coordinates `x=70+18c, y=140/158/176`, `c=0..8` | real `PlayerInventory` slots; each 16 x 16 item area has an 18 x 18 frame at `(x-1, y-1)` |
| Player hotbar slots | item coordinates `x=70+18c, y=198`, `c=0..8` | 18 x 18 frames start at `y=197`, end at `y=215`, preserving a six-pixel interior bottom bevel margin |

At GUI scale 2, the same panel appears as follows. Coordinates are physical pixels and preserve the `300 x 222` logical panel exactly.

| Window | Logical canvas | Panel top-left, logical | Panel top-left, physical | Panel extent, physical |
| --- | --- | --- | --- | --- |
| 640 x 480 | 320 x 240 | `(10, 9)` | `(20, 18)` | `600 x 444` |
| 1280 x 720 | 640 x 360 | `(170, 69)` | `(340, 138)` | `600 x 444` |

The 640 x 480 case leaves 20 physical pixels at each side and 18 above/below the panel. The normal screen resizes and recentres on resolution or GUI-scale changes; it never crops, shrinks, or reflows the player inventory. The mockup offers exact 640 x 480 and 1280 x 720 canvases only; it has no responsive scale-down rule.

## Screen states

### Powered / selectable

The top strip reads the explicit server state `Network power: ready`. The selected card has a bright border and subtle fill. Each card carries its own authoritative count (`0/3`, `1/3`, and so on). The client renders the typed server preview into three fixed compact rows; it neither renders the old free-form `result` string verbatim nor parses localized text. For example:

```
Sacrifice  +1 food icon (20 -> 22 maximum food)
Bonus      +1 heart (10 -> 11 maximum hearts)
After      food 20 -> 22     health 10 -> 11 hearts
            speed 100% -> 100%
```

For movement, the same region says `Cost: -1 maximum heart` and `Bonus: +10% base movement`, then shows the combined current/after health, food, and walking speed values. The server preview already incorporates foreign modifiers; the UI must not locally calculate or promise values.

### Unpowered / ineligible / range failure

Keep the panel open and display only the bounded server failure message in the status strip, such as `Altar unpowered`, `The Altar is out of reach.`, or `Join participation and leave editing mode.` Both cards remain readable, so the player can understand the trade, but their selection and the accept button are disabled. There is no noisy modal or chat echo. The always-visible Refresh control requests a newly issued snapshot after expiry or failure.

### Maxed

A three-purchase card uses the muted treatment, shows `3/3 — complete`, and cannot be selected or accepted. The other bargain remains independently available. If both are complete, show `All Altar bargains are complete.` as the card-area message while preserving the independent explicit station state in the top strip and leaving the real inventory usable.

### Pending / stale / completed

After a card selection or acceptance click, immediately disable selection, acceptance, and Refresh and show `Waiting for the Altar…`. Do not alter counts or totals optimistically. A returned view, timeout, or expired offer re-enables Refresh. `Offer expired`, selection changes, physical changes, pause, range, or power failure is displayed concisely with `No bargain accepted.` When the server reports an accepted receipt, render its returned count/preview and `Bargain accepted.` A close, Escape, death/disconnect, or a new screen revokes the server offer; reopening or an enabled Refresh always fetches a fresh snapshot.

## Interaction contract

### Mouse, keyboard, inventory

* Left-click either enabled bargain card to select it. The client sends the existing bounded `select` action and locks controls until the successor token/view arrives. Hovering gives a normal small tooltip with complete terms: sacrifice, bonus, survives death/reconnect, no player reversal, and both sides end when the network ends.
* Left-click `Make permanent bargain` to send the existing `accept` action for the selected server token. The control has no client-only confirmation state, repeated click, or optimistic stat change.
* Escape, the close button, opening another screen, or screen removal sends `close` once. The client must preserve the existing replacement behaviour: an old screen must not revoke the successor created by a fresh selection.
* Keyboard: `Left`/`Right` select the enabled card (the same server request as a click); `Enter` activates the focused accept button; `R` activates Refresh and Tab navigates native controls; `Esc` closes. Tab focus follows the available native buttons; inventory slots retain vanilla mouse/number-key interaction. Enter must be ignored while pending/disabled.
* The lower slots are ordinary player slots. Normal left/right/drag, number-key hotbar swap, double-click collection, and shift-click work only through the backing player inventory. Because the Altar has no item slots, shift-click never routes an item into a sacrifice or output.

### Server authority and bounded sync

The implementation should map UI actions to `OfferingPayloads.Action` exactly: `0 select`, `1 accept`, `2 refresh`, and `3 close`. It should keep `LastingBargains` as the authority for the 60-second, connection-bound, one-use token, selection, expected preview, durable ledger commit, and error wording. The opening handler and later updates carry only bounded `OfferingPayloads.View` data; the client draws it; it does not send prices, counts, stats, inventory contents, or source identity.

Replace the presentation-only free-form `Choice.result` contract with bounded structured data: per choice, bounded `cost` and `bonus` lines (each maximum 32 printable ASCII characters, reserving width for their Cost/Bonus prefixes; complete descriptions may use other glyphs) plus typed actual native maximum-health/speed doubles and integer food capacity before/after, preserving foreign modifiers without premature rounding. Server-issued `stationReady` and bounded `stationProblem` represent power/access; `completed` marks expired/failed offers separately. The client never parses the reason text to decide eligibility. A separate state enum was unnecessary for this slice. A card is complete solely when `count >= limit`; it must never be used to infer station power. These additions remain server-owned and bounded; no client world inspection or parsing of English/localized strings is allowed.

For the workstation conversion, the lead should retain the rechecks already present in `AncientAltars.Gate`: original block-entity identity, same world, alive/non-spectator state, six-block radius, Survival/Adventure, active participation/non-authoring, and living-network power. The server must repeat them at acceptance after consuming the token. A stale, expired, replaced, cross-player, disconnected, or closed-source action cannot commit. Source ending must close ledger receipts and invalidate pending views before any later action can apply them.

The implemented Fabric extended opening binds the initial view to the native handler sync ID. Successors update that same handler, and a bounded round-robin poll (four of at most256 offers per tick) refreshes only actual gate, ledger or native-stat changes. Private connection/token/parent checks still guard every action. Closing a prior handler cannot revoke a newly created handler.

The screen handler needs only the player inventory slots. It should not create an Altar inventory, block-entity inventory, fake stacks, item transfer rule, or client-owned stat cache. The server can open a normal `NamedScreenHandlerFactory`/`ScreenHandler` tied to the physical Altar position and use the bounded payload for bargain data; closing must revoke the payload offer in addition to vanilla handler closure.

The Ancient Altar has two cards and needs no paging. Generic Core groups still permit up to 16 choices: use the same two-card row with server-indexed 9 x 9 previous/next arrow hitboxes at `x=12` and `x=279`, `y=18` (status text uses the remaining span). They show two choices per page and a bounded page number, never shift the fixed main layout or add a scroll pane. The client must not assume the Altar's two-choice count when rendering another group.

A nonstandard client resource-pack font may still make a compact term too wide. The client measures the complete Cost/Bonus lines at native font size, explicitly reports the fit failure and disables acceptance instead of clipping irreversible terms. The server enforces the conservative 32-character printable-ASCII registration and wire bounds; the client width guard is only a resource-pack presentation safeguard.

## Acceptance tests for implementation

1. Open a powered Altar at GUI scale 2 on exact 640 x 480 and 1280 x 720 canvases. The 300 x 222 panel is centred at the documented geometry, native-font labels are fully visible, and all 36 player inventory/hotbar slots are reachable at their documented item/frame coordinates. The last hotbar frame ends at `y=215`, preserving its six-pixel interior bottom margin.
2. Verify the card labels show exactly Extra heart (food icon sacrifice to heart bonus) and Faster movement (heart sacrifice to +10% movement), each with an independent `0/3` count. Verify no item input/output/sacrifice slot exists.
3. Select each bargain and assert the bounded server-supplied cost/bonus lines and structured actual current-to-after health, food, and speed totals render. Add a foreign modifier and verify the preview changes from server data, not a local formula or parsed result text.
4. Buy Extra heart three times and Faster movement three times independently. Each accepted return updates the relevant count and projection; a fourth attempt is disabled/rejected without changing the ledger. Check death/reconnect retains both sides.
5. Confirm one accept press sends one token action; duplicate, stale, cross-player, expired, closed, selection-changed, and successor-revoked tokens never make a receipt. The UI waits for server acknowledgement and never shows an optimistic purchase.
6. During an open screen, cut power, pause/invalidate participation, move beyond six blocks, replace/remove the Altar, end the source, disconnect, and close with Escape. Each prevents acceptance, uses concise messaging, and leaves no pending offer capable of committing later.
7. Exercise mouse, keyboard selection/focus/Escape, and ordinary lower-inventory click, drag, number-key, and shift-click flows. Shift-click never targets an Altar bargain because only player slots exist.
8. Verify a maxed card is muted/disabled while the other is still selectable; verify both maxed state remains readable, remains distinct from unpowered, and inventory remains normal. Exercise Refresh after expiry and failure. For a registered 16-choice Core group, verify two-card pages preserve server indices/tokens without a scroll pane.

## Implementation questions that affect the wiring

The visual design is settled, but the lead must choose the smallest native transport shape: a `ScreenHandler` opening that owns the real player slots plus the bounded payload, or a handled screen associated with a position without adding a station inventory. The recommendation is the former because it naturally preserves vanilla slot semantics. This is an implementation choice, not an authorization to add storage.

`OfferingPayloads.View` currently lacks the structured preview/terms and explicit live station state needed by this layout. Add those bounded server-owned fields before implementation; its existing token replacement and count/limit still provide stale protection and completed-card state. A revision field is optional because successor tokens already bind the one-use snapshot, but any new station generation must be server-issued and checked at accept time.
