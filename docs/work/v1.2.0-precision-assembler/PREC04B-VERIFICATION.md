# V120-PREC-04B — controller-owned Item resources

```yaml
status: verified
date: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
uncommitted_worktree: true
final_artifact_sha256: 92821ec3273a7b87c2892003ca5321f8cf65105a41572a3f730c3582d67c2000
transaction_artifact_sha256: 96962cc968816ef7a7b4235a0888d0c0c83b4ef0718f3bc7969d17c6a6aefb99
energy_binding_artifact_sha256: 8e061d3ef27bbd45c1174b5b783e78304f5e2fa7cca4bac7278946e76359de59
removal_artifact_sha256: 6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a
save_retry_artifact_sha256: 207430137a547c921b094dec3e1427804858fefb2230aa5b9cc9aafc7b9c7b9d
live_region_artifact_sha256: a917364c8068494b663e4ce9829be87abb2dd7ce9d49f2bb49ac73dd1c31d0a3
readback_artifact_sha256: bf391aca72808d2098a594d1155bcda96509130bac6e018b9ade03102e6b4fae
initial_smoke_artifact_sha256: ff6b4348de28663bfd84b428b72a8f90ee7c6116a9383c9d9752f88060efbc47
environment: Windows 11, Java 17.0.7, Forge 47.4.10
decision: ADR-019
```

## Implemented boundary

Seven Item slots now serialize in `arce_precision_resources` beside the
controller's process state, resource revision and journal. Physical Item ports
delegate to that controller while formed; their schema-1 Item roots are only
legacy shadows after migration. The Energy port remains physically persistent.

An old controller without the new root pauses processing, copies the seven
loaded legacy ports into `PREPARING`, calls a synchronous level chunk-save
flush, reads back the exact controller roots, marks the ports, calls a second
flush, reads back all seven Item port roots/markers and the physical Energy
port binding/resource root, and only then becomes
`ACTIVE`. At most four migration candidates are handled per tick; machines in
one level share the flushes and each required chunk is read once per barrier.
The reader bounds compressed chunks to 1 MiB and decoded NBT to 4 MiB.
Unknown, invalid, oversized or mismatched roots block activation without
deleting the legacy data. This is a stopped-process recovery design, not an
fsync or block-break Item-entity atomicity claim.

## Bounded verification

| Check | Result |
|---|---|
| `gradlew clean build runData runGameTestServer --offline --no-daemon` at the live-region checkpoint | PASS in 2m 25s; 123 JUnit suites, 600 tests, no failure/error/skip; DataGen 61 cached resources, 0 written; fresh-world 100/100 Required GameTests |
| `gradlew runGameTestServer --offline --no-daemon` with the pre-fix save-failure tests | FAIL; the two explicit-retry assertions failed, other 100 tests passed |
| `gradlew clean build runData runGameTestServer --offline --no-daemon` during retry follow-up | Build/JUnit and DataGen PASS; command exit 1 because the shared-chunk fixture precondition failed |
| `gradlew build runGameTestServer --offline --no-daemon` with separated but inter-tick fixtures | FAIL; an intervening save violated the fixture precondition |
| `gradlew build runGameTestServer --offline --no-daemon` on the save-retry artifact | PASS in 2m 11s; 600 JUnit tests and fresh-world 102/102 Required GameTests |
| `gradlew test --tests '*PrecisionAssemblerPersistedChunkReaderTest' --offline --no-daemon` | Regression reproduced: 1/6 failed before the record-length fix; 6/6 passed after it |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine blocks |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 version plans and 33 source inputs |
| `python scripts/validate_repository.py --require-approved-identity` at the live-region checkpoint | PASS; 45 checks, 895 Markdown links, no warnings/failures |
| `python -m py_compile scripts/run_v120_precision_restart_smoke.py` | PASS |
| `python -m py_compile scripts/run_v120_precision_legacy_migration_smoke.py` | PASS |
| `python scripts/v120_precision_world_fixture.py --verify` | PASS; four archived old-world chunks verified |
| `git diff --check`; `git diff --exit-code -- src/generated` at the save-retry checkpoint | PASS; removal follow-up intentionally adds two generated language entries |
| `git diff --exit-code` | Exit 1: this implementation and evidence remain uncommitted; not a clean-tree Gate |

The JUnit codec cases cover supported, future, malformed, wrong-owner and
oversized roots. The read-only Anvil parser tests cover negative chunk
coordinates, missing sectors, truncated record headers/payloads, a flushed but
still-open region's unpadded final sector, duplicate BlockEntities, oversized
decoded NBT and exact root matching. The live-region test also verifies that
reading does not change the file's length. The new GameTests cover clean legacy import,
`PREPARING` and partial-marker replay, changed legacy roots blocking without
overwrite, inert old roots on controller/port removal, controller snapshot
cuts around a batch, and future-root preservation. Existing process-safety tests
were changed to inject the new controller root instead of a no-longer-active
physical Item root; their failure assertions remain.

The packaged server passed clean start and same-world restart. In the
cross-chunk Precision fixture, a saved, paused `2/20` process retained two
inputs, `1520 FE`, revision `4`, and no journal or applied marker after forced
termination and restart. Resuming completed exactly one observed batch; the
outputs and applied UUID stayed unchanged after a final restart. See the
[baseline summary](packaged-restart/prec04b/baseline/summary.json),
[Precision summary](packaged-restart/prec04b/precision/summary.json), and
[SHA-256 manifest](packaged-restart/prec04b/SHA256SUMS). The live-region checkpoint's ignored
combined build/DataGen/GameTest log is `.gradle/prec04b-live-region-final-check.log`, SHA-256
`7934f039aa0c6bf78dec63aa511a13701c42b378f2f26dfa42196b77c9df6601`.
That first packaged fixture used the initial artifact hash above, before
readback was added. The archived old-format world from the previous Precision
build was then copied to a disposable server, run with the readback artifact,
and restarted again. All seven legacy port Item roots remained unchanged and
inert, while the new controller root stayed `ACTIVE` with the exact Item stacks;
process revision `5`, applied transaction UUID and Energy `800 FE` were
unchanged. See the [old-world migration summary](packaged-restart/prec04b/legacy/summary.json)
and [filtered lifecycle](packaged-restart/prec04b/legacy/filtered-lifecycle.log).
Artifact hashes, not the base commit, identify the tested uncommitted builds.

After the live-region fix, the same archived old world was copied again to a
new disposable server. That checkpoint artifact passed both migration and a subsequent
same-world restart; all seven Item slots, inert legacy shadows, migration markers,
revision `5`, applied UUID and `800 FE` were preserved. See the
[live-region migration summary](packaged-restart/prec04b/legacy-live-region/summary.json)
and [filtered lifecycle](packaged-restart/prec04b/legacy-live-region/filtered-lifecycle.log).
The actual bounded command was:

```powershell
python scripts/run_v120_precision_legacy_migration_smoke.py build/dedicated-server-smoke/prec04b-live-region-legacy-20260926 --artifact build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar --evidence-dir docs/work/v1.2.0-precision-assembler/packaged-restart/prec04b/legacy-live-region --java 'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
```

The final save-retry artifact passed the same short migration/restart check on
another unchanged copy of the old world. Its seven Item slots, legacy shadows,
markers, process state and physical Energy were unchanged after restart. See the
[final-artifact summary](packaged-restart/prec04b/legacy-save-retry/summary.json)
and [filtered lifecycle](packaged-restart/prec04b/legacy-save-retry/filtered-lifecycle.log).
The command was:

```powershell
python scripts/run_v120_precision_legacy_migration_smoke.py build/dedicated-server-smoke/prec04b-save-retry-legacy-20260926 --artifact build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar --evidence-dir docs/work/v1.2.0-precision-assembler/packaged-restart/prec04b/legacy-save-retry --java 'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
```

## Live-region readback regression

An earlier reused-world run passed 100 GameTests, but a fresh-world run failed
`legacyPortItemsMoveToControllerOnceAndDoNotDropTwice`: the second migration
barrier reported a missing saved controller at `(2, -59, 155)`. Marking the
synthetic legacy test fixtures dirty was necessary, but did not resolve this
fresh-world failure. Diagnostics and a six-case reader test reproduced the
actual defect: the reader required the final allocated sector to be fully
padded, whereas vanilla `RegionFile` writes that padding only on close.

The reader now requires the complete record header and compressed payload,
within the allocated sector count and existing byte limits, without requiring
unused padding. Truncated records still fail closed. The regression failed
before this change and passed after it; the final combined command then passed
all 100 GameTests in a newly generated world. No timeout or assertion was relaxed.
The before/after unit logs are archived under
`.gradle/prec04b-live-region-before-clean-20260926/`:

- `prec04b-live-region-red.log`, SHA-256
  `871b7c001bb4605b29542227e8590fa0bb16a8f5979d256399a2c2f261b31a1d`;
- `prec04b-live-region-green.log`, SHA-256
  `75afb7890ae954e7b4285bd3366bab421ff3e66d9567680b499b8da77a213e6b`.

## Transient save-failure retry

Read-only review identified that a swallowed serialization exception clears
Minecraft's chunk dirty flag. Existing `PREPARING` snapshots and matching port
markers previously did not mark their chunks dirty on an explicit retry. Two
new GameTests reproduced this at the controller and port-marker barriers: both
stayed blocked after requeue, with no unrelated chunk mutation. Each validated
controller candidate now calls `setChanged()` before barrier A; matching Item
ports do so before barrier B even when their marker already exists. No polling
retry loop, schema change or Energy migration was introduced.

The first asynchronous fixtures detected shared-chunk changes and intervening
world saves, rather than establishing an isolated retry. Those failures remain
in the [filtered regression record](packaged-restart/prec04b/save-retry-regression.log).
The final fixtures keep all assertions, use four separate chunks with expiring
test tickets, and drive a local instance of the real manager synchronously.
They load the actual bounded pattern resource and never replace the runtime
manager or reset dirty flags. After the injected exception they require
`PREPARING`, no capability, a cleared dirty flag and no subsequent target-chunk
save before explicitly requeuing. The retry must activate and preserve all seven
Item slots and markers. Listener removal and local manager cleanup use `finally`.
These are disposable-world tests, not normal-world fixtures.

The final build and fresh-world run passed all 102 GameTests. The ignored full
log is `.gradle/prec04b-save-retry-synchronous-check.log`, SHA-256
`0799c3398cce2859987327ec8f0d23bfe55bfe8067c54678d8b527e99978d27d`.
The intentional save exceptions and readback refusals are expected observations,
not suppressed errors. Focused source review confirmed the retry correction and
fixture isolation; it did not constitute a full release audit.

## Remaining evidence and risks

The later [interrupted-migration removal check](PREC04B-REMOVAL-VERIFICATION.md)
records the new destruction hooks, retained/repaired bindings, ten added
GameTests, the shared-drop fixture failure and its exact-ledger correction.
That record distinguishes its artifact from the earlier save-retry artifact.

The subsequent [Energy-only binding correction](PREC04B-ENERGY-BINDING-VERIFICATION.md)
adds two reproduced-before/fixed-after GameTests, re-dirties the validated
Energy port before barrier B, and checks its saved binding/resource root before
activation. Its clean build passed 600 JUnit / 114 Required GameTests and wrote
no DataGen files. The new artifact also passed old-world migration and a normal
same-world restart; earlier artifact-specific records remain unchanged.

The [finite transaction recovery check](PREC04B-TRANSACTION-VERIFICATION.md)
then captured complete controller NBT at actual commit/recovery notifications.
It reproduced and corrected PREPARED recovery advancing resources before its
journal phase. Two recipe shapes, every seven-slot write prefix, revision/marker
boundaries, recovery-result reload, conservative no-journal handling and two
batches with marked inert shadows are now covered. The latest clean build and
independent GameTest rerun passed 119/119; 600 JUnit and normal packaged
old-world migration/restart also passed. This is not a new-process test at
each captured notification.

- The finite complete-controller NBT matrix `V120-PREC-04B-T` has passed.
  Together with the migration phase/save-failure cases it covers the bounded
  phase requirement, without claiming every physical I/O or power-loss cut.
  Independent evidence review identified direct Precision unload/load
  notifications as the final scoped item. The two short ACTIVE/PREPARING cases
  in [U](PREC04B-UNLOAD-VERIFICATION.md) now pass, including an independent
  reused-world rerun (121/121). They preserve resources/bindings and retire/
  renew capabilities; they do not physically evict chunks. The U artifact
  adds only four test classes; every prior JAR entry is byte-identical.
- Forge 47.4.10's `ChunkMap.save(ChunkAccess)` logs and swallows per-chunk
  serialization exceptions. The new readback blocks activation if required
  chunks do not match. Both save-event exception/retry cases now pass in GameTest;
  packaged writer-fault injection, physical I/O failure and power-loss/fsync
  remain unverified.
- Interrupted `PREPARING` removal now defers ordinary destruction, retains
  ownership across structural damage, and repairs supported saved binding cuts.
  Bound-unloaded-owner checks and missing-binding removal checks pass; the
  conservative unbound-search unavailable-candidate branch and all transforms
  are not independently exercised by the new removal tests.
- Item entities spawned by block removal and physical Energy transfers are not
  part of the controller-chunk batch atomicity claim. Power-loss/fsync behavior
  and the ADR-018-deferred full campaign have not been tested.

The finite ADR-019 acceptance evidence for `V120-PREC-04B` is now verified.
Parent runtime slice completion is recorded in the implementation log;
v1.2.0 Required Gates remain open. No release acceptance is inferred.

## ADR-019 evidence audit after transaction recovery

| Required evidence | Scoped review result |
|---|---|
| 1. Bounded NBT, old saves and migration barriers | Covered by codec/marker tests, both save-barrier fault cases, Energy binding disk checks and latest packaged migration/restart |
| 2. Transaction/migration phase snapshots without duplicate settlement or overwrite | Covered by the finite T matrix and existing migration cases; callback snapshots are not hardware I/O claims |
| 3. Removal, unload, re-form, capability epoch and cross-chunk behavior | Removal/re-form/retired views, cross-chunk migration and old-shadow restart covered; U adds independently rerun direct ACTIVE/PREPARING unload/load notifications |
| 4. Short packaged saved forced-stop/restart with hashes | Existing `ff6b4348…` S2 fixture retained; latest artifact has normal migration/restart regression, not a relabeled S2 result |

This independent read-only audit did not add a full fault-permutation campaign
or require rerunning the unchanged saved-pause S2 scenario for every internal
artifact. U completes the finite slice task; version Gates remain separate.
