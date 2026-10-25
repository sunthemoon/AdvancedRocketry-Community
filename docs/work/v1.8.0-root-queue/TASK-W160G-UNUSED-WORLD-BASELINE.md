# TASK W160G: the unused-world roundtrip row

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md),
[TASK-W160F](TASK-W160F-REVIEW-FIXES-AND-NATIVE-ROWS.md), the W160F section of `records/W160.md` on the branch below
(its Appendix A update and counted-start table) and `D:/GitHub/ARCE-Task-Evidence/root-queue/W160F/BASELINE-BLOCKER.md`
first. You are a new session; W160F's session has grown too large to resume.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w160f` |
| Branch | `codex/v1.8.0-w160f`, continuing from `a5777504f` (local commits only; Root merges) |
| Record | append a "W160G" section to `docs/work/v1.8.0-root-queue/records/W160.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W160G/` (W160F's evidence stays as it is) |

## Root decision R113 (2026-10-26)

R111 said W152's jar has the same registries as the integrated source. That was wrong. No pre-W80 commit matches:
the sealed carrier block (`sealed_rocket_block`, `d9ec985cd`) and the monitoring station (`9377285d3`, `6d1b50baa`)
landed on opposite sides of the W159 merge `af7523215`. So `59f08e179` lacks the monitoring station, and the shared
commit before W159, `31e1410ba`, lacks the sealed carrier block.

Root keeps `59f08e179` as the baseline (its jar SHA-256 `a3341874…1a21` is reproduced). For the downgrade start only,
the native log audit declares exactly the Forge lines that the retained
`W160F/native-unused-root3/native/unused-3.log` shows for the two entries this baseline lacks:

- ERROR `Unidentified mapping from registry minecraft:block` listing only
  `advancedrocketrycommunity:rocket_monitoring_station`;
- ERROR `Unidentified mapping from registry minecraft:item` listing only the same ID;
- WARN `Forge Mod Loader detected missing registry entries.` with "There are 2 missing entries in this save.";
- WARN `Missing minecraft:item:` (and `Missing minecraft:block:` if Forge prints it) listing only that ID;
- the ERROR `There are unidentified mappings in this world - we are going to attempt to process anyway`, if it occurs.

Parse the listed IDs and require exactly that set, once each. Any other missing ID, a different count, or the
declaration matching in the first or second start fails the audit. Nothing else in the audit or the oracle changes:
the sidecar stays absent through all three starts, and the old jar starts, saves and stops cleanly. This is a
property of the chosen baseline, not of W80; say so in the record.

The counted-start limit for W160F and W160G together rises from 60 to 63, so this row gets one three-start roundtrip.
If a start fails, stop and report; do not retry without a new Root decision.

## Work

1. The audit declaration, with Python tests: the exact set passes; an extra missing ID, a missing line, a duplicate,
   another mod's ID, and the declaration appearing in start 1 or 2 each fail. Write the tests first and show that they
   fail on `a5777504f`.
2. The roundtrip `59f08e179` → integrated source → `59f08e179` on this branch's committed source.
3. Update the Appendix A mapping and the counted-start table in the record.

## Verification

- The new Python tests, then `tests/test_w160*.py`, `tests/test_run_w159_rejoin_cuts.py` and
  `tests/test_check_gametest_log.py`.
- The roundtrip row with its three counted starts.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W152I shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Remove
the export, jar copies and server copy afterwards. A fresh Codex session reviews W160F and W160G together.

## Not in this task

- Any other native row, registry change or GameTest manifest change.
- The sealed cross-guard (W152I) and governed records.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
