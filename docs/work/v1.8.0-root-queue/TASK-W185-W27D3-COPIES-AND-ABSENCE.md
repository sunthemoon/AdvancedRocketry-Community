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


## Review findings under Root decision R149 (2026-10-29)

The review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W185-review/REVIEW.md` (SHA-256 `eeea955c…f35c`), returned
APPROVE-WITH-CHANGES: no path removes a copy that still holds unconsumed packages, or the last image. A fresh session
continues on `codex/v1.8.0-w185` from `31bf36d10`, one commit per finding, and appends a "Review fixes (R149)" section
to the record:

1. **W185-R1 (fix before merge).** Recognize the exact source by UUID, Level, exact x and z and the
   `[origin.y, origin.y + 48]` range, without the build-height clamp, and extend each end's query box and `within()` to
   at least `origin.y + 48 + 4`. Add unit cases at `maxBuildHeight - 48` and in a 256-high Level; they fail first.
2. **W185-R2, Root's choice (a).** From CONSUME on, an in-session claim renews region tickets on its source end's
   checked set (at most 16 columns, the existing limit), as a restart claim already does, so D6 cannot wait for ever
   at END_LOADING. Add a case whose source end is wider than its origin column; it must finish. Root updates R-042's
   ticket sentence.
3. **W185-R4, Root's scope.** The coordinator moves only an online assigned passenger who is riding a recognized copy
   that will be removed, as the record's design says. It does not pull back a passenger who dismounted, died or rides
   elsewhere. A mount change on the survivor alone does not invalidate the group. Record the 200-tick login-queue
   expiry and its WARN.
4. **W185-R5.** Release D6's slot proofs while D7 waits for a quiet receipt store, and prove again once it is quiet, so
   a parked claim holds no save-monitor slots. Show it with a test.
5. **W185-R9.** D7 checks that the store is quiet before it serializes the source, so its wait saves nothing.
6. **W185-R11.** Remove the dead `covered()` and the unused parameter, rename or remove the stale test, and route D6's
   copy check through `BayCopyCensus`, so one census uses one rule set.
7. **W185-R12.** Move the held-login branch out of `RocketTransferRecoveryService` into the bay dispatch, or say in the
   record why it stays.

Not in this round: R6 (one capture at a time) and R8 (the cargo-budget fairness), which Root registers in R-043; R3,
the W164 conflict, which W164 resolves after this slice merges; R7 and R10, which are Root's record and register
updates. No format, bound, budget or timeout changes.

Verification: the new tests alone, failing first; the whole unit suite; the bay batches three times; the native
tranche again (B3 to B7 with copies and offline seats, two restarts each). Root's hosted run on the pushed head stands
in for a complete GameTest. A fresh review of these fixes follows.

## Hosted failures under Root decision R150 (2026-10-29)

Hosted run 40738843353 on `31bf36d10` failed 10 GameTests (job log saved at
`D:/GitHub/ARCE-Task-Evidence/root-queue/W185-hosted/31bf36d10/`):

- `acknowledgedSettlementRestoresMountRefuelAndLaunch`, `ordinaryL2WithoutReceiptCompletesTwoPassengerFlights` and
  `normalizedSettlementSurvivorCompletesTwoPassengerFlights`: "Launch did not await its exact journal acknowledgement:
  LAUNCH_AREA_INACTIVE";
- `theCapAndThePerPlayerLimitRefuseColdDestinationsAndEveryWarmupEnds`: a warm-up answered `launch_area_inactive`;
- `aRecordedSeventeenColumnDestinationHoldsWithoutMarginTickets`: "The fixture geometry is not the intended invalid
  destination";
- `governedLaunchAreaRefusalsPrecedePadSelectionAndLegacySelectionIsUnchanged`: LAUNCH_CHUNK_BUDGET instead of the
  pad's `chunk_budget` refusal;
- three of this slice's copy cases, each "Fixture exceeded its bounded wait at stage 2 PROVE OWNED_WAIT
  BAY_SOURCE_PROOF";
- `SealedLazyGameTests`' 400-tick timeout, which W186 addresses.

The first six follow W185-R7: `b689f3c50` widened `RocketDiscoveryColumns`, the set every flight shares, so ordinary
flights' launch areas, warm-ups, pad choice and tickets changed in production. **Root decision R150:** the shared set
stays at `min - 1`. A fresh session, after the R149 round:

1. **Reverts the shared widening** (`b689f3c50`, and `907cf5695` if it only follows it) in its own commits, and covers
   W170-R65's low strip in the bay's own census instead: the bay recognition and absence census include the column
   that holds the strip, waiting for it to load and ticketing it under R2's renewal, without changing the shared set.
   Keep the low-edge regression as a bay-census test. W185-R3 (the W164 conflict) and R-035's change then fall away.
2. **Diagnoses the three PROVE timeouts** from the job log: whether R149's R2 tickets fix them, or what else holds D6.
3. Reruns the full GameTest once locally under the heavy-JVM lock, since this round touches shared flight geometry,
   and the bay batches three times and the native tranche. Root's hosted run on the pushed head then decides.

## The D6 capture livelock under Root decision R154 (2026-10-29)

R150 delivered `3e9eacc93`. The shared set is back to W90's `min - 1`, and the bay-only `BayCensusColumns` covers the
low strip. The full local GameTest reproduced the three PROVE timeouts. Restart claims whose census slots share chunk
columns supersede each other's slot proofs in rotation (`SUPERSEDING_SAVE`, retry 0), so none finishes. The same could
happen in production between rockets on neighbouring pads. W185 captures every census slot, where W170 captured one.

**Root decision R154:** bay claims take D6 to D7 one at a time.

1. **One turn at a time.** At most one bay claim at a time runs D6's census captures and holds their proofs through
   D7. Other claims that reach D6 wait in claim order, as a runtime hold with a reason the log shows. The turn passes
   on when D7 ends, when the claim holds for repair or ends, or when a D7 parked on a busy store releases its proofs
   (R5). A parked claim queues again behind the claims already waiting.
2. **Unchanged:** W98-R11's barrier retry rule, every bound, budget, quota and timeout, and the saved formats. This is
   a scheduling rule in the bay coordinator only.
3. **Tests.** An A0 test with two and with three claims whose census slots overlap: both or all finish, in claim
   order, and fail first on `3e9eacc93`. The three PROVE cases then pass in the complete local GameTest.
4. **Record** the expected wait per queued claim, and that ordinary single captures (F1, W98 retirements) can still
   supersede a bay proof, which then retries within its turn.

Verification: the new tests alone, failing first; the whole unit suite; the bay batches three times; one complete
local GameTest under the heavy-JVM lock; the native tranche. Root's hosted run on the pushed head then decides, and a
fresh review of the R149, R150 and R154 changes follows.
