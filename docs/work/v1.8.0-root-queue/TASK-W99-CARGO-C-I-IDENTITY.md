# TASK W99: cargo leaf C-I, the structure digest and the grouped recognition predicate (W11c and W11d)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) and [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
(frozen at `49f7e08b`; section 2.5 "The identity rule (leaf C-I)" and section 6's C-I row), and
[records/W79.md](records/W79.md) (its review and revision 2), first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w99` (new; from the shared branch at `1141cba4`, which has P1-F8 (W92) and P1-N2 (W91) merged) |
| Branch | `codex/v1.8.0-w99` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W99.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W99/` |

## What to do

1. **Recheck section 2.5 against `1141cba4`.** It cites `646bf9ee`; W91, W92, W90 and W83b have since changed the
   transfer service, the transfer entities and recovery. Note the moved sites; stop and report if a difference
   changes the rule.
2. **`RocketStructureSnapshot.structureDigest()`** exactly as section 2.5 defines it: SHA-256 with the prefix
   `arce-rocket-structure-1:` over the schema, dimension, origin, bounds, each block's position, state and payload
   adapter ID (not payload data), the anchors, the stats and the propulsion signature; cached in the immutable
   snapshot, never persisted, its one pass charged to the reconstruction budget.
3. **The grouped recognition predicate**, inside the existing owner, logical and physical identity, placement and
   state checks, for `isCommittedAuthority` and recovery's `matches`: `sameSnapshotId && (sameContentHash ||
   (sameStructureDigest && currentLedgerBinding && epoch > 0))`. Equal snapshot ID is mandatory in both branches; a
   neutral rocket keeps the exact-hash rule; every fence, floor, veto and governed routing named in section 2.5's
   "Recognition, not reconstruction authority" paragraph stays as it is.
4. **Tests (A0 and A1 with explicit before and after snapshot fixtures, not cargo devices):** stable structure
   against changed geometry, state, payload adapter or propulsion; the mandatory snapshot ID; a neutral mismatch; the
   protected duplicate veto; governed routing; the digest's stability and its prefix pinned. Run each new class alone,
   the whole unit suite, and one full GameTest with the unchanged log checker, all on committed source and serially.
   Say whether you add a GameTest batch (about 448 batch names; a reshuffle comes past 768).

Real cargo mutation, reconnect and crash-safe disassembly belong to C-E and C-S5, not here.

## Write scope

`rocket/model/RocketStructureSnapshot` (the derived digest), the recognition predicate's two sites in
`rocket/server/RocketTransferEntities` and the recovery `matches` path, new tests, and the record. W98 is changing
`RocketTransferRecoveryService`'s removal sites and the continuations in parallel: keep your edits there to the
predicate only. No saved-format change, no change to an acknowledgement order, a proof, a lease, a fence, a floor or
the replay exclusion, and no relaxed assertion, limit or timeout. Stop and report if one seems needed.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W97, W98) also run tests; take the heavy-JVM lock for every GameTest and
release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free before each heavy
start. When your heavy runs end, remove your own lock and your `build/gametest/world` with a plain `rm -rf` after
checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in the record and final answer at
once.

Not in this task: C-K and the other cargo leaves, P1-N3 (W98), production admission, ADRs, the risk register, status
files and the ledger (Root). A read-only review follows before the merge, because the rule changes recovery identity.
