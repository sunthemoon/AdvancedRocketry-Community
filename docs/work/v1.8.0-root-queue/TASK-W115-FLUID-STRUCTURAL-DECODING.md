# TASK W115: structural decoding for durable fluid payloads (R39 for fluids, before C-E)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), R-035 in `docs/11-RISK-REGISTER.md` (its R39
paragraph), ADR-065's "W11c and W11d implementation note" (the item kinds bullet), and W110's record
`records/W110.md`, section "R3: structural durable decoding, explicit native validation", first. W110F's change to
`EscrowItemStackCodec` (`validateStructure` and `validateNative`, merged in `0b11269f`) is the model. You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w115`, branch `codex/v1.8.0-w115`, created by Root from the shared branch (the launch prompt names the SHA) |
| Record | `docs/work/v1.8.0-root-queue/records/W115.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W115/` |

This is a persistence slice: commit a one-page plan in the record before any code; an independent read-only review
follows before merge.

## The problem

Every decode of a durable fluid payload, including the escrow ledger's startup check, looks the Fluid up in the
registry and requires a native round trip: `EscrowOperation`'s `FLUID_PROPULSION_FILL` branch (`:49`) and
`EscrowCargoPayload`'s constructor (`:39`, used by `decode`). If the mod that provides a recorded Fluid is removed, the
ledger fails to decode and the whole world refuses to start (`INVALID_SCHEMA`), with the bytes kept. Root decision
R39 changed items so that durable decoding is structural and the native checks run where the operation acts; R-035
records that fluids change the same way before C-E opens cargo admission.

## What to do

1. **Structural durable decoding.** Decoding a durable fluid payload (a fuel fill's payload and a cargo fluid delta)
   checks only the structure: the exact field set (`FluidName`, `Amount`, optional `Tag`), the ID syntax, the amount
   range, the `Tag` type, the size, depth and node bounds and the framing. It never touches a registry or builds a
   `FluidStack`.
2. **An explicit native check.** Expose one native validation (registry lookup, non-empty, the canonical round trip)
   that callers run where the operation acts. Inventory **every** consumer of a decoded durable fluid payload in the
   merged code (admission, the fuel fill's O2 and readback, settlement, reconciliation, the inspector and anything
   else you find) and list them in the plan with file and line. Each consumer that acts on the Fluid calls the native
   check first; a failure holds only that operation (REPAIR, or the existing operation-local failure state; name
   which), never the ledger or the world, and changes nothing before it. Admission-time encoding keeps the full
   native check before any side effect. Cargo's O2 and readback do not exist yet (C-E): say in the record where C-E
   must call the check.
3. **Tests.** A ledger whose pending fuel fill or cargo operation names an unregistered Fluid, or a Fluid whose
   canonical form changed, still decodes and the world starts; the native check refuses the same record; each
   consumer you changed holds only that operation, with the other operations and the ledger unchanged. Existing
   fuel, floor and inspector pins stay byte-identical.
4. **Records.** Your record states the new scope in the terms of AGENTS section 3.3 (what is held, for how long, the
   log lines, recovery). Root updates R-035 and ADR-065 at merge.

No hash value, saved format version, schema, dictionary, bound, proof, lease or replay exclusion changes; no existing
assertion, limit or timeout is relaxed; stop and report if one seems needed. If a consumer cannot hold a single
operation without a design change, stop and report it with the options.

## Verification

Each changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker on
committed source, under the heavy-JVM lock (other workers hold it in turn; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D: above
10 GiB first. Memory is tight: run one Gradle process at a time, and before each Gradle run check that at least 6 GiB
of physical memory is free; wait if not.

## Not in this task

C-E and D-E (cargo admission, O2 and readback), the receipt charge (W114), C-P part 2 (W112F), machine and device
roots outside the escrow ledger, ADRs, the risk register, status files and the ledger (Root).
