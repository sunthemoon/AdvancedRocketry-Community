# TASK W114: cargo and fuel receipts fit on every mod Level (Root decision R40)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 3 (with its R40 amendment), and W113's `D:/GitHub/ARCE-Task-Evidence/root-queue/W113/DECISION-NOTE.md`
(SHA-256 `abb92054…e757`) and `MEASUREMENTS.md` beside it first. W113's `patches/` and `probes/` are measurement
probes, not reviewed code: use them as a guide only. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w114`, branch `codex/v1.8.0-w114`, created by Root from the shared branch after `0b11269f` (W110 with W110F merged; the launch prompt names the SHA) |
| Record | `docs/work/v1.8.0-root-queue/records/W114.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W114/` |

This changes how admission charges a settlement, so it follows the persistence-slice rule: commit a one-page plan
in the record before the code, and an independent read-only review follows before merge.

## The problem (W113)

Admission charges every operation's worst-case settlement receipt against the frozen 2,048-byte ceiling. The charge
uses an actor name of 128 NUL characters (256 bytes) and five rocket markers (73 bytes each). With the mod's own Level
IDs (30 to 36 characters) a transfer-guarded receipt does not fit: guarded cargo is refused on every mod Level (the
worst, an item unload at Tau Ceti g, previews at 2,089 bytes), and so is a guarded **fuel fill** (2,061 bytes on the
Moon). A landed rocket keeps its transfer guard, so under escrow it could neither refuel nor move cargo there.

## Root decision R40 (2026-10-20)

W113's options (b) and (c) together. No saved format, schema, dictionary, pin, ceiling, root capacity, R6/R8/R9 value
or flight-table reservation changes.

1. **(b) The actor's real bound.** A new settlement intent's actor name must pass the 1.20.1 login rule: 1 to 16
   characters, each a code point from 33 to 126, so at most 16 modified-UTF bytes. The console's `Server` passes.
   - Enforce it where every new intent is created, `EscrowSettlementIntent.preview` (the operator, the capacity
     preview and the benchmark ceiling all call it), and refuse early in `EscrowCommandActor.require` with a clear
     operator message, before any side effect.
   - Decoding stays as it is (128 UTF-8 bytes), so a stored intent or receipt with a longer name still decodes.
   - The capacity preview (`EscrowSettlementCapacity`, today `"\0".repeat(128)`) charges a worst-case 16-byte name.
2. **(c) Cargo is charged the one rocket marker it can hold.** Admission already refuses a cargo capture with any
   retained marker (`EscrowTransferAdmission:23`) and refuses cargo while any other record names the rocket (`:39`,
   mirrored for fills at `:41`), so a cargo operation's O1 holds only its own rocket marker.
   - A named constant of 1 applies to every component that contains cargo, in `requireAdmission` **and** in the
     shared settlement preview. Never on the admission path alone: W113's run C1X admitted cargo that way and then
     made every unrelated settlement freeze in the same root fail.
   - Move the retained-marker refusal ahead of `fillRefusal`, so a retained marker reports `OTHER_TRANSFER_PENDING`,
     not `ROCKET_LIMIT`.
   - Fuel keeps five markers.

## Tests

- **Every kind fits.** A permanent test over the mod's registered Level IDs (`advancedrocketrycommunity:moon`,
  `:mars`, `:space`, `:venus`, `:tau_ceti_f`, `:tau_ceti_g`) and the three vanilla Levels, for the four cargo kinds and
  the fuel fill, guarded and unguarded: real admission, then ACCEPT_CURRENT and both ABANDON survivors through O1, O3
  and O4, neutral normalization and both root round trips, with the real encoders. Also the real `prepareTransfer` of a
  guarded fuel fill on each mod Level. State the worst receipt and its margin (W113: cargo 1,557 bytes and fuel 1,833
  bytes at Tau Ceti g).
- **(b):** a 17-character name, a NUL name, a space and a non-ASCII name are refused by `preview` and by
  `EscrowCommandActor`; a 16-character name and `Server` pass; a stored intent and receipt with a 128-byte name still
  decode.
- **(c):** the cross-component freeze (admit a guarded cargo operation at Tau Ceti g, then freeze an unrelated REPAIR
  fuel fill in the same root; it succeeds); an admitted cargo operation's O1 holds exactly one rocket marker; a cargo
  capture with a retained marker reports `OTHER_TRANSFER_PENDING`. Show in the evidence (a temporary patch, not
  committed) that the C1X variant fails the cross-component test.
- **Re-pinned tests.** W113 found 10 existing tests that pin today's charge. Move each to its new exact boundary and
  list each in the record with the old value, the new value and the reason. Re-pin the two `EscrowLayoutBudgetTest`
  cases with worst-case native endpoints, so they keep proving the same property. `requireAdmission` still refuses a
  sixth fuel marker; keep that assertion. W110F's `EscrowItemDimensionCapacityTest` (merged after W113 measured) pins today's per-Level refusals: under R40 its refused cells fit, so re-pin it to the new verdict and keep its check that a candidate that does not fit is refused before any side effect, with a candidate that still does not fit (an oversized payload or an unencodable Level ID).
- No test is deleted, no assertion weakened, no limit or timeout raised. Stop and report if one seems needed.

## Verification

Each changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker on
committed source, under the heavy-JVM lock (another worker may hold it; wait), releasing the lock and deleting
`build/gametest/world` in the `finally` of the same detached script that took it (WORKER-RULES). Check C: and D:
above 10 GiB first. Memory is tight: one Gradle process at a time.

## Not in this task

The `ESCROW_FULL` versus `CARGO_TOO_LARGE` mapping (C-E takes it, W113 item 4), the receipt ceiling, the receipt
layout, admission switches, devices, ADRs, the risk register, the design records, status files and the ledger (Root
updates W11CD-DESIGN, R-035 and ADR-065 at merge).
