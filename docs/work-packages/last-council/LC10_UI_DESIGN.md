# LC10 · Voice room Region subtype — UI proposal

Status: workflow approved by the owner on 19 November 2026. Native integration is implemented in dev.18 and its real shared-picker workflow was exercised. Microphone/audibility review remains pending; see [results](LC10_RESULTS.md). See [implementation plan](LC10_IMPLEMENTATION_PLAN.md).

## Authoring flow

- In the existing Core **Regions** list, select a Region and open **Choose subtypes**. The existing popup gains one optional **Voice room** checkbox alongside other subtypes. Apply or cancel in that popup; there is no new navigation entry, room catalogue, settings page, or additional popup. Enabling automatically creates and manages the group; disabling removes the Chronicle-owned group. Reopening or restarting reconstructs enabled rooms automatically, without manual group creation, selection, IDs or settings.
- The selected Region describes the enabled behavior concisely: people inside share the room, exit returns to normal proximity, and outsiders cannot hear it. The subtype is independent of Region access, politics, session pauses and building settings.
- One hidden, isolated Simple Voice Chat group belongs to each enabled Region; its display name derives from the Region name. All players inside join the same group. Group implementation identifiers are not exposed as room names. Movement into and out of bounds drives membership.
- Normal mode keeps room/dependency/member diagnostics out of the selected-Region view. Existing **Debug** reveals dependency availability, Region-derived group name, isolation and member count; the illustrative overlap check also appears there. Missing Simple Voice Chat is a prominent actionable unavailable state even outside Debug; do not assert that privacy is active in that state.
- The first slice rejects enabling or saving active Voice room bounds that overlap another active Voice room in the same dimension. Show the reason at the action; do not silently choose a priority. Apply must recheck the current server state and retain the draft on refusal. Bounds edits must use the same check, not just this checkbox.
- Existing operator, Editing, non-active and current-state guards apply to changes. Show a concise reason when editing is unavailable. Runtime voice membership is independent of authoring permissions.

## Mockup

[Interactive proposal](../../design/chronicle-voice-rooms.html): select either Region; open the existing chooser to enable or disable Voice room. The outside-the-game reviewer controls simulate a missing dependency, an overlapping room, members, and actor/phase guards. Switch Debug inside the screen to inspect diagnostics. The mockup is illustrative and makes no server calls.

Intended lead review at wide and compact viewport sizes: verify contained screen and dialog, reachable controls, selected Region copy, toggle/cancel, disabled editing reasons, missing-dependency refusal, overlap refusal, and Debug-only diagnostics. The established left navigation, middle Region list and right description remain visible at compact scale; content and dialog scroll inside their bounds.

## Runtime policy and verification

The approved exit behavior is normal proximity voice, rather than restoration of a previous manual group. Groups are created automatically when assigned and reconstructed from world bindings after restart. Runtime failure blocks voice authorization and reports an unavailable room; no unsupported privacy guarantee is presented. Disconnect, respawn, teleport, boundary transitions and actual voice-server behavior require native verification. These policies add no per-room settings.
