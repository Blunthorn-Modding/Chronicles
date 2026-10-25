# CF18 merged Chest and Loot Table mockup

25 October 2026. Design only; the delivered mod remains dev51. This supplements [CF18](CF18_BOOKS_LORE.md) and the [UI design](CF18_BOOKS_LORE_UI_DESIGN.md). The [clickable mockup](design/books-lore.html) now includes existing item/container workflows rather than placeholders for them.

## Existing controls and additions

| Area | Existing behavior retained | Lore addition |
| --- | --- | --- |
| Loot table navigation | Existing profiles; direct selection and Open loot table open Items. | Separate selectable Lore books section; book-to-table handoffs open that section. |
| Item entries | Select/paginate entries; searchable paged registered-item picker; Add and Remove; independent chance and Min/Max quantity fields. New entries start at 100%, count 1-1. | Named-book rows use title/author, their own independent chance, Open book, Remove book and live Place now. Removing a reference does not delete the definition. |
| Validation | Finite chance 0-100%; integer counts 1-64, Min <= Max; finite refill minutes 0-10080, including fractions. Invalid drafts remain visible and cannot save. | One combined 27-entry budget across item and lore references, retaining the existing table bound. Both Add paths respect it. |
| Rich items | Numeric edits preserve opaque item components, including custom names and ordinary written books. Components/IDs stay in Debug. | Lore references are distinct from generic written-book items; central pages/rules stay in Books & Lore. |
| Table persistence controls | One confirmed whole-table Save; cancellation retains drafts; Discard restores loaded values; confirmed global Reload. Refill 0 disables timed refills, session-start filling still applies. Saves do not fill chests. | Item, refill and lore fields share one draft and save. Book discovery/history is outside reload. |
| Bind container | Bind a supported looked-at chest/barrel/shulker and select its existing table in one confirmed operation. Reject out-of-reach, unsupported and already-bound targets. | Optional direct book/chance is separate from the normal table; no second chest registry. |
| Bound container | Show chest; Open loot table; confirmed Change loot table and Unbind. Retyping preserves contents/history and other binding settings. Unbinding removes metadata only. | Retyping keeps the direct book. Unbinding clears its direct setting without deleting books or discovery history. |
| Play/editor boundary | Setup changes require non-active Editing; read-only Show/Open remain usable during play. | All three GM shortcuts use the same ACTIVE one-copy confirmation, without Editing. Default obeys central rule, explicit override bypasses it for that copy only. |

The prototype uses a small sampled item registry and in-memory configuration/world fixtures. Show chest illustrates the private outline and coordinates, rather than touching a Minecraft world. Bind/retype/unbind preserve the fixture's physical contents and fill history; rebinding the same block demonstrates that distinction. No new table creation/rename/deletion workflow is introduced, since those are not existing native controls.

## Source comparison

- `core/src/main/java/io/github/chronicle/core/server/CoreMenuServer.java`: CHESTS/LOOT_TABLES detail actions and LOOT_TABLE_OPEN/RETYPE/UNBIND/SAVE/RELOAD dispatch.
- `core/src/client/java/io/github/chronicle/core/client/CoreMenuScreen.java`: lootForm, saveLoot, addLoot and removeLoot preserve selection, paging, numeric drafts, rich components and whole-type save semantics.
- `core/src/main/java/io/github/chronicle/core/loot/LootType.java`: MAX_ENTRIES=27; finite fractional refill 0-10080. The later named-reference schema must retain the combined budget.

## Evidence and limits

Installed Edge: **133 checks pass** (29 editor regressions, 44 lore routing/GM regressions, 60 new existing-feature/parity checks). Scripts, exact results and screenshots: `../reports/books-lore-parity/report.json`, regression.js, routes.js and flows.js. Syntax and frozen journal comparison pass: all five titles/authors and fifteen pages match the campaign defaults. Root inspected desktop Items/Lore/Chest and compact Items/picker captures. Independent read-only review found and closed the combined-cap and default-tab issues; final source review found no remaining material regression.

The first editor regression attempt timed out because the test opened Preview before resetting its previously compact viewport. Correcting that test setup produced PASS29; no product change was needed. This is HTML workflow evidence only. Native permission enforcement, stale revisions, actual inventories, configuration errors, chance rolls, persistence and receipt atomicity still require CF18b-d implementation tests. No Java/native build was run for this HTML/documentation-only revision.
