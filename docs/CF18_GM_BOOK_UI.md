# CF18 GM book status and replacement-copy UI

This small addition stays on **Advanced > Books & Lore**. It adds no new tab, registry or automatic-spawn rule.

## Selected book

The selected-book detail shows one short lifecycle line in Clean mode, beside the rule:

- `Discovery: Not found`
- `Discovery: Found by Ada (online)`
- `Discovery: Found by Ada (offline)`

The line is derived only from the authoritative campaign delivery ledger. It is visible to operators even after the recipient leaves. If a historical name cannot be resolved, Clean mode says `Found by an offline participant`. Debug adds the recorded UTC receipt time and first recipient UUID, because those are the durable historical fields. A book that has never been received is still `Not found`.

The detail also has a GM-only **Give book to player...** button. It is enabled during occupied ACTIVE play without a Chronicle operation hold; an empty participant list explains how to enroll a recipient. Editing is not required. The normal book preview and all existing book/edit/keep controls remain where they are.

## Give flow

1. Selecting **Give book to player...** opens a vanilla-popup-styled picker titled `Give <book title>`.
2. The middle of the popup lists up to six current eligible participants by live display name. It uses the same eligibility as active Chronicle play: enrolled, online, alive, non-spectator and not in author mode; Creative participants remain eligible. The list has previous/next controls when needed. Choosing a name advances to confirmation; no item is created yet.
3. The confirmation popup reads `Give one copy of <book title> to <player>?` and explains: `If this book is unfound, this player becomes its first finder.` Buttons are **Cancel** and **Give one copy**.
4. The server re-checks the menu frame, operator authority, ACTIVE/occupied/unheld state, the selected book, target eligibility and an empty inventory slot before inserting exactly one stack. The action cannot drop an item into the world, replace an occupied slot, force a chunk, refill a chest, change the book rule or restart keeping; first discovery completes any existing keep request. It returns to the selected book with either `Gave one copy to <player>.` or an actionable refusal.

A direct GM grant is a managed player-inventory receipt. If the book is unfound, the selected recipient becomes its first finder. If it is already found, the existing first finder and time remain unchanged. The copy is independent of automatic distribution and keeping, so restoration does not erase or duplicate that campaign record.

## Failure and refresh states

- No eligible participant: the popup shows an empty-list explanation and Cancel. Enroll participants under Players.
- A participant leaves, dies, changes mode, stops participating, or an operation hold/phase change occurs while the popup is open: confirmation is rejected after a fresh server check and the menu refreshes.
- A full target inventory: reject before mutating anything with `<player>'s inventory has no empty slot.` The GM may refresh and select another participant.
- A changed book selection or stale menu frame: reject without issuing and request review of the refreshed book.
- Before inserting, persist the administrative intent and conservative issuance claim. If these writes fail, insert nothing. Any exception after the claim holds play with an uncertain-outcome message; inspect inventory before deliberately issuing another copy. A crash between claim and insertion can leave an issued but unfound record. There is no automated replay or offline queue.

The picker has no offline or saved-participant rows. An offline character cannot receive a replacement through this workflow.

## Wire and test scope

Use the typed `BookGiveForm` carried in the selected book's existing Lore detail: `BOOK_GIVE_OPEN` requests the picker with an empty argument; `BOOK_GIVE_REVIEW` and `BOOK_GIVE` each carry only the selected target UUID. The server creates the player list from `PlayRuntime.eligiblePlayers`; the client never supplies names, eligibility or an item stack. The popup stores its opening menu token; a background refresh updates the parent menu and marks the popup stale rather than allowing a mutation against a changed frame.

Acceptance coverage: found/not-found and Debug receipt rendering; empty/eligible picker; cancellation; stale target/phase/hold rejection; full inventory leaves it unchanged; valid active participant receives one copy; an unfound book records that recipient as first finder while a later replacement preserves first finder/time; automatic rule is unchanged; replacement copies preserve history and completed keeping; server restart preserves ledger data. Cover one narrow native menu flow at GUI scale 2 and existing desktop/480 layouts.
