# ADR-051 — Resource mission instances, gas harvesting and reward delivery

```yaml
status: PROPOSED
revision: 2
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.6.0
slices: [V160-AST-01, V160-GAS-01, V160-DEL-01]
related: [ADR-010, ADR-029, ADR-037, ADR-043, ADR-049, ADR-050, ADR-052]
```

Revision 1 was rejected by the first independent review. Revision 2 answers
H3, H5, H6, H7, M1, M2, M3, M16 and L8–L11; see the
[preparation evidence](../work/v1.6.0-preparation/VERIFICATION.md).

## Context

In the legacy game a rocket with drills or intakes deletes itself into a mission
record that holds its whole inventory. At completion it respawns with ore in its
chests, or with 64,000 mB in every tank; gas collection also force-loads the
launch dimension, and rewards use `Math.random()` (see the
[audit](../work/v1.6.0-legacy-audit.md)). v1.6 must deliver asteroid and
gas-giant missions with controlled, reproducible and conserved rewards. It must
allocate and recycle instances transactionally, and never force-load a chunk or
reveal instance coordinates.

Mission state is in the registry (ADR-050), but item rewards must end up in a
block inventory that is saved with its chunk. The two stores are written
independently, and either can be ahead after a crash:

- the registry: barrier flushes on `data` paths, a coalesced flush at most every
  100 ticks for everything else, autosave and shutdown (ADR-050 §2);
- chunks: up to 20 dirty chunks per tick through `ChunkMap.processUnloads`
  (10-second cooldown), on unload, and at autosave. Serialization
  (`ChunkSerializer.write`, then `ChunkDataEvent.Save`) happens on the main
  thread; the file write is asynchronous through `IOWorker`;
- within one autosave, the Overworld data storage is saved before that level's
  chunks.

A terminal's chunk is therefore normally written within seconds of a claim,
often before the registry.

## Decision

### 1. Logical missions and instances

Resource missions are logical, as ADR-010 made satellite missions logical. No
rocket, entity, structure or BlockEntity moves into a mission. An asteroid
**instance** is a registry record with no world region, dimension or coordinates,
so nothing about it can be used to probe or load a chunk. Physical asteroid
fields, rocket-carried drills or intakes, and station-deployed rockets are
**deferred** to the v1.8 porting matrix. The player impact: mining happens
through mission craft, not by flying a rocket to an asteroid.

### 2. Asteroid instances

Record (≤ 4 KiB, ADR-050 §3): `instance_id`, `owner_id`, `system` (root body ID,
ADR-043), `asteroid_type`, `table_version`, `candidate_fingerprint`, `seed`,
`yield` (1..17 entries, ≤ 4,096 items, ADR-052), `created_at`, optional
`expires_at`, `state`, `source_mission`, optional `allocated_mission`.

| From | Event | To |
|---|---|---|
| — | survey start | PENDING (hidden) |
| PENDING | survey claim | AVAILABLE, `expires_at = now + TTL` |
| PENDING | survey cancel | removed |
| AVAILABLE | asteroid start (owner, same system, not expired) | ALLOCATED |
| AVAILABLE | TTL reached (expiry queue) | EXPIRED |
| ALLOCATED | mission claim or reconciliation to CLAIMED | DEPLETED |
| ALLOCATED | owner cancel after reconciliation | AVAILABLE if not expired, else EXPIRED |
| ALLOCATED | operator cancel, or cancel of a QUARANTINED mission | QUARANTINED |
| QUARANTINED | operator `instance release` | AVAILABLE or EXPIRED |
| any | invariant failure (ADR-050 §9) | QUARANTINED |

TTL is server config, 24,000..1,728,000 logical ticks, default 168,000. DEPLETED
and EXPIRED records are removed 1,200 ticks later by the expiry queue. Limits:
16 live (PENDING, AVAILABLE or ALLOCATED) instances per owner, 2,048 records
globally. Every transition happens in the same registry mutation as its mission
transition. Instances are never shared or traded.

### 3. Survey missions

A survey starts from an OPERATIONAL, idle `survey` satellite owned by the player.
It surveys the system that contains its orbit body. It creates
`instances_per_survey` PENDING instances (ADR-049), limited to the owner's
remaining live capacity; with no capacity left the start fails `OWNER_LIMIT`.
Contents and the candidate fingerprint are generated **at start** from the
current asteroid table (ADR-052); `NO_ASTEROID_TYPES` if nothing matches.
Duration is the definition's `mission_duration_ticks`, snapshotted. A survey is
claimed at any terminal with the chip. The claim is registry-only; no items move.

### 4. Asteroid and gas mission starts

Common checks: a loaded terminal within 8 blocks of the player, the craft's chip,
the owner, `LAUNCH_POWER_THRESHOLD` terminal energy (checked, not consumed), the
rate and ADR-050 limits, and a **persisted terminal ID** (§5); otherwise
`AWAITING_WORLD_SAVE`. That terminal becomes the mission's **bound terminal**.
The reward snapshot is computed at start (ADR-052) and never recomputed. The
instance or product is chosen through server-side menu selection (previous/next
intents, ADR-049 §10), so no packet carries an index into a list that could have
changed.

- `asteroid`: an OPERATIONAL, idle `asteroid_miner` whose orbit body is in the
  instance's system, and one of the player's AVAILABLE instances. The reward is
  the instance yield truncated to the craft's cargo (ADR-052).
- `gas`: an OPERATIONAL, idle `gas_harvester` whose orbit body has
  `gas_giant: true`, is discovered (ADR-037) and has a gas table, and one of that
  table's products. The reward is the ADR-052 `gas-v1` amount of the product.
  Filled canisters are minted from nothing, bounded per mission like asteroid
  ore; no empty canisters are consumed. This is a balance decision, recorded here.

### 5. Terminal delivery section

The Satellite Terminal root moves from schema 1 to 2. The ADR-029 inventory
(exactly `Size=6`), preflight bounds, quarantine, raw-root carry and the
existing item-handler exposure are unchanged. New bounded fields:

- `terminal_id`: a UUID created on placement, or on the first load of a schema-1
  root, and carried with the raw root when the block is taken as an item. It is
  **persisted** once a `ChunkDataEvent.Save` tag for the terminal's chunk has been
  seen containing it. Until then no resource mission can bind to the terminal;
- `reward_buffer`: ≤ 32 distinct plain item entries, ≤ 3,456 items in total,
  counted in items (extraction splits by each item's stack size);
- `receipts`: ≤ 256 entries of {`mission_id`, `serialized`}.

Withdrawal in v1.6 is menu-only: the withdraw intent moves up to one stack into
the player's inventory, and whatever does not fit stays in the buffer. The buffer
is not exposed to automation in v1.6. Schema-1 roots load with empty sections.
The root stays within ADR-029's 64 KiB.

### 6. Claim

A claim is allowed only at the bound terminal, after its reconciliation (§7). It
needs the chip, a READY mission, room for the **whole** reward
(`DELIVERY_BUFFER_FULL`), a free receipt slot (`TERMINAL_RECEIPTS_FULL`) and
`save_epoch > start_epoch` (ADR-050 §2), otherwise `AWAITING_WORLD_SAVE`.
Because of the last condition, every registry file that can be loaded after a
crash contains the mission. With the 100-tick coalesced flush, the wait is at
most about 100 ticks after the start.

In one server tick the claim sets the mission to CLAIMED
(`acknowledged = false`), updates the instance and the satellite, and adds the
reward and an unserialized receipt to the terminal. A receipt becomes
`serialized` only when a `ChunkDataEvent.Save` tag for the terminal's chunk
contains that receipt. Other callers of `saveAdditional` (the carry path,
`/data`, structure capture, other mods) never set it.

### 7. Reconciliation

The registry keeps an index from terminal ID to bound missions. When a terminal
loads, and before each resource action, it reconciles its bound missions and
every mission named by its receipts, ≤ 64 per tick. Resource actions at that
terminal wait until the pass completes. The table covers every registry state
and receipt state:

| Registry state of the mission | Receipt at this terminal | Action |
|---|---|---|
| Registry not operational (blocked) | any | Nothing; resource actions refused |
| ACTIVE or READY, bound here | present | Registry was behind: set CLAIMED, apply side effects, no items; audit `CLAIM_RECOVERED` |
| ACTIVE or READY, bound elsewhere (rebound) | present | Set CLAIMED, no items; audit `REBIND_CONFLICT` |
| CLAIMED, not acknowledged, bound here | serialized | Set `acknowledged`, `ack_epoch = E` |
| CLAIMED, not acknowledged, bound here | unserialized | Wait |
| CLAIMED, not acknowledged, bound here | none | Chunk was behind: add the reward and an unserialized receipt (wait while the buffer is full); audit `REMATERIALIZED` |
| CLAIMED, acknowledged | present | Drop the receipt once `ack_epoch < E`; audit `RECEIPT_DROPPED` |
| CLAIMED, bound elsewhere (paid at another terminal) | present | Keep the receipt; audit `REBIND_DOUBLE_PAY` once |
| CANCELLED | present | Keep the receipt, add nothing; audit `PAID_THEN_CANCELLED` once |
| QUARANTINED | present | Keep the receipt; record `receipt_seen` on the quarantine for the operator |
| Absent (operational registry) | present | Drop the receipt; audit `RECEIPT_DROPPED` |
| any state | none | Nothing (apart from the rematerialize row) |

"Absent" means pruned. The epoch rule keeps every claimed mission in any
recoverable file, and resource-mission records are deleted only by pruning,
which needs a durable acknowledgement (ADR-050 §7). Receipts are released by
the durable-acknowledgement row, independently of pruning, so a terminal does
not fill up on a server that rarely prunes.

### 8. Cancellation

The owner cancels an asteroid or gas mission only at its bound terminal, after
§7. Operator cancels and cancels of QUARANTINED missions move an allocated
instance to QUARANTINED (ADR-050 §8). A mission paid through a receipt therefore
cannot free its instance for a second reward.

### 9. Missing terminal and rebind

A terminal is **missing** when the chunk at its last recorded position is loaded
and holds no terminal with that ID, or when an operator declares it missing. The
mission stays READY and shows `TERMINAL_MISSING`. Only an operator can run
`mission rebind <id> <terminal>`, for a READY mission, to a terminal whose ID is
persisted. The audit line records the old terminal. If the old terminal comes
back with a receipt, §7 records `REBIND_CONFLICT` or `REBIND_DOUBLE_PAY`.
**Residual**: a destroyed terminal that had paid, and never returns, followed by
a rebind, pays twice. The audit lines make this visible, but cannot prevent it.

### 10. Audit lines

One bounded log line (≤ 512 bytes, `ARCE_MISSION_DELIVERY`) for each claim,
rematerialization, acknowledgement, receipt drop, conflict, cancel, rebind and
purge. Each carries the mission ID, terminal ID, `save_epoch`, owner and a
SHA-256 prefix of the reward snapshot. `mission inspect` shows the bound
terminal, the receipt and acknowledgement state, and the last reconciliation
result.

### 11. Crash cuts

| Cut | State after restart | Outcome |
|---|---|---|
| Claim tick not persisted in either store | READY, no receipt | Claim again; nothing lost |
| Chunk saved (incremental save or unload), registry not | ACTIVE/READY + receipt | CLAIM_RECOVERED; the instance is DEPLETED and cannot pay again |
| Registry flushed, chunk not | CLAIMED, not acknowledged, no receipt | Rematerialized once |
| Both saved | CLAIMED + receipt | Acknowledged after an observed chunk save; receipt dropped after the next flush |
| Start and claim before any registry write | Not reachable | Claim refused with `AWAITING_WORLD_SAVE` |
| Registry blocked at load | Blocked | Nothing happens; receipts kept for later |
| Registry reverted, then an operator cancels | CANCELLED + receipt | Paid once; instance QUARANTINED, not reusable |
| **Residual**: `IOWorker` write lost after `ChunkDataEvent.Save` and after the acknowledgement was flushed | Acknowledged, no receipt | The reward is lost (never duplicated); same class as a torn vanilla save |

## Consequences

- Rewards are materialized at one terminal. Claiming elsewhere is refused, and
  players see which terminal a mission is bound to.
- The terminal root changes schema. v1.5 hosts refuse the world anyway, because of
  the registry root 3 (ADR-050 §10).
- No chunk ticket, forced load or instance coordinate exists.

## Verification

- A0: instance state machine, TTL and expiry budget, limits; survey generation at
  start; start refusals; the reconciliation function over **every** row
  (`examples.json`); crash cuts by fault injection; receipt and buffer bounds;
  `serialized` set only from a chunk-save tag; terminal ID persistence before
  binding; terminal root schema 1 → 2 and quarantine; audit-line bounds.
- A1: survey → instances → asteroid mission → claim → withdraw; a gas mission on
  the discovered gas giant; cancel and recycle; a wrong-terminal claim refused;
  buffer-full and receipt-full refusals; more than 256 claims at one terminal
  with fewer than 1,536 finished records; zero chunk tickets.
- S1/S2 (C9): forced stops after an incremental chunk save and before the
  coalesced flush, after a flush and before the chunk save, and around an operator
  cancel. After each restart the reward exists exactly once and no instance pays twice.
