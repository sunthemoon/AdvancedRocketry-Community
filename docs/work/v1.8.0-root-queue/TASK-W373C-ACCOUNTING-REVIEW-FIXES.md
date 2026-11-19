# TASK W373C: W373B's review fixes and the loader's settlement cuts

Root: Claude. Date: 2026-11-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. The worktree's
branch predates this file; a copy is at `D:/GitHub/ARCE-Task-Evidence/root-queue/accounting-tasks/TASK-W373C.md`.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w373b`, branch `codex/v1.8.0-w373b` at `87b60694a` (W373B's record commit) |
| Decision | Root decision R515 (QUEUE row W373B) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W373B-review/REVIEW.md` (W373BR, APPROVE-WITH-CHANGES): findings 1 to 3 and the two production answers |
| Prior work | `docs/work/v1.8.0-root-queue/records/W373B.md` on the branch, and `TASK-W373B-ACCOUNTING-NATIVE-CUTS.md` |
| Record | Add a section "W373C follow-up (R515)" to `records/W373B.md`; do not rewrite earlier sections except where item 4 says so |
| Write scope | As W373B's TASK: the record; the gated probe under `resourceescrow/` and its unit tests; `scripts/accounting_cut_oracle.py`, `scripts/run_v180_accounting_cuts.py`, `scripts/run_v180_accounting_format.py`, the loader runner modules they call only if a case needs it, the campaign driver's accounting families, `escrow-probe-cases.tsv`, and their tests. No production behaviour, ceiling, limit or log-manifest allowance. |

## What to do

1. **Finding 1, the upgrade watcher.** A first start whose migration completed must never pass as a stop during the
   migration. Check completion before the backup directory or staged file, and check the captured log again after
   the kill. A completed first start makes both DURING cases NOT_COVERED, whatever artifact was seen. Count only files
   that this attempt created: record the names present before the start. Add regression tests for completion with a
   persistent backup directory, and completion with a stale staged file.
2. **Finding 2, exact markers at L2.** At the cut, `L2_REPLACED` and `C7_ACKNOWLEDGED` require exactly one marker on
   each endpoint; after the restarts, exactly zero. Add negative controls for zero and for one-sided pruning, in both
   cargo directions.
3. **Finding 3, the BEFORE_COMMIT snapshot.** Check the decoded endpoints at that cut exactly: the source water
   5,000 mB at epoch 0 with no markers, the destination empty at epoch 0 with no markers. Keep the raw snapshot. Add
   a negative control that changes an endpoint amount or epoch while the root-2 ledger stays the same.
4. **The record.**
   - Section 4, point 10: describe the cargo cases as covering the shared settlement core (operator preview and
     confirm, O2 proof, O3 and O4 writes, the pruning cursor). They do not cover the loader's own endpoint reader or
     its pruning branch (`EscrowSettlementDisk`, `EscrowSettlementPruning`), as the review found.
   - Section 6, O2: add the review's answer. Five interrupted pre-commit upgrades of a world that still needs
     migration fill the five backup slots, and the next start is refused with `ARCE-BETA-2004` until the operator
     archives older backups (design section 4.2). An upgraded world is not refused. A stale staged file is never
     removed.
5. **The loader's settlement cuts with the exact oracle.** Give the fuel loader's settlement the same exact account
   checks as the cargo cuts, at O1, O2, O3, O4 and pruning, each before and after its write, with a forced stop and two
   restarts. Reuse the existing `LF_S6_` probe cases where they stop at those boundaries (see
   `scripts/run_v180_loader_s6.py` and `escrow-probe-cases.tsv`), checked through the existing loader oracle and then
   the account oracle, as W373B did for L1 and L2. Add a known loss and a known gain for the loader if the gated
   probe can make them as the `FS_AC_` cases do. Where a boundary has no probe case, add a gated one in the style of
   the `FS_AC_` cases. Add these cases to the `accounting-cuts-loader` family. If a case needs more than about a day
   of work, stop, record what it needs, and finish the rest.
6. **Verification.**
   - Replay the stricter oracle on every saved raw state of W373B's 58 cases, offline. They must pass unchanged; a
     failure is a finding to record, not a reason to relax the oracle.
   - Rerun natively, on a JAR built from your branch head under the heavy-JVM lock, the family
     `accounting-root2-upgrade` (all three cases) and the new loader cases.
   - If you change Java, run its unit tests, the full unit suite once, and one full GameTest run with the log checker.
   - Run the Python modules W373B's commands table lists, and the probe inventory check.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Never relax an oracle.
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. Use the heavy-JVM lock with the 90-second gap, keep at least
  10 GiB free on C: and D:, and delete server copies after each run. Launch detached Python with the real `python.exe`,
  not the pyenv shim. Keep evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/W373B/followup/`.
- If a case shows a production defect, do not fix production: record the evidence and stop that case.
- Commit on the branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The W373BR reviewer (a Codex session) checks the follow-up before the merge.
- The last line of the record, and the last line of your answer, is the path of `records/W373B.md`.
