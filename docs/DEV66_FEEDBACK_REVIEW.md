# Dev66 combined review

Use the matching refreshed `../client/Chronicle-M9-1.21.10.mrpack`. Review servers remain authenticated, whitelisted and loopback-only; SchinderRenge retains operator access. Saved server presets are included for currently running review endpoints.

## Original campaign: 127.0.0.1:25589

Your previous world and blocked Event are retained. The stopped world was backed up before deployment. In **Events**, select the retained failed Event and inspect its recovery description. Its old spawn plan is intentionally unchanged: either clear those exact spaces and retry, or deliberately confirm **Skip remaining encounter spawns**. An uncertain entity claim must be reviewed rather than retried. Resume play only after resolving the hold.

For newly authored encounter Events, a Region may include its ground; enough loaded clear volume must fit above it. Existing saved blocked plans do not move to newly selected spaces.

At the Altar, all five prayers appear together. Try a prayer without its required resource to see ordinary one-heart refusal damage and a private explanation. Then make a valid prayer: another prayer at either Altar must refuse during its shared cooldown. Production default is **15 occupied active minutes**; paused, empty, held and downtime periods do not count. Existing purchases and paid claims are preserved. Use **Game setup → Altar offerings** for the single consolidated configuration entry.

Open **Scenario content...**, select The Quiet Below, then **Export content**. It should show pending feedback followed by the acknowledged server file and **Copy path**. The running mod/world is unchanged. **Create custom scenario...** creates an empty kit and describes its separate-server step.

## Empty authoring environment: 127.0.0.1:25590

This is a separate fresh vanilla-generated world and configuration using the installed mechanics. Roles, Books & Lore, Loot Tables, Events and encounter groups start empty. Use the normal editors, then export from **Scenario content...**. The result is a runtime profile for another fresh dedicated server; it is distinct from the Quiet Below mod-build input. Map bindings and live progress stay world-owned.

Creation in the editor produces a kit, not a live server switch. The workspace helper can prepare/start further isolated authoring servers; see [setup and persistence rules](SCENARIO_CONTENT_EXPORT.md). A same-identity profile update preserves existing local edits; it does not overwrite them with exported defaults.

Exact build/test evidence is in [results](DEV66_FEEDBACK_RESULTS.md). Deployment hashes, paths and live status are recorded outside Git in `../reports/dev66-validation.json` and the two review manifests. These documents do not certify an authenticated human join, audible voice or final map balance.
