# TASK W438: package K07, the planetary world variants on C6

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w438`, branch `codex/v1.8.0-w438` from the shared branch |
| Decision | Root decision R614 (QUEUE row W438) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K07 and rows E-1, E-3, E-5, E-9 and E-10, plan choices C7, C8, C20 and C23; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI23 and FI24; the step list E01 to E11 in the campaign checklists; [W436](records/W436.md) (FI05 to FI08) and [W431](records/W431.md) (FI04); the close native runners [discovery cuts](../../../scripts/run_v140_discovery_cut_smoke.py) and [planetary worlds](../../../scripts/run_v140_planetary_worlds_smoke.py), [StationCommands](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/command/StationCommands.java) and [PlanetaryExposureGameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/PlanetaryExposureGameTests.java) (read only); [W435's generator](../../../scripts/campaign_prep/k05_machines.py) and [W436's driver](../../../scripts/campaign_prep/k03_chain.py) as models; C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | The sealed K02 and K03 sources under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/` and `.../K03/` (read only: always work on a copy and check the parent's `SHA256SUMS.txt` first); K06's packs under `.../fixtures/K06/` |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K07/`: each variant as a source copy (never modified after it is made) with a manifest and `SHA256SUMS.txt`. Tracked: `scripts/campaign_prep/k07_planets.py`, `tests/test_campaign_prep_k07.py`, and the record `docs/work/v1.8.0-root-queue/records/W438.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next. C6 attempt 2 and other workers share the lock. Do not run the full GameTest suite or the full unit suite for this script-only task; run the new tests and the validator. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to an existing script or to a budget. Starting any client. Editing NBT, SavedData or region files by hand or by script, editing or resetting an earned research balance, erasing an ID, or merging parts of a backup (C7, C23). |

## Root's choices for the open points (FI23, FI24)

1. **Host.** All variants are made on C6 from copies of the K02 and K03 sources, using only C6's registered commands
   and normal actions, including its release-test hooks behind `-Dadvancedrocketrycommunity.releaseTestHooks=true`
   where an existing runner uses them (name each in the manifest). Read from C6's code what "undiscovered",
   "discovered", "paid-pending" and "blocked" mean (which account or owner they belong to and which store holds
   them) and say so in the record.
2. **Discovered and undiscovered (E-3).** The discovered copy is a C6 load of FI05, whose owner earned Mars and Venus
   on the v1.4 host. The undiscovered copy uses the same world with a second, disposable owner who has earned
   nothing, so no balance is edited; if C6 keeps research per world rather than per owner, use FI04 instead (Mars
   and Venus were first claimed on v1.4) and say which.
3. **Private station (E-3).** One copy where the eligible owner has a private station made through C6's station
   commands, with its ID, owner, orbit and rights read back.
4. **Paid-pending and blocked (E-9).** Made from earned progress through normal commands: one copy with a purchase
   or binding that is paid and still pending, and one where a binding is refused for a reason C6 defines. If a
   state can only be reached with a player action, record it as a scripted client step for the campaign with the
   exact preconditions, not as built.
5. **Hazard sites (E-5).** One copy with isolated sites (one hazard each) and combined sites, each with coordinates,
   the protective equipment to test with, and C6's default modifiers read from its code and recorded (C20).
6. **Pre-sky copy (E-10).** FI04 is the pre-sky source: reconstructed on the v1.3 host, before the v1.4 sky feature,
   and labelled honestly. Record what a C6 copy of it shows on first load for sky, catalog and bodies; the source
   stays unchanged. No other pre-sky world is invented.
7. **Owners.** Owner UUIDs are parameters with disposable placeholder values until the owner gives accounts (O5).
8. **Out of scope.** The sky packs and catalogs (K06 has them), the packet and pending-menu controls (K18), and client
   views.

## What to do

1. Write the generator and its tests (Linux-safe: governance runs them on Linux; no Windows-only calls in tests).
2. Build each variant on C6 under the lock, read back its state, save, stop, copy it out as a source copy with a
   manifest (parent source and its checksum list, C6 JAR hash and commit, every command, coordinates, IDs, owners,
   readbacks, a file inventory and hashes), then load a copy once more on C6 and record that the readbacks match.
3. Map each variant to the rows it serves (E-1, E-3, E-5, E-9, E-10) in the record, and list anything NOT_COVERED
   with the reason.
4. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
