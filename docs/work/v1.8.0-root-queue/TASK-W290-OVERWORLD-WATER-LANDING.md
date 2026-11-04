# TASK W290: a rocket returning to the Overworld must not land over liquid

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w290`, branch `codex/v1.8.0-w290`, created by Root from the shared head |
| Decision | Root decision R323 (QUEUE row W290) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, round 3 finding 3-2 (screenshots `u20` to `u22`, the server log's `BELOW_IS_WATER` receipt) |
| Record | `docs/work/v1.8.0-root-queue/records/W290.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W290/` |

## What a real client showed

A rocket flying from a station back to the Overworld found the first fixed landing point beside the spawn taken by
another landed rocket, landed at the second fixed point, 64 blocks east of the spawn, at (-143.5, 63, 464.5) on the
sea (`BELOW_IS_WATER`), and its rider sank after LEAVE SEAT. Read-only code: `rocket/transfer/SurfaceLandingRule.java`
lines 51 to 55 skip the selection-time support check in the Overworld (its comment cites ADR-033's spawn rule), and the
liquid check lives inside that support check (`RocketLandingPadSelector.java` lines 502 to 523); the ground height
comes from the `MOTION_BLOCKING_NO_LEAVES` heightmap (line 450), which counts a water surface.

## What to do

1. Read ADR-033 and the landing selection's ADRs and find why the Overworld skips the support check, and what the
   spawn rule must keep (for example, landing near the world spawn even where the support check would otherwise
   refuse).
2. Reproduce it in a GameTest that fails on the shared head: an Overworld arrival whose first fixed point is occupied
   and whose next point is over water must not land on the water.
3. Fix it so that no dimension selects a landing point over liquid (water, lava or any fluid block at the landing
   column), while keeping what ADR-033's rule exists for, and keeping a bounded search (no unbounded scan, no forced
   chunk loads beyond what the selector already uses). If no dry point exists within the existing bounds, follow the
   selector's existing refusal or fallback path and say which in the record. If the ADR's text has to change, propose
   the exact wording in the record for Root; do not edit the ADR.

## Verification

The new GameTest failing before and passing after; the landing selector and transfer GameTests and unit tests;
`./gradlew build` with all unit tests; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM
lock; `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Named landing pads (W291), rider position (W292), seat assignment (W287), the ledger, the status file and QUEUE.
