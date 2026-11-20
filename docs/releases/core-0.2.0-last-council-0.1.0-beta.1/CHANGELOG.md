# Release changes

## Chronicle Core 0.2.0

Since the existing Core 0.1.0-beta package:

- Added reusable weighted Event pools, durable draws and portable pool content
  for scenario-owned progression and complications.
- Extended shared ballot support and player-facing input handling, including
  anonymous submitted-count access for authorized scenario controllers.
- Added generic Region access rules with safe outside Points and scenario-owned
  permission providers.
- Added optional automatic Voice rooms through the existing Region subtype
  editor and the public Simple Voice Chat API.
- Retained the existing sessions, Building Mode, authoring, loot, lore, scenario
  profile/export, offering and enchanting services used by The Quiet Below.

No voice implementation or HUD vendor code is included. The compiled Core code
and assets match the automatically verified dev.20 build; release metadata now
identifies version 0.2.0 and the current repository contact URLs.

## The Last Council 0.1.0-beta.1

First requested beta package, requiring Chronicle Core 0.2.0:

- Character/House views, private randomized secrets and House goals, shared
  number variables, consistent cross-House knowledge and requested paper copies.
- Motion and treaty lecterns; separate Chancellor proceedings; immutable public
  and secret ballots with a fixed electorate captured at opening.
- Persistent Regency candidates, elimination, later confirmation votes, public
  withdrawal and unresolved endings, and an advisory meeting-capacity warning.
- Randomized Council complications through Core Event pools and ordinary Events.
- Public/private House treaties, physical conflict declarations, voluntary
  support, signed Peace/Ceasefire, and two-thirds foreign war/peace decisions.
- Rebellions, guarded palace Regions, one-visit permission and formal
  reinstatement.
- Optional single-serving food poisoning, escalating illness, antidotes and
  persistent exclusion after fatal Banquet poisoning.
- Public political Event sources for flags and narrative progression, preserving
  private treaty and secret-ballot information.

Compiled code/assets match the verified dev.20 review delivery. Version,
minimum Core dependency and repository contact metadata are updated for this
package. Final content, map/group rehearsal, real voice and representative-load
acceptance remain human review tasks.
