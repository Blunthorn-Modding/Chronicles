# Council decisions, House treaties and room voice - 19 November clarification

The owner's 19 November clarification supersedes the earlier separate realm-ratification interpretation, including the unchanged source brief. This records policy and investigation only; no gameplay code, dependencies or review packs change here.

## Council authority

A passed Council motion is a binding Council decision, regardless of whether its subject affects one House, several Houses or the realm. It is recorded through the existing proceedings/ballot system. Players cannot unilaterally break that decision through the House treaty action. A realm agreement proposed to the Council uses the same motion/vote flow; do not introduce a second ratification screen, signature phase or special realm-treaty lifecycle.

A voluntary House treaty is a different record: parties sign exact terms at the bound lectern, the Chancellor sanctifies it, and a party may later break it with the existing history/privacy rules. The Chancellor witnessing that ceremony does not turn it into a Council decision. Council motion effects remain typed where actual game state is supported (succession, foreign war/peace, reinstatement). General free-text decisions are retained political records; the mod does not infer arbitrary mechanics from their prose. Do not claim that code can enforce every possible written instruction.

Existing code separates ProceedingsService's saved Council close/results from TreatyService's party-authorized breach action. There is no separate Council-treaty ratification implementation to finish. Active treaty amendments were an optional planning extension, not an agreed completion requirement; a new agreement can be drafted instead. Reversal or replacement rules for Council decisions are not newly specified by this clarification.

## Voice example and proposed direction

The owner reports playing the [Blood on the Clocktower pack](https://modrinth.com/modpack/blood-on-the-clocktower), where walking into map locations automatically changed voice groups. This is the reference experience for the next voice design discussion. Previous explicit deferral is not permission to install dependencies or implement an unreviewed native workflow.

Research checked 19 November 2026:

- The [pack's map-editing wiki](https://github.com/Sybillian/minecraft-botc/wiki/Custom-Content:-Map-Edits) explicitly identifies voice-chat regions as datapack-defined map features. Its [server instructions](https://github.com/Sybillian/minecraft-botc/wiki/Installation:-Server) name Simple Voice Chat.
- Public repository tree inspected at commit `d7ace12b9e4d2ecf77e40b5e0e6b6e098980ba8c`. The [voice initializer](https://github.com/Sybillian/minecraft-botc/blob/d7ace12b9e4d2ecf77e40b5e0e6b6e098980ba8c/resources/datapack/required/ct/data/ct/function/admin/init/voice_chats.mcfunction) configures persistent isolated room groups and an open town-square group. The [room dispatcher](https://github.com/Sybillian/minecraft-botc/blob/d7ace12b9e4d2ecf77e40b5e0e6b6e098980ba8c/resources/datapack/required/ct/data/ct/function/voicechat/root.mcfunction) selects authored location predicates, and the [leave path](https://github.com/Sybillian/minecraft-botc/blob/d7ace12b9e4d2ecf77e40b5e0e6b6e098980ba8c/resources/datapack/required/ct/data/ct/function/voicechat/leave.mcfunction) releases group membership. These were inspected, not copied or executed.
- The pack contains Enhanced Groups configuration. [Enhanced Groups](https://modrinth.com/mod/enhanced-groups) documents persistent/hidden groups and server group controls. Its auto-join feature is login-based; spatial detection in the reference pack is custom map logic, not evidence of a ready-made Region editor.
- A Fabric/Quilt Minecraft **1.21.10** Enhanced Groups release exists: [1.7.0, Modrinth version u4ucs3FT](https://modrinth.com/mod/enhanced-groups/version/fabric-1.21.10-1.7.0), labelled Beta. This establishes an available target, not compatibility certification with Chronicle. The pack's current wiki targets 1.21.11, so its whole current pack must not be treated as our pinned 1.21.10 dependency set.
- [Simple Voice Chat's Group.Type API](https://voicechat.modrepo.de/de/maxhenkel/voicechat/api/Group.Type.html) documents isolated/normal/open behavior. Isolated group members hear only that group; open groups support nearby outside listeners. These are explicit group rules, not automatic wall/door simulation.

For Chronicle, reuse existing Core Regions with an optional Voice room subtype: automatic room membership on entry, leave/restore normal proximity on exit, separate from palace access and House politics. Simple Voice Chat is the voice dependency; assess its existing API before requiring Enhanced Groups as an additional dependency. Group overlap, manual-group restoration, disconnect/teleport handling and outside privacy need design and actual multiplayer verification. No voice code or runtime audibility claim follows from this investigation.

Keep Chronicle's MIT source independent. The Clocktower pack declares GPL-3.0-or-later and Enhanced Groups declares ARR on Modrinth; no upstream code/art/config is copied into Chronicle and no third-party file is relicensed. Any later dependency must use its own official distribution and notices.
