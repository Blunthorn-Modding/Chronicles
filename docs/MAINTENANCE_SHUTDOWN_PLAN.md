# City-scale maintenance shutdown repair

The organizer authorizes a focused repair and regression using a disposable copy of the retained November journal. No new three-hour run, review-server upgrade, dependency change or gameplay/schema change is required.

- Keep ordinary live-journal append durability unchanged. Only a newly created, private `.prepared` checkpoint candidate may batch disk forcing until its close boundary. Close must force successfully before the existing independent replay/hash/state checks, archival and atomic promotion. A failed force or incomplete candidate cannot be promoted; the original remains evidence. Existing file identity, locks, size limits and per-record validation remain enabled.
- Add observable maintenance stages and progress while shutdown waits for the worker. Do not cancel a transaction or release world ownership while its worker is using the source. Prevent new maintenance starts once stopping is declared.
- Give the long-soak supervisor a separate bounded 300-second graceful drain, with explicit profile capture before stop, progress reports, and profile/thread diagnostics before any owned-process kill. Shared short-smoke shutdown policy is unchanged. Final result/outcome fields must agree after all cleanup checks.
- Verify candidate force batching, create-only/path guards, close-force failure and exact durable replay; run existing compaction fault/replay tests. Exercise actual native stop while maintenance of the captured 95,927-cell journal is deliberately held, release it after stop reaches the drain, and require clean save/exit, native drain proof, readable JFR and healthy reopen. All original evidence is read-only and hash checked.

Tests and native jobs serialize the shared toolchain/ports. Use a scoped branch, commit/push evidence and do not merge to main. A longer timeout alone is not evidence of a throughput repair; record the measured drain duration.

Implemented and verified on 1 November: [focused results](MAINTENANCE_SHUTDOWN_RESULTS.md). The captured-journal drain and fresh reopen both passed with clean saving stops in about eight seconds; the original evidence remains unchanged.
