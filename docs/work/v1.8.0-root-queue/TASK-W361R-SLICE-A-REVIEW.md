# TASK W361R: independent read-only review of accounting Slice A

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w361r`, detached at W361's commit `f820aeecc` |
| Decision | Root decision R443 (QUEUE row W361R) |
| Under review | `git diff f305e5b28 f820aeecc`: `EscrowFluidAccount`, `EscrowFluidAccountCodec`, their three test classes and `records/W361.md`, written by a Claude session under [TASK-W361](TASK-W361-ACCOUNTING-SLICE-A.md) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), sections 2, 3, 4.1 and 5, and section 10 for Root's answers |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W361-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## What to check

1. **The model against the design.** Keys (fluid ID plus tag digest, absent tag distinct from empty), aggregation per
   endpoint and key, the balance and outstanding identities, the four partial-application combinations, the
   half-applied transfer under ACCEPT_CURRENT, chain projection and later-prefix subtraction, component deduplication
   by the full typed endpoint key, and shared adjustment ownership with no double count.
2. **UNKNOWN is never zero.** Find every path where an unknown amount could become 0 or where knowledge could be
   promoted.
3. **Arithmetic.** All arithmetic on amounts is checked, including subtractions; overflow gives a bounded refusal.
4. **The codec.** Every bound is applied before any field is read. Unknown fields, wrong types, malformed digests,
   negative amounts, excess or duplicate or unsorted keys, an incorrect balance and non-canonical forms are refused
   with bounded results. Encoding is canonical and round-trips.
5. **The measurements.** The method of each figure in sections 3.1 to 3.5 of the record is sound and reproducible:
   the worst row, the largest component of 14, the receipt and operation additions, the pinned counts (62, 60 and 61
   becoming 55, 53 and 54) and the benchmark blob (7,625 becoming 7,113). Section 3.6's derived pins are reasonable.
   The finding that the saturation families have no byte refusal point is correct.
6. **Nothing is activated.** No existing class refers to the new ones, and no existing test or pinned value changed.
7. **Size and structure.** `EscrowFluidAccount` has 553 lines. Say whether it mixes responsibilities (AGENTS.md section
   3.4).

Run the three new test classes and, if time allows, the full unit suite from a copy in your own Temp folder under
`D:/GitHub/ARCE-Task-Evidence/Temp/`, with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` and `--offline`. Do not take
the heavy-JVM lock and run no GameTest or native server.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the file and line, what is wrong and the change you recommend. Then give
the commands you ran with their results. Give its SHA-256 in your answer. The last line of your answer is the path of
`REVIEW.md`.
