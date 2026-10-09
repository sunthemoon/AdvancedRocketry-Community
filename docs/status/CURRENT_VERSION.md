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
latest_source_checkpoint: b001316102d064545fb81d9f4f2141ae52931118
pending_graph_source_candidate: ""
tested_code_commit: 452fd2a0dc12895c2e2b955c80115457459888bb
native_tested_code_commit: 452fd2a0dc12895c2e2b955c80115457459888bb
latest_regression_target_commit: 452fd2a0dc12895c2e2b955c80115457459888bb
latest_regression_result: NATIVE_PASS_STRICT_LINK_FAILURE_GATES_OPEN
latest_regression_run: seal-admission-root-20261009-01/cohort-02
latest_regression_attempt: 2
latest_regression_evidence: SEALED_ROOT_AND_INDEPENDENT_DEVELOPMENT_REGRESSION
latest_regression_observed_utc: 2026-10-08T17:31:42.070196Z
actual_unit_rerun: seal-admission-root-20261009-01/cohort-02/build.log
actual_unit_rerun_observed_utc: 2026-10-08T17:15:05.092363Z
tested_python_commit: 5582e3c49d548c5c002ef6c7cd45b1296f2f2703
last_updated: 2026-10-09
```

## Current development evidence

The [detector admission qualification](../work/v1.8.0-c18a-seal-detector/ADMISSION-VERIFICATION-03.md)
is independently actual-source reviewed and integrated/normally pushed at
`b001316102d064545fb81d9f4f2141ae52931118`. Actual tested source is 452fd2a0;
complete src and seven Gradle inputs match, without rerunning the merge SHA.
Preparation and corrective scope are published before their respective edits.
The original Medium exceptional worker-cleanup finding is addressed only for
retaining unresolved fixtures before cleanup, not automatic world recovery.
The new main-source GameTest enters normal JARs; production behavior, IDs,
assets and API JAR remain unchanged. No Claude is called.

Root and independent corrected forced build/test each execute 2,183 actual
JUnit /379 XML /zero failures, errors or skips; all 565 required GameTests pass.
Repeated DataGen leaves empty diffs; first-run cache write counters are separately
attributed. Strict validation retains 44 passing checks/one inherited link
failure and each native log retains 62 unwaived ERROR headers/zero FATAL.
Original independent narrative counter error is corrected by a separate erratum,
not an edit to its immutable report. Original findings/setup failures remain
distinct. Source evidence and the exact-preimage independent record review are
sealed; compact review evidence accompanies the qualification record. No new
scoped record finding is identified. The reviewer's native cleanup launcher is
policy-denied before script loading: its unsealed temporary clone remains,
excluded from the bounded manifest/archive, with no retry or Root takeover.
The earlier interrupted record reviewer is not counted as completed. No
records-only result substitutes for a new Gradle/native or full-item check.

Previous [installed detector rule qualification](../work/v1.8.0-c18a-seal-detector/INSTALLED-RUNTIME-VERIFICATION-02.md)
remains completed only as a test slice. Its Low preparation-log timing deviation
is still explicit and unwaived. Remaining detector Level/chunk/cell/query-order,
installed precedence/lifecycle, unlock, packaged/restart and actual client
obligations are unfinished. No full item/ledger delivery or Required Gate closes.

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

The next [ordinary jackhammer leaf](../work/v1.8.0-c18b-jackhammer/TASK-01.md)
has [bounded adoption](../work/v1.8.0-c18b-jackhammer/ADOPTION-01.md) after fresh
independent static numeric/data/config/visual review. The committed isolated
[source qualification](../work/v1.8.0-c18b-jackhammer/SOURCE-STATUS-01.md) retains
the original failed candidates. Corrective forced source/Root commands and all
579 required native cases pass; Root's bounded two-process inventory continuation
is independently receipt-audited. Final complete source sealing and a checksum-
notification correction remain pending. No tool source has been integrated into Main.
Titanium/motor survival progression remains unfinished. The owner has selected
[actual off-world sleep](../work/v1.8.0-c18a-sleep/OWNER-DECISION-01.md), conditional
on separate freezing/review of dimension, spawn-point and time behavior before
implementation. Read-only research has produced a
[proposed contract](../work/v1.8.0-c18a-sleep/RESEARCH-STATUS-01.md). Its
[independent review](../work/v1.8.0-c18a-sleep/CONTRACT-REVIEW-STATUS-01.md) identifies
occupancy/air and Space-context corrections; exact bed/spawn and atmosphere
feasibility research continue, and the ancillary owner choice remains pending. Those
policies are unchanged and no sleep implementation source is assigned.

The [completion plan](COMPLETION-PLAN.md) remains the execution list; prior
current-state evidence is preserved in the
[implementation log](../work/v1.8.0-implementation-log.md). No tag is created.
