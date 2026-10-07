# V180 transfer readiness-wait history

Status: READY_FOR_REVIEW; milestone v1.8.0. Root is the sole integrator; a different agent reviews the actual source before any committed verification or integration.

## Fixed assignment and ownership

- Base: `257e7b3adb54dc693d815434394f311d28e800cc`.
- Worktree: `D:/GitHub/arce-v180-transfer-wait-history-20261007`.
- Branch: `codex/v1.8.0-transfer-wait-history-20261007`.
- Write scope, exactly four paths:
  - `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/TransferFailureDiagnostics.java`
  - `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferService.java` (only the existing readiness-wait recording call)
  - `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/TransferFailureDiagnosticsTest.java`
  - this `docs/work/v1.8.0-transfer-wait-history/TASK-01.md`.

All other source, tests, workflows, registry, central/status/provenance records, AGENTS and Git metadata are read-only. New implementation/test code is original MIT code, with no upstream or native source copied. No persistent/public identifier, schema, packet or gameplay API is introduced.

## Observation semantics and non-goals

The existing manager diagnostic has one latest-prepared target. A package-private `readinessWait(UUID)` records that target's actual WAIT_ENTITY_READY branch only after an observed dispatch at the current entered service tick. Foreign/null targets, absent dispatch, and a previous tick's dispatch cannot manufacture wait history. New entry (even with an equal tick value) and completion invalidate the previous wait-dispatch marker. First and last ticks retain exact signed int values without duration arithmetic; attempts count actual recorded calls and saturate at `Integer.MAX_VALUE`. Preparation replacement (including same UUID/null) and clear reset the wait history. Unobserved or foreign history reports `UNOBSERVED`, not invented tick zero or a fabricated count.

The existing WAIT_ENTITY_READY recording call is the sole production caller. Flight control flow, readiness short circuit and query ordering, ticket calls/values, native/world getters, fuel/journal authority, exception routing, cold fixtures, 270/1400 timing and assertions remain unchanged. No loader repair, timeout change, per-tick logging, extra native query, new service or authority is authorized. The two existing failure payloads remain printable ASCII and at most 512 bytes. Wait history cannot prove a native I/O cause, uninterrupted waiting, successful loading or full-v1.8 delivery.

## Verification and evidence

Preserve all 19 existing test methods byte-for-byte. Add focused tests for first/last/count, target isolation, nonobserved/stale dispatch, exact signed tick boundaries, preparation/clear reset, saturation, read-only snapshots and worst-case payload bounds. Independent source review and real focused Java/Jupiter execution remain required. A later exact committed hosted cohort must retain the original failure oracle and full unfiltered regression; no test-count or outcome is assumed.

Current permission allows only apply_patch, read-only Git/text and bounded Python/static checks. No Java, Gradle, native/server, network, installs, cleanup, staging, commit or push is authorized. Evidence and TEMP/TMP/TMPDIR are restricted to `D:/GitHub/ARCE-Task-Evidence/v1.8.0/transfer-wait-history-implementation-20261007/`; Python runs with `-B`. No whole-source exports or old evidence rewrites.

The first actual static check completed with 19 controls and no failures. All 19 prior test bodies and the complete prior test source after removal of the appended block are byte-exact; 11 new methods are declared, with all 30 Java methods unrun. A separate Python finite formatting model evaluated 18,000 combinations with a maximum of 437 bytes; it does not execute the Java formatter. Git whitespace check exited 0, and the service has an exact one-call inverse. The final record/postcheck will include exact four postimage hashes and the task-status update.

Diagnostics is 128 lines, its test is 413 lines, and the existing transfer service remains 761 lines with only its observation call replaced. The existing service exceeds the 500-line responsibility-review threshold, but no responsibility or authority was added to it and it remains below 800 lines. No implementation/test failure occurred in the bounded static cohort. A later final-metadata helper stopped before its body with an IndentationError; its original script, stderr and exit 1 are retained, with a separately numbered correction. An oversized initial policy-read console output was truncated, followed by smaller relevant reads. Source remains an uncommitted development snapshot, not delivery or Gate evidence. Final postimages are held for the assigned independent reviewer; Root owns integration and fixed-commit replay. Java compilation/Jupiter, exact committed hosted evidence and full Required Gates remain unverified.
