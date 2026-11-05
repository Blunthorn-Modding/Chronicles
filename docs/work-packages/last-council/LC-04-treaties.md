# LC-04 — Treaties and explicit consent

Status: planned. Depends on LC-01; realm ratification also needs LC-02. Brief: §§7, 10–12, 14.

## Outcome and ownership

Build a new Last Council treaty mechanic: the parties gather at a separate bound treaty lectern with the Chancellor, review/sign identical terms and have the Chancellor sanctify their agreement. Apply the latest [scene-based clarification](UI_SCENES_FLOW.md): signatures alone do not activate it. It can later be broken, leaving a record. Supported political types include alliance, non-aggression, mutual defense, trade/rights, general treaty and peace/armistice.

No treaty/signature system exists in Core today. Do not make a generic Core agreement framework or Books & Lore integration a prerequisite. Share ordinary UI/storage helpers where useful; extract a reusable agreement service later only if a concrete need justifies it. Physical book presentation is an optional design choice, not the treaty's authority.

## Work

1. Design/review bound lectern → parties/terms → in-person attendance → explicit individual signatures → Chancellor sanctification → active → breach/end/history UI. Every signature binds the exact terms/revision shown. The Chancellor cannot force-sign for a party. Settle radius/Region, continuous attendance and signature retention after leaving before native implementation.
2. Invalidate pending consent when terms/parties change; reject stale signing and signatures on another player's behalf. No duplicate signature or operator force-sign button.
3. Public/private visibility is server enforced. Private terms/history are available to signatories and GM; review deliberate reveal/breach publication before implementing those actions.
4. House agreements require all signatures plus Chancellor sanctification while required people are present. Revalidate at commitment; missing attendance suspends completion rather than inventing consent. Realm-wide agreements additionally require the designated Council ratification outcome, applied once. Neither witness presence nor a ballot substitutes for the parties' exact consent.
5. Preserve broken agreements as history. Voting promises never force a choice; defense pacts notify rather than enlist allies.

## Acceptance and verification

Two native players can review/sign at the bound lectern with the Chancellor, who sanctifies only once when required attendance and consent are valid. Outsiders cannot obtain private terms through list/detail/picker/debug/packet/history routes. Changed terms reject old consent. Restart mid-signature/sanctification/ratification cannot activate twice. Leaving before commitment blocks completion; signature retention/re-entry behavior follows the reviewed policy. Breaking remains possible and preserves permitted history.

Test realm-ratification refusal/passage, parties losing Council privileges, private disclosure, duplicate requests and absent signatories. Political eligibility is distinct from being a treaty party; rebels remain able to negotiate where the brief permits.

## Review gate

Review consent UX, amendment rules and the public record of private breaches before LC-05 consumes agreement state. Independent privacy/transaction review is required; secret ballot privacy remains stricter than GM visibility of treaties.
