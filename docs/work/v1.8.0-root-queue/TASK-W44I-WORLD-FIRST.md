# TASK W44I: implement the world-first advancements from the frozen design

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w44i-world-first` (from the shared branch at `768fa2d7` or later) |
| Branch | `codex/v1.8.0-w44i-world-first` (local only; Root merges) |
| Design | [records/W44-DESIGN.md](records/W44-DESIGN.md), frozen at `768fa2d7` after three rounds (reviews `bf8a200e…`, `35030212…`; Root's N1 re-check) |
| Record | `docs/work/v1.8.0-root-queue/records/W44I.md` (new) |

Implement `one_small_step` and `first_station_warp` exactly as the frozen design specifies. The design
is the contract.
- If the real code forces a change, stop and write the change, with its reason, at the top of your
  record before coding around it.
- A change to the commit, receipt, award or delivered order, to an absence proof, or to a hold
  needs Root's approval first. Stop and report.

This slice adds persistence and migrations, so an independent review follows before merge.

## Order of work (commit each step with its tests)

1. **Shared progression code** (design sections 3 and 3.6, and section 10's "Shared" row):
   - the `FirstEvent` codec and fingerprint;
   - `FirstEventOutbox`;
   - `ClassicProgressionSavedData`, with the absence proof and the UNREADABLE root;
   - the `AtomicSavedData.writable()` gate;
   - `CheckedSavedDataFile.presence`.

   Add the A0 unit tests for each.
2. **The Moon landing source** (section 2): the journal root 3 outbox and its migration;
   `MoonLandingCompletion`; the durable view; the initialization rule independent of transfers.
   - Move `tickDestination` and `cleanupLandedIfComplete` into `RocketDestinationCompletion`
     first, as N6 says, so `RocketTransferService` stays under 800 lines.
3. **The station warp source** (section 1): the root 5 outbox; the one-time epoch migration of
   pre-release root 5, with a backup; the root-only checked acknowledgement; and the
   publish-failure fix.
   - The planned change to
     `StationWarpRelocationTest.publishFailureAfterReplacementQuarantinesWithAConsistentPairOnBothSides`
     is the design's (section 10, N5). Say so in the commit and the record.
4. **Delivery** (sections 3.3 to 3.5):
   - `ClassicFirstEventDelivery`, covering the tick, login, and deferred reconciliation;
   - the bounded retry diagnostics (N2);
   - the status command;
   - the two milestones change from PENDING to EVENT.

   Run `runData` for the advancement JSON. The two PENDING assertions named in section 10 are
   replaced by the new batch on purpose; that is not a weakening.
5. **Native tests.**
   - The A1 GameTest batch.
   - The cut seams from section 6.1's table, gated and no-op in production.
   - As many of the 49 headless cut variants as fit this session. Each runs with two restarts,
     pinned to the source and the jar, with evidence under 100 MB.
   - List what is left in your record. A continuation finishes the rest.

## Commands

- The whole unit suite at each step that changes code.
- `runData` under the lock when the advancement data changes; then
  `git diff --exit-code -- src/generated`.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts, under the lock.
- Delete your generated `build/gametest/world` and any server copies at the end with a plain
  `rm -rf` from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- The 14 real-client variants (final campaign).
- ADR-066, the risk register, status files and the ledger. Root adds section 7's text at merge.
