# CF22: editable Events and Region Saved-state actions

28 October 2026. Implementation and native verification are in progress on `feat/core-event-region-actions`. This document is not a release receipt; the accepted dev60 installation remains unchanged until verification and a backed-up deployment complete.

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

One immutable batch plan and one Event execution own the run. Terrain work is bounded and checkpointed. All terrain must verify and save before aggregate metadata commits; messages and sound follow the completed outcome. Players entering a target, unloaded chunks, foreign blocks or policy/authority changes suspend or conflict the exact operation. Recovery preserves its UUID and before/after evidence and never pastes over an unrelated block to make a retry succeed. It does not force-load chunks.

Original manual authority is checked through preparation and application. Another live operator can deliberately **Recover event**; a durable audit records that request while retaining the original actor in execution history. The supervisor must remain online and authorized. After the world outcome is committed, finalization no longer depends on the original GM or an online audience: only original recipients who are still eligible and online receive media. There is no offline catch-up.

Media delivery is claimed durably before network sends. Restart after a claim without completion becomes **uncertain delivery**, which is never replayed automatically. The GM may acknowledge it and subsequently allow a deliberate new run. Network receipt cannot be proven exactly once; uncertainty is shown rather than hidden behind an automatic retry.

## Ownership and upgrade

Core owns definitions, editor/protocol, clock/trigger dispatch, Region batches, strict persistence and recovery. Quiet Below continues supplying its default cues, flags and content. No new dependency or third-party license is introduced.

Source version is dev61 and the menu protocol is v8; server and client must match. PlayStore writes schema 2 and reads the prior schema 1, retaining clock, membership, bindings and receipts. Immutable completed executions live under `world/chronicle/event-history`; aggregate terrain evidence lives under `world/chronicle/event-batches`. Restoring the old release requires the verified stopped-world backup, not an old jar over newer persistence.

## Verification status

The first resumed full build passed 565 Java tests with no failures/errors and 93 Python tests. Integrated review corrected two recovery authority cases. Initial native compact editor proof passes 14 checks. Expanded three-size editor coverage, aggregate action/interruption/conflict/maximum-volume checks and existing workflow regressions are being completed. See `TEST_PLAN.md` for final evidence before treating this work as delivered.
