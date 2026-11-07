# Council group-review feedback - 7 November 2026

Status: **recorded for the next development session; no implementation today**.
The owner requested that these changes be recorded for development tomorrow.
This review follows the dev.4 e4mc group test. It supersedes earlier UI/candidate
assumptions where stated below; it does not mark LC-01/02/03 complete.
The original brief and existing review worlds remain unchanged.

## 1. House assignment after papers have been issued

The owner cannot assign MrWouter12 because setup says that the cast is committed
and character-to-house assignments are fixed. Remove this dead end for adding a
new player to a House after play has begun. Design a deliberate late-assignment
flow rather than requiring a reset of the entire experience.

Preserve existing private papers, connected facts/knowledge, player identity and
recorded ballots. Do not reroll everyone, silently change a captured electorate,
or rewrite historical membership. Distinguish adding an unassigned player from
reassigning an existing character or taking an occupied House. The exact safe
draw/publication approach still needs design; permission to add a player is not
permission to discard the committed cast.

Ownership: LC-01C House/cast rules in Last Council, with reusable durable draw
extensions in LC-01B/Core where appropriate. Existing read-only catalogue work
below must not be mistaken for a completed late-assignment implementation.

## 2. Ballot heads, names and Refresh

- Centre the voter-head roster as a group, including partially filled rows.
- Make full public character names readable. Current fixed-width tiles cut names
  off; a hover-only workaround is insufficient as the normal presentation.
- Keep public choice outlines and secret-ballot privacy from the prior review.
- The owner asked what Refresh does. Current `BallotScreen` sends `Action.BALLOT`
  for the same ballot to retrieve its latest projection; it does not reset a vote,
  open a new ballot, alter a choice or change the electorate. Review its visibility
  and wording alongside automatic updates. Removal was not explicitly decided.

Validate short and long names, one/few/many voters, normal/compact layouts,
wrapping/paging and a reopened or archived ballot.

## 3. Readable motion and voting content

The actual question/description must be prominent and readable on a live ballot.
Avoid a flattened string of title, generic instructions and unexplained metadata.
Reuse the existing separate read-only sections/cards; do not return to large
input-like black text boxes or an additional selection popup.

Motion lectern/proceedings details should have individually labelled fields:

- **Proposed by:** the Chancellor or proposing character, clearly identified.
- **Status:** plain terms such as Proposed, Recorded or Deferred, with meaningful
  wording for a currently open vote rather than raw internal state names.
- **Vote type:** Public or Secret.
- **Decision rule:** an understandable majority rule/numerical requirement.
- **Result:** show the recorded outcome when there is one, not merely Recorded.

Do not concatenate `Recorded | Public | Majority | Sponsors` as if it were prose.
Any sponsorship information that remains must be clearly labelled and relevant.
Use the same separation on the live voting screen for the question, proposal and
vote rule; post-vote For/Against history does not belong among live voting choices.

## 4. Persistent, readable vote history

From the motion lectern, players should be able to see what happened to a motion
and inspect its recorded ballot history. For a **public** ballot show who voted
For and Against, with Abstain and not-cast distinguished where applicable. Use
the captured character names/identities, not current House names that might later
change. Keep separate attempts and public void reasons; never overwrite an old
attempt with its replacement.

The previously confirmed **secret** rule remains in force: no named choices or
turnout disclosure, including for the Chancellor. Closed secret results show
aggregates only. This request for public history does not revoke that rule.

Ownership: use Core's existing immutable ballot ledger/projections; Council owns
motion navigation and its presentation. Extend generic projections only if a
specific missing history field requires it; do not duplicate a ballot ledger.

## 5. Regency choices represent eligible people

The group test allowed selecting Houses whose representatives were absent. The
owner wants choices to be eligible **players/characters who are present**, with
the underlying House association retained. Present candidates using public
character names rather than only House names. Offline placeholder candidates
must not appear as selectable people simply because they were seeded in a review
world. Keep voters and candidates conceptually distinct.

This changes the earlier all-configured-Houses candidate assumption in LC-03.
Before implementing, resolve how present-only choices interact with the durable
remaining candidate slate between meetings: being absent must not silently be
treated as elimination, withdrawal or vacancy. Preserve existing ballots and the
confirmed immutable opening electorate; do not let late arrival change an open
ballot's choices.

**Clarification for the next design session:** the dictation included the fragment
“Two-thirds majority” without a clear instruction to replace the current Regency
absolute-majority rule. Confirm whether this is a rule change or a complaint about
wording before changing tally, thresholds, elimination or vacancy behavior.

## 6. Advanced content catalogue

Expose the catalogue/pools from which House information, secrets, House goals and
known-other-House information are drawn in Advanced Game setup. The owner wants
to inspect the source entries, not only the final assigned papers.

This belongs with the already planned LC-01B reusable pool/editor/export work,
with Council-specific House/fact/goal relationships in LC-01C. Keep reusable pool
definitions distinct from live draws, seeds, assigned papers and knowledge links.
An administrative catalogue must not expose a character's private assignment to
ordinary players or reveal which other Houses know their secrets. Editing/export
scope should be reviewed with the existing scenario-content workflow.

## 7. Typing in Creative search must not open menus

The owner reports that typing I or O in the Creative inventory item search opens
Council proceedings or the Chronicle editor. Treat this as a text-focus regression.
Navigation hotkeys must not consume letters while a text input is focused, must
not close the inventory, and must leave the search query intact.

Cover the Core O binding and Council I binding with the same reliable text-focus
guard where possible. Check rebound keys, Creative search, other editable fields,
and existing R ballot/editor/confirmation safeguards. This does not disable the
hotkeys during ordinary gameplay or deliberately opening them from a non-text
inventory context.

## Related diagnostic from the preceding group test

Creative mode is allowed. The inspected saved review had a House on one player
and participation on another; both saved positions were inside the existing
Council Region. The generic empty-electorate error obscured this distinction.
Track an actionable refusal that explains actual missing eligibility instead of
implying that every empty electorate is a Region failure. Do not auto-enrol or
assign people, widen Regions or weaken eligibility as a workaround.

## Next development order and review

1. Diagnose/fix the Creative-search hotkeys and design safe late House assignment.
2. Have a dedicated UI agent refine the ballot/motion/history layout against this
   feedback, then have the lead review that design before implementation, as the
   owner previously requested. Do not spawn agents for today's note-taking.
3. Resolve Regency presence/slate and threshold clarification before tally work.
4. Implement the agreed UI/history and candidate fixes, then the source catalogue
   within LC-01B/C. Keep the next owner review bundled where feasible.

Acceptance must include actual hosted-LAN multiplayer where relevant, an existing
committed cast receiving a late player without rerolling existing papers, long
names and centred rosters, public history/secret privacy across restart, absent
candidate handling without silent elimination, and typing I/O/rebound letters in
Creative search without opening or closing another screen.
