# LC-06 — Rebellion, reinstatement and guarded Regions

Status: [UI proposal](LC06_UI_DESIGN.md) simplified after the 18 November owner review; the owner approved the revised workflow on 18 November and native implementation is automatically verified; human multiplayer review remains pending. See [implementation evidence](LC06_RESULTS.md). Rebellions has its own left-side entry under My house. Region access uses existing Region subtypes. The owner removed reinstatement without an eligible Council, superseding the earlier consent fallback. The owner retained one simple Allow one palace visit action in Chancellor proceedings; the visit ends on exit. Depends on LC-01/02/03; integrate LC-04/05. Brief: §§3, 5.3, 6.2, 8.4–8.5, 11–12, 14.

## Outcome and ownership

Core gains an optional generic Region access capability within the existing subtype workflow, with a safe exit Point and server-authoritative movement handling. Do not introduce access levels or a separate palace setup catalogue. Last Council supplies the palace eligibility policy and political rebellion/reinstatement operations. Use existing Region subtypes and Point pickers, not another Areas/Locations registry.

Rebellion itself is new Last Council political state. Core's ordinary participant tracking is not used to represent it: a rebel remains a participant throughout. A confirmation dialog is just a UI safeguard, not a reused rebellion mechanic. Room audio from LC-10 is an independent Region capability, not a consequence of palace permission or Council status.

## Work

1. Add Rebellions alongside House information, Secrets and Goals in My house. Reuse its registry when opened through a public notice; declaring and joining are actions on that screen, not under House information. Design/review declare/join rebellion confirmations that explicitly list loss of seat, proposals/sponsorship, candidacy and palace access. One house is enough to found a rebellion; multiple rebellions have stable identities.
2. Refuse membership changes during an open ballot. Between ballots, commit loss of privileges/candidacy, access restriction and public announcement through a recoverable operation.
3. Preflight the authored outside Point; safely eject an inside rebel and prevent ordinary re-entry. Cover walking, teleport/portal, reconnect, respawn, vehicle/passenger paths and stale server/client state without chunk-forcing or per-tick full-world scans.
4. Keep rebels active participants with public records and their own agreements/conflicts. Do not call Departure or put them in Spectator.
5. Restore specified houses only after a successful two-thirds reinstatement motion closes. There is no fallback reinstatement or consent workflow when no eligible Council remains. Expose a single Allow one palace visit Chancellor action; one visit ends on leaving the palace and never restores a vote. Handle crowned-ruler crises separately; reinstatement cannot return someone to an already locked or advanced succession slate.

## Acceptance and verification

Test unilateral declaration, joining, separate factions, candidate removal and loss/restoration of every Council action. A three-house rebellion after an8-seat ballot means the next electorate is5, while an already-open electorate never changes. Loyal house/civil-war participants retain access.

Use native clients at the palace boundary: inside ejection, blocked entry, login/respawn and a host exception. Missing/unsafe exit bindings refuse preflight without leaving a half-rebel or teleporting into danger. Restart mid-operation holds/reconciles safely; stale reinstatement cannot restore twice. Editing/Building Mode authoring exceptions must be explicit and must not grant a political seat.

## Review gate

Review the confirmation, safe-exit workflow and participant experience before enabling exile in a human rehearsal. Independently review cross-store recovery and bypass paths. No automatic violence, map destruction or character elimination.
