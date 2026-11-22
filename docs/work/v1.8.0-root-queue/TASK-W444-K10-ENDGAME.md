# TASK W444: package K10, the endgame fixtures on C6

Root: Claude. Date: 2026-11-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w444`, branch `codex/v1.8.0-w444` from the shared branch |
| Decision | Root decision R626 (QUEUE row W444) |
| Sources | [CAMPAIGN-PREP-BREAKDOWN](records/CAMPAIGN-PREP-BREAKDOWN.md) package K10, rows H-1 to H-4, plan choices C27 and C28; [CAMPAIGN-INPUTS-MANIFEST](records/CAMPAIGN-INPUTS-MANIFEST.md) FI27 and rows H01 to H07; [CAMPAIGN-CHECKLISTS-H](records/CAMPAIGN-CHECKLISTS-H.md); [ADR-054](../../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md); the [v1.7 closure package](../v1.7.0-c13-closure/root-checks.zip) (historical batch cuts); [W436](records/W436.md) (FI08 and its NOT_COVERED list), [W403](records/W403.md) (K15's samplers); [ReferenceLoadReleaseTestCommands](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/endgame/command/ReferenceLoadReleaseTestCommands.java) and [ReferenceLoadFixture](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/endgame/command/ReferenceLoadFixture.java) (read only); the K07 to K09 drivers under `scripts/campaign_prep/` as models; C6's identity in QUEUE row "C6 run" (R581) |
| Inputs | The sealed FI08 (the v1.7 world) under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K03/FI08/` (read only: work on a copy and check the parent's `SHA256SUMS.txt` first) |
| Outputs | Outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/fixtures/K10/`: each variant as a source copy with a manifest and `SHA256SUMS.txt`, plus the qualification runs. Tracked: `scripts/campaign_prep/k10_endgame.py`, optionally a separate load driver `scripts/campaign_prep/k10_endgame_load.py`, `tests/test_campaign_prep_k10.py`, and the record `docs/work/v1.8.0-root-queue/records/W444.md` |
| Lock | Every server run takes the heavy-JVM lock with the WORKER-RULES loop, one server per hold, at least 90 seconds after each release before the next, no hold over two hours. Detached launchers start with no console window (see WORKER-RULES). Run only the new tests and the validator (R614). |
| Not allowed | Any change under `src/`, to the cells, to an existing script, or to a budget or the ADR-054 load. Starting any client. Editing NBT, SavedData or region files by hand or by script. Lowering the reference load to make a run fit. |

## Root's choices for the open points (FI27)

1. **Host.** All variants are made on C6 from copies of FI08 with C6's registered commands, including the
   permission-2 reference-load hooks behind `-Dadvancedrocketrycommunity.releaseTestHooks=true`
   (`/arce endgame release-test refload build <x> <z>`, `drive on`, `status`); name each hook in the manifest.
2. **Coordinates and owners (H-1).** Choose `<x> <z>` for the reference-load build where every structure it
   places fits loaded, unclaimed ground in FI08 without replacing an inherited structure, and record the
   choice and the check. Bind its facility owners as placeholder parameters (O5) and list the ordinary screens
   each H row needs as scripted client steps.
3. **Batch cut and large range (H-2, H03).** Find in C6's code the seam inside a block-processing batch where a
   stop can land, pin it with an existing cut hook or a native stop at a recorded point on its own copy, and
   qualify the restart once; add the large-range variant the checklist names. Map each historical v1.7 cut to a
   C6 seam; a cut with no match is NOT_COVERED with that reason.
4. **Actors and throughput (H-4, C28).** Classify every actor in the reference load (the builder's fixture
   players are not real players; the campaign's two real connections come later, O7). Write a driver that runs the
   load with `drive on` through K15's samplers and reports achieved railgun throughput against ADR-054's required
   load, with C10's windows as defaults; qualify it once with short labelled windows. A shortfall is reported as
   not met; never lower the load. Windows figures are not REF evidence.
5. **H04 (C27).** Record it as NOT_COVERED in v1.8 under D9 (it moves to v1.9 per plan decision O6); do not
   build an adapter lane here.
6. **H05.** Write the soak's ticket and cache readbacks (before, during and after a long device run) as driver
   steps; qualify them once over a short run.
7. **Client rows.** H01, H02 and H06 run with clients in the campaign: write their controls as scripted client
   steps with preconditions; build no client tooling.

## What to do

1. Write the driver or drivers and the tests (Linux-safe: governance runs them on Linux).
2. Build each variant on C6 under the lock, read back its state, save, stop, copy it out with a manifest (parent and
   its checksum list, C6 JAR hash and commit, every command, coordinates, owners, device IDs, readbacks, file
   inventory and hashes), then load a copy once more on C6 and record that the readbacks match.
3. Map each variant to the rows it serves (H-1 to H-4, H01 to H07) in the record; list anything NOT_COVERED with
   the reason.
4. Run the new tests and `python -B scripts/validate_repository.py`, which must end with `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
