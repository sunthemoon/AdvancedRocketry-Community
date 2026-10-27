# TASK W166: five intermittent hosted GameTest failures

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md), the diagnosis
[records/W166-DIAGNOSIS.md](records/W166-DIAGNOSIS.md), and the earlier
[TASK-W161](TASK-W161-INTERMITTENT-GAMETESTS.md) with its record first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w166` (new; from the shared head named in your prompt, which contains W156) |
| Branch | `codex/v1.8.0-w166` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W166.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W166/`; the failed runs' logs are in its `input/` folder |

Two of the three hosted regressions after W152J merged failed on five different GameTests. The read-only diagnosis
found three test-side causes and no production defect. Confirm each cause before fixing it; if the evidence points
elsewhere, say so in the record and fix what you find instead.

1. **The sealed hopper test counts ticks against wall-clock proof work.**
   `SealedAutomationGameTests.tickingHoppersDropperAndCapabilityConsumerCannotAccessRetainedContainers` vetoes the first
   proof on purpose, so the second proof must finish its store, force and readback within 100 ticks inside the
   400-tick budget. On fast unthrottled ticks (about 2 ms each) it ran out of time (diagnosis, failure 1). Pace the
   fixture's ticks with the existing `GameTestTickPacer`, as the W152H and release fixtures do. The tick budget, the
   proof deadline, the retry delay and every assertion stay unchanged.
2. **A leaked landed Moon rocket blocks later flights.** `EscrowRetireBeforeFillGameTests` discards a cached arrival
   instance that has already unloaded with its chunk, so the disk copy loads again later. The shared pad cleanup
   (`primePadChunks` then `clearPadRockets`) discards only loaded entities without waiting for entity sections, so it
   can miss that copy. In the failed run the leftovers blocked Moon pads 1 and 2, failing three tests (diagnosis,
   failures 2 to 4).
   - Show the leak first: a test or check that finds the discarded rocket's copy after the batch, failing on the
     branch base.
   - Discard the live instance by UUID while its column is held and its entity sections are loaded, then check that no
     copy remains.
   - Make the shared pad cleanup wait, paced and bounded, until every pad chunk's entity sections are loaded before it
     discards. Apply it to every fixture that uses that cleanup.
   - List any other fixture that keeps a rocket entity across ticks and discards it later. Fix the ones that land on
     shared Moon or Earth pads; list the rest in the record.
   - In the passenger fixtures' `afterLanding`, replace the bare `destinationEntityId().orElseThrow()` with an
     assertion that names the record's phase and code, so a fail-back reports itself.
3. **An autosave can supersede B0 in the boarding test.**
   `RocketReleaseBoardingGameTests.sameSeatBoardsOnlyAfterS2AndSurvivesReloadWithoutRevivingOldCopies` failed when a
   world save landed in B0's capture window. W80 drops the admission on a B0 failure, as designed (diagnosis,
   failure 5).
   - Check the 1.20.1 autosave period in the mapped sources.
   - Make the fixture admit only when no periodic autosave can fall inside B0's window.
   - Make the assertion report a B0 drop as one, instead of "S2 completed before the retained-rider departure was
     exercised".
   - Leave B1's deliberate store unchanged.

## Rules

- No longer timeouts, no loosened assertions, no deleted or skipped tests, and no manifest entry for a line that only
  sometimes appears (AGENTS §5).
- Production code is out of scope. If a cause turns out to be in production, stop and report it with the evidence.
- Do not touch the areas the running sessions change:
  - W152K: `SealedProofTurns`, `RocketOrdinaryRetirement` and `SealedDisassemblyCoordinator`.
  - W164: the I6 and landed-disassembly classes and `RocketTransferService`.
  - W129bc: the satellite registry writer.

  The test files named above are yours.

## Verification

- Each part's new or changed tests, failing first where you reproduce the cause.
- The five named tests' batches, plus `escrow_retire_before_fill` followed by a batch that uses the Moon pads, run
  three times each.
- The whole unit suite.
- One complete GameTest that passes with the unchanged log checker.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule. Before starting,
check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free.

## Follow-up under Root decision R123 (2026-10-27)

W128R's three full GameTests each timed out in
`SealedSpecialHostAutomationGameTests.tankAndBayStaySealedUnderTickingAutomation`. Its batch reached SEALED at about
the same wall time as in green runs on other branches (2.0 to 2.3 s against 1.6 to 2.6 s), but at about tick 400
instead of 300 to 340. That is cause 1 again, in a sibling fixture with the same veto, 200-tick retry and 400-tick
budget.

- Pace both of its tests (`w152_special_resources` and `w152_special_containers`) as `a859acaca` paced the hopper
  fixture.
- Keep the budget, the deadline, the retry and every assertion.
- Run each batch three times.
- Look for other fixtures that wait out a deliberate veto and its 200-tick retry inside a fixed tick budget without
  pacing. List them in the record and pace the ones that do.

## Not in this task

- Any other flaky test you notice: list it in the record instead.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
