# TASK W380I: X03-B, the landing ticket and the accepted sequence

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w380`, branch `codex/v1.8.0-w380`, at W380H's commit `812ed9210` |
| Decision | Root decision R495 (QUEUE rows W380H and W380I) |
| Design | [W367-DESIGN](records/W367-DESIGN.md), revision 3, sections 9.1 to 9.5. This branch has revision 2, so read section 9 from `D:/GitHub/ARCE-Task-Evidence/root-queue/accounting-tasks/W367-DESIGN-r3.md`. |
| Prior work | `records/W380.md` on this branch, including the W380F, W380G and W380H sections. W380H's evidence is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W380H/`. |
| Record | Add a section "W380I follow-up (R495)" to `records/W380.md`. |
| Write scope | `resourceescrow/EscrowCargoRepairVerifyProbe.java` and its unit tests, `scripts/run_v180_x03_repair.py` and `scripts/x03_repair_oracle.py` only where the landing ticket must be named, their tests, and the record. No production code. |

## What to do

1. **The landing ticket (section 9.5).**
   - When `FF_X03_LAUNCH` first observes the landed rocket alive, it adds its own non-persistent ticket at distance 0
     on the landing chunk. Use the ticket kind it already holds at the source.
   - It removes the ticket when the probe ends, including on failure.
   - The live observation after pruning keeps its full contract. A rocket that is not alive when first observed is
     still `ROCKET_READBACK_ABSENT`.
   - Add unit tests for the ticket's addition, its removal on the normal end and on failure, and the unchanged absent
     case.
2. **The accepted sequence.** Run the complete X03-B sequence natively, as W380H did, on a locally built JAR under the
   heavy-JVM lock:
   - the seed and the qualifying starts;
   - the backup, `plan` and `apply`;
   - three repaired observations;
   - the functional descendant: the ordinary unload, the launch with the settled live observation, and the
     post-landing restart;
   - the second cycle;
   - both negative controls;
   - the whole-world replacement and the observation after it.

   Use the 18-name runtime list, the generation box and the receipt rule of section 9.4. If a new contradiction
   appears, stop, report it with its evidence, and do not admit it yourself.
3. **Verification.**
   - Run the unit tests of the probe and the full unit suite once.
   - Run the six Python suites W380H ran.
   - Run one full GameTest run with the log checker, because the probe is Java.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real `python.exe`.
  Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W380I/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by Claude follows before the merge.
- The last line of your answer is the path of `records/W380.md`.
