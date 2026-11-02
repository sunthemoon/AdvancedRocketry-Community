# TASK W258-review: independent read-only review of import batch P3 (the nuclear motor model)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w258` at `5875a1f66`: W258's commits `ba8459125` (design note) to `becc0be4f` (record) on top of `a3192d695`, Root's `4fefd000b` applying W258's central patch, and Root's merge of the shared head `0bde30145` (with W255 and W256), which resolved the asset plan, the occlusion GameTest, `.gitattributes` and governance by hand. Review worktree `D:/GitHub/arce-v180-w258-review`, a detached checkout of `5875a1f66` that Root creates |
| Task it answers | [TASK-W258](TASK-W258-P3-NUCLEAR-MOTOR.md), Root decision R284; P1's contract in [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [TASK-W249-FIX](TASK-W249-FIX.md) |
| Record | `docs/work/v1.8.0-root-queue/records/W258.md` and `records/W258-design.md` on the subject branch; evidence and drafts in `D:/GitHub/ARCE-Task-Evidence/root-queue/W258/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W258-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Imports.** The three files are byte copies of history-clear upstream files apart from the recorded rewrites;
   the texture is the MTL's real `nuclearengine` image; the plan is LF and import verify passes in a fresh clone.
2. **The six facings.** The blockstate rotates the one baked model for every existing `FACING` value as upstream's
   blockstate did, each pinned by a test; the item display transforms; no new property, state, block entity, packet
   or renderer; core adjacency, nozzle orientation and thrust unchanged.
3. **`noOcclusion()`** for the nuclear nozzle (R278), checked against every gameplay use yourself, and its GameTest
   for all six facings.
4. **Root's merge.** The asset plan keeps every W255 and W256 row and places W258's rows correctly (rerun a
   first-match audit); `MonoMotorOcclusionGameTests` keeps the mono and bipropellant tests with their original
   columns and assertions through W258's helper; `V180PropulsionData` and `V180MotorModelDataTest` auto-merged
   correctly; the changed ledger-test fixture still exercises the batch-order rule.
5. **The sharded branch run** on `5875a1f66` once it finishes, and **nothing weakened**.

## Rerun

In a fresh clone or your review worktree only: import verify for every v1.8 record, the importer, content-ledger,
provenance and resource Python tests, the atlas, motor, client and data unit tests, the whole unit suite and the
repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W258-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
