# TASK W125: fix F1, the named fill held by its own transfer fence, under Root decision R48

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W107C.md](records/W107C.md) (F1, F2,
F3), [records/W121-DESIGN.md](records/W121-DESIGN.md) and [records/W121.md](records/W121.md), and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W121-review/REVIEW.md` (SHA-256 `b5958f4e…c1650`) with its probes, first. Where
the note and R48 differ, R48 wins. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w125` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w125` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W125.md` (new; the plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W125/` |

## Root decision R48 (2026-10-21), on W121's note and its review

- **D1, adopted with the review's corrections.** Option (b): loader reconciliation accepts the record that guards its
  own operation, and a settled record is not fenced by its own fill (the no-revoke rule), which permits no other recovery
  action. Corrections:
  1. **W121-R1:** the reconciliation self-guard is its own predicate, not `ordinaryFillsOnly`. It admits ordinary
     guarded PREPARED fills and STALLED fills with a retryable proof-failure reason, including pending reason
     transitions, and keeps every identity, journal, frontier, tombstone, manual-conflict, settlement, flight and bay
     exclusion. A persisted stall is cleared only by the existing successful proof and L2 path.
  2. **W121-R2:** no hand-back at C8 grants recovery authority. After the fill retires, the landed record and its floor
     follow today's loaded-only recovery (F1b) until D2. The note's R-3 is dropped as written.
  3. **W121-R3:** state first-restart and second-restart outcomes separately, and "fill retired, resources conserved"
     separately from "rocket usable for its next launch".
  4. The transient reason `TRANSFER_JOURNAL_UNPROVED` (R-4) is shown while the boundary holds, and the scheduler waits
     20 ticks before retrying it, so a held fill no longer takes every tick's one capture (the starvation the review's
     probe reproduced).
  5. **F2:** the bounded `FluidFuelLoaderIndex` lookup reports `RELOCATED_ENDPOINT` for a loaded relocated loader, with
     the review's qualifications: never authority to adopt the new place; an overflow past 64 Levels refuses with
     `BARRIER_BOUNDS` (retryable); the same loaded-only rejection in abandon preview and confirmation.
- **D2:** a destination-authority design, so a rocket refuelled away from its launch site can launch without that site
  loaded, is commissioned separately. Until then fuel admission stays disabled and F1b is a recorded limitation.
- **D3:** a visible, re-derived transient reason satisfies "every hold shows a reason an operator can see", a recorded
  change from the task's "persisted" wording. Status and inspect show the runtime reason and its retry time; the loader
  shows `ESCROW_PENDING`, not an indefinite PREPARING. Existing persisted manual and STALLED reasons stay.
- **D4:** cargo gets its own direction-aware extension before cargo admission (C-E2 and C-E3); not here.

This is a persistence slice: commit a one-page plan at the top of the record before code; an independent read-only review
follows before merge.

## Tests and cuts (the review's section 6 list, as it applies)

- Unit: the self-guard predicate's matrix (PREPARED, STALLED with retryable and manual reasons, pending transitions,
  every exclusion); the no-revoke rule; the 20-tick delay and fairness (a held fill no longer starves a younger one, the
  review's probe as a permanent test); F2's cases (loaded same-chunk, cross-chunk, cross-Level, unloaded holder,
  stale or duplicate holder, more than 64 Levels).
- Native, two restarts each, under the heavy-JVM lock: W107C's two named R1 cuts (`LF_M_R1_255_DISTINCT`,
  `LF_M_R1_255_COALESCED`), with K2 kept and the original after-L1-callback stop restored; a named E5 case (three
  failures, repaired storage, automatic retirement, a restart while STALLED, another rocket progressing meanwhile);
  `R1_FLOORS_256` and the R3 cases unchanged. For each, report the first and second restart separately and the
  usability of the rocket separately from conservation. A cross-Level variant with the old source region genuinely
  unloaded is expected to stay held (F1b): record it, do not count it as a pass.
- The existing fence, recovery, scheduling, reconciliation, loader and operator tests stay unchanged and pass.

No saved format, schema, hash, bound, dictionary, proof, lease, budget or timeout changes; no existing assertion is
relaxed; `RocketTransferService` (796 lines) must not grow past 800. A new WARN or ERROR that a GameTest drives needs its
own exact manifest entry. Stop and report if any of these seems needed.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on
committed source; then the native cuts above, all under the heavy-JVM lock, releasing it and deleting every disposable
server copy and `build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check
C: and D: above 10 GiB and that 6 GiB of memory is free before each Gradle run or native start.

## Not in this task

D2's design, cargo (D4), production admission, ADRs, the risk register, status files and the ledger (Root).
