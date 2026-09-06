# v1.1.0 migration verification

Date: 2026-09-06. Baseline: `a4ae20190e9f9a36b2a3cbabf1ea44b6746bc7d6`.

## Implemented migration boundary

- Flight data schema 2 persists an exact `current_target`; schema 1 remains
  readable only through the contextual legacy migrator.
- Flight plan schema 3 persists the exact destination target while retaining
  the bounded legacy body/dimension fields needed by transfer adapters.
- Transfer record schema 2 binds source, destination and planned targets into
  its checksum. Schema-1 checksum verification remains byte-compatible.
- An active schema-1 transfer is not rewritten. Recovery first settles the
  original authority semantics; only a `COMMITTED` record may be converted and
  checksum-rebound.
- Shared-Space migration requires a committed station region. Gaps, unknown
  bodies and missing instances stay blocked instead of defaulting to Earth.

## Fixture and automated evidence

`src/test/resources/migrations/v110/v100-committed-earth-moon-transfer-v1.snbt`
is a preserved schema-1 committed transfer record with its original checksum.
`RocketTransferRecordTest` proves that it:

1. loads as an operational schema-1 authority record;
2. rejects target migration before `COMMITTED`;
3. upgrades both nested flight records to schema 2;
4. changes the checksum when typed targets are bound;
5. produces identical first and second schema-2 NBT saves.

The final JUnit suite passed 449/449 tests. The final Forge GameTest run passed
52/52 tests and includes entity schema-1 migration, second-load stability,
station-region migration, shared-Space gap rejection and the existing transfer
recovery scenarios.

## Deliberately unclaimed

This evidence is model/codec and GameTest coverage. It is not a dedicated
server restart of a complete historical player world, and it does not replace
the still-open real-client, multiplayer or long-duration acceptance work.
