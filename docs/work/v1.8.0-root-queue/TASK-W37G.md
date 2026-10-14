# TASK W37G: the two W38 player-fall failures on the W37 branch

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w37-sleep` (at `3764b2e6` plus this task) |
| Branch | `codex/v1.8.0-w37-sleep` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W37G.md` (new) |

## The problem

After W37F merged the shared branch (`36128831`), two W38 GameTests in batch `gravity_fall_players`
fail in every full run on this branch:

- `playerlandingonthemoonisscaledonceagainsttheoverworld`: "Moon player saw 0 fall events: []";
- `paddedbootsstillcancellowgravitylandings`: "Unprotected player saw 0 fall events: []".

The same tests pass on the shared branch: hosted run 38459697312 at `68dbd882`, and the W39 branch's
full local run on top of `f58f4c9f` (966 tests). So something W37 or W37F adds stops the player on
the Moon from posting `LivingFallEvent`. The logs are in `build/w37f/gametest-delivery-console.log`
(failures at line 6305) and `records/W37F.md`.

## What to do

1. Find the cause and state it with evidence: a code path, a `javap` citation or a reproduction.
   Candidates to rule in or out, not a conclusion:
   - a W37 listener (`SleepRespawnService`: player tick, level leave, respawn, clone or login)
     touching a live player who changes Level;
   - the access-transformed respawn fields or the `PlayerPersisted` backup;
   - a W37 GameTest leaving server-wide state behind (a gamerule such as `doImmediateRespawn`, a
     config switch, a fixture player), or building in the same Moon cells as `GravityFallFixtures`;
   - test batches now overlapping in time.
2. Fix the cause in W37 code or in W37's own tests. Do not change W38 code, tests, timeouts or
   expectations. If the fix must touch a W38 fixture, for example to isolate cells, stop and report
   first in the record.
3. If the cause is a production behaviour (a live player on an off-world Level is affected), add a
   W37 GameTest that would have caught it.
4. Run the full native suite twice, both under the lock, and the log checker on each. Both runs
   must pass with no required failure.

## Commands

- The whole unit suite once.
- `runGameTestServer` twice under the lock, then the log checker on each log.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- W37b (`natural=true`, D1 outcome 3).
- Any change to W37F's respawn design. An independent review of `3764b2e6` runs at the same time;
  Root forwards its findings separately.
- Status files, the ledger, ADRs and the risk register (Root).
