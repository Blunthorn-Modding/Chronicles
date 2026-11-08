# Council review ? development build 0.2.0-dev.5

Use a fresh import of the matching **e4mc Host** and **Player** packs in `../client`. The Host pack contains a clean review world; the Player pack joins it. Only one person opens the bundled world. Open to LAN with commands disabled for guests and share the e4mc address. Voice chat is omitted. The dedicated authenticated alternative remains `127.0.0.1:25589` with its matching plain review pack.

SchinderRenge is preset as Chancellor, without a House. Operators may otherwise play a House; only the appointed Chancellor controls proceedings. The old world in your existing launcher profile is not overwritten. To retain that played world, update **both** Core and Last Council to matching dev.5 jars after closing Minecraft, removing their older versions first. Back up that profile before testing a development update.

## Setup

1. Pause play, enable Editing, then use **Players > House & papers** to assign an unused House to each online reviewer. Existing committed papers now permit adding a previously unassigned character without changing anyone else's papers. Existing assignments cannot be reassigned. After Regency starts, late entrants may be voters but cannot become new crown candidates.
2. Each House player joins participation with `/chronicle players join`. Creative is allowed. Turn Editing off and start/resume play. First play issues papers automatically. The Chancellor cannot also represent a House.
3. Voters stand inside the central Council Region: x/z -5..5, y 64..80. The motion lectern is at 0,65,4. I opens Chancellor proceedings; R opens the current ballot; both are rebindable.

## Bundled review

- Add a late player after the first session has issued papers. Confirm that original papers remain unchanged and that the new player receives theirs, including after reconnect.
- Open the public motion. Heads are centered and full public character names wrap. The question, proposal and separate labeled fields use the same presentation at the lectern and in proceedings. Scroll for additional fields. Confirm a choice: it is final. No Refresh button is needed.
- Keep proceedings open while others vote. Progress updates automatically against the captured electorate; abstention counts as submitted. It never closes voting automatically. Secret ballots show the Chancellor only an anonymous submitted count; ordinary players see no secret turnout or individual choices.
- Close a public ballot and read its actual recorded attempt, named choices and result from the motion detail. Void an open attempt with a public reason and check that its replacement remains a separate attempt. Secret attempts never reveal named choices; closed secret results show totals only.
- Regency requires at least two real, eligible candidate characters present when its first ballot opens. Offline review placeholders are omitted. It retains absolute majority. Before a later round, move a remaining candidate outside the Region: opening must wait until they return, retaining the slate. A technical void still allows a replacement in the same meeting. Resolve or defer ordinary motions first.
- In Creative item search type I, O, R and K, including a rebound Council key. The query and inventory must remain intact. Outside text entry the keys should work normally.
- As operator, open **Advanced > Game setup > Source catalogue** to inspect reusable House definitions, secret/goal templates, fact defaults and knowledge rules. This first catalogue is read-only; it does not expose the live draw or which characters know whose secrets.

The next gate is the human multiplayer/layout review. Reusable pool editing/export and physical handout delivery, rebellion, treaties, banquet and random complications remain separate work packages. This build does not claim their completion. Verification evidence is in [GROUP_REVIEW_VERIFICATION.md](GROUP_REVIEW_VERIFICATION.md).
