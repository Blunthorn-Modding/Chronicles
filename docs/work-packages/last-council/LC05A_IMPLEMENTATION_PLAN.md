# LC05A house conflicts

The owner requested the next milestone after accepting dev.14. This slice develops house war, changing support and voluntary peace/ceasefire agreements. The first deliverable is the interactive workflow review. Native controls follow the approved flow.

## Current decisions

An initial declaration starts with an interaction on the actual opposing representative. The initiating house confirms a public reason. The target has no veto. Public conflicts have opposing sides, a chronological record and voluntary support. Civil-war classification starts at four participating houses across both sides, with a configurable threshold. War preserves Council and palace privileges.

The owner confirmed three rules on 17 November:

- Use a dedicated challenge item on the actual opposing representative. Its final name/art is not chosen; the mockup calls it a challenge token.
- Every current house in a conflict must sign a peace or ceasefire agreement.
- Founding houses remain on their original sides until an agreement; supporting houses may change sides or withdraw voluntarily.

The owner also rejected an abstract settlement mechanic and requested another workflow review. The revised proposal removes that category entirely. Peace/Ceasefire reuse voluntary treaty consent and only record the conflict as ended/paused. Negotiated demands stay freeform promises: the mod does not force a Council decision, ballot choice, law, payment, surrender or military result. The owner approved this revised workflow and authorized implementation.

## Workflow and UI boundaries

The dedicated challenge item opens a declaration popup identifying the actual character and house, collecting the public reason and offering Confirm/Cancel. A server-created offer binds the target, current assignments and interaction context. Confirmation revalidates both players, the held challenge item, their connection/house assignment, normal interaction reach, line of sight and active participation. Leaving before commitment refuses the declaration. The completed declaration persists after a later departure.

The public conflict register is a separate compact screen. Open it from a clickable declaration notice, a View conflict action or `/chronicle council conflicts`. Its left list selects a conflict and the right panel shows the declaration, sides, status and public history. Joining a side, changing support and withdrawal use focused confirmation dialogs. Initial war declaration remains tied to the physical interaction. The character/house personal menu keeps its existing scope.

The Chancellor can inspect public conflicts. The operator's **Game setup > Conflicts** settings popup owns the civil-war threshold under the usual non-active Editing guard. A Chancellor without a house has no house-side actions.

Peace and ceasefire use the existing treaty lectern. A focused addition to treaty creation selects the agreement type and, for Peace/Ceasefire, the conflict. All current houses are listed automatically as required parties. The parties write their own terms, then sign that exact document revision. The Chancellor witnesses/sanctifies under the existing ceremony and attendance rules; this is not a Council vote or authority to impose terms. Peace records the conflict as ended; ceasefire records it as paused. Conflict membership changes invalidate completion based on an old party set; the draft must be updated and signed again. An ended conflict is historical and has no support actions. The proposed paused view retains sides/history and directs further agreements through the lectern; renewed hostilities after peace or ceasefire use the normal physical challenge and create a new conflict ID, preserving prior records. There is no separate resume action. Only an ACTIVE conflict between the same founding pair blocks a duplicate declaration.

## Treaty prerequisite

`TreatyService.Document` currently stores title, terms, visibility and parties. It has no agreement type or conflict link. Automatic breaches cannot be inferred reliably from its text.

Add a typed purpose to new drafts: General, Alliance, Non-aggression, Mutual defense, Trade/rights, Peace or Ceasefire. Peace/Ceasefire drafts also carry a conflict ID and pinned membership revision. Editing those fields increments the content revision and clears old signatures.

The strict schema reader maps existing documents to General and preserves their terms, signatures, visibility and history. Existing active agreements are inspected under that classification. Giving an active agreement a new mechanical purpose requires a new negotiated document through the existing ceremony. Active amendments and realm ratification remain separate LC04 work.

A non-aggression pact's automatic breach applies when its party declares war against another signatory. It does not infer promises from free text. Mutual-defense parties receive a private notice and choose their own support. Private treaty existence, terms, IDs and breach history stay outside public conflict packets and announcements.

## Implementation order

1. **Review the revised focused UI.** The dedicated UI agent supplies the prototype with the three confirmed policies and concrete voluntary-agreement flow, then the lead verifies normal/compact views and interaction paths. Owner review passed: implement the revised flow, with renewed hostilities through a new normal declaration and no resume control.
2. **Typed treaty records.** Extend the Last Council treaty model, codec, draft payloads and the existing small creation/edit form. Retain legacy readers and signature/visibility rules. Verify migration, stale signatures, inactive editing and private projections before integration.
3. **Conflict domain.** Add a bounded immutable world-owned journal for declaration, sides, membership revision, classification, status and public history. Server authority maps the actor's current house. One house occupies at most one side per conflict. Multiple independent conflicts remain possible. Founders cannot withdraw or switch sides; supporters can, with explicit public records.
4. **Physical declaration and register.** Add the approved interaction handler and nonce-bound confirmation. Present public records and eligible side actions in a separate screen. Keep authority, target and revision checks on the server. Reuse the existing compact frame, list/detail sections and popups.
5. **Treaty effects and recovery.** Pin relevant treaty revisions and the conflict operation before applying a declaration's non-aggression breaches or a unanimously signed, sanctified peace/ceasefire status change. Reuse the pinned operation on retry and restart. A pending cross-store operation holds progression and has an operator recovery path. Publish the public conflict change only after its durable effects complete. Public history exposes the resulting conflict status while honoring the agreement's visibility; no private treaty ID, title or existence is disclosed. Freeform terms never execute arbitrary gameplay or Council actions.
6. **Configuration and verification.** Export the reusable threshold through the existing scenario extension pattern. Keep conflict state, current sides and operation receipts in the world. Audit Event-source budgets before registering completed public conflict changes for existing flags/Events. Verify the combined package before building matching e4mc Host/Player packs.

Last Council owns political types, consent effects, conflicts and permissions. Core's existing participation, character cards, Events, storage, authoring guards and UI widgets provide the shared facilities. This slice adds no military simulation or automatic combat outcome.

## Acceptance checks

- Physical target authority, changed assignment, expired offer, distance/line of sight, disconnect and forged remote declaration.
- Unilateral declaration, self-house refusal, duplicate request/restart, simultaneous conflicts and one-side-per-conflict enforcement.
- Voluntary support changes, refusal of founder withdrawal/side-switching, public history and no automatic ally enlistment.
- Civil-war threshold entry/exit with at least one house on both sides, preserving Council/palace rights.
- Public/private non-aggression breach projections. Outsider list/detail/debug/error/history paths reveal no private treaty identifiers, titles, terms, counts or existence.
- Every current house must sign the same peace/ceasefire text; changed membership invalidates the old party set. Test absent parties, status-only effects, repeat sanctification and interrupted cross-store recovery. Confirm no forced vote, law or demanded action is executed.
- Legacy treaty migration and export/import/restart of reusable settings without private progress.
- Native multi-client normal/compact screens and controller restart checks, with disposable worlds and retained compact evidence. Human treaty/complication group reviews remain deferred.

Foreign realms and Council-authorized external war/peace follow in LC05B. Rebellion, reinstatement and palace access remain LC06. The source event brief stays unchanged, with later owner decisions recorded here.
