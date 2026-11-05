# Last Council UI design pass

Status: **first interactive proposal, awaiting owner review**. Dated 5 November 2026. No Last Council gameplay, native screens, protocol, scenario defaults or commands have been implemented.

Open [the local prototype](../../design/last-council.html), or serve `docs/design` on loopback and visit `http://127.0.0.1:8925/last-council.html`. HTML, CSS and JavaScript are local files with no external dependencies. All sample changes are in memory. Refresh/Reset discards them. This prototype follows the [framework decisions](FRAMEWORK_INTEGRATION.md), [complication rules](RANDOM_COMPLICATIONS.md) and package contracts; it does not replace those rules with an implemented simulator.

## Review order

1. **Player / Discussion:** Council overview, a motion, proposal sponsorship, character card and issued handouts. Open Treaties, propose one, add a signatory through the shared picker, review and send it. Sign only for your own house. Inspect public versus private records.
2. **Player / Public vote**, then **Secret regency vote:** cast/change your choice and inspect confirmation. Secret votes show only your choice while open. Switch to Chancellor: there is no reveal-all control or inherited player vote.
3. **Chancellor / New gathering:** call the Council through its Event, open the meeting, open/close individual ordinary ballots, request regency, close the meeting and open another. Discussion is a separate example that starts with a meeting already open. The call gathers players; it does not automatically open a ballot.
4. **Late complication / recess:** request regency. A sample due Event is revealed and proceedings enter recess. Resume, then separately request regency again. Closing a meeting without regency uses the same due-Event check. The world remains active in an ordinary Council recess.
5. **Rebel player**, **Interrupted operation** and **Incomplete setup:** check unavailable actions and explanations. Player access never becomes GM/editor access because setup is incomplete. Technical recovery retains the interrupted operation; it does not pick another complication.
6. **Editor:** Players → randomized handouts; Content pools → templates/Events and draw rules; Game setup → Council settings; existing Events, Regions and Locations with proposed extensions. Review both desktop and narrow-window navigation.

The toolbar above the game frame switches sample viewpoints and conditions. It is **not** an in-game permission switch. The illustrative-result selector in the close-ballot dialog is also a prototype control: other houses are not connected clients. A real ballot will compute its result and will never let the GM choose it.

## Navigation and entry points

There are three tasks, sharing the established Chronicle visual components:

| Context | Left navigation | Purpose |
|---|---|---|
| Player | Council, Character, Treaties, Conflicts, Chronicle | Political participation and permitted records |
| Chancellor | Council, Houses, Treaties, Conflicts, Chronicle | Run proceedings; inspect the political roster and permitted records |
| Existing authoring editor | Retain all current tabs; add optional **Content pools** under Advanced | Prepare reusable content and map setup |

**Character** opens the existing character card and existing issued-book presentation, with public house affiliation. It is not a new house dossier. **Houses** is the Chancellor's political roster; assignment editing has one canonical home in the existing Players editor. Council operations do not become Game setup options. Realm rules and complication policy do belong in Game setup. Palace bounds stay in Regions; the safe outside Point stays in Locations. No Areas, Departure or separate voice-room catalogue is added.

Proposed entry points are the existing character/menu access plus a discoverable Council entry and a Chancellor management entry for operators. If command shortcuts are needed, they remain under `/chronicle council` and a permission-checked management route. Exact registration/shortcut wording is part of the next design pass, not a second command root or a new authority role. Chancellor means GM. A GM representing a house uses the participant interaction to cast that house's one vote; GM authority adds none.

On wide screens: sections left, selected entries middle, description and relevant actions right. Create buttons belong **above** existing entries. On small logical screens: section → list → detail, with explicit Back navigation. Do not shrink text or cram all three panes together. Native implementation must use the actual scaled Minecraft viewport, with a review at GUI scale 2; browser width is only a layout approximation.

## Screen and action contracts

| Flow | Controls and result | Constraints and states |
|---|---|---|
| Council overview | Current gathering, floor motion/ballot, agenda/proposals; Chancellor call/open/recess/resume/close | No fixed auto-agenda, no session reset, no automatic vote on resume |
| Player motion | Title, proposal, typed decision, relevant target, public/secret choice; submit → sponsor → GM agenda | Rebel cannot submit/sponsor. Emergency bypass is explicit. Withdrawal only before opening |
| Ballot | Options → explicit confirmation → own recorded choice; repeat to replace while open | Electorate and options freeze at opening. Secret has no partial counts or other choices, including GM/debug. Public has named votes |
| Ballot close | GM confirmation → computed result and record | Only one open ballot. No electorate editing, force vote or manual tally replacement. Exceptional void/reopen policy unresolved |
| Regency | Candidate slate, required support, aggregate result, elimination/crown/vacancy history | Secret, at most once per meeting. Eliminated candidate keeps seat. One final candidate uses a later confirmation; zero candidates/seats means vacant. Early end can remain unresolved |
| Complication arrival | Revealed Event and narrative → Council recess → GM resume → separately requested vote | Hidden future identity, target, seed and countdown. Current meeting's due work gates regency or meeting close; later meetings' obligations do not |
| Treaty | Create draft → pick named parties → exact terms review → offer → individual signatures → active/history | Public/private projection, no signing for others, realm ratification when applicable. Breaking remains possible. Amend/disclose/expiry detail unresolved |
| Conflict | Declare house war → target/reason → confirmation; read sides, join voluntarily, withdraw, offer ceasefire/peace | Private pact terms never leak through a declaration. Loyal enemies keep votes/access. Foreign affairs use typed Council motions. No automatic military result |
| Rebellion | Own-house standing → declare/join → public grievance → complete consequence confirmation | Lose vote/seat/candidacy/proposal/sponsorship/palace access; remain active RP. Block during ballot. Safe outside Point required. Reinstatement is a settlement, not a leave button |
| Ending | Chronicle → aggregate decisions, crown/vacancy/unresolved state → GM conclusion review | Preserve actual history and outstanding obligations; no forced winner or fabricated Event delivery |
| Character assignments | Existing Players editor links cards to houses/candidacy, optionally existing role for gear/Points | No compulsory role per house; card text does not grant political authority |
| Randomized handouts | Choose reusable pools → private proposed character draw → read generated books → reroll before commit → confirm commit → delivery/retry view | Stable committed cast/grants. Facts before linked knowledge; zero/multiple observers allowed. No owner exposure marker; no future complication in preview |
| Content pools | New pool → Book templates or Events; entries with shared Add picker, weight, remove, draw rules | Definitions only. Scenario's evening count has **one** home in Game setup. Event recurrence limits remain intact |
| Event authoring | Existing Basics / Message & sound / Trigger / Changes | Call Council uses existing media. Political sources observe committed results; cannot force consent or overwrite results. A pool is a selector, not another Event executor |
| Palace authoring | Region guarded-access subtype → LC policy → existing safe outside Point picker | Reuse shovel workflow and bounding box. Bounds/exit diagnostics are actionable. Voice is independent |
| Banquet | Normal in-world drink/dose/antidote interactions; optional feature configuration | No player poisoning dashboard, no visible contaminated tooltip, no required starter gear. Detailed interaction and effect tuning unresolved |
| Voice | Read-only pending-policy note | Await acoustic test before adding a voice subtype or implying private rooms work |

This first HTML pass makes the primary Council/vote/recess, treaty proposal/signature, war declaration, pool editing and character-commit paths interactive. Several retained screens and detailed secondary flows are deliberately labelled sketches. In particular, this is **not** complete approval of treaty amendment/disclosure, constitutional intervention, multiple late-complication spacing, ending/recovery policy, template-binding/eligibility forms, poisoning interaction or voice controls. Those need a further design iteration before their native implementation.

## Shared components and presentation

- Reuse the library's frame, vanilla-style button/text-field/popup widgets, common picker, selected-row treatment and confirmation pattern. These are scenario views of shared components, not copies of the existing editor. Native surfaces should inherit texture-pack styling where those widgets support it; the browser's CSS is not a custom font/texture dependency.
- Keep normal descriptions short. Always show information needed for a decision: voting threshold, own confirmed choice, exact treaty terms, consequences, unavailable-action reason and actionable setup/error messages. Technical IDs, revisions, receipts and routine authority/status facts belong under **Debug**, top right next to Editing where applicable. Debug never relaxes privacy.
- Preserve page, selected entry and scroll position on reopening. Popups return to their caller with unfinished form data retained; adding a house/item/book/Event must use the same picker behavior. Unsaved changes need Save/Discard/Cancel; destructive actions need clear confirmation. Pending requests should not disable unrelated buttons or flicker the entire view.
- Distinguish empty lists, search with no matches, missing required content, full inventory/pending grant, offline representative, stale draft, technical pause, Council recess and interrupted operation. Show the reason beside the affected action, not only in a fleeting chat message.
- The mockup contains no copied third-party assets, no external fonts/audio, telemetry, persistence, API requests or runtime integration.

## Ownership and implementation gate

Core owns reusable pool authoring, generic ballots/presentation helpers and optional guarded Region integration. Last Council owns political rules/projections, Council proceedings, house links, treaty/conflict/rebellion/banquet views and complication timing. Retained Core tabs must remain usable without Last Council. Default definitions/configuration versus world map bindings/live progress keep their existing split.

**Review navigation and primary flows first.** Then resolve the marked secondary forms and policy questions in the mockup. Native code follows approved slices, with real server authority, privacy-filtered packets, stale-request handling and restart proofs; HTML state does not demonstrate those guarantees. LC-01 domain work is not a reason to bypass the UI-first instruction or silently settle those decisions.

Verification completed on 5 November:

- `node --check docs/design/last-council.js`: PASS.
- Local Edge browser interaction smoke: **66 checks PASS**, no JavaScript exceptions in the final run. Covers own secret vote confirmation, GM projections, successive meetings, regency and meeting-close recess gates, treaty draft/party retention, selected conflict target, rebel restrictions/candidate removal, pool add/edit, character commitment, all editor panels and compact Back navigation. Browser state is sample data; these checks do not prove server privacy, real voting or durable storage.
- Desktop screenshots at 1440×1000 and compact navigation at 640×800 inspected. Small PNGs and the local smoke script remain under ignored `output/playwright/`, without a Minecraft world. This does not replace a future native GUI scale 2 check.
- Independent read-only UI review caught actor-permission, duplicated-setting, selected-record and rebel-candidate inconsistencies; corrections were inspected and exercised in the final smoke. An initial browser run also caught a button passing its click event as a treaty draft; the callback is corrected. A later test-script Unicode-label error was corrected before the final passing run.
- Package-relative Markdown links PASS; supplied source brief SHA256 remains `df20a7c1a8ff73f81b9f7dc1265666b4131ce62b260b79904180ea32fca5a44a`. Loopback serves byte-identical current HTML/CSS/JS. `git diff --check`: PASS.

No Gradle/native Minecraft test is needed for these docs-only files. No Minecraft review world/client/server is created. The loopback HTTP server serves static design files only.
