# CF22: editable Events and Region Saved-state actions

The organizer confirmed the dev61 Region, sound and text workflow. The subsequent [Flags and Event filters](FLAGS_AND_EVENT_FILTERS.md) follow-up adds a dedicated Advanced Flags page and separates composed Game Events from direct Region Changes in dev62/menu protocol v9. The execution and recovery mechanisms described below are unchanged; the dev61 version/deployment details are historical.

28 October 2026. Dev61 is implemented, verified and deployed for organizer review on `feat/core-event-region-actions`. No main merge. The matching review server is `127.0.0.1:25589`; use the updated `../client/Chronicle-M9-1.21.10.mrpack`.

## Organizer workflow

Open `/chronicle events`. In non-active Editing, **Create new event** appears above the list regardless of the selected event kind. **Edit event** opens the same four-page editor:

- **Basics:** generated/editable ID, name and organizer description. Existing IDs remain fixed.
- **Message & sound:** optional message and searchable registered-sound selector. **Preview privately** sends only the draft media to the operator, even if the audience or trigger is incomplete. It never applies Region changes or records completion.
- **Trigger:** all participants or a separate audience Region; Manual, Entry, Active time or Condition. Entry uses its own Region. Any trigger may have an optional prerequisite; Condition requires one. Active time excludes pauses, empty play, Building Mode and operation holds.
- **Region changes:** add up to eight current Start/End states from Regions. Each target can show private bounds or be removed. No snapshots are captured here. Recaptured/moved targets must be selected again explicitly.

Cancel discards the local draft. Save creates or replaces the entire Event configuration without running it. Changed permissions or server state preserve the draft for inspection but disable stale writes. Clean/Debug behavior, remembered main-menu navigation and existing state-action controls are retained.

During play, **Review run** pins the Event and current audience; confirmation starts its single execution. Automatic and manual runs share the same completion gate. **Allow event again** deliberately archives the previous completed execution and rearms its timer. Default Event configurations may be removed; custom Events may be deleted. Neither operation removes delivery history or permits an unresolved run to be repeated.

## Execution and recovery

All changed Regions must be disjoint, in one dimension and already loaded. The combined limits are 262,144 cells, 64 unique chunks and 64 MiB of decoded before-plus-after evidence. Preflight checks every target, player occupancy, current pins and aggregate managed-block/chest/lore effects before any terrain write. An invalid later target leaves earlier targets unchanged.

One immutable batch plan and one Event execution own the run. Terrain work is bounded and checkpointed. All terrain must verify and save before aggregate metadata commits; messages and sound follow the completed outcome. Players entering a target, unloaded chunks, foreign blocks or policy/authority changes suspend or conflict the exact operation. Recovery preserves its UUID and before/after evidence and never pastes over an unrelated block to make a retry succeed. It does not force-load chunks. The journal retains up to 256 world-changing runs; at capacity, new batches are refused during preflight before execution authority or world changes. Evidence is never automatically pruned; stopped-world maintenance is required. Media-only runs do not consume that batch journal.

Original manual authority is checked through preparation and application. Another live operator can deliberately **Recover event**; a durable audit records that request while retaining the original actor in execution history. The supervisor must remain online and authorized. After the world outcome is committed, finalization no longer depends on the original GM or an online audience: only original recipients who are still eligible and online receive media. There is no offline catch-up.

Media delivery is claimed durably before network sends. Restart after a claim without completion becomes **uncertain delivery**, which is never replayed automatically. The GM may acknowledge it and subsequently allow a deliberate new run. Network receipt cannot be proven exactly once; uncertainty is shown rather than hidden behind an automatic retry.

## Ownership and upgrade

Core owns definitions, editor/protocol, clock/trigger dispatch, Region batches, strict persistence and recovery. Quiet Below continues supplying its default cues, flags and content. No new dependency or third-party license is introduced.

Source version is dev61 and the menu protocol is v8; server and client must match. PlayStore writes schema 2 and reads the prior schema 1, retaining clock, membership, bindings and receipts. Immutable completed executions live under `world/chronicle/event-history`; aggregate terrain evidence lives under `world/chronicle/event-batches`. Restoring the old release requires the verified stopped-world backup, not an old jar over newer persistence.

## Verification and review

Final build: 566 discovered Java tests, 564 passed and two Windows symlink privilege tests skipped; 93 Python tests pass. Independent source review found no remaining material issue. Python syntax, changed-document links and diff checks pass.

Native editor coverage passes 16 checks at each logical size 320x240, 640x360 and 480x480, including real Create/Cancel/save, shared sound/Region/flag/state selectors, incomplete-trigger private preview, duplicate removal, stale Editing and geometry. Representative captures were inspected.

Actual Minecraft execution proves paired terrain/NBT/chest/managed/lore effects, invalid later-target refusal without earlier writes, manual/automatic shared history, real abrupt termination after the first saved child and exact restart without duplicate media. Separate tests prove a foreign cell is preserved, a different operator recovers the same UUID, all 262144 cells apply across exactly 64 player-loaded chunks, and physical player ingress holds all 8192 later cells unchanged until exact supervisor recovery. Media-claim uncertainty and an absent original GM/zero-recipient outcome both complete safely. Existing menu, Area, departure and legacy cue initial/restart suites pass; the latter explicitly tests a stopped schema-1 legacy fixture returning to schema 2.

The authenticated review copy is `../reviews/events/20261028T102730212325Z`, with voice24469, OP4 retained, no forced chunks or test commands. The original concluded review world remains preserved; its verified backup is `../backups/server-20261028T102032128055Z.zip`. Only the separate copy has departure completion flags cleared and Creative login forced, reopening test participation. Its map/configuration and saved Region states are retained. The session is ended; enable Editing for setup and start play for real event runs. Stay outside replacement Regions when running them.

Final evidence is in `../reports/dev61-validation.json` and `../reports/combined-dev61-deployment.json`; exact native paths and commands are in `TEST_PLAN.md`. Human audibility, the final handcrafted map/content and realistic group/voice soak remain for organizer review. Arbitrary disk/power failure is not certified by the controlled kill/restart tests.
