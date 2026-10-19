# TASK W27c1: bay deployment receipts, their startup index and the shared admission authority (deployment disabled)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w27c1` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w27c1` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W27C1.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27C1/` |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md), frozen (rounds 1 to 3): section 2 ("The receipt store", including the frozen admission contract), section 5's N5 command, section 6's insertion refusal and the W27c1 row of section 9 with its F3 matrix. [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 2, the "Bay" and "Contract delta" bullets. Sections 3, 4, 7 and 8 belong to W27c2 to W27d; read them only for context |
| Contract delta | Recorded by Root in ADR-065's W27c1 implementation note: the one `EscrowAdmissionAuthority` belongs to `EscrowRuntime` (B3-U, W93), and each store attaches to the delegate the runtime registered. Where W27A section 2 says the authority "belongs to the coordinator", read it this way. The `tryReserve` and `tryRetain` results and the no-release rule are unchanged |
| Risk | R-039 in `docs/11-RISK-REGISTER.md`, registered by Root before this task (the startup refusal of a future or invalid receipt root, the capacities, the insertion refusal and the N5 command); R-038 for the bay block itself |
| Ledger | `block_variant:loader/1` stays PLANNED until W27d and W27e |

This is a persistence slice (AGENTS.md section 13): write the one-page plan first, and Root arranges an
independent read-only review before merge.

## 1. The plan first

Commit `docs/work/v1.8.0-root-queue/records/W27C1-PLAN.md` (one page) before any Java change. It names:
- the classes you add or change, with their packages;
- the writer: generalize `EscrowLedgerWriter` into a typed writer, or reuse `RocketJournalWriter`'s
  off-thread machinery. Section 2 ("Writer") requires encoding, digest, staging, force, readback and
  replacement off the server thread, with the server thread only capturing an immutable map. Say which
  you chose and why;
- the startup order in `EscrowRuntime`'s `ServerStartingEvent` handler (register BAY, load-only lookup,
  validate, index) and how the pre-start managed-data check refuses a bad root;
- how the retained union and its 1 KiB byte reservation are derived from the indexes;
- every new log line, with its level and when it repeats.

Then continue; do not wait for Root unless the plan finds a contradiction with the contract.

## 2. Scope (the W27c1 row of section 9)

1. **The managed type.** `ManagedSavedDataType.BAY_RECEIPTS`, file
   `data/advancedrocketrycommunity_bay_receipts.dat`, schema 1, epoch `v1.8.0-bay-receipts`, no legacy
   schema. An Overworld-owned `BayReceiptSavedData`, bound to the server-lifetime escrow runtime, with no
   static collections.
2. **The receipt codec**, exactly section 2's table (24 fields, their types and encodings) and bounds:
   a receipt of at most 1,024 uncompressed bytes including its framing, depth 3, 40 nodes; the section of
   at most 4,194,304 bytes, the root of at most 4,195,328, depth 4, 131,072 nodes; at most 4,096
   receipts. The codec invariants: unique `op`, unique key, at most one receipt per satellite, REGISTERED
   requires all four hashes, released receipts cannot re-arm or retire online, unknown fields refuse. The
   codec refuses and never normalizes. `package_sig` is `RocketNativeResources.signature`; the
   generation digest is the shared `persistence/CanonicalNbtDigest`.
3. **The writer**, as section 2 ("Writer") states: one captured generation and one successor; exact
   `(generation, digest, outcome)` completions on the server thread; unknown outcomes resolved by bounded
   readback or retried as the exact candidate every 200 ticks; three unresolved failures make the store
   unavailable (BAY_RECEIPTS_UNAVAILABLE); a drain at `ServerStoppingEvent` and an abort on the crash
   path; `CheckedSavedDataPolicy(BAY_RECEIPTS, atomicMove)` then `forceDirectory` on POSIX; writes start
   at most once per 10 ticks, and autosave never publishes queued intents. Use the drain clock that W104
   merged (`250029a5`) if you reuse a drain loop.
4. **The startup index (N6).** At `ServerStartingEvent`, after the Levels exist and before the first
   tick, read an existing file with the load-only lookup (which never creates it), validate it and index
   it: NOT_LOADED before that point, NO_FILE when no file exists (the first admission creates it), LOADED
   otherwise. The index holds durable, queued, captured and UNKNOWN receipts, including REPAIR and
   released-but-unresolved ones, so it answers while the writer is unavailable. A future or invalid root
   refuses whole-world startup and keeps its bytes, as RESOURCE_ESCROW does (`ManagedSavedDataPayloadValidator`).
5. **The admission authority.** Register the BAY consumer with `EscrowAdmissionAuthority.register` in the
   runtime's startup handler, NOT_LOADED first, and attach the receipt store to its delegate. Add
   `tryRetain(op, owner)` against the retained union (durable, queued, captured and unknown receipts,
   REJECTED included): 4,096 per server, 128 per owner, and the 1 KiB byte reservation. Its results are
   RESERVED, ALREADY_HELD, IDENTITY_CONFLICT, SERVER_FULL, OWNER_FULL and UNAVAILABLE. There is no release
   call and no timeout eviction. `tryReserve` keeps its results and behaviour. Every existing PROPULSION
   test (unit and GameTest, including `RocketJournalInterlockGameTests` and the escrow GameTests that
   retarget the ledger) must pass unchanged, with BAY registered.
6. **The insertion refusal** (moved from W27b): a bay refuses a package (menu, hopper, dropper and the
   item capability, simulated and actual) while a receipt blocks it, as section 6 states. A REJECTED
   receipt never blocks.
7. **The N5 command** `/arce bay receipt retire-rejected <satellite>`, with the permission of
   `/arce rocket recover`. It refuses unless the refusal is exactly acknowledged, no INTENT, REGISTERED,
   REPAIR or released receipt names the receipt's rocket, and the transfer and transaction journals hold
   no record for that rocket. It writes through the receipt writer, frees only the retained slot on the
   exact removal acknowledgement, and logs one audit line with the operator, the key and `op`. English
   and Chinese text for every message.
8. **Gated.** Nothing in production creates a receipt. Tests create them through test hooks only (follow
   the escrow test hooks' pattern). No deployment, no registry sequence, no DEPLOYMENT_PENDING fence and no
   launch service change (W27c2), no transaction-journal policy (W27c3), no transfer hook (W27d).

## 3. Tests (the W27c1 row of section 9)

- **A0 (unit):** a real full-field receipt at its maximum encoding, measured against 1,024 bytes, and the
  root and list framing; just inside and just outside every bound; each codec invariant; 4,096 and 128
  retained, and 64 and 8 pending; the F3 matrix at the authority, with W11b3 and the bay on the very same
  authority instance (63 escrow operations plus one bay fill the 64; a second bay and a loader both
  refuse; reverse the consumers; one copy counted once across durable, captured, successor and UNKNOWN;
  all 64 in REPAIR across a restart stay full; the REJECTED re-arm sequence; 7 escrow plus 1 bay on one
  rocket; retained 4,096/128 independent of pending; mixed-store load before admission; a failed and an
  unknown retirement).
- **A1 (GameTests or real-file unit tests, as each needs):** real files with injected committers;
  startup refusal of a future and of an invalid root, with the bytes kept; startup indexing before the
  first tick; NOT_LOADED failing closed for both consumers; insertion refused while a blocking receipt
  exists, through each insertion path; the N5 leak (orphan REJECTED receipts until the owner's 128 are
  full, retire one, admit again) and every refusal of the command.

## 4. Commands

- The whole unit suite once.
- `runData` under the heavy-JVM lock if you add language entries or other generated resources.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker. A new expected WARN or
  ERROR needs its own manifest entry with an exact count, and the record says why.
- `python -B scripts/validate_repository.py --require-approved-identity`.
- Check that C: and D: have more than 10 GiB free before each heavy start. Other workers (W98, W105,
  W106 and their successors) also take the lock; release it between runs.
- At the end, delete your generated `build/gametest/world`, native copies and old jars with a plain
  `rm -rf`, and make sure the lock is released. If the tool refuses, record the exact paths; Root
  deletes them.

## 5. Write scope and not in this task

Write scope: new classes for the receipt store, codec, writer, index and command; `EscrowAdmissionAuthority`
and `EscrowRuntime` for the BAY registration and `tryRetain`; `ManagedSavedDataType` and its validator for
the new type; the bay block entity and handler for the insertion refusal; the command registration, the
language files and data generation entry points (append this slice's entries only); tests; the plan and
the record.

Not in this task: W27c2 to W27f, ADRs, the risk register, status files, the ledger, build scripts and
workflows (Root). No Mixin. If R-039 or ADR-065's W27c1 note does not match what you build, stop and
report. A class over 500 lines gets a responsibility check in the record; one over 800 lines is a stop
and report.
