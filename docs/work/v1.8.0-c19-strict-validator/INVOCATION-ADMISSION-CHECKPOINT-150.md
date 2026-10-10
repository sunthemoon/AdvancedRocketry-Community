# C19 invocation admission checkpoint 150

Date: 2026-10-10. Version: v1.8.0, IN_PROGRESS / IMPLEMENTING.
[Task150](INVOCATION-ADMISSION-TASK-150.md) is preserved unchanged.
Main preparation base: d1272416ec9b008f7cbc64d04598d5ff42236a2c.
Tested source: d7f4e69be2fc02f29c86533ee1b5cf173d96cf0c, separately
committed and normally pushed in Source139, not integrated in Main.

## Completed scope and design

The new external standard152 operator binds 3287 tracked source/build/generated
inputs, seven named external images, immutable binding bytes and BASE_ENV /
TARGET_ENV digests at entry, before/after each original and terminal.
The target environment is constructed before entry. Actual qualify_v5.py is
23034 bytes, SHA256
d1791fa8b376224384e543670ad220a3fc5bd2cdf02406ad17971888d1a1a101.
Binding SHA256 is
bba59c2381c16e2333d5b659fc692c387d88e1cee2458f40696f872f5fcbef5c.

Addendum01 privately admits only C:/Windows/System32/cmd.exe (344064 bytes,
two links, SHA256 97ac98b1a92c286054cce55239cfccdfc23a5517bd07fe693072c9ca96c7dabb)
and C:/Program Files/Git/cmd/git.exe (43352 bytes, two links, SHA256
78211c7ed73988da93a6d8a33d47ec6187f464d7ea2a9a00c182bbd7a1ecf30f).
The generic helper still rejects every multiply linked ordinary input.
No system image/link/stream content is modified or copied. The private reader
retains exact canonical name/hash/count, ordinary ancestry, regular/non-reparse
type, ADS metadata rejection, unbuffered cap-plus-one hash streaming, shared
cross-API identity and full same-API/ctime checks. Access time is not a stability
token. This is a fixed local coupling, not a generic trusted-file API.

Exception administration attempts independent whitespace and records explicit
unqualified states when entry acquisition, capture or publication fails.
Maximum raw/control reservations are acquired before writes and unused raw
maxima are released only when both reader threads end. Private Jobs own only
new suspended originals assigned by creation handle before primary-thread resume.

## Actual commands and results

All standard originals use Source139, JDK17.0.7 and the unchanged arguments.
Gradle retains --no-daemon --offline --no-build-cache; build/test/DataGen also
retain --rerun-tasks. Separate stop/drain ceiling is 10 seconds; raw ceiling 4 MiB
per stream. Metadata originals retain 30 seconds and dual 262144 bytes.

| Original | Seconds / ceiling | Separate drain seconds | Exit |
| --- | ---: | ---: | ---: |
| gradlew.bat clean build | 207.519614 / 2400 | 0.000397 | 0 |
| gradlew.bat test | 194.716737 / 1200 | 0.000261 | 0 |
| gradlew.bat runData, first | 48.605896 / 1200 | 0.000412 | 0 |
| git diff --exit-code, first | 0.416202 / 180 | 0.000059 | 0 |
| gradlew.bat runData, second | 49.601574 / 1200 | 0.000506 | 0 |
| git diff --exit-code, second | 0.099463 / 180 | 0.000041 | 0 |
| gradlew.bat runGameTestServer | 238.167853 / 1800 | 0.000335 | 0 |
| git diff --check | 0.100525 / 180 | 0.000044 | 0 |

All eight originals have complete dual EOF, ended readers, no overflow and
within-limit receipts. Entry, sixteen before/after snapshots and terminal match;
source and semantic index are clean. All commands are STARTED/QUALIFIED, with
zero unexecuted/unknown-start commands, administrative errors or outstanding
maximum reservations. Coordinator shell exit 0 is administrative, not a
whole-cohort 180-second proof.

Build and explicit test each retain 381 XML files /2192 tests and matching
testcases, zero failures/errors/skips. Two generated inventories contain 1241
outputs each and are byte-identical; both original diff captures are empty.
Native latest.log and debug.log each report 597 required tests passed and
62 ERROR /0 FATAL headers, including 10 project-logger ERROR headers.
These are two logs of the same run, not 124 distinct errors. ERRORs remain
unwaived; command qualification does not satisfy the clean-log Gate.
C:/D: exceed 10 GiB at entry and are checked by the operator before commands.

## Independent reviews and retained failures

Root reads every complete report and freshly verifies each sealed envelope
through new Root code; sealed helpers are never executed or imported.

- Contract151 approves no broader generic exception. Actual-source reviews
  153-156 retain their findings and harness failures; additive v3-v5 address
  refused-launch, entry, dependent-capture and pre-control-store administration.
  Original operators and old reports are not retroactively fixed.
- Root's final finite 12-method original exits 0 in 0.311333 seconds /180 with
  complete 12268/1716-byte captures and separate 0.000172-second drain.
- Review157 schedules 61 cases but retains only 53 complete captured records.
  Its original is CAPTURE_ERROR, exit null, stdout overflow / EOF false.
  Eight reader-rejection records are truncated. This remains failed/incomplete,
  not a full successful independent-suite receipt.
- Separate reader158 executes 38 cases /223 assertions with exit 0,
  0.119597 seconds /180, complete 20090/0-byte captures. It complements reader
  coverage, not a successful rerun of 157. Its preliminary direct-Git metadata
  protocol gap and retained hard-link fixture remain explicit.
- Result159 records 1419 checks: 1415 pass and four narrower generated-scope
  assertions fail. Its first cross-API ctime bootstrap also fails. Full report
  and 16-file /314518-byte envelope are retained; no unconditional audit PASS.
- Fresh result160 reconstructs the actual six scopes, including src/generated,
  and verifies all 1241 generated identities against entry. It verifies 182
  metadata originals, eight standard originals, all 762 XML files and log metrics.
  Of 9421 checks, 9420 pass; an added cross-run XML-byte equality assertion fails
  without a contract basis. Original exit 1 is preserved, not called PASS.
  Its startup direct-Git protocol gap is retained. The 15-file /1160287-byte
  envelope and separate own publication receipt are freshly verified.

Assigned standard/Main/Root reads end at 10:02:20.227667 UTC in 160; final permitted
helper/Python read/import cutoff is 10:05:54.229632 UTC. No assigned reads resume.
Late cleanup and these Main record updates are not implicitly peer-audited.

## Cleanup, records and evidence

Only the six named Source139 outputs and exact new standard152 runtime are
eligible; binding preparation proved all absent before the cohort. Root completes
all containment, ordinary ancestry and 6349 descendant non-reparse checks before
deleting any target. Four existing targets are removed: build, .gradle, run-data
and new standard152 runtime; three declared targets were absent. Removed 5732 files total
271521364 bytes. Tracked generated resources and all source files are retained.

Initial owned cleanup PowerShell original exits 1 before script execution because
local script execution is disabled; no target is deleted and no cleanup.json is
created. The same frozen cleanup script runs under a distinct v2 wrapper with
process-only ExecutionPolicy Bypass, not a persisted policy change. Its new
original exits 0 in 3.433001 seconds /120; separate drain 0.000220 seconds /10,
complete 3054/0-byte captures; all preflight/cleanup results are recorded.
Root/peer hard-link fixtures, old failed runtimes, unknown acquisitions and every
sealed packet remain untouched.

External evidence base: D:/GitHub/ARCE-Task-Evidence/v1.8.0.
Root: c19-invocation-admission-root-20261010-150, with reports, exact images,
original receipts, audit verification, cleanup and scoped publication bindings.
Standard: c19-invocation-admission-standard-20261010-152, sealed 1396 files /
29178702 bytes, manifest eff422b8a7c461ae184ca483bfccc8c8c848cca568581e0dd628ec36e6e3c01e,
seal 68d876a37a030ca0dfaf5f8e5caf6a34012150d07decc20da3fd92dd31bed3b5.
Peer leaves: c19-invocation-contract-review-20261010-151,
c19-invocation-successor-review-20261010-153,
c19-invocation-revision-review-20261010-154,
c19-invocation-bookkeeping-review-20261010-155,
c19-invocation-entry-review-20261010-156,
c19-invocation-capture-review-20261010-157,
c19-invocation-reader-review-20261010-158,
c19-invocation-result-review-20261010-159 and
c19-invocation-result-review-20261010-160.
Only this checkpoint, frozen Task150, CURRENT_VERSION, COMPLETION-PLAN and the
implementation log are Root's Main write/stage/commit scope. Exact Main
publication SHA and final record hashes are recorded in Root PUBLICATION.json /
final_bindings.json; protected AGENTS.md and 183 inherited rows are not adopted.

## Unfinished scope, risk and Gates

The sampled invocation set does not enumerate transitive DLLs, Gradle caches/
classpaths or all host state, prove exact loader lineage, recompute undisclosed
environment maps or eliminate between-sample TOCTOU. Retained JAR hashes are
collector declarations, not independently reacquired artifacts.
No caps, deadlines or existing test assertions are relaxed.

Whole Python/strict qualification, an unconditional full independent-suite
receipt, native clean-log resolution, Main source integration, portable committed
evidence references and C19/Gate obligations remain open. No sleep, memory,
save/content, ledger, restart/persistence, multiplayer, actual-GPU, performance
or license acceptance occurs here. Repeated owner replies match existing
decisions01/03/05/07; this task grants no new gameplay or resource acceptance.

All Required G0-G9 remain open. v1.8 is IN_PROGRESS / IMPLEMENTING; the v1.0
acceptance cursor and Main tested/native/Python qualification identities do not
advance. Next work is limited to these remaining v1.8 obligations.
