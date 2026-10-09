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
latest_source_checkpoint: ce64a8eb79c0f6fb53f000f0c2a0e4e947ef9985
pending_graph_source_candidate: ""
pending_sleep_observation_source_candidate: ""
pending_sleep_observation_qualification: DEVELOPMENT_QUALIFIED_AND_INTEGRATED_TWENTY_ROWS_UNEXECUTED
sleep_d1_outcome_contract: FROZEN_INDEPENDENTLY_REVIEWED_IMPLEMENTATION_PREREQUISITES_OPEN
tested_code_commit: d57ecda1f11dab76d882f945b0de606534ef4fb9
native_tested_code_commit: d57ecda1f11dab76d882f945b0de606534ef4fb9
latest_regression_target_commit: d57ecda1f11dab76d882f945b0de606534ef4fb9
latest_regression_result: NATIVE_PASS_STRICT_TIMEOUT_GATES_OPEN
latest_regression_run: sleep-nested-source-qualification-20261009-28/native-01
latest_regression_attempt: 1
latest_regression_evidence: SEALED_FRESH_VERIFIER_STANDARD_REGRESSION_ROOT_PAYLOAD_AUDITED
latest_regression_observed_utc: 2026-10-09T07:30:06.547652Z
actual_unit_rerun: sleep-nested-source-qualification-20261009-28/test-01.command.json
actual_unit_rerun_observed_utc: 2026-10-09T07:22:55.516Z
tested_python_commit: 5582e3c49d548c5c002ef6c7cd45b1296f2f2703
last_updated: 2026-10-09
```

## Current development evidence

The [final observation test-source integration32](../work/v1.8.0-c18a-sleep/NESTED-QUALIFICATION-32.md)
is normally merged/pushed at ce64a8eb. Actual fresh standard qualification is at
d57ecda1; the complete src tree and ten build/consumer inputs match Main, without
a merge-SHA rerun claim. Forced clean build and separate explicit test each run
2192 JUnit /381 XML /zero failures/errors/skips. Both forced DataGen/diff cohorts
pass; all 594 required GameTests pass in 151 discovered batches. Native logs
retain 62 unwaived ERROR /zero FATAL; strict remains 180-second TIMEOUT. Corrected
offline publication/API consumer pass, with the original split-argument failure
retained. Only the separate adapter fixture contains the new observation classes;
production host/API/sources are unchanged. The opt-in property and twenty console
rows are unexecuted. Task28's six owned disposable outputs are cleaned once;
old denied peer outputs/incidents remain. Complete D1 proposal29 and independent31
review are sealed, with no new material correction and all implementation/runtime
prerequisites open. [D1 disposition33](../work/v1.8.0-c18a-sleep/D1-OUTCOME-FREEZE-33.md)
separately freezes only its intended outcomes, not implementation or runtime proof. Static compressed
log revision34 and final independent35 are sealed; [disposition39](../work/v1.8.0-c18a-sleep/NATIVE-LOG-DISPOSITION-39.md)
freezes only narrowed refusal requirements. Whole-driver adoption/executable
freeze and actual runtime/cumulative admission remain open. Separately assigned
offline JSON candidate37, addendum43 and complete independent40/49 reviews are sealed.
[Normative disposition52](../work/v1.8.0-c18a-sleep/OFFLINE-CORE-CONTRACT-FREEZE-52.md)
freezes the generic decoder/API/diagnostic and unchanged finite qualification
contract only. Low R01 is addressed at the normative level; no decoder/tests are
implemented or qualified. Actual44 qualifies only named local peak-so-far queries;
R49-01/U01/U02 remain open. Separately sealed50 post-exit observations await
independent55 assessment; no terminal resource, helper/quota or native authority.
[Completed provenance audit38](../work/v1.8.0-c18a-sleep/OFFLINE-CORE-AND-DIAGNOSTICS-STATUS-42.md)
distinguishes injected native saves from direct event posts, with three recipe
attribution gaps and existing High log/R-021 policy/evidence admission obligations
still open. No implementation or error waiver. Task28's .log-only retained files do not prove
original rotation completeness; standard-test and capture-runtime evidence are
distinct. All Gates remain open.

The preceding [ordinary tool development integration](../work/v1.8.0-c18b-jackhammer/SOURCE-INTEGRATION-01.md)
is normally merged/pushed at `80bbf16d9e76708b7e39c50d21b3cff1a66432ef` after
complete independent actual-source and native-receipt reviews. Actual tested
source remains 3b18a6bc; complete src, ten Gradle/consumer inputs and two provenance/
plan blobs match the merge, without claiming a merge-SHA Gradle/native rerun.
Root and fresh independent forced clean build/explicit test each execute 2,192
JUnit /381 XML /zero failures/errors/skips; all 579 required GameTests pass.
Twice forced DataGen leaves empty diffs. Strict is the unchanged 180-second
TIMEOUT, not a passed link report. Native logs retain 62 unwaived ERROR headers /
zero FATAL. Publication and API consumer pass; API artifact remains unchanged.
Actual two-process native inventory continuation is independently receipt-audited,
not independently launched: native logout saves Damage 18, next process loads it
before ordinary use reaches 19. It is not online-stop-first-save, crash, preserved
mined terrain/loot or real-client proof. Human asset, survival and full-item
acceptance remain open. All original failed cohorts/setup/record corrections and
owned cleanup limits remain in immutable evidence; no Claude is called. A fresh
[fixed integration-record audit](../work/v1.8.0-c18b-jackhammer/INTEGRATION-REVIEW-STATUS-01.md)
independently verifies the archived source/merge/cohort/receipt attribution without
new runtime or Gate acceptance.

The preceding [detector admission qualification](../work/v1.8.0-c18a-seal-detector/ADMISSION-VERIFICATION-03.md)
remains an integrated test slice. Its exceptional worker cleanup correction retains
unresolved fixtures rather than recovering worlds automatically. Historical counts,
strict failures, original/erratum records and policy-denied own temporary retention
remain in the implementation log/evidence, not new current regression claims.
The interrupted earlier record reviewer is not counted as completed.

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
recorded for those hatch decisions. Full-slice packaged/restart, prior-world, installed continuation, V1/V2,
progression, performance and all Required Gates are unfinished. No content
delivery, release approval or completion of v1.8 is inferred from this slice.

The [ordinary jackhammer leaf](../work/v1.8.0-c18b-jackhammer/TASK-01.md)
has [bounded adoption](../work/v1.8.0-c18b-jackhammer/ADOPTION-01.md) and integrated
[source qualification](../work/v1.8.0-c18b-jackhammer/SOURCE-STATUS-01.md).
Its checksum-notification discrepancy is corrected by a separate immutable erratum,
without altering the original CRLF manifest or any receipt. Full tool acceptance,
above-tier/mod/native permission coverage and COMMON client synchronization are open.
Titanium/motor survival progression remains unfinished. The owner has selected
[actual off-world sleep](../work/v1.8.0-c18a-sleep/OWNER-DECISION-01.md), conditional
on separate freezing/review of dimension, spawn-point and time behavior before
implementation. Read-only research has produced a
[proposed contract](../work/v1.8.0-c18a-sleep/RESEARCH-STATUS-01.md). Its
[independent review](../work/v1.8.0-c18a-sleep/CONTRACT-REVIEW-STATUS-01.md) identifies
occupancy/air and Space-context corrections; both exact bed/spawn and atmosphere
feasibility reports are sealed. The owner has subsequently
[accepted the exact native ancillary candidate](../work/v1.8.0-c18a-sleep/OWNER-DECISION-03.md),
with [narrowed D1 outcomes now frozen and independently reviewed](../work/v1.8.0-c18a-sleep/D1-OUTCOME-FREEZE-33.md).
Implementation and native verification prerequisites remain open. No gameplay policy
has been changed and no production sleep implementation source is assigned.

The separate [C18a-SLEEP-01](COMPLETION-PLAN.md) leaf now has
[sealed spawn-boundary research](../work/v1.8.0-c18a-sleep/SPAWN-RESEARCH-STATUS-02.md).
Its stateless public caller candidate has no runtime/coexistence/indeterminate
failure qualification; B1 and dimension/spawn/time implementation/runtime proof
remain open. D1's narrowed outcomes are independently reviewed and frozen in33;
all thirteen D1 runtime rows remain UNEXECUTED.
The [exact ancillary question](../work/v1.8.0-c18a-sleep/OWNER-QUESTION-02.md)
is answered by decision 03. This research is not current game regression evidence.

The [sealed atmosphere/access proposal](../work/v1.8.0-c18a-sleep/AIR-RESEARCH-STATUS-02.md)
retains M1's unproved neutral transition and distinguishes registered-bed
pre-admission invalidation from occupancy/adjacent-door notifications. M2 proposes
bounded actual-host/live-access checks, with the gap/station policy not selected.
The [complete task-03 review](../work/v1.8.0-c18a-sleep/BOUNDARY-REVIEW-STATUS-03.md)
preserves these prerequisites and adds R1 callback-argument/inherited-receiver
qualification within B1. A separate
[test-only observation proposal](../work/v1.8.0-c18a-sleep/PROPOSED-OBSERVATION-01.md)
has [complete independent review](../work/v1.8.0-c18a-sleep/OBSERVATION-REVIEW-STATUS-04.md)
and a [narrow source-only assignment](../work/v1.8.0-c18a-sleep/OBSERVATION-ASSIGNMENT-05.md)
for two opt-in adapter fixtures/twenty cases. No game/driver or actual off-world
sleep is assigned. No off-world dimension, spawn or time
implementation is authorized by that observation review or the conditional
ancillary owner answer.

The [older corrected checkpoint08](../work/v1.8.0-c18a-sleep/SOURCE-REVIEW-STATUS-08.md)
preserves actual 5b findings, corrections and two separate historical short
qualification cohorts. The current d57 integration/result is in32 above, not a
rebound 5b run. No twenty console rows or off-world behavior were executed.
Strict timeout, unwaived native ERROR logs and policy-denied output retention
remain. A corrected-schema driver proposal is sealed but not executable-frozen. Complete
[contract/wire disposition 18](../work/v1.8.0-c18a-sleep/DRIVER-REVIEW-STATUS-18.md)
records two Medium and one Low contract findings, supported raw Forge routing
and typed SpawnDimension, plus an additional nested-receiver preflight boundary
and unfinished whole-trace token proof. The later
[checkpoint27](../work/v1.8.0-c18a-sleep/NESTED-BOUNDARY-STATUS-27.md) preserves
the then-isolated d57 source and complete independent patch review, with historical
receiver findings source-addressed, not universal runtime proof. Five compiles
pass; their source-review cohorts only compile the six new tests. Separate
standard qualification28 and D1 preparation29/review31 are now sealed in32.
Complete independent
proposal03 review26 identifies Medium compressed-log rotation interpretation
and Low reachable-identifier wording; the latter is additively clarified in27.
No observation driver/parser or production change is authorized. Only the
reviewed adapter test source is merged. Two independent cleanup generations remain policy-denied/retained;
Task20's external 37-byte miscreation remains after a denied one-time correction.
Its proposal is sealed but not accepted, with runtime allocation authority and
actual cumulative pre-run evidence accounting still required.
The owner-record review also reports fifteen pre-existing historical log links
to locally untracked ZIPs absent from its fixed Git trees; whole-log portability
is not approved. No production policy, ledger delivery or Gate closes.

The [completion plan](COMPLETION-PLAN.md) remains the execution list; prior
current-state evidence is preserved in the
[implementation log](../work/v1.8.0-implementation-log.md). No tag is created.
