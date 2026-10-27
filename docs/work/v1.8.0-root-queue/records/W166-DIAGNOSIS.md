# W166 diagnosis: five intermittent hosted GameTest failures (5796ebc01 and bb0eda86f)

Read-only diagnosis, 2026-10-27. No repository file was edited, staged or committed. No Gradle, Java or
Minecraft process was run. Source is cited at the commit that failed (`5796ebc01` for failures 1 to 4,
`bb0eda86f` for failure 5) unless stated. Root ran it through a separate read-only Claude session and checked
the leaked-rocket trace and the cited `orElseThrow` lines before recording it. Log line numbers refer to these files, kept
under `D:/GitHub/ARCE-Task-Evidence/root-queue/W166/input/`:

| Short name | File (under `input/`) |
|---|---|
| `L5796` | `5796/latest.log` (run 40441960965) |
| `D5796` | `5796/debug.log` |
| `Lbb0e` | `bb0e/latest.log` (run 40442135512) |
| `Dbb0e` | `bb0e/debug.log` |
| `G9ae1` | `job-122016496024.log` (green run at 9ae106795) |
| `C5796`, `Cbb0e`, `Cc54d` | console logs `job-121984153317.log` (5796ebc01), `job-121999179589.log` (bb0eda86f), `job-121186992556.log` (c54de4b0, pre-W152J green) |

Local complete-run logs used for comparison are under `D:/GitHub/ARCE-Task-Evidence/root-queue/*/`.

## Summary

| # | Test | Cause | Confidence | Caused by W152J / W129aM? | Fix location |
|---|---|---|---|---|---|
| 1 | `SealedAutomationGameTests.tickingHoppers…` | The 400-tick budget includes a fixed 200-tick veto retry and asynchronous native proof and journal I/O. On the fastest hosted ticks (2.0 ms/tick) the second PREPARE proof hit its 100-tick deadline before the force and readback finished. | High (about 80%) | No. It also failed locally before W152J. W129aM makes it more likely (completion moved about 40 ticks later). | Fixture: pace the waiting ticks with `GameTestTickPacer`. |
| 2 | `EscrowGovernedMarginGameTests.borderCrossing…` | Fixed Moon pad 1 held a leaked, discarded-but-revived landed rocket from `escrow_retire_before_fill`. It became visible only after the warm-up loaded the pad's entity sections, so the kept pad was refused and the warm-up was replaced: `DESTINATION_LOADING`. | High (about 80%) | No | Fixtures: stop the leak at its source and make pad cleanup wait for entity sections. |
| 3 | `RocketPassengerContinuedUseGameTests.logoutImmediately…` | Same leak: the arrival found pad 1 occupied (`DESTINATION_PAD_BLOCKED`, fail-back). The 270-tick wait expired, then `record.destinationEntityId().orElseThrow()` threw. | High | No | Same as 2 |
| 4 | `RocketPassengerPersistenceGameTests.legacyVehicleLogin…` | Same leak, on pad 2. Same empty Optional. | High | No | Same as 2 |
| 5 | `RocketReleaseBoardingGameTests.sameSeatBoards…` | A vanilla world save (most likely the periodic autosave) landed inside B0's native capture window. It superseded the B0 capture. W80 defines a B0 failure as a dropped admission, so the fence ended before S1. The assertion message is misleading: S2 never ran. | Medium-high (about 70%) | No | Fixture: keep B0 clear of the autosave, and report B0 drops accurately. Production is behaving as W80 designs it. |

**Shared causes.** Failures 2, 3 and 4 have one root cause: a leaked Moon rocket, plus a downstream cleanup that races
asynchronous entity loading. Failures 1 and 5 have separate mechanisms in one class: GameTest tick-count windows
overlap wall-clock background I/O (proof force and readback in 1, a vanilla autosave in 5). None of the five is a
regression from W152J. W129aM did not cause any of them. It narrows failure 1's margin.

---

## Failure 1: `SealedAutomationGameTests.tickingHoppersDropperAndCapabilityConsumerCannotAccessRetainedContainers`

### 1. What the test waits for

`src/main/java/.../rocket/server/SealedAutomationGameTests.java` at 5796ebc01:

- Line 41 sets `timeoutTicks = 400`.
- Lines 57-59 write a RESTORED `SEALED_DISASSEMBLY` record and call a synchronous checkpoint at line 59. At bb0eda86f
  (W129aM) this became `requestCheckpoint` at lines 59-60.
- Line 62 places the bedrock fence on the third member. This deliberately vetoes the first recovery proof.
- Lines 86-115 check that automation cannot reach C for 60 ticks. Line 116 removes the fence at age 60.
- Lines 121-127 define success. Both containers must be published and pipe-used, with checked U absence and no lost
  or replayed items. `succeed()` comes 20 ticks after that.

The timing path is fixed:

1. The first proof is vetoed (`BARRIER_VETO`, via `EscrowChunkStore.java:163-166` and `EscrowBarrier.java:350-353`).
2. `SealedProofBatch.fail` waits `RETRY_TICKS = 200` (`RocketOrdinaryRetirement.java:48`; `SealedProofBatch.java:110-113`).
3. The second PREPARE proof must complete within `ATTEMPT_TICKS = 100` (`RocketOrdinaryRetirement.java:46`;
   `SealedProofBatch.java:56-60` and `68-69`).
4. Then come the journal steps, the lazy unseal and the 20-tick tail.

### 2. What happened in the failed log

- L5796:1476 (03:15:30.200): batch `w152_automation` starts.
- L5796:1478: `HELD … phase=PREPARE reason=BARRIER_VETO`, between progress lines 1 and 2. This is the intended fence
  veto, declared in the manifest as `w152-sealed-automation-proof-fence`.
- L5796:1494 (03:15:30.828): `HELD … reason=DEADLINE`, after progress line 16, about tick 320 to 340. That is about
  VETO + 200 + 100: the second attempt was admitted and then ran out its 100-tick deadline. `SealedDiagnostics.java:33-50`
  flushes at most every 20 ticks, so the event is up to 20 ticks earlier than the line.
- L5796:1499-1500: the timeout, "Didn't succeed or fail within 400 ticks". It is reported after the next batch starts
  because the batch runner's listener fires before the reporter's.
- L5796:2089 (D5796:3082, 03:15:36.365): the same transaction completes `SEALED` later, after the test is gone. The
  third attempt, 200 ticks after the DEADLINE, succeeded.
- The whole batch took 0.80 s for 400 ticks, about 2.0 ms per tick. From the fence removal onward it was about
  1.2 ms per tick (progress lines L5796:1481-1493 are about 23 ms apart). The 100-tick proof deadline was therefore
  only about 120 ms of wall-clock time for store, force (`IOWorker.synchronize(true)`, an fsync) and off-thread
  readback.

Across every available log of this batch (script `w166/batch_speed.py`):

| Run | Progress lines to end | ms/tick | Result |
|---|---|---|---|
| Hosted 5796ebc01 (C5796, L5796) | 20 | 2.0 | **FAILED** |
| Local W152F `full-gametest-final` (e7940183c, pre-W152J) | 20 | 2.3 | **FAILED**: `COMPLETE` at progress 15, timeout at `.../W152F/full-gametest-final/latest.log:1449` |
| Local W152I diagnostic; W129aM-review; W129bc-review | 19; 19; 20 | 2.2; 2.3; 2.2 | passed at the edge |
| Fifteen other local runs and hosted c54de4b0, bb0eda86f, 9ae106795 | 15 to 17 | 2.5 to 4.8 | passed |

Wall time per batch is almost constant, about 0.8 to 1.4 s, because the test is bounded by I/O. The tick rate
decides whether those seconds fit into 400 ticks.

### 3. Most likely cause (high, about 80%)

The fixture counts ticks while the product's proof and journal steps progress in wall-clock time:

- The barrier forces each Level's batch 20 ticks after its first capture (`EscrowBarrier.java:167`, `306-314`).
- The ordinary quota is 4 captures per Level per 20 ticks (`EscrowBarrierBudget.java:8-10`).
- Readback runs on its own thread (`EscrowBarrier.java:221-232`).

GameTest ticks are unthrottled, and on a fast runner the second proof's force and readback outlasted 100 ticks. The
budget left after the mandatory veto retry is only about 40 to 100 ticks on green runs. The predecessor batch,
`precision_migration_port_failure`, is the same in every run, green ones included, so it does not discriminate (see 5).

### 4. W152J and W129aM

**W152J did not cause it.** `git diff 0e78fd4b..5796ebc01 -- src/main` changes:

- `SealedProofTurns` gains `refusedTicks` (`sealedRoom()` at line 21; `reservationRefused()` at 34);
- `RocketOrdinaryRetirement` gains `holdFailBack`/`holdReturn` (lines 209 and 222) and moves `ordinaryStarted` to the
  first own capture (lines 413 and 451);
- the two fail-back callers.

This test has no ordinary or fail-back caller. `reservationRefused()` runs only from `holdFailBack` while a sealed
attempt is active, so `refusedTicks` stays 0 and `sealedRoom()` reduces to its pre-W152J form. Admission is not
involved anyway: a DEADLINE needs an attempt that was already admitted (`SealedProofBatch.java:58,68`). The same test
also failed locally on 2026-10-25, before W152J, and that W152F run is noted in `records/W152.md` around line 2279.

**W129aM did not cause the 5796ebc01 failure**, which predates it, but **it makes the failure more likely.** It turns
each sealed journal step into an asynchronous request:

- `SealedJournalRequests.java:31-51` (bb0eda86f): `writePending` means wait, and a request sets `notBefore = now + 200`
  before it is answered;
- `SealedDisassemblyCoordinator.java:98-101` uses `requestCheckpoint`;
- `CheckedRootWriter.java:21,279` adds `CADENCE_TICKS = 10`, and completions are published at the start of a tick;
- the fixture itself switched to `requestCheckpoint` (`SealedAutomationGameTests.java:59-60`).

Measured completion moved from progress 14-15 (pre-W129aM median, 15 runs) to 16-18 (W129aM-review 18, W129bc-review
18, hosted bb0e and 9ae1 both 16). That is about 40 fewer ticks of margin.

### 5. Neighbouring batch

The preceding batch is `precision_migration_port_failure` (L5796:1375-1475), with intentional chunk-save failures. It
is the predecessor in every log examined, green ones included (`w166/batch_ticks2.py`). It leaves nothing that the
proof uses:

- its `OneShotSaveFailure` listener only fires for its own chunk near x=512 000;
- the listener is unregistered in `finally` (`PrecisionAssemblerMigrationSaveFailureGameTests.java:120,139`);
- its `ServerChunkCache.save(true)` (`PrecisionAssemblerManager.java:423`) is synchronous and flushes the worker
  before returning;
- the escrow save monitor keeps no quarantine state (`EscrowSaveMonitor.java:72-97`).

The autosave at D5796:2488 (03:15:30.907) came after the DEADLINE line, and a superseding save would have produced a
silent 1-tick retry rather than a DEADLINE.

### 6. Proposed fix (fixture)

Pace the fixture's ticks with the existing `GameTestTickPacer`, as W152H and the release fixtures already do
(`records/W152.md` around line 2703). Call `pacer.pace()` from the `onEachTick` callback, at least from age 60 onward.

- The tick budget, the 100-tick proof deadline, the 200-tick retry and every assertion stay unchanged.
- Each tick then spans 50 ms, as on a 20 TPS server, so the force, readback and W129aM journal writes no longer
  consume tens of ticks.
- The 60-tick automation checks are tick-based and unaffected.
- Expected wall time is about 15 to 18 s.

No production change.

---

## Failures 2, 3 and 4: one shared cause, leaked landed Moon rockets

### Root cause (high, about 80%)

`EscrowRetireBeforeFillGameTests` (batch `escrow_retire_before_fill`, three tests) lands three governed rockets on the
Moon's fixed pads.

- Step 6 caches `arrival` while the record still exists (`EscrowRetireBeforeFillGameTests.java:171-184`).
- Cleanup discards that cached object at the end (`EscrowRetireBeforeFillGameTests.java:197-207`, and `@AfterBatch`
  at 54-55).
- By then F8 has released the flight tickets, and the pad chunk may have been unloaded and reloaded. The discard hits
  a stale instance. `RocketEntity.remove` still logs `REMOVED reason=DISCARDED`, but the world or disk copy survives
  and is loaded again later.

This happens in every run:

| Run | The three landed rockets (landing, then stale discards) | Later discard of the revived copies |
|---|---|---|
| c54de4b0 (pre-W152J, green) | Cc54d:6881-6907 | all three by batch `flight` (Cc54d:7804-7807) |
| bb0eda86f (green for these) | Lbb0e:1241-1266 | two by `flight` (Lbb0e:2167-2168), the third by `moon_arrival_crater` (Lbb0e:3001) |
| 9ae106795 (green) | G9ae1:6910-6935 | all three by `flight` (G9ae1:7829-7833) |
| **5796ebc01 (red)** | L5796:1212-1238 | **only `a080718d` by `flight` (L5796:2141).** `6d2380dd` and `15345e96` survived and were seen ticking again at L5796:5209, 5895 and 5903, then discarded by `flight_security` (L5796:5909-5910, 03:27:23). |

Those downstream discards come from `RocketFlightGameTestFixtures.primePadChunks` and `clearPadRockets` (lines
226-246). `clearPadRockets` discards only entities that are already loaded, immediately after `primePadChunks` adds
tickets. Entity sections load asynchronously (W122: visible is not the same as loaded), so on a fast or slow tick
sequence the cleanup can miss on-disk rockets. `MoonArrivalGameTests.java:88-99` has the same race: it clears
immediately and again after 80 unpaced ticks.

The product saw the occupant only once the pad's entity sections loaded:

- Selection uses entity discovery over already-loaded sections only (`NamedLandingPadOccupancy.java:36-37`).
- `RocketLandingPadSelector.unavailable` refuses a `RocketEntity` inside the exact region box without logging
  (lines 318-323). Only the wider discovery check logs `ARCE_PAD_OCCUPANCY_REFUSED`, and no Moon line appears near
  these failures, so the occupant was a rocket entity in the box.

As a result, pads 1 and 2 behaved as occupied in 5796 but not in the green runs:

| Moon PREPARE | Candidates used, 5796ebc01 | Candidates used, 9ae106795 |
|---|---|---|
| `passenger_logout_cleanup` | 2 | 2 |
| `w133_ordinary_fuel_launch` | 4 | 2 |
| `rocket_l2_complete_flights` | 4 | 2 |
| `legacy_passenger_recovery` | 3 | 2 |

Sources: L5796:3755, 5125, 5196, 5835; G9ae1:9465, 10840, 10911, 11551.

Batch order before these tests is identical in the red and green runs (`w166/batch_order.py`). The difference is
purely the cleanup race.

### Failure 2: `EscrowGovernedMarginGameTests.borderCrossingMarginsLandOnAnEmptyMoonAndARestartReticketsTheSameColumns`

1. **What it asserts.** In `EscrowGovernedMarginGameTests.java`:
   - `launchWhenCold` (207-232) requires the predicted pad to be cold (line 209), so its entity sections are not
     loaded. It then expects `DESTINATION_LOADING` and a warm-up on the predicted pad.
   - `relaunchWhenReady` (234-253) waits for READY and expects `JOURNAL_PENDING`. The assertion is at lines 245-246.
2. **The log.**
   - L5796:3152: warm-up STARTED for request `first` on pad 1.
   - L5796:3153: READY at age 3, once the pad's entity sections had loaded.
   - L5796:3154-3156: on relaunch the kept pad was revalidated first (`RocketLandingPadSelector.java:102-108`) and
     refused. Pad 0 was refused and pad 2 chosen. The registry released the warm-up as `REPLACED`, from
     x=66..79 to x=-62..-49 (`RocketWarmupRegistry.java:139-147`), and started a new one, so the decision was
     `LOADING` (line 157).
   - L5796:3158: "DESTINATION_LOADING null".
   - In the green runs the same request goes STARTED, READY, BOUND, PREPARE `candidates=1` (G9ae1:8847-8851).
3. **Cause.** A leaked rocket on pad 1, invisible at the first request by the fixture's own cold-pad precondition and
   visible four ticks later. High confidence.
4. **W152J / W129aM.** Neither changes pad selection, warm-ups or the margin fixture.
5. **Leftover state.** `escrow_retire_before_fill` (L5796:1144-1238), whose leftovers were not cleared by `flight`
   (L5796:2141) or by `moon_arrival_crater` (L5796:2966). The test also leaves its own warm-up (expired at L5796:3355)
   and its Earth rocket (discarded at L5796:5906).

### Failure 3: `RocketPassengerContinuedUseGameTests.logoutImmediatelyCancelsOnlyItsPendingReconnect`

1. **What it waits for.** `afterLanding` (`RocketPassengerContinuedUseGameTests.java:198-228`) waits up to 270 ticks.
   The wait is paced, through `RocketFlightGameTestFixtures.awaitFlightState`/`awaitPaced` (lines 126-145), for LANDED
   on the Moon plus an acknowledged-current journal. When the wait expires it runs the assertions anyway.
2. **The log.**
   - L5796:3755: PREPARE `candidates=2`, so pad 1 was selected.
   - L5796:3766: at arrival (`RocketTransferService.java:673-689`) the pad recheck failed:
     `ARCE_LAUNCH_DISCARD site=FAIL_BACK … reason=DESTINATION_PAD_BLOCKED`.
   - L5796:3767: `CREW_WAITING`.
   - L5796:3775: the 270 paced ticks expired (03:21:06.6 to 03:21:20.3) and the failure "No value present" was logged.
   - No stack trace exists: the GameTest reporter logs only the message, in latest, debug and console logs alike.
   - The record still existed afterwards, because D5796:5840 shows it `ABANDONED … JOURNAL_STOPPED` at 03:22:42. So
     `journal.find(transfer).orElseThrow()` (line 213) succeeded.
   - **The empty Optional is `record.destinationEntityId().orElseThrow()` at line 214.** A pre-spawn fail-back never
     creates a destination identity.
3. **Cause.** The shared root cause. High confidence.
4. **W152J.** The fail-back did go through W152J's `holdFailBack` (`RocketTransferContinuations.java:39`). It
   succeeded (the FAIL_BACK note is logged inside the hold), and the failure is the blocked pad, not the fail-back.
5. **Leftover state.** As in failure 2. The immediately preceding batch, `space_safety_return_budget`, does not touch
   the Moon.

### Failure 4: `RocketPassengerPersistenceGameTests.legacyVehicleLoginKeepsRefueledJournalAuthority`

1. **What it waits for.** The same pattern (`RocketPassengerPersistenceGameTests.java:96-100`).
2. **The log.**
   - L5796:5835: `candidates=3`. Pads 0 and 1 were refused, the leak was now visible, and pad 2 was selected.
   - L5796:5846: `DESTINATION_PAD_BLOCKED`.
   - L5796:5855: "No value present" after 270 paced ticks.
   - D5796:7624 shows the record `ABANDONED` at 03:27:23, so it still existed.
   - **The empty Optional is `record.destinationEntityId().orElseThrow()` at line 100.**
   - Eight seconds later `flight_security` loaded and discarded both leaked rockets (L5796:5895-5910).
3. to 5. As for failure 3.

### Fix for failures 2 to 4 (fixtures only)

1. **Stop the leak at its source.** In `EscrowRetireBeforeFillGameTests`:
   - keep the arrival's UUID and its column;
   - hold a fixture ticket on that column from landing until removal;
   - in cleanup, wait (paced) for `areEntitiesLoaded` and discard the current instance, `moon.getEntity(id)`, not the
     cached object;
   - then verify that no copy remains, for example with `storeChunkSections` and a disk check, as the release fixtures
     do.
2. **Make the shared pad cleanup deterministic.** `primePadChunks` followed by `clearPadRockets` should wait (paced,
   bounded) until every pad chunk's entity sections are loaded before discarding. Otherwise it silently misses rockets
   on disk. Apply this to `RocketFlightGameTests`, `RocketFlightSecurityGameTests`, `MoonArrivalGameTests` and
   `ClassicMilestoneAdvancementGameTests`.
3. **Diagnostics, optional and not a loosening.** In `afterLanding`, replace the bare
   `destinationEntityId().orElseThrow()` with an assertion that names the record phase. That would have shown
   "fail-back DESTINATION_PAD_BLOCKED" instead of "No value present". The margin fixture could also assert, before its
   cold launch, that the predicted pad's entity sections were once loaded and empty.

No production change. The selector's loaded-sections-only discovery, followed by a recheck at arrival and a
fail-back, is the designed W83b/W13 behaviour.

---

## Failure 5: `RocketReleaseBoardingGameTests.sameSeatBoardsOnlyAfterS2AndSurvivesReloadWithoutRevivingOldCopies`

### 1. What it asserts

In `RocketReleaseBoardingGameTests.java` at bb0eda86f:

- `admit()` (152-174) admits the release once (`releaseAbsentForTests`, line 162).
- `awaitS2()` (185-200) exercises the retained rider's departure only after it has observed B1's capture (line 187).
  Once the fence drops (`!releaseFenced`, line 199), it asserts `departed` with the message "S2 completed before the
  retained-rider departure was exercised" (line 200).

### 2. The log

- Lbb0e:7918 (Dbb0e:9680, 04:28:50.608): `state=B0`.
- **Dbb0e:9682 (04:28:51.119): "Gathering id map for writing to world save".** This is a full world save
  (`saveEverything`) inside the B0 window.
- Lbb0e:7921 (Dbb0e:9685, 04:28:51.553): `ARCE_REJOIN_RELEASE_HELD … state=B0_FAILED`, about 19 paced ticks after B0,
  which is the barrier's 20-tick force cadence.
- Lbb0e:7925: the assertion.
- There is no `S1`, `E1`, `B1`, `J1` or `S2` notice. In the green runs the sequence is B0, S1, E1, LEAVE,
  CAPTURE_FAILED, B1, J1, S2 (G9ae1:13579-13600; L5796:7860-7881).

### 3. Cause (medium-high, about 70%)

A vanilla save writes the authority slot's entity section:

1. `EscrowEntityStore.storeEntities` reports the store to the monitor (`EscrowEntityStore.java:40-45`).
2. The monitor advances the generation (`EscrowSaveMonitor.java:93-97`).
3. The pending B0 capture completes as `SUPERSEDING_SAVE` (`EscrowBarrier.java:321-323`).
4. The coordinator receives a null proof and calls `fail(CAPTURE_FAILED)` (`RocketReleaseCoordinator.java:251-258`).
5. In B0 that becomes `drop(entry, "B0_FAILED")` (lines 275-276 and 281). The fence lifts, so `awaitS2` takes the
   `!releaseFenced` branch with `departed == false`.

This is the designed W80 behaviour. In `records/W80-DESIGN.md`, B0's failure row says "On failure, W80 lifts the
fence, drops the admission and logs once, and the operator may retry". The design also notes that vanilla writes
entity sections at autosave.

W161 made B1's supersession deterministic. B0 was left exposed to an incidental save. The batch holds one test, the
predecessors are identical in the red and green runs, and the save fell in the middle of the test, so it is almost
certainly the periodic autosave. Vanilla 1.20.1 autosaves when `tickCount % 6000 == 0`; this is from memory and
should be checked against the mapped jar. At about 20 paced ticks per B0 window that is about 0.3% per run, which
fits a first occurrence after many green runs.

### 4. W152J and W129aM

Neither touches `RocketReleaseCoordinator`, `RocketRejoinSavedData`, `EscrowBarrier`, `EscrowEntityStore` or the save
monitor. The W129aM `--stat` lists no `rocket/rejoin` file.

### 5. Neighbouring batch

None. The predecessors (`exoplanet_sapling`, `w152_terrain`, `w152_legacy_manager`) are the same in Lbb0e and G9ae1.

### 6. Proposed fix (fixture)

1. Before `admit()`, wait (paced, bounded) while the next periodic autosave falls within B0's worst-case window, for
   example while `6000 - server.getTickCount() % 6000 <= 60`. That costs at most about 60 ticks; the fixture uses about
   310 of its 380. Then no vanilla save can supersede B0. Leave B1's deliberate `storeChunkSections` unchanged.
2. Make the assertion truthful. Before asserting `departed`, assert that the coordinator left B0 (for example, the
   stage was observed at S1/B1), and report "B0 dropped (superseding save)" otherwise.

Re-admitting after a B0 drop is not acceptable: the `B0_FAILED` WARN would appear only in some runs, and the checker
rules forbid manifesting such a line. A production retry of B0 would contradict W80's B0 rule, so it needs a design
decision rather than a flake fix.

---

## Method and residual uncertainty

- Every failure message had no stack trace in latest, debug or console logs. The empty Optionals in 3 and 4 are
  inferred from the code path plus the later `ABANDONED` lines, which prove the records still existed.
- I could not see which pad each leaked rocket occupied, because selection logs no position. The pad assignment
  (6d2380dd and 15345e96 on pads 1 and 2) is inferred from the candidate counts and the revival times.
- To check the cleanup race, confirm with a targeted run: `escrow_retire_before_fill`, then
  `machine_menu_channel`, with the `flight` and `moon_arrival_crater` batches filtered out. It should fail every time.
- Helper scripts (read-only analysis of the logs) are in `input/scripts/`: `batch_ticks.py`,
  `batch_ticks2.py`, `batch_speed.py`, `batch_order.py`, `landed_leftovers.py`, `alive_rockets.py` and `revived.py`.
  They read the scratchpad paths the session used; adjust the paths to `input/` before rerunning them.
