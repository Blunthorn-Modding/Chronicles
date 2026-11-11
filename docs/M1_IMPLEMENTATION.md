# M1 — sessions and persistent campaign state

## Scope and architecture

Minecraft 1.21.10, Fabric and Java 21 remain pinned. This slice adds operator-controlled sessions, boolean story flags, campaign event registration, and a chronological audit. Profiles, client screens, story clues, custom content, world transformation and automatic progression remain later slices.

`ChronicleCore` registers commands and server lifecycle hooks. `ChronicleRuntime` loads one `CampaignService` per running world on the server thread. Pure Java campaign records/services validate transitions and event outcomes; `CampaignStore` persists the state in `<world>/chronicle/campaign-state.json`. Successful mutations write before publishing their new in-memory state. Invalid transitions, handler failures, and failed saves do not commit changes. The previous valid document is retained as `.bak`. Malformed, unsupported or oversized data disables campaign commands instead of resetting the campaign or preventing ordinary Minecraft from starting.

The public `/chronicle status` and `/chronicle session status` show only phase and number. All session mutations, flag access, event commands and history require vanilla operator level 2. The command tree and mutation execution both check permissions. Existing live account permissions are preserved during deployment.

## State and event contract

Initial state is inactive, number 0. Start defaults to session 1; explicit Session 0 is allowed initially. A new session can start only while inactive or ended; subsequent numbers must increase. Pause requires active, resume requires paused, and end accepts active or paused. Events and flag changes require an active session. On 19 October 2026 the operator changed the pause policy: vanilla world simulation now freezes for inactive, paused, ended or unavailable campaign state; players remain mobile and can edit cards. Active sessions unfreeze it. A repeating actionbar displays the phase/number and frozen status. See [follow-up specification](M2_IMPLEMENTATION.md).

Campaigns register named events before server start with a description, required boolean flags, a once-only setting and a handler. M1 handlers return planned flag changes and a message; they do not change worlds or external systems. Preview checks metadata/eligibility without invoking the handler. The Quiet Below supplies a harmless once-only `quiet_below:readiness_check`, setting `quiet_below:readiness_checked=true` to prove the complete registration/persistence path.

State schema 1 stores session phase/number, namespaced boolean flags, completed event IDs, audit history, and the next audit sequence. Successful mutations record the server-derived actor name/account UUID (or console identity), UTC timestamp, session number, action, target and parameters. No arbitrary event arguments are accepted in M1. History retains the latest 1,000 entries; commands display at most 50 per request. State is limited to 2 MiB, 256 flags, 1,024 completed event IDs and session number 1,000,000. Operator history does not transmit campaign details to ordinary players.

## Acceptance and validation

- Compile both distributable mods and their client source sets; run focused JUnit lifecycle/persistence/failure-path tests.
- Start a real dedicated server with both Chronicle mods, Fabric API and voice chat in a fresh isolated localhost world.
- Connect a test GM and an ordinary player; reject ordinary-player administration without changing persistent state.
- Exercise valid/invalid session transitions, flags, preview, one successful once-only event and duplicate rejection.
- Pause and restart; verify number, phase, flags, completion and audit actor/sequence persisted. Resume/end and begin the next numbered session.
- Before live deployment, stop/save and back up the play server. Archive the prior Chronicle jars, install the tested jars, and restart the preserved world.
- Keep manual Fabric UI and human voice checks distinct from command/protocol tests. Do not claim future milestones complete.

Execution evidence is recorded in TEST_PLAN.md after the checks pass.
