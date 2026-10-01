# v1.6 F01 -- missing-account recovery bounds

Date: 2026-10-01. Status: IN_PROGRESS. No release Gate is approved.
Base commit: `ab10fb53a580e53a9a1c24097a487f2a7c54ad52`.
Branch: `fix/v1.6.0-recovery-account-bounds`.

## Input and scope

External audit: `D:/GitHub/test_record/ARCE_v1.3-v1.6_深度测试与问题清单_2026-10-01.md`
and its adjacent evidence directory. F01 concerns a loader repair that bypasses
the fixed research-account count. The audit's F02 is a separate, known
three-store reward conservation risk; its model result is not a real-player
S2 reproduction. F03--F07 concern consumer configuration, historical fixtures
and evidence integrity. None is declared repaired by this task.

No upstream code or asset is imported. Schema, IDs, protocol and published
limits stay unchanged. The original v1.7 worktree is not modified.

## Design and changed files

`AccountRecovery` computes the unique missing-owner set and checks the entire
addition against the unchanged 4,096-account bound and account-section byte
budget **before any account/reservation mutation**. An inadmissible repair
throws into the existing fail-closed loader: original payload retained, no dirty
state, no automatic overwrite. It does not delete accounts or loosen admission.

`SatelliteRegistryPayload` shares its encoder with `SatelliteMissionSavedData`:
an invariant repair must also encode successfully within the 16 MiB root bound
before the loader accepts it. Successful repairs keep the existing save epoch,
dirty/flush and schema-3 rules; a second load makes no additional repair.

Production files:
- `satellite/mission/AccountRecovery.java` (new bounded helper).
- `satellite/mission/SatelliteMissionRegistry.java` (delegates account repair;
  791 lines, below the 800-line ADR threshold).
- `satellite/persistence/SatelliteRegistryPayload.java` (shared encoding and
  repaired-root validation).
- `satellite/persistence/SatelliteMissionSavedData.java` (uses the shared encoder).

Tests: `AccountRecoveryTest` (3 cases) and `SatelliteAccountRecoveryTest`
(9 cases, including parameterized inputs). They cover 4,095+1, 4,096+1,
4,094+2, 4,095+2, shared owners, preservation of existing balances/audit totals,
no partial mutation on failure, repeated failure/recovery, synthetic section
byte boundaries and root-byte input boundaries. NBT tests use
`CheckedSavedDataFile` and the stable filename for real file read/flush/reload.
The section's byte-cap arithmetic is tested independently: normal
4,096 x 128 account reservations already fit below its 1 MiB cap.

Documentation: the v1.6 implementation log and this evidence directory only.
No API, schema, protocol, published limit or original provenance record changed.

## Commands and results

All Gradle commands use `JAVA_HOME=C:/Program Files/Java/jdk-17.0.7` and the
matching `bin` first on the process PATH. The ambient `java` was Java 8 and was
not used. Platform: Windows; Gradle 8.8; Forge 47.4.10; Python 3.13.15.

| Command | Exit / actual result | Evidence |
|---|---|---|
| `gradlew.bat test --tests '*SatelliteAccountRecoveryTest' --no-daemon --max-workers=2` before the fix | 1; 9 cases, 3 F01 count failures plus 1 probe-only compressed-byte assertion failure | `baseline-test.log` |
| `gradlew.bat test --tests '*SatelliteAccountRecoveryTest' --tests '*AccountRecoveryTest' --tests '*SatelliteRootThreeTest' --tests '*RegistryLifecycleTest' --tests '*SatelliteMissionSavedDataTest' --no-daemon --max-workers=2` | 1; 30 cases, 29 passed; one new probe used lowercase `accounts` where the diagnostics API requires enum name `ACCOUNTS`; corrected without changing production limits/assertions | `targeted-test.log` |
| `gradlew.bat clean build test runData runGameTestServer --no-daemon --max-workers=2` | 0; **1,169 JUnit, zero failures/errors/skips; 290 required GameTests passed** | `full-verification.log`, `junit-summary.json`, `junit/`, `gametest.log` |
| `git diff --exit-code -- src/generated` after DataGen | 0; no generated-resource changes | `generated-diff.log` |
| `git diff --exit-code` | 1; the intentional implementation/documentation edits are not committed; no clean-worktree Gate claim | `full-working-tree.diff` |
| `git diff --check` | 0 | `diff-check.log` |
| `python -m unittest -v test_prepare_v002_g0_review_packet.V002G0ReviewPacketTests.test_git_object_reads_recompute_oid_and_bound_undeclared_bytes test_validate_repository.V002ResourceInventoryTests.test_all_current_text_and_binary_resources_are_allowlisted` (`PYTHONPATH=<worktree>/tests`, `PYTHONUTF8=1`) | 1; F04 reproduced with the same 8 paths; F05's class setup failed on a worktree-related output-directory/root check before reaching that test, so F05 was **not independently reproduced here** | `python-known-findings.log` |

The baseline compressed-byte assertion was broader than the storage contract:
an **explicit** flush can reserialize a preserved payload to different compressed
bytes. The final tests retain byte-identical original-file assertions for load
failure and ordinary automatic save; after an explicit flush they additionally
require the exact original NBT payload, never a partially repaired root. The
first failed probe is retained rather than attributed to the F01 product defect.

The full run's GameTest ERROR lines include deliberate existing invalid-input
and blocked-registry tests. The completion marker and actual test results are
retained; this is not a claim that every negative test emits zero ERROR lines.

The external audit package's `extra-junit-disk-final.log`, F01/F02 probe source,
`compat-default.log` and `focused-python-failures.log` matched its SHA256SUMS on
read-back. This validates those delivered files, not the authenticity of every
historical release or the unrelated F07 checksum entry.

## Packaged dedicated-server recovery

Command (final run, exit 0):

```powershell
python docs/work/v1.6.0-recovery-account-bounds/native_check.py `
  --installed-server 'C:/Users/Administrator/AppData/Local/Temp/arce-v160-c9-1d4b262cc75c4042b9945eebde09cd04/attempt-04/native-server' `
  --output 'D:/GitHub/arce-v160-recovery-fix/build/f01-native-attempt3'
```

The harness copies only the installed runtime libraries and already accepted
EULA into new disposable directories, installs this task's host JAR, and seeds
explicit synthetic NBT. It does not open or copy the original world's state.
Servers bind only loopback, run offline, have no real clients and use finite
timeouts. All processes started by this task exited; no unrelated Java process
was stopped. This is scoped S1 recovery evidence, not V1/V2, S2/player.dat,
production-world migration or reference performance acceptance.

| Phase | Actual result |
|---|---|
| repair | 4,095 accounts + one missing satellite owner become exactly 4,096; existing balances preserved; save and stop exit 0. Two exact, bounded `accounts_added=1` repair diagnostics are retained; no unexpected log findings. |
| restart | Same world reopens, account/satellite records and save epoch unchanged; no further repair diagnostic; save and stop exit 0; no strict log findings. |
| blocked | 4,096 + one missing owner is rejected by startup semantic validation; no server-ready marker; original compressed NBT SHA256 stays `22e4690eea3b801e3dc13dbb0dae41230431b25c85160570ec03a0e7a41224e2`. |

The blocked phase has expected ERROR/FATAL lines and an inherited vanilla
shutdown NPE before levels exist. Forge still exits **0** in that negative
phase, so the oracle uses the exact `[ARCE-BETA-2000]` satellite-registry rejection,
absence of readiness and unchanged disk hash, **not exit code alone**. These
errors are retained in `native/blocked-stdout.log`; this is not a normal-server
strict-log PASS or a repair of that diagnostic/shutdown behavior.

Native attempts retained:
1. `native-driver.log`, `native/attempt1-*`: failed because the harness omitted
   flat-world generator settings and treated intended account-repair diagnostics
   as unexplained warnings. The fixture configuration was fixed; no product
   error was suppressed or accepted in healthy runs.
2. `native-driver-attempt2.log`, `native/attempt2-summary.json`: repair and
   restart passed; the wrapper incorrectly required nonzero exit for a Forge
   pre-start rejection. Logs and unchanged file confirmed rejection; the final
   oracle was corrected to validate that specific refusal and disk state.
3. `native-driver-attempt3.log`, `native/summary.json`, `native/*stdout.log`:
   the final finite three-phase run described above, `PASS_SCOPED_S1`.

Host JAR SHA256:
`185701d7da7356a9bdb4d49daa0e18d99f5cf2c4c9b05cc7f8c3ff3704358494`.
API JAR SHA256 remains the audit's unchanged
`d29f20aafb11a751423ad8d06c3ce6206afb1faa2807d773bbcc94d4faa54a5d`.
Full artifact identities are in `artifacts.json`; source/runtime changes remain
uncommitted, and no candidate identity is assigned.

## Remaining findings and repair order

| Finding | Disposition / separate repair recommendation |
|---|---|
| F01 | Implemented and regression-tested here; independent actual-diff review and integration still required. Abnormal input without repair capacity stays blocked for administrative recovery. |
| F02 | **Open release-blocking conservation risk**, not repaired here. The audit reproduces 64 -> 128 using real registry/terminal classes plus a simulated durable player inventory, not a real-player exploit. Specify an ownership/receipt protocol spanning registry, terminal chunk, withdrawal/player persistence, carried terminals and rebind; verify the protocol before implementation. Test real S2 player/chunk/registry write orders and total item counts. Synchronizing only the registry does not prove safety; do not change the expected total to 128. |
| F03 | Use one canonical host-version source for the consumer coordinate, or require an explicit version; document it consistently. Validate from an empty local publication repository against the newly built host/API artifacts, not old cache. No runtime API break is established. |
| F04 | Freeze a real historical resource fixture for the v0.0.2 allowlist test; separately validate current resource paths/references/provenance with current rules and keep unknown-namespace/path negative tests. Do not blanket-ignore new namespaces. |
| F05 | Corrupt a unique OID in an isolated repository with no valid alternate copy; retain exact identity/undeclared-byte assertions and cover loose/packed/shared layouts. Here the pre-existing worktree fixture setup blocked that independent repro. |
| F06 | Preserve historical evidence bytes using a narrowly scoped non-text attribute, or perform explicitly recorded normalization only after checking evidence bindings; verify fresh checkout/stat refresh/DataGen/clean diff. Not changed here. |
| F07 | Reconcile against a trustworthy original artifact or reconstruct and append a new evidence record. Preserve old recorded/actual hashes and origin; merely changing the checksum would not verify authenticity. Not changed here. |

The native rejection's shutdown diagnostics, independent review, fresh candidate
build, inherited G0--G9, reference load, real players/GPU, compatibility matrix
and the full conservation/recovery campaign remain open. The full Python suite
was not rerun or declared green. No test, budget or release gate was removed.

## Review, Gate status and handoff

Implementation reviewed against the actual diff: account preflight precedes all
repair mutation; success uses the same encoder as disk flush; failure preserves
source; no common/client dependency or new world traversal; no public contract
or imported-asset change. This review is by the implementing session, **not an
independent audit**. The root version cursor, v1.6 release files and v1.7 branch
are not changed by this task. Version status remains **IN_PROGRESS**, not PASSED.

All current-version Required Gates satisfied: **no**. Scoped development
verification does not close F02, release cleanliness, real-player/GPU, S2,
performance, provenance, candidate or approval requirements.

Next current-version work: independently review/integrate the F01 patch, then
resolve F02's delivery protocol and real S2 reproduction before a v1.6 candidate.
The implementation run performed no commit, push, merge, tag, downloaded-artifact
publication or historical checksum correction. The original v1.7 checkout is
not written by this task. The change remains separate from that branch.

### Local commit authorization

The maintainer subsequently requested a local commit of this F01 repair and
its tests/evidence. Production code and tests remain identical to the successful
full verification run. Before committing, recheck the exact staged paths, diff
whitespace and evidence SHA256s against the index bytes, not only working files.
The packet-local `.gitattributes` retains raw log/diff/JSON/XML byte identity;
the historical F06/F07 files and their attributes/hashes are not edited. Raw
captures keep their original whitespace; the ordinary source/documentation
checks still apply. This is not a release candidate, independent approval,
push or merge. Integration and the open Gates remain separate obligations.
