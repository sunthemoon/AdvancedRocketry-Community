# TASK W21b: satellite registry schema 4, ground survey jobs and the migration (no blocks)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w21b-registry-schema4` |
| Branch | `codex/v1.8.0-w21b-registry-schema4` (local only; Root merges) |
| Design | [records/W21A-DESIGN.md](records/W21A-DESIGN.md), the W21b slice |
| Design review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W21a-review/REVIEW.md`, SHA-256 `0c76c2d4f9d125ca1af75ee96e35ddbd46ba914aaabbfddf5f9d3f32d01dfb50`, APPROVE-WITH-CHANGES. Read all of it |
| Record | `docs/work/v1.8.0-root-queue/records/W21B.md` |

This is a persistence and migration slice. Root dispatches an independent actual-diff review
before the merge.

## Goal

Implement exactly the W21b slice of the design, with the review's W21b conditions 1 to 4
applied. This slice adds no observatory or processor block, menu or runtime. Ground jobs exist
only in the model, codecs, migration and invariants, and in test fixtures.

## Required, from the review

1. **R2: one frozen matrix** of phase, analysis subphase, progress and optional fields.
   - CANCELLED keeps its partial progress.
   - QUARANTINED validates against a non-QUARANTINED `previous_phase`, and keeps the first
     previous phase and any credited state across re-quarantine and reloads.
   - The final survey advance publishes ACTIVE at `ticks - 1` → READY at `ticks` together with
     AVAILABLE, in one mutation. Analysis does the same: RUNNING → CREDITED/PROCESSED together
     with the account credit.
   - Test partial cancellation, quarantine from each phase, final-tick replay, and malformed
     combinations kept raw.
2. **R4: the release boundary.** An ACTIVE quarantined pair and duplicate RUNNING claims are
   offline-repair-only. Both release commands refuse, change nothing, and say why. Test it.
3. **R5: historical thresholds.**
   - Instances and `save_epoch` exist from root 3, and `ground_surveys` from root 4. Neither
     exists in roots 1 or 2.
   - Run 1/2 → 3 through a schema-specific intermediate validator, then 3 → 4.
   - The migration tests go through `SavedDataSchemaMigrator` and the pre-start migration
     service. Include a valid root-3 epoch and shape, and a second start that makes no backup
     and no write.
4. **Condition 2.** Keep every existing survey and mining check, and add the ground-origin
   checks.
   - Test every origin and backlink direction, old records whose source was pruned,
     duplicates, and idempotent quarantine over repeated loads.
   - A failed cross-record check never credits or silently discards a record. A strict
     decode failure blocks and keeps the whole root.
5. **Condition 3.** Run the count, record, root, read and lifecycle-reservation checks before
   any mutation. Test the encoded lifecycle variants, budget refusals, arithmetic overflow and
   full-account credit refusal.
6. **Condition 4: evidence.**
   - The targeted and full unit tests;
   - GameTest and the log checker;
   - a native upgrade from a pre-W21b world: backup, upgrade, refused downgrade on an old
     host, and a second start with no rewrite.

## Rules

- Write the proposed ADR-051 implementation note and the risk-register text in your record.
  Root adds both before the merge.
- Do not edit ADRs or the risk register.
- No class over 800 lines.
- Every bound has a test at the limit.

## Commands

- The whole unit suite once.
- `runData` if anything generated changes.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- The native upgrade run, with its evidence kept under 100 MB.

## Not in this task

- The observatory and processor blocks, menus and runtime (W21c).
- The advancements.
- Status files and the ledger.
