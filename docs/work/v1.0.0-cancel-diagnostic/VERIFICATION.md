# v1.0.0 cancellation diagnostic correction

Date: 2026-09-06, Asia/Shanghai. Development worktree on
`codex/v1.0.0-stable-core`, base `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`.
This verifies `V100-VISUAL-CANCEL-DIAGNOSTIC`, not a release candidate or Gate.

## Scope and design

The shared return-to-source method logged four outcomes at WARN, including a
validated player's successful countdown cancellation. Earlier actual GUI runs
therefore failed the unchanged strict packaged-server audit. Their original
FAIL evidence remains in the [single-client](../v1.0.0-flight-console-ui/VERIFICATION.md)
and [two-client](../v1.0.0-flight-duo/VERIFICATION.md) reports.

`RocketTransferReturnReason` binds four fixed server reasons. Only
`COUNTDOWN_CANCELLED` logs INFO. Blocked destination, failed entity creation
and failed entity spawn still log WARN. Event ID, format, UUIDs, reason strings
and fuel fields are unchanged. All four call sites use typed constants.
No state transition, journal flush, passenger remount, authority validation,
fuel accounting, schema, protocol 4, audit filter or budget changed. No waiver
was introduced. The producer now distinguishes a requested successful action
from a transfer fault.

Changed product files are the new reason enum and the existing
`rocket/server/RocketTransferService.java`; the new
`RocketTransferReturnReasonTest.java` covers all four diagnostic outcomes.
CHANGELOG, the implementation log and community provenance were updated.
No upstream code or assets were imported.

## Reproduction and automated results

Java 17.0.7, Windows, Forge 47.4.10, Gradle Wrapper. Commands executed from the
repository root with JAVA_HOME set to the installed Java 17 directory:

| Command | Actual result | Evidence |
|---|---|---|
| `gradlew.bat test --tests '*RocketTransferReturnReasonTest' --no-daemon` before correction | exit 1, 17s; 2 tests, 1 failure: cancellation expected INFO but got WARN | [before.txt](verification/before.txt), [XML](verification/before.xml) |
| Same targeted command after correction | exit 0, 16s; both tests pass | [after.txt](verification/after.txt) |
| `gradlew.bat clean build --no-daemon` | exit 0, 26s | [clean-build.txt](verification/clean-build.txt) |
| `gradlew.bat test runData --no-daemon` | exit 0, 25s; no generated changes | [test-data.txt](verification/test-data.txt) |
| `gradlew.bat runGameTestServer --no-daemon` | exit 0, 2m 4s; all 44 required tests pass | [gametest.txt](verification/gametest.txt), [native bytes](verification/gametest-native.log) |
| `git diff --exit-code -- src/generated` | exit 0 | [generated.diff](verification/generated.diff) |
| `git diff --exit-code` | exit 1; inherited and new uncommitted changes, not a clean candidate | [worktree.diff](verification/worktree.diff) |
| `git diff --check` | exit 0 | [diff-check.txt](verification/diff-check.txt) |

The before test reproduces the extracted original unconditional-WARN behavior;
it is not a new pre-fix packaged GUI run. Fault assertions passed before and
after. Archived [Java XML](verification/java-results) totals 387 cases in 74
suites, zero failures/errors/skips. GameTest native logs independently show
`destination_pad_blocked` at WARN and `countdown_cancelled` at INFO. Raw native
bytes are retained; localized dates need not decode as UTF-8.

## Native client, strict audits and restart

Executed the archived [driver](verification/packaged/arce-cancel-diagnostic-session.py)
with Python 3.13.15 from the task's external TEMP directory. It completed
exit 0 in approximately 98 seconds, including fresh-world startup, one native
client and dedicated restart. [Driver output](verification/packaged-driver.txt),
[summary](verification/packaged/summary.json) and native/full logs are retained.
The exact window-control helper is archived beside the driver.

Environment: local packaged Forge 47.4.10, JEI absent, Windows native client,
1280x720, GUI scale 2. This is a loopback offline fixture identity, not a new
authentication test. The owner remains OP for this diagnostic-only scenario;
it does not substitute for the preceding non-OP two-client permissions case.
No remote host was contacted or provisioned.

1. A bounded fixture creates a five-block rocket, 1000 fuel and two stations.
   The player's floor is checked before interaction. Fixture commands do not
   issue LAUNCH or CANCEL.
2. [Console](verification/packaged/02-console.png): Earth to Moon, FUELED,
   1000/1000 fuel, route requires 372.
3. The actual client clicks launch. The server accepts request
   `569e6c52-30e4-4ce8-9f67-b1206f15793e` at 02:56:59.
   [Countdown](verification/packaged/03-countdown.png) shows 54 ticks,
   disabled route controls and Cancel Countdown.
4. Tab then Enter submits actual CANCEL request
   `98d17632-0d82-4656-852f-9fb8e7a2343f`, accepted at 02:56:59.
   [After cancellation](verification/packaged/04-cancelled.png) shows FUELED,
   unchanged 1000 fuel and restored controls. The return diagnostic is INFO.
5. Reports at 02:57:00 and after dedicated restart at 02:57:24 match entity
   `3d68a97e-ac36-4e91-92a6-3580f7d8b046`, logical ID
   `1452d864-28a0-464d-8224-ffdc0cfef1d7`, snapshot hash, five blocks and source
   origin `384,101,384`: FUELED, fuel/capacity 1000, passengers 0, no transfer.
6. All three owned native processes exit 0. Existing strict error/fatal/project
   warning/client-linkage scans return no findings. Cleanup has no errors;
   no task-owned Minecraft process remains running.

This checks a real processed action rather than treating posted window input
as success. It does not assert carried inventory contents, passenger recovery,
station-target synchronization, every GUI scale or full V1/V2 acceptance.

## Input identity and audit

Development JAR: 1,254,838 bytes, SHA-256
`c860542e8971e845121b6fd5c41474407376b465acc301a7cab7975ac0ccf9d0`.
The [source inventory](verification/source-inventory.json) records 736 inputs.
Compared with the preceding quote inventory, only the existing transfer service
changed among its 734 files; the enum and test are two new inputs. Old JAR
evidence is not relabeled as results for this artifact.

Run `python docs/work/v1.0.0-cancel-diagnostic/verification/audit.py` to check
source/JAR identity, before/after XML, GameTest severity markers, actor-bound
native receipts, process exits, unchanged strict scans, restart reports,
PNG dimensions/hashes and [checksums](checksums.txt). This is a scoped evidence
audit, not independent review. No long Python suite or compatibility/recovery
matrix was repeated for this logging-only correction.
The [actual audit run](verification/audit-result.txt) exits 0: 736 source inputs,
387 Java cases, 44 GameTests, three native processes, four screenshots and
106 retained file checksums pass. The audit output itself is excluded from
the checksum manifest to avoid a self-referential hash.

## Remaining scope, risks and release status

`V100-VISUAL-CANCEL-DIAGNOSTIC` is verified. v1.0 remains IN_PROGRESS; not all
Required Gates are met. Existing station-target two-client coverage, retained
initial handshake/input failures, passenger/reconnect checks, full visual
acceptance, final candidate binding and independent/human release review remain.
This successful local connection does not establish a cause or fix for earlier
handshake timeouts. Long reference workloads remain owner-deferred, not passed
or waived. No commit, tag, release approval or v1.1 implementation was created.
The next bounded task is the existing v1.0 station-target console interaction.
