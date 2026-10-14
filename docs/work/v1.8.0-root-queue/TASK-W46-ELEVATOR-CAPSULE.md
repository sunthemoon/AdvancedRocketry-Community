# TASK W46: C18d elevator capsule effect, with its S2C carrier

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w46-elevator-capsule` (from the shared branch) |
| Branch | `codex/v1.8.0-w46-elevator-capsule` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W46.md` (new) |
| Specification | ADR-066 section 7.1, the "Elevator capsule" bullet; ADR-059 (the elevator ride); `records/W16.md` ("Elevator capsule effect") |

ADR-066 section 7.1 sets the effect's rules:
- `elevator_capsule` is a client-only effect, not a persistent ride entity;
- it is drawn against the existing endpoint, pair and countdown lifecycle;
- at most one effect per rider and 64 visible endpoints;
- its lease is at most 300 ticks;
- it clears on cancel, commit, disconnect, Level change and unload;
- there is no fake passenger, collision or player authority, and no second teleport or reward;
- it must not claim gradual physical travel. The real ride is a countdown, then a committed teleport.

W16 found no S2C carrier for pending rides: `ElevatorEndpointBlockEntity`'s update tag carries only
the `tether` flag. This slice adds the carrier, so it changes what the server sends. Write a design
section at the top of your record **before** the code. Root will have the result reviewed
independently.

## Root's decision on the carrier

- **Where it travels.** The departure endpoint's existing block-entity update tag carries a bounded
  list of the pending rides that depart from it. There is no new channel or packet type.
- **Each entry.** Exactly two fields:
  - the rider's entity network ID;
  - the remaining countdown ticks when sent (0 to `ElevatorRules.RIDE_COUNTDOWN_TICKS`).

  It carries no UUID, name, pair ID, arrival position or energy.
- **The bound.** At most 8 entries. Pick the bound from the real per-endpoint limit if one exists,
  and justify it. Extra rides are left out of the projection; the server's rides are unchanged.
- **When it is sent.** On transitions only: a ride added, cancelled, committed, or expired.
  `sendBlockUpdated` follows the same rules as W42's processing flag. Never send it per tick.
- **The client.**
  - It counts down locally from the received value.
  - It drops the entry at 0, when a newer update omits it, or when the lease of 300 ticks expires,
    whichever comes first.
  - A malformed, oversized or unknown tag decodes as no rides.
  - Keep `tether` exactly as it is.

## What to do

1. **The design section.** Cover:
   - the codec and its bounds;
   - which transitions send;
   - how the client matches an entity ID in its own Level;
   - what happens to an entity ID that is not loaded or not a player;
   - the lifecycle clears;
   - the 64-endpoint cap: how visible endpoints are chosen, nearest first.
2. **Server.** Project the pending rides into the update tag without changing `ElevatorRides`'
   authority or timing.
3. **Client.** Render the capsule around the rider, physical client only:
   - geometry drawn in code, or a NEW texture with provenance;
   - no legacy asset import;
   - it may brighten or close as the countdown nears 0;
   - it does not move the rider.
4. **Tests.**
   - Unit tests for the codec: bounds, malformed tags, the 300-tick lease, the countdown.
   - Unit tests for the client state: one effect per rider, the 64-endpoint cap, and the clears.
   - Native GameTests with a real ride: the update tag carries the rider while it is pending; a
     cancel, a commit and an expiry each send a tag without it; nothing is sent on ordinary ticks.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if generated data changes.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- Any change to ride rules, energy, timing, protection or the teleport.
- A new network channel.
- Status files and the ledger (Root).
