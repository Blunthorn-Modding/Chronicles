# Manual Modrinth publication package

Release: **0.1.0-beta**. Intended final visibility: **Public**. Publication status: **PENDING_OWNER_UPLOAD**.

Start with **PROJECTS.md** for both names, owner selection, short summaries and visibility. Page descriptions are in **CORE_DESCRIPTION.md** and **QUIET_BELOW_DESCRIPTION.md**. Each mod has its own changelog: **CORE_CHANGELOG.md** and **QUIET_BELOW_CHANGELOG.md**.

## Files to upload

1. Create a **Mod** project for the Chronicle framework and upload **chronicle_core-0.1.0-beta.jar**. Add Fabric API as required.
2. Create a **Mod** project for The Quiet Below and upload **quiet_below-0.1.0-beta.jar**. Add required dependencies on Fabric API and the matching Chronicle Core beta version.

Set Minecraft **1.21.10**, loader **Fabric**, release type **Beta** and environment **Client and server**. Original code/assets use **MIT**, with the third-party exceptions in NOTICE and the sound credits. Simple Voice Chat is recommended separately; SpiffyHUD and FancyMenu are optional client integrations.

Paste each description into its Modrinth project page and each changelog into the corresponding version. Suggested slugs are **chronicle-core** and **the-quiet-below** (subject to availability), and suggested categories are **Library** for Core and **Adventure** for The Quiet Below. `modrinth-metadata.json` records the local release settings and hashes; it is not an upload receipt.

Source: https://github.com/TheOnlyAesir/Chronicles

Issues: https://github.com/TheOnlyAesir/Chronicles/issues

Only the two owned mod JARs are downloadable mod artifacts in this package. Keep LICENSE, NOTICE and quiet_below-sound-credits.txt available as supporting credits. SHA256SUMS covers all package files. No handcrafted map is included.

Submit the completed Modrinth projects for review. Retain the actual project/version URLs and moderation status, then compare the hosted JAR hashes with SHA256SUMS. Creating a page or submitting it for review does not establish approval. No project has been created, uploaded or submitted by this preparation pass.

The owner/summary/body/public-visibility terminology was checked against Modrinth's [official project API](https://docs.modrinth.com/api/operations/createproject/) on 11 November 2026. The owner field must refer to your actual Modrinth account.
