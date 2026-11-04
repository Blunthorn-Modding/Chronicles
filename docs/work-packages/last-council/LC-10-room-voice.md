# LC-10 — Room acoustics and Simple Voice Chat

Status: investigation/design package, added after the user's 4 November feedback. Independent of palace permission and rebellion. Reuse existing Regions for room bounds; any new room-voice adapter is an optional shared Core capability. No mod installation, server startup, pack update or voice implementation is part of this planning pass.

## Desired experience

Several conversations can happen in separate rooms. Players outside a closed room should hear nothing intelligible, or deliberately muffled speech if that is the chosen design. Opening a door should matter when using the physical-acoustics approach. Region entry/exit should be automatic where a Region-based policy is selected.

The user is exploring alternatives, not specifying a full acoustic simulator. Evaluate physical wall/door attenuation, hard Region privacy and cross-Region muffling separately before choosing the implementation.

## Evidence checked 4 November 2026

- [Simple Voice Chat API examples](https://modrepo.de/minecraft/voicechat/api/examples) document creating groups and assigning a player's connection to a group. Its [group types](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/Group.Type.html) distinguish hearing the group, hearing nearby outsiders, and allowing outsiders to hear group members. Group membership alone is not an audio-muffling effect.
- The already installed, locked `voicechat-fabric-1.21.10-2.6.22.jar` was inspected with `javap`: it contains `Group.Type.NORMAL/OPEN/ISOLATED`, `VoicechatConnection.getGroup/setGroup`, and `OpenALSoundEvent` with source/channel/position access. This confirms those hooks in our actual dependency, not just latest online documentation. It does not prove a working acoustic policy.
- [Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered) provides block absorption/attenuation and explicitly supports Simple Voice Chat. The official [Fabric1.21.10 release1.5.1](https://modrinth.com/mod/sound-physics-remastered/version/fabric-1.21.10-1.5.1), version ID `pDo1ElL2`, is available. Its [upstream voice integration](https://github.com/henkelmax/sound-physics-remastered/blob/master/common/src/main/java/com/sonicether/soundphysics/integration/voicechat/SimpleVoiceChatPlugin.java) demonstrates the integration path. The release is labelled Alpha; this is an available candidate, not a claim that it is latest or validated with our pack.
- Sound Physics Remastered is GPL-3.0-only. Keep an external dependency's license and official distribution separate from Chronicle's MIT code; do not copy its implementation/art into Core. No download pin or dependency has been changed here.

## Preferred investigation order

1. **Try existing physical acoustics first.** In a tiny disposable fixture, compare an ordinary room with its door open/closed, hallways and adjacent rooms using Simple Voice Chat plus the official compatible Sound Physics Remastered build. Test whisper/normal speech at realistic distances. This may satisfy the desired play with no custom room/group management. It is an acoustic effect, not a proven server-enforced privacy boundary.
2. **If the desired result needs authored room rules, add a Voice Room Region capability.** Region geometry/selection is already available; membership/routing/filtering is new integration. Do not equate it with guarded access or house membership. Keep normal local voice behavior where possible.
3. **Choose one reviewed policy:** hard isolation, or audibly muffled crossing of room boundaries. Hard isolation needs server routing/authorization; merely lowering a client's volume is insufficient. Real muffling needs filtering/attenuation, not only joining a group.
4. **Use built-in groups only if their actual behavior fits.** Automatic enter/exit is supported by the API, but group/static audio is not a model of room geometry or doors. Verify positional/range behavior, nearby outsiders and restoration of any prior manual group before adopting it.
5. Add authored door/portal links only if the selected policy needs them. Avoid automatic full-building analysis or a second acoustic engine unless the prototype demonstrates the need.

## Implementation boundaries and failure behavior

- Core owns generic optional room-voice policy; Last Council configures rooms on its map. Existing Regions may have voice and access subtypes independently.
- Gate integration on the supported voice mod/API. Ordinary Core/Quiet Below without voice integration must continue to load. Missing required room capability shows setup diagnostics; do not claim a room is private when its policy is not operating.
- Resolve overlap, adjacent rooms and dimensions deliberately. Update membership for movement, login/logout, respawn and teleport; don't leave a player connected to an old room after leaving it.
- Voice continues to follow physical room membership during a Core play pause, since players can still move and talk. It is not a game countdown or political eligibility condition.
- If automatic groups are chosen, review manual groups, joining permissions and prior-group restoration. Do not silently break a player's group or permit an outside join to defeat hard isolation.
- Keep world/policy reads off real-time audio callbacks: use bounded cached membership snapshots and no world scans, disk writes or Event receipts per audio packet. Do not automatically record conversations.
- Decide how any Chronicle filtering composes with Sound Physics before enabling both; avoid double processing or conflicting OpenAL filters.

## Acceptance and review gate

Record the actual chosen behavior with two or more native clients: same room, outside/inside both directions, adjacent rooms, open/closed door where applicable, normal distance/whisper, rapid entry/exit, overlaps, reconnect/teleport and prior manual groups. Distinguish an actual low-pass/muffling result from simple volume reduction or conference-style group audio.

Measure representative client/server impact. If hard privacy is chosen, verify outside recipients are not sent the prohibited voice path. If only acoustics is chosen, state its limits honestly. Human review of audibility/intelligibility is required; API compilation and synthetic packet checks do not settle this.

Review the prototype and select the policy before a custom adapter/UI or mandatory dependency is implemented. LC-09's palace/group playtest must include the selected voice behavior. Successful test worlds remain disposable under the workspace retention policy.
