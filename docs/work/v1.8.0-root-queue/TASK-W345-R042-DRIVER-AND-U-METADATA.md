# TASK W345: W332's families in the campaign driver, the installed JAR, and U's metadata boundary

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w345`, branch `codex/v1.8.0-w345`, created by Root from W332's branch with W343's fixes (`6c9378840`) and this TASK merged |
| Decision | Root decision R416 (QUEUE row W345) |
| Read first | `records/W332.md`, `records/W332-DESIGN.md`, `records/W343.md` and `records/W341.md` in this worktree, and [TASK-W339](TASK-W339-DRIVER-W336-FAMILIES.md) for how families join the driver |
| Record | `docs/work/v1.8.0-root-queue/records/W345.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W345/` |

## Why

Candidate C2's rerun runs every family through `scripts/run_v180_integrated_campaign.py`. W332's R-042 runner
(`scripts/run_v180_r042_native.py`) is not in the driver yet. It also does not use W341's installed-JAR check, which
the driver's other runners now use. W343 restored the unknown-presence cases `U_BAY` and `U_LEDGER`, which reach the
refusal and recover with two clean starts, but they fail the check that no world byte changed. Vanilla rewrites
`level.dat` before the mod's refusal, and the only changed NBT field is `/Data/LastPlayed`, with `level.dat_old`
rotated.

**Root decision R416.** The U invariant protects player and world data. `LastPlayed` is a timestamp that vanilla
writes on every start attempt before any mod check runs, so it is not data the refusal can protect. The check becomes
exact instead of whole-file, and every other byte stays strict:
- every world file other than `level.dat` and `level.dat_old` is byte-identical to S0, with no file added or removed;
- `level.dat`, decoded, equals S0's decoded `level.dat` in every tag except `/Data/LastPlayed`, which is a long not
  smaller than S0's value;
- `level.dat_old` is either byte-identical to S0's `level.dat` (vanilla's rotation) or byte-identical to S0's
  `level.dat_old`; any other content fails.

This is a narrower statement of the same protection, not a looser one: any other changed tag or file still fails the
case. The runner records the decoded difference as evidence.

## What to do

1. **R416 in `run_u`**: replace the whole-world equality with the three rules above, using the repository's NBT
   reader (the one W343 used to decode the metadata). Keep the existing refusal, propagation, handle, fixture-hash
   and recovery checks unchanged. Negative tests with fakes: another `level.dat` tag changed, `LastPlayed` decreased,
   a non-metadata file changed, added or removed, and a `level.dat_old` with any other content; each must fail.
2. **Installed JAR**: give W332's runner W341's verification, as the bay, cargo, loader and W336 runners do. Install
   the session copy, verify its SHA-256 against the required value, and launch only that copy. This includes the U
   family's presence probe, which today reads the source JAR path.
3. **The driver**: add `N_BAY`, `L_LEDGER`, `P_SERVER`, `P_OWNER`, `U_BAY` and `U_LEDGER` to
   `scripts/run_v180_integrated_campaign.py` with the arguments W332's runner requires. Each planned command must pass
   the runner's own argument parser, as the existing parse tests check. W346 adds another family to the same file at
   the same time; Root resolves any merge conflict.
4. **Native smoke on a local JAR** of the U cases with both restarts. Also run W343's `negative-U` scratch control,
   which the runner must still reject. If a U case still fails, record exactly what differed and leave it
   NOT_COVERED.

## Verification

- The R-042 runner tests, the installed-candidate tests and the driver tests, with PYTHONPATH set to the tests
  directory and TEMP and TMP on D:.
- `python -B scripts/inventory_escrow_probe_cases.py --check` if you touch any case table it reads.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Heavy steps under the heavy-JVM lock (backslash lock path, as WORKER-RULES says), per step, released between steps.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code; the N, L and P oracles beyond the installed-JAR change; W346's family; the status file, QUEUE and
the risk register.
