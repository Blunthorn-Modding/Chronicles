# Ancient Altar prayer review - dev.34 (accepted)

The operator confirms the interface and prayers work as intended. The current dev.35 pack keeps this Altar behavior; see [mining/exclusion follow-up](M10_MINING_CONTACT.md). The checklist below records the accepted dev.34 review.

Join **127.0.0.1:25589** with the refreshed [dev.34 client pack](../../client/Chronicle-M9-1.21.10.mrpack). Reimport/update the pack in your launcher; this screen needs the matching client. GUI scale2 and the narrator/onboarding/multiplayer defaults are included.

The server starts PAUSED with a fresh disposable field and no previous prayers. The **Altar is at8,65,23**. A barrel at9,65,25 contains bread and clean pillar blocks. Use `/tp @s 8.5 65 26.5` if needed. Production/handcrafted maps are untouched.

1. Join participation (`/chronicle join`), use Survival or Adventure, turn editing off and resume through Session. Allow the living network a moment to settle, then right-click the Altar.
2. Check readability: a native popup, two prayer buttons and Close. There is no inventory, worksheet or hover explanation. Resource packs control the standard popup, buttons and font.
3. Choose **Pray for more life**. The popup closes and only you receive the cryptic response. Check the half-heart/empty-food aftermath, then eat. Open it again and try **Pray for quicker feet**; check its distinct private response and vanilla Slowness.
4. Each prayer can be used three times independently. Its button becomes unavailable when exhausted. Try Escape or Close without praying; neither should change your character. Reconnect/death keep the lasting terms; Slowness follows vanilla rules.
5. Pause play or cut the marked connection at-3,64,0: the open popup becomes quiet and prayers disable. Resume/reconnect restores eligible choices. Creative, editing, nonparticipation or moving beyond six blocks also prevent prayer. Removing/replacing the Altar closes its old interaction.

Please review the **readability, mysterious presentation and prayer aftermath** before the Forge receives its separate UI design pass. The [remaining corrections](POST_M12_REVIEW.md) remain queued.

## GM mechanics and compatibility

New life prayers grant one maximum heart and add25% positive exhaustion each (three:+75%), keeping the normal ten food icons. Each immediately sets current health to1HP and food/saturation to0. Feet prayers retain the one-maximum-heart sacrifice/+10% base speed and add vanilla Slowness I for eight minutes. Three of both leave ten maximum hearts,+30% base speed and75% extra exhaustion. Death/reconnect retain the lasting price and bonus; no normal reversal exists. Eradication removes both lasting sides. Transient aftermath is not replayed on reconnect or refresh.

Existing accepted receipts retain their old prices, including any earlier extra food capacity. This review uses a fresh world so those old receipts cannot mask the new normal-bar behavior. Schema2 reads old schema1; initialization markers remain compatible. The transient callback follows durable publication and has a documented hard-crash interval in which the immediate aftermath could be omitted; it is not a separate crash-atomic player transaction.

## Evidence

See the newest [TEST_PLAN](TEST_PLAN.md), [design](M11_ALTAR_PRAYERS.md) and [mechanics contract](CF12_EXHAUSTION_PRICE.md). Automated checks cover native sprite/font overrides, both GUI2 resolutions, no slots or mechanical payload fields, private responses, life/exhaustion tiers, vanilla Slowness merging/timing/death, stale/replayed actions, death/reconnect/restart and online/offline ending. They do not replace human readability or balance feedback.

[Deployment record](../../reports/qb12-altar-prayers-deployment.json). Voice24469, SchinderRenge operator level4. Final native evidence: [115/4 initial,17 reconnect,7 restart,2 ending steps](../../reports/bargain-smoke-20261022T131341199163Z/report.json). Pack SHA256`a0414b4345127c36d32b643be96c4dc634cbd75655f6039ad85d20323aa9edb0`. Finished/resolved native worlds are retired; logs/screenshots remain. The fresh human-review world stays available.
