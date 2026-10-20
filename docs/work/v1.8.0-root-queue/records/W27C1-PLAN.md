# W27c1 plan: bay deployment receipts, their startup index and the shared admission authority

Date: 2026-10-20. Task: [TASK-W27C1](../TASK-W27C1-BAY-RECEIPT-STORE.md). Base: `ffc134e1`. Contract:
[W27A-DESIGN](W27A-DESIGN.md) sections 2, 5 (N5), 6 and the W27c1 row of section 9, read with ADR-065's W27c1
note (the authority belongs to `EscrowRuntime`). Deployment stays disabled; nothing in production creates a receipt.

**Classes.** All new classes go in `resourceescrow`, beside the runtime that owns the authority, so the
authority's package-private delegate API (`Store`, `retarget`, `noFile`) stays closed.
- `BayReceipt` (the 24-field immutable record, `Phase`, `Key(rocket, bay)`), `BayReceiptRoot` (store ID,
  `issued_through`, `writer_generation`, receipts; the root invariants) and `BayReceiptCodec` (strict encode and
  decode, every bound, `RocketNativeResources.signature` for `package_sig`, `CanonicalNbtDigest` at depth 4).
- `BayReceiptWriter` (the checked writer), `BayReceiptStore` (RAM state, mutations, index), `BayReceiptSavedData`
  (Overworld root), `BayReceipts` (the runtime-owned holder: BAY delegate, startup, insertion gate, lifecycle).
- `BayReceiptRetirement` and `BayReceiptCommands` (N5), `BayReceiptTestHooks` (gated like `EscrowTestEndpoints`),
  and `datagen/V180BayReceiptLanguage` (EN and zh).
- Changed: `EscrowAdmissionAuthority` (`Index.retained`, `tryRetain`, non-mutating checks), `EscrowRuntime`
  (registration, startup, tick, drain, abort), `ManagedSavedDataType` and `ManagedSavedDataPayloadValidator` (the
  new type), `BetaWorldMigrationEvents.recoveryHint` (a receipt file also gets the whole-world hint, as escrow
  does), `SatelliteBayBlockEntity.admissible` (insertion refusal), and W95's `EscrowLoaderCutProbe`, whose N3 cuts
  registered their own BAY index and would now throw: they substitute a synthetic index through a gated seam.

**Writer.** A new `BayReceiptWriter`: EscrowLedgerWriter's state machine generalized to a typed, off-thread
candidate, written as its own class. EscrowLedgerWriter is not changed: its contract encodes and validates on
the owner thread, and every PROPULSION test must pass unchanged. `RocketJournalWriter` is not reused: it has no
exact `(generation, digest, outcome)` completions, no unavailability count, no 10-tick cadence and no guard. The
server thread captures an immutable `BayReceiptRoot` (at most 4,096 references to immutable records, no NBT). The
IO thread encodes, round-trips through the decoder, digests, stages, forces, reads back and replaces under
`CheckedSavedDataPolicy(BAY_RECEIPTS, committer)`, then `forceDirectory`. The first attempt returns the encoded
candidate and its digest; retries move exactly those bytes and must report the same digest. One captured
generation and one successor; retries every 200 ticks; three unresolved failures make it unavailable; starts at
most once per 10 ticks; W104's elapsed-time drain clock at `ServerStoppingEvent`; abort on the crash path.
Autosave publishes nothing: `save(File)` is a no-op and the root is never marked dirty.

**Startup.** The authority is created once with both delegates, PROPULSION and BAY, NOT_LOADED, so it never
exists without BAY. `EscrowRuntime.starting` keeps the PROPULSION lookup, then: `BayReceiptSavedData.existing`
(the storage's load-only `get`, which never creates); if present, bind the store (writer at the root's
generation, checked baseline readback) and retarget BAY to it (LOADED); if the file is proven absent
(`CheckedSavedDataFile.presence`), NO_FILE; otherwise BAY stays NOT_LOADED. The pre-start check refuses a bad root
first: `SavedDataSchemaMigrator` (future schema, shape, epoch), then the validator's `BayReceiptCodec.decode` and
the baseline readback; startup fails and the file is never staged or rewritten.

**Unions.** The store's index covers its durable, RAM (queued) and captured (active or UNKNOWN) roots; a
successor's contents are still RAM. *Pending:* an op counts when any state has it as INTENT, REGISTERED, REPAIR or
released, with the identity of the newest such state. *Retained:* every op in any state, REJECTED included.
`tryRetain` derives the retained union from that index plus its own queued retentions: 4,096 per server, 128 per
owner, and the byte reservation, which charges every retained op at its 1,024-byte ceiling against the
4,194,304-byte section (the same boundary as the count; a real full-field receipt measures 938 bytes). A put
checks both results first and then commits both, so a refusal changes nothing. *Blocking:* a satellite whose
receipt is INTENT, REGISTERED or REPAIR in any state; NOT_LOADED blocks every satellite.

**Log lines.** All once per event, never per tick:
- WARN `ARCE_BAY_RECEIPTS_NOT_LOADED`: the file exists but did not load (once per start).
- WARN `ARCE_BAY_RECEIPTS_BASELINE_UNAVAILABLE`: the bind-time readback differs (once per start).
- INFO `ARCE_BAY_RECEIPTS_INDEX state= receipts=`: once per start.
- INFO `ARCE_BAY_RECEIPTS_WRITE_UNKNOWN`: the first unknown outcome of a generation; INFO
  `ARCE_BAY_RECEIPTS_WRITE_RESOLVED` when it resolves.
- WARN `ARCE_BAY_RECEIPTS_UNAVAILABLE` and INFO `ARCE_BAY_RECEIPTS_AVAILABLE`: each availability transition.
- WARN `ARCE_BAY_RECEIPTS_DRAIN_ABANDONED`: the stop drain gives up after three unknown outcomes (once per stop).
- INFO `ARCE_BAY_RECEIPT_RETIRED`: the N5 audit line, once per command at its final outcome.
- The existing pre-start ERROR line carries the refusal, once per start attempt.
