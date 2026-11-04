# Clarified character setup and Council flow

User clarifications, 4 November 2026. These amend the implementation interpretation of [the unchanged brief](../../THE_LAST_COUNCIL_EVENT_BRIEF.md): use existing character cards plus randomized lore pools, and use existing Events to call gatherings before the Chancellor conducts votes. **The Chancellor is the GM**, not a separate player office requiring a new delegation system.

This remains a design/implementation plan. No randomizer, Council controls or political mechanics have been implemented yet.

## Character cards and randomized lore

Keep Core's existing character card as the public character surface and Player Roles for assignment, starting gear and destinations where needed. Add reusable assignment pools to the existing book/role workflow, supporting card-bound handout delivery when no role is assigned. Do not require an organizer to fill out a second house dossier, create a separate role solely to hold each randomized house, or maintain another character catalogue.

The current card contains public name, pronouns and description; `PlayerRole.books` currently contains fixed book IDs. Random pools, template substitution and connected cross-character facts are **new opt-in Core capabilities**, not features already supported by that fixed list. Existing fixed assignments, including Quiet Below's, must keep their current behavior.

The prepared content can include a large list of house information, personal/house secrets, goals, relationships and information known about other houses. Pool sizes, quantities, weights, allowed repetition and eligibility constraints are configurable. Authored text/templates supply the material; an external AI generator is not required.

Proposed preparation flow:

1. Select the participating characters through the existing assignment tools. Keep stable assignment/house identifiers behind the cards for political authority.
2. Draw compatible public house information and facts/secrets for those assignments.
3. Draw goals against the actual participating houses and configured political options. Opposing goals between players are desirable; validation checks that a target/mechanic exists, not that everyone can win together.
4. Draw knowledge links to **facts already assigned to another house**. Resolve names/references from that same draw. By default, an entry described as knowledge of a secret is true of its target; deliberately uncertain rumors must be authored as such.
5. Build each character's private book bundle from existing Books & Lore templates and the resolved bindings. Review the entire draft as GM, then commit and privately deliver through the existing starting-lore/inventory operation.

Example: House Blackwood receives the fact "has been smuggling royal silver." Another house receives "Your contact saw Blackwood agents moving royal silver." That knowledge book refers to the same stored fact. A different cast draw may assign the fact and observer to different houses.

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
5. The Chancellor closes the meeting or continues permissible business. Results can feed existing Event sources and flags for later announcements or complications. Electing a ruler does not automatically finish the whole experience.

There is no second alarm/sound scheduler and no automatic agenda sequence. Existing Event timers/conditions may support authored cues, but the default Council flow is host-directed. The meeting/ballot records preserve procedure; the clock does not decide which motion is next or force the ruler vote.

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
| Treaties | Shared document/book display, confirmation UI and audit/recovery patterns | Optional Core revision/consent primitives; Last Council treaty types, signatures and ratification rules |
| House/civil/foreign wars | Ballot outcomes, treaty records and existing Event/flag notifications | Last Council political conflict state; no combat, troops or economic simulation |
| Rebellion and reinstatement | Existing confirmations, player participation, Regions/Points and formal vote outcomes | Last Council rights changes plus optional Core guarded-Region enforcement; rebels remain players |
| Palace setup | Current Region selection, subtypes and safe Point binding | Guarded access subtype/policy, not a separate area registry or map-authoring workflow |
| Drinks, poison and antidote | Existing starting gear/chest loot, vanilla items/effects and Core pause/clock rules | Last Council's optional consumption/contamination/delayed illness logic; no thirst or separate stock manager |
| Complications and private letters | Events, flags, Books & Lore, existing delivery and loot tools | Last Council content and links to completed political operations |
| Political history and ending | Durable operation records, existing notices/text presentation | Last Council aggregate view of laws, treaties/conflicts and crown state, with permitted visibility |
| Authoring and portability | Existing editor, separate authoring profile, export/import and world-bound map setup | Explicit support for new reusable pools/rules; no duplicate editor or export of live secrets/ballots |

Typed political actions such as signing a treaty or closing a ballot go through their own consent/validation. Existing Events can observe committed outcomes and update flags or deliver cues; they do not become a way to force signatures, disclose secret choices or overwrite political results.
