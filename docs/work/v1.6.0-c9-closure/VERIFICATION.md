# C9 closure: v1.6 native recovery, performance, final review and handoff

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`eb5b80e` (the answer to the C9 review). V160-REC-01 and the C9 items of the
[completion plan](../../status/COMPLETION-PLAN.md). The
[v1.6 development handoff](../../releases/v1.6.0/RELEASE-EVIDENCE.md) is committed
with this packet. Development evidence only: no Gate, candidate or tag.

## Change in this commit

The second review round recommended C9R2-L2. If reconciling the row of the mission an
action is about fails, the claim or cancel is now refused (`SERVER_ERROR`) instead of
proceeding. The "reported once" flag also resets when the terminal reloads
(`TerminalResourceActions`). No path is known to throw there; this is hardening.

## Native run

`scripts/native_v160_check.py` (attempt 04, exit 0) drives finite dedicated-server
processes on a copy of the genuine v1.5 world retained by the v1.5 closure
(`arce-v150-closure…/c3-rerun/station-server/world`). The source world is verified
unchanged at the end.

Artifacts:
- the v1.5 handoff host `26a25d02…` (the JAR recorded in the
  [v1.5 handoff](../../releases/v1.5.0/development-artifacts.json));
- the v1.6 host `452885f0…`, the JAR of this packet's full run on the tree committed
  here;
- the compatibility fixture `765735d1…`, the one that world last ran with.

The harness uses the release-test hooks. They register only with
`-Dadvancedrocketrycommunity.releaseTestHooks=true` and permission level 2:
- `release-test advance` jumps the logical clock;
- `perf flush|ticks|worst-case` measure;
- `terminal … setup|inspect|cut` drive the production terminal through a FakePlayer
  and its real buttons.

A cut claims, makes one store durable and halts the JVM without shutdown
(exit 75).

| Phase | Host | End | Result |
|---|---|---|---|
| `legacy` | v1.5 | stop | The v1.5 registry (root 2) writes 200 data missions; 204 missions and 203 satellites in all |
| `upgrade` | v1.6 | stop | Pre-start migration: `[ARCE-BETA-1002] … migrated=1, backup=…`. The backup holds the v1.5 registry byte for byte (`e3ba5412…`). All 204 missions survive, then 800 more (32 stress owners) reach 1,000 unfinished. Timings are below |
| `backlog` | v1.6 | stop | No remigration (`[ARCE-BETA-1001] … migrated=0`); counts unchanged. The clock jumps 400,000 ticks, and 840 due missions drain at 32 per pass in 30 s with no lost or double completion |
| `setup` | v1.6 | stop | Four terminals in far, unloaded chunks. For each: a survey started and claimed through the terminal, the terminal ID persisted by a real chunk save, and an asteroid mission started, made durable and brought to READY. On disk: schema 2, the same ID, empty buffer and receipts |
| `cut-A` | v1.6 | halt 75 | A claims; the chunk is saved, the registry is not |
| `verify-A-cut-B` | v1.6 | halt 75 | A's registry is behind (READY). Loading the terminal gives `CLAIM_RECOVERED`, then `ACKNOWLEDGED` and `RECEIPT_DROPPED`. CLAIMED, instance DEPLETED, 192 items = one reward. B then claims; the registry is flushed, the chunk is not |
| `verify-B-cut-C` | v1.6 | halt 75 | B's registry is ahead (CLAIMED, not acknowledged). Loading the terminal gives `REMATERIALIZED` once, then acknowledged and dropped. 62 items = one reward. C then claims; the chunk is saved, the registry is not |
| `verify-C-cut-D` | v1.6 | halt 75 | C's registry is behind (READY). An operator cancels while the terminal is unloaded, and the instance goes to QUARANTINED. Loading the terminal gives `PAID_THEN_CANCELLED` once; 208 items = one reward, and the receipt is kept. D then claims; both stores are saved |
| `verify-D` | v1.6 | stop | D is CLAIMED. Loading the terminal gives `ACKNOWLEDGED` and `RECEIPT_DROPPED`; 178 items = one reward. The forced-chunk set equals the world's baseline (four chunks forced before this run) |
| `final` | v1.6 | stop | Clean restart. Nothing forced beyond the baseline, and the four terminal chunks are not loaded. On disk: A, B and D CLAIMED, C CANCELLED; each terminal holds exactly one reward; only C keeps its receipt. The worst-case flush is measured (below) |

Every phase logged 0 ERROR and 0 FATAL lines (the harness scans each log).

Retained attempts:
1. `attempt-01-failed`. Its reply matching took a hook's second, echoed line, which
   mapped terminal B to A's ID. The hooks now print one line, and setup and inspect
   match their position. This run also showed that Forge fires the post-tick event
   after vanilla records the tick time, so the coalesced flush is invisible to
   vanilla MSPT (confirmed in the Forge 47.4.10 bytecode of
   `MinecraftServer.tickServer`). The satellite manager now records its post-tick
   work per tick, and the tick measurements add it.
2. `attempt-02-pass-superseded-jar`. Every phase passed on the JAR before the review
   fixes.
3. `attempt-03-pass-superseded-jar`. Every phase passed on the JAR of `eb5b80e`
   (`ebe2d9a0…`). It was superseded by the C9R2-L2 change. Its worst-case flush was
   427 ms on average and 444 ms at most, and its largest tick was 418 ms.

## Performance (development host, not reference hardware)

The host is Windows 11 on local SSD storage, running Java 17 with `-Xmx2G`. Tick
figures are vanilla tick time plus the satellite post-tick work of the same tick.
Each figure is taken over the last 100 ticks.

| Measurement (attempt 04) | 500 missions | 1,000 missions | Worst-case root |
|---|---|---|---|
| Registry file | 45 KB | 89 KB | 370 KB (7.2 MB NBT; 8,192 missions, 4,096 satellites, 2,048 instances) |
| Barrier flush, mean / max of 10 (3 for worst case) | 31 / 43 ms | 53 / 55 ms | **497 / 631 ms** |
| Ticks at rest after the load (mean / P95 / P99) | 4.1 / 12.0 / 14.2 ms | 2.8 / 3.0 / 3.6 ms (max 76 ms) | — |
| Backlog: first window after restart (mean / P95 / P99 / max) | — | 8.4 / 24.8 / 46.0 / 90 ms | — |
| Backlog: later windows (mean / P95 / P99) | — | ≤ 3.1 / ≤ 3.4 / ≤ 3.7 ms | — |
| Coalesced flush during the backlog | — | 6 flushes, mean 70 ms, max 82 ms | — |

The docs/17 budgets are mean ≤ 25 ms, P95 ≤ 50 ms and P99 ≤ 100 ms, plus the
500 ms rule for single spikes, which does not apply to startup or save spikes. Every
tick window measured with 500 and 1,000 missions meets them.

**ADR-050 §2 flush-cost gate: open.** On a synthetic worst-case root at the load
bounds, one flush took up to 631 ms on this host, and the three attempts varied
between 427 and 631 ms. That root is reachable only as an over-limit legacy root;
admission stops at 3,072 missions.

- **One coalesced flush per 100 ticks** does not move P95/P99.
- **A burst of `data` barrier flushes** (launches, claims, cancels) on such a root
  could push P99 above 100 ms.

The flush cost is measured, but reference hardware (docs/17 §4) is not. ADR-050 §2
therefore stays open: if reference hardware exceeds the budget, the follow-up ADR that
moves the file write to one writer thread is required before release. This is recorded
in the handoff's GATE-STATUS and KNOWN-ISSUES; it is not waived.

## Chunk-ticket audit

- **Source:** `DeliveryRuntimeTest` finds no ticket API in satellite code.
- **GameTest:** the forced-chunk set is unchanged.
- **Native:** the forced-chunk set equals the world's pre-run baseline in every phase.
  Operator `forceload add/remove` was used only to load a terminal for verification,
  and was removed again.
- **Terminal loads:** terminals load chunks only through `getChunkNow` lookups. Their
  own set-up loads a chunk for one command, with no ticket.

## Final independent review

**Round 1** ([review packet](../v1.6.0-c9-review/VERIFICATION.md)) covered C8a and C8b
and found 0 Critical, 2 High, 3 Medium and 9 Low. The required findings were fixed and
tested in `eb5b80e`.

**Round 2** (`independent-review-2.zip`) reviewed the committed tree of `eb5b80e` and
**accepted** it.

- **Tests:** 53 unit tests (12 suites) and 295 GameTests passed, including 5 probes of
  its own. The probes press buttons through the real menu with its intent limiter.
- **Required findings:** each one was confirmed resolved by a probe:
  - H1: with 71 bound missions the first real press succeeds. With 150 receipts
    loaded it says `RECONCILING` once, then succeeds.
  - H2: the item carries the buffer, receipt and ID with an empty inventory, and the
    slots drop exactly once.
  - M1: the queues equal the records served.
  - M2: one expiry entry during churn.
  - M3: no stored display location, and the registry reloads.
- **Clarification 6** keeps conservation. A receipt held for a READY mission is
  applied before the claim or cancel through the menu, and nothing is paid twice.
  The reviewer accepted it, and asks that the next ADR-051 revision record it.
- **L3** is accepted as a stated residual.

Its new Lows:
- **C9R2-L1** (a terminal destroyed without a proper break loses its delivery, as
  with shulker boxes): recorded in KNOWN-ISSUES.
- **C9R2-L2:** fixed here.
- **C9R2-L3** (the KNOWN-ISSUES entries named by the review packet): they are
  committed with this handoff.

No Critical or High finding remains open.

## Commands actually executed

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `gradlew clean build test runData runGameTestServer` on the tree committed here | Exit 0, 4m05s: 1,157 JUnit tests / 218 suites, 0 failures; 290 required GameTests passed; generated diff empty; the same 18 intentional ERROR lines as the C9 review run, 0 FATAL | `root-gradle-01.log`, `full-junit/`, `junit-summary.json`, `artifacts.json`, `generated-diff.log` |
| 2 | `python native_v160_check.py`, attempt 01 | Failed on the harness (above) | `attempt-01-failed/` |
| 3 | The same, attempt 02 on the pre-review JAR | Exit 0, superseded | `attempt-02-pass-superseded-jar/` |
| 4 | The same, attempt 03 on `ebe2d9a0…` (`eb5b80e`) | Exit 0, superseded | `attempt-03-pass-superseded-jar/` |
| 5 | The same, attempt 04 on `452885f0…` | Exit 0 | `attempt-04/` |
| 6 | Repository validators after packaging | All exit 0: `validate_repository.py --require-approved-identity`, `validate_v1plus_planning.py`, `validate_bootstrap_provenance.py`, `python -m unittest tests.test_v1plus_planning`, `git diff --cached --check` | `packaging/out/validation.log` |

## Evidence archive

`root-checks.zip` holds every attempt's receipts, commands, server logs and decoded
registry states, the scripts and the full-run results. The copied worlds and server
libraries (`native-server/`) are excluded. `independent-review-2.zip` holds the round-2
report, logs, probe results and the reviewer's two probe sources, but not its repository
copies.
