# Configuration placement — design for review

**Superseded by dev.45/46:** the operator rejected nested Saved sites/Game bindings and duplicate Areas in Game setup. Use [Regions](M12_REGIONS_WORKFLOW.md) and [automatic flat block sites](M12_MANAGED_BLOCK_IMPLEMENTATION.md). The following records the historical dev.43 implementation, not the current workflow.


## Decision

Put every Quiet Below block anchor in **Locations → Block sites**. This includes **Ancient Altar**, **Ancient Forge**, and **Relic Heart**. Game setup retains game-wide settings and the existing **Source city** region assignment; it does not list block anchors. This is a presentation/routing change only: saved site IDs, revisions, `MapSiteAssignments`, and `AuthoringCatalog.Kind` remain the authority.

The block's intrinsic gameplay remains intrinsic. Binding an Altar or Forge here does not create, disable, relocate, or open its player-facing station screen. A saved block site is an optional named reference for setup/authoring consumers. The Relic Heart binding is shown here because ecology uses a particular anchor; its weakness rule is still a global game setting.

## Locations → Block sites

The accepted Points / Block sites selector remains at the top. In **Block sites**, add a second local list selector: **Saved sites** / **Game bindings**. It changes the middle list and retains the existing one-selected-item detail/action column. The wire filter values may be `POINT`, `BLOCK`, and `BINDINGS`; this is a generic filter extension, not a schema or protocol model.


### Saved sites view

The title is **Saved block sites** and its description is: “A saved reference to one exact block. Used for landmarks and game setup; binding it does not change the block.” The middle list contains only saved `AuthoringSite.Anchor` entries. A requirement never appears as if it were a saved site. Each row shows its saved name, dimension/position, expected block type, and a compact `Used by` summary. The existing **Bind looked-at block…** creates an anchor with the normal ID flow. **Show block site**, **Replace block site…**, and **Delete location…** retain their present meanings and revision behavior.

When no saved block site exists, show the empty list and **Bind looked-at block…**. A selected saved-site detail has only ordinary site actions plus **Manage game bindings**, which switches the local view. `Used by` is informational; it does not promise arbitrary linked cards or a batch panel.

### Game bindings view

The title is **Game bindings** and its description is: “Choose saved block sites for this game's registered requirements. This does not change the block.” The middle list contains only installed `AuthoringCatalog.Kind.BLOCK` requirements, including when no saved anchors exist. Each row uses a distinct wire ID `binding:<requirementId>`, kind `BINDING`, label and description from the installed catalog, and an unassigned/assigned saved-site summary. It is not a geometry row.

Selecting one binding shows one familiar detail and these controls:

| Binding | Empty state | Bound state | Actions |
| --- | --- | --- | --- |
| Ancient Altar | `No block site assigned` | saved site name and revision | **Choose existing site…**, **Create a block site…**, **Show assigned site**, **Clear assignment** |
| Ancient Forge | `No block site assigned` | saved site name and revision | same |
| Relic Heart | `No block site assigned` | saved site name and revision | same |

The selected detail is labelled **Game binding**, not “site” or “role.” It shows the catalog label/description and expected `BLOCK` geometry. Rows are produced generically from installed `AuthoringCatalog.Kind.BLOCK` requirements, so Core never checks Quiet Below IDs.

**Choose existing site…** opens the existing `BLOCK` picker and calls the current `SETUP_ASSIGN` path. It records the chosen saved site and revision, without copying coordinates. **Create a block site…** switches to Saved sites and starts the ordinary **Bind looked-at block…** flow. On success, return to the same Game binding detail; the operator then explicitly uses **Choose existing site…**. This two-step route has no automatic-assignment state or new protocol. Cancelling either creation or picker returns to the originating binding unchanged. **Clear assignment** calls the current clear path and leaves the saved block site intact. **Show assigned site** resolves the binding and uses the existing private outline. If its revision/type is stale, show the existing reference warning and offer Choose/Clear; never silently repair it.

Selecting a saved site shows the ordinary site detail first. Its `Used by` list is informational, and **Manage game bindings** switches to the binding list so the user can select the relevant requirement without confusing a named saved site with a requirement.

## Game setup

Game setup retains its existing list. Distinguish the map requirements with an `Area`/`Point` label and global tools with their setting names; no additional page or protocol structure is needed:

1. **Areas** keeps the existing Source city `REGION` requirement and its existing Choose/Show/Clear assignment flow. Region requirements stay here: moving them to Regions would broaden this point-six correction and mix a reusable geometry library with campaign configuration.
2. **Game settings** contains actual global tools. Rename the Heart rule entry to **Heart weakness**. Its description names the global threshold/wait behaviour, never a particular Heart site. Any real future Forge/Altar global tool belongs here only if it is a global rule, not a contextual block binding.

Do not show a second Relic Heart placement card in Game setup. The only route to choose or clear a Heart anchor is Locations → Block sites → Game bindings. The Game setup overview may have a one-line navigation link: `Block bindings are under Locations → Block sites.` It is navigation, not a duplicate card.

## Flow examples

* **New Forge reference:** Locations → Block sites → Game bindings → Ancient Forge → Create a block site… → Saved sites → Bind looked-at block… → name/review/save → return to Ancient Forge binding → Choose existing site…. The Forge itself remains usable whether assignment is made.
* **Assign existing Heart:** Locations → Block sites → Game bindings → Relic Heart → Choose existing site… → picker filters anchors → review/save assignment. The card shows saved ID and pinned revision.
* **Change or remove:** use Choose existing site… to replace atomically, or Clear assignment to remove only the campaign reference. Deleting a saved site presents its existing reference warning and does not manufacture replacement geometry.

## Acceptance checks

* Saved sites and Game bindings are separate local list views; each uses the existing single selected-item detail/action column. All installed `BLOCK` requirements appear in Game bindings before any site exists; only saved anchors appear in Saved sites.
* Core routes solely on `AuthoringCatalog.Kind.BLOCK`; no Quiet Below ID switch exists in generic routing.
* Altar/Forge/Heart binding shows, chooses, clears, and outlines through existing assignment/revision data without coordinate copies or changes to intrinsic block function.
* Source city remains a `REGION` card in Game setup. Heart weakness appears once as a global tool; Relic Heart placement appears once under Block sites.
* Empty, populated, stale, delete, replacement, cancelled creation/picker, and non-editing flows leave saved sites and assignments unchanged unless the user completes their existing explicit action. Permission-disabled controls state the existing edit reason and never start a picker or creation flow.
* No recipe browser, station player UI, global balance, schema, or campaign-specific Core branching is added by this design.

Lead review23October: approved after replacing stacked binding cards with the existing single-selection list/detail layout. Implement this narrow routing change; no separate workstation redesign or human design gate is necessary. Human inspection joins the combined review.

## Implemented contract

Core owns the generic BLOCK/BINDINGS views, context-aware assignment routing and existing revision-pinned assignment service. Quiet Below only registers the existing block requirements and renames its global tool to Heart weakness. There are no new saved schemas, coordinate copies, station gates or campaign-specific Core branches.

A header Refresh/edit toggle now retains the selected detail. Normal navigation abandons an unfinished binding-creation return route; cancelling the name prompt or anchor picker preserves saved data. A newly created site returns to its originating game binding without automatically assigning it. Legacy setup commands retain raw requirement IDs, with Brigadier quoting for namespaced IDs; the new Locations view requires a prefixed registered BLOCK binding.

## Verification: 23 October 2026

`python scripts/workspace.py build` passes in34s:178Core +177Quiet Below tests, zero failures/errors/skips. `python -m unittest discover -s scripts -p 'test_*.py' -q` passes77cases. `git diff --check` is clear.

`python scripts/workspace.py menu-smoke` passes in `../reports/core-menu-smoke-20261023T162858761884Z/report.json`:107operator checks,7ordinary-player checks and19layout checks at each additional480x270/640x360logical size, following the320x240functional run. Empty anchors/binding requirements, explicit creation/assignment, both cancellation routes, preserved header selection, abandoned-route reset, stale revision/revalidation, private Show, Clear retaining geometry, strict GM cross-kind/raw and non-operator forged packets, and legacy quoted setup commands pass. Actual native screenshots at all three sizes were inspected by the lead and independent reviewer. Final Cancel tooltip wording is copy-only, compiled/tested after that native pass.

Earlier attempts failed in the new test driver: Windows shell input converted ellipsis search labels to question marks; the scrolling helper rebuilt and cleared a name field; unquoted namespaced IDs failed Brigadier parsing. Corrected driver labels/submission/quoting, added rejected-response history, step diagnostics/watchdog and pacing below the normal20requests/second endpoint bound. One initial edit acknowledgement timeout coincided with a second account joining; no response history existed then, so stale-frame rejection is a hypothesis, not a proven product defect. The final driver waits for that second account before editing. Server authority/rate rules remain unchanged.

Independent source/runtime review is clear. This proves the disposable registered BLOCK workflow, not arbitrary third-party campaigns or production-map/group scale. Point6 joins the [combined human review](M12_COMBINED_REVIEW.md); no main merge.
