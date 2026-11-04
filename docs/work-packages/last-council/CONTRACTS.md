# Integration and rule contracts

Source: [The Last Council brief](../../THE_LAST_COUNCIL_EVENT_BRIEF.md), revision 1.1. These are implementation boundaries and test obligations, not additional story rules.

The user's later [card/randomizer and Event-flow clarification](FRAMEWORK_INTEGRATION.md) takes precedence over fixed-cast assumptions: use existing cards, interconnected randomized book content and Event-led gatherings, followed by manual GM procedure. Chancellor and GM are the same authority.

Further feedback: secret knowledge is a random subset with no coverage guarantee or owner-visible exposure count. Reuse genuine mechanics where appropriate; shared UI/helper code is not an existing gameplay mechanic. Complications belong in Events, banquet is new scenario gameplay, and treaties start as a Last Council feature rather than a mandatory generic Core agreement framework. [LC-10](LC-10-room-voice.md) investigates room audio separately from palace access.

## Library versus scenario

| Area | Chronicle Core | The Last Council |
|---|---|---|
| Characters | Existing roles/cards, assignment, starting gear/private lore; new optional pools, draw/template binding and stable delivery | Pool content and house/fact/goal/knowledge relationships; thin political metadata linked to existing cards |
| Voting | Opt-in ballot lifecycle, immutable electorate snapshots, counting, private/public projections and durable receipts | Which houses may vote, Council agenda, thresholds by motion type, one succession ballot per meeting |
| Succession | No monarchy or candidate-elimination assumptions | Slate lock, elimination, recorded tied-last draw, confirmation, crown/vacancy/unresolved outcomes |
| Treaties | No existing agreement mechanic; shared implementation utilities only where they help | New terms/party/signature/status/history workflow and political consequences; extract a Core service only when justified |
| Conflict | Existing audit/recovery/notification primitives | Houses, foreign counterparts, conflict sides, civil-war classification, rebellion/reinstatement |
| Map access | Opt-in server-authoritative Region access policy with safe destinations and bounded recovery | Palace restrictions derived from house standing and supervised visit exceptions |
| Room audio | Existing Region geometry; optional new Simple Voice Chat integration after an acoustics prototype | Palace/private-room configuration; independent of guards, house status and ordinary participation |
| Banquet | Reuse existing item, clock and effect helpers where suitable | Drink/poison/antidote items, information boundaries and illness rules; no generic thirst engine |
| Events/content | Existing Event execution, flags, source receipts, books and scenario export | Council-call occurrences, host-directed meeting/agenda flow, complications and replayable content pools |

Intended new content module: `campaigns/last-council`, mod ID `last_council`, depending on `chronicle_core`, Fabric API and the supported game/loader. It must not depend on or import `quiet_below`. Add only this demonstrated campaign, not empty modules for every possible format.

Core ballots and Last Council treaty operations use stable party/elector IDs with server-owned authorization. The scenario maps a player to the current house; a client-supplied house ID is not authority. There is no need to introduce a second character/group/faction engine just to model eight houses.

The existing public card contains name/pronouns/description; it is not a private dossier. Keep secrets in privately delivered books/bundles. Current roles have fixed book-ID lists; optional pool assignments and per-draw rendering require explicit Core schema/delivery extensions that preserve those fixed assignments. Generate facts first, then independently roll knowledge links: zero, one or several other houses may know a secret, without telling its owner. Persist committed draw state in the world, keep reusable pool definitions in mod/server content, and exclude live selections/seeds from exports.

## Existing extension points

- `PlayerRole`/`PlayerRoles`, role assignments and `RoleFirstEntry`: attach house participation to the existing character lifecycle; never grant duplicate gear/lore on reconnect.
- `SiteSubtypes.Registry`: add an opt-in guarded Region capability; use the existing Region selection and Point picker for its exit destination.
- `SessionGameplay` and the campaign clock: distinguish ACTIVE play, technical pause and Building Mode from political meetings. No political countdown advances during a technical pause.
- `EventSources.registerProvider/registerAuthority`: expose **completed** political operations as source receipts. Opening a menu, retrying a completed request or previewing a complication does not produce another source.
- `ScenarioContentRuntime.registerScenario`, `ScenarioProfile` and export: package content without copying player data, vote choices or political progress into defaults.
- `CoreMenuScreen`, shared picker/popups and existing confirmation patterns: reuse controls and authority checks while keeping a dedicated participant Council surface separate from the operator editor.

The last item concerns shared UI implementation only. Confirmation widgets and the ordinary participant flag do not implement treaties or rebellion. A rebel stays an ordinary participant; their political rights are new Last Council state. Complications use Events; a physical book letter uses Books & Lore only when that item presentation is wanted, and Event-driven book delivery would need an explicit new action.

Council gatherings begin with configured Event text/sound; the GM then opens a meeting, chooses motions and opens/closes votes one by one. No separate Chancellor player/delegation mechanism is needed. Repeated calls use authorized source receipts and the existing recurring-media path, whose current constraints exclude repeated terrain/flag changes and delays. Do not reset Event completion history or add a competing sound scheduler to ring the bell again.

These APIs need focused integration work; their existence does not mean ballots, treaties or palace exclusion are already implemented. The current root `build.gradle` explicitly configures two modules, so adding Last Council also requires extending that shared build configuration.

Registration also has finite budgets: `SiteSubtypes` currently allows twelve definitions and `EventSources` thirty-two providers. Audit the combined Core/Quiet Below/Last Council set before adding providers; do not silently drop late registrations or expand limits without bounded projection/tests.

## Non-negotiable ballot rules

- Freeze the eligible house set when opening each ballot. Abstentions, missing votes and disconnects remain in its denominator.
- Absolute majority is `floor(N / 2) + 1`; two-thirds is `ceil(2N / 3)`. Handle no eligible houses explicitly, not as an automatic pass.
- One final choice per eligible house; changes replace the prior choice only while open. Authorize on the server and reject stale ballot revisions.
- Suspend membership/candidacy changes while a ballot is open. Do not silently queue an unconfirmed rebellion to execute later.
- Public ballots may show named choices. Secret ballots show only the current player's acknowledgement and final aggregate totals. No live secret totals, other players' choices or host reveal-all control.
- Secret choices must not leak through menu packets, history/Event payloads, debug views, suggestions, ordinary logs or reusable content exports. Trusted filesystem administrators are outside this normal-interface secrecy guarantee.
- Closing and outcome application use durable, replay-safe operations. Technical recovery never recasts, retallies a different electorate or replays an outcome under a new identity.

## Succession rules

At least two initial candidates, locked at the first succession ballot. Crown immediately on an absolute majority. Otherwise, with more than two candidates, eliminate exactly one lowest candidate; resolve tied last place by an announced draw whose stored outcome is reused on recovery. Never use randomness to crown a monarch.

The next succession ballot occurs at a later Council meeting. With two candidates and no majority, mark vacancy. One remaining candidate requires a later Yes/No/Abstain confirmation with an absolute majority. Zero candidates or zero Council seats means vacancy. An evening cut short is unresolved, not a plurality win. Eliminated candidates keep Council votes. No new ballot after crowning or declared vacancy without an explicit, audited political reset preserving earlier history.

Meetings continue for ordinary business after a crown decision. A planned schedule should warn if it provides fewer than `candidateCount - 1` succession opportunities for an unchanged initial slate; this does not authorize automatic meetings or altered election rules.

## Consent, consequences and privacy

Every treaty party reads the exact revision being signed and explicitly consents. Term edits invalidate prior pending signatures. No ordinary player/GM action signs for someone else or mechanically forces a secret vote. Mutual defense notifies allies, never auto-enlists them.

House war, civil-war classification, external war and rebellion are separate domain facts. War does not remove palace access or enable PvP. Rebellion requires a clear confirmation and removes Council rights/candidacy/access while keeping the player an active participant. Never reuse Quiet Below's Spectator departure outcome for rebels.

Model rebellion/reinstatement, treaty breaches and political outcomes with operation receipts so a partial write cannot grant a seat twice, omit a breach or apply a result repeatedly. Preflight required map destinations; interrupted operations are held/recovered transparently, not silently converted into success.

Private treaty terms are visible to signatories and the GM; secret ballot choices are a different boundary and are not revealed through normal GM controls. Poison authorship is private moderation evidence. Public projections should be explicit DTOs, not full state with client-side hiding.

## State, release and scope

World: committed randomized character/knowledge bundles and their seed, house membership/standing, meetings, motions, ballots, signatures/conflicts, source receipts, live illness and map bindings. Defaults/configuration: reusable pools/templates/settings/content using existing scenario mechanisms; extend that schema deliberately for portable pool/political defaults, preserving old fixed-role book lists. Never move unrelated Quiet Below configuration.

Selecting installed scenario mechanics needs explicit isolation tests: Core alone; Core+Quiet Below; Core+Last Council; both content mods with a deliberate profile selection. If no unambiguous scenario is selected, refuse setup clearly rather than mixing both sets of defaults.

All new UI is capability-gated. Quiet Below must not gain mandatory ballots, treaty tabs or palace rules just because Core supports them. Commands remain under `/chronicle`; proposed Council routes belong under `/chronicle council`, without a second plural root or a competing chat form system.

No armies, numerical military economy, automatic conflict winner, NPC diplomacy, mandatory combat, secret host rewriting, objective scoreboard or production map generation. Optional poison cannot become a requirement for political play. Preserve the current alpha release artifacts; choose new mod versions when implementation is ready for its own build.
