# TASK W181: JEI per-category markers and a v1.8 Forge × JEI matrix (VDOC 10.4)

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w181` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w181` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W181.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W181/` |
| Scope source | section 3, B4c, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |

The v1.8 version document's section 10.4 asks for JEI evidence across the classic recipe categories. Today
`client/compat/jei/ArceJeiPlugin.java` logs only the electrolyzer's `status=registered recipes=1`.

## What to do

1. **One marker per category.** Make `ArceJeiPlugin` log one bounded INFO marker per registered category, with its
   recipe count, in the existing marker's format. Client-only logging; no gameplay or protocol change.
2. **A v1.8 matrix runner.** Copy `scripts/run_v100_compatibility_matrix.py` to
   `scripts/run_v180_compatibility_matrix.py`, with its Python tests. It must:
   - expect every classic category;
   - run with and without JEI;
   - cover Forge 47.4.10 and 47.4.23.
3. **Run it once locally** on a JAR built from your committed source, under the heavy-JVM lock, and record the result.
   Real-client checks remain final-campaign evidence (E) and are not part of this task.

## Verification

- The runner's Python tests.
- The unit suite.
- The local matrix run.
- `python -B scripts/validate_repository.py --require-approved-identity`.

## Not in this task

Gameplay changes, real-client sessions, ADRs, status files and the ledger.
