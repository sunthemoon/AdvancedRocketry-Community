# TASK W170-rereview: independent read-only review of the W170 R137 and R139 fixes

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w170` at `1af1d76ed`. The fixes are `26ed25928..1af1d76ed` (17 commits, no merge): R137 is `823175d74..97217ccb2` and R139 is `f986c771c..1af1d76ed`. Review worktree `D:/GitHub/arce-v180-w170-rereview`, a detached checkout of `1af1d76ed` |
| Task it answers | The R137 and R139 sections of [TASK-W170](TASK-W170-W27D2-REGISTERED-HALF.md) |
| Previous reviews | Pass 1, `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/pass1/REVIEW.md` (SHA-256 `74f48f6b…ab8e`, APPROVE-WITH-CHANGES). Pass 2, `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/pass2/REVIEW.md` (SHA-256 `d1a94f9f…956b`, BLOCK), with its probes in the same folder |
| Record | the "Pass 1 fixes (R137)" and "Pass 2 fixes (R139)" sections of `docs/work/v1.8.0-root-queue/records/W170.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170/r137/` and `.../W170/r139/` |
| Risk draft | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/input/R-043-draft-2.md`, Root's draft of risk entry D |
| Hosted run | the run on `1af1d76ed` (Root pushed the branch at 18:20); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice. The same engine implemented the fixes, so review the code, not the record's account
of it. Deployment admission must stay off outside the GameTest server and the full release-test gate.

## What to check

1. **Pass 1's findings are closed.**
   - W170-R1: `BayConsumedSnapshots.Job.step` reconstructs at most one whole snapshot per tick, and a test pins it.
   - W170-R3: the REJECTED-after-registration branch cannot be reached.
   - W170-R6 and W170-R7: the new tests pin what they claim.
   - W170-R9: the native reruns on the merged source covered every case that goes through D2's launches.
2. **Pass 2's findings are closed.** Rerun each pass 2 probe; each must now hold or refuse as the finding asked.
   - W170-R21: the census counts every loaded image naming L at both ends before it classifies. Look for any image it
     can still miss: an unloaded or unticked chunk, the destination column, a passenger or vehicle, or the census
     bound.
   - W170-R22: every source position write or removal after D6's capture invalidates the token, through captured and
     queued retirement. Judge the record's two residuals: a mod that writes positions every tick starves D7, and a
     non-motion change can reach the guard in the same tick.
   - W170-R23: R2 and R1's renewal before re-establishment wait for the exact checked L/T acknowledgement. Check the
     record's choice that from CONSUME on R1 renews tickets without the acknowledgement: what keeps those tickets
     from lasting indefinitely, and can any player or command reach that state?
   - W170-R24: the absent-source branch checks all escrow eligibility before it publishes, and a hold changes nothing.
   - W170-R25: one owner binds a group, for BEFORE, AFTER and mixed-owner groups.
   - W170-R4's remainder: no D2 runs under a REPAIR hold.
3. **Every new hold writes nothing.** That covers `UNEXPECTED_COPY`, `TRANSFER_JOURNAL_PENDING`,
   `BAY_SOURCE_PROTECTED` and the owner refusal. Check that the bay menu and logs report each one, and that each is
   bounded in log volume.
4. **The first full GameTest's two failures.** One was a cross-level launch refused by player rejoin protection, the
   other an escrow F1 callback before the journal acknowledged its record. The record calls both the W166 timing
   family. Say whether either could come from these changes.
5. **The test seams.** The R24 tests swap the world escrow ledger. Confirm the swap is gated to the GameTest server,
   and say what a crash mid-test leaves.
6. **Nothing weakened:** formats, bounds, budgets, quotas, timeouts and the protocol. Earlier tests change only as the
   record states. The three new `gametest_expected_log.json` entries are WARN-only and match real holds.
   `RocketTransferService` and `RocketTransferSavedData` are unchanged. Say whether `RocketEntity` (713 lines) now
   mixes responsibilities.
7. **Audit `R-043-draft-2.md`** against the code after both fix rounds, and name each sentence that is now wrong or
   missing.
8. **Merge conflicts:** list them against the shared head (`origin/codex/v1.8.0-classic-content`) and W164
   (`codex/v1.8.0-w164`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `BayRecoveryOwnerTest`, `BaySourceCensusTest`, `RocketJournalHoldTest`, the other new and changed unit classes,
  and the pass 2 probes;
- the `resourceescrow`, `rocket.bay`, `rocket.server` and `satellite` packages.

Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full
build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W170-R41, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
