# ADR-051 — Resource mission instances, gas harvesting and reward delivery

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-AST-01, V160-GAS-01, V160-DEL-01]
related: [ADR-010, ADR-029, ADR-037, ADR-043, ADR-049, ADR-050, ADR-052]
```

## Context

In the legacy game a rocket with drills or intakes deletes itself into a mission
record holding its entire inventory, then respawns at completion with ore in its
chests or 64,000 mB in every tank. Gas collection force-loads the launch
dimension, and rewards use `Math.random()` (see the [audit](../work/v1.6.0-legacy-audit.md)).
v1.6 must deliver asteroid and gas-giant missions with controlled, reproducible,
conserved rewards, allocate and recycle instances transactionally, and never
force-load or reveal instance coordinates.

Mission state lives in SavedData (ADR-050), but item rewards must end up in a
block inventory saved with its chunk. Minecraft saves the two stores at different
times: a chunk is also written when it unloads, while SavedData is written only
at autosave. Within one autosave the Overworld data storage is saved before the
chunks. Either store can therefore be ahead of the other after a crash.

## Decision

### 1. Logical missions and instances

Resource missions are logical, as ADR-010 made satellite missions logical.
No rocket, entity, structure or BlockEntity is moved into a mission, and an
asteroid **instance** is a registry record with no world region, dimension or
coordinates. Nothing about an instance can be used to probe or load a chunk.
Physical asteroid fields, rocket-carried drills/intakes and station-deployed
rockets are **deferred** to the v1.8 porting matrix (player impact: mining
happens through mission craft, not by flying a rocket to an asteroid).

### 2. Asteroid instances

Record (≤ 1 KiB): `instance_id` (UUID), `owner_id`, `system` (root body ID,
ADR-043), `asteroid_type`, `table_version`, `seed`, `yield` (1..17 entries of
plain item and count, total ≤ 4,096 items, ADR-052), `created_at`, optional
`expires_at`, `state`, `source_mission`, optional `allocated_mission`.

| From | Event | To |
|---|---|---|
| — | survey start | PENDING (hidden from the player) |
| PENDING | survey claim | AVAILABLE, `expires_at = now + TTL` |
| PENDING | survey cancel | removed |
| AVAILABLE | asteroid mission start (owner, same system, not expired) | ALLOCATED |
| AVAILABLE | TTL reached (expiry queue, ≤ 16 per pass) | EXPIRED |
| ALLOCATED | mission claim | DEPLETED |
| ALLOCATED | mission cancel | AVAILABLE if `now < expires_at`, else EXPIRED |
| any | invariant failure (ADR-050 §8) | QUARANTINED |

TTL is a server config value, 24,000..1,728,000 logical ticks, default 168,000
(seven in-game days). DEPLETED and EXPIRED records are removed by the ADR-050
pruning step once 24,000 ticks old. Limits: 16 live (PENDING, AVAILABLE or
ALLOCATED) instances per owner, 2,048 records globally. Every transition happens
in the same registry mutation as its mission transition. Instances are never
shared or traded.

### 3. Survey missions

Start: an OPERATIONAL, idle `survey` satellite owned by the player. Its orbit
body's system is surveyed. The survey creates `instances_per_survey` PENDING
instances (ADR-049), limited to the owner's remaining live capacity; if that is
0 the start fails with `OWNER_LIMIT`. Contents are generated **at start** from the
current asteroid table (ADR-052), so later table changes do not affect them. No
matching type fails with `NO_ASTEROID_TYPES`. Duration: the definition's
`mission_duration_ticks`. Claim at any terminal with the chip (SavedData only, no items).

### 4. Asteroid and gas missions

Common start checks, at a loaded terminal within 8 blocks of the player, with the
craft's chip, the owner, `LAUNCH_POWER_THRESHOLD` terminal energy (checked, not
consumed), the rate limit and the ADR-050 limits. The terminal becomes the
mission's **bound terminal** (its UUID, §5). The reward snapshot is computed at
start (ADR-052) and never recomputed.

- `asteroid`: an OPERATIONAL, idle `asteroid_miner` whose orbit body is in the
  instance's system; the player picks one of their AVAILABLE instances from the
  server-built menu list (sent as an index). The reward is the instance yield
  truncated to the craft's cargo (ADR-052).
- `gas`: an OPERATIONAL, idle `gas_harvester` whose orbit body has
  `gas_giant: true`, is discovered (ADR-037) and has a gas table; the player
  picks a product index from the table. The reward is the ADR-052 gas amount of
  that product (a plain item, such as a filled canister).

### 5. Terminal delivery section

The Satellite Terminal root goes from schema 1 to 2. The ADR-029 inventory (exactly
`Size=6`), preflight bounds, quarantine and raw-root carry are unchanged. New,
bounded fields:

- `terminal_id`: a UUID created once when the block entity is first placed or
  loaded, carried with the raw root when the block is removed as an item;
- `reward_buffer`: ≤ 32 distinct plain item entries, ≤ 3,456 items in total;
- `receipts`: ≤ 256 entries of `mission_id`.

Schema-1 roots load with a new `terminal_id` and empty sections. The root stays
within ADR-029's 64 KiB budget.

### 6. Claim and reconciliation

The registry keeps a **save epoch**. It increases only after the registry file
write returns without error, and every mission records the epoch at its start.

A claim is allowed only at the bound terminal. It needs the chip, a READY
mission, room for the **whole** reward in the buffer
(`DELIVERY_BUFFER_FULL` otherwise), a free receipt slot
(`TERMINAL_RECEIPTS_FULL`), and a registry save after the mission started
(save epoch > start epoch; otherwise `AWAITING_WORLD_SAVE`, retry after the next
autosave). The last rule means that any registry state recovered after a crash
still contains the mission, so a receipt can never outlive its mission record,
except by pruning. In one server tick the claim sets the mission to CLAIMED
(`acknowledged = false`), updates the instance and satellite (ADR-050), and adds
the reward and a receipt to the terminal. The receipt is marked `serialized`
once the terminal's `saveAdditional` has written it; receipts read from disk are
serialized by definition.

Before each terminal action, and on the first tick after the terminal loads,
the terminal reconciles its bound missions (≤ 64 per tick):

| Registry | Terminal receipt | Action |
|---|---|---|
| ACTIVE or READY | present | Registry ran behind the chunk: set CLAIMED, apply the claim side effects, add no items |
| CLAIMED, not acknowledged | present, serialized | Set `acknowledged = true` |
| CLAIMED, not acknowledged | present, not serialized | Wait |
| CLAIMED, not acknowledged | absent | Chunk ran behind the registry: add the reward and receipt again (wait if the buffer is full) |
| CLAIMED, acknowledged | absent or present | Nothing (a present receipt is kept until the record is pruned) |
| record pruned | present | Drop the receipt |

Owners can cancel a resource mission only at its bound terminal (after
reconciliation), and operators by ID. So a mission that paid through a receipt
cannot also be cancelled, and a claim cannot happen twice at different terminals.

**Crash cuts.**

| Crash point | State after restart | Outcome |
|---|---|---|
| Before the claim tick is saved anywhere | READY, no receipt | Claim again; nothing lost |
| Chunk written (unload) before autosave | ACTIVE/READY + receipt + items | Row 1: CLAIMED, no second reward; the instance cannot be reused |
| Start and claim before any registry save | Not reachable | Claim refused with `AWAITING_WORLD_SAVE` until a save |
| Registry saved, chunk not yet (autosave order) | CLAIMED + no receipt | Row 4: reward added once |
| Both saved | CLAIMED + receipt | Normal |
| `saveAdditional` done, region write lost | acknowledged + no receipt on disk | **Residual**: loss of that claim's reward; the same class as a torn vanilla save |

### 7. Extraction

The menu's withdraw intent moves up to one stack from the buffer into the player's
inventory. An extract-only item handler on the terminal's bottom face exposes the
buffer to automation. After materialization, buffer items follow ordinary
container rules; exactly-once is guaranteed up to the bound terminal's buffer.

### 8. Missing terminal

If the bound terminal is gone (the terminal UUID is not found when its last
position is loaded, or the owner reports it), the mission stays READY and shows
`TERMINAL_MISSING`. Only an operator can `mission rebind <id> <terminal>`, for a
READY mission, and that writes an audit line. **Residual**: if the destroyed
terminal had a saved receipt and the registry had reverted to READY, a rebind
pays twice; operators check the audit trail first. A terminal UUID duplicated by
creative NBT copying is outside the guarantee.

## Consequences

- Rewards are materialized at one terminal. Claiming at another terminal is
  deliberately refused, so players must return to the terminal they used.
- The terminal root changes schema; v1.5 hosts quarantine schema-2 roots and keep
  them (ADR-029 behaviour).
- No chunk ticket, forced load or instance coordinate exists.

## Verification

- A0: instance state machine, TTL expiry budget, per-owner and global limits;
  survey generation at start; asteroid/gas start refusals; the reconciliation
  table as a pure function over every row; crash-cut table as fault injection;
  receipt/buffer bounds; terminal root schema 1 → 2 and quarantine.
- A1: survey → instances → asteroid mission → claim → withdraw; gas mission on the
  discovered gas giant; cancel/recycle; claim at a wrong terminal refused;
  buffer-full and receipts-full refusals; zero chunk tickets.
- S1/S2 (C9): force-stop before and after a claim, and after a chunk unload before
  autosave; the reward exists exactly once after each restart.
