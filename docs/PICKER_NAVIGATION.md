# Shared picker correction

28 October 2026, dev64, branch `fix/shared-picker-navigation`. The organizer confirms the Event/flag functionality and reports redundant nonfunctional page controls and off-center trigger choices.

`CatalogAddPopup` now has only Up/Down navigation. At a loaded batch boundary these controls request the adjacent batch; returning backward lands on its last visible rows. Search resets to the first batch. Busy/stale locking and selection clearing remain. The bounded server payload and existing item/book owners are retained. Text-only choices occupy the full row; item/book choices retain their icon gutter.

The actual paging fault was in `CoreMenuScreen.requestEventCatalog`: it ignored the requested picker page and sent the main menu's index. It now sends the requested page. This applies to Event triggers, targets, activities and the other shared Event selectors. No domain, schema, protocol, dependency or campaign behavior changes.

The dedicated UI agent reviewed the design before implementation and the final production diff afterward. Native editor proofs at logical320x240 and640x360 each pass39 checks: every trigger across both server batches, final short batch, backward landing, full return to the first entry, search/clear reset, no separate page widgets and centered text-only row bounds, plus the existing editor checks. Both trigger frames were visually inspected. Evidence: `../reports/event-editor-smoke-20261028T174230946797Z` and `event-editor-smoke-20261028T174438118813Z`.

The shared menu regression passes110 operator/11ordinary checks, three additional layouts and a real restart pass21each: `../reports/core-menu-smoke-20261028T174637936008Z`. Final build/skip counts, Python checks, artifact hashes and saved deployment are recorded in the newest [TEST_PLAN](TEST_PLAN.md) and `../reports/dev64-validation.json` / `combined-dev64-deployment.json`. No unrelated gameplay/large-map/group certification is implied.

[Outstanding work](OUTSTANDING_WORK.md) reconciles the remaining Quiet Below delivery gates and deferred code with the latest organizer acceptances.
