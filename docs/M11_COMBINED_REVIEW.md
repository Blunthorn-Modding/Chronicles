# Combined review - dev.40

Update/reimport the [single matching pack](../../client/Chronicle-M9-1.21.10.mrpack).
The current world, tuning and both accepted prayers are retained. The review
server is **127.0.0.1:25589**, initially **PAUSED**; the Forge is **10,65,23**,
Altar **8,65,23**, materials barrel **11,65,25**.

1. Start the updated client. FancyMenu should no longer open its welcome or
   documentation. Multiplayer should already list **Chronicle - Current review**.
   Existing custom entries/settings should remain; repeated startup should not
   add duplicates. GUI scale 2, narrator off and startup/multiplayer acknowledgments
   remain preset.
2. Join, participate (`/chronicle join`), use Survival/Adventure, turn editing off
   and resume Session. Covered sculk directly touching connected campaign sculk
   should remain alive under the Forge, Altar or another sculk layer. Existing
   exhausted cells recover naturally; at five-minute full recovery, reaching
   the 25% power threshold can take about 75 active seconds plus assessment.
   Growth still needs reachable exposed surfaces. A real severed branch still
   stops, decays and can recover on physical reconnection.
3. At the Forge, shift-click a stack into the top entry. Exactly one should
   enter and the rest should stay in their original slot. Click the Forge image,
   then offer the second item through that same entry. Check both orders.
   A sword + echo shard should return Sharpness I equipment in the bottom exit.
4. An unrelated pair such as bone + arrow should disappear after stirring with
   no output. A valid already-Sharpness-I sword + echo shard should return only
   the unchanged sword; the shard is consumed. Closing before completion should
   still return offerings. The approved UI layout and original artwork remain.

[Detailed Forge checks](M11_FORGE_REVIEW.md), [implementation rules](M11_COVERED_SCULK_AND_OFFERINGS.md),
[exact automated evidence and remaining limits](TEST_PLAN.md). Please review
these three changes together; the other queued corrections remain separate.
