# TASK W339: add W336's families to the integrated campaign driver

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w339`, branch `codex/v1.8.0-w339`, created by Root from the shared head |
| Decision | Root decision R410 (QUEUE row W339), for the C2 rerun under R386 |
| Builds on | W337's `scripts/run_v180_integrated_campaign.py` ([W337](records/W337.md)) and W336's runners `scripts/run_v180_w336_timing.py`, `scripts/run_v180_w336_saturation.py` and `scripts/run_v180_w336_downgrade.py` ([W336](records/W336.md), with its cut list [W336-DESIGN](records/W336-DESIGN.md)) |
| Record | `docs/work/v1.8.0-root-queue/records/W339.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W339/` |

## Why

W336's observations for R-043 and R-044 must be repeated on candidate C2 with the rest of the matrix. W336's runners
take one case or device kind per call, accept no `--stop-on-failure`, mark local runs with `--smoke`, and the downgrade
needs W176's old JAR, which is not the old JAR that the G2 reuse family needs.

## What to do

1. **Families, after the existing 34, in this order:**
   - one timing family per W336 timing case, with the proof-turn case twice (`--claims 2` and `--claims 3`), using the
     runner's defaults for every window and timeout;
   - one saturation family per device kind (`--kind`, all six: the bay, the four cargo kinds and the fuel control);
   - one downgrade family, which needs W176's old JAR and its SHA-256 as separate driver arguments (for example
     `--downgrade-old-jar` and `--downgrade-old-jar-sha256`), verified before the lock like the candidate JAR. The plan
     lists them as requirements when the family is selected and the arguments are missing, as G2 does.
2. **Commands:** never pass `--smoke` (candidate runs are not smokes); do not pass `--stop-on-failure` to a runner that
   does not accept it; keep every other argument the runner requires. A failed timing case stops the later timing
   families, as for other runners. The downgrade family's whole-world verdict is FAILED by design (W176): record the
   runner's exit code and status as they are, and never convert them to a pass. It blocks no other runner.
3. **A parse test for every family:** for each planned command, including the existing 34, build the target runner's
   own argument parser and show that it accepts the command's arguments. This catches any argument the driver passes
   that a runner does not accept.
4. **Coverage test:** every W336 timing case and every saturation kind appears exactly once (the proof-turn case twice),
   and the downgrade family appears once.
5. **A dry run** with placeholder JARs, saved under the logs directory. No native run.

## Verification

- The new tests and every existing driver, bay, cargo and W336 test pass, unchanged except where a driver test pins the
  family count; say which pins changed and why.
- `python -B scripts/inventory_escrow_probe_cases.py --check` and
  `python -B scripts/validate_repository.py --require-approved-identity` pass.
- Run Python tests with TEMP and TMP set to a directory on D: (the short `ADMINI~1` TEMP spelling breaks older tests).

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

W332's families (added after W332 merges), any runner or oracle change, native runs, building C2, production code, the
status file, QUEUE and the risk register.
