# Quiet Below starting content

The dev67 source contains the organizer's `A Quite Below.docx`: six roles, nine named books, nine Event templates and four loot tables. The original Word file is unchanged. `scripts/prepare_quiet_below_defaults.py check` verifies the reviewed source hash and generated resources; regeneration is an explicit source operation, not an in-game import or server mutation.

## Installing and retaining map work

Use matching dev67 Core, Quiet Below and client pack. The editor protocol is v13 and a configuration-stage sound catalogue check rejects missing or mismatched client audio. The packaged clips play locally; no Freesound/SoundCloud connection is needed during play. See [audio sources and credits](QUIET_BELOW_AUDIO_SOURCES.md).

Existing initialized world/server configuration remains authoritative. Installing new defaults does not replace edited roles, books, loot or live Event records. An intentionally empty custom authoring profile stays empty while retaining the installed mechanics. Regions, Locations, chest bindings and role destinations stay with the map; reusable scenario content still uses the [export/build workflow](SCENARIO_CONTENT_BUILD.md).

## Roles and books

| Role | Player limit | Private starting book |
| --- | --- | --- |
| Hermit | 1 | A Tale of the Past |
| Farmer | 2 | — |
| Woodcutter | 2 | — |
| Carpenter | 1 | Weird Building Plans |
| Miner | 2 | — |
| Fisher | 2 | — |

Starting supplies match the document and are split into legal Minecraft inventory stacks. The unspecified raw fish uses cod. Set the starting/respawn Points for each role on the target map in **Player Roles**.

**Books & Lore** contains Research Journals I–VI and *Warning for Those Who Come After*, in addition to the two private books. The warning's full heading is inside the book; its item title is shortened to Minecraft's 32-character limit. Authors absent from the document use Unknown / Unknown researcher / Unknown worker; the village tale uses A village elder. All remain editable.

The seven discoverable books use campaign-wide **Until found**. The two private role books have automatic loot spawning Off and are issued by their roles. GM placement notes are in organizer descriptions, outside the journal pages. Bind each intended chest's direct lore-book entry, or add books to a loot table, using those notes when the handcrafted map is ready. No random journal distribution or coordinates are invented. A player taking the book, including receiving role supplies or a GM gift, records discovery.

Journal II uses **planks**, as requested. The claimed station interference remains the researcher's belief; the Altar and Forge function independently. Neither station drops a placeable item when destroyed in Survival, including Silk Touch. Their Creative/editor items remain available.

## Loot defaults

| Table | Suggested minimum chests | Contents |
| --- | --- | --- |
| Ancient City | 4 | Minecraft 1.21.10 Ancient City materials, equipment, plain books, discs and templates; no enchanted books |
| Crashed ship | 2 | Prismarine shards/crystals, gold nuggets/ingots, occasional wet sponge or gold block |
| Mineshaft | 4 | Cobblestone, spruce planks, raw iron, coal and torches |
| Previous explorer stash | 2 | Damaged iron hoes, amethyst shards, rotten flesh and iron nuggets |

Entries roll independently and remain editable under **Loot Tables**. Periodic refill is initially Off; session-start fill uses the existing chest policy. The Ancient City approximation derives each main-pool entry's chance of appearing at least once across vanilla's 5–10 rolls after removing both enchanted-book entries. It retains native quantity ranges where those are legal stacks, the regeneration potion and damaged diamond hoe; it omits random enchantment functions. Potions use a single legal item instead of vanilla's possible overstacked result. Trim-template chances use the separate native pool. This preserves recognizable loot while deliberately using Chronicle percentages rather than vanilla's coupled weighted rolls. Table capacity is now 32 combined item/book entries, allowing all 28 city entries.

## Event setup on the map

All nine Events are **disabled portable templates** under **Events → Create from scenario…**. Adopt and review them before play. Saving an adopted Event enables its reviewed configuration; merely loading the mod does not run it. Add actual sinkhole Region End-state actions and choose the city entry/audience Region in the editor.

| Template | Initial trigger |
| --- | --- |
| Sinkhole Opening | 30 occupied active minutes after enabling the timer; optional GM manual start |
| Sculk Awakening | 15 occupied active minutes after Sinkhole Opening completes |
| Whisper from Below | Manual GM cue |
| City Discovery | First entry into the chosen city Region; city audience |
| Altar or Forge Connected to Sculk | First powered station; all participants |
| First Altar Offering | Each participant's first successful prayer; only that participant |
| Sculk Growth stages | Each additional 5,000 living connected network cells above the initial count |
| Heart Destroyed | Durable destruction of the bound Relic Heart |
| First Warden spawned | First actual fresh Warden spawn; vanilla emergence sound |

Adopt Sinkhole Opening before Sculk Awakening so its completion target exists. The timers share occupied active play; pauses, empty-server time and downtime do not advance them, and session boundaries do not reset the story. Growth cues retain a high-water history: cutting and regrowing already-counted sculk does not repeat old milestones. Station readiness and growth receipts travel with world progress. Loading a saved entity is not a fresh spawn.

**How often** supports Once per campaign, Once per player and Each matching activity. The two repeat policies currently support source-triggered immediate text/sound only, with the existing flag prerequisites. Terrain, encounter and flag-changing Events remain once per campaign. Triggering-player audience requires a player-capable provider; ecology and entity-spawn sources have no player actor. Event-completion triggers are once per campaign to prevent repeating completion loops.

Per-player cue history belongs to the stable Event ID, so editing its words does not replay it for previous recipients. Recurring execution claims pin the source, configuration and recipients before media; exact completed runs archive before their live slot is reused. Uncertain media holds activity until a GM acknowledges that exact Event, then retires without replay. A full queue holds new activity while existing eligible cues drain; **Flags** permits restoring its prerequisites under that capacity-only hold. Player-history capacity can be reclaimed in non-active **Game setup → Event history** after queued work finishes.

## Automatic Event-history maintenance

`config/chronicle/event-history-maintenance.json` defaults to:

```json
{"schemaVersion":1,"enabled":true,"quietSeconds":60,"intervalSeconds":300}
```

Archival waits for consecutive non-active play with **zero online players** and no unresolved operations. It uses the same guarded archive path as manual maintenance. Progress, first-player identities and exact retry evidence remain in immutable world archives; no chunks are loaded for maintenance. A malformed policy is preserved and disables automation with a log message. Repair it and select **Reload automatic policy**. A storage/audit failure also stops automation and reports whether archival had already completed. Manual maintenance remains available when its normal guards permit it.

The handcrafted map, map-specific book bindings, sinkhole states, audible clip volume/feel and multiplayer balance still need organizer review. Automated decoding cannot certify how the recordings sound to players.

## Current review

Install `../client/Chronicle-0.1.0-dev.67-1.21.10.mrpack` and join **127.0.0.1:25591** for the fresh defaults world. It is paused, with an Altar, Forge and supplies near spawn. Review the catalogues under **Player Roles**, **Books & Lore**, **Loot Tables**, and **Events → Create from scenario…**. The nine templates remain optional; choose their map bindings and review them before enabling play. In Events, use **Preview privately** to judge the new audio without firing world outcomes.

The previous review remains at **127.0.0.1:25589** and the separate custom authoring profile at **127.0.0.1:25590**. Installing defaults preserves their existing content, so they are not substitutes for the fresh defaults review. All three addresses are included in the pack.
