# TASK W450: compare the satellite store's faulted and retried candidates in X02

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w450`, branch `codex/v1.8.0-w450` from the shared branch |
| Decision | Root decision R636 (QUEUE row W450), following [W446](records/W446.md) section U1 |
| Sources | [W446](records/W446.md) (U1: the two `SATELLITE_MISSIONS` cases, their injector lines, hashes and the deciding run it describes), [W379](records/W379.md) (the classification rules), [W368-DESIGN](records/W368-DESIGN.md), `scripts/run_v180_storage_faults.py` (model v4), the registry runner `scripts/run_v180_registry_writer_cuts.py`, the crash-replay recorder in `scripts/v180_crash_replay.py` and `scripts/run_v180_crash_replay.py`; C6's `SatelliteRegistryWriter`, `RegistryEpochs` and `SatelliteEpochRowGameTests` under `src/` (read only) |
| Inputs | W446's evidence under `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/x02-s5-triage/` and attempt 3 under `.../C6/x02-s5-attempt3/` (read only); the C6 JAR, template and pins exactly as W446's `v3-invocation.json` records them |
| Outputs | Tracked: an opt-in harness change with tests if one is needed, and the record `docs/work/v1.8.0-root-queue/records/W450.md`. Outside Git: new runs under `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/x02-s5-satellite/` |
| Lock | Native runs take the heavy-JVM lock as the runner already does (per case, at least 90 seconds between holds); detached launchers start with no console window (WORKER-RULES). Run the new and changed tests and the validator, not the full suites (R614). |
| Not allowed | Any change under `src/` (frozen at C6, R600) or to earlier evidence. Replacing the exact-byte retry rule with a normalized comparison, or changing any classification rule, marker, oracle, count or timeout. Editing a verdict by hand. Changing a default of the runners. |

## Background

`X02-SATELLITE_MISSIONS-READ_ERROR-1` and `-READ_CORRUPT-1` on cut `R3` stay `NOT_CAUGHT`: the injector reports the
fault and C6 holds (`ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=UNKNOWN` or `outcome=FAILED`, retained dirty
state), both healthy restarts pass, but the next successful write is not byte-identical to the faulted candidate.
C6's design takes a fresh capture after UNKNOWN or FAILED with a new epoch, so different bytes may be correct. Hashes
alone cannot tell an allowed epoch or clock change from a change in missions, identities, resources or proofs.

## What to do

1. Find a way to keep the bytes of the faulted candidate and of the next successful candidate for both cases without
   touching `src/`: for example the crash-replay recorder alongside the injector, or another harness-only capture. If
   the harness needs a change, make it opt-in with Linux-safe tests and leave every default unchanged.
2. Rerun both cases with `--case` on the C6 JAR, template and pins of W446's v3 run into `x02-s5-satellite/`, keeping
   the runner's own classification. Check that the kept candidates' SHA-256 values match the injector lines.
3. Decode both candidates of each case and compare the whole bounded roots field by field. Report every difference.
   Classify each case: oracle-contract (only fields that C6's documented UNKNOWN or FAILED retry contract changes,
   such as the epoch or a capture clock, differ; name the code that changes each one) or product (any change in a
   mission, identity, resource, proof or other state the retry must carry over). Quote the code and lines.
4. **Record** the comparison tables, commands, hashes and your classification, and run
   `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
