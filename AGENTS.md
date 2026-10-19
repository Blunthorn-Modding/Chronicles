# Chronicle workspace

Read `docs/CHRONICLE_PROJECT_OVERVIEW.md`, `docs/DECISIONS.md`, and `docs/TEST_PLAN.md` before changing architecture. The original overview is a brief, not implemented behavior.

- Source/configuration/scripts belong in this repository. Its parent `MC RP` contains portable tools, caches, server worlds, client packs, backups, reports, and disposable test worlds. Keep generated runtime data outside Git.
- Target Minecraft Java **1.21.10**, Fabric, **Java 21**. Pins live in `bootstrap.lock.json` and `gradle.properties`; update together after compatibility checks.
- Two mods: `core` (`chronicle_core`) and `campaigns/quiet-below` (`quiet_below`). Client code belongs under `src/client`; common code must never import `net.minecraft.client`.
- M1 provides sessions/persistence/flags/events/audit and native tick freezing/actionbar. Only ACTIVE unfreezes simulation; players stay mobile. M2 adds public owner-editable dossiers. M3 adds the lens/marker asset pipeline; M4 keeps bounded stages and journals in the campaign package. M5's separate pilot awaits group playtest. Read each milestone specification; do not generalize campaign stages into Core.
- Commands from repository root: `python scripts/workspace.py setup`, `build`, `deploy`, `verify`, `smoke`, `backup`, `doctor`, `client`. PowerShell: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/workspace.ps1 <action>`.
- When leaving the play server running through `exec_command`, use `tty: true` so console input stays available. Use `write_stdin` for Minecraft console commands and `stop` for a saving shutdown. Verify the ready log before handing it back.
- Offline checks: `python -m unittest discover -s scripts -p "test_*.py" -v`; `git diff --check`. Build uses child-process-only workspace JAVA_HOME/GRADLE_USER_HOME.
- `smoke` creates a fresh `../test-runs` world, uses loopback TCP 25575/UDP 24455, and runs two matching offline Fabric development clients plus a content probe. It launches graphical windows and needs a desktop/OpenGL context. `content-smoke` runs only the M3 content probe; `legacy-smoke` retains historical Mineflayer checks that cannot join M3 custom registries. Never change the play server's authentication for a test.
- `stage-smoke` rehearses bounded stages; `pilot-smoke` tests a disposable copy of the latest prepared pilot. Run suites sequentially on their shared test ports. Pilot preparation/start use fresh `../pilots` roots and authenticated loopback TCP25585/UDP24465; never build their map in the play world or force-load chunks for stages.
- EULA acceptance belongs to the operator; never toggle eula=true without explicit authorization. Never read/copy launcher tokens. Real Fabric rendering, GUI, audio and account tests remain distinct from bot checks.
- Do not open firewall/router ports, publish, push commits, change machine-wide settings, install hosted agents, or expose a server unless requested. Tests need no external LLM service.
- Preserve existing configs/worlds. Stop before deploying or backing up. Restore into a fresh directory and inspect before promoting. Never recursively clean worlds as a routine fix.
