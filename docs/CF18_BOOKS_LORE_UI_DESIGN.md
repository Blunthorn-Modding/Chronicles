# CF-18: Books & Lore authoring and discovery design

**Status:** lead reviewed; awaiting organizer workflow review. Design only; no implementation authorization is implied.

## Purpose and boundary

Named lore books are campaign definitions with their own discovery lifecycle. They may be placed in ordinary bound chests, but a book is not merely an anonymous `minecraft:written_book` loot row: an organizer needs to identify, edit, preview and intentionally distribute the named volume. **Books & Lore owns named content and its campaign-wide discovery/issuance rules; existing Loot Tables only reference registered books by stable ID.**

This design adds one operator-facing **Books & Lore** section under the existing Advanced part of the Chronicle menu. It keeps two concerns visibly separate:

1. **Definition** — the reusable named book: title, author, ordered pages and discovery/issuance rule.
2. **Placement** — two routes share the existing bound-chest registry and refill clock: an optional direct book with a per-chest chance, or a named-book entry in a normal loot table. Chests remain the only physical placement registry.

It deliberately does not add a second chest registry, another loot-table editor, recipe-only controls, graphical book art, a custom book reader, session-bound respawns, per-session quotas, or a lore-quest requirement. A discovered item still opens in Minecraft's ordinary written-book reader.

The present Forge journals remain Quiet Below content. This screen is a general Core authoring surface; campaign packages register campaign defaults and retain ownership of their prose and meaning, while organizers may add custom registered books. The five anonymous `Field journal I-V` volumes therefore remain compatible with the lore contract in [RESEARCH_JOURNALS.md](RESEARCH_JOURNALS.md), including arbitrary discovery order and no recipe unlock on finding or reading.

## Existing fit

The proposed screen follows the implemented Core menu rather than copying Minecraft's inventory UI:

- `CoreMenuModel.Page`, `Action`, `Entry`, `Detail`, `Picker`, `Control` and `View` already provide bounded typed navigation and forms. A future slice needs a `BOOKS_LORE` page and book-specific typed detail/form data, not free-form command parsing.
- `CoreMenuServer` already keeps a private per-player `Context`, performs every mutation server-side, compares a state `Frame` before mutation, and refreshes visible views. The book catalog and issuance/discovery revision must participate in that frame.
- `CoreMenuScreen` uses the vanilla `popup/background`, list/detail desktop layout, compact one-pane drill-in at narrow widths, native text fields, picker search, confirmation sheets, and a 10-second response timeout. The centered Block limits dialog is the precedent for the small modal selectors proposed below.
- Clean mode hides `Fact`s through `CoreMenuPresentation`; Debug is a local top-right preference. Book definitions must be readable in Clean mode through their description and intentional actions, while identifiers, revisions, component diagnostics and placement references remain Debug facts.
- Existing typed chest binding already resolves a looked-at container, chooses a `LootType`, and distinguishes editing a table from binding a chest. Existing `LootType` entry rows show a written book's title or an item-ID fallback, which explains why generic `written_book` identification is insufficient for important named lore.

The related Altar work confirms the product convention: show a concrete choice and cost/effect before an irreversible action, use normal native presentation, and keep campaign-specific meaning out of Core's generic UI ([M11_ALTAR_OPTIONS.md](M11_ALTAR_OPTIONS.md)).

## Information architecture

Advanced navigation becomes:

```text
Session / Players / Events
------------------------
Chests / Loot Tables / Books & Lore / Regions / Locations / Game setup
```

`Books & Lore` is beside, not inside, `Loot Tables`: it is the definition catalog. `Chests` remains where a physical container is bound. The book page owns no second chest registry. It shows read-only **Direct chests** and **Loot tables** usage pointers with hand-offs, never mirrored rules or probabilities.

### List and detail (normal width)

```text
Chronicle                                     Refresh  Debug: off  Enable editing
Session            Books & Lore               Field journal II
Players            [ + New book ]             Selected description; Author; 3 pages
Events             Field journal I            [Edit details] [Edit pages] [Preview]
Chests             Field journal II           Distribution: Until found [Change...]
Loot Tables        Field journal III          [Place now...] (ACTIVE operator control)
Books & Lore       ...                        Direct chests: Library chest [Open chest]
Regions                                       Loot tables: Mine cache [Open table]
Locations
Game setup
```

In **Clean** view, a list row has the title only. The selected detail shows its organizer-facing description, author, page count, current discovery label, a concise `Appears in` table title, and available actions. It does not dump raw components, identifiers, chest-binding internals, or duplicated chance/count controls.

In **Debug** view only, detail adds immutable book ID, definition revision, serialized-item/component validity, campaign owner/source, issued/not-issued and found/not-found receipt facts, each referencing loot-table ID and entry count, and last server refresh/staleness diagnostics. Debug does not expose an extra mutation. An author preview is never an issue, inventory receipt, or discovery event.

The page remains capped to the existing list/picker bounds and uses the established search/pagination behavior. Titles are the primary display label; IDs are never the normal way to distinguish books.

### Compact / narrow flow

At the existing compact breakpoint, choose a book from the list, then enter a one-pane detail with **Back to list**. Do not force a two-column layout or horizontal scrolling. Page editing and rule selection open one centered native popup at a time; the background is inert, Escape/Close cancels the modal, and scroll affects only its own bounded list or text area. Long page prose scrolls within the editor/preview rather than growing the base popup off-screen.

## Book definition flows

### Create and edit metadata

**+ New book** opens a small native popup:

```text
New lore book
Title   [____________________________]
Author  [____________________________]  (optional; displayed by vanilla reader)
Short organizer description [________________________]
                         [Cancel] [Create and edit pages]
```

- **Title** is required and is the label shown in Books & Lore and in loot entry rows.
- **Author** is optional, explicit and stored in the normal written-book component. The current registered Forge defaults use the existing literal author **Unknown**; preserve that exact configured value until the organizer's final prose pass, rather than rewriting or synthesizing lore in this UI.
- **Short organizer description** is not prose page content. It makes a catalog row intelligible without spoiling every page.
- **Create and edit pages** creates the registered book with **Distribution off**, then opens page editing. It does not add the book to a chest and does not generate an item. This uses the book definition itself as the draft; there is no separate draft registry.
- **Cancel** changes nothing.

On an existing book, **Edit details** opens the same popup with current title/author/description. It uses explicit **Save details** and **Cancel**. The title cannot silently be changed merely because a chest loot row was edited.

### Page editor

**Edit pages** opens one native popup, not a custom reader:

```text
Edit pages — Field journal II                         2 / 3
[ page text, wrapped as ordinary written-book content              ]
[                                                             ]
[< Previous] [Next >]  [Add page] [Remove page…]
                                     [Cancel] [Save pages]
```

- The editor changes one ordered page at a time, with an explicit current-page indicator.
- **Previous/Next** switch only the local page draft after retaining typed text; no server write occurs until **Save pages**.
- **Add page** appends a blank page, subject to the implementation's bounded page and text limits. **Remove page…** requires a confirmation that names the page number and says it affects the definition, not any already discovered inventory copy.
- **Save pages** validates ordinary written-book component limits and page order server-side, then saves the whole edited page set atomically. A failed validation leaves the local draft available to correct.
- **Distribution** remains **Off** for a new or empty book. It cannot be added to a loot table or switched to an issuance rule until it has at least one valid nonblank page. This is a visible state of the registered book definition, not a separate draft registry.

### Safe preview

**Preview** opens the actual read-only vanilla `BookScreen` for the definition's ordinary written-book component, with a **Return to editor** action when preview was opened from an editor. It is not a separately designed reader.

```text
Preview — Field journal II                            2 / 3
Author: [blank]

<rendered page text exactly as the item will contain>

[< Previous] [Next >]                                  [Close]
```

Preview does not grant an item, alter issuance/discovery state, fill a chest, mark a book found, trigger an event, or call a custom reader. It is an organizer-only rendering/fit check in the same vanilla reader players use; after a successful edit the server returns the saved definition and preview uses that revision. The actual item continues to use vanilla reading when found in the world.

## Discovery lifecycle and chest integration

### Discovery policy selector

Each definition has an explicitly named campaign-wide issuance/discovery rule shown in Clean detail. For managed modes, **found is settled as the first durable receipt of that named book into any player's inventory during normal ACTIVE gameplay**—not author/editor preview, chest generation, reader opening, page viewing, or map-building/editing activity.

```text
Discovery: Not found · Until found           [Change…]
```

**Change…** opens a centered choice popup. Its labels describe settled campaign-wide rules; they do not claim a backend API shape.

```text
Discovery rule
( ) Distribution off
    Stop new managed copies from being issued; existing table references and copies remain unchanged.
( ) Spawn once
    Allow one managed issuance only, even if no player ever receives that copy.
(*) Until found (recommended default for recipe books)
    Continue eligible chest rolls until a player receives this book in inventory.
( ) Repeat
    Continue eligible rolls even after discovery.

                   [Cancel] [Save rule]
```

The selector must also state, before confirmation, that it changes future eligibility only: it does not remove existing book copies, mutate chest contents now, reset an existing discovery record, or alter unrelated loot entries. **Save rule** requires an explicit confirmation sheet with the title and selected rule. **Cancel** has no effect. For `Spawn once`, ordinary refills must retain an issued but undiscovered managed copy rather than deleting the campaign's sole attempt; it never authorizes another eligible roll. `Until found` is distinct: it remains eligible for further rolls until the first real inventory receipt.

Do not present a fake reset/re-spawn action in this slice. A future deliberate organizer reset, if approved, must be separate, confirmed and audit-visible because it changes campaign discovery history.

### Add named book to existing loot

Book distribution starts from one book definition, then deliberately hands off to the existing table editor:

```text
Field journal II
[Add to loot table…]
```

1. **Add to loot table…** opens the existing typed loot-table picker, filtered/searchable by table title (with stable ID only in Debug).
2. The organizer chooses a table, then lands in that table's normal entry editor with a new **Named book: Field journal II — [author if present]** entry selected. The table stores the book's stable registered ID, not a copied page/component payload.
3. The ordinary existing controls set independent chance and count. For a named lore book, the first implementation constrains count to exactly one; its discovery rule is not encoded as a duplicated chance/count convention.
4. **Save loot table** uses the existing whole-table confirmation and makes no immediate chest fill. The confirmation names both the book and table and says that future eligible fills use it.

Conversely, within `Loot Tables`, **Add book…** opens the named-book picker (title and author visible; stable ID is Debug-only), and a named-book entry displays title, author when present, and a small **Lore book** kind label. **Open book** is a read-only hand-off back to Books & Lore. A missing/deleted definition must be plainly shown as unavailable and must prevent a new save rather than silently degrading to a generic written book. Existing arbitrary written-book components remain ordinary loot entries; do not automatically migrate or claim them as named lore.

`Appears in` is a read-only split summary of **Direct chests** and **Loot tables**, with **Open chest** or **Open table** hand-offs only. It deliberately contains no mirrored chance, count or rule controls. `Chests` keeps the existing physical bound-chest registry and refill clock, but now has the optional direct named-book setting described below; it does not create another registry or scheduler. The distinction is intentional:

```text
Books & Lore definition  --Add to loot table--> Loot table --bind--> Physical chest
  title/pages/rule                                  chance             coordinates/refill clock
           \---------------- direct book + chance ------------------/
```

This avoids duplicate placement/discovery records while still making important named books discoverable through chests.

### Two automatic distribution routes

Both routes use the existing bound chest registry and its existing refill clock. They do not create a second placement registry, scheduler, or lifecycle record.

**Direct chest book.** In `Chests`, a selected already-bound chest has an optional **Specific lore book…** picker and a **Chance** field. The picker lists registered, valid books by title and author. The chosen book is one independent direct roll beside ordinary table items and the table's other lore entries. The field is local to that chest; it does not change the central book rule or any other chest.

If the same named book is also in the chest's normal loot table, the direct chance overrides that book's table chance for this chest. It is one roll and at most one copy, never two competing rolls. Other book entries and ordinary items in the table are unaffected. Clearing the optional direct setting restores the table's normal chance for that book.

**Normal loot-table book.** `Loot Tables` has two selectable sections, opening on Items when entered directly:

```text
Items
  Torch                         70%  x4–12
  Bread                         40%  x1–3

Lore books
  Field journal II — Unknown     4%  [Open book]
  [Add book…]
```

The Lore books section uses the existing table's independent chance controls and references a registered book by stable ID. It never displays or edits central book discovery/issuance rules. Its **Add book…** picker shows title and author; Debug may additionally show the stable ID. `Open book` is a read-only hand-off.

The selected book's **Appears in** remains read-only and split into **Direct chests** and **Loot tables**, each with an appropriate hand-off. It shows neither chance nor rule controls, so each distribution source owns its chance while Books & Lore remains the only lifecycle authority.

### Operator placement during ACTIVE play

`Place book now…` is an operator play control, not metadata editing. It is available during **ACTIVE** play without requiring Editing, from all three shortcuts:

- From a selected bound chest, choose any registered valid book.
- From a selected book, choose an existing bound chest.
- From a table Lore books row, choose an existing bound chest; table-linked chests are preferred.

All shortcuts arrive at one confirmation sheet naming the exact chest and book and stating **one copy**, **bypass chance**, **no refill reschedule**, and **no discovery record yet**. It defaults to respecting the selected book's central rule. If that rule blocks placement because distribution is Off, the book has already Spawned once, or it is already found, the sheet visibly offers **Override book spawning rule for this copy**. The toggle is explicit; it does not alter the saved rule or discovery history.

Placing a book never overwrites existing inventory items, consumes no normal refill cycle, and does not count as found. A later real player-inventory receipt may record discovery under the normal central rule. The operation fails clearly if the target chest is unloaded or has no room; it never creates a new physical target or delayed placement job.

### Existing Chest and Loot Table workflow retained

The Books & Lore additions extend, rather than replace, the current Core screens.

**Chests** retains `Bind looked-at chest…`: a supported looked-at chest, barrel, or shulker selects a loot table and requires the usual confirmation. An unsupported, absent, or out-of-reach target cannot bind and explains why. A selected bound chest retains **Show chest** (private outline/coordinates), **Open loot table**, **Change loot table…** (confirmed; preserves its direct-book setting, current contents, and fill history), and **Unbind chest** (confirmed; removes only the binding). Read-only Show/Open remain available; bind/change/unbind and the direct-book setting require non-active Editing. No action creates a second chest registry.

**Loot Tables** retains the native whole-table editor. Its shared draft has ordinary **Items** and **Lore books** sections, one shared `Refill minutes` value (0 disables timed refill; session-start filling still applies), and one confirmed **Save loot table** that commits item entries, rich item components, refill minutes, lore references, and their chances atomically. **Reload loot configuration** is confirmed and reloads configuration only; it does not rewrite discovered books or history and rejects malformed input without changing the loaded draft.

Items keep the current independent row workflow: select an entry; use searchable/paged **Add item** registry picker (new entry defaults to 100%, count 1–1); **Remove item**; edit chance 0–100 and integer min/max 1–64 with min ≤ max; preserve rich components when numeric fields change. The shared cap is 27 total entries across Items and Lore books; both Add controls disable at that combined cap. The selected-entry window shows three rows with previous/next entry controls when needed. Lore rows are stable named-book references with their own chance, open/remove controls, and ACTIVE **Place now…**; they are not raw item-component stacks and never duplicate central rules.

## Authority, stale saves and failure behavior

Metadata, pages, rules, direct-book settings, and loot-table entry edits require the established three conditions: permission level 2, author editing enabled, and non-active/editable server state. Controls remain visible but disabled with the current plain-language reason (for example, “Pause play to edit these settings” or “Enable editing to change these settings”). `Place book now…` is the narrow exception: it requires an operator and ACTIVE play, but not Editing; it does not change metadata, schedules, rules, or discovery history. Preview and browsing are read-only and never issue an item or make a discovery receipt.

Book catalog revision and campaign-wide issuance/discovery state must be included in the server view frame. Each save carries the viewed book revision plus the menu token. If another organizer, a reload, an issue, or an inventory receipt changes relevant state, server rejects the mutation, returns a refreshed view, and says: **“This book changed elsewhere. Review the refreshed version before saving.”** No blind overwrite, silent merge, or retry occurs.

For in-progress page or metadata text, preserve the local draft on ordinary validation/network errors where the selected definition is still current, just as the current loot editor retains its draft after a failed save. On a true stale rejection, retain the unsent text only as a clearly labelled local draft; do not auto-apply it to the refreshed definition. Closing/reopening or choosing another book discards unsaved drafts after a warning if the client already has a dirty-draft convention.

For a discovery-rule save or loot-table attachment, the existing confirmation sheet is mandatory and must use the current token. A stale confirmation cannot be accepted. Persist before making the new state visible; on an uncertain I/O failure, state that it is unsafe to retry blindly and reload the authoritative view.

## Smallest implementation slice

The smallest coherent slice is deliberately narrower than a full lore system:

1. Add `Books & Lore` to the existing Core navigation with a server-owned named-book catalog of stable ID, title, optional author, description, ordinary book pages, revision, campaign-default/custom source, and issuance/discovery rule.
2. Implement list/detail, metadata/page edit, read-only vanilla-component preview and Clean/Debug behavior above.
3. Add the four settled rule choices, revision-pinned server validation, campaign-wide inventory-receipt discovery records, and the one-issued-undiscovered-copy retention requirement. The exact server API and storage/transaction shape remain root architecture work.
4. Extend the existing typed loot entry model only enough to reference a named book definition by stable ID and render title/author; give Loot Tables separate Items and Lore books sections. Add an optional per-bound-chest direct named-book chance with same-book override semantics, plus bidirectional hand-offs. Reuse the current chance, whole-table save, bound-chest registry and chest-fill scheduling—without duplicating them in Books & Lore.
5. Seed/migrate nothing automatically. Quiet Below registered campaign defaults and organizer-created custom books can coexist; existing `quiet_below:forge_notes` defaults and ordinary configured books remain compatible.

Out of slice: global search across all written books, rich text styling beyond normal components, custom reading UI, player-facing book catalog, quest/event unlocks, story ordering, placement maps, session quotas, respawn delivery, and a reset/discovery-history administration tool. The one bounded operator-only ACTIVE placement control above is the sole direct grant-like path.

## Settled product decisions and remaining risks

| Settled decision | Product rule | Implementation detail still open |
| --- | --- | --- |
| What counts as **found**? | A campaign-wide recorded receipt when a named-book stack enters any player's inventory. | Atomicity/ordering around inventory transfer and durable receipt write. |
| Discovery scope | Campaign-wide, never per character or session. | Receipt schema, audit data and reload compatibility. |
| Incomplete/new book | `Distribution off` until pages are valid; no separate draft registry. | Validation boundary and whether metadata-only books may be saved. |
| Recipe-book default | `Until found`. | How typed fill consults the registered rule without changing its scheduler. |
| Spawn once | One issuance only, even if it is never found; retain managed copies waiting in chests through normal refills. | Locating/retaining that managed copy without a second chest registry. |
| Repeat | Repeat eligible rolls even after recorded discovery; history is not reset. | Exact UI warning and legacy/config migration handling. |
| Preview audience | Operators only; author preview is not issuance or discovery. | Rendering implementation only. |

The main risk is transactional, not visual: a campaign-wide discovery receipt must be durable and idempotent across restart/reconnect, and recorded with the approved inventory transition. The system must define behavior for a full inventory, failed transfer, dropped item, disconnected player, simultaneous pickups and a normal refill while a once-issued book remains undiscovered. It must not use client-open/read events as authority, turn preview into issuance, or create a parallel chest registry/scheduler. Those backend decisions belong to the root implementation plan.

## Verification expectations for an implementation

At minimum, cover server-side bounds/authorization/stale-revision tests; title/author/page round-trip; normal vanilla item components and read-only vanilla BookScreen preview/return; all four rules with campaign-wide inventory-receipt discovery; receipt idempotency across restart; failed/full-inventory and simultaneous-discovery behavior; one-issued-undiscovered-copy retention across normal refill; named-book stable-reference validation; preservation of unrelated generic written-book loot; no immediate chest refill on attaching/saving; and old configured `quiet_below:forge_notes` preservation.

Extend the existing Core menu layout/native smoke coverage at 480, 640 and 960 logical widths: Books & Lore navigation, Clean facts hidden, Debug facts visible, compact Back to list, page editor/preview scroll and modal containment, disabled editing during ACTIVE, stale-save message/draft behavior, picker/table hand-off and confirmation cancellation. Verify all interactive controls stay within the vanilla popup and that the actual received named item opens with the normal Minecraft reader.

All managed named-book copies waiting in bound chests retain refill protection until taken, including copies waiting after global discovery and GM-restored copies. Repeat ignores discovery for future rolls; it does not erase book identity/history. Raw generic written books keep their ordinary replacement behavior. Manual placement has its own operation purpose and never consumes a normal refill cycle.

Entering ACTIVE play clears setup Editing, as in the current Core lifecycle. Metadata/table/direct-setting controls remain locked; live operator placement has no Editing prerequisite. The Items section retains the existing full add/select/edit/remove/chance/min-max quantity editor. Ordinary item and chest controls are now interactive in this merged mockup, alongside the lore additions. They operate on sample in-memory data; the item picker samples the Minecraft registry and Show chest simulates an outline. Configuration errors, real world inventories, authoritative permissions and persistence require later native implementation verification.
