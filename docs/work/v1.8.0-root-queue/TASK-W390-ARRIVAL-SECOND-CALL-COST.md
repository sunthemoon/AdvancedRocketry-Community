# TASK W390: measure the second arrival-executor call on a COMMITTED visit, and keep or remove it by a stated rule

Root: Claude. Date: 2026-11-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w390`, branch `codex/v1.8.0-w390` from the shared branch |
| Decision | Root decision R520 (QUEUE row W390) |
| Source of the item | ADR-065, the paragraph "Open before W11b3 or any admission", the N6 bullet; [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), row F19b; [W11B2-C53](records/W11B2-C53.md) and [W11B2-C54](records/W11B2-C54.md), which could not attribute the call's cost |
| Record | `docs/work/v1.8.0-root-queue/records/W390.md` |
| Write scope | The gated benchmark classes `resourceescrow/EscrowFlightBenchmark*.java` (the probe, metrics, load and work classes; `EscrowFlightBenchmarkGameTests.java` only for a test of the new mode), `scripts/run_v180_governed_benchmark.py`, `tests/test_v180_governed_benchmark.py`, any unit test of the probe, the record. No other production file. No limit, budget, timeout or log-manifest change. |

## The item

On a visit of a COMMITTED transfer whose source is gone, `RocketGovernedSourceDispatcher.destination()` calls
`EscrowRuntime.of(server).arrivals().tick(...)` first (the comment above it: restore a warm COMMITTED descent's
planned pose before F4 and F5 inspect it), and later, when the phase executor answers COMMITTED,
`RocketGovernedArrivalDispatcher.tick(...)` calls `arrivals().tick(...)` again in the same server tick. ADR-065 calls
this N6, "cost only; no state effect". The governed benchmark includes both calls in its total tick time but cannot
attribute the second call's cost. Fuel admission needs a measured disposition.

Root's choice: measure first, and do not change the flight path unless the measurement says the cost matters. The
flight path is covered by many native families that C4 reruns; a change there costs more than a small, bounded cost.

## What to do

1. **Confirm the claim in the code.** Read `RocketGovernedSourceDispatcher.destination()`,
   `RocketGovernedArrivalDispatcher.tick()` and `EscrowGovernedArrival.tick()` and its `binding()`. Write in the record:
   in which states both calls run in one tick; what the second call does when the first already ran in that tick
   (binding reuse on an unchanged observation, the same planned pose, the boundary check, the authority lookup); and
   whether the second call can ever change state that the first did not (a hold, a binding replacement, a pose, a
   result that leads to landing). If it can change state, stop and report to Root before measuring.
2. **Attribute the cost with an attribution mode of the governed benchmark.** Add a mode, selected only by the
   benchmark's existing gate (the `advancedrocketrycommunity.escrowScenario` property and the runner), in which the
   probe, at the end of each server tick, calls `arrivals().tick(...)` once more for each benchmark flight that is a
   warm COMMITTED descent with no source, with the same boundary supplier the dispatcher uses, and times that call
   with `System.nanoTime()`. That call takes the same warm path as the dispatcher's second call. In the same mode,
   check that the extra call changes nothing: its result equals the dispatcher's result for that tick where it is
   known, the rocket's governed observation and position are unchanged by it, and no executor hold is added. Any
   change fails the run. The normal benchmark mode must stay byte-for-byte the same in its behaviour and its report
   fields; a unit test or a runner test shows the mode is off by default.
3. **Run it natively once** under the heavy-JVM lock with the existing runner (`scripts/run_v180_governed_benchmark.py`,
   `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`). Report the number of timed calls and the per-call mean, P95, P99
   and maximum in microseconds, and the number of concurrent COMMITTED descents seen per tick.
4. **Apply the rule.** Let `K` be the governed arrival binding capacity, `RocketFlightLimits.MAX_ACTIVE_TRANSFERS`
   as `EscrowGovernedArrival.requireCapacity` uses it (state its value). Compute `C = K x mean` and `C99 = K x P99`. Keep both calls, with no production
   change, if `C <= 1.0 ms` and `C99 <= 5.0 ms`; these are 4 percent of the 25 ms mean tick budget and 5 percent of
   the 100 ms P99 budget of docs/17. Otherwise change no production file: stop and report the numbers with a short
   design of how the second call could be removed safely.
5. **Verification.** Compile; run the changed unit classes and the benchmark runner's Python tests; run the whole
   unit suite once. A full GameTest run is not needed if no class outside the gated benchmark changed; say so in the
   record.
6. **Record.** Write `records/W390.md`: the reading of the code (item 1), the mode, the command, the numbers, the
   rule's outcome, and a two-sentence disposition that Root can put into ADR-065 and the fuel report's F19b row. The
   last line of the record is its own path.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- These numbers come from this machine, not the reference node; say so. The reference node's soak (F17) still runs
  with both calls in the product path.
- Commit on the branch in reviewable commits in the style of AGENTS.md section 6. Do not push.
- A different engine reviews the change read-only before Root merges it.
- The last line of your answer is the path of the record.
