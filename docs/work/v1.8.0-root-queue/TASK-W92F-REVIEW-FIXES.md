# TASK W92F: the W92 review's follow-ups (P1-F8)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W92](TASK-W92-P1-F8.md) and
[records/W92.md](records/W92.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w92f` (from the shared branch, which has W92 merged at `45b55068`) |
| Branch | `codex/v1.8.0-w92f` (local commits only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W92-review/REVIEW.md` (fresh Claude session, MERGE-WITH-FOLLOWUP, SHA-256 `4c8b9c2df008fba17003937041863112775192377897434fbb9676c1bbab3a01`), findings W92-R1 to W92-R14 |
| Record | `records/W92.md` (add a W92F section) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W92F/` |

## Root decisions on the review (recorded here)

- **W92-R2:** M2 step 2 also rechecks, read-only, that the v0.5 transaction journal is operational and has no pending
  recovery overlapping E's region (for example a `Request.restorable()` evaluated with `matches()`); a failure goes to
  M3 without restoring and writes T back.
- **W92-R4:** a settled, non-native schema-1 T may be disassembled in place: Design M's "schema 2" clause also admits a
  schema-1 record without a native transfer, and the write-back puts back the identical schema-1 record (no format
  change). Schema-3 governed records stay excluded (G9).
- **W92-R8:** the diagnostic native cuts K6 and K11 move to the S2 campaign; not in this task.
- **W92-R13:** recorded for W11b3 and admission; not in this task.
- **W92-R14:** after W91 merges, Root has the two journal checks shared; not in this task.

## What to do

1. W92-R2 and W92-R4 as decided above, each with a unit test or GameTest that fails without the change.
2. W92-R3: contain a notifier failure in the discard paths (`removalDiscarded` and the write-back's discarded callback):
   log once and continue with the decision.
3. W92-R5: a neutral pending text in both locales, for example "A disassembly request for this rocket is still being
   processed; try again when it finishes.", regenerated with `runData`.
4. W92-R1, W92-R6 and W92-R7: G9 shows that the release would otherwise happen; restart 2 checks for a repeated
   rebuild and the descent wait cannot be skipped; an integration test for M3's unloaded-E branch.
5. W92-R9: rerun the W63 resource-restart case F1 and the v1.3 adapter flight smoke at your committed source.
6. Run each changed class alone, the whole unit suite, `runData` with a clean diff, and one full GameTest with the
   unchanged log checker (add an expectation only for a line your new code drives) on a **committed** SHA (W92-R11).
   Do not add a GameTest batch unless needed (379 batch names; about 384 reshuffles the run order).

## Write scope and boundaries

`rocket/server/RocketLandedDisassemblies`, `RocketDisassemblyService` and the classes W92 changed, the landed
disassembly language provider, the W92 native harness and its test, new tests, and the record. No saved-format change,
no change to an acknowledgement order or a proof outside the decisions above, and no relaxed assertion, limit or
timeout.

## Commands

As WORKER-RULES says. Other workers (W91D, W94B, W95F) also run tests; take the heavy-JVM lock for every GameTest,
`runData` and native run, and release it between runs when another worker waits. When your heavy runs end, remove your
own lock with a plain `rm -rf` of exactly `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` after checking that its
`owner.txt` names you; if the tool refuses, write the path in your record and final answer at once. Check that C: and
D: have more than 10 GiB free before each heavy start.

## Not in this task

W91, W94B, W95F, the S2 campaign, ADRs, the risk register, status files and the ledger (Root).
