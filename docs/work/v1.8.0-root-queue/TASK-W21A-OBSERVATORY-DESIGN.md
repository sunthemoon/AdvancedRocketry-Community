# TASK W21a: observatory and processor — design note (registry schema 4, patterns)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first. This is
design work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w21a-observatory-design` |
| Branch | `codex/v1.8.0-w21a-observatory-design` (local only; Root merges) |
| Contract | ADR-066 section 6.1 (owner-confirmed D2: independent ground-owned survey jobs) |
| Deliverables | `docs/work/v1.8.0-root-queue/records/W21A-DESIGN.md` (three to five pages); the two pattern fixtures; a short record `records/W21A.md` |

## Why

ADR-066 section 6.1 specifies the observatory, the astrobody data processor and the ground
survey jobs in detail. It leaves two inputs to settle before any code:
- the precise ADR-051 amendment, with its migration;
- the exact cell maps, frozen as reviewed `machine_patterns` fixtures.

This is a persistence and migration slice: satellite registry schema 3 → 4 and instance
schema 1 → 2. AGENTS.md section 13 therefore requires this note and one independent
read-only review before code.

## Read

- ADR-066 section 6.1 in full, and sections 2 and 9 (D2).
- ADR-051 (the satellite registry, instances and missions), ADR-062 (data kinds) and
  ADR-037 (discovery cost).
- ADR-016 (data patterns) and ADR-019 (controller-owned banks).
- The existing satellite registry code: its schema-3 codecs, its 1/2 → 3 migration, its
  budgets (16 MiB whole-root preflight, 4 KiB per record, 16 live instances per owner,
  2,048 in total) and its coalesced flush.
- The v1.2 kernel, through the classic machines merged in W01, W05 and W10.
- The upstream 1.12.2 observatory and processor structures. Use only the files recorded in
  the provenance audit, and record the source of each cell.

## The design must decide

1. **The ADR-051 amendment, as exact text** for Root to add as an implementation note:
   - the tagged origin `SURVEY_MISSION` / `GROUND_SURVEY` with exactly one UUID;
   - instance schema 2;
   - registry schema 4 with the `ground_surveys` section;
   - every invariant listed in section 6.1, written as a table of states and backlinks;
   - quarantine rules;
   - cancellation tombstones and retention.
2. **The migration.** The order is 1/2 → 3 → 4, and an already-current input is a no-op.
   - Specify the per-record budget snapshot that refuses to start when a migrated record
     would not fit.
   - Unknown kinds and future schemas are preserved raw.
   - An old host rejects schema 4.
   - List the migration fixtures section 6.1 requires.
3. **The job model:**
   - its fields and byte bounds;
   - its phases: ACTIVE, READY, PROCESSED, CANCELLED, QUARANTINED;
   - the analysis subphases: WAITING, RUNNING, CREDITED;
   - the limits: 1 live job per controller, 16 per owner, 2,048 global, 4 KiB each, a
     1 MiB section, and the counting inside the existing instance limits;
   - the per-tick budgets: 32 paid advances and 64 queue inspections, fair across Levels.
4. **The start, completion and analysis mutations.** For each, give the exact single
   registry mutation and its persistence barrier, and what a crash at each point leaves.
   No duplication on copy, rebind or retry.
5. **The two patterns.**
   - Freeze `observatory` (five 5 × 5 layers) and `astrobody_data_processor` (two 2 × 3
     layers) as `machine_patterns` JSON fixtures in the four rotations.
   - Give every cell's role (controller, casing, C16 motor, tower, lens or glass, slabs,
     power and IO equivalents) and its upstream source.
   - Commit the fixture files with the note.
6. **The runtime.** Sky and night checks on the single loaded lens column; the station-orbit
   case; pausing; menus showing the missing cell, sky, energy, owner and data status.
7. **The slices.** Split the code into reviewable slices, at least:
   - **W21b:** the registry schema 4, the migration and the pure invariants, with no
     blocks yet;
   - **W21c:** the observatory and processor blocks, menus and runtime.

   For each slice, give its tests and evidence.

Check every vanilla claim against the mapped Forge jar with `javap`, and cite the class and
method.

## Not in this task

- Code and tests other than the two pattern fixture files.
- ADR edits. Write the amendment text in your note.
- Status files and the ledger.
- The advancements (section 6.2).
