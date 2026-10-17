# TASK W11b2, continuation 52: why the benchmark's eight governed flights stay PREPARED, then the measured runs

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-45](TASK-W11B2-CONTINUE-45.md) and [TASK-W11B2-CONTINUE-51](TASK-W11B2-CONTINUE-51.md) first. You
are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c52` (new, at continuation 51's record `63916b63`, which is the integrated W11b2 head `884eb889` plus continuation 51's harness commits, plus this task) |
| Branch | `codex/v1.8.0-w11b2-c52` (local commits only; Root pushes and merges) |
| Input | Continuation 51's record `records/W11B2-C51.md` ("Final native result and diagnosis") and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C51/native-03/` (`governed-benchmark.json`, `start.log`, the CSV) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), section 5.4 (F2 and F3) and 5.5; R1 to R20; TASK-W11B2-CONTINUE-45 (the workload and the report) |
| Record | `records/W11B2-C52.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C52/` |

No other worker is running. Hosted run 39029453767 passed all 1,180 GameTests on `884eb889`.

## What is known

In continuation 51's final attempt all eight F1 arms were acknowledged (`F1_ARM_ACK` 8), but only two F2 callbacks ran
(`F2_PREPARED_ACK` 2, `F2_COUNTDOWN` 2). After 4,000 ticks all eight journal records were still PREPARED, all eight
ARMED entries remained, the journal was acknowledged, the ledger operational and the writer idle, and no F4 to F8
checkpoint and no hold, fence or executor-hold line was logged. The journal's callback queue holds two, and
`EscrowGovernedPreparation.prepare` relies on F3's fallback (`EscrowGovernedSourceFlight`, "An acknowledged F2 may have
stopped before its countdown callback") for the others; but even the two flights whose countdown callback ran did not
advance. The benchmark probe launches with `EscrowGovernedPreparation.begin(...)` directly
(`EscrowFlightBenchmarkProbe.launch`), not through the product's launch entry that players use, and its sources come
from `EscrowRocketAuditProbe.createRocket` at `EscrowFlightBenchmarkLoad.source(i)`. In continuation 48's native
`PF_GOVERNED_T_ACK` cut, a restarted governed flight did resume through F3 to a COMMITTED landing, and several
GameTests complete governed flights, so the executor works in those setups.

Separately, the probe's periodic `load.refill()` calls `OxygenVentBlockEntity.load()`, which resets every vent to
SCANNING, and the vents never became ACTIVE again, so the reference load stopped after 200 ticks.

## What to do

1. **Find why the flights stay PREPARED.** Compare the probe's launch path with the product's launch entry and with
   the GameTests that complete governed flights (flight tickets, launch request and replay-guard registration, the
   source dispatcher's visit and classification, chunk and entity ticking at the source positions, bindings and
   capacity, the F2 callback queue). Reproduce it with a unit test or a GameTest; one with eight concurrent governed
   launches through the product's entry, so that the F2 callback queue overflows, is the most useful.
2. **Fix the real cause.**
   - If the harness does not launch the way the product does, make it use the product's own entry (as a player
     request would), not a shortcut that skips product work, and record why.
   - If the product stalls (for example the F3 fallback never runs for a PREPARED record whose countdown callback was
     refused, or concurrent governed flights are never visited), fix it with a unit test and a GameTest within R1 to
     R20. Stop and report if the fix needs a change to an acknowledgement order, a proof, a lease, a schema or the
     replay exclusion.
3. **Fix the vent refill** in the harness so that refilling does not reset the vents (use the product's own supply
   path, not an NBT reload), and find out whether the vents return to ACTIVE by normal ticking; if a product defect
   stops that, report it with evidence and do not fix it here.
4. **Then run the measured benchmark** exactly as TASK-W11B2-CONTINUE-51 items 1 to 4 say, on your committed source,
   on the quiet machine, and report every number it asks for against docs/17's unchanged budgets.
5. Run the whole unit suite after each fix and one full GameTest with the unchanged log checker before the measured
   runs. If you add a GameTest batch, say so, because batch names change the run order.

## Write scope and boundaries

The benchmark probe, runner, recorder and load helpers and their tests; the governed launch, preparation, dispatch
and source-flight classes only for a product fix under item 2; new tests; and the record. No change to an
acknowledgement order, a proof, a lease, a schema, the replay exclusion, the log checker or its expectations, and no
relaxed budget, limit, timeout or assertion. Do not serialize the eight flights or shrink the workload to make it
pass.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C52/` and disposable server
sessions under `D:/GitHub/ARCE-Task-Evidence/Temp/W11b2-C52-<n>`. Check that C: and D: have more than 10 GiB free
before each start. Never remove a lock you do not own. If the tool policy refuses to let you remove your own lock,
world or server copy, write the exact path in your record and final answer and continue; Root removes it.

## Not in this task

Production admission, W11b3, ADRs, the risk register, the R-035 text, status files and the ledger (Root).
