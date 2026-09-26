# V120-PREC-04B-T — bounded controller transaction recovery

```yaml
status: verified
parent_status: IN_PROGRESS
date: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
uncommitted_worktree: true
artifact_sha256: 96962cc968816ef7a7b4235a0888d0c0c83b4ef0718f3bc7969d17c6a6aefb99
environment: Windows 11, Java 17.0.7, Forge 47.4.10
decision: ADR-019
```

## Implemented correction

`PrecisionAssemblerResourceStore.reconcileJournal` previously wrote the seven
Item slots, resource revision and applied marker while a recovered journal
could still be `PREPARED`. The actual recovery call produced a complete
controller snapshot with the after-revision and that old phase. Reloading this
snapshot failed its own `invalidPrepared` check and remained blocked.

After validating the owner, revision, marker, resource shape and every slot's
before/after value, recovery now advances `PREPARED` to `APPLYING` before any
resource write. It uses the existing journal owner and schema. Rejected data
is still rejected before mutation; resource rollback behavior is unchanged.
The change makes recovery follow normal commit ordering, without changing
Energy ownership, recipes, network messages or migration roots.

The reproduced observation is a **callback-boundary recovery failure**, not
proof of Item duplication/loss during ordinary ticks. Normal controller
serialization keeps process, journal and all seven Items together. The tests
do not treat these roots as separate disk writes or claim a normal vanilla
save can interleave every Java statement.

## Test method and finite coverage

`PrecisionAssemblerTransactionTestFixture` uses the actual formed controller,
ports, process owner, recipe, executor and resource store. Instance-local
reflection temporarily wraps the two existing `changed` callbacks, retaining
their original behavior and recording `saveWithFullMetadata()` after each
notification. No production observation API or global manager is installed.
Capture is capped at 64 entries; callback counts, phase/revision/marker values
and all seven ordered Item prefixes must match. Both callbacks are restored
in reverse order in `finally`, including partial installation failures.

The final-field wrapping is a test mechanism for the selected Java 17 runtime,
not a portable JVM guarantee. If the runtime does not invoke the wrappers, the
required capture/phase assertions fail rather than skip. Captured snapshots
are complete serialized controller tags, not actual Anvil writer-fault cuts.

Five short GameTests cover:

| Case | Explicit observations |
|---|---|
| Two-input/two-output commit | 12 journal-bearing cuts: PREPARED-before, APPLYING-before, seven Item-write prefixes, after-revision, applied marker, APPLIED; plus completed IDLE reload |
| Five-input/one-output commit | Same 12 cuts; all five consumed inputs, exact comparator output and empty unused output |
| Recovery interrupted again | 11 journal-bearing notifications from an actual PREPARED recovery call; all seven prefixes use APPLYING, then revision, marker and APPLIED |
| Full progress without journal | Both actual pre-journal and post-clear snapshots remain explicitly blocked; no Item/revision/marker rewrite or additional FE consumption |
| Two migrated batches | Four iron/four redstone produce exactly two circuits/four torches; 200 FE remains; marked old port snapshots stay inert; second-batch state survives reload; a deliberately stale first-batch journal is preserved and refused |

Each replay checks the original captured snapshot, a new complete snapshot
saved after recovery, and the original input again. Items, revision, applied
transaction identity and the full physical Energy root remain unchanged after
settlement. The two-batch test captures port snapshots **after migration
markers exist**; pre-migration resource roots are retained only for the inert
shadow comparison. Stale-journal injection is deliberately synthetic invalid
input, not a claimed coherent save cut.

## Actual execution

Gradle commands selected `C:\Program Files\Java\jdk-17.0.7` as `JAVA_HOME`.

| Command / checkpoint | Result |
|---|---|
| Initial `gradlew.bat runGameTestServer --offline --no-daemon` | Compilation failed in 14s: test helper imported `ModIdentity` from the wrong package; corrected without running tests |
| Same command with corrected import, before production correction | FAIL in 1m31s: 1/118 failed, `recovery cut 7: recovery did not finalize`; remaining 117 passed |
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon` after correction and test review | PASS in 2m55s; 123 suites / 600 JUnit, 0 failures/errors/skips; 119/119 Required GameTests; DataGen wrote 0 files |
| Independent `gradlew.bat runGameTestServer --offline --no-daemon` | PASS in 1m37s; 119/119 Required, including the five transaction-cut tests; reused the existing GameTest world, no clean; compilation/resources up-to-date |
| `python -B scripts/validate_v120_machine_resources.py` | PASS, 9 blocks |
| `python -B scripts/validate_v1plus_planning.py` | PASS, 11 plans / 33 source inputs |
| `python -B -m unittest tests.test_v120_electrolyzer_beta_migration_smoke tests.test_v120_electrolyzer_world_fixture tests.test_v120_rolling_world_fixture tests.test_v120_precision_world_fixture -q` | PASS, 40 tests |
| `git diff --check` | PASS |
| `git diff --exit-code`; `git diff --exit-code -- src/generated` | Exit 1: outstanding implementation and the previously added two removal messages; not a clean-tree Gate |

The initial compiler failure and runtime regression are retained in the
[evidence manifest](transaction-recovery/SHA256SUMS). The
[JUnit summary](transaction-recovery/junit-summary.json) and
[short-check output](transaction-recovery/short-checks.txt) are scoped to this
checkpoint. The successful full log includes existing intentional save-event
faults; their associated GameTests passed.

## Packaged migration/restart regression

An unchanged copy of the archived old Precision server used source region
SHA-256 `66ba5141c0568e7abcd156f1b19c6a9b6de25d63af14acbb8308ddf2b242954e`.
Only the copy received the new artifact. Both server processes exited 0:

```powershell
python -B scripts/run_v120_precision_legacy_migration_smoke.py build/dedicated-server-smoke/prec04b-transaction-legacy-20260926 --artifact build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar --evidence-dir docs/work/v1.2.0-precision-assembler/transaction-recovery/legacy-restart --java 'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
```

The exact seven central Item slots, legacy shadows, markers and process state
survived migration and another same-world restart. Outputs stayed one advanced
circuit/two torches, revision `5`, applied UUID
`06cb2c96-2b03-4e07-9fea-49da84e2f778` and Energy `800 FE`. See the
[summary](transaction-recovery/legacy-restart/summary.json) and
[lifecycle](transaction-recovery/legacy-restart/filtered-lifecycle.txt).
Full process logs are preserved with their original hashes.

This is normal packaged migration/restart, not a new packaged phase-injection
or forced-stop run. Earlier forced-stop evidence remains bound to its original
artifact. No old manifest or historical artifact claim is overwritten.

## Independent review and limits

Initial review identified two test coverage issues: reloading pre-marker port
snapshots did not represent a completed migration, and replaying only the
original input did not check a saved recovery result. Both were corrected
before the final clean build. Phase/revision/marker and slot-prefix checks now
supplement callback counts; both supported recipe shapes are exercised.

Follow-up source review found no additional scoped issue in the recovery-order
change or corrected fixtures. Independent GameTest execution passed all 119
Required tests and confirmed the five-test transaction batch. Its
[full log](transaction-recovery/independent-gametest.txt) has SHA-256
`74fe51f6e7d8f3e5bc96a5f4e6c2d4186983031c55ba1880884e48fa316317ec`;
the JAR hash stayed unchanged. The reviewer did not independently repeat the
packaged server. `V120-PREC-04B-T` is verified within these explicit boundaries.

The full-progress/no-journal cuts deliberately preserve blocked state rather
than guess whether to settle a batch. Hardware power-loss/fsync, external
Energy atomicity, Item-entity drops and the ADR-018 full-content campaign are
not proven here. No long-load, remote, real-GPU or two-client test was run.
The [implementation log](../v1.2.0-implementation-log.md) remains authoritative
for the parent task and version Gates; this report does not approve a release.
