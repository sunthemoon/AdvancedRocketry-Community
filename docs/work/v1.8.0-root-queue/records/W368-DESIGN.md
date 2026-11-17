# W368 design: X01 and X02 by crash and fault simulation at the checked-writer seam

| Item | Value |
|---|---|
| Status | **DESIGN, for Root's review.** Nothing is implemented. Author: a Claude worker session, 2026-11-17. |
| Task | [TASK W368](../TASK-W368-X01-X02-SIMULATION-DESIGN.md), under Root decisions R457 and R460 ([OWNER-DECISIONS-20261117](OWNER-DECISIONS-20261117.md)). |
| Background | [X01-X03-ISOLATION-PLAN](X01-X03-ISOLATION-PLAN.md), option E4 and sections 3 and 4; rows X01 and X02 of [W326-MATRIX](W326-MATRIX.md). |
| Base | Shared head `daba8849e`. Every code reference below is to that commit. |
| Review | The harness adds a gated hook to the saved-data write path, so AGENTS.md section 13 asks for a read-only review by another session before the first implementation slice merges. |

Terms used below:

- **Seam store**: a file written by `CheckedSavedDataFile.replace`.
- **Write**: one call of `replace`, from opening the staging file to the return of the committer.
- **Kill image**: the world directory after the Java process was stopped by force at a cut, as the existing native
  cases already produce it.
- **Old** and **new**: the target file's content before and after one write.

## 0. What the code does today

Section 0 is the finding that shapes the design. Sections 1 to 7 answer the task's seven points in order, and section 8
lists the questions for Root.

### 0.1 The write protocol

`persistence/migration/CheckedSavedDataFile.replace` (lines 33 to 74) performs every checked write in the same order:

1. It checks that every ancestor is a real directory, and that the target and the staging path are regular files or
   absent.
2. It opens `<name>.dat.arce-pending` with `CREATE`, `TRUNCATE_EXISTING` and `WRITE`.
3. It writes the encoded bytes in a loop.
4. It calls `force(true)` on the staging channel.
5. It closes the channel.
6. It reads the staging file back and compares it with the candidate.
7. It checks the target again and calls `committer.commit(staged, target)`. Every committer ends in
   `CheckedSavedDataFile.atomicMove`, which is `Files.move` with `ATOMIC_MOVE` and `REPLACE_EXISTING`.
8. On any exception it deletes the staging file and rethrows.

After `replace` returns, the asynchronous writers call `CheckedSavedDataPolicy.forceDirectory` on the parent directory.
That method returns at once on Windows (`CheckedSavedDataPolicy.java` line 40). On an exception after the committer was
entered, each writer reads the target back and classifies the attempt as COMMITTED, FAILED_BEFORE_REPLACE (or
NOT_COMMITTED) or UNKNOWN, and it retries an UNKNOWN candidate with exactly the same bytes (`EscrowLedgerWriter`
lines 173 to 191, `BayReceiptWriter` lines 142 to 158, `CheckedJournalCheckpoint.write`).

### 0.2 The committer seam sees only step 7

`CheckedSavedDataFile.Committer` receives the two paths at step 7. The existing cuts (`BayReceiptCommitCut`,
`SatelliteRegistryWriterCuts.policy`, `RocketJournalFailurePolicy`, the escrow ledger's `checkpoint` labels K1, K2, K8,
K9, BASELINE_STAGED and BASELINE_REPLACED) all act there: before the move (STAGED) or after it (MOVED). Steps 2 to 6
use a `FileChannel` that `replace` opens itself, and `forceDirectory` is a static call. No committer can record them or
make them fail.

R457 describes the recorder as working "through the existing committer seam". That is enough to rebuild the crash
states of X01, because steps 2 to 6 are fixed and the committer can read the complete staged bytes and the old target.
It is not enough for X02: a write error, a short write, a failed flush and a failed close cannot be injected from a
committer. The design therefore adds one small, gated file-operations seam inside `CheckedSavedDataFile` and uses it for
both rows (section 2.1, and question Q1).

### 0.3 Which stores use the seam

| Store | Path to the seam | Covered |
|---|---|---|
| `RESOURCE_ESCROW` (escrow ledger) | `EscrowLedgerWriter` builds a `CheckedSavedDataPolicy` for each attempt | Yes |
| `BAY_RECEIPTS` | `BayReceiptWriter.write`, with `BayReceiptCommitCut` as committer | Yes |
| `ROCKET_TRANSFERS` (transfer journal) | `RocketTransferSavedData` through `CheckedJournalCheckpoint.write` and `RocketJournalFailurePolicy.checked` | Yes |
| `SATELLITE_MISSIONS` (registry) | `SatelliteRegistryWriter`, policy `CheckedSavedDataPolicy.atomic` wrapped by `SatelliteRegistryWriterCuts.policy` | Yes |
| `ROCKET_TRANSACTIONS`, `ROCKET_REJOINS` | Their own checked writers over `CheckedSavedDataPolicy` | Yes |
| `CELESTIAL`, `ENDGAME`, `CLASSIC_PROGRESSION` | `AtomicSavedData.flush`, with the gated `committerSelector` | Yes |
| `STATIONS` | `StationCheckedWriter` for checked updates and for the first write of an absent root. Later ordinary saves are an in-place `NbtIo.writeCompressed` (`StationOrdinaryWriter.inPlace`). | Partly |
| Planetary bindings | `PlanetaryBindingStore` has its own staging, force and move code and its own `Committer` type | No |
| Migration backups | `WorldDataMigrationService` lines 429 to 440: its own `Files.write`, `force` and `Files.move` | No |
| `BeaconIndexSavedData`, `LandingPadIndex`, `StationNoticeSavedData`, `FieldCleanupSavedData` | Plain `SavedData`; vanilla writes the file in place | No |
| Chunk and entity region files, block entities, rocket entities | Vanilla `IOWorker` and region files. This includes the escrow barrier's own stores: `EscrowChunkStore` submits `worker.store` and `EscrowBarrier.forceBatch` calls `worker.synchronize(true)`. | No |
| Player files, `level.dat`, POI, configuration files | Vanilla or Forge | No |

The cargo protocol therefore crosses the seam only at its ledger writes (L1 at K1 and K2, L2 at K8 and K9) and at the
transfer journal. Its C2 mutations, the barrier's stores and force, and the C6 readback are region-file operations
outside the seam. Section 3 says how the cargo cases treat them, and section 5 states the residual.

## 1. The durability model

### 1.1 What a crash keeps after each operation

"Process stop" is what the existing native cuts test: the operating system keeps everything it accepted. "Power loss"
is the conservative rule set that the replay uses. The staging file is P and the target is T.

| The crash comes after | Process stop keeps | Power loss may leave |
|---|---|---|
| Open and truncate of P | P empty, T old | P absent or empty; T old |
| Write to P, before the flush | P complete, T old | P absent, empty, any prefix of the bytes, or the full length with a block of zeros or stale bytes in it; T old |
| `force(true)` returned | P complete, T old | P complete, or P absent because its new directory entry was not yet durable; T old |
| Close | The same as after the flush | The same as after the flush. A close adds no durability. |
| Atomic move returned | T new, P absent | Either T new and P absent, or the move undone: T old with P complete or absent. Never a T that mixes old and new bytes, because P's data was flushed before the move. |
| Directory flush returned (not on Windows) | T new | T new. Every earlier move in that directory is durable. |
| Any later moment, where no directory flush ran | T new | The move may still be undone, although the writer has acknowledged it and dependent effects have run (rule R6) |

### 1.2 The rule set

- **R1.** Bytes written and not flushed are volatile. After a power loss the file may hold none of them, a prefix, or
  a torn image.
- **R2.** `force(true)` makes the file's content and length durable up to that call. It does not make the file's
  directory entry durable.
- **R3.** A close makes nothing durable.
- **R4.** An atomic move is atomic but volatile. After a power loss the name is bound to the old content or to the new
  content, never to a mixture.
- **R5.** A directory flush that really runs makes every earlier move in that directory durable.
- **R6.** Where no directory flush runs, a move is never known to be durable while the server runs. At any later crash
  each seam store may independently be at an earlier content.
- **R7.** A file outside the seam is taken as it is in the kill image. Its own power-loss states are not modelled
  (section 5).
- **R8.** A flush that reports success is believed in X01. A flush that lies is X02's subject (section 4).

R1 to R5 are the model the task asks for. R6 is what the model implies for this PC. R7 is the limit of the seam.

### 1.3 What Windows does differently

1. **No directory flush.** `CheckedSavedDataPolicy.forceDirectory` does nothing on Windows, because Java cannot open a
   directory as a `FileChannel` there. No step of the protocol makes a move durable, so R6 applies to every
   acknowledged write. W11b2 and W129 record this residual ([W11B2](W11B2.md), [W129-DESIGN](W129-DESIGN.md): "Windows
   has no directory force, so power loss stays outside S2").
2. **The move.** `ATOMIC_MOVE` with `REPLACE_EXISTING` is a rename that replaces the target. NTFS journals the rename,
   so it is atomic across a crash, but the journal reaches the disk later. This statement comes from general knowledge
   of the JDK and NTFS and was not verified in this task. The model does not depend on it: R4 and R6 hold either way.
3. **Sharing violations.** On Windows a move or a delete fails when another process holds the target or the staging
   file open (an antivirus scanner, a backup tool, an indexer). This is an observable failure of the move and of the
   staging cleanup that does not happen on Linux. X02 injects it (section 4, faults F4 and F8).
4. **The flush.** `force(true)` asks the device to empty its cache. A device or driver that ignores the request is
   outside any application-level test (section 5).
5. **Torn tails.** NTFS is less prone than ext4 to leave a renamed file with zero length. The model keeps that state
   all the same, because servers also run on other file systems.

## 2. The recorder and the replay (X01)

### 2.1 The file-operations seam

`CheckedSavedDataFile` gains one package-private interface, here called `FileOps`, with a static volatile field that
defaults to the constant `DIRECT`:

| Method | `DIRECT` does | Called from |
|---|---|---|
| `open(pending)` | The same `FileChannel.open` call as today | `replace`, step 2 |
| `write(channel, buffer)` | `channel.write(buffer)` | Step 3 |
| `force(channel)` | `channel.force(true)` | Step 4 |
| `close(channel)` | `channel.close()` | Step 5 |
| `readStaged(pending, type)` | `BoundedSavedDataIo.read` | Step 6 |
| `move(staged, target)` | The same `Files.move` call as today | `atomicMove`, which every committer reaches |
| `forceDirectory(directory)` | Today's body, including the Windows return | `CheckedSavedDataPolicy.forceDirectory` |
| `delete(pending)` | `Files.deleteIfExists` | The cleanup at step 8 |

`DIRECT` is the code that exists now, moved into one object. The order of operations, the exceptions and the bytes do
not change. Routing `atomicMove` and `forceDirectory` through the same seam records the move and the directory flush
of every store in section 0.3's "Yes" rows without touching any writer or any existing cut: the cuts keep wrapping the
committer, and their pause and failure points stay where they are.

### 2.2 What the recorder writes

The recorder is a `FileOps` that calls `DIRECT` and appends one line for each operation to `ops.jsonl` in a trace
directory beside the world, never inside it. A line holds:

- a sequence number from one process-wide counter, which gives the order across all stores and threads;
- the thread name, the store type and the operation;
- the path relative to the world;
- the result: `ok`, or the class of the exception thrown;
- for a write: the offset, the length and the SHA-256 of the bytes.

It also keeps the bytes needed to rebuild any prefix:

- the bytes of every write call, stored once per SHA-256 under `blobs/`;
- at each `open`, a copy of the current target under `pre/<sequence>`, or a marker that the target was absent. This is
  the write's old content.

Each line is written with one unbuffered call, so a forced stop of the process loses at most the line of the operation
in flight. A line that is missing because of the kill is the definition of "the crash came before that operation
returned".

The recorder never changes a write's outcome. If it cannot write its trace, it logs
`ARCE_RELEASE_STORAGE_TRACE_INVALID` once, stops recording, and lets the write continue; the harness then discards the
case. It refuses to record more than 4,096 operations or 256 MiB of blobs in one run, with the same marker.

### 2.3 The crash prefixes of one write

One write has six recorded operations before it returns (open, write, flush, close, move, directory flush), so seven
crash prefixes. Rules R1 to R5 give each prefix its loss variants (section 1.1). Many of them are the same files. The
replay works on distinct states, identified by the SHA-256 of the target and of the staging file:

| State | Target | Staging file | Produced by |
|---|---|---|---|
| S-OLD | Old | Absent | Before the open; a lost directory entry; a move undone with the entry lost |
| S-EMPTY | Old | Empty | After the open; unflushed writes all lost |
| S-PREFIX | Old | The first half of the bytes | Unflushed writes partly lost |
| S-TORN | Old | Full length, with the last 4,096 bytes zeroed | A torn unflushed write |
| S-STAGED | Old | Complete | After the flush or the close; a move undone |
| S-NEW | New | Absent | After the move |

That is at most six states for one write. For a first creation the "old" target is absent, which gives the same six.
The state table records which prefixes and variants map to each state, so the report can still give a row for each
crash point.

### 2.4 Building the copies

Each case is one existing native cut that pauses its writer after the move and before the acknowledgement (a MOVED,
K2, K9 or BASELINE_REPLACED point). The harness runs the existing runner for that case with the recorder switched on,
and stops the process by force at the pause, exactly as the runner does today. It then builds two groups of copies of
the kill image.

- **In-flight states.** The write held at the cut is rewound to each state of section 2.3. Nothing has depended on
  that write yet, because its acknowledgement was never published. Every such state is therefore a state that a crash
  during a slower write could leave, and the stores that other threads wrote in the meantime are independent of it.
  If another seam write is also unfinished at the kill, it gets its own six states with the first write left as
  killed. The cross product is not built.
- **Late-loss states (rule R6, the Windows model).** For each seam store that completed a write inside the recorded
  window before the cut, the store is put back to its content at the window's start, alone and in every combination
  with the others. With at most three stores in a window that is at most seven states. Intermediate generations are
  not built; the report says so.

The builder is a pure function of the trace and the kill image. It writes a `states.json` that lists, for each state,
its derivation and the SHA-256 of every seam file, before any server starts. A copy is built on D:, started twice,
checked, and deleted before the next one is built.

### 2.5 Bounds

| Bound | Value |
|---|---|
| States for one unfinished write | 6 |
| Late-loss states for one case | 7 |
| States for one case | 16. A case that would need more stops with `OVER_BOUND` and is reported to Root; the harness does not sample silently. |
| Cases | 12 (section 3) |
| Copies in the whole run | At most 192, so at most 384 starts |

## 3. The cases

Each row records one cut that pauses after the move. "Old side" names the existing case whose expected result an
S-OLD, S-EMPTY, S-PREFIX, S-TORN or S-STAGED copy must meet, and the recorded case's own expected result is what an
S-NEW copy must meet. Runner aliases: B = `scripts/run_v180_bay_forced_stop.py`, F = `scripts/run_v180_cargo_cuts.py`,
S = `scripts/run_v180_cargo_s4_s5_cuts.py`, I = `scripts/run_v180_item_cuts.py`,
R = `scripts/run_v180_registry_writer_cuts.py`.

| Case | Store and write | Recorded cut | Old side | Late-loss stores in the window |
|---|---|---|---|---|
| X01-01 | Bay receipts, D1 (INTENT) | B: `B1_MOVED` | B: `B1_STAGED` | None |
| X01-02 | Bay receipts, D4 (REGISTERED) | B: `B3_MOVED` | B: `B3_STAGED` | Receipts (D1), registry |
| X01-03 | Bay receipts, D7 (guarded removal) | B: `B8_MOVED` | B: `B8_STAGED` | Receipts (D4), registry |
| X01-04 | Ledger L1, fluid load | F: `FC_LOAD_L1_REPLACED` | F: `FC_LOAD_L1_BEFORE` | None |
| X01-05 | Ledger L2, fluid load | F: `FC_LOAD_L2_REPLACED` | F: `FC_LOAD_K8` | Ledger (L1) |
| X01-06 | Ledger L2, fluid unload | F: `FC_UNLOAD_L2_REPLACED` | F: `FC_UNLOAD_K8` | Ledger (L1) |
| X01-07 | Fluid load between the barrier and L2 | F: `FC_LOAD_C6_JOINT_READBACK` | None: no write is in flight | Ledger (L1) |
| X01-08 | Ledger L1, item load | I: `IC_LOAD_K2` | A new expectation (see below) | None |
| X01-09 | Ledger L2, item unload | I: `IC_UNLOAD_K9` | A new expectation (see below) | Ledger (L1) |
| X01-10 | Ledger first creation (N1) | S: `FF_N1` | The fixture before creation: no ledger file | None |
| X01-11 | Transfer journal replacement | S: `FS_O3J_AFTER` | S: `FS_O3J_BEFORE` | Ledger, journal |
| X01-12 | Satellite registry | R: `R3` | The runner's expected durable count for a staged-only write | None |

Notes on the table:

- The item runner has no staged-side case for K1 and K8. The old-side expectation for X01-08 and X01-09 is the
  conservation total of the fixture with the ledger at its earlier generation. It is added as a new expectation and
  reviewed in slice S2. It adds an oracle; it relaxes none.
- I did not read runner R's internals. The old-side expectation for X01-12 has to be confirmed in slice S2.
- X01-07 exists for the cargo barrier. Its only seam states are late-loss states. The barrier's own region-file writes
  stay as they are in the kill image (R7).

**Starts and oracles.** Every copy is started twice with the candidate, and each start runs the runner's existing
restart check unchanged. A copy passes when both starts meet one of these outcomes:

1. each store is wholly old or wholly new, and the matching side's existing oracle passes;
2. no rocket, fluid or item is duplicated or lost, by the runner's conservation totals;
3. every protected root is byte-identical to the kill image, or to the rewound content;
4. the server holds, or reports REPAIR, in a way the design names. The allowed markers are listed for each case in
   `states.json` before the run (for example `ARCE_ESCROW_BASELINE_UNAVAILABLE`, a FLOOR_CONFLICT hold, or a refused
   start with a migration diagnostic).

A leftover staging file must change nothing: a copy in S-EMPTY, S-PREFIX, S-TORN or S-STAGED must give the same result
as S-OLD, and the next write must replace the leftover. Whether start-up reads or removes a leftover
`.arce-pending` file was not checked in this task; the replay checks it.

Any other result is a FAIL and a defect to analyse. No oracle is relaxed. A FAIL in a late-loss state is reported
against the Windows directory-flush residual that W11b2 and W129 already state (question Q2).

## 4. The fault injector (X02)

The injector is a second `FileOps`, plus a wrapper for the existing `CheckedSavedDataPolicy.Reader` seam. It is chosen
by one property of the form `<store>:<fault>:<count>`, and it acts only on the named store type. A count of 1 is a
single fault; a count of 4 lasts longer than the writers' three-failure limit and is then lifted, which shows the hold
and the recovery. Faults that only a power loss shows are built offline by the replay builder of section 2.4.

| ID | Fault | How it is injected | What is checked |
|---|---|---|---|
| F1 | I/O error on write | `write` throws `IOException` on the first call | The attempt ends FAILED_BEFORE_REPLACE (journal: NOT_COMMITTED). The target is byte-identical. The staging file is deleted. No dependent effect runs. With count 4 the writer reports unavailable and refuses admission, then recovers. |
| F2 | Full disk | `write` accepts half the bytes and then throws `IOException` ("not enough space"); a variant throws at `open` | As F1. The half-written staging file never reaches the target. After the fault is lifted, the next attempt commits. |
| F3 | Failed flush | `force` throws | As F1. The committer is never entered. |
| F4 | Failed move | `move` throws before the rename (also as `AccessDeniedException`, the Windows sharing violation); a variant renames and then throws | Before: FAILED_BEFORE_REPLACE or UNKNOWN as the writer defines, target old. After: UNKNOWN, then the writer's readback finds the candidate and resolves COMMITTED. The retry uses the same bytes. |
| F5 | Failed close | `close` throws after a successful flush | The attempt fails before the committer. The target is old. The staging file is deleted or harmlessly left. |
| F6 | Write reports success but is lost, seen in the session | `write` drops the bytes and reports success; the staging file is short or zeroed | The staging readback at step 6 differs, and the attempt fails before the committer. |
| F7 | Write or flush reports success but is lost at power-off | Offline, on a cleanly stopped world after an acknowledged write: (a) the store is put back to its earlier generation; (b) the target keeps its name and has zero length; (c) the target has a zeroed tail | Start-up twice. (a): do the cross-store checks catch the stale store (a ledger behind the journal, a receipt store behind the registry)? (b) and (c): does the bounded reader refuse the file, and does the server hold instead of creating a fresh root? |
| F8 | Failed staging cleanup | `delete` throws after F1 or F3 | The original failure is still reported. The leftover staging file is replaced by the next write. |
| F9 | Corrupted read, in the session | The `Reader` wrapper throws, or returns a different root, on the readback after an UNKNOWN move | The outcome stays UNKNOWN, and the exact candidate is retried. It is never taken as COMMITTED or as FAILED_BEFORE_REPLACE. |
| F10 | Corrupted read, at rest | Offline: one byte flipped in the middle of the target of a stopped world; a variant truncates the file by half | Start-up twice. The reader refuses the file. The server holds and does not replace the root with an empty one. The file is byte-identical afterwards. |

The task's seven faults map to F1 (write error), F6 and F7 (lost write), F3 (flush), F4 (move), F5 (close), F2 (full
disk), and F9 and F10 (corrupted read). F8 is added because Windows makes it likely.

**Stores.** Each fault runs against the ledger, the bay receipts, the transfer journal and the satellite registry.
The other seam stores share `replace` but have their own writer reactions; they are not run (question Q5). That is
8 live faults and 2 offline faults on 4 stores: with the variants, about 40 live and about 20 offline cases. A live case is one run with the fault
and two restarts after the fault is lifted; an offline case is two starts.

**Classification.** Each case gets exactly one class:

| Class | Meaning |
|---|---|
| SEEN_HELD | The running server saw the fault and held, refused or retried as designed |
| CAUGHT_LATER | The running server did not see it; a later readback, a cross-store check or the next start caught it, and the server held or repaired as designed |
| NOT_CAUGHT | Nothing caught it: a stale, lost or damaged store was accepted |

A NOT_CAUGHT result is written into the report as a stated residual with its reach (which store, what the player could
lose or gain). It is never made to pass by changing an oracle. Whether to fix it is a separate decision. F1 to F6, F8
and F9 are expected to be SEEN_HELD by reading the code; F7 and F10 are the cases the row is open for.

## 5. What stays uncovered

| Not covered | Why | Where it would be tested |
|---|---|---|
| The real OS page cache and its write-back order | The model replaces it with rules R1 to R6 | A Linux virtual machine with `dm-log-writes` (the plan's option E1 or E2, Method A) |
| File-system journals (NTFS, ext4, xfs) and their recovery | The replay builds files directly | The same VM for ext4 and xfs; a disposable Windows VM for NTFS |
| Disk and controller firmware that ignores a flush | No application can see it | Real hardware with a power-cut rig; F7 only shows what the mod would do next |
| A real power cut | Simulated by a forced process stop plus rewritten files | A virtual-machine power-off (the plan's Method B), still an optional extension under R457 |
| Region and entity files, including the escrow barrier's own stores, force and readback | Vanilla `IOWorker` is outside the seam (R7) | Slice S6 below (file-level old and new variants), if Root wants it; torn region sectors need the VM |
| Plain `SavedData` files, station ordinary saves after the first commit, planetary bindings, migration backups | Not written through `CheckedSavedDataFile` (section 0.3) | The VM replay covers them without code; or route them through the seam in a later task |
| Player files, `level.dat`, configuration | Vanilla and Forge | The VM replay |
| Intermediate generations in a late-loss state, and the cross product of two unfinished writes | Bounded out in section 2.5 | Raise the bounds for one case if a finding points there |
| Faults in two stores at once | X02 injects one fault into one store | A later matrix, if a single-fault finding suggests it |

The report of each run repeats this table, so that a PASS is not read as proof of power-loss safety.

## 6. Gating

- The seam's field holds `DIRECT` unless an installer replaces it. The installer is one class. It runs at server
  start and installs a recorder or an injector only when all of these hold:
  - `advancedrocketrycommunity.releaseTestHooks` is true;
  - the server is a dedicated server;
  - `EscrowTestEndpoints.requireEnabled(server)` passes;
  - the specific property is set: `advancedrocketrycommunity.storageTrace=<directory>` or
    `advancedrocketrycommunity.storageFault=<store>:<fault>:<count>`.
- It restores `DIRECT` when the server stops, as `FirstEventCutSeams` does for `installCommitterSelector`.
- A unit test pins that the field is `DIRECT` when no property is set, and that `DIRECT` leaves the same bytes and
  throws the same exceptions as the current code for a normal write, a failed readback and a throwing committer. The
  existing `CheckedSavedDataFile` tests and every writer test run unchanged.
- The recorder and the injector add no command, no network message, no configuration key and no saved field. The
  trace lies outside the world directory.
- The offline faults and the replay never run inside the server. They are Python that edits copies.
- No production behaviour changes. The seam does change the candidate JAR's bytes, so evidence has to come from a
  candidate built after the Java slices merge (question Q4).

## 7. Machine time, slices and estimate

### 7.1 Slices

| Slice | Content | Files | Worker time |
|---|---|---|---|
| S1 | The `FileOps` seam with `DIRECT`, the recorder, the gated installer and unit tests. Read-only review by another engine before merge. | `persistence/migration/CheckedSavedDataFile.java`, `CheckedSavedDataPolicy.java` (the `forceDirectory` body only), new `persistence/migration/CheckedFileOps.java` and `ReleaseTestStorageTrace.java`, one installer call beside the existing release-test start-up hooks, new tests under `src/test/java/.../persistence/migration/` | 1 to 1.5 days |
| S2 | The replay builder and the X01 runner. The existing runners gain a way to run only their restart check on a given world; their oracles do not change. | New `scripts/v180_crash_replay.py`, `scripts/run_v180_crash_replay.py`, `tests/test_v180_crash_replay.py`; small additive changes to runners B, F, S, I and R | 1.5 to 2 days |
| S3 | The X01 run on the candidate, and the report with one row for each case and crash point | Evidence outside Git; one record and one result table in `docs/work/v1.8.0-root-queue/records/` | 0.5 day, plus machine time |
| S4 | The fault injector, the `Reader` wrapper, the offline fault builder and the X02 runner | New `persistence/migration/ReleaseTestStorageFault.java`, the installer, new `scripts/run_v180_storage_faults.py` and its test, an entry in `scripts/gametest_expected_log.json` only if a GameTest is added | 1.5 to 2 days |
| S5 | The X02 run and the classification report | Evidence outside Git; one record | 0.5 to 1 day, plus machine time |
| S6 (optional) | File-level old and new variants of the region files that the barrier forces, for X01-07 and two more cargo cases | The recorder (a copy of the touched region files at `BARRIER_FORCE_AFTER`), the builder | 1 to 1.5 days |

S1 and S4 touch the same Java class and go to one worker in that order. S2 can run beside S1. The total without S6 is
about 5.5 to 7 working days, against 7 to 9.5 days for the plan's virtual-machine route, and it needs no owner action.

### 7.2 Machine time

The duration of one native start was not measured in this task. The estimate assumes about two minutes for a start
with its check.

| Run | Starts | Time |
|---|---|---|
| X01: 12 recordings | 12 | About 1 hour |
| X01: at most 192 copies, two starts each | At most 384 | About 10 to 16 hours, unattended |
| X02: about 40 live cases with three starts, about 20 offline cases with two | About 160 | About 4 to 6 hours |

A native server is a heavy JVM. The runner takes the machine lock `locks/heavy-jvm` once for each case (at most 16
copies, about one hour), releases it in the same script's `finally`, and waits 90 seconds before the next case, as
[WORKER-RULES](../WORKER-RULES.md) requires. It runs as a detached process. Before each case it checks that C: and D:
each have 10 GB free.

### 7.3 Evidence layout and size

The evidence lives outside Git, in two directories:

- `D:/GitHub/ARCE-Task-Evidence/x01-crash-replay/<candidate SHA>/`;
- `D:/GitHub/ARCE-Task-Evidence/x02-storage-faults/<candidate SHA>/`.

Each has this layout:

```text
MANIFEST.json            candidate commit, JAR SHA-256, model version, commands
cases/<case>/trace/      ops.jsonl, blobs/, pre/        (X01 only)
cases/<case>/states.json each state: derivation, file hashes, allowed outcomes
cases/<case>/results.jsonl  one line for each copy and start: verdict, markers, oracle
cases/<case>/logs/       <state>-start1.log.gz, <state>-start2.log.gz
SUMMARY.md
SHA256SUMS.txt
```

- World copies, server directories and uncompressed logs are deleted after each copy's two starts. The fixture worlds
  and the kill image of each case are kept until the case's results are written, then the kill image is deleted too:
  the trace and the fixture rebuild it.
- Logs are stored compressed. With about 400 logs, the X01 directory is expected to stay under 60 MB; the hard limits
  are 100 MB for a slice's evidence directory and 50 MB for a single file (AGENTS.md section 9). If a directory would
  pass 100 MB, the logs of passing copies are reduced to their marker lines and the report says so.
- `SHA256SUMS.txt` lists only files inside the evidence directory. The repository gets the record, the result table
  and the SHA-256 of the manifest.

## 8. Questions for Root

1. **The seam.** R457 names the committer seam, which cannot see or fail the staging write, the flush or the close
   (section 0.2). Should the harness add the `FileOps` seam inside `CheckedSavedDataFile`?
   *Recommendation: yes.* X02's write, flush, close and full-disk faults cannot be injected otherwise, and one seam
   covers every checked store without touching a writer. The alternative for X01 alone is to rebuild the states from a
   recording committer; it needs no new hook but leaves most of X02 undone.
2. **Late-loss failures.** A late-loss state (R6) may fail on this PC, because Windows has no directory flush and the
   design records that power loss is outside its guarantee there. Is such a FAIL a defect to fix before the row
   closes, or a finding reported under the existing residual?
   *Recommendation: report it as a finding under the existing residual, with its reach, and decide a fix separately.*
   The oracle stays as it is in either case. If findings appear, a later task can examine whether a second
   `force(true)` on the target after the move narrows the gap on NTFS.
3. **Region files.** Should the first pass include slice S6?
   *Recommendation: no.* Run S1 to S5 first. The barrier's region writes are vanilla's format, and file-level variants
   are a coarse model of them. State the residual, and add S6 only if the X01 report leaves the cargo barrier as the
   main open doubt.
4. **The candidate.** The seam changes the JAR. *Recommendation: merge S1 and S4's Java before candidate C4 is cut,*
   so that X01 and X02 run on the candidate of the final evidence. Otherwise they run on a later candidate.
5. **The stores of X02.** *Recommendation: the four named stores only* (ledger, bay receipts, transfer journal,
   registry). The rocket transaction journal and the rejoin sidecar can be added at about half a day each if Root wants
   them in the report.
6. **The stores outside the seam with their own staging code** (planetary bindings, migration backups).
   *Recommendation: leave them out and list them as uncovered,* as section 5 does. Routing them through the seam is a
   production refactor that this row does not need.
7. **The bound of 16 states for one case.** *Recommendation: accept it,* with `OVER_BOUND` reported instead of silent
   sampling. It keeps the run near the plan's estimate of one day of machine time.
