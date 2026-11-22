# TASK W441: package K09, the mission fixtures on C6

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w441`, branch `codex/v1.8.0-w441` from the shared branch |
| Decision | Root decision R619 (QUEUE row W441) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K09, rows G-1 to G-4, plan choices C9, C10, C24 and C25; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI26 and rows G01 to G07; [CAMPAIGN-CHECKLISTS-G](records/CAMPAIGN-CHECKLISTS-G.md); [ADR-051](../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md); the [v1.6 closure procedure](../v1.6.0-c9-closure/VERIFICATION.md) and its [archived native scripts](../v1.6.0-c9-closure/root-checks.zip) (historical cuts); [W436](records/W436.md) (FI07 and its NOT_COVERED list), [W438](records/W438.md) (C6's research accounts, the satellite store and the release-test hooks it used), [W403](records/W403.md) (K15's tick sampler); [ResourceMissionCommands](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/command/ResourceMissionCommands.java) and [ResourceMissionGameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/ResourceMissionGameTests.java) (read only); [W438's driver](../../../scripts/campaign_prep/k07_planets.py) as the closest model; the K08 driver on `codex/v1.8.0-w440` if it has merged by then; C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | The sealed FI07 (the v1.6 world) under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI07/` (read only: work on a copy and check the parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K09/`: each variant as a source copy (never modified after it is made) with a manifest and `SHA256SUMS.txt`, plus the qualification runs. Tracked: `scripts/campaign_prep/k09_missions.py`, optionally a separate load driver `scripts/campaign_prep/k09_mission_load.py`, `tests/test_campaign_prep_k09.py`, and the record `docs/work/v1.8.0-root-queue/records/W441.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next. C6 runs and other workers share the lock. Do not run the full GameTest suite or the full unit suite for this script-only task (R614); run the new tests and the validator. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to an existing script or to a budget or scheduler limit. Starting any client. Editing NBT, SavedData or region files by hand or by script, editing an earned research balance, or inventing mission dimensions or physical instance regions (ADR-051 defines logical records). |

## Root's choices for the open points (FI26)

1. **Host.** All variants are made on C6 from copies of FI07 with C6's registered commands and normal actions,
   including its release-test hooks behind `-Dadvancedrocketrycommunity.releaseTestHooks=true` where an existing
   runner or GameTest already uses them (name each in the manifest). Read from C6's code what a mission kind, an
   active mission, a deadline, expiry, catch-up, settlement and the reward write are, which store holds each, and
   C6's scheduler limits (the checklist's 32 completions and 64 inspections per tick), and record them.
2. **Mission sets (G-1, G01).** Three copies with more than 100, 500 and 1,000 active missions made through C6's
   own commands or hooks. If a per-owner or global limit stops a set, spread it across distinct placeholder owners
   within the limits; never raise a limit. If a size cannot be reached within C6's limits, record the largest
   reached and the limit that stopped it.
3. **Deadlines (G-2, C24).** Deadlines per kind chosen so at least one mission of each kind expires inside the
   campaign window (the four-hour soak plus one 30-minute stop), written into the manifest with the effective
   limits read back from C6. Wall-clock downtime is never counted as progress: the driver checks that.
4. **Load driver (G01, C10).** A driver that starts C6 on a copy of a mission set, runs C10's plan (a discarded
   two-minute warm-up, then ten minutes of samples, with the baseline run twice) through K15's existing tick
   sampler, plus a stop and restart, and writes the samples and receipts. The window lengths and the stop length
   are arguments with C10's and C24's values as defaults. Qualify it once per set with short windows passed
   explicitly and labelled as qualification, not as a measurement. Do not compare against docs/17 budgets here
   (they apply on REF).
5. **Offline catch-up (G02).** Qualify the bounded catch-up on the 100-plus copy with a short stop passed
   explicitly; the campaign runs the 30-minute stop.
6. **Same tick (G-3, C9).** Find what C6 already records about the server tick at which a settlement intent
   arrives (logs, receipts, release-test output). Write the observation that reads it. If C6 records no tick,
   say so, name the A1 test that covers the same-tick branch, and mark the V2 branch NOT_COVERED; K18 owns any
   bounded intent tool.
7. **Cut mapping (G-4, C25).** Map each historical cut in the v1.6 archived scripts to the C6 save boundary that
   matches it (asynchronous snapshot and durable epoch), naming the C6 code that defines the boundary. A cut with
   no matching boundary is NOT_COVERED with that reason. Qualify the forced stop before and after the reward
   write on C6 once each with an existing runner or a new one in your driver, on its own copy.
8. **Instances (G05).** Under ADR-051 instances are logical: check on a copy that a terminal mission leaves no
   chunk ticket and its logical record is reclaimed, and record how the inherited physical-chunk wording of G05
   is disposed of.
9. **Client rows.** G03's two players, G06 and G07 run with clients in the campaign: write the normal GUI and
   command controls each needs as scripted client steps with their preconditions; build no client tooling.
10. **Owners.** Owner UUIDs are parameters with disposable placeholder values until the owner gives accounts (O5).

## What to do

1. Write the driver or drivers and the tests (Linux-safe: governance runs them on Linux; no Windows-only calls in
   tests).
2. Build each variant on C6 under the lock, read back its state, save, stop, copy it out as a source copy with a
   manifest (parent source and its checksum list, C6 JAR hash and commit, every command, mission IDs and kinds,
   owners, deadlines, limits, readbacks, a file inventory and hashes), then load a copy once more on C6 and record
   that the readbacks match.
3. Map each variant to the rows it serves (G-1 to G-4 and G01 to G07) in the record, and list anything
   NOT_COVERED with the reason.
4. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
