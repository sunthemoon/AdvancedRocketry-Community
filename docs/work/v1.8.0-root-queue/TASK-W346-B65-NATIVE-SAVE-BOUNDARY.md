# TASK W346: B6.5's native case at the source-save boundary

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w346`, branch `codex/v1.8.0-w346`, created by Root from W342's branch (W342's GameTests and design) with the shared head merged |
| Decision | Root decision R415 (QUEUE row W346) |
| Design input | `docs/work/v1.8.0-root-queue/records/W342-DESIGN.md`, section 4 (the native assessment), and `records/W342.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W346.md`, design addendum `records/W346-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W346/` |
| Review | This slice adds a gated save control, so a separate read-only review runs before Root merges it |

## Why

W27A row B6.5 is the crash state after D7 in which a source-Level save has stored the discard of the source rocket S
and the destination Level has not yet stored the new rocket. W342 covers ordinary recovery from that state with
GameTests that build it directly. No native case shows that the production sequence reaches it and that a real
restart recovers from it, so B6.5 is still NOT_COVERED natively. Candidate C2 has not been built yet, so a gated
control added now lands in the same JAR as the rest of the rerun.

## What to do

1. **Check the save paths first, then write `records/W346-DESIGN.md` before any code.** Use javap on the mapped
   Forge 47.4.10 jar as W338 did (its logs are under `D:/GitHub/ARCE-Task-Evidence/root-queue/W338/`). Answer, with
   the bytecode lines:
   - whether a chunk unload, the entity manager's unload processing, or any other path that can run while the
     server is up stores entity sections or chunks of a Level whose `noSave` is true;
   - whether `saveEverything(true, false, false)` skips such a Level entirely, its entity manager included;
   - what else `noSave` stops for that Level (its chunks and Level-attached data), so the oracle avoids it;
   - in this repository: what retires a COMMITTED transfer record after the destination spawns, and whether that
     can happen before the destination Level has stored the new rocket. If production can retire T' while the
     destination entity exists only in memory, the state after a kill has no S, no destination and no record: that
     is a production finding. Report it with the evidence and stop; do not fix production code in this task.

   If the bytecode shows that `noSave` cannot keep the destination's entity section off disk on every path that
   can run in the window, stop and report. Do not build a control that cannot hold the state.
2. **The gated control**, as W342-DESIGN section 4 describes, beside W331's commands
   (`BayGapReleaseTestCommands` or a sibling): registered only with the release-test hooks property, permission 2,
   and `EscrowTestEndpoints.requireEnabled` called first. One subcommand holds the saves of a named Level (sets
   `noSave`, refuses if it is already set), one runs the autosave's own call `saveEverything(true, false, false)`
   and reports which Levels were skipped, and one restores the held Level for an aborted run. The held state lives
   in memory only. Nothing outside the command reads it, and no production call site is added. Add a gate unit test
   like W331's.
3. **The native case B6.5** in the bay forced-stop runner (`scripts/run_v180_bay_forced_stop.py`) or as a W331
   family beside it, with the candidate's commit and JAR SHA-256 required as the other bay families do:
   - a real bay group flight; hold the destination Level before the transfer spawns its destination;
   - wait until the journal has acknowledged COMMITTED, run the autosave pass, then hard-kill the JVM;
   - in the stopped world, confirm that the source chunk has no S, the destination chunk has no rocket of L, the
     journal file holds committed T' with consumed snapshots, the registry holds the group's satellites, and no
     receipt exists;
   - restart 1: REBUILD_DESTINATION from T', the same consumed operations, no package in any bay, inventory or
     item entity, an unchanged registry, no receipt;
   - restart 2: the rebuilt destination is kept with the same consumed operations, no entity file is deleted, and
     no source is forced back.

   The oracle reads nothing that `noSave` stopped as evidence (for example the destination pad or atmosphere data).
4. **The campaign driver**: add the family to `scripts/run_v180_integrated_campaign.py` and its parse test. W345
   adds other families to the same file; Root resolves any merge conflict.
5. **Proof the case can fail**: on a scratch JAR with W342's mutation M1 (the recovery decision never chooses
   REBUILD_DESTINATION), the native oracle must fail. Keep the patch beside its log and delete the scratch tree.

## Verification

- `./gradlew build` with every unit test, and the new gate test.
- The full GameTest run with `scripts/check_gametest_log.py`; any manifest addition names only lines your code emits.
- The native smoke of the new case on a JAR you build from your commit, with both restarts, and the M1 scratch run.
- The runner and driver Python tests (PYTHONPATH set to the tests directory, TEMP on D:).
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges after the read-only review.

## Not in this task

Production code outside the gated command; W331's other NOT_COVERED rows; W345's families; the status files,
QUEUE and the risk register.
