# Council review ? development build0.2.0-dev.3

One local authenticated server: `127.0.0.1:25589`. Import `../client/Chronicle-Last-Council-0.2.0-dev.3.mrpack` as a new client profile. It contains Core + Last Council, Fabric API and Simple Voice Chat, with GUI scale2/narrator disabled. The running review is added to the Multiplayer presets. This is a disposable flat platform, not a production map.

SchinderRenge is whitelisted/operator and assigned a sample house. Two offline placeholder house assignments allow a solo reviewer to see a multi-candidate slate. They never cast or count toward the electorate. Only real eligible representatives in the Council Region at opening count: alone means N1/majority1. The separate automated proof uses two real non-GM voters and a third non-voting candidate.

## Start

1. Stand on the platform near the lectern at0,65,4. The Council Region covers its central11?11 area (x/z from-5 to5).
2. Run `/chronicle editing off`, `/chronicle players join`, then `/chronicle session start`.
3. Open `/chronicle council proceedings`. **Call meeting** sounds the configured bell; **Open meeting** begins political discussion. It does not create a new Chronicle session.

## Review together

- Choose one of the two prepared motions, open its vote and use **R** (rebindable Open current ballot in Controls) or `/chronicle council vote`. Dismissing never abstains. Confirm a choice; it becomes final. Return to proceedings and close the ballot. The public motion can show named choices; the secret one shows aggregate totals only after closing.
- Try **Void ballot** before closing, supply a reason, and open a new replacement. The old ID/reason/own choice remain in Recorded ballots. A finalized result cannot be voided.
- Request a **regency vote**. It is always secret. To inspect elimination alone, close the first completed regency without casting: no candidate has a majority, so exactly one is eliminated by the announced tied-last draw. Another completed regency needs **Close meeting ? Open meeting**. Then choose a remaining candidate and close: your one-of-one majority crowns them. Ordinary meetings still work afterward.
- A technical regency void can be replaced in the **same** meeting, using a new ID/electorate but the same candidate slate. It does not consume the meeting's completed-regency slot.
- Read the result and archive wording, check the compact layout, and try opening a ballot from an inventory or a nested draft. Notifications should not replace the current screen; the hotkey should preserve an unsent editor draft and ignore text entry/confirmation dialogs.

The physical motion lectern supports proposing/sponsoring, but a real second house is needed to review another-house sponsorship. Building/paused interaction behavior stays Core-owned.

## This gate

Please review the small proceedings/ballot screens and constitutional result wording before rebellion starts depending on these outcomes. Actual random complication pools/timing/recess, withdrawal/reset/unfinished-ending controls, schedule-capacity warnings and physical house-paper delivery are still pending. The tested progression hook is not the actual LC08 integration. There is no hidden-complication surprise in this review build yet.

`python scripts/council_review.py start` restarts this exact review after a saving stop. Its current path is in `../reports/council-review-latest.json`. Enter `stop` in its console for a saving shutdown. Do not manually delete files while it runs. Successful test worlds are cleaned after evidence is retained; this one review world remains for your inspection.
