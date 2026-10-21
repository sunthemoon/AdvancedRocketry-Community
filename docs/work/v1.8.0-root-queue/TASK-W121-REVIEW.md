# TASK W121-review: independent read-only review of W121's design note (F1, the named fill held by its own fence)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. A Claude
session wrote the note, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w121` at `a0bc2985` (base `a92f7bfd`): `docs/work/v1.8.0-root-queue/records/W121-DESIGN.md` (`097e3b9e`) and `records/W121.md`. Your review worktree `D:/GitHub/arce-v180-w121-review` is a detached checkout of `a0bc2985` |
| Task it answers | [TASK-W121](TASK-W121-F1-NAMED-FILL-DEADLOCK-DESIGN.md) |
| The finding | [records/W107C.md](records/W107C.md) (F1, F2, F3) and its native evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W107C/` (`w107c-native-02`, `smoke-native-02`) |
| Contract | [records/W11A-DESIGN.md](records/W11A-DESIGN.md) sections 4, 5 and 7; [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 2, 4 and 5; R-035 in `docs/11-RISK-REGISTER.md` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W121-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The cycle** (note section 1): verify every quoted line at `a92f7bfd`, which states reach it (only an ordinary
   COMMITTED landed record?), that the hold starts at admission (K0) and re-forms at every start because "settled" is
   kept in RAM only, and that governed flights and the bay cannot reach it while cargo will once C-E2 admits it.
2. **The starvation claim** (found by reading only): does a held fill take the one capture per tick, every tick, so that
   newer fills in other components never get their first capture? Write a probe test if you can show it in a unit test.
3. **F1b**: after a named fill retires, does its floor make the landed record need a proof with both ends loaded at every
   start, so that a rocket refuelled away from its launch site cannot launch until that site is loaded? Is this new with
   the fix, or already true for any named fill that W11b2's operators settled?
4. **The recommendation (b) and its three companions**: the no-revoke rule (a settled record is not fenced by its own
   fill, and this permits no recovery action); the hand-back at C8 (the landed record's proof refreshed from escrow's
   own proofs when the last guarded fill finishes; check the proof key in particular); and the visible, unpersisted
   reason `TRANSFER_JOURNAL_UNPROVED` retried every 20 ticks. Against the three invariants: no resource can be spent
   twice; no fence is weakened for any action other than the guarded settlement; every hold resolves automatically or
   shows a reason an operator can see. Walk K0 to K10 and both restarts. Is any option the note rejects safer?
5. **F2**: can `FluidFuelLoaderIndex` detect a relocated loader within at most 64 lookups once per operation, without
   loading a chunk, and report `RELOCATED_ENDPOINT` as W11A section 4 names it?
6. **No saved-format change**, as the note claims; and which existing tests and native cuts the fix would touch.
7. **The decisions it asks of Root** (D1 to D4): give your recommendation on each, with reasons.

You may run existing unit tests and add a probe test in your review worktree to check a claim; keep each probe in the
output folder. Before each Gradle run, check that at least 6 GiB of memory is free and wait if not; run one Gradle
process at a time, no GameTest, `runData` or server, and take no heavy-JVM lock. Delete the worktree's `build` and
`.gradle` folders afterwards.

## Output format

- A verdict on the note: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W121-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change; then your recommendation on D1 to D4.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
