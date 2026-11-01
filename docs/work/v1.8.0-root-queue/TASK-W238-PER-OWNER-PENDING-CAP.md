# TASK W238: the per-owner pending cap of 16 (R244)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w238` (new; branch `codex/v1.8.0-w238` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W238.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W238/` |
| Decision | Root decision R244 under the owner's delegation, in [OWNER-DECISIONS-20261101](records/OWNER-DECISIONS-20261101.md): 16 pending operations per owner, over the same unique pending union of loader, cargo and bay operations (PROPULSION and BAY), with no operator exemption |
| Design | [W231](records/W231.md) section 4: the candidate rules, the OWNER_PENDING_FULL refusal, the exact English and Chinese messages, the restart and grandfathering rules, and the exact ADR-065 section 10 text; W231's measurement and fairness tests (`W231BayCapacityTest`, `W231BayFairnessTest`) are on branch `codex/v1.8.0-w231` at `986e11fa7` |

**Root decision R244.** Implement W231's per-owner cap with the value 16, exactly as W231 section 4 specifies:

1. **Admission.** `EscrowAdmissionAuthority` counts each owner's pending operations over the unique union, charged
   to the server-validated stored owner. The check runs on the server thread before any reservation, INTENT, capture
   or resource change. The order is identity, unavailable or idempotent first, then SERVER_FULL, ROCKET_FULL and
   OWNER_PENDING_FULL for new operations. A refusal changes no resource and no count.
2. **Codes and messages.** Append OWNER_PENDING_FULL to the existing result, reason and status codes without reusing
   or shifting any existing code. Add W231's English and Chinese messages for bay, loader and cargo, and the
   floor-retirement command's preview.
3. **What stays.** The retained 128 per owner, 4,096 per server and the byte allowance are unchanged. An acknowledged
   REJECTED releases pending only, and its re-arm reacquires and can meet OWNER_PENDING_FULL. Existing pending
   operations, phase updates, recovery and exact retirement need no new slot.
4. **Restart and old saves.** Owner counts are rebuilt from both complete indexes before admission, and NOT_LOADED
   still fails closed. A save already over the cap keeps every valid operation. Startup and saving never fail
   because of the cap; that owner admits nothing new until below 16. There is no new persisted field, schema bump,
   eviction or timeout. The value is a constant, not a configuration option.
5. **Tests that fail first.**
   - Mixed-owner fairness: one owner at 16 is refused while others are admitted.
   - Saturation: four owners at 16 fill 64, and a fifth gets SERVER_FULL.
   - Re-arm at the cap.
   - Restart rebuild.
   - A grandfathered save over the cap loads and admits nothing new for that owner.
   - Loader, cargo and bay all count against the one union.
   - The floor-retirement preview reports the refusal instead of throwing.
   - The message keys exist in both languages.
   Bring in W231's measurement and fairness tests where they still apply.
6. **Drafts for Root.** The exact ADR-065 section 10 text from W231 (with 16) and the R-042 or R-039 risk text that
   changes, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W238/W238-drafts.md` with an audit. Do not edit ADR-065 or
   the risk register.

Write a short design note at the top of the record before the code. An independent review follows before the merge.

## Verification

- The changed tests, failing first.
- The `resourceescrow` and bay packages, and the whole unit suite.
- The escrow and bay GameTest batches three times under the heavy-JVM lock.
- The repository validator.

## Not in this task

Bay admission itself, the safe exit (W230), status files and the ledger.
