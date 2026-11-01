# TASK W243: the per-owner cap's follow-ups (W238-R1, R2, R3, R6, R7 and R8)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w243` (new; branch `codex/v1.8.0-w243` from the shared head named in your prompt) |
| Findings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W238-review/REVIEW.md`, SHA-256 `faeeb086294088b8452034f7989f6530be336b85ef6d7708dde15daacb132027` |
| Merged slice | W238, merged at `f285abf26` ([TASK-W238](TASK-W238-PER-OWNER-PENDING-CAP.md), [record](records/W238.md)) |
| Record | `docs/work/v1.8.0-root-queue/records/W243.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W243/` |

**Root decision R258**, under the owner's delegation:

1. **W238-R1.** The floor-retirement refusal uses `Component.translatableWithFallback` with W231's English text, as
   `RocketFeedback` already does, so a server console or RCON operator reads the sentence. The unit test asserts the
   English text. Then draft, for Root, the R-042 sentence without the console qualification Root added in `8a03948cd`.
2. **W238-R2.** Restore the earlier order for the floor command. The permanent RECEIPTS_FULL refusal, which waiting
   cannot clear, comes before the transient pending-capacity refusal: move the early `checkReserve` after the floor
   retirement and tombstone checks, and run the command's floor admission after the preview's tombstone check. Restore
   the coordinator-level RECEIPTS_FULL assertion in `EscrowCombinedReservationTest` to its form before W238, if the
   order allows it.
3. **W238-R3.** Add the two tests W231's list asks for: ROCKET_FULL wins over OWNER_PENDING_FULL when both apply, and
   a restart with about 24 escrow-ledger records for one owner (OWNER_PENDING_FULL for that owner, RESERVED for
   another, then a phase change and exact retirement without a new slot).
4. **W238-R8.** Make the actor-owner overloads test-only (package-private, or remove them and pass the owner in the
   fixtures), so no production path can store the command actor as a new floor operation's owner.
5. **W238-R7.** A cross-reference comment in `V180FluidFuelLoaderData` pointing to where the loader's message is
   emitted.
6. **W238-R6.** Your record notes the corrections to W238's record (the coordinator grew from 597 to 604 lines; the
   fixtures use four or five owners; all eight measurement cases load written files). Optionally move the bay reason
   datagen row last.

No persisted field, schema, codec rule, configuration or limit changes, and no assertion is weakened.

## Verification

- The changed and new tests failing first where they check a behaviour change (R1, R2), then passing.
- The `resourceescrow`, `rocket.bay` and `rocket.server.Bay*` packages and the whole unit suite.
- runData determinism, and the escrow and bay GameTest batches in a focused run under the heavy-JVM lock.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run and reviews the diff.

## Not in this task

Bay admission, W242's safe exit, status files, the risk register (drafts only) and the ledger.
