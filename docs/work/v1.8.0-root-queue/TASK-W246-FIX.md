# TASK W246-fix: the provenance records of the two rebound item models

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w246` (existing; branch `codex/v1.8.0-w246` at `b3bcde97b`, which is W246's `9abea95ad`, Root's applied drafts `8be926991` and Root's merge of the shared head `001a7cd3c`) |
| Failure | Sharded branch run 41312791314, build job, step "Audit the built artifact": `scripts/validate_v180_provenance.py` rule 4, "A target matches the latest record that names it", fails for `src/generated/v1.8/resources/assets/advancedrocketrycommunity/models/item/jackhammer.json` and `.../models/item/tracking_circuit.json`. The log is `D:/GitHub/ARCE-Task-Evidence/root-queue/W246/branch-run41312791314-build.log` |
| Task it answers | [TASK-W246](TASK-W246-FLAT-TEXTURES.md), Root decision R262 |
| Record | Append a "W246-fix" section to `docs/work/v1.8.0-root-queue/records/W246.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix/` |

**Root decision R267.** W246 rebound the two items to imported textures through their providers, which changed the
generated item models that earlier provenance records (for example `docs/provenance/v1.8.0-c18b-jackhammer.json`)
still pin. Record the change the way the validator expects a later record to supersede an earlier one: a new dated
record or revision that names each changed target with its new SHA-256 and says why. Never edit a sealed earlier
record. Check every other target W246's provider changes touched, not only these two.

## Steps

1. Run `python -B scripts/validate_v180_provenance.py` (with the arguments the CI build uses; read
   `.github/workflows/v180-development.yml`) on the branch and reproduce the two failures, keeping the log.
2. Add the superseding record or records, then rerun the same command until it passes, and the provenance Python tests.
3. Rerun runData once to confirm the generated files are unchanged, and the repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and the independent review of W246 follows.

## Not in this task

Any texture or model change, the asset plan, the ledger, status files and other batches.
