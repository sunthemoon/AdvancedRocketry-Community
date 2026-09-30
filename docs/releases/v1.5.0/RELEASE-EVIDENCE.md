# v1.5.0 development evidence handoff

**IN_PROGRESS — not a release candidate, stable release or Gate approval.**
This is the ACC-01 handoff: an index of the implemented station, orbit, star-system
and warp capabilities, the evidence retained for them and the acceptance that is
still open. This is an unofficial community rewrite.

## Development identity

- Branch `codex/v1.5.0-orbital-station-warp`, build `1.20.1-1.5.0-dev`. The
  handoff commit is the one that adds this file; its parent is
  `d45534ebc55a4005df511935ed34adf62a8a7b2e`.
- Minecraft 1.20.1, Java 17, Forge 47.4.10.
- Public API 1.7: no API source changed since the v1.4 baseline.
- Network: celestial display channel 3 (ADR-047); flight 8, life support 1 and
  rocket visuals 1 are unchanged. The table is pinned by `NetworkProtocolPinTest`.
- Station registry root schema 4 (ADR-040, ADR-044); other managed stores are unchanged.
- Exact bytes of the tested JARs: [development-artifacts.json](development-artifacts.json).
  Earlier slice runs stay tied to their own artifacts.
- Candidate commit and artifact, release tag, final candidate reviewer and human
  release approver: **not assigned**.

[ADR-039](../../decisions/ADR-039-V150-DEVELOPMENT-BASELINE-EXCEPTION.md) permits
this development from the v1.4 development handoff; it is not a passed v1.4
release. The [release-acceptance cursor](../../status/CURRENT_VERSION.md) remains
v1.0, and inherited Gates are not rewritten here.

## Implemented capabilities

| Area | What exists | Main record |
|---|---|---|
| Stations | Root-4 registry with pre-start migration and backups; teams and invitations; checked expansion; per-station gravity; server-wide write spacing | [station schema](../../work/v1.5.0-station-schema/VERIFICATION.md), [expansion](../../work/v1.5.0-station-expansion/VERIFICATION.md), [native](../../work/v1.5.0-station-native/VERIFICATION.md), [closure](../../work/v1.5.0-closure/VERIFICATION.md) |
| Orbit environment | Any data body as orbit; `/arce station environment`; gravity and solar intensity | [orbit environment](../../work/v1.5.0-orbit-environment/VERIFICATION.md), [capacity](../../work/v1.5.0-station-capacity/VERIFICATION.md) |
| Station sky | The orbited body as a disc, sun size from its sunlight; only the body ID is sent | [orbit sky](../../work/v1.5.0-orbit-sky/VERIFICATION.md) |
| Star systems | Root trees as systems; Tau Ceti example; no cross-system routes | [star systems](../../work/v1.5.0-star-systems/VERIFICATION.md) |
| Warp | Warp core and energy balance; countdown; one checked commit per tick; in-motion rocket rule; docked rockets move with the orbit; operator diagnostics | [schema](../../work/v1.5.0-warp-schema/VERIFICATION.md), [core](../../work/v1.5.0-warp-core/VERIFICATION.md), [rockets](../../work/v1.5.0-warp-rockets/VERIFICATION.md), [native](../../work/v1.5.0-warp-native/VERIFICATION.md) |
| Controls | Commands only; no new packet (ADR-046) | [elevator and UI](../../work/v1.5.0-elevator-ui/VERIFICATION.md) |
| Elevator | Read-only, operator-only endpoint check (ADR-045) | [elevator and UI](../../work/v1.5.0-elevator-ui/VERIFICATION.md) |
| Recovery | v1.4 worlds upgraded natively with station, rocket and missing-body cases; concurrent warps; killed countdowns and commits | [MIG-01a](../../work/v1.5.0-mig-missing-orbit/VERIFICATION.md), [MIG native](../../work/v1.5.0-mig-native/VERIFICATION.md), [RECOVERY-MATRIX](RECOVERY-MATRIX.md) |

Not implemented here: physical copying of blocks or players on warp, elevator
structures and transport, a station control screen, localisation of station
messages, satellites and resource missions (v1.6), and direct 1.12.2 saves.

## Latest development checks

On the tree committed with this handoff (the [closure](../../work/v1.5.0-closure/VERIFICATION.md), run 01):
- `./gradlew clean build test runData runGameTestServer`: exit 0;
- 1,058 JUnit tests with 0 failures, errors or skips;
- all 260 required GameTests passed, including the permission matrix (345/345 cells);
- `git diff --exit-code -- src/generated`: exit 0.

Native, on the same host JAR (`26a25d02…`) and the rebuilt fixture (`765735d1…`):
- the missing-body and gravity restart harness passed;
- the C3 harness (upgrade, in-flight recovery, concurrent warps, kills, S1) passed again.

Every failed attempt and every superseded run is kept in the packets.

## Independent review

Every v1.5 development slice had an independent review:
- STAR-02/03 and WARP-02: the [slices review](../../work/v1.5.0-slices-review/VERIFICATION.md);
- WARP-03/04: the [review closure](../../work/v1.5.0-review-closure/VERIFICATION.md), whose
  one High finding (R1, the satellite registry's heap factor) was fixed and rechecked by
  final review A;
- ORBIT-03: its own contract and implementation reviews ([orbit sky](../../work/v1.5.0-orbit-sky/VERIFICATION.md)).

Two final reviews then covered the rest (the [closure](../../work/v1.5.0-closure/VERIFICATION.md)):
- area A: authority and persistence;
- area B: sky, MIG-01a, ORBIT-02/04 and STATION-03/04.

Neither found a Critical or High issue. Every Medium finding and every fixable Low
finding is fixed; the rest are recorded in [KNOWN-ISSUES](KNOWN-ISSUES.md). The
closure fixes were checked by mutations and reruns, not by a further review. None
of these reviews is the candidate audit (ACC-03).

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

The maintainer's work (ACC-02 and ACC-03, marked `[H]` in the
[completion plan](../../status/COMPLETION-PLAN.md)):
- **Visual.** V0 was not run: the development host is Windows, with no Xvfb or
  LLVMpipe. V1 on real GPUs and V2 with two real clients are open for the station
  sky, the warp feedback and the permission behaviour.
- **Multiplayer.** S2 and V2 with real players: team changes, warp with passengers
  online and offline, and visitors.
- **Performance.** Reference-hardware measurements, native multi-station load and
  reload (ORBIT-04), and the heap headroom ([KNOWN-ISSUES](KNOWN-ISSUES.md)).
- **Candidate.** A frozen candidate commit and JAR, the candidate-bound automated and
  native matrix, the inherited ADR-039 obligations, the final independent audit,
  an uninvolved installation and human approval.

**Recommendation:** the version stays `IN_PROGRESS`. It should not be moved to
`READY_FOR_AUDIT` before the V1/V2, S2 and performance evidence exists. This
handoff approves nothing.

## Installing the development build

Build with Java 17 and the repository Gradle wrapper. Install the normal host JAR
(not the API or sources classifier) on matching Forge clients and server. Clients
and servers must both run this build, because the celestial protocol changed.
Test on a complete backed-up copy of a world, with its data packs and configuration:
the station registry is upgraded to root 4 before start, and older builds refuse it.
The compatibility fixture is a test tool, not a gameplay dependency.
