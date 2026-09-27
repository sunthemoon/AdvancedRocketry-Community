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

The acceptance cursor above remains at the earliest unfinished release Gate;
it is not the active feature-development branch. Under accepted
[ADR-030](../decisions/ADR-030-V140-DEVELOPMENT-BASELINE-EXCEPTION.md), scoped
v1.4 development proceeds from the immutable v1.3 baseline while inherited
acceptance remains open:

```yaml
active_development_version: v1.4.0
active_development_branch: codex/v1.4.0-planetary-expansion
accepted_development_baseline: 1a192b4b9b9a90372c11643e086f7b0fbdcd1860
development_log: docs/work/v1.4.0-implementation-log.md
development_handoff: docs/releases/v1.4.0/RELEASE-EVIDENCE.md
development_checkpoint: d33552a5374272b3b0669090ec4466075662ee4c
```

Use the [v1.4 implementation log](../work/v1.4.0-implementation-log.md) for active
tasks. DATA-01 has implemented and verified schema-2 definitions, optional Level
mappings, capability-aware consumers and display protocol 2; see
[slice evidence](../work/v1.4.0-data01/VERIFICATION.md). DATA-02/03 now implement
and verify joint bounded reload, error retention and packaged restart; see
[reload evidence](../work/v1.4.0-data02/VERIFICATION.md). MAP-01 adds persistent
body/Level bindings, removal retention and checked startup/reload publication;
see [binding evidence](../work/v1.4.0-map01/VERIFICATION.md), including its
prospective-adoption limitation. MAP-02 implements actual Mars/Venus worlds,
an unmapped gas giant, physical travel/landing and planetary station creation;
see [world evidence](../work/v1.4.0-map02/VERIFICATION.md). Its reverse-binding
diagnostic follow-up is corrected. ENV implements opt-in temperature/pressure/
sunlight effects, passive equipment and supplied climate-room protection; see
[environment evidence](../work/v1.4.0-env/VERIFICATION.md). NAV implements the
interactive console star map, dynamic station choices and generation-coherent
server navigation; see [navigation evidence](../work/v1.4.0-nav/VERIFICATION.md).
SKY implements bounded resource-pack sky/fog/sun/stars and lifecycle-bound
ambience for Moon/Mars/Venus/shared Space; see
[presentation evidence](../work/v1.4.0-sky/VERIFICATION.md).
DISC connects opt-in destination permission to shared celestial discoveries and
private satellite research, with locked star-map feedback and flight protocol 8;
see [discovery evidence](../work/v1.4.0-discovery/VERIFICATION.md).
MIG-01 implements ordered durable research/discovery writes, historical paid
receipt repair and bounded runtime replay; see
[recovery evidence](../work/v1.4.0-mig-claims/VERIFICATION.md). MIG-02 verifies an
authentic v1.3 development-world copy and coherent removal/restoration through
four clean processes; see [migration evidence](../work/v1.4.0-mig-worlds/VERIFICATION.md).
MIG-03 verifies four finite packaged two-store interruptions and unedited
same-world restart, with independent native readback; see
[cut evidence](../work/v1.4.0-mig-cuts/VERIFICATION.md). Uncommitted READY remains
unpaid, paid receipts recover automatically and repeated claims do not repay.
This does not certify arbitrary hardware power loss or unrelated subsystem cuts.
Real-client navigation/presentation/discovery acceptance and remaining
migration/acceptance are still pending. The [v1.4 development handoff](../releases/v1.4.0/RELEASE-EVIDENCE.md)
maps implemented capabilities, matching-artifact recovery evidence and deferred
G0-G9 acceptance. It is not a release tag or Gate approval. The next content
milestone is v1.5 Orbital/Station/Warp; production implementation needs its own
development-order decision and frozen contracts, not an implied ADR-030 extension.
The [v1.3 development handoff](../releases/v1.3.0/RELEASE-EVIDENCE.md)
retains the prior implemented scope, artifact evidence and outstanding
acceptance. This pointer does not approve any candidate or change `GATE_STATUS.md`.
