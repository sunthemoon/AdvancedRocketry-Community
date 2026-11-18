# TASK W380H: X03-B, Root's measurement decisions and the unload diagnosis

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w380`, branch `codex/v1.8.0-w380`, at W380G's commit `863bc63c8` |
| Decision | Root decision R490 (QUEUE rows W380G and W380H) |
| Design | [W367-DESIGN](records/W367-DESIGN.md), revision 3. Section 9.4 is new. This branch still has revision 2, so read section 9 from `D:/GitHub/ARCE-Task-Evidence/root-queue/accounting-tasks/W367-DESIGN-r3.md`. |
| Prior work | `records/W380.md` on this branch, including the W380F and W380G sections. W380G's evidence is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W380G/` (see `measurement-summary.json`, `runtime-list-proposal.json`, `offline-replay.json` and `complete/measurement/cycle-1-functional-0.log`). |
| Record | Add a section "W380H follow-up (R490)" to `records/W380.md`. |
| Write scope | `scripts/repair_v180_rebuild_source.py` (only its census lists and comparisons), `scripts/x03_repair_oracle.py`, `scripts/run_v180_x03_repair.py`, their tests, `resourceescrow/EscrowCargoRepairVerifyProbe.java` and its unit tests, and the record. Other production Java only as in step 2. |

## What to do

1. **Apply section 9.4, points 1 to 4.** For each point, add tests that refuse the case just outside it.
   - **The runtime list.**
     - Install the 18 names with their reasons.
     - Inside `raids.dat`, only `/data/Tick` may change, and it may not decrease.
     - The region and entity per-object comparisons stay.
   - **The generation box.**
     - Derive it from the candidate:
       - the destination warmup tickets' radius (`RocketDestinationWarmups`, `RocketPadTerrainPreparation`);
       - vanilla's chunk-status dependency margin, checked with `javap` on the mapped jar as W380G did.
     - Write the derivation in the record.
     - In the margin, allow only chunks that were absent before.
     - The box must contain W380G's 288 ring chunks. If it does not, stop and report.
   - **The progression receipt.**
     - Allow exactly one `first_moon_landing` receipt with the codec's fields and `source` = `rocket_transfers`.
     - Its landing must match the observed Moon landing.
     - Anything else stays refused.
   - **The launch settle.**
     - `FF_X03_LAUNCH` reports only after the ledger holds no armed-flight row for R and the writers are quiet.
     - This stays inside the existing 6,000-tick bound.
     - Add a unit test for the wait.
2. **Diagnose the unload.** In both of W380G's runs, `FF_X03_UNLOAD`:
   - named the device through `EscrowCargoSwitches.forTests`;
   - observed the rocket at 4,000 mB and the device at 1,000 mB of a 16,000 mB tank, with both authorities true;
   - saw no operation start within 6,000 ticks.

   Find the refusing condition in the device's tick path (`RocketFluidCargoBlockEntity.serverTick`, its pacer and
   preconditions, and `EscrowFluidCargoAdmission`). Compare with how the seed fixture's own unload is switched on in
   the `FF_REBUILD_SOURCE` case, which does unload.
   - If the cause is in the gated probe (for example a switch key that differs from the admission's device identity,
     or a precondition the seed fixture sets and the probe does not), fix the probe with a unit test that fails
     without the fix.
   - If the cause is in production code, do not change production. Write a failing unit test or GameTest that names
     the condition, record the evidence, and stop the native part there. Root routes that fix, because the
     accounting activation is changing the same area on another branch.
3. **The accepted sequence.** If step 2 ends with a probe fix, run the accepted sequence natively:
   - the seed and the qualifying starts;
   - the backup, `plan` and `apply`;
   - three repaired observations;
   - the functional descendant with a completed ordinary unload, the launch and the settled landing;
   - both negative controls;
   - the whole-world replacement and the observation after it.

   If any new contradiction appears, stop and report it with its evidence. Do not admit it yourself.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). The allowances of section 9.4 are the
  only new ones, and each is pinned by tests.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run.
- Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W380H/`. Launch detached Python with the real
  `python.exe`, not the pyenv shim.
- Run the touched Java classes' unit tests and the full unit suite once, and the six Python suites W380G ran.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by Claude follows before the merge.
- The last line of your answer is the path of `records/W380.md`.
