# TASK W268-recheck: recheck the corrected REVIEW closure proposal (W268-R1 to R10)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w268` at `e82206a6a`: W268-fix's merge `296c7cc6b` of the shared head `0cae612d2` and its commits `75a11f1e5` and `e82206a6a`. Review worktree `D:/GitHub/arce-v180-w268-recheck`, a detached checkout of `e82206a6a` that Root creates |
| Tasks | [TASK-W268-FIX](TASK-W268-FIX.md) (R297) and [TASK-W268](TASK-W268-REVIEW-CLOSURE-PROPOSAL.md) (R296) |
| Earlier review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-review/REVIEW.md` (SHA-256 `846b2235661b9e344c8b5fe9b4fe07e3fb42b67186da95c3a8e71cbb41af1bd6`) with its evidence files |
| Records | The "W268-fix" section of `records/W268.md` and `docs/work/v1.8.0-w268-review-closure/` on the subject branch; logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/` |
| Upstream | The pinned checkout `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/scratch/upstream` (CRLF working files; use your own LF clone if a check needs one, and delete it afterwards) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-recheck/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. Each of W268-R1 to R10 is fixed as the earlier review asked, or the record says why not. Recheck R2's nineteen
   consumer and replacement statements and R3's casing consumers against upstream yourself.
2. The rebase: the drafts and every count are rebuilt on `0cae612d2` (IMPORT 0, REVIEW 95 to 10, EXCLUDE 270 to 355,
   allowlist 247 to 162 with the stated SHA-256), `build_proposal.py` reproduces every committed output byte for byte,
   and it refuses a base whose REVIEW set differs.
3. The owner summary at the top of PROPOSAL.md: each of the eight questions is accurate and says what accepting it
   gives up; nothing the owner should know is missing from a question.
4. The adoption steps: the placeholder guard and its test, the named validator change, and the three fixture updates
   and the fourth test's explanation.
5. The trimmed `REVIEW-EVIDENCE.json` still holds what a reviewer needs, and the full scan's SHA-256 matches the file
   kept outside Git.
6. Nothing outside the proposal directory and the W268 record changed relative to the merged shared head.

## Rerun

In fresh clones or your review worktree only: `build_proposal.py` against `0cae612d2`, the proposal's tests and scratch
scripts, the content-ledger validator on the branch and on a scratch copy with the drafts applied, the importer, ledger,
provenance and resource Python tests, and the repository validator. Do not run Gradle, GameTests, runData, servers or
clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK) on whether the proposal can go to the owner. Findings with an ID
(W268-R11, ...), severity, file and line, problem and suggested change. The commands you ran and their results. The
SHA-256 of REVIEW.md on the last line.
