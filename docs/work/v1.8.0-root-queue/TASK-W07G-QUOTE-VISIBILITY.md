# TASK W07G: keep the warp quote's cost and countdown visible (W07F re-review N1)

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w07-station-controls` (at the W07 candidate `6d552a5a`: W07 and W07F merged with the shared branch) |
| Branch | `codex/v1.8.0-w07-station-controls` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W07F-review/REVIEW.md`, SHA-256 `19859b139a62abaf42ad7f27c9f5b017a683c9ceda59aea5b21614b65636c39b`. Verdict BLOCK on N1 only. R1–R3, R5 and the channel are fixed. |
| Record | `docs/work/v1.8.0-root-queue/records/W07G.md` |

## Problem

Read N1 in the review. Two legal 128-character body IDs from a data pack wrap the Orbit and
Target lines so far that Balance, Cost and Countdown fall below the seven drawn rows. Confirm
stays enabled, so a player can confirm a warp without seeing its price.

## Root's decision

1. Reserve fixed rows for Balance, Cost and Countdown (and the status area). They are always
   drawn, whatever the length of an ID.
2. Show each body ID on at most one row:
   - abbreviate a long one with an ellipsis;
   - show the full ID in a tooltip on hover.

   Never change the legal ID bounds.
3. Confirm is enabled only while a quote is shown **and** its cost row is actually drawn. Keep
   the token checks from W07F.
4. Test:
   - add a maximum-ID layout regression with both a 128-character orbit and a 128-character
     target;
   - assert that Cost and Countdown stay within the drawn rows;
   - assert that Confirm's enabled state follows the visible cost.

   Use the existing layout test's font-width approach. Record that V1 visual evidence in
   both languages and at the supported GUI scales is still required.

## Commands

- The whole unit suite once. `runGameTestServer` (under the lock) if you change server-side
  menu behaviour; otherwise say why it was not needed.
- The log checker after any GameTest run.

## Not in this task

- Other station or warp behaviour.
- Status files and the ledger.
