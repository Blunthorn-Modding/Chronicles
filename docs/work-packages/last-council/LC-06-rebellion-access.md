# LC-06 — Rebellion, reinstatement and guarded Regions

Status: planned. Depends on LC-01/02/03; integrate LC-04/05. Brief: §§3, 5.3, 6.2, 8.4–8.5, 11–12, 14.

## Outcome and ownership

Core gains an optional guarded-Region access policy with a safe exit Point and server-authoritative movement handling. Last Council supplies the palace eligibility policy and political rebellion/reinstatement operations. Use existing Region subtypes and Point pickers, not another Areas/Locations registry.

Rebellion itself is new Last Council political state. Core's ordinary participant tracking is not used to represent it: a rebel remains a participant throughout. A confirmation dialog is just a UI safeguard, not a reused rebellion mechanic. Room audio from LC-10 is an independent Region capability, not a consequence of palace permission or Council status.

## Work

1. Design/review declare/join rebellion confirmations that explicitly list loss of seat, proposals/sponsorship, candidacy and palace access. One house is enough to found a rebellion; multiple rebellions have stable identities.
2. Refuse membership changes during an open ballot. Between ballots, commit loss of privileges/candidacy, access restriction and public announcement through a recoverable operation.
3. Preflight the authored outside Point; safely eject an inside rebel and prevent ordinary re-entry. Cover walking, teleport/portal, reconnect, respawn, vehicle/passenger paths and stale server/client state without chunk-forcing or per-tick full-world scans.
4. Keep rebels active participants with public records and their own agreements/conflicts. Do not call Departure or put them in Spectator.
5. Restore specified houses only after a successful two-thirds reinstatement motion closes, or an explicitly recorded GM settlement when no Council exists. A temporary supervised visit does not restore a vote. Handle crowned-ruler crises separately.

## Acceptance and verification

Test unilateral declaration, joining, separate factions, candidate removal and loss/restoration of every Council action. A three-house rebellion after an8-seat ballot means the next electorate is5, while an already-open electorate never changes. Loyal house/civil-war participants retain access.

Use native clients at the palace boundary: inside ejection, blocked entry, login/respawn and a host exception. Missing/unsafe exit bindings refuse preflight without leaving a half-rebel or teleporting into danger. Restart mid-operation holds/reconciles safely; stale reinstatement cannot restore twice. Editing/Building Mode authoring exceptions must be explicit and must not grant a political seat.

## Review gate

Review the confirmation, safe-exit workflow and participant experience before enabling exile in a human rehearsal. Independently review cross-store recovery and bypass paths. No automatic violence, map destruction or character elimination.
