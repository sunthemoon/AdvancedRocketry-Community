# TASK W307: the release boarding GameTest depends on the terrain above its structure

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w307`, branch `codex/v1.8.0-w307`, created by Root from the shared head |
| Decision | Root decision R353 (QUEUE row W307) |
| Earlier work | [W304](records/W304.md), merged at `ac52dac32`: `rocket/rejoin/RocketRejoinFixturePad.java` builds a recorded, restored stone pad at y 299 to 300 over every candidate column, so the rejoin fixtures no longer depend on the terrain above their structures or on the one dry column of the spawn ring |
| Record | `docs/work/v1.8.0-root-queue/records/W307.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W307/` |

## The problem (W304's note)

`RocketReleaseBoardingGameTests` (batch `rocket_release_boarding`) places its player from a floor inside its
structure, about y -60, but the placement takes its height from the Overworld surface, so its landing spot depends
on the natural terrain above the structure (and, over the sea, on the spawn ring), as the rejoin fixtures did before
W304.

## What to do

1. Read the test and the placement path it uses, and record where its player actually lands in a normal run.
2. If it depends on the terrain, give it a recorded, restored landing area as W304 did (reuse `RocketRejoinFixturePad`
   if it fits; move it to a shared GameTest package only if both users need that), with the batch's end hook restoring
   it after a failure or timeout. Change no production code, assertion or timeout.
3. Show it with W304's flooded-column experiment on throwaway commits that are never merged (keep them local and say
   so): the batch with the landing spot flooded, before and after your change.
4. If the test does not in fact depend on the terrain, say why in the record and change nothing.

## Verification

The experiment above; the `rocket_release_boarding` batch three times and the `rocket_rejoin_*` batches once; the whole
unit suite if any class changes; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock;
`python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Release boarding behaviour and production code, the ledger, the status file and QUEUE.
