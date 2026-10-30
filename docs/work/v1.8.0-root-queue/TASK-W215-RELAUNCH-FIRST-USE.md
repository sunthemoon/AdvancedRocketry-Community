# TASK W215: the first-use cost of W90's governed relaunch

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w215` (new; from the shared head named in your prompt, which contains W212) |
| Branch | `codex/v1.8.0-w215` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W215.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W215/` |
| Inputs | `records/W212.md` ("Measured design decision" and "Direct elapsed attribution") and its evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W212/` (the per-relaunch ClassLoad events and spans) |
| Harness | W212's on the shared head: `scripts/w212_instrument.py`, `scripts/w212_summary.py`, `scripts/run_w206_warmup.*`, `W212Timing`, `W206NativeWarmup` |
| Background | [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md); R-035 in `docs/11-RISK-REGISTER.md` |

**Root decision R184's follow-up.** W212 found that the governed relaunch's remaining checkpoint cost is first use in
a process: the first relaunch's API cost is 73 to 95 ms against 6 to 13 ms later, with 30 to 38 ms of ClassLoad
intervals (1,807 events over nine first relaunches) and none later; checkpoint begin alone is 24.5 against 0.7 ms.
First-use checkpoint P99 is 99 to 125 ms, over docs/17's 100 ms; every later cohort meets it. The first relaunch is a
player-facing tick.

## What to do

1. **Design note first, committed before code** (one page): which classes the first relaunch loads and initializes
   (from W212's ClassLoad events, grouped by package), which of them are this mod's; a server-startup step (for
   example on `ServerStartedEvent`, or another point that is not a player-facing tick) that loads and initializes
   them; proof that their static initializers have no side effects on world, registry, saved data, configuration or
   networking, and that nothing reads server or level state; the startup cost and memory it adds and where that lands;
   what it cannot fix (JIT and cold code, vanilla or Forge classes loaded lazily). No state, authority, ordering,
   proof or acknowledgement changes, and no dummy launch or fake world work.
2. **Implement it** with a unit test that fails first (for example, the class list stays in step with the classes the
   relaunch path uses, and the step is idempotent and side-effect-free), and a guard so that a missing class fails
   loudly in tests rather than silently at startup.
3. **Measure before and after** on W212's harness under the heavy-JVM lock, on JARs built from committed source: the
   first relaunch's API cost, ClassLoad intervals and checkpoint-bearing P99, later relaunches, and the startup step's
   own cost. Report against docs/17 honestly; if the first-use P99 stays over 100 ms, say so with the numbers.

## Verification

The new tests alone, failing first; the whole unit suite; the governed warm-up and escrow launch GameTest batches three
times under the heavy-JVM lock; the native before and after runs. Root's hosted run on the pushed branch stands in for a
complete local GameTest. An independent read-only review follows before merge.

## Not in this task

JIT warm-up by running code paths, a dummy launch, caps, lifetimes, quotas, the 100, 200, 400 and 600-tick bounds,
budgets, timeouts, F1's checks, the journal format, ADRs, status files and the ledger.
