# Sequential Forge implementation - dev.39

The operator approves the revised ritual layout on23 October2026. This replaces
the design-only hold for the screen and sequential offering flow. The approved
[design](M11_FORGE_RITUAL_DESIGN.md) and [mockup](design/forge-ritual.html) define
the visual result. Destructive salvage and the maxed-item sacrifice rule remain
unsettled; this implementation preserves both inputs for invalid/maxed pairs.

## Implementation boundary

Core owns a224x220 handled screen with exactly one visible entry, one owned
output and36 player slots. The first offering moves into private server escrow;
the entry clears for the second. Both offering orders use the same recipe and
consumption rules. After the second deliberate Offer, the server waits30 actual
ticks before resolving. A correct recipe consumes one target and the configured
ingredient count, returns unused ingredient remainder, and creates one real
output. Invalid or ambiguous recipes return the input stacks unchanged.

The server checks current handler, connection, one-use token, exact stacks,
physical station, eligibility, power and current recipe/cap policy. Interruption
before completion returns all inputs once. The completed output belongs to the
handler and remains recoverable even after power loss; ordinary native output
pickup/shift-click and close/drop rules apply. No derived output preview exists.
This uses vanilla inventory persistence, not a separate crash-atomic ledger.

The client receives only phase, availability, action availability and artwork
IDs. It receives no recipe catalog, diagnostic reason, held-item components or
prospective result. Normal item synchronization covers only the actual visible
entry/output/player slots. The only Forge action tooltip/narration is `Offer`.

Core draws native popup/slot/font components, its existing shared beveled
inventory tray, and campaign-provided illustration IDs. Quiet Below supplies
three original top-down Forge images and its existing physical readiness gate.
`scripts/forge_workshop_art.py` reproducibly renders the original Blockbench
up faces and unchanged RGBA atlases into64px GUI sprites. Normal ready/idle uses
kindling; unavailable uses off; working uses active with contained warm embers.
The client's animation never selects an outcome or moves an item.

## Compatibility and validation plan

The new payload shape uses new protocol identifiers. Install matching dev.39
clients/server; old clients must receive the existing missing-client explanation
rather than interpreting an incompatible payload. Existing world, recipe/cap
JSON, ecology journal and accepted Altar receipts require no migration.

Verify bounded payloads and unordered matching, the actual native entry/Offer/
working/output cycle in both orders, exactly-once closure/remainder handling,
stale/replayed/foreign authority, interruption/config/cap changes, two clients
and orderly stop/restart. Actual native screenshots must match both GUI-scale-2
sizes, the inventory tray, idle/off/working art and resource-pack overrides.
The latest [TEST_PLAN](TEST_PLAN.md) records completed evidence and limits;
this implementation plan is not a claim that those checks have already passed.

Before deployment, save-stop the authenticated review, preserve a checked
backup and compare all world/config/auth hashes during the jar swap. Retain
accepted prayers and tuning. Refresh/verify the matching pack and start the same
loopback review paused. Never merge main automatically.

## Verified delivery

Dev.39 is deployed to the preserved authenticated review, paused at
127.0.0.1:25589 with the matching pack. Final build, 343 Java passing tests
(one existing Windows symlink skip), 70 Python tests, independent source/fixture
review and two full native two-client/restart rehearsals pass. The lead inspected
the actual screen at both GUI-scale-2 window sizes and the contrast-pack tooltip.

The latest [TEST_PLAN](TEST_PLAN.md) records precise reports and limits,
including one earlier unreproduced owned-output timeout. A diagnostic-only
server checkpoint was added; it does not change power, advance time or commit
an output. Isolated death, stopping before client disconnect, token expiry and
full multi-slot drag remain native hardening follow-ups. The next review is the
human interaction/appearance check in [M11_FORGE_REVIEW](M11_FORGE_REVIEW.md).
