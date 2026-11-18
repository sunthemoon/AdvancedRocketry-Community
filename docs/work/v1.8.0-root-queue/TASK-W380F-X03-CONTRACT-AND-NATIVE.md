# TASK W380F: X03-B follow-up, the revision-3 contract and the complete native sequence

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w380`, branch `codex/v1.8.0-w380`, at W380's record commit `269cf73c2` |
| Decision | Root decision R487 (QUEUE rows W380 and W380F) |
| Design | [W367-DESIGN](records/W367-DESIGN.md) revision 3. Section 9 is new and amends sections 2.2, 3.5, 5.2 and 5.3. On this branch the design is still at revision 2, so read section 9 from the copy at `D:/GitHub/ARCE-Task-Evidence/root-queue/accounting-tasks/W367-DESIGN-r3.md`. |
| Prior work | W380's record `docs/work/v1.8.0-root-queue/records/W380.md` on this branch, and its evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W380/` |
| Record | Add a section "W380F follow-up (R487)" to `records/W380.md`. Do not rewrite W380's sections; mark the superseded statements where they stand. |
| Write scope | `scripts/repair_v180_rebuild_source.py` (only the `fence` rule and the region header reading), `tests/test_repair_v180_rebuild_source.py`, `scripts/x03_repair_oracle.py`, `scripts/run_v180_x03_repair.py`, their tests, and the record. No production Java, no other runner, and no change to the C-S5 assertions. |

## What to do

1. **The fence rule (section 9.1).**
   - Change `fence` in the repair tool so that:
     - every line names T;
     - every reason is `FLOOR_CONFLICT` or `ENDPOINT_JOIN_PROOF_REQUIRED`;
     - at least one `FLOOR_CONFLICT` line is required.
   - The plan binds the log hash and the first `FLOOR_CONFLICT` line.
   - Add tests for each refusal:
     - another transfer;
     - another derived reason, such as WAITING_UNLOADED;
     - only transient lines;
     - no lines.
   - Add a test for the accepted mixed sequence.
   - The runner's own fence assertion follows the same rule.
2. **The region reader (section 9.2).**
   - In the tool and in the oracle, a `.mca` file of exactly zero bytes is a region with no chunks.
   - A file of 1 to 8,191 bytes still refuses.
   - The file stays in the census with its hash.
   - Add tests for zero bytes, a 1-byte file, an 8,191-byte file and a valid header.
   - Every existing tool test stays and passes unchanged.
3. **The complete native sequence.** Rerun X03-B as W367-DESIGN sections 3 and 5 and TASK-W380 describe, on a
   locally built JAR under the heavy-JVM lock:
   - two complete dry runs, and from them the runtime file list, each entry with its reason, for Root's review;
   - the positive sequence through `plan`, `apply`, the verification starts, the functional descendant (ordinary
     unload, launch to the Moon, landing read from the files) and the whole-world replacement;
   - the three refusals natively: FF_KEEP_SOURCE by `DESTINATION_IN_TARGET_CHUNK`, `ROOT_NOT_CURRENT`, and
     `UNVALIDATED_AUTHORITY`;
   - both negative controls natively.
   If a further native fact contradicts the design, stop at that point and report it with evidence. Do not widen the
   file list or any check to get past it.
4. **GameTest.** A full local GameTest run is not required for this Python-only slice; the branch's hosted regression
   covers it. If you run one, record the result as it is.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). The amended fence and region rules
  are the only contract changes allowed, and each is pinned by new tests.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Keep the 90-second gap between lock holds, check 10 GiB free on
  C: and D: before heavy steps, and delete server copies after each run. Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W380F/`.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by Claude follows before the merge.
- The last line of your answer is the path of `records/W380.md`.
