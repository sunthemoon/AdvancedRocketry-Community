# C11 independent review: findings, dispositions and fixes

Date: 2026-10-02. Branch `codex/v1.7.0-endgame-systems`. Reviewed commit:
`8d52d7f` (C11a–C11d, ADR-054 framework, ADR-055 laser drill, ADR-058 gravity
field). The fixes are committed on top of `6ff9d27` (the C12a evidence
commit), one commit per finding. No Gate, candidate or tag.

## Round 1

An independent, read-only reviewer read the accepted contracts, the
implementation log, the four C11 evidence packets and every C11 production
class and test, checked the vanilla and Forge behaviour it relied on against
the mapped Forge 47.4.10 jar, and ran the suites plus nine review-only probe
GameTests and a benchmark in an exported copy (1,290 JUnit tests, 303
GameTests and the probes passed; each probe asserted the suspected
behaviour, so a pass confirmed a finding). Its report, probe sources, probe
results and logs are archived in `independent-review.zip` (its copy of the
repository is excluded).

**Verdicts:** C11a accept with required changes (M3, M5); C11b reject (C1,
H2, M2, M4); C11c reject (C1, H1, H2, M1); C11d accept. Critical 1, High 2,
Medium 5, Low 8, Info 11. The reviewer noted that the fixes are local and
need no redesign.

## Dispositions

| Finding | Disposition |
|---|---|
| **C11R-C1** A partial shift-click out of the drill output or a marker buffer was never persisted (duplication) | **Fixed** in `e56eec4`. Device menu slots are `EndgameItemSlot`s that forward `setChanged()` to the owning block entity (Forge's `SlotItemHandler` hands out the live stack and its `setChanged()` reaches a dummy container). Covers the drill lens and output, the marker buffer and the black-hole generator fuel (C12a). GameTests save a chunk after a partial move and read the stored count, and check partial moves and merges |
| C11R-C1, other menus | **Fixed** in `bcce9c4`. The review asked to check every `SlotItemHandler` menu: the satellite terminal and builder (v1.5/v1.6) hold up to 64 redstone in their charge slots and had the same defect. The machine menus write moved stacks back with `set`, and the microwave receiver holds one item per slot, so neither was affected. GameTest for both menus |
| **C11R-H1** Blocks hanging on an earlier cell of a physical layer were counted twice | **Fixed** in `0cffc08`. Cells without a full collision shape go first, then the rest; a cell a neighbour reaction of the same layer already destroyed is skipped with its planned drops. GameTest with a wall torch, a ladder and a button |
| **C11R-H2** Menu slots ignored `WITHDRAW` | **Fixed** in `4f5556e`. Taking needs `WITHDRAW` and putting needs `CONFIGURE`, decided on the server for the viewer at every click and shift-click; a data slot shows the decision. GameTests: a stranger at a laser target outside stations and a station member at the owner's drill take and put nothing; the owners can |
| **C11R-M1** The 8-entry revoked-link list let an old link return with free credit | **Fixed** in `c87b71f`. A monotone marker `generation` replaces the list; the link records the generation of its first contact. GameTest (13 resets) and `LaserLinkTest` |
| **C11R-M2** `device owner` left the endpoint record with the old owner | **Fixed** in `bbae140`. The record is reassigned first, as a barrier within the new owner's limit, then the block entity; a laser target given to another owner resets its link. GameTest |
| **C11R-M3** A refused `endpoint forget` forced root writes and audit lines | **Fixed** in `490e2bc`. Refusals are decided on the read view and change, write and audit nothing; another player's record reads as not found; a successful forget is coalesced. GameTest |
| **C11R-M4** `WITHDRAW` was unreachable in an unavailable station | **Fixed** in `c5de66a`. A menu opens and stays open with `VIEW`, or with `WITHDRAW` alone where `VIEW` is refused; that menu only takes items and shows the refusal code. GameTest after deleting the drill's station |
| **C11R-M5** The chunk index was rebuilt per mutation; housekeeping ran every tick | **Fixed** in `e8540b6`. The root reports the IDs each mutation touched and the index moves only those; housekeeping runs at most once per 200 ticks and counts without copying. The reviewer's benchmark on the fix: 8,192 settled tombstones and 1,024 endpoints, idle tick 0.008 ms and a no-op call 0.001 ms (were 1.26 and 0.95 ms). Unit test: the index equals a full rebuild after every kind of mutation |
| C11R-L1 Effect and break events posted twice per layer | **Fixed** in `a1bf266`. The layer grant is taken before planning. The hanging-blocks GameTest counts 9 break events for 9 cells |
| C11R-L2 The counter tests proved a model the engine did not use | **Fixed** in `53af094`. The engine takes every step from `LaserLinkCounters.next` and every contact decision from `LaserLinkCounters.accepts`; model tests for resets and relinks; a GameTest drives the debt and credit cuts on the engine |
| C11R-L3 Crash paths from over-long IDs | **Fixed** in `f849173`. Registration refuses an over-long kind or Level key (`TARGET_OUT_OF_BOUNDS`); table and black-hole data IDs over 128 characters fail the reload; view text is cut to its byte bound. Unit tests |
| C11R-L4 A 32-bit Level hash could retire a live endpoint | **Fixed** in `67a3635`. Live records, young tombstones and candidates are keyed by the full Level key; only settled tombstones keep the hash. Unit test with two colliding keys |
| C11R-L5 Registration relied on incidental saves | **Fixed** in `2038307`. An awaiting marker marks its chunk changed at every status check; the unload save, which vanilla writes before removing block entities, then registers it. GameTest |
| C11R-L6 The trust list had no schema version | **Fixed** in `4c7c707`. `{schema_version: 1, owners: [...]}`; the unversioned development list still reads. GameTests |
| C11R-L7 Missing negative tests and ticket counts | **Fixed** with the tests above and `a8cf76d`: a physical `TARGET_UNLOADED` GameTest, and ticket counts by type around the systems' chunks in the physical-shaft and gravity GameTests |
| C11R-L8 An unbounded results map; unpaged lists | **Fixed** in `199e351`. Results are kept only for refusals; `zone list [page]`, `endpoint list [player] [page]`. Unit test and GameTest |
| C11R-I1 `hasNeighborSignal` without `getChunkNow` | **Accepted as is.** Block-entity tickers run only in block-ticking chunks, whose neighbours are `FULL`, so the read never loads a chunk |
| C11R-I2 Client-side identity in `saveAdditional` | **Accepted as is.** Harmless: `onlyOpCanSetNbt` and placement re-randomise the ID |
| C11R-I3 One audit line per physical layer | **Recorded decision** (implementation log); bounded by 7 layers per tick |
| C11R-I4 The danger confirmation is in a hover tooltip | **Left to the V1 `[H]` check** of the drill screen |
| C11R-I5 A field indexed while its chunk is FULL but not ticking | **Accepted as is.** Reachable only with non-ticking chunk loaders, where no player can stand in the box |
| C11R-I6 Housekeeping evicts in settlement order | **Accepted as is.** A tombstone settles when its observed absence ages, so settlement order is observation order; operator `tombstone settle` counts from the command |
| C11R-I7 The quarantine line was unbounded and outside the ring | **Fixed** in `f103e18` for the bound (256 characters, one line). It stays a server log line as ADR-054 §2 names it, because the device package does not depend on the service that owns the ring |
| C11R-I8 `endpoint forget` accepted `/execute as` | **Fixed** in `8c5e94d` (the shared own-source helper of `field trust`). GameTest |
| C11R-I9 Gravity particles ignore consent | **Accepted as is** (cosmetic, client side) |
| C11R-I10 The tree moved during the review | **Re-checked.** C1, H2 and M4 are fixed in the shared device menu code, which the C12a black-hole generator uses too |
| C11R-I11 Validators need a Git worktree | Not applicable to the repository; the packets record the validator runs |

## Tests added or changed

- GameTests: `EndgameMenuGameTests` (partial moves out of a marker, the drill
  output and merges into generator fuel; a stranger and a station member
  with `VIEW` only; withdraw in an unavailable station),
  `SatelliteMenuPersistenceGameTests`, `LaserTargetGameTests` (generation,
  owner change, refused forget, awaiting marker, zone pages),
  `LaserPhysicalGameTests` (hanging blocks with break-event count, debt and
  credit cuts, unloaded target, ticket counts), `GravityFieldGameTests`
  (versioned trust list, ticket counts); `TicketCounts` test support.
- Unit tests: `LaserLinkTest`, `LaserLinkCountersTest` (contact rule),
  `EndgameServiceTest` (incremental index, colliding Level keys,
  registration results), `EndgameRootTest`, `EndgameDeviceViewTest`,
  `LaserDrillTablesTest`.
- One existing assertion changed: the gravity GameTest reads the trust list
  in its new versioned form (the check is stricter). No assertion was removed
  or loosened.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8. The final
run is at `6ace111` (every fix, round 2 included); the run at `a8cf76d` that
round 2 reviewed is kept in `root-checks.zip` under `run-a8cf76d`.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` at `6ace111` | Exit 0, 305 s. **321 required GameTests** passed (304 at the C12a base; 17 added by the fixes) |
| `git status --porcelain` after DataGen | Only the implementation log, which I edited by hand during the run; `src/generated` and `src/main/resources` unchanged (`generated-diff-02.log`) |
| `gradlew test --rerun --no-daemon` | Exit 0, 146 s. **1,304 JUnit tests / 240 suites**, 0 failures, errors or skips |
| API jar against the C12a run | SHA-256 `24b6527e…5218ab` in both: no public API change |
| Same pair of commands at `a8cf76d` | Exit 0; 320 GameTests, 1,304 JUnit tests / 240 suites, API jar unchanged |
| The reviewer's benchmark (`ReviewHousekeepingBenchmarkTest`, run from a temporary copy at `a8cf76d`) | 3,840 settled tombstones: idle tick 0.001 ms, no-op call 0.003 ms; 8,192: 0.009 ms and 0.001 ms (`benchmark-m5.log`) |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

Both full build logs have **18 ERROR lines and 0 FATAL**, the same
intentional failure-injection set as the earlier runs. The v1.3 rocket
GameTest named in C11R2-I5 passed in both.

Development GameTest runs that failed before the fixes were committed are
kept in `root-checks.zip` under `attempt-01-failed`: the first L5 GameTest
checked the dirty flag 25 ticks later, after vanilla's eager chunk save had
already cleaned the chunk (the test now runs one status check in its own
tick); and the first two ticket-count versions counted every ticket in the
Level and then saw rocket-flight tickets of earlier tests' flights end (the
count is now limited to the system's chunks and leaves those tickets out).
Production code was unchanged by them.

## Round 2

The same reviewer re-reviewed the 19 fix commits (`6ff9d27..a8cf76d`) in an
exported copy: it re-ran its round-1 probes, added eight round-2 probes and
its benchmark, and ran the suites (1,305 JUnit tests with its benchmark, 328
GameTests with its probes, DataGen identical to the committed tree). Its
report is `REVIEW-ROUND2.md` in `independent-review.zip`.

**Verdicts:** C11a, C11b, C11c and C11d **accept**. Every round-1 finding is
fixed or accepted as recorded, the recorded refinements (H1 removal order,
put = `CONFIGURE`, `TARGET_OUT_OF_BOUNDS` for over-long keys, I7 as a log
line) are accepted, and the fixes introduced no Critical, High or Medium
problem. New findings:

| Finding | Disposition |
|---|---|
| **C11R2-L1** `device owner` on a marker not registered yet could register it for the old owner when its chunk saved before the next status check | **Fixed** in `45d337c`. The marker waits again under the new owner in the same tick; a request queued from an earlier save no longer matches the candidate. GameTest that saves right after the command |
| **C11R2-L2** `EndgameService` over 500 lines without the AGENTS §3.4 check | **Fixed** in `4178839`. The chunk index is `EndpointChunkIndex`; the service is 444 lines. The log gives the drill block entity's current 587 lines |
| C11R2-I1 The skip rule can leave a block a same-layer reaction transformed; vanilla drops then lie in the shaft | **Recorded**; the C13 operator guide says so |
| C11R2-I2 `TARGET_OUT_OF_BOUNDS` also names a key too long to record | **Accepted** as recorded |
| C11R2-I3 C11 development-format roots now quarantine | **Accepted**; that format was never released |
| C11R2-I4 A duplicate import and a misplaced Javadoc | **Fixed** in `6ace111` |
| C11R2-I5 The v1.3 rocket GameTest `assemblyRollbackExceptionRetainsSnapshotForLaterRestoration` failed once in the reviewer's two runs | **Watched**; it passed in every run of this packet |

## Round 3

A confirmation round on `6ace111`: the reviewer re-ran its round-2
owner-race probe unchanged (it now fails its assertion, as intended: the
record registers for the new owner; an inverted copy passes, including a
stale request queued in the same tick), compared the index split line by line
with the old code, and ran the suites on a fresh export (all 321 GameTests
pass besides its probes, 1,305 JUnit tests with its benchmark, DataGen
identical, 0 FATAL). Report: `REVIEW-ROUND3.md` in `independent-review.zip`.

**Verdicts:** C11a, C11b, C11c and C11d stay **accept**; C11R2-L1, L2 and I4
are fixed, I1–I3 and I5 accepted as recorded, and nothing new was found.
C11 implementation and review are closed; no Gate is claimed.
