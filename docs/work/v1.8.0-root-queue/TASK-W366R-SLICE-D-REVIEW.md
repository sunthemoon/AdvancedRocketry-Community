# TASK W366R: independent read-only review of accounting Slice D

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w366r`, detached at W366's commit `614d1bc0c` |
| Decision | Root decision R462 (QUEUE row W366R) |
| Under review | `git diff 2759ff017 614d1bc0c`, written by a Codex session under [TASK-W366](TASK-W366-ACCOUNTING-SLICE-D.md), with its record `records/W366.md` |
| Design | [W357-DESIGN](records/W357-DESIGN.md), sections 2.2, 2.3, 3, 4.1 and 6; the earlier slices' records W361, W362 and W364 |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W366-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## Why

Slice D creates and releases schema-4 receipts with the final account digest, a saved-form change. AGENTS.md section 13
requires a separate read-only review before it merges. The author was a Codex session. You are a different session,
and you work from the code alone.

## What to check

1. **Preview and confirm.**
   - Per-key deltas, residuals, missing flags and owners are shown.
   - A non-zero residual is a signed proposed discrepancy, and a half-applied transfer is shown as such.
   - Unknown contributions print UNKNOWN.
   - The intent commits every account projection and approved discrepancy.
   - Confirmation repeats the observations, the actor and digest checks and the 200-tick interval.
2. **O1 to O4.**
   - O1 freezes the rows with the confirmed intent in one generation.
   - O2 recomputes typed vectors, and a changed vector invalidates the proof even when the scalar total is unchanged.
   - O3 writes the exact final account digest into a schema-4 receipt and infers no new loss.
   - O4 revalidates, and release never turns UNKNOWN into KNOWN.
   - A mismatch after O1 holds the frozen decision.
3. **ABANDON, settle-floor and pruning.**
   - ABANDON keeps the known side and records UNKNOWN for the missing side, with no rebuild or refund.
   - A bare floor's settlement creates no row.
   - Pruning keeps vectors and permanent rows.
4. **The item caller.** `EscrowItemCargoAdmission` passes the combined pending root, and root-2 behaviour is unchanged.
5. **Logs.** `ARCE_ESCROW_ACCOUNTING_RELEASED` and the audit INFO are bounded as the design says. Inspect is
   paginated, and no native tag body is logged.
6. **Activation off.** With `EscrowAccountingFormat.ENABLED` false, nothing in an existing world or file changes.
7. **Tests.** No existing test, assertion or pin was deleted, loosened or changed (AGENTS.md section 5), and the new
   tests cover the slice's decision paths.

You may run the targeted tests and the full unit suite from a copy in your own Temp folder under
`D:/GitHub/ARCE-Task-Evidence/Temp/`, with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` and `--offline`. Do not take the
heavy-JVM lock and run no GameTest or native server.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the file and line, what is wrong and the change you recommend. Then give
the commands you ran with their results. Give its SHA-256 in your answer. The last line of your answer is the path of
`REVIEW.md`.
