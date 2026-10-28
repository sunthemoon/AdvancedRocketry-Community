# TASK W176-review: independent read-only review of the fuel loader's save protection

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w176` at `df67ad06f`, on `b6250acfd`. Read `git diff b6250acfd..df67ad06f`. Review worktree `D:/GitHub/arce-v180-w176-review`, a detached checkout of `df67ad06f` |
| Task it answers | [TASK-W176](TASK-W176-FUEL-LOADER-SAVE-PROTECTION.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W176.md` on the branch: the design at its top, with the proposed risk and ADR-065 text |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W176/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W176-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a save-refusal slice. Its rule must match the bay's and the cargo devices' (W109): a lossless root, or one over
the work budget, saves unchanged, and only an observed native incompatibility records a `GuardedChunkSaves` denial for
that chunk.

## What to check

1. **The rule.** Compare it line by line with `RocketFluidCargoProtection` and `RocketFluidCargoBlockEntity`. Check:
   - the 256 and 257 coordinate boundary;
   - the saturation behaviour after it;
   - that a root is never discarded or replaced.
2. **The extraction.** `FluidFuelLoaderBlockEntity` was split. Check that no behaviour moved, no tank operation
   changed, and fuel admission is untouched.
3. **The tests.** The GameTests and unit tests must test the real protection. The WARN manifest entries must be
   exact, and earlier fixtures change only as the record states.
4. **The native S1 rows.** Six pass. The three downgrade rows fail one assertion: the historical JAR rewrites
   `level.dat` and `level.dat_old` before its journal guard refuses to start. It drops 12 block-registry and 11
   item-registry entries, and updates LastPlayed. Terrain, entities and the journals are preserved.
   - Check that this is the old JAR's behaviour and not W176's.
   - Say what reopening the world on the new JAR does with that metadata.

   Root decision R138 keeps those rows recorded as failed and treats the rewrite as a known downgrade behaviour,
   since downgrades already need a pre-upgrade backup.
5. **The proposed risk and ADR-065 text** for AGENTS 3.3: scope, duration, log volume and recovery. Is it accurate?
6. **Nothing weakened:** no format, bound, budget or timeout changed.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the new and changed loader test classes, and the `rocket.fuel.loader` and `resourceescrow` packages;
- the harness's Python tests.

Unit runs need no lock. Check 6 GiB free memory first. No GameTest, runData, full build or server. Delete the
worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W176-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
