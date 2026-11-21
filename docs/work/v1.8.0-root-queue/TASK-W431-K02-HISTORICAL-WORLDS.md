# TASK W431: package K02, the historical worlds and the v1.0 forced-stop runners on C6

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w431`, branch `codex/v1.8.0-w431` from the shared branch |
| Decision | Root decision R598 (QUEUE row W431) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K02, choices C3 and C7, rows A-3, B-1 and I-2; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI01 to FI04 and FI09 with sections 3.1 and 3.4; [W429](records/W429.md) and the JARs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/historical-jars/`; the step lists [A](records/CAMPAIGN-CHECKLISTS-A.md), [B](records/CAMPAIGN-CHECKLISTS-B.md) and [I](records/CAMPAIGN-CHECKLISTS-I.md); the scripts [v100_beta_world_fixture.py](../../../scripts/v100_beta_world_fixture.py), [run_v100_flight_forced_stop.py](../../../scripts/run_v100_flight_forced_stop.py), [run_v100_transaction_forced_stop.py](../../../scripts/run_v100_transaction_forced_stop.py) and [v170_world_fixture.py](../../../scripts/v170_world_fixture.py); C6's identity in QUEUE row "C6 run" (R581) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/`: each world as a source copy (never modified after it is made) with a manifest (how it was made, with which JAR and commit, the coordinates and states the step lists need, a file inventory and hashes) and `SHA256SUMS.txt`; small tracked changes to the forced-stop runners only if they need a C6 option, with tests; the record `docs/work/v1.8.0-root-queue/records/W431.md` |
| Lock | Every world build and runner qualification starts a server: take the heavy-JVM lock with the WORKER-RULES loop (C6 attempt 2 yields it for 120 seconds after each family), hold it only for one server run at a time. |
| Not allowed | Changing a retained source world, product code or a historical JAR. Editing earned balances, IDs or binding history in any world (C7). Starting any client. |

## What to build

1. **FI01, the v0.9 Beta world**: copy the retained archive the REL-1.0 migration report names, check its hash, and
   record the copy and the coordinates the rows need.
2. **FI02 and FI03, a normal v1.0 world and its in-flight variant**: build them on the v1.0.0 JAR with the existing
   fixture scripts, as played or scripted states the rows describe; label each as "built on JAR (hash)", and say plainly
   that they are reconstructed, not historically played (C7).
3. **FI04, the v1.3 world**, on the v1.3.0 JAR, and **FI09, the v1.7 world with every placeholder ID** (W62 item
   10.3), on the v1.7.0 JAR, with `v170_world_fixture.py`.
4. **The two v1.0 forced-stop runners retargeted to C6** (C3): run each once against C6 with its own baseline and
   oracle, record what each now checks, and keep adjacent-chunk arrival, seat selection and native expiry as separate
   observations; one without a deterministic control is recorded as NOT_COVERED with the reason.
5. For each world, load a copy once on C6 (the migration path the step lists name), record the result and the log
   excerpts, and leave the source copy unchanged.
6. Run any changed tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
