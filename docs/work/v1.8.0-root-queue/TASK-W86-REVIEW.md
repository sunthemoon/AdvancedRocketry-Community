# TASK W86-review: independent read-only review of the P1-F3 and N2 design note

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree. Do not start subagents.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W86-DESIGN.md`, revision 1, and `records/W86.md` on branch `codex/v1.8.0-w86-p1-f3-design`, commit `b989d31e` |
| Task it answers | `docs/work/v1.8.0-root-queue/TASK-W86-P1-F3-DESIGN.md` at the same commit |
| W11b2 code | The note cites W11b2 at `a8d81d24`. Read it only through Git (`git show a8d81d24:<path>`, `git grep <pattern> a8d81d24`). Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration`, where continuation 26 is working |
| Checkout | `D:/GitHub/arce-v180-w86-review`, detached at `b989d31e`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W86-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The per-record rule is safe.** A slot counts as acknowledged when the latest acknowledgement was captured at or
   after the slot's last change. Check it against the writer and checkpoint code: capture order, retries, coalescing,
   the definite-failure, UNKNOWN, blocked, first-write and stop cases. Can any effect run on a record or an absence
   that the disk can still lose, or run twice?
2. **Nothing that must not change, changes:** the acknowledgement order of every record kind (journal root 3, W44's
   outbox, W11b2's governed record 3, P1-F8's write-back), the proofs, the holds and the replay exclusion. Check the
   note's claim that the recommended option keeps all of them, and the one visible change it names (the second Moon
   landing answers NOT_FIRST from memory).
3. **The continuation scheduler:** one reservation per rocket, the limit of 128, the 64 runs per tick, when a
   continuation is discarded, and whether a continuation kept across UNKNOWN can run on stale state.
4. **N2:** the reservation before `failBackToSource` changes anything, and the recommendation to make N2 the first commit
   of the P1-F3 leaf.
5. **The escrow consumers** that still wait for a clean journal (settlement, floor collection, governed F1 admission,
   test hooks): is that safe, and is the starvation risk under continuous traffic stated and measured by the proposed
   tests?
6. **P1-F8 after F3:** are the W85 sentences and tests that change listed correctly?
7. **No saved-format change** is claimed: confirm that no record, key, schema or root changes.
8. **Tests, estimates, the proposed R-035 and ADR-065 text, the open questions and readability.**

Take no heavy-JVM lock. Run no Gradle build, GameTest or native server. Reading compiled classes with `javap` is allowed.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID, a severity, the section and line, the problem, and a suggested change.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
