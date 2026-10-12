# TASK W11a: design of the D3-A escrow/receipt coordinator (design only)

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11a-d3-design` |
| Branch | `codex/v1.8.0-w11a-d3-design` (local only; Root merges) |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W11A-DESIGN.md`, two to four pages, and a short record `W11A.md` |

## Why

ADR-065 §4 (loaders, linker, monitor and the mutable rocket resources) and its §8
satellite bay move resources between a block entity, stored with the terrain chunk, and a
moving rocket entity, stored in the entity chunk file. Vanilla save order makes no such
transfer crash-atomic. The owner chose **D3-A** (§10): an explicit escrow/receipt
coordinator with documented durable barriers and S2 fault-cut verification. ADR-065 §10
says: "Admission must not begin before the selected durability contract is frozen."

This task produces that contract **as a design**, for independent review. Write no
production code. W11b implements it after the review.

## Read

- ADR-065 sections 4 (4.1, 4.1.1, 4.2, 4.3), 8 (the satellite bay, for the second
  consumer), 10 (D3-A; D3-B was rejected) and 11.2.
- ADR-027 and ADR-054 (the existing torn-save residual class that D3-A does **not**
  select), ADR-019 (controller-owned snapshots), and AGENTS.md section 3.3.
- Existing durable writers in this repository, to reuse instead of inventing:
  - the station registry's checked stage, fsync, readback and replace path;
  - `persistence/**`, including `GuardedChunkSaves` and the atomic SavedData files;
  - the rocket transfer journal (`RocketTransferSavedData`) and its recovery service, which
    W02F recently hardened.
- Facts about vanilla and Forge 1.20.1 storage. ADR-073 and
  `docs/work/v1.8.0-gametest-log-expectations/STORAGE-ALIAS-INVESTIGATION-01.md` show that
  observing `ChunkDataEvent.Save` does **not** mean an asynchronous write is durable.

## The design must decide

1. **The authority:**
   - Which store is the durable authority for a pending transfer? The ADR suggests a
     separately forced ledger.
   - How is that ledger made durable (fsync / stage-readback-replace), and how much may
     it hold? Use the ADR's 64 per server and 8 per rocket.
2. **The record:**
   - fields: unique UUID, the before and after signatures of both endpoints, the frozen
     native payload, the endpoint epochs, the phase;
   - schema version and byte limits.
3. **Barriers and phases:** the exact order of in-memory mutation, ledger writes and
   endpoint markers. At each point, say what is durable, and what a crash there leaves
   after a restart.
4. **Reconciliation on load:**
   - How does each endpoint, the block entity and the rocket entity, carrying or lacking
     the transfer's marker, reconcile against the ledger idempotently?
   - When may a ledger record be retired? The design must not rely on `ChunkDataEvent.Save`
     meaning durable.
   - A lost or unknown write outcome keeps the resources and requires repair; it never
     falls back to an old snapshot.
5. **Blocking:**
   - Which mutations, materializations, flights, countdowns and disassemblies are blocked
     while a record is pending?
   - No one may withdraw a tentative destination payload.
6. **Budgets:** how the coordinator fits §4.1.1, with one whole-snapshot reconstruction
   per tick and the 64-operation fair queue.
7. **The S2 fault-cut plan:**
   - the exact cut points;
   - how each cut is produced in a test (in-process injection, or a killed dedicated
     server where needed);
   - the expected outcome of each, as a table.
8. **What W11b delivers first:**
   - the coordinator;
   - `fluid_fuel_loader` as the first consumer: mB-precise refuelling of typed propulsion
     banks, which resolves W02 review F3;
   - and which of the other §4.2 and §4.3 devices follow, in which slices.

## Rules for this task

- Write only the two record files. No production code, no tests and no ADR edits.
- Be concrete: names, fields, byte limits, phases, cut points. Where something is
  genuinely unknown, say so and propose how W11b will verify it.
- Run `git diff --check` before committing.

## Not in this task

- Any implementation.
- The satellite bay implementation (C17c).
- Status files and the ledger.
