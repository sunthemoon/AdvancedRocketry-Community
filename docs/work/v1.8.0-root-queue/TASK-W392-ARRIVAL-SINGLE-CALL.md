# TASK W392: one arrival-executor computation per COMMITTED visit (F19b), and the governed benchmark on root 3

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w392`, branch `codex/v1.8.0-w392` from the shared branch at dispatch (after W390's merge) |
| Decision | Root decision R523 (QUEUE row W392) |
| Source | [W390](records/W390.md), "Removal design and remaining work"; ADR-065, the N6 bullet of "Open before W11b3 or any admission"; [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), row F19b |
| Record | `docs/work/v1.8.0-root-queue/records/W392.md` |
| Write scope | Part A: `rocket/server/RocketGovernedSourceDispatcher.java`, `rocket/server/RocketGovernedArrivalDispatcher.java`, `resourceescrow/EscrowGovernedArrival.java` only for a small read-only validity check, their unit tests, GameTests in the existing arrival or governed-flight GameTest classes with their batch registration. Part B: the gated benchmark classes `resourceescrow/EscrowFlightBenchmark*.java`, `scripts/run_v180_governed_benchmark.py` and its test. The record. No other production file. No limit, budget, timeout or log-manifest change without stopping for Root first. |

## Why

W390 measured the second, warm `arrivals().tick(...)` call at 69 microseconds mean and 210 microseconds at P99 on
this machine. Scaled to `MAX_ACTIVE_TRANSFERS` (64) that is 4.4 ms mean and 13.5 ms at P99 per tick, above the
1.0 and 5.0 ms rule, so the second computation goes. W390 also found that the governed benchmark's default fixture
lacks the root-3 accounting baseline, so the benchmark's normal `start` mode no longer runs on the activated build;
the final campaign's reference-node phase (F17) needs it.

## Part A: one computation per visit

Root's design, from W390's: `RocketGovernedSourceDispatcher.destination()` keeps its first call (it restores a warm
COMMITTED descent's pose before F4 and F5 inspect it) and keeps the result as a local value for this visit. When the
phase executor answers COMMITTED and the source is absent, `RocketGovernedArrivalDispatcher` consumes that result
for the descent trail, the READY_TO_LAND first-event completion and landing, and the LANDED terminal step, instead of
calling `arrivals().tick(...)` again.

Reuse only when all of these hold; otherwise call `arrivals().tick(...)` as today:
- the first call ran in this visit for the same record and the same arrival entity object that F4 then reports
  PRESENT;
- the arrival's governed observation is the same as right after the first call (F4 and F5 changed nothing);
- the first call's result was not HELD (a HELD first call means nothing was applied, so recompute; this keeps a hold
  path unchanged);
- the arrival binding for that transfer is still the one the first call used (add a small read-only check to
  `EscrowGovernedArrival` if needed; it must not capture or serialize).

Keep every final check where it is: `land()` reacquires its binding and checks the boundary; the terminal checks its
own authority; the first-event completion keeps its seam. Do not remove the first call, do not move it after F4 or
F5, and do not change F4, F5, the arrival's binding rules, holds or the journal.

Tests: unit tests of the reuse decision (each condition false makes a recompute, and all true makes a reuse); a
GameTest that drives a governed COMMITTED descent to landing and counts arrival computations per visit (one on a
warm visit; two when F4 or F5 changes the observation, or when the first call answered HELD), and that the landing,
the world-first acknowledgement, LANDED and F8 complete as before. Every existing arrival, landing, governed-flight
and first-event test stays unchanged and passing.

## Part B: the benchmark on root 3, and the before and after measurement

1. Make the benchmark's default `start` and `recover` modes use the accounted native-loader fixture that W390 added
   for its attribution mode, since root 3 is now the only format. Keep its checks exact (1,000 and 4,000
   conservation, C0 to C8 retirement, operator O1 to O4, pruning). The attribution mode stays available.
2. Measure natively under the heavy-JVM lock with the existing runner: one normal benchmark run on the shared head
   before your Part A change (the JAR of your branch's base) and one on your head. Report whole-tick mean, P95, P99,
   the largest non-save tick, the DEFER ticks and the flight latencies for both, and the number of
   `arrivals().tick(...)` computations per COMMITTED visit if you can count them without changing production code
   (for example from the attribution mode's census). These are development numbers, not the reference node.

## Verification

Compile; run the changed unit classes and the whole unit suite once; run the arrival, landing, governed-flight and
first-event GameTest batches; run one full GameTest run with the log checker under the heavy-JVM lock, with
`JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. A `server-overloaded` count alone over the limit is host lag under
R131; record it.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit Part A and Part B in separate reviewable commits in the style of AGENTS.md section 6. Do not push.
- This is a production change in governed flights: a different engine reviews it read-only before Root merges it.
- The last line of your answer is the path of the record.
