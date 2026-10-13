# TASK W27a: satellite bay design note (C17c-02, receipt durability on the D3-A core)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first. This is design
work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w27a-bay-design` |
| Branch | `codex/v1.8.0-w27a-bay-design` (local only; Root merges) |
| Contract | ADR-065 section 8 (the satellite bay) and section 10 (D3-A), plus the merged W11b1 core (`f1eb5d83`) and its ADR-065 note |
| Deliverables | `docs/work/v1.8.0-root-queue/records/W27A-DESIGN.md` (three to five pages) and a short record `records/W27A.md` |

## Why

ADR-065 section 8 specifies the bay in detail. It requires a deployment receipt that is
independent of `SatelliteState`, in the scoped durable coordinator. The W11a design left the bay
as a sketch: "its receipt retirement contract must be reviewed before admission; ordinary
AtomicSavedData flush is not automatically proof."

The escrow core (W11b1) is merged. Rocket integration (W11b2) is in progress, on branch
`codex/v1.8.0-w11b2-rocket-integration`; read its design note `records/W11B2-DESIGN.md`. This
note freezes the bay's contract so its implementation can follow W11b2 without a redesign
round. AGENTS.md section 13 requires one independent read-only review of it.

## Read

- ADR-065 sections 8, 10 and 11, and the W11b1 implementation note after section 10.
- `records/W11A-DESIGN.md` round 2, especially the bay sketch and section 8.
- `records/W11B2-DESIGN.md`.
- ADR-010, ADR-049, ADR-050 and ADR-051: satellites, the registry, and missions.
- The current `SatelliteManager`, `SatelliteMissionRegistry` (schema 4 since W21b), and the
  rocket orbit checkpoint code.

## The design must decide

1. **The receipt store.**
   - Its schema and its section: schema 1, at most 4 MiB, at most 4,096 receipts, 128 per
     owner, at most 1 KiB each.
   - Its key, `(logicalRocketId, bayRelativePosition, satelliteId)` together with the operation
     UUID, and its fields.
   - How it is forced and proved through the W11b1 writer and checked policy.
   - How capacity is reserved before registry registration.
2. **The sequence.**
   - First the intent receipt, then the registry registration, then the registration proven
     durable, then the registered outcome receipt forced, then the package exposed or removed.
   - For each step: what is durable, and what a crash leaves behind.
   - A matching replay consumes the retained package idempotently and never registers twice,
     even after a decommission.
   - Decommission is interlocked with `DEPLOYMENT_PENDING` until the receipt is durable.
3. **The registry proof.** How registration is proven durable without treating an ordinary
   `AtomicSavedData` flush as proof: a checked writer for the satellite registry, or an
   equivalent.
4. **Retirement.** When a receipt may be retired, the decommission rules, and what happens to
   tombstones.
5. **The bay block.**
   - Its schema-1 package root, with the native item's 4 KiB bound and the package identity
     decoder.
   - The host rocket adapter.
   - The flight lock.
   - The checks that the owner, package and rocket identities agree.
   - The limits: 8 deployments per rocket and 32 per server tick, through a fair recovery
     queue.
6. **The orbit checkpoint.** The source body's orbit; launch targets, orbitability and
   discovery, catalog, research, quotas and byte budgets. Rejection keeps the package and its
   reason. Retries reuse the same receipt identity.
7. **The production launch service.** It shares validation with `SatelliteManager`. It uses no
   release-test hook, no invented ServerPlayer and no FakePlayer.
8. **The slices.** Split the implementation into reviewable slices, with their tests and S2
   cuts. State what depends on W11b2.

Check every vanilla claim with `javap` against the mapped jar, and cite the class and method.

## Not in this task

- Code and tests.
- ADR and risk-register edits. Write the proposed text in your note.
- Status files and the ledger.
