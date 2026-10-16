# W80: releasing a rocket held by offline passengers

**Revision 3**, 2026-10-16. **Proposed design; not implementation, risk acceptance or Gate evidence.**
This replaces revision 2 (`0b7bc153`), which replaced revision 1 (`cc30fcbd`). Assignment:
[W80R2](../TASK-W80R2-OFFLINE-PASSENGER-REVISION-3.md). Root owns the ADRs, the risk register and the authorization
to implement. After this revision, Root re-checks findings N1 to N7 against the note and freezes it; no third full
review is planned. The record [W80.md](W80.md) maps every finding to the place where this revision answers it.

**Inputs.** The four files are in `D:/GitHub/ARCE-Task-Evidence/root-queue/`. Their SHA-256 hashes were checked in
this session.

| Input | File | SHA-256 |
|---|---|---|
| First review (findings R1 to R8) | `W80-review/REVIEW.md` | `8f03b9057e52fa2eb61e190145a45f21d15e83e472505dd4092539de465d8961` |
| Root's decisions 1 to 5 on it, with the correction to decision 5 | `W80-review/ROOT-DECISIONS.md` | `0748860558f1a7fc2a314699f50018c1c6bc093faf4e91ca5883aef1efe0ff83` |
| Second review (findings N1 to N13) | `W80-review-2/REVIEW.md` | `6371824378d974c6574e559f3563f65eb8e038552ed0fcff011947b3ab500972` |
| Root's decisions 1 to 8 on it | `W80-review-2/ROOT-DECISIONS.md` | `d061ca88dfb0d3b729e8eee8f984e923615ae6b6ec878e67995ebb21cbdbaedf` |

Revision 2 cited `a987b12c...` for the first decisions file. That hash belongs to the file before Root corrected
decision 5.

**Baseline.** The code baseline is `7cb8b048daafb0d4c66f284ca6d0852d29584c27`. This revision started at `d3ee072d`,
whose `src/` tree is identical to that of `7cb8b048`, so every line citation still holds. W11b2, the governed rocket
integration leaf on branch `codex/v1.8.0-w11b2-rocket-integration`, was read only through Git at
`d7431669970596b29b35bfb8f54887a650161ac4`. In code citations, `J` means
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. Bytecode checks used Forge 47.4.10, and the record
lists the classes that were read.

**Names used below.**

- T is the legacy transfer record being released, and L is its logical rocket identity.
- H1 and H2 are the two holds (section 1). MT, ME and A are the two witnesses and the released set, and AP is the
  part of A that needs placement (section 3).
- B0, S1, E1, B1, J1 and S2 are the release and seat stages (section 3). P1 and P2 are the placement generations
  (section 4). C0 to C17 are crash cuts (section 5). K is an entry key and G a placement generation (section 2).
- A code in brackets, such as (N3), names the review finding that a rule answers: R1 to R8 come from the first
  review, and N1 to N13 from the second.
- F3, F8 and "ADR-065's item N2" are open items in section 10 of
  [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md). They are per-record
  scheduling, a landed reservation released before a disassembly that can still fail, and `failBackToSource` with
  two queued generations. W11b3 is the propulsion fuel-loading leaf designed in [W11B3-DESIGN.md](W11B3-DESIGN.md).
  W44 is the leaf that added the outbox to transfer journal root 3. W63 is the native runner that demonstrated H1.

## 1. Scope and the two holds

**H1** is the nonempty offline-passenger hold that [W63, case F2_TWO](W63.md) demonstrated.
`settleRecoveredSource` (`J/rocket/server/RocketTransferRecoveryService.java:398-419`) and
`finishSourceSettlementIfComplete` (441-467) require `allPassengersOnline` (469-472), which reads **T's source
manifest**. A recovered PREPARED transfer T stays settled in RAM but retained on disk until all its remaining riders
are online at the same time. An empty manifest already passes, and a restart does not end a nonempty offline hold.
Resources, launch and disassembly stay locked, and a journal slot and a destination region stay occupied
(`RocketTransferService.resourceMutationAllowed`, `RocketDisassemblyService` and `RocketTransferSavedData`). W63's
separate reconnect sessions did not end that hold.

**During an H1 hold, the source entity's manifest can differ from T's (N2).** Recovery sets the entity's flight data
from T through `stationarySource` (310-313), which makes it ASSEMBLED or FUELED. `RocketFlightService.board` and
`leave` (`J/rocket/server/RocketFlightService.java:454-491`) accept those states (`stationary`, 521-525) whenever
`passengerMutationAllowed` (494-499) holds. It holds while T's version is acknowledged, because
`RocketTransferSavedData.resourceMutationUnproved` (373-382) then returns false. Within one session, an online rider
can therefore leave and walk away, and another player can board. Neither change touches T's source manifest, and a
restart rewrites the entity from T again.

**H2** is the live COMMITTED landed hold. `RocketDestinationCompletion.cleanupLandedIfComplete` (116-156) tests the
**current landed entity's manifest** (150-151), not T's original manifest, and boarding and leaving change that
manifest. Ordinary restart recovery settles a landed authority without the all-online check
(`RocketTransferRecoveryService.recover`, 371-373). W80 must therefore intercept restart recovery before it bypasses a
pending release.

Both holds protect the rule of [ADR-006](../../../decisions/ADR-006-ROCKET-CROSS-DIMENSION-TRANSFER.md) that an
offline rider rejoins the authority or a safe pad without any offline edit of their file. Their saved position can be
above the pad, because the rocket deliberately does not serialize as their `RootVehicle`
(`RocketEntity.hasExactlyOnePlayerPassenger`, `RocketPassengerPersistenceGameTests`). Removing T alone would strand
them. A stale manifest is also unsafe. `RocketTransferService.transferPassengers` moves the online players it names,
even if they did not board the new flight, and `tryReconnect` reads T's source manifest for an H1 record when no
settled authority is loaded (`RocketTransferRecoveryService.java:231-232`).

At the pinned W11b2 commit, legacy recovery still has these predicates. `RocketTransferFences.classify` and `permit`
route native records away from legacy recovery, and `RocketTransferEscrowRules.flightAllowed` refuses them. The
governed offline-assignment wait (item F6 of W11b2's design) remains specified by section 5.4 of `W11B2-DESIGN-2.md`
at `d7431669`, and that commit's `EscrowSettlementDisk.observe` also requires an empty disk manifest and no mounted
passengers. None of this permits releasing a governed assignment. W80 refuses native-envelope records and rockets
fenced by escrow or a transfer bay. It adds no governed-flight acknowledgement step and does not implement F3, F8,
ADR-065's item N2 or W11b3.

## 2. Storage, startup and compatibility (R5, R6)

**Choice: a separate checked rejoin file.** Root's preferred option holds, because the sidecar commits first, both
files are classified before any recovery or passenger action, and an unfinished sidecar entry is never removed merely
because T disappeared. The two files need no joint atomic replacement, because the sidecar is a write-ahead
obligation, not a second resource authority. Section 3 defines the order.

**File and type (N12).** The proposed managed type `ROCKET_REJOINS` owns
`data/advancedrocketrycommunity_rocket_rejoins.dat`, which follows the `advancedrocketrycommunity_*` names of every
managed type (`J/persistence/migration/ManagedSavedDataType.java:14-78`). It has schema 1 and the format epoch
`v1.8.0-rocket-rejoins`. The first accepted command creates the file. Startup and inspection never create an empty
file, and the file is kept after it becomes empty, because its presence documents use. `CheckedSavedDataFile.presence`
(ABSENT, PRESENT or UNKNOWN) keeps a missing file apart from an unreadable one, including in a recheck before the
first write. The sidecar reuses `CheckedSavedDataPolicy` and the journal's pattern of immutable snapshots, one active
and one successor write, and acknowledgement on the owner thread. `RocketJournalWriter` encodes transfer records and
W44's outbox and is not a multi-file writer. The sidecar therefore gets a small dedicated writer over the same
checked-file primitives: `CheckedSavedDataFile.replace` stages to `.arce-pending`, forces, reads back within bounds
and moves atomically. The journal writer's fixed target is never rebound, and no general framework replaces it.

The transfer journal stays at **root 3**, and neither its epoch nor W44's introduction constant changes. Roots 1 and
2 still migrate to 3 by adding an EMPTY outbox, and a root-3 outbox in state EMPTY, ELECTED or DELIVERED is never
reset. The sidecar is not copied into journal snapshots or migration payloads. Unrelated transfer checksums, native
envelopes, outbox event identity and delivery state survive every W80 operation. The scrubbed legacy T of section 3
gets a recomputed checksum, and no governed T is rewritten. W11b2's `EscrowTransferEnvelopeTest` root-3 assertion
and its historical root-2 fixture stay valid without weakening.

**Bounds (N12).** The sidecar holds at most 256 entries and 256 batches, with at most four entries per player and at
most 16 entries per batch, which is the passenger maximum (`RocketFlightLimits.MAX_PASSENGERS`). An entry encodes to
at most 512 bytes and a batch's shared data to at most 2,048 bytes, so the per-item maxima total 655,360 bytes. The
type's uncompressed payload limit is 1 MiB (1,048,576 bytes), checked after encoding and before publication. The
existing reader enforces that limit in three ways, and this note states it the same way:

- the compressed file may hold at most the payload limit plus 64 KiB (`maxCompressedBytes` adds `FILE_OVERHEAD_BYTES`,
  `ManagedSavedDataType.java:80` and `:211-213`);
- `BoundedSavedDataIo.readCompressed` allows the same number of inflated bytes and accounts heap at eight times that
  number (`heapAccountingFactor`);
- vanilla's compound reader refuses a depth above 512 (`CompoundTag$1.load` in 47.4.10).

The existing reader has no tag-count limit. W80 therefore adds a structural check after decoding and before any use,
`BoundedNbt.fits(root, 1_048_576, 32, 16_384)` (`J/persistence/BoundedNbt.java:25`). `AnvilRecordReader` applies the
same kind of check to region records (`J/resourceescrow/AnvilRecordReader.java:104`), but for this type it is new
code. Unknown fields or states, duplicate keys, bad references and overflows reject the whole sidecar without
truncation. Capacity counts entries that have finished only one of their two obligations.

**Batch and entry content.** A batch holds:

- an immutable release UUID and a sequence number;
- the UUIDs of T, L and the authority entity, and the hold kind, H1 or H2;
- the authority's dimension, pad position and region;
- the frozen resource vector, and T's before version and scrubbed version;
- the released set A, at most 16 player UUIDs.

L is the logical rocket identity, and a T version consists of T's checksum, phase and destination identity. No
manifest digest is stored, because after S1 no stage compares a manifest with a frozen copy (N2).

An entry has the key K, which is the pair of release UUID and player UUID. It also has the player's frozen seat index
(in ME for a player of AP, in MT otherwise) and two independent states:

- `placement` is PENDING, VERIFIED or NOT_REQUIRED. NOT_REQUIRED marks a released player who is no longer seated in L
  (section 3).
- `seat` is PENDING or VERIFIED.

A PENDING placement may carry one attempt: a fresh generation UUID G, finite x, y and z coordinates, and a target
kind of PAD or OVERWORLD_SPAWN. The batch supplies the pad's dimension. Dimension IDs are canonical ASCII resource
locations of at most 128 bytes. Coordinates obey the existing rocket and Level limits, and placement also checks
build height and the world border. The codec validates UUIDs, indices and references. No original player-file
position or resource payload is copied into the sidecar.

**Batch retirement (N9).** The generation that makes an entry's second state final also deletes the entry. If that
was its batch's last entry, the same generation deletes the batch. This always happens at or after S2, because no
entry's seat is VERIFIED before S2. The root stores `nextSequence`, a nonnegative long that only increases and is not
reset when batches are deleted. A new batch takes `nextSequence` and increments it, and an overflow refuses the
command without mutation. Sequence numbers are therefore never reused, and retired batches free their capacity.

**Whole-world startup refusal.** The type is registered in installed migration discovery and validation.
`WorldDataMigrationService.discoverSources` and `BetaWorldMigrationEvents` reject an existing root that is future,
malformed, oversized, unreadable or of unknown presence before the world starts, and they preserve its bytes. The
refusal lasts until a compatible jar or a validated whole-world repair is used. Each failed start emits one bounded
diagnostic with its stack, and repeated starts have no lifetime cap on the log. A sidecar of unknown presence or
content at startup is the only state that cannot name its players, and it never reaches a login, because the world
does not start.

**A runtime failure still names its players (N6).** The sidecar is validated at startup and never re-read at
runtime, and every pending generation is in RAM. The last acknowledged generation and the pending generations
together therefore always name every affected player. If a sidecar write fails or reports an unknown outcome, W80
keeps the obligations and the dirty state in RAM, refuses new releases, keeps every fence it holds, and continues to
guard exactly the players named there. Other players log in normally. Revision 2's whole-server login refusal for
"unknown global runtime state" is removed, because no such state exists after startup. No exception is used as
routine save control.

**Downgrade after use is unsupported.** Older jars do not know this managed type and may ignore it rather than
refuse to start, so a used world must not run them, even with an empty sidecar. Restore a consistent whole-world
snapshot taken before first use, or use a separately validated offline whole-world conversion. Never delete just the
sidecar, and never restore a single journal, player or entity file. Worlds that never use the command gain no new
file, marker or schema change. This avoids revision 1's unconditional root-4 startup failure, but it does not claim
an enforceable downgrade fence in an older binary.

## 3. Release and independent seat revocation (R3, R4, R7)

The command is `/arce rocket recover <transfer> release-absent`, and section 7 covers who may run it. It is refused
without mutation in these cases:

- the journal or the sidecar is missing or blocked, or the writer has no capacity;
- T is in neither hold, or T is a native record, or L is fenced by escrow or a transfer bay;
- the authority is not unique, or its entity sections are not loaded;
- another release of L is in progress, the absent set is empty, or a table is full;
- a released player already has a conflicting obligation (section 4), or is still seated in L and also seated
  elsewhere (below).

No chunk outside the server-recorded slots is ever loaded. Submitting the same pending T again reports its release
status instead of adding a batch. The command replies once W80 has admitted or refused the release; the later stages
report through the log and the read-only `rejoins [player]` output.

**Witnesses and the released set (R3, N2).** At admission, on the server thread:

- **MT** is T's source manifest. It is the H1 hold's own input, and only H1 uses it.
- **ME** is the authority entity's current manifest, for both holds. For H2 it is what revision 2 called M.
- **A** is the released set. For H1, A is every seat of MT whose player is offline at admission, so the release
  covers every offline seat of T. For H2, A is every seat of ME whose player is offline at admission. A former H2
  rider is therefore not in A, and a newly boarded absent H2 rider is.

Each player of A is then classified, and the classification is frozen with A:

| Player of A | Classification and effect |
|---|---|
| Seated in ME, and neither named by another active transfer nor seated in another loaded rocket | The player is in the placement set AP, and the entry has `placement = PENDING`. E1 removes the seat, and section 4 places the player. |
| Not seated in ME, such as an H1 rider who left during the hold, whether or not they are seated elsewhere | The entry has `placement = NOT_REQUIRED`. J1 removes the player from T, and E1 removes nothing. The player is neither guarded nor moved, because their saved position is wherever they went. The batch filter still keeps them from being reconnected into L. |
| Seated in ME and also named by another active transfer or seated in another loaded rocket | The whole command is refused without mutation, because one player holding two seats is a repair case. |

For H1, a player who boarded during the hold, and so is in ME but not in MT, is not in A. Their seat is untouched,
and they are not guarded. W80 consults the journal, which is in RAM, and the loaded rockets only. A seat in an unloaded rocket
with no active transfer is invisible here and stays that rocket's ordinary offline seat.

W80 freezes MT, ME, A with its classification, T's version, the authority's identity, dimension, origin and region,
and the resource vector. It reserves capacity and establishes a per-L release fence in RAM before it accepts. An
unexpected change of version or identity is a conflict, never permission to recompute A from T or to remove every
seat of L.

**The release fence (N8).** Until S2, the fence on L refuses or defers each of these paths:

- automatic settlement: `settleRecoveredSource`, `finishSourceSettlementIfComplete` and
  `RocketDestinationCompletion.cleanupLandedIfComplete`;
- ordinary recovery through `recoverNext` and `recover` (`RocketTransferRecoveryService.java:76-90` and 284-396),
  including the `stationarySource` rewrite (310-313), `rebuildSource`, and the H2 rebinding through
  `destinationSpawned` and `rebindDestination` (349-354);
- operator recovery by `/arce rocket recover <transfer>` (`J/rocket/command/RocketCommands.java:76-78`), which reaches
  `recoverById` (`RocketTransferRecoveryService.java:122-159`);
- the reconnect itself (`tryReconnect`, 210-282) and its calls to `recoverById` (245-253);
- boarding L (`RocketFlightService.board`), launch, `transferPassengers`
  (`J/rocket/server/RocketTransferService.java:602-624`), `failBackToSource` (637-667) and disassembly through
  `releaseLandedReservation` (424-447);
- resource changes to L, such as the fuel loader (`J/rocket/fuel/FuelLoaderBlockEntity.java:111`), the propulsion
  interaction (`J/rocket/fuel/RocketPropulsionInteraction.java:65`) and the release-test refuel command
  (`RocketCommands.java:233`).

A rider of L who is not in A may leave while the fence holds, because B1's predicate does not require the manifest to
stay equal to ME. Boarding L stays refused until S2.

**Stages.** A release moves through the stages in this table. After E1, the seat track (B1, J1 and S2) and the
placement track (section 4) run independently.

| Stage | Condition | Action | Result |
|---|---|---|---|
| **B0** (N3) | Admission is frozen, and the RAM fence is held. | Capture, store, force and read back the authority's entity slot, with a validator that requires exactly one copy of L showing ME and the frozen resources. | Nothing durable. On failure, W80 lifts the fence, drops the admission and logs once, and the operator may retry. |
| **S1** | B0 is confirmed. | Write the complete batch and all its entries, and obtain the exact checked-generation acknowledgement. | T and the entity stay unchanged until the acknowledgement. |
| **E1** | S1 is acknowledged. | Remove each seat whose player is in AP from the **current** manifest of every loaded copy of L. Never write "ME minus A" or any other frozen list. Fuel, debits, snapshots, escrow and inventories stay unchanged. | E1 counts as applied in RAM once the loaded copies are scrubbed and the batch filter is installed. |
| **B1** (N1, N2) | E1 is applied. | Prove every recorded slot of L in the native entity store, as described below. | The proof is held, with its watches, through S2. |
| **J1** | B1 is valid or ended as "gone", and the journal lease is fresh. | Run the narrow legacy compare-and-replace on T described below. | The exact checked journal acknowledgement. |
| **S2** | J1 is installed, and B1 is still valid. | Set `seat = VERIFIED` for every entry of the batch, and delete entries and the batch as section 2 states. | After the acknowledgement, W80 lifts the fence on L, and L accepts boarding again. |

**B0 (N3).** W80 freezes ME from RAM, but vanilla writes entity sections only at autosave or when a chunk unloads, so
the entity store can hold an older manifest. B0 closes that gap before anything durable exists. It applies the same
retained `EscrowChunkStore.captureAndStore` and `EscrowBarrier` mechanism as B1 to the authority's slot, which the
command already requires to be loaded. B0 must finish within 200 ticks; otherwise the admission is dropped as on a
failure. A player of AP who logs in during B0 is guarded (section 4). If the admission is dropped, the guard ends and
ordinary reconnect resumes for that player.

**The batch filter (N1).** From S1 until S2, every path that reads a manifest to seat or move a player first removes
the players of A. These paths are:

- the entity join of any copy of L, which reapplies E1 before any remount;
- `remountOnlinePassengers` (`J/rocket/server/RocketTransferEntities.java:287`);
- `tryReconnect`, including its use of T's source manifest for an H1 record
  (`RocketTransferRecoveryService.java:231-232`) and its `movePassenger` call (270-276);
- H1 reconstruction from the old T through `rebuildSource` and `stationarySource`;
- `transferPassengers`.

W80 loads and classifies the sidecar no later than the first `EntityJoinLevelEvent` of a rocket, so no copy of L can
join before the filter exists. After a restart, E1 therefore counts as applied as soon as the batch is classified.

**B1 (N1, N2).** B1 deliberately replaces revision 1's `SAVE_FIRST` approximation. It uses W11b2's retained
`EscrowChunkStore.captureAndStore`, the force on the store's own worker, the stable slot readback, and the watch and
invalidation mechanism in `EscrowBarrier`. This is **not** an escrow transfer or receipt, and no new injection is
authorized. B1 proves at most three distinct recorded slots: the authority's slot from the batch, and T's source and
destination slots from T's snapshots.

- **Loading (N1).** For a recorded slot whose entity sections are not loaded, B1 adds a region ticket of its own type,
  keyed by the release UUID. It follows the flight-ticket pattern (`RocketTransferEntities.java:34-39`), including the
  400-tick expiry `RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS`. The positions come only from the sidecar and T,
  never from a client. There are at most three tickets per batch and six in total, which covers the scheduler's two
  batches. A missing Level or a full cap waits without loading anything else, and a failed attempt retries no sooner
  than 200 ticks later.
- **Predicate (N2).** The readbacks must show exactly one authority copy of L across the recorded slots, with its
  identity unchanged and its resource vector equal to the frozen vector. No copy of L in any recorded slot may hold a
  seat whose player is in A. Equality with a frozen manifest is not required. The scrubbed manifest of the session and
  the manifest that recovery rewrites from T after a restart therefore both prove.
- **"Gone" (N1).** Suppose every recorded slot is loaded, captured and read back with no copy of L, and T is absent
  or at one of the batch's recorded versions. Then the seat track finishes as "gone": W80 logs it once at WARN and
  continues with J1 and S2. Anything else stays a repair case, including a missing Level, duplicates, a resource
  mismatch or an unrecorded version. The fence on L then stays, the case is logged once per transition, and placement
  is not affected.
- **Budget.** B1 uses `EscrowRuntime.barrier()` and `store(level)`, so it shares the existing global capture budget
  rather than multiplying it. It never force-saves unrelated terrain. The binding needs review against the merged
  W11b2 APIs, including proof collection across Levels and the proof's lifetime across J1. This note does not claim
  that the adapter exists.

B1's watches stay in place through J1 and the acknowledgement of S2. A superseding store invalidates the proof and
requires a bounded reverification before S2. An installed J1 is never rolled back.

**J1.** `RocketTransferSavedData.put` rejects a changed checksum for an existing T, so J1 needs a narrow, legacy-only
compare-and-replace operation, bound to the acknowledged S1 release and to T's exact before version. It removes each
player of A from T's source and destination passenger fields where present, recomputes the checksum, and keeps every
other field. It must refuse any other change, publish one journal generation, and leave the ordinary `put` invariant
in place. It is not implemented as separately persisted remove and put calls, and no checksum check is relaxed for
other callers. For H1, if every remaining seat of the scrubbed source manifest is online, T is removed through the
existing checked removal instead. For H2, the landed reservation stays. In both cases W80 awaits the exact checked
acknowledgement.

**After S2.** For H1 with another remaining rider offline, T's scrubbed record is an ordinary wait, and a later
command may release the remaining seats. H2 may settle normally.

**Restart (N8).** Every restart classifies sidecar batches **before** ordinary T recovery, rocket activation or
reconnect. T may be at the batch's before version or at its scrubbed version; W80 then reacquires B1 and finishes J1
and S2 idempotently. An absent T after J1 is continuation work, not `NOT_FOUND`. T at any other version is a
conflict: W80 logs one line, keeps the fence on L, performs no J1 or S2, and does not block placement. A
service-owned cursor schedules unfinished batches independently of T and of H1's `settledTransfers`, at most two per
tick, with failed attempts at most every 200 ticks. A declined callback cannot silently strand work.

**Later boarding is distinct by ordering.** Boarding L is refused until S2, so a manifest of L before S2 contains no
new seat. S2 proves the entity copies and the reconstructible journal copy clean before L accepts a new assignment.
Once S2 is durable, this batch never filters seats again, even if a placement is still pending. A later boarding of
the same player into the same seat is therefore not revoked, and no flight schema needs an assignment token. A player
save, a loaded entity, a Level event or absence from one chunk cannot retire revocation. Foreign copies and
whole-world rollback remain repair cases, because the proof covers the protocol's recorded slots, not an invented
scan of every entity.

## 4. Protected login and exact placement proof (R1, R2, R7)

**Who is guarded (N6).** Before login dispatch, a server-lifetime service indexes every player with a PENDING
placement entry in the acknowledged sidecar or in a pending generation, and every player of AP in an admission still
in B0 or S1. Only these players are guarded, and only they bypass ordinary reconnect. Players whose placement is
NOT_REQUIRED are not guarded; the batch filter alone keeps them out of L. The index holds at most 256 players.

**The guard starts at the first callback.** The highest-priority join and login handling installs a session guard
before ordinary reconnect, including for a login before S1 is acknowledged. `EntityJoinLevelEvent` covers the
player's entity admission, which in 47.4.10 precedes every tick and the `RootVehicle` remount, and
`PlayerLoggedInEvent` repeats the check idempotently. The protection is independent of queue capacity and of
vanilla's login invulnerability. The guard does the following:

- it stops riding and pins the authoritative position with normal teleport synchronization;
- it clears motion, fall distance, fire ticks and frozen ticks at the end of each of the player's ticks;
- it cancels damage through Forge's living attack, hurt and damage events;
- it denies harmful effects through `MobEffectEvent.Applicable`, which 47.4.10 fires from
  `LivingEntity.canBeAffected`, the check that `addEffect` uses; a wither rose applies Wither through `addEffect`
  (N13);
- it suppresses ARCE's environmental exposure and the player's boarding and interaction actions.

The guard does not persist invulnerability, game mode or no-gravity flags. A supported damage route that bypasses
it blocks implementation, because eventual relocation is not a substitute for protection.

**Status gained while guarded (N13).** `Entity.lavaHurt` calls `setSecondsOnFire` before `hurt`, so cancelled lava
damage can still leave the player burning. When the guard is installed, it records the player's active effects.
Before unguarding, W80 clears fire ticks and frozen ticks and removes every harmful effect type that was not in that
record. `forceAddEffect` fires no event, and the record covers it as well. A harmful effect that the player already
had when they logged off is left as it was saved.

**Queue and deadline (R2).** The placement and readback queue has 128 slots and runs at most four owner-thread
actions per tick. Guards use the player index, not a queue slot, and a full queue leaves the player protected without
repeated offers. Unknown journal or sidecar readiness refuses ordinary reconnect for the indexed players only.
Unavailable dimensions, delayed writers, saturation, unsafe positions and the wait for a save share a **200-tick
deadline per login**. At the deadline, W80 re-pins the player and disconnects them, still guarded, with a message
that says why and that the next login retries. It releases transient work and tickets, not obligations, and it never
exposes the player at the old seat. Operators can restore a safe pad or repair the store. The deadline bounds
connected waiting, not the life of a durable obligation. A player who should be indexed but cannot get a guard is
disconnected at once.

**The same safety checks for the pad and the fallback.** Each attempt probes at most 64 deterministic candidate
columns: 32 just outside the recorded footprint, then 32 around the Overworld's shared spawn. It uses bounded lookups
of loaded heightmaps in a fixed ring and Y order. A candidate needs solid support, a player box without collisions,
no fluid, and no fire, lava, cactus, magma or other supported damaging block. It must comply with build height and
the world border, and its environment must be survivable with the player's equipment. Collision and neighbour checks
use at most a 3-by-3-by-4 envelope in chunks that are already loaded. W80 rechecks before the teleport and before
unguarding, and an unknown hazard fails the check. `SafeCelestialTravel.prepare` supplies a height, not this proof.
W80 never uses beds or respawn anchors, never consumes charges, never writes blocks and never grants equipment. With
no safe candidate, the player waits protected and is then disconnected. At most two short-lived tickets per player and
16 in total may cover the server-recorded pad and spawn chunks, and all of them expire with the deadline. Saturation
waits without loading anything else.

**Deterministic refusal, not an arbitrary winner (R7).** A player normally has at most one unresolved placement.
Several PENDING entries for one player, or a different active transfer naming that player, yield `REJOIN_CONFLICT`.
W80 then does not choose by collection order, time or UUID, does not teleport, and retires no entry, and the guard and
the deadline apply. The released T's own revoked assignment is not a conflict. W80 refuses boarding and flight
admission for a player of AP until their placement is VERIFIED, so an old placement cannot override later travel.
Because admission also refuses or leaves out players seated elsewhere (section 3), `REJOIN_CONFLICT` is reachable only
in an inconsistent world, such as one with restored files. Several VERIFIED entries may coexist while seat proof
finishes. No forget command and no automatic winner are proposed, and inconsistent worlds need whole-world repair. A
new entry can never use an earlier save as its proof.

**Placement starts after S1 and E1, not after S2 (N1).** Once S1 is acknowledged and E1 is applied in RAM, W80
chooses a safe target and persists the attempt in checked sidecar generation **P1**: K, G, the target kind, and the
x, y and z coordinates. After the acknowledgement, it revalidates safety and the session and places the player
unmounted, with zero motion and fall distance. It sets the live player's `ForgeData.arce_passenger_rejoin` marker
(schema 1, holding K and G, at most 160 encoded bytes) through `getPersistentData()`. Vanilla serializes the marker,
and W80 never writes player data. The guard keeps the player at this exact position until proof completes. A failed or
changed target gets a new checked generation before another placement, so a later save of a moving player is never
mistaken for the specified placement. Placement needs no seat proof, because the batch filter and the boarding refusal
keep the player out of L until S2.

**Prompt saves (N4).** While any placed player awaits proof, W80 calls vanilla's public `PlayerList.saveAll()` once
right after a placement, and then at most once per 100 ticks. In 47.4.10, `saveAll` calls the protected
`save(ServerPlayer)` for each online player. This is the server's own online save, not an offline writer or an
injection. W80 does not call it while one of its own readbacks holds a player file open. Before each call, W80
re-pins every placed player who awaits proof. The calls stop when no placed player awaits proof. A normal first login
after a release therefore completes within the deadline. The deadline still applies to slow or failed saves, unsafe
positions, saturation and conflicts, and the two-real-client evidence records the observed disconnect rate.

**The save event is only a read trigger (R1).** In Forge 47.4.10, `PlayerDataStorage.save` calls
`Util.safeReplaceFile` and then fires `PlayerEvent.SaveToFile`, and `Util.safeReplaceOrMoveFile` can return normally
after a failed backup or replacement. A failed temporary write can fire no event at all. Both cases keep the
obligation. W80 retries at the next save or login, and a logout by itself proves nothing. No new injection, reflective
access to the player writer, or offline player-data writer is proposed.

**Exact position (N10).** The saved position must equal P1's coordinates exactly. W80 re-pins the player, setting the
position and zero motion and fall distance, at four points:

- at the end of each of the player's ticks;
- before each `saveAll` call that W80 makes;
- before W80's own disconnect;
- in W80's `PlayerLoggedOutEvent` handler. In 47.4.10, `PlayerList.remove` fires the logout event (offset 1) before it
  calls `save` (offset 18), so a quit that the client starts also saves the pinned position.

Vanilla's autosave runs in `tickServer` after `tickChildren` (offsets 21 and 62), and player ticks run inside
`tickChildren`, so the tick-end pin comes first. A position that still differs proves nothing and leads to a new
attempt.

**Bounded streaming readback (N5, N10).** W80 reads the installed `playerdata/<uuid>.dat`, never `.dat_old`, a
temporary file or only the live NBT. It resolves the server-owned directory and the UUID file name, and it refuses
symbolic links and files that are not regular.

- **Opening.** W80 opens the file with `Files.newInputStream` and default options. On Windows, JDK 17 then opens it
  with read, write and delete sharing, because the `WindowsChannelFactory$Flags` constructor sets all three. Vanilla
  can therefore rename the file while it is open. W80 does not use `java.io.FileInputStream` or
  `NbtIo.readCompressed(File)`.
- **Change check.** W80 records the size and last-modified time before opening and compares them after closing. Any
  difference keeps the obligation and schedules a new read. `BasicFileAttributes.fileKey()` returns null on Windows,
  so it is not used.
- **Fields.** The scan reads only the top-level `UUID`, `Dimension`, `Pos`, `Motion`, `FallDistance`, `Health` and
  `DeathTime`, whether `RootVehicle` is present, and `ForgeData.arce_passenger_rejoin`. It descends only into
  `ForgeData`. A duplicate of a needed key makes the file unverifiable.
- **Skipping.** Every other subtree is skipped without being allocated. Strings, byte, int and long arrays, and lists
  of fixed-size values are skipped by their declared lengths, each checked against the remaining byte budget first.
  Compounds, and lists of compounds or of lists, are walked header by header with an explicit depth counter. W80 uses
  its own scanner, not vanilla's `TagType.skip`: in 47.4.10, `CompoundTag$1.skip(DataInput)` recurses without a depth
  parameter, while `CompoundTag$1.load` refuses a depth above 512.
- **Ceilings.** The file may be at most 64 MiB on disk, and a larger file is not opened. A scan reads at most 512 MiB
  of inflated bytes, descends at most 512 levels, which matches what vanilla itself can load, and takes at most 10
  seconds.
- **Thread.** Scans run on one background daemon thread, one at a time, with at most one coalesced pending request
  per queued player. Results are applied on the owner thread within the budget of four actions per tick. Read errors,
  timeouts and changing files keep the obligation.
- **Proof.** The file must show the player's UUID, the exact marker K, G and schema, the exact finite dimension and
  `Pos` of P1, no `RootVehicle`, zero `Motion` and `FallDistance`, a `Health` above zero, and a `DeathTime` of zero.
- **Operator outcome above a ceiling.** The obligation stays. The player is guarded and disconnected at the deadline
  at each login, with a message that names an oversized player file. W80 logs one WARN per transition with the UUID,
  the size and the ceiling. The recovery is a validated whole-world repair under R-035. The ceilings are far above
  what vanilla loads comfortably, so a legitimate file is not expected to reach them.

**Windows overlap (N10).** With delete sharing, vanilla's rename of an open `.dat` to `.dat_old` succeeds. If a later
vanilla save deletes that `.dat_old` while a scan still holds it open, a file system without immediate delete
semantics can keep the name until the handle closes. Vanilla's backup step could then fail and return normally, as
in C9. Scans therefore start only after a save event, W80 does not call `saveAll` while a scan is open, and the time
limit closes every handle. A Windows test holds a scan open across two consecutive vanilla saves of the same player
and asserts that both replace the file. If that test fails on the supported Windows version, the implementation leaf
is blocked until the design changes, for example to a read on the server thread inside the save handler.

**Applying a result (R7).** A result applies only to the exact still-current K, G, attempt and session. A clean
logout can finish its retained attempt offline, and a later login, death or clone invalidates the old session's
result and schedules fresh verification. Duplicate save events coalesce. A successful readback permits checked
generation **P2**, which changes only that K and G's placement to VERIFIED and deletes the entry if its seat is
already VERIFIED. After P2's acknowledgement and a fresh safety check, W80 clears the status gained while guarded,
removes the marker from the live player's data (N10), and unguards the player. A marker found at login whose K and G
have no current PENDING entry is stale. It proves nothing, and W80 removes it from the live data at that login. A
crash after a matching installed save but before P2 allows verification at a guarded login without another teleport.
Old callbacks, changed generations, missing markers and saves made before placement prove nothing. A player who is
already dead or respawning keeps the obligation, and W80 classifies and places the cloned session rather than
certifying the old body's save.

**Durability boundary.** Checked sidecar and journal writes, B0 and B1 use their existing force contract. Player
readback proves the installed file for process-stop and restart tests, not an fsync of vanilla's player data. P2 does
not make power loss, file-system rollback or partial backup restoration safe. Closing that separate gap needs the
owner's approval and evidence, and the save event cannot substitute for either.

## 5. Crash cuts and retry oracles

Every restart loads and classifies the sidecar first and restores fences before any journal or entity passenger
recovery. Placement remains actionable without T. No cut transfers or refunds resources. Cuts C1 to C11 keep their
revision 2 numbers; C0 and C12 to C17 are new.

| Cut | Installed state and required continuation |
|---|---|
| C0: admitted, B0 in progress (N3) | Nothing durable exists, and T and the seats are unchanged. The RAM fence and the guards end with the process. Retry the command. |
| C1: before the S1 replacement | T and the seats are unchanged, and no acknowledged release exists. Retry the command. An UNKNOWN first-write outcome is reconciled, not assumed absent. |
| C2: S1 replaced, before its callback and E1 | The obligations and the old T and seats exist, and the disk entity shows ME because of B0. Resume the frozen release without another command, and guard a joining player of AP. Placement may start once the batch is classified. |
| C3: after E1, before B1 | The disk entity shows ME or a scrubbed manifest. Reapply E1 at load and obtain B1. A Level event retires nothing. |
| C4: B1 obtained, before J1 | The sidecar and the old T remain. Reacquire the proof and filter reconstruction before any remount, and admit no new seat or resource operation. |
| C5: J1 replaced, before its callback and S2 | T is absent (a completed H1) or scrubbed (a waiting H1, or H2). Verify the installed J1 and the entity state, then finish S2 independently of the ordinary settled set. |
| C6: S2 replaced, before its callback | Both persistent seat sources are clean, and the same generation deleted the finished entries and possibly the batch. Never reapply revocation to a later boarding. |
| C7: P1 replaced, before placement | The attempt exists, but the player's marker and position need not match. Recheck safety and place the player protected, or persist a new attempt. |
| C8: placed, before a verified save | The player file may still hold the unsafe seat. Keep PENDING, and retry protected placement and readback at login. |
| C9: save event after a failed replacement | The old file or backup is not proof. Write no P2, and keep protection and the obligation until the next save or login. |
| C10: matching readback, before P2 | Reverify the exact K and G at login, without retiring another entry or choosing by collection order. |
| C11: P2 replaced, before its callback | Placement is VERIFIED, and the entry is gone if its seat was VERIFIED. No relocation repeats. The marker may remain in the player's file; W80 removes it at the next login, and it proves nothing. |
| C12: S1 replaced, entity store older than ME (N3) | Normal operation cannot reach this cut, because B0 proved ME on disk and the fence refuses manifest and resource changes. Only an outside rollback of the entity file causes it. The filter scrubs whichever copy loads. B1 proves if the identity and resources match and no seat of A remains, and a resource mismatch is a repair case that keeps the fence without blocking placement. |
| C13: placed before S2 (N1) | P1 or P2 exists while B1, J1 or S2 is unfinished. The batch keeps filtering until S2, so reconstruction and reconnect cannot remount the player. A VERIFIED player logs in normally but cannot board L until S2, and a PENDING player is reverified or placed again. |
| C14: authority killed after S1 (N1) | B1's tickets load every recorded slot. With no copy of L in any slot, and T absent or at a recorded version, the seat track finishes as "gone"; otherwise it stays a repair case. Placement is unaffected. |
| C15: destination slot unloaded at B1 (N1) | B1 adds a ticket within the cap, captures the slot and proceeds. A missing Level or a full cap retries every 200 ticks. Placement is unaffected. |
| C16: T at an unrecorded version at restart (N8) | W80 logs one conflict line and keeps the fence on L, with no J1 or S2. Placement is unaffected. |
| C17: the generation that deletes a batch's last entry, replaced before its callback (N9) | After a restart, neither the entry nor the batch exists, and `nextSequence` is not reused. |

H2 also uses S1, S2, J1, P1 and P2, so it is not a single-generation case. Losing the gate queues the same durable
batch and does not cancel it. A superseding journal generation, including W44's delivery, needs a fresh lease.
Sidecar-only writes do not invalidate journal-root leases, but J1 does, so governed and W44 retries need tests.

## 6. Risks, logs and proposed ADR text

**No time-based release in v1.8.** A retry deadline disconnects safely and never releases another absent rider.
Without an operator, H1 remains indefinite. Unverifiable saves, unsafe destinations, conflicts and full storage can
prevent availability indefinitely across attempts. Obligations are never pruned for capacity.

**Protections that refuse or isolate (AGENTS.md section 3.3, N6).** The proposed R-035 text must carry this table.

| Protection | Scope | Duration | Log volume | Recovery |
|---|---|---|---|---|
| Startup refusal for a sidecar that is future, malformed, oversized, unreadable or of unknown presence | The whole world | Until a compatible jar or a validated whole-world repair is used | One diagnostic with its stack per failed start | A compatible jar, or whole-world repair |
| Login guard and protected disconnect | Each player named by a PENDING placement or by an admission in B0 or S1 | At most 200 ticks per login, and across logins until the placement is VERIFIED | One line per transition per player, and one per disconnect | Automatic retry at the next login; an operator can restore a safe pad |
| `REJOIN_CONFLICT` | The conflicting player | Until whole-world repair | One line per transition, and one per disconnect | Whole-world repair |
| Player file over a readback ceiling | That player | Until repair | One WARN per transition | Whole-world repair |
| Release fence | One logical rocket L | Until S2, or until a conflict is repaired | One WARN at admission, and one line per transition | Automatic, or repair for a conflict |
| Runtime sidecar write failure | New releases are refused, the named players stay guarded, and fenced rockets stay fenced | Until a successful write or a restart | One line per transition | Automatic retry, or a restart |
| Full storage | New releases are refused without mutation | Until entries retire | One reply per refused command | Automatic |

**Proposed R-035 addition for Root.** An operator release fences one legacy rocket until the checked sidecar, native
seat proof and journal settlement agree, and it can block that rocket until repair. It guards, and may disconnect at
each login, only the players it names, until their placement is proven. Full storage refuses new releases without
mutation. A future, malformed or unknown sidecar refuses the whole world's startup until compatible software or a
validated whole-world recovery is supplied. While a placed player awaits proof, the server saves all online players
at most once per 100 ticks. That cost grows with the number of online players and the size of their files, and it
ends when no placed player awaits proof. A first login can still end in a protected disconnect when I/O is slow, a
position is unsafe or capacity is saturated, and the two-client evidence records the observed rate. Whether the
command works inside a function depends on the function compilation level (section 7). Preserve bytes and
obligations, and never delete player files or restore an isolated store. Back up all Levels, entities, POI, journals,
the sidecar, player data and metadata consistently before use, downgrade or repair. Player readback covers process
stops, not power failure. B0 and B1 replace W80's unproved assumption about entity saves. They do not repair unrelated
orphan windows in ordinary recovery, `failBackToSource` or disassembly (R-032 and R-035).

**Log budget.**

- Per batch: one accepted-release WARN, one seat-completion INFO, at most one existing settlement audit, and one WARN
  for a B0 failure or a "gone" outcome.
- Per placement attempt: at most one placement line, one fallback line and one verified-placement line.
- Per transition: one line for entering a conflict, an I/O wait, an unsafe wait, ticket saturation, an oversized file
  or a timeout. Repeated retries in the same state do not log.
- One reply per refused command. A runtime root failure logs once per transition, and startup uses section 2's
  per-start diagnostic.
- UUID lists are capped at 16, and the read-only `rejoins [player]` output at eight entries per response. Nothing is
  logged per tick.

Affected players are told why they are protected or disconnected, and operators are told which proof stage is
unfinished.

**Proposed ADR-065 note.** A legacy-only operator release records bounded placement and seat-revocation obligations
in a lazy, checked, managed sidecar, and it keeps journal root 3 and the governed protocol. Before writing, it proves
the entity's current manifest on disk. It then removes only the released seats from the entity's current manifest. It
proves, in the native entity store and the reconstructible journal, that no recorded copy holds a released seat, or
that the rocket is gone, before the rocket accepts a later boarding. Players are placed as soon as the release is
durable and its seats are removed in RAM. They are placed unmounted, under bounded protection, at a probed pad or an
equally checked Overworld fallback. A placement retires only after an exact streaming readback of the installed
file's marker, dimension and position, followed by a checked acknowledgement, and never after `SaveToFile` alone. The
server's own `saveAll` is called at a bounded rate to prompt that save. No offline player-data writer, new injection
or automatic release timer is introduced. Permission level 3 is checked when the command is parsed and again when it
executes, and its effect inside functions depends on the server's function compilation level. R-035 must cover the
local holds, the startup refusal, downgrade after use, the logs, repair and the player power-loss boundary before
implementation is assigned.

## 7. Root decisions and the command policy (R8, N7, N11)

**Decisions on the first review.** Decisions 1 to 4 are reflected above: separate storage, exact readback, protected
bounded waiting, distinct authoritative absent sets, independent seat proof and conflict refusal. Revision 2 already
superseded the historical record's claims about the save event, stale seats and downgrade.

**Decision 5 as corrected (N11).** Revision 2 reported a conflict between decision 5 and the bytecode. On 2026-10-16,
Root corrected decision 5 in the section "Correction to decision 5" of `W80-review/ROOT-DECISIONS.md` (SHA-256
`0748860558f1a7fc2a314699f50018c1c6bc093faf4e91ca5883aef1efe0ff83`). The purpose is to keep **automatic** origins
out, and an operator who runs a function that contains `release-absent` has chosen to run it. The policy is
permission level 3 or higher. Tick, load and scheduled functions and command blocks are refused, and operators,
functions an operator runs, the console and RCON are accepted. No origin-provenance mechanism is needed, and none is
authorized. The command policy is therefore decided, not open.

**Decisions on the second review.** Root accepted every finding and decided how revision 3 applies them. Decision 1
(N1) is applied in sections 3 to 5, decision 2 (N2) in sections 1, 3 and 4, decision 3 (N3) in sections 3 and 5,
decision 4 (N4) in sections 4 and 6, decision 5 (N5) in section 4, decision 6 (N6) in sections 2, 4 and 6, decision 7
(N7) in this section and Appendix A, and decision 8 (N8 to N13) throughout.

**Why level 3 does not exclude every function.** This reasoning is kept from revision 2. `FunctionCommand.runFunction`
passes `withMaximumPermission(2)`, and in the pinned jar that method returns the existing source when its level is
already 3 or 4. It is a floor, not a cap. Tick, load and scheduled functions use
`ServerFunctionManager.getGameLoopSender` at level 2, while functions that an operator or the console runs keep the
higher level. `execute as` changes the entity, not the original permission, so a level-2 command block cannot gain
access by impersonating an operator. There is no public `ServerFunctionManager.isInFunction` in this baseline. Entity
type is not provenance, and neither is suppressed output, because `withSuppressedOutput()` also returns the same
object for sources whose `alwaysAccepts()` is true. No reflection, stack inspection or command rewriting is proposed.

**Compile time and execution time (N7).** A function is parsed once, with a source at the server's function
compilation level. In 47.4.10, `DedicatedServer.getFunctionCompilationLevel` returns the `function-permission-level`
property, whose default is 2; `IntegratedServer` returns 2, and `GameTestServer` returns 4. Brigadier checks
`requires` only while parsing. In brigadier 1.1.8, `CommandDispatcher.execute(ParseResults)` contains no requirement
check, and `CommandFunction$CommandEntry.execute` replays the stored parse with the caller's source. The consequences
are these:

- On a default dedicated server or in single player, a function that contains `release-absent` fails to load, and
  `ServerFunctionLibrary` logs "Failed to load function" at ERROR. "Functions an operator runs are accepted" therefore
  holds only where `function-permission-level` is 3 or 4.
- At level 3 or 4, tick, load and scheduled functions compile the command, and only the check at execution refuses
  the level-2 game-loop sender. The command's handler must therefore call `source.hasPermission(3)` itself, in
  addition to the leaf's `requires`. This execution-time check is **mandatory**, and it runs before any read of T or
  the sidecar.
- GameTest compiles at level 4, so GameTest results do not represent a default dedicated server.

**Origins and their levels in 47.4.10.**

| Origin | Level | Result |
|---|---|---|
| Console | 4 | Accepted |
| RCON | 4 | Accepted |
| Operator player (the default operator level is 4) | 3 or 4 | Accepted |
| Player at level 2 | 2 | Refused at parse |
| Command block | 2 | Refused at parse |
| Minecart command block | 2 | Refused at parse |
| Tick, load and scheduled functions (the game-loop sender) | 2 | Refused at load with compile level 2, or at execution with compile level 3 or 4 |
| Advancement reward function (`AdvancementRewards` uses `withPermission(2)`, which sets the level exactly) | 2 | Refused at load or at execution, as above |
| Function that an operator runs | At least the operator's level | Accepted with compile level 3 or 4; fails to load with compile level 2 |
| Any of the above through `execute as` | The original level | As for the original origin |

**Other recommendations** stay. Keep H2 in scope and governed records excluded. Use native-store proof instead of an
approximation by Level saves. Add no forget command. Accept protected disconnects only with the listed verification.
Root registers the risks and the ADR clarification, and reviews the merged W11b2 barrier binding, before it assigns
implementation.

## Appendix A. Verification plan and cost (not executed)

**A0 unit tests.**

- Every codec bound; rejection of duplicates, bad references and unknown states; the first-write race; the
  `BoundedNbt` check at depth 32 and 16,384 tags; and the monotonicity and overflow of `nextSequence` (N9, N12).
- Capacity with retired batches: fill 256 batches, retire them, and admit new ones (N9).
- Command refusals, including the double-seat refusal and the NOT_REQUIRED classification (N2).
- The H1 and H2 absent sets from MT and ME, and E1's removal from the current manifest. B1's predicate passes when a
  rider outside A has left, and fails when a seat of A remains or a resource differs (N2).
- B0 confirmation and failure; the "gone" outcome; and ticket caps, expiry and the 200-tick retry (N1, N3).
- Several PENDING entries, newer-transfer conflicts, stale K, G or session results, duplicate saves, logout, clone,
  and callbacks that are declined and then retried.
- The legacy compare-and-replace refuses native records, stale versions and every change outside the passenger
  fields, while ordinary `put` still rejects a checksum replacement.
- The streaming scanner, with a valid file over 2 MiB, depth 513, a duplicate needed key, a declared length beyond the
  budget, a file over the disk ceiling (which is not opened), and the time limit (N5).
- Fake worlds that check support, collision, fluids, hazards, the border and the atmosphere, and the candidate, ticket
  and queue budgets.
- Temporary-write failure, a replacement or backup failure that returns normally, and a successful installed-file
  proof, each tested separately. A synthetic `SaveToFile` alone is never a success oracle.
- New test classes that touch Minecraft classes bootstrap Minecraft and run alone once.

**Migration and integration.** Startup without use creates no sidecar. Journal roots 1 and 2 migrate to 3, root-3
EMPTY, ELECTED and DELIVERED outboxes round-trip, and the sidecar's schema 1 round-trips on its own. A malformed or
future sidecar refuses startup with unchanged bytes across repeated attempts. S1, S2, P1, P2 and J1 interleave with
W44's election and delivery and with governed journal writes. The tests assert that unrelated checksums, envelopes
and the full outbox are preserved and that leases retry. Older-jar fixtures without use stay compatible. Downgrade
after use is unsupported and is not claimed to be blocked automatically.

**GameTests.** About 26 focused tests plus parameterized cases:

- two absent riders; mixed online and offline riders; and H2 with a departed original rider and a new absent rider;
- an H1 rider who left and is offline elsewhere, who is not guarded, moved or reconnected into L; one who boarded
  another rocket, who is left out of placement, or whose command is refused while they are still seated in L; and a
  newcomer who boarded during the hold, who is untouched (N2);
- a rider outside A leaving under the fence, and boarding L refused until S2 (N8);
- placement before S2, a killed authority, and an unloaded destination slot loaded by B1's ticket (N1);
- logins before S1's acknowledgement, during B0, and after a dropped admission;
- gate loss and retry at E1, B1 and J1; a full queue; and a delayed writer and an unloaded pad;
- low-health logins with an obstruction, the void, lava, fire or a wither rose at the old seat, which assert no
  burning, Wither or freezing after unguarding (N13);
- a hazardous spawn with no safe candidate, ending in a timeout; disassembly followed by a login; player-first and
  entity-first load order; a remaining rider's logout; and a later boarding of the same player into the same seat;
- multiple and newer-transfer conflicts, and repeated saves and a logout before the acknowledgement;
- `saveAll` at most once per 100 ticks with re-pinning, and a client quit between pins that saves the exact position
  (N4, N10);
- on Windows, a readback held open across two vanilla saves; and marker removal after P2 and at login (N10);
- real temporary-write and replacement failures, and an unaffected ordinary reconnect;
- the command matrix at compile level 4 (N7): players at levels 2, 3 and 4, the console, RCON, command blocks and
  minecart command blocks, tick, load and scheduled functions, an advancement reward function, an operator's
  function, and nested `execute` for each. Automatic origins are refused at execution with no sidecar change, and the
  operator's function is accepted.

**Native restarts.** Extend W63's runner and probe only in later code tasks. Use gated checked-writer cuts, told apart
by the sidecar or journal payload and stage, plus the existing native-store cuts. Inject real file-system failures in
disposable fixtures, not merely events. Inspect the exact sidecar K, G and states; the journal, its outbox and the
checksums; entity rosters and copies; each player's dimension, `Pos`, marker, `RootVehicle` and health; unrelated
players' positions; and unchanged fuel vectors. Create prerequisites in the first counted JVM. H2 must reach landing
and be cut in that same session, so that the setup does not end its hold by restarting. A dedicated server with
`function-permission-level=2` loads a datapack function that contains the command and must reject it at load. Its
"Failed to load function" ERROR line is declared in that run's expected-log manifest, and in
`scripts/gametest_expected_log.json` only if a GameTest run ever produces it (N7).

| Campaign | Counted starts |
|---|---:|
| Basic H1 two-rider release, and disassembly followed by rejoin: the original seven starts plus six stages. | 13 |
| C1 to C11, each with one seed-and-cut start and two recovery starts. | 33 |
| H2 at C2, C3, C5, C6 and C10, each with one live-landing-and-cut start and two recovery starts. | 15 |
| Temporary-write failure, backup or replacement failure, and a successful save, three starts each. | 9 |
| Player-first and entity-first load, later same-seat boarding, login before S1, multiple and newer conflicts, and a queue or hazard timeout, three starts each. | 18 |
| An old jar without use, repeated rejection of a malformed root and of a future root, and W44 or governed interleaving, three starts each. | 12 |
| C0 and C12 to C17, each with one seed-and-cut start and two recovery starts (N1, N3, N8, N9). | 21 |
| An H1 restart between E1 and J1 with a diverged entity manifest (N2). | 3 |
| A player file over 2 MiB, and a file over the disk ceiling (N5). | 3 |
| Rejection at load with `function-permission-level=2` (N7). | 1 |
| **Total planning allowance.** | **128** |

Revision 1's unmeasured assumption is 20 seconds per start plus 15 minutes of setup, with a 1.5 allowance. The
estimate is therefore `(128 * 20 + 900) * 1.5 / 3600 = 1.44` heavy-lock hours. Allow about 0.4 more hours for one
runData and one GameTest run; setup, review and failure diagnosis can add to this. The two-real-client evidence (N4)
is separate and not counted. No game, Gradle or GameTest ran for this revision.

## Appendix B. Implementation leaves after Root's check

- **W80-storage (planned).** Add the sidecar model, the bounded checked writer, lazy startup integration and the
  codecs. It depends on Root's risk and ADR registration. Verify A0, migration without use and malformed startup
  without enabling command admission.
- **W80-release (planned).** Bind the frozen H1 and H2 planning, the fences, B0, B1, J1 and S2, B1's tickets and retry
  scheduling against merged W11b2. Before enabling release, verify the load-order, later-boarding and new cuts,
  resource conservation, and the W44 and governed interleavings.
- **W80-rejoin (planned).** Add protection, safe placement, prompt saves, the streaming readback, exact retirement
  and the command adapter, with the corrected decision 5 and the mandatory execution-time check. It depends on both
  earlier leaves. Run the combined unit, GameTest and native matrix, and obtain two-real-client evidence later.

Each leaf needs its own read-only review under AGENTS.md section 13. These are v1.8 suggestions, not delegated tasks
or completion claims. This revision changes only this note and [W80.md](W80.md). It edits no code, test, ADR, risk
register, status file, ledger or build file.
