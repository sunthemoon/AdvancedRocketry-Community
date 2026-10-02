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
[ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md), the accepted
v1.8 development baseline is the v1.7 handoff commit. Inherited acceptance
remains open; accepted v1.8 contracts do not imply implemented classic content:

```yaml
active_development_version: v1.8.0
active_development_branch: codex/v1.8.0-classic-content
phase: IMPLEMENTING
execution_state: ACTIVE
accepted_development_baseline: 55da6a58842762c382edce0a5a842d06bb76ff6e
development_log: docs/work/v1.8.0-implementation-log.md
previous_development_handoff: docs/releases/v1.7.0/RELEASE-EVIDENCE.md
runtime_build: 1.20.1-1.8.0-dev
```

v1.8 preparation (C14) is complete. [ADR-060](../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md)
accepts v1.8 development from the v1.7 handoff commit `55da6a5`, separately from
inherited acceptance. [ADR-061](../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md)
(identity, import pipeline, vanilla derivation check, validation),
[ADR-062](../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md) (653 legacy
units, their dispositions and player impact, the C15a–C18d batches) and
[ADR-063](../decisions/ADR-063-MATERIALS-ORES-AND-PLANETARY-SURFACES.md) (the C15 batch)
are frozen after five independent review rounds
([preparation evidence](../work/v1.8.0-preparation/VERIFICATION.md)). The owner amended
ADR-018's campaign trigger (revision 2) and accepted the deferral of terraforming and the
hovercraft past v2.0. C15a (the material set, ores and the small plate press) is delivered
and changes the runtime identity to `1.20.1-1.8.0-dev`
([C15a evidence](../work/v1.8.0-c15a-materials/VERIFICATION.md)); its independent review
and the owner's decision on ADR-063 revision 4 are pending. The next chunk is in
[COMPLETION-PLAN](COMPLETION-PLAN.md). No Gate is claimed.

Previous development version (v1.7, development handoff, release `IN_PROGRESS`):

```yaml
previous_development_version: v1.7.0
previous_development_branch: codex/v1.7.0-endgame-systems
previous_accepted_development_baseline: ab10fb53a580e53a9a1c24097a487f2a7c54ad52
previous_development_log: docs/work/v1.7.0-implementation-log.md
```

v1.7 preparation (C10) is complete. [ADR-053](../decisions/ADR-053-V170-DEVELOPMENT-BASELINE-EXCEPTION.md)
accepts v1.7 development from the v1.6 handoff commit `ab10fb5`, separately from
inherited acceptance. ADR-054..059 freeze the endgame framework (authority,
protection, rate, energy, the transit ledger and audit) and the laser drill,
railgun, black-hole generator, gravity controller and space-elevator contracts
after four independent review rounds and a confirmation round
([preparation evidence](../work/v1.7.0-preparation/VERIFICATION.md)). The runtime identity
is `1.20.1-1.7.0-dev`. C11 (the framework, the laser drill and the gravity field), C12
(the black-hole generator, the transit ledger, the railgun and the space elevator) and
C13 (destruction bounds, performance, native recovery evidence, three independent review
rounds and the operator guide) are complete. The
[v1.7 development handoff](../releases/v1.7.0/RELEASE-EVIDENCE.md) lists the evidence and
the open acceptance: V0/V1/V2, S2 with real players, reference-hardware performance and
the flush-cost gate (ADR-054 §7), the candidate-bound matrix and the final audit and
human decision. Release status remains `IN_PROGRESS`, not `PASSED`. No Gate is claimed. The v1.6 facts below are unchanged.

Previous development version (v1.6, development complete, release `IN_PROGRESS`):

```yaml
previous_development_version: v1.6.0
previous_development_branch: codex/v1.6.0-satellite-resource-missions
previous_accepted_development_baseline: 940a5ed3b90a4da0ca4e43417b2bddf41ebb7307
previous_development_log: docs/work/v1.6.0-implementation-log.md
```

v1.6 preparation (C6) is complete. [ADR-048](../decisions/ADR-048-V160-DEVELOPMENT-BASELINE-EXCEPTION.md)
accepts v1.6 development from the v1.5 closure commit `940a5ed`, separately from
inherited acceptance. ADR-049..052 freeze the satellite, scheduler, resource-mission
and reward contracts after three independent review rounds
([preparation evidence](../work/v1.6.0-preparation/VERIFICATION.md)). C7 (satellite
components, the Satellite Builder, launchable kinds, survey scans and microwave
receivers) is implemented and closed after two independent reviews, which also
accepted ADR-049 and ADR-050 revision 4 ([closure](../work/v1.6.0-c7-close/VERIFICATION.md)).
The runtime identity is `1.20.1-1.6.0-dev`. C8a (write policy, limits, budgets,
retention and resource tables), C8b (resource missions and terminal delivery) and C9
(the independent review of C8, the native recovery and performance runs) are complete.
The [v1.6 development handoff](../releases/v1.6.0/RELEASE-EVIDENCE.md) lists the evidence
and the open acceptance: V0/V1/V2, S2 with real players, reference-hardware performance,
the candidate-bound matrix and the final audit and human decision. Release status
remains `IN_PROGRESS`, not `PASSED`. The next development chunk is v1.7 (C10 in
[COMPLETION-PLAN](COMPLETION-PLAN.md)), in a new session. No Gate is claimed. The v1.5
facts below are unchanged.

Earlier development version (v1.5, development complete, release `IN_PROGRESS`):

```yaml
previous_development_version: v1.5.0
previous_development_branch: codex/v1.5.0-orbital-station-warp
previous_accepted_development_baseline: 6f530ac7db4bf0e06be6d6aaef35e6ca31a5651b
previous_development_log: docs/work/v1.5.0-implementation-log.md
```

The maintainer paused advancement on 2026-09-30 and resumed it the same day.
The station model/migration slice (STATION-01/02) passed 943 unit tests,
237 GameTests and two finite copied-world native starts. It is committed with
its [archived evidence](../work/v1.5.0-station-schema/VERIFICATION.md); see the
[checkpoint](../work/v1.5.0-implementation-log.md#checkpoint--2026-09-30-paused-then-resumed).
STATION-03, confirmed owner/operator expansion with a checked commit, was
independently reviewed and its findings fixed
([verification](../work/v1.5.0-station-expansion/VERIFICATION.md)). STATION-04
passed a native v1.4-world team/upgrade/expansion/restart check
([verification](../work/v1.5.0-station-native/VERIFICATION.md)). Both were
re-reviewed and then confirmed by the final review. ORBIT-02 station gravity and
environment display are implemented under accepted ADR-041
([verification](../work/v1.5.0-orbit-environment/VERIFICATION.md)). Star systems
with the Tau Ceti example are implemented under accepted ADR-043
([verification](../work/v1.5.0-star-systems/VERIFICATION.md)). Warp follows accepted
ADR-044 revision 3; WARP-02 (station root schema 4, balances and the checked
relocation) is implemented
([verification](../work/v1.5.0-warp-schema/VERIFICATION.md)), and so is WARP-03
(warp core, commands and countdown;
[verification](../work/v1.5.0-warp-core/VERIFICATION.md)), and WARP-04 (rocket
authority; [verification](../work/v1.5.0-warp-rockets/VERIFICATION.md)) and WARP-05
(diagnostics and native warp restarts;
[verification](../work/v1.5.0-warp-native/VERIFICATION.md)). An independent review
of STAR, capacity and WARP-02 was applied
([record](../work/v1.5.0-slices-review/VERIFICATION.md)), then a review of WARP-03/04
([record](../work/v1.5.0-review-closure/VERIFICATION.md)), and two final reviews of
the remaining slices ([closure](../work/v1.5.0-closure/VERIFICATION.md)). The one
High finding (WARP review R1) was fixed; the final reviews found no Critical or High
issue, and every Medium finding is fixed. The
[v1.5 development handoff](../releases/v1.5.0/RELEASE-EVIDENCE.md) (ACC-01) lists
the evidence and the open acceptance: V0/V1/V2, S2, reference-hardware performance,
ORBIT-04's native load items, the candidate-bound matrix (ACC-02) and the final audit
and human decision (ACC-03). Release status remains `IN_PROGRESS`, not `PASSED`.
The next development chunk is v1.6 (C6 in [COMPLETION-PLAN](COMPLETION-PLAN.md)).

Use the [v1.5 implementation log](../work/v1.5.0-implementation-log.md) for active
tasks. The first runtime slice implements the accepted station model and
pre-start migration contract, retaining old station identity/geometry. The
acceptance remains open current-version work. The per-station sky
([verification](../work/v1.5.0-orbit-sky/VERIFICATION.md); V0/V1/V2 open), the
elevator endpoint validator and the A1 permission matrix
([verification](../work/v1.5.0-elevator-ui/VERIFICATION.md); ADR-046 records that
v1.5 has no control screen), and native rocket identity, concurrent warp,
interruption and the S1 subset with the recovery matrix
([verification](../work/v1.5.0-mig-native/VERIFICATION.md)) are implemented,
not metadata-only substitutes.

The [v1.4 development handoff](../releases/v1.4.0/RELEASE-EVIDENCE.md) and
[v1.4 implementation log](../work/v1.4.0-implementation-log.md) retain implemented
planetary content, artifact-specific reload/migration/recovery results and
remaining acceptance. Those results do not verify new station/warp code.
ADR-030 is not extended; the new decision is version-limited. No candidate, tag,
human Gate approval or change to `GATE_STATUS.md` follows from this pointer.
