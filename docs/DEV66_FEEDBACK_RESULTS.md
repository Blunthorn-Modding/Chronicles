# Dev66 organizer feedback repairs

2 November 2026. Branch `fix/dev65-authoring-altar-recovery`. The [agreed scope](DEV65_FEEDBACK_PLAN.md) is implemented. Core owns encounter recovery, typed refusal/cooldown authority and isolated scenario profiles; Quiet Below supplies its five prayers, source policy and explicit empty-content mechanics support.

## Resulting behavior

- Retained encounter plans can be inspected and deliberately retried or skipped while play is paused. Recovery preserves exact original plans and never silently relocates or respawns uncertain entities. New Region plans search loaded clear full footprints; a Region including its ground is valid if enough space remains above it. Fixed Point placements are reserved before flexible Region placements.
- The Altar shows all five prayers together. Ordinary gameplay refusals cost 2 HP and privately explain the refusal in the scenario's voice. Stale/duplicate requests and technical failures cause no damage. Successful prayers share a captured, persistent 900-active-second cooldown across Altars. Permanent prayers retain three purchases each; repair/sight cannot stack; sheltering grace can repeat after cooldown. Quiet Harvest is removed from new offers, with paid legacy claims honored. One **Altar offerings** configuration entry retains guarded recovery.
- Scenario creation/export has immediate pending feedback and persistent acknowledged results with a copyable server file path. Empty custom profiles use a separate dedicated server/world/configuration and retain installed mechanics. Existing editors save normally there. Profile exports seed fresh servers, while same-identity restarts preserve local edits and deletions. No installed JAR or original campaign is rewritten. See [workflow](SCENARIO_CONTENT_EXPORT.md).

## Verification

Offline build and both remaps pass. Java results: 735 tests, 728 passed and seven existing Windows symlink-privilege skips; zero failures/errors. Python discovery passes 121 tests, including actual helper-start control flow with a mocked child and same-identity export update. Native JVMs/clients below exercise production server and transport paths in disposable fixtures.

| Area | Evidence under `../reports/` | Proven scope |
| --- | --- | --- |
| Encounters | `dev66-encounter-final-status.json`, seven `encounters-*` runs listed there | Region/Point ordering, shared Region placement, zero/partial-plan restart, blocked retry/skip and uncertainty review |
| Five-prayer UI | `temporary-altar-smoke-20261102T132737151955Z` | Compact/wide real C2S, refusal damage, expiry/repeats, duplicate and technical no-damage checks |
| Temporary authority | `temporary-altar-smoke-20261102T132900871857Z` | Death/reconnect/restart, paused/downtime clocks, captured cooldown, expiry and explicit uncertainty recovery |
| Permanent regression | `bargain-smoke-20261102T133244493114Z` | Six purchases, limits, death/reconnect/restart and optional HUD |
| Scenario lifecycle | `scenario-profile-20261102T140525377527Z` | Actual create/export acknowledgement, empty isolated startup with mechanics, authored content, updated-profile restart, fresh import, edit/deletion preservation; source-world hashes unchanged |
| Existing role/export regression | `player-roles-20261102T141030348386Z` | Normal role/item/lore UI, deaths/spawn overrides, Event-source history, visible export result, fresh bundled-content copied-map restart; original world hashes unchanged |

Independent source reviews are clear after fixed-Point reservation and same-identity helper restart corrections. The lead inspected the integrated diff and native screenshots at GUI scale 2. Earlier failed reports remain intact: Peaceful hostile despawns and stale fixture menu frames were corrected in test setup; an early tool-registration startup bug was fixed in production. No new three-hour soak was needed for these focused changes.

On 3 November the organizer passed the combined review: retained Event recovery and encounter spawning, the Altar, custom authoring, export and import work. The sole follow-up corrects the ambiguous export refusal: play must be paused or stopped, while Editing must be enabled. Scenario switching remains a deliberate server/configuration operation, as accepted by the organizer. Final handcrafted-map integration, final journals/content, group/balance/voice gates, bundled custom sounds and quiet-period history scheduling remain tracked in [Outstanding work](OUTSTANDING_WORK.md).

The wording-only follow-up passed `gradlew.bat --offline --no-daemon :core:compileJava` (44 seconds) and `git diff --check`. No conditions, permissions or persistence changed, so no additional gameplay suite/review was required. It is a source correction for the next server update; the installed dev66 review artifacts remain unchanged.

## Review deployment

Authenticated review is live at `127.0.0.1:25589`, voice24469, preserving the original world and exact blocked encounter journal. Separate empty authoring review is live at `127.0.0.1:25590`, voice24470. Both retain SchinderRenge OP4. All75 protected world/config/access files were unchanged during the JAR swap; the original stopped world backup, prior JARs and prior client pack remain available. Both servers report zero forced chunks and no native test command; startup logs have no errors. Authenticated human join and audible voice have not been tested.

The refreshed matching client pack includes both live presets and the existing GUI/narrator/onboarding/FancyMenu/license defaults. Its SHA256 is `0e1d9c70041a1eb15e1a30cadfb3d503bbbfd467836fb2f80aa9c0c892444fef`. Exact artifact hashes, live PIDs/PTYs and checks are in `../reports/dev66-validation.json` and `combined-dev66-deployment.json`. No main merge or Modrinth publication was performed.
