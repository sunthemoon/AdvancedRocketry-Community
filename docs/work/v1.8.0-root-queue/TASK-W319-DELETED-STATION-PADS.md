# TASK W319: landing pad index entries after a station is deleted

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w319`, branch `codex/v1.8.0-w319`, created by Root from the shared head |
| Decision | Root decision R372 (QUEUE row W319) |
| Background | [W318](records/W318.md): `StationManager.delete` does not remove a deleted station's entries from `LandingPadIndex` (`station/pad/`), and two escrow GameTests leave 20 index entries under IDs that are not stations |
| Record | `docs/work/v1.8.0-root-queue/records/W319.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W319/` |

## What to do

1. Find out what a deleted station's leftover pad entries do to a player or operator:
   - `/arcepad list` and `/arcepad select`;
   - the flight console's pad choice;
   - landing selection and the index's per-station limits;
   - save size over many deletions;
   - a new station that reuses the deleted station's grid cell.

   Reproduce each effect you claim with a GameTest or a unit test. If nothing visible happens, say so and stop after
   item 3.
2. If there is an effect, remove a station's pad entries when the station is deleted, in the same operation that
   deletes it (`StationManager` and `LandingPadIndex`). The index is saved data: write a one-page design section in the
   record first, covering:
   - when removal happens;
   - what a load of an old save with orphaned entries does (prune them on load only if that is safe and bounded);
   - the schema, which should not change.

   Root will have a separate session review it before merge.
3. Say in the record whether the 20 entries that `EscrowGovernedLiveReturnGameTests` and
   `EscrowGovernedSourceFlightGameTests` leave are a test cleanup gap (fix it in those tests' own cleanup if it is) or
   expected.

## Verification

- The tests from items 1 to 3 that fail before any change.
- The landing pad, station deletion and pad selection tests.
- `./gradlew build` with all unit tests.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Pad selection rules, landing rules, station creation, the ledger, the status file and QUEUE.
