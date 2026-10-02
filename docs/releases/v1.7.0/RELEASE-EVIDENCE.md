# v1.7.0 development evidence handoff

**IN_PROGRESS — not a release candidate, stable release or Gate approval.**
This handoff indexes the v1.7 endgame work: what is implemented, the evidence retained
for it, and the acceptance that is still open. This is an unofficial community rewrite.

## Development identity

- Branch `codex/v1.7.0-endgame-systems`, build `1.20.1-1.7.0-dev`. The handoff commit is
  the one that adds this file; its parent is `33cb72b2f1a0cbabde6e7937305410a8349939f7`.
- Minecraft 1.20.1, Java 17, Forge 47.4.10.
- Public API 1.8: adds `EndgameEffect` and `EndgameEffectEvent` (ADR-054 §5.1); apart
  from `ApiVersions`, no earlier API class changed.
- Network: the new `endgame` channel is protocol 1 (device views, server to client,
  ADR-054). The other channels are unchanged.
- Storage:
  - the new endgame root `advancedrocketrycommunity_endgame.dat`, root schema 1
    (ADR-054 §10);
  - device roots of schema 1 in the endgame block entities;
  - other managed stores unchanged.
- Exact bytes of the tested JARs: [development-artifacts.json](development-artifacts.json).
- Candidate commit and artifact, release tag, final candidate reviewer and human release
  approver: **not assigned**.

[ADR-053](../../decisions/ADR-053-V170-DEVELOPMENT-BASELINE-EXCEPTION.md) permits this
development from the v1.6 development handoff; it is not a passed v1.6 release. The
[release-acceptance cursor](../../status/CURRENT_VERSION.md) remains v1.0, and inherited
Gates are not rewritten here.

## Implemented capabilities

| Area | What exists | Main record |
|---|---|---|
| Contracts | ADR-053 to ADR-059, accepted after four review rounds and a confirmation round | [preparation](../../work/v1.7.0-preparation/VERIFICATION.md) |
| Framework | Endgame root and endpoint index; authority, protection chain and API 1.8 event; audit; zones; switches and limits; intents; device views | [C11a](../../work/v1.7.0-c11a-framework/VERIFICATION.md), [C11 review](../../work/v1.7.0-c11-review/VERIFICATION.md) |
| Orbital laser drill | Logical mode (data-driven tables); physical mode with laser targets, links and danger confirmation | [C11b](../../work/v1.7.0-c11b-laser-logical/VERIFICATION.md), [C11c](../../work/v1.7.0-c11c-laser-physical/VERIFICATION.md) |
| Area gravity field | Gravity per cube, station rules, trust lists | [C11d](../../work/v1.7.0-c11d-gravity-field/VERIFICATION.md) |
| Black-hole generator | Singularity-bound burn, data-driven singularities and fuels, Cygnus X-1 | [C12a](../../work/v1.7.0-c12a-black-hole/VERIFICATION.md) |
| Transit ledger | Escrow, registration, claims, receipts, the incoming gate, removal settlement, redirect, purge, resettle, resolve | [C12b](../../work/v1.7.0-c12b-transit-ledger/VERIFICATION.md) |
| Railgun | Structure, routes within a star system, launches, effects | [C12c](../../work/v1.7.0-c12c-railgun/VERIFICATION.md) |
| Space elevator | Anchors, terminals, pairs, rides, cargo, warp and deletion guards | [C12d](../../work/v1.7.0-c12d-space-elevator/VERIFICATION.md), [C12 review](../../work/v1.7.0-c12-review/VERIFICATION.md) |
| Closure | Destruction bounds; performance work to the ADR-054 §7 budgets; native crash cuts, forced stops and reference load; the operator guide | [C13 closure](../../work/v1.7.0-c13-closure/VERIFICATION.md) |

Not implemented here, as the plan excludes them (§4): unbounded block destruction,
unbounded energy or unbounded offline work; any bypass of server or claim protection;
legacy duplication or crash behaviour kept for fidelity. The remaining classic content
is the v1.8 backlog, and direct 1.12.2 saves stay unsupported.

## Latest development checks

On the tree committed with this handoff
([C13 closure](../../work/v1.7.0-c13-closure/VERIFICATION.md) full run):
- `./gradlew clean build test runData runGameTestServer`: exit 0;
- 1,376 JUnit tests with 0 failures, errors or skips;
- all 337 required GameTests passed;
- the generated diff is empty.

Native, on the same host JAR (`HOST_SHA…`): the C13 harnesses passed every phase. They
cover:
- the v1.6 world upgrade and the flush benchmark;
- twelve ADR-054 §11 crash cuts over three railgun pairs, with a held coalesced flush
  where a row needs the ledger unflushed, and a record pruned while its source was
  unloaded;
- an elevator cargo cut across Levels and three ride forced stops (ADR-059 S2);
- a final restart;
- the endgame reference load ([PERFORMANCE](PERFORMANCE.md)).

Every failed attempt and every superseded run is kept in the packet.

## Independent review

- **Contracts:** reviewed in four rounds and a confirmation round before acceptance.
- **C11:** reviewed in three rounds; every finding fixed or accepted as recorded.
- **C12:** reviewed in three rounds; every finding fixed or accepted as recorded.
- **C13:** reviewed in three rounds. Round 1 found 1 High, 4 Medium, 4 Low and 4 Info;
  round 2, 2 Medium, 2 Low and 1 Info; round 3 accepted with no code finding open.
  Every fix is its own commit. The revised native harness found two more ledger
  defects (a stub never pruned; endpoints in chunks held below FULL), both fixed.
- **Overall:** no Critical or High finding remains open.

None of these reviews is the candidate audit.

## Review entry points

| Question | Record |
|---|---|
| Which plan items have evidence? | [REQUIREMENT-MAP](REQUIREMENT-MAP.md) |
| What still prevents release approval? | [GATE-STATUS](GATE-STATUS.md) |
| What survives a crash, upgrade or removal? | [RECOVERY-MATRIX](RECOVERY-MATRIX.md) |
| How fast is it? | [PERFORMANCE](PERFORMANCE.md) |
| What matters to operators? | [KNOWN-ISSUES](KNOWN-ISSUES.md), the [operator guide](../../ENDGAME-OPERATOR-GUIDE.md) |
| Which tested bytes? | [development-artifacts.json](development-artifacts.json) |
| Which packets, with which manifests? | [evidence-index.json](evidence-index.json) |
| This handoff's integrity | [checksums.txt](checksums.txt) |

## Acceptance still open

This is the maintainer's work, marked `[H]` in the
[completion plan](../../status/COMPLETION-PLAN.md):
On 2026-10-02 the maintainer deferred the visual and multiplayer acceptance below to a
later round; it stays open and is not waived.

- **Visual.** V0 was not run (Windows host, no Xvfb or LLVMpipe). V1 on real GPUs and V2
  with two real clients are open: the device screens, the laser beam, railgun streaks,
  the generator, the tether and gravity fields.
- **Multiplayer.** S2 with real players: contested targets, shared stations, rides and
  fields with several players.
- **Performance.** Reference-hardware measurements (docs/17 §4) and the flush-cost gate:
  the closure run met the 60 ms budget, but earlier loaded runs on the development host had
  single flushes of 116–562 ms. If reference hardware exceeds it, ADR-054 §7 requires the
  follow-up ADR that moves the write to a writer thread. The reference load's railgun rate
  is a recorded deviation from §7.
- **Candidate.** A frozen candidate commit and JAR, the candidate-bound automated and
  native matrix, the inherited ADR-053 obligations, the final independent audit, an
  uninvolved installation and human approval.

**Recommendation:** the version stays `IN_PROGRESS`. It should not be moved to
`READY_FOR_AUDIT` before the V1/V2, S2 and reference-hardware evidence exists. This
handoff approves nothing.

## Installing the development build

Build with Java 17 and the repository Gradle wrapper. Install the normal host JAR (not
the API or sources classifier) on matching Forge clients and server. Clients and servers
must both run this build, because v1.7 adds a network channel. Test on a complete
backed-up copy of a world, with its data packs and configuration. Read the
[operator guide](../../ENDGAME-OPERATOR-GUIDE.md) first. Do not enable
`-Dadvancedrocketrycommunity.releaseTestHooks=true` outside test servers.
