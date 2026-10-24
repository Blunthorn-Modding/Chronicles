# CF-16 editor UI design

**Status: lead-reviewed and approved24 October before implementation.** User authorization is the request to continue; this document records the reviewed UI design. Implement the Block limits correction first, then the four polish items as one review bundle.

This design covers the next Core menu correction and the four later presentation items as one operator review. It keeps the current bounded, server-authoritative `CoreMenuModel` protocol: page, selected ID, finite action/input kind, token and revision remain the contract. It does not rename saved IDs, rewrite legacy commands, add client-owned permission state, or broaden protocol limits.

## Outcome and boundaries

An organizer must be able to answer three questions without hunting through nested menus: what kind of thing is selected, what can be done now, and why an action is unavailable. The accepted navigation remains in this order:

* Session
* Players
* Events
* Advanced: Chests, Loot Tables, Regions, Locations, Game setup

Locations retains its top selector, **Points** and **Block sites**. Points are saved positions and facing used by game systems; Block sites are physical supplied blocks or optional manual vanilla/third-party references. They intentionally remain distinct lists.

The editor borrows the Altar's native popup, legible white text, restrained spacing, standard `ButtonWidget` and `TextFieldWidget` controls, and no inventory panel. It does not borrow player-facing cryptic prayer wording, prayer choices, inventory behavior, or gameplay transport.

## Block limits: one policy destination

**Game setup > Block limits** is the only editor for managed-block limits. It is a Core-owned entry which lists every currently registered `ManagedBlockDefinition` type; campaigns do not add an Altar/Forge-specific page. The existing finite `BLOCK_LIMITS`, `BLOCK_LIMIT_SET` and `BLOCK_LIMIT_TYPE` route is sufficient. Implementation moves its accepted page/guard from `Locations/BLOCK` to `SETUP`; it does not create command-string controls or a second policy store.

The Game setup list begins with `Block limits`, then registered campaign tools. Its detail has a short purpose sentence and a middle picker/list of types:

```text
                         Game setup
 ┌────────────┬─────────────────────────┬──────────────────────────────┐
 │ Session    │ Block limits            │ Ancient Altar                 │
 │ Players    │ Ecology                 │ Registered block             │
 │ Events     │ Heart weakness          │ quiet_below:ancient_altar    │
 │────────────│                         │ Placed: 1 / Limit: 1         │
 │ Chests     │                         │ [ Set limit...              ] │
 │ Loot Tables│                         │ Existing blocks stay put.    │
 │ Regions    │                         │ New placement is refused at  │
 │ Locations  │                         │ the limit.                   │
 │ Game setup │                         │                              │
 └────────────┴─────────────────────────┴──────────────────────────────┘
```

Each row reads `Label — count / cap` and has a concise state: `Available` below the cap, `Limit reached` at it, or `Over limit` after a policy reduction. Existing blocks remain; new placement is refused. The structural Relic Heart reads `Fixed: 1` and exposes no value entry. Unknown/unloaded instances remain included in displayed count. A disabled value action states the real reason (editing must be enabled, policy service is checking, or a hold must be resolved), rather than silently dropping the choice. A successful save returns to the same type and refreshes count/state; Cancel returns without a write.

`Locations > Block sites` has no `Block limits` entry, detail control, back link, or policy picker. A supplied block detail only displays `Capacity: 1 / 1 — Limit reached` (or `Available`/`Over limit`) with normal `Show block site` and separately scoped manual-reference actions. A manual reference says it does not change capacity. The registry stays observable without becoming a second configuration destination.

## Shared layout and navigation

At normal and wide logical sizes, every page uses the same hierarchy:

```text
 Chronicle                                      [Editing] [Refresh] [Close]
 Session 4 · Paused                         one short, actionable state line
 ┌──────────────┬──────────────────────────┬─────────────────────────────┐
 │ Session      │ Select an item            │ Selected item                │
 │ Players      │ [row]                     │ Purpose in plain language.   │
 │ Events       │ [row]                     │ Essential status             │
 │──────────────│ [row]                     │ [Primary action]             │
 │ Chests       │ [page / search when used] │ [Secondary action]           │
 │ Loot Tables  │                          │ Why unavailable, if needed. │
 │ Regions      │                          │                              │
 │ Locations    │                          │                              │
 │ Game setup   │                          │                              │
 └──────────────┴──────────────────────────┴─────────────────────────────┘
 feedback / confirmation / error              active time only if useful
```

The left tab is stable; it does not become an action button. The middle column is always selection, paging, search, or a bounded picker. The right column is the selected item's explanation, necessary facts, and actions. Selection never executes an action. Destructive/world-changing actions retain the current explicit confirmation screen. Pickers replace only the middle column and retain source detail/draft; Cancel returns to that exact detail.

At compact logical widths, retain the left rail and one main column. Opening a row changes the main column to detail and shows `Back to list` first. The 320x240 and480x270 fixtures use this layout;640x360 restores list/detail columns. Locations' Points/Block sites selector remains above its list. Forms, facts, and actions scroll only inside the main panel; header, feedback, and Close stay reachable. No action relies on hover-only text.

The popup/background treatment is the Altar convention: centered opaque vanilla popup texture, darkened world behind it, white title/body text with shadow, gray secondary text, amber blocked-state text, green success text, and normal Minecraft widget borders. Use `popup/background`, `DrawContext`, `ButtonWidget`, and `TextFieldWidget` rather than a fixed image or inventory screen. Texture packs can replace normal GUI assets. No custom inventory grid, embedded browser, or FancyMenu dependency is introduced.

## Per-page normal workflow and copy

| Page | Middle list | Right detail/actions | Clean wording |
| --- | --- | --- | --- |
| Session | `Play controls`, `Activity` | Start, Pause/Resume, Finish for now; Activity is read-only recent record | “Play continues in this world between meetings.” |
| Players | known online/saved players | participation, own card/read card; operator-only destinations | `Starting position`, `After-death destination` |
| Events | visible `Cue`, `Game action`, `Condition` rows | private Preview, optional private sound, Check audience/readiness, then Run after review; configure cue trigger while editing | “Cue” means a player message/sound; “Game action” changes registered campaign state. |
| Chests | physical bound containers | Bind looked-at chest, Show, Change loot table, Unbind | “Binding chooses a table; it does not fill now.” |
| Loot Tables | reusable loot types | Add item, chance/range/component entry, refill interval, Save | “Each item rolls independently. Scheduled fills replace contents.” |
| Regions | saved regions and active draft | Create, Continue in world, Change position 1/2, Save/Create, Show, Use for, Clear use | “Continue in world turns on the private box. At each corner run `/chronicles author region pos1` or `pos2`; return here to save.” |
| Locations > Points | saved positions | Save my position, Update, Show, Use for, Clear use | “A point is a saved position and facing; it does not require a block.” |
| Locations > Block sites | supplied managed blocks plus manual references | Show; manual-only Replace/Delete | “A block site is physical. Supplied blocks register when placed; manual references do not change capacity.” |
| Game setup | Core Block limits then registered global tools | select a type/tool and perform bounded actions | “Global campaign settings. Region uses belong under Regions; point uses under Locations.” |

The Events page must not call all events “observations.” For cue rows rename `Preview event` to **Preview cue**, `Check live audience` to **Check audience**, and `Run event…` to **Review run**; after the existing review it becomes **Run now**. Game-action rows use **Preview action** and **Check readiness**. This preserves semantics: preview is private/no state change, check is read-only, review calculates current eligibility, and Run commits the one allowed action.

`Activity` is a log, not session recovery. Label it **Activity** with subtitle “Recent Chronicle activity.” Do not introduce session preview/recovery, because the campaign world continues outside a meeting.

## Clean editor and Debug mode

Default is **Clean view**. It shows facts needed for the next choice, the selected item's ordinary state, and every actionable error/confirmation. A small operator-only header toggle reads `Debug: off` / `Debug: on`; it is a client presentation preference only. It never changes server authorization, edit mode, actions, tokens, or persisted campaign state. Retain it only in the local client options/profile mechanism already used for client preferences; new/unavailable config falls back to off.

Use a client-side `Fact` classification/render filter. Do not remove facts from the server response or weaken its bounded envelope. The same response must render either mode, preserving refresh and token behavior.

| Fact class | Clean view | Debug view | Existing examples |
| --- | --- | --- | --- |
| Purpose/actionable state | always | always | selected description, participation, connection, chest loot table, region bounds, managed capacity |
| User-actionable blocked state | always, amber | always | pause-before-editing, hold reason, missing corners, invalid target, unavailable policy service |
| Confirmation/result | always, green/amber | always | Saved, failed validation, Review run confirmation, preview text, private outline result |
| Routine session/authority | hidden unless it changes current action | shown | session/phase, Editing enabled, full hold detail, clock, operator/authorship fact |
| Internal/provenance/receipt | hidden | shown | saved revision, assignment pin, event recipient receipt, arrival attempt history, raw actual block IDs/scan diagnostics, tool revision |
| Diagnostics/history | hidden except “Recent activity available” | shown in Activity | campaign audit parameters, loot journal wording, low-level references, raw registered IDs |

Clean header shows `Editing on` only while on; otherwise no permanent yellow warning. If a page cannot be edited during ACTIVE, show the contextual blocked reason beside its disabled action. A hold remains visible whenever it blocks current action. Debug adds full session/phase, authority, and clock line. `Refresh` exists in both modes.

## Session actionbar presentation

World HUD follows transitions rather than a twenty-tick ACTIVE repeat:

| Event | Player sees |
| --- | --- |
| ACTIVE transition or join during ACTIVE | one green actionbar `Chronicle — play is active` for about 3 seconds, then clear |
| unchanged ACTIVE | no Chronicle actionbar; vanilla/item messages use the space |
| PAUSED, INACTIVE, ENDED, unavailable, or operation hold | persistent amber/red frozen reminder; include hold reason |
| frozen/held → ACTIVE | replace reminder with brief green confirmation, then clear |
| reconnect while frozen | immediate persistent reminder |

`SessionPresentation` owns these transitions; the client never infers phase from a stale screen. State/hold changes send immediately. Periodic refresh is only for persistent frozen states or recovering from another actionbar; it must never recreate ACTIVE banner. Status is not a new gameplay boundary.

## Minimal implementation plan

1. In `CoreMenuServer`, add Core `Block limits` under `SETUP`; move selection/detail/action guard there; remove LOCATIONS/BLOCK controls, special `limit:` detail, and return route. Preserve `ManagedBlocks` persistence, revision checking, enforcement, and Heart fixed rule.
2. Refactor `CoreMenuScreen` into shared popup frame and existing left/list/detail states. Preserve `CoreMenuPayloads`, `CoreMenuModel`, bounds, page IDs, picker kinds, confirmation flow, and `sameControls`. Implement compact Back-to-list and capacity statuses.
3. Add local Debug preference and render-only fact classifier. Do not add debug data to server protocol. Keep errors, reasons, confirmations, and selected purpose in Clean view.
4. Update `SessionPresentation` transition/join bookkeeping: ACTIVE brief then clear; frozen persistent; holds supersede phase.
5. Update model/menu tests and `CoreMenuSmoke`: SETUP-only limits, fixed Heart read-only, Locations absence, compact/wide navigation, clean/debug visibility, and actionbar transition/join behavior. Retain existing authority/revision/native request tests.

No update moves a placed block, changes its capacity, reinterprets a manual anchor, auto-enables edit mode, rewrites a loot table, or makes Debug a server privilege.

## Acceptance checks for review

1. At480 and640 logical widths at GUI scale2 and wide layout, all eight tabs are visible/reachable; detail/back behavior is clear; no overlap, clipped action, custom inventory grid, or horizontal overflow.
2. Locations > Block sites has no Block limits control/detail/link. Supplied rows report capacity; Heart is informational; manual actions only affect manual reference.
3. Game setup > Block limits lists every registered type, changes editable Altar/Forge caps only with server-authorized editing, exposes no Heart value editor, and distinguishes available/reached/over-limit without deletion.
4. Regions guide Continue in world → `pos1`/`pos2` → return → save, preserving an unchanged corner. The private outline begins from Continue, not selection alone.
5. Events distinguish cue preview/check/review-run from game-action preview/check/review-run. Preview has no completion/world effect.
6. Debug off retains purpose, primary action, blocked reason, confirmation, and result while hiding revisions/receipts/raw IDs/authority. Debug on changes no enabled action, selection, draft, token, or server response.
7. ACTIVE shows once briefly on transition/join then clears. Frozen/held remains through normal ticks/reconnect; a hold overrides ACTIVE immediately.
8. Existing native checks still prove non-operators receive only player/card data, stale edits fail safely, closed screens do not reopen, and vanilla texture-pack asset replacement retains legibility/input.

## Risks and lead decision

`Detail.facts()` currently combines ordinary facts and diagnostics. Classification must occur client-side; server omission could change `sameControls` and encourage stale authority guesses. The Altar popup is 224 pixels wide while this editor needs rail/list/detail: “same framework” means native texture/widgets/typography and centered popup composition, not forcing the exact Altar dimensions. Render at both required sizes before implementation.

The current `REFRESH` route doubles as the former return-to-block-sites control. Remove that UI behavior only after routing Block limits to SETUP; retain generic Refresh and protocol enum for compatibility.

This design received lead approval before implementation. Debug defaults off and ACTIVE feedback is intentionally brief rather than a new gameplay setting.

## Lead review decisions

Approved the native popup/widgets, stable navigation and default-clean structure. Logical480/640 are widths in existing native fixtures, not a required portrait height. Retain actual physical Missing/Changed/Unloaded states in Clean view because they explain what can be done. Fact classification must be an explicit conservative allow/hide table: unknown facts remain visible, actionable blocked/errors/results always remain visible, and toggling Debug changes no token/control/authority. Never hide solely by matching a vague substring of a value. Raw IDs may be hidden as diagnostics while user-named region/site identifiers and selectable labels remain available.

For Block limits, one Game setup entry opens a normal registered-type selector and selected-type policy detail; there are no Locations links to it. Keep generic Refresh and current finite protocol; the old BLOCK_SITES policy-return behavior is removed. Native tests must reject forged limit actions originating from Locations as well as prove the visible control is absent.

The existing initial native menu fixture is320x240 logical (640x480 window atGUI2); preserve this compact minimum as well as480x270 and640x360 captures. Keep the eight tabs reachable at all three widths. Put the Debug toggle in the footer rather than crowding the header beside Editing and Refresh. ACTIVE actionbar should fade through vanilla's normal lifetime after one send, with no delayed empty packet that might erase a later item/mod message.

## Implementation progress

The approved presentation is implemented. `CoreMenuScreen` uses the vanilla popup background and standard widgets, preserves the left rail/list/detail structure, and places Debug in the footer. Clean view excludes explicitly classified facts from layout; unknown facts, actual status, capacity, manual references, errors and action results remain. An explicit Debug toggle may reflow widgets while preserving draft values, selection and server authority; passive updates retain the existing stable-control path. `EditorMenuPreferences` stores the optional local toggle with a fail-soft Clean default. No server schema or permission is changed.

Game setup owns the sole Block limits selector and value editor. Old Locations policy requests are rejected server-side. Events distinguish private cue/action previews from audience/readiness checks and reviewed execution. Regions and Activity use plainer descriptions without renaming persisted IDs.

Native menu run `../reports/core-menu-smoke-20261024T142345143334Z` passed119 operator and8 ordinary-player checks, plus both layout clients. Lead inspected320x240,480x270 and640x360 captures. Assertions include unsaved-draft conservation across Debug toggles, unchanged authority, SETUP-only limit writes, fixed Heart policy and rejection of forged old routes. The host now requires named terminal assertions, rather than accepting an early PASS marker.

`SessionPresentation` sends `Chronicle | Play is active` once on join/activation and lets vanilla fade it; it never schedules an empty packet which could clear another message. Frozen states still repeat. Native pause run `../reports/pause-gameplay-20261024T142902974956Z` confirms exactly one ACTIVE overlay after resume across multiple former refresh intervals, alongside the gameplay boundary. Pure tests cover identity, repeated refresh, hold and phase changes. Texture-pack replacement, active reconnect and a multi-player production soak remain human/future checks, not native claims.

## Addendum: global Building Mode

**Status: lead-approved before implementation after the operator selected a global build exception.** This supersedes CF15's temporary per-author build assumption. Building Mode is a server-global, ephemeral permission for map work; it is not an editor/authoring permission, game-mode change, campaign setting, or new menu tab.

### Session workflow

`Session > Play controls` gains one small, clearly separate group after the ordinary Start/Pause/Finish controls:

```text
Building Mode: Off — map interactions are frozen for everyone.
[ Enable Building Mode ]
```

When enabled it becomes:

```text
Building Mode: On — anyone may work on the map; survival remains frozen.
[ Disable Building Mode ]
```

Only an operator can see/use the control. Enabling is a confirmed action because it grants all currently connected and later-joining players map interaction until it is turned off or automatically cleared. Disabling is immediate and needs no confirmation. The finite Core menu actions should be `SESSION_BUILD_ON` and `SESSION_BUILD_OFF`, with no free-form command value or new page.

The exact unavailable explanations are:

* `Building Mode requires a non-active Chronicle play state.`
* `Resolve the active Chronicle operation hold first.`
* `Chronicle is unavailable; Building Mode cannot be changed.`

The operator command is the matching explicit fallback: `/chronicles session building on|off`. It is operator-only, accepts no additional arguments, and reports the same current state/reason as the menu. A forged menu request, command while ACTIVE, request under a hold, or unavailable runtime is denied by the server.

### Behavior contract

Building Mode defaults to off. It may be enabled only while the Chronicle phase is non-ACTIVE and no runtime hold exists. It is held only in live server memory and is reset to off on every phase edge, including Start and Resume, and on server restart. It is never restored from player state, the authoring store, or campaign configuration.

While it is on, **everyone** may perform normal map interactions: placing/breaking/using blocks, container interaction, item pickup, and item drop. This applies equally to normal players, operators, creative players, and people without the editor flag. Movement and character cards stay available as before.

Survival progression remains frozen for everyone, including map builders: health damage/healing, hunger/exhaustion, status-effect time, food consumption, XP pickup, and survival timers do not advance. Building Mode does not make eating, fighting, healing, or XP farming available.

Authoring/edit mode remains solely the permission for Chronicle setup metadata and UI mutations. It must not be consulted as a map-interaction bypass once this mode exists; a live operator with authoring enabled has no private building privilege while Building Mode is off.

### Clean UI and frozen HUD

In Clean editor view, the Session page always shows the Building Mode fact because it changes what ordinary players can do. When on, the header uses the persistent amber warning:

`Building Mode is on — anyone can change the map.`

This warning replaces the normal clean `Editing is enabled.` header when both conditions occur. Debug may additionally show the phase and authoring state, but never changes the warning or controls.

The existing persistent frozen actionbar includes the same global fact while Building Mode is on, for example:

`Chronicle | Session 4: Paused | World frozen | Building Mode on`

It remains persistent while paused/inactive/ended, consistent with the frozen HUD contract. The active one-shot notice is unchanged because Building Mode is cleared before ACTIVE play begins.

### Minimal implementation and proof

1. Add the two finite menu actions and server-side Session detail controls. Keep the currently accepted left navigation and Play controls entry; no new tab or settings page is needed.
2. Add one volatile, server-thread-owned Building Mode state alongside the existing phase/hold policy. Route both the command and menu actions through it; phase transitions, holds, unavailable runtime and restart fail closed.
3. Replace the previous `op + author` interaction exception with this one global state. Keep all existing physiology/consumption/effect/XP freeze guards independent of it.
4. Extend presentation to show the Session fact, Clean warning and frozen-HUD suffix. Do not persist it in the client Debug preference or packet view.
5. Extend native multiplayer coverage: an operator enables the mode in a paused session; a non-operator can place, break, use a container, pick up and drop; both players remain unable to eat or advance health/hunger/status/XP. Disable, phase-start/resume, hold and restart all revoke map interaction. Verify forged non-operator/ACTIVE/held requests leave state unchanged and verify the persistent frozen HUD marker.

Lead decision: ordinary inventory/container work, including crafting building materials, remains available during deliberate Building Mode, as it was for the former map-work exception. Consumables, combat damage, health/hunger/effect timers and XP stay frozen independently. Holds close Building Mode rather than silently restoring it later. No new player gamemode, flight or creative-inventory privilege is granted. A server-owned fact in bounded detail responses supplies the clean header warning, without adding a client-owned mode flag. Native assertions and untested reconnect/restart paths must be reported separately.

Global-mode integration verified: native pause144712 passes ordinary non-author map work with frozen survival, revocation and lifecycle checks; menu145032 passes122 operator/9 ordinary-player checks plus both layouts, including actual Session controls and wrong-page/non-operator denial. Lead inspected the global warning at320px. Container/pickup/drop and fresh-join/restart behavior remain source-only coverage.
