# TASK W110-review: independent read-only review of W110 (cargo D-K) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. A Codex session wrote W110, so this is a fresh Claude
session with no context from the author (AGENTS.md section 13).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w110` at `76f73841` (base `20a82b88`; code `96eee1c5`) in `D:/GitHub/arce-v180-w110`. Read it with `git -C D:/GitHub/arce-v180-w110 diff 20a82b88..76f73841`, `git show` and the file reader; never check out, commit or edit there |
| Task it answers | [TASK-W110](TASK-W110-CARGO-D-K-ITEM-KINDS.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) (frozen at `49f7e08b`) sections 2.2, 3.2, 3.3 and 5.3 and section 6's D-K row; [records/W100.md](records/W100.md) (C-K, which D-K mirrors for items) |
| Plan and record | `docs/work/v1.8.0-root-queue/records/W110-PLAN.md` and `records/W110.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W110/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W110-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The saved format.** The escrow ledger gains item load and unload kinds, `CONTAINER`, `ITEM_BUFFER_V1` and a new
   adapter ID while intent schema 2 and receipt schema 3 stay. Confirm every existing ledger decodes and re-encodes to
   identical bytes; that a value this jar does not know is refused, never normalized; and whether the record's "no
   migration needed, the root is unpublished" holds against R-035 and the code. Say whether an older jar reading a ledger
   with the new kinds refuses it as invalid or future data, as the existing whole-world refusal requires.
2. **The stack codec.** Native NBT is preflighted before any recursive copy; 4,096 bytes, depth 16 and 512 nodes;
   a canonical, registered, non-air ID; the exact count within the item's native maximum; `ForgeCaps` refused at any
   depth; a lossless native round trip; `CARGO_TOO_LARGE` or `INVALID_DATA` without repairing input. Look for an input
   that passes preflight but expands later (for example a list of empty compounds, long strings in modified UTF-8, or
   a tag Minecraft rewrites on load).
3. **The buffer signature.** `{slots: [slot0..slot3]}` with exact stacks or empty compounds, the domain prefix
   `arce-escrow-item-buffer-1:` plus the device UUID, Count and slot order and typed tags signed; display text is not
   identity. Can two different buffers sign equal, or one buffer sign two ways?
4. **Merge identity and settlement.** `isSameItemSameTags` plus the canonical id and tag digest; Count excluded only for
   comparison; item settlements carry quantity 0 and presence comes from state; `EscrowSettlementCapacity`'s preview.
   Check both ABANDON survivors and the neutral rocket normalization against section 3.3.
5. **Capture and exclusion.** Direction-correct capture, one whole-rocket PRIMARY key, composite frontiers, and the
   reused floors, bindings, guards and exclusive admission with unchanged rules. Does any change alter an existing
   fluid (C-K) path?
6. **The size report.** Real encoders in both directions, 0 to 4 retained markers plus the candidate, worst-case names
   and actors. Does the worst case actually fit the combined limits, and is any limit raised?
7. **Production inertness.** Production admission stays disabled; no new code path runs outside tests.
8. **Tests and limits.** No existing test, assertion, limit or timeout was removed or relaxed (AGENTS.md section 5);
   list every edited pre-existing file and say why each edit is legitimate. Flag any class over 500 lines.
9. **The record** against the code: does it claim anything the diff does not do?

## Rerun

Export the subject with `git -C D:/GitHub/arce-v180-w110 archive 76f73841` into a new folder under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W110-review/` and, there only, run with JDK 17.0.7
(`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: the eight new `EscrowItem*` test classes and the
existing tests of every touched class (find them by name: the operation, endpoint, signature, capture, settlement and
ledger codec tests, and C-K's fluid cargo tests). You may add a probe test in the export to check a finding; keep it in
the output folder. Run no GameTest, `runData` or server, and only one Gradle process at a time: memory is tight while
other workers run. Delete the export's `build` and `.gradle` folders afterwards and keep only logs.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W110-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
