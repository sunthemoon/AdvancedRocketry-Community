# TASK W469: three local full GameTest runs of C6, one unloaded and two loaded

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w469`, branch `codex/v1.8.0-w469` from the shared branch |
| Decision | Root decision R675 (QUEUE row W469), following [W467](records/W467.md) (R674) |
| Sources | [W467](records/W467.md): five open intermittent items whose condition names local or loaded full runs and that had no hosted recurrence after their fix: adapter capture stray button (W273, `AdapterFaultGameTests.throwingCapturePreservesSource`, loaded and hosted runs), classic scale and sawmill under load (W283, `ClassicScaleGameTests.sixteenOfEveryKindStayIdleThenHalfResumeAfterRealUnload` and `ArcCuttingGameTests.sawmillOffRefusesNewWoodJobsAndPausesAndResumesAnActiveJob`, loaded runs), adapter restore under load (W299, `AdapterFaultGameTests.throwingRestoreCleansPartialInventoryAndRetries`, loaded runs), sleep in vacuum (W315 and W318, `SleepRespawnGameTests.sleepIsDeniedInVacuumOnAPlanetAndInSpace`, later local full runs) and I/O-completion budgets (W350, the two `EscrowProofRetryGameTests` proof-retry tests and `SealedSpecialHostAutomationGameTests.tankAndBayStaySealedUnderTickingAutomation`, later local full runs, plus the durability-held warning). The v1.8 workflow `.github/workflows/v180-development.yml`, [ci_gametest_shards.py](../../../scripts/ci_gametest_shards.py), `scripts/ci_gametest/` (the observer that writes each shard's `result.json`) and [check_gametest_log.py](../../../scripts/check_gametest_log.py) |
| Inputs (read only) | C6 `c8fa16e16be87554f26c402546733df6555d15e6`; C6's sealed hosted GameTest runtime bundle `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/v180-gametest-runtime-c8fa16e16be87554f26c402546733df6555d15e6-1.zip` (SHA-256 `a5be944d7925cbe2c5e6355070fb29016ac9891d3a3da52e1809a77ff3d9eb5d`) and C6's hosted shard results under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/hosted-shards-44770334930/` for comparison |
| Outputs | Tracked: the record `docs/work/v1.8.0-root-queue/records/W469.md`, and a script change with tests only if running the shards locally needs one (opt-in, the CI path unchanged). Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/local-full-runs/` with each run's per-shard `result.json`, logs, checker output, load record and a `SHA256SUMS.txt` |
| Lock | Each GameTest server takes the heavy-JVM lock with the WORKER-RULES loop, one JVM per hold, at least 90 seconds between holds, including across a launcher restart, no hold over two hours; detached launchers and load processes start with no console window. Check that C: and D: each have at least 10 GiB free before each hold. |
| Not allowed | Any change under `src/` (frozen at C6, R600). Running anything but C6's exact code (prefer the sealed runtime bundle; if you build, build only from a `git archive` of C6 and record the identity). Changing a timeout, budget, log expectation or test. Retrying a failed shard and counting only the retry. |

## What to do

1. **Local full run.** Find from the workflow and scripts how a hosted shard runs, and run all four shards of C6's
   full GameTest inventory locally the same way, so that the union of executed tests equals the 1,886-test inventory,
   with the log checker applied as in CI. Record the exact commands.
2. **Three runs.** Run 1 unloaded. Runs 2 and 3 loaded: before each shard starts, launch one busy background process
   per logical core minus one, at normal priority, and stop them when the shard ends; record the core count, the
   load processes and the CPU use you observe. A run is complete only if all four shards finish.
3. **Report** for each run and shard: complete, executed and failed counts, every failed test with its exact failure
   line, and the checker result; and for each of the five items, whether its tests passed in each run. Report every
   other failure as a new observation with its log line. Do not retry a failed shard within a run.
4. **Record** the commands, tables, identities and hashes, and run `python -B scripts/validate_repository.py`; its
   `Summary:` line must show `0 failed`.

## Root's rule for these items (R675)

An item whose condition names local or loaded full runs is **MET** when its tests passed in all three complete local
runs and, where the condition names loaded runs, both loaded runs are among them; any failure of its tests in a local
run makes it **NOT_MET**. Root applies the rule after review; you report the facts.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
