# TASK W04: owner-protected force field projector

Root: Claude. Date: 2026-10-11. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w04-force-field` |
| Branch | `codex/v1.8.0-w04-force-field` (local only; Root merges) |
| Ledger plan | C17b (`forceField`, `forceFieldProjector`) |
| Record | `docs/work/v1.8.0-root-queue/records/W04.md` |

## Goal

An owner can project a straight, sealed force-field line of up to 32 blocks with
redstone, and retract it again. The field respects every protection, never deletes
another projector's field, and is never lost or duplicated across unloads, explosions
and restarts.

## Read

- ADR-065 (`docs/decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`):
  - section 6.2 in full: it is the specification;
  - section 2 (identities and native root handling);
  - section 11 (verification).
- ADR-054 section 5.1 and ADR-021, for the protection chain and the API version rule.
- ADR-024, for the immutable BlockState boundary classification and the bounded dirty-cell
  room invalidation.
- Existing code:
  - `api/endgame/EndgameEffect` and `EndgameEffectEvent`;
  - the v1.7 endgame protection chain;
  - `ApiVersions`;
  - the atmosphere room service;
  - `persistence/GuardedChunkSaves`.

## Scope

1. **Design note first.** This slice holds persistent roots and a cleanup tombstone, so
   AGENTS.md section 13 applies. As your first commit, write a design note of at most one
   page at `docs/work/v1.8.0-root-queue/records/W04-DESIGN.md` covering:
   - the codec of the projector root, the field root and the tombstone root;
   - recovery after restart;
   - the event interception points.

   Then implement it. Root has it independently reviewed before the merge.
2. **Blocks:**
   - `force_field_projector`, with a block entity, an item and owner binding, and six
     directions;
   - the technical `force_field`, with a block entity, no item and no loot, listed in the
     recipe-graph exemption file.

   The field state is full-cube, collidable and SEALED. The boundary callback reads only the
   BlockState.
3. **Behaviour** as section 6.2 specifies:
   - placement only into empty AIR;
   - at most 1 cell per 5 ticks per projector, and a shared, fair budget of 64 cells per
     server tick;
   - no chunk tickets;
   - work stops when the source or target is unloaded;
   - a per-profile server switch;
   - redstone without a claimed owner is inert;
   - no energy fee.
4. **Protection:**
   - post `EndgameEffectEvent` with the new `FIELD_PROJECTION` before every placement;
   - post the standard break path and `BLOCK_BREAK` for retraction and removal;
   - an offline owner never becomes FakePlayer authority.
5. **API:**
   - append `FIELD_PROJECTION` to `EndgameEffect`, keeping every old constant and its
     order;
   - serialize by stable name;
   - raise the API minor version from 1.8 to 1.9;
   - update the API allowlist and documentation;
   - extend the isolated compatibility consumer: an old 1.8-compiled listener runs
     against 1.9, and a listener that throws or cannot handle the new effect refuses the
     effect.
6. **Claims and cleanup:**
   - exact-claim retraction: up to 32 offsets per projector, and a field records its
     projector UUID and generation;
   - the bounded orphan-tombstone root, which a full root refuses before any effect;
   - explosions and world removal go through the same admission.
7. **Room authority:** a successful placement or removal synchronously revokes the
   affected room authority through the bounded dirty-cell mechanism.
8. **Data:**
   - models and original textures;
   - the projector's loot and recipe;
   - en_us and zh_cn names.

   All through data generation.

## Tests (required)

- JUnit:
  - the root codecs and their bounds;
  - the claim bookkeeping;
  - the tombstone admission and the full-root refusal;
  - the API enum stability (old ordinals and names).
- GameTests covering the native-test list at the end of section 6.2:
  - source and field in different chunks, then unload and reload;
  - stale generations;
  - crossed projectors that do not delete each other;
  - protected and cancelled placement and removal;
  - supplied-room authority revoked before the next oxygen debit;
  - explosion cancellation;
  - unavailable claims;
  - restart;
  - a full cleanup root.
- The compatibility-consumer test for the 1.8 listener on the 1.9 host.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- Landing pads (section 6.1, which waits for W02) and station controls (section 5).
- Any other API addition; it must not reuse 1.9.
- Status files and the ledger.
