# LC01 source content editor: current workflow

Owner approved the variable model on 11 November. Dev.11 replaces the old fixed-value authoring and removes sample previews. The existing compact Chronicle frame, list/detail catalogue, focused forms and server-side source-editing lock remain.

| Control | Behaviour |
|---|---|
| Houses | Create/edit public House information; the server supplies a stable internal House ID. |
| Secret name and weight | Name the secret and set its relative draw weight. A source secret can be assigned once per experience. |
| Secret text | Write ordinary text; Insert variable offers Own House, Other House and this secret's named number ranges. |
| Applicable Houses | Choose Houses eligible to receive the secret. One is chosen during allocation; this does not duplicate the secret for every eligible House. |
| Number variables | Optional ranges attached to this secret. Add asks Name, Minimum and Maximum, using inclusive signed whole numbers. Existing variable names stay stable; editing changes the range. |
| Own House | `${house.own}` means the House originally receiving the secret, including when other Houses learn it. |
| Other House | `${house.other}` chooses one other assigned House once for this secret. Repeated references and knowledge copies retain that House. At least two assigned Houses are needed. |
| Number insertion | `${number.capacity}` uses the secret-local capacity range. It is drawn once, uniformly, then reused throughout the secret and every copy. |
| House goals | Name/weight, text with Own House / Other House insertion, and applicable Houses. Secret-local numbers are not a separate goal-variable catalogue. |
| Knowledge rules | Counts of secrets/goals per House and independent knowledge chance. Zero, one or several observers remain possible, with no exposure display to the owner. |
| Catalogue actions | Create, Edit and Delete. Source sample Preview and raw JSON sample bindings are removed. Event and Region previews are unchanged. |
| Scenario export | Existing global export/import carries source definitions and ranges; committed draws, used-secret IDs, resolved private papers and seeds stay world-private. |

A new draw respects House scopes and configured counts while assigning each secret ID at most once. Insufficient unique eligible secrets produce an actionable setup error. Late enrollment excludes previously used IDs and does not alter existing papers. Every number and Other House resolves before publication; learning or physically copying a secret never rerolls it.

Only operators inspect the catalogue. Changes require non-active Editing, no conflicting operation, current connection and unchanged authoritative source. Issued or pending House papers lock editing. Multipart transfers, stale-write refusals and retained uncertain/refused drafts remain in place.

Old source schema 1 and fixed facts remain readable and preserved, with old house.title/other.title aliases supported. There is no fixed-string-variable authoring UI. Existing committed papers remain authoritative and are never regenerated on upgrade. See DEV11_VARIABLES_PLAN.md and DEV11_VARIABLES_RESULTS.md for state compatibility, legacy dependency bounds and verification.
