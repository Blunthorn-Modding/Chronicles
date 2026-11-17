# LC05 UI design — house conflicts and treaty types

## Status

This is an interactive design proposal for review, not native gameplay authorization. It supersedes no approved treaty ceremony and does not implement LC05 conflict state, breach integration, foreign affairs, rebellion, automatic PvP, or a winner.

Prototype: [`last-council-conflicts.html`](../../design/last-council-conflicts.html).

## Approved flow represented

- The personal menu remains only **Character / My house / Other houses**. It has no Conflicts entry.
- A player opens the public conflict register by clicking a public declaration notice or by `/chronicle council conflicts`. The register never offers remote initial declaration.
- Initial house war begins in person on the actual other representative. The prototype offers the proposed, not approved, empty-hand + sneak gesture fixture and then names the target character, target house, and public reason in a focused confirmation.
- Declaration is unilateral: the target has no veto. The public event is generic. An authorized initiator alone receives a private warning that their non-aggression agreement may be breached; neither its existence, type, terms, nor breach appears in an outsider projection.
- The compact public register uses a chronological left list and right detail: declaration/reason, both house-side lists, and immutable public history. A house can Join side, Change side, or Withdraw only through a focused confirmation. The Chancellor gets the same public view with no house actions.
- The Operator-only **Game setup → Conflicts** popup is explicitly limited to non-active Editing. It contains the configurable civil-war label threshold (fixture: four total houses across both sides), and has no winner control.
- Loyal warring houses keep their votes and palace rights; war does not set PvP or decide a winner. This is stated where a player accepts the declaration, not as permanent screen boilerplate.

## Treaty lectern extension

The existing treaty form needs a focused type selector: **General, Alliance, Non-aggression, Mutual defense, Trade / rights, Peace, Ceasefire**. Legacy documents safely read as **General**. For Peace or Ceasefire, the existing bound treaty lectern reveals a Conflict selector. The normal exact-signature ceremony and Chancellor presence remain the only route to settlement; the extension does not redesign the broader treaty UI or introduce realm ratification.

Private treaty type/information must be projection-scoped exactly like existing private terms: outsiders see no existence, type, terms, signatures, history, or breach implication. This design adds only an initiator-local declaration warning.

## Geometry and compact behavior

The prototype reuses the compact treaty framing: a centered maximum 700px frame, 75vh scroll bound, a 190px left list and flexible detail panel. At 650px and below, the list stacks above detail with a 150px cap. Dialogs are maximum 580px wide and 88vh high. Buttons wrap inside the frame; the screen deliberately avoids a full-width empty dashboard.

## Owner decisions still pending

1. **Challenge gesture:** whether empty-hand + sneak is the final input or a dedicated item replaces it.
2. **Peace/ceasefire consent set:** whether required signers are all current sides/parties or founding parties. The prototype does not freeze either policy; it only routes the selected conflict into the existing exact-consent ceremony.
3. **Founder withdrawal / empty side:** whether founding houses must draft settlement while supporters can withdraw, or anyone may withdraw and an empty side ends the conflict. The fixture leaves founder withdrawal disabled and labels the policy pending; it is not an approved rule.

## Focused review checks

- Normal and compact frames keep list, details, actions and dialogs within the bounds.
- Actor fixture proves an outsider sees public conflict detail only; private pact information is absent. Ash alone sees the private warning before declaration.
- The declaration confirmation names Sera / House Reed / reason, says unilateral and has no acceptance affordance.
- Public records respond to Join, Change side and Withdraw confirmations. Chancellor has no house controls; Operator configuration is isolated from Chancellor.
- Treaty type selection reveals a Conflict picker only for Peace and Ceasefire, preserving the existing ceremony statement.

## Lead verification ? 17 November 2026

Installed Edge / cached Playwright CLI: **26 checks passed**, with no page exceptions on the final run. Covered actor separation, private-warning presentation, side changes/withdrawal/rejoining, record-specific sides, threshold validation/classification, duplicate-founder refusal, treaty-type conditional fields, Escape cancellation and compact scrolling/confirmation bounds. Lead inspected normal, compact upper/lower register and compact confirmation captures. Early fixture defects were corrected before this result.

Evidence: `../reports/lc05a-prototype-validation.json` relative to repository root; screenshots and replay script under ignored `output/playwright/conflicts-*`. Reproduce with cached `playwright-cli --session lc05 run-code --filename output/playwright/conflicts-smoke.js` against loopback port 8926. Static inline JavaScript syntax, UTF-8/no-BOM, local Markdown links, unchanged source-brief hash and `git diff --check` also pass.

This verifies a local workflow simulation. It does not certify native privacy, persistence, interaction reach, treaty recovery or Minecraft layout. Those checks follow owner workflow/policy approval and implementation. No native code changed; Gradle/game tests and new packs are unnecessary for this design-only slice. Human treaty/complication multiplayer reviews remain deferred and unpassed.
