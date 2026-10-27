# TASK W128: W27d1, the bay deployment coordinator, admission (D0, D1) and reject-only outcomes

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W127-PLAN.md](records/W127-PLAN.md) (its
W27d1 sub-leaf, the closure rule and section 4) and [records/W27A-DESIGN.md](records/W27A-DESIGN.md) (sections 4, 4.1, 8
and 8.1, the W27d row of section 9, B1 to B18 and section 10's risk entry C), first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w128` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w128` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W128.md` (new; the one-page design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128/` |
| Merged prerequisites | W27b, W27c1, W27c2 ([W119](records/W119.md)), W27c3 ([W123](records/W123.md)) and W11b2's section 8.1 hook |

## Root decision R49 (2026-10-21), on W127's plan and its questions

- **The split** W27d1 to W27d4 and the closure rule are adopted. Every sub-leaf keeps deployment admission disabled
  (only the GameTest server and the gated release-test hooks start D0) and is a persistence slice.
- **Q1:** the claim holds T from the D0 enqueue and through an unacknowledged INTENT, and ends with READY_SOURCE(T, live
  source) at the last REJECTED acknowledgement, or at once when D0 rejects every bay or the INTENT write definitely
  fails, matching the merged dispatch.
- **Q2:** W11b2's rule stands (a dismount is never cancelled; ADR-065). A passenger who leaves a waiting rocket at
  altitude falls while T keeps the seat; Root corrects risk entry C and registers it as a residual; the passenger-exit
  permit (N4) comes before W27e.
- **Q3:** while the receipt index is NOT_LOADED, hold every PREPARED transfer record (only a rocket in flight can carry a
  blocking receipt) and leave landed rockets free; Root registers this with entry C; W27d4 revisits it.
- **Q4:** the interim behaviours are accepted: W27d1's REJECTED with `DEPLOYMENT_UNAVAILABLE` for a validation that would
  succeed (re-armed with the same `op` once W27d2 merges), its runtime refusal at D0 of a package whose satellite X
  already exists, and W27d2's runtime DUPLICATE_PENDING hold.
- **Q5:** released flight is kept, as W27d4, after W27d3.
- **Q6:** the stop rule stands: if W27d1's registry benchmarks miss docs/17 P95 or P99, W27d2 does not start until an
  ADR for an off-thread registry writer. W27d1 also times the synchronous transaction-journal checkpoint D0 can request.
- **Q7:** extract before adding. `RocketTransferService` and `RocketTransferSavedData` stay under 800 lines; W27d1 adds
  only the one `tickSource` call to `RocketTransferService`.
- **Q8:** the bay part joins `idle`, `logicalLocked` and `resourcesIdle`; `escrowIdle` and `escrowLogicalLocked` stay
  escrow-only for permitted boundaries. List every production call site whose behaviour changes for a held L in your
  design, and cover them with the W11b2 matrix rows section 8 requires.
- **Q9:** Root registers the rest of risk entry C before W27d1 merges and entry D before W27d2 merges.
- **Q10:** no conflict with W126.

## What to do

W127-PLAN's W27d1 scope exactly: the registry benchmarks first (one checkpoint with 32 watched registrations, the
coalesced flush and autosave, each at a 16 MiB registry against docs/17 P95 and P99, and the transaction-journal
checkpoint; if any misses, stop and report before writing more code), then the coordinator, D0, D1, the reject-only D2
through a new session-bound registry permit, the exits and restart rows, the section 8 gate lock under Q3 and Q8, status
with EN and zh text, the claim audit line, and the native harness with section 9's gated cut flags. Commit the one-page
design before any code; each part in its own commit with its tests.

## Tests

W127-PLAN's W27d1 A0, A1 and native lists (B1, B9, B11, B12 at D1, B13's first-tick refusals with a durable INTENT, B14,
B18 with INTENT and REJECTED writes, B10's hold half for INTENT, B17 without registered members), two restarts each. No
existing assertion is relaxed; where section 8's lock changes an existing test's result for a bay-held rocket, add the
bay case beside it and leave the original. No saved format beyond the receipt store's existing one, no bound, budget or
timeout changes; new classes under 500 lines. A new WARN or ERROR that a GameTest drives needs its own exact manifest
entry. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on
committed source; then the native cases, all under the heavy-JVM lock, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB and that 6 GiB of memory is free before each Gradle run or native start.

## Not in this task

W27d2 to W27d4, W27e, ADRs, the risk register (Root registers entry C before merge), status files and the ledger (Root).

## Resumption under ADR-074 (Root decision R121, 2026-10-27)

The first session stopped at the Q6 benchmark rule (record and benchmarks merged; R51). The owner accepted ADR-074 on
2026-10-26, and its slice 1, the off-thread transaction journal writer, merged with W129aM at `bb0eda86f` after B2,
B3a and a fresh review. W27d1 writes no registry file, so it resumes now ([W129-DESIGN](records/W129-DESIGN.md)
section 5.1; ADR-074 item 11). A fresh session continues:

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w128r` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w128r` (local commits only; Root merges) |
| Record | append a "Resumption" section to `docs/work/v1.8.0-root-queue/records/W128.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R/` |

1. **The design change, committed before code.** In the one-page design, D0's "one timed checked `checkpoint`"
   becomes ADR-074 item 11's request and wait:
   - the first UNPROVED or WRITE_REQUIRED answer sends `requestCheckpoint`;
   - WRITE_PENDING waits with L's claim HELD, its tickets renewed and no time limit;
   - PROVED continues;
   - UNPROVED after the request rejects every bay with ASSEMBLY_JOURNAL_UNPROVED;
   - WRITE_REQUIRED (mutated since) requests again;
   - the writer's availability joins D0's existing checks;
   - at server stopping, a waiting D0 is discarded without running any effect (W129-DESIGN section 4.6).

   Every other B14 answer keeps its meaning. State any difference from the design's B14 sentence in the record.
2. **Q6 is answered.** The registry benchmarks gate only W27d2's registry writes, which W129b and W129c cover. Do not
   rerun them. W129a's B2 measured the transaction request on the server thread (P99 0.22 ms at the bound); the IO
   write itself is not a server-thread cost. Do not measure again.
3. **Then the original scope, unchanged:** R49's decisions and the "What to do", "Tests" and "Verification" sections
   above, from the coordinator on.
4. **Extra tests:**
   - unit or GameTest: D0 on WRITE_PENDING holds and runs nothing; PROVED after a pending wait continues; UNPROVED
     after the request rejects every bay; WRITE_REQUIRED requests again; a stop while D0 waits runs nothing;
   - native: W129-DESIGN's T1 for the bay, a D0 request in flight and then a kill, with two restarts. The new session
     has no proof and W27A's restart rows apply.

Deployment admission stays off outside the GameTest server and the gated release-test hooks. W27d2 starts after
W129b and W129c merge. Root registers risk entry C before this slice merges. A fresh read-only review follows.

## Continuation under Root decision R126 (2026-10-27)

W128R delivered `b8d38e275`. Its review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-review/REVIEW.md`
(SHA-256 `eed6900f…5239`), returned BLOCK. Read the whole review first. A fresh session continues on
`codex/v1.8.0-w128r`, in `D:/GitHub/arce-v180-w128r`, and appends a "Continuation (R126)" section to the record.

1. **Merge the shared head named in your prompt.** It contains W152K and W129bc. The review's merge section names
   the conflicts:
   - In `SatelliteMissionSavedData` (`launch`, `launchIdle`) and `SatelliteManager`, keep W129bc's asynchronous
     `mutation(...)` bookkeeping and your launch fence that ignores permits. Keep both sides' accessors.
   - Rerun the permit, registry-fence and registry-writer tests after the merge.
   - W164 conflicts with you in `RocketManager.tick`. Whichever merges second keeps both the bay tick and
     `landedDisassemblies().tickRetained()`.
2. **W128R-R1 (High) first.**
   - `BayDeploymentCuts.firstTick` must pass the complete `EscrowTestEndpoints` gate before it parses the UUID or
     touches the registry, through the existing gate and not a partial copy.
   - Add negative tests with an existing idle satellite and no receipt: RCON enabled, and a connected player who is
     not test-owned. Each must leave the registry unchanged. Keep the B13 first-tick refusal test.
   - Command nodes may stay registered on the property alone, as the existing release-test commands are, because every
     execution path reaches the full gate. Add a test that a command run without the gate is refused and changes
     nothing.
3. **W128R-R2: the acceptance cases of the original task.** None of these move to a later slice. Use narrowly gated
   seams, and assert real receipt, index, claim, registry and package state, not classifier results.
   - **B10's INTENT hold half, natively, with two restarts.** Add a gated startup seam that blocks the registry, or
     registers a conflicting X, before the first tick. Also add a GameTest of the coordinator's REGISTRY_BLOCKED hold
     and its durable REPAIR write with the exact acknowledgement.
   - **Section 8.1 tests 1, 2, 3 and 6 with real bays and this coordinator:**
     - station-warp overlap;
     - another record recovering while T is held;
     - no ordinary recovery ticket for the held record;
     - a final-boundary HELD adding T in the same call.
   - **The interlock rows while the rocket is in TRANSIT:** mount, teleport, `/kill`, `/data`, resources, loaders,
     disassembly and launch. Add a real vanilla dismount that leaves the seat with T (R49 Q2).
   - **Loaders under shared pending saturation, and the N5 leak after `/kill`.**
   - **The NOT_LOADED part of the gate lock:** PREPARED records only, and every rocket when the transfer journal is
     not operational. Use a gated seam or a unit test on the real `BayReceipts` with real files.
   - **The D0 matrix's F5 refusals,** for DATA and non-DATA packages: a missing body, a non-orbitable body, the legacy
     `space` body, and a captured source-body context.
   - **Live-coordinator B14:**
     - a stop while D0 waits issues no receipt and runs no effect;
     - a refused checkpoint request rejects every member, with real receipt state.
4. **W128R-R4.** Correct the record:
   - Replace "No W27d1 path runs in that test". The coordinator's empty tick runs every tick; the fixture creates no
     claim, and the timing evidence is consistent with W166's cause 1.
   - Add the item cargo admission partner and the retained-release access and world callers (`RocketReleaseAccess`,
     `RocketReleaseWorld`) to the gate-lock call-site inventory.
5. **Verification:**
   - each new class alone, and the whole unit suite;
   - one complete GameTest with the unchanged checker;
   - the twelve native cases plus B10, on the final committed source.

   W166 paces the special-host fixture (R123). If that test still fails and W166 has merged, report it with its log.
   Do not change it.

Not in this continuation: W27d2, admission, ADRs, the risk register (Root corrects and registers R-042 from the
review's audit), status files and the ledger. A fresh read-only review follows.

## Test fixes under Root decision R130 (2026-10-28)

The R126 continuation (`660b0e468`) was re-reviewed. The review is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W128R-rereview/REVIEW.md` (SHA-256 `dc6d1914…bef5`): BLOCK. R1 and R4 are
closed. Read W128R-R5, R6 and R8 there. A fresh session works on `codex/v1.8.0-w128r` at `36d568db0`, Root's merge of
shared head `dd080faa3`, and appends a "Test fixes (R130)" section to the record:

1. **W128R-R5.** `BayDeploymentMatrixGameTests` (around line 153) compares the package signatures of the immutable
   `flight.record()` with themselves.
   - Compare the baseline with the live source rocket's snapshot and a fresh journal lookup of T before cleanup, and
     with a published destination if there is one. `BayDeploymentGameTests` (around lines 107 and 128) shows the
     pattern.
   - Keep every receipt, claim and registry assertion.
2. **W128R-R6.** `BayDeploymentLockGameTests` (around lines 117-122) checks only predicates for the held TRANSIT
   rocket.
   - Run a real loader, correctly bound to that rocket, and assert the refusal with unchanged device and rocket
     resources and reservation counts.
   - Run disassembly as the eligible owner in range, and assert the refusal with no confirmation and no pending
     operation.
   - Keep the mount, dismount, command, launch and receipt-removal checks.
3. **W128R-R8.** Correct the record's batch order. Batches run in their own order; the filter selects and does not
   reorder. Describe the comparison as matrix-included, with a limited causal conclusion.

Each changed test must fail first against a deliberately broken check, for example a loader that ignores the lock,
in a local experiment that is not committed. Record how. Then run:
- the bay batches three times;
- the whole unit suite.

No full GameTest is needed: the branch's hosted regression covers it, and Root pushes again. Change no production
code. If a test reveals a production gap, stop and report it. Root reviews the diff.
