# Council review ? development build 0.2.0-dev.6

**8 November follow-up:** functional review with a friend is pending. The oversized Event-pool editor and clipped bottom action row are queued for tomorrow; see [review notes](REVIEW_FEEDBACK_2026-11-08.md). Future deliveries will contain only e4mc Host and Player packs. Today's build remains unchanged.

Use a fresh import of the matching **e4mc Host** and **Player** packs in `../client`. The Host pack contains a clean review world; the Player pack joins it. Only one person opens the bundled world. Open to LAN with commands disabled for guests and share the e4mc address. Voice chat is omitted. The dedicated authenticated alternative remains `127.0.0.1:25589` with its matching plain review pack.

SchinderRenge is preset as Chancellor, without a House. Operators may otherwise play a House; only the appointed Chancellor controls proceedings. The old world in your existing launcher profile is not overwritten. To retain that played world, update **both** Core and Last Council to matching dev.6 jars after closing Minecraft, removing their older versions first. Back up that profile before testing a development update.

## Setup

1. Pause play, enable Editing, then use **Players > House & papers** to assign an unused House to each online reviewer. Existing committed papers now permit adding a previously unassigned character without changing anyone else's papers. Existing assignments cannot be reassigned. After Regency starts, late entrants may be voters but cannot become new crown candidates.
2. Each House player joins participation with `/chronicle players join`. Creative is allowed. Turn Editing off and start/resume play. First play issues papers automatically. The Chancellor cannot also represent a House.
3. Voters stand inside the central Council Region: x/z -5..5, y 64..80. The motion lectern is at 0,65,4. I opens Chancellor proceedings; R opens the current ballot; both are rebindable.

## Bundled dev.6 review

- Open **Events > Event pools**. Create a pool, add existing manual once-only Events through the picker, change weights, save and reopen. Possible entries are visible; upcoming selections and timing never are. Definitions require paused/stopped play with Editing enabled.
- Open **Game setup > Council complications > Configure complications**. The defaults are two interruptions over meetings 1/2/3, 5-25 active Council minutes after opening, five-minute spacing. Zero disables them. Timing and declared meetings can be edited before the first meeting opens.
- For a short functional test, set total 2, meeting 1 only, earliest/latest 1 minute and spacing 0 before opening the meeting. The default configuration remains the approved 5-25/five-minute setup in a fresh pack. Resolve/defer the prepared motions, then request Regency early: one complication must arrive and Council must recess, while the world stays active. Resume and request again for the second. A meeting-close request has the same protection. The normal default's pacing needs real play review separately.
- While paused and Editing after scheduling has started, remove a future meeting under **Meetings**, then confirm **Cancel future...**. Future obligations redistribute without exposing their allocations. Removing every future opportunity needs a reason and leaves a durable undelivered record. Current/claimed interruptions cannot silently disappear.
- Keep proceedings open while others vote. Progress updates automatically. Secret ballots give only the Chancellor anonymous submitted counts, and ordinary players no secret turnout or choices. The live ballot now omits the requested explanatory cards, retaining centered heads, actual choices and immutable confirmation.
- Opening and closing a Council meeting should announce it to every player once. Polling, refused retries and reconnecting must not repeat it. Public motion history, void replacements, locked Regency slates and the Creative text-entry key guard retain the previous review behavior.


The next gate is this bundled native UI and interruption/recess/pacing review. Use two participating House players plus the separate Chancellor for Regency. [LC08_IMPLEMENTATION.md](LC08_IMPLEMENTATION.md) records tests, storage and omissions. Reusable pool/settings export, physical handout delivery, final content, rebellion, treaties, banquet and acoustics remain separate work.
