# V170-LEDGER-01 (C12b) transit ledger

Date: 2026-10-02. Scope: ADR-054 §9 and §11, the transit ledger that the
railgun (ADR-056, C12c) and the space elevator (ADR-059, C12d) will use. No
device uses it yet, so this slice is verified by unit tests on the models and
the service; the GameTests of C12c and C12d drive it on a running server. No
release Gate is claimed.

Base: `e5b4bb3` (the C11 review packet). Commits on
`codex/v1.7.0-endgame-systems`, one layer each:

| Commit | Layer |
|---|---|
| `f17a134` | `EndgameNbt` moved to `endgame.model`, so the root depends on the transit package and never the reverse |
| `150330d` | Model and pure rules: `TransitKey`, `TransitPayload`, `OutboxEntry`, `TransitRecord`, `TransitRules` |
| `debc047`, `9f6f040` | The root's `transits` section: `TransitTable`, the strict `TransitCodec`, pins, accounting and restore checks |
| `478b353` | Endpoint state: `TransitSourceState`, `TransitDestinationState`, `TransitTags`, `TransitLedgerView` |
| `9d29d99` | The server side: `TransitLedger` (END-tick passes, observations, admission, removal settlement, retirement), the transit codes and two COMMON limits |
| `e4c0e85` | Operator and owner actions: `TransitOperations` and `/arce endgame transfer …`, `/arce endgame endpoint resolve` |
| `fa3f5ae` | CHANGELOG entry |

The evidence run used `fa3f5ae`. The
[implementation log](../v1.7.0-implementation-log.md) ("C12b progress")
records every decision for the C12 review.

## Delivered

| Area | Contract | Result |
|---|---|---|
| Source | ADR-054 §11 source state, dispatch steps 1–3 | `next_seq` from 1, an outbox of at most 4 entries; registration only of the lowest entry, once a chunk tag showed it at least 40 ticks earlier; release once the record is durable; stale drops, `SOURCE_ROLLBACK` and `SEQUENCE_GAP` as audited rows |
| Record | §11 record | `(source, seq)` identity, states `IN_TRANSIT`/`ARRIVED`/`CLAIMED`/`QUARANTINED`, `paid_endpoint`, acknowledgement and its epoch, `redirected`; at most 2.5 KiB (payload ≤ 4 stacks of ≤ 512 B); `dispatched_through` never decreases |
| Delivery and the incoming gate | §11 delivery, finding F02 | Claims into a non-extractable incoming area with a receipt (≤ 64); a payload moves to the receive buffer and is acknowledged only after an aged chunk-tag observation; recovery, rematerialization and receipt drops follow the reference rows |
| Pruning | §11, R3-L6 | A durably acknowledged record drops its payload (a stub) and is pruned once its paid endpoint's chunk, saved at least 40 ticks after the acknowledgement, no longer holds it |
| Removal and retirement | §9, §9.1, R3-M1 | Removal settles from live state in one barrier when anything settles; `MISSING` and `endpoint retire` return unacknowledged claims; a retired endpoint is frozen and resolved item by item |
| Registration freeze | §9, R3-H1 | An unregistered copy whose tag holds outbox entries, incoming payloads or receipts never registers |
| Limits and passes | §7, §10, §11 | 256 records and 32 per owner (COMMON `endgame.transitRecords`, `endgame.transitPerOwner`), stubs and known outbox entries counted; arrivals ≤ 64, registrations ≤ 32 and reconciliations ≤ 64 per tick in the END handler, round-robin in ID order |
| Commands | §11, §13 | `transfer list [page]`, `inspect`, `purge`, `resettle incoming|moved` (operators); `transfer redirect` and `endpoint resolve` for operators and, from their own connected source within the 20-tick player barrier spacing, for owners; every change is an audited barrier |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `fa3f5ae` | Exit 0, 276 s. **321 required GameTests** passed (no new ones: no device uses the ledger yet). `git status --porcelain --untracked-files=no` after DataGen: empty |
| `gradlew test --rerun --no-daemon` | Exit 0, 126 s. **1,334 JUnit tests / 245 suites**, 0 failures, errors or skips |
| API jar against the C11 review close run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| Nine mutations of `TransitRules` (`scripts/mutations.py`), endgame tests each | **9 of 9 killed**; the file was restored byte for byte (SHA-256 `caf01c1e…1868b`) |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**, the same intentional
failure-injection set as the earlier runs (only a GameTest chunk coordinate
differs).

### Mutation checks

Each mutation changes one decision of `TransitRules`; the failing tests are
in `mutations/mutations.json` inside `root-checks.zip`.

| Mutation | Killed by |
|---|---|
| M1 registration without the aged observation | `TransitEndpointStateTest`, `TransitLedgerTest` |
| M2 receipts dropped before the acknowledgement is durable | 8 tests, among them the delivery and transit models |
| M3 registration despite held contents (R3-H1 freeze) | the redirect model with retirement and freeze |
| M4 the incoming gate without the aged observation (F02) | 12 tests, among them the delivery and redirect models |
| M5 a resolve returns every outbox entry of a tombstoned endpoint | `TransitOperationsTest` |
| M6 a resolve keeps a payload claimed at another endpoint | the redirect model |
| M7 a redirect without a durable destination removal (R2-H1) | the redirect model |
| M8 a claim of a record that is not durable | 5 tests, among them the transit models |
| M9 a stub pruned while its paid endpoint still holds the payload | the redirect model |

Three earlier development mutations (logs in `mutations/development/`) were
killed the same way.

## Tests added

- `TransitModelTest` (12): the C10 reference models (`Transit`, `Delivery`,
  `Redirect` of `check_examples.py`) ported to JUnit with every protocol
  decision taken from `TransitRules`. They reach the pinned state counts of
  `examples.json` exactly (72,618; 24,011; 27,578; 1,385 and 33,057; 1,622;
  4,011; 8,122), the same outcome classes and named cuts, and reproduce the
  findings the contracts answer (F02, R2-H1, R3-H1) when their guard is
  removed.
- `TransitCodecTest` (4): payload bounds, a payload of a removed item stays
  raw, strict record and section round trips, pins.
- `TransitEndpointStateTest` (7): the source and destination rows, a
  quarantined outbox payload, conflicts after an operator restore, room
  reserved for whole payloads, the tag scan.
- `TransitLedgerTest` (4): a transfer from escrow to pruning through the tick
  passes, escrow admission with known outbox entries, removal settlement and
  retirement, the registration freeze.
- `TransitOperationsTest` (3): redirect (durable removal first, the route
  rule, owner rules, spacing), purge and resettle, and a resolve that returns
  only what is provably the endpoint's own.
- `CommonConfigTest` counts the two new COMMON values (50 in total) and
  their ranges; `EndgameRootCodecTest` follows the `EndgameNbt` move.

## Not verified

- The ledger on a running server with real endpoints: the railgun (C12c) and
  elevator (C12d) GameTests cover it, and the crash cuts with a railgun pair
  are S2 of C13.
- `transfer` and `endpoint resolve` from a command source: covered with the
  railgun in C12c.
- Ledger cost at the ADR-054 §12 load (C13).

Next: C12c, the ADR-056 railgun on this ledger.
