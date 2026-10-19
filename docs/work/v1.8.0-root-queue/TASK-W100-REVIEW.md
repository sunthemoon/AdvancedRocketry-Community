# TASK W100-review: independent read-only review of cargo leaf C-K (the escrow core's cargo kinds)

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: change
nothing in any repository worktree. Do not start subagents. Codex implemented W100, so this is a fresh Claude session.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w100` at `d9871ede` against the shared branch at `5ed12bfa`: `git diff 5ed12bfa..d9871ede` |
| Task it answers | [TASK-W100](TASK-W100-CARGO-C-K-ESCROW-KINDS.md) |
| Design (frozen) | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 3.1 to 3.3 and section 6's C-K row |
| Record | `records/W100.md` in the checkout (with its size report) |
| Checkout | `D:/GitHub/arce-v180-w100-review`, detached at `d9871ede`, made for you; do not commit in it |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W100/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W100-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Section 3.2 items 1 to 4 and section 3.3, exactly:** the kinds and direction; the rocket-side accessor at every
   site that assumed the rocket is the destination (binding, floors, guard checks, capacity preview, O4); one live
   cargo operation per rocket; the turn token's bound and lifetime; `prepareTransfer` with `prepareFill` delegating;
   the five-marker preview and same-capture markers; settlement admitting cargo members while O1 to O4, the receipt
   dictionary and the frozen fuel-vector representation stay as section 3.3 states.
2. **The fill path is unchanged for players.** Commit `d185f657` keeps W11b3's closed fill result and maps the new
   cargo refusals (`ROCKET_BUSY`, `OTHER_TRANSFER_PENDING`, `RECEIPT_BACKED`) to `ENDPOINT_BUSY` for fills, while
   design section 3.2 item 2 says the fill's mirror refusal is reported as `ROCKET_BUSY`. Say which is right and what a
   fuel-loader player sees. Also check "reject unproven markers": what changed for fills and whether any fill that
   worked before is now refused.
3. **The size report:** that it measures both directions with the real encoders as section 3.3 requires, the
   five-marker verdict, and that a candidate that does not fit is refused before any side effect, without raising a
   bound.
4. **Unchanged bytes:** existing fuel and floor-retire receipt bytes and inspector output pinned and unchanged; no
   change to a proof's meaning, a lease, the replay exclusion or the governed paths; cargo admission unreachable in
   production.
5. **Tests:** whether the A0 cases cover the C-K row's list, including R6, R8 and R9 with cargo.
6. **Rerun** the six new or changed escrow test classes and `gradlew.bat --offline test --tests '*resourceescrow*'` in
   the checkout (unit tests need no lock); take no lock and run no GameTest or server.

## Output format

- A verdict: MERGE, MERGE-WITH-FOLLOWUP, or FIX-FIRST.
- Findings, each with an ID (W100-R1, ...), a severity, the file and line, the problem, and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
