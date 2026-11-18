# TASK W380G: X03-B, the first bookkeeping fields and a measurement pass

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w380`, branch `codex/v1.8.0-w380`, at W380F's record commit `1f712a153` |
| Decision | Root decision R488 (QUEUE rows W380F and W380G) |
| Design | [W367-DESIGN](records/W367-DESIGN.md) revision 3, sections 3.6, 5 and 9. This branch still has revision 2, so read section 9 (9.1 to 9.3) from `D:/GitHub/ARCE-Task-Evidence/root-queue/accounting-tasks/W367-DESIGN-r3.md`. |
| Prior work | W380's record `docs/work/v1.8.0-root-queue/records/W380.md` on this branch, including the W380F section; evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W380/` and `.../W380F/` |
| Record | Add a section "W380G follow-up (R488)" to `records/W380.md`. Do not rewrite earlier sections. |
| Write scope | `scripts/repair_v180_rebuild_source.py` (only `ROOT_BOOKKEEPING_FIELDS` and the census comparison that reads it), `scripts/x03_repair_oracle.py`, `scripts/run_v180_x03_repair.py`, their tests, and the record. No production Java, no other runner, and no change to the C-S5 assertions. |

## What to do

1. **The two bookkeeping fields (section 9.3).**
   - In the tool and in the oracle, admit exactly `data/clock/logical_game_time` and
     `data/clock/last_observed_game_time` of the satellite mission root, each with its reason.
   - Accept a difference in them only when:
     - the observed time did not decrease;
     - the logical increase equals the observed increase;
     - every other path of that root, the epoch included, is unchanged.
   - Add tests:
     - an accepted forward step;
     - a decreased observed time;
     - unequal increases;
     - a changed epoch;
     - a changed other field;
     - the same paths in another root.
   - Every existing test stays and passes.
2. **Measurement pass.**
   - Run the whole X03-B sequence twice in the runner's measurement mode:
     - seed;
     - qualifying starts;
     - backup;
     - `plan`;
     - `apply`;
     - three repaired observations;
     - the functional descendant with ordinary unload, launch to the Moon and landing read from the files.
   - Measurement mode never promotes, and it must not accept anything.
   - At every stage, record each contradiction with its file and typed path, and the changed files. Do not stop at
     the first contradiction.
   - From the two runs, derive the runtime file list with a reason for each entry. Give it in the record for Root's
     review.
3. **Then one of two outcomes.**
   - **No contradiction remains.** Run the accepted sequence with the derived list:
     - both negative controls natively;
     - the whole-world replacement;
     - the observation after it.
   - **Contradictions remain.** List every one with its evidence, the code that writes it, and a proposed reason or a
     statement that it looks like a defect. Do not admit any of them yourself: Root decides them together.
4. **GameTest.** A full local GameTest run is not required for this Python-only slice.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). The two named fields with their check
  are the only new allowance.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`, the heavy-JVM lock with the 90-second gap, at least 10 GiB free
  on C: and D:, and delete server copies after each run. Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W380G/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by Claude follows before the merge.
- The last line of your answer is the path of `records/W380.md`.
