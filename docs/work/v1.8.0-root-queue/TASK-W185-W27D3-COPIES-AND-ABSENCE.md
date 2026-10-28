# TASK W185: W27d3, copies, the absence census and offline login

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w185` (new; from the shared head named in your prompt, which includes W170) |
| Branch | `codex/v1.8.0-w185` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W185.md` (new; the one-page design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W185/` |

## Read first

- [records/W127-PLAN.md](records/W127-PLAN.md): section 2's W27d3 sub-leaf (scope, A0, A1, native, size), section 3,
  and section 4's questions. Root decision R49 in [TASK-W128](TASK-W128-W27D1-COORDINATOR-ADMISSION.md) keeps released
  flight for W27d4 (Q5).
- [records/W27A-DESIGN.md](records/W27A-DESIGN.md): sections 4.1, 5, 6 and 8.1, section 9's B3 to B7, and section 10's
  risk entry D.
- `docs/work/v1.8.0-root-queue/records/W170.md` (W27d2), including its R142 section and Root's R143 corrections at the
  end, and risk entries R-042 and R-043 in [the risk register](../../11-RISK-REGISTER.md).
- The W170 final review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-final-review/REVIEW.md` (SHA-256
  `96e6bebe…d427`), findings W170-R62 and R65.

## What to do

1. **Two carried-over items first, each in its own commit with a test that fails first.**
   - **W170-R62.** Move D6's end-loading check ahead of the source save in `BaySourceProof`, so that a waiting tick
     saves nothing. Apply the same order to D6's DUPLICATE_PENDING, BARRIER_UNSUPPORTED and capture-unavailable waits,
     unless the record shows a wait needs the save.
   - **W170-R65.** The census box's low edge reaches about 0.75 block past the checked columns. Either widen the
     checked set's low edge by one column, which R1 then tickets and R2 and D6 wait for, or make the absence census
     below cover that strip. Say which in the design.
2. **The one-page design, committed before any further code.** Cover:
   - recognition at both recorded ends: the exact UUID first, at most 64 matches, the cap a conflict;
   - election and D5 on every recognized copy with the group's hashes;
   - scoped removal of neutral non-survivors;
   - absence proofs across the bounded end regions through W98's census, including the parts R-043 says are read only
     when loaded (in-session ends that D6 does not wait for);
   - quarantine of an unmatched or out-of-bound copy, with the receipt kept;
   - logins during a queued retirement, routed without settlement, and the offline-passenger path;
   - cold-chunk variants, the bounded recovery-read benchmark, and W27d2's DUPLICATE_PENDING claims continuing at the
     next start.

   List every saved structure the slice writes. The plan expects no new kind of durable state. **Stop and report
   before code** if the slice needs a saved format beyond the existing receipt store and journal fields, a new bound,
   budget, quota or timeout, or any line in `RocketTransferService`.
3. **Implement.** Admission stays off outside the GameTest server and the gated release-test hooks. New classes stay
   under 500 lines; the plan's estimate is 5 to 7 classes.
4. **Tests, from the plan's lists.**
   - A0: the recognition and election matrix, census limits, and token invalidation with copies.
   - A1: copies joining at both ends, offline login, and cold chunks.
   - Native: B5, B6's copy variants, B7, and B3 and B4 with copies and offline passengers, two restarts each.

   No existing assertion is relaxed. A new WARN or ERROR that a GameTest drives gets its own exact manifest entry.

## Verification

- each new class alone, then the whole unit suite;
- the bay batches three times;
- the native cases in tranches of at most 60 counted starts.

All heavy runs go under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off. Unit runs need no
lock. Check C: and D: above 10 GiB and 6 GiB of free memory first. Keep the evidence under 100 MB. Root's hosted run on
the pushed branch stands in for a complete local GameTest.

## Not in this task

Released flight (W27d4), admission (W27e), ADRs, the risk register, status files and the ledger. Root updates R-043
before this slice merges, and an independent read-only review follows.
