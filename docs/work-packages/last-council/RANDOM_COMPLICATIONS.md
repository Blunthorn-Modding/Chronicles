# Random complication pools and arrival

User clarification, 4 November 2026. The GM should also be surprised by the selected complication and its arrival. Reusable authored pools replace choosing/preparing a particular crisis for every evening. This supersedes earlier default manual/GM-selected complication timing; the Chancellor still directs ordinary discussions and ballots.

Confirmed answers: **a configurable total across the evening**, not one guaranteed complication per meeting; each complication must arrive **before the regency vote in the meeting where it appears**, not before the evening's first regency vote.

## Reusable pool, live draw

- Authors create a pool of possible complications once, with Event references/templates, weights, requirements and suitable bindings. Default content supplies a useful starting pool; each new game does not require the GM to choose a script or place its crisis on an agenda.
- At delivery time, select randomly from eligible entries and resolve any house/realm references against the actual game. The chosen content must be compatible with current state; for example it cannot secretly add a candidate to a locked election.
- Do not preview the chosen complication, pending target, draw seed or arrival countdown in normal GM/player/debug interfaces. The GM may know/edit the possible pool contents, but learns the live selection when it is delivered to the scene. Ordinary preparation/cast preview does not draw or expose future complications.
- Commit the selection and invocation identity before dispatch. A restart, retry, reopen or recovery keeps that same draw; there is no live reroll button or automatic redraw because an effect was interrupted.
- Configure the total number for this experience, including zero to disable it. Define repetition policy against actual Event eligibility: existing once-only Events are not repeatable merely because a pool references them. Insufficient eligible content is a setup/recovery issue, not permission to invent a crisis or silently miss the configured count.

This is a new optional **Core Event-pool selection/dispatch capability**, using the existing Event executor for media and supported outcomes. It is not Books & Lore, a new mandatory story director, or an independent sound engine. Last Council supplies the pool content and Council-specific arrival rules. Existing Event definitions/history must retain their current semantics.

## Hidden timing across meetings

The declared evening/meeting setup provides eligible Council opportunities. Randomize which eligible meetings receive the configured complication slots, and their timing within those meetings. Several may fall in one meeting; none is required in every meeting. The first or last meeting can be selected. Do not display this hidden allocation as an agenda item.

An automatic arrival is eligible only while the relevant Council gathering is actually in progress and Core play is active/unheld. It must not fire during free RP outside a meeting, a technical pause, Building Mode or server downtime. Use the shared active clock for temporal calculations, not a new wall-clock scheduler that advances during pauses.

Allow arrival during discussion/between ordinary ballots. If an ordinary ballot is open at an arrival checkpoint, preserve that ballot and wait for a safe boundary before applying the complication. Do not change its electorate, submitted choices or result to make room for the story. Review exact interruption/pacing behavior with the Council UI before implementing it.

The GM can change meeting length or order. Persist and reconcile cancelled/replaced meeting opportunities for undelivered slots without exposing their contents or duplicating delivered events. Require a last-meeting/ending check so an evening cannot silently end with promised slots stranded in a cancelled future meeting. An explicit exceptional early conclusion must record what was not delivered; never mark an absent complication as delivered or emit it outside a Council to make the count look correct.

## Regency gate and the late-arrival recess

1. The Chancellor requests the regency/succession ballot.
2. Before creating/opening it, the Council service checks whether this meeting has an undelivered complication obligation. Pending allocations for later meetings do not block the present meeting's regency vote.
3. If a complication remains for this meeting, deliver it now through the Event pipeline and enter a **Council recess** for reaction and negotiation. This is the latest allowed arrival, not a hidden bypass or an additional random crisis beyond the configured total.
4. Core play stays ACTIVE during this normal recess: players can move, talk and use banquet/other gameplay. Political ballot progression is suspended; this is not `/chronicle session pause`, which freezes gameplay and would also stop automatic Event processing.
5. When delivery is complete and players have had the needed RP break, the Chancellor resumes proceedings and requests the ballot again. Resume does not automatically open or cast the vote.

For meetings without a regency ballot, perform the equivalent obligation check before closing that meeting. Multiple due complications must not be dumped simultaneously merely to clear the counter; use reviewed spacing/recess controls. The total and timing policy remain configurable rather than imposing a fixed crisis per meeting.

The gate must be enforced by every normal ballot-opening route, not just a disabled UI button. Clicking twice or racing the scheduler cannot open the regency vote before a required complication. A technical Event hold/recovery is distinct from a normal Council recess; ordinary safety/recovery behavior still applies.

## Ownership and scheduling boundary

**Core:** reusable pool definitions, validation/eligible selection, durable draw/invocation identity and dispatch through the existing executor. Extend authoring/export deliberately; do not claim pools or arbitrary repeated stateful outcomes already exist. Preserve current recurring-media restrictions instead of clearing once-only completion history.

**Last Council:** configured complication total, hidden meeting allocation, valid Council arrival windows, regency/meeting-close gate, recess/resume and scenario-specific content/bindings. The existing call-to-Council Event still starts the gathering flow; random complications do not automatically pick ordinary motions, run the election or determine political results.

**Storage:** reusable pool definitions/defaults in existing mod/server content; hidden allocation, committed draws, count progress and invocation receipts in world state. Public/GM projections show revealed history, not upcoming selections. Trusted raw server-file administrators are outside this ordinary-interface surprise guarantee. No external AI service is needed.

## Acceptance evidence

- Several seeds/counts produce different eligible events and first/intermediate/last meeting allocations. A configured count is distributed across the evening, not multiplied by the number of meetings.
- Only actual Council windows can deliver. Test technical pause/resume, empty-server time, long free-RP intermissions and changed meeting order; none cause an out-of-meeting reveal or a duplicated slot.
- Normal GM/player/debug UI, previews, suggestions and ordinary logs disclose no upcoming content or countdown. Revealed history appears after dispatch; exceptional recovery must not offer a different draw.
- A last-moment regency request enters recess and delivers the due complication before any ballot is created. A meeting without succession still resolves its due slots before closing. Later-meeting allocations do not gate an earlier meeting's vote.
- Players can continue RP/gameplay in a normal recess, including banquet interactions. No held ballot is discarded or silently recounted. The Chancellor resumes deliberation explicitly.
- Duplicate requests, restart during selection/dispatch, uncertain media, failed outcomes, cancelled meetings and exhausted pools preserve the same draw/count and leave actionable recovery. Never decrement remaining count before the recorded invocation has the defined completion/acknowledgement.
- Core/Quiet Below behavior without an enabled pool is unchanged. Native tests cover actual Event delivery and the ballot-opening gate, not just a random-number unit test.

## Review gate

Review the pool editor, hidden scheduling policy, configurable count/repetition/spacing and the late-recess player/GM flow before implementing native controls. The two quota/horizon questions above are settled; exact tuning and rescheduling presentation can be reviewed with the Council prototype.
