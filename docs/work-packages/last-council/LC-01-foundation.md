# LC-01 — Scenario foundation and houses

Status: planned; first implementation slice. Depends on existing Core. Brief: §§1–4, 11–12, 16.

## Outcome and ownership

Add the Last Council content module and a durable house roster linked to existing role assignments. Default one representative per house. A player can view their house and public standing without receiving another character's objectives or secret documents.

Core owns only extension work needed to register/select optional mechanics and expose existing roles safely. Last Council owns house definitions, membership/standing and rules. Touch `settings.gradle`, shared `build.gradle`, new `campaigns/last-council` and narrowly scoped Core registration hooks; Quiet Below gameplay is outside this package.

## Work

1. Specify IDs, roster lifecycle, role/house link validation and public/private projections. Keep crown candidacy separate from Council eligibility.
2. Add the Fabric module with matching Core/game requirements and common test/build configuration. No placeholder ballot engine or duplicate role-card subsystem.
3. Register the scenario/mechanics provider; define deliberate selection when multiple content mods are installed. Reuse the existing separate authoring/export approach.
4. Persist versioned house state and audited roster changes. Deny conflicting assignment and unresolved/stale mutations; preserve reconnect/death identity.
5. Design the roster/setup/player entry flow before implementing new screens. Council controls are opt-in; existing operator editing/debug behavior remains consistent.

## Acceptance and verification

- Two characters cannot cast as the same house through a stale or forged membership request. Multiple representatives, if requested, require a reviewed delegation rule first.
- Candidate elimination does not remove a house's seat; rebellion status is represented separately and initially cannot be changed through a placeholder button.
- Role assignment/gear/lore remain existing Core behavior, including privacy and once-only first entry.
- Restart preserves roster identity and links; changed/missing role IDs show actionable setup diagnostics.
- Core+Last Council runs without Quiet Below. Core-only and Quiet Below-only installations remain usable; deliberate multi-mod selection cannot mix defaults.

Run focused roster/profile tests, `:core:test` and the new `:campaigns:last-council:test`, then a disposable startup/role-link/restart smoke. These new module targets are added by this package; they do not exist in the planning baseline.

## Review gate

Review role-to-house authority, scenario isolation and the roster UI design before LC-02 uses them. Deliver the minimal foundation with no automatic roster changes during an open future ballot. Document defaults versus map/progress storage and the public/private boundary.
