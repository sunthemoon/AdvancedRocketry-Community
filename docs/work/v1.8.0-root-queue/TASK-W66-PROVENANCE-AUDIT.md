# TASK W66: the v1.8 provenance audit of ADR-061 section 4.7 (W62 slice S2)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w66-provenance-audit` (from the shared branch) |
| Branch | `codex/v1.8.0-w66-provenance-audit` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W66.md` (new) |
| Specification | `records/W62.md` section 7, row S2, and the S2 rules under it |

Write `scripts/validate_v180_provenance.py` and its tests. Follow the five S2 rules in W62 section 7:

1. Every packaged `assets/` and `data/` entry comes from exactly one source root.
2. Every file in the tree or the jar whose SHA-256 is in the legacy manifest is a record target.
3. No QUARANTINED or REJECTED target is packaged.
4. A target is compared against the latest record that names it.
5. The shared v1.8 language files are checked by key, not by whole-file hash.

The script writes `provenance-index.md` and a JSON result.

- **Unit tests:** use a synthetic tree, jar and records, including a superseded target and a grown
  language file.
- **Real run:** on the shared tree and a built jar.
- **Workflow lines for Root:** the regression step, and `import_v180_assets.py verify` for K6 in
  governance.

## Commands

- The new Python tests (`python -B -m unittest tests.<module>` from the repository root, with `TEMP`
  and `TMP` set to `C:/Users/Administrator/AppData/Local/Temp`).
- One real run as the slice says, with its output in `D:/GitHub/ARCE-Task-Evidence/root-queue/W66/`.
- At the end, delete your generated `build/gametest/world`, native copies and old jars with a plain
  `rm -rf`, and make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths;
  Root deletes them.

## Not in this task

- Workflows, `build.gradle`, status files, the ledger and ADRs (Root). If the slice needs a workflow
  line, write the exact line in your record; Root adds it.
- Any production behaviour change. If a test finds a production defect, stop and report it with the
  evidence. The exception is a fix of a few obvious lines: give it its own commit and say so.
