# V160-SAT-03 (C7c) verification: survey area scan and the Microwave Receiver

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`7e736b5` (C7b). Contracts: [ADR-049](../../decisions/ADR-049-SATELLITE-BLUEPRINTS-AND-ASSEMBLY.md)
§7–§10, with the proposed revision-4 amendment (items 5–7 added here). No
Gate, candidate or tag.

## Implemented

- **Survey area scan** (`satellite/scan`).
  - Using a survey satellite's bound chip in hand requests a scan; the client
    sends only the vanilla item use. The server derives the centre from the
    player's block position and requires the player's Level to be the orbit
    body's.
  - Payment from the lazy battery,
    `charge = min(battery, charge + power × Δ)` in saturating 64-bit logical
    ticks (`SatelliteKindState.Survey.chargeAt`). Only a paid scan changes the
    record; every refusal is checked before payment, and a cancelled job is not
    refunded.
  - Limits: one job per player, four on the server, a cooldown from 100 ticks,
    and at most 16,384 block-state reads per tick per job, on the main thread.
    The three limits are COMMON config values that cannot be loosened past
    ADR-049 §8.
  - Reading: `SurveyScanJob` is pure Java over a `ScanColumnSource`, resumable
    at any read. The Level adapter reads only chunks returned by
    `getChunkNow`, so it never loads, generates or tickets a chunk. A cell
    stops at its first unloaded column and is `UNKNOWN`.
  - Result per cell: the `#forge:ores` share of non-air blocks scaled to
    0..65,535, and the dominant biome, sampled at every fourth block. The
    palette holds up to 16 biomes; others are `OTHER`. The result is one S2C
    message (index 1 on the satellite channel); the client opens a read-only map
    when no other screen is open. Results grant nothing.
  - A job ends when its player object is retired (logout, respawn), changes
    Level or moves more than 64 blocks. Jobs and cooldowns are runtime state,
    cleared with the server.
- **Microwave Receiver** (`satellite/receiver`, `SolarLinks`).
  - Four slots for bound chips of `solar` satellites; a `receiver_id` created
    at the first tick.
  - At its 20-tick check, the receiver claims the link of a chip whose
    satellite is unlinked. The existing holder keeps the link until its own
    check no longer finds the chip, or it is broken. A copied chip elsewhere
    shows "linked to another receiver" and produces nothing. A chip whose owner
    differs from the record does not link.
  - Output: `Σ power × solar_intensity(orbit body) × multiplier / 100` FE/t,
    floored and capped at 10,000, into a 100,000 FE output-only buffer pushed
    to neighbours. It is 0 for a body that left the catalog, and nothing is
    produced while unloaded.
  - Breaking the receiver clears its links and drops its chips. Root schema 1
    (`schema_version`, `inventory`, `energy`, `receiver_id`) with the ADR-029
    bounds, quarantine and exactly-once carry.
  - The receiver menu uses open-data format 1 (position, link statuses, output)
    and read-only live data (the energy in two 16-bit halves).
- **Receiver-missing detection and unlink.**
  - A runtime `ReceiverDirectory` records the last position of every receiver
    seen since the start. A link's receiver is *missing* only if that chunk is
    loaded and has no receiver with that ID; otherwise it is unknown.
  - Decommission (C7b) now uses this rule.
  - The terminal's new `UNLINK` intent (button 7) lets the owner clear a
    missing receiver's link. `/arce satellite admin unlink <satellite_id>`
    clears any link. Both log `ARCE_SATELLITE_UNLINK`.
- **Registry.**
  - `payScan`.
  - A receiver-link index maintained on every satellite change and rebuilt on
    restore, never persisted.
- **Terminal screen** gains a second button row; the player inventory moves
  down 18 pixels, on both sides of the menu.
- **DataGen**: the receiver model (existing project textures plus a referenced
  vanilla texture), loot, recipe, tool-tag entries, and the scan and receiver
  labels in both locales.

## Tests

New unit tests:

- `SurveyScanJobTest`:
  - the worst case (radius 48, cell 4, 384 blocks high) takes exactly 216
    steps of 16,384 reads;
  - resuming in 37-read steps gives the same grid;
  - the exact ore share of non-air blocks;
  - an unloaded column makes its cell `UNKNOWN` after one read;
  - the 16-entry palette then `OTHER`, and biome ties;
  - invalid geometry and budget.
- `SurveyScanResultPacketTest`:
  - measured `ARCE_SCAN_RESULT_MAX_BYTES=3819` (bound 8,192), pinned;
  - strict decoding (trailing bytes, truncation, oversize, geometry, palette
    size, dangling palette index, `UNKNOWN` with a ratio);
  - constructor bounds.
- `SatelliteScanAndLinkRegistryTest`:
  - lazy battery (cap, no backwards time, saturation, zero power);
  - payment only when covered, with no change on refusal;
  - owner, operator and kind checks;
  - the link index through claims, moves, restore, unlink and decommission.

New GameTests (`satellite` batch):

- `SurveyScanGameTests`:
  - a paid scan costs exactly the scan energy;
  - a second request while running and the cooldown are refused;
  - the scan finishes once, with the server-derived centre and snapshot
    geometry;
  - the centre cell's ore share equals a direct count of the same columns in
    the same tick, including three placed ores;
  - refusals before payment: `NO_POWER`, `TARGET_NOT_ALLOWED`,
    `BODY_UNAVAILABLE`, `SATELLITE_NOT_FOUND`, `UNAUTHORIZED` and
    `CAPACITY_REACHED`;
  - moving 100 blocks cancels the job without a refund.
- `MicrowaveReceiverGameTests`:
  - a free link is claimed at the check, and a power-4 satellite over Earth
    gives 4 FE/t;
  - a copied chip in a second receiver is `ELSEWHERE` with 0 output;
  - decommission and the owner's unlink are refused while the holder is
    present;
  - the buffer is output-only;
  - removing the chip passes the link to the other holder within two checks;
  - breaking the holder clears the link and drops the chip;
  - an unknown receiver blocks the owner's unlink and decommission, a
    confirmed-missing one allows both, and unlinking twice is idempotent;
  - the operator command unlinks by ID;
  - a future receiver root is preserved, blocked and carried exactly once.

Changed to exact new values, with no assertion removed or loosened:

- `CommonConfigTest`: nine values, plus a range test for the three scan limits;
- `NetworkProtocolPinTest` table: gains
  `advancedrocketrycommunity:satellite|1|1|SurveyScanResultPacket|PLAY_TO_CLIENT`;
- `ModMetadataTest`: the new description.

## Commands actually executed

| Command | Result |
|---|---|
| `gradlew compileJava`, `runData`, and `test`/`runGameTestServer` for the new tests (iterative development runs) | All passed on their first run (271 GameTests). Afterwards the receiver's immediate check on an inventory change was removed, so that links change only at the 20-tick check as ADR-049 §9 states, and the receiver GameTest waits were lengthened to match. These iterative outputs were not captured into the evidence directory; this table is their only record |
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 3m55s. **1,102 JUnit tests / 206 suites executed**, 0 failures. **271 required GameTests** passed. The v1.6 DataGen output was staged before the run; DataGen rewrote it byte-identically (generated diff empty) |

The log has 15 ERROR lines and 0 FATAL, the same set as C7a and C7b: the
intentional failure-injection GameTests and the missing `server.properties`.

## Repository validators

Run on the staged tree after packaging (log kept outside the archive in
`packaging/out/validation.log`): `validate_repository.py
--require-approved-identity` (45 passed, 0 failed), `validate_v1plus_planning.py`,
`validate_bootstrap_provenance.py`, `python -m unittest tests.test_v1plus_planning`
and `git diff --cached --check`, all exit 0.

## Not done in C7c

- The ADR-050 write policy (flush pending, coalescing), the per-player intent
  rate limits and the budgets arrive in C8a. Here a paid scan or a link change
  marks the registry dirty.
- No chunk-ticket audit on a native server and no V1 visual check of the scan
  map, receiver and terminal screens (C9, `[H]`).
- The C7 independent review follows this commit; ADR-049 revision 4 stays
  **PROPOSED** until then.
