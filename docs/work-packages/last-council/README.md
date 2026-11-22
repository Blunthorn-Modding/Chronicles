# The Last Council work-package tree

**22 November worldbuilding review:** the editable [worldbuilding workbook](../../worldbuilding/last-council/README.md) proposes 16 public Houses with five private alternatives each, 50 general secrets, 50 open-ended goal motivations and 24 complications, with historical/literary sources. It is a discussion draft. Implementation is explicitly postponed and the beta defaults remain unchanged; goals are not scored tasks or prescribed end-success states.

**Requested beta delivery:** The Last Council **0.1.0-beta.1** with Chronicle Core **0.2.0** packages the verified dev.20 implementation for combined multiplayer review. See the [release notes](../../releases/core-0.2.0-last-council-0.1.0-beta.1/README.md) and [human review checklist](BETA_REVIEW_CHECKLIST.md). The release uses plain JARs; existing dev.20 e4mc Host/Player packs remain available as a starting point after replacing both mod JARs. No dedicated review server is required.

**Implementation baseline:** dev.20 [election controls](ELECTION_RESULTS.md), including the automatically verified dev.19 [political Event sources](POLITICAL_SOURCES_RESULTS.md) and dev.18 [Voice rooms](LC10_RESULTS.md). Candidate withdrawal, Chancellor unresolved ending, public audit and the advisory meeting warning are implemented.


**Earlier delivered slice:** [LC10 automatic Voice rooms](LC10_RESULTS.md), approved through the existing Regions subtype workflow and implemented in optional Chronicle Core integration. Matching dev.18 e4mc Host/Player packs include the official pinned Simple Voice Chat dependency. Enabling a Region creates its group automatically; no manual group setup is needed. Microphone/audibility, representative load and the final map/group rehearsal remain unpassed. The Council authority clarification uses existing motions and votes, without a separate ratification system. Earlier [LC06](LC06_RESULTS.md), [LC05B](LC05B_RESULTS.md) and [LC05A](LC05A_RESULTS.md) remain delivered; their deferred human reviews remain pending.

Planning baseline: 4 November 2026. Source: [gameplay brief revision 1.1](../../THE_LAST_COUNCIL_EVENT_BRIEF.md). The brief is preserved unchanged; its revision number is not a mod release number.

Worktree: `E:\Coding\MC RP\Chronicles-last-council`, release branch `release/core-0.2.0-council-0.1.0-beta.1` from verified dev.20 commit `53b0821`. The shared source development version remains **0.2.0-dev.20**; per-module build overrides produce Core **0.2.0** and Last Council **0.1.0-beta.1**. Minecraft remains **1.21.10**. The existing Core/Quiet Below **0.1.0-beta** release files remain unchanged; Quiet Below's unchanged beta is the Core 0.2.0 compatibility target. See [implementation status](IMPLEMENTATION.md) for delivered slices and [release verification](../../releases/core-0.2.0-last-council-0.1.0-beta.1/VERIFICATION.md) for evidence and limits. Public publication and a main-branch merge are separate actions.

The Last Council is the next concrete **Intrigue** campaign: one evening of player-directed politics, with several formal Council meetings inside it. A Council meeting is not a Chronicle play-session boundary. Electing a monarch does not stop laws, treaties or roleplay.

**Clarified direction:** use existing character cards with randomized, connected lore/goal/house-information pools. Existing Events call the Council; the **Chancellor is the GM** and manually sequences discussion and votes, ending with succession when relevant. See [the clarified framework integration](FRAMEWORK_INTEGRATION.md), which supersedes fixed-cast assumptions from the original interpretation without editing the brief.

**Further clarification:** secrets may be known by zero, one or several other houses; the owner gets no indication of exposure. Complications are Events; banquet and treaties are new scenario mechanics, not starting-gear/book systems. Shared UI utilities are not gameplay reuse. Room voice/acoustics has its own LC-10 investigation, with existing mod support evaluated before custom routing.

**Random complications:** a reusable Event pool surprises everyone, including the GM, with hidden selection and arrival. The configured total spans the evening, not one per meeting; delivery happens during Council meetings before that meeting's regency vote. A late event creates a Council recess with normal gameplay still active. See [the confirmed count/timing rules](RANDOM_COMPLICATIONS.md). That timing plan selected no voice dependency; the later LC10 decision now supplies optional Simple Voice Chat room groups.

## Package order and dependencies

| Package | Deliverable | Depends on | Status |
|---|---|---|---|
| [LC-01](LC-01-foundation.md) | Scenario/card links and reusable randomized lore assignments | Existing Core | Implemented: character/House views, random papers, late enrollment, source editor, number variables and requested physical copies. Solo editor/copy review passed; final content and group acceptance remain. |
| [LC-02](LC-02-council-ballots.md) | Event-led gatherings; Core ballots and GM-led Council procedure | LC-01A | Implemented and automatically verified: immutable ballots, captured electorate, motions, proceedings and complication gate. Human group acceptance remains. |
| [LC-03](LC-03-succession.md) | Locked candidate slate and progressive succession | LC-02 | Succession engine and integration implemented. Approved meeting-capacity warning and explicit withdrawal/unresolved-end controls are implemented and automatically verified in dev.20; group acceptance remains. |
| [LC-04](LC-04-treaties.md) | New Last Council treaties, signatures and visibility | LC-01A | House-agreement ceremony implemented; human review pending. Council decisions use normal proceedings; no separate ratification milestone |
| [LC-05](LC-05-conflicts.md) | House/civil/external wars and peace agreements | LC-02, LC-04 | LC05A implemented/automatically verified; human review deferred. LC05B approved18November; implemented and automatically verified; human acceptance deferred |
| [LC-06](LC-06-rebellion-access.md) | Optional guarded Regions; rebellion and formal reinstatement | LC-01, LC-02, LC-03; LC-04/05 integration | Implemented and automatically verified; human boundary/visit review pending |
| [LC-07](LC-07-banquet.md) | Optional drinks, delayed illness and antidote | LC-01, existing Core clocks/items | Dev.13 food poisoning and dev.14 feedback/loot corrections accepted by owner; see LC07B_RESULTS.md and DEV14_BANQUET_FEEDBACK.md |
| [LC-08](LC-08-content-events.md) | Core Event pools; random Council complications, character content and source/flag links | LC-01B/C for handouts; LC-02 progression gate for timing; integrate delivered operations | Pools, hidden timing/gates and portable content implemented. Additional public political Event sources are implemented and automatically verified in dev.19. Final narrative pools and group pacing review remain. |
| [LC-09](LC-09-verification-release.md) | Native multi-client rehearsal, map/group gates and separate release | LC-01–06, LC-08, chosen LC-10 voice behavior; LC-07 when enabled | Native coordinator delivered; map/group/voice/release gates pending |
| [LC-10](LC-10-room-voice.md) | Automatic optional Core Voice rooms using Regions and Simple Voice Chat | Existing Regions/voice setup | Approved and implemented in dev.18; human microphone/remote UDP and representative load review pending |

The political critical path is LC-01A → LC-02 → LC-03, then peace-agreement/access integration and LC-09. LC-01B/C add the randomizer and Last Council bindings; they must be complete before randomized handout delivery/content review, but need not block the ballot-domain work. Treaties and banquet work need not wait for succession. Packages describe coherent deliverables, not instructions to implement everything concurrently.

LC-10 now supplies the approved isolated room-group behavior independently of Council politics. Physical wall/door attenuation and boundary muffling are not part of this implementation.

## What is already available

Reuse Core's Player Roles, private role-issued books, character cards, Regions/Points/Block sites, shared picker and confirmation widgets, session/Building Mode, active-play clocks, Event/flag execution, scenario profiles/export and audited recovery patterns. Keep the existing storage split: map bindings and live progress belong to the world; reusable defaults and server configuration retain their current homes.

Delivered foundation includes connected private lore draws and unique house assignments. Electorate snapshots, ballots and motion/proceedings screens are implemented in LC02; initial house treaties are implemented; house/foreign conflicts, rebellion state and guarded Regions are implemented and automatically verified; human group acceptance remains pending. Small house/seat metadata attaches to existing character assignments; it does not require another house dossier/catalogue. Existing Events supply the gathering/announcement flow, while the new political services preserve vote/consent state. Quiet Below's sculk and stations are not dependencies of this campaign.

Read [integration boundaries and rule contracts](CONTRACTS.md) before implementation. Each package lists acceptance evidence and its review gate. Never mark a package delivered based only on its model classes or a screenshot.

## Review and verification workflow

**5 November direction:** UI design precedes gameplay. Continue with [small screens and physical interaction points](UI_SCENES_FLOW.md). My house separates information, secrets, assigned house goals and known other-house secrets; personal goals are player-defined and omitted. Votes are final once confirmed. Only online eligible houses inside the voting Region **at opening** form the fixed electorate; five present means three required, with no late entrants or threshold changes on departure. Same-lectern treaty creation, physical challenge and focused Chancellor controls remain. The owner has approved these flows and authorized implementation; new or materially changed flows still need their own design review.

1. After UI review, start LC-01 with domain/state and module isolation; retain the existing release baseline.
2. Before each new player/GM screen, design the actual flow and review the mockup. Reuse the established vanilla-style components and shared popups. Do not replace approval of the flow with a finished native screen.
3. Implement one bounded package at a time; independently review changes that affect secret votes, consent, cross-store operations or guarded movement.
4. Run focused domain tests, then relevant module tests and native interaction/restart tests. Core changes also require Quiet Below regression evidence proportional to their surface.
5. Bundle human reviews when independent work can continue. Stop for decisions or reviews that would materially change the next implementation, rather than after every small feature.

The original planning-only checks were `git diff --check`, local Markdown-link validation and the source-brief hash comparison. Focused Gradle targets are `:core:test` and `:campaigns:last-council:test`, using the existing workspace toolchain environment. `python scripts/workspace.py build` remains the current full-build entry point. The first dedicated native foundation runner is `python scripts/last_council_smoke.py`, after building its matching JARs and `:campaigns:last-council:writeSmokeClientLaunch`. It does not verify later political gameplay.

Keep builds/native clients serialized where they share class directories or test ports. Use small disposable fixtures, retain compact results, then delete successful worlds. No review server, production map generator or long soak is authorized by this planning pass.

## Decisions to settle at the relevant package gate

- **LC-01:** the implemented first version uses one representative per House. Multiple simultaneous representatives are future scope requiring a separate delegation policy.
- **LC-02:** technical void with a public reason and a new replacement ballot is confirmed. Confirmed access uses a configurable hotkey and command. Voided regency permits same-meeting replacement; only completed regency consumes that meeting slot. Recovery resumes the same saved attempt without a fresh snapshot.
- **LC-04:** signatures persist departure/reconnect; everyone returns to the same lectern for sanctification. Private existence/breaches/history stay private. Active amendments and deliberate reveal are optional future decisions. Council decisions are binding through existing proceedings, with no additional realm-ratification lifecycle. Never disclose private terms merely because a public war was declared.
- **LC-06:** one simple Chancellor visit action is confirmed; the no-Council reinstatement fallback has been removed. Crowned-ruler abdication/deposition remains separate future work.
- **LC-07:** review dose counts, onset/duration, symptoms and antidote strength; the brief deliberately leaves those numbers open. Banquet poisoning remains optional.
- **LC-01B/LC-08:** review pool content, draw quantities, repetition policies and compatibility rules for replayable characters. Cross-house knowledge must resolve to the same draw's assigned facts. The brief's fixed eight-house example is illustrative, not final canon.
- **LC-08B:** complication total across the evening and the current-meeting regency horizon are confirmed. Review hidden allocation/rescheduling, spacing and Council-recess presentation with the UI. No per-run GM choice or preview of future crises.
- **LC-10:** automatically managed isolated room groups are implemented; microphone audibility and remote UDP delivery still need owner review.

These are tracked design decisions, not reasons to delay the work-package tree or invent new rules now. The source brief remains authoritative where it already gives an answer.
