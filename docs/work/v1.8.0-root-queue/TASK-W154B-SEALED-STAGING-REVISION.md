# TASK W154B: sealed staging, revision 2 of the W154 design (Root decision R93)

Root: Claude. Date: 2026-10-24. Read [TASK-W154](TASK-W154-CONTAINER-ISOLATION-DESIGN.md), your W154 design
(`records/W154-DESIGN.md` on branch `codex/v1.8.0-w154`, commit `967e56aa`) and Root decision R93 below. This continues
the W154 session in the same worktree and branch.

## Root decision R93 (2026-10-24)

W154 showed that deferred contents alone cannot isolate the retained-U window and proposed sealed staging. Root adopts
sealed staging (section 4, steps C0 to C5) with these decisions:

1. **The carrier holds the adapter payload, not a native image.** C stores the member's original block state, its
   adapter ID and the adapter's captured payload, the same bytes U holds for that member, within U's per-member bounds
   plus framing. No native-image encoder and no provider API change: every adapter admitted today keeps disassembly.
2. **Unseal after U's acknowledged absence; no publication fence.** Replace C6 and C7. Once D and U are durably gone
   (C4, then C5 with W123's checked acknowledgement), C is the only owner and C to N is a change inside one chunk.
   Unsealing places the original state and calls the existing adapter restore on the live block entity, as today's
   restore does. A crash before N's chunk saves leaves C on disk, which unseals again. A later transfer out of N to
   another chunk is an ordinary container transfer with the torn-save behaviour of every vanilla chest (ADR-054,
   the "ordinary container/player torn save"), not part of the disassembly's two-store transfer, so D3-A's rule
   against an added residual is kept. No outgoing-image projection, save fence or new chunk serialization path.
3. **C is inert by construction.** No `Container`, no capability on any face, no menu, ticker, comparator signal,
   item form, pick-block payload or drops; unbreakable in survival, player break events cancelled (creative too),
   explosion-immune, `PushReaction.BLOCK`, in the wither- and dragon-immune tags, not holdable by endermen; its
   client update carries no payload. C never activates itself: a C loaded before the journal is ready, or with an
   unreadable journal, stays sealed. Server commands and world editors are operator actions, recorded in R-035.
4. **Lazy unseal.** A C found later (a chunk unloaded at retirement, or after a crash) asks the coordinator on the
   server thread; the coordinator unseals only after it confirms U's durable absence for that transaction, inside the
   shared 256-inspection cursor and per-tick budgets. No persistent pending list unless you show one is needed.
5. **Saved format: the smallest change, stated exactly.** Authorized: the carrier block and block entity (one stable
   ID, a schema-1 root bounded as in decision 1, ADR-061/064 unknown-data preservation; never discarded or replaced
   with air). For U, prefer no schema change. If old-path DISASSEMBLY records (native terrain, current recovery) can be
   retained across a jar upgrade, state the smallest record-level distinction (for example a new transaction type
   value) that keeps their current recovery, and its old-reader behaviour. Downgrade follows ADR-065 section 13 (a
   whole-world backup); no downgrade guard is required.
6. **All members are staged**, containers and plain blocks alike, as section 4 says. The visual is a uniform sealed
   texture (original art with provenance) and an EN and zh name; rendering the original state is not in this slice.

## What to write

Revise `records/W154-DESIGN.md` to an implementation-ready revision 2 under R93: the write and acknowledgement order
C0 to C5 plus unsealing, what a crash at each step restores (each item exactly once, foreign items never erased), the
access table with C's closures, the old-record distinction, bounds and per-tick costs, the player and operator impact
for R-035 (what players see, duration, holds and recovery), and the tests (unit, GameTest with a real hopper, a
dropper and an extracting and inserting capability consumer, native cuts with two restarts each, including a kill during
the first recovery). If a decision above is unsound, say why and propose the smallest fix instead of building around it.

## Verification

Read-only inspection and probes in `D:/GitHub/ARCE-Task-Evidence/root-queue/W154/`; no production change; the repository
validator. Commit on your branch. A fresh Codex session reviews it next.

## Not in this task

Implementation (W152 resumes it), a Mixin, narrowing the supported set, ADR and risk-register edits, status files and the
ledger (Root).
