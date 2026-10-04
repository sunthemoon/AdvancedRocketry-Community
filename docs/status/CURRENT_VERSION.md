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
previous_development_handoff: docs/releases/v1.7.0/RELEASE-EVIDENCE.md
runtime_build: 1.20.1-1.8.0-dev
tested_code_commit: 23bec1eb1452e8c8ef37c004606aaed3a2394063
last_updated: 2026-10-04
```

## Current development evidence

The integrated development code, data/art, probes and reviewed contracts are
committed and pushed in scoped phase commits. The latest Java addition is only
an opt-in fixed-cell unload observer, without save-policy or resource changes.
[Current verification](../work/v1.8.0-c16a-save-guard/NATIVE-LIFECYCLE-VERIFICATION-01.md)
records 1,765 JUnit cases in 330 suites, 464 required GameTests and 114 focused
Python cases passing. DataGen has 771 files and zero repeat writes. The fresh
bounded Source23 repository check reports 45 passed. The different-agent
[build evidence audit](../work/v1.8.0-c16a-save-guard/lifecycle-01/reviews/BUILD23-EVIDENCE-01.zip)
records no introduced discrepancy in that exact scope. The 61 ERROR logger
lines remain disclosed, not waived. The two Python-only copied-spawn setup
postimages have separate focused-test and native input pins; they do not claim
a new Java build or another complete repository-check execution.
Their exact source is committed and pushed at
`f945dc1c33f09199da9d47663e891acf1cf4a475`, without runtime-delivery credit.

The development main JAR SHA-256 is
`5fd2a23dd02c8c4977efe9c03628edfe4c517bc1d3d02ba0e846aa89649aeee9`.
The API artifact remains unchanged. This is not a frozen release candidate.
The content ledger has **186 PLANNED units /154 REVIEW assets**; its closure
check still fails. v1.8 remains **IN_PROGRESS /IMPLEMENTING**, with **G0–G9 open**.

All three copied-world lifecycle attempts fail. The original immediate
reacquisition fails live restoration; the new-JAR attempt and the separately
reviewed copied-spawn setup attempt miss the actual unload event within the
unchanged deadline. The last attempt confirms the spawn command, not unloading.
Stopped record retention does not replace live restoration, clean stop or
restart proof. Their logs, independent audits and original failures remain
separate; the remaining cause is not established. No real client or crash
recovery result is supplied, and new guarded writers are not opened.

## Implemented development scope

- C15 materials, plate press acquisition, planetary surfaces and Tau Ceti worlds
  have submitted implementation and review fixes, including radius 224 coverage
  without restricting legal rockets. Real client acceptance remains open.
- C16a combustion generator, five fluids/canisters, motor/casing component
  definitions, pump and pressurized tank have reviewed development code.
  All-tier machine formation is not completed by component registration.
- Recipe-signature preservation and paused unproven legacy tasks, bounded save
  refusal, menu reasons and transport compatibility are integrated. Historical
  restart/reload evidence applies only to its recorded artifact and scope.
- Bank/value models, K1 ordering, K2 bounded bytes and K3 digest are private/model-only
  infrastructure, not completed physical hatch/controller or full codec APIs.

## Confirmed decisions and unfinished work

The owner confirms controller-owned resources across chunks; broken fluid hatches
retain the corresponding controller inventory for rebuilding. Removed charged
power plugs retain FE in their item; charged operations that cannot conserve FE
must refuse, while supported zero-energy operations retain vanilla behavior.
The reviewed ordinary/mode inventories are technical dependencies only: server
creative pre-transform interception and real FE conservation are unimplemented.

Earlier independent reviews record qualified contract acceptance for ADR-064
revision 5, ADR-065 revision 4 and ADR-066 revision 3. The owner's condition is
no unresolved Critical/High/Medium in the reviewed final contract; major semantic
changes still require separate confirmation. The later save-refusal audit below
leaves two disclosure/ADR findings open, so that historical record does not
authorize new guarded runtime admission. These decisions do not supply runtime,
migration or native recovery evidence. C18 D4 remains unproven.

Remaining: bounded observation of the unresolved native unload condition,
Guard lifecycle and first-save/final-unload proof; full hash/frame/
native codecs and resource consumers; physical hatches, lathe and motor-tier
formation; steel and component acquisition; C16b–d, C17–C19; real clients/GPU,
multiplayer, crash recovery, performance and inherited release acceptance.
Whole-chunk save refusal can prevent unrelated chunk changes from being saved;
its impact, repair workflow and release disposition remain open. Historical
failures and oversized evidence-storage debt remain documented, not erased by
passing automatic tests.
Future temporary helpers/output now use the owner's requested project-parent
`D:/GitHub/ARCE-Task-Evidence` location. The tool rejects checked C-script
removal before execution; C cleanup is not complete and no bypass is attempted.

The separate [save-refusal audit](../work/v1.8.0-c16a-save-guard/SAVE-REFUSAL-DISPOSITION-01.md)
records two original Medium disclosure/ADR findings. Their missing facts are now
addressed by the corrected R-021 and an independently reviewed explanation
proposal; the accepted ADR is unchanged and R-021 is still open and unaccepted.
See the [factual correction disposition](../work/v1.8.0-c16a-save-guard/DISCLOSURE-REVIEW-DISPOSITION-01.md).
After the 257th distinct refused
chunk, the guard denies subsequent terrain saves throughout that ServerLevel.
Historical attempts emit EventBus/ChunkMap ERROR pairs with no guard log quota;
dirty state does not prevent unload or provide cross-store durability. No
quarantine/reset/stop policy, runtime risk acceptance or repair proof is selected.

The user assigns this agent sole integration of the existing v1.8 work and
requires phase commits/pushes. User-maintained AGENTS.md and the private document
bundle are excluded. On 2026-10-04 the owner allocates only R-021 correction and
a proposed ADR disclosure amendment, with no save behavior, risk acceptance or
other-row changes. That factual correction is independently reviewed and
separately published; the explanatory amendment remains PROPOSED.
Other newly observed external edits are not silently staged.
The [completion plan](COMPLETION-PLAN.md) lists the remaining leaves. Historical
status checkpoints have moved to the implementation log, not this current-state
summary. No version Gate, tag or release is approved.
