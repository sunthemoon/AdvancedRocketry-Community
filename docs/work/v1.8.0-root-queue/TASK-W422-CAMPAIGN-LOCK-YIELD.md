# TASK W422: let the integrated campaign runner yield the heavy-JVM lock between families

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w422`, branch `codex/v1.8.0-w422` from the shared branch |
| Decision | Root decision R577 (QUEUE row W422) |
| Sources | [run_v180_integrated_campaign.py](../../../scripts/run_v180_integrated_campaign.py) (`HeavyLock`, the family loop, `LOCK_LIMIT`), its tests under `tests/`, the lock protocol in [WORKER-RULES](WORKER-RULES.md); QUEUE row "C5 run" (R566), which records why workers could not run GameTests during a campaign |
| Record | `docs/work/v1.8.0-root-queue/records/W422.md` |
| Write scope | `scripts/run_v180_integrated_campaign.py` and its existing test file (or a new `tests/test_v180_integrated_campaign_yield.py`), the record. No other runner, no product code. |
| Not allowed | Any change to which families run, their order, their arguments or their oracles. Any JVM, Gradle or native run. |

## What to build

1. An option `--yield-seconds N` (default 0, which keeps today's behaviour exactly; at most 600). After a family
   releases the heavy-JVM lock and before the next family tries to take it, the runner waits N seconds, so that a
   worker polling the lock every 60 seconds can take it. The wait and its start and end times are recorded in the
   family's entry of the campaign JSON.
2. When the runner then finds the lock held by someone else, it waits for it as it does today (the three-hour limit
   and the stale-lock refusal stay unchanged), and records how long it waited and the owner line it saw.
3. The option is recorded in the plan and in each attempt's identity, so a reader knows the campaign yielded.
4. Unit tests with a fake lock and fake clock: default 0 never sleeps; N sleeps once per family boundary, not after
   the last family; a foreign owner taking the lock during the yield is waited for and recorded; the three-hour limit
   still applies; a stale lock is still refused. Python tests must pass on Linux too.
5. Run the new and existing tests of this runner, and `python -B scripts/validate_repository.py`, which must end with
   `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
