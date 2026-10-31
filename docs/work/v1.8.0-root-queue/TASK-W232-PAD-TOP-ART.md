# TASK W232: redraw the gas charge pad's top texture so the generated-art screen is CLEAR

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w232` (new; branch `codex/v1.8.0-w232` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W232.md` |
| Failure | Governance runs 41217417397 (`f3a99180e`) and 41218330314 (`965352ecd`), step `screen_generated_art.py`: `{"CLEAR": 201, "SUSPECT": 1}`, `SUSPECT: assets/advancedrocketrycommunity/textures/block/gas_charge_pad_top.png ~ 1.12.2:assets/minecraft/textures/particle/footprint.png`. Root reproduced it locally with the vanilla clients in `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/scratch/clients/` |
| Sources | `V180GasChargePadData` and `V180MaterialArt`; the NEW-art record `docs/provenance/v1.8.0-gas-charge-pad-new-art.json`; `tools/audit/screen_generated_art.py`; the governance workflow step that runs it |

**Root decision R233.** W226's merge turned the shared governance red. The worker branches' regression workflow does
not run this screen, so W226's review and branch run never saw it.

1. Find out why the screen matches the top texture's diamond to vanilla's footprint particle, and say whether the
   resemblance is real or an artefact of the screen's measure.
2. Redraw the top texture so that the screen reports CLEAR against both vanilla clients. Keep the art original and
   keep the side texture unless it must change too. Do not change the screen, its thresholds or the workflow.
3. Regenerate it with `runData` under the heavy-JVM lock. The generated diff must contain only the changed texture.
4. Update the NEW-art record with a dated revision: the new grid, the new byte count and SHA-256, the reason, and
   the review still PENDING_HUMAN_REVIEW. Do not rewrite the original entry's history.
5. Add the screen to your verification, and say in the record how future NEW art should run it before merging
   (one sentence that Root can add to WORKER-RULES).

## Verification

- `screen_generated_art.py` exits 0 with every texture CLEAR.
- `runData` produces no diff after the commit.
- The repository validator and the content-ledger validator pass.
- `GasChargePadDataTest` and the gas charge pad GameTests pass.

## Not in this task

Other textures, the screen tool, the workflow, status files and the ledger.
