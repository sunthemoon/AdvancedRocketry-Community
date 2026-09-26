# V120-PREC-04B — interrupted-migration removal

```yaml
status: IN_PROGRESS
date: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
uncommitted_worktree: true
artifact_sha256: 6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a
decision: ADR-019
```

## Implemented behavior

- Pending or unsupported controller ownership prevents ordinary player removal,
  explosion loot/removal and destruction through Forge's entity hook.
- A bound port uses the recorded controller position, not a formed-machine
  capability lookup. Unavailable owners defer removal without chunk loading.
- Unbound ports with legacy Items or markers inspect at most 40 fixed-layout
  prospective controller positions. Unavailable candidate chunks conservatively
  defer removal. Empty unmarked facades remain replaceable.
- Structural mismatch and missing definitions retain pending bindings and part
  positions in schema-valid `BINDING_CONFLICT`; unloading retains
  `WAITING_UNLOADED`. Repairing the structure permits migration to continue.
- Recovery preflights all eight fixed ports before restoring older/missing
  same-owner bindings to the existing controller generation. Foreign/newer
  bindings, conflicting shadows/markers, unsupported data and missing chunks
  cannot be adopted. No Items, markers or persistent identities change during
  binding repair.
- Untouched legacy and completed `ACTIVE` controllers retain normal removal.
  Inert old port shadows do not drop again. A bilingual player message explains
  loading/repairing the machine and unsupported-save compatibility.

## Files and responsibility review

Production changes are in `PrecisionAssemblerRemovalPolicy`,
`PrecisionAssemblerMigrationBindings`, controller lifecycle handling, the two
block adapters, `PrecisionAssemblerServerEvents`, and
`V120MachineLanguageProvider` plus its two generated language files.
`PrecisionAssemblerRemovalGameTests` adds ten tests; two existing migration
fixture helpers gained package visibility for reuse without weakening their
assertions. The 513-line controller adapter was reviewed under the 500-line
check: process logic, resource codecs, binding repair and removal lookup remain
separate classes; this change adds lifecycle coordination, not another domain
engine. No class exceeds 800 lines and no upstream assets were imported.

## Bounded checks

| Command/check | Observed result |
|---|---|
| `gradlew compileJava --offline --no-daemon` | PASS |
| `gradlew runData --offline --no-daemon` | PASS; two language files written |
| `gradlew clean build runData runGameTestServer --offline --no-daemon` before the binding-cut follow-up | PASS; 600 JUnit tests, fresh-world 109/109 Required GameTests; second DataGen wrote 0 files |
| `gradlew build runGameTestServer --offline --no-daemon` with binding-cut additions | Build PASS; GameTest FAIL, 110/112, on two fixture-wide empty-drop assertions |
| Same command with an exact before/after no-drops ledger | PASS; 600 JUnit, fresh-world 112/112 Required GameTests; pre-existing entity counts 1 and 2 remained unchanged |
| Same command with exact ledgers for both refused and successful removals | PASS in 1m 43s; 600 JUnit tests, no failures/errors/skips; fresh-world 112/112 Required GameTests |
| Final packaged migration/restart | PASS; archived old-world migration and same-world restart retained all seven Item slots/shadows/markers, process state and 800 FE |
| `python scripts/validate_v120_machine_resources.py` | PASS; 9 machine blocks and bilingual resources |
| `python scripts/validate_v1plus_planning.py` | PASS; 11 plans, 33 source inputs |
| `git diff --check` | PASS |
| `git diff --exit-code` / `git diff --exit-code -- src/generated` | Both exit 1 as expected: implementation is uncommitted; generated changes are the two intended language entries, not DataGen drift |

The new tests use `FakePlayer.gameMode.destroyBlock` for survival/creative
server-side removal, real explosion finalization with a fixed affected-block
list, and the entity-destruction predicate (not Wither AI). They cover all eight
prefix marker counts from 0 through 7, structural repair, missing definitions,
unavailable bound owners, legacy/active removal, future resource/identity roots,
saved missing/older bindings, and four conflicting-binding preflight cases.

The first 112-test run retained the blocks, rejected player removal and preserved
NBT, but two final assertions required the entire nearby world to contain no
Item entities. Templates share an entity region with other tests. The corrected
oracle snapshots every nearby Item entity UUID and full ItemStack NBT before
the synchronous removal actions and requires exact equality afterward. It
still detects every new resource/block drop or changed stack; it does not
discard entities, relax the resource ledger or increase a timeout. Diagnostic
output records unchanged pre-existing entity counts. Both failed observations
remain part of the evidence.

Successful-removal cases also preserve each pre-existing UUID/ItemStack and
require exactly two newly dropped iron, two redstone, and (for the explosion
control) one controller block item. Thus unrelated prior drops cannot mask a
missing result in the positive controls either.

Ignored full logs:

- `.gradle/prec04b-removal-check.log`, SHA-256
  `dcf376f265ff2fa524153b32e47bcac0d88daca12861c261df0f2add8236d231`.
- `.gradle/prec04b-removal-recovery-check.log`, SHA-256
  `6ec7ae31b0cbaa3185f2a7c15e93a8a0dca0652299e58bae474e439463426fb4`.
- `.gradle/prec04b-removal-final-check.log`, SHA-256
  `caf9294d5c6e4ed577cbc467849e05ec9b679afc02fb709b4d47ecfe409fde9e`.
- `.gradle/prec04b-removal-ledger-check.log`, SHA-256
  `691bd3386f567d5ff84644739ecab2c133bac24f99ad523f8115c971fefccd3a`.

The [filtered regression record](packaged-restart/prec04b/removal-regression.log)
retains all four runs and the two failed assertions. The full repository validator
was not repeated for this follow-up; its last actual 45-check pass remains the
live-region checkpoint in the parent record, not a new acceptance claim.

Independent read-only source review found and helped correct binding-cut and
unsupported-identity gaps. It reported no further concrete defect after the
bounded unbound-owner lookup was added; it did not execute a separate release
test campaign.

## Final packaged artifact

An unchanged copy of the archived old-format full server/world was started with
the final artifact and cleanly restarted once. This tests real saved migration
and same-world reload, not a packaged explosion/player simulation or a hardware
power-loss guarantee. The [summary](packaged-restart/prec04b/legacy-removal/summary.json)
and [filtered lifecycle](packaged-restart/prec04b/legacy-removal/filtered-lifecycle.log)
bind the result to the artifact hash above. All evidence files are listed in the
[SHA-256 manifest](packaged-restart/prec04b/SHA256SUMS).

```powershell
python scripts/run_v120_precision_legacy_migration_smoke.py build/dedicated-server-smoke/prec04b-removal-legacy-20260926 --artifact build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar --evidence-dir docs/work/v1.2.0-precision-assembler/packaged-restart/prec04b/legacy-removal --java 'C:\Program Files\Java\jdk-17.0.7\bin\java.exe'
```

## Boundaries and remaining work

Direct administrative/third-party world replacement and Enderman-holdable
datapack overrides bypass the supported removal hooks. Item-entity crash
atomicity and physical Energy transfers remain outside ADR-019's Item-batch
claim. The conservative lookup can temporarily protect orphan ports near an
unloaded chunk. Its unavailable-candidate branch and every transform are not
individually exercised by this test addition; the existing bound-owner test
does check no forced load. All saved-chunk fault cuts, packaged writer faults
and the ADR-018 full campaign remain incomplete.

This is a bounded implementation check, not version acceptance. See the
[parent verification](PREC04B-VERIFICATION.md) and
[implementation log](../v1.2.0-implementation-log.md). Required Gates remain open.
