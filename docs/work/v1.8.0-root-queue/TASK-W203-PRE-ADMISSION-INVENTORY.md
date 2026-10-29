# TASK W203: what still stands between the escrow chain and admission

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This is a read-only
planning task: change no tracked file, make no commit and run no Gradle, GameTest or server.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w203` (new; a detached checkout of the shared head named in your prompt) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md`, and its SHA-256 on the last line of your answer |
| Sources | [CURRENT_VERSION](../../status/CURRENT_VERSION.md) ("What is still open"), [COMPLETION-PLAN](../../status/COMPLETION-PLAN.md), [QUEUE](QUEUE.md) (the owner-decision table and the rows from W164 on), [records/W148-DESIGN.md](records/W148-DESIGN.md) section 7, `records/W164.md`, `records/W197.md` to `records/W202.md`, `docs/11-RISK-REGISTER.md` (R-035, R-040, R-042, R-043), ADR-065, and W198's branch `codex/v1.8.0-w198` at `1e86fa5a2` (`records/W198.md`, under review) |

The I6 and terrain work closes with N3-B4 (W198, under review). CURRENT_VERSION says that before admission come
W11b3, the P1-N3 fix, the remaining N2 cuts and W90's measured cold-destination costs; the satellite bay's W27e waits
for owner decisions.

## What to do

1. **Inventory.** For each item that blocks enabling escrow admission (and the bay's W27e), give its state with
   evidence (commit, record or hosted run): done, partly done (what is missing), not started, or waiting for the owner
   (which decision, quoted from the QUEUE table). Include the open risk-register conditions and the open performance
   evidence (docs/17 reference load, restart, soak).
2. **Stale text.** List sentences in CURRENT_VERSION, COMPLETION-PLAN or QUEUE that the merged work has made wrong,
   with the exact text and what is now true, for Root.
3. **Next leaves.** Propose at most four next Codex tasks that can run without the owner, in order, each with a goal,
   a write scope, the tests that prove it, what it does not do and whether it needs a design note and an independent
   review (persistence, protocol and save-refusal slices do). Say which need the heavy-JVM lock.
4. **Owner questions.** The decisions only the owner can make that block admission, each in one sentence with its
   options.

## Not in this task

Any tracked file, code, ADRs, status files and the ledger.
