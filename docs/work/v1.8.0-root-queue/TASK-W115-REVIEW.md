# TASK W115-review: independent read-only review of W115 (structural fluid decoding) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. A Claude
session wrote W115, so this is a fresh Codex session with no context from the author (AGENTS.md section 13).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w115` at `81d04848` (base `91b9d665`; tested source `583b60f1`). Your review worktree `D:/GitHub/arce-v180-w115-review` is a detached checkout of `81d04848`; read the diff with `git diff 91b9d665..81d04848` there |
| Task it answers | [TASK-W115](TASK-W115-FLUID-STRUCTURAL-DECODING.md), which applies Root decision R39 (in [TASK-W110F](TASK-W110F-REVIEW-FOLLOW-UPS.md)) to fluids |
| Risk and ADR | R-035 in `docs/11-RISK-REGISTER.md` (the R39 and fluid whole-world sentences) and ADR-065's "W11c and W11d implementation note" |
| Record | `docs/work/v1.8.0-root-queue/records/W115.md` on the branch (its plan section lists every consumer) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W115/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W115-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Durable decoding is structural everywhere.** Startup, `SavedData` load, the migration pre-start, settlement
   (freeze, receipts, O1 to O4), C3 to C8 readback and proofs, the barrier and the inspector must not look a Fluid up or
   build a `FluidStack` for a durable fill or fluid cargo record. Search every caller of `EscrowCargoPayload.decode`,
   `new EscrowCargoPayload`, `EscrowFluidCodec.validateNative`, `EscrowOperation.nativeFluid` and
   `FluidStack.loadFluidStackFromNBT` in the escrow, rocket and cargo packages, and say for each whether it can run on a
   durable path. Do this on the subject and on the shared branch head `034ed99b`, which W115 will be merged into: W112
   (merged `16b368f6`) added `EscrowCargoCapture.bind`, which calls the native `EscrowCargoPayload.decode` for its
   cursor. Read it with `git show 034ed99b:<path>`; say whether any native caller there reaches a durable path, and what
   the merge must change.
2. **The native check at every acting consumer.** Admission (`EscrowTransferAdmission.invalid`,
   `EscrowCoordinator.invalid`, `prepareTest`, `EscrowLoaderAdmission.capture`), the C2 permit and both projections
   (`EscrowReconciliationAction.issue`, `EscrowReconciliationChange.rocket` and `source`), and the other callers of
   `EscrowReconciliationChange.rocket` the record lists. Is any consumer that acts on a Fluid missing the check? Does
   every caller handle the `IllegalArgumentException` as the record says, with no path where it escapes into a server
   tick, a save or the writer? Could `validateNative` throw anything other than `IllegalArgumentException` (for example
   from `FluidStack.loadFluidStackFromNBT` with a structurally valid but odd name)?
3. **The hold (AGENTS.md section 3.3).** Confirm that the refusal holds exactly one operation (phase `REPAIR`, reason
   `OTHER_STATE`, persisted by the existing scheduler), that its endpoints and chained operations wait as the record
   says, that other components, the ledger, its writer, startup and the world continue, and that the record keeps its
   exact bytes. Check the claim that `ARCE_ESCROW_FLUID_UNAVAILABLE` is logged at most once per operation per session:
   is there any path (an unavailable writer, a failed persist, a retry, a reload) where `issue` runs again for the same
   operation and repeats the WARN?
4. **Format and compatibility.** No hash, saved field, schema version, dictionary value, bound, proof, lease or replay
   exclusion changed. A ledger written by the base jar decodes and re-encodes to identical bytes; a structurally invalid
   payload is still refused, never normalized; `minecraft:empty` and the size, depth and node bounds behave as before on
   both sides of each limit.
5. **Cargo objects stay native.** `EscrowCargoPayload`'s constructor and `decode` keep the native check, so eligibility,
   `RocketCargoSelection` and `RocketCargoMutation` act only on checked objects. Is the record's note for C-E (where
   cargo's C2, O2 and readback must call the check) complete and correct?
6. **Tests and limits.** No existing test, assertion, limit or timeout was removed or relaxed (AGENTS.md section 5);
   the record says no existing test file changed: confirm it. Does `EscrowFluidDurableValidationTest`, copied onto
   `4d1cfa95`, fail there and pass on `583b60f1`, and does it drive the real startup and scheduler paths it names, or
   stand-ins?
7. **The record** against the code: does it claim anything the diff does not do?

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`:
`EscrowFluidDurableValidationTest`, and the existing tests of every touched class (find them by name: the operation,
codec, cargo payload, reconciliation, loader admission, coordinator and transfer admission tests, and C-K's fluid cargo
tests). You may check out `4d1cfa95` in your worktree with the new test copied in to confirm it fails there (return to
`81d04848` afterwards), and you may add a probe test
to check a finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is
free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock.
Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W115-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
