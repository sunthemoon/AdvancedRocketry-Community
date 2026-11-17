# TASK W377: X03 slice X03-A, the offline repair tool

Root: Claude. Date: 2026-11-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w377`, branch `codex/v1.8.0-w377`, at the shared head |
| Decision | Root decision R474 (QUEUE row W377) |
| Design | [W367-DESIGN](records/W367-DESIGN.md) revision 2, sections 1 to 4 and section 6 row X03-A; Root's answers in QUEUE rows W367 and W367F |
| Record | `docs/work/v1.8.0-root-queue/records/W377.md` |
| Write scope | New `scripts/repair_v180_rebuild_source.py` (`plan`, `apply`, `census`), new `tests/test_repair_v180_rebuild_source.py`, small additive helpers in `scripts/loader_cut_nbt.py` or the evidence helpers if the design names them, and the record. No Java, no runtime code. |

## What to do

1. Implement the tool as the design says.
   - It edits exactly the one record of the retired, stationary REBUILD_SOURCE shape, on a copy, under the rule of
     section 2.1, which is stricter than production.
   - The candidate's own `ARCE_TRANSFER_FENCED` line is the bound evidence.
   - It takes the lock and writes reproducible bytes.
   - It refuses unless every root is current.
   - It refuses an FF_KEEP_SOURCE world by the geometric predicate (R474).
   - It refuses every competing authority.
   - A failure has no effect, and interrupted output leaves nothing behind.
   - `census` is a read-only check an operator can run after each stop.
2. Write unit tests on synthetic NBT for every rule and every refusal: the one-record edit, the writer's bytes, the
   current-root table, the FF_KEEP_SOURCE refusal, an interrupted write, and a no-effect failure.
3. Run the new tests and the Python suites of every helper you touch.

## Rules

- The tool never restores a single store, rebuilds a resource or refunds one. It changes nothing it does not name.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- This is a saved-data writer. A different session reviews it read-only before the merge.
- The record gives what was done, the commits, the commands and results. Its last line, and the last line of your
  answer, is the path of `records/W377.md`.
