# TASK W190: the two native smokes that W140 left (W172's B12)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w190` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w190` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W190.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W190/` |
| Scope source | section 3, B12, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |
| Background | [records/W140.md](records/W140.md) |

**Root decision R157.** W140 gave the native harnesses the v1.8 build propagation, but not
`scripts/run_v140_celestial_schema_smoke.py` and `scripts/run_v180_guard_lifecycle_smoke.py`. The final S1 campaign
needs both.

## What to do

1. Give each script the v1.8 build propagation that W140 gave the other harnesses, in its own commit.
2. Keep their phase-specific refusal and reload contracts: count per process, with no blanket opt-in, as W140's
   record explains.
3. Add or update each script's Python tests; a test that pins the propagation fails first where it can.
4. Run each script once, under the heavy-JVM lock, on a JAR built from your committed source, and record the result
   (2 to 4 native starts).

## Verification

- the scripts' Python tests;
- the two native runs;
- `python -B scripts/validate_repository.py --require-approved-identity`.

No Java change is expected. If one is needed, stop and report.

## Not in this task

Other harnesses, gameplay or production code, ADRs, status files and the ledger.
