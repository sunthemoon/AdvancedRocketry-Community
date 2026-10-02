# C12 independent review: findings, dispositions and fixes

Date: 2026-10-02. Branch `codex/v1.7.0-endgame-systems`. Reviewed commit:
`616b9fd` (C12a–C12d: ADR-057 black-hole generator, ADR-054 §11 transit
ledger, ADR-056 railgun, ADR-059 space elevator, plus the C13 release-test
hooks). The fixes are committed on top of it, one commit per finding. No Gate,
candidate or tag.

## Round 1

An independent, read-only reviewer worked from a `git archive` export. It read
the accepted contracts, the implementation log, the four C12 evidence packets,
and every C12 production class and test. It ran the suites twice (1,350 JUnit
tests; 329 GameTests, all passing in the second run) and five review-only
probes. Each probe asserted the suspected behaviour, so a pass confirmed a
finding. Its report, probe sources and logs are archived in
`independent-review.zip`; its copies of the repository are excluded.

**Verdicts:**

- C12a: accept.
- C12b: accept with required changes (H1, M1).
- C12c: accept.
- C12d: accept with required changes (M2, M3).

Count: High 1, Medium 3, Low 7, Info 6.

## Dispositions

| Finding | Disposition |
|---|---|
| **C12R-H1** A registration could name an evicted destination; the written root no longer loaded | **Fixed** in `ed1e453`. The restore check covers only a record's source and paid endpoint. A destination may be unknown: the record is `DESTINATION_MISSING` until a redirect or purge. Service test: escrow, removal, eviction, then registration; the written root loads |
| **C12R-M1** One endpoint with 64 records starved every other endpoint's reconciliation | **Fixed** in `62ec8b6`. Three changes: a record with no applicable destination row costs nothing; a pass the budget cuts short resumes at the record it stopped at, while the incoming gate and receipts still run; and the round robin moves past an endpoint once it has had its turn. Three service tests, each failed by reverting its part of the fix |
| **C12R-M2** The ride `TELEPORT` event named the arrival endpoint's owner | **Fixed** in `0541991`. It now names the departing endpoint's owner, as ADR-054 §5.1 says. GameTest: a zone that allows only the arrival's owner refuses the ride; a zone that allows the departing owner lets it commit |
| **C12R-M3** Elevator binds were neither spaced nor cooled down | **Fixed** in `e234cf5`. `BarrierSpacing` applies the §7 rules: 20 ticks shared by binds, owner redirects and owner resolves; 100 ticks per station for binds and elevator redirects; refusals change nothing. Unbinds count toward the spacing. Exempt barrier flushes show as `exempt_barriers` in the status. Unit, service and GameTests |
| **C12R-L1** The elevator redirect rule accepted any valid pair | **Fixed** in `20e3a88`. The target's pair must name the station whose region holds the transfer's source or destination. GameTest with two stations |
| **C12R-L2** Resolving a returning `MISSING` source destroyed provably unregistered entries | **Fixed** in `55392fb`. The rule now uses `root.retired(id)`, so a `MISSING` record counts. Service test; the mutation is caught |
| **C12R-L3** A same-ID copy elsewhere fed persistence observations to the original | **Fixed** in `68de387`. Transit sections carry their block entity's position, and only the one at the ID's recorded position counts. Service test; the mutation is caught |
| **C12R-L4** A refused `endpoint retire` still mutated the root | **Fixed** in `e1dba3c`. `TransitLedger.retire` refuses an ID without an index record before it changes anything. Service test; the mutation is caught |
| **C12R-L5** The status showed a constant `ride_tickets=0` | **Fixed** in `139e1fb`. The device status line shows the tickets held. GameTest |
| **C12R-L6** Package cycle between the root and the elevator | **Fixed** in `8f11659`. The pair model moved to `endgame.elevator.model` |
| **C12R-L7** Automation inherited the owner's operator exemption | **Fixed** in `c8483eb`. Automatic launches never have it. GameTest with an online operator owner |
| C12R-I1 A dead `pinned` predicate | **Fixed** in `81e0c27`. Callers pass the root's own pins |
| C12R-I2 Class-size notes | **Recorded** in the implementation log (`c454676`) |
| C12R-I3 Test adequacy | **Addressed**: the service tests above. `3c681dd` adds generator ticket counts, an empty burn at a full buffer, the `ELEVATOR_REFERENCES` reason, and a stop that releases a ride's ticket. `ARRIVAL_UNLOADED` stays without a GameTest |
| C12R-I4 Purge and restore edge semantics | **Documented** (`3a16fba`, corrected in `83f1b9c`) in the log and the operator guide |
| C12R-I5 A bind whose write failed stayed live | **Fixed** in `b495bcc`. The pair is taken back and the bind answers `ROOT_BUSY`. No automatic test: a GameTest cannot make the root write fail |
| C12R-I6 Flaky pre-v1.7 rocket GameTests | **Fixed** in two parts: a fresh GameTest world for each run (`00a5f31` records the provenance first, then `644c903`), and the entity-section wait from round 2 (`16a71ba`) |

## Round 2

The same reviewer re-reviewed `3c681dd` in an exported copy:

- Its round-1 probes now fail at their `PROBE:` assertions, so each defect is
  gone.
- It answered the specific questions about M1, M3, L3 and L1.
- It ran the full suite once and the GameTests three more times, each on a
  freshly generated world: 1,363 JUnit tests and 333 GameTests every time,
  with DataGen byte-identical. The report is `REVIEW-ROUND2.md`.

**Verdicts:** C12a, C12b, C12c and C12d **accept**. New findings:

| Finding | Disposition |
|---|---|
| **C12R2-L1** The I6 diagnosis did not explain the round-1 failures, which happened on a fresh world | **Fixed** in `16a71ba`. When about 100 tests of a batch start in one tick, a test chunk's entity section may not be loaded yet. Two tests depended on it: the recovery fixture asserted it once, and the fuel loader fuels only loaded rockets. Those tests now wait for the section with `EntitySections.whenLoaded`. The log records both causes |
| C12R2-I1 Two dangling Javadoc blocks | **Fixed** in `e8a5c12` |
| C12R2-I2 The I4 note claimed `endpoint resolve` discards a purged claim's payload | **Fixed** in `83f1b9c` (log and operator guide) |
| C12R2-I3 An admitted spaced request uses its slot even when nothing changes | **Accepted** as recorded in the log (`10d9b12`) |
| C12R2-I4 GameTests share the server-wide spacing across batches | **Accepted** as recorded (`10d9b12`) |
| C12R2-I5 No test of the restore rule for an unknown source or paid endpoint | **Fixed** in `8a68b15`. Removing the rule fails the paid-endpoint case. The source case is also refused by the `dispatched_through` check |
| C12R2-I6 Cargo redirected back to a removed anchor source loses its terminal side | **Accepted** as a recorded residual (`10d9b12`); an operator can purge |

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8. The run
is at `10d9b12` (every fix from rounds 1 and 2).

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` | Exit 0, 7 min 29 s. **335 required GameTests** passed, on a fresh world (329 at the reviewed commit, 6 added by the fixes) |
| `git status --porcelain` after DataGen | Clean: `src/generated` and `src/main/resources` unchanged |
| `gradlew test --rerun --no-daemon` | Exit 0, 3 min 7 s. **1,364 JUnit tests in 251 suites**, 0 failures, errors or skips |
| API jar against the C12d run, rebuilt at `7492ca7` in a temporary worktree | All 29 classes and every other entry are byte-identical. Only `META-INF/THIRD-PARTY-NOTICES.md` differs, from the C12R-I6 date. No public API change (`api-jar-comparison.json`) |
| Mutation checks (development runs) | Each unit- or service-tested fix fails its new test when reverted: M1 (three parts), M3, L2, L3, L4, C12R2-I5 |
| GameTest runs during the fixes (development) | The three runs on the kept world after M3 failed the rocket fuel test; those logs are summarised in the implementation log's I6 entry. Every run on a fresh world passed: 331, 333, 333 and 335 |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0, 45 passed (after packaging) |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0 (after packaging) |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| `python -B -m unittest tests.test_v1plus_planning` | Exit 0, 15 tests (after packaging) |

The full build log has **18 ERROR lines and 0 FATAL**: the same intentional
failure-injection set as the C11 close run (event-bus fault adapters, refused
satellite launches, migration ports, unsaveable fixture chunks).

## Round 3

A confirmation round on `10d9b12` (the same evidence commit). The reviewer:

- re-ran its round-1 and round-2 probes: p1 to p5 still fail their `PROBE:`
  assertions, and p6 agrees with the corrected purge note;
- checked that `EntitySections.whenLoaded` cannot hide a failure, with two
  probe GameTests: a chunk whose entities never load fails at the timeout,
  and a failing body fails;
- removed parts of the restore rule as mutations;
- ran the suites on a fresh export: 1,364 JUnit tests, and 335 GameTests in
  each of three runs on new worlds. DataGen was identical and the log had
  0 FATAL lines.

Across rounds 2 and 3, eight fresh-world GameTest runs passed every required
test. The report is `REVIEW-ROUND3.md`.

**Verdicts:** C12a, C12b, C12c and C12d **accept**; every round-2 disposition
is confirmed. One new Info finding:

| Finding | Disposition |
|---|---|
| C12R3-I1 The new restore test's Javadoc said no other check refuses its roots first; for the unknown source the `dispatched_through` checks do | **Fixed** in `6249564` (Javadoc only). The test file changed after the evidence run, as `post-run-changes.json` in `root-checks.zip` records with both hashes |

No Critical, High, Medium or Low finding remains open for C12.

## Archives

- `root-checks.zip` holds the evidence run at `10d9b12`:
  - the two Gradle logs;
  - JUnit XML;
  - the GameTest logs;
  - artifact hashes;
  - the API-jar comparison;
  - the implementation-file hashes and the post-run change.
- `independent-review.zip` holds the reviewer's three reports, its probes and
  its logs; its repository copies are excluded.
