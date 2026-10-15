# TASK W83: an arrival waits until its footprint's entity sections are loaded (W13-N1, first option)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w83-arrival-readiness` (from the shared branch) |
| Branch | `codex/v1.8.0-w83-arrival-readiness` (local only; Root merges) |
| Background | ADR-065, the W13 known-limitation paragraph ("W13F review N1"); [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), the W13-N1 paragraph in section 1 and its row in section 6 (Root's recommendation: the first option) |
| Code | `rocket/transfer/NamedLandingPadOccupancy.java`, `NamedLandingPadStaging.java`, `RocketLandingPadSelector.java` and their callers |
| Record | `docs/work/v1.8.0-root-queue/records/W83.md` (new) |

## The problem

The station-wide occupancy search visits only loaded, accessible entity sections and never loads a chunk. A rocket
parked where entities are unloaded or still loading is not found, so an arrival can be placed over it.

## What to do

1. **Require loaded entity sections in every arrival chunk.** Before an arrival is placed, every chunk that the
   arrival footprint intersects (at most 16) must have its entity sections loaded (`ServerLevel.areEntitiesLoaded`).
   Never load or force a chunk for this check. Recheck immediately before placement, in the same tick as the
   occupancy search.
2. **While a chunk is not ready, the arrival waits**: it is not placed, and it is retried through the existing arrival
   retry path. Give the wait its own refusal reason and English and Chinese text (for example
   `message.advancedrocketrycommunity.pad.reason.entities_loading`), and log it once per transition, not per tick.
   Add no new timeout, and do not change an existing one. Say how the production transfer's own destination tickets
   make the sections load, so the wait ends.
3. **State the remaining limitation exactly.** A rocket whose entity sits in a chunk outside the footprint but whose
   structure overlaps the footprint can still be missed while that chunk's entities are unloaded
   (`discoveryBox` reaches beyond the footprint). Do not claim to detect every unloaded off-footprint rocket.
4. Keep the existing `ARCE_PAD_OCCUPANCY_REFUSED` behaviour and the entity-inspection budget.

If the change needs to alter a transfer's acknowledgement order or a saved format, stop and report: that would need a
design note.

## Tests

- A GameTest: a parked rocket in a footprint chunk whose entity sections are not yet loaded; the arrival waits with the
  new reason, then refuses as occupied once they load, and is never placed over it.
- A GameTest: all footprint chunks ready and free; the arrival is placed as before.
- The existing `NamedLandingPadOccupancyGameTests` and pad tests pass unchanged.
- The whole unit suite once; `runData` if the language data changes; `runGameTestServer` under the lock on a
  **committed** SHA, then the log checker. Add a log expectation only for a line your new test drives.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the heavy-JVM lock is
  released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- A persisted parked-region index (the second option), W11b2, W11b3, and the bay.
- ADRs, the risk register, status files and the ledger (Root).
