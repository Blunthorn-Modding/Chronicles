# Core workstation transaction contract - dev.39

This replaces the dev.36/37 derived-preview and custom Take protocol. The
operator approved the [vertical ritual design](M11_FORGE_RITUAL_DESIGN.md) on
23 October. [Implementation boundary](M11_FORGE_RITUAL_IMPLEMENTATION.md).

Core owns a native 38-slot handler: entry 0, owned output 1, player inventory
2..37. Both offerings use the same entry sequentially. Offer removes the whole
entry stack into private server escrow; after the first, the empty entry accepts
the second. No client payload or handler slot exposes the private stacks. The
client receives only the phase, availability, action identity and supplied art
IDs. No recipes, target/material roles or prospective output are transmitted.

The second Offer starts a 30-server-tick deadline. At completion, evaluate both
orientations against the current recipes, native compatibility and universal
caps. Identical exact output/consumption proposals coalesce; conflicting ones
are rejected. A valid pair consumes one target and the configured ingredient
count, returns unused ingredient remainder and creates one actual output in the
exit. Invalid, already-maxed or ambiguous pairs return unchanged; destructive
salvage is pending a separate decision. Neither order receives priority.

Each Offer binds the connection, actual current handler/sync ID, session UUID,
rotating one-use action UUID and exact observed entry stack. Tokens expire after
30 seconds and rotate on stack/state changes. Before committing, recheck the
exact escrow snapshots, unchanged recipe revision and cap policy, eligibility,
power, physical station identity and current connection/handler. All work runs
on the server thread. Four live bindings are checked per tick, at most 256 open
handlers. The work deadline uses actual server ticks rather than visits to the
bounded checker, so load can delay completion but never accelerate it.

Clear private ownership before returning stacks or publishing output. Native
slot updates and inventory transfers remain authoritative. Working locks the
entry; output cannot be inserted into and can only be taken in RESULT. Once
committed, native pickup, shift-click, cursor, number-key and drop behavior act
on an owned output; collecting it cannot repeat the recipe. Power or policy
loss after commitment does not erase it. A new pair waits for output collection.

Before commitment, config/cap/gate invalidation returns private offerings and
any staged entry exactly once. Physical replacement/unload, invalid player,
disconnect, close and orderly server stop settle the handler, including entry,
output and vanilla cursor recovery. A full inventory uses vanilla item drops.
This is normal Minecraft persistence, not a separate crash-atomic escrow
ledger. Abrupt process failure retains vanilla workstation limitations.

The breaking payloads have new IDs: `workshop_ritual_view_v1` and
`workshop_ritual_offer_v1`. Capability checking happens before opening the
handler; use matching dev.39 clients and server. No world, JSON recipe/cap or
accepted prayer migration is needed.

Core owns recipe configuration, private transaction state, handler, protocol,
native controls and the generic screen. Campaigns supply three resource-packable
art IDs, their gate and cosmetic completion feedback. Quiet Below retains its
intrinsic station identity, existing network power rule, Forge models and sound.
No block storage, crafting price, recipe discovery book or diagnostic prose is
added by this change. Completed verification and remaining limits are recorded
in the newest [TEST_PLAN](TEST_PLAN.md) entry.
