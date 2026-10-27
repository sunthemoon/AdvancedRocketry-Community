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

## Not in this task

W27d3 (copies and the absence census), W27d4 (released flight), admission (W27e), ADRs, the risk register, status
files and the ledger. Root registers risk entry D before this slice merges. A fresh read-only review follows; Root may
split it into two passes, as the plan suggests.
