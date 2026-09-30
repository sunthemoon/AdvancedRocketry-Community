# V160-AST-01, V160-GAS-01, V160-DEL-01 (C8b) verification: resource missions and delivery

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`53d4cf7` (C8a-1). Contract: [ADR-051](../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md)
revision 3, with [ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md)
revision 4 §2–§9 and [ADR-052](../../decisions/ADR-052-RESOURCE-TABLES-AND-SEEDS.md)
revision 2 §3–§7. This closes C8. No Gate, candidate or tag.

## Implemented

- **Instances (ADR-051 §2).**
  - `InstanceLedger` keeps the live count per owner and the owner index. Its
    expiry queue expires AVAILABLE instances at `expires_at` and removes
    DEPLETED or EXPIRED ones 1,200 ticks later. A pass makes at most 16 changes
    and 32 inspections, and runs in the scheduler pass.
  - Every transition happens in the same registry mutation as its mission:
    - survey start → PENDING; survey claim → AVAILABLE for the TTL; survey
      cancel → removed;
    - asteroid start → ALLOCATED; claim or reconciliation → DEPLETED;
    - owner cancel → AVAILABLE, or EXPIRED once past its expiry;
    - operator cancel, the cancel of a QUARANTINED mission, or of a rebound
      one → QUARANTINED;
    - `instance release` → AVAILABLE or EXPIRED. It is refused while the
      allocating mission is unfinished or CLAIMED.
  - Limits: 16 live instances per owner, 2,048 globally. The byte budget and
    the reservation are counted per instance.
- **Survey, asteroid and gas missions (§3–§4).** `ResourceMissions` works on
  the registry; `ResourceMissionService` adapts it to the server.
  - A survey covers the system of its orbit body, recomputed from the current
    tree. It creates `instances_per_survey` PENDING instances, limited to the
    owner's remaining live capacity, from the current asteroid table and a
    server seed (`survey-v1`). With no capacity left it fails `OWNER_LIMIT`;
    with no matching type, `NO_ASTEROID_TYPES`. The duration is snapshotted
    from the kind definition.
  - Asteroid and gas starts check an idle, operational craft of the right
    kind, the owner, the terminal's power (checked, not consumed) and a
    **persisted** terminal ID (`AWAITING_WORLD_SAVE`). That terminal becomes
    the bound terminal.
  - The reward is snapshotted at start and never recomputed:
    - `asteroid-v1`: the instance yield truncated to `cargo × 64`;
    - `gas-v1`: the configured product of the discovered gas giant.
  - `target_body` is the system root for survey and asteroid missions, and the
    gas giant for gas missions.
  - Instance and product selection are server-side previous/next intents.
    The server keeps the selected instance ID, not an index.
  - A removed or changed asteroid type, or a missing gas table, refuses new
    starts (`DEFINITION_NOT_FOUND`); a removed orbit body refuses them with
    `BODY_UNAVAILABLE` (ADR-050 §11).
- **Terminal root schema 2 (§5).** `TerminalDelivery` holds:
  - `terminal_id`, created on placement or on the first load of a schema-1
    root, and carried with the raw root;
  - the reward buffer: at most 32 entries and 3,456 items;
  - at most 256 receipts.

  The decoder is strict. A schema-2 root outside these bounds is quarantined
  and preserved exactly, while a schema-1 root loads with empty sections.
  Withdrawal moves up to one stack per intent into the player's inventory;
  the buffer is not exposed to automation.
- **Persistence signal (§5).** `TerminalChunkEvents` is the only signal:
  - `ChunkDataEvent.Save` marks the live terminal's ID and receipts from its
    entry of the chunk tag, on the main thread, before the file write;
  - `ChunkDataEvent.Load`, which may run off the main thread, records a
    bounded observation in `TerminalObservations`. The terminal consumes it
    when it first ticks in the level;
  - `BlockEntity.load`, carried roots and `/data` never mark anything;
  - while anything is unpersisted, the terminal calls `setChanged()` every
    20 ticks.
- **Claim (§6).** A claim happens only at the bound terminal (otherwise
  `WRONG_TERMINAL`, or `TERMINAL_MISSING` when that terminal is known to be
  gone). It needs a READY mission (completing a due one lazily), room for the
  whole reward (`DELIVERY_BUFFER_FULL`), a free receipt slot
  (`TERMINAL_RECEIPTS_FULL`) and `save_epoch > start_epoch`
  (`AWAITING_WORLD_SAVE`). In one tick it:
  - sets CLAIMED with `paid_terminal`;
  - depletes the instance and releases the satellite;
  - adds the reward and an unpersisted receipt.
- **Reconciliation (§7).** `DeliveryReconciliation` is the pure table. The
  terminal reconciles its bound missions (`TerminalIndex`) and every
  receipt's mission:
  - at most 64 per tick;
  - on load, every 20 ticks, and before each start, claim or cancel. Those
    actions answer `RECONCILING` until the pass completes;
  - the `REBIND_CONFLICT` bind-back is the only barrier flush.

  An acknowledgement records `ack_epoch = E`. Once a later write makes it
  durable, the record re-enters retention, and the receipt is dropped on the
  next pass. A blocked registry changes nothing, refuses resource actions
  (`UNSUPPORTED_DATA`) and keeps receipts.
- **Cancellation and rebind (§8–§9).**
  - The owner cancels an asteroid or gas mission only at its bound terminal,
    after reconciliation; a survey is cancelled at any terminal.
  - Operator commands:
    - `mission rebind <id> <terminal>`: a READY mission, to a loaded
      terminal whose ID is persisted;
    - `mission purge <id>`: a CLAIMED, unacknowledged record;
    - `instance release <id>`.

    Each writes an `ARCE_SATELLITE_ADMIN` line.
- **Audit (§10).** Each claim, rematerialization, acknowledgement, receipt
  drop, conflict (`CLAIM_RECOVERED`, `REBIND_CONFLICT`, `REBIND_DOUBLE_PAY`,
  `PAID_THEN_CANCELLED`, the last two once per receipt), cancel, rebind and
  purge writes one line of at most 512 bytes (`ARCE_MISSION_DELIVERY`). Each
  line has the mission, terminal, save epoch, owner and a 16-hex SHA-256
  prefix of the reward. `mission inspect` now shows the kind payload: for
  resource missions, the bound terminal, `rebound`, the paying terminal, the
  acknowledgement and the last reconciliation. It no longer calls the
  data-only research accessor, which would have failed for the new kinds.
- **Configuration.** Three COMMON values:
  - asteroid instance TTL, 24,000–1,728,000 ticks (default 168,000);
  - asteroid mission time, 10–1,000 % (default 100);
  - gas mission time, 10–1,000 % (default 100).
- **Client.** The Satellite Terminal gains a delivery panel. It shows:
  - the selected instance (type, expiry, yield) or gas product, with
    previous/next buttons;
  - the reward buffer, with a WITHDRAW button;
  - the bound missions, paged eight at a time.

  The screen is 352 px wide. The new codes and labels are generated in
  English and Chinese.
- **Structure.**
  - The registry stays below 800 lines (794): `OperatorRecovery` moved out,
    null checks were folded, and resource work goes through package
    primitives.
  - The terminal block entity is 779 lines, after folding five repeated chip
    checks. Delivery lives in `TerminalDelivery` and
    `TerminalResourceActions`.

## Implementation clarifications

1. **Missing terminal.** The operator's declaration that a terminal is missing
   (ADR-051 §9) is the `mission rebind` itself. The record schema has no
   separate "declared missing" field, so none was added.
2. **Replayed claims.** They keep the existing codes: `ALREADY_CLAIMED` in the
   registry, as for `data`, and `MISSION_NOT_FOUND` at the terminal once the
   satellite is released. Nothing is paid again.
3. **Busy craft.** A start with a busy craft answers `MISSION_BUSY` before any
   instance or product lookup.
4. **One-off audit lines.** `REBIND_DOUBLE_PAY` and `PAID_THEN_CANCELLED` are
   reported once per receipt per load of the terminal; the flag is runtime
   only.
5. **Changed existing test.** One existing GameTest assertion changes with the
   new behaviour: a survey chip without a package now starts a survey instead
   of answering `DEFINITION_NOT_FOUND`. The test now asserts that the survey
   mission exists. No other existing assertion changed except the config
   value count (19 → 22).

## Tests

- `DeliveryReconciliationTest`: all 36 rows of the accepted vectors, and no
  combination outside them.
- `ResourceMissionsTest`:
  - survey generation, visibility, claim, TTL expiry and the 1,200-tick
    removal (the instance budget returns to 0);
  - the live-instance limit, partial surveys, and survey cancel deleting
    PENDING instances;
  - the asteroid claim at its bound terminal only, after a durable start:
    `NOT_READY`, `AWAITING_WORLD_SAVE` (a lazy completion is still a change),
    `WRONG_TERMINAL`, `DELIVERY_BUFFER_FULL`, success, then
    `ALREADY_CLAIMED`, and a depleted instance never pays again;
  - owner, operator and wrong-terminal cancels, and instance release;
  - reconciliation: recovery from ACTIVE, acknowledge, wait, drop, and pruning
    after a durable acknowledgement while an unacknowledged claim stays;
    rematerialization only at the paying terminal; purge;
  - two owners settling in the same tick;
  - **all 64 rebind orderings** of `examples.json`: payments and instance
    state as the vectors say, and exactly 21 double pays (the stated
    residual).
- `DeliveryCrashCutTest` (real registry codec and terminal section, restarts
  from files written at different moments) covers every ADR-051 §11 cut:
  - neither store saved: claim again;
  - chunk ahead: `CLAIM_RECOVERED` with no items;
  - registry ahead: rematerialized once;
  - both saved: acknowledged, then released;
  - blocked registry: nothing changes;
  - reverted claim, then operator cancel: paid once, instance held;
  - lost chunk write after a durable acknowledgement: lost, not duplicated.
- `TerminalDeliveryTest`:
  - round trip, with everything read starting unpersisted;
  - another terminal's tag marks nothing;
  - buffer, entry and receipt bounds and the refusal codes;
  - the strict decoder;
  - pass order and 64-mission batches;
  - chunk-tag parsing that skips other block entities and schema-1 roots.
- `DeliveryRuntimeTest`:
  - observations are bounded and consumed once at their position;
  - audit lines are bounded, with the reward digest;
  - a source audit finds no chunk-ticket API in satellite code.
- `CommonConfigTest`: 22 values; ranges and defaults of the three new ones.
- GameTests (`ResourceMissionGameTests`, in a batch-owned fresh registry):
  - survey → instances → selected asteroid instance → claim → withdraw →
    acknowledgement and receipt release on the production schedule:
    - the real `ServerChunkCache.save` path marks the terminal ID persisted;
    - `mission verify` reports MATCH for the live survey;
    - forced chunks are unchanged;
  - a gas mission refused before the gas giant is discovered, then 576
    canisters over 72,000 ticks (`gas-v1`);
  - cancel and recycle at the bound terminal only (a wrong-terminal claim
    delivers nothing), then an operator cancel holds the instance and
    `instance release` returns it;
  - a full buffer refuses the whole claim;
  - **272 claims at one terminal**: 256 unacknowledged receipts refuse the
    next claim with `TERMINAL_RECEIPTS_FULL`. Durable acknowledgements, not
    pruning, release them, and the finished records stay below 1,536;
  - terminal root schema 1 → 2, and quarantine of a bad delivery section,
    preserved exactly;
  - in the blocked-registry batch, resource actions are refused and the
    receipts kept.

## Commands actually executed

| Command | Result |
|---|---|
| Development runs (`gradlew test`, `runGameTestServer`) | These outputs were not captured into the evidence directory; this table is their only record. **Unit tests:** two fixture errors in new tests (a finished-record count that forgot the claimed survey; a config helper that assumes default = maximum) were corrected. The first crash-cut run failed on the test harness (temp file name, bootstrap), not on product assertions. **GameTest run 1:** 3 failures. (1) A "no forced chunks" check that GameTest structures invalidate; it now compares the forced set before and after. (2) A recycle check that assumed which instance the terminal selects; it now selects the returned instance explicitly. (3) A busy miner answered `INSTANCE_NOT_FOUND`; product change: a busy craft now answers `MISSION_BUSY` first. **GameTest runs 2 and 3:** all 287, then 289, required tests passed |
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 4m14s. **1,152 JUnit tests / 217 suites executed**, 0 failures. **289 required GameTests** passed. DataGen rewrote nothing during the run: the generated diff against the staged tree is empty, and the two new language files are part of this change |

The log has the same 18 intentional ERROR lines as the C8a-1 run and 0 FATAL.
It also has one expected `ARCE_SATELLITE_RESTORE` warning (the lifecycle
batch fixture) and 824 `ARCE_MISSION_DELIVERY` lines: 274 claims, 274
acknowledgements, 274 receipt drops and 2 cancels. The repository
validators, run on the staged tree after packaging
(`packaging/out/validation.log`), all exit 0:

- `validate_repository.py --require-approved-identity`;
- `validate_v1plus_planning.py`;
- `validate_bootstrap_provenance.py`;
- `python -m unittest tests.test_v1plus_planning`;
- `git diff --cached --check`.

## Not done in C8b

The C9 items remain:

- restart and upgrade recovery on a native dedicated server (S1/S2, including
  forced stops around claims);
- flush times at 500 and 1,000 missions, and the flush frequency;
- the chunk-ticket audit on a native server;
- the independent review of C8a and C8b;
- the development handoff.

Visual review of the delivery panel is `[H]`.
