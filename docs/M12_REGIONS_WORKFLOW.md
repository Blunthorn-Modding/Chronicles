# Regions workflow — lead approved

## Decision

Move generic registered `REGION` requirements out of Game setup and into the existing **Regions** workflow. Regions keeps one list of saved regions. It gets no binding subpage, second list, or synthetic binding rows.

Game setup omits REGION entries. It retains global tools and current LOCATION requirements until the separate Points relocation work. **Heart weakness** remains a global tool.

## Saved regions

The middle list contains only `AuthoringSite.Region` entries. Rows retain name, dimension/bounds, revision, and concise `Used by` context. Requirements never appear as geometry.

Selected-region detail keeps **Show region**, **Edit region**, **Delete region**, and **Create region…**. Add one contextual action: **Use for…**. It opens a picker of installed `AuthoringCatalog.Kind.REGION` purposes, labelled from the generic catalog. Each picker row says unassigned, assigned here/current, assigned elsewhere, or stale. Choosing one confirms `REGION_ASSIGN` with that purpose and the selected saved region. The existing assignment service pins the current region revision; it copies no bounds.

`Used by` is informational and lists each attached purpose with current/stale status. The selected detail has exactly two assignment controls: **Use for…** and **Clear use…**. **Use for…** is the purpose picker above and may explicitly update a current/stale assignment. **Clear use…** opens a picker containing only purposes pinned to this region, then confirms `REGION_UNASSIGN` and retains the region. **Show region** remains the single geometry outline action; no per-purpose Show or per-purpose button is added.

Core obtains these actions by enumerating `AuthoringCatalog.Kind.REGION` and matching `MapSiteAssignments`; it does not recognize Source city, Sculk takeover, or another campaign ID.

## Existing draft flow

**Create region…** begins the current named draft. **Continue in world** and Position 1/2 work unchanged; **Create selected region** persists the normal ID/revision. It then selects the region detail, where the operator may deliberately choose **Use for…**. Creation never assigns automatically.

**Edit region** retains the current draft and **Save region changes** behaviour. Saving creates the existing new revision. Any assigned purpose pinned to the old revision becomes stale; the operator explicitly uses **Use for…** to update it. No assignment advances silently.

With no saved regions, show the current empty-state guidance and **Create region…**. The page-level summary lists any unassigned registered purposes with plain “Create or select a region, then use Use for…” guidance; it is informational and has no second assignment wizard. With an unassigned saved region, `Used by` says `None` and **Use for…** remains the one assignment route. Cancelled drafts/pickers/confirmations and denied edits leave saved sites and assignments unchanged.

## Game setup

Remove only `AuthoringCatalog.Kind.REGION` entries from Game setup. Its description becomes: “Global settings and point requirements. Region assignments are under Regions; block bindings are under Locations → Block sites.” Do not claim tools-only while LOCATION requirements still live there.

Source city and Sculk takeover therefore have one assignment route: select a saved region and use **Use for…**. Game setup has no duplicate assignment/status/clear controls. Points relocation and automatic block registration remain separate point-3 work.

## Acceptance checks

* Regions has one saved-geometry list, no binding selector or binding rows.
* Every installed `Kind.REGION` purpose is available through **Use for…**; Core has no Quiet Below ID condition.
* Assign, replacement, stale update, and clear reuse the existing revision-pinned assignment service; no bounds/revisions are copied or advanced silently.
* Draft, cancellation, denied edit, missing site, stale reference, and deletion retain existing safety behavior.
* Game setup omits REGION entries while retaining LOCATION requirements and global tools until the separate Points slice.

## Grounding

Current `CoreMenuServer` already has the region list/detail/drafts (`REGION_NEW`, `REGION_EDIT`, `REGION_CONTINUE`, `REGION_POSITION`, `REGION_SAVE`/`REGION_CREATE`) and revision-pinned assignment service. Current Control input is a finite kind rather than a per-control argument, so implementation should use finite generic `REGION_ASSIGN` and `REGION_UNASSIGN` actions with the selected region and picker result. Neither requires a payload/schema change. Commands remain backward compatible.

## Implementation

Dev.45 implements this design with two appended actions, preserving existing wire enum ordinals. The server checks the current menu page, registered purpose kind and saved region, then delegates to the existing operator/non-active-author/revision checks. Clearing additionally verifies that the purpose is still assigned to the selected region. Summary text is bounded for the full catalog; event and other consumer references remain visible.

Raw Game setup packets accept only point requirements. Explicit legacy `setup assign`, `setup clear` and `setup inspect` commands retain their generic behavior through a separate private command adapter using the same checked services. This does not introduce another menu route.

The complete Java build passes (40 seconds, 372 tests); all 77 Python tests pass. Native menu verification passes: `../reports/core-menu-smoke-20261023T182616805359Z/report.json`, with 124 operator checks, seven player checks, and 19 checks at each larger logical layout (480 and 640 wide). It covers actual picker/confirmation/cancellation, a single-corner edit, stale pins, explicit refresh, geometry retention, forged kinds/pages and command compatibility. The lead inspected the Regions screenshot; independent source/runtime review is clear. Synthetic 256-role stress and incompatible old clients are not native-tested.

The first native run submitted an empty coordinate field because its scrolling helper rebuilt the field after typing. The driver now presses Send directly, retaining the same real-coordinate assertion. The production coordinate parser was already correct and remains unchanged; no gameplay parser fix is claimed. The diagnosed disposable world was saved/stopped and retired, with its reports retained.

This point does not implement the separate supplied-block registration correction.
