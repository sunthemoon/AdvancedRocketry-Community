# V130-ROCKET-03B: external inventory recovery

## Scope and status

VERIFIED for this development slice. Coverage uses one four-block rocket with
the independently built API-only fixture and its two-slot external container. Clean-process restart,
installed-block/missing-provider rejection, actual fixture removal while its
container is held only in the snapshot, matching-fixture reinstallation and
recovered BlockEntity restart are separate observations.

This is finite development evidence under ADR-018/020, not full acceptance,
arbitrary third-party uninstall support, unknown-item recovery, forced power
loss, cross-dimension movement, client verification or a load test.

- Host baseline: `b42cbdb` (ROCKET-03A evidence complete).
- Startup-only fixture control: `80d0b43`.
- Runner: worker `f961ec8fc99b01e1e2fc0d65783cdd8e8b0cab11`, integrated as
  `7ca7861`; its 21 pure-Python tests pass in the primary checkout (0.212s),
  with [complete output](python-integration.txt).
- API remains 1.1 / ADR-022; host production, save and network contracts are
  unchanged. The fixture property omits only adapter registration, not its
  block, BlockEntity or tags.
- The existing `stage-recovery` command creates an ASSEMBLY/EXTRACTING journal
  with progress 4 and suppresses recovery until process exit. It is a synthetic
  durable precommit state, not an observed crash.
- Valid snapshot decoding does not depend on provider availability. Missing
  providers are rejected by restoration preflight; `operational=false` is not
  the preservation oracle.

## Commands completed

Windows / Oracle Java 17.0.7 / Gradle 8.8 / Forge 47.4.10; final fixture source
state `80d0b43`. All commands below ran in the primary checkout.

| Command | Result |
|---|---|
| `gradlew.bat clean build test runData runGameTestServer publishMavenJavaPublicationToLocalProjectRepositoryRepository --offline --no-daemon --no-build-cache` | Exit 0 / 2m48s; 130 suites, 663 JUnit, zero failures/errors/skips; all 134 Required GameTests passed; DataGen written 0; local publication regenerated |
| `git diff --exit-code -- src/generated` | Exit 0; no generated resource change |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache` | Exit 0 / 19s; all 10 tasks executed, including compile/reobfuscation and both boundary verifiers; `test` is NO-SOURCE, not additional JUnit coverage |

Full host output is in [host-integration.txt](host-integration.txt), native
GameTest output in [gametest-native.txt](gametest-native.txt), and the complete
JUnit XML in [junit-full.zip](junit-full.zip), archived before any focused rerun.
Existing deliberate save-fault GameTest logs remain visible. Standalone
consumer output is in [consumer-integration.txt](consumer-integration.txt), with
its three actual reports in [consumer-reports](consumer-reports/).

## Artifact identity

| Artifact | SHA-256 |
|---|---|
| Main host | `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200` |
| API classifier | `06568c596efb623c80eddfbdd41afd695451f47ceb935b165d149349157e4020` |
| Sources | `7b1e565e5ce326605d739effd5d37721d65e6fb41f4dba859ede53233c4d755f` |
| Standalone fixture | `8144ded5d03764cdd6b1eda8ff5e778f7dd87c564ebde3e5dfe20d382629085f` |

The three host artifacts remain byte-identical to ROCKET-03A. The new fixture
contains the startup-only provider control. No dependency download, remote
upload or release tag is part of these commands.

## Runner review and retained failure

Independent review of the initial runner corrected missing explicit outer
journal bindings; the corrected oracle has negative tests. The initial reviewed
source passed 21 independent Python tests (0.216s), with exact source hashes in
[independent](independent/).

A final preparatory air-shell change crossed four chunks while its readiness
check still covered one. The pure oracle tests did not cover that setup detail.
The exact integrated runner was already executing when this finding arrived.
It was observed without changing source or terminating its process; the actual
four-block setup succeeded, but did not establish deterministic setup elsewhere.

The first run exited 1 after four clean JVM exits. Its uninstall process preserved
live authority through two observations, then the log audit rejected an additional
Forge GameData ERROR header for the missing block registration. Its only entry
was `arce_adapter_test:cargo_container: 1022`. This is a harness classification
failure, not zero-ERROR evidence or a six-process success. A separate read-only
capture of the stopped world confirmed the complete entity and journal unchanged.
Neither recovery nor the final container restart ran in this attempt.

The [failed-attempt report](runtime-initial/FAILED_ATTEMPT.md),
[failure](runtime-initial/failure.json) and [readback](runtime-initial/postcheck.json)
remain visible. [runtime-initial.zip](runtime-initial.zip) preserves all 83
checksummed capture/source/report files and their original manifest (84 entries),
including stdout/debug logs and raw NBT. Every source digest and ZIP readback was
checked before import; the original Temp world is retained and was not retried.

Correction `959c1f7` loads and waits for all four shell chunks before clearing.
It classifies only the exact missing-block GameData header followed by a single
known fixture ID/number; unexpected registries, IDs, extra entries and unrelated
errors remain failures. Forge 47.4.10's cached `GameData.java` lines 643-648
confirm that diagnostic format. No host, payload, inventory/authority oracle or
timeout changed.
Two new regression methods cover setup ordering and diagnostic boundaries.

Corrected primary tests: 23 passed / 1.078s,
[output](python-corrected.txt). Independent final-source rerun: 23 passed / 0.265s,
exit 0; exact script and tests unchanged, no unresolved source finding. See
[independent-corrected](independent-corrected/). The earlier exact 21-test
integrated-source rerun and its setup finding are also retained in
[independent-initial-final](independent-initial-final/).

## Corrected packaged execution

Runner `959c1f7a44e7a7a07979d2fab185c08647b459f9` passed in a separate new
disposable world, with unchanged host/fixture artifacts and 104 hash-checked
runtime library files. The actual command is retained in
[launch-command.txt](runtime-final/launch-command.txt). Execution was
15:53:44-15:55:43 UTC on 2026-09-26; runner exit 0, six JVM exits 0.

| Process | Observed ticks | Durable result |
|---|---:|---|
| Assemble | 22 | One entity, source blocks air, exact external envelope and cargo |
| Entity restart | 22 | Complete RocketEntityData/UUID unchanged |
| Provider skipped | 22 | Disassembly UNSUPPORTED_BLOCK_ENTITY; blocks/BE/tag still installed; synthetic journal staged |
| Fixture uninstalled | 22 | Complete entity and one-entry journal unchanged; bounded recovery CONFLICT, competing disassembly REGION_BUSY |
| Exact fixture reinstalled | 22 | Four blocks restored, exact native inventory; no rocket or journal entry |
| Container restart | 28 | Restored two-slot inventory/metadata unchanged; no repeated material authority |

The exact cargo is 17 vanilla diamonds with the custom display-name metadata and
64 vanilla iron ingots. The snapshot's adapter ID and payload version remain
unchanged. Checks cover the fixed fixture positions/chunk, with no item drops
or duplicate rocket in the observed area. The provider-skipped process suppresses
recovery after synthetic staging; persisted-journal retry is observed in the
actual-uninstall process, not a separate skipped-provider restart.

The [independent runtime report](runtime-final/VERIFICATION.md) and
[raw-NBT observations](runtime-final/independent-observations.json) verify actual
Anvil entity/block data and journal bytes, rather than trusting PASS fields.
Status, registered-mod sets, actual loaded JAR sources, registration counts,
unchanged artifact hashes and closed endpoint/processes were checked separately.
The postcheck exited 0. Runtime stdout WARN counts are 25/10/10/14/10/10;
ERROR counts are 0/0/0/2/0/0. Only the two known uninstall registry diagnostics
are accepted. No unrelated ERROR/FATAL, project warning/error, linkage failure
or tick-lag warning was observed; this is not a performance Gate.

[runtime-final.zip](runtime-final.zip) contains all 114 checksummed final
capture/source/report files plus their manifest (115 entries). Every digest and
ZIP entry was verified at import. Raw logs, per-process commands/status, configs
and native region/entity/journal/level data are included. Entire runtime installs
and JAR copies are excluded; both original Temp worlds remain retained.
Readable [summary](runtime-final/summary.json) and supplemental reports accompany
the archive. [Archive verification](runtime-final-archive.json) records its hash.

A separate reviewer also verified all 91 native manifest entries and reread the
six raw captures. The [independent readback](independent-native/readback.json)
matches complete entity/journal authority, exact recovered BlockEntity data,
artifact/source hashes, process identities and the two classified errors. No
unresolved finding remains in this slice. This was another readback, not another
six-process execution. Reviewer bookkeeping corrections are retained in the report.

## Remaining work and risks

No host production correction was required. Fixture controls default off and
the test mod remains outside distributed host/API/sources artifacts. The public
guide now distinguishes snapshot preservation from ordinary missing world blocks
or unknown item types; the fixture README documents the bounded runner.

Existing per-tick CONFLICT logging can be noisy during absent-provider recovery;
only a short observation was made. Callback side effects, unknown item/block
recovery, arbitrary external providers, cross-chunk power-loss atomicity, real
clients and long-duration behavior remain outside this evidence.

ROCKET-03C cross-dimension conservation and the remaining v1.3 provider APIs are
still planned. v1.3 stays IN_PROGRESS, inherited acceptance remains open, and
G0-G9 are not approved. No tag, release or full deferred test campaign was run.
