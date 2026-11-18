# W367: X03 whole-world repair after FF_REBUILD_SOURCE

Date: 2026-11-17, revised 2026-11-18. Status: **DESIGN ONLY, revision 2. Nothing here is implemented.**
Revision 2 was inspected against commit `8edb061cb1e43f53fd0cef6daccb600b517d7cf5`. This note implements no
repair, changes no admission switch, and closes no risk, native row or Required Gate. Root commissioned it in
[TASK-W367](../TASK-W367-X03-REPAIR-DESIGN.md) under R460, and its revision in
[TASK-W367F](../TASK-W367F-X03-DESIGN-REVISION.md) under R473. The parent plan is
[X01-X03-ISOLATION-PLAN, section 5](X01-X03-ISOLATION-PLAN.md#5-x03-the-operators-whole-world-repair-after-rebuild_source).

Revision 2 resolves the findings of the independent review W367R
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W367-review/REVIEW.md`, SHA-256
`83ee8c9cb1762f77d60273fbf50688e3c4fec194d6f6f90ade2603217a78e5a4`, verdict APPROVE-WITH-CHANGES). The table
that maps each finding to its section is in [W367](W367.md). The top-level section numbers are unchanged from
revision 1, so existing references to sections 1 to 8 still point at the same subjects.

Revision 3 (2026-11-18, Root decision R487) adds [section 9](#9-revision-3-root-decision-r487-after-w380s-native-runs),
which amends sections 2.2, 3.5, 5.2 and 5.3 after W380's native runs. The other sections are unchanged.

Terms used below:

- **T** is the one stale transfer record in the journal. **R** is the one physical rocket. **The device** is
  the bound unloader.
- **B** is the complete backup of the stopped world. **C** is the candidate copy that receives the edit.
- **The pinned candidate** is the exact mod JAR, identified by SHA-256, that the plan names.
- **The qualifying start** is one start and clean stop of the unrepaired world under the pinned candidate, in
  which the candidate logs its fence line for T (section 2.2). B is taken after it.
- **Operator** steps are what a server owner does on a real world. **Harness** steps exist only in the native
  test family and need release-test hooks, which a production server must never set.

## 1. Diagnosis and authority, store by store

[W142](W142.md), particularly R73, the preserved-source R74 continuation and R76, distinguishes two inputs.
R73's new source snapshot UUID allowed an actionable historical copy and 10,000 mB. R74 preserved the real
source identity, and R76 accepted its **protected refusal**, not a playable repaired world. This proposal
accepts only the second shape. It must refuse R73's shape, and it must not delete R73's extra rocket or hide
its extra water. [W326 N084/X03](W326-MATRIX.md) and
[C3 sections 7 and 8.6](C3-SWITCH-REPORT.md#7-fluid_cargo_unload-block_variantloader4) still do not
demonstrate a repair.

| Store | Corrected FF_REBUILD_SOURCE state and authority | Proposed offline action |
|---|---|---|
| Escrow ledger | The ordinary 1,000 mB unload completed L2 and C8. The fixture requires no operation, floor, receipt, READY head or ARMED flight, and launch serial 0. The admission guard was NONE, so L2 did not install a named-transfer floor. The existing ledger ID still binds both endpoints. | Preserve the complete ledger file, including the writer generation, byte for byte. No floor is removed, invented or lowered. |
| Transfer journal | One stale legacy PREPARED record T was installed **after** that retirement. It names the actual physical source, logical rocket, owner, source snapshot UUID and origin, but it contains the pre-unload cargo of 5,000 mB. Its destination is a relocated historical snapshot with no physical destination. | Remove exactly T from the copied journal's `data/transfers` list after all checks. Preserve the root schema, every other field, the outbox and every unrelated record. Keep T's original bytes in the immutable backup and its typed representation in the repair report. |
| Rocket entity | Exactly one physical rocket R remains at the original source placement. Its current snapshot has the same source UUID and structure, but the cargo change gives a different content hash. Its escrow section is bound to the ledger at epoch 1 with no markers. It has 4,000 mB of water and, separately, 1,000 mB of propulsion fuel. The fixture creates a stationary fueled rocket; its saved flight is not the historical TRANSIT flight inside T. | Preserve the entire entity region and entity NBT byte for byte offline, including `RocketEntityData`, flight, fuel, passengers and escrow. Require FUELED, no plan, no active transfer and no passenger assignments. No flight normalization is needed or authorized. |
| Tank inside the rocket snapshot | One `pressurized_tank_v1` payload contains the current 4,000 mB of water. This is physical cargo represented inside R, not an additional world tank. | Do not edit the payload or its hash. |
| Unloader and terrain | The bound UNLOAD device contains 1,000 mB of water, epoch 1 and no markers. No corresponding pressurized tank has been restored into terrain at either recorded end. | Preserve terrain, device identity, tank, binding and escrow bytes offline. |
| Other stores | Assembly and disassembly transactions, pad claims, bay receipts, the rejoin sidecar, player data and other dimensions must contain no competing claim for these identities. | Read and cross-check; do not edit. Unknown or conflicting authority refuses the repair. |

There is **no missing 1,000 mB** to refund. The physical cargo is `4,000 + 1,000 = 5,000 mB`. T's historical
5,000 is a recovery description, not another owned inventory. Replacing the current rocket payload with that
description would make the physical cargo 6,000 mB. Materializing a whole historical rocket beside R is
worse. Propulsion is a different fluid account and must not be added to the water total.

The authority is the complete, consistent native world: the exact current endpoints, the ledger binding and
epoch, the retirement evidence and the exclusive identities together, not any single file. Historical
snapshot contents cannot override credited endpoints. The earlier ENDPOINT_PROOF_REQUIRED and a RETRY_LATER
report's zero match counters do not prove that the source is absent.

### 1.1 The action production elects, and where the fence fires

The case is named FF_REBUILD_SOURCE, but with R74's preserved identity production no longer elects
REBUILD_SOURCE for it:

- `RocketTransferEntities.recognizes` matches a rocket to a recorded snapshot when the snapshot ID is equal
  and either the content hash is equal, or the epoch is above zero, the structure digest is equal and the
  ledger binding is current. R matches T's source through the second branch.
- `RocketTransferRecoveryDecision.decide` therefore sees a present source and no destination, and it elects
  **KEEP_SOURCE**. REBUILD_SOURCE would need both ends absent.
- KEEP_SOURCE is a source-side action, and R is a protected keeper with a nonzero epoch. The fence in
  [EscrowRecoveryFence](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowRecoveryFence.java)
  (`reason`, the source-side branch that the review cites at line 61) returns **FLOOR_CONFLICT**, even though
  the ledger's `floors` list is empty. The name does not imply that a durable floor exists.
- [RocketTransferFences](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferFences.java)
  then logs `ARCE_TRANSFER_FENCED transfer=<T> reason=FLOOR_CONFLICT repair=offline-whole-world`. The fence
  reasons are held in memory and rebuilt from the journal on each start. The conflict therefore lives only in
  T together with R's epoch, which is why removing exactly T returns the world to the state the fixture saved
  just before it installed T.

### 1.2 The accepted shape is a predicate, and FF_KEEP_SOURCE is refused by name

The retained FF_KEEP_SOURCE world has the same on-disk shape as FF_REBUILD_SOURCE. In
[EscrowCargoRecoveryCuts](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoRecoveryCuts.java)
(`legacyRecord`) the two records differ only in where the relocated destination snapshot stands: the
REBUILD_SOURCE destination is the far cell in the pad's chunk, and the KEEP_SOURCE destination is eight
blocks from the source, in the chunk that holds R and the device. Both elect KEEP_SOURCE and both fence with
FLOOR_CONFLICT.

The tool accepts a world only when all of the following hold. Sections 2.1 and 3.5 give the exact checks.

1. The journal holds T, a legacy PREPARED record of an accepted form (section 2.5), and no other record that
   names the same logical rocket.
2. T names R as its source: physical ID, logical rocket, owner, snapshot ID and origin all agree, and the
   snapshots agree under the rule of section 2.1.
3. T has no destination entity, and no rocket, tank or other block stands in the destination's recorded
   footprint.
4. R is stationary, FUELED, without a plan, an active transfer or a passenger assignment, and T's recorded
   flight has no passenger assignment.
5. The ledger is bound to both endpoints at epoch 1 with no markers, and it holds no operation, floor,
   receipt, READY head or ARMED flight for these identities.
6. **Scope predicate `DESTINATION_IN_TARGET_CHUNK`.** No chunk of the destination's recorded footprint is a
   chunk that holds R's footprint or the device. A world that fails only this predicate is the FF_KEEP_SOURCE
   shape, and `plan` refuses it with exactly this reason.

Predicate 6 is a limit of scope and of evidence, and this note does not present it as a safety property. The
authority of a KEEP_SOURCE world is the same as that of a REBUILD_SOURCE world, and nothing but the recorded
destination position separates them on disk. The reasons for refusing it are these:

- X03 names the REBUILD_SOURCE case, and the native family of section 5 produces positive evidence only for
  a destination footprint that is disjoint from the target chunks. In the KEEP_SOURCE arrangement the "empty
  destination footprint" census reads the same chunk records that hold R and the device, and no run covers
  that.
- The tool repairs only what its evidence covers. Extending it is one positive native case and a Root
  decision, not a design change, so the refusal costs little to lift later.

An operator whose world is refused for this reason is told that the world has another shape and needs a
separately reviewed repair (section 7). FF_RECONNECT is refused by predicate 4 and FF_STALE_PREPARED by the
second physical rocket (section 3.5). R73's shape is refused by predicate 2, or by the missing T and the two
rockets after its restart.

## 2. Choice: a narrow offline tool, not settlement or a new game command

The audited settlement is preferable where it is eligible. It is not eligible here:

- [EscrowOperatorDiagnostics](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowOperatorDiagnostics.java)
  reports no selectable operation or floor. ACCEPT_CURRENT and ABANDON select a connected component of
  existing operations; they cannot settle a bare journal entry. The intent also excludes FLOOR_CONFLICT.
- [EscrowSettlementOperator](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSettlementOperator.java)
  requires a real stored floor for TRANSFER_FLOOR_RETIRE.
  [EscrowSettlementObserver](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSettlementObserver.java)
  additionally requires a COMMITTED record, a matching protected destination and absence at both ends. Here
  T is PREPARED, there is no floor, and R is present.
- [EscrowSettlementRecovery](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowSettlementRecovery.java)
  reaches the journal removal only after a frozen O1, an own-store O2 and an installed O3. O4 releases the
  exact decision, and pruning only removes retired markers. A maintenance start changes none of these rules.

The design therefore proposes a small operator-invoked **offline planner and copier**,
`scripts/repair_v180_rebuild_source.py`, with three modes. None of them is runnable today.

- `plan` reads B, checks every predicate and writes an immutable plan with a digest. It collects every failed
  predicate, up to 32 reasons, and does not stop at the first, so that a refusal names the whole difference.
- `apply` takes a plan and the operator's confirmation of its digest, and builds a fresh candidate C. It never
  patches the source, the backup or an active server directory in place.
- `census` is read-only. It compares a stopped copy with B and the plan, lists every changed, added and
  removed file, and gives the semantic verdict of section 3.6. The operator and the harness both run it after
  each stop.

The sole semantic edit is the removal of the verified stale T. That removal is an audited consequence of the
whole-world validation, **not permission to delete a journal entry on its own**. The tool neither replays
recovery nor creates an escrow operation, a READY head, a receipt, an account row or a floor to manufacture
online eligibility.

There is no new in-game command, packet or permission level. The offline authority is filesystem access plus
the operator's recorded identity, reason and explicit confirmation of the plan digest. The tool cannot accept
an arbitrary NBT patch, a replacement snapshot, an amount, `--force` or any public option that skips a check.
One invocation handles one T. The console shows one summary for planning, one for application or refusal, and
at most four bounded details of 160 characters each. Full manifests go to a size-bounded external report.

The tool reuses the typed NBT representation in [loader_cut_nbt.py](../../../../scripts/loader_cut_nbt.py)
and the existing checked reads and evidence helpers, and adds no dependency. It preserves NBT types, the
order of compound entries and all non-selected subtrees, and it compares them before and after
serialization. It needs no region writer. Its decompressor and schema checks reject ambiguous or invalid
input and never repair it silently.

### 2.1 The recognition rule: stricter than production, with no second hash implementation

The tool is Python and cannot call the Java codecs. `RocketSnapshotNbtCodec` stores `content_hash` but no
structure digest, `RocketStructureSnapshot` computes that digest in Java, and `RocketTransferRecord` verifies
its checksum in Java. The tool does **not** reimplement the record checksum, the content hash or the
structure digest. A second implementation of authority validation could drift from the server and approve a
record that the server reads differently.

The tool applies this rule instead, on typed NBT:

1. T's `source_snapshot` and R's current snapshot must be equal as typed NBT, entry for entry and type for
   type, **except** for exactly two leaves: the fluid amount in the one tank payload (5,000 in T, 4,000 in R)
   and the stored `content_hash`. Any other difference refuses with `SNAPSHOT_DIFFERS`, and the report names
   the first differing path.
2. T's `source_entity_id`, `logical_rocket_id`, `owner_id` and source origin must equal R's physical UUID,
   assembly transaction, owner and position. T's `phase` must be PREPARED, and T must have no
   `destination_entity_id`.
3. R's escrow section and the device's escrow section must name the ledger's own ID, at epoch 1 or above,
   with no markers. These are compared as stored values, not as hashes.

The rule is stricter than production's epoch branch. Production accepts an equal snapshot ID with an equal
structure digest; the tool requires the whole snapshot to be equal apart from the two leaves that the cargo
change is known to alter. The tool does not prove from first principles that equal NBT gives an equal
digest. That link is supplied by the bound evidence of section 2.2, in which the pinned candidate itself
recognized R and fenced T.

### 2.2 Bound evidence: the candidate's own fence line

`plan` requires, as input, the server log of the qualifying start. The log must contain the line

```text
ARCE_TRANSFER_FENCED transfer=<T's ID> reason=FLOOR_CONFLICT repair=offline-whole-world
```

for T's ID. That line proves that the pinned candidate decoded T, verified its checksum, recognized R as the
source and elected the fence. The tool refuses with `FENCE_EVIDENCE_MISSING` when the line is absent, with
`FENCE_REASON_DIFFERS` when T was fenced for another reason (for example WAITING_UNLOADED, which means the
recorded ends were not loaded in that start), and with `OTHER_TRANSFER_FENCED` when the log fences any other
transfer. The plan binds the log's SHA-256 and the exact line.

The log is supplied evidence. The tool cannot prove which JAR wrote it, so the operator's confirmation of the
plan digest covers the statement that the log comes from the qualifying start under the pinned candidate. In
the native family the runner produces the log itself and binds the JAR hash.

The first start of C is the codec check of the edited journal. If the journal root fails to decode, the
candidate marks it BLOCKED (`RocketTransferSavedData.load`), and that refuses promotion (section 3.5).

### 2.3 Locks

"Exclusive world locks" means the following, and nothing weaker:

- Minecraft holds `session.lock` with a Java `FileChannel` lock on the whole file. On Windows the tool takes a
  byte-range lock on the same file with `msvcrt.locking` and `LK_NBLCK`. On Linux it takes a POSIX record
  lock with `fcntl.lockf` and `LOCK_EX | LOCK_NB`. It must not use `fcntl.flock`, which does not conflict with
  a Java lock on Linux and would be silently non-exclusive.
- The tool opens each `session.lock` once and keeps that handle until it exits, because a POSIX record lock is
  released when the process closes any handle to the file. It reads and hashes `session.lock` through the
  locking handle, because on Windows the locked range cannot be read through another handle.
- It locks the `session.lock` of the source world, of B and of every working copy. A lock that cannot be
  taken refuses with `WORLD_LOCKED`.
- For its own directories it also creates one lock file with `O_CREAT | O_EXCL` in the external report
  directory, never inside a world, so that it adds no file to any world's file-name set.
- A unit test starts a second holder (a child process) against a locked world on the running platform and
  requires the refusal. The Linux variant runs in the hosted workflow.

The tool also rejects aliasing, symbolic links, reparse points and hard-link sharing, and it resolves every
path beneath its declared roots.

### 2.4 The writer: reproducible bytes and the empty list

`loader_cut_nbt.write_gzip` is not staged, forced or atomic, so the tool has its own journal writer:

- It writes the uncompressed NBT to a staged file beside the target, with gzip header time 0, no file name
  and a fixed compression level, forces it to disk, reads it back, decodes it and compares it with the
  approved typed candidate, and only then replaces the target atomically and reads the target back.
- The plan binds two digests. The digest of the **uncompressed** payload is authoritative and depends only on
  the typed NBT. The digest of the compressed file depends on the zlib build as well, so the plan also records
  the Python and zlib runtime versions, and `apply` refuses with `TOOLCHAIN_DIFFERS` when they differ from the
  plan's. The compressed digest is a check that `apply` wrote what `plan` predicted; it is not claimed to
  equal what a Java writer would produce.
- After the edit the `transfers` list is empty. The tool writes it as vanilla does, with element type END (0)
  and length 0. A unit test pins these bytes and checks that the list reads back as an empty list of
  compounds, so that the candidate's first save does not change the decoded form.

### 2.5 Accepted record forms

"Legacy PREPARED" means a record with `phase` PREPARED, no `native_transfer` entry and a `schema_version`
below the governed version (3 at the inspected head, per `RocketTransferJournalCodec.decodeRecord`). The tool
accepts exactly the one legacy version that the pinned candidate's `RocketTransferRecord.create` writes for
the fixture. Slice X03-A reads that number from the retained FF_REBUILD_SOURCE world and pins it in a unit
test. The tool refuses a governed record with `GOVERNED_RECORD`, any other version with
`UNSUPPORTED_RECORD_VERSION`, and any phase other than PREPARED with `PHASE_NOT_PREPARED`.

## 3. Procedure and checks

This is the proposed procedure for approval, not a supported repair that operators can use today. It follows
[R-035](../../../11-RISK-REGISTER.md) and its accounting paragraph, and
[ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md). Nobody operates on a
running server, restores one store, rebuilds a resource or refunds one.

Section 3.1 is the complete operator procedure. Section 3.2 explains what the operator's starts can and
cannot show. Section 3.3 lists the steps that exist only in the native harness. An operator performs nothing
from section 3.3.

### 3.1 Operator procedure

1. **Stop and identify.** Disable automatic restart and deny players access. Stop the server and prove that
   its process has exited. Record the candidate JAR's SHA-256, the Forge and Java versions, the configuration
   hashes and the world identity. The repair handles only the schema and feature combination that the pinned
   candidate supports. It does not upgrade a world.
2. **Qualifying start.** Start the unrepaired world once under the pinned candidate, with players excluded.
   If the chunk that holds R is not loaded by itself, load it as in section 3.2. Wait for the fence line of
   section 2.2 for T, then stop cleanly. This start also brings every managed root to the candidate's current
   schema where a migration was due (section 4), so that no later start of C migrates anything. The world
   starts in its protected state; nothing is repaired by this step.
3. **Keep the entire original.** With the locks of section 2.3 held, inventory every regular file in the
   complete world: all Levels' terrain, entities and POI, `.mcc` files, all global data, journals, ledgers and
   accounts, receipts, sidecars, player data, advancements, statistics, datapacks and metadata. Record relative
   names, sizes and SHA-256, and a canonical manifest digest. Make a complete backup B, hash it again
   independently, and retain the original and B unchanged. A SavedData migrator's partial backup is not B.
4. **Plan.** Run `plan` on B with the log of step 2. It enumerates **all** dimensions, every entity-region
   header slot, nested entity passengers and all terrain block entities. It counts R's physical, logical and
   snapshot IDs, T's two ends, tank locations and the device identity across the complete file set, searches
   every managed store for references, and checks every predicate of sections 1.2, 2.1, 2.2, 2.5 and 3.5. It
   writes plan version 1, which binds B's manifest digest, the tool and candidate hashes, the ledger schema
   and ID, T's exact record digest, the identities of R and the device, the epochs, the per-fluid
   observations, the accounting knowledge of section 4, the fence evidence, the operator and the reason. The
   plan contains the exact edit `/data/transfers[T.transfer_id] -> absent`, explicit no-edits for the ledger,
   entities and terrain, the expected output digests of section 2.4 and a write list of one file.
5. **Confirm.** The operator reviews the plan, including the identities, the amounts and the UNKNOWN or
   confirmed classification of the external account, and confirms its digest. Confirmation cannot waive a
   failed structural or conservation check.
6. **Apply to a fresh copy.** `apply` verifies all input hashes and locks again and creates C from B, never
   from a partially modified attempt. It removes exactly T with the writer of section 2.4 and preserves the
   root's wrapper, DataVersion and first-Moon-landing outbox. Any failed or uncertain write, force, close,
   replace or readback leaves C **unapproved**; the operator keeps the diagnostics and builds a fresh C after
   the cause is corrected. Running `apply` on an already repaired input refuses, because its manifest differs.
7. **Verify offline: the strict boundary.** Before any start of C, only
   `data/advancedrocketrycommunity_rocket_transfers.dat` may differ between B and C, and only T may differ in
   its decoded payload. Every other file and the file-name set must match B exactly. `census` checks this and
   stores the manifest and the semantic difference outside the world. This one-file difference is the only
   byte-for-byte boundary the operator procedure has.
8. **First save and restart.** Start C in isolation under the pinned candidate, with players and automation
   excluded and with no test property set. Load the chunks of R and the device as in section 3.2. Check the
   log for the signals of section 3.2. Run `save-all flush`, wait for its completion message, undo any chunk
   forcing made for this start, stop cleanly, and run `census` on the stopped copy.
9. **Second save and restart.** Start C again and repeat step 8 exactly. Then start C a third time, check the
   same signals, stop cleanly and run the final `census`. There are thus an initial start of C **plus two
   restarts**, each a distinct server process, not two logins or two saves in one process. A fence line, a
   blocked root, a failed save or a failed `census` at any stop prevents approval. A successful process exit
   alone proves nothing.
10. **Replace the whole world.** Only after both cycles and the operator's approval, stop every process,
    verify the hashes of the original, B and C and the exclusive locks again, and promote **the entire**
    validated C. Stage it on the target filesystem before a stopped-server directory swap, and retain the
    original directory and B. If the promotion fails or is interrupted, keep the server stopped and verify
    whole directories before retrying. Never copy only the repaired journal into a live or divergent world.
    Rollback is also whole-world, including metadata and player data.

The operator does not perform an unload or a launch as part of the repair. Production admission of cargo is
off (`EscrowCargoSwitches.production` returns false for every cargo kind), so an operator cannot run the
functional check of section 3.3, and the procedure does not ask for it. The evidence that a repaired world
works comes from the native family.

### 3.2 What the operator's starts load, show and change

**Loading the targets.** In the fixture the chunks of R and the device are loaded by the probe. On a real
world, with players excluded and T removed, nothing loads R's chunk, and a start would then neither read nor
write R or the device. The two restarts would be empty for the targets. The operator therefore loads the
chunks explicitly in the qualifying start and in every start of C:

- From the server console, in R's dimension, run the vanilla command `forceload add` for the chunk of R and
  the chunk of the device, and `forceload remove` for the same chunks before the stop.
- If a chunk was already forced before the procedure, the operator leaves it forced and records that.
- Forcing writes the dimension's `data/chunks.dat`. That file can be created or changed by this procedure.
  `census` expects exactly this difference, names it in its report and accepts it only when the decoded forced
  list after the stop equals the list in B.

**Signals an operator can observe without hooks.**

- No `ARCE_TRANSFER_FENCED` line for any transfer in the log of each start of C. With T removed there is no
  journal entry left to fence, so this line's absence is a necessary signal, not a sufficient one.
- No blocked or refused managed root in the log, in particular not the transfer journal (section 2.2).
- The completion message of `save-all flush`, and a clean stop.
- The existing operator status of
  [EscrowOperatorDiagnostics](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowOperatorDiagnostics.java),
  which reports the counts of operations, floors, receipts, READY heads and ARMED entries, where the pinned
  candidate exposes it to the console.
- The `census` verdict after each stop. This is the signal that carries the weight: the tool decodes R, the
  device, the ledger and every managed root from the files that the start just rewrote.

**What the starts change.** A started copy is no longer byte-identical to B. Its game time has advanced, and
its loaded and forced chunks have kept working during the isolated starts. For a real world there is no exact
runtime file list; the lists of section 3.6 exist only for the fixed fixture. After a start, the operator's
gate is therefore semantic and not byte-level: `census` reports every changed, added and removed file for the
operator to read, and it decides on the semantic census of the targets and of every managed root
(section 3.6). The procedure does not claim that nothing else in a real world changed during the starts. The
operator keeps the starts short for that reason.

### 3.3 Harness-only additions

These steps exist only in the native family `cargo-X03-REPAIR` (section 5). They need the release-test hooks
and the gated verification mode of section 5.1.

- **Observation in every start of C.** The runner starts C with the verification mode's observation case. The
  probe loads the fixture's chunks with non-persistent tickets, waits until the journal and the ledger are
  checked and operational and the writers are idle, requests one ordinary full save, waits for the journal's
  acknowledgement and the native IO to complete, and reports what it reads. It decides nothing; the runner and
  the independent oracle compare its report with the files.
- **Exact runtime lists.** After each stop the runner compares the complete manifest with the frozen lists of
  section 3.6, byte for byte outside them.
- **Functional verification and conservation.** On a separately hash-verified descendant of the
  twice-restarted C, the runner admits one ordinary unload and then one launch through production entry
  points (section 5.1). It saves and reads back the completed unload: R 3,000 mB, the device 2,000 mB, one R
  and no world tank. It then launches and lands, and records the actual propulsion debit per fluid, separately
  from water. No privileged resource edit, direct success-state setter or bypass of escrow authority is
  allowed. The descendant is never promoted and is never substituted for C.
- **Negative controls and refusal cases** of section 5.3.

### 3.4 Bounded work and space

The initial offline work limits are 64 Levels, 100,000 files, 256 GiB of total input, 4,096 region files,
1,024 header slots per region, 1,024 objects per decoded chunk and passenger depth 8. Hashing is streamed
with a 1 MiB buffer. Decoding uses the existing stricter limits per root, record, region and NBT tree. An
exhausted limit refuses without truncating the census; it is not permission to infer absence. These are tool
limits and raise no runtime limit. Slice X03-A tests the selected values before the tool is approved.

Free space is checked before `plan` and before `apply`. The operator procedure holds the original, B and C,
and stages C once for the promotion, so the working volume needs at least **three times** the world's size
free, in addition to at least 10 GiB free on each of C: and D: (or the volumes that hold the temporary and
Gradle directories). The harness also holds the functional descendant and two control copies, so it needs
**six times** the fixture world's size. Less space refuses with `INSUFFICIENT_SPACE`.

### 3.5 Refusals

Every condition below refuses planning or application, or, after C was started, refuses promotion.

- **Environment.** A running writer, an unavailable lock, a path overlap, a traversal, an alias, a nonregular
  or linked file, an unreadable input or one whose presence is unknown, a changed input or manifest, an
  incomplete copy, an unverified backup, insufficient space, or a toolchain that differs from the plan's.
- **Decoding.** An exceeded work, size or depth limit, an unknown region compression, an unsupported external
  entity record, a corrupt, future or noncanonical schema, an invalid checksum field, a missing adapter or
  fluid, or an ambiguous decoding. All files are backed up even when a record format makes this repair refuse.
- **Roots not current (`ROOT_NOT_CURRENT`).** Any managed root in B whose schema stamp is not the pinned
  candidate's current one (section 4).
- **Evidence.** A missing, different or additional fence line (section 2.2).
- **The record.** Anything other than the one accepted T: a governed or native envelope, an unsupported
  version, a phase other than PREPARED, a destination entity, another transfer for the logical rocket, or
  inconsistent IDs, source snapshot, origin or owner (`SNAPSHOT_DIFFERS` and its siblings).
- **The rocket and the device.** A missing, duplicate or relocated R or device; a second physical rocket of
  the same logical identity anywhere (`SECOND_PHYSICAL_ROCKET`, the FF_STALE_PREPARED shape); a passenger
  assignment in R or in T's flight (`PASSENGER_ASSIGNMENT`, the FF_RECONNECT shape); an active flight or
  plan; a relocated or non-FUELED R; a physical historical tank or rocket; a nonempty destination footprint;
  unsupported or multiple cargo tanks; a changed fluid, tag, cargo amount, propulsion vector, collateral
  inventory, binding, epoch or marker state.
- **Scope (`DESTINATION_IN_TARGET_CHUNK`).** The FF_KEEP_SOURCE shape (section 1.2).
- **Competing authority.** Any related active escrow operation or account, floor, receipt, READY or ARMED
  authority, assembly or disassembly transaction, bay receipt or claim, pad reservation or rejoin obligation.
  Unrelated entries may remain only when the full reference audit proves that they are disjoint. They are
  never deleted to make a world fit.
- **A supplied witness that does not verify (`WITNESS_INVALID`).** See section 4.1. A world with **no**
  historical witness is not refused.
- **The plan.** An unconfirmed or mismatched plan, a plan made stale by an input change, an invented baseline
  or zero, an unexplained resource difference, changed non-selected NBT, extra file changes before the first
  start, missing evidence or an uncertain storage outcome.
- **After a start.** A fence line for any transfer, a blocked or refused root, a migration during a
  verification start (`MIGRATION_DURING_VERIFICATION`: a root's schema stamp changed, or a file appeared
  under `advancedrocketrycommunity-backups`), a failed save or restart, or a failed `census`.

A refusal keeps the original and B intact. After an `apply` error the partial candidate is quarantined and is
not repaired piece by piece. The tool uses no exception as an ordinary save veto, and it introduces no
runtime refusal to save terrain or a Level. Its refusals are local to making or promoting a copy, and they
last until a fresh valid input and plan exist. Existing server holds and logs are unchanged.

### 3.6 File-difference boundaries

There are three boundaries, and they must not be confused.

**Boundary 1: B to C before any start (operator and harness).** Exactly one file differs, as in step 7 of
section 3.1. This is strict and byte for byte.

**Boundary 2: the semantic census after a start (operator and harness).** Starting Minecraft necessarily
writes files, so a claim that started files stay byte-identical would be false. After each stop, `census`
requires:

- R's entity NBT and the device's block-entity NBT equal those in B as typed NBT, apart from a fixed list of
  clock fields. That list is shipped in the tool with a reason per field, is established by the dry runs of
  slice X03-B, and may be empty. It is never extended at run time.
- The decoded payload of every managed root equal to B's, apart from the absent T and a second fixed list of
  per-root bookkeeping fields, again with a reason per field.
- Exactly one R, the device, zero restored tanks and unchanged resource vectors, by a full census of the
  stopped files.
- No removed file. Added and changed files are listed for the reader.

**Boundary 3: the exact runtime lists (harness only).** For the fixed fixture the harness additionally
freezes an exact relative-file list for the verification starts. The rules are:

- The list is derived from **at least two** complete dry runs of the whole sequence (seed, repair and three
  starts of C). A file enters the list if it changed in either run.
- Each entry carries the **reason** it is written, for example "rewritten by every save" (`level.dat`,
  `level.dat_old`), "locked and rewritten at every start" (`session.lock`), "the journal's baseline capture on
  every start", "holds the chunk of R and the device, which the probe loads" (`region/r.0.0.mca`,
  `entities/r.0.0.mca`), or "forced-chunk list". An entry without a reason that Root accepts is not admitted,
  and the cause of the write is investigated instead.
- The list is an **allowance**. A listed file may stay unchanged in a run, because which chunks are dirty at
  a save depends on timing. An unlisted file may never change.
- No directory wildcard and no automatic acceptance of observed changes is permitted in the campaign. Root
  reviews the list, its reasons and both dry runs' differences before the campaign.
- Inside a listed file the semantic checks of boundary 2 and the per-object comparison of section 5.2 still
  apply. Permission to save a region is not permission to modify its other objects or cargo.

For the **functional descendant**, which is never promoted, new files are bounded by a rule and not by exact
names, because a launch and a landing create files that depend on the route:

- New or changed files are allowed only in named directories: the source and destination dimensions'
  `region`, `entities` and `poi` directories, the dimensions' `data` directories for the roots that the
  flight writes, and, only if the route needs a seated test player, `playerdata`, `stats` and `advancements`
  for that one player's UUID.
- Region and entity files are allowed only inside a box computed before the run from the fixture's positions
  and the plan's landing position, extended by the server's ticket radius.
- Everything outside the rule is compared byte for byte, and unexpected world generation or unrelated machine
  activity fails the run. The rule is frozen before the run and reported with all actual differences.

## 4. Root 2 and activated root 3: accounting without invented history

[W357 section 6](W357-DESIGN.md#6-failures-holds-and-logging) says that the transient numeric row may already
have disappeared at L2. At the inspected head the account models and codecs exist, but
`EscrowAccountingFormat.ENABLED` is false: writers still emit root 2, and `ManagedSavedDataType` selects
root 3 only when the switch is true. Supporting root 3 in an offline parser is not permission to start it on
that JAR.

**Every root must already be current.** The repair never coincides with a migration:

- `plan` holds a pinned table of the current schema stamp of every managed root for each format profile
  (accounting disabled: ledger root 2; accounting enabled: ledger root 3). A unit test compares the table with
  the source of `ManagedSavedDataType`, so that a drift fails the test. The plan names its profile, and the
  operator's confirmation covers that the profile is the pinned candidate's.
- If any managed root in B is not at that stamp, `plan` refuses with `ROOT_NOT_CURRENT`. The remedy is step 2
  of section 3.1: the qualifying start under the pinned candidate migrates the unrepaired world, in its
  protected state, and B is taken afterwards. A backup directory `advancedrocketrycommunity-backups` that this
  start creates is then part of B and is preserved like every other file.
- As a second, independent guard, a start of C in which any root's schema stamp changes or any file appears
  under `advancedrocketrycommunity-backups` refuses promotion (`MIGRATION_DURING_VERIFICATION`). A root-2
  ledger is therefore never migrated by the copy's first start.

| Form | Treatment by this repair |
|---|---|
| Root 2 under an accounting-disabled candidate | Preserve its bytes and its UNKNOWN settlement semantics. There is no retained fluid account for this retired transfer. The external repair audit records `UNKNOWN_BASELINE` for the original transfer account. It does not create a numeric zero or a permanent ledger receipt. |
| Fresh root 3 after the reviewed activation | Run only on an accounting-enabled candidate with capture, settlement, diagnostics and native readers integrated. A normally retired unload has no operation or account row. Preserve `fluid_accounts` exactly, including unrelated rows. Do not invent an operation ID to attach a replacement row, and do not resurrect the deleted transient account. |
| A root-2 world under an accounting-enabled candidate | Refused as `ROOT_NOT_CURRENT` until the qualifying start has migrated it. After that it is the next row. |
| Root-2 history migrated to root 3 | Preserve UNKNOWN_BASELINE; a legacy record or hash is not a typed fluid baseline. If a related live record or account remains, this tool refuses and a separately assigned settlement or repair is needed. |
| A retained known account or receipt outside this case | Do not rewrite its immutable baseline, commitment or frozen decision. That is a separate slice, not a mode that weakens this repair's preconditions. |

### 4.1 The external account, and one treatment of the historical witness

Two things are kept apart.

- **Structural authority is always required.** The predicates of section 1.2, the rule of section 2.1, the
  fence evidence, the exclusive census and the ledger state are checked on every world. A world that lacks
  any of them is refused.
- **The numeric before-witness is optional.** It is an independently captured record of the amounts before
  the unload (in the fixture, 5,000 mB in R and 0 in the device). It decides only what the external account
  says, never whether the repair proceeds, because the edit moves no resource.

The three cases are:

| Witness | External account | Repair |
|---|---|---|
| Supplied and verified against its checksums | May record a confirmed discrepancy of zero for `5,000/0 -> 4,000/1,000`, with the operator's confirmation in the plan digest. | Proceeds. |
| Absent | Records UNKNOWN for the historical account, with no numeric discrepancy. | Proceeds. |
| Supplied but not verifiable | None is written. | Refused with `WITNESS_INVALID`, because supplied evidence that fails its own check must not be ignored silently. The operator may run `plan` again without the witness, which gives the second row. |

The native fixture always has the witness. A world outside the harness usually has none, and it is then
repaired with an UNKNOWN account.

The external, versioned repair audit holds a resource account that is clearly distinguished from an
`EscrowFluidAccount` row. It identifies `(fluid registry ID, native-tag digest)`, with absent and empty tags
distinct, and gives the baseline provenance, the known endpoint contributions, the status and an optional
signed discrepancy. For an available typed baseline, `loss[f] = S0[f] + D0[f] - S[f] - D[f]`: positive means
loss and negative means gain. A journal's 5,000 mB source does not by itself establish an original device
balance. The offline edit itself moves **zero** water and fuel, and this is verified independently of the
knowledge classification.

This narrow oracle admits no actual discrepancy: a nonzero loss or gain refuses the repair or the promotion,
even if an operator would accept it in some other settlement. Accounting is not authority to rebuild an old
source or to waive the physical census. The root-3 bounds (2,048-byte rows, depth 5, 65 nodes and 14-member
lists), the receipt lifetime and capacity and the schema identities are preserved, and no new persistent
public ID is added.

## 5. New native family and independent oracle

The family is `cargo-X03-REPAIR`, run by a new `scripts/run_v180_x03_repair.py`. It is separate from the
C-S5 oracle in [run_v180_cargo_s4_s5_cuts.py](../../../../scripts/run_v180_cargo_s4_s5_cuts.py), whose
expectations stay exactly as they are. It pins the committed source, the candidate JAR hash, the tool and
runner hashes, the configuration and every raw checkpoint.

The seed reuses
[EscrowCargoRecoveryCuts](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoRecoveryCuts.java)
unchanged to produce the FF_REBUILD_SOURCE forced stop and both protected restarts.

**The family asserts FLOOR_CONFLICT itself.** The existing C-S5 table has no fence expectation for
FF_REBUILD_SOURCE, so the reused oracle would pass even if the seed never reached the fence, and "the
conflict cleared" would then mean nothing. The new family therefore requires, as a precondition, the line
`ARCE_TRANSFER_FENCED transfer=<T> reason=FLOOR_CONFLICT repair=offline-whole-world` for T's ID in **both**
protected restarts, together with the 4,000 and 1,000 mB endpoints, before anything is applied. It requires
the absence of any `ARCE_TRANSFER_FENCED` line, for any transfer, in every start of C and of the descendant.

### 5.1 The gated verification mode (new Java, slice X03-V)

The starts of C cannot use either existing scenario. `start` runs `create()` and builds a new rocket.
`recover` ends in `requireRefused`, which requires T to be present. Without a probe, nothing can observe on
the server thread, and nothing can admit an unload, because `EscrowCargoSwitches.forTests` is session-local
and needs code behind `EscrowTestEndpoints.requireEnabled`. New gated Java code is therefore **required**. It
is its own slice, X03-V, and X03-B depends on it.

**Route.** The mode is a new probe class beside the existing cargo cuts, attached through
[EscrowCargoCutProbe](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoCutProbe.java).
W142B's external diagnostic agent (`W142R73Action.java`) was the other possible route. It is not chosen,
because it is not in the repository, is not covered by the routing and gating tests, and would have to be
attached to a JVM from outside.

**Gating.** The probe attaches only when all three of these hold: the release-test hooks are on
(`EscrowTestEndpoints.HOOKS`), `escrowScenario` is the new value `verify`, and `escrowCase` is one of the
three new cases. Further rules:

- Without the hooks the mode is inert, exactly as `selected` is today.
- The existing cases are not accepted under `verify`, and the new cases are not accepted under `start` or
  `recover`. If the hooks are on and a new case meets any other scenario, `attach` throws before anything is
  created, so that the dispatch cannot fall through to the generic release fixture on a repaired world.
- The probe's constructor calls `EscrowTestEndpoints.requireEnabled`, as every cargo cut does.
- `EscrowRuntime` is not changed. The new cases are routed by the existing cargo-cut branch.

**Cases.** The names stay in the `FF_` family, so the inventory's token pattern is unchanged.

| Case | What it may do |
|---|---|
| `FF_X03_OBSERVE` | Read only. It waits for the checked journal, the operational ledger and idle writers, requests one ordinary full save (`saveEverything` and the flushes that the existing probe's `save()` performs), waits for the journal's acknowledgement, and logs one result line with the number of rockets, R's cargo and epoch, the device's tank and epoch, the number of journal entries, the ledger's counts, whether `RocketRuntime.resourceMutationAllowed(R)` holds and whether the journal's action authority is available. It asserts nothing about amounts; the runner and the oracle decide. It fails only on its bounded wait of 6,000 ticks. |
| `FF_X03_UNLOAD` | Functional descendant only. After the same observation it names the one device with `EscrowCargoSwitches.forTests(server, FLUID_CARGO_UNLOAD, device, true)`. The device's ordinary production path then admits one unload. When the ledger shows the operation, the probe withdraws the switch, as the seed fixture does. It waits for the retirement (no ledger record, no markers, idle writers), saves, and logs the result. |
| `FF_X03_LAUNCH` | Functional descendant only. It requests one flight for R through `RocketRuntime.requestAdminFlight`, waits within a bounded number of ticks until the rocket has landed, saves, and logs the typed propulsion debit and the landing position. The route is fixed in the runner: the Earth-to-Moon plan kind that T described, or, if the fixture world cannot host that landing, the shortest route that the production planner accepts. X03-B records which route it used and why. |

In every case the mode **never** creates, moves or edits a rocket, a device, a journal record or a ledger
entry by itself, never calls `data.setDirty()` on the ledger, never calls `RocketRuntime.recoverTransfer`,
and never sets a success state. The only writes it requests are ordinary full saves. The only authority it
grants is the one named device switch in `FF_X03_UNLOAD`, through the existing release-test gate; production
admission stays off. Its chunk tickets are the probe's existing non-persistent ones and write no forced-chunk
file.

The descendant's sequence is: `FF_X03_UNLOAD`, stop, `FF_X03_OBSERVE` (the restart readback of 3,000 and
2,000 mB), stop, `FF_X03_LAUNCH`, stop, `FF_X03_OBSERVE`, stop. Each is a distinct server process.

**Files of slice X03-V.**

| File | Change |
|---|---|
| `src/main/java/.../resourceescrow/EscrowCargoRepairVerifyProbe.java` (new) | The probe with the three cases above. |
| `EscrowCargoCutProbe.java` | `accepts`, `selected` and `attach` learn the `verify` scenario and the three cases, with the misrouting refusal. |
| `EscrowCargoCutProbeTest.java` | New assertions: inert without the hooks; the new cases only under `verify`; the old cases never under `verify`; the misrouting refusal. No existing assertion changes. |
| [EscrowProbeRoutingTest](../../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowProbeRoutingTest.java) | The pinned count rises from 674 to 677 for the three cases, with a comment naming X03-V. Its routing formula is unchanged: the new cases route to `cargoCuts`. |
| `src/test/resources/escrow-probe-cases.tsv` | Regenerated; three new rows. |
| [inventory_escrow_probe_cases.py](../../../../scripts/inventory_escrow_probe_cases.py) | `x03_repair` joins `DECLARED`. |
| `scripts/run_v180_x03_repair.py` (new, skeleton) | Only the `CASES` table and the builder of the verification start command, so that the inventories change once, in this slice. |
| `tests/test_run_v180_x03_repair.py` (new, skeleton) | Tests of that command builder: the hooks, `verify` and one case are set, and neither `start` nor `recover` is. |
| [test_escrow_probe_inventory.py](../../../../tests/test_escrow_probe_inventory.py) | Rows 674 to 677, direct producers 23 to 24, related tests 41 to 42, each with a comment. |

The counts above are the expected ones. The implementer states the actual numbers, and they must equal the
generator's output; a pin is never set to make a test pass without the matching inventory row. If slice X03-B
later adds a test literal that matches the token pattern, it updates both pins again and says so. The review
also names a "relaunch static inventory". No file of that name was found at the inspected head, so X03-V runs
the whole Python inventory and runner test set and reports any further pin it meets.

Because X03-V changes Java, WORKER-RULES requires the whole unit suite and one full GameTest run under the
machine lock, followed by the log checker. It adds no configuration key, no network channel and no deliberate
ERROR or WARN, so no log expectation should change.

### 5.2 The independent oracle

An independent raw-file oracle must not call the planner's success predicates. It decodes every entity-region
slot across every Level, the terrain and tank data and all relevant managed roots, and then checks:

1. Water is 5,000 mB throughout the repair: 4,000 and 1,000 until the functional unload, then 3,000 and
   2,000. No historical journal snapshot counts as physical inventory, and no unrelated fluid can cancel a
   water loss.
2. The same physical, logical, owner and source-snapshot identity exists once, with its escrow epoch and
   binding and unchanged propulsion. There is no restored or duplicate tank or rocket in another slot,
   dimension or passenger subtree, or at either recorded footprint.
3. T alone is absent. No replacement T, floor, receipt, READY head or ARMED entry was fabricated, all
   unrelated authority is unchanged, and the observation's readback and mutation checks pass after each
   restart. No fence line is present. Log silence alone is not sufficient: the oracle also needs the
   observation's result line and the decoded files.
4. An ordinary unload really applies and retires, and a launch really progresses and lands. The release-test
   gate enables only the one named device; the production switches remain off. The launch consumption is
   verified from its typed propulsion debit.
5. The complete per-stage manifests show no extra, missing or changed file outside the frozen lists.
6. **Inside every listed region and entity file, every object is compared, not only the targets.** For each
   chunk record in a listed file, the oracle compares the decoded chunk with the previous stage as typed NBT
   and permits only the named clock fields and, in the functional descendant, the named differences of the
   ordinary action. A difference is reported as `(file, chunk, path)`.
7. B and the source hashes are verified again after all runs.

### 5.3 Cases and controls

**Positive cases.** A root-2 case on an accounting-disabled build, which is development evidence only. On
the accounting-enabled candidate: a fresh-root-3 case, and an **upgraded retired-root-2** case, which is
exactly this sequence: a root-2 world in the FLOOR_CONFLICT state; `plan` under the accounting-enabled
candidate refuses with `ROOT_NOT_CURRENT`; the qualifying start under that candidate migrates the ledger,
fences T and stops cleanly; a new B is taken; the procedure runs. If Root's activation design refuses a
root-2 ledger at start and does not migrate it, the case reduces to the refusal and the positive half is
dropped. Activation is never faked with a schema edit.

**Native refusal cases.** These need no server start after their seed. `plan` on the seeded worlds must
refuse:

| Seed | Required reason |
|---|---|
| FF_KEEP_SOURCE | Exactly `DESTINATION_IN_TARGET_CHUNK` and no other reason, which shows that the world is otherwise the accepted shape. |
| FF_STALE_PREPARED | The reasons include `SECOND_PHYSICAL_ROCKET`. |
| FF_RECONNECT | The reasons include `PASSENGER_ASSIGNMENT`. |

**Negative control 1: an unlisted file changed.** This control shows that a playable, conserved target is not
sufficient.

- **The target is chosen by a rule, not by a store.** It is the first file in bytewise order of relative
  path that satisfies all of these: it is in B's manifest; it is outside the frozen runtime list; it was
  byte-identical to B after the second clean stop in **both** dry runs; and it is of a type the harness can
  change while keeping it valid, namely a region file with at least one chunk record or a gzip NBT file.
- **The dry run must prove that such a file exists**, and the reviewed list records its name. If the fixture
  world has none, the seed stage generates one deliberately: before the seed's stop, the runner forces and
  then releases one chunk at block (4096, 4096) with the vanilla `forceload` console command, so that B holds
  `region/r.8.8.mca`, which no later start loads. The dry runs are then repeated.
- **If no target exists after that, the case refuses to run** with `NO_TAMPER_TARGET`. It never invents a
  record at control time, because that would change the file-name set and test something else.
- **The mutation.** On a separate disposable copy, after the second clean stop, the harness changes one
  numeric leaf of the target and keeps the file valid (for a region file, through
  `loader_cut_nbt.write_chunk`). A test-only coordinator variant omits the final unchanged-file check; no
  shipped option does. All target resource and identity checks stay enabled and pass.
- **The two halves.** The independent oracle must fail on exactly that file's hash and forbid promotion. The
  unmodified coordinator must refuse the same copy.

**Negative control 2: a non-target object changed inside a listed file.** The byte boundary protects only
files outside the list, and the two files that hold every target are necessarily inside it. This control
tests the typed comparison that protects them.

- On another disposable copy, after the second clean stop, the harness changes one block in
  `region/r.0.0.mca` with `loader_cut_nbt.set_block`. The chunk is chosen by a rule: the first chunk in slot
  order that holds neither R's footprint, the device nor the pad. The block is one that no target check
  reads.
- The same test-only coordinator variant omits the per-object comparison. The independent oracle must fail
  on that object by name, as `(region/r.0.0.mca, chunk, path)`, and the unmodified coordinator must refuse
  the same copy.
- The fixture has no second entity, so the same comparison for `entities/r.0.0.mca` is controlled at unit
  level on a synthetic region with two entities, of which one is changed.

**Unit controls.** Every refusal reason of section 3.5; both R73 shapes (a differing source snapshot ID, and
two rockets with no T); preservation of unknown NBT fields, types and entry order; disjoint unrelated
entries; corrupted and truncated gzip; a write failure at each step of the writer; a stale plan replay; the
lock test of section 2.3; the bytes of section 2.4; missing or duplicate identities; a skipped journal
removal; and an invented account zero.

Evidence records commands, exit codes, manifests, native observations, the reasons of the controls and the
source and JAR hashes. It keeps logs, results and manifests, not source archives or disposable server trees,
and it respects the limits of 50 MB per file and 100 MB per evidence directory. All native servers, complete
builds, runData and GameTests use the machine lock and clean up what they own. The X01 and X02 storage and
power-loss residuals remain separate; a successful X03 run does not cover them.

## 6. Proposed slices and estimate

All rows are **planned**, not assigned or implemented. Estimates are focused engineer-days and exclude lock
waits and the scheduling of candidates and reviews. Proposed file names are code spans because the files do
not exist yet.

| Slice | Scope, dependencies and files | Exit check | Estimate |
|---|---|---|---|
| X03-R. Design review | Done: W367R reviewed revision 1, and this revision resolves its findings. Root owns any ADR-065 or R-035 update before implementation. | Root accepts revision 2. | Spent. |
| X03-A. Offline tool | New `scripts/repair_v180_rebuild_source.py` (`plan`, `apply`, `census`) and `tests/test_repair_v180_rebuild_source.py`. Reuses `loader_cut_nbt.py` and the evidence helpers; no runtime code. Depends on Root's acceptance. | The one-record edit; the rule of section 2.1; the fence evidence; locks; the writer's bytes; the current-root table; every refusal; no-effect failures and interrupted output. A different session reviews this saved-data writer before the merge. | 1.5 to 2 days. |
| X03-V. Gated verification mode | The Java and inventory files of section 5.1. Independent of X03-A; can run in parallel with it. | The gating tests, both inventory pins, the whole unit suite and one full GameTest run with the log checker. | 0.75 to 1 day, plus one GameTest run. |
| X03-B. Native family and oracle | `scripts/run_v180_x03_repair.py` and its tests, completed from X03-V's skeleton; the independent oracle; the two dry runs and the reasoned lists; the refusal cases and both negative controls. Depends on X03-A, X03-V and Root's review of the lists. The C-S5 assertions stay intact. | The fenced seed, two repair cycles, the per-object oracle, a real unload and launch, three refusal cases and two controls. | 1.25 to 1.5 days, plus about 4 to 6 hours of serial machine time. |
| X03-C. Root-3 integration | The scripts and tests above, and the shared accounting decoder only if needed; no format or activation change. Depends on the reviewed accounting activation and candidate. | The fresh and upgraded cases; UNKNOWN for a missing baseline; signed audit values; refusal of a future schema. | 0.5 day, plus serial reruns. |
| X03-D. Operator guidance and evidence | Root-owned guidance, ADR, risk and X03 report and matrix updates. Depends on native evidence and the owner's approval of the wording. | Evidence from committed source is reviewed; no premature PASS or admission claim. | 0.5 day. |

X03-V and X03-B together are the former X03-B, at **2 to 2.5 days**. The total after this note is about
**4.5 to 6 engineer-days**, including the review of the saved-data writer, plus serial execution and the wait
for a candidate. Revision 1 estimated 3.5 to 4.5 days; the difference is the required Java mode with its full
unit and GameTest runs, the `census` mode, the two controls and the three refusal cases. No new public
command, generic NBT editor, account-history framework, automatic repair, new save veto or repair mode
outside X03 is included.

## 7. Draft operator guidance, not approved for publication

> **When this applies.** Your server log shows the line
> `ARCE_TRANSFER_FENCED transfer=<id> reason=FLOOR_CONFLICT repair=offline-whole-world` for one transfer, and
> the rocket it names is standing still at its launch position. Worlds on current builds, where production
> cargo admission is off, are not expected to reach this state.
>
> Stop the server and preserve a hash-verified backup of the complete world, including every dimension,
> player file, ledger, journal and metadata file. Do not delete the transfer entry, reset an epoch, restore
> one store or reconstruct the historical tank. Use only the reviewed repair tool and the matching build on a
> complete copy. Review its identities, amounts, its UNKNOWN or confirmed account and its exact edit before
> you confirm the plan digest.
>
> If the tool refuses, your world has another shape. Keep the server stopped, retain the original and the
> backup, and ask for a separately reviewed repair. Do not work around a refusal.
>
> After the edit, complete two saves and restarts of the copy with the rocket's chunk loaded, and run the
> tool's census after each stop. These isolated starts advance the copy's game time and let its loaded chunks
> run, so the validated copy is slightly ahead of your original. Promote only the entire validated copy, and
> keep the original and the backup. Restarting alone is not a repair, and the historical snapshot is not a
> refund. This procedure is available only after its tool and native evidence are approved, and it does not
> enable disabled production features.

## 8. Root's answers of R463, as they stand after the review

Root answered revision 1's five questions in QUEUE row W367 (R463). The answers stay. The review changes
their wording as follows.

1. **The narrow one-record offline tool: yes.** Its scope is now the predicate of section 1.2. That
   predicate refuses the FF_KEEP_SOURCE shape explicitly, by the scope reason `DESTINATION_IN_TARGET_CHUNK`
   (R473). Generic FLOOR_CONFLICT repair remains a different assignment.
2. **An external repair account, UNKNOWN unless witnessed: yes.** Section 4.1 adds that the witness is
   optional: its absence gives UNKNOWN and does not refuse, and only a supplied witness that fails its check
   refuses.
3. **Final evidence on the accounting-enabled C4, with root-2 and root-3 cases; earlier runs are development
   evidence: yes.** On an accounting-enabled candidate the root-2 case is the upgraded sequence of section
   5.3, in which the qualifying start migrates before B is taken. A pure root-2 run is possible only on an
   accounting-disabled build and stays development evidence.
4. **A reviewed dry-run-derived file list with semantic checks, and the tamper control: yes.** The list now
   comes from at least two dry runs, is an allowance and carries a reason per entry (section 3.6). The
   control's target is chosen by rule and proven by the dry run, and the POI-specific wording is dropped. A
   second control covers the comparison inside listed files (section 5.3).
5. **A read-only review before code, a separate review of the saved-data writer before the merge, and the
   operator text to the owner only with the native evidence, in the final packet: yes.** The review before
   code is W367R, and it is done. X03 remains NOT_COVERED until Root reviews native evidence. No Gate or
   production-admission approval follows from this note.

Two points are new in revision 2 and are put to Root for acceptance with it:

- The scope predicate of section 1.2 is geometric, because nothing else separates the two shapes on disk. If
  Root prefers the review's other option, FF_KEEP_SOURCE becomes a second positive native case, and the
  predicate and its refusal case are removed. That costs about a quarter of a day in X03-B.
- The gated mode uses a new scenario value, `verify`, and three `FF_X03_` cases, rather than an external
  agent (section 5.1).

## 9. Revision 3 (Root decision R487), after W380's native runs

W380's native runs (record `W380.md` on branch `codex/v1.8.0-w380`, commit `269cf73c2`) met two facts that
revision 2 did not describe. Both are properties of the candidate and of vanilla, not of the damaged world, so
the contract changes and no check is dropped.

### 9.1 The fence evidence is a sequence of reason changes (amends 2.2 and 5.3)

`RocketTransferFences` logs `ARCE_TRANSFER_FENCED` each time a transfer's reason changes, not once per start.
When an endpoint of T joins a Level, `joined` fences T with the transient reason `ENDPOINT_JOIN_PROOF_REQUIRED`,
and the next reevaluation with both ends loaded logs the derived reason. A qualifying log therefore holds
`ENDPOINT_JOIN_PROOF_REQUIRED` lines for T around the `FLOOR_CONFLICT` line. The rule becomes:

- every `ARCE_TRANSFER_FENCED` line names T, or the tool refuses with `OTHER_TRANSFER_FENCED`;
- every reason is `FLOOR_CONFLICT` or `ENDPOINT_JOIN_PROOF_REQUIRED`, or the tool refuses with
  `FENCE_REASON_DIFFERS` (WAITING_UNLOADED and every other derived reason still refuse);
- at least one line carries `FLOOR_CONFLICT`, or the tool refuses with `FENCE_EVIDENCE_MISSING`; a log with
  only the transient reason shows that no reevaluation derived the fence;
- the plan binds the log's SHA-256 and the first `FLOOR_CONFLICT` line.

The native family asserts the same rule in both protected starts.

### 9.2 A zero-byte region file is an empty region (amends 3.5 and 5.2)

Vanilla opens region, entity and POI files with `CREATE`, so a start that only reads a region leaves a zero-byte
`.mca` file. Its `RegionFile` constructor treats a header read that returns end-of-file as an empty header, and
only warns about a header of another short length. Root checked this in the bytecode of the Forge 47.4.10
mapped jar: the constructor compares `FileChannel.read` with -1, skips the offset table on -1, and logs
"Region file {} has truncated header" for any other length but 8,192. Therefore:

- the tool and the independent oracle read a `.mca` file of exactly zero bytes as a region with no chunks;
- the file stays in the census with its size and hash, and a later change of it is judged by section 3.6;
- a file of 1 to 8,191 bytes still refuses with `CORRUPT_REGION`, which is stricter than vanilla;
- neither the tool nor the runner creates, fills, removes or normalizes such a file.
