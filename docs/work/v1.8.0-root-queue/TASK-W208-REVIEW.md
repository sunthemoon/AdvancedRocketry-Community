# TASK W208-review: independent read-only review of the R-040 near-bound availability measurement

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w208` at `2269b69ef`, cut from `bffcdc472`; the slice is `bffcdc472..2269b69ef` (14 files, all test-side: `src/test`, `scripts`, `tests` and the record). Review worktree `D:/GitHub/arce-v180-w208-review`, a detached checkout of `2269b69ef` |
| Task it answers | [TASK-W208](TASK-W208-R040-NEAR-BOUND-AVAILABILITY.md), Root decisions R178 and R180 |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md`, section 3, "W203-P4" |
| Record | `docs/work/v1.8.0-root-queue/records/W208.md` on the branch (the design at its top; the R180 delivery at its end) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W208/` (`native-06/`, `native-07/`, the `r180-*` audits) |
| Hosted run | the run on `2269b69ef` (Root pushed the branch at 09:00); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W208-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W208 reports an eight-case native matrix with two recoveries each: quiet near-bound roots seal in 242 ticks; bounded
traffic, C1 UNKNOWN and C1 expiry end ROLLBACK_PROVED; the first real C1 retain takes about 302 to 485 ms on the server
thread (interrupted control P99 315.868 ms, over docs/17); 27 saved-state replays conserve every identity and resource.

## What to check

1. **The design against the TASK and the plan:** every required case and outcome is covered (small and near roots,
   quiet and bounded traffic, WRITE_PENDING, UNKNOWN and commit, C1 and C3 expiry and retry, a normal stop, the
   interrupted drain, two recoveries); the observation ceiling is recorded as NOT_COMPLETED and never treated as a
   product timeout; the design was committed before the first native run.
2. **Test-side only:** no production source, schema, budget, bound, timeout or assertion changed; the diagnostic
   entries in the native JAR are the only additions (the record says five) and change no production entry.
3. **The fixtures and runner** (`W208JournalFixture`, `W208WriterControl`, `W208NativeAvailability`,
   `W208WriterTiming`, `run_w208_availability.*`, `w208_summary.py`): each new behaviour has a test that failed first;
   the near root really is near the 64 MiB bound; the injected UNKNOWN, expiry and interruption reach the paths they
   claim; nothing in the fixture makes a case pass that production would fail.
4. **The results:** recompute the table and the cost figures from `native-06` and `native-07` (summaries, timing
   samples, GC and RSS); check that `ROLLBACK_PROVED` is a safe failure with U and D conserved, that no effect runs
   before acknowledgement, and that the 27-replay audit checks what the record says.
5. **The C1 retain finding:** where the 302 to 485 ms goes, from the retained logs and timings, and whether it is a
   production cost on the server thread or a fixture artefact. Say what a scoped remedy would have to measure.
6. **Cleanup:** no world, export, archive or lock left by W208; retained failures from the first session are intact.
7. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W207 (`e2dbbb74c`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the four W208 unit classes and the
`rocket.persistence` and `rocket.server` packages; the W208 Python tests and the sealed oracle tests; the summary and
audit scripts on the retained evidence. Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No
GameTest, native case, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W208-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
