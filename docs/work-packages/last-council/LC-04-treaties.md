# LC-04 — Treaties and explicit consent

Status: planned. Depends on LC-01; realm ratification also needs LC-02. Brief: §§7, 10–12, 14.

## Outcome and ownership

Build a new Last Council treaty mechanic: one house proposes terms to named parties, each reads/signs the same terms, and the agreement becomes active after the required consent. It can later be broken, leaving a record. Supported political types include alliance, non-aggression, mutual defense, trade/rights, general treaty and peace/armistice.

No treaty/signature system exists in Core today. Do not make a generic Core agreement framework or Books & Lore integration a prerequisite. Share ordinary UI/storage helpers where useful; extract a reusable agreement service later only if a concrete need justifies it. Physical book presentation is an optional design choice, not the treaty's authority.

## Work

1. Design/review draft → parties/terms → offered → explicit signatures → active → breach/end/history UI. Example: A proposes a non-aggression pact with B; B reads/signs; both see it active; either may later break it. Every signature binds the exact terms/revision shown.
2. Invalidate pending consent when terms/parties change; reject stale signing and signatures on another player's behalf. No duplicate signature or operator force-sign button.
3. Public/private visibility is server enforced. Private terms/history are available to signatories and GM; review deliberate reveal/breach publication before implementing those actions.
4. House agreements activate on all required signatures. Realm-wide agreements additionally require the designated Council ratification outcome, applied once.
5. Preserve broken agreements as history. Voting promises never force a choice; defense pacts notify rather than enlist allies.

## Acceptance and verification

Two native players can review and sign; an outsider cannot obtain private terms through list/detail/picker/debug/packet/history routes. Changed terms reject an old consent. Restart mid-last-signature or ratification cannot activate twice. Breaking a treaty remains possible and preserves the permitted history.

Test realm-ratification refusal/passage, parties losing Council privileges, private disclosure, duplicate requests and absent signatories. Political eligibility is distinct from being a treaty party; rebels remain able to negotiate where the brief permits.

## Review gate

Review consent UX, amendment rules and the public record of private breaches before LC-05 consumes agreement state. Independent privacy/transaction review is required; secret ballot privacy remains stricter than GM visibility of treaties.
