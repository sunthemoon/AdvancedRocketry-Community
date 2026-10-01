# v1.6.0 development evidence handoff

**IN_PROGRESS — not a release candidate, stable release or Gate approval.**
This handoff indexes the v1.6 satellite and resource-mission work: what is implemented,
the evidence retained for it, and the acceptance that is still open. This is an
unofficial community rewrite.

## Development identity

- Branch `codex/v1.6.0-satellite-resource-missions`, build `1.20.1-1.6.0-dev`. The
  handoff commit is the one that adds this file; its parent is
  `eb5b80e1aaa6d427e6e3f879f468a814c1bb336f`.
- Minecraft 1.20.1, Java 17, Forge 47.4.10.
- Public API 1.7: no API source changed since the v1.4 baseline.
- Network: the new `satellite` channel is protocol 1 (terminal view and scan results,
  ADR-049 §10). The celestial display channel stays at 3; flight 8, life support 1 and
  rocket visuals 1 are unchanged.
- Storage:
  - satellite registry root schema 3, with satellite schema 2, mission schema 2 and
    instance schema 1 (ADR-050);
  - Satellite Terminal root schema 2 (ADR-051);
  - other managed stores unchanged.
- Exact bytes of the tested JARs: [development-artifacts.json](development-artifacts.json).
- Candidate commit and artifact, release tag, final candidate reviewer and human
  release approver: **not assigned**.

[ADR-048](../../decisions/ADR-048-V160-DEVELOPMENT-BASELINE-EXCEPTION.md) permits this
development from the v1.5 development handoff; it is not a passed v1.5 release. The
[release-acceptance cursor](../../status/CURRENT_VERSION.md) remains v1.0, and
inherited Gates are not rewritten here.

## Implemented capabilities

| Area | What exists | Main record |
|---|---|---|
| Contracts | ADR-049 to ADR-052 accepted after three review rounds; ADR-049 and ADR-050 revision 4 after the C7 review | [preparation](../../work/v1.6.0-preparation/VERIFICATION.md), [C7 closure](../../work/v1.6.0-c7-close/VERIFICATION.md) |
| Satellites | Components and blueprints; the Satellite Builder; data, survey, solar, asteroid-miner and gas-harvester kinds; idle launches, decommission and `blank-chip`; lifetime-research and discovery gates | [C7a](../../work/v1.6.0-c7a-model/VERIFICATION.md), [C7b](../../work/v1.6.0-c7b-builder/VERIFICATION.md) |
| Survey scan and power | Area scans on the satellite channel; the Microwave Receiver with link claim, hold, release and unlink | [C7c](../../work/v1.6.0-c7c-scan-receiver/VERIFICATION.md) |
| Registry | Root 3 with pre-start migration and backup; write policy with coalesced flush and save epoch; configurable limits; byte budgets with lifecycle reservation; retention with discovery evidence; load invariants, quarantine and operator commands | [C7a](../../work/v1.6.0-c7a-model/VERIFICATION.md), [C8a-1](../../work/v1.6.0-c8a-scheduler/VERIFICATION.md) |
| Resource tables | Versioned asteroid and gas tables; `survey-v1`, `asteroid-v1`, `gas-v1`; server seeds; `mission verify` | [C8a-2](../../work/v1.6.0-c8a-resources/VERIFICATION.md) |
| Resource missions | Surveys and asteroid instances; asteroid and gas missions bound to a persisted terminal; terminal delivery (buffer, receipts, chunk-tag persistence); the reconciliation table; rebind, purge and instance release; audit lines; the delivery panel | [C8b](../../work/v1.6.0-c8b-delivery/VERIFICATION.md), [C9 review](../../work/v1.6.0-c9-review/VERIFICATION.md) |
| Recovery | Native: v1.5 world upgraded; 1,000 missions through restarts and a backlog; four crash cuts around claims; [recovery matrix](RECOVERY-MATRIX.md) | [C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md) |

Not implemented here (deferred by contract):
- physical asteroid fields and rocket-carried drills or intakes (v1.8 porting matrix);
- automation access to the reward buffer;
- orbital lasers and other v1.7 endgame systems;
- direct 1.12.2 saves.

## Latest development checks

On the tree committed with this handoff
([C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md) full run):
- `./gradlew clean build test runData runGameTestServer`: exit 0;
- 1,157 JUnit tests with 0 failures, errors or skips;
- all 290 required GameTests passed;
- the generated diff is empty.

Native, on the same host JAR (`452885f0…`): the C9 harness passed every phase
([C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md)). That covers the upgrade,
the 1,000-mission load and backlog, the four crash cuts, the final restart and the
worst-case flush measurement. Every failed attempt and every superseded run is kept in
the packets.

## Independent review

- **Contracts:** reviewed in three rounds before acceptance.
- **C7:** reviewed in two rounds.
- **C8a and C8b:** the C9 review covered them. Round 1 found 0 Critical,
  2 High, 3 Medium and 9 Low. The required findings were fixed and tested
  ([C9 review](../../work/v1.6.0-c9-review/VERIFICATION.md)).
- **Round 2** ([C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md)): **accepted**.
  - Every required finding was confirmed resolved by re-running the reviewer's probes.
    Those include presses through the real menu and its intent limiter.
  - It found no new duplication path, and accepted clarification 6 and the L3 residual.
  - Its three new Lows are handled: C9R2-L2 is fixed in the closure; C9R2-L1 (terminals
    destroyed without a proper break) is recorded in [KNOWN-ISSUES](KNOWN-ISSUES.md); and
    C9R2-L3 (the missing KNOWN-ISSUES entries) is answered by this handoff.
- **Overall:** no Critical or High finding remains open.

None of these reviews is the candidate audit.

## Review entry points

| Question | Record |
|---|---|
| Which plan items have evidence? | [REQUIREMENT-MAP](REQUIREMENT-MAP.md) |
| What still prevents release approval? | [GATE-STATUS](GATE-STATUS.md) |
| What survives a crash, upgrade or removal? | [RECOVERY-MATRIX](RECOVERY-MATRIX.md) |
| What matters to operators? | [KNOWN-ISSUES](KNOWN-ISSUES.md) |
| Which tested bytes? | [development-artifacts.json](development-artifacts.json) |
| Which packets, with which manifests? | [evidence-index.json](evidence-index.json) |
| This handoff's integrity | [checksums.txt](checksums.txt) |

## Acceptance still open

This is the maintainer's work, marked `[H]` in the
[completion plan](../../status/COMPLETION-PLAN.md):
- **Visual.** V0 was not run: the development host is Windows, with no Xvfb or
  LLVMpipe. V1 on real GPUs and V2 with two real clients are open. They cover the
  builder, the terminal delivery panel, survey scans, receivers and the mission
  feedback, plus a mission gameplay video.
- **Multiplayer.** S2 and V2 with real players (two players settling at once, shared
  terminals).
- **Performance.** Reference-hardware measurements (docs/17 §4). The ADR-050 §2
  flush-cost gate is open. On the development host a flush of a synthetic worst-case
  root took up to 631 ms. If reference hardware exceeds the budgets, the follow-up ADR
  that moves the file write to one writer thread is required before release.
- **Candidate.** A frozen candidate commit and JAR, the candidate-bound automated and
  native matrix, the inherited ADR-048 obligations, the final independent audit, an
  uninvolved installation and human approval.

**Recommendation:** the version stays `IN_PROGRESS`. It should not be moved to
`READY_FOR_AUDIT` before the V1/V2, S2 and reference-hardware evidence exists. This
handoff approves nothing.

## Installing the development build

Build with Java 17 and the repository Gradle wrapper. Install the normal host JAR (not
the API or sources classifier) on matching Forge clients and server. Clients and
servers must both run this build, because v1.6 adds a network channel. Test on a
complete backed-up copy of a world, with its data packs and configuration. The
satellite registry is upgraded to root 3 before start, and older builds refuse it. Do
not enable `-Dadvancedrocketrycommunity.releaseTestHooks=true` outside test servers.
