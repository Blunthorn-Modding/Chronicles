# LC-03 — Succession and constitutional outcomes

Status: in implementation. Pure rules, durable regency intents/history and native proceedings integration are implemented; native verification and actual LC08 progression integration are tracked separately. Depends on LC-02. Brief: §§3.3, 4, 6, 12, 14–15.

## Outcome and ownership

Last Council adds succession state on top of Core ballots. Core must not acquire monarchy rules or an obligatory elimination engine. An initial majority can elect a monarch immediately; failed ballots narrow the slate across later meetings.

## Work

1. Configure candidates before the first ballot; lock the initial slate at opening. Store election and meeting identities separately from Chronicle sessions.
2. A technical void may be replaced in the same Council meeting, preserving its candidate slate and complication history. Only a completed regency consumes the meeting slot; replacement uses a new ballot ID and electorate. Enforce secret succession votes and at most one succession ballot per meeting on the server, including stale clients and retries.
3. On majority: crown once. On no majority with >2 candidates: eliminate exactly one lowest candidate. Persist tied-last draw input/outcome before announcement; recovery cannot reroll.
4. On two candidates with no majority: vacancy. On one candidate after withdrawal/rebellion: a later Yes/No/Abstain majority confirmation. On zero candidates/seats: vacancy. An early evening end records unresolved, never a plurality winner.
5. Warn about insufficient scheduled succession meetings (`C-1` opportunities for an unchanged initial slate), without changing rules or interrupting scenes automatically.
6. Before opening a regency/succession ballot, enforce the [random-complication progression gate](RANDOM_COMPLICATIONS.md). A due complication for the current meeting arrives first; a late one creates a Council recess. Ordinary RP continues, then the Chancellor resumes and requests the vote. Pending slots in later meetings do not block the present vote. No interface/command may bypass this gate.

## Acceptance and verification

Cover every brief example:8 seats 4/3/1;4/4;5/3;9 seats5/2/2;8 seats4/2/1/1; and five eligible seats after three rebels. Also cover all abstentions, zero votes for several last-place candidates, withdrawal to one/zero, no eligible seats, stale close and actual restart during draw/outcome application.

An eliminated candidate retains their vote. A crowned house receives no extra vote/veto or automatic expulsion power. Ordinary meetings continue after crowning/vacancy. A new meeting ID is required for the next succession ballot. Repeated close/notification requests cannot eliminate another candidate or produce a second crown.

Native proof must include real secret casting and a later meeting, not only direct model setup. Review how a host explicitly resets an election while preserving its history; do not implement ordinary rebellion as an implicit abdication/deposition shortcut.

Add a real late-complication/recess → resumed regency proof, including racing/double-open requests and restart. LC-03's domain can use the LC-02 gate contract before LC-08B is implemented, but integrated succession is not marked complete until its actual Event-pool gate passes.

## Review gate

Review constitutional results and result/history wording before proceeding to rebellion integration. UI shows required majority, remaining candidates and recorded eliminations without secret individual choices or a misleading total of votes cast.


## Current engineering boundary

See [LC03 implementation slices](LC03_IMPLEMENTATION_PLAN.md). Succession uses the existing proceedings journal, schema2, preserving schema1 ordinary ballot history on load. A tied-last result is saved in the close intent before Core closes its ballot, then applied with the round finalization. Chronological replay validates every slate and removal, including withdrawal order, terminal outcomes and meeting bounds. The initial slate currently supports2?15 candidates (Core's bounded sixteen-option ballot includes Abstain); larger configuration refuses explicitly.

The approved Request regency scene and public outcome text are implemented. Pure withdrawal/unfinished-ending APIs are tested but deliberately have no new player/GM control pending their detailed flow review. There is no election reset or post-crown rollback. Actual rebellion/zero-political-seat integration, schedule-capacity warnings and the LC08 hidden complication pool/gate remain pending. Empty Region attendance refuses a ballot and never declares the throne vacant. The default progression hook means no configured complications in this development slice; it is not evidence of integrated random complications.
