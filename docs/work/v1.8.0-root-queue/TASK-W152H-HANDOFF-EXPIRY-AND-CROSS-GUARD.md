# TASK W152H: the W152 re-review 2 findings and the W80 cross-guard (W162)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W152G](TASK-W152G-ACTIVE-PROOF-PRIORITY.md)
(R109), the W152F and W152G sections of [records/W152.md](records/W152.md), and the second re-review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W152-rereview-2/REVIEW.md`, SHA-256 `5373a3d1…07be`, verdict BLOCK) first.
Its evidence-only probe, `probe-src/.../SealedAdmissionReviewTest.java`, holds the mixed-queue and live-expiry
scenarios. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152h` |
| Branch | `codex/v1.8.0-w152h` from the shared merge commit `6f00cbf5e` (local commits only; Root merges) |
| Record | append a "W152H" section to `docs/work/v1.8.0-root-queue/records/W152.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152H/` |

## Root decision R110 (2026-10-25)

W152 is merged at `6f00cbf5e` with W152-R1 to R3 closed. W152-R4 and W152-R5 need sealed records, which only test
hooks create while player admission (N3-B3, I6) stays off; the re-review says the same. Player admission stays off
until this task is merged after a fresh review. W152-R4, R5 and R6 are accepted as the re-review states them. Each
part below is its own commit, with a regression that fails before the fix. Part 1 comes first, so Root can merge it
alone if a later part needs another round.

1. **The W80 cross-guard (formerly W162).** The merge already keeps both guards in `canAddPassenger`,
   `escrowReconciliationBoundary`, `resourceMutationAllowed` and `beginLandedDisassembly`. Add the two missing ones:
   - W158's release planning and its boundary refuse a rocket that sealed staging claims or freezes, before any
     sidecar write or fence;
   - sealed staging refuses a rocket with a release fence before any claim, carrier or journal write.

   Use the existing refusal results. Unit tests cover both directions at both entry points. Add a GameTest each way
   only if the existing test hooks can reach both states in one world.
2. **W152-R5: a request's own lifetime is not paused by proof backoff.** A request that has not retained U still
   checks its validity, cancellation and the unchanged 200-tick expiry every service tick, even while its proof
   start is backing off. It then ends and releases its RAM claim at the existing boundary. A retained U is never
   released or emptied because its proof cannot start. Regression: a live PREPARE within the slot limit whose
   admission stays BUSY ends at tick 201 with nothing retained, as the re-review's control does.
3. **W152-R4: ordinary retirement gets its turn between sealed attempts.** When an eligible ordinary return is
   waiting for admission and a sealed attempt ends, the waiting ordinary return gets the free capacity and the next
   native capture opportunities before another sealed owner reserves all four slots. When both classes wait, the
   turns alternate, so neither starves the other. The bounds:
   - an ordinary return starts no later than 2 ticks after the end of the sealed attempt that was active when it
     asked;
   - a waiting sealed candidate reserves no later than 2 ticks after the ordinary attempts admitted in that turn end
     (each is still bounded by its own unchanged deadline).

   Active-proof priority (R109) and the fairness among sealed candidates stay as they are. Regressions:
   - the re-review's mixed queue with 2 and 63 sealed owners, both callback orders, asynchronous callbacks and lazy
     discovery, as a repository test using the production classes;
   - a worst case whose sealed attempt uses its full 100-tick deadline;
   - the reverse case: continuous ordinary traffic does not keep a sealed candidate waiting past the bound.
4. **W152-R6 and wide plans.**
   - Correct the comment in `SealedProofAdmission` and the W152G paragraph of the record: 21 slots is a conservative,
     phase-independent admission policy. A Level window that another capture opened earlier can fit up to 24, so it
     is not an absolute native limit. Do not raise it.
   - The re-review showed that supported assemblies can reach the planner's 129-slot ceiling. A sealed attempt for
     such a plan is never started, so no U may be created for one. A new request whose plan is over the admission
     limit is refused before any claim, carrier or journal write. It gets its own refusal result, named like the
     existing codes, and one INFO line with the slot count and the limit. The rocket entity stays exactly as it was.
   - A U record already over the limit can only come from test worlds of earlier builds. It stays retained as now,
     with one WARN per record per load naming the slot count and the limit. Nothing is released automatically, and
     recovery follows R-035's whole-world copy process.
   - The proof diagnostic must not swallow this case: an over-limit plan has its own log line, separate from `BUSY`.

Nothing else changes: the 256-inspection allowance, the 100-tick proof deadline, the 200-tick retry, the native
quotas, the 64-pending bound, the 129-slot census bound, the saved formats and the protocol stay as they are. Root has
added the wide-plan restriction to ADR-065 and R-035.

## Verification

- Each part's new tests alone, failing before its fix.
- The W152, W152F and W152G unit classes, unchanged.
- The `resourceescrow`, `rocket.flight.persistence`, `rocket.server`, `rocket.sealed` and `rocket.rejoin` packages.
- The whole unit suite.
- One complete GameTest with the unchanged log checker.
- The W152 native cases that the scheduler touches, with their two restarts.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W160 shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes. A fresh Codex session reviews afterwards.

## Not in this task

- Enabling player admission (N3-B3) or I6.
- The W160 command and view.
- The W128 checkpoint cost.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.
