# TASK W230-recheck: confirm the revised safe-exit design closes the review's findings

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and an independent
reviewer: read only, change no tracked file, make no commit.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W230-DESIGN.md` on branch `codex/v1.8.0-w230` at `80ac9ddb9` (Revision 1), read against the source at `b6e13ac78`. Review worktree `D:/GitHub/arce-v180-w230-review`, a detached checkout of `80ac9ddb9` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W230-review/REVIEW.md` (SHA-256 `bd2246a7…89127b1`): APPROVE-WITH-CHANGES, W230-R1 to W230-R4 (Medium) and W230-R5 (Low) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W230-recheck/RECHECK.md` with its SHA-256 |

**Root decision R232.** Before Q1 goes to the owner, a session other than the designer checks the revision.

For each finding, answer CLOSED or OPEN, with evidence from the source and the Forge 47.4.10 jar where the revision
relies on a mechanism. In particular, check that the FALL-only suppression is implementable with the stated hooks.
Also check that the revision introduces no new problem against D1b's constraints:

- dismount is never cancelled;
- playerdata authority does not change;
- only loaded chunks are used;
- work and memory are bounded.

Say whether Q1 and Q2, as now written, can be decided as stated. Give an overall APPROVE or BLOCK.

## Not in this task

Any tracked file, code, the owner's decision.
