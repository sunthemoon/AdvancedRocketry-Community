# TASK W212: the checkpoint-bearing P99 of W90's governed relaunch

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w212` (new; from the shared head named in your prompt, which contains W207) |
| Branch | `codex/v1.8.0-w212` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W212.md` (new; the attribution plan and design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W212/` |
| Inputs | `records/W207.md` (section "Attribution and unresolved budget") and its evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W207/`; W207's review `D:/GitHub/ARCE-Task-Evidence/root-queue/W207-review/REVIEW.md` (SHA-256 `7408cee6…d5abe2a2`), "Where the remaining relaunch cost goes"; `records/W206.md` |
| Harness | W206's and W207's runners on the shared head (`scripts/run_w206_warmup.*`, `scripts/run_w207_batches.ps1`, `scripts/w207_*.py`) |
| Background | [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md); R-035 in `docs/11-RISK-REGISTER.md`; `records/W199.md` (how W199 followed W197) |

**Root decision R184.** After W207, the checkpoint-bearing tick of W90's governed relaunch still has P99 118.937 to
164.162 ms across eight flights (131.482 ms after a restart), over docs/17's 100 ms. W207's JFR has only two to four
samples per relaunch window. They show `EscrowGovernedLaunch.prepare`'s resource-signature hashing, flight and fuel
decoding, `EscrowTransferEnvelope.encode` into `RocketTransferJournalCodec.encodeRecord` (NBT copying and
validation), first-use class loading under `EscrowFlightCheckpoint.begin` and `EscrowFlightSource`, and 23 to 27 ms of
transition observation. Nobody yet knows how much of the P99 each one is.

## What to do

1. **Attribution first, committed before any remedy:** a plan that separates, with enough samples to apportion the
   whole checkpoint tick: one-time class loading on the first relaunch versus later relaunches in the same process;
   the observer's own cost (a run with the transition observation reduced to timing only, both reported); and each
   production step named above, timed directly around the calls. Run it natively under the heavy-JVM lock on a JAR
   built from committed source, and record the breakdown.
2. **Design note, then a bounded remedy** for the production steps that dominate, with unit tests that fail first:
   for example hashing or encoding work repeated for the same immutable input, or work that can move off the
   checkpoint tick without changing its authority, ordering, proof or acknowledgement. Class loading may be moved
   earlier only if the note shows where the cost then lands and that it is not on a player-facing tick. If the
   remaining cost is observer or one-time cost only, say so with the numbers and stop without a production change.
3. **Measure before and after** on the same harness and entry points: the eight flight cases and the restart case,
   checkpoint-bearing and work cohorts (mean, P95, P99, maximum), against docs/17, honestly.

## Verification

The new tests alone, failing first; the whole unit suite; the governed warm-up and escrow launch GameTest batches three
times under the heavy-JVM lock; the native before and after runs. Root's hosted run on the pushed branch stands in for
a complete local GameTest. An independent read-only review follows a production change.

## Not in this task

Caps, lifetimes, quotas, the 100, 200, 400 and 600-tick bounds, budgets, timeouts, F1's authority and acknowledgement
checks, the journal format, ADRs, status files and the ledger.
