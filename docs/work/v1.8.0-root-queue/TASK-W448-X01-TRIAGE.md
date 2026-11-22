# TASK W448: triage C6's X01 crash-replay results and rerun the harness cases

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w448`, branch `codex/v1.8.0-w448` from the shared branch |
| Decision | Root decision R634 (QUEUE row W448; the C6 run row records X01 S3) |
| Sources | [W368-DESIGN](records/W368-DESIGN.md) (sections 2.3 to 2.5, the case table of section 3, the four pass outcomes, and question Q2 on late-loss states), [W368](records/W368.md), [W374](records/W374.md), [C6-SWITCH-REPORT](records/C6-SWITCH-REPORT.md) section 13 and the residuals R-035 names, the harness [run_v180_crash_replay.py](../../../scripts/run_v180_crash_replay.py) and [v180_crash_replay.py](../../../scripts/v180_crash_replay.py) with their tests, and the runners the cases call (B `run_v180_bay_forced_stop.py`, F `run_v180_cargo_cuts.py`, S `run_v180_cargo_s4_s5_cuts.py`, I `run_v180_item_cuts.py`, R `run_v180_registry_writer_cuts.py`); C6's code under `src/main/` (read only) |
| Inputs | C6's X01 S3, run 2026-11-23 02:40 to 05:38: `D:/GitHub/ARCE-Task-Evidence/x01-crash-replay/c8fa16e16be87554f26c402546733df6555d15e6/` (`MANIFEST.json`, `SUMMARY.md`, `SHA256SUMS.txt`, `cases/X01-NN/` with `states.json`, `results.jsonl`, `commands.json`, `capture-context.json`, `logs/` and `trace/`; read only). The exact command and pins are in `MANIFEST.json` and in Root's launcher `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/reruns/rerun_x02_x03_x01.py` (read only) |
| Outputs | Tracked: fixes under `scripts/` with tests under `tests/` where a cause is the harness, and the record `docs/work/v1.8.0-root-queue/records/W448.md`. Outside Git: the reruns under `D:/GitHub/ARCE-Task-Evidence/x01-crash-replay/c8fa16e16be87554f26c402546733df6555d15e6-triage/` |
| Lock | Native runs take the heavy-JVM lock as the harness already does (per case, at least 90 seconds between holds); detached launchers start with no console window (WORKER-RULES). Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to the S3 evidence. Loosening a pass outcome, an oracle, an allowed-marker list after the fact, an assertion or a timeout to turn a state into a pass. Editing a verdict by hand. Building intermediate generations or cross products that the design bounds out, unless one case's finding needs it (then say so and keep the bound of 16 states). |

## S3 in brief

12 cases: 3 PASS (X01-01, X01-04, X01-08) and 9 FAIL; report status `FAIL`. The failing states, from
`MANIFEST.json`:

- **X01-02** (`B3_MOVED`) and **X01-03** (`B8_MOVED`): states 07, 08 and 09, all `LATE-LOSS`, both starts each:
  `Existing runner did not pass: FAILED` (the log shows, for example, `fixture owner holds 0 satellites, expected 1`
  and `never settled ... hold REPAIR reason TRANSFER_AUTHORITY_CONFLICT`).
- **X01-05** (`FC_LOAD_L2_REPLACED`), **X01-06** (`FC_UNLOAD_L2_REPLACED`) and **X01-09** (`IC_UNLOAD_K9`): state 07
  `LATE-LOSS`: `Server exited with code 0 before 'ARCE_RELEASE_CARGO_RESULT cut=...'` on the old-side case.
- **X01-07** (`FC_LOAD_C6_JOINT_READBACK`): its only state, 01 `LATE-LOSS`, the same `exited with code 0` line.
- **X01-10** (`FF_N1`): states 01 to 05 (`S-OLD`, `S-EMPTY`, `S-PREFIX`, `S-TORN`, `S-STAGED`), all in-flight, not
  late loss: `Timed out waiting for server marker 'ARCE_RELEASE_CARGO_CUT_RESULT cut=FF_N1 '` on both starts.
- **X01-11** (`FS_O3J_AFTER`): state 12 `LATE-LOSS`: timed out waiting for the `FS_O3J_BEFORE` cut marker.
- **X01-12** (`R3`): no state was built: `Overlapping writes on a thread/store`.

## What to do

1. **Classify the cause of each failing state** from its logs, `states.json`, the harness, the runner and C6's code,
   as one of: harness (the builder, the state copy, the runner or its oracle is wrong for this state; for example a
   late-loss copy that no real crash could leave, a fixture expectation the rewound store cannot meet by design, or a
   marker the old-side runner never prints from that state), product (C6 loses or changes data without a refusal,
   crashes, or refuses in a way the design does not name, from a state a crash could leave), or residual (the Windows
   directory-flush late-loss residual that W368-DESIGN Q2 and R-035 already declare, with its reach stated). Quote the
   lines that decide it. Do not guess: if the evidence cannot decide, say what run would.
2. **X01-10 and X01-12 first.** They are not late-loss findings: say why `FF_N1`'s in-flight states never reach the
   cut marker, and whether the registry trace's overlapping writes are a real overlap in C6 or a recorder or builder
   fault.
3. **Fix the harness causes** in the scripts, with tests, without weakening any rule in step 1's sense. The harness
   refuses an existing evidence directory; add an opt-in for a rerun directory named
   `<candidate SHA>-triage` beside it that requires the original run's `MANIFEST.json` and leaves the default rule
   unchanged.
4. **Rerun** with `--case`, on the C6 JAR and the same source, template and pins as S3, every case whose cause was the
   harness, into the triage directory, and report each state's new result. For a product cause, write a native
   reproduction (the command line and the decisive lines) for Root; do not work around it.
5. **Record** a table of every failing state (cause, evidence, fix commit, rerun result), the S3 totals with the reruns
   applied, and the late-loss findings worded for C6-SWITCH-REPORT section 13 under the residual; run
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
