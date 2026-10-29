# TASK W193: bounded classic-recipe refusals and vanished tags (W179-F4 and F5)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w193` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w193` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W193.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W193/` |
| Proposal | [records/W192.md](records/W192.md), with its probe and line map in `D:/GitHub/ARCE-Task-Evidence/root-queue/W192/` |
| Findings | W179-F4 and F5 in [records/W179.md](records/W179.md) |

**Root decision R161** adopts W192's recommendations. None of them changes a packet, the recipe-serializer format or
saved data; if the implementation would, stop and report.

## What to do

1. **F4, the bounded refusal path (W192's option F4-A).** In the mod's eleven recipe serializers:
   - check the size before copying the JSON tree, with the bounded walk the rolling serializer already uses, so an
     oversized file costs no full copy on the server thread;
   - return `null` for a refused recipe instead of throwing, so vanilla's recipe manager writes no per-file ERROR with
     a stack trace and the recipe is simply not registered, as today;
   - write one ERROR summary per reload, of at most 1,024 characters, with the count and the first few recipe IDs and
     reasons.

   Update W179's over-limit reload GameTest and its exact log expectations to the new counts. Every change to an
   expectation is named in the record with its reason; no other assertion changes.
2. **The docs/17 waiver for what the mod does not control.** Write a PROPOSED ADR under `docs/decisions/` for the
   entries vanilla and Forge write themselves (W192 counts 5 in W179's run), with its exact scope, W179's data, the
   owner, the reason, an expiry at the v1.9.0 Gate and the condition that ends it. Do not accept it; the owner decides.
3. **F5, a vanished tag.** In the shared ingredient codec (`BoundedItemIngredientCodec.resolveAlternatives`), treat a
   tag as absent when none of its items still count as members, which is the result after a restart. Add a test that
   reloads without the tag and fails first.
4. **W192's side note**, that vanilla's JSON parser keeps the last of two equal keys so the classic codec's duplicate
   check cannot fire for a data-pack file: record it, and leave the check in place.

## Verification

- the new tests alone, failing first; the whole unit suite;
- the data-pack reload batch and the classic recipe batches three times under the heavy-JVM lock;
- `runData` if generated data changes; `python -B scripts/check_client_imports.py` and
  `validate_repository.py --require-approved-identity`.

Root's hosted run on the pushed branch stands in for a complete local GameTest.

## Not in this task

Recipe content, the placeholder or separate-directory options (F4-B, F4-C), changing the registry's tags (F5-D), ADR
acceptance, status files and the ledger.
