# The Last Council 0.1.0-beta.1 human review checklist

This is an acceptance checklist for **The Last Council 0.1.0-beta.1** with
**Chronicle Core 0.2.0**. It records observations made by people in a real
Minecraft session; it is not a replacement for the automated evidence in the
[work-package tree](README.md). Leave every box unchecked until it was observed
in this review world. Record surprises, confusing wording, and failed steps with
the date, role, and enough context to reproduce them.

Start with **Quick release smoke**, then cover the focused sections over several
sessions. Mark unavailable checks **Not run**, with the reason. A Chancellor plus
two Houses covers basic ballots/treaties; use three Houses plus the Chancellor
for elimination, outsider privacy, and supporter tests. Crowned, vacant, and
unresolved endings need separate fresh world copies.

## Before inviting reviewers

- [ ] Use a disposable world or a fresh copy of the intended review world. Do
  not edit the original source map, a played world, or a previous release world.
- [ ] Back up the copy while Minecraft is closed. Do not delete or hand-edit
  journals to simulate a reset; begin another scenario in a fresh copy instead.
- [ ] Install the matching **plain JAR** release artifacts for both Chronicle
  Core and Last Council. This review does not assume a new `.mrpack` exists.
- [ ] To start from the existing dev.20 Host/Player review pack, copy it first, close
  the game, replace **both** matching release JARs, then remove old Chronicle
  versions only after confirming Minecraft is closed.
- [ ] Confirm every client uses the matching Minecraft/Fabric/mod versions and
  that the server opens the copied world without an upgrade or missing-mod error.
- [ ] Assign an operator only for setup/editing as needed. An OP is **not** the
  Chancellor merely by being an operator.
- [ ] Assign one separate Chancellor and at least two participating House
  representatives. Preferred coverage is three House representatives plus a GM
  or Chancellor, so secret-ballot privacy and elimination can be observed.
- [ ] Assign each reviewer an unused House in **Players > House & papers**;
  join participation with `/chronicle players join`; then turn Editing off and
  start/resume play. The Chancellor must not represent a House or vote.
- [ ] Confirm the Council voting Region, motion lectern, treaty lectern, and a
  safe outside Point for any guarded palace Region are already present. Do not
  continue a test without its required binding.

## Quick release smoke

- [ ] First active play makes each participating House's papers available in its menu once. Reopen
  **Character** and **My house** and confirm private papers are visible only to
  their intended House.
- [ ] Edit the public character card, including pronouns. Type in Creative
  search and other text fields with the I/O/R keys. Expected: typing does not
  open unrelated Chronicle screens; keybinds remain configurable.
- [ ] The Chancellor opens **Chancellor proceedings** (not an OP-only shortcut),
  calls a meeting, and opens a public vote on a motion proposed at the motion lectern.
- [ ] Representatives present in the voting Region open the current ballot with
  the configured hotkey or `/chronicle council vote`, confirm one choice, and
  see that the confirmed choice is final.
- [ ] Close the ballot and verify its result/history reads sensibly. The
  Chancellor can continue ordinary Council business after the result.
- [ ] Open one secret ballot. Each voter sees only their own acknowledgement;
  no House voter sees another House's choice or live turnout. The Chancellor
  sees at most the anonymous submitted count, never individual choices.
- [ ] Enter and leave a configured Voice room with two people. Expected: room
  membership switches automatically on the Region boundary and returns to
  proximity voice outside it; no manual group picker is required.
- [ ] Stop the copied server cleanly, restart it, and verify the same Houses,
  papers, meeting/history, and enabled Voice-room definition remain available.

## Setup, editing, and House papers

Use a fresh copy before first active play for source-content editing. Issuing
House papers locks that source catalogue for the experience.

- [ ] While play is paused and Editing is enabled, create or edit a House using
  its ordinary name. Expected: the author does not need to invent a namespaced
  internal identifier; its stable identity survives a later name edit.
- [ ] Create/edit a secret with a name, text, eligible Houses, and a weight.
  Add an optional number variable through **Insert variable**. Expected: a
  minimum/maximum range and Own House / Other House references are understandable;
  no internal fact key or namespaced ID needs to be typed.
- [ ] Configure a bounded number variable and use it in a paper/secret. Start a
  fresh draw and verify repeated references in that recipient's assigned text
  resolve consistently, while no private allocation becomes public.
- [ ] Compare a secret with a learned copy held by another House. Expected:
  both name the same subject House and number; a copy does not reroll either.
  The owning House gets no marker revealing who, if anybody, knows the secret.
- [ ] Check the personal menu separates House information, own secrets, House
  goals, and known secrets about other Houses. Player-written personal goals
  remain separate from the scenario's assigned House goals.
- [ ] Request physical House papers through the supplied workflow. Expected:
  the request is attributable to the intended House and does not grant another
  House's private paper or alter an existing committed assignment.
- [ ] After papers are committed, add one previously unassigned online
  character/House. Expected: the late entrant receives its permitted papers
  without rerolling, replacing, or exposing anybody else's papers.
- [ ] Begin Regency, then add a late participant where setup allows it.
  Expected: they may be a voter under the normal ballot opening rule but cannot
  become a newly added crown candidate.
- [ ] Attempt to reassign an existing committed House/paper. Expected: refusal
  is clear; existing assignments remain intact.

## Motions and fixed-electorate ballots

- [ ] Put two House representatives inside the voting Region and keep a third
  outside. Open a public ballot. Expected: only eligible Houses present at
  opening are captured; the threshold and electorate do not change afterward.
- [ ] Have the outside House enter late. Expected: it is not added to that
  ballot. Have a captured House leave/reconnect and return to the Region;
  expected: it may cast if it has not already done so, and its prior final vote
  remains final if it already voted.
- [ ] Verify an empty electorate refuses to open rather than automatically
  passing a motion or deciding succession.
- [ ] Submit a public vote and inspect the in-progress display. Expected:
  public ballots may identify public choices; secret ballots keep every head
  neutral. House players see no live turnout; the Chancellor receives only an
  anonymous submitted count. Individual secret choices stay private.
- [ ] Void an open ballot with a technical mistake using a required public reason.
  Expected: original choices/history remain archived, and the replacement has a
  new ballot identity and its own fixed electorate.
- [ ] Replace a voided Regency ballot in the same meeting. Expected: replacement
  is allowed without changing the locked slate; only a completed Regency consumes
  that meeting's succession opportunity.
- [ ] Create/resolve or defer every ordinary pending motion before requesting
  Regency. Expected: unresolved ordinary motions block Regency until voted or
  explicitly deferred; a deferred motion returns at a later meeting.
- [ ] Inspect a completed ordinary Council decision. Expected: it remains a
  binding recorded decision; the treaty breach action cannot undo it.

## Regency, candidates, and meeting endings

- [ ] Begin a contested Regency with at least two eligible candidates. Confirm
  the slate locks at the first succession ballot and does not gain later entrants.
- [ ] Run a ballot with more than two candidates and no majority. Expected:
  exactly one lowest candidate is eliminated; a tied lowest result uses one
  announced durable draw rather than random crowning.
- [ ] Continue at a later Council meeting. Expected: succession is one ballot
  per meeting; eliminated candidates retain their ordinary Council vote.
- [ ] Keep a remaining candidate outside the Region for a later Regency opening.
  Expected: opening waits until all remaining candidates are present, without
  silently eliminating the absent character or changing the slate.
- [ ] Leave two candidates without a majority. Expected: the throne becomes
  vacant rather than selecting a plurality winner.
- [ ] Leave one candidate and run the later confirmation ballot. Expected: it
  requires an absolute majority; the candidate is not crowned automatically.
- [ ] As a remaining candidate, use **My house > Election** between ballots to
  withdraw with confirmation and a public reason. Expected: their House keeps
  its Council seat/vote; withdrawal is blocked while a ballot or same-meeting
  void replacement is open.
- [ ] As the assigned Chancellor, use **Chancellor proceedings > Election** to
  end a contested election unresolved with confirmation and a public reason.
  Expected: no ballot can be open; public history remains and ordinary motions
  can continue. A non-Chancellor, including an OP, cannot perform this action.
- [ ] Schedule too few remaining meetings for the worst-case Regency path.
  Expected: only the Chancellor receives an advisory warning; it never blocks a
  lawful vote or invents a meeting.

## Random complications, pools, and recess

- [ ] While non-active with Editing enabled, open **Events > Event pools**;
  create a weighted pool from eligible manual once-only Events, save, reopen,
  and confirm the edits persist.
- [ ] Confirm an empty/exhausted pool fails explicitly and an Event cannot be
  silently drawn a second time through another pool.
- [ ] In **Game setup > Council complications**, configure a small disposable
  test: two interruptions in meeting 1, earliest/latest one minute, spacing zero. Do
  not overwrite the intended final pacing merely for this test.
- [ ] Open a meeting and request Regency when a complication is due. Expected:
  one hidden prepared Event arrives, Council enters a recess, and Core play stays
  ACTIVE; the Chancellor must resume and request Regency again.
- [ ] Try to close a meeting with an outstanding current-meeting complication.
  Expected: the same boundary protection/recess applies.
- [ ] Verify normal/debug views reveal possible pool definitions only, not a
  future allocation, selected Event, seed, countdown, or private recipients.
- [ ] Pause/enable Editing and remove a future meeting. Confirm redistribution
  only after the explicit confirmation; removing all future opportunities needs
  a reason and leaves an undelivered record rather than dropping obligations.

## Treaties and conflicts

- [ ] At the treaty lectern, have two House representatives draft exact public
  terms, sign individually, and return with the Chancellor to sanctify. Expected:
  signatures persist through close/reopen and reconnect; editing terms clears
  pending signatures.
- [ ] Draft a private agreement and ask a third House to inspect records.
  Expected: the third House does not see private titles, terms, breaches, or
  history. Do not treat a declared war as permission to reveal private terms.
- [ ] Record a breach of an active agreement. Expected: it is a voluntary treaty
  record, visible according to that agreement's public/private setting.
- [ ] Obtain a Challenge token with `/chronicle council challenge-token`, use it
  on a nearby opposing representative, and confirm the public declaration.
  Expected: the conflict appears with its founding Houses and history; a remote
  menu action cannot replace the in-person challenge.
- [ ] Have a third House support a side, then withdraw/change its support.
  Expected: supporters can change allegiance; founders remain until a signed
  Peace or Ceasefire. All current Houses must sign that agreement before the
  Chancellor sanctifies it. A later physical declaration creates a new conflict.
- [ ] Open a foreign war or peace motion for a named external realm with three
  captured Houses. Expected: passage requires the fixed two-thirds threshold;
  a failed or void vote does not apply the foreign effect.
- [ ] Compare a House conflict, a foreign conflict, and a treaty. Expected:
  they remain distinct records; peace/ceasefire requires the configured current
  House signers and does not erase unrelated treaty privacy.

## Rebellion and palace access

- [ ] Configure Region access through **Regions > Choose subtypes** using a
  loaded, safe, same-dimension outside Point; then disable Editing/Building
  Mode before the scenario. Expected: the guard has a safe exit.
- [ ] A participating House declares or joins a rebellion from **My house >
  Rebellions**. Expected: confirmation explains the loss of Council rights,
  candidacy, sponsorship/proposals, and palace access while papers/participation
  remain. An open ballot blocks membership changes.
- [ ] Try ordinary entry, Creative/Spectator, teleport/camera/vehicle paths.
  Expected: Creative or Spectator alone does not bypass the guarded boundary.
- [ ] The Chancellor grants **Allow visit** to one rebel House. Expected: it has
  one entry, becomes inside on entry, ends on exit, and never restores a vote or
  other Council rights. Reconnect once inside and once outside to check both
  states.
- [ ] Propose **Reinstate Houses**, select named rebels, and pass the captured
  two-thirds ballot. Expected: only those pinned memberships regain palace access
  and rights. Failed/void results do nothing; no free leave/fallback exists.

## Banquet poisoning and antidote

- [ ] Enable Banquet poisoning only in this disposable review world. Put one
  issued poison dose in the main hand and an edible item in the offhand.
  Expected: exactly one serving is contaminated; clean stack remainder and the
  normal food item/nutrition/container behavior remain sensible.
- [ ] Consume the contaminated serving and review the configured progression:
  incubation, Nausea I/Slowness I, Poison I, then critical Poison II plus damage.
  Confirm technical pause, Building Mode, disconnect, and restart do not advance
  active-time stages unexpectedly.
- [ ] Administer one antidote at an early and a critical stage. Expected: it
  cures the entire owned illness and symptoms.
- [ ] Allow fatal Banquet poison only with informed reviewers. Expected:
  Banquet fatality causes permanent Spectator/participation exclusion; an
  unrelated death does not. Placed cakes are outside this held-food path.

## Event flags and sources

- [ ] Create a test flag in Advanced **Flags**, configure a source that updates
  it, and use it as an Event prerequisite. For example, use a Region entry or a
  public political result. Expected: the flag changes through the configured
  source and the existing Event triggers after its prerequisites become ready.
- [ ] Give that Event a prerequisite timer, then turn a required flag Off.
  Expected: the countdown resets and starts in full when every required flag
  becomes On again; technical pauses do not advance the active-play timer.
- [ ] Trigger a public completed vote, succession result, public treaty change,
  and rebellion change with a configured Event/flag listener. Expected: the
  configured public source arrives once with the correct public outcome.
- [ ] Check that secret ballot choices, individual secret turnout, private treaty
  terms/breaches, and private papers do not appear in Event source output or
  flags. An uncertain/recovered operation must not invent a cancellation or
  replay a confirmed source.

## Voice rooms: real microphones and remote transport

- [ ] With the optional Simple Voice Chat runtime installed and two working
  microphones, test same-room audibility, adjacent rooms, and room/outside
  isolation in **both** directions. Expected: isolated room membership, not
  walls/doors or simulated muffling, controls what can be heard.
- [ ] Test rapid boundary crossing, reconnect, teleport, paused play, and
  Building Mode. Expected: automatic membership follows the saved Region and
  leaving returns to proximity voice.
- [ ] Outside rooms, create/use an ordinary manual voice group. Expected: it
  still works outside but cannot bypass automatic room membership inside.
- [ ] Test the actual remote hosting path. e4mc may relay Minecraft gameplay,
  but it is **not** evidence that Simple Voice Chat UDP is relayed. Confirm a
  working UDP voice connection separately; do not use a voice relay claim unless
  reviewers actually heard each other remotely.

## Restart and reset scenarios

- [ ] Perform one clean restart during an ordinary meeting and one during a
  non-destructive scenario state (for example, a saved treaty, rebellion visit,
  complication recess, or illness). Expected: the saved state resumes without
  rerolling papers, changing an electorate, duplicating an Event, or leaking
  private data.
- [ ] Close Minecraft before replacing either mod JAR for a version comparison.
  Never downgrade a world after it has written newer political data; restore the
  matching stopped-world backup with its matching mods instead.
- [ ] For a second complete rehearsal, make a new disposable world/copy. Do not
  delete journals, modify permissions, or repeat a three-hour soak as part of
  this beta checklist.

## Review record and handoff

- [ ] Record Minecraft/Fabric/Core/Last Council/Simple Voice Chat versions,
  world-copy identifier, reviewer roles, date, and whether the remote UDP test
  occurred.
- [ ] Mark each observed result as pass, concern, or not run; attach screenshots
  or short reproduction notes for concerns without exposing private papers or
  secret choices.
- [ ] Keep the copied world and its logs until the release decision is made.
  Report separately that map roleplay, representative-load testing, and any
  unchecked privacy/microphone items remain unaccepted.

Related behavior/evidence: [election controls](ELECTION_RESULTS.md),
[Council review history](REVIEW_COUNCIL.md), [conflicts](LC05A_RESULTS.md),
[foreign affairs](LC05B_RESULTS.md), [rebellion access](LC06_RESULTS.md),
[banquet](LC07B_RESULTS.md), and [Voice rooms](LC10_RESULTS.md).
