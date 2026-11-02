# TASK W261-fix: the landing pad uses upstream's own tile (R289)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w261` (existing; branch `codex/v1.8.0-w261` at `49f7f778d`) |
| Task it answers | [TASK-W261](TASK-W261-P0-LANDING-PAD.md), Root decisions R287 and R289 |
| Record | Append a "W261-fix (R289)" section to `docs/work/v1.8.0-root-queue/records/W261.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W261-fix/` |

W261 found that upstream's landing pad (`blockstates/landingpad.json` at the pinned commit) is a plain `cube_all` block
with `advancedrocketry:blocks/rocketpad_` on every face. The connected tiles (`rocketpad_noedge` and the fourteen
others) belonged to the launch pad, which this mod redesigned into the rocket assembler with no pad block (ledger unit
`block:launchpad`, REDESIGNED). R287 assumed the landing pad itself was connected; that premise was wrong.

**Root decision R289**, under the owner's delegation, replaces R287's tile choice:

1. **The landing pad's own look.** Import `textures/blocks/rocketpad_.png` (byte copy) into the stitched
   `textures/block/` folder, with the `_classic` rule and its recorded reason if a name is taken, and bind it to every
   face through `cube_all`, as upstream does; the block item inherits it. Replace the `rocketpad_noedge` import: take it
   out of the plan, the provenance record and the tree (regenerate the pending record; it is unsealed and unmerged).
   The retained NEW `landing_pad.png` is listed with a proposed disposition, never deleted.
2. **The other fifteen tiles** (`rocketpad_noedge` and the fourteen connected variants) go to EXCLUDE in the
   asset-plan draft with the reason "launch pad connected texture; block:launchpad was redesigned into the rocket
   assembler with no pad block (R289)".
3. IDs, recipes, geometry, collision, landing selection and occupancy stay as they are.
4. Give Root fresh drafts based on your branch's base `7e9b9c15b` (asset plan, ledger note, governance pair and the
   VERIFICATION file under `docs/work/v1.8.0-w261-landing-pad/`); say in them that W260 changes the same files in
   parallel, as your first drafts did.

## Verification

- The landing-pad import test updated to the new tile and failing first on the old binding; the atlas, client and data
  unit tests and the whole unit suite; runData twice with identical output.
- The importer, content-ledger, provenance and resource Python tests; import verify on a clean export; the provenance
  validator.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine reviews.

## Not in this task

A connected pad look, the rocket assembler, other blocks, status files and the ledger beyond the drafts.
