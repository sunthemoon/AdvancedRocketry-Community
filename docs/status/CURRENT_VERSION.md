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
diagnostic follow-up is corrected. Environmental response/protection, navigation,
discovery, custom skies and remaining migration/acceptance are still pending.
The [v1.3 development handoff](../releases/v1.3.0/RELEASE-EVIDENCE.md)
retains the prior implemented scope, artifact evidence and outstanding
acceptance. This pointer does not approve any candidate or change `GATE_STATUS.md`.
