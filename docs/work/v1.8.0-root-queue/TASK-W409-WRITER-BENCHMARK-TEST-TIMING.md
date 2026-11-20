# TASK W409: make the writer benchmark test reach its readback path regardless of runner speed

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w409`, branch `codex/v1.8.0-w409` from the shared branch |
| Decision | Root decision R551 (QUEUE row W409) |
| Failure | Shared regression run 44672650620 at `f2bf5b79c` (2026-11-20), build job: `EscrowWriterBenchmarkAccessTest.pacedMutationsOfBothRootsPublishOnOwnerTicksAndEveryTenthTakesTheReadbackPath` failed at line 41 (`readback_paths_taken >= 1` for both roots); 6,743 other unit tests passed. The same commit's tests passed on W404's branch run and locally. |
| Record | `docs/work/v1.8.0-root-queue/records/W409.md` |
| Write scope | `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowWriterBenchmarkAccessTest.java` and, only if the test cannot be made deterministic otherwise, test-only helpers in `src/test/`. No change to `src/main` (the benchmark access class stays as it is), no other test. |
| Not allowed | A full GameTest run, `runData`, a full build or any native server (the heavy-JVM lock belongs to C4's native runs). Targeted unit tests are allowed (`JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`). |

## Cause, as Root read it

`EscrowWriterBenchmarkAccess` sets the readback flag on the tenth published mutation of each root
(`mutations % READBACK_EVERY == READBACK_EVERY - 1`), and counts a mutation attempted while the previous write is
still unpublished as `skipped_while_unpublished` instead. The test makes 21 attempts per root with `Thread.sleep(2)`
between ticks. On a slow runner more than 11 attempts can be skipped, so fewer than ten mutations publish, the
readback path is never taken, and line 41 fails. The design is timing-dependent; the product is not at fault.

## What to do

1. Confirm or correct the cause above from the code.
2. Change the test so that the readback path is always reached and the check gets stronger, not weaker: keep
   attempting mutations, within a fixed upper bound of attempts (fail if the bound is reached, with a message that
   gives the counts), until each root has published at least ten mutations; then assert, for each root, that the
   readback count equals `published / READBACK_EVERY` exactly, that mutations plus skips equal the attempts, that
   `published` equals `mutations`, and every other existing assertion. Do not remove any assertion or raise a sleep
   or timeout to make it pass (AGENTS.md section 5).
3. Run the test 20 times in a row (`./gradlew test --tests '*EscrowWriterBenchmarkAccessTest' --rerun-tasks` in a
   loop, or JUnit repetition in a temporary local change you do not commit), plus once with the CPU loaded (for
   example a parallel busy loop in another process), and record the results; then the escrow tests once.
4. Run `python -B scripts/validate_repository.py`, which must end with `0 failed`. The record gives the cause, the
   change, the repeated runs and their results.

## Rules

- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
