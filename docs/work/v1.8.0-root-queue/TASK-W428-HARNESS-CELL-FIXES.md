# TASK W428: fix the client harness's cell loading and JEI detection before the P9 dry run

Root: Claude. Date: 2026-11-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w428`, branch `codex/v1.8.0-w428` from the shared branch |
| Decision | Root decision R589 (QUEUE row W428) |
| Sources | W425's record on branch `codex/v1.8.0-w425` (`docs/work/v1.8.0-root-queue/records/W425.md`, section "Development corrections and remaining harness findings", and its `scripts/campaign_prep/cell_check.py`, which already parses `[[mods]]` correctly); [W399](records/W399.md) and `scripts/campaign_client/` (`cells.py`, `load_cell`, the JEI detection); [W400](records/W400.md) and the real `cell.json` files under `D:/GitHub/ARCE-Task-Evidence/root-queue/P3/cells/` |
| Record | `docs/work/v1.8.0-root-queue/records/W428.md` |
| Write scope | `scripts/campaign_client/` and its tests `tests/test_campaign_client_*.py`, the record. No other script; do not depend on W425's unmerged script (copy the parsing rule, not the file). |
| Not allowed | Any JVM, server or client start. |

## What to fix

1. **Cell schema.** `load_cell` rejects W400's manifests with "Unsupported cell schema", because W400 adds fields (`mods`
   with `server` and `client` lists of `{path, sha256}`, and `server_forge_args`) beyond the ones W399 required. Accept
   schema 1 with those fields, validate them (every listed file exists inside the cell and has the stated hash; the
   Forge argument file matches), and keep refusing unknown schema versions and malformed values. Load each of the four
   real `cell.json` files in a test that is skipped when the folder is absent, so CI on Linux still passes.
2. **JEI detection.** The check that a client's mods folder holds JEI exactly when the cell says so treats ARCE's own
   optional `[[dependencies.*]] modId="jei"` entry in `mods.toml` as an installed JEI mod. Detect an installed mod only
   from `[[mods]]` declarations of the JARs in the folder, as W425's driver does. Add unit tests with a synthetic ARCE JAR
   that declares the optional JEI dependency, with and without a real JEI JAR beside it.
3. Run the campaign client tests (`python -B -m unittest discover -s tests -p "test_campaign_client_*.py" -v`) and
   `python -B scripts/validate_repository.py`, which must end with `0 failed`. Python tests must pass on Linux too.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in one reviewable commit plus the record, in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
