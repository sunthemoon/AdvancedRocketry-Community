# TASK W376: X03 slice X03-V, the gated verification mode

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w376`, branch `codex/v1.8.0-w376`, at the shared head |
| Decision | Root decision R474 (QUEUE row W376) |
| Design | [W367-DESIGN](records/W367-DESIGN.md) revision 2, sections 5 and 5.1, and section 6 row X03-V; Root's answers in QUEUE rows W367 and W367F |
| Record | `docs/work/v1.8.0-root-queue/records/W376.md` |
| Write scope | The Java and inventory files that section 5.1 names: the new gated probe class, the `verify` scenario, the cases `FF_X03_OBSERVE`, `FF_X03_UNLOAD` and `FF_X03_LAUNCH`, the routing and probe inventories (`escrow-probe-cases.tsv`, `EscrowProbeRoutingTest`, the Python inventory test), the skeleton of `scripts/run_v180_x03_repair.py` if the section asks for it, their tests, and the record. |

## What to do

1. Implement the gated verification mode exactly as section 5.1 describes. It works on a repaired copy whose transfer
   record is gone, observes, admits an ordinary unload on the server thread, and launches. It does nothing without the
   release-test hooks, the open release-test gate and the `verify` scenario.
2. Update the inventories. The design expects 674 to 677 rows, 23 to 24 direct producers and 41 to 42 related tests,
   derived by reading the generator. Run the generator, use its real numbers, and say whether they differ.
3. Run the gating tests, both inventory tests, the whole unit suite, and one full GameTest server with the log checker
   under the heavy-JVM lock. Report any further pinned count you meet (for example, the relaunch static inventory
   `src/test/resources/relaunch-existing-dependencies.txt`) and update it only after an initializer audit, as R445 did.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Production behaviour must not change
  without the gates.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Heavy runs go under the lock.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- A read-only review by another engine follows before the merge.
- The record gives what was done, the commits, the commands and results, and the inventory numbers. Its last line, and
  the last line of your answer, is the path of `records/W376.md`.
