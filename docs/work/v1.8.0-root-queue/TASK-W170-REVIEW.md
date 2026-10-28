# TASK W170-review: independent read-only review of W27d2, in two passes

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w170` at `26ed25928`. The slice's own work runs from `36d568db0` (W128R's head when it started) to `26ed25928`, with the shared-head merge `5d1a9d030` |
| Task it answers | [TASK-W170](TASK-W170-W27D2-REGISTERED-HALF.md) and its R133 section |
| Design | [records/W127-PLAN.md](records/W127-PLAN.md), the W27d2 sub-leaf; [records/W27A-DESIGN.md](records/W27A-DESIGN.md) sections 4, 4.1.1, 5, 6, 8 and 8.1, B2 to B18 and section 10's entry D; ADR-074 item 11 |
| Record | `docs/work/v1.8.0-root-queue/records/W170.md` on the branch: the one-page design and the logs |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170/` |
| Risk draft | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/input/R-043-draft.md`, Root's draft of risk entry D |
| Hosted run | the run on `26ed25928` (Root pushed the branch at 09:20); read its result if it has finished |

This is a persistence slice: it registers satellites, consumes packages, rewrites journal records and retires
receipts. Deployment admission must stay off outside the GameTest server and the full release-test gate. Each pass
writes its own `REVIEW.md`.

## Pass 1: receipts and registry

Commits `6c7cff2db..3874646a3`. Output: `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/pass1/REVIEW.md`.
Review worktree: `D:/GitHub/arce-v180-w170-review1`, a detached checkout of `26ed25928`.

1. **D2** registers only under the coordinator permit, replays first, and checks a fresh DATA launch's initial
   mission (F2).
2. **D3** requests one registry checkpoint per claim under ADR-074 item 11. REGISTRY_UNPROVED comes after three
   failures, with 200-tick pacing. Nothing is rejected after a RAM registration.
3. **D4** freezes the group, computes S' and T' within the runtime's budget, publishes every outcome in one receipt
   generation, and lifts the decommission fences only at the exact acknowledgement.
4. **W27d1's interim outcomes are gone** (R49 Q4). The four W27d1 tests that changed must assert the final behaviour,
   with no other assertion changed.
5. **Restart:** INTENT with an equal X promotes.

## Pass 2: entity and journal

Commits `6e8380522..4d9ed95fc`, plus the cuts, tests and harness up to `26ed25928`, and the R133 fixes `5c7cabad8` and
`05574840c`. Output: `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/pass2/REVIEW.md`. Review worktree:
`D:/GitHub/arce-v180-w170-review2`, a detached checkout of `26ed25928`.

1. **D5:** S' on the entity and T' in the journal together. Check the journal rewrite class
   (`RocketTransferConsumption`): it keeps the transfer ID, phase, flight data and reservations, and changes no saved
   format.
2. **D6:** the survivor-slot proof at altitude, through W98's port and the barrier's ordinary quota.
3. **D7:** the guarded removal, written only when the receipt store is otherwise quiet, and the READY_SOURCE(T')
   hand-off.
4. **Restart rows R0 to R3:** the source kept if present, or rebuilt at altitude with T's original UUID; DUPLICATE_PENDING
   and REPAIR for copies. Every runtime hold writes nothing.
5. **The R133 fixes:** the captured matrix case follows the transfer until the destination is published, and the
   recovery fixture is paced with no budget or assertion changed.

## Both passes

- Nothing weakened: no format, bound, budget, quota or timeout changed; new classes are under 500 lines;
  `RocketTransferService` is not enlarged.
- The native cases (32) and the full GameTest used committed source.
- Audit `R-043-draft.md` against the code.
- List merge conflicts with the shared head and W164 (`codex/v1.8.0-w164`).

Rerun in your worktree, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes, and the
`resourceescrow`, `rocket.bay`, `rocket.server` and `satellite` packages. Unit runs need no lock. Check 6 GiB free
memory first. No GameTest, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs`
afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (pass 1: W170-R1, ...; pass 2: W170-R21, ...),
severity, file and line, problem and suggested change; the commands you ran and their results; the SHA-256 of your
REVIEW.md on the last line.
