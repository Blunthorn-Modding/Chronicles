# LC-02 — Council meetings, motions and ballots

Status: planned. Depends on LC-01A; randomized handout preparation can progress separately. Brief: §§4–5, 11–12, 14–15. Apply the user's [Event-led Council flow](FRAMEWORK_INTEGRATION.md).

## Outcome and ownership

Existing Events supply the Council alarm/announcement and recurring call delivery. Core adds an optional ballot service; Last Council supplies political meeting/agenda rules and eligible-house snapshots. The Chancellor is the GM: one authority calls the gathering, conducts discussions and starts/closes votes individually, choosing when to raise the stakes with succession. Players submit/sponsor motions and cast/change real public or secret votes.

Proposed Core ownership: ballot domain/store, privacy projections and reusable voting widgets. Last Council: meeting/agenda/motion services, thresholds by category and participant/host Council screens. Native UI and commands use the same authoritative services.

## Work

1. Design/review the flow: existing Call Council Event → gathering → GM opens meeting → discussion and individually opened/closed motions → succession when relevant → GM closes meeting. Participant navigation remains Council overview → agenda/motion → ballot. GM schedule/agenda/open/close controls are distinct from participant casting, not a separate Chancellor role. Reuse normal UI components, shared Add pickers and confirmations; no public full-state packet.
2. Implement submitted → sponsored → agenda → debate → voted → recorded motions. Default one other house sponsors; host/emergency motions may bypass. A withdrawal is allowed only before the vote opens.
3. Implement one open ballot at a time, immutable electorate/options/threshold/visibility and a revisioned latest choice per house. Disconnects retain seats; pause freezes voting/countdowns, not a fabricated result.
4. Close with a durable tally/outcome receipt. Aggregate history and law records remain available before and after a crown decision; free-text decrees do not execute arbitrary rules.
5. Add eligibility checks for every player and host operation, public versus secret projections and transparent technical recovery. Settle the void/reopen rule before exposing it.
6. Connect the authorized Council call to a source occurrence consumed by the existing repeating text/sound Event path. Keep alarm configuration in the existing Events editor; preserve once-only Event completion records and recurring-media restrictions. A later call gets a new occurrence, while a retry of the same call does not replay it. Meeting state and ballot choices remain their authoritative records; the bell does not automatically open a vote or choose an agenda.

## Acceptance and verification

- N=8 absolute majority requires5; two-thirds requires6. Abstention/non-voting never lowers N. Disconnect/reconnect cannot cast twice or edit after close.
- Public named choices work. Secret ballots expose no partial totals or other choices to players, GM/debug, normal history, Event payloads or reusable exports.
- Changes of membership/rebellion are refused while a ballot is open with a clear explanation. Opening another ballot concurrently fails without changing the first.
- Repeated/delayed close requests and restart mid-close reuse the same result; no duplicated political outcome. Uncertain operations have visible recovery.
- Player motions, sponsorship and deferral reasons are recorded; an eliminated candidate remains a voter. No automatic forced meeting at a timer deadline.
- The GM can conduct several small motions before the ruler vote, defer that vote to a later meeting, and stop the current meeting without exhausting a fixed agenda. Once-per-meeting succession remains enforced. GM authority alone adds no seat/vote.
- Two authorized gatherings can use the same configured alarm Event with distinct call receipts. Duplicate requests/restart cannot produce an unplanned second alarm or ballot. Existing private Event preview remains without political effects.

Use parameterized counting/lifecycle/privacy tests, forged packet tests and at least two native participants plus a GM. Include restart while open and interrupted close. Core changes need relevant Quiet Below session/menu/profile regressions; ballots stay absent when the capability is unused.

## Review gate

UI mockup approval precedes native screen implementation. Independent review of secret projections and close/outcome recovery is required before succession depends on the service. Document exact public/GM data, active-clock behavior and exceptional intervention records.
