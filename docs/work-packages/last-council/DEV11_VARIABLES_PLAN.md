# dev.11: secret variables and unique assignment

Owner approved on 11 November: name these variables, keep number ranges on their secret, draw each secret once per experience, preserve all resolved information in shared knowledge, expose Own House / Other House clearly, and remove source sample previews entirely. No changes to Event/Region previews, political voting, treaties or live casts are authorized by this slice.

## Ownership and flow

Core supplies a bounded inclusive integer NumberRange with overflow-safe random sampling, alongside the existing weighted draw and literal template renderer. Last Council owns secret definitions, eligibility, globally unique allocation, variable context, privacy and immutable cast persistence. Source schema 2 carries numbers by friendly name; old fixed facts remain readable/preserved for compatibility but have no authoring UI. House placeholders become house.own and house.other; prior aliases remain readable. Other House and numbers resolve once for each secret, so every occurrence and every knowledge copy is consistent.

Allocate a secret to no more than one eligible assigned House, respecting configured counts, scopes and weights. Use bounded weighted ordering plus bipartite augmentation so a flexible House cannot accidentally consume a restricted House's only secret. Refuse impossible draws clearly; do not reduce counts, duplicate secrets or reroll live state. Save selected secret IDs privately with new casts. Late enrollment excludes previously used IDs. Old casts retain their exact rendered papers; where template IDs were not saved, reserve templates eligible for any pre-existing House conservatively before a late draw. This preserves old worlds without guessing which secret can be reused. Conditional legacy references use a bounded compatibility fallback with provider pruning before publication; the explicit 20,000-branch limit can require simplifying complex old dependency sets. Canonical number-only variables do not need that fallback.

## UI

Reuse the approved source frame and focused forms: Name/weight, Text with Insert variable, Houses, and Number variables on secrets. Goals have Name/weight, Text and Houses. Number editing asks Name, Minimum and Maximum, validates signed inclusive integers, and preserves stable names during edit. The insertion picker explains Own House as the original recipient and Other House as one randomly selected other participating House. No raw JSON sample bindings or Preview actions remain in the source editor. Existing CAS, operator/Editing/cast guards and retained uncertain drafts remain.

## Verification

Tests cover range boundaries including full signed-int width, schema 1/2 and near-limit round trips, local variable references, matching feasibility/uniqueness/weights, same-secret substitution consistency, knowledge-copy equality, restart and late enrollment including conservative old-cast handling. Use the existing disposable native source-editor runner and add real variable-picker/range authoring and no-preview assertions. Run relevant Core/Last Council tests and all matching remaps before e4mc Host/Player packaging. Preserve the original brief, played launcher world and unrelated acoustics; remove stopped disposable fixtures after proof. Human treaty/complication reviews remain deferred, not passed.
