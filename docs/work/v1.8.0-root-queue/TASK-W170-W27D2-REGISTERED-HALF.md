# TASK W170: W27d2, the registered half of bay deployment for one copy

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w170` (new) |
| Branch | `codex/v1.8.0-w170`, created from `codex/v1.8.0-w128r` at `36d568db0` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W170.md` (new; the one-page design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W170/` |

**Base.** W27d1 (W128R) is in its final test fixes and not merged yet. Its production code is settled, so this slice
starts from its branch. When Root names a newer W128R or shared commit, merge it into this branch and record any
conflict.

## Read first

- [records/W127-PLAN.md](records/W127-PLAN.md): section 2's W27d2 sub-leaf (scope, A0, A1, native, size), section 3,
  and section 4's questions with Root decision R49 in [TASK-W128](TASK-W128-W27D1-COORDINATOR-ADMISSION.md).
- [records/W27A-DESIGN.md](records/W27A-DESIGN.md):
  - sections 4, 4.1 and 4.1.1, 5, 6, 8 and 8.1;
  - section 9's B2, B3, B4, B6, B8, B12, B13 and B15 to B18;
  - section 10's risk entries C and D.
- [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md) item 11 and
  [records/W129-DESIGN.md](records/W129-DESIGN.md) section 5.1. D3 now requests the registry checkpoint with its
  watches, and waits for the first published completion with a sequence of at least m. W27A's REGISTRY_UNPROVED rule
  (three failures) and its 200-tick pacing are unchanged.
- [records/W128.md](records/W128.md) on the base, for W27d1's coordinator, and the risk entries R-039 (with its
  W129b/W129c addition), R-041, and Root's R-042 draft
  (`D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-review/input/R-042-draft-3.md`).

## What to do

1. **The one-page design, committed before any code.** Cover:
   - how D2 registers under the permit;
   - D3 under ADR-074 item 11;
   - D4's group publication and fence lift;
   - D5's payload-only preparation, and its journal mutation in a new class so that `RocketTransferSavedData` stays
     under 800 lines;
   - D6's survivor-slot proof through W98's port;
   - D7's guarded removal;
   - restart rows R0 to R3, the DUPLICATE_PENDING hold, and the removal of W27d1's interim outcomes (R49 Q4).

   List every saved structure the slice writes. **Stop and report before code** if any of these is needed:
   - a saved format beyond the receipt store's and the journal record's existing fields;
   - a new bound, budget, quota or timeout;
   - any line added to `RocketTransferService` (809 lines; W167).
2. **Then implement in two halves, each in its own commits:**
   - receipts and registry (D2 to D4);
   - entity and journal (D5 to D7, and the restart rows).

   Admission stays off outside the GameTest server and the gated release-test hooks. New classes must stay under 500
   lines. The plan's size estimate is 11 to 14 classes.
3. **Tests, from the plan's A0, A1 and native lists:**
   - A0: every D2 to D7 prefix for one and eight bays; mixed groups; refusals.
   - A1: real data and solar rockets registered once each and consumed once each; decommission after registration;
     source present and absent after a restart; section 8.1 test 7 with consumption; the D5 benchmark.
   - Native: B2, B3, B4 and B6 (with the protected-copy refusal), B8, B12 at D4 and D7, B13's checkpoint cases, and
     B15 to B18 for one copy, two restarts each.

   No existing assertion is relaxed. A new WARN or ERROR that a GameTest drives gets its own exact manifest entry.

## Verification

- Each new class alone, then the whole unit suite.
- One complete GameTest with the unchanged log checker.
- The native cases in tranches of at most 60 counted starts.

All heavy runs go under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off. Unit runs need no lock.
Check C: and D: above 10 GiB and 6 GiB of free memory first. Keep the evidence under 100 MB.

## Integration under Root decision R133 (2026-10-28)

W170 delivered `0623fa7be`: all 1,457 GameTests and all 32 native cases pass. Two things remain before its review:

- **The shared-head merge.** The branch was cut from W128R's `36d568db0`. W128R merged later with R130's test fixes
  (`16ca2b269`). Root's trial merge of the shared head conflicts in one place, `BayDeploymentMatrixGameTests` (around
  line 152):
  - this branch narrowed the assertion to "a refused solar bay registered X";
  - the shared head added `assertPackagesUnchanged()`, which compares live and journal package state (W128R-R5).

  Under W27d2 a matrix case may now register and consume the data package, so neither side is right on its own.
- **One checker finding.** The full run had an unexpected WARN in `w152_recovery_rollback`: a sealed-disassembly
  rollback proof overran W98's 100-tick attempt window and was retried.

A fresh session continues on `codex/v1.8.0-w170`:

1. **Merge the shared head named in your prompt.** For each matrix case:
   - where W27d2 leaves the packages alone, keep `assertPackagesUnchanged()`;
   - where it registers and consumes, assert the consumed state on the live source, the journal's T' and any
     published destination, in the same live-state way.

   Record each case. Rerun the bay batches three times and the whole unit suite.
2. **Classify the `w152_recovery_rollback` WARN.**
   - Run that batch alone three times on the merged branch.
   - Check whether its fixture waits out a deliberate veto or a 200-tick retry in unpaced ticks, as W166 found for
     other sealed fixtures.
   - If so, pace it as `a859acaca` and W166b did, keeping its budget and assertions, and add nothing to the manifest.
   - If it points at W27d2, stop and report.
3. **Run one complete GameTest with the unchanged checker.** R131 applies.

Root then pushes the branch for a hosted regression, registers risk entry D, and arranges the read-only review in the
two passes the plan suggests.

## Pass 1 findings under Root decision R137 (2026-10-28)

Review pass 1 (receipts and registry), `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/pass1/REVIEW.md` (SHA-256
`74f48f6b…ab8e`), returned APPROVE-WITH-CHANGES. Pass 2 is still to come. A fresh session fixes pass 1's findings on
`codex/v1.8.0-w170` and appends a "Pass 1 fixes (R137)" section to the record:

1. **W170-R1 (Medium).** `BayConsumedSnapshots.Job.step` can rebuild both S' and D' in one server tick. ADR-065
   section 4.1.1 allows at most one whole-snapshot reconstruction per tick. Split the step so that each tick
   reconstructs at most one, with a test that pins it, and rerun the D5 benchmark.
2. **W170-R3 (Low).** Remove or guard the unreachable branch that would write REJECTED after a RAM registration, so
   that it can never be reached.
3. **W170-R6 (Low).** Add a test that pins D3's three-failure threshold and its 200-tick pacing.
4. **W170-R7 (Low).** Restore a durable-REJECTED bay menu assertion.
5. **W170-R4 and R8 (Low).** Correct the record: F2 holds are runtime-only and are promoted at the next start, and
   filtered runs on uncommitted trees were not committed evidence.
6. **W170-R9 (Info).** Rerun the native cases that go through D2's launches on the merged source, since the shared
   head changed the registry storage-budget path. That is at least B2, B3 and B4; name any others. Two restarts each,
   under the lock.

W170-R2, the R-043 draft, is Root's. Verification: the new tests alone, the whole unit suite, and the bay batches
three times. A full GameTest is needed only if production code outside `rocket/bay` changes.

## Pass 2 findings under Root decision R139 (2026-10-28)

Review pass 2 (entity and journal), `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-review/pass2/REVIEW.md` (SHA-256
`d1a94f9f…956b`), returned BLOCK. Its probes are in the same folder. A fresh session starts after the R137 session
has delivered, on the branch head that session leaves. It appends a "Pass 2 fixes (R139)" section to the record:

1. **W170-R21 (High).** `BaySourceResume.collect` drops an entity naming L whose snapshot or hash is unexpected, so a
   visible conflicting copy counts as absent and can survive retirement.
   - Bound the census by identities naming L first.
   - Then classify each candidate by owner, end, snapshot ID and hash, position and protection.
   - An unexpected image holds as runtime REPAIR without mutation; a recognised neutral non-survivor stays
     DUPLICATE_PENDING.
   - Add source-margin and destination-column regressions, starting with the review's probe.
2. **W170-R22.** Movement after D7 queues must invalidate the replacement guard. Connect source position and
   authority observations to an IO-safe token through captured and queued retirement. Add a pre-guard cut, and run
   B16 natively for motion, superseding save, join and leave, keeping receipts until a fresh proof succeeds.
3. **W170-R23.** R1 and R2 effects must wait for the exact checked L/T acknowledgement, not `journal.operational()`.
   Add a regression that holds the startup writer.
4. **W170-R24.** The absent-source branch must check all escrow eligibility (`bayNeutral`: locks, floors, tombstones,
   an unavailable root) before it publishes. Add regressions that leave entity, journal and receipts unchanged on
   the hold.
5. **W170-R25.** All of a group's receipts and the checked record must agree on owner before any recovery effect.
   Start from the review's probe, and cover BEFORE, AFTER and mixed-owner groups.

6. **W170-R4's remaining part** (from pass 1). Once a claim is REPAIR-held, skip D2 for its remaining INTENT members, so
   that no further satellite is registered in RAM under a claim that cannot proceed this session.

No format, bound, budget or timeout changes. Verification:
- the new tests alone, failing first;
- the whole unit suite and the bay batches three times;
- one complete GameTest;
- the native cases that the fixes touch, B16 included, two restarts each.

A fresh review of the R137 and R139 changes follows.

## Re-review findings under Root decision R142 (2026-10-28)

The re-review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W170-rereview/REVIEW.md` (SHA-256 `7c1dc465…24e3`), returned
APPROVE-WITH-CHANGES. Every pass 1 and pass 2 finding is closed. Its probes are in `probe-src/` in the same folder. A
fresh session works on `codex/v1.8.0-w170` from `1af1d76ed` and appends a "Re-review fixes (R142)" section to the
record:

1. **W170-R41 (Medium): wait for both ends before the census counts them.**
   - R2 waits (END_LOADING) until the destination snapshot's end is also entity-loaded. R1 already tickets that end,
     so add no ticket.
   - D6 runs its census only while both ends' checked columns are entity-loaded, using the tickets that already exist.
     If some path reaches D6 with an end that no ticket covers, do not wait there and do not add a ticket. Name the
     path in the record instead, and Root states the limit in R-043.
   - Add a case whose destination end is not yet loaded. It must wait, not reach READY_SOURCE, and then proceed once the
     end loads. Start from the review's HIDDEN-section probe.
2. **W170-R42 (Low).** A loaded rocket whose data did not decode, and whose preserved `assembly_transaction_id` is L,
   counts toward the census cap and is classified UNEXPECTED_COPY. Add the probe's case
   (`aLoadedBlockedImageWhosePreservedDataNamesLMustHold`) to `BaySourceCensusTest`.
3. **W170-R44 (Low).** Once the claim is REPAIR-held, `bayStep` writes no further INTENT: the remaining candidates hold
   without a write, as `outcome` already does for D2. Tighten the A1 test `aClaimHeldForRepairBeforeD2RegistersNoSatellite`
   to assert that no INTENT is written after the hold.
4. **W170-R45 (Low).** In `scripts/run_v180_bay_forced_stop.py`, allow a changed receipt file only after the fresh
   proof's own D7, that is, a BAY_RECEIPT_WRITE claim line after the BAY_SOURCE_PROOF line. Otherwise require the file
   unchanged. Add a Python test for both sides.
5. **W170-R46 (Low).** Correct the record on what B16_LEAVE exercises, and add an A0 test that drives
   `BaySourceProof.observe` through the real unload path (`setRemoved`, then the per-tick observation).
6. **W170-R43 (Low), the log part only.** Log one INFO line per refused D6 capture, at its existing 200-tick pacing.
   The bay menu does not change: new menu reasons would change the menu's synced data, which is out of scope. Root
   states in R-043 that runtime holds show only in the log and the gated status command.
7. **W170-R47 (Info).** Correct the record's attribution: TRANSFER_JOURNAL_BLOCKED came from one of
   `RocketFlightService`'s launch preconditions, not necessarily rejoin protection.

W170-R48 and R49 (risk entry D), the R5 acceptance and ADR-065's W27d2 note are Root's. **Root accepts W170-R5:** a D2
refusal is written in its own REJECTED generation before D4, and D4 publishes the REGISTERED receipts in one
generation. Pass 1 traced every forced-stop state, and the restart rows reach the same end state.

No format, bound, budget or timeout changes. Verification:
- the new tests alone, failing first;
- the whole unit suite, the bay batches three times, and the harness's Python tests;
- the native restart cases that go through R2, and B16, two restarts each, under the lock.

Root's hosted run on the pushed head stands in for a complete local GameTest. A short fresh review of these fixes
and of R-043 draft 3 follows.

## Not in this task

W27d3 (copies and the absence census), W27d4 (released flight), admission (W27e), ADRs, the risk register, status
files and the ledger. Root registers risk entry D before this slice merges. A fresh read-only review follows; Root may
split it into two passes, as the plan suggests.
