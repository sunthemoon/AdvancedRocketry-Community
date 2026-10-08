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
latest_source_checkpoint: 79cb3e91c84595672b8f4de8b6a4d8b85cf3337a
pending_graph_source_candidate: ""
tested_code_commit: 3939dd55f8f020a33d7f1c7c5d55bd6caa237c7b
native_tested_code_commit: 3939dd55f8f020a33d7f1c7c5d55bd6caa237c7b
latest_regression_target_commit: 3939dd55f8f020a33d7f1c7c5d55bd6caa237c7b
latest_regression_result: NATIVE_PASS_STRICT_LINK_FAILURE_GATES_OPEN
latest_regression_run: hatch-native-load-order-root-20261008-01/cohort-02
latest_regression_attempt: 2
latest_regression_evidence: SEALED_ROOT_AND_INDEPENDENT_DEVELOPMENT_REGRESSION
latest_regression_observed_utc: 2026-10-08T11:34:42.190684Z
tested_python_commit: 5582e3c49d548c5c002ef6c7cd45b1296f2f2703
last_updated: 2026-10-08
```

## Current development evidence

The [native LOAD-order qualification](../work/v1.8.0-c16a-hatches/NATIVE-LOAD-ORDER-VERIFICATION-21.md)
is independently source-reviewed and integrated/normally pushed at
`79cb3e91c84595672b8f4de8b6a4d8b85cf3337a`. Actual tested source is
`3939dd55f8f020a33d7f1c7c5d55bd6caa237c7b`; complete src and seven Gradle
inputs match, without a rerun of the merge SHA. No Claude is called.

Root and independent Codex each pass clean build, 2,179 actual JUnit,
all 548 required GameTests, two DataGen runs and empty diffs. Two new cases
exercise ordinary survival/creative native placement and pre-tick serialization.
Pinned bytecode distinguishes synchronous in-Level new-owner onLoad from bulk
chunk fresh queues. The hatch's two LOADs are fresh tickets in one invocation,
not two callbacks. This qualifies only the normal-route U2 scheduling concern;
it does not establish physical hatch, source/outcome or save/terminal authority.

Original two helper-login fixture failures and the reviewer's initial launcher
failure remain retained. Corrected source has no unresolved introduced finding.
Strict repository validation still has 44 passing checks / one inherited link
failure; both native logs retain 62 unwaived ERROR headers and zero FATAL.
Both new owned cleanup attempts succeed once; no task disposable debt remains.
Earlier refused targets and inherited cleanup debt remain untouched/open.

The ledger stays 186 PLANNED /154 REVIEW assets. C16-C19, both Medium physical
prerequisites, U1/U3-U7, O1/O2/O3, ADR-068 PROPOSED and R-021 OPEN remain.
Packaged/restart, prior-world, installed continuation, V1/V2, progression,
performance and all Required Gates are unfinished. No content delivery,
release approval or completion of v1.8 is inferred from this test slice.

The [completion plan](COMPLETION-PLAN.md) remains the execution list; prior
current-state evidence is preserved in the
[implementation log](../work/v1.8.0-implementation-log.md). No tag is created.
