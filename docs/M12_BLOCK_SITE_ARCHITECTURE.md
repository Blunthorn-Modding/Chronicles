# M12 automatic block sites: backend architecture

Status: implemented for dev.46; native integration verification is recorded in `M12_MANAGED_BLOCK_IMPLEMENTATION.md`. The lead approved this architecture before code, following the dedicated UI design in `M12_AUTOMATIC_BLOCK_SITES.md`.

## Outcome

Blocks supplied by a Chronicle campaign register themselves when placed. The server enforces a configurable count per managed block type, including while known instances are in unloaded chunks. The Ancient Altar and Ancient Forge default to one instance each. The Relic Heart remains a single structural root and keeps its historical identity after it is broken.

The ordinary map-authoring model remains available for regions, points, and vanilla or third-party block references. A supplied block does not need a second manual anchor merely to make its own behavior work.

## Non-goals

- Do not scan or force-load the whole world.
- Do not delete blocks to enforce a lowered limit or repair an over-limit migration.
- Do not intercept every possible `setBlockState` caller or add a global block-placement mixin.
- Do not make Core aware of Quiet Below identifiers or campaign rules.
- Do not permit a second Relic Heart root, retcon an existing root, or discard its historical authoring pin.
- Do not remove legacy authoring commands or stored anchors. They remain compatibility and advanced-authoring APIs.
- Do not make Altar or Forge functionality depend on an authoring anchor. Their block and block entity implementations remain intrinsic.

## Existing constraints

The current persistence layers have separate responsibilities:

- `AuthoringStore` persists stable, revisioned regions, points, and block anchors in `chronicle/authoring.json`.
- `SiteAssignmentStore` persists requirement-to-site pins in `chronicle/map-assignments.json`. Assignments include the exact site revision, so replacing an anchor makes the assignment stale by design.
- Quiet Below's ecology setup pins the source-city and Relic Heart anchor identifiers and revisions. Terminal Heart cleanup still needs that original root identity after the physical Heart has gone.

Consequently, automatic physical-instance accounting must not reuse `AuthoringStore`. An authoring anchor is a historical semantic reference; a managed instance is a physical block that consumes a placement quota. Removing a station releases its quota, while removing the Relic Heart must not delete or revise the historical root anchor.

All mutation is serialized on the server thread. The existing strict JSON, maximum-size, fingerprint, atomic-replace, and backup conventions remain the persistence baseline.

### Verified Minecraft placement seam

In the repository's mapped Minecraft 1.21.10 classes, `BlockItem` exposes:

```java
public ActionResult place(ItemPlacementContext context)
protected boolean place(ItemPlacementContext context, BlockState state)
```

The public method computes the final placement context and state, then calls the protected method immediately before the world write. Item decrement happens later. A Core `ManagedBlockItem` can therefore reserve the exact dimension and position durably in the protected method before calling `super.place(context, state)`.

If the world write succeeds but later managed metadata confirmation fails, the protected method must still return success. Returning failure or throwing at that point could leave a placed block while preventing the item decrement. The durable reservation remains quota-bearing and reconciliation completes it later.

`Block.onStateReplaced` does not provide the replacement state. Supplied blocks may use it to enqueue verification, but Core must verify the loaded position before releasing a quota.

## Invariants

1. A supported item placement never changes the world or consumes the item unless Core first saves a quota-bearing reservation.
2. Every known instance, including a reservation and an instance in an unloaded chunk, counts toward the limit.
3. Lowering a limit preserves every existing instance. It only blocks further item placements until the count falls below the limit.
4. Discovery is conservative: an existing world block is recorded even when doing so makes the type over limit. Discovery never removes the block.
5. Dimension plus block position is the physical deduplication key. One position cannot create duplicate quota entries through callbacks, migration, and scanning.
6. A loaded, verified removal releases the active quota for every managed type, including the Relic Heart. Uncertainty retains quota until loaded reconciliation proves the block absent or changed.
7. Once ecology setup exists, the Relic Heart's canonical anchor identifier, revision, position, and ecology pin never change as a side effect of removal or repair. Before setup, normal map authoring may move the Heart and update its still-unbound anchor and assignment.
8. Once the ecology is bound, only an exact-position repair of a missing Heart can succeed. Once the campaign has ended, every Heart placement is denied, including repair. These rules use historical root authority, not an absent active-ledger entry.
9. Reconciliation reads loaded chunks only, has explicit per-tick work limits, and never modifies the world.
10. A failure in the managed-block subsystem blocks new managed placements and policy edits; it does not stop intrinsic behavior of already placed stations or hold general gameplay.

## Core model

### Managed block catalog

Add a generic catalog in Core, registered during mod initialization and frozen with the existing authoring catalogs at server start.

```java
record ManagedBlockDefinition(
    String typeId,
    String blockId,
    String label,
    String description,
    int defaultLimit,
    LimitMode limitMode
) {}

enum LimitMode { EDITABLE, FIXED }
```

Suggested bounds are 256 definitions, a configurable limit from 0 through 1,024, and 8,192 known instances. The exact constants should follow the repository's existing bounded-decoder conventions.

The catalog resolves `blockId` against the block registry while freezing and rejects duplicate type IDs, duplicate block registrations, invalid defaults, or missing blocks. Core contains no Quiet Below identifiers. Quiet Below registers:

| Managed type | Block | Default | Limit mode |
|---|---|---:|---|
| Ancient Altar | `quiet_below:ancient_altar` | 1 | editable |
| Ancient Forge | `quiet_below:ancient_forge` | 1 | editable |
| Relic Heart | `quiet_below:relic_heart` | 1 | fixed |

### Durable active-instance ledger

Use a new strict atomic world file, `chronicle/managed-blocks.json`. It contains physical active instances only and is independent from authoring sites, assignments, and server policy.

Proposed schema:

```json
{
  "schemaVersion": 1,
  "revision": 7,
  "migrationVersion": 1,
  "instances": [
    {
      "id": "uuid",
      "type": "quiet_below:ancient_altar",
      "block": "quiet_below:ancient_altar",
      "dimension": "minecraft:overworld",
      "position": {"x": 10, "y": 64, "z": 20},
      "registration": "CONFIRMED",
      "origin": "ITEM",
      "revision": 2
    }
  ]
}
```

`registration` has only durable transaction meaning: `RESERVED` or `CONFIRMED`. `ITEM`, `LEGACY`, and `DISCOVERED` origins are diagnostic facts. Present, Unloaded, Missing, and Changed are runtime views derived from the ledger plus a loaded-only world lookup; they are not trusted as durable truth.

The stored block registry ID detects catalog drift. A changed catalog mapping requires an explicit migration and fails closed for new placement instead of silently reinterpreting an existing record. Records for definitions unavailable after a mod change remain opaque and quota-bearing until an intentional migration; the decoder must not discard them.

Every loaded-proven removed instance is removed from the active ledger by an atomic revisioned save, including a removed Heart. The Heart's anchor, assignment, and ecology setup remain in their existing stores and do not consume active quota. The global revision and per-entry revision support stale-screen rejection and idempotent reconciliation. The file is created even for an empty ledger so migration is not repeated on every start.

### Server limit policy

Store operator-configurable limits separately in strict atomic `config/chronicle/managed-block-limits.json`:

```json
{
  "schemaVersion": 1,
  "revision": 3,
  "overrides": {
    "quiet_below:ancient_altar": 2,
    "quiet_below:ancient_forge": 1
  }
}
```

An absent override uses the frozen catalog default. Fixed definitions reject overrides. The file uses the same bounded decoding, fingerprint conflict, backup, and atomic-replace rules as other server configuration. Keeping this policy outside the world ledger lets operators review or deploy server policy without editing transaction records. Limit writes remain server-thread serialized and revision checked.

### Service and item APIs

Names are proposed; implementation may adjust packages without changing the responsibilities.

```java
final class ManagedBlockItem extends BlockItem {
    ManagedBlockItem(Block block, Settings settings, String managedTypeId);
}

interface ManagedBlockService {
    Reservation reserve(PlacementRequest request) throws ManagedBlockException;
    void confirm(Reservation reservation);
    void cancelOrVerify(Reservation reservation);
    void observe(Observation observation);
    void scheduleVerification(RegistryKey<World> dimension, BlockPos position);
    ManagedBlockSnapshot snapshot(MinecraftServer server);
    ManagedBlockSnapshot setLimit(String typeId, int value, long expectedPolicyRevision);
}
```

The item embeds a registered type ID; clients cannot supply an arbitrary ID. On the logical client it follows normal prediction. On the server it reserves before `super.place`. A missing/unavailable service or a rejected reservation returns failure before the world write, sends a concise server-owned reason to the player, and leaves the stack unchanged.

Core also exposes loaded-world lifecycle observations for supplied blocks:

```java
ManagedBlocks.observePlaced(world, position, typeId, cause);
ManagedBlocks.verifyAfterReplacement(world, position, typeId);
```

Ancient Altar, Ancient Forge, and Relic Heart call these from their block lifecycle hooks and always call their superclass implementations. Replacement callbacks enqueue verification rather than directly releasing records. This catches ordinary breaks, explosions, and most command placements without making those hooks the source of truth.

### Campaign lifecycle policy

Core permits a campaign to register a policy with a managed type. The policy is generic and keyed by type ID; Core does not branch on campaign IDs.

```java
interface ManagedPlacementPolicy {
    PlacementDecision preflight(PlacementRequest request, ManagedBlockSnapshot snapshot);
    void afterReservation(ReservedPlacement placement) throws Exception;
}
```

`preflight` is read-only and runs before the durable reservation. `afterReservation` runs after the reservation save and before the world write. Its failure rejects the world placement and leaves the reservation until loaded reconciliation proves whether a physical block exists. Policy callbacks run on the server thread, may not load chunks, and receive bounded snapshots rather than mutable repositories. Loaded-proven absence always releases the active record; historical campaign authority remains separate.

For a managed block that needs an authoring compatibility pin, the policy returns a declarative compatibility-anchor request. Core fulfills it through narrow internal `AuthoringService` and `SiteAssignmentService` operations; it must not fabricate a player or bypass public command permissions. Creation is exact and idempotent:

- reuse an existing matching assignment and anchor without changing its revision when ecology is already bound;
- before ecology setup, move a valid canonical anchor after the previous physical Heart is proven absent, preserving its current alias ID where possible, then update the assignment to the new anchor revision;
- create the preferred anchor and assignment only when neither conflicts;
- reject mismatched or stale legacy assignments instead of silently repairing them;
- protect the compatibility anchor from replacement or deletion only after ecology setup binds it or terminal history depends on it.

This facility is generic. Quiet Below supplies its Heart requirement and preferred site ID through registration; Altar and Forge do not request anchors.

## Placement transaction

For a supported item placement:

1. Vanilla validates replaceability, collision, border, and the final placement state.
2. Heart or campaign policy performs its read-only preflight.
3. Core checks the effective limit against all active records, including `RESERVED` and unloaded records.
4. Core atomically saves a `RESERVED` record for the exact dimension and position.
5. The placement policy performs its idempotent post-reservation preparation. For the Heart, this creates, moves, or reuses the compatibility pin according to ecology state.
6. `ManagedBlockItem` calls the vanilla protected placement method.
7. If the world write returns false, Core attempts to release the reservation and always queues loaded verification. A failed release remains safely quota-bearing.
8. If the world write succeeds, Core marks the record `CONFIRMED`. A failure to save confirmation leaves the valid `RESERVED` record and queues reconciliation; placement still returns success so vanilla consumes the item normally.

The position key makes `onBlockAdded` observation during step 6 idempotent with the reservation.

### Crash windows

| Last durable action | Recovery |
|---|---|
| Nothing saved | No world change occurred; retry is normal. |
| Reservation saved, no policy pin | Loaded mismatch releases the active record. |
| Heart pin saved, no world block | Loaded mismatch releases the active record; the separate anchor and assignment survive. Before setup they may move through a later valid map-authoring placement. After setup they permit repair only at the pinned position. |
| World block written, record still Reserved | Loaded reconciliation confirms the same block; quota was already protected. |
| Removal happened, release save failed | Keep counting the record until a later loaded verification and atomic save succeeds. |

This deliberately favors a temporary denied placement over duplicate blocks. Recovery does not roll back world blocks and never treats an active-instance UUID as Heart authority.

## Reconciliation and discovery

Use three evidence sources:

1. Managed block lifecycle callbacks provide immediate hints.
2. A bounded round-robin verifier checks known records whose chunks are already loaded.
3. `ServerChunkEvents.CHUNK_LOAD` enqueues loaded chunks for bounded discovery of preexisting unregistered managed blocks.

Chunk-load callbacks must not scan an entire chunk inline. The server tick worker keeps a dimension/chunk/section/cell cursor, skips palettes that contain no registered managed blocks, and enforces explicit maximum chunks, sections, and cells per tick. Before each read it confirms the chunk is still loaded. It never calls a loading chunk accessor. Discovery records exact positions but never changes blocks.

Known unloaded records keep counting and show `Unloaded`. An unloaded chunk is not evidence that a block is missing.

Direct `/setblock`, WorldEdit, structure placement, and tools that bypass `ManagedBlockItem` cannot be rejected before changing the world without invasive global hooks. The supported behavior is therefore:

- lifecycle callbacks adopt the block immediately when the tool honors normal block callbacks;
- otherwise loaded-chunk discovery adopts it later;
- adoption preserves every discovered block, even above the configured limit;
- the type is shown as over limit and future item placements are denied;
- a block created and removed between observations is unknowable and creates no record.

Integrations that require pre-placement enforcement should call the Core reservation API instead of raw world writes.

## Relic Heart rules

The Heart placement policy is structural and is evaluated in addition to the fixed physical count of one. Root authority is the canonical authoring anchor and assignment, followed by the immutable pin copied into ecology setup. The active ledger only answers whether a physical Heart currently consumes quota.

### Before ecology binding

The first supported Heart item placement may reserve a position and establish the compatibility anchor and requirement assignment. While `ecology.setup()` is absent, a map author may break that Heart and place it elsewhere after loaded reconciliation proves the old physical block absent. The trusted placement transaction updates the canonical anchor to the new position, increments its revision, and repins the assignment to that revision. It preserves the current anchor/alias ID where possible.

This movement is allowed only from a valid, current assignment. A stale assignment, conflicting aliases, mismatched expected block, or uncertain unloaded candidate fails closed. The automatic path does not silently repair legacy binding conflicts. Before ecology setup, the anchor remains ordinary editable map metadata; automatic creation alone does not make it permanently protected.

### After ecology binding, before ending

- A present canonical Heart rejects every additional placement.
- If the canonical block is missing or changed, placement is allowed only at the exact pinned position.
- Exact repair creates a new physical active-instance UUID but reuses the original authoring anchor, assignment, and setup pin without changing their IDs or revisions. It does not rebind the ecology or create a new root.
- A different position is rejected even if the physical Heart is gone.

### After ending

Every Heart placement is rejected, including exact-position repair. Loaded-proven destruction removes the physical entry from the active ledger. The compatibility anchor, map assignment, and ecology setup pin remain as historical state required by cleanup and auditing.

### Discovery and migration

An exact valid legacy Heart assignment and anchor take precedence and are adopted without revision changes. A direct command placement cannot move or replace an existing bound root. Core records the physical block when it observes it, possibly over limit, but never changes the ecology pin; numeric capacity alone never makes the command-created block the root.

Startup backfill must not silently choose one of several historical unregistered Hearts as a new root. Conflicting historical aliases remain fail closed until their chunks naturally load and physical presence can be verified. If no canonical legacy pin exists, only a live supported item transaction may establish one. A command integration that needs to establish the first root must use the reservation API. This keeps scan order from becoming campaign state.

## Migration

Migration runs after the managed catalog, authoring runtime, and map assignments are loaded, and before managed placement is enabled.

1. Create/load the managed ledger and its migration marker, then load the separate limit-policy file.
2. Resolve every saved block anchor whose `expectedBlock` matches a managed definition, whether assigned or unassigned.
3. Add a provisional active record for each distinct dimension and position without loading its chunk. Multiple aliases at one position produce one physical record. The durable anchors remain separate metadata.
4. Count provisional unloaded records conservatively until their chunks naturally load. Loaded absence or a changed block releases the active record but does not delete the anchor.
5. Adopt loaded physical blocks through the bounded discovery queue, deduplicating by dimension and position.
6. Preserve every discovered or imported physical instance, including instances above the effective limit.
7. Leave vanilla/third-party block references in the manual authoring model. Conflicting managed aliases are reported and remain fail closed until natural-load verification supplies enough physical evidence; migration never guesses from alias order.

Legacy Altar and Forge anchors may remain stored. Their physical instance appears once in the flat UI; the manual metadata is merged into that row. Removing a legacy manual reference does not release the managed instance quota. A Heart compatibility anchor becomes protected from manual replacement or deletion when ecology setup or terminal history depends on it; before binding it remains normal map-authoring metadata.

Preexisting unregistered blocks in unloaded chunks that have no matching saved anchor are inherently unknowable without a full-world scan. Until their chunks naturally load, they cannot count. This is the only migration gap. Anchored unloaded candidates count provisionally. From the first managed release onward, every supported new item placement is durably tracked before the world changes, so unloads and restarts cannot evade its quota.

## UI boundary

The backend supplies a revisioned read model; it does not duplicate Game Setup tools.

```java
record ManagedBlockTypeView(
    String typeId, String label, String description,
    int count, int limit, boolean fixed, boolean overLimit
) {}

record ManagedBlockInstanceView(
    UUID id, String typeId, String label,
    String dimension, BlockPos position,
    String expectedBlock, String actualBlock,
    InstanceStatus status, long revision
) {}

record ManagedBlockSnapshot(
    long instanceRevision,
    long policyRevision,
    List<ManagedBlockTypeView> types,
    List<ManagedBlockInstanceView> instances
) {}
```

`ManagedBlockSnapshot` contains both `instanceRevision` and `policyRevision`, plus definitions, type totals, and instance rows. This is the stable facade for the approved flat Locations list and generic paginated Block limits picker. The screen sends both revisions in its frame; a limit change validates `expectedPolicyRevision`, runs on the server thread, and returns a fresh snapshot. Limit changes use the typed integer prompt already used by the authoring UI, require operator plus non-active author mode, and accept lower values without deleting blocks.

`Show` uses an ephemeral position preview and does not create an authoring anchor or load an unloaded chunk. Managed rows have no manual Bind, Replace, or Delete actions. Manual anchors remain separate unless merged at the same dimension and position. Structural dependency checks disable deletion of the Heart's compatibility anchor through legacy commands only after ecology has bound it.

Regions stay under Regions. Point roles and their assignments move under Points. Existing BLOCK-role command and data compatibility remains, but supplied station controls and Heart controls are removed from Game Setup.

## Failure behavior

- Corrupt, oversized, or conflicting active-ledger data disables new managed placement with a clear operator diagnostic. Corrupt or conflicting limit-policy data separately disables managed placement and limit writes because Core cannot establish the effective cap. Neither failure deletes world blocks, stops station block entities, or creates a general play hold.
- A catalog/ledger block-ID mismatch fails closed for that type.
- Store failure before reservation means no world change and no item consumption.
- Store or policy uncertainty after reservation retains quota and relies on reconciliation.
- Observation/scanning failures keep records and retry within the bounded worker; they never infer absence from an unreadable or unloaded chunk.
- An over-limit world remains playable. Only new item placements of that type are denied.
- A failed Heart compatibility write cannot create or move the bound root. A partial pre-setup authoring update remains visible for operator recovery; the automatic path never guesses through a stale assignment.

## Implementation slices

Implement sequentially so each persistence boundary is reviewable.

1. **Core catalog and stores**: pure String-based catalog, strict active-ledger and limit-policy schemas, atomic saves, bounds, independent revisions, snapshots, and opaque unknown-definition preservation.
2. **Core service state machine**: reserve, confirm, loaded verification, default release policy, conflict handling, and bounded work queues.
3. **ManagedBlockItem and lifecycle hooks**: pre-world reservation, successful-placement semantics, callback deduplication, and replacement verification.
4. **Loaded-only discovery and migration**: chunk cursor, periodic known-record verification, legacy anchor import, over-limit preservation, and migration marker.
5. **Quiet Below registration**: replace the three ordinary `BlockItem` registrations with `ManagedBlockItem`; register editable station definitions and fixed Heart definition.
6. **Heart compatibility policy**: movable pre-setup anchor/assignment preparation, immutable bound/ended guards, exact repair, physical-record release, and legacy pin adoption.
7. **UI read model and actions**: flat rows, Show, type picker, typed limit prompt, stale revision handling, and removal of duplicate Game Setup entries.
8. **Compatibility cleanup**: retain commands and data readers, hide redundant supplied-block binding paths, and document supported WorldEdit behavior.

Slices 1 through 4 belong to Core. Slice 5 and the campaign policy belong to Quiet Below. The UI consumes only Core's generic definition and snapshot APIs.

## Verification

### Pure and store tests

- strict schemas for both files, size/count/string limits, duplicate positions, invalid identifiers, corrupt data, backup recovery, fingerprint conflict, and atomic-save failure;
- default, overridden, fixed, zero, lowered, and over-limit policies;
- reservations count immediately and survive reload;
- confirmation is idempotent by position and reservation ID;
- failed confirmation stays quota-bearing;
- loaded-proven removal of every type releases, while unknown/unloaded state does not;
- chunk discovery deduplicates callbacks/migration and preserves over-limit blocks;
- work budgets stop a scan exactly at configured chunk/section/cell limits.

### Heart policy tests

- first supported placement establishes one stable compatibility anchor and assignment;
- legacy anchor ID and revision are adopted unchanged;
- before setup, breaking and replacing elsewhere updates the valid canonical anchor and assignment revision while preserving its alias ID;
- stale or conflicting pre-setup aliases fail closed until loaded verification; they are not silently repinned;
- a present root rejects another position;
- a missing bound root has no active-ledger entry and allows exact repair with a new physical UUID but no anchor, assignment, or setup-pin changes;
- a different-position repair is denied;
- terminal state denies exact repair, releases the physical record, and preserves the historical pin;
- crash after reservation but before pin safely releases when absence is proven;
- crash after pin but before world write releases the physical reservation after loaded verification while retaining the separate root metadata;
- direct discovery never changes an existing root and cannot choose among legacy candidates.

### Integration and native acceptance

- In Survival, place an Altar from a stack of two: the first block appears and one item is consumed; a second placement is rejected before world change and item consumption.
- Restart with the first block's chunk unloaded: the second placement remains denied.
- Break the Altar, observe durable release, then place it elsewhere.
- Lower a station limit below the present count: every block remains, the UI shows over limit, and later placement is denied.
- Create extra stations with `/setblock` or WorldEdit: they remain in the world, are discovered without force-loading, and raise the count above the limit.
- Altar and Forge function without authoring anchors.
- A legacy map retains anchor IDs, revisions, assignments, and behavior after migration.
- Break a pre-ending Heart and place it back at the exact location: repair succeeds without rebind. Any other position fails. After ending, both attempts fail.
- Before starting ecology, break the Heart and place it elsewhere: the alias ID is preserved, anchor and assignment revisions advance together, and no obsolete physical entry consumes quota.
- The Locations screen contains one merged row per physical position, Block limits is generic and paginated, and Game Setup contains no duplicate Areas or supplied-block controls.

## Risks and rollback

- Heart preparation spans the active ledger, authoring store, and assignment store, so it cannot be one filesystem transaction. Explicit reserved/pinned/placed progression and idempotent recovery are required; the active ledger must never become campaign-root authority.
- Tools that bypass item placement cannot receive pre-world quota enforcement. Post-world adoption is deliberate and must be stated in operator documentation.
- A full chunk scan can create tick spikes even without force loading. The cursor and cell budget are mandatory, not an optimization.
- Removing a type from the catalog must not silently erase its records. A future data migration may archive them only with explicit operator intent.

Rollback is code-only: older builds ignore the new ledger and limit-policy file, while existing authoring and assignment files remain compatible. Before reverting after managed placements have occurred, preserve `managed-blocks.json` and `config/chronicle/managed-block-limits.json`; returning to the new build restores quota and policy knowledge. Do not delete compatibility anchors as part of rollback.
