# v1.8.0 test report — C6 draft

Snapshot: 2026-11-21. Candidate C6 is commit
`c8fa16e16be87554f26c402546733df6555d15e6`, host JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
[C6 identity record](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md) §2.1
pins regression **44770334930, attempt 1**, and governance **44770334865,
attempt 1** under R581. No Gate, requirement, native row or release is approved.

## Current C6 hosted receipts

| Check | C6 observed identity/result | Receipt and boundary |
|---|---|---|
| Hosted build and units | Build 14.7 minutes; 6,766 JUnit tests in 1,193 XML suites, 0 failures, 0 errors, 0 skipped | C6 build ZIP below; XML totals read without a JVM |
| DataGen | First/repeat generated diffs empty; both status logs explicitly PASS clean, including untracked files | `datagen-diff.log`, `datagen-status.log` and repeated counterparts; status logs are not empty |
| Artifact/package | Host JAR 16,026,312 bytes, 7,013 entries; artifact hash matches the candidate | `artifact-sha256.txt`, `artifact-audit.log`; metadata/notices/path/credential scan passed |
| Client/provenance/ledger/planning | Source/bytecode/provenance audits passed; ordinary ledger 653 units, five PLANNED rows; planning passed | Existing C6 build receipts inspected read-only; ordinary validation is not strict closure |
| Hosted GameTests | 1,886 tests across four shards; shard durations 15.3 / 21.6 / 22.2 / 16.1 minutes | R581 metadata, not a W423 rerun or an independently inspected C6 union archive |
| Shard overloads | 4 / 4 / 4 / 2, each within the unchanged maximum 8 | R581; no limit or log expectation changed |
| Governance | Run 44770334865 attempt 1 | R581; not release approval |

The build ZIP is
`D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/v180-build-evidence-c8fa16e16be87554f26c402546733df6555d15e6-1.zip`,
SHA-256 `d189bea1278f5b4672708fcd6fab20767703803c51da5d655916230d1b0b50e8`.
Its `tested-commit.txt` equals C6, and `artifact-sha256.txt` pins the JAR above.
The hosted JAR archive SHA-256 is
`7479d237518d8397a5eaab9d6d87d46c085c1d692fc3a0a15ac9a9c44f5f61b7`.
C6 union-archive retention and detailed per-shard receipt reconciliation remain
PENDING; none of C5's shard counts, runtime hashes or archive hashes below are
relabelled C6. Native, X01/X02, client and reference results are PENDING.

## Retained historical C5 receipts

The following snapshot and its observations are retained as C5 history, not
current-candidate results. C5's eight-family pre-run later passed (2026-11-20,
14:13–21:21, R566); its full campaign and X01/X02 did not run on C5.

Snapshot: 2026-11-20. Automated results below belong only to C5, commit
`9172113ad4ecfb9047432f02b97d240fc06130b6`, host JAR SHA-256
`e5cd2beb4a38b4ea741c5103b18ba224d3f102ef6231adbc3a0849ad4f1e35c8`.
No Gate, requirement or release is approved by this report. The
[C5 identity record](../../work/v1.8.0-root-queue/records/C5-SWITCH-REPORT.md) §2.1
pins hosted regression **44681973284, attempt 1**, and governance
**44681973374, attempt 1**. Later branch runs are not substituted for C5.

## Hosted automated evidence already available

| Check | C5 observed result | Receipt |
|---|---|---|
| Java 17 clean build and fresh unit suite | Hosted build succeeded, about 14.2 minutes; 6,751 tests in 1,191 JUnit XML suites, 0 failures, 0 errors, 0 skipped | Build evidence ZIP: `build.log`, `build/test-results/test/*.xml`; Java Temurin 17.0.21+9, Minecraft 1.20.1, Forge 47.4.10; wrapper Gradle 8.8 |
| DataGen | First and repeat generation succeeded; both generated diff and status logs empty | `data.log`, `repeat-data.log`, `datagen-diff.log`, `datagen-status.log`, repeated counterparts |
| Artifact/package | Host JAR 15,946,403 bytes, 6,994 entries; metadata/notices/path/credential scan passed | `artifact-audit.log`, `artifact-sha256.txt`, `jar-content-manifest.json` |
| Common/client boundaries | Source import scan and packaged bytecode checks succeeded | `client-boundary.log`, `client-bytecode.log`, `client-bytecode.json` |
| Provenance and ledger | Packaged provenance audit succeeded; ordinary ledger validation reports 653 units and 5 PLANNED rows | `provenance-audit.log`, `provenance/`, `ledger.log`; ordinary validation is not `--closure` |
| Planning/governance | Hosted governance succeeded; planning receipt says 11 version plans, explicit G0–G9 and 33-input inventory | Governance run above; build evidence `planning.log`; source [QUEUE R555](../../work/v1.8.0-root-queue/QUEUE.md) |

### GameTest shards

The archived `union.json` and `union.log` require the exact executed union, not
an addition of unrelated runs: **1,867 tests across 807 batches**. All four
jobs and the union succeeded. Bound runtime SHA-256:
`bd41f2c952c465f54bb86a18640f70538f3131facaaf94a2274850d129b16227`.

| Shard | Required tests completed | Hosted duration (minutes, recorded approximation) | server-overloaded / maximum | FATAL / ERROR / WARN | Log expectations |
|---|---:|---:|---|---|---|
| 0 | 401 | 15.0 | 3 / 8 | 0 / 30 / 322 | All matched 88 declared expectations |
| 1 | 639 | 21.2 | 5 / 8 | 0 / 44 / 251 | All matched 112 declared expectations |
| 2 | 401 | 19.1 | 1 / 8 | 0 / 28 / 210 | All matched 75 declared expectations |
| 3 | 426 | 18.0 | 3 / 8 | 0 / 46 / 235 | All matched 89 declared expectations |
| Union | 1,867 | Not a serial timing total | 12 total; ceiling is per shard | 0 FATAL | No undeclared log entry accepted |

Deliberate injected ERROR/WARN evidence follows
[ADR-072](../../decisions/ADR-072-V180-GAMETEST-LOG-EXPECTATIONS.md), not the
ordinary packaged-server log policy. Its development allowances and the
overload exception are not blanket release waivers. Candidate audit must check
their expiry; product errors and unexplained repeated warnings still fail
native/client acceptance.

### Archive identity and inspection boundary

The build ZIP was read in place, without starting a JVM:
`D:/GitHub/ARCE-Task-Evidence/root-queue/C5/candidate/v180-build-evidence-9172113ad4ecfb9047432f02b97d240fc06130b6-1.zip`,
SHA-256 `dd324128db3bb0d76c44c0adb5d01147ae373d33398734a692081ac4109b6621`.
Its `tested-commit.txt` equals C5. JUnit counts above are summed from its XML,
not inferred from annotations or a historical unit count.

The aggregate artifact `v180-regression-9172113ad4ecfb9047432f02b97d240fc06130b6-1`
of run 44681973284 was read in memory through its public nightly.link archive;
ZIP SHA-256 `c35dbf152028951a57048c002c4cf2a2edba11055fb2a4bf0dcf5fd8aeaf731c`.
`union.json` and `union.log` supply the shard counts and log verdicts. Archive
retention/storage in the final evidence index is PENDING; no `build/` or Temp
location is proposed as durable evidence. The host JAR archive has SHA-256
`4283a9ac371604985dc5e69c5d62f1c2c32eef32dfffe2130f1110ff8552c491`.
These inspections verify existing receipts; they are not a local rerun.

## C6 native families — every result PENDING

Names and selected case counts below are read from the tracked
[campaign driver](../../../scripts/run_v180_integrated_campaign.py) `family_specs()`.
A wrapper case can execute many cuts/starts; counts are not pass counts. The
full selection is 78 families. C5's eight-family pre-run is historical partial evidence,
not a substitute for C6's full run. See [C6 report](../../work/v1.8.0-root-queue/records/C6-SWITCH-REPORT.md)
§§3–13 and [fuel inventory](../../work/v1.8.0-root-queue/records/FUEL-ADMISSION-REPORT.md)
§3.2 for exact matrix rows, cases, exclusions and risks.

| Family | Selected cases | C6 result |
|---|---:|---|
| forced-A | 14 | PENDING |
| forced-A-retry | 4 | PENDING |
| released | 4 | PENDING |
| exit | 11 | PENDING |
| forced-B | 18 | PENDING |
| forced-CD | 15 | PENDING |
| forced-E | 14 | PENDING |
| forced-E-retry | 9 | PENDING |
| forced-F | 5 | PENDING |
| forced-G | 2 | PENDING |
| forced-GH-retry | 2 | PENDING |
| forced-H | 1 | PENDING |
| forced-W331-T | 3 | PENDING |
| forced-W331-W | 4 | PENDING |
| forced-W331-D | 6 | PENDING |
| forced-W331-G | 3 | PENDING |
| forced-W331-R | 1 | PENDING |
| forced-W331-L | 1 | PENDING |
| forced-W331-U | 1 | PENDING |
| cargo-C-S1 | 28 | PENDING |
| cargo-C-S2 | 16 | PENDING |
| cargo-C-S3 | 10 | PENDING |
| cargo-C-S4 | 20 | PENDING |
| cargo-C-S4-LOAD | 20 | PENDING |
| cargo-C-S5 | 16 | PENDING |
| cargo-D-S1 | 12 | PENDING |
| cargo-D-S2 | 25 | PENDING |
| cargo-D-S2-LOAD | 20 | PENDING |
| cargo-C-Q | 4 | PENDING |
| cargo-B3-S1-REUSE | 7 | PENDING |
| cargo-B3-S4-REUSE | 15 | PENDING |
| cargo-B3-S5-REUSE | 8 | PENDING |
| cargo-B3-S6-REUSE | 96 | PENDING |
| cargo-G2-REUSE | 1 wrapper | PENDING |
| w336-timing-B12_D4 | 1 | PENDING |
| w336-timing-B12_D7 | 1 | PENDING |
| w336-timing-B13_FAILED | 1 | PENDING |
| w336-timing-B13_UNKNOWN | 1 | PENDING |
| w336-timing-B16_MOTION | 1 | PENDING |
| w336-timing-B10_BLOCKED | 1 | PENDING |
| w336-timing-RECONNECT | 1 | PENDING |
| w336-timing-TURNS-2 | 1 | PENDING |
| w336-timing-TURNS-3 | 1 | PENDING |
| w336-timing-D6_RETRY | 1 | PENDING |
| w336-saturation-bay | 1 | PENDING |
| w336-saturation-item-load | 1 | PENDING |
| w336-saturation-item-unload | 1 | PENDING |
| w336-saturation-fluid-load | 1 | PENDING |
| w336-saturation-fluid-unload | 1 | PENDING |
| w336-saturation-fuel | 1 | PENDING |
| w336-downgrade | 1 wrapper | PENDING |
| r042-N_BAY | 1 | PENDING |
| r042-L_LEDGER | 1 | PENDING |
| r042-P_SERVER | 1 | PENDING |
| r042-P_OWNER | 1 | PENDING |
| r042-U_BAY | 1 | PENDING |
| r042-U_LEDGER | 1 | PENDING |
| forced-W346-R | 1 | PENDING |
| forced-W358-B10-INCOHERENT | 1 | PENDING |
| forced-W358-B11-REARM | 1 | PENDING |
| forced-W358-B11-ESCROW-MAJOR | 1 | PENDING |
| forced-W358-B11-BAY-MAJOR | 1 | PENDING |
| forced-W358-B11-UNKNOWN | 1 | PENDING |
| forced-W358-CAP-OWNER | 1 | PENDING |
| forced-W363-B6-GOVERNED | 1 | PENDING |
| forced-W363-B17-UNLOADED | 1 | PENDING |
| forced-W363-B17-UNLOADED-RESTART | 1 | PENDING |
| r042-X_BAY | 1 | PENDING |
| r042-X_LEDGER | 1 | PENDING |
| r042-R031_FUTURE | 1 | PENDING |
| r042-R031_INVALID | 1 | PENDING |
| cargo-X03-REPAIR | 1 wrapper, both world origins | PENDING |
| accounting-cuts-cargo-load | 17 | PENDING |
| accounting-cuts-cargo-unload | 17 | PENDING |
| accounting-cuts-loader | 28 | PENDING |
| accounting-outcomes | 18 | PENDING |
| accounting-root2-upgrade | 4 | PENDING |
| accounting-root3-old-jar | 1 | PENDING |

Separate from those families: X01 S3 rows X01-01–X01-12, X02 S5 rows
X02-F1–X02-F10 across the four selected writers, their real offline group and
stable differing-vector readback are **PENDING**. E4 application-level replay
and injection do not prove physical power-loss/OS-crash durability. Ordinary
loader F9 and bay B11.2/B17.5 retain their precise non-coverage arguments; the
historical downgrade failures remain failures and cannot become passed rows
through a signature. Other older loader cuts required by the fuel inventory
must be reconciled rather than assumed included in these 78 wrappers.

## Campaign results by row

All **117** row results remain PENDING in this snapshot. The tables in
`MANUAL-TEST.md` enumerate A01–J29 individually. Their authoritative steps and
phase/level assignments are the [plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md)
and [checklists A](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-A.md),
[B](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-B.md),
[C](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-C.md),
[D](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-D.md),
[E](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-E.md),
[F](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-F.md),
[G](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-G.md),
[H](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-H.md),
[I](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-I.md),
[J](../../work/v1.8.0-root-queue/records/CAMPAIGN-CHECKLISTS-J.md).
C05/H04 carry the planned NOT_COVERED disposition under O6/D9; their result
records must still explicitly name the reason. No fake-player or software-GPU
run can fill a V1/V2 cell.
