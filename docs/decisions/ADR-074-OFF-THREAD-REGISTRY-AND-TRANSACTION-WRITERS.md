# ADR-074 - Off-thread checked writers for the satellite registry and the rocket transaction journal

```yaml
status: ACCEPTED
revision: 2
date: 2026-10-22
owner: sunthemoon
deciders: [sunthemoon]
target_version: v1.8.0
slices: [W129a, W129b, W129c]
related: [ADR-010, ADR-038, ADR-049, ADR-050, ADR-051, ADR-065]
supersedes: in mechanism only, ADR-049 sections 6 and 7, ADR-050 section 2 and its W119 note, ADR-065's W27c3 note
```

This ADR is the follow-up that ADR-050 section 2 requires before an off-thread registry writer. The reasoning, the
checked facts at `75ffa1fc` and the test plan are in
[W129-DESIGN](../work/v1.8.0-root-queue/records/W129-DESIGN.md). This text is proposed for review and owner
acceptance. It approves no Gate and accepts no risk.

Revision 2 follows Root decision R53 in [TASK-W129R](../work/v1.8.0-root-queue/TASK-W129R-OFF-THREAD-WRITERS-REVISION-2.md).
It addresses W129-R1 to R6 and adopts the review's Q1 to Q8 recommendations. The source review and finding-by-finding
disposition are in [W129, Revision 2](../work/v1.8.0-root-queue/records/W129.md#revision-2). No implementation is changed
by this ADR revision; fresh review and acceptance are still required.

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
(`SatelliteRegistryWriter.write`; `RocketTransactionSavedData.saveChecked`). Their record graphs support reference
capture only after the nested-value audit and `TerminalLocation` constructor hardening in item 2.
The transfer journal already writes off-thread with an immutable capture, which W11B2 measured at P99 7.3 ms on the
server thread against 515 ms for in-place encoding.

## Decision

1. **Scope.** `SATELLITE_MISSIONS` and `ROCKET_TRANSACTIONS` each get one checked writer with its own daemon IO
   thread, owned by that root for one server lifetime. No saved field, schema, epoch literal, file name, bound,
   budget or timeout changes. Existing writers otherwise stay unchanged; item 13 may require including the transfer
   writer in a measured shared IO permit before merging the new overlap.

2. **The server thread captures and publishes only.**
   - A capture is immutable:
     - for the registry: the mutation sequence s, the carried epoch w, the two clock values and five lists of record
       references (at most 20,480), or the preserved blocked root by reference;
     - for the journal: s, at most 256 entries, and the previous acknowledgement and outcome.
   - A capture builds no NBT.
   - Copy live-map values, not views; leave sorting/identity-map construction on IO, not in existing sorted getters.
   - Audit nested mutable types as in design section 2.1. W129b freezes `MissionPayload.TerminalLocation.pos` at
     construction using `immutable()` and tests a supplied `BlockPos.MutableBlockPos` mutated after construction
     and capture. Lists/maps must be immutable down to their elements; rocket payload tags keep their existing
     defensive copies. A blocked registry's private preserved tag may be shared read-only, never exposed or mutated.
   - Publication applies one completion per tick at `ServerTickEvent` START.
   - Neither step may cost more than docs/17's tick budgets at the bounds (item 13).

   **Implementation note (2026-10-30, W210).** "The server thread captures and publishes only" describes the
   checked file writer's capture and completion publication, not every transaction-journal operation.
   "A capture builds no NBT" remains true: capture still copies immutable entry references and does not capture
   the RAM-only size table. W210's journal-owned admission table holds an exact size for each successfully
   measured immutable resident and is used only for the identical entry object. Load measures each decoded
   resident, successful RAM writes refresh its row, removal drops it, and failed decode clears the table.
   On the owner thread, a first write or a different snapshot object encodes and measures one full entry;
   replacement with an already measured identical snapshot object encodes and measures metadata only, reusing
   that snapshot field's exact contribution. Admission still fully encodes candidates; an unmeasured resident
   uses the original encoding/check fallback. These synchronous costs are outside capture/publication and are
   reported, including the small-root increase, in [W210](../work/v1.8.0-root-queue/records/W210.md).
   The table retains no candidate or NBT and is never a disk acknowledgement. This note changes no accepted
   decision, schema, bound, budget, timeout, refusal or recovery policy, and approves no risk or Gate.

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

   **Implementation note (2026-10-30, W210).** "The IO thread does the rest" refers to the checked file write
   from the immutable capture: encoding, bound and digest checks, staging, force, readback, replacement and
   outcome classification remain on IO as specified above. It does not move all journal admission, RAM-write
   or load encoding to IO. The writer neither reads nor trusts W210's owner-thread size table and still
   independently encodes and checks its captured entries. Publication and acknowledgement remain unchanged;
   cached sizes cannot make failed or UNKNOWN writes acknowledged. "A capture builds no NBT" remains true.
   This is an implementation clarification, not a change to the accepted decision or a performance/availability
   guarantee; R-040 and the remaining interruption, traffic, expiry and storage-fault evidence stay open for
   owner disposition. No risk or Gate is approved.

4. **The writer.**
   - At most one capture exists. A request while one is in flight sets a pending flag.
   - Every attempt captures fresh, and frozen bytes are never rewritten.
   - Every running-server start path respects 10 ticks since the last start and 200 ticks after a non-COMMITTED
     outcome. Requests coalesce without resetting backoff. Shutdown uses the separate monotonic drain clock.
   - A completion that is not the active capture's is refused.
   - The first request binds the target file, and a different target is refused.
   - `close` uses item 10's shared shutdown state/budget and joins. `abort` joins without publishing and reports
     unresolved work. Requests after either are refused. Neither operation releases world ownership while IO can
     still replace files; interrupt/cancel/timeout does not prove termination.

5. **Registry epochs.** E is the durable epoch, h is W119's reserved epoch, and a capture is in flight until its
   completion is published. MAX means `Long.MAX_VALUE`.
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
   - Nothing else advances E. No delayed readback exists. FAILED or UNKNOWN alone does not refuse mutations.
     Finite capacity still applies: before admitting a stamped mutation, let t be the stamp it would receive and
     r = max(E, h, active w if present), including an unpublished completion. Require a representable r + 1 > t
     before changing RAM or consuming resources. State changes requiring a new epoch also need capacity. Return
     explicit capacity refusal, never wraparound, an unchecked exception or an admitted permanently pending job.
     At E = h = MAX - 1 an idle stamp MAX - 1 can be covered by MAX; a mutation during capture MAX would stamp MAX
     and is refused. At E or h = MAX no changed successor is representable. UNKNOWN reserving MAX after earlier
     admission produces an explicit exhaustion diagnostic, retained dirty/unproved data and no retry spin/reuse.
     Recovery requires a separately reviewed migration/operator decision, not silently resetting epochs. Clock-only
     unchanged writes may retain E. Design section 4.3 specifies MAX - 1/MAX fixtures.
   - When nothing mutates during a flight, these rules give exactly W119's results.

   **Explicit reviewed semantic transition (W129-R3):** `SatelliteRegistryProofTest.java:120-137`,
   `mutationInsideCommitCannotAdvanceEpochOrClearDirtyOrPending`, changes intentionally. Pause IO after capture w,
   move its later launch to the owner thread, then release/publish IO. Assert E becomes w, the later stamp equals w,
   the later mutation is absent from that file and unproved, dirty/flush-pending remain, and the next fresh capture
   includes it and advances to w + 1. The old expectation that E stays unchanged at the first completion is replaced,
   not silently weakened; retain all conservation/proof assertions. All no-mutation matrices remain unchanged.

6. **Registry proof and dependent effects.** W119's proof rule is unchanged: a watch at sequence m is proved by a
   published COMMITTED acknowledgement with s >= m and an equal captured identity. A removal token (session, m) is
   covered by such an acknowledgement with s >= m. `checkpoint` becomes `requestCheckpoint` and keeps its fast path.
   ADR-050's barrier flushes become **acknowledged effects**: the change is made in RAM, a write is requested at
   once, and the dependent effect in another store runs only after an acknowledgement covers the change.
   - A terminal launch extracts the package, and a decommission blanks the chip, when covered, and only if the slot
     still holds the same item. Until then the terminal answers REGISTRY_PENDING.
   - Data start, claim and cancel retain the original token and RAM result separately and show that result when
     covered. Polling must not replace the token with a moving current sequence. Conflicting actions are refused
     before mutation, including actions from another terminal for the pending satellite/package identity.
   - Discovery writes `CelestialSavedData` only after the paid receipt is covered. Until then it answers
     PENDING_DISCOVERY and retries through the existing queue.
   - The REBIND_CONFLICT bind-back and the coalesced flush make plain requests.
   - Append REGISTRY_PENDING/REGISTRY_BUSY without shifting existing ordinals; provide EN/zh text, non-error pending
     display and bounded unknown-code handling. Show WRITE_PENDING wherever the bay exposes that state.
   - Adapt release hooks including `ResearchReleaseCommands.java:58-78` to awaited outcomes and epoch/FE checks.
     Its failure injection uses a same-target policy seam, never a fake target that binds the live writer.
     GameTest/release-only waits remain unavailable to normal running servers.

7. **Terminal protection.** A server-lifetime table holds at most one pending action per terminal, keyed by Level and
   position, with at most 256 entries, including data-action tokens. A full table answers REGISTRY_BUSY before any
   mutation. While a launch/decommission entry exists:
   - the package and chip slots refuse insertion and extraction through the menu and the item handler, simulated and
     actual;
   - player breaking is refused, and the terminal is removed from explosion block lists;
   - raw replacement stays an operator residual.

   At most 32 entries complete per tick, for loaded terminals only, with no chunk loaded or ticketed. Keep entries
   through FAILED/UNKNOWN and covered-but-unloaded states. Coverage alone cannot release the lock: the matching
   inventory effect must complete first. Mismatch, retry and second-terminal paths cannot cancel a lock and release
   a usable registered package; retain a diagnostic hold until a tested reconciliation establishes conservation.
   The table is discarded without effects on both shutdown paths (item 10). ADR-049 section 6's replay applies
   after restart, including a normal stop that saved the registration without consuming the package in its chunk.

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
   - While `!server.isStopped() && server.isRunning()` (autosave, `/save-all`, `/save-all flush`), a dirty root makes
     a request and returns without waiting. `/save-all flush` therefore does not mean that these two files are
     current, as is already the case for the other three writers.
   - W129c's operator documentation must distinguish individual-file integrity from multi-file backup consistency:
     a live copier can read an old registry and a later dependent chunk even with atomic replacement. Recommend a
     gracefully stopped, successfully drained whole-world backup, or a separately specified consistent snapshot
     protocol. An incomplete drain is not a backup guarantee; no synchronous command drain is added here.
   - `save(CompoundTag)` stays encoding-only and never acknowledges.

10. **Stop, crash and kill.**
    - Shutdown is `server.isStopped() || !server.isRunning()`. `ServerStoppingEvent` latches effect suppression on
      the normal path; every effect/save/publication entry also checks the predicate and latches it idempotently,
      covering tick exceptions without that event. Discard waiting effects without executing them, refuse gameplay
      requests and keep persistence/logout mutations possible. No gameplay effect runs in a drain or cleanup.
    - Set a per-root final target F to the mutation sequence after logout mutations, at shutdown `save(File)`.
      Success requires a published COMMITTED capture with s >= F, no uncovered current mutations, no unserviced
      requests and no active write. An older COMMITTED result retains dirty/pending successor work. Include clock
      mutations; if a persistence hook changes RAM later, raise F and continue under the same budget. A clean,
      already-current acknowledgement needs no redundant write. Blocked-root persistence coverage never creates
      gameplay proof, and unreadable journals still refuse IO.
    - One per-root shutdown budget permits three total attempts across `save`, `drain` and `close`. An active or
      completed-but-unpublished attempt at shutdown entry consumes one; each fresh attempt consumes another,
      regardless of outcome. Do not start new attempts during logout before F is set. Neither another call nor a
      newer target resets the budget. Exhaustion, capacity refusal or an exception reports incomplete persistence
      once with target F, last committed sequence, attempts and reason; uncovered data stays dirty/unproved.
    - Overworld `LevelEvent.Unload` rechecks/establishes F if `save` was skipped, resumes that same drain and joins
      before storage release. Exceptional cleanup must likewise join before ownership can be released;
      `ServerStoppedEvent` is only an idempotent abort backstop, not sufficient lifecycle ordering by itself.
      An interrupted join waits for termination and preserves interruption afterward. No timeout may return while
      the IO thread can replace files, even after an exhausted/aborted drain.
    - Shutdown duration is unmeasured. Three attempts bound count, not time in force/readback/join or backoff.
      Stuck IO can hold world ownership indefinitely; the operator must end the process before reopening/copying
      the world, then use restart recovery or a known-good backup, without a final-RAM-persistence guarantee.
    - `ServerWatchdog` tests `isRunning()`. A tick exception may leave running=true/stopped=true, so the watchdog
      can interrupt teardown. Only committed-or-unknown whole-file S2 recovery and acknowledged-effect ordering
      survive that interruption; F is not guaranteed saved. Do not alter the watchdog timeout to mask it.
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

12. **Protections, bounds, logging and recovery** (AGENTS.md section 3.3). Existing blocked/unreadable-file rules
    remain. New protections include item 5's explicit capacity refusal, item 7's terminal lock and item 10's
    final-target shutdown accounting. For the terminal lock:
    - scope: one terminal;
    - duration: until a covering acknowledgement and the loaded-terminal effect/reconciliation complete, or the
      session ends; a covered unloaded terminal remains held;
    - recovery: repair storage and complete the matching effect; a restart drops RAM waits and replay handles items.
      Capacity exhaustion affects one registry's new epoch-dependent mutations, can survive restart and needs the
      separately reviewed recovery of item 5. Neither protection silently drops player data.

    Keep the registry's once-per-streak report; add this policy explicitly for the transaction writer (W123/R-040
    does not already have an ordinary write-failure loop): one ERROR at the first failed/unknown outcome and one
    INFO at the next commit. Log one ERROR per unexpected writer `Throwable`, one capacity diagnostic per exhaustion
    streak, and one WARN per root/shutdown for incomplete drain, with item 10's fields. There is no total cap.
    Every structure is bounded: one capture per root, a one-slot completion queue, and
    256 pending terminal actions. Root registers the new protection and updates R-039 and R-040 before the slices
    merge (design section 5.3), including watchdog interruption, unbounded join, pending/unloaded terminals,
    ordinary-shutdown replay and backup limitations. Root also adds the supersession pointers; W129R changes none
    of those central records.

13. **Measurement gate.** Measure in the implementation's early commits, under the heavy-JVM lock with Xmx4G:
    - B2 and **B3a before W129a merges**: the journal's request/publication at its bounds and its new overlap with
      the transfer writer and existing writer traffic, with all resident roots present. W27d1 cannot resume before
      this gate and the persistence review.
    - B1 and full **B3 before either W129b or W129c merges**: all three large writers at their bounds together.
      B1 includes maximum-reference-count and maximum `epochAdvanced` publication-work compositions, not only
      W128's byte-bound fixture. B3a cannot substitute for B3.
    - Both overlap runs use at least 50 rounds, successful writes and failure/readback paths. Account for resident
      and pinned graphs, candidate/decoded trees, compressed arrays and buffer growth/copies, allocation and GC
      headroom; serialized NBT bytes are not a heap bound. Report peak heap/RSS and writer CPU/latency.
    - Use real ticks or a monotonic 50 ms paced surrogate recording intended deadlines, actual start/completion,
      lateness, missed/skipped deadlines, and GC pause count/maximum/cumulative stalls. No tight-loop sampling or
      empty catch-up samples that dilute pauses. Report work and lateness percentiles with the workload/JVM/hardware.

    docs/17's P95/P99, heap/RSS and no-OOM/watchdog budgets stay unchanged. A missing measurement or miss stops the
    slice before merge until Root chooses a remedy and a rerun meets the budgets. If needed, use a server-lifetime
    fair IO-side permit for large writes, including transfer in its scope; never block the owner thread acquiring
    it. The permit is a conditional remedy, not an assumed requirement or a performance waiver. Ground preflight
    remains a separate measured leaf: START, account creation and restored-over-budget fallback (design section 2.4).

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

Not selected before measurement. The linear reference copy is plausibly cheap but unmeasured; persistent maps stay
a remedy if item 13 shows capture itself misses the budget.

## Consequences

### Positive

- Autosave, the coalesced flush, barriers and the bay checkpoints cost only a capture and a publication on the server
  thread at every size. This is intended to remove W128's encoding misses; item 13 must measure capture, publication
  and overlap before any claim that the tick/heap budgets are met.
- W27d1 can resume after the journal writer, and W27d2 after the registry writer and the barrier conversion.
- W119's no-mutation matrices and W123's acknowledgement results stay unchanged; item 5 explicitly migrates the
  defensive mutation-inside-committer test rather than claiming all synchronously injectable cases are identical.

### Negative

- Terminal launch, decommission and data actions answer REGISTRY_PENDING for about one write. A launching or
  decommissioning terminal is locked until then, or until the session ends on a failing disk.
- Discovery, bay holds and epoch waits last about one write longer.
- `/save-all flush` no longer makes these two files current.
- Writes can overlap and raise transient heap; item 13 measures each newly enabled overlap before merge.
- A graceful stop can require item replay. Watchdog interruption or exhausted/stuck IO can prevent final-target
  persistence; joining may retain world ownership indefinitely. Epoch exhaustion has no automatic reset remedy.
- Two more IO threads exist, and the code has one more writer state machine to review.
- ADR-050 section 2's "barrier flushes behave as today", ADR-049 sections 6 and 7's synchronous barrier, and
  ADR-065's W27c3 "writes stay synchronous" are superseded in mechanism. Each gains a pointer note on acceptance.

## Validation

- [ ] B2/B3a before W129a merge; B1/full B3 before W129b/c merge, within docs/17 (design section 6.1).
- [ ] W119 no-mutation and W123 matrices unchanged; item 5's named proof-test transition preserves its safety checks.
- [ ] Writer core/final-target tests include an older success, clock/logout mutations, failed/UNKNOWN attempts, one
      shared retry budget across save/close, exhaustion, effect suppression and join-before-ownership-release.
- [ ] Epoch cases E to H, MAX - 1/MAX admission/exhaustion fixtures, stamp/check sites, nested mutable-position/tag/
      collection tests, WRITE_PENDING, stable action tokens, terminal conservation and held-ticket churn tests.
- [ ] GameTests on the real server's file and lifecycle, with the unchanged log checker.
- [ ] Native crash cuts R1 to R7, T1 to T3 and discovery, with two restarts each, conserving packages and satellites
      and never letting a discovery get ahead of its paid receipt.
- [ ] R7 throws a tick exception with running=true/stopped=true; distinguish successful final-target drain from
      watchdog-interrupted teardown and verify only the latter's S2 recovery guarantee. R6 checks normal-stop replay.
- [ ] Appended-code compatibility/unknown-code bounds, same-target research hook checks and operator backup guidance.
- [ ] A read-only persistence review of each slice before merge.

## Revisit when

- the reference node's measurement (docs/17 section 4) disagrees with the development host;
- B3 shows that concurrent writers exceed the heap or tick budget;
- a third root needs the same writer;
- Forge or Minecraft changes the stop sequence that item 10 relies on.

## Implementation notes

- **Slice 1 (W129a, the transaction journal writer):** merged with W129aM at `bb0eda86f` on 2026-10-27, after B2,
  B3a and a fresh read-only review. The risk text is R-040.
- **Slices 2 and 3 (W129b and W129c):** merged with W129bc at `a03fdd048` on 2026-10-27.
  - These slices are the satellite registry writer and the barrier callers.
  - B1 and the full B3 met docs/17 in both modes. The fair IO-side permit therefore stays off and exists only in
    test sources.
  - The first review (BLOCK) found W129bc-R1 and R2. A continuation fixed both, and W129aM-R1 as well.
  - A fresh re-review (APPROVE-WITH-CHANGES, SHA-256 `4d271edf…9f70`) found one Low issue, W129bc-R3. While the
    256-entry terminal table is full, resource start, claim, cancel and unlink are refused with REGISTRY_BUSY before
    any change, although they add no entry. Root decision R125 keeps this as a deliberate conservative choice.
  - The risk text is R-041, with the R-039 and R-040 updates. Item 9's operator backup guidance is in
    `docs/ENDGAME-OPERATOR-GUIDE.md`.
  - The supersession pointers in ADR-049, ADR-050 and ADR-065 take effect with this merge.

These notes approve no Gate.

## Acceptance

Accepted by the owner (sunthemoon) in the Claude Code Root session on 2026-10-26, answering Root's multiple-choice question with the option "Accept (Recommended)". The question was: "ADR-074 (move the
save writes for the satellite registry and the rocket transaction journal off the server thread): accept it under
the W129R review's four conditions?" The acceptance covers revision 2 under the four conditions of the W129R review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W129R-review/REVIEW.md`, SHA-256 `3d1a7124…0dc3fa`, APPROVE):

1. This record and the supersession pointers below.
2. Root registers the new protections and updates R-039 and R-040 before the affected merges. The Q6
   ground-preflight leaf is queued separately.
3. B2 and B3a before W129a (both passed under Root decisions R60 and R63, with a re-review APPROVE), and B1 and B3
   before W129b and W129c, under unchanged budgets. W129b's temporary owner-thread await never reaches production
   without W129c.
4. A read-only implementation review of each slice, with real lifecycle and crash tests.

Superseded in mechanism, each with a pointer note: ADR-049 sections 6 and 7 (the synchronous launch and
decommission barrier), ADR-050 section 2 ("barrier flushes behave as today") and its W119 implementation note (the
synchronous epoch mechanism; W119's proof rule and its results stay), and ADR-065's W27c3 note ("writes stay
synchronous"). Each takes effect when the slice that changes it merges. This acceptance approves no Gate and no
release.
