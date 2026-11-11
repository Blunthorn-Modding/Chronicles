# CF-17 editor layout and Block-limit dialog

**Status: lead-approved before implementation.** The operator explicitly requested these corrections; this records the dedicated design and lead decisions.

This corrects the popup edge layout, makes Clean mode genuinely sparse, and replaces the full-detail Block limits flow with a small modal. It preserves server-owned menu tokens, finite actions/input kinds, revision checks, Game setup as the only limit route, and current IDs/commands. No new tab, dependency, game mode, campaign setting, or arbitrary command field is introduced.

## Delivery order

Implement and review these sequentially:

1. General editor layout and strict Clean/Debug presentation.
2. Block-limit modal using the corrected shared frame.

## Slice 1: inset popup and strict Clean view

### Frame geometry

The vanilla `popup/background` is a real frame. Every tab, header action, list, detail/form, footer action, feedback line, and modal stays inside it:

```text
frameX = clamp(width / 48, 6, 20)
frameY = clamp(height / 32, 6, 12)
frameW = width - 2 * frameX
frameH = height - 2 * frameY
pad = 8
contentLeft = frameX + pad
contentRight = frameX + frameW - pad
```

No widget has an X coordinate below `contentLeft` or a right edge beyond `contentRight`. This fixes the reported 1920x574 window: at **960x287 logical**, frame is `x=20 y=8 w=920 h=271`, and the first tab begins at `x=28`, never at window `x=0`.

| Logical size | Frame | First tab | Header action strip | Footer action strip |
| --- | --- | --- | --- | --- |
| 320x240 | x6 y7 w308 h226 | x14 | x97..306 | x14..306 |
| 480x270 | x10 y8 w460 h254 | x18 | x261..462 | x18..462 |
| 640x360 | x13 y11 w614 h338 | x21 | x418..619 | x21..619 |
| 960x287 | x20 y8 w920 h271 | x28 | x739..932 | x28..932 |

Header controls are ordered right-to-left: **Refresh**, **Debug**, **Enable/Stop editing**. Their minimum widths are 54, 60, and 87 with two-pixel gaps, so they fit even at 320x240 without crowding the Chronicle title. Debug moves from footer to this header strip. Close remains in the framed footer. Navigation starts at `frameY + 30`, uses the existing eight-tab rhythm and Advanced separator, and computes available height from `frameH`, never raw window height.

At320x240 and480x270, retain the existing compact drilldown: the left rail remains and detail replaces the list with `Back to list`.640x360 and the960x287 wide fixture use the list/detail split. The lead deliberately retains the established threshold so multi-field forms have usable width; merely fitting two120px text panels is not the acceptance rule. Text wraps within its panel and cannot run under tabs or header actions.

### Clean and Debug behavior

Clean is intentionally limited to selected title, selected description, any necessary interactive form, and currently available/disabled working controls. **Clean renders no `Detail.facts()` rows at all.** It therefore shows no status/count/capacity/location/revision/authority/receipt/raw-ID list. A disabled control’s amber reason remains visible because it is actionable, not a fact row.

Debug renders all existing `Detail.facts()` in server order, plus the same title, description, forms and controls. It adds no server data, changes no token, and changes no active control. Errors, confirmation text, validation failures and meaningful successful action results remain visible in both modes. The routine `Server state updated.` acknowledgement and idle “Choose an item” hint remain absent in Clean mode.

The top-right control is `Debug: off` / `Debug: on`, tooltip `Show or hide editor details on this client.` It remains the existing fail-soft local-only preference and is never sent through `CoreMenuPayloads`. Toggling may reflow widgets but preserves selected ID, list/detail scroll, form drafts, picker query, request token, authority state, and an unconfirmed confirmation. Passive refresh continues to use the current stable-widget path.

```text
      ┌──────────────────── vanilla popup frame ────────────────────┐
      │ Chronicle                   [Editing] [Debug] [Refresh]     │
      │ ┌────────┐ ┌─────────────────┐ ┌──────────────────────────┐ │
      │ │Session │ │ selectable rows │ │ Selected title           │ │
      │ │Players │ │                 │ │ description              │ │
      │ │Events  │ │                 │ │ [working control]        │ │
      │ │Advanced│ │                 │ │ [form, when required]    │ │
      │ │...     │ │                 │ │ unavailable reason        │ │
      │ └────────┘ └─────────────────┘ └──────────────────────────┘ │
      │ feedback / error                                      [Close]│
      └─────────────────────────────────────────────────────────────┘
```

## Slice 2: Block limits modal

`Game setup > Block limits` remains the only entry. Its normal detail has a description and **Choose block type…**. Pressing it opens a centered modal over the unchanged Game setup page. The page, tabs and selected item remain visible behind a dim scrim and are not interactive until Close.

The first modal is a registered-type chooser driven by existing `BLOCK_LIMITS` and `BLOCK_LIMIT_TYPE` picker messages. No new command syntax or protocol branch is needed. Current picker rows contain the bounded registered block ID as reference; the client may resolve it to an optional vanilla item icon. If no item exists or it cannot resolve, show the same text row without an icon. Icons never carry authority.

Chooser geometry is `min(248, width - 32)` wide and `min(206, height - 32)` high, centered in the popup frame. It has title **Choose block type**, seven visible 18-pixel rows, local up/down scrolling for more entries on the existing maximum-twelve picker page, page navigation when further catalog pages exist, and **Close**. Each row is `icon when available + label + placed / current limit`. Row selection sends the existing bounded selection and opens the policy modal. Close uses the current picker cancel/refresh path and writes nothing.

The policy modal is `min(224, width - 32)` by `min(158, height - 32)`, centered:

```text
                      Ancient Forge
                    1 / [ 1               ]
                    [ Save ]     [ Close ]
```

The left number is the placed count; only the right number is an editable field. Do not add separate redundant status/count paragraphs. The numeric field enables only under server-authorized editing and for a non-fixed type. Save is disabled until it parses as an integer at least equal to Placed. It then says `Limit cannot be below 1 placed block.` beside the input. The server repeats this check before writing, so a forged `BLOCK_LIMIT_SET` cannot lower capacity under current placed count. This deliberately replaces the previous allowance for an over-limit policy.

For Relic Heart, show `1 / 1` with a short `Fixed` label; there is no input or Save. A disabled editable type shows the actual server reason (editing off, hold, or policy-service state). Modal validation/server errors are amber and visible under strict Clean rules. Successful save displays a meaningful result, refreshes selected type and keeps modal open. Close writes nothing, discards only local input and returns to unchanged Game setup detail.

Save retains the existing `BLOCK_LIMIT_SET`, policy revision and stale request checks. The Save button itself is the explicit commit; no secondary confirmation is shown. A stale policy or server error retains the draft and shows a refusal in the modal. Changed policy requires closing and reopening before another save.

No modal action returns to Locations, opens a nested page, or edits an unregistered type.

## Minimal implementation sequence

### General editor

1. Refactor `CoreMenuScreen` geometry so frame/content bounds constrain all widgets/render lines. Move Debug to header.
2. Render no fact rows in Clean; render all facts in Debug. Preserve forms, controls, disabled reasons, error/result feedback and all server semantics.
3. Extend local/native screen hooks and layout smoke for 320x240, 480x270, 640x360 and 960x287.

### Limits modal

1. Add local modal state in `CoreMenuScreen`, driven by existing limit picker and selected `limit:` detail; draw it over retained Game setup view.
2. Reuse `BLOCK_LIMITS`, `BLOCK_LIMIT_TYPE`, `BLOCK_LIMIT_SET`, revision and refresh routes. No arbitrary wire field is needed.
3. Make the server limit setter reject a requested value below current placed count before persistent write. Preserve fixed Heart and stale/author checks.
4. Add focused source/native tests and independent review before human build.

## Acceptance and native checks

### General editor

* At all four logical sizes every widget lies inside popup content; no tab starts at x=0; Editing, Debug, Refresh and Close remain reachable.
* Clean detail shows title/description/forms/controls and zero fact/status rows. Debug reveals all facts in original order. Errors, confirmations, disabled reasons and meaningful result feedback appear in both modes.
* Debug toggle retains selected item, drafts, picker query, scroll and authority/token. Passive refresh retains native widget identities.
* Ordinary players remain Players-only; layout changes expose no operator data.

### Limits modal

* Only Game setup opens the chooser modal. Locations has neither visible control nor successful forged route.
* Chooser lists every registered type and draws an icon only when a resolvable item exists.
* An editor changes Forge 1→2 then 2→1 through modal. Cancel/Close leave JSON unchanged. Non-editor/stale requests fail.
* Value below placed count is locally disabled and server-rejected if forged. Heart stays fixed/read-only.
* Save itself is the explicit commit; do not add another confirmation dialog. Close/cancel returns to Game setup without a policy write or Locations route.

## Risks and remaining implementation choice

The existing picker permits twelve entries while compact modal displays seven rows; specified local scrolling is the smallest client-only solution and does not alter bounded picker pages. An unavailable block item may reserve blank 16 pixels or render text flush-left; both preserve label, accessibility and authority, so no product decision blocks implementation.

## Lead decisions

Approved slice1 before implementation. Content padding is a real bound for all visible controls and drawn text, including confirmations, prompts and wide/short windows. Header-only routine state is Debug-only; Clean shows title/description and working forms/controls, with errors and disabled-control explanations as feedback. The persistent in-world frozen/Building Mode HUD remains unchanged. Use the same compact/wide threshold as before unless measured panel widths require adjustment.

Approved slice2 design subject to backend review after slice1: the modal displays `placed / [editable maximum]` on one row, as requested. Save is the one explicit write action, no additional confirmation menu. Typed server state should carry counts/limits/editability rather than parsing translated fact prose. Retain the existing finite actions and revision checks; a bounded optional typed modal model is allowed if needed. Close discards the local value and cancels the picker without writing. Background navigation remains visible and unchanged but non-interactive. Legacy over-limit data remains loadable/non-destructive; new limit edits below actual reserved/placed count are rejected under the synchronized service lock.

## Slice1 verification

Full build50s/399 Java tests passes. Native menu `../reports/core-menu-smoke-20261024T200238459919Z` passes123 operator/9 ordinary-player checks and19 each at480,640 and the reported960x287 wide-short layout. Visible widgets are checked against actual inset content bounds; Clean has zero fact labels and Debug shows the entire server fact list in order, without changing authority. Lead inspected320 and960 captures. Independent source review clear after documenting the intentional320/480 compact threshold. Rendered text is visually/source reviewed rather than independently boxed by the widget assertion. The local prompt/confirmation pre/post-action frame assertion was added after this native compile and will run with the next slice's native suite. No separate client pack/deployment; slice2 follows.

## Slice2 verification

Full build33s passes401 Java tests (202Core/199Quiet Below); all77 Python tests pass. Native menu `../reports/core-menu-smoke-20261024T201824760282Z` passes123 operator/9 ordinary-player checks and19 each at480,640 and960x287. Lead inspected editable and fixed popups plus the wide-short backdrop. The popup retains Game setup, rejects background navigation, edits Forge1->2->1 with direct Save, and Close discards an unsaved7 draft without changing JSON.

Native managed-block test `../reports/managed-blocks-20261024T202142946902Z` passes all three phases including two saved restarts. It proves Save disables below placed count, a forged live-token request is refused by the server without policy/count change, lowering succeeds after removal, discovered overage remains intact, and cap2/count3 persists while further placement is denied. Independent source review is clear. More-than-seven-type chooser scrolling/pagination and a dedicated stale-frame modal Save are source-reviewed, not exercised by the current three-type native catalog; synchronized unit tests cover placement appearing between opening and saving. No separate human client/deployment; Heart follow-up joins this bundle.
