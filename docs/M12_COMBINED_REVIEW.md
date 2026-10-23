# Combined correction review: dev.43

Use the matching [client pack](../../client/Chronicle-M9-1.21.10.mrpack) and **127.0.0.1:25589**. Minecraft Java1.21.10, Fabric. This is one disposable comparison world for points4-6 of [the correction queue](POST_M12_REVIEW.md). Earlier accepted station behaviour is included. The server starts paused; resume through Session after joining. Pause before editing settings and turn editing off before participating.

## Spread comparison

The small platform contains the Heart, Altar and Forge. The separate large bank contains about4,096 sculk cells. A narrow sculk bridge joins them at z3,y64; removing x8-10 fully separates the bank. Supplies are beside the spawn. Replacing those three blocks reconnects it. The gap has no stone underneath, so growth cannot quietly cross through the foundation.

In Game setup, **Spread mode** shows the mode, conservative living Count, effective rate and since-load Attempts. The fixture uses base1x and +100% per1,000 connected cells to make the difference obvious. Compare a similar period of occupied ACTIVE play with the bank connected and disconnected, then compare Fixed rate. Count and topology take bounded time to settle after cuts. Attempts measure growth opportunities; actual new blocks depend on available surfaces. You can tune the amplification and base rate while paused/editing.

## Configuration placement

Open Locations, then Block sites. **Saved sites** lists named physical references; **Game bindings** lists the installed Altar, Forge and Heart requirements. Choose a binding, then choose an existing saved site. Creation routes through normal looked-at-block authoring and returns to the binding, where assignment remains an explicit second step. Cancelling does not assign anything. A revised site exposes the existing stale-reference warning until explicitly reassigned.

Game setup keeps Source city / takeover regions and global rules. **Heart weakness** is the global threshold/delay setting. Altar and Forge work as placed blocks independently of their optional authoring references.

## Physical Heart: test last

The Heart is at **-4,64,0**. Its lens shows resting/unverified, protected, weakening and vulnerable states using the sculk vitality colours. Right-clicking has no destruction menu. Break the three bridge blocks and wait for the small connected network to settle, then let it stay below Established for60 occupied ACTIVE seconds. Unloaded/unresolved branches prevent vulnerability.

With editing off, use a pickaxe in Survival to mine the weakened Heart. Wrong tools or protected states cannot destroy it. Players within five blocks receive a cryptic message. Destruction permanently ends this fixture's network, removes its Altar bargains and restores recorded terrain through bounded cleanup. Do this after the spread/menu checks; the world can be replaced afterward.

## Automated evidence and limits

See [count measurements](M11_COUNT_RATE_VERIFICATION.md), [Heart contract and native checks](M12_PHYSICAL_HEART.md), [configuration design/evidence](M12_CONFIGURATION_PLACEMENT.md) and [test history](TEST_PLAN.md). This synthetic fixture is not a production-map or group-performance soak. M13 still needs the handcrafted map and group playtest.
