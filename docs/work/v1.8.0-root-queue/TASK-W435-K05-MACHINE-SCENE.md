# TASK W435: package K05, the packaged many-machine scene on C6

Root: Claude. Date: 2026-11-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w435`, branch `codex/v1.8.0-w435` from the shared branch |
| Decision | Root decision R606 (QUEUE row W435) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K05, rows C-1 to C-3, C-5, C-6 and I-2, and plan choices C2, C11 to C14 and C30; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI10; the step list [C](records/CAMPAIGN-CHECKLISTS-C.md) (C01 to C04, C06, C08) and I02, I03, I06 and I08 in [I](records/CAMPAIGN-CHECKLISTS-I.md); [W70](records/W70.md) and [ClassicScaleGameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/runtime/ClassicScaleGameTests.java) (layout, recipes, unload witnesses and per-machine ledgers; read only); the external generators `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/scripts/gen*.py`; [W427's generator](../../../scripts/campaign_prep/k16_core.py) and W434's `scripts/campaign_prep/k04_rockets.py` (shared branch once merged, otherwise branch `codex/v1.8.0-w434`) as models for a deterministic console build on a C6 cell; C6's identity in QUEUE row "C6 run" (R581) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K05/`: each world as a source copy (never modified after it is made) with a manifest and `SHA256SUMS.txt`. Tracked: `scripts/campaign_prep/k05_machines.py`, `tests/test_campaign_prep_k05.py`, and the record `docs/work/v1.8.0-root-queue/records/W435.md` |
| Lock | Every world build and readback starts a server: take the heavy-JVM lock with the WORKER-RULES loop, hold it for one server run at a time, and wait at least 90 seconds after each release before taking it again. C6 attempt 2 and other workers share the lock. |
| Not allowed | Any change under `src/` (frozen at C6, R600), to the cells, to an existing script or to a budget. Starting any client. Exporting a GameTest structure or world as the scene. Counting a machine as formed or working on the generator's word alone. |

## Root's choices for the open points (FI10)

1. **Builder.** A deterministic generator writes each layout as console commands and places it in a disposable copy
   of the pinned C6 cell on the native server, as W427 and W434 did. It reproduces the scale test's layout pattern
   (16 controllers of each of the nine classic kinds, 144 in all, over 36 remote chunks) from its source code; it
   does not run the GameTest. A machine counts as formed and idle, or working, only from C6's own readback (block
   entity data or an existing release-test hook behind `-Dadvancedrocketrycommunity.releaseTestHooks=true`, each
   named in the record).
2. **Scene A, 100 idle machines (C11).** The first 100 positions of scene B's layout in its deterministic order,
   with the kinds taken round-robin in the scale test's order, so the first kind has 12 and the others 11. All
   idle, no inputs.
3. **Scene B, 144 controllers (C11).** The full layout, saved idle; then a copy where exactly half (72, the scale
   test's selection) run the scale test's recipes. Inputs and energy are fixture grants, disclosed in the manifest,
   and each active machine's progress is read back before saving.
4. **Unload and restart (C-2).** One copy of scene B where its remote chunks are unloaded and reloaded natively,
   with an unload witness as in W70 and the per-machine ledgers before and after, and a server restart readback.
5. **Damage (C12).** One copy per damage mode the test plan lists for machines (at least: breaking a structure
   block, breaking the controller, and an explosion), on one controller of each kind. The expectation is the
   conservation rule: the per-machine ledger before equals the ledger after plus the drops. Whether the machine
   refused the damage or was torn down is recorded per mode as an observation, and neither is a failure by itself.
6. **Permissions (C-3).** One copy with two owners. The owner UUIDs are parameters with disposable placeholder
   values until the owner gives accounts (owner item O5); the manifest says so.
7. **Long names and extreme numbers (C13).** For each field these machines show, read the limit C6's code declares,
   and place one value at the limit and one past it, which must be refused. A field with no declared limit is
   listed as such; no value is invented.
8. **Interaction distance (C-6).** Read the allowed distance from C6's code and record it, with the coordinates of
   one standpoint inside and one outside it. No intent tool is used (that is package K18).
9. **Views.** For each scene, two observer standpoints (x, y, z, yaw, pitch) that frame the machines at a 1280 by
   720 window at GUI scales 2 and 3 (C2), marked as geometric proposals not yet seen on a client.

## What to do

1. Write the generator and its tests (Linux-safe: governance runs them on Linux; no Windows-only calls in tests).
2. Build each world on C6 under the lock, read back formation, idle or progress state, ledgers and the variant's own
   observations, save, stop, and copy the world out as its source copy with a manifest (how it was made, the JAR
   hash and commit, every command, coordinates, grants, readbacks, a file inventory and hashes).
3. Load each source copy's own copy once on C6 and record that the readbacks match; leave the source copy unchanged.
4. Map each world to the step-list rows it serves (C01 to C04, C06, C08, I02, I03, I06, I08) in the record, and
   list any row this package cannot serve, with the reason.
5. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
