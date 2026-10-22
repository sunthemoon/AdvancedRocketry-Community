# TASK W134F: W134's review follow-ups (W134-R1 to R4) under Root decision R62

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W134](TASK-W134-CARGO-D-E2-ITEM-SETTLEMENT.md),
`records/W134.md` and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W134-review/REVIEW.md`
(SHA-256 `61b0aed2…5c2d9a0d`) with its probe in `W134-review/probe/` first. You are a new session. Take this in a free
slot that does not delay progress tasks.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w134f` (new; from the shared branch at the commit named in your prompt, which has W134 merged) |
| Branch | `codex/v1.8.0-w134f` (local commits only; Root merges) |
| Record | append a "W134F" section to `docs/work/v1.8.0-root-queue/records/W134.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W134F/` |

## Root decision R62 (2026-10-22)

W134-review approves with changes, all Low; W134 merges once its complete GameTest is green (W134-R6's cascade is an
existing test's cleanup, assigned to W137). The follow-ups:

1. **W134-R1:** add the review probe's assertions to `EscrowItemSettlementProofTest`, so a committed test shows that the
   settlement proof step's `nativeCargo` never picks up a fuel fill.
2. **W134-R2 and R3:** in your record section, say that `EscrowCargoLaunch` calls `EscrowGovernedPreparation.begin`
   directly with a same-Level plan, so W134's launches do not exercise the launch request path (W133's F3, F1b), and
   that the "partial move and restart" reload runs after the operation retired, so it shows the saved data round-trips
   and not a restart of a pending move (that is D-E1's native evidence).
3. **W134-R4 (optional):** a case that tries a second item device on the same rocket while an operation is held and
   after O4, with the refusal pinned.

No production change; no assertion, bound or timeout weakened.

## Verification

Each changed test class alone, then the whole unit suite; if you add the R4 GameTest, one full GameTest with the
unchanged log checker under the heavy-JVM lock with the atomic pattern and the hand-off rule in WORKER-RULES.

## Not in this task

D-S cuts, W137, enabling admission, status files and the ledger (Root).
