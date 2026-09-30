# V160-SCHED-01/02 (C8a-1) verification: write policy, limits, budgets, retention and invariants

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`e7119a8` (C7 closed). Contract: [ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md)
revision 4 §2 and §4–§9, with ADR-049 §10 for intent spacing. C8a-2 (resource
tables, `d15dfcc`) completes C8a. No Gate, candidate or tag.

## Implemented

- **Write policy (§2).**
  - Every state change marks the registry "flush pending", including
    completions, pruning, link changes, scan payments, releases and recoveries.
  - A coalesced flush runs at most once per 100 ticks while a change is pending,
    and a failing one stays pending and is reported once.
  - Clock-only passes set only the ordinary dirty flag.
  - The barriers are unchanged: launches, decommissions, the `data`
    start/claim/cancel and discovery replay.
  - Scheduler completions no longer flush at once.
  - Revision 4 item 1 (every change advances the save epoch) is tested.
- **Queues (§5).**
  - A cancelled, quarantined or lazily claimed mission leaves the deadline
    queue (the C7a finding H2).
  - The queue never holds more entries than unfinished records, and admission
    is checked before any map changes.
  - Loaded over-limit roots keep their queue up to the record bound.
- **Limits (§6).**
  - Ten COMMON config values that can only be lowered (intent intervals only
    raised): unfinished missions 1,024 and 64 per owner, finished per owner
    128, all missions 3,072, satellites 4,096 and 256 per owner, instances
    2,048 and 16 per owner, intents every 10 ticks, selections every 2.
  - Refusals are `CAPACITY_REACHED`, `OWNER_LIMIT`, `STORAGE_BUDGET` or
    `RATE_LIMITED`.
  - Intent spacing applies where client intents arrive (the terminal and
    builder menus), per player, and is forgotten on logout.
- **Budgets (§7).**
  - Per-section byte budgets: satellites 4 MiB, missions 6 MiB, instances
    2 MiB, accounts 1 MiB.
  - Each admitted record reserves the largest encoding it can reach in its
    lifecycle, measured with the record codec (`CodecRecordSizer`): a current
    mission, a receiver link, times, the longest quarantine, and a paying or
    rebound terminal with an acknowledgement.
  - Reservations are released on removal. Restored records are always
    reserved, so an over-budget root loads.
- **Retention (§7).**
  - A finished record is eligible 1,200 logical ticks after it resolved; a
    CLAIMED resource record only once acknowledged with `ack_epoch < E`.
  - Pruning runs for owners above their finished limit and while more than
    1,536 finished records exist, at most 64 removals and 128 inspections per
    pass, oldest first.
  - QUARANTINED records are never pruned.
  - The newest CLAIMED `data` mission that needed discovery is kept per body.
  - Counters are maintained incrementally (`MissionRetention`).
- **Invariants (§9).** One pass on load, planned by `RegistryInvariants`:
  - an unfinished mission without its satellite binding is QUARANTINED
    (`MISSING_SATELLITE`, `SATELLITE_OWNER_MISMATCH`, `SATELLITE_NOT_BOUND`,
    `INSTANCE_MISMATCH`), and the satellite is not changed;
  - a satellite whose current mission is not unfinished becomes
    RECOVERY_REQUIRED;
  - a broken instance reference is QUARANTINED;
  - a missing research account is created.

  The root is no longer blocked for these, which revises the C7a behaviour.
  The changes are logged (`ARCE_SATELLITE_RESTORE`) and flushed with the next
  coalesced flush. Quarantined missions are not scheduled, cannot be claimed,
  and are cancelled or released only by operators; releasing requires the
  invariants to hold.
- **Operator commands (§9).** `/arce satellite admin mission
  inspect|release|cancel|verify <id>`, `satellite recover <id>`, `instance
  inspect <id>` and `diagnostics` (counts, queue size, reserved bytes, save
  epoch, pending flag, coalesced flushes). Each action writes one
  `ARCE_SATELLITE_ADMIN` audit line.
- **Structure (C7-L8).**
  - The registry is split below 800 lines: `MissionRetention`,
    `StorageBudget`, `RegistryInvariants`, `ReceiverLinkIndex`,
    `SchedulerPass` and `RestoreReport` are separate.
  - The scan job returns the domain `SurveyScanResult`; the packet converts
    it.

## Implementation clarifications

1. Instance expiry (§5), `mission purge`, `mission rebind` and `instance
   release` concern instances and resource missions, which are created only in
   C8b; they are delivered there. The retention rule for CLAIMED resource
   records is already implemented.
2. The research-account section reserves a fixed 128 bytes per account.
3. A load-time quarantine or recovery counts as a change, so the epoch advances
   with the next write.

## Tests

- `RegistryLifecycleTest`:
  - cancelled and lazily claimed missions leave the queue;
  - 1,200 claim/start cycles never fill it;
  - explicit limit codes, and a refused launch registers nothing;
  - the byte budget refuses and frees on decommission;
  - per-owner pruning after the replay window keeps discovery evidence;
  - the global prune removes at most 64 per pass down to exactly 1,536;
  - broken references are quarantined or marked for recovery;
  - operator cancel and recover work, while release, claim and owner cancel
    are refused while quarantined;
  - a released quarantine returns to ACTIVE and is scheduled.
- `SatelliteWritePolicyTest`: twelve kinds of change each set "flush pending"
  and make the next write carry E + 1; clock-only passes set only the dirty
  flag; a flush clears the flag.
- `IntentRateLimiterTest`: spacing per player and kind, forgetting on logout,
  and configured intervals.
- `CommonConfigTest`: 19 values; ranges and defaults of the ten new limits.
- GameTests (`SatelliteLifecycleGameTests`):
  - menu intents are spaced per player, at the terminal and the builder;
  - the production tick runs the coalesced flush for a pending link change;
  - in a dedicated batch whose fixture loads a root with broken references,
    operators inspect, fail to release, cancel and recover, and a player
    without permission is refused.
- Changed to the new behaviour, with no assertion weakened:
  - `SatelliteMissionSavedDataTest`: a broken reference now loads as a
    quarantine, which is asserted in detail, instead of blocking; malformed
    lists still block.
  - `SatelliteRootThreeTest`: the over-cap legacy fixture (8,192 missions) is
    built by cloning a real claimed record in the saved root, because the
    live API now admits at most 3,072 and prunes; the same load and budget
    assertions hold.
  - The adapter test `registeredPayloadIsManufacturedLaunchedAndClaimedExactlyOnce`
    spaces its menu intents by 10 ticks; its replayed claim is now also
    asserted to be processed.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew test` and `runGameTestServer` (development runs) | The first full unit run had 3 failures: the config value count; the fail-closed cross-reference test, whose behaviour ADR-050 §9 changes; and the over-cap fixture, whose builder loop the new admission and pruning now limit. All three were updated as described above. The new lifecycle test had two fixture errors: an invalid definition, and a prune expectation that ignored the 64-per-pass bound; no product assertion changed. The first GameTest run had 1 failure, the adapter test's same-tick menu intents, now spaced. These outputs were not captured into the evidence directory; this table is their only record |
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 4m18s. **1,127 JUnit tests / 212 suites executed**, 0 failures. **282 required GameTests** passed. DataGen rewrote nothing (generated diff empty) |

The log has the same 18 intentional ERROR lines as the C7 closure run and 0
FATAL. There is one expected `ARCE_SATELLITE_RESTORE` warning, from the
lifecycle batch's broken-reference fixture. The repository validators, run on
the staged tree after packaging (`packaging/out/validation.log`), all exit 0:
`validate_repository.py --require-approved-identity`,
`validate_v1plus_planning.py`, `validate_bootstrap_provenance.py`,
`python -m unittest tests.test_v1plus_planning` and `git diff --cached --check`.

## Not done in C8a-1

- Resource missions, instances, terminal delivery and the C8b operator
  commands.
- The C9 measurements: flush times at 500 and 1,000 missions, flush frequency
  on a native server, and the chunk-ticket audit.
