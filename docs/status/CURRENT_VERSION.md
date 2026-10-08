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
latest_source_checkpoint: ee77c51aef7613753a85a156a84cd817adfb2529
pending_graph_source_candidate: ""
tested_code_commit: 23bcb6b141da88a7a9548c2aa8f3de5438a09e68
native_tested_code_commit: 23bcb6b141da88a7a9548c2aa8f3de5438a09e68
latest_regression_target_commit: 23bcb6b141da88a7a9548c2aa8f3de5438a09e68
latest_regression_result: NATIVE_PASS_STRICT_LINK_FAILURE_GATES_OPEN
latest_regression_run: atmosphere-exposure-root-20261008-01/repair
latest_regression_attempt: 1
latest_regression_evidence: SEALED_ROOT_AND_INDEPENDENT_DEVELOPMENT_REGRESSION
latest_regression_observed_utc: 2026-10-08T10:10:58.299576Z
tested_python_commit: 5582e3c49d548c5c002ef6c7cd45b1296f2f2703
last_updated: 2026-10-08
```

## Current development evidence

The [atmosphere exposure repair](../work/v1.8.0-c18a-airlock/EXPOSURE-REPAIR-VERIFICATION-01.md)
is independently actual-source reviewed and integrated/normally pushed at
`ee77c51aef7613753a85a156a84cd817adfb2529`. The actual tested commit is
`23bcb6b141da88a7a9548c2aa8f3de5438a09e68`; complete src and seven Gradle
inputs match the integration, which is not a rerun of the merge commit.

Root and independent Codex separately pass clean build, 2,179 actual JUnit,
all 546 required GameTests, two DataGen runs and empty diffs. Four new native
regressions precede the repair; two demonstrate the incorrect OPEN outcome
under transparent or newly closed roofs. Existing airlock cases, limits,
deadlines, registry/schema/network and persistence behavior are unchanged.
Historical failures are retained and not uniquely attributed by these fresh
cohorts. No Claude call is made.

Strict repository validation remains 44 checks passing / one Markdown-link
failure. Each repaired native log retains 62 unwaived ERROR headers and zero
FATAL. New Root and reviewer cleanup attempts were rejected before execution;
their own disposable output remains, with no retry or bypass.

The ledger remains 186 PLANNED /154 REVIEW assets. C16 machine families,
physical hatch/save qualification and components/progression; C17 typed
propulsion and station devices; C18 equipment/life support/HUD/audio; and C19
content/provenance/release closure remain incomplete. R-021 and the unaccepted
physical-operation/save prerequisites remain open. Packaged/restart,
prior-world, natural continuation, V1/V2, performance, progression and all
Required Gates are not proved. This is a verified defect slice, not content
delivery, release approval or completion of v1.8.

The [completion plan](COMPLETION-PLAN.md) remains the complete execution list.
Former current-state checkpoints are preserved in the
[implementation log](../work/v1.8.0-implementation-log.md). No tag is created.
