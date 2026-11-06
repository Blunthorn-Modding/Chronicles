# Council review - development build0.2.0-dev.4

One local authenticated server: `127.0.0.1:25589`. Import `../client/Chronicle-Last-Council-0.2.0-dev.4.mrpack` as a new client profile. It contains Core + Last Council, Fabric API and Simple Voice Chat, with GUI scale2/narrator disabled. The running review is added to the Multiplayer presets. This is a disposable flat platform, not a production map.

SchinderRenge is whitelisted/operator and explicitly appointed Chancellor, with no house assignment. Two offline placeholder houses allow a useful candidate slate but never vote. Review voting with a different person playing a house, as requested. Operators are allowed to play houses when they are not Chancellor. Only the appointed Chancellor can open proceedings; the motion lectern never contains procedural controls.

## Start

1. Add your reviewer with `/whitelist add TheirMinecraftName` if needed. This local server binds to127.0.0.1; remote access is not configured.
2. Pause play and enable Editing. In **Players > House & papers > House**, give that person an unoccupied house and, if wanted, candidacy. The Chancellor is configured separately on the **Chancellor** tab; assigning both roles is refused.
3. Stand near the lectern at0,65,4. The Council Region is the central 11x11 area (x/z-5..5). Your house player joins participation and turns Editing off. Start the first play session; secrets, house goals and known other-house secrets issue automatically and survive restart.
4. The Chancellor opens proceedings with **I** (rebindable Open council proceedings) or `/chronicle council proceedings`. **Call meeting** sounds the bell; **Open meeting** begins political discussion. These do not create a new Chronicle session.

## Review together

- The lectern shows a visible motion list and separate description/details; it has only player proposal/sponsorship/withdrawal actions. The Chancellor uses the proceedings sidebar and its Motions workspace. Choose one of the two prepared motions, open its vote and the house player uses **R** (rebindable Open current ballot in Controls) or `/chronicle council vote`. Dismissing never abstains. Confirm a choice; it becomes final. Return to proceedings and close the ballot. The public motion can show named choices; the secret one shows aggregate totals only after closing.
- Try **Void ballot** before closing, supply a reason, and open a new replacement. The old ID/reason/own choice remain in Recorded ballots. A finalized result cannot be voided.
- Before **Regency** unlocks, every pending ordinary motion needs a completed ballot or an explicit deferral. Deferring clears it for that meeting; it returns next meeting. A void leaves an ordinary motion unresolved. Request a **regency vote**. It is always secret. To inspect elimination, close the first completed regency without casting: no candidate has a majority, so exactly one is eliminated by the announced tied-last draw. Another completed regency needs **Close meeting > Open meeting**. Resolve any returned deferred motions, then let the house player choose a remaining candidate and close. With one actual house voter present, one vote is a majority; offline placeholders never increase N. Ordinary meetings still work afterward.
- A technical regency void can be replaced in the **same** meeting, using a new ID/electorate but the same candidate slate. It does not consume the meeting's completed-regency slot.
- Ballot heads use public character-card names. Public Yes/No/Abstain outlines are green/red/amber; secret heads always remain neutral and expose no turnout. Read the result and archive wording, check the compact layout, and try opening a ballot from an inventory or a nested draft. Notifications should not replace the current screen; the hotkey should preserve an unsent editor draft and ignore text entry/confirmation dialogs.

The physical motion lectern supports proposing/sponsoring, but a real second house is needed to review another-house sponsorship. Building/paused interaction behavior stays Core-owned.

## This gate

Please review the small proceedings/ballot screens and constitutional result wording before rebellion starts depending on these outcomes. Actual random complication pools/timing/recess, withdrawal/reset/unfinished-ending controls, schedule-capacity warnings and physical house-paper delivery are still pending. The tested progression hook is not the actual LC08 integration. There is no hidden-complication surprise in this review build yet.

`python scripts/council_review.py start` restarts this exact review after a saving stop. Its current path is in `../reports/council-review-latest.json`. Enter `stop` in its console for a saving shutdown. Do not manually delete files while it runs. Successful test worlds are cleaned after evidence is retained; this one review world remains for your inspection.
