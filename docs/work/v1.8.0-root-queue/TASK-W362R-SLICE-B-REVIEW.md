# TASK W362R: independent read-only review of accounting Slice B (saved form and migration)

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w362r`, detached at W362's commit `7dfbecae9` |
| Decision | Root decision R445 (QUEUE row W362R) |
| Under review | `git diff e1782d757 7dfbecae9` (base: the shared head with Slice A merged in), written by a Codex session under [TASK-W362](TASK-W362-ACCOUNTING-SLICE-B.md), with its record `records/W362.md` |
| Known follow-ups | W362F, the same Codex session, is adding the relaunch initializer audit and inventory entries for the 20 account types and the shared receipt predicate in `EscrowSettlementPruning.groups`. Note anything else that is missing; Root reviews W362F's small delta separately. |
| Design | [W357-DESIGN](records/W357-DESIGN.md) sections 4.1, 4.2, 4.3, 5 and 10; Root's limits and accepted values in QUEUE rows W361 and W362 (R443) |
| Deliverable | `D:/GitHub/ARCE-Task-Evidence/root-queue/W362-review/REVIEW.md` (outside Git) |
| Write scope | The review file only. Do not change, stage or commit anything in any worktree. |

## Why

Slice B changes saved forms: ledger root 3, account rows, operation and intent schema 3, receipt schema 4, and a
migration from root 2. AGENTS.md section 13 requires a separate read-only review before such a change merges,
preferably by a different engine from the author.

## What to check

1. **Activation is off.** With `EscrowAccountingFormat.ENABLED` false, every existing world and file behaves byte for
   byte as before: the writer emits root 2, root 3 is refused as future, no migration or backup runs, and all root-2
   pins are unchanged.
2. **Saved forms.** The root-3, operation, intent and receipt forms match the design. Every variable-length field is
   bounded. Account references, ownership, membership, knowledge and the component recomputation are validated
   exactly. No catch-and-fallback reader exists, and an invalid baseline is refused.
3. **Lock predicates.** Every comparison in `EscrowSettlementTombstones` accepts exactly schemas 3 and 4 through the
   shared predicate. Search the whole `resourceescrow` package for any other comparison with a single receipt schema
   constant that could miss schema 4.
4. **Migration.** The dedicated escrow branch:
   - checks the root-2 epoch;
   - keeps every embedded encoding, digest, floor and authority field;
   - validates the target and its reservations separately;
   - refuses root 1, a non-canonical source, an invalid target and a full target, each without changing the original.

   Check also that `supportsSchema` is exactly 2 and 3, and that the split baseline check is sound.
5. **Capacity.** Each row is charged once at 2,048 bytes, O3 overlap is counted once, and no existing charge shrank.
   The accepted re-pins (55, 53, 54 and the 7,113-byte benchmark blob with the root at exactly 1 MiB) appear only in
   explicit root-3 tests. The new measurements in record section 3 are reproducible.
6. **Tests.**
   - No existing test, assertion or pinned value was deleted, loosened or changed (AGENTS.md section 5).
   - The new tests cover the design's section 7.1 items for this slice.
   - The restored duplicate-candidate confirmation check is the original behaviour.
7. **Mixed components.** Check W357F's choice that an item or legacy member embeds the component's schema-3 intent
   while its own receipt stays schema 3. Say whether it is consistent with `validateComponents`.

You may run the targeted tests and the full unit suite from a copy in your own Temp folder under
`D:/GitHub/ARCE-Task-Evidence/Temp/`, with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` and `--offline`. Expect the one
known relaunch inventory failure. Do not take the heavy-JVM lock and run no GameTest or native server.

## Output

`REVIEW.md` begins with a verdict: APPROVE, APPROVE-WITH-CHANGES or REJECT. Then give numbered findings, each with a
severity (High, Medium, Low or Information), the file and line, what is wrong and the change you recommend. Then give
the commands you ran with their results. Give its SHA-256 in your answer. The last line of your answer is the path of
`REVIEW.md`.
