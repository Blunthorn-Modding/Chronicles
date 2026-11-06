# Last Council: smaller interfaces tied to the scene

Status: **scene-based UI proposal, revised after owner review**. 5 November 2026. Replaces the combined-menu direction in [the first pass](UI_DESIGN.md); its HTML remains unchanged. The owner has approved this design and authorized native implementation. Current corrections: distinct My house information/secrets/house goals/known-other-house secrets, no generated personal goals or duplicate read controls; confirmed votes are final; the electorate is only eligible houses present at opening and stays fixed. Treaty creation and Call meeting remain as reviewed.

Active prototype: [last-council-scenes.html](../../design/last-council-scenes.html), served at `http://127.0.0.1:8925/last-council-scenes.html`. The external toolbar simulates approaching blocks, interacting with a character, opening a vote and switching people. These are review controls, not an in-game dashboard or permission switch. Changes are local sample data and are discarded by Reset/refresh.

## Owner's correction

The first prototype put too many different activities into one political menu. The owner explicitly prefers several small screens and physical interactions that bring players into the Council hall and face to face. Preserve the existing editor for authoring; do not turn its three-column layout into the universal player interface.

| Entry in the world | Screen | Contains | Excludes |
|---|---|---|---|
| Existing personal-menu access | Character / My house / Other houses, three tabs | Existing editable public card; own house and the public directory have separate entry points | No Council/treaty/conflict action tabs, no ballot browser |
| My house | Separate House information / Secrets / Goals / Other houses' secrets sections | House profile, own private facts, assigned house goals, explicitly displayed reports about other houses; related records and rebellion actions | No generated personal goals, duplicate read controls or indication of who knows your secrets |
| Other houses | Rich public directory | Reputation, economic/social role, known interests, public representative and candidacy | No private capacities, goals, handouts or automatic knowledge of a secret |
| Interact with bound motion lectern by the Chancellor | Council motions | Upcoming motions, proposal, sponsorship and withdrawal where eligible | Does not open a vote or offer global remote submission |
| Chancellor opens an actual ballot | Standalone ballot popup | Current motion, options, threshold, own acknowledgement/change, visibility-specific information | No personal/house navigation, agenda editor or future ballot |
| Interact with separate bound treaty lectern | Treaty ceremony | Parties/terms, who is physically present, own signature, Chancellor sanctification | No remote signature, forced signature or automatic activation after signatures alone |
| Challenge another representative in person | Declaration popup | Actual target character/house, public reason, consequences, confirm/cancel | No global target list for remote war declaration and no target accept/veto button |
| Chancellor opens procedural controls | Council proceedings | Call/gathering, agenda item, open/close individual ballots, request regency, recess/resume/close | No universal political dashboard, house-war action or signing for a party |
| Existing Chronicle editor | Existing authoring screens | Content, bindings and rules in their canonical homes | Does not double as a player's political menu |

The house presentation is a view of political metadata linked to the existing character card, not a replacement identity system or compulsory Player Role per house. House profiles need authored public substance: what the house is known for, its lands/trade/influence and stated interests, rather than a representative's short biography. Private goals, capacities and knowledge remain in the existing private handout/draw path. No “Your handouts” top-level item remains.

The owner's wheat example is explicit: House Vale publicly exports large quantities of wheat; its assigned secret capacity is reliable support for **three other houses**. Its representative may promise more through roleplay. Another house may independently receive the same capacity fact, or nobody may. The owner is not told the exposure. In this sample Vale sees capacity under Secrets; Rowan sees the identical report under Other houses' secrets. Other samples may assign no report. This creates leverage and bluffing, not a wheat ledger, enforced truthful promises or economic simulation. Goals contains **assigned house goals only**. Personal goals belong to the player and are removed from both this UI and generated goal content.

Each selected section renders its content once. House information shows the profile, Secrets shows own private facts, Goals shows house objectives, and Other houses' secrets shows only the knowledge issued to this character. There is no repeated Goals card plus Read goals button, no repeated public profile above every section, and no modal required merely to read content already selected.

## Ballots and motions

The motion lectern gives a reason to approach the Chancellor. Players read upcoming motions or submit/sponsor there. The Chancellor approves the agenda and chooses when to open each vote. Opening sends a notice to eligible representatives; they deliberately open the standalone ballot through a configurable hotkey or `/chronicle council vote`. There is no ordinary player Council menu from which to start or browse ballots.

The ballot contains only the motion/question, public/secret indication, required support and options. **Once confirmed, the house's vote cannot be changed**, even while voting remains open. The confirmation says so; all options become read-only after submission. Duplicate/stale requests must preserve the first accepted choice. This supersedes the earlier brief/plan's replaceable-choice rule. A secret ballot never displays partial totals or other houses' choices, including to the Chancellor. Ineligible houses are not offered voting controls. Prototype final tally simulation has deliberately been removed; native code will compute the result.

**Confirmed electorate rule:** capture distinct eligible houses with an online representative inside the bound voting Region when the ballot actually opens. Five present out of eight houses means N=5 and absolute majority3 (two-thirds4). That roster and threshold stay fixed. Absent-at-opening houses cannot join late; leaving/disconnecting does not reduce N or remove an accepted vote. A captured representative may return/reconnect and cast from the Region if not already voted. Reopening a dismissed popup never takes another snapshot. The Chancellor does not wait for missing representatives. An empty electorate refuses opening rather than auto-passing or deciding the throne.

The review controls now show eight sample houses' live Region presence; five are initially checked. Four house profiles are illustrated in the personal menu; the four extra names are sample voters for the electorate example. Product ballot/GM screens show the restored **3 of 5** threshold, which never changes as presence is toggled after opening. Ballot display, active reminder and submission all consult the captured roster, while submission also checks live Region presence. The old pending-question note is removed.

Closing/dismissing a popup never abstains. Confirmed access is a configurable **Open current ballot** key (default R) and `/chronicle council vote`. A new ballot does not replace a workstation screen. The key works from another screen when a current ballot is known, closes containers through their normal close path, and respects text entry and editor discard guards. Closed/void ballots clear the active notice. Reconnect restores access to the same fixed ballot, without adding late arrivals. A technical void requires a public reason and preserves the original choices; replacement uses a new ID and electorate. A voided regency can be replaced in the same Council meeting without changing its slate or complication history; only a completed regency consumes the meeting's succession slot.

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

Current verification, 5 November: syntax PASS; local Edge smoke **36 checks PASS**, zero JavaScript exceptions. Covers the four separate My house content sections, no duplicate read controls or generated personal goals, same owner/report fact, no public secret leak; five-of-eight electorate3, departures/reentry, immutable choice after reopen, late-arrival refusal, new snapshot only on a later ballot, empty-electorate refusal, two-thirds4 and secret regency projections. Desktop 1280×1000 and compact 640×800 screenshots inspected. Small evidence stays under ignored `output/playwright/council-distinct-*` and `council-sections-electorate-smoke.js`. Independent read-only UI review found no material continuity/privacy issue. No native/server guarantee is claimed by these browser checks.

Prior iterations' 37 and 45 passing browser checks are historical evidence. The former pending electorate choice is now settled and numerical thresholds restored; the new 36-check run is the evidence for that policy and the separated house sections. Source brief hash, package links and final diff checks remain required.

Independent read-only UI review caught two-party-only draft controls, sample profile/paper mismatch and an agenda-selection dead end; all corrected and exercised before the passing smoke. Initial browser-global helper-name collision was corrected. An initial test locator omitted a motion button's status suffix; the final run uses its actual accessible label. Package links, brief hash and diff checks also pass. The original prototype's HTML/CSS/JS are byte-identical to commit `97d8ea3`.

Browser sample state proves presentation/transitions only, never server authorization, actual physical presence, durable signatures, real random scheduling or networking. No Minecraft world, review server, client build or release artifact is created.
