# Last Council: smaller interfaces tied to the scene

Status: **second UI proposal, awaiting further owner feedback**. 5 November 2026. Replaces the unified-menu direction in [the first UI pass](UI_DESIGN.md); that page and its HTML remain available unchanged while the owner continues reviewing. No gameplay/native mod implementation is authorized by this design iteration.

Active prototype: [last-council-scenes.html](../../design/last-council-scenes.html), served at `http://127.0.0.1:8925/last-council-scenes.html`. The external toolbar simulates approaching blocks, interacting with a character, opening a vote and switching people. These are review controls, not an in-game dashboard or permission switch. Changes are local sample data and are discarded by Reset/refresh.

## Owner's correction

The first prototype put too many different activities into one political menu. The owner explicitly prefers several small screens and physical interactions that bring players into the Council hall and face to face. Preserve the existing editor for authoring; do not turn its three-column layout into the universal player interface.

| Entry in the world | Screen | Contains | Excludes |
|---|---|---|---|
| Existing personal-menu access | Character & Houses, two tabs | Existing public card; editable name, **pronouns**, description; public houses, representatives and candidates | No Council/treaty/conflict action tabs, no ballot browser |
| Select a house | House information inside the personal menu | Public description/status/candidacy. On **own house only**: private house papers, related permitted records, rebellion declaration | No other house's private handouts, exposure count or a separate “House standing” screen |
| Interact with bound motion lectern by the Chancellor | Council motions | Upcoming motions, proposal, sponsorship and withdrawal where eligible | Does not open a vote or offer global remote submission |
| Chancellor opens an actual ballot | Standalone ballot popup | Current motion, options, threshold, own acknowledgement/change, visibility-specific information | No personal/house navigation, agenda editor or future ballot |
| Interact with separate bound treaty lectern | Treaty ceremony | Parties/terms, who is physically present, own signature, Chancellor sanctification | No remote signature, forced signature or automatic activation after signatures alone |
| Challenge another representative in person | Declaration popup | Actual target character/house, public reason, consequences, confirm/cancel | No global target list for remote war declaration and no target accept/veto button |
| Chancellor opens procedural controls | Council proceedings | Call/gathering, agenda item, open/close individual ballots, request regency, recess/resume/close | No universal political dashboard, house-war action or signing for a party |
| Existing Chronicle editor | Existing authoring screens | Content, bindings and rules in their canonical homes | Does not double as a player's political menu |

The house presentation is a view of public political metadata linked to the existing character card, not a replacement identity system or another hand-authored house/role catalogue. Handouts move under their house in this presentation, with the existing physical-book delivery and privacy rules retained. No “Your handouts” top-level item remains.

## Ballots and motions

The motion lectern gives a reason to approach the Chancellor. Players read upcoming motions or submit/sponsor there. The Chancellor approves the agenda and chooses when to open each vote. Opening emits the standalone ballot for each eligible representative. There is no ordinary player Council menu from which to start or browse ballots.

The ballot contains only the motion/question, public/secret indication, required support and options. Confirming records the house's choice; a new confirmation replaces it only while open. A secret ballot never displays partial totals or other houses' choices, including to the Chancellor. Ineligible houses are not offered voting controls. Disconnects do not change the electorate. Prototype final tally simulation has deliberately been removed; native code will compute the result.

Closing/dismissing a popup must not implicitly abstain. The prototype proposes a temporary active-vote reminder to return to the **same still-open** ballot, and shows it automatically when switching to an eligible sample actor after opening. **Dismissal/reopening, late join/reconnect presentation and interruption of another workstation remain review questions.** A closed vote cannot reopen through the reminder. No new notification behavior is considered accepted yet.

The Chancellor retains the existing hidden-complication gate: request regency → resolve this meeting's due Event → normal Council recess → resume → separately request regency again. Closing a meeting without regency also checks its due obligation. Recess keeps Core ACTIVE; technical pause remains in the existing Session tools. No future complication is shown in the live controls. The example checkbox outside the mock game screen only lets the owner inspect that path.

## Treaty ceremony

The owner adds a substantive rule: the parties and the Chancellor must be physically present at a separate treaty lectern, and the Chancellor sanctifies their deal. This supersedes the earlier assumption that all signatures alone activate a house treaty.

Flow: gather → select named parties and exact terms → each party reads/signs for their own house → Chancellor reviews the same terms and sanctifies → agreement active. Required realm ratification is still an additional political condition where appropriate; a witness cannot replace it. The Chancellor's presence and authority never sign on behalf of a house.

Show required attendance and missing people. Revalidate attendance, exact signed revision and eligibility at final commitment, not just while drawing buttons. Loss of presence suspends completion and shows whom to bring back. Do not silently fabricate approval, redraw terms, activate on reconnect, or delete signatures because of a UI refresh. Terms/party changes invalidate pending consent. Private terms are available only to named parties and GM; even an uninvolved user at the lectern gets no draft/title/content payload for a private agreement.

**Unsettled:** proximity radius versus an authored ceremony Region; whether attendance is continuous throughout signing; whether valid signatures are retained when somebody leaves/rejoins; draft ownership/multiple concurrent agreements; amendment/expiry/disclosure and the public record of private breaches. The sample retains signatures while attendance is missing solely to demonstrate a waiting state. It is not approval of that policy.

Breaking an active treaty remains possible; the mockup puts that action on its agreement at the treaty lectern. The latest feedback concerns formation and does not yet establish whether later breach/withdrawal also needs a witnessed ceremony. No extra requirement is silently imposed. House/public records are read-only and do not duplicate ceremony controls.

## In-person challenge

The proposed gesture is an empty-hand/item interaction with the actual representative, followed by a small declaration screen. The owner mentioned a stick or another challenge item but has not chosen one; the prototype simulates the interaction and installs no item or handler.

The screen identifies the target, requests the public reason and clearly says it declares house war. The initiator confirms; **the target has no veto**. Physical approach supplies drama, not mutual consent. Validate the actual character/house, authority and continued physical presence before commitment. Leaving/disconnecting before commitment refuses the action cleanly. A later departure does not undo an already recorded declaration.

War remains different from rebellion and a duel: loyal opponents keep votes and palace rights; no automatic PvP setting, army or winner. Existing non-aggression breaches are coordinated with the declaration without publishing private terms. Foreign war/peace remains a Council motion, not this gesture.

**Unsettled:** exact item/gesture, discoverability, distance/line of sight, multiple representatives and accessibility alternative with identical presence checks. Confirm these before native handlers/commands. A convenience command must not become a remote bypass.

## Authoring and ownership

Bind both lecterns through existing **Locations → Block sites** with distinct scenario subtypes; no new Lecterns tab, duplicate Region registry or separate ceremony editor in Game setup. Reuse the ordinary binding popup. Link existing Regions only when the reviewed attendance policy needs geometry. Geometry/private-room voice behavior remains independent.

Existing Players handles card/house assignments and draw/commit delivery. Books & Lore holds reusable content; optional shared Content pools holds templates and Event possibilities. Events handles calls, media and supported changes. Game setup holds rules and the one evening complication count. Regions/Points hold palace access and safe exit. Those authoring capabilities still need their detailed forms; the first prototype's unapproved forms are references, not approved native screens.

Core continues to own generic card fields, block binding, widgets, pools and ballots. Last Council owns political authority/projections, lectern purpose, ceremony rules, challenges and proceedings. Share small components across these screens, not one giant navigation shell. Capability-gate scenario entries so Quiet Below does not acquire political interfaces. Reusable definitions and world-bound bindings/progress retain the agreed storage split.

Public history and the Chancellor's eventual conclusion report are distinct **uses** of records, not two nearly identical player menu tabs. A public read-only record popup is included under Houses; final conclusion is a later GM-only review flow, not an always-visible second copy. Record placement can be refined with the owner.

## Review and verification boundary

First review these entry points and the size/purpose of each screen. Additional verbal feedback can refine this draft; do not request blanket implementation approval while the owner is still clicking/talking. Resolve the marked presence, challenge and ballot presentation policies before their implementation. Source brief remains unchanged; this document records later clarifications.

Verification on 5 November: `node --check docs/design/last-council-scenes.js` PASS. Local Edge smoke **37 checks PASS**, no JavaScript exceptions in the final run; desktop (1280×1000) and compact (640×800) screenshots inspected. Checks cover editable card pronouns/name continuity, own-house versus public views, lectern/ballot separation, own secret choice, dismissal/recess examples, three-house signatures plus GM sanctification, missing presence, private treaty outsiders, unilateral challenge and per-house rebellion. Small evidence stays in ignored `output/playwright/council-scenes-*`.

Independent read-only UI review caught two-party-only draft controls, sample profile/paper mismatch and an agenda-selection dead end; all corrected and exercised before the passing smoke. Initial browser-global helper-name collision was corrected. An initial test locator omitted a motion button's status suffix; the final run uses its actual accessible label. Package links, brief hash and diff checks also pass. The original prototype's HTML/CSS/JS are byte-identical to commit `97d8ea3`.

Browser sample state proves presentation/transitions only, never server authorization, actual physical presence, durable signatures, real random scheduling or networking. No Minecraft world, review server, client build or release artifact is created.
