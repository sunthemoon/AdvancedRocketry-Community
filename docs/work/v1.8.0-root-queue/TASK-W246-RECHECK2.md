# TASK W246-recheck2: independent read-only recheck of W246-fix3

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w246` at `01b306cbd`: W246-fix3's commits `7297d6812` (the test) and `01b306cbd` (record) on top of `bc39b1347`, which the first recheck reviewed. Review worktree `D:/GitHub/arce-v180-w246-review`, a detached checkout of `01b306cbd` |
| Finding | W246-R5 (and through it W246-R2) in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-recheck/RECHECK.md` (SHA-256 `d89e73d89dc13d0191d664bae6198f5166b8d60075f4241d9d81d09930b74ce9`); its probe scripts are in the same folder |
| Task it answers | [TASK-W246-FIX3](TASK-W246-FIX3.md), Root decision R270 |
| Record | The "W246-fix3" section of `docs/work/v1.8.0-root-queue/records/W246.md` on the subject branch; logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix3/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-recheck2/RECHECK.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W246-R5.** The atlas test now derives its expected models from every blockstate `model` value (variants,
   multipart, arrays), every registered item model and every parent chain, and fails when one of the mod's models is
   missing or another namespace's does not resolve. Rerun the first recheck's partial probes (one model removed, one
   model kept): both must now fail for that reason. Try at least one probe of your own.
2. **The one exception**, `minecraft:builtin/generated`: confirm from the 1.20.1 `ModelBakery` that it is synthesized
   and has no JSON file, and that no other `builtin/*` name passes.
3. **The shared registration capture.** `RegisteredContentResourcesTest.requiredItemModels()` reuses the existing
   capture; check that making `record()` run once and bootstrap itself leaves that test's six checks and their
   results unchanged, whichever class runs first.
4. **The fixture tests** (`PackagedBlockAtlasCoverageFixtureTest`) test the real checker, not a copy, and the missing
   cases fail on `bc39b1347`'s checker as the record says.
5. **Nothing weakened**: every earlier assertion of the atlas, registration and flat-import tests stays.

## Rerun

In your review worktree only: the three datagen classes alone and together in both orders, the client and data unit
tests, the whole unit suite and the repository validator. Do not run GameTests, servers or clients. Remove any
temporary folder you create under the review worktree's `build/` before you finish.

## Output format

A verdict for W246-R5 and W246-R2 (CLOSED or NOT CLOSED) and for the fix (APPROVE or BLOCK), new findings with an ID
(W246-R6, ...), the commands you ran and their results, and the SHA-256 of RECHECK.md on the last line.
