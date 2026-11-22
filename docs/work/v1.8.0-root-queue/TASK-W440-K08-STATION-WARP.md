# TASK W440: package K08, the station and warp fixtures on C6

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w440`, branch `codex/v1.8.0-w440` from the shared branch |
| Decision | Root decision R617 (QUEUE row W440) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K08, rows B-2 and F-1 to F-4, plan choices C8 and C10; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI25 and rows B02 and F01 to F07; [CAMPAIGN-CHECKLISTS-F](records/CAMPAIGN-CHECKLISTS-F.md); [W436](records/W436.md) (FI06 and its NOT_COVERED list), [W406](records/W406.md) (K06's station packs and the 48-character station name), [W403](records/W403.md) (K15's tick sampler, `scripts/campaign_observe/` and `scripts/run_campaign_observer.py`), [W438](records/W438.md) (the closest model); [StationCommands](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java), [StationManager](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/service/StationManager.java), [StationLimits](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/model/StationLimits.java) and [StationWarpGameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/StationWarpGameTests.java) (read only); the historical [station](../v1.5.0-station-native/VERIFICATION.md) and [warp](../v1.5.0-warp-native/VERIFICATION.md) native procedures; [W438's driver](../../../scripts/campaign_prep/k07_planets.py) and [W436's driver](../../../scripts/campaign_prep/k03_chain.py) as models; C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | The sealed FI06 (the v1.5 world) under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI06/` and K06's packs under `.../fixtures/K06/` (read only: work on a copy and check the parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K08/`: each variant as a source copy (never modified after it is made) with a manifest and `SHA256SUMS.txt`, plus the qualification runs. Tracked: `scripts/campaign_prep/k08_stations.py`, optionally a separate load driver `scripts/campaign_prep/k08_station_load.py`, `tests/test_campaign_prep_k08.py`, and the record `docs/work/v1.8.0-root-queue/records/W440.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next. C6 runs and other workers share the lock. Do not run the full GameTest suite or the full unit suite for this script-only task (R614); run the new tests and the validator. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to an existing script, to K06's packs or to a budget. Starting any client. Editing NBT, SavedData or region files by hand or by script, or raising a per-owner limit. |

## Root's choices for the open points (FI25)

1. **Host.** All variants are made on C6 from copies of FI06 with C6's registered commands and normal actions,
   including its release-test hooks behind `-Dadvancedrocketrycommunity.releaseTestHooks=true` where an existing
   runner or GameTest already uses them (name each in the manifest). Read from C6's code what the ordinary station
   route, membership, warp cores, warp energy and a pending warp are, which store holds each, and say so in the
   record.
2. **Two player stations (B-2, C8).** Two eligible owners each own one station. If C6's ordinary route needs a
   player, make the stations with the permission-2 admin create for the two owners, label them operator-created,
   and record the ordinary route as a scripted campaign client step with its exact preconditions. Read back each
   station's ID, owner, orbit, region, pad, cores and energy into the region, owner and energy manifest.
3. **Ten and 100 stations (F-1, F-2, C8).** Two further copies with 10 and 100 station records made with
   `/arce station admin create`, one distinct placeholder owner per station so no owner passes the per-owner
   limit; the manifest says they are operator-created.
4. **Load driver (F-2, C10).** A driver that starts C6 on a copy of the 10 or 100-station source, runs C10's
   plan (a discarded two-minute warm-up, then ten minutes of samples, with the baseline run twice) through K15's
   existing tick sampler, forces the station chunks to unload and reload, and writes the samples and receipts.
   The window lengths are arguments with C10's values as defaults. Qualify the driver once per count with a short
   window passed explicitly and labelled as qualification, not as a measurement; the campaign runs the full plan.
   Do not compare against docs/17 budgets here (they apply on REF).
5. **Warp cores and energy (F-1, W436's gaps).** Where C6 lets a station's warp cores be charged and a warp be
   started with commands, normal actions or an existing hook, build one copy with charged cores and one with a
   warp pending, and read both back. Otherwise record each as a scripted client step with its preconditions.
6. **Activation boundary (F-3).** With K06's `station_baseline.zip` enabled, a pending warp to `k06:warp_target`,
   then `station_target_removal.zip` (expected `WARP_TARGET_UNAVAILABLE` at the due commit) and separately
   `station_cost_change.zip`, each on its own copy. Record the outcome C6 actually gives at the boundary; if it
   differs from W406's expectation, keep it as a finding and do not change the pack or the expectation.
7. **IDs and actions (F-4, F06).** Bind the exact station IDs to the owner, member and non-member actions
   (invite, accept, decline, remove, warp request, admin delete with literal `confirm`): run the ones the console
   can run and list the player-only ones as scripted client steps with the station ID and the actor.
8. **Docked rocket (F04).** If C6 can dock a rocket at a station with commands or an existing hook, add one copy
   with a docked rocket before a warp; otherwise record it as NOT_COVERED with the reason.
9. **Owners.** Owner UUIDs are parameters with disposable placeholder values until the owner gives accounts (O5).
10. **Out of scope.** Client views and V1 or V2 rows (F02, F03, F06 and F07 run with clients in the campaign),
    the packs themselves (K06), packet and pending-menu tools (K18), and the combined reference world (K17).

## What to do

1. Write the driver or drivers and the tests (Linux-safe: governance runs them on Linux; no Windows-only calls in
   tests).
2. Build each variant on C6 under the lock, read back its state, save, stop, copy it out as a source copy with a
   manifest (parent source and its checksum list, C6 JAR hash and commit, every command, station IDs, owners,
   orbits, regions, cores, energy, readbacks, a file inventory and hashes), then load a copy once more on C6 and
   record that the readbacks match.
3. Map each variant to the rows it serves (B-2, F-1 to F-4 and F01 to F07) in the record, and list anything
   NOT_COVERED with the reason.
4. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
