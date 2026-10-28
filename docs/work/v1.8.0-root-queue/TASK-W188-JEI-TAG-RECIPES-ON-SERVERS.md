# TASK W188: tag-based machine recipes in JEI on a dedicated server (W181-F1 and F2)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w188` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w188` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W188.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W188/` |
| Findings | the findings section of `records/W181.md` on `codex/v1.8.0-w181` (`b72f03975`); evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W181/attempt-4/` |
| Runner | `scripts/run_v180_compatibility_matrix.py` on `codex/v1.8.0-w181` (`25f0b6dd8`); merge that branch into yours first |

**Root decision R148.** W181's matrix found that a client connected to a dedicated server shows in JEI only the
classic machine recipes without an item tag: lathe 0 of 7, arc furnace 1 of 5, cutting 13 of 14, crystallizing 0 of 2,
chemical reacting 3 of 7, laser etching 0 of 2. The 18 rolling and 8 precision-assembler recipes appear, but each logs a
mod ERROR and a JEI "broken recipe" ERROR. Single-player and GameTest runs do not show it. The recipes sync their
authored JSON and expand tags at lookup (`ClassicMachineRecipe.resolveInputs`,
`BoundedItemIngredientCodec.resolveAlternatives`, which reads `BuiltInRegistries.ITEM.getTag`). The likely cause is that
the client looks them up before the server's tags are bound. This is a defect players see on every server with JEI.

## What to do

1. **Merge `codex/v1.8.0-w181` at `b72f03975`** into your branch, so the markers and the runner are present.
2. **Find the cause.** Reproduce it on a dedicated server with a client, as the matrix does. Confirm when JEI
   registers recipes relative to the client's recipe and tag updates, and what each lookup sees.
3. **Design note first,** covering:
   - where tag expansion happens for the server (machine lookup) and for the client (JEI and any mod screen);
   - how the client path sees the server's tags, for example by resolving on use, or by re-registering when tags
     arrive;
   - that the synced recipe data, its bounds and the server's lookup do not change. If the fix needs a packet or
     recipe-serializer format change, stop and report.
4. **Fix it,** with a test that fails first where the harness allows; otherwise the matrix cell is the regression.
5. **W181-F2, the runner rule (Root decision).** JEI 15.56 starts twice when the server has no JEI, and the first start
   logs `recipes=0` for every category. Adopt the v1.0 runner's rule: the last marker for each category after the
   connection must carry the expected count. Earlier markers may differ. Add a Python test for both a double start
   and a wrong final count.
6. **Run the matrix** under the heavy-JVM lock on a JAR built from your committed source: all four cells and the
   restart cycles. Reuse W181's staged asset cache (`attempt-4/asset-cache/`) and do the downloads outside the lock.

## Verification

- the new tests alone, failing first; the whole unit suite; the runner's Python tests;
- the full matrix once;
- `python -B scripts/check_client_imports.py` and `validate_repository.py --require-approved-identity`.

Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent read-only review
follows before merge.

## Not in this task

Real-client sessions (final-campaign evidence), recipe content changes, the data-pack findings W179-F4 and F5, ADRs,
status files and the ledger.
