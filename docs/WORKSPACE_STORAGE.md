# Workspace storage and test retention

4 November 2026: the owner requested removal of every review server and
explicitly approved deleting the generated test worlds, backups and old
pilot/restore copies. All three running review servers were stopped cleanly
before removal. No review server was restarted.

The removed directories were `../test-runs`, `../reviews`, `../backups`,
`../pilots` and `../restores`. Bulk non-map profiler recordings and the huge
derived execution-sample JSON were removed from reports. The inventory
estimates **96.68 GiB** of logical file data removed; this is not a measurement
of physical free-space change. Receipt: `../reports/workspace-cleanup-20261104.json`.

The large accumulation came chiefly from hundreds of disposable test runs,
each with its own copied Minecraft libraries/world, plus retained backups and
profiling captures. Old reports and dated documentation may reference those
deleted worlds or raw recordings. Compact result JSON, logs and documentation
remain; retained summaries do not imply the original raw files still exist.

Preserved: the Git repository, the original Word source and unrelated user
documents, Blockbench designs, Dragon Island map and map-specific reports,
small reusable fixtures, the shared Java/Gradle toolchain/cache and the
canonical stopped server runtime used to stage future tests. Minecraft
profiles installed through the owner's launcher are outside this cleanup.

Future test worlds and review installations are disposable unless a current
investigation specifically requires retaining one. After successful tests,
stop processes and remove their generated worlds; retain compact results and
only useful representative screenshots. Do not create new review servers or
archive another full installation for every build unless requested/needed.
Validate absolute deletion paths remain under their intended workspace
directory, and do not traverse junctions or symlinks.

The current beta upload package belongs in `../releases/0.1.0-beta`; its former
alpha-named folder was renamed for publication. Historical source release notes
remain under `docs/releases/0.1.0-alpha.1`. Older
client review packs are superseded. Map-specific reports are preserved because
they belong to the separate map-building work.
