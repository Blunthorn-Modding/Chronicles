# Event-driven progression through flags

28 October 2026. **Proposal for organizer review; no implementation authorized by this planning pass.** Based on dev62 and the organizer's clarification: Events own triggers and actions. Flags store progress, Events update them, and other Events use them as prerequisites. Flags remain under Advanced as a state catalogue and GM override surface.

## The missing connection

The current native Events editor exposes a single optional required flag. It has no configurable flag outcomes. Its timer is armed when the definition is saved, so a flag becoming On after that delay can make the Event run immediately. This does not implement a countdown starting when prerequisites become true.

The older registered `CampaignEvent` callbacks can already change flags through `EventOutcome`, but those hardcoded callbacks do not provide reusable authoring for composed Events. The plan extends the existing Event workflow rather than adding a Flags-side automation editor.

The intended flow is:

1. A normal Event has a trigger: for example first Region entry or a completed Altar prayer.
2. Its successful outcome sets one or more flags On or Off, optionally alongside existing Region changes, text and sound.
3. Other Events require a list of flags. All listed flags must be On.
4. Those Events either execute when ready or start an active-play countdown after their prerequisites are satisfied.

Example: **City discovered** (Region entry -> `city_discovered = On`); **First offering** (successful Altar prayer -> `offering_made = On`); **Answer from below** (requires both flags -> wait five active minutes -> message, sound and optional saved Region changes -> `answer_started = On`). These are examples of configurable dependencies, not an imposed narrative sequence or session schedule.

## Event editor changes

Follow the Event's logical flow, using the established native components. The organizer rejected forcing the design into the old four pages and splitting actions into Regions / Flags modes. Screen pagination is a later layout decision, not the model:

- **When:** choose the trigger and its registered target, for example Region entry, a particular block interaction/activity, a completed prayer or a selected book being opened.
- **Requirements:** a list of prerequisite flags and **Add prerequisite** through the shared picker. The rule is explicitly **All required flags must be On**.
- **Delay:** an optional active-play countdown beginning when prerequisites are satisfied, using the confirmed reset-on-Off rule.
- **What happens:** one action list with **Add action**. Available actions include update flag, send text, play sound and apply a saved Region state. Each action has its own fields; updating a flag selects its ID and desired On/Off value, while applying a Region state selects the existing saved target. All use shared pickers. Saving a draft does not perform any action.

Block trigger configuration selects the bound/managed location (or a supported block type) and the activity to observe. Supplied Altar/Forge blocks use their existing automatic registration. Vanilla block sites reuse Locations. Offer explicit activity kinds, such as player interaction, a container actually opening, successful station work, placement or breaking, as supported by registered providers. Cancelled/refused interactions and automatic terrain replacement do not count as successful player activity.

For books, provide separate **Book found** and **Book opened** triggers, selecting the specific Books & Lore entry. In this plan, opening the real in-world book counts as reading it; it does not claim the player read every page or understood the text. Inventory possession alone is the existing discovery occurrence. GM/editor previews do not count as gameplay reading. Verify the actual managed book identity and active player's interaction rather than trusting a supplied title or an unrestricted client report. The trigger belongs to the Event; updating a flag belongs in its action list.

Permit a flag-only Event with no message, sound or terrain change. For example, entering the Ancient City can silently record discovery. Private preview continues to preview media only; it never writes flags or completes an Event.

The existing Events list keeps **Game Events / Region Changes**. A composed Event that updates flags remains a Game Event. Flags never become an additional Event category.

On **Advanced -> Flags**, retain current state and the manual override. A small follow-up improvement is **Updated by** and **Required by** relationships, with links opening those existing Events. This relationship navigation is optional polish after the core flow. Debug can show available change evidence, clearly reporting when the bounded audit no longer contains it; guaranteed lifetime first/last provenance would require additional persisted metadata and is deferred. Manual changes retain explicit GM confirmation and audit history. Creating a flag alone does not add any automation.

## Candidate trigger integrations

Every row below is an input to an ordinary Event. The chosen Event decides whether to update flags, send media or perform other supported effects. No independent action rules are attached to flags.

| Gameplay occurrence | What counts | Example Event outcome | Ownership / readiness |
| --- | --- | --- | --- |
| First Region visit | First eligible participant observed inside the selected Region during active play. Existing entry dispatch supplies this. | `city_discovered = On` | Core; first implementation pass. |
| Existing manual, timed or conditional Event | Its real outcome succeeds, with no flag updates from preview/check/editor saves. | `investigation_started = On`, `investigation_pending = Off` | Core; first pass. |
| Departure completed | The selected departure's committed player/optional terrain work completes. Accepting the first confirmation is insufficient. | `ship_departed = On` | Core; source integration pass. |
| Selected block activity | An eligible player's authorized interaction, actual container opening, supported placement/break or provider-confirmed activity at the chosen block/site. Each activity is explicitly labelled. | `door_examined = On` | Core hook/provider; cancelled work, authoring and automated world changes are excluded. Opening a workstation and successfully crafting/praying are distinct sources. |
| Altar prayer completed | A specific bargain, or any prayer, actually commits. Opening its menu, cancellation or refusal does not count. | `first_offering_made = On` | Core offering evidence; Quiet Below registers Altar/prayer labels and selectors. |
| Forge enchantment completed | A valid recipe produces an actual enchantment. Unrelated offerings/scraps or capped unchanged equipment do not count as this success. | `forge_mastered = On` | Core workshop integration with durable result evidence; Quiet Below supplies Forge semantics. |
| Lore book found | Existing campaign-wide first inventory discovery; current semantics also count GM gifts and role-issued books. | `research_journal_found = On` | Core; durable discovery already exists. |
| Specific lore book opened | An eligible participant opens the actual selected managed book during gameplay. Preview and merely carrying it do not count as opening/reading. | `forge_notes_read = On` | Core; a new validated opening occurrence is required. Finding and opening remain separate triggers. |
| Saved Region state applied | The selected Start/End state is durably applied, including an apply initiated by a departure or Event. Capturing a template or viewing bounds does not count. | `sinkhole_opened = On` | Core; direct/departure Area operation receipts and composed Event batch commit evidence are separate authorities. |
| Role assigned / first entry completed | Separate named sources: assigning a role and successfully delivering its initial setup are different facts. | `expedition_ready = On` | Core; later reusable integration. |
| Ecology milestone reached | Quiet Below reports an actual ecology transition, such as first reaching Established or Extensive. | `sculk_has_reached_established = On` | Quiet Below provider; Core hosts the trigger API/editor. |
| Relic Heart destroyed | Durable network ending after a valid pickaxe destruction. Complete sculk cleanup is a different milestone. | `heart_destroyed = On` | Quiet Below provider; durable ending authority exists. |

Recommended first source integrations after the Event foundation: departure, block interaction/activity, Altar, Forge, lore discovery/opening and saved-state completion. Role/ecology extensions then use the same provider boundary. Routine crop ticks, healing pulses and every item movement do not need individual flags for the current scenario.

## Countdown and completion semantics

- Prerequisites are gates. No prerequisite means no flag gate; several prerequisites use All/AND.
- A flag-gated countdown starts when all required flags are On during eligible active play, not when the Event was created or the play window began. An already-satisfied gate starts on the next eligible active tick after arming.
- Pause, Building Mode, operation holds, lack of eligible participants and downtime do not consume countdown time. Connected non-participants, editors, spectators and departed players do not keep the clock running. An already-On gate waits for eligible active play to start its countdown. The remaining time survives reconnects, server restarts and later play windows. Preserve the existing eligibility policy; individual station providers retain their stricter interaction rules.
- **Organizer decision confirmed:** if any required flag turns Off, reset the countdown and discard its accumulated time. Start the full configured delay again once all required flags are On and eligible active play can advance. Ordinary play pauses preserve the countdown when its flags remain On. Persist flag transitions so a brief Off -> On between dispatch ticks cannot evade the reset.
- Check prerequisites again before starting real effects. A flag update by one Event is considered by dependent Events on a later eligible dispatch tick, avoiding recursive execution inside a callback.
- Keep the existing deliberate rearm model: an Event completes once until a GM allows it again. Flags and progress remain campaign-wide across play windows. Automatic session resets are not introduced.
- First visit uses the current entry semantics: an eligible player already inside when play starts or reconnects may establish discovery. Editing/spectating/departed characters do not count. Repeated visits do not repeatedly complete the once-only Event.
- Historical facts stay recorded. `sculk_has_reached_established` remains true if the network later weakens. A current-state indicator would require an explicit transition integration; manually changing a progression flag never changes physical ecology.

## Core and content boundary

**Core** owns flags, multi-flag prerequisites, Event flag outcomes, gated countdown state, durable trigger evidence, the shared trigger provider catalogue, editor/pickers, provenance and recovery. Existing Core systems publish their successful operations through that boundary. Mods register labelled trigger kinds and supported source selectors, following the existing registry approach; authoring selects registered options rather than typing arbitrary action strings.

**Quiet Below** registers Altar/Forge/ecology/Heart trigger meaning and recommended default flags/Events. Its physical mechanics remain authoritative. Managed blocks already register themselves; this plan does not require manually re-registering an Altar or Forge to receive its trigger.

A generic block interaction and a provider-confirmed completed operation are distinct selectable sources. Only the station provider can certify that a prayer/enchantment succeeded. No JavaScript, free-form command execution or second automation UI is needed.

## Durability and compatibility work required

The critical implementation work is outcome persistence, not merely calling the current `setFlag` from a block callback:

1. Pin the complete flag patch in the Event definition/execution fingerprint. Update all selected flags atomically and persist a per-execution outcome receipt in the same authoritative transaction. Reconciliation must identify the original operation and never overwrite a later GM/Event change by replaying an old patch.
2. Flag outcomes follow successful Region-state application and precede media claiming. A failed/conflicted terrain operation must not claim its completion flags. An uncertain media delivery must not erase or replay already committed flag outcomes; a progress flag does not prove that every player received chat/audio.
3. Keep world writes, trigger receipts, flag outcome receipts and media claims separate enough to resume a partly completed operation safely. Bounded audit history is insufficient as the only deduplication ledger. If evidence cannot be saved, stop that operation and expose recovery instead of silently losing progress.
4. Adapt flag-only execution consistently: definition validation, review, automatic dispatch, recipient validation, serialization and recovery. It needs no media audience while preserving its active-play/participant trigger eligibility. Do not accidentally relax unrelated media/world execution rules.
5. Persist countdown progress and eligibility transitions; do not infer unlocked time from a flag's current value or from the old `armedAtTicks` field.
6. Completion evidence can arrive after an originating action changes phase or holds the game. In particular, a final departure can end play before its completion routine finishes. Recommended authoring is a dedicated silent **Departure completed** Event recording `ship_departed`, with reactions in separate dependent Events. A pre-armed, matched, trusted progress-only Event may finalize that receipt/flag patch after ENDED. A matched mixed Event still waiting to begin when play has ended is recorded as **Skipped: play ended**, with no effects or flag patch and no permanent game hold. Show that policy when configuring a departure trigger. Already-started operations retain their own recovery rules. No new chained world/media effects run after ending; the public manual flag setter keeps its permission/phase checks. This is a deliberate proposed completion rule for review.
7. Version the strict schemas/codecs and preserve existing flags, completed Event history, immutable archives and in-flight execution fingerprints. Use version-aware decoding/verification and preserve the actual old archive bytes, including embedded configuration hashes; do not rewrite them to a new shape. Test schema-2 in-flight executions as well as old root schemas. Old definitions receive no flag patch. Preserve timers without flag prerequisites. Existing gated timers have no reliable stored unlock time and need an explicit review/rearm migration rather than an inferred countdown or immediate firing.
8. New rules observe future gameplay occurrences. Persist source registration/arming cursors and matched occurrence receipts, pinned to the configured Event revision. Enabled, armed Events can reconcile newly accepted occurrences after a crash; rules added later cannot consume older evidence automatically. Occurrences produced while disabled or through paused authoring are not queued for future narrative execution. A valid operation accepted in active play can finish while held, using its durable origin evidence. Check and pin prerequisite eligibility when matching a discrete occurrence; subsequent readiness changes do not resurrect a missed occurrence. Flag-condition Events continuously test their gate instead. Rearming observes future occurrences and does not manufacture a second first discovery. Importing historical book/departure/Heart evidence should be an explicit reviewed GM operation.

## Repository integration map

| Surface | Existing evidence / affected symbols |
| --- | --- |
| Core flag transaction | `campaign/CampaignService`, `CampaignState`, `CampaignStore`; existing `EventOutcome` and atomic legacy `trigger`. |
| Configured Event effects and requirements | `play/EventDefinition`, `EventConfiguration`, `EventExecution`, `EventJson`, `PlayState`, `PlayService`, `PlayStore`; `menu/EventEditorForm` and JSON codec. |
| Execution/countdown | `server/EventRuntime.save`, `automatic`, `begin`, `finishMedia`; `PlayRuntime` active occupied clock/eligibility. |
| Native authoring | `client/EventEditorScreen`, `server/CoreEventMenu`, `CoreMenuServer`, flag relationship details and matching menu protocol. |
| Durable Core sources | `Departures.drive`, `LastingBargains` / `OfferingStats` purchase receipts, `BookDeliveryRuntime.observeInventory` recording discovery through its delivery service, `WorldAreaOperations` COMPLETE, `EventWorldBatches` / `EventBatchFiles` WORLD_COMMITTED, `RoleFirstEntry` receipts. |
| Sources needing stronger result evidence | `ItemEnchantingRuntime.complete` currently invokes a cosmetic completion callback without a durable work-result receipt; generic bound block interactions need explicit semantics. |
| Quiet Below source providers | `content/AncientAltars`, `AncientForges`, `ecology/QuietBelowEcology.destroyHeart` and `EcologyEngine.ending`; ecology transition boundaries. |

## Proposed implementation sequence

1. **Review this behavior/UI plan.** The organizer has selected a full countdown reset when any prerequisite turns Off; the remaining plan still awaits review. A dedicated native UI design/review precedes editor changes, as established for this project.
2. **Event progression foundation in Core:** multi-flag All prerequisites, atomic flag outcomes including silent Events, provenance/relationships and migration/recovery. Prove Region entry -> set flag -> another Event allowed.
3. **Prerequisite-started timers in Core:** persist activation/elapsed progress, phase/occupancy guards, restart and deliberate rearm. Prove two prerequisite Events -> full delayed Event, with no save-time countdown credit.
4. **Existing system triggers:** departure, selected block interaction/activity, successful Altar prayer, successful Forge enchantment, separate lore discovery/opening and Region state applied. Connect durable success evidence and ensure final departure completion records its fact even if it concludes play.
5. **Quiet Below progression providers/defaults:** ecology milestones and Heart ending, optional recommended Event wiring. Do not impose session-specific story beats. Final prose/lore balancing and handcrafted map integration remain their existing review tasks.

Foundation and timer slices can be reviewed together. System integrations can form one subsequent combined review; a separate client/world per source is unnecessary. Progress is gated by design decisions and meaningful human checks, not merely by the existence of a built feature.

## Acceptance and verification

- Unit tests: all prerequisites, atomic On/Off patches, duplicate operation IDs and later overrides, conditional activation, full timer reset when any prerequisite turns Off (including Off -> On between dispatch ticks), arming/edit/rearm, schema1/2 reads and archive/in-flight compatibility.
- Native chain: first eligible City visit sets only the configured flags; opening/previewing/editing does not; all required flags start a full countdown; a successful outcome can unlock a later Event.
- Native clocks: pause, Building Mode, empty play, holds, disconnect/reconnect and restart preserve the chosen countdown semantics without downtime credit.
- Native sources: departure cancellations/refusals vs completion; selected block interaction vs cancelled/automatic changes; Altar menu/refusal vs purchase; Forge scraps/capped unchanged gear vs actual enchantment; book inventory discovery vs gameplay opening vs GM/editor preview, with wrong-book/spoofed identity rejected; template capture vs state apply; Heart ended vs cleanup complete.
- Recovery: interruption before/after world, flag patch and media checkpoints; no duplicate/reverted flag writes or media replay; final-departure metadata finishes without triggering ended-world actions; historical import stays explicit.
- UI: shared Add pickers, compact/wide frames, flags' linked Event navigation, existing two Events filters, Clean/Debug, stale authority and ordinary-player denial.
- Use the workspace Java environment for `gradlew.bat --no-daemon build`; run `python scripts/event_editor_smoke.py`, `python scripts/events_smoke.py`, `python scripts/event_recovery_smoke.py` and the affected departure/bargain/forge/lore suites serially on their shared test ports. Exact harness extensions follow implementation, not fabricated passing claims in this plan.

This planning pass reads source and records the proposal only. It does not change code, data schemas, runtime settings, the running dev62 review server or its client pack.
