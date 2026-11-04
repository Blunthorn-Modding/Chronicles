# LC-02 — Council meetings, motions and ballots

Status: planned. Depends on LC-01. Brief: §§4–5, 11–12, 14–15.

## Outcome and ownership

Core supplies an optional ballot service; Last Council supplies political meeting/agenda rules and eligible-house snapshots. Players submit and sponsor motions, debate and cast/change real public or secret votes. Hosts manage procedure, not vote choices.

Proposed Core ownership: ballot domain/store, privacy projections and reusable voting widgets. Last Council: meeting/agenda/motion services, thresholds by category and participant/host Council screens. Native UI and commands use the same authoritative services.

## Work

1. Design/review participant navigation: Council overview → agenda/motion → ballot. Design GM schedule/agenda/open/close controls separately. Reuse normal UI components, shared Add pickers and confirmations; no public full-state packet.
2. Implement submitted → sponsored → agenda → debate → voted → recorded motions. Default one other house sponsors; host/emergency motions may bypass. A withdrawal is allowed only before the vote opens.
3. Implement one open ballot at a time, immutable electorate/options/threshold/visibility and a revisioned latest choice per house. Disconnects retain seats; pause freezes voting/countdowns, not a fabricated result.
4. Close with a durable tally/outcome receipt. Aggregate history and law records remain available before and after a crown decision; free-text decrees do not execute arbitrary rules.
5. Add eligibility checks for every player and host operation, public versus secret projections and transparent technical recovery. Settle the void/reopen rule before exposing it.

## Acceptance and verification

- N=8 absolute majority requires5; two-thirds requires6. Abstention/non-voting never lowers N. Disconnect/reconnect cannot cast twice or edit after close.
- Public named choices work. Secret ballots expose no partial totals or other choices to players, GM/debug, normal history, Event payloads or reusable exports.
- Changes of membership/rebellion are refused while a ballot is open with a clear explanation. Opening another ballot concurrently fails without changing the first.
- Repeated/delayed close requests and restart mid-close reuse the same result; no duplicated political outcome. Uncertain operations have visible recovery.
- Player motions, sponsorship and deferral reasons are recorded; an eliminated candidate remains a voter. No automatic forced meeting at a timer deadline.

Use parameterized counting/lifecycle/privacy tests, forged packet tests and at least two native participants plus a GM. Include restart while open and interrupted close. Core changes need relevant Quiet Below session/menu/profile regressions; ballots stay absent when the capability is unused.

## Review gate

UI mockup approval precedes native screen implementation. Independent review of secret projections and close/outcome recovery is required before succession depends on the service. Document exact public/GM data, active-clock behavior and exceptional intervention records.
