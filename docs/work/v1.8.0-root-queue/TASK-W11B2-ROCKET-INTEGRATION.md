# TASK W11b2: D3-A escrow rocket integration (admission stays disabled)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Branch | `codex/v1.8.0-w11b2-rocket-integration`. It is based on W11b1 (`19ab684a`, not yet reviewed or merged) plus the shared branch (`ac96a7f0`). Local only; Root merges after W11b1 |
| Contract | [records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 8, row W11b2, and [records/W11B2-DESIGN.md](records/W11B2-DESIGN.md) |
| Design review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-design-review/REVIEW.md`, SHA-256 `8ec318a3863eef3b20bdc078309cc0dee9de8ad0d3e7cbb579a983a66a8b6f06`, APPROVE-WITH-CHANGES. Read all of it |
| Record | `docs/work/v1.8.0-root-queue/records/W11B2.md` |

This is a persistence, migration and recovery slice. Root dispatches an independent
actual-diff review before the merge.

**W11b1 is under independent review at the same time.** If that review changes the W11b1
core, Root merges the fix into this branch, and you adapt to it. Do not change W11b1's core
files except where W11b2's contract requires it, and record each such change.

## Goal

Implement the W11b2 slice:
- the rocket schema-3 migration;
- both mutation layers;
- the mount and movement interlocks;
- the checked-journal prerequisite;
- the recovery fences;
- floors and reservations;
- operator settlement and tombstones;
- the managed pre-start policy;
- the S2 cuts the design lists.

Production escrow admission stays **disabled**: no player-facing block or command can create a
record.

## Required corrections from the design review (R1 to R6)

1. **R1 (High).**
   - Expose the checked journal outcome and sequence to the transfer service.
   - A caller must not report a successful launch, or perform any effect that depends on a
     new authority phase, until the matching snapshot is acknowledged.
   - Keep uncertain records, and prevent dependent mutation and destruction while retrying.
   - Specify the failure policy for all nine sites.
   - Add A1 and S2 cuts with a failed committer for launch admission and for authority-phase
     advancement. The failure persists across ticks, entity saves and two restarts.
2. **R2 (High).**
   - Keep FENCED classification during a separate bounded, read-only re-evaluation that uses
     only already-loaded queries: no `recoveryEntityChunksLoaded`,
     `authorityEntityChunkLoaded`, `findMatches` or `keepLoaded`.
   - Classify records first, before any readiness check that adds tickets.
   - Promote a record only after a loaded proof.
   - Bound retries per tick and per transfer.
   - Test first startup, an entity join, repeated operator attempts, an unloaded end and an
     unavailable Level. Assert zero tickets and zero loads, and that an unrelated station
     warp stays available throughout.
3. **R3 to R6 (Medium).** Apply each exactly as the review states:
   - R3: the neutral state of schema 3's ledger UUID;
   - R4: the live flight paths in the interlock matrix;
   - R5: the exact-schema fixture updates;
   - R6: live `/data` protection.

## Rules

- Write the proposed text of the risk entries and of the ADR-065 implementation note in
  your record, including the W11b1 residuals (E1, E3, writer failures the mod cannot
  observe) and this slice's own scope. Root adds them.
- Do not edit ADRs or the risk register.
- No class over 800 lines. Every bound has a test at the limit.
- Pace loading waits with `GameTestTickPacer`.

## Commands

- The whole unit suite once.
- `runData` if anything generated changes.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- The native S2 cuts the design lists, on the final source, with evidence under 100 MB.

## Not in this task

- `fluid_fuel_loader`, the linker and production admission (W11b3).
- Status files and the ledger.
