# TASK W152L: several crew-waiting arrivals against an announced caller (W152K-R1 and R2)

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152l` (new; from the shared head named in your prompt, which contains W152K) |
| Branch | `codex/v1.8.0-w152l` (local commits only; Root merges) |
| Record | append a "W152L" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152L/` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152K-review/REVIEW.md` (SHA-256 `5aa78b21…7674`), findings W152K-R1 and R2 |
| Probe | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152K-review/probe-src/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/W152KReviewProbeTest.java`, prepared by the reviewer and never run |

## Root decision R123 (2026-10-27)

The W152K review returned BLOCK.
- **The missing package rerun.** It waited three hours for the heavy-JVM lock and never ran. Root ran it on the
  merged tree before merging W152K.
- **W152K-R1 (Medium), found by code inspection.** In the tick a sealed attempt completes, four new crew-waiting
  arrivals ahead of an announced eligible caller A can take all four attempt slots. Each keeps its creation-tick token,
  as N2 requires, so A is refused. Four more can do the same at T+1. The limited turn ends at T+2, and the next sealed
  attempt can start before A captures.
- **This is older than W152K:** at the base one such arrival was enough. W152K merges, and this task settles R1.
  Player admission (N3-B3) stays off until it does.

## What to do

1. **Measure first.** Copy the reviewer's probe into `src/test` as a regression class with a descriptive name, and run
   it. Add these cases, each in both callback orders and with the production server-tick clock:
   - one, three, four and eight distinct new crew-waiting arrivals ahead of A in the completion tick and the tick after;
   - **the same** four crew-waiting logicals retrying every tick over at least five sealed cycles, while A retries
     every tick.

   Record A's capture tick, or that it never captured, in every case. The question to answer is whether the same
   logicals can keep A from capturing without bound, or whether each logical can take a slot from A only a bounded
   number of times.
2. **Then one of two outcomes:**
   - **Bounded.** No production change. The new class pins the bound with exact assertions. Write the residual text
     for Root: the bound, its scope, what it delays and what it never loses.
   - **Unbounded.** Make the smallest fix that lets an announced eligible caller capture within a stated bound. For
     example, A could keep one of the four attempt slots while its limited turn lasts. The new regression must fail on
     the shared head first.
3. **The fix may not change:**
   - N2's token protection (an ineligible fail-back keeps its creation-tick token);
   - the four-attempt limit, the window's expiry, quotas, the 100-tick proof deadline, the 200-tick retry, the 20-tick
     poll, the 64-pending bound or the inspection allowance;
   - saved formats or the protocol.

   W152H-R1 and R112's sealed-direction bound must still hold. Run their classes and RR3.
4. **W152K-R2.** Correct the W152K section of `records/W152.md`.
   - Once an ineligible hold limits the turn, a nudge of an already-pending fail-back is refused while sealed demand
     remains, before the window's expiry.
   - Ordinary service can still advance that pending return; that is different from accepting the nudge.
   - "Until the next sealed completion" holds only while sealed demand waits, and an eligible native-window BUSY clears
     the limit.
   - Leave the accepted one-shot countdown-cancel residual as it is.

## Verification

- The new class alone.
- `SealedRefusedWindowTest`, `SealedRefusedWindowHandoffTest`, and the W152 to W152J and R112 classes.
- The `resourceescrow`, `rocket.server` and `rocket.sealed` packages, then the whole unit suite. Unit runs need no lock.
- **Only if production code changes:**
  - one full GameTest with the unchanged log checker, under the heavy-JVM lock;
  - W152K's eight native cases (the W152K section of `records/W152.md` names the driver), under the lock, on
    committed source.

  Root then arranges a fresh read-only review of the production diff only.

Check 6 GiB free memory before each Gradle run, and C: and D: above 10 GiB before a GameTest or native start. Delete
every server copy, export and `build/gametest/world` in the `finally` of the script that took the lock.

## Not in this task

N3-B3 admission, W164, quotas or limits, ADRs, the risk register, status files and the ledger (Root).
