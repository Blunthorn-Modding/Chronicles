# LC01 reusable House content — UI proposal

**Status: owner-approved workflow and cast lock; native implementation and verification in progress on dev.9.** [Interactive standalone example](../../design/last-council-content.html) extends Advanced → Game setup → Source catalogue. The toolbar is a reviewer-only simulation, not a game permission selector. All sample state is local and Reset discards it. No live players, committed facts, assignments, seeds or draws are accessed.

The compact Chronicle frame has category buttons for Houses, Secrets, House goals and Knowledge rules. For each list category, creation appears above search and the left-hand list. Selection displays separately styled read-only cards to the right; small popups handle editing and House selection. Narrow layouts stack the list above details. Search filters the displayed definitions, not the underlying data. Empty and no-match states are explicit. Close returns to the prior screen. The default screen contains descriptions, not operational/debug output.

| Control | Action and validation |
|---|---|
| Category | Select the corresponding source catalogue. Knowledge rules presents one settings record. |
| Search | Filter the current list by identifier or visible text. |
| Create House / secret / House goal | Open a new popup; Save adds an independent definition only after validation. A duplicate ID is refused. |
| Edit | Open a copy of the selected definition; IDs are fixed in this proposal. Save checks that the original still exists and refuses stale/missing keys instead of silently creating one. A real server must also check the expected revision. |
| House fields | Identifier, title, reputation, description, interests and default-candidate checkbox. House IDs use the existing namespaced syntax. |
| Secret fields | Identifier, positive weight, literal template text, explicit fact defaults and applicable Houses. Add fact default creates a key/value row; Remove fact removes that row. Duplicate or malformed `fact.*` keys are refused. |
| House goal fields | Identifier, positive weight, literal template text and applicable Houses. |
| Choose applicable Houses | Open a multi-select popup of existing Houses. Done applies the selection; Cancel leaves it unchanged. At least one existing House is required when saving a template. |
| Edit rules | Set secrets per House (0–4), goals per House (0–4) and independent per-secret per-other-House knowledge chance (0–100%). Zero observers is valid; no exposure tally is shown. |
| Preview sample | Render a selected saved template with explicit sample JSON string bindings supplied in the popup. Unbound variables remain visible and are named; sample defaults are not silently substituted. No live data is read. |
| Delete | Confirm removal. A referenced or otherwise in-use House must be refused with a reason; the sample demonstrates refusal for House references in secrets/goals. A native implementation must also consult all relevant authoritative uses. |
| Scenario content header | Use the existing global Scenario content flow to export reusable content, subject to its authorization. No per-catalogue export control is added. Exports must not include players, a live cast, rendered private handouts, seed or draw. |
| Save / Cancel | Save validates without clearing the form on failure; Cancel discards popup edits. Show a useful error for a changed authorization, validation failure or concurrent modification. |

The catalogue is OP-only even for inspection; the non-OP simulation shows an access message without the source definitions. All writes additionally require Editing, inactive play and no unresolved operation. The server must recheck authority, lifecycle, references and expected revision at commit; disabled buttons are only an affordance. Read-only inspection and authorized export remain available while writes are locked. **Approved:** lock authoritative source editing after cast commitment, including during later enrollment, so the existing rendered facts and the source used for late enrollment remain consistent. The owner approved this policy on continuation; inspection and authorized export remain available. This UI proposes no silent cast replacement, reset or mutation.

Native validation must apply the existing domain limits and semantics, including namespaced House IDs, bounded text and control characters, positive bounded weights, valid fact keys and House references, category capacity, and drawable pool feasibility. The prototype demonstrates common errors; it is not a complete domain validator. It permits editing fact rows explicitly and refuses edits to a missing record. Literal `${...}` tokens are not executable code. Supported bindings include `${house.title}`, `${fact.*}` and `${other.title}`; the last requires another assigned House. Renderer and server validation must enforce their context and escaping; the sample preview performs simple literal substitution only.

The owner approved native implementation. A new experience uses a separate prepared world; this editor does not reset a live cast. Source diagnostics identify incomplete content while allowing incremental authoring; actual cast generation retains its final validation. Export/import uses the existing Scenario content flow. See LC01_EDITOR_COPY_RESULTS.md for implementation boundaries and verification evidence.
