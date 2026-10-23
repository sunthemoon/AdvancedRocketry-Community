# W149 design: ordinary captures and a protected entity-slot proof

Date: 2026-10-23. Author: Codex implementer, assigned by Root (Claude).
Status: proposal for independent review, not an accepted ADR or an implemented fix.
Source inspected: `2645891f609ff608a17da842c63b373ff24a5d1e`.
Worktree: `D:/GitHub/arce-v180-w149`; branch: `codex/v1.8.0-w149`.
Task: [W149](../TASK-W149-W98-R1-DESIGN.md). This note is the task record and its
only repository write scope. External verification belongs under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W149/`.

## 1. Recommendation and exact promise

Give each admitted non-W98 entity capture a **generation-scoped scheduling pin**.
While that generation is still current and its capture/proof is retained, defer
W98's `beginOrdinary` on that entity slot **before** it reserves capture budget,
calls `watch`, serializes, or submits a store. Keep the pin through the proof's
last consumer, including a queued or active checked ledger write, not merely
through the barrier callback. Ordinary requests on other slots continue normally.

The proposed bound is **zero W98-caused invalidations, hence one admitted proof
attempt**, for a stable governed operation with completing storage and downstream
write service. The number/duration of competing W98 requests does not enter this
bound. Section 4 defines the assumptions and the distinction between a barrier
proof and an acknowledged operation. This is not a promise of a fixed number of
wall-clock seconds, unconditional admission, or progress under arbitrary vanilla
saves, other governed captures, changing authority, or failed storage.

The cost is explicit: an unrelated ordinary rocket in the same entity chunk can
wait as long as the protected proof's downstream consumer. Existing consumers can
wait indefinitely. This proposal does **not** rename that interval a short lock
or invent a timeout. Root must decide on that availability tradeoff and record
its scope before implementation/integration; see section 6. If bounded waiting
for both classes is required, this minimal design is insufficient. An additional
fair writer/capture scheduling design would be needed, not a longer proof lease.

## 2. Today's code and evidence

### 2.1 Terminology and source map

Here, **ordinary capture** means W98's retirement `beginOrdinary`, including its
later `BEFORE_FILL` consumer. It does not mean every monitor event labelled
`ORDINARY_STORE`, nor every escrow operation called "ordinary reconciliation".
Fuel/cargo C4 uses the non-W98 `beginJoint` path and is a protected consumer in this
proposal. The physical slot is `(Level resource key, entities=true, ChunkPos)`;
two different rockets, Y coordinates, or endpoint identities in that column
still share it. Terrain and entity slots with the same X/Z remain different.

All line ranges below refer to the source commit above, not historical W98 lines.
`E` below denotes the linked resource-escrow package; `R` denotes rocket/server.

| Reference | Code and relevant lines |
|---|---|
| S1 | [EscrowChunkStore](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowChunkStore.java), 44-54, 79-86, 101-115, 135-144: both paths call `watch` before serialization; full entity-slot capture and validation. |
| S2 | [EscrowSaveMonitor](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSaveMonitor.java), 28-58: 128-slot map, reference count, capture and observed-save generation changes. |
| S3 | [EscrowEntityStore](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowEntityStore.java), 30-50: own store through the original worker; ordinary/decorated stores and nested stores remain observed. |
| S4 | [EscrowBarrier](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowBarrier.java), 19-28, 69-119, 127-145, 159-247, 250-309, 329-336: joint proof release, force/readback, final generation check, retry and shutdown. |
| S5 | [EscrowBarrierBudget](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowBarrierBudget.java), 8-50: shared quotas and ordinary sub-quota. |
| S6 | [RocketOrdinaryRetirement](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketOrdinaryRetirement.java), 40-51, 194-240, 285-392, 402-495: census, attempt pacing, retained T, release and retry. |
| S7 | [RocketRetirementWorld](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketRetirementWorld.java), `captureAvailable`, `capture`, `keepLoaded`; [RocketTransferService](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferService.java), 307-309: the hint currently ignores the slot, and ordinary retirement ticks after governed sources/cleanup. |
| S8 | [EscrowFlightCheckpoint](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowFlightCheckpoint.java), 151-199; [EscrowSuccessorRetry](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSuccessorRetry.java), `failed`: F1 proof, retained writer guard, discard and caller retry. |
| S9 | [EscrowReconciliationDriver](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowReconciliationDriver.java), 164-243; [EscrowReconciliationCheckpoint](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowReconciliationCheckpoint.java), `accept`, `discardInvalid`, `proved`, `close`: C4-C8 proof ownership. |
| S10 | [EscrowReconciliationScheduler](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowReconciliationScheduler.java), `tick`, `completed`; [EscrowReason](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowReason.java), 19-33: eight visits/tick, failure history and STALLED retry. |
| S11 | [EscrowCoordinator](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCoordinator.java), 494-518; [EscrowFlightWrites](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowFlightWrites.java), 25-100: W145 admission, capture prerequisites and pre-replacement guards. |
| S12 | [RocketTransactionSavedData](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/persistence/RocketTransactionSavedData.java), 79-156: W123's current acknowledgement, absence query and sticky transaction UNKNOWN. |

Read alongside [W98 and W98F](W98.md), [W145](W145.md), [W147's R86 attribution](W147.md),
[W123](W123.md), Root decision R35 in [QUEUE](../QUEUE.md), residual (b) of R-035 in
the [risk register](../../../11-RISK-REGISTER.md), and the W98/W123 implementation
notes in [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md).
The independently read W98 review is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/REVIEW.md`, SHA-256
`493187a77ccc393c585203f7af42176f4ac14716ff393cec137acae7e086042b`.

### 2.2 Supersession, rate and consequence

1. Governed capture G calls `watch(slot)`, retaining counter object C and value g.
   It submits the whole slot's own store, awaits that future and the same worker's
   force, then reads the Anvil record off-thread (S1-S4).
2. On a later tick ordinary capture O calls the same `watch(slot)`. C becomes
   g+1 **before** O serializes or submits its store. Even if O subsequently fails
   validation, G is already invalid. Different rocket IDs or unchanged G payload
   do not prevent this; the monitor does not compare entity projections.
3. That later-tick O is detected by the reader-generation check, owner completion
   check, or a retained `Proof.valid()` guard, depending on timing. The pre-store
   `unchanged` check separately catches invalidation during synchronous capture;
   it is not where the later-tick O is detected. After a successful callback, O
   can still invalidate G while it waits for a ledger write. Releasing O's watch
   does not restore g or resurrect G.
4. A shared force or identical readback cannot repair that generation mismatch.
   In a mixed batch W116 isolates ordinary-only store/force failures, but a force
   failure on the worker also used by G still affects G. That is separate from R1.

The actual bounds are not "one ordinary capture per rocket per tick forever":

| Boundary | Present behavior |
|---|---|
| Server/runtime tick | At most one admitted capture across both kinds and all Levels, from `lastCapture`. Refused attempts do not capture. |
| Level batch | A quota window of 20 ticks allows at most 16 entity and 16 terrain captures, with at most four ordinary entity captures inside those totals. Force dispatch is also paced in 20-tick batches; quota and force-window start times need not coincide. |
| In flight | 32 pending captures, eight of them ordinary, 16 MiB shared captured bytes; 64 Level-budget entries and 128 monitored slots. Returned retained proofs can outlive the pending-capture budget charge. |
| One logical rocket | One pending retirement entry, one capture per distinct census slot per attempt; at most one capture in any real tick under the shared budget. Source-return census is normally at most two origin columns; I3 may add actual discarded slots. The explicit census ceiling is `1 + 2 * MAX_TRANSFER_ENTITY_MATCHES = 129`, not permission to bypass the monitor or deadline limits. |
| Retirement attempts | 64 pending returns; the start guard counts PROVING entries and reservation holders against four attempts for new unreserved starts. An attempt checks `now - started > 100` to expire; later-slot budget waits stay inside that existing deadline. |
| Retry | I1-I3 supersession/BUSY retries no earlier than the next tick; IO/force/veto/bounds/deadline retries after 200 ticks, crew/unloaded/save-off polls after 20. All release proofs and the reservation, except the existing same-tick fail-back reservation deferral. No lifetime attempt limit. |
| Later ordinary consumer | [RocketFillRetirementPacing](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketFillRetirementPacing.java) retains BEFORE_FILL's 200-tick per-rocket demand pacing and three ticket steps. It must not inherit I1-I3's faster retries. |

F1 does **not** autonomously retry the same launch until success. On proof/write
failure it releases/discards the pending launch and records 100/400/1,200-tick
successor backoff; a later caller can try again (S8). In native fuel/cargo
reconciliation, `SUPERSEDING_SAVE` is a retryable proof failure: after failures
one/two it backs off 100/400 ticks; the third requests persisted STALLED and
retries every 1,200 ticks. STALLED is not terminal. Success requires the C7
acknowledgement, not just C6 readback, before the scheduler clears the failure
history (S9-S10). These are different consumers, not one universal retry machine.

**Starvation is possible, inferred from those transitions.** For each G capture
at tick t, admit O on the same slot at t+1 before G's force/readback/consumption.
Repeat on each retry. One O per G attempt is enough, well below four ordinary
captures per Level batch, including the later 1,200-tick retries. Thus the quota
bounds work, not the number of lost proofs. A single successful, otherwise quiet
ordinary retirement ends after its census; an infinite counterexample needs a
continuing supply of ordinary returns/demands or repeatedly invalidated attempts.
It does not follow that every ordinary return or every finite workload starves G.

[RocketRetirementCoverageGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketRetirementCoverageGameTests.java),
87-94 and 235-329, already observes the shared-slot outcome; its shared-slot case
does not assert governed completion. W116F additionally proves the pair shares a
force batch. W147 separately observed finite-load stalls and eventual recovery;
its traced causes include other cargo captures/publications and `saveEverything`,
not evidence that W98 retirement caused those specific failures. In particular,
the diagnostic name `ORDINARY_STORE` alone cannot attribute W98-R1.

## 3. Proposed mechanism

### 3.1 Admission and lifetime

- Store the pin in the existing monitor's per-slot state, with the exact Watch
  identity and generation. No second global slot map, background waiter, saved
  field, chunk ticket or payload copy. Only entity captures through `begin` or
  `beginJoint` acquire it; terrain is unaffected.
- On the owner thread, `begin` first reserves existing budget, then atomically
  acquires a watched generation and its pin before serialization. It still
  increments the generation, validates the whole root, and submits the same own
  store. This protects the serialization-to-callback interval too.
- `beginOrdinary` checks the precise slot before reserving budget and again as
  part of acquiring its ordinary watch. A protected slot returns **not admitted**,
  not a proof failure or unsupported-storage result. It creates no future, emits
  no capture checkpoint and changes neither generation nor counters. No exception
  is used as normal scheduling control.
- Make `RocketRetirementWorld.captureAvailable` slot-aware, but keep the actual
  begin check authoritative. Before the first capture, use existing BUSY behavior
  so no new journal reservation waits on this pin. If an attempt already has
  other census proofs, keep its existing later-slot wait and 100-tick deadline;
  never remove T because a capture was deferred.
- Transfer the pin with the capture to `Proof`; release it through the same
  idempotent lifetime as `releaseWatch`. `splitProof` retains the original until
  the last joint child releases. Budget release on callback is **not** pin release.
  Failure before store, failed force/readback, stale callback, abandonment and
  close must all release their own handles exactly once.
- A pin protects only a still-current generation. A genuine observed save or a
  new non-W98 capture still increments the counter and invalidates the old proof.
  It also makes the old pin ineffective; a later release of that stale handle
  must never clear a newer generation's protection. An identity check prevents
  an old handle affecting a removed/recreated Watch after lifecycle cleanup.
- `quiesce` stops new work and releases unfinished captures, but must retain pins
  associated with successful proofs already held by a frozen writer generation,
  just as it retains their proof validity today. Final close invalidates lifetime
  and clears remaining scheduling state. Ordinary admission is already closed.

This is a one-way capture-admission rule, not a mutex around disk IO or a claim
on a whole Level. Governed captures do not wait for ordinary-held slots, and
multi-slot consumers acquire no all-or-nothing resource lock. Consequently an
ordinary census holding slot A while waiting for protected slot B cannot prevent
G from capturing A. G can invalidate that ordinary proof as today; ordinary safety
still requires its entire census and final frozen-survivor comparison.

### 3.2 What must not be optimized away

Never suppress vanilla/entity unload saves, Forge events, publication/pruning
invalidations, or another non-W98 capture's generation increment. Never accept
an older generation because G's individual rocket appears unchanged. Do not turn
two independent validations into one Boolean or use a proof of G as proof of O.
W98 must later perform its own fresh capture/force/readback and survivor/absence
checks; its survivor might have returned only after G's capture.

Moving ordinary retirement later in the tick only protects that tick, and is
already implemented. Lowering its quota still permits the counterexample. A
fixed delay of one force batch releases protection before slow readback or a
queued ledger write. Merely refreshing the proof's generation bypasses retained
store/readback authority. None of these is the proposed fix.

## 4. Bound and limits of the liveness claim

Consider an **admitted** governed operation whose relevant entity-slot captures
all obtain their pins. Assume (a) its frozen projections, journal leases and
other authority stay valid; (b) no vanilla/external save, publication invalidation
or other non-W98 capture supersedes those slots; (c) its own stores, forces,
readbacks and needed checked writes complete successfully and their owner-thread
callbacks are serviced; (d) other-slot captures/budgets and downstream writer
queues eventually grant the service the operation needs. Continuous W98 demand,
including demand on its own slot, is deliberately **not** required to stop.

While G's generation is current, every attempted O on that slot is refused
before `watch`. Induction over arbitrarily many O requests leaves C equal to g.
Under (a)-(d), G's unchanged validators and writer guards therefore succeed on
the first admitted attempt. The result is **one attempt per required slot, zero
retries caused by W98**, extending through the final checked write consuming the
proof. For a one-slot F1 this is one source proof plus the existing ARMED write;
for a multi-slot retirement all required proofs and C7 still have to complete.
Ordinary traffic cannot spend a governed retry or cause STALLED in that interval.

With measured upper service bounds, a single slot reaches its barrier callback
within `20 + S + F + R + C` ticks: S is remaining own-store service, F force service,
R reader queue/read service and C owner-callback service. Add downstream queue and
checked-write service Q + W to bound consumption. This deliberately loose sum is
conditional, not a newly configured timeout. Multiple slots additionally need
their existing scheduling/capture opportunities. No production source supplies
unconditional finite S/F/R/Q/W, so this note asserts no universal tick bound.

Admission before `begin`, competing governed captures, endlessly replenished
ledger queues, and continuous actual vanilla saves remain separate liveness
problems. In particular, this rule does not fix all W147 interference. A native
finite run will test the conditional bound, while the transition argument above
covers arbitrary *length* of W98 demand. Neither is evidence of unconditional
bounded liveness. If review requires that stronger claim, keep R-035(b) open
rather than change a budget or call a timed-out test a pass.

## 5. Constraints and adjacent contracts

- **No saved/API authority change:** keep formats, schemas, stable IDs, journal
  contents, replay exclusion, capture sizes, queue capacities, proof/journal lease
  validity, force cadence, retry delays and timeouts unchanged. Pins are RAM-only
  scheduling metadata. No new public/persisted reason or configuration key is
  needed. No upstream code or assets are copied.
- **Work bounds:** admission is one existing slot lookup and constant metadata
  work; no world/entity scan on deferral. Capture work remains under S5 and the
  16 MiB byte budget. Metadata is constant per each of the existing 128 watches;
  no list of rejected ordinary requests is accumulated. The existing at-most-64
  retirement entries/retry callbacks retain demand. IO stays asynchronous; no
  owner-thread `join`, sleep, force loop or new executor.
- **W98:** keep the complete census (including empty/discarded slots), frozen
  projection, passenger rule, exact-T ownership, all ticket refresh/expiry rules,
  save-off checks, storage-subclass fallback and demand pacing. BUSY is not a
  successful proof, a reason to omit a slot, or a 200-tick IO failure. Retain W116's
  mixed-batch outcome isolation, silent ordinary sinks and governed-only metrics.
- **W123:** a scheduling pin proves neither journal durability nor absence.
  Keep B14's rejection of any matching assembly/disassembly record, even a saved
  one; no record plus a current checked acknowledgement is still required. Keep
  mutation sequencing, transaction UNKNOWN until successful checked recapture,
  and the transfer adapter's distinct per-attempt outcomes. A pin never makes an
  unacknowledged/changed journal usable, nor causes a main-thread checkpoint loop.
- **W145:** retain admission to its existing 64-request flight queue while ordinary
  admissions/transitions drain. Hold the source pin through queue wait, writer
  capture, the off-thread pre-replacement guard and exact acknowledgement. Do not
  release at `F1_READBACK_AFTER` or `F1_ARM_CAPTURE`, issue a serial earlier, bypass
  the ten-tick cadence, change queue refusals, or restore the old enqueue rejection.
  An external invalidation must still fail the retained guard. UNKNOWN retains
  the exact candidate and its live proof as before. Ordinary *ledger admission*
  queues are not W98 capture requests; this rule does not drain/reprioritize them.
  Do not extend the pin into a logical/ledger lock: that could introduce a cycle
  with the queue whose draining G needs. Tests must cover that distinction.

## 6. Availability risk and Root decision required

The pin can delay multiple ordinary logical rockets sharing one protected entity
slot, including a return proving the absence of a discarded copy there. Their
actual T stays retained; source-return rockets can appear FUELED while launch,
refuel and disassembly stay refused. No world save is vetoed, no data is dropped,
and the effect is not Level-wide, but it extends a governed consumer's wait to
otherwise unrelated co-located ordinary retirements.

Duration is the retained **current** capture/proof lifetime, potentially indefinite
for hanging IO, a non-draining writer queue or UNKNOWN. Ordinary attempts still
release their own reservation on BUSY or at the unchanged attempt deadline. A
continuous sequence of different governed captures on the same slot can also
keep renewing protection even with healthy storage; this proposal gives no
fairness bound to ordinary requests under that reverse workload. A
continuing BUSY reason should not generate per-tick logs: retain existing
reason-transition logging, with gated counters for diagnosis. Other reason
transitions keep their existing logging policy; no lifetime log-volume guarantee
is invented. Normal proof release or genuine supersession removes the obstacle;
storage/queue recovery or a normal restart allows fresh proofs from retained T.
Restart discards every pin, never treats a previous session's proof as current,
and does not by itself repair damaged storage.

Root must record this availability impact in the applicable ADR/risk entry before
merging its implementation, with the distinction from a save refusal. This note
does not edit or accept R-035. **No new timeout is necessary for the narrow
one-attempt guarantee.** To guarantee finite waiting for ordinary work too would
require an additional, explicitly approved scheduling bound/fairness policy and
downstream service assumptions. Silently expiring a pin while its proof is used,
or declaring an UNKNOWN write failed to release it, is not an authorized shortcut.

## 7. Proposed tests (not executed in W149)

### Unit cases

- **U1, generation and deferral:** create a governed entity capture; submit W98
  attempts every tick through several ordinary quota windows. Assert unchanged
  generation, no ordinary store/serialization, no budget/counter/checkpoint charge,
  then exactly one ordinary admission after release. Also exercise stale hints.
- **U2, retain beyond callback:** complete barrier readback, release pending-byte
  charge, retain the proof and keep requesting O. Assert the pin still works.
  Cover joint child release order, double release, capture throw, force/readback
  failure, stale callback, quiesce, final close and Watch recreation.
- **U3, genuine invalidation:** a vanilla/nested store, publication invalidation,
  and another non-W98 capture must each invalidate G. Release of G's old handle
  must not clear the newer governed pin. Different Level, chunk, or terrain slot
  must remain independent. Ordinary-only behavior remains unchanged.
- **U4, retirement state machine:** preserve exact T, source and discarded census,
  zero/partial-census budget waits, 100/200/20-tick behavior, same-tick reservation
  deferral, save-off/fallback, W130 demand pacing and final effects-on-ack order.
  A partial census blocked by G must expire safely, not omit the blocked slot.
- **U5, W145 queue:** with nonempty ordinary admission/transition queues, retain
  F1, drain those queues, and assert one source capture and exact ARMED ack despite
  continuous W98 requests. Inject actual external invalidation while queued and
  immediately before replacement: refusal and unchanged failure/backoff rules.
  UNKNOWN retains its candidate; proof release does not acknowledge a write.
- **U6, invariants/regressions:** monitor capacity, one global capture/tick, ordinary
  sub-quotas, mixed-worker failures, W123 current/absence/UNKNOWN cases and distinct
  transfer-journal outcomes. Assert no unbounded waiter collection is added.

### GameTest cases

- **G1, shared slot:** two different logical rockets in one entity chunk. Exercise
  a real governed proof/consumer while an ordinary return repeatedly requests its
  real capture. Assert governed attempts = 1, W98 invalidations = 0, no premature
  T removal, and eventual ordinary proof/removal after G releases. Keep producer
  requests active through G's acknowledgement, not only through readback.
- **G2, multi-slot:** survivor and discarded-copy columns, with the latter pinned;
  verify no lost/duplicated rocket, no partial-census retirement, normal deadline
  cleanup and success on an eligible later attempt. Include two ordinary rockets
  sharing a slot and a different-slot control that continues while G is pinned.
- **G3, actual save:** force a genuine entity save during protection. Require
  SUPERSEDING_SAVE/refusal, no stale authority, existing retry/backoff and recovery
  once that external invalidation ends. This is a safety case, not the U1 bound.
- **G4, existing contention coverage:** retain W116F's different-slot shared-batch
  success and shared-worker force-failure coverage. The shared-slot test currently
  asserts co-staging of G and O, which the fix intentionally prevents. Replace
  only that incompatible scheduling expectation with explicit deferral and
  one-attempt governed completion; preserve every ordinary safety assertion and
  the 400-tick timeout. Keep old behavior visible in a before-fix regression run.

Register GameTest schedules at test start, not within scheduled callbacks. Keep
every existing timeout/budget. New classes run alone, then targeted suites and
all unit tests; run full GameTest and the unchanged log checker under the machine
lock. Only genuinely new diagnostics warrant exact manifest additions.

### Native adversarial case

Use a committed-source diagnostic fixture with the real entity store, Anvil
readback, checked writers and at least two logical rockets in the **same** entity
chunk, not W147's separated layout. Produce ordinary retirement demand throughout
setup, G's capture, readback, W145 queue wait and final acknowledgement; maintain
that producer for the entire fixed observation interval after G completes too.
Use a bounded ring of real ordinary launch/cancel-return cycles, starting a new
cycle only after the preceding removal is acknowledged, with fresh request IDs
and the normal replay checks. Do not clear journals or accumulate unlimited test
rockets to sustain demand. Capture attempts while a return waits remain the real
retirement coordinator's, not a fixture's bypass of its pacing.
Run additional ordinary captures in other chunks to exercise the unchanged
sub-quota. Distinguish attempted requests from admitted physical captures: this
design deliberately defers same-slot stores, not their producer. Log both, so
"continuous captures" cannot conceal a producer that was stopped for G.

Use the existing finite 4,000-tick reference deadline as a test failure ceiling,
not a new production timeout. Include repeated one-attempt cycles and an initial
W145 queue backlog that drains normally; record each governed operation's exact
ID, slot, capture generation/Watch identity, invalidator cause, queue residence,
callback, writer guard, acknowledgement and release. Pass only if every eligible
cycle completes in **one admitted attempt with zero W98 invalidations**, ordinary
captures occur before and after protection, other-slot work proceeds during it,
all conservation/census checks hold, and existing performance budgets hold.
Reaching the deadline, missing observations or freeing the slot by moving rockets
is failure, not inconclusive success. A deliberate external-save run is separate
and must demonstrate safe invalidation, not be counted as a one-attempt pass.

Add native cuts while O is deferred, while G's proof waits in W145, and after G's
checked acknowledgement but before O's proof. Restart each twice and verify
retained authority and conservation; no scheduling pin may survive the process.
Keep command lines, source/JAR hashes, bounded event data, logs and results, not
server/export copies. Take the heavy-JVM lock, check C:/D: free space and clean only
owned copies. No native experiment was run for this design-only task.

## 8. Proposed implementation slices

All remain **planned** and require Root's decision plus fresh read-only review.

| Slice | Outcome, scope and dependencies | Verification / acceptance |
|---|---|---|
| W149-P1 | Monitor/capture/barrier pin lifetime plus slot-aware ordinary admission and retirement hint. One vertical runtime change; do not ship a hint without its authoritative begin check. Depends on section 6's disposition. No journal, writer, schema or central-status edits. | U1-U4/U6, G1-G4, unchanged affected suites, full unit suite. Source review must cover every release path and joint child. |
| W149-P2 | W145 retained-queue and exact-write integration coverage; change a caller's lifecycle only if review shows missing release, never its authority checks. Depends on P1. | U5 plus real queued-flight GameTest, genuine invalidation and UNKNOWN tests. No broader writer fairness claim. |
| W149-P3 | Diagnostic native demand/load and cut evidence, with bounded test-only instrumentation as needed. Depends on P1/P2, committed tested source and independent persistence review. | The native case above, two restarts per cut, complete GameTest/checker and Root's integrated regression. |

Root owns ADR/risk disposition, admission decisions, integration, central records
and version Gates. N3-B/W148, generalized external-save liveness, writer fairness,
production admission, upstream imports, network/UI changes and v1.9 work are not
part of these slices or this design task.

## 9. Execution record

### Completed / not completed

Completed: baseline/source inspection, W98-R1 behavior and rate analysis, the
proposed mechanism, its conditional attempt bound and availability tradeoff,
W123/W145 constraints, test specification and implementation decomposition.
Only this note is committed locally; no push, merge or delegation. Initial worktree
was clean and branch/HEAD matched the assignment. Other observed agents used the
shared Root or W148 review worktree, not this write scope.

Not completed: runtime implementation, executable regression tests, native load,
independent review, ADR/risk acceptance or any release acceptance. The existing
implementation log, status files, ledger, source, tests and build files are unchanged.
No upstream file was imported; provenance records were inspected only for scope.

### Commands and verification

Read-only inspection used `git status --short`, `git branch --show-current`,
`git rev-parse HEAD`, `git worktree list`, `git log -1`, `Get-CimInstance Win32_Process`,
`Get-Content`, `rg` and `Get-FileHash`. A few exploratory reads used nonexistent
guessed filenames or PowerShell wildcard arguments unsuitable for `rg`; the actual
files were then located with `rg --files` and read. No missing-read result is
treated as evidence. The W98 review hash matches the recorded independent review.

`python -B scripts/validate_repository.py --require-approved-identity` passed with
45 checks, zero pending/warnings/failures, exit 0. The retained pre-commit run is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W149/verification/repository-validator-before-commit.log`,
SHA-256 `19c075bbf060101670005fef062000eab1c5c8534a67812df57789234b5ac8cc`.
`git diff --check` passed. A scoped `git diff --exit-code
2645891f609ff608a17da842c63b373ff24a5d1e -- src scripts tests build.gradle
gradle.properties docs/status docs/11-RISK-REGISTER.md docs/decisions` returned 0.
Final staged-scope, committed-source validator and clean-worktree checks are
retained in the same external verification directory; the delivery commit is
identified there rather than embedding this document's own changing commit ID.
No Java, Gradle, GameTest, runData, full build or server command was run: the TASK
explicitly requires source inspection and repository validation for this no-code
design. Historical W98/W116/W147 test results are attributed records, not reruns.

### Gate status and next action

The active development scope is v1.8 under
[ADR-060](../../../decisions/ADR-060-V180-DEVELOPMENT-BASELINE-EXCEPTION.md);
the release cursor remains v1.0 and predecessor release Gates are not approved.
v1.8 still requires G0-G9. **This task does not satisfy all Required Gates or close
R-035(b).** Next: Root commissions the specified fresh read-only design review,
decides whether the explicitly unbounded ordinary wait is acceptable, and only
then assigns the remaining v1.8 implementation/verification slices.
