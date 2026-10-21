# W129 design note: off-thread checked writers for the satellite registry and the rocket transaction journal

Date: 2026-10-21. Author: a headless Claude session, assigned by Root (Claude). Base: shared commit `75ffa1fc`;
worktree `D:/GitHub/arce-v180-w129`, branch `codex/v1.8.0-w129`, local commits only. Contract:
[TASK-W129](../TASK-W129-OFF-THREAD-WRITERS-DESIGN.md), Root decision R51 (in [QUEUE](../QUEUE.md), the W128 row).
Inputs: [W128](W128.md) (the benchmarks and the stop), [W119](W119.md) (the registry's epoch rule), [W123](W123.md)
(the transaction journal's checked acknowledgement), [W27A-DESIGN](W27A-DESIGN.md) sections 3, 4 and 8,
[ADR-050](../../../decisions/ADR-050-MISSION-SCHEDULER-AND-RECOVERY.md) section 2 with its W27 implementation note,
and [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md) section 4. The normative text is the proposed
[ADR-074](../../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md); this note holds the reasoning,
the checked facts and the test plan. It changes no Java, script, test, status file, risk entry or ledger, and it
approves no Gate.

Revision 2 (2026-10-22): revised by the W129R Codex worker at base `0a361903`, under
[TASK-W129R](../TASK-W129R-OFF-THREAD-WRITERS-REVISION-2.md), Root decision R53. This revision supersedes the
original implementation contract, not its historical measurements. The independent review is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W129-review/REVIEW.md`, SHA-256
`5f5ba8a1a2edfbf6daa88be278aa2bbb7d81cf5fc0b94ebe95eee528850cbf11`; its model, mutable-position probe and
pinned-JAR bytecode extracts were read. Finding dispositions are in [W129, Revision 2](W129.md#revision-2).

Terms. E is ADR-050's save epoch, from now on called the **durable epoch**. A **capture** is the immutable copy of a
root that one write attempt encodes; s is its mutation sequence and w the epoch it carries. h is W119's highest
reserved epoch (W119 calls it the highest unknown epoch). MAX means `Long.MAX_VALUE`. m is the mutation sequence of a change that something waits
for. L, T, D0 to D7 and B14 are W27A-DESIGN's. Java paths are relative to
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`, at `75ffa1fc`.

## 0. Checked facts

| Fact (file and line at `75ffa1fc`) | Consequence for this design |
|---|---|
| Every registry write entry calls `SatelliteRegistryWriter.write` (`satellite/persistence/SatelliteRegistryWriter.java:76-137`). It encodes the live registry with `data.save(new CompoundTag())` (`:88`), runs the checked replace (`:94-97`), reads back by `equals` after a committer throw (`:99-110`) and applies W119's epoch rule (`:116-130`). | Encoding, gzip, staging force, staging readback and replacement all run on the calling thread. W128 timed exactly this path. |
| `CheckedSavedDataFile.replace` (`persistence/migration/CheckedSavedDataFile.java:50-65`) builds the outer tag, gzips it (`BoundedSavedDataIo.write`, `BoundedSavedDataIo.java:118-125`), writes and forces the staging file, decodes it again and compares it with `equals`, then commits. | Every write decodes a second full tree. Both trees are transient heap on the writing thread. |
| W128 measured, with CPU close to wall time: registry checkpoint P50 409.5, P95 448.1, P99 892.5 ms; coalesced flush 415.4, 454.7, 495.0; autosave 417.4, 461.2, 509.0 (root 16,708,791 bytes, 5,226 missions); transaction `saveChecked` with one record 12.3, 25.6, 78.7 ms; with 256 records at 66,937,673 bytes 3,951.3, 4,104.8, 4,205.1 ms. Evidence `registry-benchmark.json` SHA-256 `3d854dec…1031034`, `transaction-benchmark.json` SHA-256 `7030c2a9…19f665012b`, re-hashed for this note. | The cost is encoding and checking, not IO waiting. Moving it to another thread removes it from the tick; nothing smaller than that does. |
| The registry's domain state is five `LinkedHashMap`s of records plus the clock and epoch. Lists are copied, but `MissionPayload.TerminalLocation` retains a potentially mutable `BlockPos` argument (`satellite/mission/MissionPayload.java:106-120`). | Reference-only capture requires the constructor hardening and nested-value audit in section 2.1; a Java record alone does not establish deep immutability. |
| Count bounds: 4,096 satellites, 8,192 missions (load bound), 4,096 research accounts, 2,048 instances, 2,048 ground surveys (`satellite/model/SatelliteLimits.java:47-55`). | A registry capture copies at most 20,480 references. |
| The transaction journal holds at most 256 `RocketPersistedTransaction` entries (record, snapshot, owner). Snapshots are immutable, with defensive tag copies (`rocket/model/RocketBlockEntityPayload.java:24,40`, `RocketStructureSnapshot.java:38,127,144`). Its checked save encodes and digests on the calling thread (`rocket/persistence/RocketTransactionSavedData.java:124-157`, digests `:136-142`). | A journal capture is a list of at most 256 references. |
| Assembly and disassembly write and remove their journal record inside one `execute` call (`rocket/transaction/RocketAssemblyTransaction.java:114-209`, failure paths `:261-313`). | The live journal is normally empty or nearly so; records persist only after an interrupted or failed operation. |
| The transfer journal already writes off-thread: an immutable capture of records (`rocket/flight/persistence/RocketJournalWriter.java:22-37`), encoding and digests on the IO thread (`:87-114`), owner-thread publication (`:115-132`), drain, close and abort (`:134-166`). W11B2 continuation 03 measured its server-thread capture and publication at mean 0.44 ms, P95 0.87 ms, P99 7.30 ms and maximum 10.13 ms, against 515.5 ms for the in-place encoding of the same 134 MB root, with a sampled peak heap of 1.94 GB. | The capture pattern is proven on this host at a larger root. |
| `EscrowLedgerWriter` and `BayReceiptWriter` capture through a supplier when the active request finishes (`resourceescrow/EscrowLedgerWriter.java:117-146`) and retry an UNKNOWN outcome with the exact candidate bytes (`BayReceiptWriter.java:119-160`). | The registry cannot copy that retry rule: W119 forbids rewriting frozen bytes. The new writers capture fresh at every attempt. |
| Vanilla, by `javap -p -c` on `forge-1.20.1-47.4.10_mapped_official_1.20.1.jar` (SHA-256 `95eecc59…2d394d71e`, the W27A jar): normal `runServer` calls `handleServerStopping`, sets `stopped`, then calls `stopServer`; the exception path sets `stopped = true` at offset 521 and calls `stopServer` at 525 without that event. It need not clear `running`. `onServerCrash` is a base no-op and is not overridden in this `DedicatedServer`. `stopServer` saves/removes players, drains chunks, saves Levels, posts their unload events, closes Levels and then storage. `ServerLevel.save` reaches SavedData only when `skipSave` is false; `flush` affects chunks/entities. Autosave is every 6,000 ticks. `ServerWatchdog.run` tests `isRunning()`. | Shutdown is `server.isStopped() || !server.isRunning()`. The watchdog can interrupt exception-path teardown; a final-state drain is conditional on its completion. `/save-all flush` gives SavedData nothing extra. |
| A ground-survey start and an account creation run a whole-root preflight: `CodecRecordSizer.groundMutationFits` encodes the full registry and round-trips it through `BoundedSavedDataIo.validatePayload` (`satellite/persistence/CodecRecordSizer.java:107-125`, called from `GroundSurveys.java:369-375`). | This is a server-thread whole-root pass that no writer change removes (section 2.4). |

## 1. Summary

Each of the two roots gets one checked writer with its own IO thread. The server thread only takes an immutable
capture (references to immutable records, a sequence and, for the registry, a carried epoch) and later publishes the
exact outcome. Encoding, digests, staging, force, readback, replacement and directory force move to the IO thread.
At most one capture exists per root. A request made while one is in flight only sets a pending flag, and the next
attempt captures the then-current RAM. W123's acknowledgement and W119's no-mutation results stay unchanged.
W119's defensive mutation-inside-committer test intentionally changes semantics (section 4.1).
A mutation during a flight is stamped with the in-flight capture's epoch, the **snapshot epoch** that ADR-050
section 2 requires, so it cannot appear durable until a later capture that contains it commits. Barrier callers
stop waiting inside `flush`. Each waits instead for an acknowledgement that covers its change before its dependent
effect runs, and the terminal locks the package and chip it acts on until then. D0's journal checkpoint and D3's
registry checkpoint become requests answered on a later tick.

## 2. What stays on the server thread

### 2.1 The registry capture

A capture holds:
- the mutation sequence s;
- the carried epoch w (section 4.1);
- the logical and last observed game times;
- five immutable lists of the current record references: satellites, missions, research accounts, instances and
  ground surveys;
- the request attributes: whether the directory is forced, and the committer policy, including a release-test cut.

A blocked registry's capture holds the preserved root. `preservedBlockedData` is set once at load and never mutated,
so the capture shares that reference read-only instead of copying 16 MiB.

**Nested-value audit at `0a361903` (W129-R6).** Shared captures require these boundaries:

| Captured graph | Mutable-type boundary and implementation obligation |
|---|---|
| `SatelliteState` -> blueprint, stats, kind state | Blueprint components use `List.copyOf`; IDs, stats and kind-state fields are immutable scalar/value records. No live item or battery object is captured. |
| `MissionState` -> payload, quarantine | Survey UUID lists and resource reward lists are copied; `RewardEntry` holds an ID/count, not `ItemStack`. Optional wrappers do not freeze their contents: `Resource.boundTerminalDisplay` reaches `TerminalLocation.pos`. W129b must assign `pos = pos.immutable()` in the constructor. Test mutation of a supplied `MutableBlockPos` after construction and after capture; the accessor and encoded capture must retain the original position. |
| `AsteroidInstance`, `GroundSurveyJob`, `ResearchAccount` | Yield lists use `RewardEntry.validated`/`List.copyOf`; ground devices use packed `long` positions; snapshot, claim, analysis and quarantine records contain scalar/value fields. Accounts hold UUID/numeric values. |
| `RocketPersistedTransaction` -> record/region and structure snapshot | Region/bounds/anchors use integer `RocketPosition` records. Snapshot lists are copied. `RocketBlockState` copies properties into an unmodifiable sorted map; propulsion settings copy the map and nested string lists. `RocketBlockEntityPayload` copies mutable `CompoundTag` on construction and access; IO encoding may copy it, but owner-thread capture must not. |
| Blocked registry root, acknowledgements, capture lists | The preserved `CompoundTag` is privately copied at load; public accessors return copies. A new internal capture seam may share only that private read-only root, never expose it or let encoders mutate it. Freeze any newly built identity/digest maps and copy live-map values into capture lists, not views. |

W129a/b tests must attempt mutation of supplied collections, nested positions and tags and verify captured encoding
does not change. This is a source audit, not execution of a new asynchronous writer. Recheck new nested fields in
the implementation diff; do not replace this boundary with a whole-root owner-thread NBT copy.

The capture avoids re-encoding because it builds no NBT. `SatelliteRegistryPayload.encodeCurrent` today reads the
live registry (`satellite/persistence/SatelliteRegistryPayload.java:87-111`). The writer instead uses an encoder of
the capture with byte-identical output. The sorting that `SatelliteMissionRegistry.sorted` does today (`:536-542`),
the identity map that W119's acknowledgement carries (`SatelliteRegistryWriter.java:60-64`), the NBT bound check,
gzip and the readback all move to the IO thread.

**Cost at the bounds.** This is an estimate from the code, not a measurement. The capture iterates at most 20,480
map values into five arrays and reads four scalars. That is linear, with no allocation per record beyond the list
slots, and should take well under 5 ms. The nearest measured analogue is the transfer journal's capture and
publication: mean 0.44 ms and P99 7.30 ms on this host. The implementation's first commit measures it (section 6.1)
under W128's stop rule.

If the capture missed the budget, its own bound would be the count limits above. The remedy would be persistent
(copy-on-write) section maps, which make a capture O(1) at the price of slower single updates. No bound needs to
change. This note does not select that remedy before measurement. The linear copy is plausibly cheap, but the
estimate is not a measured margin or a reason to skip the gate.

### 2.2 The transaction journal capture

The capture holds the sequence s, an unsorted list of entries (at most 256 references) and the previous
acknowledgement and outcome. The IO thread needs the last two for W123's readback classification (section 4.4). The
cost is unmeasured. Sorting, encoding, per-record NC1 digests, the root digest and every file step leave the server
thread. Neither root may capture through today's sorted getters; sorting and identity-map construction belong to IO.

### 2.3 Publication on the owner thread

At `ServerTickEvent` START, at HIGHEST priority, each root polls its writer:
- It publishes at most one completion. Publication applies the outcome, epoch and acknowledgement rules of section 4.
- For the registry, an epoch advance also runs the existing hook `ResourceMissions.epochAdvanced`
  (`satellite/mission/ResourceMissions.java:456-470`). It is bounded by the acknowledged-but-not-durable missions
  and runs today inside every write.
- It then starts the next capture if one is pending and allowed (section 3).

Waiting effects run after publication, bounded per tick (section 5.2). Publication encodes nothing.

### 2.4 Server-thread work that this design does not remove

- **The ground-survey preflight** encodes and round-trips the whole registry for each ground-survey START, each
  research-account creation through ground mutations, and the restored-over-budget fallback when section
  reservations cannot bound the root (`GroundSurveys.java:368-397`). At a full root this is owner-thread work. It is
  not a write entry, W27d never calls it, and no writer design touches it. Root should track it as its own leaf: for
  example, prove the root bound from the section reservations, as other mutations already do (`GroundSurveys.java:369-372`),
  or measure it at the bound (question Q6).
- **Load, migration and the pre-start validator** stay synchronous. They are startup work, which docs/17 exempts
  from the spike rule.
- **The stop-path drain** (section 4.6) waits after the tick loop, but the exception path can still have a running
  watchdog. Neither a retry count nor the historical per-write measurements bound shutdown wall time.

## 3. What moves off the thread: one writer per root

Both roots use one small shared writer core, with a root-specific capture, encoder and outcome rule. The core is
used by two current roots, as W123's checked core is, so it is not an unused framework. The three existing writers
otherwise stay as they are; section 5.4 may require the transfer writer to share a measured IO-side permit.

| Property | Rule |
|---|---|
| Ownership | One writer per root instance, created lazily on its first request, owned by that `SavedData` for one server lifetime; no static state. Every public method checks the owner thread. The IO thread is a single daemon thread, `ARCE-satellite-registry` or `ARCE-rocket-transactions`. |
| Candidates | At most one capture exists. `request(attributes)` merges into one pending flag: a directory-force request is ORed in, and a later policy replaces an earlier one. If the writer is idle and allowed to start, it captures and starts IO at once. Otherwise the capture happens when it may start. Every attempt is a fresh capture. Frozen bytes are never written again (W119). |
| Start cadence | Every running-server start path observes at least 10 ticks after the previous start and 200 ticks after a FAILED, NOT_COMMITTED or UNKNOWN outcome. Requests coalesce without resetting the failure backoff. Shutdown uses W104's separate monotonic millisecond drain clock for the intervals, not frozen server ticks. |
| IO thread | It touches no `Level`, server or live `SavedData`, only the capture. It encodes, checks the bound, computes digests (journal only), stages, forces, reads the staging file back and replaces through the policy's committer, then forces the directory per policy. A committer throw leads to a bounded readback in the same attempt. Any `Throwable` other than a `RuntimeException` gives UNKNOWN, as in W27C1-R6 and W11b2. The completion goes into a one-slot queue. |
| Publication | Owner thread only, at tick START. A completion that does not belong to the active capture is refused with `IllegalStateException`, as `RocketJournalWriter.tick` does. |
| Drain | Stop path only (section 4.6): success requires a COMMITTED capture covering the final target sequence, with no uncovered mutation or unserviced request. One per-root shutdown budget of three total attempts includes any active/unpublished attempt and is shared by `save` and `close`. Older success does not clear successor work. No gameplay effect runs. Exhaustion or an exception reports an incomplete drain, not success. |
| Close and abort | `close` uses the same drain state/budget, stops admission and joins IO. `abort` stops admission and joins without publishing; it reports unresolved work and cannot claim a successful drain. Both are idempotent. Neither cancellation, interruption nor a join timeout permits world ownership to be released while IO can still replace a file. |
| Target | The first request binds the normalized file. A different target is refused, as `RocketTransactionSavedData.saveChecked` refuses today. A detached root binds its own file. Tests may pass an executor seam. |

The IO steps per root, with today's outcome rules moved unchanged:

| Step | Registry | Transaction journal |
|---|---|---|
| Encode | Capture encoder; NBT bound 16 MiB. | `save(CompoundTag)` equivalent over the captured entries. |
| Digest | None (W27A N9). | NC1 per record (depth 35) and NC1 root, as `CheckedJournalCheckpoint.write` does. |
| Replace | `CheckedSavedDataPolicy.replace`: staging, force, staging readback, atomic move without fallback. | The same. |
| Directory force | Only when a request asked for it (the bay checkpoint, W119-4). A failure is visible but leaves COMMITTED. | Always, per platform. A failure makes the outcome UNKNOWN (W123). |
| Committer threw | Read back; `equals` the candidate gives COMMITTED, a readable different file gives FAILED, an unreadable one gives UNKNOWN. | Read back; equal gives COMMITTED; disk equal to the previous acknowledgement's digest, or absent with none, gives NOT_COMMITTED; otherwise UNKNOWN. |
| Failure before the committer | FAILED. | NOT_COMMITTED. |

## 4. Acknowledgement semantics

### 4.1 The registry's epochs

Definitions:
- **E**, the durable epoch, is the epoch of the newest COMMITTED capture published in this session, or the loaded
  file's epoch.
- **h** is W119's reserved epoch: no new capture reuses an epoch at or below it. On load h = E.
- A capture is **in flight** from the moment it is taken until its completion is published.
- The **snapshot epoch** is the epoch that a mutation stamps. It is w while a capture carrying w > E is in flight,
  and E otherwise.

Rules:
1. A capture taken while the registry holds a state change that no published COMMITTED capture covers carries
   w = max(E, h) + 1. Otherwise it carries E. A clock-only write keeps E, and an unchanged reload writes the same
   bytes (ADR-050 revision 4, item 1).
2. Every stamp uses the snapshot epoch: mission `start_epoch`, delivery `ack_epoch`, and ground-survey start and claim
   epochs. Every durability test uses E. Section 4.3 lists the sites.
3. COMMITTED, published: E becomes max(E, w) and h becomes max(h, w), once, at publication. The acknowledgement
   becomes (session, s, w, identities of the captured satellites). If nothing mutated since the capture, dirty clears.
   If no state change happened since the capture, `flushPending` and the changed-since-E flag clear. Otherwise they
   stay, and the next write captures again.
4. FAILED, published: E, the acknowledgement, dirty, `flushPending` and the changed flag stay. h becomes w only if the
   registry mutated while this capture was in flight. Otherwise h is unchanged and w may be carried again, as W119
   allows.
5. UNKNOWN, published: the acknowledgement is cleared, h becomes max(h, w), and the rest stays (W119: UNKNOWN reserves
   its epoch).
6. Nothing else advances E. There is no readback of an older candidate: each attempt's readback compares the disk
   only with its own candidate, inside the same attempt. FAILED or UNKNOWN alone does not refuse mutations; the
   finite-epoch capacity guard in section 4.3 still applies.
7. A blocked registry's COMMITTED write clears dirty and never creates an acknowledgement (as at `:130-131` today).

**Why this keeps W119.** W119 lets E advance "only when that capture is COMMITTED within its own call and s still
equals `mutationSequence`". The sequence guard existed because a mutation made after a capture stamped the unchanged
E. If E then advanced to w, that mutation would look durable while missing from file w. Under rule 2, every mutation
made while capture w is in flight stamps w, and "durable" means stamp < E, so E = w never covers it. Rule 3 can
therefore advance E at any COMMITTED publication. Rule 4 reserves w when such a stamp may exist, so that stamp
becomes durable at the very next commit instead of waiting for an unrelated change. When nothing mutates during the
flight, rules 1 to 7 give exactly W119's results: the same
carried epochs, the same E after each outcome, the same stamps, the same dirty and flush-pending state. The existing
no-mutation matrices keep their expected values; synchronous calls become a request followed by awaited publication.

**Reviewed semantic transition (W129-R3).** `SatelliteRegistryProofTest.java:120-137`,
`mutationInsideCommitCannotAdvanceEpochOrClearDirtyOrPending`, already injects a mutation inside the synchronous
committer. Its epoch expectation intentionally changes under ADR-074; this is not merely an await conversion.
Pause IO after capture w, perform the later launch on the owner thread, then release IO and publish. Assert E = w,
the later start stamp = w, its identity absent from the file and unproved, and dirty/flush-pending retained. The
next fresh capture must contain it, advance to w + 1 and prove it. Keep all safety assertions; never mutate live RAM
from an IO committer callback. This named exception corrects the revision-1 claim that every existing expected value
would remain unchanged. Other semantic differences require their own explicit review, not weakened assertions.

Keeping W119's sequence guard literally would also be safe, but not live. Every 20-tick scheduler pass that moves the
clock mutates the registry (`SatelliteMissionSavedData.java:108-115`). At the bound a write is in flight for 8 to 10
ticks, and a busy server mutates every tick. E would then rarely advance, and every epoch consumer would stall:
resource receipts, retention, ground surveys and classic research. This is the separation ADR-050 section 2 requires:
"a snapshot epoch, fixed when the root is serialized on the main thread and used for start_epoch, from the durable
epoch, advanced when the write completes".

**Invariant** (ADR-050 revision 4, item 1): a stamp t is treated as durable (t < E) only after a COMMITTED capture
containing it has been published. A stamp issued before capture c was taken is in c. A stamp issued while c is in
flight equals c's epoch. A stamp issued after c was published is at least E. After a restart E is the file's epoch,
and RAM is the file, so the session-local snapshot epoch needs no saved field.

| Case | Capture carries | During the flight | Outcome | After publication |
|---|---|---|---|---|
| A | E (clock only) | nothing | COMMITTED | E unchanged; dirty clears. |
| B | w = max(E,h)+1 | nothing | COMMITTED | E = h = w; all flags clear (W119). |
| C | w | nothing | FAILED | E, h unchanged; the next changed capture may carry w again (W119). |
| D | w | nothing | UNKNOWN | h = w; acknowledgement cleared; the next carries w+1 (W119). |
| E | w | mutation M stamped w | COMMITTED | E = w; M is not yet durable; dirty and flags stay; the next capture carries w+1 and proves M. |
| F | w | M stamped w | FAILED | h = w; stamps return to E; the next carries w+1, which proves M and later stamps. |
| G | w | M stamped w | UNKNOWN | h = w; acknowledgement cleared; as F. |
| H | E (clock only) | state change M stamped E | COMMITTED | E unchanged; the changed flag stays; the next carries max(E,h)+1. |

### 4.2 The registry proof

The proof rule and the session-local acknowledgement stay exactly as in W119 and W27A section 3:
- A watch (session, m, identity I) is proved by a published COMMITTED acknowledgement with s >= m and an equal
  captured identity.
- UNKNOWN clears the acknowledgement.
- A clean current-schema load is an acknowledged disk observation (`SatelliteRegistryWriter.java:54-59`). A restored
  root needs a fresh write.
- A blocked root never proves.
- A directory-force failure is reported in the completion and does not undo COMMITTED.

The checkpoint's fast path stays: a clean registry whose acknowledgement already proves every watch answers COMMITTED
without writing.

Two changes are needed. `checkpoint(server, watches)` becomes `requestCheckpoint(server, watches)`, which asks for
the directory force and returns at once. The completion that answers it is the first published completion with
s >= m. The registry also issues a removal token (session, m) for changes that leave no identity to watch:
decommission, and the data actions. A COMMITTED acknowledgement of the same session with s >= m covers the token.

### 4.3 Stamp and durability sites

Each site is classified once and tested. Stamps read the snapshot epoch and checks read E. Before any stamped
mutation is admitted, compute its prospective stamp t and reservation ceiling r = max(E, h, active w if present).
Require a representable successor u = r + 1 with u > t, using checked arithmetic before changing RAM or consuming
items/FE. This includes a completed-but-unpublished capture. State changes needing a new epoch also require this
capacity; clock-only unchanged writes may retain E. Exhaustion returns an explicit capacity refusal to the caller,
not wraparound, an admitted permanently pending job, or an unchecked arithmetic exception.

For E = h = MAX - 1, an idle stamped mutation can receive MAX - 1 and be covered by capture MAX. While that capture
is active, another stamped mutation would receive MAX and must be refused. At E or h = MAX, no new changed capture
or stamped mutation is admissible. If an UNKNOWN at MAX consumes the last reservation after earlier admission,
report explicit epoch-capacity exhaustion for the outstanding work and keep it dirty/unproved; do not spin retries
or reuse the uncertain epoch. Existing data is retained, not reset or dropped. Recovery needs a separately reviewed
migration/operator decision; a restart observes the file but is not a guaranteed capacity repair. These finite-long
rules apply to FAILED and UNKNOWN as well as COMMITTED; no schema or numeric bound is relaxed.

| Use | Sites |
|---|---|
| Stamp | `SatelliteMissionRegistry.java:354` and `SatelliteLaunchAdmission.java:35` (launch start epoch); `ResourceMissions.java:107,153` (start epoch); `ResourceMissions.java:298` (`acknowledgedAt`); `GroundSurveys.java:125` (start epoch); `GroundSurveys.java:214,232` (claim epochs). |
| Durability check | `DeliveryReconciliation.java:53` through the view built at `ResourceMissions.java:290-292`; `ResourceMissions.java:220,464,475`; `GroundSurveys.java:154,249`; retention through `SatelliteMissionRegistry.java:376,644,650,735`; `machine/classic/runtime/ResearchService.java:193,268`. |
| Overflow guard | `GroundSurveys.java:314,368`; `ResourceMissions.java:395`; `CodecRecordSizer.java:114`. |
| Audit or diagnostics | `ResourceMissionService.java:288,298`; `SatelliteMissionSavedData.java:249`; `ReleaseTestCommands.java:297` (report E; the snapshot epoch may be added). |

`ResourceMissions.reconcile` reads one `epoch` at `:290` for both the durability view and the acknowledgement stamp at
`:298`. The implementation must split it into the two values.

### 4.4 The transaction journal

W123's acknowledgement stays exact: session-local (sequence, root digest, transaction-to-record digests); loading
never acknowledges. On publication of the capture with sequence s:
- COMMITTED sets the acknowledgement to that candidate and the outcome to COMMITTED.
- NOT_COMMITTED keeps the old disk proof and sets the outcome to NOT_COMMITTED. If the previous outcome was UNKNOWN, it
  stays UNKNOWN (W123's sticky rule, `RocketTransactionSavedData.java:150-153`).
- UNKNOWN clears the acknowledgement.
- In every case dirty becomes `!acknowledgedCurrent()`, compared with the current sequence, so a mutation during the
  flight keeps it dirty.
- A non-operational journal (blocked or unreadable) refuses the request on the owner thread: NOT_COMMITTED, the sticky
  rule applied, nothing staged, the file never overwritten.

B14 keeps its four answers and gains one, WRITE_PENDING, which only exists while a request is pending or in flight.
The synchronous design could not observe that state.

| Order | Condition | Answer |
|---|---|---|
| 1 | journal not operational | ASSEMBLY_JOURNAL_UNPROVED |
| 2 | a RAM record matches L's transaction ID, the snapshot ID or the entity ID | ASSEMBLY_JOURNAL_PENDING |
| 3 | `acknowledgedCurrent()` | PROVED |
| 4 | the writer has a capture in flight or a pending request | WRITE_PENDING |
| 5 | last outcome NOT_COMMITTED or UNKNOWN | ASSEMBLY_JOURNAL_UNPROVED |
| 6 | otherwise | WRITE_REQUIRED |

PROVED before WRITE_PENDING is sound: the writer is sequential, so a capture in flight while the acknowledgement is
current was taken at the current sequence and has the same content. WRITE_PENDING masks stale outcomes. After a
request, the first answer other than WRITE_PENDING therefore reflects a capture taken at or after that request.
`checkpoint(server)` becomes `requestCheckpoint(server)`, which answers REQUESTED, or REFUSED on a non-operational or
stopping journal.

### 4.5 Vanilla autosave, `/save-all` and `save(File)`

- **While the server runs** (`!server.isStopped() && server.isRunning()`). Autosave, `/save-all` and `/save-all flush`
  call `save(File)` on the server thread. If the root is dirty, the call makes a request and returns. It never waits.
  - If a capture is in flight, the request sets the pending flag, and the next capture, after publication and the
    cadence, takes the then-current RAM.
  - A clean root makes no request.
  - `/save-off` skips SavedData in autosave, but registry barriers, the coalesced flush and D0's request still write,
    as today.
- **Stopping** (`server.isStopped() || !server.isRunning()`, either path): section 4.6.
- **`save(CompoundTag)`** stays an encoding-only view for tests and diagnostics, and never acknowledges. The writer
  never calls it on the live object.
- **Operator meaning of `/save-all flush`.** It no longer guarantees that these two files are current when it
  returns. This is already true of the transfer journal, escrow ledger and receipt files. Atomic replacement
  protects an individual file, not a consistent multi-file live backup: a copier can read the old registry and then
  a chunk changed after the new registry committed. W129c must document this operator-visible limitation. Recommend
  a gracefully stopped, successfully drained whole-world backup; a failed/interrupted drain is not that guarantee.
  A consistent live snapshot protocol would need a separate design. No synchronous command drain is added here.

### 4.6 Stop, crash and kill windows (both roots)

| Moment | Registry and transaction writers |
|---|---|
| Tick loop running | Requests, IO and publication as above; every dependent-effect entry checks the shutdown predicate before acting. |
| `ServerStoppingEvent` (normal path only), HIGHEST, or first observation of either shutdown flag | Latch stopping idempotently and discard waiting terminal/D0/D3 effects without executing them. Gameplay requests are refused; persistence/logout mutations remain possible. The writer stays open, but starts are controlled by the one shutdown state/budget. |
| `stopServer`: players saved and removed, chunk work drained | Logout handlers may mutate RAM while an older IO attempt runs. They also check/latch shutdown before any effect or publication; the missing normal event on the exception path cannot permit gameplay effects. |
| `saveAllChunks` reaches `save(File)` after logout | Set final target F to the root's current mutation sequence, including clock-only changes. Drain until a published COMMITTED capture has s >= F and all final work is covered. An older success retains dirty/pending successor work. This is the final-state success condition, not a promise that storage will succeed. |
| Overworld `LevelEvent.Unload`, before storage closes | `close` rechecks the target against RAM and resumes the same drain, if needed, without resetting attempts. If `save` was skipped, establish F here. Only report a successful drain if coverage holds; then join. An exhausted drain still joins and reports incomplete persistence. |
| `ServerStoppedEvent` or exceptional lifecycle cleanup | Idempotent abort backstop, not a substitute for joining before ownership release. The implementation must prove its normal and exceptional cleanup order; it must never return control to world-storage release while its IO can still replace files. |
| Hard kill or power loss | The file is the last COMMITTED candidate, or a later candidate whose outcome was UNKNOWN. A leftover staging file is overwritten by the next staging. Proofs, the snapshot epoch and pending actions are RAM only and vanish. Every dependent effect that ran had a published acknowledgement, so its prerequisite is in the file (S2). Windows has no directory force, so power loss stays outside S2, as today. |

**Final-target algorithm and budget (W129-R2).** Keep per-root shutdown state `(F, attemptsUsed, outcome)` across
`save`, `drain` and `close`. At shutdown entry count the active attempt, including a queued unpublished completion,
as attempt 1; with none, start at 0. Each subsequent fresh IO attempt consumes one slot, whether it commits or
fails; three is the total, not three per method or per target. No new attempt starts during logout before F is
established. An already published current acknowledgement can satisfy a clean target without another write.
After each publication compare s with F and the current sequence. If persistence hooks mutate RAM, raise F to that
sequence and retain/request fresh work under the remaining budget; never reset the budget. Success requires target
coverage, no remaining dirty/pending work and no active write. A redundant request may be cleared only when its
sequence and attributes (including directory force) were serviced. A blocked registry uses a committed capture's
sequence solely as persistence coverage, never a gameplay acknowledgement; an unreadable journal still refuses IO.

If the budget is exhausted, capacity is exhausted, or the drain throws, retain uncovered dirty state and report one
incomplete-drain WARN per root/shutdown with F, last committed sequence, attempts and reason. `close` must not retry
under a new budget or hide the failure. Shut down admission and join any active IO, even when abandoning publication.
An interrupted join waits for termination and preserves the interruption for its caller afterward; cancellation or
executor shutdown is not proof of termination. No timeout may release world ownership while replacement is possible.
Filesystem force/readback/join can block indefinitely. Total shutdown duration is **unmeasured**; W128's healthy-disk
per-write timings are neither failing-disk bounds nor bounds for three attempts plus backoff and join. If IO is stuck,
the operator must end the process before reopening/copying the world and use restart recovery or a known-good backup;
final RAM persistence is then not guaranteed.

**Watchdog residual (W129-R1).** `ServerWatchdog` checks `isRunning()`. A thrown tick exception may leave it true
while `isStopped()` is true. No watchdog setting is changed or timeout enlarged. If it terminates teardown before
coverage, only S2's committed-or-unknown whole-file recovery and already-acknowledged effect ordering remain; F is
not guaranteed durable and an in-process failure log may not finish. Native R7 must exercise these exact flags,
and an interrupted-teardown cut must check the reduced guarantee, not report a graceful-drain success.

## 5. Effects

### 5.1 The bay protocol

| Step | Today's design (W27A, W128) | With this design |
|---|---|---|
| D0, B14 | One timed synchronous journal checkpoint, re-query on the next step; failure rejects every bay. | The first UNPROVED or WRITE_REQUIRED answer sends `requestCheckpoint`. L's claim stays HELD with its tickets renewed and no time limit. WRITE_PENDING waits; PROVED continues; UNPROVED after the request rejects every bay (ASSEMBLY_JOURNAL_UNPROVED); WRITE_REQUIRED (mutated since) requests again. W128 measured about 26 ms P95 for one record and about 4.2 s P99 at the bound per synchronous attempt; these are not asynchronous wait bounds. Writer availability joins D0's existing checks. |
| D1 | Receipt writer, already off-thread. | Unchanged. |
| D2 | RAM validation and, in W27d2, RAM registration at sequence m. | Unchanged. |
| D3 | Synchronous `checkpoint`; FAILED or UNKNOWN keeps INTENT and retries at most once per 200 ticks; three failures give runtime REGISTRY_UNPROVED. | `requestCheckpoint` with watches. Wait for the first published completion with s >= m; COMMITTED and proved goes to D4. FAILED or UNKNOWN counts one failure, and the writer's 200-tick backoff paces the retry. Same three-failure reason; never reject in-session after a RAM registration. |
| D4 to D7 | Receipt and journal acknowledgements. | Unchanged. The receipt's audit `registry_epoch` is the proving completion's carried epoch, which names exactly one file. |
| Restart rows | The loaded registry is a disk observation. | Unchanged. |
| Gate lock (Q3, Q8 of R49) | HELD while claimed. | Unchanged; the hold lasts at least one registry write longer per deployment. |

W27d1 writes no registry file, so it can resume once the transaction writer is merged (section 7). The W128 design
text changes in one sentence: D0's "one timed checked `checkpoint`" becomes the request and wait above. W27d2 waits
for the registry writer and the barrier conversion.

### 5.2 Satellites and missions

Everything that today assumes the registry is durable when `flush` returns:

| Caller | Today | With this design |
|---|---|---|
| Terminal launch (`SatelliteManager.java:219-223`; extraction at `SatelliteTerminalBlockEntity.java:309-312`) | Barrier, then extraction; a throw keeps the package (UNSUPPORTED_DATA). | RAM launch at m, prompt request, the terminal records a pending launch (watch of X) and answers a new appended code REGISTRY_PENDING. When the acknowledgement proves the watch, the package is extracted if the slot still holds the same package, and the result shows SUCCESS or IDEMPOTENT. An acknowledgement that already proves the watch (a clean replay) extracts at once, as today. |
| Decommission (`SatelliteKindLifecycle.java:41-43`; chip at `SatelliteTerminalBlockEntity.java:350-353`) | Barrier, then the chip is blanked. | RAM removal at m, prompt request, pending removal token. The chip is blanked when an acknowledgement covers m, if the same chip is still there. |
| Data start, claim, cancel (`SatelliteManager.java:264-266,444-446,676-678`) and admin cancel (`:467-469`) | Barrier; the result is shown after the flush. | Retain the original mutation token and RAM result separately; polling never replaces the token with a moving current sequence. The terminal shows REGISTRY_PENDING, then the stored result once covered. No item is locked for a data-only action, but conflicting terminal actions are refused before mutation. The admin command distinguishes its RAM result from durable completion. |
| Discovery (`DiscoveryClaimRecovery.java:36-45`, `SatelliteManager.java:638-650`) | Missions flush, celestial write, missions flush. | `discoverDurably` runs only once an acknowledgement covers the paid receipt's sequence; until then the result is PENDING_DISCOVERY with a request, and the existing replay queue retries. `finishDiscovery` makes a prompt request. A celestial discovery is never durable before its paid receipt (ADR-038). |
| REBIND_CONFLICT bind-back (`ResourceMissionService.java:264-271`) | Barrier, failure tolerated. | Prompt request; its consumers already use the epoch rule. |
| Coalesced flush (`SatelliteManager.java:153-173`) | Synchronous every 100 ticks while pending. | A request at the same cadence. Its timing diagnostic splits server-thread capture time from writer time. |
| Resource missions, ground surveys, classic research (epoch consumers) | Wait for `save_epoch > stamp`. | Unchanged, with the stamp and check split of section 4.3. Waits last about one write longer. |
| Release-test hooks (`releaseTestLaunch`, `releaseTestBatch`, `ReleaseTestCommands`, `machine/classic/runtime/ResearchReleaseCommands.java:58-78`) and GameTests that call `flush` | Synchronous, including research failure injection into a fake target. | Hooks request and the native harness awaits the result/acknowledgement before epoch/FE assertions. Research failure injection uses a same-target policy seam, never binds a live writer to the blocker path. A GameTest-only drain requires a GameTestServer; test/release-only waits are unavailable to a normal running server. No assertion accepts an unacknowledged state. |

**Terminal protection while a launch or decommission is pending.** Without a lock, a player could pull the package out
before the acknowledgement, wait until X is durable, decommission X and relaunch the old package as a fresh X. Today
that needs a crash; asynchronously it would be easy. So:
- A server-lifetime table holds at most one pending action per terminal, keyed by Level and position, and at most 256
  entries, including data-action tokens. A full table answers REGISTRY_BUSY before any registry mutation. The table
  survives unload/reload. Admit only one conflicting action per terminal, and reject conflicting actions for the same
  satellite/package identity at other terminals before changing RAM. The bounded table can supply this check.
- While a launch/decommission entry exists, the package and chip slots refuse insertion and extraction: menu `mayPickup` and `mayPlace`,
  the item handler (simulated and actual), and through it hoppers and droppers. The terminal refuses player breaking
  and is removed from explosion block lists, as the bay does (R-038). `/setblock`, `/fill` and third-party raw
  replacement stay operator residuals.
- At most 32 entries per tick are completed after publication, for loaded terminals only; no chunk is loaded or
  ticketed. An entry stays through FAILED and UNKNOWN and when covered-but-unloaded. Coverage alone does not release
  the lock: release only after the matching inventory effect actually completes (or a data result is delivered).
  A mismatch must retain a diagnostic hold until an explicitly tested reconciliation establishes conservation; it
  must not cancel the entry and release a usable package after registration. Retry and second-terminal paths obey
  the same rule. Shutdown discards without effects as specified in section 4.6.
- At a stop or crash the table is discarded with no effect. After a restart, ADR-049 section 6's replay applies: the
  package replays as IDEMPOTENT, or the chip is inert. Suppression can leave registration saved with the package still
  in the chunk even after a normal stop: this expands the occurrence of ADR-049's existing restart window, not just
  its crash occurrence. Tests must cover ordinary shutdown replay as well as kills.

### 5.3 R-039 and R-040, and a new entry

- **R-039** (bay receipts and the W27 registry proof). D3's proof becomes an asynchronous completion, so INTENT lasts
  at least one registry write longer. That is about 0.5 s plus the cadence at the bound; on a failing disk it lasts
  until a commit or the end of the session. The receipt's `registry_epoch` is the proving completion's epoch, and the
  restart rows are unchanged. The receipt writer itself is unchanged. The passenger, loader and warp effects that
  R-039 defers to W27d inherit the longer hold.
- **R-040** (transaction journal). The sentence "the synchronous write cost at the maximum size is not measured" is
  superseded by W128's measurement (about 4.1 s at the bound) and by this design's writer. The entry gains:
  - WRITE_PENDING in B14;
  - D0's request and wait;
  - an `Error` on the writer thread, recorded as UNKNOWN;
  - the stop drain and crash path of section 4.6;
  - the transient heap of a 64 MiB write, overlapped with the existing transfer writer before W129a merges;
  - new once-per-streak write-failure logging; W123/R-040 has no existing ordinary failure-log loop to preserve.

  Unreadable and blocked handling, the sticky UNKNOWN and the no-overwrite rule are unchanged.
- **New entry** (all worlds, not only the bay). The registry's writes become asynchronous. The entry should record:
  - the pending terminal actions and their protection (section 5.2);
  - discovery that waits for proof;
  - the operator meaning of `/save-all flush`;
  - final-target drain exhaustion, the single retry budget, watchdog interruption and potentially unbounded IO join;
  - covered-but-unloaded and mismatched terminal holds, ordinary-shutdown replay and multi-file backup limitations;
  - finite-epoch capacity refusal/exhaustion, retained data and the separately reviewed recovery requirement;
  - logging: one ERROR at the first failed or unknown outcome of a streak and one INFO when a later write commits, as
    the once-per-streak reporting does today; one ERROR per unexpected writer `Throwable`; one WARN when a stop drain
    gives up (with target/committed sequence and reason); one capacity diagnostic per exhaustion streak, not per
    rejected poll; no total cap, like the other writer entries;
  - recovery: repair storage, then a covering commit plus loaded-terminal reconciliation can complete the effect;
    a restart drops RAM waits and replay handles items. Epoch exhaustion is not repaired merely by fixing storage.

  The scope is one registry per world (terminal locks affect individual terminals). A wait lasts until its effect
  completes or the session ends; capacity exhaustion can persist across restart. Root writes these entries and the
  ADR supersession pointers before the affected slices merge; this note proposes them only.

### 5.4 Other effects

- **Heap.** Serialized NBT size is not a heap bound. Count resident and pinned old/new domain graphs, candidate NBT,
  staging decode trees, failure readback, compressed arrays, `ByteArrayOutputStream` growth/`toByteArray()` copies,
  and GC headroom. W11b2's 1.94 GB sampled peak for its 134 MB journal proves no spare capacity for another writer.
  B3a measures transaction/transfer overlap with resident roots before W129a merges; B3 measures all three large
  writers before W129b/c merge. Both run under Xmx4G. If needed, use one server-lifetime fair IO-side write permit,
  including the existing transfer writer in its scope. Never acquire it while blocking the server thread; remeasure
  with the permit, without waiving budgets. Existing writers otherwise remain unchanged.
- **CPU.** At the bound a registry write uses about 0.4 s of one core, at most once per 100 ticks for coalesced
  changes, plus barriers. A transaction write uses about 4 s at its bound, but only on autosave and D0. B3 also
  measures server-thread tick percentiles while these writes run, which catches GC pauses.
- **Class sizes.** `SatelliteMissionSavedData` has 479 lines. The writer state belongs in `SatelliteRegistryWriter`
  (159 lines) and a new capture and encoder class, so that no class passes 500 lines.
- **ADR texts.** If accepted, this design supersedes ADR-050 section 2's statement that barrier flushes "behave as
  today", and ADR-049 sections 6 and 7's "barrier flush before extraction or blanking", in their mechanism; the
  durability order itself is kept. It also supersedes the W27c3 note's "writes stay synchronous" in ADR-065. Those
  ADRs gain pointer notes when ADR-074 is accepted.

## 6. Tests and benchmarks that would prove the implementation

### 6.1 Benchmarks first, under W128's stop rule

These run as standalone `main` classes under `src/test`, with Xmx4G, under the heavy-JVM lock, on W128's fixtures.
W128's synchronous benchmark classes stay as the baseline. Each server-thread figure is held to docs/17: P95 at most
50 ms and P99 at most 100 ms. If any figure misses, the slice stops and reports, as W128 did.

| ID | Measured on the calling (server) thread | Also reported |
|---|---|---|
| B1 | Registry: request/capture for the 32-registration checkpoint, coalesced flush and autosave at W128's byte-bound root, plus maximum-reference-count compositions and maximum `epochAdvanced` publication work. Do not infer those costs from the 5,226-mission byte fixture. | Writer-thread time per write; peak heap; composition/counts and publication cost separately. |
| B2 | Transaction journal: the request and publication with one record and with 256 records at the 64 MiB bound. | Writer time (about 4.1 s expected); peak heap. |
| B3a (before W129a merge) | Paced server-tick surrogate or real ticks during the new transaction/transfer writer overlap, with resident registry, journal and other live roots, plus existing writer traffic; at least 50 rounds at the write bounds. | Peak heap/RSS under Xmx4G, retained/pinned graphs, allocation, GC pauses/stalls, missed deadlines, writer CPU and latency; success and failure/readback paths. |
| B3 (before either W129b or W129c merge) | Same paced workload with registry, transaction and transfer writers concurrently at their bounds for at least 50 rounds, with resident roots and ordinary mutation/publication work. | The same memory, tick-deadline and GC observations as B3a; no OOM/watchdog and docs/17 budgets unchanged. |

The surrogate uses a monotonic 50 ms schedule, not a tight loop: record intended deadline, actual wake/start,
completion, lateness and work duration for every tick. Record skipped/missed deadlines rather than dropping them or
diluting them with immediate empty catch-up iterations. Report duration and schedule-lateness percentiles alongside
GC pause count/maximum/cumulative stalls, including intervals during allocation failures. Hardware, JVM flags,
collector, fixture sizes and resident-root composition belong in the report. Missing measurements or a budget miss
stop the slice before merge; Root chooses a remedy, and the changed configuration must be measured again. B3a does
not stand in for B3, and neither development-host result replaces reference-node acceptance.

### 6.2 Unit tests

- **Writer core** (executor seam):
  - at most one capture;
  - a pending request while in flight leads to a fresh capture at the next start;
  - publication only on the owner thread; a foreign completion is refused;
  - an `Error` gives UNKNOWN; the backoff and the 10-tick cadence;
  - both roots: capture s, logout/state or clock mutation s + 1, older COMMITTED/UNKNOWN/FAILED (NOT_COMMITTED for
    the journal), then a fresh final capture; older success leaves dirty/pending and cannot finish the drain;
  - one three-attempt budget includes the in-flight attempt across repeated `save`/`close`, target changes and
    exception cleanup; exhaustion reports F and last committed sequence without resetting or claiming success;
  - shutdown suppresses effects for both flag combinations, including running=true/stopped=true;
  - close/abort join before world ownership release, including an interrupted join and stalled-IO seam; abort
    publishes nothing; a request after close is refused. Tests release their own stall seam, not production timeouts.
- **The W119 no-mutation matrices unchanged.** `SatelliteRegistryEpochTest`, `SatelliteRegistryProofTest`,
  `SatelliteRegistryDirectoryTest`, `SatelliteRegistryFenceTest`, `BayRegistryOrderingTest` and
  `DiscoveryClaimRecoveryTest` keep their no-mutation outcomes, epochs, stamps and flags. The named proof test at
  lines 120-137 is the explicit semantic transition of section 4.1/ADR-074 item 5, implemented in a separate cited
  commit with the owner-thread mutation behind paused IO. No safety assertion is removed or weakened.
- **New epoch cases.** Cases E to H of section 4.1. Every site of section 4.3 gets a stamp-or-check test, including
  the split of `ResourceMissions.java:290`. The encoder of a capture is byte-identical to `save(CompoundTag)` on
  fixtures, including the 16 MiB fixture.
  MAX - 1/MAX fixtures cover launch/resource/ground start, acknowledgement, ground claim and FAILED/UNKNOWN:
  admission before capture MAX, refusal during that flight, no stamp MAX admitted, explicit exhaustion after UNKNOWN
  reserves MAX, no wraparound or infinite pending retry, and unchanged RAM/items/FE on refusal. Clock-only and
  unchanged-load encodings retain E at the limit. Verify each guard before mutation, not just at encode time.
- **Nested immutability.** W129b's `TerminalLocation` constructor/accessor/capture test uses a supplied mutable
  position; collection and tag alias tests cover both captured graphs from section 2.1.
- **The W123 matrix unchanged.** `RocketTransactionProofTest` keeps every case. New cases: WRITE_PENDING masking a
  stale NOT_COMMITTED or UNKNOWN; a mutation during the flight keeps the journal dirty; sticky UNKNOWN across a later
  pre-replacement failure; a refused request on an unreadable or blocked journal.
- **Callers.**
  - Terminal launch and decommission: same item, swapped item, removed item (refused), block entity reloaded within
    the session, menu swaps, breaking/explosions refused, a full table answers BUSY with no mutation, FAILED/UNKNOWN
    retain locks, covered-but-unloaded retains the entry, and mismatch/second-terminal/conflicting-button/retry paths
    never release a usable package without conservation. Release happens after the effect, not just its proof.
  - Discovery: no celestial write before the proof covers the paid receipt.
  - Data actions: a stable original token, stored RAM result, pending presentation then durable result, conflicting
    actions refused before mutation; appended ordinals/EN/zh text and bounded unknown-code handling.
  - `ResearchReleaseCommands`: same-target failure policy, awaited epoch/FE checks, no live-target rebinding and no
    test-only blocking wait on a normal running server. D0 churn retains HELD/tickets and never accepts stale proof.
  - Every class above is run alone once (WORKER-RULES).

### 6.3 GameTests

The real server's file and lifecycle:
- autosave and `/save-all` make requests and publish on the next ticks;
- the coalesced flush cadence;
- `SatelliteRegistryProofGameTests` and `RocketTransactionProofGameTests` with awaited publication;
- terminal launch and decommission end to end, with a hopper attempt refused while pending;
- the bay D0 and D3 waits once W27d uses them.

Each ERROR or WARN logged on purpose gets its exact expectation in `scripts/gametest_expected_log.json`.

### 6.4 Native crash cuts (forced stop or kill, two restarts each)

| Cut | Expected after restart |
|---|---|
| R1 registry capture taken, IO not started | The previous file; RAM changes lost; no dependent effect ran. |
| R2 staged, before the move | The previous file; the staging file is replaced next time. |
| R3 moved, before publication | The new file; the terminal package is still present and replays as IDEMPOTENT; no duplicate satellite. |
| R4 published, before the terminal effect | As R3. |
| R5 a mutation during the flight, then commit, then kill | The mutation is absent from the file and from every reloaded stamp check. |
| R6 normal stop with dirty roots and a stale capture active before logout mutations | If the final-target drain succeeds, both files include the final sequences. Verify pending/dirty survival across the older success and ordinary-shutdown terminal replay without effects. |
| R7 thrown tick exception, dirty roots, running=true/stopped=true, no `ServerStoppingEvent` | Verify shutdown detection, suppressed effects and final-target coverage if teardown completes. Include stale/failed/UNKNOWN attempts and budget exhaustion on both roots. |
| R7 interrupted teardown, including watchdog termination while those flags hold | Do not require final RAM persistence: verify the last committed-or-unknown whole-file S2 recovery, no dependent effect without acknowledgement, and two restarts without duplication. Keep this distinct from successful R7 drain evidence. |
| T1 D0 request in flight, then kill | No proof in the new session; the restart rows of W27A apply. |
| T2 and T3 journal staged, and moved before publication | The previous or the new file; B14 starts from WRITE_REQUIRED. |
| Discovery: kill between the registry commit and the celestial write | The replay finishes the discovery once; the paid receipt is never lost. |

Every case checks package and satellite conservation, no duplicate satellite, and discovery never ahead of its paid
receipt. Root reruns the bay cuts that involve D3 (B9, B11 to B13) with the asynchronous proof when W27d2 lands.

### 6.5 Regression

The whole unit suite, one full GameTest with the unchanged log checker, `runData` for the new language entries
(REGISTRY_PENDING and REGISTRY_BUSY, and WRITE_PENDING if the bay status shows it), and the native cases above. This
is development evidence, not reference-node acceptance: docs/17's reference node remains open, as W128 noted.

## 7. Proposed slices

Each slice is a persistence slice: a one-page design note before code, and a read-only review before merge.

1. **W129a, the writer core and the transaction journal.** The shared core, the journal's capture, encoder and
   publication, WRITE_PENDING, lifecycle/final-target drain tests, B2 and **B3a before merge**, the W123 matrix awaited,
   and cuts T1 to T3 plus the journal's R6/R7 variants. Only after its overlap-memory/tick gate and review does its
   merge unblock W27d1's D0.
2. **W129b, the registry writer.** The capture and encoder, the snapshot epoch and rules 1 to 7, the site split of
   section 4.3 including finite-long admission, `TerminalLocation` freezing and its alias test, `requestCheckpoint`
   and removal tokens, B1 and **B3 before merge**, the unchanged no-mutation matrices plus the named R3 test migration,
   and cuts R1, R2, R5 to R7. The barriers keep working in this slice through a temporary owner-thread await. That
   await is development-only and never merged to production: W129c replaces it before merge, or W129b and W129c
   merge together.
3. **W129c, the barrier callers.** The terminal pending table and protection, discovery, data actions, rebind,
   release-test hooks including research's same-target seam, operator backup documentation, language entries,
   GameTests, and cuts R3, R4 and discovery. **B3 must pass before this merge too**, on the combined configuration.

W27d1 resumes after W129a. W27d2 starts after W129b and W129c and this ADR's acceptance.

## 8. Q1 to Q8 review recommendations adopted for Root's decision

All eight recommendations are adopted in this proposed revision; none is claimed accepted by Root here.

| Question | Adopted disposition |
|---|---|
| Q1 | Pending then durable success, with the original token fixed, the RAM result stored separately and one conflicting action per terminal (section 5.2). |
| Q2 | Append REGISTRY_PENDING and REGISTRY_BUSY, preserving ordinals; EN/zh text, non-error pending presentation and bounded unknown-code handling. Expose WRITE_PENDING where the bay shows it (sections 5.2, 6.2). |
| Q3 | Nonblocking `/save-all flush` with explicit operator documentation of individual-file versus multi-file consistency; recommend a successfully drained stopped-world backup (section 4.5). |
| Q4 | Conditional FAILED reservation, with finite-long admission and the explicitly reviewed proof-test transition (sections 4.1, 4.3). |
| Q5 | Initial 10/200 cadence on every start path; repeated requests cannot reset backoff. Separate monotonic shutdown clock; no wall-time deadline claim (sections 3, 4.6). |
| Q6 | Root should queue a separate measured ground-preflight leaf now: START, account creation and restored-over-budget fallback; no reduced bounds or claim that W129 removes it (section 2.4). W129R cannot edit the queue. |
| Q7 | No per-rocket B14 redesign. Retain exact whole-journal currency and indefinite churn risk; test HELD/ticket behavior, never accept stale acknowledgement (sections 4.4, 6.2). |
| Q8 | No assumption either of safe overlap or required serialization. B3a gates W129a; B3 gates W129b/c. On a miss, stop and measure a fair server-lifetime IO-side permit if needed, including transfer; never block the owner thread on that permit (sections 5.4, 6.1, 7). |

## 9. Not in this task

Code, tests, benchmarks, W27d, the ADR's acceptance, the risk register, status files and the ledger. This note and
the proposed ADR record no Gate result and do not accept any risk.
