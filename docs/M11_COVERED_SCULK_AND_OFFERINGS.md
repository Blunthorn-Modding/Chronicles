# Covered sculk, client defaults and Forge offerings - dev.40

Authorized 23 October 2026. Implement sequentially, independently review each
slice, then deliver one combined client pack and preserved review server.

1. Client defaults: ship FancyMenu's supported `show_welcome_screen=false`
   option, without modifying/distributing vendor code. Pack generation discovers
   currently listening authenticated loopback review servers in this workspace.
   Ship a client-only preset document. Core merges missing entries through
   Minecraft's native server list, preserving existing labels/order/settings,
   deduplicating loopback aliases and exposing matching hidden history entries.
   Invalid input/list data must remain untouched. No automated connection.
2. Ecology: diagnose direct-exposure vitality below stations and stacked sculk.
   Covered campaign sculk must receive nourishment through its connected network;
   surface exposure still bounds new growth. Preserve physical Heart roots,
   loaded-only bounded work, dormant detached cells, pause/empty gates, existing
   tuning and natural recovery from zero. No oxygen, catalyst upkeep, immunity
   for unrelated blocks or full-world scan. Record exact nourishment propagation
   and stale/unloaded behavior before changing the engine.
3. Forge: exactly one item per entry/offer. Ordinary clicks and shift-clicks
   leave the rest in the player's inventory. After two offerings and the normal
   server-tick ritual, unrelated invalid pairs disappear. For a valid already-
   maxed recipe, return only its unchanged equipment and consume the ingredient
   (operator-confirmed). Interruption before commitment still recovers offerings.
   Preserve unordered resolution, native compatibility/caps, exact components,
   nonce/session/connection authority, owned output and conservation. Preserve
   ambiguous recipe pairs rather than choosing/destructively guessing a target.
   Explicitly handle previously configured multi-count recipes without silently
   making enabled recipes impossible or burning the user's gear.

Verify native fresh-client FancyMenu startup without closing/bypassing a welcome
screen, native server-list preservation/idempotence, client-only pack provenance,
stacked/buried sculk regression and detach/reconnect/unload/station behavior,
and actual Forge single-item controls/invalid consumption/maxed equipment return
with both orders and interruptions. Exact completed evidence belongs in TEST_PLAN.

One dev.39 full-inventory timeout remains a retained reproduction; do not mark
it fixed without diagnosis. Existing death/isolated shutdown/token-expiry/drag
native coverage limits remain unless new evidence closes them. Save-stop and
CRC-check a backup before deployment, preserve current world/config/prayers,
refresh matching client pack, start authenticated/paused; scoped commit/push,
no main merge. The scope above records the plan; completed evidence and delivery follow below.

## Slice 1 evidence

Core client compilation passes. The Python discovery/preset/launch regression
checks pass. Native `python scripts/hud_smoke.py` passes at
`../reports/hud-prototype-20261023T133521539497Z/report.json`: fresh FancyMenu
reaches Title without dismissing/bypassing its welcome screen, and native
server-list tests prove seeding, preserving user labels/order/pack policy,
loopback-alias history promotion, idempotence and malformed/remote input recovery.
Only the authenticated review at 25589 was listening during discovery.
Independent `forge_presentation_review` is clear after mirroring loopback-only
validation in Core. The first private run (`133153450719Z`) failed because a
launcher loop shadowed the username argument; the variable and regression test
are corrected. At that checkpoint the combined review pack had not yet been deployed.

## Slice 2 nourishment and evidence

The six existing loaded neighbour reads also check for an owned neighbour in
completed Heart-certified topology, matching its actual recorded shape and
physically joined through `EcologyTerrain.connected`. Such a neighbour supplies
SHADED nourishment regardless of its current vitality, allowing covered cells
to recover from zero. Direct bright air still takes LIT priority. This local
rule intentionally sustains a fully enclosed connected mass; it does not relay
light or open a growth route. Detached clusters remain DORMANT. Unloaded states
are never read; unresolved topology holds the existing clock. No scan, schema,
chunk ticket or extra terrain-read budget is added.

The new fully enclosed 3D regression first failed with SEALED instead of SHADED,
then passed. The full Quiet Below suite passed 171 tests outside the sandbox;
a subsequently added direct-bright-air priority test passed with the four-case
covered-sculk suite. The initial broader attempt had Windows temporary-directory
AccessDenied failures; the same persistence tests and full suite passed with
normal filesystem access. Existing isolated-seal/unload behavior, station
support, disconnection/recovery/restart and bounded projection/read limits are
retained. Independent source review is clear. Native layered/station evidence
ran with the Forge slice before the combined deployment, as recorded below.

## Slice 3 implementation and native correction

The entry Slot caps both native APIs at one, and Core independently rejects an
oversized entry before either private escrow stage. Valid maxed pairs preserve
the exact equipment components in the owned exit and consume the ingredient;
unrelated pairs clear both offerings after the normal server deadline. Ambiguous
proposals still recover both. Enabled `count != 1` recipes fail with the recipe
ID and single-item requirement, preserving the JSON; disabled legacy entries
remain readable. The current human review has five enabled count-one defaults.
No layout, art or protocol was changed.

Independent source review cleared the slice. The first native run at
`../reports/forge-smoke-20261023T135841045454Z` passed covered station/interior
aging, oversized inputs/config recovery and ordinary single-item clicks, then
caught shift-click remainder relocation. Vanilla repeats QUICK_MOVE while the
source item remains. A second handler pass saw the full entry and fell through
to hotbar/main-inventory routing. Saved player NBT confirmed the unoffered shards
moved to main slot 9. The handler now terminates with EMPTY when staging is
allowed but entry is full; working/result inventory routing remains unchanged.
The reviewer cleared this correction. The same native assertion was retained
and passes in the final full run; this is distinct from the old unexplained dev.39 timeout.

The corrected shift-click native run (`140306213208Z`) passed all 105 initial
A steps and both B steps. Its restart failed an invalid global `block light == 0`
assumption: a campaign-grown catalyst at 0,65,1 emits six light, yielding exactly
one at 2,66,3. A separate saved-world diagnostic confirmed Forge HEAT 0 and
luminance 0. No production lighting change was made. The restart check now keeps
exact off-state/luminance assertions and permits the controlled native catalyst
ambient ceiling; the initial clean off/active/off propagated-light checks remain
exact. Independent review accepts the cause and correction. This ceiling does
not independently exclude hypothetical stale traces below six light.

## Combined delivery

Final native `141028819905Z` passes (105 A / 2 B / 2 restart steps, 47 server
proof commands, 13 PNGs). Final build: 349 Java cases pass plus one existing
Windows symlink skip; Python 74 pass. One authenticated preserved review at
127.0.0.1:25589 is running PAUSED with the combined dev.40 pack, after stopped
CRC backup `forge-pre-dev40.zip` and exact preservation of all 61 protected
world/config/auth files during the jar swap. Both current prayer receipts and
all tuning remain. The pack is generated after server readiness and includes
its preset. Follow [the combined review](M11_COMBINED_REVIEW.md); exact evidence,
failed attempts and remaining limits are in the newest TEST_PLAN entry. This
correction batch is implemented; human feedback is still pending.
