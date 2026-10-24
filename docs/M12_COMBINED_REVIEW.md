# Combined correction review: dev.47

Use the matching [client pack](../../client/Chronicle-M9-1.21.10.mrpack) and **127.0.0.1:25589** (Minecraft Java 1.21.10, Fabric). This is one review for the three functional corrections and four presentation improvements. Check the newest TEST_PLAN checkpoint for deployment readiness; this guide alone does not establish a running server.

## Pause and map work

While paused, turn editing off and use Survival. You should be able to move and edit character cards, but not place, mine, eat, take damage or lose hunger. Effects and in-flight food use freeze. **Session > Play controls > Enable Building Mode** deliberately permits everyone to place/build while paused; survival progression remains frozen. Only an operator can enable it. Metadata editing is separate and grants no private building bypass. Resume restores ordinary play and clears Building Mode. Holds, phase changes and restart clear it too; reconnecting never restores metadata editing privileges.

## One configuration destination

**Game setup > Block limits** is the only limit editor. Choose Ancient Altar or Forge, change its maximum, then place an additional block while paused with Building Mode on. Editing is required to change the policy; Building Mode grants actual map interaction. Lowering the limit preserves existing blocks and refuses more placements. The Heart remains fixed at one.

**Locations > Block sites** lists actual supplied blocks and manual references. It shows capacity/status without policy controls or links. Supplied blocks register automatically. Regions alone owns source-city/takeover geometry through **Use for...** and **Clear use...**; Game setup has global settings.

## Four UI improvements together

- ACTIVE status appears briefly, then leaves the hotbar area clear. Paused/held status remains visible.
- The editor starts in Clean view. **Debug: off/on** in the footer reveals diagnostic facts locally; it must preserve drafts, selection and permissions. Errors, blocked reasons and useful physical status stay visible in both views.
- Configuration uses the Altar's normal Minecraft popup/widgets and readable text, with the established left tabs. It has no inventory panel. Check scrolling and input at your normal GUI scale 2.
- Events distinguish private **Preview cue/action**, **Check audience/readiness**, and reviewed execution. Regions explains the two-position workflow. Activity uses readable summaries.

Please review these four as one UI pass. Native coverage includes 320x240,480x270 and 640x360 logical layouts; taste, texture-pack appearance and wording remain human checks.

## Spread comparison

The Heart/stations are on the small platform; a separate bank has roughly 4096 sculk cells. A bridge at z3,y64 joins them; replacing the cut at x8-10 reconnects it. Game setup's Spread mode shows living Count/effective rate; Debug exposes Attempts. Compare similar occupied ACTIVE periods connected/disconnected, then Fixed rate. Attempts are opportunities; actual growth depends on available surfaces.

## Heart last stand: test last

The Heart is at **-4,64,0**. Keep its network below Established for **60 occupied ACTIVE seconds**. Unresolved/unloaded branches prevent vulnerability. It pulses, spreads explosively across a local **5x5 footprint where eligible surfaces exist**, and directly summons one normal Warden when safe local space exists. Peaceful skips the Warden. Enclosure, exclusions and actual connected geometry still apply; it cannot grow through solid walls. Reconnecting/recutting cannot replay the phase within **ten occupied ACTIVE minutes**. Pauses/downtime do not count; cooldown survives restart.

The brown lens marks vulnerability. With editing off, use a pickaxe in Survival. Nearby players receive a cryptic message; there is no destruction menu. Eradication ends this fixture's network and removes Altar bargains. Test this last because it ends the fixture.

See [pause](CF15_PAUSED_GAMEPLAY.md), [last stand](M12_HEART_LAST_STAND.md), [editor design](CF16_EDITOR_UI_DESIGN.md) and [test evidence](TEST_PLAN.md). M13 still requires the handcrafted map/group playtest; this synthetic fixture is not that soak.
