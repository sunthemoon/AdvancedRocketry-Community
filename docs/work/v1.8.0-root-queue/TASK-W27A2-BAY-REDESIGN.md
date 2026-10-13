# TASK W27a round 2: satellite bay design, revised after the independent review

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work
only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w27a-bay-design` (round 1 plus the current shared branch) |
| Branch | `codex/v1.8.0-w27a-bay-design` (local only; Root merges) |
| Round 1 | `records/W27A-DESIGN.md` at `b55a41f2`, and `records/W27A.md` |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27a-review/REVIEW.md`, SHA-256 `6f5b186fb066868bb4037fc6c23c20213977e901935bce56b75810f09ed245a1`. Verdict REDESIGN: F1 High; F2 to F5 Medium |
| Deliverables | A revised `records/W27A-DESIGN.md`, with a section at its end answering each finding, and a short record `records/W27A2.md` |

Read round 1, the whole review, and the original task
[TASK-W27A](TASK-W27A-SATELLITE-BAY-DESIGN.md). W11b2 is still being implemented on branch
`codex/v1.8.0-w11b2-rocket-integration`. Read its current recovery fences (`RocketTransferFences`,
`RocketTransferRecoveryService` at its branch HEAD) and `records/W11B2.md`, because F1 depends
on them.

## Root's decisions

Root accepts all five findings. The revised design must do the following.

1. **F1: bay-aware recovery before ordinary recovery.**
   - Specify a coordinator-owned bay recovery protocol. It runs **before** ordinary transfer
     recovery, duplicate election, reconnect settlement and journal removal.
   - Bind it to the exact logical rocket, transfer ID, end and snapshot hashes.
   - Define how it obtains or reconstructs a missing source, establishes the physical chunk,
     position and flight state, keeps PREPARED T through D7, and handles already-loaded
     duplicates.
   - Only those coordinator-owned actions are permitted; there is no blanket exemption for
     the transfer service.
   - Alternatively, redesign retirement around stationary-source recovery, with the right
     source hashes and a durable journal-removal proof. Choose one, and say why.
   - List the native cases B3 to B8: source present, source absent, offline passengers, and
     both mixed-write combinations of S and T, driven through the real restart service.
   - Say exactly which W11b2 hook the bay needs. If W11b2 must expose a hook it does not have,
     name it precisely, so that Root can add it to W11b2's scope.
2. **F2: the proof tuple from immutable identity fields.**
   - Give separate rules for a fresh data launch and for an existing matching data satellite.
   - Never require a current mission, or a retained historical mission, to consume a valid
     replay.
   - Test cancelled, claimed, pruned and replaced initial missions.
3. **F3: one shared pending reservation.**
   - REPAIR and released-but-unresolved receipts count against the shared 64-per-server
     pending reservation until they are durably resolved.
   - Count unique operations across the durable, queued, captured and unknown-outcome states.
   - Specify how a REJECTED receipt returns to INTENT.
   - The bay and W11b3 use **one** admission authority. Include the mixed-store tests.
4. **F4: explicit replay before admission.**
   - The shared launch service checks for an existing-identity replay **before** the
     new-launch catalog, stats, research and quota validation, so the terminal's replay
     results are preserved.
   - The operational-registry, ownership and deployment-fence checks still run.
   - Pin the parity cases the review lists.
5. **F5: a common orbitability check.** Add a body and orbitability check at the bay
   checkpoint, before the kind-specific validation. Data missions keep their existing
   discovery behaviour. Test non-orbitable bodies, the legacy `space` plan, a missing body,
   and a station's captured orbit body.

Keep everything from round 1 that the review did not challenge. Check every vanilla claim with
`javap` against the mapped jar, and cite the class and method.

## Not in this task

- Code and tests.
- ADR and risk-register edits. Write the proposed text in your note.
- Status files and the ledger.
