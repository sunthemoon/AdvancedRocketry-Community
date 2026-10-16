# TASK W84: five GameTests that pass only in the current batch order

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w84-order-dependent-tests` (from W68's branch `codex/v1.8.0-w68-automation-conservation` at `93c17837`, which already merges the shared branch and has 387 batches) |
| Branch | `codex/v1.8.0-w84-order-dependent-tests` (local only; Root merges) |
| Evidence | W68's record `docs/work/v1.8.0-root-queue/records/W68.md` in this worktree, section 6 and "Open, blocks a clean merge" (the runs, the batch neighbours and the failure messages); its logs are under `D:/GitHub/ARCE-Task-Evidence/scratch-w68/` and `D:/GitHub/ARCE-Task-Evidence/root-queue/W68/` |
| Record | `docs/work/v1.8.0-root-queue/records/W84.md` (new) |

## The problem

Vanilla groups GameTests into a default `HashMap` by batch name and runs the batches in its iteration order. Above
384 batch names the table doubles and almost every batch moves. The shared branch has 369; W68's 18 new batches make
387, and then five tests outside W68 fail in every full run:

- `expiredarrivalwaitsendsanemptydepartureprojection` (batch `elevator_capsule_expiry`): "The projection changed ride
  energy".
- `OrbitalLaserDrillGameTests` (batch `endgame_laser_drill`): `afulloutputpausesandretriesthesamedrawwithoutloss`
  ("The drill did not pause"), `adrillonastationminesitsorbitbodyandawarpchangesthetable` ("No first operation") and
  `abrokenstructurestopsthedrilluntilitisrepaired` ("Not formed").
- `aphysicalshaftdigswholelayerspaysonceandeverystopleavesthelayer` (batch `endgame_laser_physical`): "A layer loaded a
  chunk". It fails even run alone on the shared branch.

Each depends on state that an earlier batch leaves behind (W68 found `station_warp_deop` and `station_flight` running
just before them in the shared branch's order). Any branch that passes 384 batches, including W82 and W11b2, will hit
the same failures.

## What to do

1. **For each test, find the state it depends on**, for example a station, a warp, loaded or generated chunks, a
   saved-data singleton, the game time, or a registry entry that an earlier test creates.
2. **Make each test set up, or wait for, what it needs itself**, so it passes alone, in the shared branch's order and
   in the 387-batch order. Do not raise a timeout, weaken an assertion, or split a test to hide its cost (AGENTS.md
   section 5). If the honest fix is in production code (for example a real bug that only shows on a fresh world),
   stop and report it.
3. **Prove order independence:** run each of the three batches alone, and run the full suite in the 387-batch order
   (this branch). Running each alone needs the batch filter W68 used; reuse it.
4. Do not change how vanilla orders batches. Making the order fixed would hide the next dependency rather than fix it.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the heavy-JVM lock on a **committed** SHA, then the log checker. The run must pass all
  tests, W68's 18 included.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the lock is released.
  If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- W68's own tests, production behaviour (unless you stop and report), workflows, status files, the ledger and ADRs.
