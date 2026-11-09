# Recipient-specific physical House handouts: implementation notes

Discovery only; physical delivery is not implemented by the dev.8 portability change. The owner has been asked whether delivery is automatic at first play or requested from the existing personal menu. Both current menu papers and the committed cast remain authoritative; no future complication selection is involved.

## Existing foundations and constraints

`BookDefinition` and the common `BookRuntime` catalogue are reusable content. Rendered private pages must never be registered there: catalogue pickers, chest rules and scenario export would then expose or redistribute them. `BookDeliveryRuntime` currently authenticates managed named books through campaign + book ID + delivery token and tracks campaign-wide first discovery. Its256-book lifecycle limit is shared with normal lore. One common book ID cannot stand for distinct recipients' private text. Fixed PlayerRole books and chest ONCE/UNTIL_FOUND rules must remain unchanged.

`RoleFirstEntry` already provides the important ordering: pin validated before/after inventory and book identities, prepare durable source/issuance, mutate inventory, save player data, then complete the receipt. Its operation currently requires a role and resolves books through the common catalogue. A card-bound generated grant therefore needs a small explicit Core extension; silently pretending it is another role would duplicate or corrupt role entitlements. Reuse its inventory proof/recovery and standard written-book rendering where practical, rather than creating another player-facing book library.

## Proposed delivery contract

- Last Council builds literal recipient bundles only from the immutable committed/late-enrollment cast. Pin rendered pages once in world-owned grant state; mutable reusable templates and public card edits cannot rewrite a queued or issued copy.
- Generic Core owns bounded pages, stable recipient/grant identity, inventory insertion and exact interrupted-operation recovery. Last Council owns House categories and cast-to-text mapping. The template/source definition is distinct from a rendered physical copy.
- Stable identity includes committed cast and character/grant identity; no observer/exposure counts or knowledge allocations are attached to an owner's papers. A physically traded book can intentionally reveal its contents.
- Inventory-full must wait without dropping books, overwriting items, consuming an entitlement or rerolling text. Plan/capacity checks precede writes. Do not blindly repeat a grant after an uncertain save: exact before/after evidence or operator recovery is required.
- Reconnect/restart/resume/repeated first-entry must not duplicate completed delivery. A new Chronicle play window is not a new cast. Late enrollment creates only the new character's grant and never reissues older characters' papers.
- Respect the existing32-character book title,64-page/1024-code-point page and16384-code-point total limits. Large valid categories need bounded volumes; do not truncate secrets to fit an item. Actual native book readability needs a rendering check, beyond JSON limits.
- Do not force private generated copies into the256-entry public named-book discovery ledger. Decide/implement an explicit personal-grant identity path while preserving ordinary book authenticity and recovery. Book-read Event integration should be an explicit later source contract, not an accidental side effect of catalogue registration.
- Additional-copy policy and recovery controls must be designed with the selected automatic/requested flow before native implementation. No implicit keep-inventory or restoration-on-death policy is introduced.

Acceptance: initial and late recipient delivery, physical show/trade, private catalogue/export exclusion, full inventory queue, forced interruption on each inventory/save/receipt seam, duplicate/stale requests, restart, pause/death/reconnect and unchanged fixed-role/chest behavior. Separate new UI workflows require their normal owner design review. Treaty and complication group acceptance remains deferred rather than blocking these foundations.
