# TASK W236-review: independent read-only review of the K3 machine models

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w236` at `2e90a59b2`. It holds W236's commits up to `4dbd5d2b7` (projection design `6c9cf3164`, beacon binding `0f23b8ff5`, materials `b44b6af9d`, import `a8715c30f`, renderers and projections `58eca601a`, record `4dbd5d2b7`), Root's commit `a9cb53b9f` applying W236's audited drafts, and Root's merge of the shared head `02bea50d7`. Review worktree `D:/GitHub/arce-v180-w236-review`, a detached checkout of `2e90a59b2` |
| Task it answers | [TASK-W236](TASK-W236-K3-IMPORT.md), Root decision R240, owner decision D8 |
| Precedent | W233 and its review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W233-review/REVIEW.md`), merged at `229161522` |
| Record | `docs/work/v1.8.0-root-queue/records/W236.md`; drafts and audits in `D:/GitHub/ARCE-Task-Evidence/root-queue/W236/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W236-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Projections.** Each new formed flag is the same bounded, transient, server-decided block-entity update field
   that W51 and W233 use:
   - no packet channel, persisted field, schema or identity is added;
   - it is sent only on change;
   - it is absent from saves;
   - it is correct through break, reform, rotation and chunk reload.
2. **Imports.** The upstream files are byte copies. The NEW MTLs bind only audited textures. Check the beacon's
   binding to the existing controller front (`0f23b8ff5`): the art it reuses must already be recorded, and its
   provenance must be honest. The provenance stays PENDING_HUMAN_REVIEW, import verify passes, and K4, K5 and the
   etcher are untouched.
3. **The client.**
   - Budgets and references are respected.
   - Each machine renders in the state its legacy renderer used, with fallbacks.
   - The solar array's shape matches its multiblock.
   - No client reference appears in common code.
4. **The applied drafts.** The asset-plan, ledger and `.gitattributes` rows are true to the evidence, and the
   allowlist is unchanged.
5. **GameTests.** The new GameTests stay out of other tests' forced template chunks (W233's lesson). Read the
   sharded branch run on `2e90a59b2` once it finishes.
6. **Nothing weakened.**

## Rerun

In your review worktree only:

- the client model, machine and projection unit tests;
- the importer and content-ledger Python tests;
- import verify;
- both validators.

Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W236-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
