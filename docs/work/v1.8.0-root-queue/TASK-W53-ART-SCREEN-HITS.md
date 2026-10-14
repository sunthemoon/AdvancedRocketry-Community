# TASK W53: redraw the five v1.8 textures the art screen flags (parallel bug)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w53-art-screen` (from the shared branch) |
| Branch | `codex/v1.8.0-w53-art-screen` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W53.md` (new) |
| Source | The Repository governance run 38556570618 at `fc194a8e`, step 11: `tools/audit/screen_generated_art.py --root src/generated/v1.8/resources` with both pinned client JARs. Log in `D:/GitHub/ARCE-Task-Evidence/root-queue/ci/gov-38556570618/job.log` |
| Specification | ADR-063 section 9 and ADR-066 section 7.3 (screen NEW generated assets; never weaken a threshold or turn a HIT into CLEAR because the artist says it is original); ADR-061 |

The governance workflow had never run on the shared branch until Root enabled it. Its first run
flags five NEW textures out of 171:

| Result | Texture | Closest vanilla texture |
|---|---|---|
| HIT | `textures/block/station_light.png` | 1.20.1 `block/glow_item_frame.png` |
| SUSPECT | `textures/block/force_field_projector_side.png` | 1.12.2 `particle/footprint.png` |
| SUSPECT | `textures/block/solar_array_side.png` | 1.20.1 `block/barrel_bottom.png` |
| SUSPECT | `textures/block/solar_generator_side.png` | 1.20.1 `block/respawn_anchor_top_off.png` |
| SUSPECT | `textures/item/carbon_brick.png` | 1.20.1 `item/heart_of_the_sea.png` |

## What to do

1. For each texture, find the DataGen art provider and the provenance record that produced it, and
   state them in the record.
2. Redraw each texture in its provider: deterministic, code-drawn, with no vanilla or upstream
   pixels. Keep its size, its role in the models and its readability. Update the pre-authoring and
   provenance records, keeping the old entry and adding the new one, as the earlier correction
   files do.
3. Run the screen locally with both pinned client JARs (fetch them with
   `tools/audit/fetch_vanilla_clients.py`).
   - The whole `src/generated/v1.8/resources` root must come back with zero HIT and zero SUSPECT.
   - Do not change the tool, its thresholds or its inputs.
   - If a redraw keeps landing near a vanilla texture, change the design, not the screen.
4. Check whether older generated roots carry the same files. Shipped copies under other
   `src/generated/v*` roots count too: list them, and screen those roots if the workflow does not.
5. **Tests.** The existing resource and provenance tests stay green, and the art tests cover the
   changed files.

## Commands

- The screen command above, plus the art and provenance unit tests.
- `runData` under the lock, then `git diff --exit-code -- src/generated` after committing.
- `runGameTestServer` under the lock, then the log checker.
- Delete the client JARs and your generated `build/gametest/world` at the end with a plain `rm -rf`.
  If the tool refuses, record the paths and Root deletes them.

## Not in this task

- Any change to the screening tool, any threshold or any HIT appeal.
- Status files and the ledger (Root).
