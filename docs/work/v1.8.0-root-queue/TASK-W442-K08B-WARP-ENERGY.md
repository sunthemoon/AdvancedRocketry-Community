# TASK W442: package K08b, warp-ready station energy on C6

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w442`, branch `codex/v1.8.0-w442` from the shared branch |
| Decision | Root decision R622 (QUEUE row W442), following R621 on [W440](records/W440.md) |
| Sources | [W440](records/W440.md) (K08's sources, the station energy store, the 200-tick fold, the 2,000,000 FE in-system and 8,000,000 FE interstellar requirements, the charged copies at about 500 FE); [K08's station driver](../../../scripts/campaign_prep/k08_stations.py) as the model; C6's generator and energy code under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/` (read only); C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | K08's sealed sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/` (`two/`, `charged/`, `target-removal/`, `cost-change/`; read only: work on a copy and check the parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08B/`: each variant as a source copy with a manifest and `SHA256SUMS.txt`, plus the qualification runs. Tracked: `scripts/campaign_prep/k08b_warp_energy.py`, `tests/test_campaign_prep_k08b.py`, and the record `docs/work/v1.8.0-root-queue/records/W442.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, and no hold longer than two hours. C6 runs, P9's night rounds and other workers share the lock. Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to the cells, to an existing script, to K06 or K08 sources, or to a budget, warp cost or generator rate. Starting any client. Editing NBT, SavedData or region files by hand or by script, or giving energy through a creative-only or test-only source that ordinary play cannot use. |

## What to prepare

1. **Read C6's energy routes.** List every C6 block or machine that produces Forge Energy in ordinary survival
   play, its rate per tick and its fuel or condition, and how a station's warp core receives and folds energy.
   Choose the route that reaches 2,000,000 FE folded on one station with the least server time, using only
   ordinary blocks placed and fuelled with registered commands (for example `setblock` and `item replace` or
   container insertion), and say why.
2. **Plan before running.** Compute the server time it needs per station. If one station needs more than two
   hours of server time with the chosen route, use several ordinary generators in parallel within the station's
   claimed region; if it still needs more than two hours, build only what fits, record the computed time and
   mark warp-ready energy NOT_COVERED as a fixture (the campaign then supplies energy live).
3. **Build.** On fresh copies of `two/` (both stations), `target-removal/` and `cost-change/` (the station that
   warps), raise each station's folded energy to at least the in-system requirement (and for one station of
   `cost-change/`, the interstellar requirement if it fits the two-hour rule), wait for the fold, remove the
   temporary generators and tickets, read back the folded balance, save, stop and copy out with a manifest
   (parent, commands, generator positions and counts, run times, balances), then restart a copy and record that
   the balance is unchanged.
4. **Map** each variant to the rows it serves (F02, F03, F05, F07) and list anything NOT_COVERED with the reason.
5. Run the new tests (Linux-safe) and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
