# v0.9.0 migration checkpoint

This checkpoint exercises the packaged Forge server rather than an in-memory
test double. It is bound to implementation commit
`b5483a77a01419f0edf284b79b99e9e325535664` and candidate JAR SHA-256
`468c254d3c7c96cac2c98628aa000916ae84cbbaf834ff43c69d26174415bfe3`.

The harness seeded the five hash-inventoried schema-1 roots into a copy of the
accepted dedicated-server smoke world. On first start the packaged mod:

1. decoded and semantically validated all five sources;
2. created a byte-exact backup and SHA-256 manifest before replacement;
3. staged, reread, and committed five schema-2 files;
4. reported `ARCE-BETA-1002` and all five services operational;
5. stopped cleanly, restarted the same world, reported `ARCE-BETA-1001`, and
   again reported all services operational.

Files:

- [`summary.json`](summary.json) binds the commit, artifact, fixture manifest,
  seeded and migrated hashes, backup, Java runtime, and both server cycles;
- [`backup-manifest.json`](backup-manifest.json) is the runtime-generated
  byte-exact source inventory;
- [`filtered-lifecycle.log`](filtered-lifecycle.log) retains only stable
  migration and operator-report lines. Full disposable logs remain outside Git
  because they can contain machine-specific paths.

The same JAR was rebuilt after a test-harness-only commit and retained the exact
SHA-256 above. This is a migration-slice checkpoint, not the final v0.9.0 Gate
record; the final candidate must rerun the packaged migration test.
