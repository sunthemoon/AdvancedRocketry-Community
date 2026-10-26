# TASK W164: N3-B3, I6 integration (P1-N3 leaf N3-B, Root decision R87)

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. Then read:

- [records/W148-DESIGN.md](records/W148-DESIGN.md): sections 2, 2.1, 3.1, 3.2, 5, 6 and the N3-B3 row of section 7;
- Root decision R87 in the W148-review row of [QUEUE](QUEUE.md), and R89 in the W150 row;
- [records/W150.md](records/W150.md) (N3-B1, merged);
- [records/W152.md](records/W152.md) (N3-B2, merged at `6f00cbf5e` and `787c28e52`): what the retained-U and sealed
  staging path implements, and the R91, R93 to R97, R110, R112 and R114 decisions it records;
- the N3-B notes in ADR-065 and R-035.

You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w164` |
| Branch | `codex/v1.8.0-w164` from the shared head that records this task (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W164.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W164/` |

## What to do

N3-B3 as the W148 section 7 row states it, with N3-B1 and N3-B2 on committed code:

1. **Plan first.** Commit a one-page plan at the top of the record before any code. Map each item below to the classes
   it changes and to the tests that fail before the change. List the native cases with their counted starts. Name
   anything the design needs but the merged B1 or B2 code does not provide. If an item needs a new saved key, schema,
   bound, budget, timeout, packet or Mixin, stop after the plan and report it for Root.
2. **I6a, the plain hook.** Replace the direct `removePlain` with the section 2 D1 and exclusion proof and the
   existing checked removal (W148 3.1). FM12a keeps its pending-before-absence and outbox semantics. This is test-hook
   work and is never counted as production coverage.
3. **Design M (I6b and I6c).**
   - M0 admits proof work as `DISASSEMBLY_PENDING` while T remains.
   - M1 creates P and removes T only after the proof, and keeps the original 200-tick request age.
   - Cold-site preparation follows R87 answer (2), inside the unchanged caps. The first demand only prepares the
     recorded origin and reports pending preparation. Disassembly then needs a fresh request and a fresh proof.
   - The executor then delegates to the merged B2 path in place of the synchronous `Request.restore()` completion.
   - M3 needs B2's certified rollback (native state and U's checked absence) before it writes T back.
4. **No-T ordinary disassembly.** Route it to B2 after proving the current standalone D1 in its origin slot (W148
   3.2). This includes rockets already retired by W130 or I6a. Do not synthesize a T or an unknown predecessor.
5. **Player feedback.** Use the existing request and refusal results where they fit. The pending-preparation answer
   may add language entries; append only this slice's keys. No new packet.

**Constraints:**

- No change to quotas, the 100-tick proof deadline, the 200-tick retry or request age, the 64-pending bound, the
  inspection allowance, ticket lifetimes or steps, saved formats or the protocol.
- No change to assembly ordering or governed or escrow code, and do not open W98-R1 or governed admission.
- No silent new timeout and no manual orphan repair.
- New log lines get exact manifest entries when a GameTest drives them; the checker stays unchanged.
- Each step goes in its own commit with a test that fails before the change.

**W152J runs in parallel.** It changes `SealedProofTurns`, `RocketOrdinaryRetirement`, `RocketTransferContinuations`
and `RocketTransferRecoveryService` (the fail-back reservation turn). Avoid editing those four classes. If the plan
needs them, keep those edits small and in their own commits; Root merges W152J first and resolves any conflict.
Player admission stays off on the shared branch until W152J merges after its review (R114), so this slice merges
after W152J.

This is a persistence slice: an independent read-only review by a fresh Codex session follows before merge.

## Verification

- The new tests alone, failing before each change.
- Each changed class alone, then the `resourceescrow`, `rocket.server` and `rocket.sealed` packages and the whole
  unit suite.
- One complete GameTest with the unchanged log checker, and runData with a clean tree if data changes.
- The native cases B-PRE (for I6a and I6b), B-PLAIN, B-M and the combined terrain matrix from W148 section 6, two
  restarts each. Stop and report before more than 60 counted starts.
- The worst-case tick cost of the new proof and preparation work.

Run heavy work under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule; W152J shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes and delete only the server and export copies you create.

## Continuation under Root decision R115 (2026-10-26)

The first session stopped at checkpoint `be1b01062` (05:48 to 07:20), not merge-ready.

- **Done:**
  - the bounded `BEFORE_RELEASE` site;
  - the plain hook through the current-D and predecessor proof;
  - the predecessor-U guards;
  - the asynchronous completion, rollback-certificate and original-age seams.
- **Results:** 5,130 unit tests pass. The complete GameTest had 1,415 of 1,416 pass:
  `RocketI6PlainGameTests.plainHookKeepsTUntilCurrentDAndOldOriginAreProved` fails only in the complete run ("Plain
  hook restored or removed D").
- **Native count:** the record corrects the count. B-PRE alone is 20 cases and 60 counted starts, so B-PLAIN, B-M and
  the combined terrain matrix cannot fit the 60-start limit above.

A fresh Codex session continues on the same branch. It starts from the record and
`D:/GitHub/ARCE-Task-Evidence/root-queue/W164/verification-9336b0fd4/INTEGRATION-NOTES.md`, in this order:

1. **Diagnose the complete-run failure first.** Tell fixture timing from production behaviour, then fix the cause with
   a regression. Do not raise a timeout or change the assertion.
2. **Keep `RocketTransferService` under 800 lines** by extracting the I6 entry point. Do not add logic to it.
3. **Finish items 3 to 5 above:**
   - M0 and M1 with the proved P hand-off;
   - cold preparation with a fresh request;
   - B2 delegation using B2's actual result, shared capacity and original request age;
   - M3 only on `ROLLBACK_PROVED`;
   - no-T routing;
   - the pending-preparation feedback.
4. **Run native verification in tranches of at most 60 counted starts.**
   - Tranche 1 is B-PRE (20 cases, 60 starts), after the implementation is complete. Then stop and report the count.
   - B-PLAIN, B-M and the combined terrain matrix are tranche 2, in a later continuation before merge.
   - No case is dropped or folded into another.

Stage temporary source archives under `build/`, not in the evidence directory. The `W152J` notes above still apply:
your small edits to `RocketOrdinaryRetirement` and `RocketTransferRecoveryService` stay in their own commits, and
Root reconciles them after W152J merges.

## Not in this task

- N3-B4 (the combined cut evidence), W98-R1 and governed admission.
- The W152J fix and the W80 command and view.
- ADRs, the risk register, `build.gradle`, workflows, status files and the ledger, which stay with Root.
