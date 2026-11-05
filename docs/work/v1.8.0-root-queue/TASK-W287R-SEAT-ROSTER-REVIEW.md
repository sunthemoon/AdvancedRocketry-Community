# TASK W287R: independent review of W287's seat roster change

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
you did not write this change. Review; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Change under review | Branch `codex/v1.8.0-w287` at `c222724a1` (base `ef33f273f` on the shared branch), checked out read-only at `D:/GitHub/arce-v180-w287r` |
| Implementer's record and design note | `docs/work/v1.8.0-root-queue/records/W287.md` on that branch; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W287/` |
| Task it implements | [TASK-W287](TASK-W287-SEAT-ROSTER.md), with Root's design direction R334 |
| Your output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W287R/REVIEW.md`, plus your logs in that folder |
| Why a review | It changes a persisted seat assignment (AGENTS.md section 13) |

## What to check

1. **The design.** Does the change clear the seat only for a dismount the player chose while the rocket is on the
   ground and not in a flight or transfer phase, and keep it for disconnects, dimension transfers, recoveries, server
   stops and other server-driven detaches? Look for paths the design note misses (death, teleport commands, another
   entity dismounting the player, a client that sends sneak while a transfer starts, the rocket being removed).
2. **Persistence and crash windows.** Which saved fields change, whether a save or crash between the dismount and the
   assignment's removal can leave an inconsistent state, and what happens to players already saved with a stale
   assignment.
3. **Server authority.** Nothing decided from a client-supplied position or state beyond the vanilla sneak input the
   server already accepts.
4. **The three full-regression failures in W287's local run** (its record, section "Full-regression failures left
   open"): `supersededEntityProofRetriesTheSameFrontierAfterBackoff`, `supersededTerrainProofRetriesTheSameFrontierAfterBackoff`
   and especially `sameSeatBoardsOnlyAfterS2AndSurvivesReloadWithoutRevivingOldCopies` ("Release boarding fixture
   exceeded its bounded wait at phase 3"), which involves seats. Determine whether W287's change can cause any of
   them: read the tests and the changed code, and run the affected batches (`rocket_release_boarding` and the escrow
   proof batches) on the W287 commit and on its base, several times each, under the heavy-JVM lock
   (`D:\GitHub\ARCE-Task-Evidence\locks\heavy-jvm`, protocol in WORKER-RULES), to compare.
5. The tests W287 added: do they fail on the base for the reason the task names, and do they cover the keep cases?

## Report

Write REVIEW.md in English: a verdict (APPROVE, APPROVE WITH NOTES, or CHANGES REQUIRED), each finding with its
severity, the file and line, what is wrong and what would fix it, your conclusion on each of the three failures with
its evidence, and the commands you ran with their results. The last line of your answer is the REVIEW.md path.
