# LC05 UI design — conflicts, peace and ceasefire

## Status

The owner approved the revised workflow and authorized native implementation. It represents the confirmed Challenge-token and exact-consent rules without adding a Council outcome, forced PvP, resource change, timer, resume mechanism, or winner. The native implementation was delivered and automatically verified on dev.15; see [results](LC05A_RESULTS.md). The earlier browser results below certify the proposal only.

Prototype: [`last-council-conflicts.html`](../../design/last-council-conflicts.html).

## Conflict register and declaration

- Personal-menu access stays **Character / My house / Other houses**. A public notice or `/chronicle council conflicts` opens the public register; neither starts a remote declaration.
- A house representative uses the dedicated **Challenge token** on a nearby representative. The visual item choice remains cosmetic and unspecified. The confirmation names target character/house and reason, records a unilateral declaration, and has no target-veto control.
- The initiator alone can receive a private non-aggression warning in that confirmation. No public/outsider projection exposes a private pact’s existence, type, terms, history, or breach implication.
- The compact list/detail register presents public status, sides and history. Supporters can Join, Change side, or Withdraw through confirmations. Founders see: “Founding house: leave through a peace agreement.” Chancellor sees no house actions.
- Operator-only **Game setup → Conflicts**, while non-active Editing, configures the civil-war label threshold (2–16). It assigns no winner.

## Peace and ceasefire at the existing treaty lectern

The existing type picker includes **General, Alliance, Non-aggression, Mutual defense, Trade / rights, Peace, Ceasefire**; legacy documents read as General. Only Peace and Ceasefire expose the Conflict selector. Peace can end an Active or Paused conflict; Ceasefire selects an Active conflict. The draft shows freeform agreed terms and automatically lists every current house on both sides as a required signer, with no party-omission control.

Each current house signs the exact terms separately. The Chancellor is not a party signer: after every required house has signed, the assigned Chancellor performs the existing separate **Sanctify agreement** witness action at the same lectern. This is not Council permission.

Before sanctification, membership changes invalidate signatures and retain the typed terms for review in a fresh exact-consent draft. Peace records the conflict as **Ended**; Ceasefire records it as **Paused**. Public history records only the status change, not private agreement details. Terms remain roleplayed promises. Renewed hostilities after either agreement use another normal physical declaration and a new conflict record. Only an Active conflict between the same founding houses prevents a duplicate declaration. There is no separate resume control.

## Geometry and fixture controls

The frame remains maximum 700px, max-height 75vh, with a 190px left list/detail split. At 650px and below the list stacks over detail and caps at 150px. Dialogs remain bounded to 580px/88vh. Reviewer controls select an actor, simulate Challenge-token use, open the existing treaty lectern, and reset fictional fixture data.

## Browser review paths

- **Peace:** Ash opens Treaty lectern → Peace → Ash–Rowan; the required list is Ash, Vale and Rowan. Enter terms and prepare. Select each house actor in turn; each uses **Sign as House …**. Select Chancellor; **Sanctify agreement** becomes enabled only after all three house signatures and records Ended.
- **Ceasefire:** repeat with Ceasefire and observe Paused after sanctification; no resume control is proposed.
- **Membership invalidation:** before sanctification, select Vale, use Back to conflicts, then Change side or Withdraw. The unsigned draft’s signatures are cleared; its terms are retained in the rebuilt draft and a review notice appears.
- **Privacy:** Ash’s Challenge-token confirmation against Reed gives the private warning. Vale’s does not; public history never reveals it.

Native implementation details and the cosmetic token appearance remain outside this UI proposal.

## Lead review evidence - 17 November 2026

Lead consolidated the fixture handlers, removing superseded consent implementations. Installed Edge/cached Playwright CLI passed **44 focused checks** across challenge/side controls and the complete agreement flow. These cover dedicated-item presentation, founder/supporter controls, private-warning presentation, all-current-house signatures, Chancellor-only witnessing, missing-signature refusal, Ended/Paused records, retained text and cleared consent after membership changes, conditional fields, paused-to-peace selection and normal/compact layout. Lead inspected normal register, signed terms and compact upper/lower draft captures.

Replays: ignored `output/playwright/conflicts-v2-smoke.js` and `conflicts-v2-agreements.js`; result JSON and prototype hash: `../reports/lc05a-prototype-v2-validation.json` relative to repository root. Static inline JavaScript syntax, UTF-8/no-BOM, local Markdown links, unchanged brief hash and scoped diff checks also pass. Earlier draft fixture issues were corrected before final evidence.

These are historical browser-review results, not native implementation or multiplayer/privacy certification. The owner subsequently approved the flow and chose a new normal declaration for renewed hostilities after both agreement types. Native evidence is recorded separately.
