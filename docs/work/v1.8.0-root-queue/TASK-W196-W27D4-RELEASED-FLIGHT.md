# TASK W196: W27d4, released flight for a coherent REPAIR or REGISTRY_BLOCKED bay group

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w196` (new; from the shared head named in your prompt, which includes W170 and W185) |
| Branch | `codex/v1.8.0-w196` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W196.md` (new; the one-page design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W196/` |

## Read first

- [records/W127-PLAN.md](records/W127-PLAN.md): section 2's W27d4 sub-leaf (scope, A0, A1, native, size) and section 4's
  Q5, which Root decision R49 in [TASK-W128](TASK-W128-W27D1-COORDINATOR-ADMISSION.md) keeps (released flight after
  W27d3).
- [records/W27A-DESIGN.md](records/W27A-DESIGN.md): sections 4.1, 5, 6, 8 and 8.1, and section 9's B10.
- `docs/work/v1.8.0-root-queue/records/W170.md` (W27d2) and `records/W185.md` (W27d3), and risk entries R-042 and
  R-043 in [the risk register](../../11-RISK-REGISTER.md). R-043 states the D6 turn, the census columns and the holds
  this slice builds on.

## What to do

1. **The one-page design, committed before any code.** Cover, from W127's W27d4 scope:
   - for a coherent REPAIR or REGISTRY_BLOCKED group, forcing `released = 1` and requiring its exact acknowledgement
     before the first delegated step;
   - delegating `tickLive`, destination publication, passenger moves, source removal and cleanup under exact permits
     that check the unresolved package signatures, consumed markers, ends, snapshot IDs, wrapper and journal digests;
   - finishing REGISTERED consumption before any package reaches the destination;
   - the restart dispatch of released receipts (`RocketTransferBayDispatch` already classifies RELEASED_FLIGHT), and a
     released receipt with T absent held for offline repair;
   - R-042's lock while the index is NOT_LOADED (W127 Q3), which a released receipt on a landed rocket changes.

   List every saved structure the slice writes. **Stop and report before code** if it needs a saved format beyond the
   receipt store's existing `released` field and the journal record's existing fields, a new bound, budget, quota or
   timeout, or any line in `RocketTransferService`.
2. **Implement.** Admission stays off outside the GameTest server and the gated release-test hooks. New classes stay
   under 500 lines; the plan's estimate is 4 to 6 classes.
3. **Tests, from the plan.** A0: release eligibility and permit refusals. A1: a coherent REPAIR group flies with its
   packages and stays locked. Native: B10's release half, two restarts each.

   No existing assertion is relaxed. A new WARN or ERROR that a GameTest drives gets its own exact manifest entry.

## Verification

- each new class alone, then the whole unit suite;
- the bay batches three times;
- the native cases in tranches of at most 60 counted starts.

All heavy runs go under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off. Unit runs need no
lock. Keep the evidence under 100 MB. Root's hosted run on the pushed branch stands in for a complete local GameTest.

## Not in this task

Admission (W27e), ADRs, the risk register, status files and the ledger. Root updates R-043 before this slice merges,
and an independent read-only review follows.

## Review findings under Root decision R168 (2026-10-29)

The review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-review/REVIEW.md` (SHA-256 `07d50582…d613`), returned BLOCK.
Its probe is in `probes/` in the same folder. A fresh session continues on `codex/v1.8.0-w196` from `c61480208`, one
commit per finding, and appends a "Review fixes (R168)" section to the record:

1. **W196-R1 (High).** A refused acknowledged effect must suspend delegation and schedule checked-phase reconciliation
   before another live phase is permitted, and the passenger movement obligation must finish before source removal is
   authorized. Start from the review's probe. Add a test with an assigned rider and an endpoint save or same-position
   write between publication and acknowledgement, at both endpoints, through to flight completion.
2. **W196-R2 (High).** Keep an observed physical or escrow conflict (UNEXPECTED_COPY and the other physical-authority
   holds) from before release authority exists, in the bounded claim state; release eligibility must tell those apart
   from an eligible registry or signature problem, and an existing release flag must not erase them. Recheck the hold
   before and after recovery side effects and before delegation. Add restart and delayed-end tests where an outside
   entity joins or moves before the first successful released resume, with the recorded authority present and absent;
   each keeps the hold without spawning, moving passengers, removing copies or delegating.

No format, bound, budget or timeout changes. Verification: the new tests alone, failing first; the whole unit suite;
the bay batches three times; B10's native release cases again. A fresh review follows.
