# V120-PREC-04B-E — Energy-only chunk binding recovery

```yaml
status: verified
parent_status: IN_PROGRESS
date: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
uncommitted_worktree: true
artifact_sha256: 8e061d3ef27bbd45c1174b5b783e78304f5e2fa7cca4bac7278946e76359de59
environment: Windows 11, Java 17.0.7, Forge 47.4.10
decision: ADR-019
```

## Defect and correction

A prepared controller can repair an older same-owner binding on all eight
physical ports. Previously barrier B only re-dirtied and read back the seven
Item ports. When Energy occupied a separate chunk, a serialization exception
at barrier A could clear that chunk's dirty flag while its saved binding still
used the old generation. Migration could then become `ACTIVE` with the repaired
binding only in memory. Reload would lose access to the `PREPARING` binding
repair and the saved generation would conflict with the controller.

Before barrier B, the validated Energy port is now marked dirty alongside the
Item ports. The bounded saved-port verifier also compares its actual binding
and resource roots. A mismatched disk root prevents activation. Energy remains
physically owned; no schema, persistent ID, resource ownership, chunk-loading
permission or external Energy transaction guarantee changed.

The production changes are confined to `PrecisionAssemblerBlockEntity` and
`PrecisionAssemblerMigrationSaveVerifier`. Existing save-failure test helpers
are reused by the new `PrecisionAssemblerMigrationBindingGameTests` class.
The tests share the precision package to access the existing package-private
Anvil reader, rather than adding a public read API or a second parser.

## Two bounded GameTests

Both fixtures place the north-facing controller at chunk-local `(15, 13)` and
assert that the Energy chunk contains neither the controller nor any Item
port. They start with an actually saved Energy generation 1 and a prepared
controller generation 2. Four expiring test tickets keep only the fixture
available. A local instance of the real manager executes synchronously so an
intervening world tick cannot hide a cleared dirty flag.

| Case | Required observation |
|---|---|
| One Energy serialization failure | Barrier B retries the repaired binding; the actual disk binding is generation 2 before any additional save or reload |
| Two Energy serialization failures | Both barriers attempt the write; disk stays generation 1; controller remains `PREPARING` with no Item capability; removing the fault and explicitly retrying activates generation 2 |

Both cases then flush and reload the actual controller and Energy BlockEntity
data from Anvil. Every BlockEntity field survives the round-trip, the structure
forms with generation 2, the central Items stay unchanged and Energy stays
`777 FE`. The reader rejects packed BlockEntities and strips only the false
`keepPacked` chunk-envelope flag, which `saveWithFullMetadata` does not emit.
The fault listener and local manager are always cleaned up in `finally`.

These are actual disk serialization/readback/reload checks in one GameTest
server process, not new-process crash tests or a claim about hardware fsync.

## Execution record

All successful Gradle commands explicitly selected
`C:\Program Files\Java\jdk-17.0.7` as `JAVA_HOME`.

| Command / stage | Actual result |
|---|---|
| `gradlew.bat runGameTestServer --offline --no-daemon`, initial fixture | FAIL: 2/114 failed at the baseline equality check because of the chunk-envelope flag; 112 existing tests passed |
| Same command, corrected fixture and unchanged production code | FAIL: the two new tests reproduced the missing second write and activation with the old disk binding; 112 existing tests passed |
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon`, corrected production code | PASS, 2m43s; 600 JUnit and 114/114 Required GameTests; DataGen wrote 0 files |
| Independent `gradlew.bat test --tests '*PrecisionAssemblerPersistedChunkReaderTest' --offline --no-daemon` | PASS, 6 tests, 19s; reviewer did not independently repeat the GameTests or packaged server |
| Full JUnit recap without explicit `JAVA_HOME` | Exit 1 before Gradle: inherited `JAVA_HOME` named a nonexistent JRE; no test ran |
| Full JUnit recap with Java 17 selected | PASS, restored full test results from cache in 14s |
| `gradlew.bat cleanTest test --offline --no-daemon --no-build-cache` | PASS, 20s; executed 123 suites / 600 tests, 0 failures/errors/skips |
| `python -B scripts/validate_v120_machine_resources.py` | PASS, 9 machine blocks |
| `python -B scripts/validate_v1plus_planning.py` | PASS, 11 version plans / 33 source inputs |
| `python -B -m unittest tests.test_v120_electrolyzer_beta_migration_smoke tests.test_v120_electrolyzer_world_fixture tests.test_v120_rolling_world_fixture tests.test_v120_precision_world_fixture -q` | PASS, 40 tests |
| `git diff --check` | PASS |
| `git diff --exit-code`; `git diff --exit-code -- src/generated` | Exit 1: existing uncommitted implementation and two generated removal-message entries; not a clean-tree Gate |

The first setup failure and both production regression failures are retained,
not recast as passes. The corrected pre-fix run reports
`Energy binding was not retried at the second barrier` and
`Migration activated with an older Energy binding still on disk`.
The successful GameTest log deliberately contains the injected serialization
exceptions and readback refusals; those are not suppressed server failures.

See the [raw execution logs and hashes](energy-binding-recovery/SHA256SUMS),
[full JUnit summary](energy-binding-recovery/junit-summary.json), and
[short-check output](energy-binding-recovery/short-checks.txt).
The repository-wide validator's previous 45-check pass remains historical;
this correction reran the scoped validators and changed-document link check,
not that entire validator.

## Packaged old-world migration and restart

The immutable old Precision world was copied into a new disposable server.
Its source region SHA-256 remains
`66ba5141c0568e7abcd156f1b19c6a9b6de25d63af14acbb8308ddf2b242954e`.
Only the copy received the artifact identified above. The bounded command was:

```powershell
python -B scripts/run_v120_precision_legacy_migration_smoke.py build/dedicated-server-smoke/prec04b-energy-binding-legacy-20260926 --artifact build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar --evidence-dir docs/work/v1.2.0-precision-assembler/energy-binding-recovery/legacy-restart --java 'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
```

Both migration and the subsequent same-world restart exited 0. The machine
remained formed with all seven central Item slots, inert old shadows, migration
markers and the exact process state. Outputs remained one advanced circuit and
two redstone torches, revision `5`, applied UUID
`06cb2c96-2b03-4e07-9fea-49da84e2f778`, and Energy `800 FE`.

The [machine-readable summary](energy-binding-recovery/legacy-restart/summary.json)
and [filtered lifecycle](energy-binding-recovery/legacy-restart/filtered-lifecycle.txt)
identify the artifact, source world and two full process logs. Those full logs
are preserved beside the summary and checked against its hashes. This normal
old-world regression did not inject the Energy-only writer fault into a second
process. The earlier `6077fd10…` migration reports and manifests remain unchanged.

## Review and remaining boundary

Independent source review found no additional scoped issue in the production
correction or two fixtures, and independently reran the six bounded-reader
JUnit tests. This is not a complete release review.

`V120-PREC-04B-E` is verified within the stated scope. Its parent still needs
the finite controller-transaction snapshot/recovery checks tracked as
`V120-PREC-04B-T` in the [implementation log](../v1.2.0-implementation-log.md).
Review identified a candidate PREPARED-recovery ordering gap to reproduce;
it did not establish a normal-tick Item duplication or loss defect. Tests must
use coherent complete controller snapshots, not independently recombine roots
that are serialized together.

Physical I/O failure, power-loss/fsync, Item-entity drop atomicity, packaged
writer-fault injection and the ADR-018 full-content campaign remain outside this
verification. No long load test, remote server test, real-GPU or two-client
campaign was started. No version Gate, release tag or commit was created.
