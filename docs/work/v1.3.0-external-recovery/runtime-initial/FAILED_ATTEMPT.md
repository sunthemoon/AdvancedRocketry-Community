# V130-ROCKET-03B first packaged attempt

Result: **FAIL**, runner exit 1. The world was not retried or reused.

- Integrated runner: `7ca7861`, SHA-256 `0d202f27514146eb3d8acd5bdf672f779bbc185d347a207f6a6f6803fb3e9feb`.
- Host SHA-256: `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200`.
- Fixture SHA-256: `8144ded5d03764cdd6b1eda8ff5e778f7dd87c564ebde3e5dfe20d382629085f`.
- Exact command: `launch-command.txt`; runner exit: `runner-exit.json`.
- Execution: 2026-09-26 15:47:16 through 15:48:50 UTC; Java 17.0.7,
  normal Forge 47.4.10 dedicated server, offline loopback port 62854.
- The first four processes all exited 0. Assembly, entity restart and skipped
  provider phases passed their checks. Actual fixture uninstall completed its
  two live authority checks, 22-tick observation, flush and clean stop.
- The fourth phase then failed the log classifier on the additional Forge
  `GameData/REGISTRIES` ERROR header `Unidentified mapping from registry minecraft:block`.
  Its only continuation entry was `arce_adapter_test:cargo_container: 1022`.
  Both this header and the subsequent unidentified-mappings ERROR are retained.
- No provider reinstall or restored-container restart ran. This is not a
  six-phase success, not a forced crash, and not a release Gate approval.

`recovery-evidence/` is the unmodified failed runner output. Its original
`SHA256SUMS` has SHA-256
`497b6d5c15d6c030b8a2bc3681adbbcce31ff191a101439b48c35e26ddcf6076`.

The runner stopped before its fourth disk-capture step. `failure_postcheck.py`
therefore copied the stopped world files without launching Java or altering the
world, into `failed-uninstall-disk/`, then parsed those copied raw NBT bytes.
The postcheck exited 0: exact rocket UUID and opaque RocketEntityData matched
the provider-skipped phase; the full schema-2 EXTRACTING journal matched; the
assembler remained, the four source blocks were air, no cargo BlockEntity or
item drop was present, and the loopback endpoint was closed.

The source snapshot preserves the exact executed runner and its imported
repository helpers. Original process observations identify Python PID 4428
and owned Java PIDs 13916, 12368 and 23576; the second JVM PID was not sampled.
All sampled processes were absent after the runner terminated. No unrelated
process was stopped. No repository files or build outputs were modified by
this verifier, and no Gradle command was run.

Residual runner issue: its air-shell fill crossed four chunks while readiness
waited only for the central chunk. In this actual world the fill reported
`No blocks were filled` and the exact four-block assembly succeeded; this does
not establish deterministic setup on other world generation outcomes.
