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
no main merge. This document records the plan, not completed validation.

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
are corrected. No final review pack has been deployed yet.
