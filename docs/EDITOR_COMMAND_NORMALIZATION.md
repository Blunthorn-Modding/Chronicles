# Editor and command normalization (dev58)

Organizer accepted dev57 on 27 October 2026 and requests remembered navigation, reusable Add pickers, one /chronicle command tree aligned with the current editor, and a Core/content boundary review. Branch: feat/editor-command-normalization. Preserve the accepted world and pack until verified backup/deployment. No main merge.

## Outcomes and boundaries

The generic opener (O or /chronicle) restores the last page, selected record, list page and Locations filter within the player's current connection. Explicit family commands intentionally open that destination. Server Context owns this transient navigation; reconnect/server stop still discard connection state. Reopening never restores review tokens, confirmation dialogs or mutation authority. Missing selections are revalidated. A client navigation snapshot may retain scroll/subpane position only, never unsaved edits or authority.

All Add item/Add lore book actions use one Core native picker component: approved role-style search/paging/list, deliberate selection plus Add, Cancel, optional role-item quantity. Data adapters support local registry items and server-paged book/item definitions; owning screens keep their own domain mutations, entry caps, component preservation and save timing. Role lore shows assigned books plus Add/Remove. Direct chest lore keeps its separate chance dialog; delivery/keep confirmations remain distinct. The shared popup cannot save a stale parent or reopen after deliberate closure.

The sole public owned root is /chronicle. Canonical families: session, players, events, chests, loot-tables, books, regions, locations, roles, setup, editing, debug, game. Nouns match UI tabs. /chronicles, /chronicle area, /chronicle author, duplicate singular families, and /quiet_below are removed from public registration. Existing durable files/IDs/history are retained. There is no alias promise for commands the organizer explicitly asked to replace.

Regions owns create/edit/pos1/pos2/save/confirm/cancel/preview and states. Optional initial Start/End capture uses the same create/shovel/save flow. State capture/apply/removal use named Regions and explicit review/confirm where needed, not internal Area IDs and manually supplied revisions. Locations owns Points/Block sites; departure setup remains its popup. General operational inspection and legacy/recovery functions live under debug where necessary. Quiet Below's retained scenario stage operations move under /chronicle game quiet-below; their gameplay authority/recovery guards remain.

Core owns reusable UI, navigation, registries, roles, loot/books, geometry, saved-state transactions, departures, session/build gates, item enchanting and lasting-effect engines. Quiet Below owns sculk ecology, stations/assets, recipes, narrative/default cast, and scenario-specific bargain policies. Audit actual code before moving policy; avoid storage migrations or unrelated engine rewrites.

## Execution and evidence

1. Inventory commands, picker flows and module ownership; lead reviews a dedicated shared-popup design.
2. Remembered opener and shared picker with role/loot/chest parity, stale/Cancel preservation.
3. Normalize registrations and all production/native-fixture callers, preserving permissions, holds, loaded-only access, physical departures and revision-pinned review/recovery.
4. Resolve bounded boundary violations; record correct existing boundaries and deliberate follow-ups.
5. Independent source review; focused pure checks and full build/Python tests; serialized native menu/books/roles/regions/departure/pause checks as risk requires. Verify public command tree and removed roots, operator/nonoperator gates and actual typed commands.
6. Stopped verified backup, matching dev58 jars/pack, authenticated live preset/defaults/licenses/data preservation, commit/push; combined organizer review.

Do not claim the prior intermittent outline report fixed without reproducing it. No new disk-fault/maximum-size/group-soak certification or gameplay/content tuning is part of this normalization.

## Core and Quiet Below responsibility audit

Core has no production dependency on Quiet Below IDs/classes. Durable offerings, enchanting/caps/escrow, book delivery/discovery, role YAML/assignment/once-only entry, geometry/state/departure transactions and managed placement are reusable engines. Quiet Below correctly registers concrete terms, maximum purchases, sculk power/eligibility, aftermath and flavor in AncientAltars; physical Forge heat, recipes, journal assets and loot in AncientForges/ForgeRecipeDefaults; default characters in its player-roles.yaml; ecology and its Game setup controls in QuietBelowEcology. The cap=1 recommendation is scenario-owned while Core's independent default is unrestricted. These boundaries remain intact.

The bounded correction is presentation: Core's generic Offering UI no longer supplies altar/prayer flavor. LastingBargains accepts a bounded per-group Presentation descriptor with neutral defaults. Quiet Below supplies exactly its existing seven mysterious status lines. View/action IDs advance to offering_view_v2/offering_action_v2 with matching pack required; terms, counts, statistics and gate diagnostics remain server-only. No durable receipt/config migration, rule or asset move is needed. Core fixtures referencing QB IDs are test integration, not production ownership.

The retired CF09 screen, server, payload and DTO-only tests have been removed; there is one current editor transport. Operator feedback consistently calls the server-owned mode Editing. The default Fabric regression retains its backend/durability assertions and uses strictly disposable trial helpers for observation setup/reset where no production CLI exists; reviewed manual delivery still uses the real Events facade. The obsolete separate Area/Departure catalog smoke has been replaced by the native Region/Location subtype suite; its old script entry point forwards explicitly to that suite.

## Combined organizer review

Use the matching dev58 pack. The review server keeps its existing address, world and configuration; deployment follows verification and a stopped backup.

1. Open with **O** or `/chronicle`, choose a page and record, close, then reopen. Check that the page, selected record, list position and Locations Points/Block sites subpage return. Reconnect starts a fresh connection; unsaved forms and confirmations are not restored. A direct family command deliberately opens its named page.
2. In Player Roles, add an item and private lore book. Search, select, then Add; Cancel leaves the assigned list alone. Quantity belongs to role supplies; existing component-bearing stacks remain separate.
3. In Loot Tables, add an ordinary item and named book using the same popup. Percentages/counts remain table drafts until Save. In Chests, choosing a specific book continues to its existing chance form.
4. During active play, **Keep book placed until found...** retains its own reviewed workflow and does not require Editing. It is a delivery operation, separate from adding a definition to a table.
5. Check tab completion for `/chronicle`. Regions has create/edit/pos1/pos2/save/cancel/preview and states; there is no second `author` or `area` command workflow. Use the [reference](COMMAND_REFERENCE.md) for operational/debug paths.

Shared picker rows adapt to short windows; row scrolling and server paging remain separate controls. Ordinary item IDs appear in tooltips, with registry detail retained where the role adapter already supplies it. Debug remains optional.

## Verification notes

Independent source reviews covered the merged command gates, stale frames, remembered selection validation, popup cancellation/late responses and Core/content ownership. Native suite paths and final build counts are recorded in the newest [test plan](TEST_PLAN.md) and deployment receipt.

The formerly separate CF09 transport and Area/Departure screen probes are retired, while backend/durability assertions are retained in current suites. Scenario smoke drivers use the real canonical commands. Setup-only source/Heart/takeover bindings use a finite scenario-owned trial bridge, enabled only by native-test environment settings and checked again against a disposable loopback server and expected actor; it is absent on the authenticated review server. This keeps removed reverse-assignment CLI paths retired.

Two old fixture assumptions were corrected without changing gameplay: role-death checks now clear the already-verified original kit before the first kill so scattered drops cannot masquerade as reissued supplies; paused Altar checks use valid non-active Editing. Ordinary Altar death is tested normally. The unknown-price check first proves paused damage is blocked, then uses vanilla's player-replacement service administratively to test modifier retention while frozen. It is not a claim that paused gameplay permits death.

Handcrafted-map integration, the final narrative/content pass and real group/voice soak remain separate work. This change does not certify a new disk-fault, maximum-size or process-kill scenario.

The migrated Warden rehearsal caught an operator self-join command regression: the new facade initially routed it through administrative participation editing. The canonical self-only command now calls the existing self-participation service for operators and ordinary players alike, preserving during-play opt-in and departure guards. GM assignment remains non-active Editing only. The Warden fixture also sets the ordinary Heart delay to 3600 seconds and asserts it, isolating vanilla warning behavior from the newer direct last-stand summon; no gameplay defense is disabled.

The retained diagnostic terrain-stage rehearsal exposed a pre-existing mismatch with the newer cosmetic vitality property. Its runtime codec now maps only healthy vanilla sculk to the original empty-property journal representation. Nonhealthy variants retain their property and remain conflicts; all other states still require complete properties. Existing stage journals and Core region/state engines are unchanged. Native rehearsal includes a changed-vitality undo refusal before restoring healthy sculk for the successful undo.

The actual state-action rehearsal also caught a facade argument mismatch: event execution and condition changes forwarded quoted namespaced IDs into identifier arguments. Both now validate/canonicalize the Identifier and pass it unquoted. String arguments remain escaped. Event review pins, active-play checks and condition boolean parsing are unchanged.

The older Core driver now edits the normalized maximum corner with pos2, uses the current draft/participation feedback and short active banner, expects the existing Forge-notes sixth loot type, and routes legacy captured-chest operations through debug. Its override/recommendation check follows canonical inspection text rather than the retired chat picker. Geometry, components, audience, clock, receipt and restart assertions remain intact.

The Core paused-kill oracle now proves the same player remains alive with unchanged health and automatic-policy history. Two real ACTIVE deaths still verify automatic and vanilla fallback destinations. Policy configuration records its actual pre-edit location rather than the retired screen?s hardcoded viewing position.
