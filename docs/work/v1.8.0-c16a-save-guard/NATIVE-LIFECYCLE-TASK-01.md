# C16a-S1-GUARD-02: existing save-guard lifecycle verification

Date: 2026-10-04. Owner and sole integrator: Root. Status: IN_PROGRESS.
Independent source/command review and result audit are assigned separately.
Baseline: `355c4681a6cbddd380545a50e0f6e524bad6d295`; production code and
packaged main JAR remain the verified K3 development build.

Outcome: a bounded copy-only native exercise of the existing refusal through
fixed OP removal, a native loaded-predicate cut, reacquisition and clean restart. Observe
the exact compressed terrain record, four retained resource roots and an
unrelated marker. This is diagnostic test tooling, not a supported online repair.

Write scope: new `scripts/run_v180_guard_lifecycle_smoke.py`, its focused Python
test, this task record and a separate verification record. Root alone updates
current progress files after verification. Workers write only their own small
external evidence directories. No production Java, schema, registration,
recipes, assets, network, AGENTS.md or sealed prior evidence changes.

Input: stopped Tank06 oversized-refusal world, its runtime libraries and
compatibility fixture, independently pinned; fresh runtime on D:. Copy and
verify the world before launch, install the exact current main JAR, and never
modify the input world. Only the fixed fixture chunk (11, 11) receives a forced
ticket. A command barrier is not a durable save acknowledgement; inspect native
records after actual process termination. Use the existing 60-second load
observation deadline and 300-second startup/save/stop bounds without extension.
The native predicate also requires entity-ticking status and entity readiness;
its false result alone does not prove LevelChunk destruction or unload callbacks.
Require live removal/marker mutation and later old-snapshot restoration as
separate observations. Callback ordering remains uninstrumented.

Validation: malformed/wrong-cell/duplicate fixture rejection; strict server
marker and deadline controls; retained compressed-record/marker/resource
oracle; paired refusal-header accounting; actual two native cycles and original
input postchecks. Run applicable Python tests, independent actual-diff review
and independent disk/log result checks before stage commit and non-force push.
The unchanged Java build/DataGen/GameTest results remain explicitly inherited,
not new executions by this tooling leaf.

Non-goals: new GuardTicket/carrier/first-save writer admission, 256/257 saturation,
new terrain coordinates, cross-store conservation, force-stop recovery,
offline repair, log quotas, clients and physical hatches. No R-021 acceptance,
ADR proposal acceptance, risk-policy change, release approval or Gate closure.

## Source-stage checkpoint

The two-file tool has independent source review and actual 106 focused Python
tests passing (16 new plus 90 unchanged). The original mocked-clock error,
two red log-oracle controls and corrected independent controls remain preserved.
The corrected severity/logger oracle has no unresolved source-review findings.

First actual native attempt exits 1 in 20.909501 seconds: initial and post-removal
saves refuse, but the live restored-culprit assertion fails after immediate
ticket reacquisition. The stopped compressed terrain record and all original
inputs remain unchanged. This is not a successful reload/restart exercise;
the second native cycle did not run. The source stage can be committed without
claiming runtime delivery. Evidence remains at
`D:/ARCE-Task-Evidence/v1.8.0/guard-lifecycle-01`.

A subsequent observation amendment must use source-backed native unload
ordering, preserve this failure and rerun the same restoration/record oracles
within the existing deadline. It is a separate review boundary, not a timeout
extension or a replacement PASS for this failed attempt.
