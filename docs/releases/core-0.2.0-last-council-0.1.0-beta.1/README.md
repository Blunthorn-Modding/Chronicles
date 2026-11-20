# Chronicle Core 0.2.0 / The Last Council 0.1.0-beta.1

Minecraft Java **1.21.10**, Fabric Loader **0.19.5 or later**, **Java 21**.
The tested Fabric API is **0.138.4+1.21.10**. Install the selected campaign and
Chronicle Core on the server and each client.

## Files to install

- `chronicle_core-0.2.0.jar`
- `last_council-0.1.0-beta.1.jar`
- Fabric API for Minecraft 1.21.10

The Last Council requires Chronicle Core **0.2.0 or later**. Its Modrinth release
type is **Beta**; Core's requested version is **0.2.0**. Both owned projects use
the MIT license. Third-party libraries retain their own licenses; see `NOTICE`.

This is a local upload package. Project creation, version publication and the
main-branch merge remain separate actions. No world, account data or server
configuration is included in these mod JARs.

## Updating a review installation

1. Close Minecraft and stop the server cleanly. Back up the stopped world and
   its scenario configuration.
2. Remove the older Core/Last Council JARs from that installation's `mods`
   directory, then install these two JARs. Every peer needs the same pair.
3. Open a fresh copy of the existing dev.20 review world, or prepare your own
   map bindings. Assign the separate Chancellor and participating Houses.
4. Start with the quick smoke section in `REVIEW_CHECKLIST.md`, then cover the
   multiplayer sections as reviewers become available.

Existing dev.20 political data is supported. Proceedings writes schema 6;
older schemas 1–5 migrate on load. A downgrade after saving newer data needs
the matching stopped-world/configuration backup and matching older mods.

## Optional voice and HUD support

Simple Voice Chat **1.21.10 / 2.6.22** is the tested optional voice runtime.
Enable a Region's **Voice room** subtype to create and maintain its group
automatically. Real microphone/audibility and remote UDP connectivity still
need human review. An e4mc game connection does not establish a working voice
UDP path.

FancyMenu and SpiffyHUD integrations remain optional. Their dependencies and
licenses belong to their respective projects; they are not bundled into these
owned mod JARs. Chronicle's included SnakeYAML Engine remains covered by its
Apache-2.0 notice.

## The Quiet Below

The existing, unchanged `quiet_below-0.1.0-beta.jar` is the compatibility target
for Core 0.2.0. Consult `VERIFICATION.md` for the actual test results and limits.
Back up a played installation before changing its Core version; these checks
do not replace testing your particular handcrafted map.

## Reproducing the build

Use the workspace-local Java/Gradle environment from `scripts/workspace.py`:

```text
gradlew.bat --offline --no-daemon --no-parallel --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2G -Pcore_version=0.2.0 -Plast_council_version=0.1.0-beta.1 build :campaigns:quiet-below:writeSmokeClientLaunch :campaigns:last-council:writeSmokeClientLaunch
```

Version overrides are per module. The shared development default remains
`0.2.0-dev.20`; this release does not rename the existing Quiet Below release.
