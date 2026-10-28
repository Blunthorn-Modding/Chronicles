# Event progression review

Use the latest matching client pack and the preset server `127.0.0.1:25589`. The existing review world and the organizer's saved play state are retained.

For authoring, pause play and enable Editing. For gameplay checks, use Survival, participation On and Editing Off, then resume play.

1. Under Advanced **Flags**, create `chronicle_core:review_read`. In Events create an Event whose **When** is **Book opened**, selecting an existing lore book. Leave Requirements empty and choose No delay. Under **What happens**, add **Update flag**, select that flag, and choose On. Text and sound are optional.
2. Create a second Event with **When flags are On**, require that flag, choose a ten-second delay, and add a text/sound outcome. During play, obtain the genuine lore book through the normal Books & Lore GM give or loot route and open it. Check that the flag turns On and the second Event follows after its delay. A merely same-titled ordinary book should not count.
3. Before the delay finishes, turn the flag Off in Advanced Flags, then On again. The full ten seconds should start again. Pausing should preserve elapsed active time; reopening the server should also preserve it.

The same source selector supports bound block activity, successful prayers/enchantments, departures, saved Region-state application and Quiet Below ecology/Heart milestones. Review the source/target picker wording and the single mixed outcome list together. Events run once until deliberately allowed again; changing a flag alone does not rearm a completed Event.

Book discovery and opening are distinct. Interrupted-source recovery is a GM tool, not part of ordinary play. It never grants the original items again; recording a missing book discovery requires authentic inventory evidence from its original recipient.

The handcrafted map, final narrative and realistic group/voice playtest remain separate reviews. Role assignment/first-entry triggers remain future provider extensions; role-issued book discovery is included.
