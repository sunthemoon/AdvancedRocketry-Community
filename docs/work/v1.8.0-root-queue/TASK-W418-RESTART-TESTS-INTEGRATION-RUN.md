# TASK W418: run the new restart GameTests of W415, W416 and W417 together, under the lock

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w418`, branch `codex/v1.8.0-w418` from the shared branch |
| Decision | Root decision R569 (QUEUE row W418) |
| Inputs | Branch heads, already reviewed by Root for scope: W415 `f0656840f` (`codex/v1.8.0-w415`), W416 `cc59e162d` (`codex/v1.8.0-w416`), W417 `e610de350` (`codex/v1.8.0-w417`). Each record (`docs/work/v1.8.0-root-queue/records/W415.md`, `W416.md`, `W417.md` on its branch) lists its new batch names and the planned command. |
| Record | `docs/work/v1.8.0-root-queue/records/W418.md` |
| Write scope | Your branch only: the three merges, the record, and, only if a new test itself is wrong (not the product), the smallest test-only correction in that test's file, with the reason. No product code. |

## What to do

1. Merge the three heads into your branch by SHA (`git merge --no-ff <sha>`), in the order W415, W416, W417, and
   resolve any conflict in test files only. Compile and run the unit tests of the touched packages.
2. **Lock.** Candidate C5's native pre-run holds the heavy-JVM lock until about 21:00; a watcher then stops C5 and
   frees the lock. Wait for it with the WORKER-RULES loop (poll every 60 seconds), for at most three hours. Do not
   break a lock you do not own, even an old one. Hold the lock only while the GameTest server runs.
3. Run all new batches of the three tasks in one GameTest server run: `.\gradlew.bat --offline --no-daemon --no-parallel
   --max-workers=1 --console=plain runGameTestServer` with `JAVA_TOOL_OPTIONS=-Dadvancedrocketrycommunity.gameTestBatches=<all
   their batch names, comma separated>` and `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Release the lock at once.
4. For each test: pass or fail, with the failure message and the relevant log lines. A failing test is classified:
   a defect in the test (fix it test-only, rerun that batch, and say why) or a product finding (do not change product
   code; record the evidence and stop that test there). Keep `build/gametest/logs/latest.log` and the result files
   by copying them to `D:/GitHub/ARCE-Task-Evidence/root-queue/W418/` before anything removes them.
5. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on your branch; do not push. The last line of your answer is the path of the record.
