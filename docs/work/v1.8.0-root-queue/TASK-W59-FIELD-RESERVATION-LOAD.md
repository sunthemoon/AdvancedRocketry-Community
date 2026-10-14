# TASK W59: release the stray UNBOUND force-field reservation after an in-place NBT load (W04 N5)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w59-field-reservation` (from the shared branch) |
| Branch | `codex/v1.8.0-w59-field-reservation` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W59.md` (new) |
| Finding | `D:/GitHub/ARCE-Task-Evidence/root-queue/W04F-review/REVIEW.md`, N5; W55's classification in `records/W55.md` section 2 |

`ForceFieldProjectorBlockEntity.onLoad` reserves the constructor's random UNBOUND identity in the
force-field cleanup root. When NBT then replaces the identity in the same placement (an operator
places a projector from an item with `BlockEntityTag`, or a structure template is placed into a live
Level), `load` swaps the identity without releasing the first reservation. Each such placement leaks
one entry into the persisted cleanup root. The GameTest fixtures that load NBT into a projector
(`ForceFieldGameTests.Fixture`) leak one entry each today.

This changes the reservation lifecycle of a persisted root, so AGENTS.md section 13 applies: a design
note first, then the code; Root has an independent read-only review done before merging.

## What to do

1. **Design note** (section 1 of your record, committed before any code). Answer:
   - how an in-place load is recognised: the block entity is already in a Level, and its previous
     reservation is an exact, non-orphan, zero-claim UNBOUND entry for the identity being replaced;
   - what happens when the cleanup root is not operational, is full, or the loaded identity collides
     with another projector's entry (existing refusal rules must hold; never drop a bound entry);
   - whether the loaded identity is reserved at once or at the next chunk load;
   - the cases that must not change: a chunk load from disk, a projector that has claims, a bound
     reservation, a different owner;
   - the risk-register and ADR wording you propose (Root applies it): R-022 and the ADR-064 or
     ADR-054 note that covers the cleanup root, whichever owns it (cite file and line).
2. **Implement** the design. Keep every existing assertion.
3. **Tests:**
   - a GameTest: place a projector from an item with `BlockEntityTag` carrying another identity; the
     cleanup root has no entry for the constructor's identity and exactly the expected entry for the
     loaded one; a restart-equivalent round trip of the root keeps it so;
   - a GameTest for each refusal case of the design;
   - check that the `ForceFieldGameTests.Fixture` placements no longer leak (count the root's
     entries before and after the batch's fixtures, or per fixture).
4. **Record**: the design note, the commits, the commands and results, and what is left.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker, which must pass
  with no new expectation.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf` and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- ADRs, the risk register, status files and the ledger (Root).
- Any other force-field finding.
