# TASK W192: a design proposal for W179-F4 and F5 (data-pack error aggregation and vanished tags)

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This task writes
a proposal only; it changes no code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w192` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w192` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W192.md` (new; the proposal) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W192/` |
| Findings | W179-F4 and F5 in the "Findings for Root" section of [records/W179.md](records/W179.md) |
| Budget | `docs/17-V1PLUS-QUALITY-BUDGETS.md` section 5, the data-pack reload row ("定义总数、文件大小、错误聚合上限") |

**Root decision R159.** W179 found:
- **F4:** vanilla's recipe manager logs one ERROR with a stack trace for every refused recipe or tag file, so the count
  grows with the number of bad files, while docs/17 asks for an error aggregation cap. The mod's pattern catalogs
  already aggregate. Vanilla also parses a whole recipe file before the classic codec's 64 KiB text bound applies.
- **F5:** after a pack is deselected, `MappedRegistry.bindTags` keeps a vanished tag's named set with its old items.
  `BoundedItemIngredientCodec.resolveAlternatives` reads `Registry.getTag`, so a classic recipe naming that tag resolves
  to the old items. Vanilla's own ingredients behave the same way.

## What to do

1. **Trace both paths in the code**, for 1.20.1 Forge: where a classic recipe's JSON is read and refused, what vanilla
   logs and where, and how a tag reaches `resolveAlternatives` after a reload. Cite the classes and lines.
2. **Propose options for F4,** each with its cost, risk and test plan. Include at least:
   - a bounded refusal path in the classic serializers, so a refused classic recipe is counted and summarized in
     one bounded log entry per reload, rather than thrown to vanilla's per-file log. Say what a refused recipe becomes
     (no registered recipe, or a disabled placeholder), and what JEI and the recipe book would see;
   - a written waiver under docs/17 for vanilla's own per-file logging, with its scope and expiry.
   Say which part of the count the mod controls (its own serializers) and which it does not (vanilla recipes and tags).
3. **Propose options for F5:** whether the classic codec should treat a tag that the current reload did not define as
   absent, how it could know, and whether that should differ from vanilla's ingredients.
4. **Recommend one option for each,** with the data, protocol and save effects named. A packet, recipe-serializer
   format or saved-data change is allowed in a proposal but must be marked as such.

## Verification

`python -B scripts/validate_repository.py --require-approved-identity` and `git diff --check`. A short probe test kept
in the evidence folder is allowed; commit no code.

## Not in this task

Implementing either option, ADRs, status files and the ledger.
