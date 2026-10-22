# Contact-cost correction review

This is the first correction from the [dev.31 feedback queue](POST_M12_REVIEW.md). Only sculk footing/exposure is changed. Altar/Forge screens, count-rate comparison and Heart interaction/appearance remain queued, with dedicated UI design before workstation implementation.

## Test route

Use the separate contact world at **127.0.0.1:25589** with the refreshed [dev.32 client pack](../../client/Chronicle-M9-1.21.10.mrpack). Voice is24469. The old ending-review world on25588 is preserved. The new world starts PAUSED, uses default40-second initial waits, and already has a connected living Extensive field and a vein lane. Server startup/deployment evidence is added below after verification.

1. Join participation under Players (or `/chronicles join`), choose Survival/Adventure, turn editing off, and resume play. Stand on the main sculk field around **8,65,22** until Hunger and Darkness begin. Both wait40 seconds by default; Game setup can shorten each to10 seconds while paused/editing.
2. Walk and jump in place, then move and jump across other infested blocks. Neither the initial wait nor the already active effects should restart.
3. The raised floor-vein lane runs from **2,66,2** to **14,66,2** over alternating sculk/stone supports. Try walking and jumping along it. A seam stays infested while some of your sole still touches a living sculk support or a supported floor vein.
4. Stand fully on a clean block or build a clean pillar above the field. Both campaign effects should clear immediately. Returning to sculk begins a new full wait. A vein directly coating the floor counts; a side-only vein beside a clean support does not create a nearby hazard.
5. Pause or sever the marked Heart connection at **-3,64,0**. Campaign effects should clear. Reconnect and resume to begin a fresh wait. Ordinary potion/Warden effects remain independent.

Please review this contact behavior before the next correction. The next point is the Altar workstation design, followed by Forge, using a dedicated UI designer and lead review before implementation.

## Verification

The final build passes309 Java tests, with70 Python tests passing. Actual native movement/restart passes132/20 client steps, including exact effect identity across the vein lane, clean pillar escape, cuts, foreign effects, death and reconnect. An independent review found no remaining material defect. [Exact native evidence](../../reports/ecology-smoke-20261022T103309789457Z), [deployment evidence](../../reports/qb09-contact-fix-deployment.json).

The new authenticated server is PAUSED and SchinderRenge remains operator level4. This is a fresh disposable world; the previous25588 world and manifest were untouched. The pack was CRC/embedded-jar/official-HUD-reference/options verified; SHA256`716acc634443d33671fb92d3ddbf6cc9c4d1278d6614e0f60bf2a50cae5324cc`. Its predecessor is retained as [client-pre-dev32-20261022T103632172502Z.mrpack](../../backups/client-pre-dev32-20261022T103632172502Z.mrpack). No map or gameplay balance acceptance is inferred from automated checks.
