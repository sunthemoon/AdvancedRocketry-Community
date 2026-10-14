# TASK W44S: close the round-2 review findings in the world-first design (design only)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44-world-first-design` (at `02aef80c` plus this task) |
| Branch | `codex/v1.8.0-w44-world-first-design` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W44R-review/REVIEW.md`, SHA-256 `3503021258e82fccbb5b2a4c2b68d27f94aaf645277ea0bb513d9f8ab89ed5c5`. Verdict APPROVE-WITH-CHANGES: N1 Medium, N2 to N6 Low |
| Output | Revise `docs/work/v1.8.0-root-queue/records/W44-DESIGN.md` in place. Add a "Round 3 changes" section under "Round 2 changes", mapping N1 to N6 to the changed lines. No production or test code |

Read the whole round-2 review, the note, and `records/W44R.md`. The reviewer said a targeted
re-check of N1 is enough, so keep the changes narrow and precise.

## Required changes

1. **N1, Medium: unreadable is not absent.** Distinguish "absent" from "present but unreadable" for
   every root the design reads: the progression root, and any journal or station source the design
   treats as absent.
   - An unreadable root is never overwritten by a baseline or receipt write. It holds the affected
     kinds and keeps the raw file.
   - Choose the mechanism and state it. Either:
     - an absence check before every first write; or
     - carry the pre-start validator's result into the session.
   - Add the case to the cut inventory: an unreadable progression root at startup, with one event
     pending. Neither event's receipt may be erased, and a plain restart after the file is repaired
     must recover.
2. **N2.** Bound the retry diagnostics: one full stack trace per transition, then a counter. State
   the retry interval's cost on a large registry. Add both to the proposed risk text.
3. **N3.** Add the native case of a rocket record that is pruned or superseded before its receipt
   exists, as ADR-066's test table asks.
4. **N4.** Name the test injection point each cut needs, and reword the three ambiguous rows the
   review lists.
5. **N5.**
   - Add the journal "until durable reload" reinterpretation to section 9.
   - In section 10, plan the change to the existing station unit test whose expectation the design
     reverses. Name the test and say why its new expectation follows the design; it is not a
     weakened assertion.
6. **N6.** Name what the implementation moves out of `RocketTransferService` (793 lines) so it stays
   under 800, or show that its net change is zero or negative.

## Not in this task

- Any production or test code.
- ADRs, the risk register, status files and the ledger (Root).
