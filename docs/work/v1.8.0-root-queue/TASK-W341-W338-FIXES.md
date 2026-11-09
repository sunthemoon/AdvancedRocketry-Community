# TASK W341: fix W338's findings 1 and 2 before C2

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w341`, branch `codex/v1.8.0-w341`, created by Root from the shared head |
| Decision | Root decision R412 (QUEUE row W341), from W338's review `D:/GitHub/ARCE-Task-Evidence/root-queue/W338/REVIEW.md` (read it first, with its `probes.py` and `probe-results.json`) |
| Record | `docs/work/v1.8.0-root-queue/records/W341.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W341/` |

## Why

C2's rerun will use these runners. W338 reproduced two false acceptances without changing production code: a
consumed three-bay group passes with a live survey receipt left behind, and a JAR that changes during the session copy
starts Java with different bytes while the run still records PASS.

## What to do

1. **Finding 1, the complete receipt set.** In `scripts/run_v180_bay_forced_stop.py` (`w331_settled`, `w331_check`
   and the stopped-world oracle), compare the whole expected receipt set for the logical rocket, not only the data
   member. A fully consumed LANDED or CONSUMED_REST outcome has no receipt for any member, in RAM and after each
   restart. INTERRUPTED allows only the specifically expected acknowledged REJECTED data receipt. Also assert the
   expected unlock and reservation release for a retired group. Add negative tests with a leftover REGISTERED, REPAIR
   and released survey member, and check whether any other W325 or W331 case accepts a member's receipt it should not.
2. **Finding 2, the installed JAR.** Copy the candidate into the owned session, verify that copy against
   `--jar-sha256`, start Java only on that verified file, and record its hash. Do this in every runner that the
   integrated campaign driver calls and that copies the candidate: the three bay runners and
   `bay_integrated_evidence.py`, the cargo wrapper's runners, and W336's runners (`scripts/w336_native.py`). Prefer
   one shared helper if the runners already share one. Add a regression, as W338's probe did, that replaces the input
   between pinning and copying and requires refusal before any process starts. Keep the clean-source and
   candidate-commit production-diff checks unchanged.
3. **No oracle loosening anywhere.** Existing tests stay unchanged unless a test pinned the old permissive behaviour;
   name any such test and why.

## Verification

- W338's `probes.py` scenarios, run against the fixed code, now refuse (record how you ran them).
- Every bay, cargo, W336 and campaign driver test passes, with TEMP and TMP on D:.
- `python -B scripts/inventory_escrow_probe_cases.py --check` and
  `python -B scripts/validate_repository.py --require-approved-identity` pass.
- No Java change is expected; if one is needed, stop and say why in the record.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

W338's finding 3 (B6.5's reconstruction, W342); W332's runner (it is under review in W340 and gets the same fix after
it merges); native runs; production code; the status file, QUEUE and the risk register.
