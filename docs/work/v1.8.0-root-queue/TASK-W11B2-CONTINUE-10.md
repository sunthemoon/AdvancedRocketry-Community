# TASK W11b2, continuation 10: design 2, steps 3 to 7 (flights after release, required test)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md),
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) and [TASK-W11B2-CONTINUE-09](TASK-W11B2-CONTINUE-09.md)
first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `93af6e1d` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | [records/W11B2-DESIGN-2.md](records/W11B2-DESIGN-2.md), frozen at `238f4b9a`, and Root's decision in TASK-W11B2-CONTINUE-09 (`f83103fa`) |
| Input | `records/W11B2.md`, "Continuation 09" and "Continuation 09 resumed" (what is done, the evidence, and the exact unfinished scope) |
| Record | Add a "Continuation 10" section to `records/W11B2.md`. Keep the existing text |

## Where continuation 09 stopped

Continuation 09 delivered, as local commits `88c2272f`, `2def2668`, `f3828a81` and `ce9bb346`:
- the physical-rocket prefix identity (Root's decision);
- exact-group O4 pruning;
- F3 normalization, F5 UNKNOWN receipts and the M2 schema replacement with raw-preserving refusal;
- the READY and ARMED codecs and tables;
- the pre-L1 settlement-space reservation.

The whole unit suite (3,190) and all 1,059 GameTests pass at `ce9bb346`, and seven native settlement
cuts pass with two restarts each. The unchanged log checker still fails, only on N1's three fence
warnings.

## What to do: continuation 09's steps 3 to 7, in order (commit each step with its tests)

3. **ARMED and READY execution.** The server-issued serials, the replay exclusion, and the F1 source
   checkpoint (own-store, force, stable readback) before ARMED. Section 1's boundary table for
   `RocketTransferFences.classify`, `RocketEscrowGate.idle`, `EscrowRecoveryFence.reason`, joins and
   callbacks, cancellation and failed landing. The journal root stays at schema 2; only governed
   records are schema 3.
4. **The flight order F0 to F8** (section 5.4) and the credited-world reconciliation order (section
   5.3, C0 to C8), with section 5.1's canonical signatures. A missing credited source is REPAIR.
5. **The required test.** Strengthen `RocketSettlementIdleGameTests` exactly as section 1's "Required
   test" says: two complete flights after settlement, and two after an ordinary L2 release, with
   passengers, successor refuelling, handoff and cleanup. No new log expectation for
   FLOOR_PROOF_REQUIRED, FLOOR_CONFLICT or ENDPOINT_JOIN_PROOF_REQUIRED. The unchanged log checker
   must pass. This closes N1 and F3's complete-flight requirement.
6. **The A0 cases** (serial, replay, cancel) and as many of the section 5 S2 cuts as fit, two restarts
   each, pinned to the committed source and its jar. Include the two cuts continuation 09 listed as
   missing: native held-B or unloaded-C pruning, and the O4 replacement-before-callback cut. List what
   is left.
7. **F6 to F11.** Fold in section 6's dispositions: the console-origin `inspect` and `retry` commands,
   retry, status and fairness or backoff. Add the eight-governed-flight benchmark, measured against
   docs/17 and recorded as a measurement, not a pass.

The design is the contract. If the real code forces a change, write it with its reason at the top of
your section before coding around it. A change to an acknowledgement order, a proof, a hold, or the
replay exclusion needs Root first: stop, commit what you have with your record, and report.

## Commands

- The whole unit suite once.
- `runData` under the lock if data generation changes.
- `runGameTestServer` under the lock on a **committed** SHA, then the unchanged log checker.
- The native cuts under the lock, with evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C10/`.
- At the end, delete your generated `build/gametest/world` and Temp copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, and signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).
