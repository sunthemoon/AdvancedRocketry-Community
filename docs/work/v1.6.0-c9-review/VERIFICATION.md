# C9 independent review of C8a and C8b, and its fixes

Date: 2026-10-01. Branch `codex/v1.6.0-satellite-resource-missions`, parent
`ce55ee3` (C8b). Development slice only: no Gate, candidate or tag.

## The review

An independent reviewer read the committed tree of `ce55ee3` (C8a-2 `d15dfcc`,
C8a-1 `53d4cf7`, C8b `ce55ee3`) and wrote only in its own directory.

- **Contracts:** [ADR-050](../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md)
  revision 4, [ADR-051](../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md)
  revision 3 and [ADR-052](../../decisions/ADR-052-RESOURCE-TABLES-AND-SEEDS.md)
  revision 2.
- **Tests re-run:** it re-ran the 9 key suites (42 tests, 0 failures) and the GameTest server
  (291 passed with its two probes).
- **Probes:** it wrote five probes of its own, and each reproduced its finding.
- **Archive:** the report, logs, probe results and the two probe sources are in
  `independent-review.zip`. Its repository copy is not archived.

**Verdict: accept with required changes**:
- counts: 0 Critical, 2 High, 3 Medium, 9 Low, and 3 questions;
- required: H1, H2, M1, M2, M3;
- recommended alongside them: L1, L2, L8(a)/(b).

It found no way to duplicate a reward in normal play, and accepted all eight implementation clarifications.

## Dispositions

| ID | Finding | Disposition |
|---|---|---|
| **H1** | A terminal with more than 64 bound missions plus receipts refused almost every claim, start and cancel with `RECONCILING`: an action restarted the whole pass | **Fixed.** A pass covers only what can need action: every receipt's mission, and the claims the terminal paid without an acknowledgement (`TerminalIndex.awaiting`). Every other bound row is "nothing" in the ADR-051 §7 table. An action waits only for the first pass after the terminal loads (at most a few ticks). Then it reconciles the row of the mission it acts on, so a receipt at this terminal is applied before a claim or cancel of that mission. Periodic passes never refuse an action. Implementation clarification 6 below |
| **H2** | Breaking a terminal deleted its buffer, receipts and ID; bound missions were stranded | **Fixed.** A normally removed terminal carries its schema-2 root (ID, buffer, receipts and owner) with an empty inventory. The six slots still drop, so nothing is copied. A placed terminal loads it unpersisted, as for any carried root (ADR-051 §5). A quarantined root still travels raw (ADR-029) |
| **M1** | A record pruned through one retention queue left a stale entry in the other; queues grew for the uptime | **Fixed.** Each prunable record has exactly one entry, indexed by mission, in the global and its owner's ordered set. Every change re-files it and every removal takes it out of both |
| **M2** | Start/cancel churn added an expiry entry per cycle | **Fixed.** The instance ledger keeps at most one indexed entry per instance, re-filed on every change |
| **M3** | The bound terminal's level ID was not length-checked at start, rebind or bind-back, but the loader rejects more than 128 characters | **Fixed.** `TerminalLocation` refuses over-long IDs; `DeliveryTerminal.display()` stores no display location for them (it is display-only), so the record always loads |
| L1 | The terminal paid on `success()`, which is also true for `ALREADY_CLAIMED` and `IDEMPOTENT` | **Fixed.** It pays only for `SUCCESS` with a change |
| L2 | An exception in reconciliation (for example a failing bind-back barrier) crashed the server from the block-entity ticker | **Fixed.** The bind-back barrier failure is logged and stays pending for the coalesced flush. A failing row is logged once per load and skipped |
| L3 | Withdrawing before the receipt is saved, then a cut "registry flushed, chunk not", rematerializes the reward while the player keeps the stack | **Recorded** in the v1.6 handoff's KNOWN-ISSUES. Gating withdrawal on a persisted receipt would not close the class. A torn save of the player file and the chunk duplicates a withdrawn stack either way, as for any vanilla container. The duplication is bounded by what was withdrawn before the cut |
| L4 | Asteroid and gas starts did not refuse a removed kind definition | **Fixed.** `DEFINITION_NOT_FOUND` when the current catalog lacks the craft's kind definition (ADR-050 §11) |
| L5 | Survey cancels and operator cancels of resource missions still ran barrier flushes | **Fixed.** Only `data` cancels keep their barrier; v1.6 kinds mark "flush pending" (ADR-050 §2) |
| L6 | `mission verify` of an asteroid mission said MATCH after its type changed or was removed | **Fixed.** The instance outcome (`VERSION_CHANGED`, `INPUTS_UNAVAILABLE`) is returned; two test cases added |
| L7 | Every claim, cancel or completion scanned the whole deadline queue | **Fixed.** One indexed entry per mission; removal is logarithmic |
| L8 | Test gaps | **Fixed:** (a) a claim through the real menu and intent limiter with 80 pending receipts; (b) a GameTest breaks and re-places a terminal and claims its bound mission at the new place; (c) `SatelliteWritePolicyTest` covers every resource path; (d) crash-cut restarts go through `TerminalChunkEvents` and `TerminalObservations`. The C8b packet's wording "restarts from files written at different moments" overstated (d); this is its correction |
| L9 | An unowned terminal let any player withdraw another player's rewards | **Fixed.** A terminal takes an owner at its first asteroid or gas start |
| Q1 | `instance release` after `PAID_THEN_CANCELLED` returns a paid instance | Kept as the operator's decision (ADR-050 §8, ADR-051 §2). The `PAID_THEN_CANCELLED` line names the mission; recorded in KNOWN-ISSUES |
| Q2 | Duplicate terminal IDs from creative pick-block, structure blocks, `/clone` or movers | Outside the contract: creative and operator tools can copy any block entity. Recorded in KNOWN-ISSUES |
| Q3 | Is `ChunkDataEvent.Save` posted on the main thread before the write? | **Verified** in the Forge 47.4.10 bytecode (`ChunkMap.save`): `ChunkSerializer.write`, then the event, then `write` to the `IOWorker`, all on the calling main thread |

### Implementation clarification 6 (C9-H1)

ADR-051 §7 says a terminal reconciles "when it loads, and before each resource action", and actions "wait until the pass completes". In v1.6:

- **Load pass.** It covers every receipt's mission and every unacknowledged claim paid at the terminal: the rows whose action is not "nothing" without a receipt. Actions wait for it.
- **Before an action.** The terminal reconciles the row of the mission the action is about. That row is the one whose state can change the outcome of that claim or cancel.
- **Other rows.** Periodic passes every 20 ticks reconcile them without holding actions back.

The safety argument is unchanged:
- an action on a mission sees that mission's receipt first;
- a start creates a fresh mission ID that no receipt can name;
- its instance is AVAILABLE only if no receipt can have paid it (ADR-051 §8).

## Tests added or changed

- `QueueBoundsTest` turns the reviewer's probes into bounds:
  - 2,000 claim/start cycles leave no more queue entries than finished records;
  - 2,000 start/cancel cycles leave one expiry entry and an empty deadline queue;
  - an over-long terminal level is not stored, and the registry reloads;
  - rescheduled deadlines keep one entry.
- `RewardVerifierTest`: asteroid missions after a type change or removal.
- `SatelliteWritePolicyTest.everyResourceMissionChangeIsMarked`: each change marks "flush pending" and advances the save epoch. The changes are:
  - survey start and claim, asteroid start, completion;
  - rebind, claim, acknowledgement;
  - owner and operator cancels, instance release, purge;
  - expiry with pruning.

  The closing clock-only pass runs before the expired instance's removal is due.
- `DeliveryCrashCutTest`: restarts through the chunk-event path.
- `ResourceMissionGameTests`:
  - the 272-claim test claims once through the menu with 80 pending receipts;
  - it releases the 256 receipts on the terminal's own schedule (periodic passes and the coalesced flush), instead of synchronous presses;
  - the new `aBrokenTerminalCarriesItsDeliveryToItsNewPlace`.

## Commands actually executed

| Command | Result |
|---|---|
| Development runs of the changed suites | First run of the new write-policy test: 2 failures, both errors in the test sequence, with no product assertion changed. The purge case claimed before the start was flushed (correctly refused `AWAITING_WORLD_SAVE`). The closing clock-only pass fell on a due removal. The test now flushes first, and checks clock-only before the removal is due. Not captured in the evidence directory; this table is their record |
| `gradlew test runGameTestServer` (development) | Exit 0; all 290 required GameTests passed |
| `gradlew clean build test runData runGameTestServer --console=plain` | Exit 0, 2m46s. **1,157 JUnit tests / 218 suites**, 0 failures. **290 required GameTests** passed. The generated diff is empty |

The log has the same 18 intentional ERROR lines as the C8b run and 0 FATAL. The
repository validators ran on the staged tree after packaging
(`packaging/out/validation.log`) and all exit 0:

- `validate_repository.py --require-approved-identity`;
- `validate_v1plus_planning.py`;
- `validate_bootstrap_provenance.py`;
- `python -m unittest tests.test_v1plus_planning`;
- `git diff --cached --check`.

The release-test hooks for the C9 native run (`ReleaseTestCommands`, the
satellite post-tick timing, the stress batch owner spread) are in this tree too.
They register only with `-Dadvancedrocketrycommunity.releaseTestHooks=true`. Their
native results are reported with the C9 closure.
