# TASK W110F: W110's review follow-ups before merge (Root decision R39)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W110](TASK-W110-CARGO-D-K-ITEM-KINDS.md),
the branch's `records/W110-PLAN.md` and `records/W110.md`, and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W110-review/REVIEW.md` (SHA-256 `db1025f7…b872`) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w110` (existing; branch `codex/v1.8.0-w110` at `76f73841`, clean) |
| Record | add a "W110F" section to `docs/work/v1.8.0-root-queue/records/W110.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W110F/` |

The independent review answered APPROVE-WITH-CHANGES: the saved format is additive and an older jar refuses the new
values, but the size verdict, one round-trip claim and the scope of a decode failure need changes. Fix each item in its
own commit with a test that fails before the fix where a test can show it.

## Root decision R39 (2026-10-20)

1. **W110-R1, the size report.** Correct the record before merge: add transfer-guarded LOAD and UNLOAD measurements and
   the mod's real dimension IDs (`advancedrocketrycommunity:moon`, `:mars`, `:space`, `:venus`, `:tau_ceti_f`,
   `:tau_ceti_g`, plus the vanilla three) to the size tests and the record, and restate the verdict per kind,
   direction, guard and dimension. **Change no bound, allowance or layout**: W113 measures the options and Root decides
   the format separately. Do not change the `ESCROW_FULL` mapping either; C-E and D-E take the permanent-size mapping.
2. **W110-R3, the scope of a decode failure.** Decoding a durable item record stays **structural**: ID syntax, the
   byte `Count` range, the size, depth and node bounds, `ForgeCaps` at any depth and the exact field set. The registry
   lookup, the native maximum stack size and the native round trip move to a separate native validation that the
   codec exposes for admission, O2 and readback (C-E and D-E call it there, and a failure holds only that operation
   for REPAIR). Admission-time encoding keeps the full native check before any side effect. Test that a ledger whose
   pending item operation names an unregistered item, or gains a capability on round trip, still decodes and starts,
   while native validation of the same record refuses.
3. **W110-R2, the deferred player-head rewrite.** Refuse, as `INVALID_DATA` and before `ItemStack.of`, a
   `minecraft:player_head` whose `tag.SkullOwner` is a string, in both the structural and the native checks. If a
   check for items whose class overrides `Item.verifyTagAfterLoad` works reliably in both the development and the
   obfuscated production environment, refuse those too unless allowed; otherwise record the general case as a
   residual in the record (it fails closed later as a REPAIR hold). Add a test that installs
   `SkullBlockEntity.setup` profile services, so every environment reaches the same verdict.
4. **W110-R4.** An item load requires the item loader as source and the rocket as destination; an unload requires the
   rocket as source and the item unloader as destination. Refuse the other combinations as `INVALID_DATA`.
5. **W110-R5.** The record says that an older jar refuses a ledger with item records as invalid data (whole-world
   refusal, bytes kept), and drops the request to delete `depth-report-classes` (Root already removed it).

No hash value, saved format version, bound, proof, lease or replay exclusion changes; no existing assertion, limit or
timeout is relaxed; stop and report if one seems needed.

## Verification

Each changed test class alone and the whole unit suite. Then one full GameTest with the unchanged log checker on
committed source, under the heavy-JVM lock (another worker may hold it; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB first. Memory is tight: one Gradle process at a time. Not in this task: C-E, D-P, D-E, devices, ADRs, the risk
register, status files and the ledger (Root).
