# TASK W132F: W132's review follow-ups (W132-R1 to R5) under Root decision R56

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W132](TASK-W132-CARGO-D-E1-ITEM-ADMISSION-RECONCILIATION.md),
W132's record (`docs/work/v1.8.0-root-queue/records/W132.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W132-review/REVIEW.md` (SHA-256 `d5be5943…34ba`) with its probes in
`D:/GitHub/ARCE-Task-Evidence/root-queue/W132-review/probes/`, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w132`, branch `codex/v1.8.0-w132` at `4bf9ad87`; continue on that branch |
| Record | append a "W132F" section to `docs/work/v1.8.0-root-queue/records/W132.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W132F/` (keep W132's evidence unchanged) |

## Root decision R56 (2026-10-22)

The review approves with changes and finds no exactly-once, persistence or authority defect; its two Medium findings
concern liveness once item admission is enabled. The review also notes that W132 has not run against W125, which has
since merged and changes the refusal reason at the rocket-boundary check that item reconciliation uses. So:

1. **First, merge the shared head into your branch** (`git merge --no-ff` the shared commit named in your prompt), keeping
   both W132's item work and the shared side (W125 with W125F, W122 and Root's records), and commit it.
2. **W132-R1:** item selection reserves a bounded per-container estimate (for example a fixed multiple of the container
   payload's `uncompressedBytes()`, as `RocketCargoPreparation.itemWork` does) instead of `RocketCargoWorkBudget.MAX_BYTES`,
   and defers (`retrying`) while `RocketCargoPreparations` holds an active ticket, so reconciliation keeps priority. The
   per-tick budget, `RocketCargoTicketLifetime.LIMIT` and every other bound stay unchanged. Add an A0 test in which a
   replay ticket survives a long selection (the review's `W132ReviewSelectionStarvationProbeTest` is the model).
3. **W132-R2:** check unload `DEVICE_FULL` up front from the buffer alone (all four slots non-empty and at
   `min(64, maxStackSize)`), as the fluid devices do; cache the last no-move verdict keyed on the device root and the
   rocket snapshot identity until either changes, and do not queue the device while it is unchanged. Tests for both.
4. **W132-R3:** the raw native oracle compares item stacks with their NBT tag types (a type-tagged decode or the raw tag
   bytes of each stack); the fluid oracle stays unchanged. Show that the review's `probe_oracle_nbt_types.py` case is now
   refused.
5. **W132-R4:** drive the real `EscrowCargoPublication.Preparation` with a mismatched frozen digest for an item operation
   (a GameTest is fine) and assert a REPAIR `OTHER_STATE` hold with both endpoints byte-identical.
6. **W132-R5:** an item R39 hold logs `ARCE_ESCROW_ITEM_UNAVAILABLE op={} item={}` once per operation and session, with
   the structurally checked ID, and has its own exact manifest entry for the batch that drives it. W131 (not merged yet)
   factors the fluid line into `reportFluidUnavailable`; keep your change small and separate so Root can merge both.
7. **W132-R6** (disabled item devices take turn registrations from the pool shared with fuel loaders, inherited from the
   fluid devices) is not in this task; Root records it for the slice that enables cargo admission.

No saved format, schema, hash, dictionary, proof, lease, budget or timeout changes; no existing assertion is relaxed;
`EscrowCoordinator` and `RocketTransferService` do not grow; new classes stay under 500 lines. Stop and report if any of
these seems needed.

## Verification

On the merged branch: each changed test class alone, then the whole unit suite; one full GameTest with the unchanged log
checker (plus your R5 entry); then W132's 24 native cuts again, two restarts each, on your committed source; all under
the heavy-JVM lock with the atomic pattern in WORKER-RULES, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it. If the checker refuses only the
host-overload warning count, record the count and the number of agent sessions running. Check C: and D: above 10 GiB
and that 6 GiB of memory is free before each Gradle run or native start.

## Not in this task

W132-R6, D-E2 (item settlement), the F1 fix's cargo extension, enabling admission, ADRs, the risk register, status files
and the ledger (Root).
