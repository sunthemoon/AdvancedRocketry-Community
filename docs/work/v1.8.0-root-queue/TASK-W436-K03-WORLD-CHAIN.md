# TASK W436: package K03, the v1.4 to v1.7 historical world chain on C6

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w436`, branch `codex/v1.8.0-w436` from the shared branch |
| Decision | Root decision R611 (QUEUE row W436) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K03, row E-10 and plan choice C7; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI05 to FI08 and H03, H05 to H07; [W429](records/W429.md) for the pinned host JARs; [W431](records/W431.md) for FI04 and its launcher pattern (external `package.py`, one lock per JVM, R607 liveness check); the historical procedures named in FI05 to FI08 ([v1.4 migration](../v1.4.0-mig-worlds/VERIFICATION.md) with [the migration runner](../../../scripts/run_v140_migration_smoke.py), [v1.5 migration](../v1.5.0-mig-native/VERIFICATION.md), [v1.6 closure](../v1.6.0-c9-closure/VERIFICATION.md), [v1.7 closure](../v1.7.0-c13-closure/VERIFICATION.md) and their root-checks archives, read only); C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | FI04 at `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K02/FI04/source-world/` (read only: always work on a copy); the host JARs under `D:/GitHub/ARCE-Task-Evidence/final-campaign/historical-jars/` (check each SHA-256 against W429 before use) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/`: FI05 to FI08, each as a source copy (never modified after it is made) with a manifest and `SHA256SUMS.txt`. Tracked: `scripts/campaign_prep/k03_chain.py`, `tests/test_campaign_prep_k03.py`, and the record `docs/work/v1.8.0-root-queue/records/W436.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, holds it for one server run at a time, and waits at least 90 seconds after each release before taking it again. C6 attempt 2 and other workers share the lock. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to an existing script or to a budget. Starting any client. Editing NBT, SavedData or region files by hand or by script. Substituting a newer JAR for a historical host. Removing registry data. Calling any world historically played. |

## Root's choices for the open points (K03)

1. **Chain.** Each world is made from the one before on its own pinned host: a copy of FI04 opened on the v1.4 host
   becomes FI05; a copy of FI05 on the v1.5 host becomes FI06; FI06 on v1.6 becomes FI07; FI07 on v1.7 becomes
   FI08. Each step first loads the copy once without changes and records that host's own migration log lines, then
   adds that version's content, saves and stops. Every world is labelled "reconstructed, not historically played"
   (C7), with the host's source commit and JAR SHA-256.
2. **Content, only through each host's own commands and normal game actions.** Use the commands that host
   registers (including its release-test hooks behind `-Dadvancedrocketrycommunity.releaseTestHooks=true` where the
   archived procedure used them; name each one in the manifest).
   - FI05 (v1.4): research and discovery progress as the archived v1.4 evidence describes (Mars and Venus
     researched) and a station request. This is the researched world; the undiscovered and pre-sky variants are
     K07's.
   - FI06 (v1.5): two stations with an owner and a member each, a charged core and one warp route, following the
     archived v1.5 procedure.
   - FI07 (v1.6): a few active resource missions with their deadlines and claim state, following the archived v1.6
     procedure; record the forced-chunk baseline.
   - FI08 (v1.7): endgame devices and their targets and owners as the archived v1.7 procedure builds them.
   Anything a host offers no command or normal action for is listed as NOT_COVERED with the reason; it is never
   written into the save directly.
3. **Owners.** Owner and member UUIDs are parameters with disposable placeholder values until the owner gives
   accounts (owner item O5); the manifest says so.
4. **Qualification.** Load a copy of each source copy once on C6, as W431 did for FI01 to FI09, and record the
   readbacks: migration log lines, research accounts, station IDs, rights and orbits, mission instances, device
   bindings, rockets and fuel, as each world has them. The source copy stays unchanged. A C6 load that refuses or
   quarantines data is recorded as an observation with its log lines, not hidden.
5. **Inherited archives.** Where an archived procedure names absolute Temp paths that no longer exist, use only
   what the archive retains and say so; do not invent the missing world.

## What to do

1. Write the chain driver and its tests (Linux-safe: governance runs them on Linux; no Windows-only calls in tests).
2. Build FI05 to FI08 in order under the lock, each with a manifest (how it was made, host JAR hash and commit,
   every command, coordinates, IDs, readbacks, a file inventory and hashes).
3. Qualify each world on C6 as in choice 4.
4. Map each world to the rows it serves (E-10, and the sources of K07 to K10) in the record, and list anything
   NOT_COVERED with the reason.
5. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
