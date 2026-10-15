# TASK W44L: the remaining world-first native cut rows

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44l-cuts` (at `21d8dfcd`, the world-first slice with shared merged, plus this task) |
| Branch | `codex/v1.8.0-w44l-world-first-cuts` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W44L.md` (new) |
| Contract | `records/W44-DESIGN.md` section 6 (the cut inventory and section 6.1's seam table); `records/W44I.md`, with its W44J and W44K sections |

The world-first slice (W44I to W44K) implements the design and has passed review. Still open:
- **the 31 H rows** listed in `records/W44I.md` (around line 319);
- the refusal checks of the four migration V rows on a jar from before W44.

The real-client rows stay for the final campaign.

## What to do

1. **Build the unwritten seams** from section 6.1, each gated and a no-op in production:
   - the latch seam;
   - the gated fixture actions (start the second rocket or warp, release the landed reservation,
     launch the return flight, delete the winning station, act on the unreadable registry);
   - the station-world fixture.
2. **Run the 31 H rows.** Do the writer-thread Moon rows first. Each row:
   - two subsequent restarts;
   - pinned to a **committed** source SHA and its jar;
   - the award oracle the design names, checking the actual advancement as well as the receipt;
   - evidence under 100 MB in `D:/GitHub/ARCE-Task-Evidence/root-queue/W44L/`, with an inventory.
3. **The four migration V rows: refusal on an older jar.**
   - Build a jar from the shared commit before the world-first slice (`d75ebe78` or the last commit
     without W44's code).
   - Show that it refuses a root 3 journal and the new station epoch as future roots, and keeps the
     raw files.
4. **Production stays unchanged.** If a row fails because of production code, stop and report with
   the evidence. Do not change persistence order or holds; that needs Root.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a committed SHA, then the log checker.
- The native rows under the lock.
- At the end, delete your generated worlds, server copies and older jars with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root
  deletes them.

## Not in this task

- The real-client rows, ADR-066, the risk register, status files and the ledger (Root).
