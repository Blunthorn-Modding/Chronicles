# The Last Council — worldbuilding workbook

> **Draft for discussion · 22 November 2026**
>
> Editable worldbuilding only. The beta, scenario defaults, map and mod code
> remain unchanged. None of these entries has been selected for a live game.

## Open the documents

| Document | Contents | Suggested review focus |
|---|---|---|
| **[Houses and private House information](HOUSES.md)** | **16 Houses**, each with a public identity and **5 alternative private entries**: **80 private entries** total. | Do the families feel distinct? Does each private alternative give its player something concrete and interesting to work with? |
| **[General secret pool](GENERAL_SECRETS.md)** | **50 secrets** that can concern a different side of any family. | Variety, believable stakes and room for different interpretations. |
| **[House goal pool](GOALS.md)** | **50 motivations** for negotiation and roleplay. | Does the character have a reason to care and enough background to pursue the ambition? There is no scored success checklist. |
| **[Complication pool](COMPLICATIONS.md)** | **24 possible developments** during Council meetings. | Would the arrival create interesting choices and conversations, including for a surprised Chancellor? |
| **[Shared setting](SETTING.md)** | The public situation, institutions, proposed common laws, outside actors and authoring assumptions. | Is the kingdom understandable, and is the supplied context enough for a fair argument? |
| **[Historical and literary sources](SOURCES.md)** | Ten annotated research references from royal archives, museums, Parliament and Shakespeare editions. | Which inspirations fit the tone you want? These are patterns adapted into original fiction. |

Start with the Houses, then sample the general secrets and goals. The setting
document is a reference; you do not need to approve every institutional detail
before commenting on a family. These are working names and proposals, not a
locked canon.

## The design in one example

**Public identity:** a recognised family of grain exporters.

**One selected private House entry:** its dependable winter surplus can support
a particular number of other Houses, or its impressive trade depends on a
concealed import arrangement. The five entries are alternative starting
circumstances, not a stack of five simultaneous truths.

**One independently selected general secret:** the family might secretly profit
from an unlicensed gambling room, conceal a debt or have acted generously for
reasons it would rather not explain. This does not have to concern grain.

**A selected motivation:** gain influence over the next ruler, protect someone
from an overreaching coalition, secure recognition or pursue another ambition.
The player decides how their position and private circumstances affect that
pursuit. We do not prescribe a grain-related objective merely because they
represent the grain House.

The same public House can therefore play very differently next time. The
attending combination of Houses adds another source of variety.

## Goals are motivations, not scores

The point is what characters try, risk, compromise and feel they accomplish.
There are no exhaustive end states, point totals or mandatory victory proofs.
A successful compromise can matter as much as the initial ambition; an
unsuccessful bid can still produce satisfying roleplay.

The writing still needs to be usable. A player should not be told to obtain a
pardon for an unspecified relative without knowing the offence, circumstances
and available authority. Likewise, a goal involving another House must refer
to someone actually in the game. Background and possible approaches help the
player begin; they do not limit what counts as a worthwhile outcome.

## How to read and edit the pools

- The House **public** text is what other delegations can know. The five
  **private** blocks beneath it are author material until one is drawn.
- General secrets are recipient-facing truths. A forged document may exist,
  but its false claim is not automatically true; the selected text must make
  that distinction clear.
- Goal motivations can be given to players. Notes about suitable cast, realms
  or political state are for the author and future draw, not extra objectives.
- A complication's quoted opening is what the Chancellor can present. Its
  author notes explain context and possible reactions without selecting the
  Council's response.
- Identifiers such as **H01-I03**, **S12**, **G08** and **C05** are editing
  references. You can change the prose directly and refer to an identifier
  when giving feedback. They are not commands or live configuration IDs.
- Numerical ranges are proposals. One number would be drawn once and retained
  in every copy of that information. These describe political commitments,
  not new Minecraft inventory counters or a resource-management minigame.

## Replayability and the hidden draw

The proposed shape is one House-specific private draw plus one general secret,
with the number of goals left to organizer preference. The draft does not
require every House to be represented. Sixteen catalogue entries fit within
the stated fifteen main and three additional map spaces; exact residence
assignments and the status of those extra spaces are still open.

Selected private facts must be compatible. A broad secret can undermine public
reputation without contradicting another established private fact. When other
Houses learn something, they learn the same resolved information. Knowledge
remains independently random: nobody, one observer or several may know, and
the owner gets no indicator of exposure.

Knowing the possible pool as an author does not reveal the actual evening's
draw. The intended normal Chancellor view does not reveal private assignments
or forthcoming complications. Access to raw server files remains outside that
surprise guarantee. These documents are a catalogue, not an advance live cast
or a prescribed event sequence.

## Implementation is deliberately postponed

This draft changes no Chronicle or Last Council behaviour. It is also not an
importable configuration file.

The content, selection categories and political eligibility belong to **The
Last Council**. Later work would need to distinguish the two private pools,
choose compatible entries and bind any references to the attending cast.
Existing Chronicle template, number and Event-pool services are foundations we
can reuse. Simply setting today's secret count to two would not guarantee one
entry from each pool. **No Chronicle Core change is proposed at this stage.**

After you have revised the worldbuilding, we can decide what belongs in the
initial default set and only then plan its implementation.
