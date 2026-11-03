# TASK W270: the planetary multi-leg GameTest's leg budget after a slow retirement proof

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w270`, branch `codex/v1.8.0-w270`, created by Root from the shared head |
| Subject | `PlanetaryWorldGameTests.earthMarsVenusEarthKeepsOneRocketAndExactFuelDebits` (batch `planetary_surface_flight`) |
| Evidence | Hosted run 41559401551, attempt 1, shard 2 at `a50576dca`: `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-fix/hosted-41559401551-attempt1-shard2.log` (SHA-256 `f8605c07ff7017f3a501edc43881dbe970c5396650e69c3fc319b18c04bc50ea`) |
| Record | `docs/work/v1.8.0-root-queue/records/W270.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W270/` |
| Decision | Root decision R300 (2026-11-03): a test-reliability fix, run while no content task is open |

## What the log shows

The test flies one rocket Earth to Mars to Venus to Earth. Each leg requests the next flight from the previous leg's
landed callback and then waits 270 paced ticks for the landing (`fly`, `awaitPaced(..., helper.getTick() + 270, ...)`).

- Leg 0 (Earth to Mars) prepared at 01:52:41 and landed at 01:52:53.
- At 01:52:54 the old transfer's retirement logged `ARCE_TRANSFER_RETIREMENT_WAIT site=BEFORE_REPLACE
  reason=SUPERSEDING_SAVE`; it logged `ARCE_TRANSFER_RETIREMENT_PROVED ... attempts=2` only at 01:53:03.
- Leg 1 (Mars to Venus) was prepared at 01:53:03, right after that proof, and completed its countdown at 01:53:06.
- At 01:53:07 the leg's 270 ticks ran out with the rocket in ASCENT six ticks after it started (`ARCE_PLANETARY_FAILURE
  leg=1 ... state=ASCENT ... phase=PREPARED`, test tick 534).

So most of leg 1's budget went to the retirement proof of leg 0's transfer, which a natural superseding save delayed
by about nine seconds on a loaded hosted shard. The same test passed in W269-fix's local full run and on every recent
shared hosted run. The status file already names this class: a natural supersession can exhaust a fixed budget (W94,
W94B).

## What to do

1. **Confirm the cause.** Find what the retirement proof waited for between the two log lines (`RocketOrdinaryRetirement`:
   `RETRY_TICKS`, `ATTEMPT_TICKS`, the SUPERSEDING_SAVE path with delay 1, the proof barrier and the save it waits
   on). Reproduce it deterministically in this test or a sibling: force one superseding save during leg 0's
   retirement proof and show the current fixture fails the same way.
2. **Fix the fixture, not production.** Each leg's landing budget must not include the previous transfer's
   retirement. For example, start the leg's 270 ticks when the new transfer is PREPARED (the journal record for this
   logical rocket with a new transfer ID), and bound the retirement wait separately with a budget derived from the
   retirement's own constants (attempts, attempt and retry ticks) plus a stated margin. Set the test's `timeoutTicks`
   from the same arithmetic and write the arithmetic in a comment. Keep every existing assertion (one logical rocket,
   landed state, typed target, exact fuel debit, unchanged blocks) and the failure-only observation.
3. **Look for the same pattern.** List other GameTests that chain flights of one logical rocket from a landed callback
   with a fixed per-leg budget (for example `MoonArrivalGameTests`). Fix those with the same pattern in the same way;
   list the rest with the reason they are not affected.

Production code, the retirement constants and every other test stay unchanged. Do not delete the test, loosen an
assertion or raise a budget without the arithmetic above (AGENTS.md section 5).

## Verification

The forced-supersession reproduction failing before and passing after; the batch `planetary_surface_flight` 20 times
in a row under load (state how you made the load); the GameTest classes you changed; the whole unit suite; the full
GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; the repository validator.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Production retirement behaviour, its constants and logs, other intermittent tests (W139, the escrow proof and relogin
timeouts in the status file), the status file, QUEUE and the ledger.
