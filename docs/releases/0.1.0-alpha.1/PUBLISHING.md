# Initial Modrinth publication

Prepared release: **0.1.0-alpha.1**, **Alpha**, Minecraft **1.21.10**, loader
**Fabric**, environment **Client and server**. The files in this directory are
the native-tested release bytes. SHA256SUMS records their checksums.

The owner chose to create the projects and upload this prepared package
manually. No project or version has been created, uploaded or submitted by the
release-preparation script. No API token is needed or stored in this package.
Do not paste credentials into chat or commit them to the source repository.

## Chronicle Core project

Create a **Mod** project named **Chronicle Core** (suggested slug
`chronicle-core`, subject to availability), category **Library**, MIT license.
Use CORE_MODRINTH.md as its description and the GitHub repository as Source
and Issues. Upload **chronicle_core-0.1.0-alpha.1.jar** as the primary version
file, with CHANGELOG.md as the changelog. Add **Fabric API** as required.
Simple Voice Chat, SpiffyHUD and FancyMenu are optional separate downloads.

## The Quiet Below project

Create a **Mod** project named **The Quiet Below** (suggested slug
`the-quiet-below`, subject to availability), category **Adventure**, MIT license
with the third-party exceptions explained in its description and NOTICE.
Use QUIET_BELOW_MODRINTH.md as the description. Upload
**quiet_below-0.1.0-alpha.1.jar**, with the same game/loader/alpha settings and
CHANGELOG.md. Add required dependencies on **Fabric API** and the newly
uploaded matching **Chronicle Core 0.1.0-alpha.1** version.

Submit the completed projects for Modrinth review. Review submission is not
approval: retain the project/version URLs and actual moderation status. After
approval, download both hosted JARs and compare their SHA256 values to this
package before declaring publication complete.

Never upload the Minecraft server JAR, copied game libraries, review worlds,
backups, third-party HUD mods or access/configuration files. The two Chronicle
JARs already contain their required project/library/audio notices. No
handcrafted map is bundled, and the alpha descriptions disclose remaining
map-specific balance and group testing.

Official API contracts checked 4 November 2026:
https://docs.modrinth.com/api/operations/createproject/ and
https://docs.modrinth.com/api/operations/createversion/ . Account-authorized
API automation would require project-create, version-create and project-write
permissions; preparation alone grants no account access.
