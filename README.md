# Chronicle

**Current dev69:** the obsolete Readiness Check and Mine Whisper prototype actions are removed from the normal game. The nine authored Events and automatic flag/setup behavior remain. See the [cleanup results](docs/PROTOTYPE_EVENT_CLEANUP.md), [starting-content guide](docs/FINAL_DEFAULTS_GUIDE.md) and [remaining work](docs/OUTSTANDING_WORK.md).

**Editor workflow:** [menu and command normalization](docs/EDITOR_COMMAND_NORMALIZATION.md) and [command reference](docs/COMMAND_REFERENCE.md). **O** or `/chronicle` remembers your page within the current connection; every item/book Add action uses the same popup.

Use the [matching dev69 review pack](../client/Chronicle-0.1.0-dev.69-1.21.10.mrpack) and join **127.0.0.1:25591**. The existing review at **25589** and custom authoring server at **25590** retain their worlds/configuration. All three addresses are included in the pack. The organizer accepted the default content and sounds; handcrafted-map/group testing remains. Earlier dated checkpoints below describe historical builds.

## Historical development checkpoints

The following checkpoints describe earlier builds. Use the dev58 workflow and command reference above for current behavior; retired commands in these notes are historical.

**27 October dev57 editor:** [Region and Location subtypes](docs/EDITOR_SUBTYPES_IMPLEMENTATION.md) consolidate saved states under Regions and departures under Locations while preserving one Departure setup popup. Every Region uses the Debug shovel/two corners and `/chronicle regions save`; Saved states is optional. Player Roles separates assigned supplies from Add item. Read the [combined review checklist](docs/EDITOR_SUBTYPES_REVIEW.md). Earlier dated checkpoints below are historical.

**26 October revised design:** the [Core roles, departures and area-change plan](docs/CF19_CF21_IMPLEMENTATION_PLAN.md) gives **Player Roles**, **Departures** and **Area changes** their own Advanced navigation entries. Area changes captures Start/End states of the same bounds through a private in-world shovel/position-command workflow, supports later recapture and deletion with a state choice. Review the [updated UI mockup](docs/design/roles-departures-transitions.html) before implementation. These features are not installed yet: accepted dev54 remains at **127.0.0.1:25589**, with its world and matching pack preserved. Earlier checkpoints below are historical.

**Combined review, dev.47:** paused play now freezes player survival and world-changing interactions while allowing movement and explicit global Building Mode for everyone. Metadata editing remains operator-only. The Heart's last stand spreads across a surface-adaptive5x5 footprint. Block limits lives solely in Game setup. The editor uses native popup/widgets, optional Debug details and clearer wording; ACTIVE status fades after one message. [Review these together](docs/M12_COMBINED_REVIEW.md).

Install the refreshed [matching client pack](../client/Chronicle-M9-1.21.10.mrpack), then join authenticated **127.0.0.1:25589**, currently **PAUSED**. The world, tuning and access settings were preserved through a stopped verified backup. 385 Java/77 Python tests, native placement/restart/menu/layout checks and the exact saved-world Heart regression pass; independent reviews are clear. Human review precedes M13 handcrafted-map/group readiness. Older dated paragraphs below are historical.

**Combined correction review, dev.40:** covered Heart-connected sculk stays nourished beneath stations and other sculk; surface exposure still governs growth. The approved Forge layout now accepts one item per offering, consumes wrong pairs, and returns only unchanged equipment for valid already-maxed pairs. The refreshed [matching pack](../client/Chronicle-M9-1.21.10.mrpack) suppresses FancyMenu's welcome/documentation prompt and adds the running review server without overwriting an existing server list. One authenticated review at **127.0.0.1:25589** is **PAUSED**, with the current world, tuning and both accepted prayers retained after a verified stopped backup. [Review all three together](docs/M11_COMBINED_REVIEW.md), [implementation contract](docs/M11_COVERED_SCULK_AND_OFFERINGS.md), [Core transactions](docs/CF11_FORGE_WORKSTATION.md) and [exact checks/limits](docs/TEST_PLAN.md). Other [corrections](docs/POST_M12_REVIEW.md) remain sequential. Earlier deployments below are historical.

**Sequential feedback correction, dev.32:** the first correction addresses Hunger/Darkness continuity through jumping and walking over floor veins. [Contact review and separate server](docs/M10_CONTACT_REVIEW.md). The [remaining feedback queue](docs/POST_M12_REVIEW.md) is tracked one item at a time; Altar/Forge workstation screens will receive a dedicated design-agent pass before implementation. Earlier dev.31 ending-review details below describe the preserved prior world.

**Current review, dev.31 (22 October):** the Relic Heart now weakens, offers an explicit destruction confirmation, and permanently ends its network. Cleanup restores recorded terrain, includes detached branches, and removes both Altar bonuses and sacrifices. The default is fewer living connected cells than Established for 60 active seconds, configurable in Game setup. Unresolved or unloaded branches prevent vulnerability. [Bundled review and matching client pack](docs/M12_REVIEW.md), [Heart contract](docs/M12_HEART_ERADICATION.md), [Altar contract](docs/CF12_LASTING_BARGAINS.md). Authenticated **127.0.0.1:25588**, PAUSED; map/tuning preserved through a verified stopped backup. 309 Java and 70 Python tests plus native Heart/Altar/Forge regressions pass. The refreshed classic pack retains official HUD references and license notices. Earlier dated paragraphs record historical delivery; M13 still requires human gameplay review and the handcrafted map.

**Ancient Forge, dev.29:** place the supplied Forge beside living Established sculk, hold a target in the main hand and an ingredient in the offhand, then confirm the displayed trade. [Recipes, power and workflow](docs/M11_FORGE.md) are implemented and natively verified, alongside permanent Core enchantment caps. The authenticated review is live and paused at **127.0.0.1:25588** with its map/tuning preserved and a matching refreshed client pack. [Review and backup details](docs/M11_REVIEW.md). Altar gameplay remains implementation work.

**Core enchantment caps, introduced dev.28 and deployed with dev.29:** server-wide configurable maxima permanently lower stored levels on gear/books, including native enchanting, anvils and loot. Core alone defaults unrestricted; Quiet Below recommends level I on first setup. See [configuration and persistence](docs/CF11_ENCHANTMENT_CAPS.md). Native save/restart checks and258 Java/60 Python tests pass. A stopped verified backup precedes the existing-review-world upgrade; raising caps cannot restore removed levels.

**HUD integration prototype:** selected SpiffyHUD/FancyMenu and dependencies are pinned as official Modrinth download references, with [license/distribution rules and test commands](docs/THIRD_PARTY_HUD.md). The separate prototype pack preserves the current review pack; expanded hunger and Altar gameplay remain implementation work.

**Development build: contact costs and growth modes (M10), dev.27**, on `feat/qb09-contact-costs-and-growth-modes`. Hunger/Darkness now wait once on living sculk, then persist until you step off. Spread can use a fixed rate or configurable amplification from living sculk count. [Review instructions](docs/M10_REVIEW.md) cover the authenticated **localhost:25588** bench. The existing map/tuning are preserved through a stopped backup; [verification evidence](docs/TEST_PLAN.md) records build and native results. [Client pack](../client/Chronicle-M9-1.21.10.mrpack).

Core owns the generic Game setup UI; Quiet Below owns physical growth, vitality, benefits/costs, tuning and its journal. Costs require direct footing on owned, rooted sculk at25%+ vitality, including floor-facing veins; a non-sculk pillar escapes them. Established growth grants Hunger I, Extensive also Darkness, each after its independent default40-second initial wait. Foreign effects are preserved; our transient effects are withdrawn on exit and excluded from player saves. Healing/crops retain their independent nearby reach. [Contact/growth contract](docs/M10_CONTACT_COSTS.md), [city-scale evidence](docs/M9_CITY_SCALE.md). M9 reliability and prolonged operations, Forge/Altar mechanics and the Heart ending remain open.

Chronicle's planned campaign formats are **Survival, Journey, Werewolf and Situation** (working name for “Happening”). [The format guide](docs/CAMPAIGN_FORMATS.md) defines their session/world structures and proposes Operation and Intrigue as optional additions. The Quiet Below is the first Situation campaign; the four formats are design direction, not four installed modes.

A Fabric roleplay campaign framework for Minecraft Java 1.21.10. **Chronicle Core** and **The Quiet Below** provide persistent sessions, simulation freezing, hotbar status, public character dossiers, custom investigative content and bounded environmental stages. The [pilot runbook](docs/PILOT_RUNBOOK.md) prepares the first group playtest.

**Current direction — 19 October 2026:** follow the [M6–M13 engineering milestones](docs/DEVELOPMENT_MILESTONES.md), [Quiet Below design](docs/THE_QUIET_BELOW.md), [handcrafted-map tools](docs/MAP_AUTHORING.md) and [tracked backlog](docs/BACKLOG.md). The operator authorizes implementation until a necessary decision or hands-on review. There is no prescribed event/session outline. The operator builds the production map; generated pilot worlds and their marker/lens lore remain technical fixtures.

The revised draft proposes tangible crop-growth, local-healing and Ancient Forge benefits as connected sculk gains power, with environmental costs. Mine-stabilization physics is withdrawn. Authoring uses shared **Chronicle Core in-world tools**: two-corner regions, exact block/location bindings, chest profiles and spawn points. See the [local Vampire SMP reference findings](docs/REFERENCE_VAMPIRE_SMP.md). Crop growth, passive healing and contact-based local costs are implemented review slices; forge mechanics remain design work; Core geometry, typed loot and manual spawn tools are implemented below.

The latest [Quiet Below revision](docs/THE_QUIET_BELOW.md) removes authored growth routes and scheduled benefit reveals: sculk follows actual caves/mines from the city, with catalysts/sensors and possible later shriekers. Builders register key sites; the ecology discovers its path. The Ancient Focus is dropped. Destroying a sufficiently weakened Relic Heart provides a full-network eradication ending alongside containment, coexistence and departure. These are design requirements, not installed gameplay.

**M6 adds shared Core location authoring:** select two corners, save/replace regions, record precise locations/facing, target block anchors and inspect private outlines directly in-world. See [commands and the builder review checklist](docs/M6_IMPLEMENTATION.md). M6 saves metadata without changing terrain or activating story progression; M7 adds explicit content/player-move actions below.

**M7 chest revision:** the operator accepted persistent outlines and the registered chat menu, and requested typed randomized loot instead of capture-first authoring. Look at a container, run `/chronicle author chest` and choose its type in one step. Campaigns register default loot distributions and recommended chest counts; edit `config/chronicle/chest-loot.json` to override them. Entries roll independently by percentage. Managed loot chests replace all contents at each new session and optionally every configured active interval; pause/resume/restart preserve the clock and cycle history. See [the current builder workflow](docs/MAP_AUTHORING.md) and [the revised contract](docs/M7_LOOT_TYPES.md). Captured profiles remain a legacy compatibility path. Entity encounter groups remain pending; Quiet Below ecology remains dormant.

**Core menu (CF-10):** press **O** (rebindable), or run `/chronicle`. Use Session, Players and Events during play; advanced Chests, Loot Tables, Regions, Locations and Game setup are separate. Enable editing while play is non-active; status comes from the server and clears on reconnect/ACTIVE. Chests binds the container you look at to a table; Loot Tables adds registry items and edits independent chances/counts before a whole-table save. Use the [current command reference](docs/COMMAND_REFERENCE.md); older command aliases are retired. See [the builder guide](docs/MAP_AUTHORING.md).

Automatic player destinations live under **Players**, on the selected account. Starting position and after-death destination are independent opt-ins over saved point locations; defaults remain off. Starting-position claims persist across reconnects and meetings until explicitly reset. Assigning a destination moves nobody immediately, and each player's settings use private bindings. Unsafe/stale destinations retain the player/vanilla respawn result; beds and anchors remain intact. `/chronicle players leave` removes the caller's participation and automatic destinations. See [the policy guide](docs/M7_AUTOMATIC_SPAWNS.md). Entity encounter groups remain separate work.

**Participation and optional events:** use **Players** to enroll accounts, or opt in yourself with `/chronicle players join`. Shared active time pauses with the session, empty play, spectators, author walks and operation holds. **Events** presents message/sound cues separately from game-state actions and conditions. Content and private audio previews work before region setup, even while inactive; live audience checks/runs remain separate and use durable once-only delivery. No cues or timetable are enabled by default. See [the runtime contract](docs/M8_IMPLEMENTATION.md). Quiet Below supplies two optional cue examples; ecology remains M9 work.

## Workspace and setup

```text
MC RP/
  Chronicles/       Git repository: source, lockfile, scripts, docs
  tools/           Portable Java 21 and Gradle
  cache/           Verified downloads and Gradle caches
  server/          Authenticated, whitelisted play server; persistent world
  client/          Generated Modrinth client pack
  test-runs/       Separate offline localhost worlds for automated tests
  pilots/          Separate authenticated pilot worlds, loopback port 25585
  Blockbench/      Operator's original designs; retained unchanged
  backups/         Stopped-server backups
  restores/        Separate recovered snapshots
  reports/         Smoke logs and latest JSON result
```

On Windows x64, install Python 3.11+ and Node.js 22+ with npm, then run from `Chronicles`:

```powershell
python scripts/workspace.py setup
python scripts/workspace.py build
python scripts/workspace.py deploy
python scripts/workspace.py verify
python scripts/workspace.py doctor
```

Scripts install Java/Gradle/Fabric inside the parent workspace without admin access or permanent PATH changes. Initial setup/build need internet. Modrinth supplies Fabric API and Simple Voice Chat; official providers supply Java, Gradle, Fabric Loader, and Minecraft. Downloads use pinned hashes. Setup preserves existing server properties/EULA choices and requires a stopped server. It refuses to overwrite another Minecraft runtime or accept stale managed mod versions; deploy/pack also reject Chronicle jars built for another game version. Updates are explicit: review `scripts/resolve-bootstrap.ps1`, align `gradle.properties`, then migrate the stopped runtime deliberately.

PowerShell wrapper (policy override applies only to this process):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/workspace.ps1 doctor
```

## Start and join

Review the [Minecraft EULA](https://www.minecraft.net/eula). Only the operator can accept it:

```powershell
python scripts/workspace.py setup --accept-eula
python scripts/workspace.py start
```

Use `stop` in the server console for a clean shutdown. The play server initially listens on **127.0.0.1:25565**, uses online account authentication, and requires a whitelist. In its console run `whitelist add YourMinecraftName`; optionally `op YourMinecraftName` for GM access. The server must be able to resolve that account through Mojang. `/chronicle session status` itself needs no operator privilege.

Import `../client/Chronicle-M9-1.21.10.mrpack` into Modrinth App or another `.mrpack` launcher. Authenticate your own licensed Minecraft account there; scripts never access tokens. The pack pins Minecraft/Fabric, Fabric API and voice chat, and includes both locally built Chronicle jars. In Minecraft Java, choose **Multiplayer → Direct Connection** and enter **`127.0.0.1:25565`**, or use **Add Server** to save that address. The dedicated server does not automatically appear in the server list. Use the explicit IPv4 address to match its loopback bind. Keep the server process running and whitelist your account before joining. No global launcher install or automatic account login is performed.

For development, `python scripts/workspace.py client` starts Loom's Fabric client with both Chronicle modules. It uses a development identity: join an isolated offline test server, not the authenticated play server. Its run directory is `campaigns/quiet-below/run`. Simple Voice Chat is installed on the server and packaged launcher client; it is not a mod compile dependency or automatically included in Loom's client.

Remote friends cannot join loopback defaults. Before hosting, deliberately configure bind addresses, invited-account whitelist, backups and firewall/VPN/router access. Voice chat uses UDP **24454**, separate from Minecraft TCP. This bootstrap opens no ports or always-on services.

## Automated checks

```powershell
python -m unittest discover -s scripts -p "test_*.py" -v
npm --prefix testing/minecraft-bot test
python scripts/workspace.py build
python scripts/workspace.py verify
python scripts/workspace.py smoke
python scripts/workspace.py stage-smoke
python scripts/workspace.py menu-smoke
python scripts/workspace.py ecology-smoke
python scripts/workspace.py warden-smoke
python scripts/workspace.py deploy
```

Smoke needs prior EULA acceptance and a desktop/OpenGL context. It overlays built jars in a fresh test world, starts the dedicated Fabric server with all four mods, and connects a **matching Fabric test GM and ordinary player** in two graphical windows. It checks permissions, session/freeze behavior, flags/events/audit and actual dossier packets, then restarts and checks persistence. A third client checks survival placement/mining/loot, clues and rendered models. Results are in `../reports/smoke-latest.json`. `content-smoke` runs only that content check; `stage-smoke` separately rehearses preview/apply/pause/restart/conflict/undo and writes `stage-latest.json`. Test TCP 25575/UDP 24455 must be free, so run these suites sequentially. Logs/screenshots remain for diagnosis. Disposable test worlds can be removed once complete unless a concrete follow-up needs them; save-stop and verify exact owned paths first. Production/handcrafted maps are preserved. The play server's authentication, operators and world are never test fixtures.

`ecology-smoke` covers source growth, restart, containment, organic patches and a real-time dormant/reconnected vitality trial. `warden-smoke` uses a separate hard-difficulty fixture for actual vanilla warnings 1-4, emergence, quiet digging and natural despawn; it does not inject warning levels or shorten cooldowns. Both rebuild the remapped server artifacts before staging, retain evidence under `../reports`, and share the test ports above.

The tests need no AI credentials or launcher tokens. Custom M3 registries require matching Fabric clients; historical `legacy-smoke` Mineflayer clients cannot join this build and fail explicitly. The Node parser tests remain useful historical checks. Use the [manual checklist](docs/TEST_PLAN.md) for licensed-launcher import, group play and microphone/audio. Jars are `core/build/libs/chronicle_core-0.1.0-dev.58.jar` and `campaigns/quiet-below/build/libs/quiet_below-0.1.0-dev.58.jar`. Players install both mods plus the pinned Fabric API and voice chat jars.

New client imports also receive GUI scale 2, narrator off, accessibility onboarding skipped and the multiplayer warning acknowledged. These initial preferences come from `config/client/options.txt`, packaged in `client-overrides/options.txt` using the [Modrinth pack format](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack). They are editable in Minecraft; no mod forces them each launch. Existing profiles can retain their own options when replacing the jars.

## Campaign controls (M1)

`/chronicle session status` is public. Administration requires operator level 2; the server console can use the same commands without the leading slash. Grant GM access deliberately using `op <username>` in the console. Deployment does not grant players operator permissions.

```text
/chronicle session start [number]
/chronicle session pause
/chronicle session resume
/chronicle session end
/chronicle events flags list
/chronicle events flags get quiet_below:example
/chronicle events flags set quiet_below:example true
/chronicle debug events list
/chronicle debug events preview quiet_below:readiness_check
/chronicle debug events trigger quiet_below:readiness_check
/chronicle debug events history [limit]
/chronicle session history [limit]
```

Start defaults to session 1. Explicit session 0 is supported for preparation. After ending, the next number must increase. Flags and events can change only during an active session. **World simulation and survival interactions freeze whenever play is inactive, paused, ended or unavailable.** Players remain mobile and may chat and edit character cards. Explicit global Building Mode allows everyone to build during non-active play. Starting/resuming restores play; the active status appears briefly, while paused status remains visible. The readiness event sets a saved flag without modifying blocks.

State lives in `<world>/chronicle/campaign-state.json`. Each successful change saves before becoming visible and records the actor, UTC time and sequence. The previous valid file is retained as `.bak`; history retains 1,000 entries. Corrupt, unsupported or oversized data disables campaign commands rather than resetting the campaign. Stop the server, preserve the damaged file, inspect the `.bak` or a complete server backup, and restore a verified matching snapshot before restarting. Session state restores as saved, including active or paused.

See [M1 architecture and acceptance criteria](docs/M1_IMPLEMENTATION.md).

## Character dossiers (M2)

Press **K** (rebindable in Controls) or use `/chronicle players card` to edit your public card. `/chronicle players card <online-player>` opens another player's read-only card. Fields are public name (48 characters), optional pronouns (32) and optional description (512), counted as Unicode codepoints. Text is plain, with surrounding whitespace removed. No secret fields are sent. Cards can be edited while simulation is frozen.

The screen saves only after server acknowledgement; errors preserve your text. If another edit changed the card, reopen it to refresh the revision before retrying. Long public cards can be scrolled with the mouse or keyboard. Text commands provide a fallback:

```text
/chronicle players card [online-player]
/chronicle players card set name Rowan Vale
/chronicle players card set pronouns they/them
/chronicle players card set description A patient mason who knows the old roads.
/chronicle players card clear pronouns
/chronicle players card clear description
```

Only your own card can be edited, including through packets; operator privileges do not add another-player edit command. Saves are limited to once per second and view requests to five per second. Card files are `<world>/chronicle/profiles/<uuid>.json`, with schema/revision/owner checks and previous-file `.bak` recovery. A missing card displays the account name without creating data. Invalid files remain untouched; stop the server and restore that owner's verified file before retrying. Backups include these files. See [M2 specification and checks](docs/M2_IMPLEMENTATION.md).

An opt-in development-client visual test uses `CHRONICLE_UI_SMOKE=<output-directory>` with `python scripts/workspace.py client`. It opens synthetic cards, checks resize draft retention, captures screenshots and exits without using an account. Check that `chronicle-ui-smoke.json` reports PASS; a normal game exit alone is not a test result. For the real save/acknowledgement test, additionally set `CHRONICLE_UI_SERVER=true` with a disposable offline server already running at 127.0.0.1:25575; this mode connects only to that test address. Unset both variables for normal development play. The graphical test is distinct from two-player packet/restart tests and human voice audio.

## Investigative content and environmental stages (M3/M4)

`quiet_below:resonance_marker` reuses the operator's ancient-symbol Blockbench relief. Place it on a floor or against a wall; mine it with a pickaxe to recover it. During an active session, right-click empty-handed for an observation or use `quiet_below:echo_lens` for more detail. The lens is not consumed. Both have survival recipes and creative entries; see [assets and recipes](docs/M3_IMPLEMENTATION.md).

The once-only `/chronicle debug events trigger quiet_below:mine_whisper` reveals a new observation at every marker, accessible without a lens. It records a flag and audit entry without changing blocks. It leaves the meaning and response to the players.

Environmental stages default to **no configured zones**. A level-2 GM explicitly approves exact natural-block coordinates, at most 64 loaded positions per zone. Back up first. For a selected small area:

```text
/chronicle game quiet-below zone define <id> <from-x> <from-y> <from-z> <to-x> <to-y> <to-z>
/chronicle game quiet-below stage preview <id>
/chronicle game quiet-below stage apply <id> <token-from-preview>
/chronicle game quiet-below stage status
/chronicle game quiet-below stage undo
/chronicle game quiet-below stage resume
```

Only stone, deepslate, cobblestone, cobbled deepslate, tuff and calcite can be captured. Defining a zone is approval of those coordinates; the mod cannot infer who built a stone wall. Preview lists every original state and target. Apply requires ACTIVE and unchanged captured blocks, replacing at most eight positions per tick with passive sculk. Containers/block entities, unloaded chunks, height/border violations and changed states are rejected. No catalyst, sensor, shrieker, automatic spread or forced loading is added. Pause/end suspends work immediately.

Only the latest operation is retained for undo; undo it before defining/applying another stage. Undo can cancel a partial apply, requires ACTIVE and preserves third-state player edits by refusing to proceed. Restore a conflicting position deliberately to its recorded original/sculk state before retrying. After a restart, an in-flight job is INTERRUPTED: inspect it, then explicitly resume or undo. Resume checks the entire template and reconciles from the beginning. Completed jobs are never silently reapplied. The strict journal is `<world>/quiet-below/stages.json` with `.bak`, up to 32 zones and 256 audit entries. Corrupt/future/inconsistent data disables stages without replacing it. Save failure after a world write halts with recovery required; preserve the journal/world and inspect a stopped backup. See [M4 guarantees and recovery](docs/M4_IMPLEMENTATION.md).

## Separate pilot preparation (M5)

```powershell
python scripts/workspace.py pilot-prepare
python scripts/workspace.py pilot-smoke
python scripts/workspace.py pilot-promote
python scripts/workspace.py pilot-start
```

Preparation creates a fresh runtime under `../pilots`, with the copied invited-account whitelist/operators, online authentication, loopback **127.0.0.1:25585** and voice UDP 24465. It never installs a map in the ordinary play world. The separate datapack has no auto-run hooks; its guarded `function chronicle_pilot:build` is only for first construction in a fresh pilot. `pilot-smoke` builds/checks a disposable offline copy using normal player chunk loading, sign-text checks, a survival stair walk and screenshots. `pilot-promote` copies the verified site into the still-virgin authenticated pilot, requiring stopped worlds, matching function/mods and a passing test; it refuses to replace an existing world and excludes test identities/cards. `pilot-start` runs only the latest validated pilot and retains its console for a saving `stop`. After stopping, `pilot-backup` creates a CRC-checked pilot snapshot. The ordinary `backup` command covers the play server only. `restore --archive <pilot-backup>` extracts into a separate inspection directory; never promote it into the ordinary play world by mistake.

The [pilot runbook](docs/PILOT_RUNBOOK.md) contains Session 0, five flexible session pressures, three written clues and optional public character hooks. M5 still requires a group playtest; automated checks cannot establish roleplay feel or human voice audio.

## Backup and recovery

Stop the server, then:

```powershell
python scripts/workspace.py backup
python scripts/workspace.py restore --archive "E:\Coding\MC RP\backups\server-<timestamp>.zip"
```

Backups include worlds, configuration, player/whitelist/operator data, mods, launcher jars and the dependency lockfile; caches/libraries/logs are excluded. CRC checks run after creation and before restore. Restore extracts to a fresh `../restores/<timestamp>` directory. To recover: stop the server; retain a backup of its current state; inspect the recovered world/config and matching mod versions; manually promote the corresponding files into the stopped server. Setup can reinstall missing libraries. Never merge two worlds' contents. For future version upgrades, take a stopped-server backup first and retain the original runtime until the upgrade is accepted; do not load an upgraded world with an older Minecraft version. The operator explicitly requested removal of the retired 1.21.1 installations and backups on 18 October 2026; those are no longer available. Current 1.21.10 backups remain. After a crash, inspect the child/controller PIDs in `.chronicle-process.json` and verify the world lock before removing that marker.

See [decisions](docs/DECISIONS.md) and [test plan](docs/TEST_PLAN.md) for scope and outstanding checks.
