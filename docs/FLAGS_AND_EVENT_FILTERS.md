# Flags and Event filters

The organizer requested a dedicated **Flags** entry under Advanced and two Events filters: **Game Events** and **Region Changes**. Existing Event media and Region execution have been accepted; this change leaves that dispatcher and its recovery rules intact.

## Approved design

- Flags uses the existing list/details layout. **Create flag** sits above the list and opens a native popup with a namespaced ID, Create and Cancel. New flags start Off. Duplicate IDs never overwrite an existing value.
- Creation requires operator level 2, enabled Editing, non-active play and no operation hold. Switching an existing flag On/Off requires active play, operator level 2 and no hold, with explicit confirmation. Turning a flag On can satisfy automatic Event prerequisites.
- The Event prerequisite picker lists these persisted flags and their current values. It explains where to create flags; it does not manage flags itself. Flags are boolean campaign state, not a new automatic condition rule engine.
- **Game Events** contains scenario/custom composed Events and registered game actions. A composed Event remains here even if it changes Regions. **Region Changes** contains direct saved Start/End state actions. Flags no longer appear in Events.
- Each Events filter remembers its selection/page for this menu connection. Create event belongs only to Game Events. Invalid selections cannot cross filters. Existing Locations filtering and remembered navigation remain intact.
- Canonical commands mirror the new menu at `/chronicle flags`; no duplicate Events flags management entry. Existing campaign schema and audit history are preserved; new definitions use `flag.define`. No flag deletion or renaming is introduced.

## Ownership and verification

All functionality belongs to Core. Quiet Below defaults and gameplay mechanics remain unchanged. Add service tests for phase/duplicate/capacity/persistence refusal; pure filtering tests; native client coverage for creation/cancel/duplicates/stale authority, true/false controls, prerequisite selection and filtered navigation at compact and wide sizes. Run the standard build and relevant menu/Event editor regressions serially. Inspect native captures, independently review the integrated diff, then deploy one matching client/server update after saving and backing up the current review world.

Dedicated UI design was reviewed by the lead before implementation. The existing store is sufficient: names/descriptions, arbitrary condition expressions and destructive flag management are outside this change.

The client/server menu protocol is v9 in dev62. Install the matching updated pack; the campaign schema remains v1. The existing script-oriented active-play `flags set` upsert is retained under the canonical `/chronicle flags` family, with an operation-hold guard. The UI's live controls require an existing flag; its explicit Create route never overwrites one.

## Delivery

Dev62 is live on the existing authenticated review server at `127.0.0.1:25589`, with the matching `../client/Chronicle-M9-1.21.10.mrpack`. The organizer's ACTIVE session5 and saved setup remain intact; pause play and enable Editing to create flags, then resume for live On/Off control. Source branch `feat/core-flags-and-event-filters`; no main merge. Build571Java/93Python and native18-per-size editor plus110operator/11ordinary menu checks pass; see the newest [test evidence](TEST_PLAN.md).
