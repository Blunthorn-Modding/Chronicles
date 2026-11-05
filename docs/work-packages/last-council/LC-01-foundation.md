# LC-01 — Scenario foundation and randomized character handouts

Status: planned; first implementation slice. Depends on existing Core. Brief: §§1–4, 11–12, 16.

## Outcome and ownership

Add the Last Council content module using existing character cards, with randomized lore/goal/house-information bundles and small authoritative house/seat links behind them. Default one representative per house. Do not introduce a second house dossier or require a separate hand-authored Player Role for every randomized house.

Latest [UI direction](UI_SCENES_FLOW.md): the personal menu has Character and Houses. All displayed card fields, including pronouns, are editable through the existing card workflow. Houses shows public representatives/candidacy; private handouts sit under the owner's own house only. There is no separate House standing panel or unified political-action menu.

Core owns optional reusable book-pool/template/draw and delivery extensions alongside the existing card/role/book services. Last Council owns political metadata, pool content and house/fact/goal/knowledge rules. Touch `settings.gradle`, shared `build.gradle`, new `campaigns/last-council` and focused Core services; Quiet Below gameplay is outside this package. Read [the clarified flow](FRAMEWORK_INTEGRATION.md).

## Work

**LC-01A — Foundation:** specify stable character/house links, lifecycle and public/private projections; add the Fabric module and matching build requirements; isolate selected scenario defaults; persist seat/candidacy metadata independently of editable card text. Reuse roles where they supply starting gear/destinations, without requiring a duplicate role or house-catalogue workflow. LC-02 can build on this slice before randomization UI is complete.

**LC-01B — Reusable Core draw:** extend the existing role/book assignment workflow with opt-in pools, authored templates, weights/counts/repetition rules and literal variable bindings. Preserve existing fixed `PlayerRole.books` lists and migration/export behavior. Add staged GM preview/reroll → explicit commit → durable recipient-specific delivery. Reuse `BookDeliveryRuntime`/`RoleBookGrant` and their inventory/discovery conservation rather than a parallel book library; extend the delivery adapter for card-bound handouts if no role is assigned. A template can produce distinct private instances without consuming another recipient's template or changing global chest-book once/until-found rules.

**LC-01C — Council relationships:** select actual house facts and goals, then independently roll cross-house knowledge against that same draw. Each secret may be known by zero, one or several other houses; coverage is not guaranteed. Last Council validates target presence, candidate/realm references and authored compatibility. Opposing objectives are allowed. Do not assign self-knowledge as a secret about another house, refer to absent houses or invent facts missing from the target's assignment. Keep the algorithm bounded and reject an impossible required draw clearly.

Design/review the shared pool selection and GM cast-preview flow before new screens. Existing cards remain public; privately rendered secrets do not populate their description. Do not mutate common book definitions or all players' role grants to supply one recipient's generated text.

## Acceptance and verification

- Two characters cannot cast as the same house through a stale or forged membership request. Multiple representatives, if requested, require a reviewed delegation rule first.
- Cross-house secret references resolve to the same committed target fact. Equal input/seed produces a reproducible draft; additional seeds may produce different compatible casts. Configured counts/repetition/weights are respected, and impossible assignments fail without partial grants.
- Test no outsider knowledge, one observer and multiple observers of the same secret. No post-processing fills in undistributed secrets or guarantees reciprocal knowledge. Owners cannot query exposure status/count or infer it from a public allocation marker; knowledge can of course be revealed through actual RP.
- Reconnect, pause/resume, death, repeated entry and restart retain the committed cast and delivery identity. An explicit new cast is separate from starting another Chronicle play window.
- An outsider cannot obtain another character's private bundle or the draw seed through cards, ordinary catalogues, packets, logs or reusable exports. Characters can intentionally show/trade their issued physical books.
- Candidate elimination does not remove a house's seat; rebellion status is represented separately and initially cannot be changed through a placeholder button.
- Role assignment/gear/lore remain existing Core behavior, including privacy and once-only first entry.
- Restart preserves roster identity and links; changed/missing role IDs show actionable setup diagnostics.
- Core+Last Council runs without Quiet Below. Core-only and Quiet Below-only installations remain usable; deliberate multi-mod selection cannot mix defaults.

Run focused assignment/pool/template/relationship/profile tests, `:core:test` and the new `:campaigns:last-council:test`, then a disposable startup/draw/role-or-card delivery/restart smoke. Check full inventories, interrupted grants, old fixed-role content and export round trips. These new module targets are added by this package; they do not exist in the planning baseline.

## Review gate

Review card-to-house authority and scenario isolation before LC-02 uses LC-01A. Review pool authoring, coherent cross-house generation and GM cast preview before LC-01B/C delivery. Document reusable definitions versus world-bound draws and preserve the fixed-role book path. No automatic roster changes during an open future ballot.
