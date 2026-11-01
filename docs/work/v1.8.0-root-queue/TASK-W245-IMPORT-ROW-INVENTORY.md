# TASK W245: inventory and history audit of the remaining IMPORT rows (read-only)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This task changes
no file in the repository.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w245` (new, detached at the shared head named in your prompt; read-only) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md` with its machine-readable JSON and the audit outputs, each run under a distinct name |
| Inputs | `docs/work/v1.8.0-asset-plan.csv` (the rows whose handling is `IMPORT`), `docs/work/v1.8.0-content-ledger.csv`, [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) section 4, [ADR-066](../../decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md) section 7.3, the importer `tools/import/import_v180_assets.py`, and W229's history audit (`D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-history-audit.json`, its tool and its read-only upstream clone and client JARs under `W229/scratch/`) |

**Root decision R261.** After the K1 to K5 and etcher imports, 36 asset-plan rows are still `IMPORT` (block and item
textures, GUI textures, rocket motor, tank, saw blade, jetpack and laser gun models). Find out what each needs, so
Root can dispatch them in batches as K1 to K5 were.

1. **Inventory.** For every remaining `IMPORT` row: the files its pattern matches in the upstream tree at `c5cd5af6`,
   their SHA-256, the ledger units it serves and their disposition, and whether the repository already ships NEW or
   generated art or a JSON model for the same unit (so the import would replace, add beside, or be unnecessary).
2. **History audit.** Run W229's history audit, unchanged, on every matched file, with the current importer rules
   (the D8 upstream-URL rule and W244's identical-candidate rule if it has merged; otherwise say which files it would
   affect). Report each file as clear, REVIEW (with the trigger) or error.
3. **Consumers.** For each model row, which block, item or renderer would use it, whether it needs a formed or held
   projection or a design note, and whether its unit's current behaviour depends on the owner's pending decisions
   (D5, D7, D14, the sounds) or on bay admission.
4. **Batches.** Propose import batches in dependency order, each small enough for one worker session like K2 to K4,
   naming rows that should not be imported (with the reason) and any owner decision a batch would need.

Do not import, generate, edit the asset plan, the ledger, ADRs or any tracked file, and do not run Gradle, GameTests
or servers. Read-only Git, Python audit scripts and file reads only.

## Output format

The inventory table, the audit results, the proposed batches, and the SHA-256 of `INVENTORY.md` on the last line of
your answer.
