# v1.0.0 transaction recovery verification

Date: 2026-09-05. Branch: `codex/v1.0.0-stable-core`.
Base: `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`.
Status: transaction matrix VERIFIED; the full v1.0 milestone remains IN_PROGRESS.
This is development evidence, not an approved release Gate or independent review.

## Artifact and environment

- Fixed development JAR: `advancedrocketry-community-1.20.1-1.0.0-dev.jar`,
  1,238,476 bytes, 766 entries.
- SHA-256: `cf077ef750149f6b957ac4e8dc8a7da8e5cb91eafbe80703859157aac34a49b8`.
- [Artifact manifest](artifact-manifest.json), [artifact audit](artifact-audit.txt).
- [Source/test/harness inventory](source-inventory.json): 714 hashed inputs;
  the base commit alone does not identify this uncommitted development tree.
- [Evidence checksums](checksums.txt) cover the archived reports, full logs,
  staged journals, manifests and harness revisions.
- Windows 11, Java 17.0.7, Gradle 8.8, Forge 47.4.10, JEI absent.
- Python: `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe`.
  Only process-local `PYTHONUTF8=1`, `TEMP`/`TMP` and `JAVA_HOME`/`PATH` were set.
- Owned disposable servers bind to `127.0.0.1:25610`, `online-mode=true`.
  No player login, physical GPU, multiplayer or performance result is claimed.
- The installed template contains the earlier `5b5d942a...` development JAR;
  its bytes are verified before copying. Only the new owned server copy receives
  the tested JAR. Summaries distinguish template and tested hashes. Original
  servers, failed worlds and historical release evidence are not overwritten.

## Reproduced defect and fix

The pre-fix JAR is 1,238,172 bytes / 766 entries, SHA-256
`65b10b88521a41191cecf01724e03139549c22e08595615d75ab9341b41dc7af`.
Its transaction recovery service is unchanged from the accepted Beta base;
new opt-in probes make intermediate states observable without replacing them.
See the [pre-fix manifest](pre-fix-artifact-manifest.json).

1. The [first run](pre-fix-smoke/summary.json) passes `ASSEMBLY:EXTRACTING:2`
   through two restarts, then fails `ASSEMBLY:SPAWNED:5` at the single-material-
   authority assertion. The recovery receipt precedes the entity-active receipt.
2. A [repeat with read-only failure diagnostics](pre-fix-diagnostic/summary.json)
   reproduces the same failure with the same JAR. All five origin positions are
   occupied while the original rocket entity is present; the chest contains
   17 diamonds in slot 0 and 64 iron ingots in slot 26. Raw logs retain the
   original 30-second assertion timeout, not a relaxed retry or repaired world.
3. `RocketTransactionRecoveryService` previously checked block-chunk readiness
   alone. A block chunk can become available before its persisted entities.
   Selecting BLOCKS and deleting the journal at that point restores inventory
   before the saved rocket arrives, duplicating the material authority.
4. Recovery now also waits for `ServerLevel.areEntitiesLoaded` at the snapshot
   origin, where assembly/disassembly entities are anchored. This matches the
   existing flight-recovery readiness boundary. It neither loads chunks nor
   creates tickets and does not change authority selection, save schema or IDs.
5. Ten new unit cases check deferred entity reads, unloading again, short-circuit
   on an unloaded block region, signed chunk boundaries and integer extremes.
   The previously failing packaged case passes after the fix, including a
   second restart. All 25 cases in the complete matrix also pass.

The fix prevents this premature recovery decision. It does not retroactively
identify or delete duplicated assets in worlds already affected by an older build.

## Real transaction probes

The probe requires both `advancedrocketrycommunity.releaseTestHooks=true` and
an explicit `advancedrocketrycommunity.transactionCheckpoint=TYPE:PHASE:PROGRESS`.
It is disabled by default, server-local, single-use, dedicated-server-only,
main-thread-only and rejects a server with players present.

The wrapper delegates each actual journal write before observing its record.
At the selected record it flushes the real world and SavedData, emits
`ARCE_RELEASE_TRANSACTION_PAUSED`, and waits at most 20 seconds for an external
kill. Expiration is a failure. No console command is queued while the main
thread is paused. Normal gameplay does not flush at every journal write.

For rollback cases, a per-transaction fault is injected after mutation 2 so the
existing transaction's catch/rollback code executes. FAILED additionally rejects
one real undo operation. No replacement journal, fabricated world layout or
post-kill repair is used. Normal restart has no checkpoint selection or fault
wrapper and resolves the persisted partial state through production recovery.

## Packaged matrix contract

The [completed matrix](matrix/summary.json) and
[post-run raw-evidence audit](matrix-audit.json) cover 25 actual kills and
50 clean recovery/second-restart processes. All forced exits are 1; all clean
exits are 0. Twelve cases retain ENTITY authority and thirteen retain BLOCKS.
The audit rereads 75 log hashes, 25 staged journal hashes/records and 100 exact
server-chat command receipts. The archived executed harness matches the live
source and the summary's SHA-256. No failed case is omitted from a successful
run; the two earlier failing runs remain in their own directories.

The five-block fixture has a fuel tank, motor, chest, seat and guidance computer
at the same origin used by the established packaged flight harness. Setup
checks the exact two occupied inventory slots and absence of a third item.

- Assembly: SNAPSHOT_VALIDATED:0, LOCKED:0, EXTRACTING:1-5, EXTRACTED:5,
  SPAWNED:5, COMMITTED:5, ROLLING_BACK:2, ROLLED_BACK:2 and FAILED:1.
- Disassembly: SNAPSHOT_VALIDATED:0, LOCKED:0, RESTORING:1-5, RESTORED:5,
  COMMITTED:5, ROLLING_BACK:2, ROLLED_BACK:2 and FAILED:1.
- Each case must use a live owned process, a nonzero process-kill exit, and no
  graceful stop command before the forced exit. The staged on-disk journal is
  bounded-read, checked against the exact marker and archived before restarting.
- First restart requires successful production recovery, the expected entity
  or block authority, zero dropped items around the fixture, and no competing
  loaded rocket. Entity authority requires every origin position to be air.
- Entity authority is normally disassembled after verification. The entire
  restored structure content hash and complete chest Items SNBT must equal the
  captured baseline, not merely contain a few expected substrings.
- Clean shutdown and the second restart must retain those exact contents, zero
  rocket entities and zero journal entries. No repeated recovery is allowed
  after journal cleanup. Operator reports and on-disk schema 2 are checked.

These are durable, explicitly flushed checkpoints. They do not simulate power
loss during writes or prove durability of unflushed intermediate operations.
Five-block mutation coverage is not a 2,048-block performance measurement.

## Commands and completed results

Commands run at the repository root with the supplied Python and Java 17.
The exact packaged invocations and reproduction order are in
[COMMANDS.md](COMMANDS.md).

| Command | Result | Evidence |
|---|---|---|
| `gradlew.bat test --tests '*RocketTransactionCheckpointTest' --tests '*RocketTransactionReleaseProbeTest'` | 0; 48 probe cases | [Log](probe-tests.txt) |
| `gradlew.bat clean build` before recovery fix | 0; 352 tests | [Log](clean-build.txt) |
| `gradlew.bat clean build` with readiness fix | 0; 38 s, 362 tests / 70 suites, zero failures/errors/skips | [Log](clean-build-recovery-fix.txt), [JUnit XML](junit/) |
| `gradlew.bat test runData` after fix | 0; test up-to-date, generated output unchanged | [Log](test-data-recovery-fix.txt) |
| `gradlew.bat runGameTestServer` before fix | 0; 44 required tests | [Log](game-tests.txt) |
| `gradlew.bat runGameTestServer` after fix | 0; 2 m 24 s, 44 required tests | [Log](game-tests-recovery-fix.txt) |
| `python -m unittest discover -s tests -p 'test_run_v*.py' -v` | 0; 76 cases including 14 new transaction-harness tests | [Log](server-harness-tests.txt) |
| `python -m unittest discover -s tests -v` | 0; 735 tests in 3,865.199 s, 731 passed and four existing conditional skips | [Full output](python-full-suite.txt) |
| `python scripts/validate_build_artifact.py <versioned JAR> --expected-version 1.20.1-1.0.0-dev --content-manifest <report>` | 0 for pre-fix and fixed JARs | [Fixed](artifact-audit.txt), [Pre-fix](pre-fix-artifact-audit-retry.txt) |
| Same validator on a locally renamed `pre-fix-dev.jar` | 1; filename contract correctly rejects it; rerun uses the original versioned file | [Failure](pre-fix-artifact-audit.txt) |
| `python scripts/check_client_imports.py` | 0 | [Log](client-imports.txt) |
| `python scripts/check_celestial_identity.py` | 0 | [Log](celestial-identity.txt) |
| `python scripts/validate_repository.py --require-approved-identity` | 0; 45 checks, no pending/warnings/failures | [Log](governance.txt) |
| `python scripts/validate_v1plus_planning.py --package-root AdvancedRocketry-Community-v1plus-Development-Docs` | 0; eleven plans and all 33 original input hashes/sizes | [Log](planning-audit.txt) |
| Additional work-document link check | 0; 83 local links across six untracked/changed work documents | [JSON](work-links.json) |
| `python scripts/run_v100_transaction_forced_stop.py ...` pre-fix runs | 1 twice; original and diagnostic reproduction | [Initial](smoke-command.txt), [Diagnostic](diagnostic-command.txt) |
| Same runner with the fixed JAR, all 25 cases | 0; 75 processes, two restarts per case | [Output](matrix-command.txt), [Summary](matrix/summary.json) |
| `python scripts/run_dedicated_server_smoke.py ... --port 25611` | 0; new fixed-JAR installation, first start and restart | [Output](ordinary-baseline-command.txt), [Summary](ordinary-baseline/summary.json) |
| `python scripts/run_v060_flight_server_smoke.py ... --expected-version 1.20.1-1.0.0-dev` | 0; 20 round trips / 40 legs, all eight clean restart checkpoints | [Output](ordinary-flight-command.txt), [Summary](ordinary-flight/summary.json), [Ledger](ordinary-flight/round-trip-ledger.json) |
| `python scripts/run_v100_flight_forced_stop.py ...` with fixed JAR | 0; all ten flight kill/recovery cases | [Output](fixed-flight-command.txt), [Summary](fixed-flight/summary.json), [Audit](fixed-flight-audit.json) |
| `git diff --exit-code -- src/generated`; historical release diff; `git diff --check` | 0 each | [Results](git-results.txt) |
| `git diff --exit-code` | 1; intended development changes, not a clean release tree | [Diff](development-tree.diff.txt) |

GameTest fresh-world startup emits Forge/default configuration notices and the
vanilla missing-server.properties diagnostic before creating defaults; the raw
logs are retained. Its 44-test result is not labeled a warning/error-free
packaged-server result. The dedicated matrix applies its own strict project-log
and recovery-outcome checks.

The ordinary regression's [raw-log/debit audit](ordinary-flight-audit.json)
checks 17 clean process exits, 40 exact debits and one logical rocket across
20 Earth/Moon round trips. The subsequent ten-case forced-flight rerun preserves
the complete receipt/state/fuel contract with the fixed JAR. Combined with the
transaction matrix, 35 actual forced stops are now verified against the same
development artifact; this is not mixed-artifact final-candidate evidence.

The complete Python suite passes on this development revision. The four skips
are the existing conditional checks for absent local v0.2-v0.5 build artifacts,
not disabled new tests or added exemptions. The full output retains their
individual reasons. Prior full-suite evidence remains the separate planning
baseline; it has not been relabeled as this revision's result.

## Changed scope and remaining acceptance

Runtime: `RocketManager`, `RocketTransactionReleaseProbe`,
`RocketTransactionCheckpoint`, `RocketTransactionFaultProbe`,
`RocketTransactionRecoveryService`. Tests: three new Java test classes and the
new Python transaction runner/test module. Tracking: implementation log,
stabilization audit, changelog and authored-work provenance. Inherited planning
and earlier stabilization changes remain separate, uncommitted work.

`RocketManager` already exceeds 500 lines; the diagnostic lifetime is kept in a
small separate class instead of mixing more persistence/fault-injection logic
into the manager. It remains below 800 lines. No domain transaction algorithm,
packet, asset, dependency, schema, identifier or budget is changed.

The full v1.0 milestone is still IN_PROGRESS. Representative Beta upgrade,
candidate compatibility, 20 final-candidate round trips, four-hour reference
load, real-client/GPU scenarios, independent audit, uninvolved installer,
candidate/tag reproduction and human approval remain outstanding. No v1.1+
feature, release tag or version approval is introduced here.
