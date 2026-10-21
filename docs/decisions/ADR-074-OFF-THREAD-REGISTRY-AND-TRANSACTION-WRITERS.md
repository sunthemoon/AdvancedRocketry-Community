# ADR-074 - Off-thread checked writers for the satellite registry and the rocket transaction journal

```yaml
status: PROPOSED
date: 2026-10-21
owner: sunthemoon
deciders: []
target_version: v1.8.0
slices: [W129a, W129b, W129c]
related: [ADR-010, ADR-038, ADR-049, ADR-050, ADR-051, ADR-065]
supersedes: none
```

This ADR is the follow-up that ADR-050 section 2 requires before an off-thread registry writer. The reasoning, the
checked facts at `75ffa1fc` and the test plan are in
[W129-DESIGN](../work/v1.8.0-root-queue/records/W129-DESIGN.md). This text is proposed for review and owner
acceptance. It approves no Gate and accepts no risk.

## Context

[W128](../work/v1.8.0-root-queue/records/W128.md) measured both writers on the development host at their bounds, and
the CPU time was close to the wall time:
- the satellite registry at a 16.7 MB root: checkpoint P95 448 ms and P99 893 ms; coalesced flush 455 and 495 ms;
  autosave 461 and 509 ms;
- the rocket transaction journal: 26 ms (P95) with one record, but about 4.1 s with 256 records at its 64 MiB bound.

docs/17 allows 50 ms at P95 and 100 ms at P99. Under ADR-050 section 2's cost gate, and Root decisions R49 (Q6) and
R51, W27d2 cannot start, and D0 cannot request a 4-second synchronous checkpoint. The same costs exist today in
ordinary autosave at those sizes.

Both writers encode, gzip, force, decode again and compare on the server thread
(`SatelliteRegistryWriter.write`; `RocketTransactionSavedData.saveChecked`). Both roots hold only immutable records.
The transfer journal already writes off-thread with an immutable capture, which W11B2 measured at P99 7.3 ms on the
server thread against 515 ms for in-place encoding.

## Decision

1. **Scope.** `SATELLITE_MISSIONS` and `ROCKET_TRANSACTIONS` each get one checked writer with its own daemon IO
   thread, owned by that root for one server lifetime. No saved field, schema, epoch literal, file name, bound,
   budget or timeout changes. The transfer journal, escrow ledger and receipt writers are unchanged.

2. **The server thread captures and publishes only.**
   - A capture is immutable:
     - for the registry: the mutation sequence s, the carried epoch w, the two clock values and five lists of record
       references (at most 20,480), or the preserved blocked root by reference;
     - for the journal: s, at most 256 entries, and the previous acknowledgement and outcome.
   - A capture builds no NBT.
   - Publication applies one completion per tick at `ServerTickEvent` START.
   - Neither step may cost more than docs/17's tick budgets at the bounds (item 13).

3. **The IO thread does the rest.** It encodes the capture byte-identically to today's encoder, checks the bound,
   computes the NC1 record and root digests (journal only; the registry keeps no digest), stages, forces, reads the
   staging file back, replaces atomically without fallback and forces the directory per policy. After a committer
   throw it reads back within the same attempt. The outcome rules are today's:
   - The registry gives COMMITTED, FAILED or UNKNOWN by `equals`. Its directory force runs only on a bay checkpoint
     request, and a failure there is visible but stays COMMITTED.
   - The journal gives COMMITTED, NOT_COMMITTED or UNKNOWN as `CheckedJournalCheckpoint.write` decides. Its directory
     force always runs, and a failure there gives UNKNOWN.
   - Any `Throwable` other than a `RuntimeException` gives UNKNOWN.
   - The IO thread touches no Level, server or live SavedData.

4. **The writer.**
   - At most one capture exists. A request while one is in flight sets a pending flag.
   - Every attempt captures fresh, and frozen bytes are never rewritten.
   - An IO start follows the previous one by at least 10 ticks, and a non-COMMITTED outcome by at least 200 ticks.
   - A completion that is not the active capture's is refused.
   - The first request binds the target file, and a different target is refused.
   - `close` drains and joins. `abort` joins without publishing. Requests after either are refused.

5. **Registry epochs.** E is the durable epoch, h is W119's reserved epoch, and a capture is in flight until its
   completion is published.
   - A capture carries w = max(E, h) + 1 if a state change is not covered by a published COMMITTED capture.
     Otherwise it carries E.
   - Every epoch stamp uses the **snapshot epoch**: w while a capture carrying w > E is in flight, and E otherwise.
     The stamps are mission `start_epoch`, `ack_epoch`, and ground-survey start and claim epochs. Every durability
     test uses E.
   - Publication of COMMITTED sets E and h to at least w and records the acknowledgement (session, s, w, captured
     identities). Dirty clears only if nothing mutated since the capture. `flushPending` and the changed flag clear
     only if no state change happened since the capture.
   - FAILED keeps E, the acknowledgement and the flags. It reserves w (h at least w) only if the registry mutated
     during that flight.
   - UNKNOWN clears the acknowledgement and reserves w.
   - Nothing else advances E. No delayed readback exists. No mutation is refused while an outcome is FAILED or
     UNKNOWN.
   - When nothing mutates during a flight, these rules give exactly W119's results.

6. **Registry proof and dependent effects.** W119's proof rule is unchanged: a watch at sequence m is proved by a
   published COMMITTED acknowledgement with s >= m and an equal captured identity. A removal token (session, m) is
   covered by such an acknowledgement with s >= m. `checkpoint` becomes `requestCheckpoint` and keeps its fast path.
   ADR-050's barrier flushes become **acknowledged effects**: the change is made in RAM, a write is requested at
   once, and the dependent effect in another store runs only after an acknowledgement covers the change.
   - A terminal launch extracts the package, and a decommission blanks the chip, when covered, and only if the slot
     still holds the same item. Until then the terminal answers REGISTRY_PENDING.
   - Data start, claim and cancel show their result when covered.
   - Discovery writes `CelestialSavedData` only after the paid receipt is covered. Until then it answers
     PENDING_DISCOVERY and retries through the existing queue.
   - The REBIND_CONFLICT bind-back and the coalesced flush make plain requests.

7. **Terminal protection.** A server-lifetime table holds at most one pending action per terminal, keyed by Level and
   position, with at most 256 entries. A full table answers REGISTRY_BUSY before any mutation. While an entry exists:
   - the package and chip slots refuse insertion and extraction through the menu and the item handler, simulated and
     actual;
   - player breaking is refused, and the terminal is removed from explosion block lists;
   - raw replacement stays an operator residual.

   At most 32 entries complete per tick, for loaded terminals only, with no chunk loaded or ticketed. The table is
   discarded at `ServerStoppingEvent` and on the crash path. After a restart, ADR-049 section 6's replay applies.

8. **Transaction journal acknowledgement and B14.**
   - W123's session-local acknowledgement (sequence, root digest, record digests) and its sticky UNKNOWN are
     unchanged.
   - Publication sets dirty to `!acknowledgedCurrent()` against the current sequence.
   - A blocked or unreadable journal refuses the request on the owner thread and is never staged or overwritten.
   - B14 answers in this order: ASSEMBLY_JOURNAL_UNPROVED if not operational; ASSEMBLY_JOURNAL_PENDING if a RAM record
     matches; PROVED if `acknowledgedCurrent()`; the new **WRITE_PENDING** while a request is pending or in flight;
     ASSEMBLY_JOURNAL_UNPROVED after NOT_COMMITTED or UNKNOWN; WRITE_REQUIRED otherwise.
   - `checkpoint` becomes `requestCheckpoint`.

9. **`save(File)`.**
   - While the server runs (autosave, `/save-all`, `/save-all flush`), a dirty root makes a request and returns
     without waiting. `/save-all flush` therefore does not mean that these two files are current, as is already the
     case for the other three writers.
   - `save(CompoundTag)` stays encoding-only and never acknowledges.

10. **Stop, crash and kill.**
    - `ServerStoppingEvent` discards waiting effects. The writers stay open.
    - When `stopServer`'s `saveAllChunks` calls `save(File)` with the server not running, on the normal and the crash
      path, a dirty root requests a fresh capture and drains: at most three attempts, the first COMMITTED wins, and
      no gameplay effect runs. This persists the final RAM state, as today's synchronous writers do.
    - The Overworld's `LevelEvent.Unload` closes the writer while world storage is still owned. `ServerStoppedEvent`
      aborts as a backstop.
    - After a kill, the file is the last committed candidate or a later one whose outcome was unknown. Every effect
      that ran had a published acknowledgement (S2). Windows still has no directory force.

11. **Bay protocol.**
    - D0 requests the journal checkpoint on its first UNPROVED or WRITE_REQUIRED answer, waits on WRITE_PENDING with
      L held and no time limit, continues on PROVED, rejects every bay on UNPROVED, and requests again on
      WRITE_REQUIRED.
    - D3 requests the registry checkpoint with its watches and waits for the first published completion with s >= m.
      W27A's three-failure REGISTRY_UNPROVED rule and its 200-tick pacing are unchanged.
    - D1, D2, D4 to D7 and the restart rows are unchanged. A receipt's `registry_epoch` is the proving completion's
      carried epoch.

12. **Protections, bounds, logging and recovery** (AGENTS.md section 3.3). Neither writer refuses a save that is
    accepted today. The new protection is the terminal lock of item 7:
    - scope: one terminal;
    - duration: until the change is covered, or the session ends;
    - recovery: repair the storage, after which the next commit releases it; a restart releases it, and replay
      handles the item.

    Each root keeps its once-per-streak failure report: one ERROR at the first failed or unknown outcome and one INFO
    at the next commit. One ERROR is logged per unexpected writer `Throwable`, and one WARN when a stop drain gives
    up. There is no total cap. Every structure is bounded: one capture per root, a one-slot completion queue, and
    256 pending terminal actions. Root registers the new protection and updates R-039 and R-040 before the slices
    merge (design section 5.3).

13. **Measurement gate.** The first commit of each slice measures, under the heavy-JVM lock with Xmx4G on W128's
    fixtures:
    - the server-thread request and publication of every write entry at the bounds;
    - the server-thread tick percentiles and peak heap while the registry, transaction and transfer journals write
      together at their bounds.

    Each figure must be within docs/17's P95 and P99. A miss stops the slice until Root decides. For the heap, the
    named remedy is one shared IO permit for the large writers.

## Alternatives

### A. Keep synchronous writes and cache per-record encodings

Records are immutable, so their encodings could be memoized. However, the whole-tree write, gzip, staging decode and
`equals` stay on the server thread and still cost hundreds of milliseconds at the bound. This alternative was
rejected.

### B. Make only the coalesced flush, autosave and checkpoint asynchronous, and keep synchronous barriers

Launch and decommission at a full root would still cost about 0.4 s each. A barrier that arrives while an
asynchronous write is in flight would also have to join that write first. This alternative was rejected.

### C. Retry the exact candidate, as the receipt and ledger writers do

W119 forbids rewriting frozen bytes, and its carried epochs name exactly one file. Fresh captures keep that property
and need no digest for the registry. This alternative was rejected for both roots, for uniformity.

### D. Keep W119's literal "no mutation since capture" guard and stamp E

This would be correct, but under ordinary clock observations (every 20 ticks) and activity, E would rarely advance,
and every epoch consumer would stall. ADR-050 section 2 already requires the snapshot epoch. This alternative was
rejected.

### E. A per-rocket B14 proof

It would avoid waiting for whole-journal currency under constant journal churn. However, it changes W123's rule, and
the live journal is normally empty because assembly removes its record within the same call. This alternative is not
proposed.

### F. Persistent copy-on-write maps for an O(1) capture

This is not needed: the linear reference copy is estimated two orders of magnitude inside the budget. It stays the
remedy if the measurement in item 13 shows otherwise.

## Consequences

### Positive

- Autosave, the coalesced flush, barriers and the bay checkpoints cost only a capture and a publication on the server
  thread at every size. This removes W128's misses and the existing autosave spikes.
- W27d1 can resume after the journal writer, and W27d2 after the registry writer and the barrier conversion.
- W119's and W123's results are unchanged for every case that a synchronous writer can produce.

### Negative

- Terminal launch, decommission and data actions answer REGISTRY_PENDING for about one write. A launching or
  decommissioning terminal is locked until then, or until the session ends on a failing disk.
- Discovery, bay holds and epoch waits last about one write longer.
- `/save-all flush` no longer makes these two files current.
- Writes can overlap and raise transient heap; item 13 measures this.
- Two more IO threads exist, and the code has one more writer state machine to review.
- ADR-050 section 2's "barrier flushes behave as today", ADR-049 sections 6 and 7's synchronous barrier, and
  ADR-065's W27c3 "writes stay synchronous" are superseded in mechanism. Each gains a pointer note on acceptance.

## Validation

- [ ] B1 to B3 within docs/17 at the bounds (design section 6.1).
- [ ] The W119 and W123 test matrices pass with unchanged expected values; only synchronous calls become awaited.
- [ ] New unit tests for the writer core, epoch cases E to H, the stamp and check sites, WRITE_PENDING and the
      terminal protection.
- [ ] GameTests on the real server's file and lifecycle, with the unchanged log checker.
- [ ] Native crash cuts R1 to R7, T1 to T3 and discovery, with two restarts each, conserving packages and satellites
      and never letting a discovery get ahead of its paid receipt.
- [ ] A read-only persistence review of each slice before merge.

## Revisit when

- the reference node's measurement (docs/17 section 4) disagrees with the development host;
- B3 shows that concurrent writers exceed the heap or tick budget;
- a third root needs the same writer;
- Forge or Minecraft changes the stop sequence that item 10 relies on.

## Acceptance

Pending. Root arranges an independent read-only review of this ADR and its design note. Acceptance is the owner's.
