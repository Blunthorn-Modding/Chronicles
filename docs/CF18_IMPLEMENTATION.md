# CF18 implementation: Books & Lore

Core owns named books, their server configuration, delivery rules, discovery and keeping a copy available. Quiet Below supplies five unchanged research journals and defaults. The approved workflow is in [the design](CF18_BOOKS_LORE_UI_DESIGN.md); implementation is on `feat/core-books-lore`, candidate dev52. One combined organizer review follows native verification.

## Organizer workflow

Advanced > Books & Lore lists titles and authors. Create a book, edit its metadata or ordered literal pages, preview it with the vanilla book reader, and choose its distribution rule. New books start with distribution off. Cancel discards local edits. Removing a page and changing rules require confirmation. Metadata edits require an operator, non-active play and Editing. Stale saves preserve the unsent draft and require reopening current data. The read-only Open book handoff retains unsaved loot-table fields when returning; if that table changed meanwhile, Save is blocked until an explicit reload/discard.

Chests retains its existing binding, inspection, retyping and unbinding controls. A bound chest can have one specific named book with an independent percentage. Loot Tables retains Items, add/remove items, quantities, rich components, refill interval and whole-table save/reload. Its separate Lore books section adds named references and percentages. Items and lore share the existing 27-entry budget. Direct settings override the matching table-book percentage on that chest, including zero; different books still roll independently.

Books own their campaign-wide spawning rules:

- Off: no new automatic copies.
- Spawn once: one completed issuance; pending claims also reserve eligibility.
- Until found: eligible rolls continue until a real player receives a managed copy in inventory.
- Repeat: discovery does not stop future rolls.

Existing managed copies remain protected during refills. A copy already waiting suppresses another copy of that book in that chest. Metadata changes do not modify existing inventory copies. Found means actual server-side player inventory receipt during active play; cursor-only, failed/full transfers, creative/editor activity and preview do not count. Reading is unnecessary. Discovery is idempotent and survives reconnect, death and restart.

During active play an operator can choose **Keep placed until found** without enabling Editing. Choose the existing loot-table group; it immediately tries a book-only insertion/adoption, then uses that table's normal refill opportunities. The maintained copy may move between same-table chests. This does not reroll ordinary contents, reset the refill clock or create another scheduler. Full, missing or uncertain inventories defer. Known missing sources are not blindly replaced. Off/already-issued Spawn once requires a request-specific override; changed rules suspend maintenance for review. Stop keeps existing copies/history. Found books cannot restart keeping.

## Files and API

Campaign mods register `BookDefinition` defaults through `ChronicleCore.books()` before the registry freezes. `config/chronicle/books.json` schema1 stores complete configured overrides. Namespaced IDs are stable; UI creation uses server-generated `chronicle:book/<uuid>` IDs. Bounds are 256 books, title32/author80/description256 Unicode code points, 64 pages, 1024 code points/page and 16384 total/page content; the aggregate encoded JSON cap is8MiB. Deliberate blank pages retain their order. Use preview to inspect actual wrapping with the chosen resource pack/font.

Loot table JSON has an optional `books` array:

```json
"books": [{"bookId": "quiet_below:replace_with_registered_id", "chancePercent": 25}]
```

Select a real registered book through the picker; unknown references reject save/reload. Ordinary `entries` and custom components retain their existing format. Direct book settings live on the existing chest binding, not a second registry.

The shared `world/chronicle/chest-content.json` schema3 includes book provenance, issuance reservations, first discovery and keep requests. Old schema1/2 loads without rewriting until an actual mutation. Existing bindings, clocks, receipts and pending fills are retained. Book tokens are campaign/book scoped and validated against the server ledger; anonymous raw written books do not count as managed lore.

Pending operations explicitly distinguish FILL, book-only INSERT and same-table MOVE. Journal claims are saved before inventory mutation. MOVE stores both exact before/after inventories and pinned targets. Only proven complete inventories finalize issuance; exact original inventories can abort unmaterialized claims. Mixed/unknown recovery holds for existing explicit chest recovery. It never guesses or silently retries a half-transfer. Scheduled keep reconciliation is bounded to four requests and one inventory transaction per batch; deferred requests are stamped for that opportunity too.

Core menu wire IDs are v2, so dev52 requires a matching client. Public player menu snapshots do not include unpublished book definitions. Server authority, expected-definition comparison and opening-frame tokens guard writes.

Book configuration writes recheck observed bytes at the commit boundary, force-write temporary files, retain a valid `.bak`, and publish memory only after atomic replacement. These checks detect ordinary external edits; they are not a filesystem compare-and-swap against arbitrary noncooperating writers racing the final rename. Stop the server for bulk external editing, then reload after restart. The existing chest journal follows its established durable-write/recovery contract.

## Compatibility and rollback

The five journal titles, authors and fifteen pages are frozen. New Quiet Below defaults use named references with unchanged independent4% chances. Existing configured raw book entries remain full overrides and are not silently converted. To adopt named books, use Items to remove the old raw entries, then add the corresponding named volumes under Lore books and save the whole table. Existing books in inventories remain untouched.

Before downgrading to dev51 restore a stopped-world/config backup: the older runtime cannot read chest schema3 or menu v2. Never delete the journal to resolve a recovery hold. Map integration, group play and a realistic-load soak remain pending; the handcrafted map is not ready. Journal prose/events get their final review after main functionality.

## Verification

Current exact test results, native evidence and limitations are recorded at the top of [TEST_PLAN](TEST_PLAN.md). Native tests use explicitly guarded disposable offline loopback worlds; authenticated review servers receive no trial flags. Failed test worlds may be retired after stopping, while logs/screenshots are retained outside them.
