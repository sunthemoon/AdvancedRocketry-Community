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
