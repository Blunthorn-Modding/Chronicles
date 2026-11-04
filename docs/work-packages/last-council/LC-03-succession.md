# LC-03 — Succession and constitutional outcomes

Status: planned. Depends on LC-02. Brief: §§3.3, 4, 6, 12, 14–15.

## Outcome and ownership

Last Council adds succession state on top of Core ballots. Core must not acquire monarchy rules or an obligatory elimination engine. An initial majority can elect a monarch immediately; failed ballots narrow the slate across later meetings.

## Work

1. Configure candidates before the first ballot; lock the initial slate at opening. Store election and meeting identities separately from Chronicle sessions.
2. Enforce secret succession votes and at most one succession ballot per meeting on the server, including stale clients and retries.
3. On majority: crown once. On no majority with >2 candidates: eliminate exactly one lowest candidate. Persist tied-last draw input/outcome before announcement; recovery cannot reroll.
4. On two candidates with no majority: vacancy. On one candidate after withdrawal/rebellion: a later Yes/No/Abstain majority confirmation. On zero candidates/seats: vacancy. An early evening end records unresolved, never a plurality winner.
5. Warn about insufficient scheduled succession meetings (`C-1` opportunities for an unchanged initial slate), without changing rules or interrupting scenes automatically.

## Acceptance and verification

Cover every brief example:8 seats 4/3/1;4/4;5/3;9 seats5/2/2;8 seats4/2/1/1; and five eligible seats after three rebels. Also cover all abstentions, zero votes for several last-place candidates, withdrawal to one/zero, no eligible seats, stale close and actual restart during draw/outcome application.

An eliminated candidate retains their vote. A crowned house receives no extra vote/veto or automatic expulsion power. Ordinary meetings continue after crowning/vacancy. A new meeting ID is required for the next succession ballot. Repeated close/notification requests cannot eliminate another candidate or produce a second crown.

Native proof must include real secret casting and a later meeting, not only direct model setup. Review how a host explicitly resets an election while preserving its history; do not implement ordinary rebellion as an implicit abdication/deposition shortcut.

## Review gate

Review constitutional results and result/history wording before proceeding to rebellion integration. UI shows required majority, remaining candidates and recorded eliminations without secret individual choices or a misleading total of votes cast.
