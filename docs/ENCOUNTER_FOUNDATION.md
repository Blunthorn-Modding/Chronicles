# Encounter foundation

The Core `encounters` package owns the persistence/domain foundation. The integrated server coordinator and native editors are described in [Event encounters](EVENT_ENCOUNTERS.md); those adapters own permitted Minecraft types, target resolution and Event execution. There is no automatic narrative schedule.

`EncounterGroup` is a portable ordered composition: a namespaced ID, UTF-8 bounded label and description, and one to sixteen distinct entity types with one to thirty-two entities each (sixty-four total). Runtime code must provide `EncounterTypeAllowlist` from the server's actual permitted MobEntity registry; player, boss, projectile, drop, marker, root-runtime and other unsafe types belong in that adapter's rejection policy.

`EncounterBinding` deliberately contains only a group ID and an `AuthoringSite` logical ID/revision for a POINT or REGION. Map geometry and coordinate resolution stay in the existing authoring/map layer.

The world-owned `encounter-authoring.json` stores group and binding sections together so each editor save has one atomic publication. Its initialized marker prevents silently recreating a deleted catalog; guarded legacy bootstrap requires both old files and valid cross-references. Once created, the catalog is authoritative: deleting a packaged default remains effective after restart. Portable scenario export includes compositions and omits this binding section.

`EncounterOperationStore` persists a fixed plan before use. Each planned entity has a predetermined UUID, type and finite destination. Runtime must persist `claim` before making any external spawn call, then persist `markSpawned` only after confirmation. A claimed operation interrupted by a crash must be reconciled by exact UUID or held for GM review: it must never be replanned or automatically retried. The live journal has a hard 128-record capacity. Completed operations may be explicitly archived through `ImmutableHistoryArchive` before removal; if capacity is full, new operations fail closed.
