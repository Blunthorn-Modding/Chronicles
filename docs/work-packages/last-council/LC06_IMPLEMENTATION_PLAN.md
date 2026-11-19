# LC06 implementation - rebellion and Region access

The owner approved the simplified UI on 18 November 2026. Branch: `feat/last-council-rebellion`, from dev.16 `5bd11b8`. Rebellions is a My house left-side section. Public notices use the same registry. Normal reinstatement is a two-thirds motion. There is no no-Council fallback. One Chancellor action grants a palace visit that ends on exit.

## Authority and ownership

Core owns reusable Region access bindings, safe outside Points, provider registration and server movement enforcement. The existing Region subtype and Location picker flow configures them. Last Council owns rebellion membership, public factions, visits and political effects. Rebels remain participants; ordinary wars do not remove rights. No extra permission levels, separate palace catalogue, forced settlement or new dependency.

The rebellion world journal is canonical. Council roster projections suppress seat/candidacy for current rebels without rewriting cast/enrollment records. Declarations and joins require active eligible participation, no open ballot, no crown and safe authored exits. A durable membership change precedes candidate withdrawal and ejection; failures hold play and reconcile the same membership at recovery. Candidate withdrawal is preflighted, and never reversed by reinstatement into an already locked succession.

## Storage and transactions

Strict bounded rebellion journal and initialized marker live under world/chronicle/last-council. Definitions and progress remain separate; no political state enters scenario exports. Exact operation IDs make declaration/join/visit retries idempotent. Per-House and faction revisions pin reinstatement targets and refuse ABA/stale proposals. Reinstatement uses the existing saved Council CLOSE operation: exact Core ballot close, exact rebellion effect, then clear pending. Missing, orphan or mismatched outcomes hold. Proceedings schema 5 explicitly reads 1-4; old General/foreign and pending operations retain identity and electorate. Failed and void votes change no membership. Public projections contain no private lore or secret choices.

## Sequence and delegated ownership

1. Independent worker: new pure rebellion service/store/tests only.
2. Independent worker: new Core Region access foundation, movement hooks and tests; no politics or menus.
3. Lead: typed Motion/schema/protocol, runtime policy and recovery coordinator, Region menu integration.
4. Dedicated UI worker: approved native personal registry, confirmations, motion selector and single Chancellor action.
5. Independent read-only access/recovery review. Lead inspects diff, integrates and runs tests; shared Gradle/native class use remains serialized.

## Evidence and delivery

Focused pure tests: declaration/join/unique membership/history/visits/expiry-on-exit/reinstatement receipts/stale/ABA, strict files and uncertainty. Proceedings tests: schemas 1-4, fixed two-thirds, failed/void/duplicate outcomes and interrupted close. Core tests: binding safety, provider refusal and geometry rules. Native clients: normal/compact screens; role separation; rebellion preserves participation and loses every political privilege; safe ejection and blocked movement/teleport/login/respawn/vehicles; visit per House and one entry; durable restart and real file-fault recovery. Run full module build, Python regressions and existing native ballots/foreign/conflict suites proportionately. Preserve reports and source worlds, remove stopped generated fixtures only after evidence retention. Deliver matching e4mc Host/Player mrpacks, no dedicated review server. Human group review remains deferred where not performed. No downgrade of journals in place; retain pre-upgrade copies.
