# The Last Council work-package tree

**Current slice:** dev.8 adds [reusable House/secret/goal-content export and custom-profile migration](LC01_PORTABILITY_RESULTS.md); source-content authoring is in workflow design. Both treaty and complication multiplayer acceptance are explicitly deferred to the next review cycle, not passed and not development blockers. Dev.7 repairs the Event-pool editor frame/footer and adds reusable pool/settings export/import. The owner approved continuing on 9 November and deferred the human complication/Regency group review until more testers are available; it remains outstanding without blocking development. See [9 November implementation](NOV09_IMPLEMENTATION.md). The owner also approved the treaty UI and policies; the initial native house-agreement ceremony is implemented and awaiting the deferred human UI/ceremony review. Future review deliveries are Host/Player packs only.

Planning baseline: 4 November 2026. Source: [gameplay brief revision 1.1](../../THE_LAST_COUNCIL_EVENT_BRIEF.md). The brief is preserved unchanged; its revision number is not a mod release number.

Worktree: `E:\Coding\MC RP\Chronicles-last-council` on `feat/last-council`, based on release commit `4d73742`. Chronicle Core and The Quiet Below's latest released version remains **0.1.0-alpha.1**, Minecraft **1.21.10**. The approved native implementation is now in progress on development version **0.2.0-dev.8**. The released **0.1.0-alpha.1** artifacts remain unchanged. See [implementation status](IMPLEMENTATION.md) for delivered slices, current evidence and omissions.

The Last Council is the next concrete **Intrigue** campaign: one evening of player-directed politics, with several formal Council meetings inside it. A Council meeting is not a Chronicle play-session boundary. Electing a monarch does not stop laws, treaties or roleplay.

**Clarified direction:** use existing character cards with randomized, connected lore/goal/house-information pools. Existing Events call the Council; the **Chancellor is the GM** and manually sequences discussion and votes, ending with succession when relevant. See [the clarified framework integration](FRAMEWORK_INTEGRATION.md), which supersedes fixed-cast assumptions from the original interpretation without editing the brief.

**Further clarification:** secrets may be known by zero, one or several other houses; the owner gets no indication of exposure. Complications are Events; banquet and treaties are new scenario mechanics, not starting-gear/book systems. Shared UI utilities are not gameplay reuse. Room voice/acoustics has its own LC-10 investigation, with existing mod support evaluated before custom routing.

**Random complications:** a reusable Event pool surprises everyone, including the GM, with hidden selection and arrival. The configured total spans the evening, not one per meeting; delivery happens during Council meetings before that meeting's regency vote. A late event creates a Council recess with normal gameplay still active. See [the confirmed count/timing rules](RANDOM_COMPLICATIONS.md). The owner will evaluate the sound mod tomorrow; no new voice dependency is selected by this plan.

## Package order and dependencies

| Package | Deliverable | Depends on | Status |
|---|---|---|---|
| [LC-01](LC-01-foundation.md) | Scenario/card links and reusable randomized lore assignments | Existing Core | In progress: source editor and requested copies delivered; dev.11 adds secret-local number variables, explicit House variables and unique assignment; see DEV11_VARIABLES_RESULTS.md |
| [LC-02](LC-02-council-ballots.md) | Event-led gatherings; Core ballots and GM-led Council procedure | LC-01A | In implementation: approved scenes, Core ballots, motions/proceedings; complication gate implemented; human group review deferred |
| [LC-03](LC-03-succession.md) | Locked candidate slate and progressive succession | LC-02 | In progress: rules/journal/regency scene; actual LC08 gate implemented; human group review deferred |
| [LC-04](LC-04-treaties.md) | New Last Council treaties, signatures and visibility | LC-01A; LC-02 for realm ratification | Initial native house-agreement ceremony implemented; human review and realm ratification/amendments remain |
| [LC-05](LC-05-conflicts.md) | House/civil/external wars and political settlements | LC-02, LC-04 | Planned |
| [LC-06](LC-06-rebellion-access.md) | Optional guarded Regions; rebellion and formal reinstatement | LC-01, LC-02, LC-03; LC-04/05 integration | Planned; confirmation/access review required |
| [LC-07](LC-07-banquet.md) | Optional drinks, delayed illness and antidote | LC-01, existing Core clocks/items | Planned; can progress independently of LC-03/04/05 |
| [LC-08](LC-08-content-events.md) | Core Event pools; random Council complications, character content and source/flag links | LC-01B/C for handouts; LC-02 progression gate for timing; integrate delivered operations | In implementation: pools/timing/gates and portable export; final content/source links pending |
| [LC-09](LC-09-verification-release.md) | Native multi-client rehearsal, map/group gates and separate release | LC-01–06, LC-08, chosen LC-10 voice behavior; LC-07 when enabled | Planned |
| [LC-10](LC-10-room-voice.md) | Shared room-voice/acoustics investigation using Regions and Simple Voice Chat | Existing Regions/voice setup | Investigate early; implementation choice needs prototype review |

The political critical path is LC-01A → LC-02 → LC-03, then settlement/access integration and LC-09. LC-01B/C add the randomizer and Last Council bindings; they must be complete before randomized handout delivery/content review, but need not block the ballot-domain work. Treaties and banquet work need not wait for succession. Packages describe coherent deliverables, not instructions to implement everything concurrently.

LC-10 can be investigated early and independently. Its later ID does not mean room acoustics waits until after release. Reuse existing physical-acoustics support if it satisfies the map; build custom Region routing/filtering only after reviewing the demonstrated gap.

## What is already available

Reuse Core's Player Roles, private role-issued books, character cards, Regions/Points/Block sites, shared picker and confirmation widgets, session/Building Mode, active-play clocks, Event/flag execution, scenario profiles/export and audited recovery patterns. Keep the existing storage split: map bindings and live progress belong to the world; reusable defaults and server configuration retain their current homes.

The current slice implements connected private lore draws and unique house assignments. Electorate snapshots, ballots and motion/proceedings screens are implemented in LC02; treaties, conflict/rebellion state and access-control Regions remain pending. Small house/seat metadata attaches to existing character assignments; it does not require another house dossier/catalogue. Existing Events supply the gathering/announcement flow, while the new political services preserve vote/consent state. Quiet Below's sculk and stations are not dependencies of this campaign.

Read [integration boundaries and rule contracts](CONTRACTS.md) before implementation. Each package lists acceptance evidence and its review gate. Never mark a package delivered based only on its model classes or a screenshot.

## Review and verification workflow

**5 November direction:** UI design precedes gameplay. Continue with [small screens and physical interaction points](UI_SCENES_FLOW.md). My house separates information, secrets, assigned house goals and known other-house secrets; personal goals are player-defined and omitted. Votes are final once confirmed. Only online eligible houses inside the voting Region **at opening** form the fixed electorate; five present means three required, with no late entrants or threshold changes on departure. Same-lectern treaty creation, physical challenge and focused Chancellor controls remain. The owner has approved these flows and authorized implementation; new or materially changed flows still need their own design review.

1. After UI review, start LC-01 with domain/state and module isolation; retain the existing release baseline.
2. Before each new player/GM screen, design the actual flow and review the mockup. Reuse the established vanilla-style components and shared popups. Do not replace approval of the flow with a finished native screen.
3. Implement one bounded package at a time; independently review changes that affect secret votes, consent, cross-store operations or guarded movement.
4. Run focused domain tests, then relevant module tests and native interaction/restart tests. Core changes also require Quiet Below regression evidence proportional to their surface.
5. Bundle human reviews when independent work can continue. Stop for decisions or reviews that would materially change the next implementation, rather than after every small feature.

The current docs-only checks are `git diff --check`, local Markdown-link validation and the source-brief hash comparison. After LC-01 adds the module, focused Gradle targets will be `:core:test` and `:campaigns:last-council:test`, using the existing workspace toolchain environment. `python scripts/workspace.py build` remains the current full-build entry point. The first dedicated native foundation runner is `python scripts/last_council_smoke.py`, after building its matching JARs and `:campaigns:last-council:writeSmokeClientLaunch`. It does not verify later political gameplay.

Keep builds/native clients serialized where they share class directories or test ports. Use small disposable fixtures, retain compact results, then delete successful worlds. No review server, production map generator or long soak is authorized by this planning pass.

## Decisions to settle at the relevant package gate

- **LC-01:** first release uses one representative per house, matching the brief's default. If multiple simultaneous representatives are required, decide delegation before implementing casting/signature authority; there is still only one vote per house.
- **LC-02:** technical void with a public reason and a new replacement ballot is confirmed. Confirmed access uses a configurable hotkey and command. Voided regency permits same-meeting replacement; only completed regency consumes that meeting slot. Recovery resumes the same saved attempt without a fresh snapshot.
- **LC-04:** signatures persist departure/reconnect; everyone returns to the same lectern for sanctification. Private existence/breaches/history stay private. Active amendments, deliberate reveal and realm ratification remain future decisions. Never disclose private terms merely because a public war was declared.
- **LC-06:** define the narrow supervised-visit exception and the separate crowned-ruler abdication/deposition procedure before exposing those controls.
- **LC-07:** review dose counts, onset/duration, symptoms and antidote strength; the brief deliberately leaves those numbers open. Banquet poisoning remains optional.
- **LC-01B/LC-08:** review pool content, draw quantities, repetition policies and compatibility rules for replayable characters. Cross-house knowledge must resolve to the same draw's assigned facts. The brief's fixed eight-house example is illustrative, not final canon.
- **LC-08B:** complication total across the evening and the current-meeting regency horizon are confirmed. Review hidden allocation/rescheduling, spacing and Council-recess presentation with the UI. No per-run GM choice or preview of future crises.
- **LC-10:** compare natural wall/door attenuation with hard room privacy or boundary muffling. Automatic group membership is a possible implementation, not a substitute for choosing and testing the acoustic behavior.

These are tracked design decisions, not reasons to delay the work-package tree or invent new rules now. The source brief remains authoritative where it already gives an answer.
