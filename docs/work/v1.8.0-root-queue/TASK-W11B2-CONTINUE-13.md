# TASK W11b2, continuation 13: fix the four unit failures, apply the re-review follow-ups, continue step 3

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md)
and [TASK-W11B2-CONTINUE-12](TASK-W11B2-CONTINUE-12.md), including its Root decision, first. You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `9e5d58ab` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); Root's decisions in TASK-W11B2-CONTINUE-09, TASK-W11B2-CONTINUE-12 and `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` (SHA-256 `dfa7e0303718c3eaf8dc53b9cf4ba2483123f7121feb4ad0fd2fa91e5403ba1f`; its last section covers the continuation 11 re-review) |
| Input | `records/W11B2.md`, "Continuation 12" (what is done, the four failures, the remaining scope) |
| Record | Add a "Continuation 13" section to `records/W11B2.md`. Keep the existing text |

## Order of work (commit each step with its tests)

1. **The four unit failures.** These four methods fail at `9e5d58ab` and passed at `e03fa37f`:
   - `EscrowAdmissionAuthorityTest:110`
   - `EscrowFloorsTest:105`
   - `EscrowSettlementWriterTest:102`
   - `RocketEscrowAuditTest:99`

   Each fails while waiting for the writer-unavailable boundary. The suite passed before
   continuation 12's code commits (`cbde4d21`, `db9e884f`, `ba66a268`, `f953ca4e`), so treat this as a
   regression and find the cause:
   - bisect over those commits;
   - read what changed in the writer or coordinator path that these tests drive.

   Fix the cause. Do not extend a timeout, weaken an assertion or skip a test (AGENTS.md section 5).
   W54 made the earlier escrow writer tests deterministic with a package-private test IO thread. If
   these four still poll wall-clock time, you may move them onto that seam, keeping every assertion.
   Say exactly what the cause was.
2. **The re-review follow-ups.** Apply the decisions on N1 to N4 and N6 in `ROOT-DECISIONS.md`
   (section "Re-review of continuation 11"), each in its own commit with its test. For N5, add the
   I3 boundary sentence to your record.
3. **Step 3, continued.** Wire the governed record-3 dispatcher and the action-specific permits
   into the boundary table of design section 1, and route every root-growing write through the
   combined reservation. Then continue with steps 4 to 7 of TASK-W11B2-CONTINUE-10, in their order, as
   far as you get. Stop at a committed step boundary with every test of that step passing, and say
   exactly where the next continuation starts.

A change to an acknowledgement order, a proof, a hold or the replay exclusion needs Root first. In
that case, stop, commit what you have with your record, and report.

## Commands

- The whole unit suite, which must pass with no failure, on the final committed SHA.
- `runGameTestServer` under the lock on a **committed** SHA, then the unchanged log checker. Only N1's
  three warnings may remain.
- Native cuts under the lock for any step that adds one, with evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C13/`.
- At the end, delete your generated `build/gametest/world` and Temp copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, and signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).
