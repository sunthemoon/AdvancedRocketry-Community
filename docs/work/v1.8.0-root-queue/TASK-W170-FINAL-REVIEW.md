# TASK W170-final-review: independent read-only review of the R142 fixes and of risk entry D

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w170` at `c4ab71611`. The R142 fixes are `1af1d76ed..c4ab71611` (11 commits, no merge). Review worktree `D:/GitHub/arce-v180-w170-final-review`, a detached checkout of `c4ab71611` |
| Task it answers | The R142 section of [TASK-W170](TASK-W170-W27D2-REGISTERED-HALF.md) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-rereview/REVIEW.md` (SHA-256 `7c1dc465…24e3`, APPROVE-WITH-CHANGES), with its probes in `probe-src/` there |
| Record | the "Re-review fixes (R142)" section of `docs/work/v1.8.0-root-queue/records/W170.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170/r142/` |
| Root's texts | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/input/R-043-draft-3.md` (SHA-256 `a0e343f5…ce11`) and `.../ADR-065-W27d2-note-draft-1.md` (SHA-256 `046beabf…9928`) |
| Hosted run | the run on `c4ab71611` (Root pushed the branch at 20:22); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-final-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is the last review before W170 merges. Keep it short: check the R142 fixes and Root's two texts. Do not re-review
the earlier rounds, which the previous reviews closed.

## What to check

1. **W170-R41.** R2 waits for both recorded ends. D6 waits for every end that the claim's renewed tickets cover in
   full. The record names one path where D6 does not wait: an in-session claim whose source end is wider than its
   origin column, or whose destination end is not admissible. Is that the only such path? Can a copy of L in that
   end's unloaded part hold unconsumed packages when D7 retires? Rerun the previous review's HIDDEN-section probe.
2. **W170-R42.** A blocked rocket whose preserved data names L counts toward the cap and holds UNEXPECTED_COPY. Rerun
   `aLoadedBlockedImageWhosePreservedDataNamesLMustHold`. Check the new `RocketEntity` accessor reads only.
3. **W170-R44.** Under a REPAIR hold, no INTENT is written. The record says the named A1 test never reaches the new
   path in its normal timing, and that a new A1 case does. Confirm the new case would fail on the unfixed code.
4. **W170-R43, R45, R46 and R47.** The `ARCE_BAY_PROOF_REFUSED` line is bounded. The harness accepts a changed receipt
   file only after the fresh proof's own D7. The A0 test drives `observe` through a real `setRemoved`. The record
   corrections are accurate.
5. **The test-only follow-ups** (`af74af1c2`, `bb31a3fe5`, `77f36df19`) move tests into their own batches and read a
   reason earlier in the tick. Confirm that no assertion, timeout or bound was relaxed and that no existing test
   changed. Check the two new manifest WARN entries match real holds.
6. **R-043 draft 3.** Audit each sentence against the code at `c4ab71611`. Name every sentence that is wrong or
   missing. AGENTS section 3.3 needs an accurate scope, duration, log volume and recovery.
7. **ADR-065's W27d2 note draft.** Check it against the code and the record.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W164
   (`codex/v1.8.0-w164`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the new and changed unit classes of R142, and the previous review's probes;
- the `rocket.bay` and `rocket.server` packages;
- the harness's Python tests (`tests/test_run_v180_bay_forced_stop.py`).

Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full
build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W170-R61, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
