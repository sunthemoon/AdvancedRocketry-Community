# Readiness-wait history source checkpoint

Date: 2026-10-07. Scope: observer-only diagnostic helper, its tests and one
existing service observation call. Physical transfer readiness and v1.8 Gates
are not repaired or completed by this checkpoint.

Root commits and normally pushes the four exact reviewed postimages at
`f43ecefb0a29a3286f6a67a495c36659d1b42a85`, then integrates them unchanged at
`046fc477ab80a9bc10d4170092bed6e0ad4e34ef`. The
[integration receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/transfer-wait-history-main-integration-20261007/INTEGRATION-01.json)
records normal remote equality, an empty index and unchanged/excluded AGENTS.md.
Fresh planning, accepted-ledger and committed whitespace checks exit 0.

## Implemented observation, not authority

`TransferFailureDiagnostics` retains the existing latest-prepared UUID slot,
adding three integers and one current-dispatch marker. An actual qualified wait
records exact signed first/last tick values and a call count saturating at
`Integer.MAX_VALUE`. New entry/completion invalidate the marker; preparation
replacement and clear reset history. Null/foreign/unprepared/undispatched/stale
calls cannot invent observations. Snapshot tokens remain `UNOBSERVED` without
the relevant tracked history; generic branch assignment alone supplies no count.

The sole service delta replaces the existing WAIT_ENTITY_READY recording call.
Its whole-file inverse reproduces the prior service exactly. Native readiness
getter order/short circuit, tickets, dispatch, return, pad, fuel, journal,
exceptions and original 270/1400 assertions/deadlines remain unchanged.
No per-tick logging, persistent/public ID, schema or world authority is added.
Counts describe observed calls, not distinct ticks, elapsed duration, uninterrupted
waiting, native I/O completion, ticket propagation or a uniquely corrective fix.
Direct native calls without manager dispatch intentionally have no wait history.

| Path | Bytes | SHA256 |
| --- | ---: | --- |
| main `rocket/server/TransferFailureDiagnostics.java` | 4,160 | `93611b34a1615706b852ccd1cf6abc0eb2c1d7743a09195293f4f47ec67b5000` |
| main `rocket/server/RocketTransferService.java` | 36,253 | `17aca9f4f5eb3e055021d99857949617eea5dcfdcc1bf9ecf4f508744159d4bd` |
| test `rocket/server/TransferFailureDiagnosticsTest.java` | 17,683 | `1c647a21ccc1650d4f2842c21be053615740fb66d0e0eb56e02aba6e1cc96e0d` |
| `docs/work/v1.8.0-transfer-wait-history/TASK-01.md` | 5,026 | `ccb2282807f1d733a176279ef06d2e457acc80203bb33d7755858e1ca48936d7` |

The Java paths are relative to the existing project package in their stated
main/test roots. New code is original MIT, not upstream/native source copying.

## Actual review and verification

The [independent actual-source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/transfer-wait-history-source-independent-review-20261007/REVIEW-01.md),
SHA256 `751163c4349e2a4aa883bd93aae9a4621e1f34415bb01009cd0b5b3950e68af0`,
finds no C/H/M/L in the observer-only scope. It proves the service inverse and
preserves the entire old test source by removing only the added block: all 19
prior tests are unchanged, with 11 appended subjects. One fresh real javac/Jupiter
cohort passes all 30/30, zero failures/aborts/skips/container failures. Its 95
finite associations are separate from test counts; 54 named inputs are unchanged.

Root's separate [published-commit replay](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-fixed-check-20261007-01/RESULT-01.json),
SHA256 `494e8a721d6a153b6960b035b9ea50bf273e34b53f0ee0cb0afd0e26ac16696b`,
binds all four Git objects at `f43ecefb`; actual javac/Jupiter exits 0/0, 30/30
passed, zero failure/abort/skip/container failure and 17 named inputs unchanged.
Only the actual stdlib helper/test, unchanged harness and six cached JUnit JARs
are compiled. The actual Service remains static-only in these direct runs.
Children use Java 17, 256 MiB/two CPUs, 120-second/1 MiB stream limits and empty
sourcepath; TEMP/home/classes are in their own D: leaves. There is no Bootstrap,
Minecraft/Forge dependency, installation, project build output or native launch.
Actual formatter checks preserve the existing printable ASCII/512-byte envelope.

The author's original metadata-helper IndentationError remains in its sealed
packet, corrected in a separate numbered file. The reviewer retains its own
parse-time helper failure and zero-deletion cleanup accounting failure; separately
authorized cleanup removes only its eight ended classes /31,790 logical bytes
and ten empty directories. Root's [separate own cleanup](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-owned-cleanup-20261007/CLEANUP-01.json)
also removes only eight ended classes /31,790 logical bytes and ten directories
after exact path/hash/alias/process/input checks. Raw streams/results/maps remain;
no C:, peer sealed output, source/cache or physical disk-reclaim claim is made.

## Current full-run state and remaining work

The [new exact-source workflow](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37554222038)
binds `046fc477`, run 37554222038 /attempt 1 /job 112576587243. Root's
[2026-10-07T00:52:58Z capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-transfer-wait-ci-observation-20261007-01/OBSERVATION-01.json)
observes **IN_PROGRESS /metadata only**; clean build is running, later steps
pending. No full unit count, GameTest result, new independent JAR or Service
execution result is inferred. The last completed [RESULT-19](../v1.8.0-ci/RESULT-19.md)
belongs only to `257e7b3a`: its two required failures remain, not a new-source result.

Use actual later failure history to investigate readiness without weakening
native predicates or deadlines. Physical machines, resource writers, dedicated
recovery, clients, performance, ledger closure, R-021 and G0-G9 remain unfinished.
Local full build/GameTest/native remains barred below 10 GB. v1.8 remains
IN_PROGRESS / IMPLEMENTING.
