# Manual upload notes

The prepared files are local artifacts. No Modrinth page/version was created or
published by this build task.

1. Upload `chronicle_core-0.2.0.jar` to your Chronicle Core project as version
   **0.2.0**, type **Release**, Fabric, Minecraft **1.21.10**. Require Fabric API.
2. Upload `last_council-0.1.0-beta.1.jar` to The Last Council project as version
   **0.1.0-beta.1**, type **Beta**, Fabric, Minecraft **1.21.10**. Require Fabric API
   and Chronicle Core **0.2.0 or newer**. Select the actual Core project/version
   dependency in Modrinth after uploading Core.
3. Both mods are required on client and server. Simple Voice Chat is optional;
   the tested version is **1.21.10-2.6.22**, with separate voice networking needed.
4. Use `LAST_COUNCIL_DESCRIPTION.md` for The Last Council's project-page body.
   Select `last-council-icon.png` for its project icon; `ICONS.md` records the
   original Minecraft texture and preparation details.
   Use `CHANGELOG.md`, `README.md`, `VERIFICATION.md` and
   `modrinth-metadata.json` as the upload reference. The metadata document is a
   checklist, not a ready-to-submit authenticated API request.
5. Match each primary JAR upload against `SHA256SUMS`. The source repository is
   linked in each mod's metadata; the upload package contains the playable JARs.

Your existing Quiet Below `0.1.0-beta` artifact is unchanged. Its compatibility
evidence with Core 0.2.0 is recorded in `VERIFICATION.md`; this task does not
produce a newly versioned Quiet Below release.

The Last Council remains a beta with final content, map/group, human voice and
representative-load acceptance outstanding. Use `REVIEW_CHECKLIST.md` to record
that review. A release version label is not a claim that those human checks
were already completed.
