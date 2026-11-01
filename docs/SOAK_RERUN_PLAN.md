# Soak measurement and shutdown correction

The organizer requests fixes and another three-hour run. Preserve the October raw FAIL result and its world for comparison. The review server, pack, gameplay settings and performance limits remain unchanged.

1. Record the actual exit path of `EcologyEngine.tick()` in an ephemeral, package-private diagnostic enum. A growth attempt remains an active operation even if it queues an incremental extension at the end of the tick. Separate full topology rebuilding, unknown chunks, extension waits, disconnection, pause and runtime skips. No journal/schema/rules/terrain budgets change.
2. Telemetry version 2 retains the old end-of-tick unresolved count for comparison and adds actual-operation counts plus unavailable-reason counts. Keep the same 80% active / 5% unexpected-unavailable limits and the existing travel/recovery exemption. Do not retroactively regrade October data.
3. Every server FINISHED result gets a bounded native-client completion handshake before saving stop, independently of PASS/FAIL budgets. Never turn a failed performance result into PASS; missing acknowledgements or nonzero client exits fail a successful result.
4. Regression checks: actual growth with queued extension; true unload/recovery; paused/skipped and maintenance-ending ticks cannot inherit a prior active operation; FINISHED plus failed budget still waits; crashed/missing/wrong-shaped acknowledgement data remain failures without aborting cleanup; interrupted runs do not wait for success. Version 2 counts must be complete, nonnegative and consistent with the active/unavailable totals.
5. Run relevant Java tests and all scripting tests, build matching diagnostic artifacts, and run a short real two-client normal-world trial. After that succeeds, launch an isolated 10,800-second run with the same seed/workload, verify advancing native telemetry, and leave its supervisor to save-stop automatically.

All builds/native tests serialize their shared outputs and TCP25575/UDP24455. Commit/push the scoped branch; no merge or review-server deployment. Results of the new long run remain pending until its own terminal report exists.
