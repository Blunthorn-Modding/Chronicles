# Operator follow-ups after dev.46

Recorded23 October2026; implementation authorized24 October. The original notes below are retained as acceptance context and are superseded by this status: all three functional corrections and four presentation items are implemented for one combined dev.47 review. Functional points received independent review; the UI received a dedicated design and lead review before implementation. See [pause contract](CF15_PAUSED_GAMEPLAY.md), [Heart burst](M12_HEART_LAST_STAND.md), [editor design](CF16_EDITOR_UI_DESIGN.md), [combined review](M12_COMBINED_REVIEW.md) and [current evidence](TEST_PLAN.md).

## Next implementation work

### Core pause, stop and build-session behavior

Operator reports that a frozen game still permits block placement, eating, damage and hunger loss. Investigate and correct the broader pause/stop behavior in Chronicle Core. Reproduce with the exact session phase, player game mode, editor/author flag, participant state and relevant damage/interaction sources recorded, so intentional builder permissions are distinguishable from a participant bypass.

Inspect the locally supplied `../../VampireSMP-1.1.2.jar` for how VSMP handles session pausing, stopping and build sessions. The [existing reference findings](REFERENCE_VAMPIRE_SMP.md) cover authoring/chests; they do **not** establish its pause/build implementation. That inspection was completed for CF15; see its contract and the updated reference findings. Use it as behavioral reference for a Fabric-native Core design, not as an installed Paper dependency or an unreviewed code copy.

Acceptance direction:

- Ordinary paused/stopped gameplay must prevent world-changing block placement/breaking and survival progression, including eating, taking damage and hunger depletion. Inspect other interactions and timers needed to make this state coherent rather than patching only the examples.
- Preserve the earlier explicit requirement that players may move and edit character cards while play is paused.
- Establish a clear, server-authorized build/authoring exception for intentional map work, informed by the VSMP comparison. Do not silently allow every paused player to build, and do not accidentally block the authoring tools needed while inactive. Resolve remaining build-session policy questions during design if the reference and existing user decisions are insufficient.
- Verify pause/resume/stop and intended build permissions in native multiplayer, including reconnect and transitions. Keep campaigns dependent on Core's shared pause contract rather than Quiet Below-specific interaction patches.

### Stronger Heart final burst (QB-13)

The current attempt produces only a single vein. Make it visibly explosive and cover at least a5x5 area with veins/sculk according to the available surfaces. Preserve connected ecology, exclusions, restoration records and bounded work, with the existing sixty-second weakening, one Warden and ten occupied ACTIVE-minute cooldown. [Detailed acceptance criteria](M12_HEART_LAST_STAND.md#next-implementation-stronger-final-burst).

### Block-limit configuration placement (CF-14)

Move Block limits exclusively to Game setup. Remove both Locations copies and all placed-block links back to policy configuration. Locations remains a physical registry with count/cap and available/reached/over-limit status only. [Updated design](M12_AUTOMATIC_BLOCK_SITES.md#next-implementation-separate-limits-from-locations).

## After the main functionality

The operator subsequently authorized bundling these four polish tasks into the same implementation and human review:

- **Session status:** replace the continuously repeated ACTIVE actionbar with a short message when play becomes active, then let it disappear. Keep the paused-session reminder persistent. Choose a consistent transition/reconnect behavior during design.
- **Editor Debug Mode:** add an optional diagnostics toggle. The default editor view should hide routine technical/status text such as purchases and authority details; debug mode reveals it when needed. Keep ordinary errors and necessary action/confirmation information understandable in the clean view. This is presentation, not a change to server authority or permissions.
- **Options UI:** redesign the configuration/options screens using the Ancient Altar's visual framework and normal Minecraft UI building blocks. Reuse its presentation conventions rather than its prayer mechanics. Dedicated UI design and review precede implementation.
- **UI wording:** audit labels, explanations and descriptions for other organizers/players, replacing confusing or technical wording with clear purpose and action text. Coordinate this with the clean/debug split and configuration redesign; keep user-facing descriptions consistent with actual behavior.

The original notes alone made no runtime changes. Deployment status belongs to the newest TEST_PLAN checkpoint; implementation does not complete the production-map/group-play milestones.
