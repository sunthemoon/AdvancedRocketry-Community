# TASK W159-rereview: independent re-review of the W159F fixes

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w159` at `1b4ab84cc`: six W159F commits on `397a0a525`. Read `git diff 397a0a525..1b4ab84cc` (18 files). Review worktree `D:/GitHub/arce-v180-w159-review`, a detached checkout of `1b4ab84cc` |
| Task it answers | [TASK-W159F](TASK-W159F-P2-COMMIT-POINT.md), Root decision R108, and the R108 correction in section 4 of [records/W80-DESIGN.md](records/W80-DESIGN.md) |
| Earlier review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159-review/REVIEW.md` (BLOCK: W159-R1 to W159-R3), with its controller harness in `independent-run/` |
| Record | the "W159F" section of `docs/work/v1.8.0-root-queue/records/W159.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159F/`, and the correction files in `D:/GitHub/ARCE-Task-Evidence/root-queue/W159/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W159-R1 is closed.** A death, a clone or a new login before P2 is installed keeps the obligation PENDING, and
   the new session is guarded and placed with a new attempt. Rerun the earlier review's controller interleaving
   against the new code, or show that a repository test now covers it.
2. **W159-R2 is closed.** If the target becomes unsafe before P2 is installed, the obligation survives another login
   and a restart. After installation, the residual path keeps the player guarded, places them again in the same
   session, and unguards only after a safe recheck. Check that the record states the residual window correctly.
3. **The writer change.** A P2 capture is revocable, and it is discarded only when its lease failed before the
   committer ran and no earlier attempt of it has an uncertain outcome. Check these points:
   - an uncertain outcome is never discarded;
   - the discarded generation is never reused;
   - no later root can depend on a revocable capture;
   - S2's proof lease, its successor and its retries behave exactly as before;
   - `acknowledgedCurrent`, the dirty flag and vanilla's save path stay correct after a discard;
   - the `beforeProof` gate is test-only and touches no Level.
4. **The shutdown change** in `9c4c3e352`: a scan in flight is drained at server stop, and no thread or ticket is
   left behind.
5. **W159-R3 is closed.** The W159 sealed files are unchanged; `CORRECTION-01.md` and the new manifest verify. W159F's
   own manifest verifies.
6. **Nothing weakened.** No pre-existing test was deleted or loosened. No new saved field or schema, and no change to
   the journal root, bounds, budgets, timeouts or protocols. New log lines have exact manifest entries.
7. **Merge.** List any conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the W159F test classes alone;
- then the `rocket.rejoin` package;
- `tests/test_run_w159_rejoin_cuts.py`.

**Take the heavy-JVM lock for every Gradle run in this review**, with the atomic pattern and the 90-second hand-off;
W152G shares it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards, and leave no generated folders outside your output folder.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W159-R4, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.