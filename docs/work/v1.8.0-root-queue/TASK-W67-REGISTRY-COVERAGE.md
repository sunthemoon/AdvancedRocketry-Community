# TASK W67: registry to resource coverage and unique IDs (W62 slice S4)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w67-registry-coverage` (from the shared branch) |
| Branch | `codex/v1.8.0-w67-registry-coverage` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W67.md` (new) |
| Specification | `records/W62.md` section 7, row S4, and the S4 checks under it |

Write `RegisteredContentResourcesTest` with the S4 checks of W62 section 7:

- IDs are unique in each registry, and no two IDs differ only by case.
- Every block has a blockstate and a loot table, or an exemption with a reason.
- Every item has a model and en_us and zh_cn names.
- Every sound event is in `sounds.json`.
- Every item, fluid and tag ID in a packaged recipe, loot table, advancement or tag resolves.

The test writes `build/reports/registered-content-resources.md`. Keep the exemption file short, with
a reason for each entry.

## Commands

- The new test class alone, then the whole unit suite once.
- At the end, delete your generated `build/gametest/world`, native copies and old jars with a plain
  `rm -rf`, and make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths;
  Root deletes them.

## Not in this task

- Workflows, `build.gradle`, status files, the ledger and ADRs (Root). If the slice needs a workflow
  line, write the exact line in your record; Root adds it.
- Any production behaviour change. If a test finds a production defect, stop and report it with the
  evidence. The exception is a fix of a few obvious lines: give it its own commit and say so.
