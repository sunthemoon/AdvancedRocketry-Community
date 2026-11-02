# W268 - scratch-copy results

**Draft evidence only. No owner decision, human finding, Gate approval or central change is made.**

## Tested snapshots

- Full compatibility run: `9914e30da622457c2cd307502498e44d5f2e07d4`. All thirteen v1.8 import plans plus v0.1 verify, and all four requested Python modules, were actually run on this scratch snapshot.
- Final proposal/helper checks: `54155ba46919aac5e5fff46c566f58e7635a049e`. The committed `--quick` run checks first matches, all ledger modes, six proposal invariants in ordinary and optimized Python, and both repository validators. It is not a repeat of the import/module suites.
- The three central draft previews and the pasted Revision 7 section have identical bytes in the full-run and final snapshots. The later changes strengthen helper safety, immutable-reference scanning and local proposal tests; runtime source, resources and existing tests remain unchanged.

| Identical artifact | SHA-256 |
|---|---|
| `asset-plan.csv.draft` | `57d00e26439cfae077f5281bfc76aca158c06771605ea2e1dad658abbdc4a5ce` |
| `allowlist.txt.draft` | `aba84a60d93bf847e2a84ded5829c0447140d5a0902e6b69e13d41b0515e5f8d` |
| `origin-findings.json.draft` | `07589c80238d17a23bd3007a00e637c104ab2018fb266ef2447d5d5cae493164` |
| `pasted_revision_7_section` | `7536c3f675b1fe0ed498e0b885b333bf4d8088892ba0ae0c004a74d61b346b48` |

## First-match and scope results

Both corrected audits enumerate 898 legacy files: exactly 85 REVIEW files in 80 rules become EXCLUDE (with plan `-`); the other 813 effective file decisions and all owners/orders remain unchanged. The allowlist loses only those 85 entries, 247 -> 162. Before: 45 IMPORT / 95 REVIEW / 444 REGENERATE / 226 EXCLUDE / 88 IMPORTED. Draft after: 45 / 10 / 444 / 311 / 88. No tracked central file was modified.
All 95 source SHA-256 values were verified against pinned upstream Git blobs; no tracked src blob copies one of those hashes. None of the 95 files is a source in any current v1.8 import plan or provenance entry. The exhaustive reference evidence is in `REVIEW-EVIDENCE.json`, with deliberately over-inclusive lexical hits and explicit dynamic/unsupported-format limits.

## Actual commands and results

Every command below ran with `-X utf8 -B`. Full command vectors, elapsed times, per-log hashes and actual exit codes are retained in each external verification JSON. Nonzero results remain failures.

### Full compatibility

| Command | Exit | Observed result | Log SHA-256 |
|---|---:|---|---|
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_v180_content_ledger.py` | 0 | PASS; 653 units; 311 EXCLUDE, 45 IMPORT, 88 IMPORTED, 444 REGENERATE, 10 REVIEW | `27682aaf10c4333c98d92afc6312cec1257bdc5083e0261e29c7a081a58a8193` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_v180_content_ledger.py --require-accepted` | 1 | ledger: 417 rows depend on ADR-062, which is not ACCEPTED | `f8160ad8c0c41a62a01e65256a1dcf1eb35f95d6c9977e13849940aa630a255e` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_v180_content_ledger.py --closure` | 1 | ledger: 417 rows depend on ADR-062, which is not ACCEPTED; closure: 16 ledger rows are still PLANNED; closure: 10 assets are still handled as REVIEW | `c5224873a53ba9770b7a7359c808f7c12cc49f67e87c0b42e7aa69a63c6980d2` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-k1-plan.json --record docs/provenance/v1.8.0-c18d-k1.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 3 v1.8 targets and their record reproduce byte for byte | `2113788e94b25f905db2abc182dbd1233841360d9e63d69218adab808559d59b` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-k2-plan.json --record docs/provenance/v1.8.0-c18d-k2.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 12 v1.8 targets and their record reproduce byte for byte | `c330e9b0e728949a08413164554878563694339e0657a6153eccba1c256c89c2` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-k3-plan.json --record docs/provenance/v1.8.0-c18d-k3.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 12 v1.8 targets and their record reproduce byte for byte | `c330e9b0e728949a08413164554878563694339e0657a6153eccba1c256c89c2` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-k4-plan.json --record docs/provenance/v1.8.0-c18d-k4.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 18 v1.8 targets and their record reproduce byte for byte | `03dd6849cdd0d9a41720aeee6ca31ad60b0c61470da11920ffa14eea76a9d82d` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-k6-plan.json --record docs/provenance/v1.8.0-c18d-k6.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 6 v1.8 targets and their record reproduce byte for byte | `0c75e4ca5a72693c7d05bf9b24f5e494d8797b0934ec1e433b02a87ef761b2bb` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w244-plan.json --record docs/provenance/v1.8.0-c18d-w244.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 4 v1.8 targets and their record reproduce byte for byte | `988380155f0a8f2bf1bf96e442832f8781c44c09f3cc648318b6105db538ac84` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w246-plan.json --record docs/provenance/v1.8.0-w246-flat-textures.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 16 v1.8 targets and their record reproduce byte for byte | `7b9e6ec208004c15187282431577ac5f5b08dae06aad86d61783061f3bc11a41` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w249-plan.json --record docs/provenance/v1.8.0-w249-motor-models.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 6 v1.8 targets and their record reproduce byte for byte | `0c75e4ca5a72693c7d05bf9b24f5e494d8797b0934ec1e433b02a87ef761b2bb` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w255-plan.json --record docs/provenance/v1.8.0-w255-bipropellant-motor-models.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 6 v1.8 targets and their record reproduce byte for byte | `0c75e4ca5a72693c7d05bf9b24f5e494d8797b0934ec1e433b02a87ef761b2bb` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w256-plan.json --record docs/provenance/v1.8.0-w256-saw-model.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 4 v1.8 targets and their record reproduce byte for byte | `988380155f0a8f2bf1bf96e442832f8781c44c09f3cc648318b6105db538ac84` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w258-plan.json --record docs/provenance/v1.8.0-w258-nuclear-motor.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 3 v1.8 targets and their record reproduce byte for byte | `2113788e94b25f905db2abc182dbd1233841360d9e63d69218adab808559d59b` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w260-plan.json --record docs/provenance/v1.8.0-w260-fuel-tanks.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 3 v1.8 targets and their record reproduce byte for byte | `2113788e94b25f905db2abc182dbd1233841360d9e63d69218adab808559d59b` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v180_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream --plan tools/import/v180-w261-plan.json --record docs/provenance/v1.8.0-w261-landing-pad.json --vanilla 1.12.2=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.12.2.jar --vanilla 1.20.1=D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\clients\client-1.20.1.jar` | 0 | [PASS] 1 v1.8 targets and their record reproduce byte for byte | `f314c529f541ff7b1c6b534a7a70e965f7195f0caa15a4249eafd9fc63bbdd8d` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B tools/import/import_v010_assets.py verify --upstream D:\GitHub\ARCE-Task-Evidence\root-queue\W229\scratch\upstream` | 0 | [PASS] 10 v0.1.0 imported targets match exact upstream bytes and transforms | `8393d1ee7b9843031f0c3781a7036923365e006865b468c04bea2fad17f6d44b` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B -m unittest tests.test_import_v180_assets` | 0 | Ran 63 tests in 73.703s; OK | `f82e16a6794e401efff636d9b85956d94f81003730952d76bf02a84a58042e02` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B -m unittest tests.test_validate_v180_content_ledger` | 1 | Ran 58 tests in 270.701s; FAILED (failures=4) | `2ee760f72271196d0f018a9246fc274722e2b9948b26c9761befaa421986f14e` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B -m unittest tests.test_validate_v180_provenance` | 0 | Ran 32 tests in 58.015s; OK | `43c38ad10ed9f2af8e8341a381f0e79d60687c7a8ccb7824c414bce8b3eb4754` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B -m unittest tests.test_validate_v090_resources` | 0 | Ran 13 tests in 0.853s; OK | `be979f41fd46056e15b64847d06e0e28be4c97380a95235ccae28bcebbd81649` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_repository.py --require-approved-identity` | 0 | Summary: 45 passed, 0 pending, 0 warnings, 0 failed | `43a0ed0db3ffac83548432bf992432e4e6c2e3b5c296d0fb5e5fc09c33056cd5` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_repository.py --require-approved-identity` | 0 | Summary: 45 passed, 0 pending, 0 warnings, 0 failed | `fcda491ebb4becf089e0cfdefc4907b707616a81520238442d5fad386dc910e2` |

### Final committed supplemental checks

| Command | Exit | Observed result | Log SHA-256 |
|---|---:|---|---|
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_v180_content_ledger.py` | 0 | PASS; 653 units; 311 EXCLUDE, 45 IMPORT, 88 IMPORTED, 444 REGENERATE, 10 REVIEW | `27682aaf10c4333c98d92afc6312cec1257bdc5083e0261e29c7a081a58a8193` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_v180_content_ledger.py --require-accepted` | 1 | ledger: 417 rows depend on ADR-062, which is not ACCEPTED | `f8160ad8c0c41a62a01e65256a1dcf1eb35f95d6c9977e13849940aa630a255e` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_v180_content_ledger.py --closure` | 1 | ledger: 417 rows depend on ADR-062, which is not ACCEPTED; closure: 16 ledger rows are still PLANNED; closure: 10 assets are still handled as REVIEW | `c5224873a53ba9770b7a7359c808f7c12cc49f67e87c0b42e7aa69a63c6980d2` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B docs/work/v1.8.0-w268-review-closure/test_proposal.py` | 0 | Ran 6 tests in 0.093s; OK | `dbad59b74d0c9cd5f166f946dd7975a83354f16d255d7e705c0fa4e3d5a04a8c` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B -O docs/work/v1.8.0-w268-review-closure/test_proposal.py` | 0 | Ran 6 tests in 0.092s; OK | `97359266d68cba7df69a1c879a71dd9104c234a85b88223cc713bc77f66449ed` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_repository.py --require-approved-identity` | 0 | Summary: 45 passed, 0 pending, 0 warnings, 0 failed | `b095b2ec1bc44975746a32446f3fb35111a6fa5579cec771f85cd3b49f25e5e1` |
| `D:\python\pyenv\pyenv-win\versions\3.13.15\python.exe -X utf8 -B scripts/validate_repository.py --require-approved-identity` | 0 | Summary: 45 passed, 0 pending, 0 warnings, 0 failed | `e8029b940d5ed1f9a3bdb0b8f2cefa174669c46a452bbb7737b2bc5af98ae2c0` |

## Four ledger-suite failures remain failures

The importer passes 63 tests, provenance passes 32, and resources pass 13. The draft-applied ledger suite runs 58 tests: 54 pass and four fail. No existing test or assertion was edited.

| Failed test | Evidence-based cause |
|---|---|
| `test_excluded_finding_forces_exclusion` | The fixture assumes beacon.png is still REVIEW; the proposal already excludes it, so the expected contradiction is absent. |
| `test_import_for_a_deferred_unit_fails` | The fixture expects REVIEW handling after changing beacon.png owners; its proposed handling is EXCLUDE. |
| `test_require_accepted_reports_proposed_adrs` | The fixture expects the copied ADR baseline to contain no proposed dependency; scratch revision 7 is deliberately PROPOSED, with 417 dependent rows. |
| `test_review_needs_a_cleared_finding_to_import` | The fixture assumes the old beacon REVIEW ceiling/batch. The draft removes its allowlist entry and sets plan `-`; actual errors cite ceiling none, the exclusion and inherited controlpanel finding. |

The same four unmodified tests were rerun on the actual, unapplied branch at `54155ba46919aac5e5fff46c566f58e7635a049e`: all four pass. This does not claim a branch-wide 58-test ledger rerun. Before adoption, Root must deliberately rebase the fixture setup to the accepted asset baseline and rerun, preserving the security assertions. This task does not do that or pretend the draft suite is green.

## What closure still lists

`--require-accepted` fails because 417 ledger rows depend on the deliberately PROPOSED ADR-062 revision 7. `--closure` also lists 16 PLANNED ledger rows and ten REVIEW assets. The repository validator is a separate check: it passes all 45 checks in both branch and scratch; that does not override closure.

| PLANNED unit | Batch | Target |
|---|---|---|
| `block:oxygenCharger` | C18a | gas charge pad |
| `block_variant:loader/1` | C17c | rocket satellite bay |
| `block_variant:loader/2` | C17a | rocket item unloader |
| `block_variant:loader/3` | C17a | rocket item loader |
| `block_variant:loader/4` | C17a | rocket fluid unloader |
| `block_variant:loader/5` | C17a | rocket fluid loader |
| `sound_event:airHissLoop` | C18d | air hiss sound |
| `sound_event:basicLaserGun` | C18d | laser gun sound |
| `sound_event:combustionRocket` | C18d | rocket engine sound |
| `sound_event:ElectricShockSmall` | C18d | electric shock sound |
| `sound_event:gravityOhhh` | C18d | gravity controller sound |
| `sound_event:laserDrill` | C18d | laser drill sound |
| `sound_event:lathe` | C18d | lathe sound |
| `sound_event:MachineLarge` | C18d | large machine sound |
| `sound_event:railgunBang` | C18d | railgun sound |
| `sound_event:rollingMachine` | C18d | rolling machine sound |

The retained REVIEW assets are:

- `sounds/airhissloop.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/basiclasergun.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/combustionrocket.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/electricshocksmall.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/gravityohhh.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/laserdrill.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/lathe.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/machinelarge.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/railgunbang.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.
- `sounds/rollingmachine.ogg`: owner D5 in-use listen and a per-file retain/replace/exclude decision are still required.

## Earlier development attempts and cleanup

- The first builder attempt found a source-hash mismatch for motor.mtl because Windows Git archive conversion used CRLF. Git reads/archives now explicitly disable core.autocrlf, and source comparisons use immutable blobs.
- `verification-01` stopped before commands: a historical JUnit pathname was 261 characters under the first export name. Its owned export was removed by finally. Subsequent short sibling export paths avoid that limit.
- `verification-02` retains the invalid-draft results: the three ledger modes failed on 80 EXCLUDE batch markers, and its ledger module failed 9 of 58. Imports and the other modules still completed. The fix sets excluded plan fields to `-`; nothing rewrites the failed logs.
- A command to stop verification-02 and remove its export was refused before execution. It was not retried through another mechanism. That bounded runner continued to its original finally block and removed its own export normally.
- The earlier `final-quick` development run passed, but the authoritative final supplemental run is `final-quick-committed`, after the helper fixture correction was committed. All completed export runs report scratch_removed true. No upstream cache, other worktree, source world or other agent process was removed.

## Evidence locations and checksums

- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/verification-03/verification.json`; SHA-256 `6062c4bbd821152acb1b65d5421fd597886d1b9d76f153c54db5af5956872913`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/verification-03/first-match-audit.json`; SHA-256 `4d437e138638dbfdd439e3b57b976fcbd92fd65f7b1a2b4f080519405e21d612`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/final-quick-committed/verification.json`; SHA-256 `26dc50cd720e3ce279c539cd8d94d2ed135fdc18c66e8f9fad2f7525d1e64c10`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/final-quick-committed/first-match-audit.json`; SHA-256 `bc73f17814daba5eded446cbd745bcc1d5ae4f6d5c9a5133733c09e01a847686`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/verification-02/verification.json`; SHA-256 `e15ddbc330c0d3e315c625bc1a08ad9272a8dc89a74d74c474343b0d387ce908`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/verification-01/verification.json`; SHA-256 `82073bdca64ba6a4420e050db2bfc9a4e6b194ece1e46a85df328b92604e4035`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/branch-ledger-four.json`; SHA-256 `256a849d6fcd97448d73c7e7060e5309913bece98040f17bb7d9efe90055bdc2`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/branch-ledger-four.log`; SHA-256 `837a5bc29805aa79d7c4a856d1fbfa5b7a83ca5a054e8cdc418baec7fb2ae0da`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/draft-identity-comparison.json`; SHA-256 `9eebe98e2ee3ac0bb4f330daaf635e56d11e0f9068de97569cc04c0424d46e7b`.
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/scope-audit.json`; SHA-256 `2796f1fd9a94d904a1a712a03c1fabb227a8811515252c550cdce76406c64da4`.

No clean build, runData, GameTest, Java unit suite, native restart, JAR, D5 listen, D7 review, V1 or V2 was run: this task changes no runtime source/resource and explicitly requests Python/scratch verification. No v1.8 Required Gate is passed. Root and the other engine review the draft; the owner alone decides revision 7.
