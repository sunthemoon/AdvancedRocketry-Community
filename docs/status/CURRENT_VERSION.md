# CURRENT_VERSION

```yaml
current_version: v1.0.0
status: IN_PROGRESS
next_action: Review remaining adjacent-chunk loading and expiry behavior plus candidate acceptance gaps in docs/releases/v1.0.0/RELEASE-EVIDENCE.md; controlled native queue recovery and pending-player logout pass, genuine storage ordering and final v1.0 Gates remain open, no long-load work
last_updated: 2026-09-06
prerequisite_version: v0.9.0
prerequisite_status: PASSED
prerequisite_merge_commit: a7196ff9b22220c344071a1af69a663036f76aef
work_branch: codex/v1.0.0-stable-core
base_commit: 34b2e99b48a33f4ba8905b6a69a38efee1649d3f
build: 1.20.1-1.0.0-dev
tested_implementation_commit: ""
artifact_sha256: ""
```

The accepted Beta identity, approvals and published artifact remain immutable
in [v0.9.0 GATE-STATUS](../releases/v0.9.0/GATE-STATUS.md). v1.0 stabilizes that
core without implementing v1.1+ features. Development-tree checks are not a
frozen release-candidate commit or stable approval. See the
[implementation log](../work/v1.0.0-implementation-log.md).

## Active development checkout

The acceptance cursor above remains at the earliest unfinished release Gate.
Under [ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md),
v1.8 development proceeds separately from inherited release acceptance.

```yaml
active_development_version: v1.8.0
active_development_branch: codex/v1.8.0-classic-content
phase: IMPLEMENTING
execution_state: ACTIVE
accepted_development_baseline: 55da6a58842762c382edce0a5a842d06bb76ff6e
development_log: docs/work/v1.8.0-implementation-log.md
session_handoff: docs/work/v1.8.0-session-handoff-20261008.md
previous_development_handoff: docs/releases/v1.7.0/RELEASE-EVIDENCE.md
runtime_build: 1.20.1-1.8.0-dev
latest_source_checkpoint: c554e810780f0f9b5b8b6cd290bddd6e0822b2eb
pending_graph_source_candidate: ""
tested_code_commit: 794dd123f9af0047adbab385eddeb1c9d5089773
native_tested_code_commit: 794dd123f9af0047adbab385eddeb1c9d5089773
latest_regression_target_commit: 794dd123f9af0047adbab385eddeb1c9d5089773
latest_regression_result: NATIVE_PASS_STRICT_LINK_FAILURE_GATES_OPEN
latest_regression_run: seal-installed-runtime-root-20261008-01/cohort-03
latest_regression_attempt: 3
latest_regression_evidence: SEALED_ROOT_AND_INDEPENDENT_DEVELOPMENT_REGRESSION
latest_regression_observed_utc: 2026-10-08T15:42:27.726670Z
actual_unit_rerun: seal-installed-runtime-root-20261008-01/cohort-03/build.log
actual_unit_rerun_observed_utc: 2026-10-08T15:29:27.798186Z
tested_python_commit: 5582e3c49d548c5c002ef6c7cd45b1296f2f2703
last_updated: 2026-10-09
```

## Current development evidence

The separate [detector admission candidate](../work/v1.8.0-c18a-seal-detector/ADMISSION-TASK-03.md)
is IN_PROGRESS at unintegrated corrected source 452fd2a0. Preparation 77154351
and corrective scope a2e3c476 are published before their respective source edits.
The original e456ffc0 Root cohort passes unit/native commands but retains strict
failure and an independent Medium exceptional harness-worker cleanup-order
finding. The correction checks terminal state before both actual closers and
adds finite worker-gate tests; its distinct full cohort and independent review
are pending. This does not replace the completed integrated regression below,
describe a normal detector failure, approve delivery or close any Gate.

The [installed detector rule qualification](../work/v1.8.0-c18a-seal-detector/INSTALLED-RUNTIME-VERIFICATION-02.md)
is independently actual-source reviewed and integrated/normally pushed at
`c554e810780f0f9b5b8b6cd290bddd6e0822b2eb`. Actual tested source is
`794dd123f9af0047adbab385eddeb1c9d5089773`; complete src and seven Gradle
inputs match, without rerunning the merge SHA. No Claude is called.
Only two new development-adapter cases and their task change; production and
all normal artifact identities remain unchanged. Both native hands read the
startup-installed external full-collision rule through registered-item use;
serialized closed/open/closed payloads, held data and embedded reply callbacks
are checked. This is not real-client delivery, packaged S1/S2 or V1/V2 proof.

Root and independent Codex genuinely execute uncached clean builds with 2,179
JUnit, two DataGen runs/empty diffs and all 553 required native GameTests.
The original Medium rendered-projection assertion limit is corrected and
independently rechecked; original passing cohorts, launcher/setup failures and
console-extractor corrections remain sealed. Strict validation still has 44
passing checks/one inherited link failure; each corrected native log retains
62 unwaived ERROR headers/zero FATAL. The Low preparation-log timing deviation
remains explicit, unwaived and separately attributed. Both owned cleanup
attempts succeed once; no previous refused target or user work is changed.
Full detector actor/lifecycle/query-order, installed precedence, unlock,
packaged/restart and actual client obligations remain unfinished.

The latest [coupled private successor](../work/v1.8.0-c16a-hatches/COUPLED-CONTRACT-DISPOSITION-23.md)
binds genuine entry/allocation, two fresh LOADs, both save consumers and terminal
publication at base a0170336. It remains PROPOSED, not source-assignment-ready.
Original draft findings are retained; a fresh complete revision review confirms
the three specification corrections without granting runtime/assignment proof.
Pinned static primary facts distinguish the disk FULL event from Proto promotion;
isNewChunk and attachment-time emptiness are not origin authority. Outcome caps,
authentic origin/final writer, both Medium prerequisites and O1/O2/O3 remain open.
No runtime source, hook, dependency, writer, policy, ADR/risk acceptance or Gate
changes. That contract inspection itself executes no game regression.
The preceding [placement qualification](../work/v1.8.0-c16a-hatches/PLACEMENT-PROVENANCE-VERIFICATION-22.md)
and its original findings/cohorts remain in the implementation log and sealed
packages; they are not replaced or rebound to the newer detector commit.

The ledger stays 186 PLANNED /154 REVIEW assets. C16-C19 and both full Medium
physical prerequisites remain open. U1 is only narrowly measured; U1/U3-U7,
O1/O2/O3, ADR-068 PROPOSED and R-021 OPEN remain. No owner policy response is
recorded. Packaged/restart, prior-world, installed continuation, V1/V2,
progression, performance and all Required Gates are unfinished. No content
delivery, release approval or completion of v1.8 is inferred from this slice.

The [completion plan](COMPLETION-PLAN.md) remains the execution list; prior
current-state evidence is preserved in the
[implementation log](../work/v1.8.0-implementation-log.md). No tag is created.
