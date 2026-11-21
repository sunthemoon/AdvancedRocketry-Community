# TASK W434: package K04, the three legal rocket scenes and the payload boundary scene on C6

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w434`, branch `codex/v1.8.0-w434` from the shared branch |
| Decision | Root decision R603 (QUEUE row W434) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K04 and rows A-5 and I-5; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI12 to FI14; the step lists [A](records/CAMPAIGN-CHECKLISTS-A.md) (A10, A12) and [I](records/CAMPAIGN-CHECKLISTS-I.md) (I09), and J08 in [J](records/CAMPAIGN-CHECKLISTS-J.md); [the cargo native benchmark](../../../scripts/run_v180_cargo_native_benchmark.py); the assembler GameTest fixtures under `src/main/java/.../gametest/`; [W427](records/W427.md) and [its generator](../../../scripts/campaign_prep/k16_core.py) as the model for a deterministic build on a C6 cell; C6's identity in QUEUE row "C6 run" (R581) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K04/`: each world as a source copy (never modified after it is made) with a manifest and `SHA256SUMS.txt`. Tracked: `scripts/campaign_prep/k04_rockets.py`, `tests/test_campaign_prep_k04.py`, and the record `docs/work/v1.8.0-root-queue/records/W434.md` |
| Lock | Every world build and readback starts a server: take the heavy-JVM lock with the WORKER-RULES loop (C6 attempt 2 yields it for 120 seconds after each family), hold it only for one server run at a time. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to an existing script or to a budget. Starting any client. Counting a rocket as legal on the generator's word alone. |

## Root's choices for the open points (FI12 to FI14)

1. **Builder.** A deterministic generator writes each layout as console commands and places it in a copy of a C6
   cell on the native server, as W427 did. The rocket is then assembled through C6's normal assembly route. A rocket
   is legal only when C6 accepts the assembly and its own snapshot reports the block count; the record quotes that
   readback. If the normal route needs a player, use the existing release-test hooks behind
   `-Dadvancedrocketrycommunity.releaseTestHooks=true` and name each one; if no existing route can assemble it
   natively, stop and record the blocker, because product code cannot change.
2. **Sizes.** Exactly 64, 512 and 2,048 blocks, counted the way C6's snapshot counts them (docs/17 section 4).
3. **Fuel.** Each rocket is fuelled to the state C6's own launch readiness check accepts, through the normal fuelling
   path where it can run natively; any fixture grant (fuel, energy or items) is disclosed in the manifest, as in W427.
4. **Views.** For each rocket: the seat positions, and two observer standpoints (x, y, z, yaw, pitch): one that frames
   the whole rocket at 1280 by 720 and GUI scale 2, and one shared overview for the multi-rocket copy.
5. **Multi-rocket copy.** One world with all three rockets assembled on separate pads, all visible from the overview.
6. **Payload boundary scene.** A separate world from the cargo benchmark's construction (2,048 blocks, 128 payloads,
   the 1 MiB bound), kept apart from the visual rockets and never used as one.
7. **Variants.** A first-use copy of each size (assembled, fuelled, never launched). A relaunch copy of each size
   where the step lists need one, made by one real flight and return on C6 through the route the native campaign
   families already drive; if a size cannot be flown natively, record that variant as NOT_COVERED with the reason.

## What to do

1. Write the generator and its tests (Linux-safe: governance runs them on Linux; no Windows-only calls in tests).
2. Build each world on C6 under the lock, read back the snapshot block count, seats, fuel and readiness, save, stop,
   and copy the world out as its source copy with a manifest (how it was made, the JAR hash and commit, every command,
   coordinates, grants, readbacks, a file inventory and hashes).
3. Load each source copy's own copy once on C6 and record that the readbacks match; leave the source copy unchanged.
4. Map each world to the step-list rows it serves (A10, A12, I09, J08, and K17's input) in the record.
5. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
