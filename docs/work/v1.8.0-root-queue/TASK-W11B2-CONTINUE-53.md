# TASK W11b2, continuation 53: concurrent governed launches must not choose the same pad, then the measured runs

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md),
[TASK-W11B2-CONTINUE-45](TASK-W11B2-CONTINUE-45.md), [TASK-W11B2-CONTINUE-51](TASK-W11B2-CONTINUE-51.md) and
[TASK-W11B2-CONTINUE-52](TASK-W11B2-CONTINUE-52.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c53` (new, at the integrated W11b2 head `379525ea`, which contains continuations 51 and 52, plus this task) |
| Branch | `codex/v1.8.0-w11b2-c53` (local commits only; Root pushes and merges) |
| Input | Continuation 52's record `records/W11B2-C52.md` ("Final native result and diagnosis") and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C52/native-03/` |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 5.4 and 5.5; Root decisions R1 to R20 |
| Record | `records/W11B2-C53.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C53/` |

No other worker is running.

## The defect (Root's reading of continuation 52, confirmed in the source)

`RocketTransferService.prepareLaunch` builds the surface and station pad `reservations` only from
`journal.entries()`. A governed launch publishes its PREPARED record (F2) only after its F1 ARMED write is
acknowledged; until then its selected destination is held only by `EscrowFlightCheckpoint` in RAM. So several governed
launches requested before the first F2 all select the same first free pad. When the first F2 is published,
`RocketTransferSavedData.ensureNoAuthorityConflict` refuses the others ("Transfer destination overlaps an active
reservation"), `EscrowFlightCheckpoint` catches the refusal, and their arms are retired by the checked pre-F2
cancellation. In continuation 52's `native-03`, eight real player launches gave eight ARMED, one landing and seven
cancellations. The legacy, ungoverned path does not have this race, because it writes its journal record inside
`prepareLaunch`. `EscrowFlightBenchmarkPadAdmissionTest` reproduces the journal refusal.

## What to do

1. **Count pending governed destinations as reservations.** A governed launch's selected destination region must be
   reserved from the moment `EscrowGovernedPreparation.begin` accepts it until either its F2 record is in the journal
   (from then on the journal entry reserves it) or the launch is discarded or refused (then it is released at once).
   Pad selection in `prepareLaunch` (surface, station and named station pads) must exclude these pending regions as
   well as the journal's. Keep it in RAM next to the checkpoint's existing pending state, bounded by the existing 64
   pending launches; a restart needs nothing, because a pre-F2 arm without T is cancelled (R19, R19A) and never
   publishes F2. The journal's own overlap check stays as the final guard; do not weaken it.
   - Do not change an acknowledgement order, a proof, a lease, a schema, the replay exclusion, the F2 callback queue,
     the capture cadence or any limit or timeout. If the fix needs one of these, stop and report.
   - State in the record what a player sees when every pad is taken by pending launches (the existing
     `LANDING_PAD_UNAVAILABLE` refusal is expected) and that a refused or discarded launch frees its pad in the same
     tick.
2. **Tests.**
   - A unit test: eight launches through `prepareLaunch`'s pad selection while none has reached F2 select eight
     distinct, non-overlapping pads; a discarded one frees its pad for the next request; with all pads pending, the
     next request gets `LANDING_PAD_UNAVAILABLE`.
   - A GameTest: at least three concurrent governed launches through the product entry
     (`RocketRuntime.requestFlightIntent`, as the benchmark now does), so that the two-slot F2 callback queue
     overflows, all reach F2 on distinct pads, and all land as checked idle survivors (F3's countdown fallback covers
     the refused callbacks). Put it in an existing escrow batch if you can; if you add a batch, say so (376 batch
     names now; about 384 reshuffles the run order).
   - Keep `EscrowFlightBenchmarkPadAdmissionTest` (it tests the journal guard, which stays).
3. **Run the whole unit suite and one full GameTest** on your committed fix, with the unchanged log checker.
4. **Then run the measured benchmark** exactly as TASK-W11B2-CONTINUE-51 items 1 to 4 say, on your committed source,
   on the quiet machine, and report every number it asks for against docs/17's unchanged budgets, including the
   restart check. If it is still blocked, report the first failure with evidence, as continuation 52 did; do not fix a
   newly found product defect beyond item 1.

## Known and out of scope

Governed dispatch requires every chunk of each end's discovery margin to be loaded and entity-ticking
(`RocketTransferEntities.recoveryEndsAlreadyLoaded`), but the flight tickets make only each end's origin chunk
entity-ticking. Continuation 52's fixtures keep the margin inside one chunk. Root records this separately; do not
change tickets or readiness here, and do not move the benchmark fixtures across a chunk border.

## Write scope and boundaries

`RocketTransferService` (the pad-selection inputs only), `EscrowFlightCheckpoint` and `EscrowGovernedPreparation` (the
pending-destination view only), the pad selectors only if their inputs need it, new tests, the benchmark probe and
runner if the measured run needs an observation change, and the record. No status file, ledger, ADR, risk register,
build script, log checker or expectation. No relaxed budget, limit, timeout or assertion. Do not serialize the eight
flights or shrink the workload.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C53/` and disposable server
sessions under `D:/GitHub/ARCE-Task-Evidence/Temp/W11b2-C53-<n>`. C: has about 14 GiB free: check that C: and D: have
more than 10 GiB free before each heavy start, and stop and report if C: falls below that. Never remove a lock you do
not own. If the tool policy refuses to let you remove your own lock, world or server copy, write the exact path in
your record and final answer and continue; Root removes it.

## Not in this task

Production admission, W11b3, the ticket and readiness margin, ADRs, the risk register, the R-035 text, status files
and the ledger (Root).
