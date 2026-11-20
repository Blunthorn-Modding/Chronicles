# Election management: approved workflow

Status: **owner approved the workflow on 19 November 2026; native implementation and automated verification are complete in dev.20**. See [delivery evidence](ELECTION_RESULTS.md); combined human review remains pending. The [interactive mockup](../../design/last-council-election-management.html) uses illustrative state and does not establish authorization or persistence. Reviewer controls are outside the game frame.

## Scenes and transitions

- **My house → Election:** a current candidate House sees the remaining slate and public history, with a withdrawal action between ballots. Its confirmation names the House, explains permanent removal from this election's slate and retained Council participation and vote, and captures a reason for the public record. Other Houses cannot withdraw someone else's candidacy. An open ballot and the same-meeting replacement of a voided ballot block withdrawal. A one-House slate remains contested and needs its existing confirmation vote; an empty slate becomes vacant according to `SuccessionState`.
- **Chancellor proceedings → Election:** only the assigned Chancellor can explicitly end a contested, unfinished election with no open ballot, including after a same-meeting void. The confirmation requires a reason and explains that history and ordinary Council business remain. This does not replace or reset the election or depose a crowned ruler. The saved public audit includes the operation identity, actor, meeting and reason.
- **Planning warning:** remaining declared Regency opportunities include the current discussion or recess meeting only if a Regency has not completed there, plus each future declared meeting once. For a contested slate of N candidates, the worst case is `max(1, N - 1)` further completed ballots, assuming one elimination per completed meeting followed by a final two-candidate or single-candidate ballot. When opportunities are fewer, show an informative warning that does not block play. No warning appears once crowned, vacant or ended unresolved. This warning is advisory and visible only to the assigned Chancellor.

Existing constraints still apply: initial slate locks on opening, later rounds require all remaining candidates present, and Regency requires an absolute majority of the captured electorate. Houses and the assigned Chancellor have separate role paths. Due complications and normal recess gates remain in ordinary proceedings. Political Event source selection uses the existing Core picker; this proposal introduces no new source-selection UI.

Native verification and release evidence are recorded separately; approval of this mockup does not establish multiplayer acceptance.

Lead layout review: mutation buttons stay in the fixed bottom action row, outside scrolling history. Meeting-capacity warnings appear only in Chancellor proceedings. Existing My house sections, including Other houses' secrets and Rebellions, remain in the navigation. Twelve browser interaction checks and normal/compact captures passed; the owner subsequently approved the workflow. Native controls reuse the existing screen components.

Native implementation must reuse existing public character-card candidate labels (character name with House), not replace them with House-only voting labels. The House names in the illustrative reviewer state are placeholders.
