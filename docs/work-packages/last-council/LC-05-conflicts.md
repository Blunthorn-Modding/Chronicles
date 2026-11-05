# LC-05 — Political conflict and foreign affairs

Status: planned. Depends on LC-02 and LC-04. Brief: §§5.4, 8.1–8.3, 10–12, 14–15.

## Outcome and ownership

Last Council tracks house wars, sides, civil-war classification and realm-level foreign diplomacy, using its treaty records from LC-04 and the planned Core ballots from LC-02. Existing audit/source/notification infrastructure can support implementation; it is not an existing political-conflict mechanic. There is no military simulation.

## Work

1. House war declaration begins with an in-person challenge interaction on the other representative, followed by target/reason/consequence confirmation; see [the latest UI direction](UI_SCENES_FLOW.md). Gesture/item and presence boundary are not yet chosen. Revalidate initiator authority, actual target character/house and presence before commitment. Declaration is unilateral: no target veto or Council vote. No remote target-list/command bypass. Participants join one side only, with voluntary changes/withdrawals and recorded peace/ceasefire.
2. Classify civil war at the configured total-house threshold across opposing sides; recompute on membership changes without converting participants to rebels.
3. Identify a breached active non-aggression pact when war is committed; coordinate declaration and breach receipts. Do not leak private treaty terms to outsiders.
4. Foreign war/peace targets a host-defined realm. Apply the appropriate successful Council motion once; a failed motion changes no diplomatic state.
5. Expose public conflict/history projections and voluntary defense-pact notifications. War never changes palace access, votes or PvP settings.

## Acceptance and verification

Test declaration without a ballot, side exclusivity per conflict, multiple simultaneous conflicts, threshold entry/exit, withdrawal, ceasefire/peace and treaty-linked breach recovery. Defense allies are notified without auto-joining. Both warring loyal houses remain voters and palace visitors.

An8-seat external-war ballot needs6 affirmative votes at default two-thirds. Repeated close/restart cannot create another war or overwrite a later settlement. Named foreign realms have narrative diplomatic state, no spawned armies/resources or automatic winner.

## Review gate

Review declarations/side changes and private-breach projections before group rehearsal. The host may narrate consequences but cannot silently replace voted outcomes or assign a military victor.
