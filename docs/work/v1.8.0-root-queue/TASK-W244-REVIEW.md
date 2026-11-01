# TASK W244-review: independent read-only review of K5 and the etcher pair

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w244` at `96e4925c9`. It holds W244's commits from `8cea37e84` (the importer rule) to `2573841a2` (record), Root's commit `8ace041b5` applying W244's audited drafts (with the ADR-061 paragraph labelled as R259), and Root's merge of the shared head `fc24123d6`. Review worktree `D:/GitHub/arce-v180-w244-review`, a detached checkout of `96e4925c9` |
| Task it answers | [TASK-W244](TASK-W244-K5-AND-ETCHER.md), Root decision R259, owner decision D8 |
| Precedent | W241 (K4) and its review and recheck (`D:/GitHub/ARCE-Task-Evidence/root-queue/W241-review/REVIEW.md`, `W241-recheck/RECHECK.md`), merged at `3c1f977e3` |
| Record | `docs/work/v1.8.0-root-queue/records/W244.md` on the subject branch; drafts, audits and logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W244/` (final drafts `drafts-20261101T101856Z-178ab402/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W244-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The importer rule** (`8cea37e84`, ADR-061 section 4 paragraph "Identical deleted rename candidates").
   - It applies only to one unpaired destination with several unpaired, byte-identical deleted sources, and every
     candidate's history must clear the unchanged checks; a trigger keeps REVIEW and an incomplete walk fails.
   - It did fail first, and its tests cover a differing byte, an unclear candidate history and more than two
     candidates. It cannot clear any asset the old rule did not, other than this case.
   - Rerun the etcher texture's history yourself and confirm both predecessors' histories.
2. **Imports.** The three upstream files are byte copies, the NEW MTL binds only audited textures, the provenance stays
   PENDING_HUMAN_REVIEW and lists both candidates, and import verify passes against the authoring commit `a6b90e9cf`.
3. **The etcher's hull.** Its formed signal is the existing server formation, through the same transient formed byte
   as K2 to K4; orientation is pinned in four facings against the legacy transform (check it yourself); the R257
   rotation rule holds from the start; no packet field, persisted field or authority is added.
4. **The capsule.** W244 decorates the existing stationary countdown cage with a new UV layout instead of the legacy
   moving Capsule and Door mesh. Confirm it adds no field, entity or ride action, stays within the rider and vertex
   bounds, keeps the line fallback when the texture is missing, and say whether the redesign is reasonable to put
   before V1.
5. **The applied drafts** (`8ace041b5`): the asset-plan, ledger and `.gitattributes` rows, the ADR-061 paragraph and
   the VERIFICATION summary are true to the evidence; the allowlist is unchanged.
6. **GameTests** stay out of other tests' forced template chunks. Read the sharded branch run on `96e4925c9` once it
   finishes.
7. **Nothing weakened.**

## Rerun

In your review worktree only: the importer and content-ledger Python tests, import verify, the client model and
machine unit tests, and both validators. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W244-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
