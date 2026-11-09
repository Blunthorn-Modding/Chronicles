# LC04 treaty lectern UI design

**Status: owner approved the workflow and both policies on 9 November 2026; native implementation authorized.** [Interactive prototype](../../design/last-council-treaties.html) is a self-contained sample with in-memory state. Reviewer controls above the game screen choose the person and which representatives and separately assigned Chancellor currently have the same bound treaty lectern open. They are simulation controls, not game UI or an authority selector. Reset discards all sample data. Automatic redraw represents server-pushed updates; no Refresh control is proposed.

## Screen and interactions

A separate bound treaty lectern opens a compact Chronicle screen. The top bar has **Close**. **Create treaty** appears above the left record list; the selected record's read-only title, exact terms, visibility, parties, signature/attendance status, actions and history appear on the right. The list scrolls independently; at narrow widths it stacks above details. Empty list says no agreements are available; absence of the current person shows a lectern attendance message. Public records are visible to any present viewer; private record titles, existence, terms and history are delivered only to its parties and the appointed Chancellor. History belonging to an earlier private revision is restricted to that revision's parties and Chancellor, even when a later revision changes parties or visibility. Native projections must enforce these restrictions, not merely hide controls.

| Control | Action and authority |
|---|---|
| Close | Close the lectern screen. |
| Create treaty | A representative using this lectern opens the draft form when at least two distinct house representatives currently have it open. Chancellor cannot create as a house. |
| Title, exact terms, visibility | Draft fields. Public/private describes who can see the exact record; no terms are redacted in a visible record. |
| Representative checkboxes | Choose from the present set, including the creator. Existing selected but absent representatives appear disabled as absent and cannot be submitted. At least two distinct houses must be selected. |
| Submit treaty | Revalidate actor, selected sessions, title and terms. Create a separate draft revision 1; no record is overwritten. Missing fields or changed attendance show an inline error and preserve the form. |
| Select record | Show its details and history, subject to visibility. |
| Edit draft | Any named party currently using the lectern may open the draft form. Saving a changed title, terms, visibility or parties creates a new revision, retains prior history under its original audience, and invalidates every prior signature. Saving unchanged content creates no revision. Revalidate revision and sessions. Editing active treaties/amendments is a future decision. |
| Read & sign | A present named representative confirms their own house's exact displayed revision and terms. Revalidate actor, presence, draft status and revision on confirmation; reject a stale request. Signing never activates the record. Already signed is disabled. |
| Sanctify agreement | Only the explicitly assigned Chancellor using this lectern can confirm. All named parties must have signed the exact revision and have the same lectern open. Revalidate role, status, revision, signatures and all attendance at commitment. Missing parties and signatures are named; failure leaves the draft intact. |
| Break agreement | A present named party can confirm a minimal breach of an active agreement. Append a broken event; preserve prior record and signatures. Later ceremony/authority details need review. No automatic law or war effect is implied. |
| Cancel | Close a form or confirmation without committing. |

Records are independent. Draft revision changes and signatures, activation and breach remain in immutable history; newly authorized audiences must not inherit earlier private revisions automatically. A production implementation must revalidate server-side and reject duplicate or stale requests. Disabled controls and messages are guidance, not authorization. There is no force-sign or house participation by the Chancellor. No remote dashboard is introduced. The prototype's Close button reports the simulated close so the review surface remains accessible.

## Confirmed policies - 9 November

1. Exact signatures survive departure and reconnect, with all parties and the assigned Chancellor using the same lectern for sanctification.
2. Private treaty existence, terms, breach and history remain private to parties and Chancellor; no public breach notice.


Active amendments, realm ratification, breach ceremony and other end conditions remain future decisions. The prototype does not settle these or claim that an active treaty changes laws, war or other game systems. Acceptance requires human UI review; native persistence, networking, privacy and concurrent authority remain unverified by this sample.
