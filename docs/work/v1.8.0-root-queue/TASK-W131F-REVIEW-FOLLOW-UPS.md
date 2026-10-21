# TASK W131F: W131's review follow-ups (W131-R1, R2) under Root decision R57

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W131](TASK-W131-CARGO-C-E3-SETTLEMENT.md),
W131's record (`docs/work/v1.8.0-root-queue/records/W131.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W131-review/REVIEW.md` (SHA-256 `d541a682…625d4`), first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w131`, branch `codex/v1.8.0-w131` at `5f3fcb01`; continue on that branch |
| Record | append a "W131F" section to `docs/work/v1.8.0-root-queue/records/W131.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W131F/` (keep W131's evidence unchanged) |

## Root decision R57 (2026-10-22)

The review approves with changes: no resource or settlement defect, but two A1 cases the C-E row asks for are missing.

1. **First, merge the shared head into your branch** (`git merge --no-ff` the shared commit named in your prompt; the
   review's preview at `6dc96301` was clean), keeping W131's work and the shared side (W125 with W125F, W122 and Root's
   records), and commit it.
2. **W131-R1:** add a real-device **unload** held before rocket publication: freeze its neutral source in O1, introduce a
   mismatch after O1's acknowledgement, assert that normalization, the proofs and the release stay held without changing
   cargo, restore the exact choice, then verify the ledger, both O2 endpoints and the frozen O4 result. Keep the existing
   both-published unload case.
3. **W131-R2:** keep the plain-path disassembly test and add an A1 case with a real cargo-mutated snapshot and an
   eligible, checked legacy COMMITTED record in the live journal: `DISASSEMBLY_PENDING`, the checked removal, restoration
   of exactly the latest cargo, and the repeat refusal. A gated setup may install the valid record after the real
   mutation, without bypassing named cargo's F1 hold. In the record, keep this A1 case separate from the crash and restart
   windows that stay in C-S5.
4. **Defensive (Low, from the review's section 5):** the gated fixture fallback in `EscrowSettlementPruning` reads
   `getUUID("identity")` unchecked; make it check `hasUUID` and skip (holding the marker, as the hooks-off path does)
   instead of throwing, with a unit test. Do not add an item-device branch here (item settlement is D-E2).

No saved format, schema, hash, dictionary, proof, lease, budget or timeout changes; no existing assertion is relaxed;
`EscrowCoordinator` and `RocketTransferService` do not grow; new classes stay under 500 lines. A new WARN or ERROR that a
GameTest drives needs its own exact manifest entry. Stop and report if any of these seems needed.

## Verification

On the merged branch: each new or changed test class alone, then the whole unit suite; one full GameTest with the
unchanged log checker on committed source (the station departure test W131 saw fail is fixed on the shared side by W122,
so it should now pass; report it if not), under the heavy-JVM lock with the atomic pattern in WORKER-RULES, releasing it
and deleting `build/gametest/world` in the `finally` of the same detached script that took it. If the checker refuses
only the host-overload warning count, record the count and the number of agent sessions running. Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run.

## Not in this task

The C-S native cuts, item settlement (D-E2), the F1 fix's cargo extension, enabling admission, ADRs, the risk register,
status files and the ledger (Root).
