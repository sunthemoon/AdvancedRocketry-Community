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
[ADR-039](../decisions/ADR-039-V150-DEVELOPMENT-BASELINE-EXCEPTION.md), the accepted
v1.5 development baseline is the immutable v1.4 handoff. Inherited acceptance
remains open; implemented station storage does not imply completed station/warp gameplay:

```yaml
active_development_version: v1.5.0
active_development_branch: codex/v1.5.0-orbital-station-warp
phase: IMPLEMENTING
execution_state: ACTIVE
paused_at: 2026-09-30
resumed_at: 2026-09-30
accepted_development_baseline: 6f530ac7db4bf0e06be6d6aaef35e6ca31a5651b
development_log: docs/work/v1.5.0-implementation-log.md
previous_development_handoff: docs/releases/v1.4.0/RELEASE-EVIDENCE.md
runtime_build: 1.20.1-1.5.0-dev
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
([verification](../work/v1.5.0-station-native/VERIFICATION.md)). Both await an
independent re-review; its findings are being fixed. ORBIT-02 station gravity and
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
([record](../work/v1.5.0-slices-review/VERIFICATION.md)); the later slices await
review. Release
status remains `IN_PROGRESS`, not `PASSED`.

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
