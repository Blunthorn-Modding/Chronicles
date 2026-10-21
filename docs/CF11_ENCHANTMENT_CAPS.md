# CF-11 enchantment caps and shared enchanting

21 October 2026; implementation branch `feat/cf11-enchantment-caps`. Builds on QB-09 commit f0da079. The operator explicitly chooses permanent stored-level reduction, including existing gear/books, vanilla enchanting, anvils and loot. Raising a cap cannot recover a previously removed level.

## Contract

Core owns strict server configuration, native stored-level normalization, operator controls and reusable ingredient mappings. Quiet Below owns physical Forge power and campaign defaults. The library alone defaults to no restriction; Quiet Below recommends maximum I. A configured cap is an upper bound, never an enchantment grant or permission to exceed native maximum. Overrides use enchantment registry IDs. Disabled rules stop future reductions; they cannot undo earlier ones. Invalid configurations must remain untouched and hold gameplay rather than silently choosing permissive defaults.

First-file defaults are disabled/255 in Core alone; Quiet Below recommends enabled/I. Existing operator files are never overwritten by campaign recommendations. `config/chronicle/enchantment-caps.json` schema1 has enabled/defaultMaxLevel/overrides, up to1024 namespaced enchantment IDs and1..255 limits. The operator tool changes the global maximum, enables/disables and reloads per-ID JSON overrides, with irreversible-change confirmation.

The first implementation slice covers caps. Reusable Forge ingredient mappings/transactions follow separately; no station models, Altar stats, player food mechanics or Heart ending are implemented by this slice. No new dependency or client registry is required for caps.

## Native behavior and persistence

Normalize both ENCHANTMENTS and STORED_ENCHANTMENTS even on unusual items carrying both. Preserve count, durability, names and every unrelated component. Nested container/bundle/charged-projectile item components must be rebuilt, preserving slot order/selection/occupancy; their cached hashes make in-place child mutation unsafe. Bound depth and node work, reject unsupported over-limit ingress explicitly, and never silently truncate contents. No item entities or per-sculk entities are added.

Permanent reduction occurs on authoritative server access/load; offline players and unloaded chunks update when loaded, with no forced chunk loads or automatic offline-file rewrite. Player inventories/ender storage, loaded block/entity inventories, item entities, equipment, trades and persistent projectile item data need explicit coverage. Existing closed containers must mark changed state dirty; packets/screens must reflect actual stored values.

Table offers and anvil candidate levels must honor caps before charging/consumption. An anvil merge that produces no component/durability/name improvement must not consume inputs/levels. New vanilla loot, item commands, creative packets, crafting/smithing outputs and trades are ingress routes to prove. Native command permissions and the separate non-active operator configuration guard remain intact.

## Implementation order and risk gate

1. Pure immutable cap policy and strict atomic server config; corruption/concurrency/backup tests.
2. Pinned native normalizer and authoritative context proof; bounded nested reconstruction and stored-data tests. Investigate server-thread-only ItemStack access/mutation boundaries against explicit ingress/load hooks before accepting the final hook surface. Side-neutral client/async codec work must never acquire a server policy accidentally.
3. Server configuration controls, registry validation, precise failure/hold behavior and reload. A rule change carries an irreversible-item warning; back up before deploying to any existing world.
4. Disposable native tests: actual component values through give/loot/table/anvil/creative/crafting/trades/nesting; player/container/equipment/projectile load and save/restart; cap increase proves levels stay lowered. Verify unaffected clients and absence of client-side mutation. Full build/tests/diff and rendered operator controls.
5. Save-stop/verified backup before any review-world upgrade. Do not mix unverified dev.28 artifacts into the current dev.27 review pack/server. Record limitations, exact evidence and failed attempts in TEST_PLAN.

No universal enforcement or completed station behavior is claimed until the relevant native routes pass. The current review remains dev.27 on localhost25588 with its map/tuning preserved.

## Implemented architecture

The Core service binds its actual MinecraftServer only on SERVER_STARTING's verified server thread and removes the binding on STOPPED. It never propagates to client/render/network/IO threads. ItemStack getters/patches and its exact canonical copy constructor normalize the actual owning MergedComponentMap; these are permanent writes, not a capped view. A cache compares policy and five immutable component value identities, so reuse/mutation of the map cannot hide new enchantments. The steady path does not allocate a new stamp. Recursive normalization builds complete replacements before changing the root; failure changes no contents. It reuses all unrelated components and copies nested children before rebuilding cached-hash container/bundle/projectile values.

The global per-enchantment native maximum is bounded only in that server context, affecting table offers, anvil candidates, native enchant commands and trade generation. This is separate from future Forge ingredient recipes. An anvil output with no improvement after ignoring its newly raised prior-work penalty is rejected at zero cost; rename, repair and added enchantments remain valid. Explicit loot final-consumer, creative ingress/resync, trade-output and projectile stack/weapon hooks close their native ingress/read paths. Client-local components and vanilla maximum values remain unchanged.

JOIN/ENTITY_LOAD handle existing player/end storage, item/frame/equipment/vehicle inventories and persistent projectile data. Loaded chunks are marked directly for server-thread stack-snapshot normalization; only256 BEs are considered for admission per loaded chunk. The queue retains at most8192 total remaining slots, processing64 slots per tick. Chunk/entity unload, disconnect and replacement after respawn discard their pending references. Overflow relies on native save/access normalization. Never call BlockEntity.markDirty from the load callback: it may synchronously wait on the very chunk whose FULL future is still being completed. Disabled policy queues/dirties nothing extra. The service does not load a missing chunk or edit offline player/region files. Arbitrary mod code using raw internal maps/components off the server thread is outside this native integration contract.

The pinned ItemStack counted/uncounted codecs use private component getter lambdas instead of public getters. Exact required hooks on both lambdas normalize the owning stack before NBT encoding. The canonical copy constructor is likewise pinned by its full descriptor; the one-argument constructor alone misses native crafting/copy routes. Slot reads normalize their actual inventory root before preview/network copies. Native tests separately inspect raw owning maps, serialized NBT, real world saving and same-world restart under a higher cap; a capped display is insufficient evidence.

Malformed startup configuration aborts startup rather than inventing a permissive policy. Invalid reload holds Chronicle play while retaining the previous valid in-memory policy; fixing/reloading releases only that service's hold. The exact old valid config becomes .bak on an atomic forced save; .bak cannot restore already-lowered item data. Full stopped-world backups are the rollback for item transformations.
