# TASK W11b2, continuation 18: one charging rule, the shared merge, the rest of step 3, then steps 4 to 7

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-17](TASK-W11B2-CONTINUE-17.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `80bbc1ac` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Input | `records/W11B2.md`, "Continuation 17" (R5 to R7 applied; stopped at the flight-table cleanup case) |
| Root decision | "Continuation 17: one charging rule for every reserved region" (R8 and R9) at the end of `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `05324af2e613228f4238268d6001cbec9dfb55ca04e0d4198b5e0ec809eafd7e`) |
| Record | Add a "Continuation 18" section to `records/W11B2.md`. Keep the existing text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C18/` |

## What to do (commit each step with its tests)

1. **Apply R8 and R9, in their own commit.** R8 is a general rule: apply it to every region with the same
   pattern without stopping, and stop only under R8's own stop condition.
2. **Merge the shared branch** (`codex/v1.8.0-classic-content`, at `37babcd5` or later) into this branch, in
   its own merge commit.
   - A trial merge at `b451e436` conflicts in `persistence/migration/ManagedSavedDataType.java`,
     `resourceescrow/EscrowLedgerWriter.java`, `StationWorldMigrationTest.java` and `EscrowFloorsTest.java`.
     They come from the W44 and W72 merges (W72 moved the first-event seam behind a committer selector that
     the persistence package owns).
   - Keep both sides' behaviour. Do not drop a test or an assertion from either side. If a conflict needs a
     design choice, stop and report.
   - Then run the whole unit suite, and the GameTests under the lock on the committed merge, with the
     unchanged log checker. The shared branch has about 1,097 GameTests; say how many ran.
3. **Finish step 3**, where continuation 15 says it resumes: the live launch and F1 reservation connection;
   the action-specific recovery fence use; the destination, passenger, removal and return permits; deferred
   callbacks, cancellation and failed landing.
4. **Steps 4 to 7 of TASK-W11B2-CONTINUE-10, in order:** F0 to F8 and C0 to C8; the required test that
   closes N1; the A0 cases and the S2 cuts; F6 to F11 with the eight-flight benchmark as a measurement.

Keep the whole unit suite passing after every step. A change to an acknowledgement order, a proof, a hold or
the replay exclusion needs Root first: stop, commit what you have with your record, and report. If you run
short of time, stop at a committed step boundary with every test of that step passing, and say exactly where
the next continuation starts.

The intermittent sleep, natural-beds, first-Moon-landing and spawn-egg GameTests belong to W74 and W76. If one
fails in your run, record it with the log line and say that your change does not touch it. Do not retry it
until it passes, and do not edit it.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C18/`. If your
cleanup command is refused, record the exact paths; Root deletes them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, signed per-fluid accounting.
- Part-1 findings F3, F8 and N2 (after the W11b2 merge).
- ADRs, the risk register, status files and the ledger (Root).
