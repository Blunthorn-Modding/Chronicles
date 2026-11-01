# Event encounter groups

Chronicle encounter groups are reusable, portable mob compositions. A group stores only an ID, organizer-facing text, and bounded entity type/count pairs. Its Point or Region target remains a logically separate world-owned binding, so scenario content never embeds map coordinates or revisions. Core publishes both sections together in one atomically replaced world catalog; an edit cannot expose a new composition with an old target (or the reverse).

Use **Chronicle → Advanced → Encounter groups** while play is paused and Editing is enabled. The editor has Basics, Members, and Spawn sections. Members come from Chronicle's finite normal-mob catalog; players, bosses (including the dragon, wither, and warden), projectiles, items, marker entities, commands, and custom NBT are not supported. A group permits at most 16 types, 32 entities of one type, and 64 entities total.

In an Event's **What happens** section, choose **Spawn encounter group**. An action can use the group's exact world binding or an exact Point/Region override. An Event permits four encounter actions and 128 planned entities total. A private Event preview sends only text and sound; it never spawns entities.

## Runtime and recovery guarantees

- The Event rechecks active play, flags, participants, reviewed configuration, group definition, and exact map revisions before and during spawning.
- Region saved-state work completes before encounter spawning. Flag outcomes and player media happen afterward.
- Core rejects deterministic geometry errors (overlapping candidates, build-limit overflow, or world-border overflow) while saving/reviewing the Event, before an execution or hold exists. Before the first claim, Core then preflights the entire bounded Event plan: every chunk touched by every entity-sized box must already be loaded and its current world space must be clear. The same checks repeat immediately before each spawn. A later invalid candidate therefore cannot leave earlier entities spawned. Chronicle does not generate or force-load chunks for an encounter.
- Every child operation and entity UUID is predetermined from the Event operation. Chronicle durably records `CLAIMED` before asking Minecraft to spawn it, saves the bounded successful batch, then records `SPAWNED`.
- After interruption, an exact claimed UUID/type that still exists may be confirmed. A missing claimed entity, a conflicting UUID/type, or another ambiguous external result becomes uncertain; Chronicle never replans or automatically respawns it.
- For an uncertain child, an operator opens that parent Event, chooses **Review uncertain encounter result**, inspects the exact child UUID, plan hash, and every entity UUID/stage, then may confirm **Accept current encounter result**. Acceptance records a distinct reviewed terminal receipt, preserves every `PLANNED`/`CLAIMED`/`SPAWNED` stage, and performs no spawn, despawn, refund, or replan. Any unspawned members are permanently skipped; the parent still rechecks its normal session and prerequisites before later flag or media outcomes.
- Once the child operation is complete, mobs follow normal vanilla lifecycle. Later death or despawn does not invalidate completion and does not cause replay.
- **Game setup → Event history → Archive completed records** publishes immutable per-operation history before removing completed live records. Exact archived lookup prevents an old Event retry from spawning again while reclaiming live capacity.

The portable scenario format carries group definitions and symbolic `GROUP`, `POINT`, or `REGION` references. Importing a scenario Event opens the ordinary Event editor as a disabled local draft. Map references resolve only when the current world has the matching logical site; missing or changed targets must be selected again before saving.

## Files

World data lives under `world/chronicle/`:

- `encounter-authoring.json` — authoritative group definitions and logically separate exact Point/Region revision pins, published atomically;
- `encounter-authoring.initialized` — initialization evidence; a missing initialized catalog is not silently recreated;
- `encounter-operations.bin` — bounded live at-most-once journal;
- `encounter-operation-history/` — immutable completed-operation records.

Configuration and journals are strict and fail closed. Unknown fields, invalid UTF-8, external concurrent edits, corrupt archive identity/hash data, or unsupported schemas are preserved for repair rather than overwritten.

The former `encounter-groups.json` / `encounter-bindings.json` pair is read-only legacy bootstrap evidence. Import requires both files and valid cross-references. Once the canonical catalog exists, later legacy edits are ignored and deleted defaults stay deleted.

Spawn coordinates are authored feet anchors. Points use the chosen position; Regions supply deterministic spaced anchors, including a single Y layer. Airborne targets are supported, and subsequent movement follows vanilla entity physics.
