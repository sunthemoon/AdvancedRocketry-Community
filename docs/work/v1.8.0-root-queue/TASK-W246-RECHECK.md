# TASK W246-recheck: independent read-only recheck of W246-fix2

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w246` at `bc39b1347`: W246-fix2's commits `c2c5974fc` (the move) to `aa1b40af9` (record) on top of the reviewed `e495440bb`, Root's `0b144a148` applying the VERIFICATION draft, and Root's merge of the shared head `3f56dc17d`. Review worktree `D:/GitHub/arce-v180-w246-review`, which Root moves to a detached checkout of `bc39b1347` |
| Findings | W246-R1 to R4 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-review/REVIEW.md` (SHA-256 `91b384f34f209c88594e7673931704bb4ff6c188b1f4a4cec33a59325b4ab274`), with its `atlas_coverage.py` |
| Task it answers | [TASK-W246-FIX2](TASK-W246-FIX2.md), Root decision R268 |
| Record | The "W246-fix2" section of `docs/work/v1.8.0-root-queue/records/W246.md` on the subject branch; logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix2/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-recheck/RECHECK.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W246-R1.** The sixteen imports now lie in `textures/block/` and `textures/item/` with their upstream bytes; the
   three `*_classic.png` names collide with nothing and the retained NEW art is untouched; the nine providers and the
   24 generated models name the new sprites and change nothing else; import verify passes. Rerun the review's
   `atlas_coverage.py` on a JAR built from `bc39b1347`: no uncovered sprite.
2. **W246-R2.** `PackagedBlockAtlasCoverageTest` fails on `e495440bb` with the test added and passes on `bc39b1347`.
   Check that it cannot pass on an empty or partial model set, that its atlas sources match the pinned vanilla 1.20.1
   stack and Forge's `forge:white`, and that an unknown or `filter` source fails rather than passes.
3. **Records regenerated in place.** Both W246 provenance records were pending and unsealed, so R268 allowed this.
   Confirm that the import record changed only in the target paths, their transformation text, `plan_sha256` and the
   pending review's digest; that the R267 record still supersedes exactly the two pinned item models with their new
   hashes; and that no earlier or sealed record changed.
4. **W246-R3 and R4.** The applied VERIFICATION file (`0b144a148`) is true to the evidence; the seven provider
   Javadocs no longer call the retained art a fallback.
5. **Nothing weakened.** `V180FlatTextureImportTest` keeps every earlier assertion; no other test, budget or checker
   changed.
6. **The sharded branch run.** Run 41324835541 on `bc39b1347` passed every test and failed only shard 2's host-lag
   ceiling (10 entries against 8; the log is `D:/GitHub/ARCE-Task-Evidence/root-queue/W246-fix2/branch-run41324835541-shard2.log`).
   W247 addresses that stall, and Root re-runs the branch after W247 merges. Confirm from the log that no test failed.

## Rerun

In your review worktree only: `PackagedBlockAtlasCoverageTest` at both commits, the client and data unit tests, the
importer, content-ledger and provenance Python tests, import verify, the provenance validator with the CI build's
arguments, and the repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict for each finding (CLOSED or NOT CLOSED) and for the fix (APPROVE or BLOCK), new findings with an ID
(W246-R5, ...), the commands you ran and their results, and the SHA-256 of RECHECK.md on the last line.
