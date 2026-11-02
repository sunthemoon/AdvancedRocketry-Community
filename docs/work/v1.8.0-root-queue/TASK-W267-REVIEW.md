# TASK W267-review: independent read-only review of the remaining asset exclusions (R295)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w267`: W267's commits `3c4676a91` (ledger test fixtures), `4b26e5a5d` (audit script), `4f2e014ca` (evidence) and `17c3e38f5` (record) on the shared commit `a67d55dab`, and Root's commit applying W267's asset-plan draft. Review worktree `D:/GitHub/arce-v180-w267-review`, a detached checkout of the branch head that Root names in your prompt |
| Task | [TASK-W267](TASK-W267-ASSET-EXCLUSIONS.md) (R295, with R291 and R292) |
| Inputs | The W263 inventory `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md` (sections 4 to 7); `records/W264.md` for R292 |
| Records | `records/W267.md` and `docs/work/v1.8.0-w267-asset-exclusions/EVIDENCE.md` on the subject branch; logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W267/` |
| Upstream | The pinned checkout `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/scratch/upstream` (CRLF working files; make your own LF clone of the same commit if a content-inventory check needs one, and delete it afterwards) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W267-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Exactly these files.** The plan change moves exactly the 41 files of X1, X2, X3, J0, U1 and the furnace's active
   face from IMPORT to EXCLUDE, every other asset keeps the same first-match rule and handling, no rule is left unused,
   and no REVIEW row or allowlist entry changes. Do your own first-match comparison against `a67d55dab`; do not rely
   only on `audit_w267.py`.
2. **Each exclusion is sound.** For each file, nothing in the tree (models, MTL, blockstates, Java, JSON, import plans
   and records, data generators) references it, and the reason names a replacement that exists and works today. Check
   upstream's own use where the reason depends on it (for example, where the four `space_*` icons and the active face
   were drawn).
3. **Owners only trimmed.** No owner was added; the trimmed owners match upstream's consumers.
4. **The ledger tests.** `3c4676a91` restores the active face's former IMPORT row inside three fixtures; confirm no
   assertion was weakened and the tests still prove what they proved before.
5. **The 35 retained NEW textures** are listed with a disposition each and none is deleted.
6. **Overlap with W265.** W265 (branch `codex/v1.8.0-w265` at `77292739e`) also edits the asset plan, next to the
   replaced `models/item/models/*` wildcard. Say whether any row is changed by both, and whether W267's exact rows
   still describe the laser gun's files correctly once W265 lands.
7. **The sharded branch run** Root names in your prompt.

## Rerun

In fresh clones or your review worktree only: the content-ledger validator (also with `--require-accepted`), import
verify for every v1.8 record listed in `.github/workflows/repository-docs.yml`, the importer, content-ledger,
provenance and resource Python tests, `validate_v090_resources.py`, the audit script and the repository validator. Do
not run Gradle, GameTests, runData, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W267-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
