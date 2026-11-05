# Last Council: smaller interfaces tied to the scene

Status: **scene-based UI proposal, revised after owner review**. 5 November 2026. Replaces the unified-menu direction in [the first UI pass](UI_DESIGN.md); that page and its HTML remain available unchanged. No gameplay/native mod implementation is authorized by this design iteration. Latest changes: Character / My house / Other houses; rich public profiles and private goals/facts; create multiple treaties from representatives using the same lectern; immutable confirmed votes; always-visible Call meeting. Voting requires the voting Region; the majority-denominator question is pending owner clarification.

Active prototype: [last-council-scenes.html](../../design/last-council-scenes.html), served at `http://127.0.0.1:8925/last-council-scenes.html`. The external toolbar simulates approaching blocks, interacting with a character, opening a vote and switching people. These are review controls, not an in-game dashboard or permission switch. Changes are local sample data and are discarded by Reset/refresh.

## Owner's correction

The first prototype put too many different activities into one political menu. The owner explicitly prefers several small screens and physical interactions that bring players into the Council hall and face to face. Preserve the existing editor for authoring; do not turn its three-column layout into the universal player interface.

| Entry in the world | Screen | Contains | Excludes |
|---|---|---|---|
| Existing personal-menu access | Character / My house / Other houses, three tabs | Existing editable public card; own house and the public directory have separate entry points | No Council/treaty/conflict action tabs, no ballot browser |
| My house | Private house view | House goals, character goals, private facts and assigned reports about other houses, related permitted records, rebellion declaration | No indication of who knows your secrets |
| Other houses | Rich public directory | Reputation, economic/social role, known interests, public representative and candidacy | No private capacities, goals, handouts or automatic knowledge of a secret |
| Interact with bound motion lectern by the Chancellor | Council motions | Upcoming motions, proposal, sponsorship and withdrawal where eligible | Does not open a vote or offer global remote submission |
| Chancellor opens an actual ballot | Standalone ballot popup | Current motion, options, threshold, own acknowledgement/change, visibility-specific information | No personal/house navigation, agenda editor or future ballot |
| Interact with separate bound treaty lectern | Treaty ceremony | Parties/terms, who is physically present, own signature, Chancellor sanctification | No remote signature, forced signature or automatic activation after signatures alone |
| Challenge another representative in person | Declaration popup | Actual target character/house, public reason, consequences, confirm/cancel | No global target list for remote war declaration and no target accept/veto button |
| Chancellor opens procedural controls | Council proceedings | Call/gathering, agenda item, open/close individual ballots, request regency, recess/resume/close | No universal political dashboard, house-war action or signing for a party |
| Existing Chronicle editor | Existing authoring screens | Content, bindings and rules in their canonical homes | Does not double as a player's political menu |

The house presentation is a view of political metadata linked to the existing character card, not a replacement identity system or compulsory Player Role per house. House profiles need authored public substance: what the house is known for, its lands/trade/influence and stated interests, rather than a representative's short biography. Private goals, capacities and knowledge remain in the existing private handout/draw path. No “Your handouts” top-level item remains.

The owner's wheat example is explicit: House Vale publicly exports large quantities of wheat; its assigned private capacity is reliable support for **three other houses**. Its representative may promise more through roleplay. Another house may independently receive knowledge of the same capacity, or nobody may. The owner is not told the exposure. The sample gives Rowan one report purely as one possible committed draw. Both owner information and the report render from one assigned fact, so they cannot disagree. This creates leverage and scope for bluffing, not a wheat ledger, automatic treaty validation, forced truthful promises or economic simulation. Show separate house goals and personal goals; knowledge of a capacity does not reveal another house's goals.

## Ballots and motions

The motion lectern gives a reason to approach the Chancellor. Players read upcoming motions or submit/sponsor there. The Chancellor approves the agenda and chooses when to open each vote. Opening emits the standalone ballot for each eligible representative. There is no ordinary player Council menu from which to start or browse ballots.

The ballot contains only the motion/question, public/secret indication, required support and options. **Once confirmed, the house's vote cannot be changed**, even while voting remains open. The confirmation says so; all options become read-only after submission. Duplicate/stale requests must preserve the first accepted choice. This supersedes the earlier brief/plan's replaceable-choice rule. A secret ballot never displays partial totals or other houses' choices, including to the Chancellor. Ineligible houses are not offered voting controls. Prototype final tally simulation has deliberately been removed; native code will compute the result.

The Chancellor does not wait for tardy representatives. Voting takes place in a bound Council voting Region; an absent/outside representative cannot cast remotely. **Pending owner clarification:** does the required majority use only representatives present when opening (five present → three required), or all Council houses with absent ones uncast (eight houses → five required)? The mockup suppresses numeric electorate/majority projections and shows an external review note until that answer; remaining internal sample counts are not a chosen policy. It does not establish the new denominator or late-arrival electorate policy. Freeze the chosen electorate/threshold at ballot opening once settled. Reconnect/absence must not recalculate a running ballot's majority.

Closing/dismissing a popup must not implicitly abstain. The prototype proposes a temporary active-vote reminder to return to the **same still-open** ballot, and shows it automatically when switching to an eligible sample actor after opening. **Dismissal/reopening, late join/reconnect presentation and interruption of another workstation remain review questions.** A closed vote cannot reopen through the reminder. No new notification behavior is considered accepted yet.

The Chancellor retains the existing hidden-complication gate: request regency → resolve this meeting's due Event → normal Council recess → resume → separately request regency again. Closing a meeting without regency also checks its due obligation. Recess keeps Core ACTIVE; technical pause remains in Session tools. The live complication panel omits explanatory text about roleplay/world pause. **Call meeting** stays visible during discussion, an open vote and recess; sounding its Event never automatically opens a vote or resets proceedings. No future complication appears in live controls. The separate example checkbox only inspects that path.

## Treaty ceremony

The owner adds a substantive rule: the parties and the Chancellor must be physically present at a separate treaty lectern, and the Chancellor sanctifies their deal. This supersedes the earlier assumption that all signatures alone activate a house treaty.

Flow: two or more representatives open the **same bound lectern** → Create treaty → choose a subset of those representatives and write terms → submit a new agreement → each party reads/signs for its own house → Chancellor reviews/sanctifies → active. The initiator is included; a single house cannot submit a treaty with itself. Revalidate the selected open-lectern sessions when submitting. The catalogue holds separate agreements; creating one never overwrites another. Private titles/details are filtered from outsiders. Required realm ratification remains additional; a witness cannot replace it. GM authority never signs on behalf of a house.

Show required attendance and missing people. Revalidate attendance, exact signed revision and eligibility at final commitment, not just while drawing buttons. Loss of presence suspends completion and shows whom to bring back. Do not silently fabricate approval, redraw terms, activate on reconnect, or delete signatures because of a UI refresh. Terms/party changes invalidate pending consent. Private terms are available only to named parties and GM; even an uninvolved user at the lectern gets no draft/title/content payload for a private agreement.

The former ambiguous “All required people present” toggle is removed. Review controls separately show **players with this treaty lectern open**, **Chancellor nearby**, **challenge target nearby**, and **current voter in the voting Region**, only in the relevant scene. Treaty open-menu membership is not a ballot quorum or voting gate. Exact witness proximity, continuous attendance, valid-signature retention on departure/rejoin, draft ownership/concurrent editing, amendment/expiry/disclosure and private-breach publication remain open. The sample retains signatures during missing attendance only to demonstrate a waiting state; that policy is not approved yet.

Breaking an active treaty remains possible; the mockup puts that action on its agreement at the treaty lectern. The latest feedback concerns formation and does not yet establish whether later breach/withdrawal also needs a witnessed ceremony. No extra requirement is silently imposed. House/public records are read-only and do not duplicate ceremony controls.

## In-person challenge

The proposed gesture is an empty-hand/item interaction with the actual representative, followed by a small declaration screen. The owner mentioned a stick or another challenge item but has not chosen one; the prototype simulates the interaction and installs no item or handler.

The screen identifies the target, requests the public reason and clearly says it declares house war. The initiator confirms; **the target has no veto**. Physical approach supplies drama, not mutual consent. Validate the actual character/house, authority and continued physical presence before commitment. Leaving/disconnecting before commitment refuses the action cleanly. A later departure does not undo an already recorded declaration.

War remains different from rebellion and a duel: loyal opponents keep votes and palace rights; no automatic PvP setting, army or winner. Existing non-aggression breaches are coordinated with the declaration without publishing private terms. Foreign war/peace remains a Council motion, not this gesture.

**Unsettled:** exact item/gesture, discoverability, distance/line of sight, multiple representatives and accessibility alternative with identical presence checks. Confirm these before native handlers/commands. A convenience command must not become a remote bypass.

## Authoring and ownership

Bind both lecterns through existing **Locations → Block sites** with distinct scenario subtypes; no new Lecterns tab, duplicate Region registry or separate ceremony editor in Game setup. Reuse the ordinary binding popup. Link existing Regions only when the reviewed attendance policy needs geometry. Geometry/private-room voice behavior remains independent.

The **map-binding sketch** now lists Locations → Block sites for the two lecterns, the Council voting Region, palace Region and safe outside Point. The old “Players” entry there was confusing: it meant linking a logged-in player to a character/house in the GM roster, not binding a player onto the map. It is removed from this sketch. Existing Players remains that separate roster operation; reusable house profiles, goals and handout pools are scenario content. Books & Lore, Content pools, Events and Game setup retain their canonical content/rule roles. Those authoring forms still need detailed review; unapproved earlier forms are references only.

Core continues to own generic card fields, block binding, widgets, pools and ballots. Last Council owns political authority/projections, lectern purpose, ceremony rules, challenges and proceedings. Share small components across these screens, not one giant navigation shell. Capability-gate scenario entries so Quiet Below does not acquire political interfaces. Reusable definitions and world-bound bindings/progress retain the agreed storage split.

Public history and the Chancellor's eventual conclusion report are distinct **uses** of records, not two nearly identical player menu tabs. A public read-only record popup is included under Houses; final conclusion is a later GM-only review flow, not an always-visible second copy. Record placement can be refined with the owner.

## Review and verification boundary

First review these entry points and the size/purpose of each screen. Additional verbal feedback can refine this draft; do not request blanket implementation approval while the owner is still clicking/talking. Resolve the marked presence, challenge and ballot presentation policies before their implementation. Source brief remains unchanged; this document records later clarifications.

Current refinement verification, 5 November: syntax PASS; local Edge smoke **45 checks PASS**, zero JavaScript exceptions. Checked the three personal entries, editable pronouns, private goals, richer public descriptions, owner/report fact equality, unexposed secrets, multiple treaty creation from same-lectern users, independent records/private title filtering, three-party creation, signatures and witness, final locked choices after reopening, separate presence examples, outside-Region casting refusal, visible Call meeting and succinct recess, unchanged motion flow and map bindings. Desktop 1280×1000 and compact 640×800 screenshots inspected. Small evidence stays under ignored `output/playwright/council-refined-*` and the refinement smoke script.

The preceding scene iteration passed 37 browser checks before these changes; that is historical evidence, not proof of the new immutable-vote rule. Independent review of this refinement found a misleading numerical electorate projection while its rule was pending; numbers are now omitted with an explicit review note. Source brief hash, package links and final diff checks remain required.

Independent read-only UI review caught two-party-only draft controls, sample profile/paper mismatch and an agenda-selection dead end; all corrected and exercised before the passing smoke. Initial browser-global helper-name collision was corrected. An initial test locator omitted a motion button's status suffix; the final run uses its actual accessible label. Package links, brief hash and diff checks also pass. The original prototype's HTML/CSS/JS are byte-identical to commit `97d8ea3`.

Browser sample state proves presentation/transitions only, never server authorization, actual physical presence, durable signatures, real random scheduling or networking. No Minecraft world, review server, client build or release artifact is created.
