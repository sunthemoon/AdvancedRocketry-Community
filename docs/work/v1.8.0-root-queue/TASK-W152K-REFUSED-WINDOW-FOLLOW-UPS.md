# TASK W152K: the refused-caller window follow-ups (W152J-R3, R5 and R6)

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W152J](TASK-W152J-FAILBACK-RESERVATION-TURN.md)
with its R114 and R117 sections, and the W152J section of [records/W152.md](records/W152.md) first. Then read the
re-review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J-rereview/REVIEW.md` (verdict APPROVE-WITH-CHANGES). Its
evidence-only probe holds the failing sequences: `probe-src/.../rocket/server/SealedRereviewProbeTest.java` in the same
folder. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152k` |
| Branch | `codex/v1.8.0-w152k` from the shared head that records this task (local commits only; Root merges) |
| Record | append a "W152K" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152K/` |

## Root decision R119 (2026-10-27)

W152J is merged at `0e2cd5645`: W152J-R1, W152J-R2 and W152H-R1 are closed for the reviewed sequences. Player
admission (N3-B3) stays off until this task is merged after a fresh review.

- **W152J-R3 is fixed here.** An ineligible fail-back hold must not spend a refused caller's hand-off window.
- **W152J-R4 is accepted, not fixed.**
  - The hand-off covers callers that retry within two service ticks of a sealed completion. The automated pad-blocked
    producer retries every tick, so it is covered.
  - A player's one-shot countdown cancel that is refused gets `TRANSFER_JOURNAL_BLOCKED`, the countdown continues,
    and the player can cancel again. While sealed work runs back to back in the same Level, such a cancel has no
    bound.
  - Root records this residual in ADR-065 and R-035. A bounded claim for slower callers would delay sealed hand-offs
    beyond R112's bound, so it is not added. It is revisited if native or play evidence shows sustained back-to-back
    sealed work in one Level.
- **W152J-R5 and W152J-R6 are fixed here.**

## Work

1. **W152J-R3.** Write a short design paragraph in the record first.
   - The anonymous window is used up only when an admitted caller becomes an eligible claim: its first capture, a
     native-window `BUSY` that keeps its place in the turn queue, or `removeUnproved`.
   - Token admission alone, or an ineligible creation-tick hold (crew waiting, an unloaded slot, a non-native `BUSY`),
     does not use it up.
   - The window's own expiry, at most two service ticks after a sealed completion, still bounds what ineligible holds
     can add, so W152H-R1 stays closed:
     - an ineligible hold does not extend the window;
     - it does not keep the ordinary turn open after the window expires;
     - the same logical cannot renew its token through the window.
2. **W152J-R5.** Store the window's expiry as an absolute server tick, not a count of ordinary service ticks:
   - the last refusal plus the existing `ATTEMPT_TICKS + 2`;
   - at sealed completion, the earlier of that and the completion tick plus 2.

   The expiry then holds when ordinary service stops, such as an unreadable transfer journal or an inactive flight
   lifecycle. Use the server tick that the sealed and ordinary services already receive. Add no new clock.
3. **W152J-R6.** Remove the two trailing blank lines from `SealedFailBackAdmissionHandoffTest.java`. Run
   `git diff --check` on the committed range `0e2cd5645..HEAD` and record that result, not the worktree's.
4. **W152J-R4 under R119.** Make no code change. State in the record which callers the hand-off covers.
5. **Regressions in the repository.** Use the production composition as the review's probe does, in both callback
   orders:
   - RR1 with 2 and with 62 sealed owners;
   - RR1b;
   - RR1c in production order;
   - RR2 with 1-tick and 3-tick retry periods, before and after service;
   - RR4 as a unit test of the window.

   The RR1 cases, RR1b, RR1c and RR4 must fail on `0e2cd5645` and pass after the fix. Keep green:
   - RR1d, RR3 and RR5;
   - the W152H-R1 arrival and nudge cases;
   - the three W152J classes and `SealedOrdinaryHandoffTest`.

   Do not add RR2's 20-tick period as a test: R119 accepts it.
6. **No change** to:
   - quotas, the four attempts, the 100-tick proof deadline, the 200-tick retry, the 20-tick poll;
   - the 64-pending bound, the 129-slot ceiling, the 256-inspection allowance;
   - saved formats or the protocol.

## Base

Before the final test run, check `git log codex/v1.8.0-classic-content` for a commit `merge(v1.8.0): W129aM`. If it is
there, merge the shared head into your branch (a merge, not a rebase) and run the verification on the merged tree:
W129aM changes the sealed coordinator's journal port and can shift sealed timing.

## Verification

- The new tests alone, failing first on `0e2cd5645`.
- These unit classes, unchanged, then the `resourceescrow`, `rocket.server` and `rocket.sealed` packages and the whole
  unit suite:
  - the W152, W152F, W152G, W152H and W152J classes;
  - the R112 classes the W152J record lists.
- One complete GameTest that passes with the unchanged log checker.
- The eight W152 native cases with their two restarts each, as in R117, because scheduling code changes.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule. W164, W156 and the
reviews share the lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory
is free. Keep the evidence under 100,000,000 bytes. A fresh Codex session reviews afterwards.

## Not in this task

- Enabling player admission (N3-B3) or I6, and W164's work.
- A bounded claim for refused callers (R119).
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
