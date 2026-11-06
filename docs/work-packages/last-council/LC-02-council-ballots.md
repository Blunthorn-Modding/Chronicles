# LC-02 — Council meetings, motions and ballots

Status: in implementation; Core ballots, motion/proceedings runtime and native screens are implemented. LC-08 complication progression remains pending. Depends on LC-01A; randomized handout preparation can progress separately. Brief: §§4–5, 11–12, 14–15. Apply the user's [Event-led Council flow](FRAMEWORK_INTEGRATION.md).

## Outcome and ownership

Existing Events supply the Council alarm/announcement and recurring call delivery. Core adds an optional ballot service; Last Council supplies political meeting/agenda rules and eligible-house snapshots. The Chancellor is the GM: one authority calls the gathering, conducts discussions and starts/closes votes individually, choosing when to raise the stakes with succession. Players submit/sponsor motions and cast one immutable confirmed public or secret vote per ballot.

Proposed Core ownership: ballot domain/store, privacy projections and reusable voting widgets. Last Council: meeting/agenda/motion services, thresholds by category and participant/host Council screens. Native UI and commands use the same authoritative services.

## Work

1. Design/review the flow: existing Call Council Event → gathering → GM opens meeting → discussion and individually opened/closed motions → succession when relevant → GM closes meeting. Apply the latest [scene-based UI](UI_SCENES_FLOW.md): players propose/read/sponsor at a bound motion lectern beside the Chancellor; opening an actual vote presents a standalone current-motion ballot to eligible players. No general player Council/ballot browser. GM proceedings controls are focused and separate from authoring and casting, not a separate Chancellor role. Reuse widgets, shared pickers and confirmations; no public full-state packet. Confirmed access is a configurable hotkey plus `/chronicle council vote`, without replacing an unrelated workstation on opening. Dismissal never abstains.
2. Implement submitted → sponsored → agenda → debate → voted → recorded motions. Default one other house sponsors; host/emergency motions may bypass. A withdrawal is allowed only before the vote opens.
3. Implement one open ballot at a time, immutable electorate/options/threshold/visibility and one immutable confirmed choice per house. At opening snapshot distinct eligible houses whose online representative is inside the bound Council Region; five present means three required for absolute majority. Absent houses cannot join that ballot late. Departures/disconnect/reconnect never change its N/threshold; captured representatives may return and cast from the Region, retaining any prior final choice. Duplicate/stale submissions cannot replace it. Refuse an empty electorate without an automatic pass or monarchy outcome. Pause freezes voting/countdowns, not a fabricated result.
4. Close with a durable tally/outcome receipt. Aggregate history and law records remain available before and after a crown decision; free-text decrees do not execute arbitrary rules.
5. Add eligibility checks for every player and host operation, public versus secret projections and transparent technical recovery. Settle the void/reopen rule before exposing it. Confirmed: the Chancellor may void a mistaken open ballot with a required public reason. Preserve its immutable original votes; any replacement is a new ballot ID and a new electorate snapshot. Finalized political outcomes need separate rollback rules before permitting post-close voids.
6. Connect the authorized Council call to a source occurrence consumed by the existing repeating text/sound Event path. Keep alarm configuration in the existing Events editor; preserve once-only Event completion records and recurring-media restrictions. A later call gets a new occurrence, while a retry of the same call does not replay it. Meeting state and ballot choices remain their authoritative records; the bell does not automatically open a vote or choose an agenda.
7. Provide the Council progression/recess contract used by LC-08B: due random complications block regency opening and meeting closure until delivered and the Chancellor resumes. Normal Council recess leaves Core play ACTIVE. Enforce the gate on UI, command and recovery/opening paths; do not secretly change ordinary ballot choices or show the GM a future complication schedule.

## Acceptance and verification

- N=8 absolute majority requires5; two-thirds requires6. Abstention/non-voting never lowers N. Disconnect/reconnect cannot cast twice or edit after close.
- Public named choices work. Secret ballots expose no partial totals or other choices to players, GM/debug, normal history, Event payloads or reusable exports.
- A confirmed choice is final for that ballot: different-value retries, stale clients, reopening, reconnect and command routes cannot replace it. Concurrent submissions accept at most the first valid choice. Technical void/reopen remains a separately reviewed intervention, never an ordinary change-vote action.
- Five of eight Council houses physically present at opening produce N=5, absolute majority3/two-thirds4. Late arrivals cannot vote; departures, missing votes and disconnects never reduce N. Captured representatives can return and cast from the Region only if they have not voted. Test actual Region entry/exit, empty electorate, absent-at-opening reconnects and repeated opening requests. No waiting/quorum popup or remote cast route.
- Changes of membership/rebellion are refused while a ballot is open with a clear explanation. Opening another ballot concurrently fails without changing the first.
- Repeated/delayed close requests and restart mid-close reuse the same result; no duplicated political outcome. Uncertain operations have visible recovery.
- Player motions, sponsorship and deferral reasons are recorded; an eliminated candidate remains a voter. No automatic forced meeting at a timer deadline.
- The GM can conduct several small motions before the ruler vote, defer that vote to a later meeting, and stop the current meeting without exhausting a fixed agenda. Once-per-meeting succession remains enforced. GM authority alone adds no seat/vote.
- Two authorized gatherings can use the same configured alarm Event with distinct call receipts. Duplicate requests/restart cannot produce an unplanned second alarm or ballot. Existing private Event preview remains without political effects.
- A random complication due for this meeting is delivered before a regency vote can be opened. Late delivery allows a Council recess for RP; the world is not technically paused. Due events in later meetings do not block this meeting's election. See [timing/count rules](RANDOM_COMPLICATIONS.md).

Use parameterized counting/lifecycle/privacy tests, forged packet tests and at least two native participants plus a GM. Include restart while open and interrupted close. Core changes need relevant Quiet Below session/menu/profile regressions; ballots stay absent when the capability is unused.

## Review gate

UI mockup approval precedes native screen implementation. Independent review of secret projections and close/outcome recovery is required before succession depends on the service. Document exact public/GM data, active-clock behavior and exceptional intervention records.
