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
- **Follow-up decision:** remove the Refresh button. Ballot screens must update
  automatically without manual refreshing. Current `ProceedingsRuntime.tick`
  already checks subscribed ballots every ten server ticks and pushes changed
  authorised projections/eligibility (about half a second at normal tick rate).
  Retain this behaviour and normal reopen/reconnect access; verify public head
  outlines, the voter's own confirmation, closing/void outcomes and eligibility
  changes update without a click. Do not expose secret turnout, steal focus or
  reintroduce button flicker. Refresh previously fetched the same ballot; it did
  not reset votes, choices or the electorate.

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

### Follow-up: one presentation system, relevant sections only

Use shared ballot/motion presentation components with the same labels, spacing,
text hierarchy and formatting everywhere. Reuse the components selectively rather
than putting every section on every screen or rebuilding each view separately.
The screen and authoritative ballot state determine which sections are relevant.

| Context | Show | Omit |
| --- | --- | --- |
| Motion not yet voted on, viewed at the lectern | Title/question, proposal, status, vote type and decision rule | Empty For/Against lists, result/history panels for a ballot that never existed |
| Live voting screen | Readable question and rule, centred head roster, applicable choice controls and own final choice | Duplicate For/Against text groups; heads already carry public choice presentation |
| Recorded public motion at the lectern | Consistent motion fields, outcome and its actual attempts, with named For/Against/Abstain history | Irrelevant live voting controls |
| Recorded secret motion | The same applicable fields and closed aggregate results | Named secret choices or individual turnout |
| Chancellor's proceedings during an open ballot | Active motion and permitted vote-completion information, without opening the standalone ballot | A second inconsistent formatting system or a requirement to inspect the voter screen |

Do not show placeholder result sections simply to keep every screen identical.
A proposed/deferred motion with no prior ballot has no vote result; a motion with
an earlier voided attempt still retains that real history. Shared formatting does
not mean hiding relevant prior attempts or presenting an open attempt as closed.

### Chancellor completion visibility

The owner could not tell from proceedings whether everyone had voted. Show
permitted submission progress directly on the active-ballot workspace and update
it automatically. For public ballots this can show submitted/required-electorate
counts, an all-submitted state and the captured representatives still outstanding.
Use the frozen opening electorate, not whichever players are currently connected
or standing in the Region. Abstention is a submitted choice; disconnecting does
not remove an outstanding voter. Progress does not automatically close a ballot
or change its fixed threshold; closing remains the Chancellor's decision.

**Confirmed follow-up:** the Chancellor may see an anonymous submitted count
for secret ballots, for example **3 of 5 votes submitted**, including an
all-submitted state. This narrowly supersedes the earlier rule hiding aggregate
turnout from the Chancellor. Do not reveal who submitted, who remains outstanding,
submission order/timestamps or individual choices. Secret heads stay neutral;
ordinary players receive no additional turnout information. Send the count only
in the authorised Chancellor projection, never raw votes or per-person flags.
Validate authority changes/reconnects and that no hidden information leaks through
ordinary views, packet fields or reusable exports. Closed results remain aggregate.

## 4. Persistent, readable vote history

From the motion lectern, players should be able to see what happened to a motion
and inspect its recorded ballot history. For a **public** ballot show who voted
For and Against, with Abstain and not-cast distinguished where applicable. Use
the captured character names/identities, not current House names that might later
change. Keep separate attempts and public void reasons; never overwrite an old
attempt with its replacement.

For **secret** history, no named choices or individual turnout may be disclosed,
including to the Chancellor. Closed results show aggregates only. The confirmed
Chancellor-only anonymous submitted count above is the sole new turnout exception;
it does not authorise per-person history or disclosure to ordinary players.

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
candidate handling without silent elimination, shared formatting with irrelevant
sections omitted, automatic Chancellor progress against the frozen electorate
(including Chancellor-only anonymous secret counts and no per-person leakage), and typing I/O/rebound letters in
Creative search without opening or closing another screen.
