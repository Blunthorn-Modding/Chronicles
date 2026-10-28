# CF22 — Unified Events UI design

**28 October status:** organizer-approved workflow implemented and natively verified in dev61. See [implementation](CF22_IMPLEMENTATION.md) and TEST_PLAN for actual evidence; this mockup remains the historical design artifact.

This is a design contract and an interactive local mockup, not a runtime change. Open [design/events.html](design/events.html), served locally at http://127.0.0.1:8925/events.html. Its **320×240 proof** is a bounded schematic: vertical navigation stays visible and List/Details switches the main pane. Form footers and popups stay inside that frame. It is not a tall mobile web layout or a rendered Minecraft screen.

## One Event form

Events keeps its existing place in Chronicle. + Create event sits above the list. Built-in/default events and custom events open the same native-style editor; there is no Composite-event category and no cue-selection layer.

The selected Clean view contains only the description and actions. **Edit event**, **Preview privately**, **Check audience** and **Review run** remain. Conditional **Disable automatic trigger**, **Remove event configuration** and **Allow event again** retain current capabilities. Custom events also have confirmed **Delete event**; pending execution/history rules are in the implementation contract. IDs, message content, sound status, trigger diagnostics and Saved-state details belong in Debug. The editor itself shows the values being edited.

The editor has four short internal pages with a persistent in-frame **Cancel** / **Create event** or **Save edits** footer:

1. **Basics** — required name and optional description. Create has a generated, editable Event ID; once created it is fixed. The server owns identifier validation, collision checks, cap and persistence errors.
2. **Message & sound** — optional message and optional registry sound through the shared searchable, paged picker. Message can be blank only when sound or a Saved-state change supplies an effect. The mockup uses minecraft:ambient.cave, the verified sound of the registered Quiet Below observations; event IDs are not sound IDs.
3. **Trigger** — existing Manual, Entry, Active time and Condition controls, plus Participants or Selected Region audience. Entry and audience choose existing Regions. Active time requires positive seconds. Condition uses a flag picker, preserving existing registered flags rather than raw typing. The sample data is not a new campaign condition or narrative commitment.
4. **Region changes** — optional existing Region Saved states. **Add Saved state…** opens the shared deliberate search → select → Add paged picker; targets can be removed without altering unrelated event values.

Audience/trigger Region references are separate from changed Region Saved states. Preview privately plays selected media only to the organizer; per-target **Show bounds** reuses private geometry preview without applying a state. Neither consumes completion. **Review run** names audience and planned changes, warns about replaced blocks/container contents and player exclusion, then requires explicit **Run event** confirmation. Media follows verified changes. Manual and automatic delivery share the same completion authority.

## Saved-state integrity

Initial support is one dimension and disjoint target bounds. The picker constrains later candidates to the first target's dimension; server validation remains authoritative. Missing Regions/states remain visible as missing references and block save/review rather than being silently erased. The mockup rejects overlapping cuboids from actual min/max coordinates, not names or list position.

The prototype presents up to eight target rows as a compact-layout exercise; that is provisional UI policy, not a story/configuration contract. There is no scheduled reset, story/session reset, or new timing model.

## Implementation seams

The current presentation seam is CoreMenuServer Events detail plus CoreMenuModel.EventForm; default content is registered through ObservationCatalog.Definition (for Quiet Below, QuietBelow.java). The native CatalogAddPopup supplies the picker interaction model for sound, flag and Saved-state selectors. The [implementation contract](CF22_EVENT_ACTIONS_PLAN.md) defines one event-execution authority, aggregate preflight/effects and backward-readable history. Native schema/wire work is not delivered by this mockup. Existing condition actions and direct Region saved-state operations remain available through their shared services; the new editor must not remove those capabilities or start a second scheduler.

## Prototype exercises

The mockup supports create, edit, cancel/revert, generated-ID-before-create versus fixed-ID-after-create, select/add/remove Saved states, sound/flag/Region picker pagination, missing/empty-effect validation, dimension/geometric-overlap rejection, private-preview wording, and explicit review/run wording. It uses local sample data only and makes no network or game request.

The external **Demo: make first state unavailable** control and status line are prototype test aids, not proposed Minecraft controls. The form itself uses local in-memory examples; reload restores them.

## Verified design evidence

The lead ran the cached Playwright CLI against a separate Edge profile on27 October. **66 browser checks pass**, with no JavaScript errors in the final run. Checks cover create/cancel/default preservation, generated and fixed IDs, actual picker selection/confirmation, separate audience/entry Regions, multiple targets, overlap/missing-state refusal, current audience/automatic-disable/configuration/reset controls, and persistent footer/picker containment in all four320×240 pages. Eight desktop/compact captures were produced in `../reports/cf22-ui-qa/` and the lead inspected the Basics, Region changes, picker and live-review layouts. The independent architecture review reports no remaining material plan blocker.

Initial prototype typing/picker/selection errors were found and fixed; a later browser timeout was a test-driver assumption about compact List/Details, corrected without changing that navigation. This evidence proves the browser prototype only. No Java build, native Minecraft interaction, terrain mutation, persistence migration, recovery or performance claim is made. Organizer workflow review remains the next boundary before native UI implementation.
