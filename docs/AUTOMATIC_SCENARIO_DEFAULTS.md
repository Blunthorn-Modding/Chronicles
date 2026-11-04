# Automatic scenario Events and flags

4 November 2026. The organizer approved dev67 content and audible sounds. This change keeps the deliberate storage split: reusable content/settings come from the scenario mod and server configuration; map bindings, configured Events, flags and live progress belong to the world. Books, loot and gameplay configuration are not being relocated.

## Required behavior

A fresh map shows the installed scenario's Events and declared flags automatically. Ready Events use the existing executor; incomplete map-dependent Events appear in the ordinary Events list with `!` and an actionable reason. Opening one uses the existing editor with its missing selectors. A prepared map plus its matching scenario mod uses its saved Event definitions/pins and flag values without another import or setup pass. Player/access setup remains separate.

An Off flag is normal progression, not an error. A flag receives `!` when a linked updater Event has incomplete/invalid setup. Manual-only flags remain valid. Row descriptions and the selected detail explain missing/stale Regions, saved states, sources or flags even with Debug Off. No extra screens or warning panels are introduced.

## Ownership and initialization

Core owns generic registration, idempotent initialization, setup diagnostics and UI projection. Scenario content schema4 adds declared initial flags; older schemas remain readable. Quiet Below declares four progression flags and once-only Event updates; its sinkhole End-state and city Region are explicit map dependencies. Source-driven recurrence and timing rules remain unchanged.

Play state schema4 atomically records seen default IDs, unresolved draft IDs and newly installed ready configurations. Existing definitions, deliveries, executions and flag values win. Seen IDs persist when a GM deletes an Event, so restart cannot resurrect it. Saving a draft promotes it to a live configuration in the same commit. Incomplete drafts have no partial terrain/flag/media execution.

Initialization runs on the server thread after every startup listener has completed, before normal gameplay ticks. It declares missing flags without resetting existing values, resolves current map pins and seeds only new/default-draft IDs. Later setup checks can promote never-configured drafts when their expected bindings become ready, only during non-active, unheld play and at most once per second. Existing configured Events are never silently rebound or rearmed. Holds and unavailable services remain blocking.

Exports preserve flag definitions with initial values reset to Off, live Event definitions stripped of map pins, and remaining default drafts. Deleted defaults stay deleted. Empty custom authoring profiles stay empty; installed mechanics do not inject Quiet Below's content into them.

## Acceptance evidence

Run focused pure tests for old/new codecs, batch flag preservation, default initialization/restart, draft promotion, deletion tombstones and failure atomicity. Native tests must verify fresh default list/flags/warnings, normal edit/save, missing Region correction, saved-world copy onto fresh server configuration, no completion replay, unchanged custom-profile suppression and export/import. Verify the two existing storage homes remain unchanged. Independent UI design and source review precede final delivery; existing worlds require a saving stop/backup before the updated JAR swap.
