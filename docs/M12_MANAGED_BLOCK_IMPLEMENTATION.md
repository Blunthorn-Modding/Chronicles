# Automatic block placement and flat configuration (dev.46)

Core owns generic managed-block definitions, placement quotas, physical accounting and menu controls. Quiet Below registers the Ancient Altar and Forge with editable limits of one each, and the Relic Heart with a fixed structural limit of one. The dedicated UI design and backend architecture were reviewed before implementation; see [design](M12_AUTOMATIC_BLOCK_SITES.md) and [architecture](M12_BLOCK_SITE_ARCHITECTURE.md).

## Placement and persistence

Ordinary item placement durably reserves the final dimension/position before vanilla writes the block or consumes an item. A rejected extra placement consumes nothing. A successful world write remains successful even if later metadata confirmation fails; its reservation continues to count until loaded verification settles it. A thrown/ambiguous write also retains its claim. Verified physical removal releases the slot. Unloaded known instances count throughout unload and restart.

The world ledger is `world/chronicle/managed-blocks.json`; server policy is `config/chronicle/managed-block-limits.json`. Both use strict bounded JSON, external-change fingerprints, backups and atomic replacement. Stop the server before editing JSON. The policy format is:

```json
{"schemaVersion":1,"revision":0,"overrides":{"quiet_below:ancient_altar":2,"quiet_below:ancient_forge":1}}
```

Limits range from 0 to 1024. Omitted editable types use their registered defaults. The Heart has no editable override; a fixed-type override is rejected without rewriting the file. Lowering a cap never deletes existing blocks. Unknown type records and overrides remain opaque; missing dependencies do not erase their history.

Legacy saved anchors import once through a resumable migration marker. Migration preserves their IDs, revisions and assignments, counts unloaded candidates conservatively, and deduplicates by physical position. Completed migration never resurrects a historical reference as an active quota after verified removal. Loaded chunk discovery and verification adopt command/WorldEdit-created blocks, including over-limit ones, without deleting them. No chunks are forced or loaded for this work. An old, unregistered block in an unknown unloaded chunk cannot be discovered until that chunk loads naturally. Command/WorldEdit writes bypass item preflight and are accounted after the write.

## Heart history

Before ecology setup, removing the physical Heart permits mapmaker relocation while retaining its anchor ID and advancing its unbound revision. Once ecology setup exists, the canonical root pin is immutable: repair may only occur at its original dimension and position. After eradication, all Heart placements are denied even though physical quota has been released. Generic authoring and assignment mutation guards protect this history through both menu and legacy commands. Altar and Forge interactions remain intrinsic to their blocks and require no manual reference.

## Configuration

**Regions** is the sole saved-region workflow, with contextual Use for/Clear use. **Locations** retains Points and Block sites; Block sites is one flat physical list, with no Saved sites/Game bindings tabs. Supplied blocks register on placement. Bind looked-at block remains available for manual vanilla/third-party references. Several historical references at one managed position stay accessible through a contextual Manual references picker without duplicating the physical row.

**Block limits...** opens a paginated registered-type selector and the existing detail pane. Set limit is a confirmed integer edit. The Heart is read only. Game setup contains global tools only; region, point and block geometry are absent. Eligible game point purposes use the same contextual model on Points. Starting-position/respawn policy stays under Players.

## Verification and limits

Full build: 385 Java tests, zero failures/errors/skips; Python suite: 77 passed. Native placement31/55/4, menu114/8 plus both larger layouts, and exact saved-world Heart20 checks pass. Reports and deployment are recorded in the newest [test-plan entry](TEST_PLAN.md).

Independent source review covered stores, reservation ordering, migration, Root policy/guards, callbacks and menu routing. It caught and led to fixes for fixed-type override validation, failed-start snapshots, unavailable-type preservation and multiple manual aliases. Native testing additionally corrected rejected-placement client inventory prediction, compact policy routing, and unfinished prompt handling across unrelated player changes; actual target changes invalidate the draft. Execution is bounded per tick (32 queued verifications, 8192 scan cells plus four periodic record checks). Queue memory is not separately capped under adversarial chunk-event volume. Runtime IO-fault injection, a native bound-root exact-repair rehearsal and a multi-hour production-map/group soak are not claimed. Pure guard/policy/failure tests cover those seams; the native suite verifies ordinary placement, quota denial, unload/restarts, edits, discovery and terminal-root denial.
