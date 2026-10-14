# TASK W11b2 design 2: the settlement-to-flight boundary and credited-world reconciliation (design only)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `e66360b0` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-2.md` (new). No production or test code |
| Inputs | `records/W11B2.md`, "Continuation 08" (BLOCKED for Root); the settlement review `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-settle-review/REVIEW.md`, SHA-256 `9ec5c86fa0ed873444ac90668f86fcdc14dee470ffadd70d2d514252f667ba08` (F1 to F12) |

Continuation 08 stopped for Root at two related gaps. The settlement review's F1, F3, F4 and F5 belong
to the same boundary. Root decided to freeze them in one design note before any more code. An
independent review follows. Production admission stays disabled throughout.

## The design must answer

1. **A fresh launch after settlement (continuation 08's N1 finding, and review F1).**
   - Today a released receipt counts as known protected history. So the settled rocket's next fresh
     transfer is classified FLOOR_PROOF_REQUIRED, then FLOOR_CONFLICT, then
     ENDPOINT_JOIN_PROOF_REQUIRED. The new destination entity is identity-locked for ever.
   - Define a distinction between a fresh launch and recovery that preserves the proof:
     - what proves that a transfer began after the settlement's release was acknowledged;
     - how `RocketTransferFences.classify` and `EscrowRecoveryFence.reason` use that proof;
     - why a stale or replayed pre-settlement transfer can never use it.
   - No live-set membership shortcut, and no weakening of the existing protected-source recovery
     rule.
   - State the test that must then pass: a settled rocket launches, ascends and completes a flight,
     with no new log expectation.
2. **Review F3:** an unbound chosen survivor is locked after its first native resource change.
3. **Review F4:**
   - one held settlement starves marker pruning for every other released settlement;
   - the O4 callback prunes the wrong receipt.

   Define per-settlement pruning and the exact receipt the callback prunes.
4. **Review F5:** receipts record `audited_loss=0` and `loss_known=1` for every accept-current
   decision. Define what the receipt records when the loss is not known; keep signed per-fluid
   accounting itself out of scope. Say how the receipt schema changes and how existing test-world
   receipts migrate.
5. **Credited-world reconciliation.**
   - Freeze the native rocket mutation envelope. Today two signatures exist:
     - `RocketFuelState.resourceSignature()`, used by the propulsion-operation endpoints;
     - the settlement observer's `hash("arce-escrow-rocket-1:" + logical + ":" + snapshotHash + ":" + fuelSignature)`.

     `EscrowPrefix.classify` compares them literally. Choose one canonical endpoint signature, or a
     versioned mapping, without silently changing existing operation hashes.
   - Freeze the reconstruction boundary that W27a section 8 leaves open: what a credited source
     reconstructs, from which proved records, and when a proof may return `proved=true` in
     `RocketEscrowAudit.attempt`.
   - Order every step with its acknowledgement and its forced-stop cuts.
6. **Review F6 to F11.** Answer each in one or two lines: fix in the implementation, or record as
   accepted with a reason. Include the missing `inspect` and `retry` commands.
7. **Risk text.** Propose the update to R-035 (scope, duration, logs, recovery). Root edits the
   register.

Keep the note focused: tables over prose, one section per question. If W11a, W27a or ADR-065 cannot be
followed literally somewhere, say exactly where and propose the smallest change.

## Not in this task

- Any production or test code, production admission, W11b3 and the bay slices.
- ADRs, the risk register, status files and the ledger (Root).
