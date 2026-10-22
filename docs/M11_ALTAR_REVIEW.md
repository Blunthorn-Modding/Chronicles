# Ancient Altar workstation review ? dev.33

Join **127.0.0.1:25589** with the refreshed [dev.33 client pack](../../client/Chronicle-M9-1.21.10.mrpack). Reimport/update the pack in your launcher; the new native screen handler needs the matching client. GUI scale 2 and the existing narrator/onboarding/multiplayer defaults are included.

The server is PAUSED. The **Altar is already placed at 8,65,23**, on the living test field. A barrel at 9,65,25 contains bread and clean pillar blocks. Existing players can use `/tp @s 8.5 65 26.5`; new arrivals use that area as world spawn. This is a disposable test map and can be retired after review.

1. Join participation (`/chronicle join`), use Survival or Adventure, turn editing off and resume through Session. Give the loaded network a moment to settle, then right-click the Altar.
2. The compact workstation has your real inventory below and two bargains above. Select each and read **Cost**, **Bonus**, and actual current-to-after health/food/speed. Hover a card for complete terms and a status for the full reason when unavailable.
3. Press **Make permanent bargain** once. There is no checkbox or extra confirmation screen. The count and preview change only after the server accepts. Extra heart adds one maximum heart and one extra food icon to fill; movement trades one maximum heart for+10% base walking speed. Each can be bought three times independently. Buying does not heal/refill you.
4. Check the final counts, hearts, food bar and movement. All three of both leave ten maximum hearts,13 food icons and+30% base movement. Death/reconnect retain both price and bonus. No reversal exists; ending the source removes both. The Heart's interaction redesign is a later correction.
5. Try moving items in the lower inventory, shift-clicking between it and the hotbar, number-key swapping and Escape. None should buy a bargain or move items into hidden Altar storage.
6. Pause play or sever the marked sculk connection at -3,64,0. The panel remains readable but acceptance disables; reconnect/resume restores it. Creative, editing, nonparticipation or moving more than six blocks away also prevent a purchase. Removing/replacing the physical Altar closes the old handler.

Please review the **Altar's workstation feel, wording and actual trades** before the Forge gets its separate design pass. Forge, spread comparison, Heart appearance/mining and configuration placement are still queued in [POST_M12_REVIEW](POST_M12_REVIEW.md).

## Verification and runtime

Dedicated UI design and lead approval preceded implementation. Independent source review,315 Java tests,70 Python tests and the [final native client lifecycle run](../../reports/bargain-smoke-20261022T115827002830Z/report.json) pass. Native screenshots cover 640x480/1280x720 at GUI2, unavailable/maxed/combined states and the generic16-choice group's last page. Actual slot transfers preserve counts/custom data; live power/range, foreign-stat previews, expiry/refresh, keyboard/mouse acceptance, replay, death, reconnect/restart and source-ending checks pass. Automated tests do not substitute for your judgement of the interface or balance.

[Deployment record](../../reports/qb12-altar-workstation-deployment.json). Voice 24469, SchinderRenge operator level 4, console64330. The existing25589 field is reused. Its57 pre-fixture world/config/auth files matched during the stopped jar swap; startup rewrites Minecraft/voice property formatting/defaults, while authentication/bindings and operator/whitelist/EULA were separately verified. Bench placement/supplies were checked in loaded chunks, saved, and temporary force-loads removed. Production/handcrafted maps were untouched.

Pack SHA256`eaaaedb8c6405b2bb2482194cf6b52fd3dfd9734bad8655e8fe92e58c0f8266a`. Both Chronicle jars aredev33. Approved HUD dependencies remain official download references with their notices; no third-party jars/assets are bundled. The four completed successful native Altar worlds were deleted after matching PASS evidence, stopped-world checks and exact owned-path checks; [retirement record](../../reports/altar-test-world-retirement.json). Logs/screenshots remain.

The final native client run has100 active-account initial steps,3 passive-account steps,13 optional-HUD reconnect steps,7 restart steps and2 offline-ending steps. One earlier immediate close assertion raced server cleanup; the corrected rehearsal also sends a late accept, waits five ticks, then verifies no live offer and no receipt. Its failed log and fixture are retained for a concrete close-scheduling follow-up.
