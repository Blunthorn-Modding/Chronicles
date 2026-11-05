# Clarified character setup and Council flow

User clarifications, 4 November 2026. These amend the implementation interpretation of [the unchanged brief](../../THE_LAST_COUNCIL_EVENT_BRIEF.md): use existing character cards plus randomized lore pools, and use existing Events to call gatherings before the Chancellor conducts votes. **The Chancellor is the GM**, not a separate player office requiring a new delegation system.

This remains a design/implementation plan. No randomizer, Council controls or political mechanics have been implemented yet.

Further clarification on 4 November: cross-house secret exposure is independently random, with no requirement that every secret be known by someone else. Reuse refers to genuinely shared workflows or services, not a reason to describe every new mechanic as an existing feature. Room acoustics through Simple Voice Chat is a new shared integration to investigate in [LC-10](LC-10-room-voice.md).

Latest complication rule: [reusable random Event pools](RANDOM_COMPLICATIONS.md) surprise the GM as well as players. A configurable total is distributed across the evening's Council meetings. Each arrives before its own meeting's regency vote; a late arrival requires a Council recess while Core play stays active. This supersedes manual crisis selection/timing. Character cast preview does not reveal future complications.

## Character cards and randomized lore

Keep Core's existing character card as the public character surface and Player Roles for assignment, starting gear and destinations where needed. Add reusable assignment pools to the existing book/role workflow, supporting card-bound handout delivery when no role is assigned. Do not require an organizer to fill out a second house dossier, create a separate role solely to hold each randomized house, or maintain another character catalogue.

The current card contains public name, pronouns and description; `PlayerRole.books` currently contains fixed book IDs. Random pools, template substitution and connected cross-character facts are **new opt-in Core capabilities**, not features already supported by that fixed list. Existing fixed assignments, including Quiet Below's, must keep their current behavior.

The prepared content can include a large list of house information, personal/house secrets, goals, relationships and information known about other houses. Pool sizes, quantities, weights, allowed repetition and eligibility constraints are configurable. Authored text/templates supply the material; an external AI generator is not required.

Proposed preparation flow:

1. Select the participating characters through the existing assignment tools. Keep stable assignment/house identifiers behind the cards for political authority.
2. Draw compatible public house information and facts/secrets for those assignments.
3. Draw goals against the actual participating houses and configured political options. Opposing goals between players are desirable; validation checks that a target/mechanic exists, not that everyone can win together.
4. Independently roll whether other eligible houses learn each secret, using configurable chances. **Zero, one or several other houses may know it.** Do not impose full secret coverage, reciprocal knowledge, or minimum book quotas that indirectly force someone to know every secret. Successful knowledge draws refer to facts actually assigned to their targets; deliberately uncertain rumors must be authored as such.
5. Build each character's private book bundle from existing Books & Lore templates and the resolved bindings. Review the entire draft as GM, then commit and privately deliver through the existing starting-lore/inventory operation.

Example: House Blackwood receives the fact "has been smuggling royal silver." Independent knowledge rolls may tell no other house, one house or several. Anyone who does learn it receives text referring to that same stored fact. Blackwood receives no indication of whether anyone knows, who knows or how many know. A different cast draw may assign different facts and observers.

The shared Core part manages pools/templates, explicit variable bindings, draw identity, rendering limits, review/commit and durable book delivery. Last Council supplies the fact/goal/knowledge vocabulary, pool content and political compatibility rules. Prefer bounded selection with a scenario validation hook over a new universal rule language.

The published draw is live world state: its selected entries, seed, links, recipients and rendered text stay stable across pause, death, reconnect and restart. A new play window is not a new draw. GM preview/reroll is available before commitment; changing a committed cast requires a deliberate new setup operation with review, preserving prior records. Never silently rewrite already-issued books or delete unrelated inventory.

Pool definitions and templates are reusable mod/server content and travel through deliberate scenario export extensions. Actual selections, seeds, private bundles and discoveries do not become packaged defaults. Do not mutate a shared book definition or a role's global book list to deliver different text to different players.

Private handouts are delivered privately and excluded from ordinary public card/catalogue projections. Players can still show or trade their physical books in RP. Public card edits are not a source of voting authority, and private secrets must never be inserted into the public card description. If generated public text is applied to a card, it requires reviewed setup and respects existing player-authored fields and text limits.

Last Council still needs a small amount of authoritative political state tied to those assignments: house ID, seat status, candidacy and rebellion. This is rules data behind the existing character, not another role-card editor. Changing a display name or editing a book cannot gain a seat or become another house.

## Events call the Council; the Chancellor conducts it

1. The GM triggers the configured **Call Council** Event. The existing Event editor owns the alarm sound, announcement text and audience settings.
2. Players gather in the authored Council Region. The Chancellor opens the meeting and chooses what to discuss.
3. The Chancellor selects or adds a motion, opens that vote, allows discussion/casting, then closes and records it. They repeat this for as many ordinary motions as appropriate, starting with smaller matters and raising the stakes at their discretion.
4. When relevant, the Chancellor opens the ruler/regency vote using the brief's succession procedure. A meeting need not contain that vote; the existing maximum of one succession ballot per meeting still applies.
5. The Chancellor closes the meeting or continues permissible business, after any complication obligations for this meeting are resolved. Results can feed existing Event sources and flags for later announcements or pool eligibility. Electing a ruler does not automatically finish the whole experience.

There is no second alarm/sound scheduler and no automatic agenda sequence. Existing Event timers/conditions may support authored cues, but the default Council flow is host-directed. The meeting/ballot records preserve procedure; the clock does not decide which motion is next or force the ruler vote.

Random complications have their own Council-window gate within that flow: the system chooses content/arrival from the configured pool without the GM seeing the upcoming result. Before a regency vote—or closing a meeting without one—the current meeting must resolve its due complications. Late delivery creates RP time in a Council recess, not a technical pause. A later meeting may still receive a complication even if an earlier meeting already held a regency vote.

Implementation detail requiring LC-02 work: current repeatable Events use registered source occurrences, with text/sound only and no delay. A completed once-per-campaign manual Event cannot simply be reused indefinitely. Route each authorized Council call through a durable, distinct call occurrence and the existing repeating Event path. Expose that approved call through the Events/Council integration so the GM does not maintain duplicate cue definitions. Retrying a call uses its same receipt; a later deliberate call is a new occurrence. Preserve current limits on repeating world/flag outcomes rather than clearing completion history.

Event media remains in Core's executor. New ballot state handles choices, secrecy, electorate snapshots and counting; announcement/flag history alone is insufficient for those guarantees. The exact call button and meeting/voting panels need the usual UI design review before implementation.

Use the existing GM/operator authority for Chancellor controls. No separate role delegation, reveal-all secret-vote permission or extra GM vote is introduced. Council eligibility still follows the configured house assignment.

## How the remaining features fit

| Feature | Existing foundation reused | Required addition and owner |
|---|---|---|
| House information and private objectives | Character cards, Player Roles, Books & Lore, private first-entry grants | Core opt-in pool/draw/template support; Last Council's authored pools and relationship rules |
| Meeting alarm and announcements | Event editor, text/sound executor, occurrence receipts | Last Council's authorized call source and meeting reference; no second cue engine |
| Agenda and votes | Core session authority, active clock, shared forms/pickers, recovery patterns | Optional Core ballot service; Last Council motions, sponsorship and GM sequencing |
| Ruler election | The same ballot service and meeting records | Last Council's candidate lock, majority/elimination rules and recorded draw; no automatic immediate runoff |
| Treaties | No existing treaty mechanic; ordinary shared UI/storage code only where it fits | New Last Council agreement workflow: terms, parties, explicit signatures, active/broken status and permitted history; no generic Core agreement framework required upfront |
| House/civil/foreign wars | Ballot outcomes, treaty records and existing Event/flag notifications | Last Council political conflict state; no combat, troops or economic simulation |
| Rebellion and reinstatement | Existing Region geometry and, once implemented, ballot outcomes | New Last Council political rights/standing and Core guarded-Region enforcement. A confirmation is just UI; ordinary participation tracking is not the rebellion system |
| Palace setup | Current Region selection, subtypes and safe Point binding | Guarded access subtype/policy, not a separate area registry or map-authoring workflow |
| Room acoustics | Existing Regions plus Simple Voice Chat's documented APIs; evaluate Sound Physics Remastered first | New optional shared room-voice integration if existing physical acoustics is insufficient; decide hard isolation versus audible muffling after a prototype |
| Drinks, poison and antidote | No existing banquet mechanic; vanilla item/effect facilities and Core pause/clock behavior are implementation foundations | New Last Council drinks, contamination, delayed illness and antidote interactions. Starting gear and chest loot are not dependencies of the banquet |
| Complications | Existing Event execution with text/sound/flags | New optional Core Event-pool selection/dispatch; Last Council's configurable evening total, hidden Council timing and pre-regency/recess rules. Author the reusable pool once; do not pick a live crisis each run |
| Letters | Event text for a narrative message; Books & Lore only when an actual book item is wanted | A letter is content of a complication/communication, not a separate mechanics category. Event-driven physical book delivery would require a new explicit action; it does not exist today |
| Political history and ending | Durable operation records, existing notices/text presentation | Last Council aggregate view of laws, treaties/conflicts and crown state, with permitted visibility |
| Authoring and portability | Existing editor, separate authoring profile, export/import and world-bound map setup | Explicit support for new reusable pools/rules; no duplicate editor or export of live secrets/ballots |

Typed political actions such as signing a treaty or closing a ballot go through their own consent/validation. Existing Events can observe committed outcomes and update flags or deliver cues; they do not become a way to force signatures, disclose secret choices or overwrite political results.

For treaties, the latest player experience is an in-person ceremony: A and B gather at a bound treaty lectern with the Chancellor, read/sign the same exact terms, then the Chancellor sanctifies the deal. Signatures alone do not activate it; realm ratification remains additional where applicable. Either party can later break it, leaving a record. It does not force votes or automatically enlist allies. This is a new Last Council feature, not Books & Lore repurposed as a treaty engine.

**5 November UI correction:** use [separate small screens at meaningful interaction points](UI_SCENES_FLOW.md). The personal menu contains Character/Houses and own-house papers; editable pronouns are part of the existing card. Motion proposal/sponsorship happens at the hall's bound motion lectern, while an actual GM-opened vote presents a standalone ballot. House war begins with an in-person interaction/challenge against the other representative and unilateral declaration confirmation, not a remote target list. A focused Chancellor proceedings screen and the existing authoring editor serve different tasks. The first combined Council/Treaties/Conflicts menu is rejected; shared widgets do not require shared navigation. The revised prototype is pending review, and exact presence, gesture/item and ballot reopening policy remain open.
