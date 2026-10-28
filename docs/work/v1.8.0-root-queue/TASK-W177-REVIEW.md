# TASK W177-review: independent read-only review of the canonical identifier bounds

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w177` at `cb1f27839`, on `09b2fb614`. Read `git diff 09b2fb614..cb1f27839`. Review worktree `D:/GitHub/arce-v180-w177-review`, a detached checkout of `cb1f27839` |
| Task it answers | [TASK-W177](TASK-W177-CANONICAL-ID-LENGTH.md), from W173-DESIGN section 6.3 and W168-R5 |
| Record | `docs/work/v1.8.0-root-queue/records/W177.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W177/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W177-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The three parser bounds** (`SatelliteKindDefinition.parse`, `ResourceTableCodec.id`,
   `SatelliteComponentDefinition.location`). Each must bound the canonical identifier exactly as
   `BoundedCelestialCodecs.validateResourceLocation` does. Look for any other content parser that still bounds only
   the spelling; the record names `SatelliteItemData`.
2. **What the refusal does.** The record says such a datapack is refused at reload, and that on the initial load the
   world will not start.
   - Check that this is the catalog's existing behaviour for an invalid entry, not a new failure mode.
   - Check whether one invalid entry refuses only itself or the whole catalog.
   - Check the diagnostic the operator sees.
3. **The fit check.** `SatelliteRecordFit.resourceMissionBytes` now covers every reachable state of a mission from
   the longest accepted definition ID. Rerun the boundary test, and check that no table that loads today is refused.
4. **W168-R5's guards:**
   - `reserveAccount(UUID)` replaces the `int` overload at every call;
   - the override test really fails if `CodecRecordSizer` stops declaring one of the four methods;
   - the test sizers' delegation, and the `SizedMissions` change, preserve what those tests protect.
5. **Nothing else changed:** the decoder, load acceptance, formats, bounds, budgets and the protocol. Earlier tests
   change only as the record states, with no relaxed assertion.
6. **The record's two side findings.** Gas table file IDs have no length bound, and `ResourceTables.create` throws
   past 176 characters. `SatelliteItemData` bounds item-NBT identifiers by spelling. Say how serious each is.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the new and changed test classes;
- the `satellite` and `celestial` packages.

Unit runs need no lock. Check 6 GiB free memory first. No GameTest, runData, full build or server. Delete the
worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W177-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
