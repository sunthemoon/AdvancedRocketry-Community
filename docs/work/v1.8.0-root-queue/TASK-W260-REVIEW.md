# TASK W260-review: independent read-only review of import batches P4 (fuel tanks) and P0 (landing pad)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktrees and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject 1 | Branch `codex/v1.8.0-w260` at `4d6dca3ff`: W260's commits `e9e62034d` (design note) to `25c25cf2d` (record) on `144794fe3`, Root's commit applying W260's central patch, and Root's merge of the shared head. Review worktree `D:/GitHub/arce-v180-w260-review`, a detached checkout of `4d6dca3ff` that Root creates |
| Subject 2 | Branch `codex/v1.8.0-w261` at `7a4737287`: W261's commits `c54c886bd` to `49f7f778d`, W261-fix's `498a23e3f` and `bedb60336` (R289), Root's commit applying W261-fix's drafts, and Root's merge of the shared head. Review worktree `D:/GitHub/arce-v180-w261-review`, a detached checkout of `7a4737287` that Root creates |
| Tasks | [TASK-W260](TASK-W260-P4-FUEL-TANKS.md) (R286, with W258-R1 to R3), [TASK-W261](TASK-W261-P0-LANDING-PAD.md) (R287) and [TASK-W261-FIX](TASK-W261-FIX.md) (R289); P1's contract in [TASK-W249](TASK-W249-P1-MOTOR-MODELS.md) and [TASK-W249-FIX](TASK-W249-FIX.md) |
| Records | `records/W260.md`, `records/W260-design.md`, `records/W261.md` and `docs/work/v1.8.0-w261-landing-pad/VERIFICATION.md` on the subject branches; evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W260/`, `W261/` and `W261-fix/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W260-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W260.** The middle-tank imports are byte copies with recorded rewrites; the three tanks share one ordinary baked
   model with their block items; `noOcclusion()` and its GameTest; the build exclusion of the two v0.5
   `rocket_fuel_tank` models; the top, bottom and end files left IMPORT for the exclusion batch; W258-R1 to R3 fixed
   (the broad row's owners, the ledger-test fixture, the occlusion fixture's space) without weakening any test.
2. **W261.** The landing pad shows upstream's own `rocketpad_` on every face through `cube_all`, as
   `blockstates/landingpad.json` does at the pin; `rocketpad_noedge` is gone from the plan, record and tree; the
   fifteen EXCLUDE rows' reason and owner make sense (the tiles belonged to the redesigned launch pad); IDs, collision,
   landing selection and occupancy unchanged.
3. **Root's draft applications** on both branches (asset plan, ledger notes, `.gitattributes`, governance), and that
   W260's and W261's asset-plan changes do not overlap, so Root can merge one after the other.
4. **The sharded branch runs** (41458932030 and 41467212344, both green), and **nothing weakened**.

## Rerun

In fresh clones or your review worktrees only: import verify for every v1.8 record, the importer, content-ledger,
provenance and resource Python tests, the atlas, client and data unit tests, the whole unit suite on each branch and the
repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict for each branch (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W260-R1, ... and W261-R1,
...), severity, file and line, problem and suggested change. The commands you ran and their results. The SHA-256 of
REVIEW.md on the last line.
