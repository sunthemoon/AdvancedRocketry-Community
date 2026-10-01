# v1.7.0 contract review round 1 — dispositions

Round 1 reviewed revision 1 at `10e3d2d` and found 0 Critical, 3 High,
12 Medium, 14 Low and 4 Info findings. Verdicts: ADR-053 ACCEPT; ADR-054..059
ACCEPT WITH REQUIRED CHANGES. The unmodified report is archived with the
preparation evidence. Each answer below is its own commit, so it can be checked
alone; revision 2 is the result of all of them.

Before the report arrived, root found four issues of its own (S1–S4), and the
external v1.3–v1.6 deep-test report of 2026-10-01 (finding F02) showed that the
ADR-051 delivery shape duplicates a reward a player withdraws before the
terminal's chunk is saved; ADR-054 §11 reused that shape. These are answered
first.

## Root and external findings

| ID | Finding | Answer | Commit |
|---|---|---|---|
| S1 | ADR-055: "at most one unpaid layer per crash" is wrong | The gap is every layer between the two chunks' latest saves | `1175907` |
| S2 | ADR-054 §12 / ADR-059 §8: the ride arrival needed an unstated chunk load | Superseded by R1-M4 | `1175907`, `62b82d3` |
| S3 | ADR-055: tool tiers unstated | Harvest-tool requirements are not applied | `1175907` |
| S4 | The v1.2 port/binding machinery is not needed | Structure-only multiblocks (ADR-054 §2.1) | `1175907` |
| F02 | Third-store duplication after withdrawal | Claims land in a non-extractable incoming area until persisted; third-store model | `7e9e231` |

## Round 1 findings

| ID | Severity | Answer | Verification | Commit |
|---|---|---|---|---|
| R1-H1 | High | `dispatched_through` stays as a tombstone until the source's absence is persisted and aged 6,000 ticks, removed only under table pressure; registration takes the lowest entry, a gap registers with `SEQUENCE_GAP`; `endpoint purge` keeps the tombstone | Model with source removal and tombstones: exactly once without faults; the only unaudited duplicate needs a lost absence write (documented); new cut and mutation | `1752c87` |
| R1-H2 | High | Same as F02: claimed items are not extractable until the receipt is persisted (and, with R1-M1, aged) | Third-store model: no duplicate or loss without a container/player torn save; the ungated shape reproduces F02 | `7e9e231`, `552e633` |
| R1-H3 | High | §9.1 removal by any cause: wither/dragon immune, blast resistance 1,200; outbox entries never drop (registered delivered, unregistered destroyed and audited); incoming claims settled per record from live state with a barrier flush; the move acknowledges | Forced source removal and destination removal with redirect in the models; the model found and the text fixes a first-draft double payment; three new mutations | `2b03ff0` |
| R1-M1 | Medium | 40-tick age for the move and the acknowledgement; destination lost-write residuals listed | Destination fault in the model: every other non-exact outcome needs it | `552e633` |
| R1-M2 | Medium | `ManagedSavedDataType.ENDGAME` with its own schema and epoch cases; `AtomicSavedData` allowlist widened; bad roots refuse world start like every managed file | Text against `WorldDataMigrationService` and `ManagedSavedDataType` | `3b6296c` |
| R1-M3 | Medium | Per-action override table: ride/ship need station `VISIT` and an anchor owner who is the station owner or a member; bind and unbind owners; selection lists show the device owner's endpoints | Nine new authority vectors | `7318e12` |
| R1-M4 | Medium | Expiring `elevator_arrival` pre-load ticket during the countdown (≤ 64, 300 ticks), no synchronous load, `ARRIVAL_UNLOADED` after 100 ticks; ADR-053/054 state exactly which tickets exist | Ticket assertions in the verification lists | `62b82d3` |
| R1-M5 | Medium | `EndgameEffectEvent(TELEPORT)` and zones checked at ride commit | — | `62b82d3` |
| R1-M6 | Medium | `ElevatorStationGuard` port, fail closed with `ENDGAME_UNAVAILABLE` until installed or while the root is not operational, independent of the elevator switch | — | `3b6296c` |
| R1-M7 | Medium | Debt paid layer by layer; credit waits while disabled; relink after `LINK_LOST` or a removed/`MISSING` marker with `LINK_ABANDONED`; `MARKER_RESET` audited | Counter model without the unlimited top-up: 97,656 sequences settle; a 300,000 FE debt settles at 1,000 % | `1a3361e` |
| R1-M8 | Medium | Marker at local 2..13; the 3 × 3 surrounding chunks `FULL`; black-hole push skips absent neighbours; where work runs; `getChunkNow` before foreign reads | Vectors check every direct neighbour of every cell is in the marker's chunk | `d241647` |
| R1-M9 | Medium | Outside stations a field affects only its owner and an allow list; inside stations everyone in the station owner's field; ≤ 4 fields of one owner per chunk | Jump-height and consent vectors | `c661710` |
| R1-M10 | Medium | One S2C-only device-view message (≤ 8 KiB) on a new `endgame` channel; generated endpoint labels | — | `c8c0204` |
| R1-M11 | Medium | Endgame reference load, numeric budgets on total tick time, 50 ms barrier-flush gate, barrier spacing (20 ticks server-wide, 100 per station) | Measured in C13 | `27f7dee` |
| R1-M12 | Medium | ADR-054 §16: nine public IDs, recipes from existing items, progression gates, DataGen location | — | `e6e072a` |
| R1-L1..L14 | Low | L1, L2, L3, L5–L12, L14 in one commit; L4 with R1-M8; L13 with R1-M3 and R1-M4 | Railgun vector at 400 % | `b156191` |
| R1-I1 | Info | The models now cover source and destination removal, tombstones, destination lost writes, extraction by a third store and the capped counter buffer | 23 checks; 12 protocol mutations caught (one only under a lost write) | several |
| R1-I2 | Info | The source-side lost-write residual stays documented; `ChunkStorage.flushWorker()` is noted as a possible narrowing if C13 measurements justify it | — | — |
| R1-I3 | Info | The v1.6 terminal withdraw has the F02 pattern; it belongs to the v1.3–v1.6 fixes in progress and will be reviewed against ADR-054 §11 when they land | — | — |
| R1-I4 | Info | Verified claims; no change | — | — |
