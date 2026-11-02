# TASK W268-review: independent read-only review of the REVIEW closure proposal (R296)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w268` at `144d34b44`: W268's commits `8e891d0a5` to `144d34b44` on the shared commit `a65465138`. Review worktree `D:/GitHub/arce-v180-w268-review`, a detached checkout of `144d34b44` that Root creates |
| Task | [TASK-W268](TASK-W268-REVIEW-CLOSURE-PROPOSAL.md) (R296) |
| Inputs | The W263 inventory `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md` (sections 2, 4, 6 and 7); ADR-061 section 4, ADR-062 revision 6 and ADR-066 section 7.3 |
| Records | `records/W268.md` and `docs/work/v1.8.0-w268-review-closure/` (PROPOSAL.md, SCRATCH-RESULTS.md, the drafts, changes.json, REVIEW-EVIDENCE.json and the scripts) on the subject branch; logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W268/` |
| Upstream | The pinned checkout `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/scratch/upstream` (CRLF working files; make your own LF clone of the same commit if a check needs one, and delete it afterwards) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

The proposal goes to the owner, who decides. Your review tells Root and the owner whether it is accurate enough to
decide on.

## What to check

1. **Completeness.** The 95 files are exactly the REVIEW first matches at `a65465138`; each appears once; the groups'
   counts add up (85 EXCLUDE, 10 REVIEW); each file's source SHA-256 matches W263 section 7 and the pinned upstream.
2. **Each exclusion's evidence.** For a sample of at least two files per group, and every file in G4 (space suit) and
   G6 (LibVulpes casing and motor): nothing in the tree references the legacy file, upstream's own consumer is stated
   correctly, and the named live replacement exists and is bound today. Say whether any group's owner question is
   misleading or hides a choice the owner should see.
3. **The boundaries.** Excluding does not clear authorship; the findings draft keeps the owner placeholder and is not
   a signature; the scratch ADR stays PROPOSED; G7 is tied to D5 and stays REVIEW; no IMPORT is proposed.
4. **The drafts.** The draft plan, allowlist and findings change exactly the 85 files; the new allowlist SHA-256 is
   right; the scratch application reproduces the stated results. The drafts are based on `a65465138`, before W265
   and W267 changed other plan rows: say whether `build_proposal.py` or `apply_scratch.py` can regenerate them on a
   later plan, and whether any W265 or W267 row interacts with a REVIEW row.
5. **The four ledger-test failures** with the drafts applied: confirm they are fixtures that use a REVIEW file and would
   need a deliberate update at adoption, not a defect in the drafts, and say which tests and what update each needs.
6. **Evidence size and form.** `REVIEW-EVIDENCE.json` is about 5 MB; the nested `.gitattributes` marks the drafts LF.
   Say whether either should change before it is merged (AGENTS.md section 9 keeps only what review needs).

## Rerun

In fresh clones or your review worktree only: the proposal's own tests and scratch scripts, the content-ledger
validator on the branch and on a scratch copy with the drafts applied, the importer, ledger, provenance and resource
Python tests, and the repository validator. Do not run Gradle, GameTests, runData, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK) on whether the proposal can go to the owner. Findings with an ID
(W268-R1, ...), severity, file and line, problem and suggested change. The commands you ran and their results. The
SHA-256 of REVIEW.md on the last line.
