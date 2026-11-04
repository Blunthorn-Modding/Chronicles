# The Last Council
## Event Design & Functional Requirements

**Document status:** Gameplay specification — revision 1.1  
**Date:** 2 November 2026  
**Format:** One-evening, session-based Minecraft political roleplay  
**Suggested group:** 6–12 players; all important counts and thresholds adjustable  
**Typical duration:** 3–4 hours, depending on Council scheduling  
**Host style:** GM-led pacing; player-directed political outcomes  
**Existing foundation:** Chronicle is already in development and provides established features such as player roles/character information and lore books. This document describes **The Last Council's event-specific experience and rules**, not how to implement or restructure Chronicle.

> **Core premise:** The King has died without an acknowledged successor. Noble houses gather at a heavily guarded royal palace to govern during the interregnum and, if possible, elect a new ruler. Players pursue their own ambitions through diplomacy, formal decisions, secrets, treaties, wars, and rebellion. All meaningful outcomes are determined by player choices; the systems formalize political consequences without turning the event into a PvP or army-management game.

---

## 1. Vision and player experience

The Last Council is a **contained political-intrigue one-shot**, not a traditional Minecraft minigame, combat event, or RPG questline. Most play should happen through proximity-voice conversations, private negotiations, Council debates, bargaining, accusations, and dramatic public decisions. The game systems give those scenes permanence and stakes.

**What a successful evening feels like:**

- Every house has something it wants, something it fears, and reasons to approach other houses.
- Some participants run for the crown; others attempt to shape the kingdom without becoming ruler.
- Participants can secure laws, agreements, and diplomatic outcomes **before a ruler is chosen**—possibly achieving all their personal goals even if no one is crowned.
- Players can submit their own political proposals, not merely respond to a fixed host script.
- Consequential actions have clear, enforceable procedures: a vote is counted; a treaty is signed; a war is declared; rebels lose their Council seats and access to the palace.
- The GM introduces complications and maintains pace, but does not dictate allegiances, ballots, betrayals, or the ending.

**Not part of the event:** military resources, troop recruitment, battle simulation, automatic war winners, PvP-driven objectives, NPC diplomacy, mandatory survival grinding, or automated scoring of personal objectives.

---

## 2. Setting and game space

### 2.1 The interregnum

The monarch died without an acknowledged heir. Under an established **Interregnum Charter**, each participating noble house holds a Council seat. Until a monarch is elected, the Council can still pass decrees, make diplomatic decisions, and address emergencies. Passed measures remain politically valid even if succession fails.

The royal palace is a **neutral, heavily guarded meeting place**. Noble houses can be at war with one another while their representatives safely negotiate inside. **Rebellion is different:** a rebel formally repudiates the Council's authority and forfeits access to its proceedings and palace.

The kingdom, houses, foreign adversaries, family histories, and specific lore can be adapted for each event. There should be no predetermined rightful winner.

### 2.2 Map spaces

| Location | Role in the event |
|---|---|
| Council / throne chamber | Formal sessions, speeches, public deliberation and ballots. |
| Banquet hall | A communal social scene, drinks, suspicion and possible poisoning. |
| Private rooms | Secret negotiations, treaties and confrontations. |
| Gardens, corridors, courtyard | Unscheduled encounters and discreet conversations. |
| Guarded palace perimeter | The boundary from which rebels are excluded. |
| Outside-palace meeting point | A place for rebels to continue influencing events and receive visitors. |

Use the group's existing **Simple Voice Chat** setup. The event must permit several simultaneous conversations; it must not funnel everyone through a single scripted conversation or NPC.

---

## 3. Participants, noble houses and characters

### 3.1 House participation

**Default:** one player represents one house; every active Council house has **one vote**. A participant's eligibility is derived from their house's Council status, not whether they are running for the throne.

Each house needs an identity and political standing, and can have an ambition, secret, source of leverage, relationship, and claim to the throne. Those narrative elements are prepared for the session and attached to the **existing Chronicle role/character facilities**. Do **not** create a competing role-card, character dossier, or lore-book subsystem solely for The Last Council.

For this event, only the following additional *political* distinctions matter:

- **Council house:** May participate in Council votes, propose/sponsor motions and enter the palace.
- **Crown candidate:** Council house currently eligible to receive succession votes. A subset of houses only.
- **Eliminated candidate:** No longer eligible for the crown, but **still a full Council voter** unless separately removed by rebellion.
- **Rebel house:** No Council vote or Council privileges, and excluded from the guarded palace; remains in the game as a political actor.
- **Crowned house:** Holds the elected title. No automatic extra vote, veto, command of players, or arbitrary power to expel other houses.

A roster should be settled before formal voting begins. House membership and identity should remain understandable to participants. Secrets and individual goals remain private unless characters reveal them.

### 3.2 Goals and role design

Use **curated, interconnected objectives**, assigned randomly or by the host using existing Chronicle tools. Avoid blindly randomized goals that may be unachievable or unrelated.

Each role ideally has:

1. A public identity and position.
2. A primary private ambition.
3. One or two secondary objectives that offer alternative routes to success.
4. A secret, vulnerability, or disputed fact.
5. Leverage: a letter, debt, claim, witness, document, or political promise.
6. Reasons to interact with **at least two other houses**.

Goals should include examples such as securing rights to mines, blocking a foreign war, recognizing an old charter, forming an alliance, preventing a particular claimant from winning, or provoking two rivals into a house war. **A crown winner is not required for these objectives to count as meaningful.** Chronicle can deliver relevant documents through its existing lore-book features.

### 3.3 Candidates

The **number and identities of crown candidates are configurable** according to player count. Around 2–4 is a useful starting range, not a hard-coded limit. Candidates may be predetermined or established in a nomination phase **before the first succession ballot**. Once that first ballot opens, the candidate slate is locked and can only shrink through elimination, withdrawal, or loss of eligibility; it does not gain new candidates mid-election without an explicit host reset.

---

## 4. Event rhythm and Council meetings

### 4.1 One evening, several political phases

The default rhythm is **one formal Council meeting approximately every hour**, with freeform RP between meetings. The host controls timing and can shorten or extend an intermission if the roleplay needs it. The event must not automatically interrupt a scene just because a timer reached zero.

An individual Council meeting can contain **multiple ordinary motions**, but **no more than one succession ballot**. Succession votes do not immediately repeat within the same meeting.

Example: **eight players, three candidates, three Council meetings**.

| Approx. event time | Segment | Main opportunities |
|---|---|---|
| 0:00–0:50 | Arrival, introductions, negotiation | Secret objectives, offers, drafts, early treaties. |
| 0:50–1:10 | Council 1 | Prepared and player-submitted policies; succession not yet on the agenda by default. |
| 1:10–1:50 | Free RP and banquet | Bargaining, drinks, crisis, house politics. |
| 1:50–2:10 | Council 2 | Further motions; **succession ballot 1**. |
| 2:10–2:50 | Final negotiations | Coalition changes, war/rebellion if chosen. |
| 2:50–3:10 | Council 3 | Further motions; **succession ballot 2**, final if only two candidates remain. |
| 3:10–3:30 | Epilogue | Announce consequences; character resolutions; OOC debrief if desired. |

For **four initial candidates**, the event normally needs as many as three separate succession meetings. Start succession earlier or schedule an additional meeting. For two candidates, a single ballot may decide the crown or leave it vacant. The host chooses the number of meetings to fit the evening; the system should make any mismatch clear beforehand.

### 4.2 Agenda rules

- The host may prepare motions in advance and decide which items appear at each meeting.
- Players may propose topics before or between meetings. Valid sponsored proposals enter an agenda queue.
- The host publishes the agenda and opens/closes each formal vote, with optional voting windows.
- The host may defer a motion but should give a procedural reason rather than silently burying player proposals.
- Meetings must remain playable if the crown has already been elected or if succession is declared vacant.
- The event can be paused for technical problems and resumed without inventing results.

---

## 5. Council motions and voting

### 5.1 Player-proposed and prepared motions

Every active Council house can submit a motion containing:

- **Title and description:** What is being proposed, in characters' own words.
- **Category:** Policy/decree, advisory resolution, foreign war/peace, rebel reinstatement, or another supported formal action.
- **Vote visibility:** Public or secret where the category permits it.
- **Options:** Normally Yes / No / Abstain; advisory resolutions may use several named alternatives.
- **Relevant parties or targets:** Where the motion has a defined mechanical consequence.

**Default proposal path:** Submitted → sponsored by **one other Council house** → placed on the agenda by the host → debated → voted on → recorded as passed, rejected, or inconclusive. Sponsorship count should be configurable. Host-prepared and emergency motions can bypass sponsorship. A proposal can be withdrawn before its vote begins.

A free-text decree has an **official political record and narrative weight**, but does not automatically alter arbitrary game rules. Where there is a defined action—such as declaring foreign war or restoring Council seats—use a specific, clearly labelled motion category so the effect is predictable.

### 5.2 Public and secret ballots

| Feature | Public ballot | Secret ballot |
|---|---|---|
| Choice submitted through a real in-game voting interaction | Yes | Yes |
| Voter's named choice visible to others | Yes | No |
| Live partial totals | Can be public | **Not shown** |
| Final result | Totals and named votes | Totals only; no voter-to-choice breakdown |
| Vote changes before closing | Allowed | Allowed |
| Abstention | Allowed | Allowed |

**Succession votes are always secret.** Ordinary motion voting visibility may be selected by the host or proposal rules. Secret voting must be genuinely private from other participants in the normal event interface; the host must not get a convenient reveal-all button. Ordinary host moderation of exceptional technical problems is separate from public ballot reporting.

### 5.3 Vote counting

Default rule: **one vote per active Council house**.

- At the moment a ballot **opens**, its eligible Council electorate is fixed for that ballot.
- If there are `N` eligible houses, an **absolute majority** means `floor(N / 2) + 1` affirmative votes (or votes for one candidate/option).
- A **two-thirds majority** means `ceil(2N / 3)` affirmative votes.
- Abstentions and uncast votes **still count in `N`**. A narrow majority of ballots cast is not sufficient.
- Unless otherwise configured, normal decrees need an absolute majority; declarations of war against external kingdoms and rebel reinstatement need two-thirds.
- Ties or insufficient support mean the motion does not pass. There is no automatic host casting vote.
- A player may change their own vote while the ballot is open. Only the final selection counts.

A disconnected representative does **not** silently stop counting as a Council seat. The host can pause for genuine connection issues; any formal removal of a seat must occur **before a later ballot begins** and be made explicit.

**Membership changes are suspended while a ballot is open** (including rebellion, joining a rebellion and reinstatement). They take effect between ballots so a running electorate never changes halfway through a count.

### 5.4 Kinds of Council decision

| Type | Default voting rule | Political result |
|---|---|---|
| General policy / decree | Absolute majority | Officially enacted decision, respected in subsequent proceedings. |
| Advisory resolution | Set options and declared threshold | Records the Council's preferred direction, not an automatic law. |
| External war | Two-thirds | Realm formally enters war with a named outside realm. |
| External peace / armistice | Configurable; default absolute majority | Diplomatic state formally changes. |
| Rebel reinstatement | Two-thirds | Named houses regain Council rights after the approving ballot closes. |
| Kingdom-wide treaty ratification | Configurable; default absolute majority | Council approval of a treaty that purports to bind the realm. |
| Succession | Special secret ballot | Applies the election procedure in §6. |

---

## 6. Succession election — the central constitutional rule

**Do not replace this with instant-runoff voting or multiple immediate ballots.** The intention is **ideally one successful succession vote**. Elimination is a fallback that unfolds across later Council sessions.

### 6.1 Procedure

1. A configurable slate of **at least two crown candidates** is finalized before the first succession vote.
2. During a scheduled Council meeting, all currently eligible Council houses—including the candidates themselves and candidates previously eliminated—receive **one secret vote** for a remaining candidate or may abstain.
3. If a candidate receives an **absolute majority of all eligible Council seats**, they are crowned immediately. No further succession ballots are held.
4. If no one receives a majority and **more than two candidates** remain, **exactly one candidate with the fewest votes is eliminated**.
5. Further negotiation happens. At a **later Council meeting**, the remaining candidates face a new secret ballot.
6. If **exactly two candidates** remain and neither achieves an absolute majority, succession **ends without a ruler**. Do not hold endless votes or award the throne based on a mere plurality.
7. Eliminated candidates remain full Council members and may vote on policies and later succession ballots, unless they rebel.

### 6.2 Ties and eligibility changes

- If multiple candidates tie for **last place** with more than two remaining, use an announced **random draw among those tied for last** to remove exactly one candidate. The draw may eliminate a candidate, **never crown one**. Record the result so it cannot be quietly changed.
- If a candidate **rebels** between ballots, they immediately lose their seat and candidacy; remove them from the remaining candidate list for future ballots.
- If candidates voluntarily withdraw between ballots, they likewise leave the slate but normally retain their Council seat.
- If these changes leave **one** candidate, hold a **Yes / No / Abstain confirmation vote at the next scheduled succession meeting**. The candidate still needs an absolute majority; otherwise the throne remains vacant.
- If **no** candidates or no active Council seats remain, succession cannot continue and the throne remains vacant.
- If the evening ends before necessary ballots can occur, describe succession as **unresolved / no monarch elected**, not as a win by the leading candidate.
- If a crowned ruler later needs to abdicate or be deposed, that is a separately moderated political crisis, not an ordinary rebellion click that produces contradictory crown status.

### 6.3 Worked examples

| Situation | Outcome |
|---|---|
| 8 eligible houses, 3 candidates: **4 / 3 / 1** | Nobody has the necessary **5**. Eliminate the candidate with 1. Next ballot is in a later session. |
| Next session, 2 candidates: **4 / 4** | Neither has 5. **No ruler is elected.** |
| 8 houses, 2 candidates: **5 / 3** | Candidate with 5 is crowned at once. |
| 9 houses, 3 candidates: **5 / 2 / 2** | Candidate with 5 is crowned at once. No tie draw needed. |
| 8 houses, 4 candidates: **4 / 2 / 1 / 1** | No majority. Public draw eliminates one of the tied candidates with 1 vote. |
| 8 houses, then 3 rebel before the next ballot | Next ballot has **5 eligible houses** and needs **3** for an absolute majority. Rebels cannot vote. |

---

## 7. Treaties and formal diplomacy

A treaty is a **real agreement within the event**, with identifiable signatories, terms, status, and history. It must not be reduced to casual chat promises, although the negotiations leading to it are entirely freeform.

### 7.1 Types

- **Alliance:** General political cooperation.
- **Non-aggression pact:** Promise not to start a house war against the other signatories.
- **Mutual defense pact:** Promise of support if a signatory is attacked.
- **Trade / rights agreement:** Mining concessions, tax privileges, access rights, debts and similar political commitments; **no simulated resource economy**.
- **General written treaty:** Freeform negotiated commitments.
- **Peace / armistice:** A negotiated end or suspension of a specific house conflict or other hostility.

### 7.2 Signing and visibility

- Any eligible house can draft an agreement, specify its parties and terms, and offer it for signature.
- All required signatories must explicitly accept before the treaty becomes **active**.
- Players can read the full terms before signing; no one signs on another player's behalf through ordinary play.
- An agreement may be **public or private**. Private agreements are visible to signatories and the GM until someone chooses to reveal them.
- A party may **break** a treaty. The breach becomes part of the political record and may notify the affected parties; betrayal is permitted, not blocked.
- A war against a non-aggression signatory can be flagged as a breach, but the war still proceeds.
- A defense pact can notify allies of war, but **does not automatically force them to join**.
- A promise to vote for a candidate **must never mechanically force that vote**; secret betrayal is part of the game.

Treaties between individual houses require signatures, **not** a Council vote. Agreements claiming to bind the whole kingdom require Council ratification through the applicable formal motion.

---

## 8. War, civil war, external war and rebellion

These concepts are **different political actions**, with different consequences. They do not automatically start Minecraft fighting.

### 8.1 House war

- One house can formally declare war on another house without Council permission.
- The declaration has a named initiator, target, public statement/reason, conflict status and sides.
- Other houses may choose to join **either side**. No one joins automatically because of a treaty.
- A house cannot support both sides of the **same** war simultaneously.
- Peace, ceasefire, voluntary withdrawal and changed allegiances can be acknowledged through explicit actions and agreements.
- **All loyal Council houses remain Council members and keep palace access**, even when openly at war with each other.
- A house war does not turn PvP on or need a mechanical battlefield, winner or settlement calculation.

### 8.2 Civil war classification

A house war is designated a **civil war** once a configured participation threshold is reached—for example, **four houses total across two opposing sides**. This is a political classification, useful for announcements and complications, not a new battle simulator.

- Council rights are unaffected by becoming a party to a civil war.
- The classification can change if the number of participating houses changes.
- A civil war can exist **without rebellion**. Rebellion only happens when a house explicitly repudiates the Council/Crown.

### 8.3 External war

Foreign kingdoms are **named political counterparts**, represented through lore, letters, announcements and GM decisions; no NPC faction simulation is needed.

- A declaration of war **by the kingdom** against an external realm requires a formal Council motion.
- Default threshold: **two-thirds of eligible Council houses**.
- Passing records the realm's official diplomatic state as **at war** with the named opponent. Failing makes no formal change.
- Peace or armistice can likewise be proposed and voted on.
- The GM narrates reactions from the other realm, but neither military resources nor automatic war results are tracked.
- A particular noble's private desire for war is not the same as the entire realm officially declaring it.

### 8.4 Rebellion — irrevocable without a settlement

A rebellion is a public rejection of the Council's or Crown's authority. It is a **much more serious act** than a house war and **does not require Council permission**.

**Declaration:**

1. A Council house declares rebellion and supplies a short public statement of demands or grievances.
2. Before confirming, the house sees an unmistakable warning: **"You will immediately lose your Council seat and access to the guarded palace. You will no longer be able to vote, propose or sponsor motions, or participate in the succession."**
3. On confirmation, its rebellion is publicly announced. Its Council privileges cease, and it is excluded from the palace.
4. Other houses can **join that rebellion** voluntarily. Joining requires the same warning, confirmation and consequences.
5. A separate house may found a separate rebellion; each declaration has an identifiable faction and supporters.

**Effects:**

- Rebel houses cannot vote, sponsor motions, put matters on the Council agenda, or nominate/receive succession votes.
- Rebel crown candidates become ineligible for **future** succession ballots.
- Rebel houses cannot simply reverse their declaration at will.
- **Rebel players remain active participants**: they can meet loyal nobles outside, form agreements, communicate through intermediaries and pursue political outcomes.
- Their characters are not killed, kicked, made spectators or automatically defeated.

**Ending rebellion:** The Council can approve a negotiated peace and **formal reinstatement** of specified houses, normally requiring a **two-thirds majority**. Reinstatement restores Council participation and palace access **after that vote has closed**. If no Council exists, a clearly announced GM-mediated settlement is the fallback.

### 8.5 The palace is protected neutral ground

- Host-defined palace interiors and the Council chamber are safe/guarded political space.
- Rival houses at war may still enter, converse and vote.
- Rebels are **removed from the palace when they rebel** and cannot re-enter by ordinary means.
- There must be a safe, designated location **outside the palace** for rebels to continue playing.
- Any special visit by a rebel, such as supervised negotiation, is an explicit host exception and **does not restore voting rights**.
- No action under war or rebellion automatically enables PvP, spawns troops, destroys the map or determines who wins militarily.

---

## 9. Banquet, drink and poison intrigue

The banquet is intended to create social opportunities, suspicion, and possible political complications. **Do not add an obligatory food or thirst survival system.**

### 9.1 Desired mechanics

- **Drinks** can be consumed even when a player's Minecraft hunger bar is full.
- An ordinary drink and a poisoned drink look identical to an uninformed participant in normal play.
- A limited **poison dose** can be used to contaminate a particular drink through a clear in-game action.
- Symptoms develop **after a delay**, providing opportunities to roleplay suspicion, care, accusations and investigation.
- A limited **antidote** can counter or shorten the fictional illness.
- Poison is **non-lethal by default**. It must not instantly remove a player from the evening.
- Illness **does not remove voting rights by default**; poisoning a rival must not be an easy way to control the electorate.

The identity of the person who poisoned a drink should not be broadcast to other players. The GM may have access to private moderation records. A physician-type role is optional, not required to use the system.

The banquet can also be played without poisoning if the group prefers political intrigue through other means.

---

## 10. GM complications and evolving narrative

Prepare **several possible complications** before the event. The GM chooses when to introduce one; none is mandatory merely because a timer expires. A good complication adds political pressure while leaving players free to respond.

| Event | Dramatic purpose | Possible formal consequences |
|---|---|---|
| **The Lost Testament** | A document may suggest the late King left disputed succession instructions. | Evidence, argument and a motion; do not insert a new candidate into an active elimination sequence without openly resetting it. |
| **The Northern Ultimatum** | A neighbouring kingdom demands concessions. | Emergency foreign-policy motion or formal war proposal. |
| **The Poisoned Chalice** | A noble falls ill during the feast. | Accusations, investigation, negotiations; may be player-caused or a prepared non-lethal event. |
| **The Empty Treasury** | The realm's obligations become impossible to meet. | Competing decrees or rival demands; no numeric economy required. |
| **The Missing Royal Seal** | The validity of a treaty or decree is questioned. | Evidence, legal dispute, possible new Council motion. |

Complications may be **public**, **private**, or revealed to particular houses. Use the Chronicle features already available for distributing private lore, letters and books; The Last Council only needs to identify which information and political actions are tied to a scenario.

The host may introduce a new fact or crisis, but should not retroactively change a secret vote, force a treaty signature, or decide a rebellion's success without player involvement.

---

## 11. Expected player interactions and host controls

This section specifies **what participants must be able to do**, not UI frameworks, command APIs or software architecture.

### 11.1 For players

Players should have clear, discoverable in-game access to:

- Current Council phase and next scheduled meeting.
- Council agenda, motions and available votes.
- Public or private voting as applicable, including confirmation of their own selection.
- Succession candidates, elimination history, next ballot and eventual crown status.
- Their own motion submission and sponsorship actions.
- Treaties: propose, review, sign, disclose or break an agreement.
- Conflicts: declare a house war, join a side, view participating houses, propose peace.
- Rebellion: declare or join with an explicit consequences warning.
- Political history: enacted decrees, official war declarations, publicly known treaties and other major outcomes.

A rebel should still see public information and their own agreements, conflicts and messages, but **Council-only actions must be unavailable with a clear explanation**.

Existing Chronicle character-role and lore-book interfaces should be **reused**, not duplicated. A dedicated Council interface is appropriate for the event's new political actions. The exact GUI navigation, commands and visual presentation are implementation decisions for the existing project.

### 11.2 For the GM

The host should be able to:

- Set participating houses, eligible crown candidates, meeting schedule and political rules.
- Prepare motions, choose the agenda, approve valid player proposals and open/close votes.
- Determine public/secret ballot mode where allowed and see result summaries without exposing private votes to players.
- Activate complications, distribute announcements and trigger any associated prepared motions.
- See public treaty/conflict status, administer a legitimate peace/reinstatement process and correct procedural mistakes transparently.
- Mark palace boundaries and a safe rebel meeting point.
- Pause/resume an event and record exceptional host interventions.
- End with an accurate political summary, even when there is no ruler.

Host controls are **procedural**, not a license to secretly rewrite the electorate or vote totals.

---

## 12. Configurable event rules

The event must be adaptable without rewriting the story mechanics. These are **functional options**; their data representation is left to the existing Chronicle project.

| Setting | Suggested default / requirement |
|---|---|
| Number of players/houses | 6–12 suggested; configurable. |
| Votes per house | One. |
| Crown candidates | Configurable set/count; 2–4 suggested. |
| Council cadence | Approximately every 60 minutes, host-controlled. |
| Succession ballots per Council meeting | **Maximum one.** |
| Candidate-elimination rule | Eliminate **one** lowest candidate after an unsuccessful non-final ballot. |
| Final two with no majority | **Vacant throne.** |
| Ordinary policy majority | Absolute majority of eligible Council houses. |
| External war majority | Two-thirds. |
| Rebel reinstatement majority | Two-thirds. |
| Sponsorships for player motions | One other house by default. |
| Public/secret voting | Both supported; succession always secret. |
| Vote changes | Allowed before close. |
| Civil-war classification | Four participating houses across opposing sides, adjustable. |
| Rebellion | Immediate loss of Council seat and palace access. |
| Poison | Optional, delayed, non-lethal; no automatic vote exclusion. |
| Foreign realms | Host-defined named realms and diplomatic states. |
| Complications | Prepared pool, triggered manually by host. |
| Palace exclusion and rebel gathering point | Designated to match the chosen map. |

A configuration should make it **obvious when the planned number of Council meetings cannot accommodate the maximum succession ballots** for the initial candidate count. The system must never secretly change the election rules to squeeze the crown decision into the event's remaining time.

---

## 13. Example eight-house scenario

These are **illustrative scenario ideas**, not hard-coded character roles. They can be authored using Chronicle's existing character and lore features.

| House | Public position | Private objective or leverage idea |
|---|---|---|
| **Valemont** | Strong-monarchy claimant. | Candidate; seeks Ashford's support through promises of mining privileges. |
| **Blackwood** | Powerful frontier house. | Candidate; wants war against Northmarch, has evidence of secret dealings. |
| **Greyhaven** | Wealthy coastal house. | Candidate; seeks commercial rights before succession, conceals significant debts. |
| **Ashford** | Controls important mines. | Wants exclusive mining rights, holds a document casting doubt on a claimant. |
| **Northmere** | Border house exposed to foreign pressure. | Wants to prevent external war and secure protection regardless of monarch. |
| **Redwyck** | Traditionalist house with grievances. | Wants an old privilege restored; may turn to rebellion. |
| **Thornfield** | Agricultural estates. | Seeks an emergency grain policy, knows someone concealed a shortage. |
| **Wycliffe** | Royal archivists. | Seeks recognition of an old charter, possesses part of a disputed testament. |

**Prepared motions that might open the evening:**

1. **Emergency Grain Reserves** — Make Crown grain available to vulnerable provinces.
2. **Southern Mining Charter** — Grant or reject a monopoly over valuable mines.
3. **Northern Border Policy** — Make concessions, strengthen defenses, or issue a diplomatic ultimatum.
4. **Recognition of the Old Charter** — Determine whether an old political privilege remains law.
5. **Succession Ballot** — Scheduled separately according to candidate count and the rules in §6.

The sample deliberately creates several non-succession pathways to achievement. The GM should adapt objectives until every house has a reason to meet multiple others, a meaningful choice, and at least one goal independent of crowning a ruler.

---

## 14. Rules examples and acceptance scenarios

These are **observable gameplay expectations**, not an implementation plan.

### Elections and Council

- With 8 voting Council houses, 3 candidates and votes **4/3/1**, no monarch is crowned; the candidate with 1 is eliminated. No new succession vote can open until a later Council meeting.
- With 8 houses, 2 candidates and votes **4/4**, no monarch is crowned. With **5/3**, the candidate with 5 is crowned.
- With 8 houses, 4 candidates and votes **4/2/1/1**, one tied last-place candidate is eliminated by the announced draw.
- A candidate eliminated from the succession can still vote on laws and on the next succession ballot.
- Abstentions and non-votes do not lower the majority threshold.
- Two succession ballots in a single Council meeting are not allowed.
- Prepared and player-sponsored motions can both be debated, voted upon and recorded.
- Public ballots show named choices; secret ballots do not reveal individual choices or live totals.

### Treaties and conflict

- A treaty requires the intended signatories' consent; a broken treaty remains visible in its permitted history.
- Breaking a non-aggression treaty to declare war is possible and recorded as a breach.
- A house war does not remove either house from the Council or palace.
- More houses joining a house war can cause it to be classified as a civil war, without treating participants as rebels.
- Foreign war requires the specified Council majority and can be refused.
- Rebellion by **one** house is sufficient to establish a rebel faction; others can join it.
- A rebel immediately loses Council privileges and guarded-palace access but can continue roleplaying outside.
- When three of eight houses rebel between ballots, the **next** ballot counts five Council seats and needs three votes for an absolute majority.
- Reinstatement is a formal political act, not a free "leave rebellion" button.

### Banquet and host direction

- Players can consume drinks at full hunger; limited poisoning causes delayed, non-lethal effects.
- Poisoning doesn't publicly identify its perpetrator or automatically suppress a Council vote.
- The host can introduce a crisis and an emergency motion, without overriding existing vote results.
- An ending with no crowned ruler is a **valid, fully supported conclusion**.

---

## 15. Host checklist and ending

### Before the session

- Select and confirm attendees, houses and crown candidates.
- Use Chronicle's existing role tools to prepare private objectives, secrets and lore documents.
- Ensure every house has several opportunities for interaction and a non-crown path to influence.
- Set the Council agenda, meeting schedule, thresholds and host-triggered complications.
- Establish guarded palace space and a safe outdoor meeting point for rebels.
- Explain the **rebellion penalty**, succession majority, abstentions, public/private votes and Council rhythm before play.
- Rehearse one public vote, secret vote, treaty and rebellion with test participants.

### During the session

- Run introductions and let negotiations unfold without overdirecting them.
- Publish agendas, allow proposals and respect the scheduled Council cadence.
- Allow time for speeches and private conversations between formal decisions.
- Introduce a complication if the story benefits, not merely because one is ready.
- Enforce political commitments and rebellion consequences consistently.
- Resolve technical interruptions openly; do not alter rules retroactively.

### At the end

- Establish the succession result: **crowned**, **vacant after final ballot**, or **unresolved without a monarch**.
- Announce enacted decrees, relevant treaties, known betrayals, house/civil/external wars and rebellions.
- Narrate what these decisions mean for the kingdom, without inventing an army victor.
- Allow a short character epilogue and optional out-of-character discussion.
- Judge the event as a **roleplay experience**, not a scoreboard of personal mission completions.

---

## 16. Boundaries for the existing Chronicle project

This document intentionally **does not specify**:

- A technical architecture, package/module structure, service diagram, state serialization format or networking implementation.
- A development roadmap, milestone order, agent bootstrap steps, build tooling, or framework/library choices.
- A second system for player roles, character sheets, role assignment, secrets, or lore books where Chronicle already supplies them.
- Military-resource systems, automated warfare, tactical combat or compulsory Minecraft PvP.

It **does specify** the rules and player-facing consequences that The Last Council needs to support: formal Council procedures, real public and private ballots, the configurable progressive-elimination succession, treaties, political conflict and rebellion, palace exclusion, optional banquet intrigue, and GM-directed complications.

**Implementation interpretation:** The existing Chronicle codebase is the authority for how these requirements are integrated. Where Chronicle already offers a capability, reuse it. Where this brief introduces a new political mechanic, implement its observable behavior without unnecessarily recreating existing character or lore functionality.
