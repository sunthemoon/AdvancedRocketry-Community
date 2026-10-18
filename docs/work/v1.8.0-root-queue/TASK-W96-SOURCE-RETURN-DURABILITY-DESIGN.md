# TASK W96: design note for P1-N3, a returned source that a crash leaves in flight with no record (Root decision R33)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), `records/W91K.md` (the two N2 findings),
`records/W86-DESIGN.md` sections 5 and 8, and `records/W91.md` (its R23 decision) first. You are a new session. This is
a design task: write the note, change no production code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w96` (new; from the shared branch at `93eb3ad2`, which has W91, W91N, W91K and W90 merged) |
| Branch | `codex/v1.8.0-w96` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W96-DESIGN.md` (new), and a short `records/W96.md` with the commands you ran |
| Evidence to use | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91K/` (`native-02/` and `native-03/`, the N2 after-move and N2 UNKNOWN cases) |

## Root decision R33 (recorded here)

W91K's native cuts found that after an ordinary fail-back's journal removal is durable (after its atomic move, or with
its write UNKNOWN) and before the source rocket's chunk is saved, a hard kill leaves rocket B in its saved in-flight
image (ASCENT, mid-air, full fuel and structure) with no transfer record, on both restarts; nothing recovers it.
`RocketTransferContinuations.failBack` changes the source in RAM (stationary, at its origin, passengers remounted) and
then removes the record; no entity save precedes the removal. W91 only added the reservation before this order, so
the defect predates W91. Root decides:

1. It is **P1-N3**, a part 1 open item that affects ordinary flights in every world, not only governed ones. It is
   fixed in v1.8 before admission, after this design note and an independent review (AGENTS section 13: a persistence
   and recovery slice).
2. **The N2 refused-reservation cut** may use a gated, test-only continuation-slot limit that can only be lowered
   (release-test gate and its own property, on the existing empty test-owned dedicated server), so that the native
   refusal happens without 128 queued continuations; named-pad variants of the removal cuts are allowed. Both belong
   to the implementation leaf, not to this note.

## What the note must contain

1. **The class, not only the instance.** Inventory every path that removes or advances a transfer record while the
   entity image that recovery would then rely on is not yet durable: the ordinary fail-back, recovery's KEEP_SOURCE and
   other removals, the landed releases (W92's Design M paths), COMMITTED retention and replacement, and the governed
   return (W91K's governed exact-source cut passed: say which mechanism protects it). For each: the window, the saved
   images on each side, and what a restart finds today.
2. **Options, compared:** (a) make the source's entity image durable before the removal (for example a guarded
   own-store save and readback, as the escrow proofs do, then the removal); (b) keep a record until the source image is
   durable, ideally with an existing phase whose recovery already returns the source (say whether that needs a saved
   format change); (c) a load-time rule that returns an in-flight rocket with no record, no escrow authority and an
   acknowledged, operational journal to its own snapshot's origin; and any combination. For each: crash points and
   restart outcomes, the player-visible effect, saved format, acknowledgement order, cost per return, interaction with
   W91's per-record scheduling and N2's reserve-before-mutation, W92's landed disassembly, W90's tickets, passengers
   (online, offline, reconnecting), multiplayer, and the governed paths (which must stay as they are).
3. **A recommendation**, with the exact order of operations and the guards.
4. **Tests:** unit and GameTest cases, and the native cuts: W91K's two FINDING cuts must then pass with B FUELED at its
   origin and no duplicate, plus a cut for each other path in the inventory that has the same window.
5. **Risk and ADR text** for Root (R-035 and the ADR-065 W11b2 part 1 note), and the open questions with your answers.

Keep it to about 400 lines. Read code at `93eb3ad2` and cite file and line.

## Not in this task

Any code change, the implementation, the refusal fixture, ADRs, the risk register, status files and the ledger
(Root). Run no GameTest or server; reading the W91K evidence and running unit tests is enough.
