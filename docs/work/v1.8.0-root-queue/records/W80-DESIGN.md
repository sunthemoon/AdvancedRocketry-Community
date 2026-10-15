# W80: releasing a rocket held by offline passengers

**Revision 2**, 2026-10-16. **Proposed design; not implementation, risk acceptance or Gate evidence.**
This replaces revision 1 (`cc30fcbd`). Assignment: [W80R](../TASK-W80R-OFFLINE-PASSENGER-REDESIGN.md).
Root owns the ADRs, risk register and implementation authorization. A fresh independent review is required.
Section 7 records a factual conflict with Root's command-policy decision 5; it is not silently resolved.

**Inputs.** The review is `D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/REVIEW.md`, SHA-256
`8f03b9057e52fa2eb61e190145a45f21d15e83e472505dd4092539de465d8961`. Root's decisions are
`D:/GitHub/ARCE-Task-Evidence/root-queue/W80-review/ROOT-DECISIONS.md`, SHA-256
`a987b12c2e56a209454b57ac23d2c64a047885766640b102872189dbebfb88b2`. Both hashes were checked.
The local code baseline is `7cb8b048daafb0d4c66f284ca6d0852d29584c27`. W11b2 was read **only through Git**
at `d7431669970596b29b35bfb8f54887a650161ac4`, not in its worktree. In code citations below, `J` means
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity`. Class/method names identify these pinned
sources. Bytecode checks used Forge 47.4.10; the class inventory is in [the record](W80.md).

## 1. Scope and the two holds

**H1** is the nonempty offline-passenger hold demonstrated by [W63, F2_TWO](W63.md).
`J/rocket/server/RocketTransferRecoveryService.java`, `settleRecoveredSource` (398-419), and
`finishSourceSettlementIfComplete` (441-467), require `allPassengersOnline` (469-472). This predicate uses
**T's source manifest**. A recovered PREPARED transfer T remains settled in RAM but retained on disk until
all its remaining riders overlap online. An empty manifest already passes; restarting does not end the
nonempty offline hold. Resources, launch and disassembly remain locked, and a journal slot and destination
region remain occupied (`RocketTransferService.resourceMutationAllowed`, `RocketDisassemblyService`, and
`RocketTransferSavedData`). W63's separate reconnect sessions did not end that hold.

**H2** is the live COMMITTED landed hold. `RocketDestinationCompletion.cleanupLandedIfComplete` (150-156)
tests the **current landed entity's manifest**, not T's original manifest. Boarding and leaving change that
manifest (`RocketFlightService.board/leave`, 454-498). Ordinary restart recovery settles a landed authority
without the all-online check (`RocketTransferRecoveryService.recover`, 371-373). W80 must intercept restart
recovery before it bypasses a pending release.

Both preserve the pointer required by [ADR-006](../../../decisions/ADR-006-ROCKET-CROSS-DIMENSION-TRANSFER.md):
an offline rider must rejoin the authority or a safe pad without editing their file offline. Their saved
position can be above the pad; the rocket deliberately does not serialize as their `RootVehicle`
(`RocketEntity.hasExactlyOnePlayerPassenger`, `RocketPassengerPersistenceGameTests`). Removing T alone
would strand them. A stale manifest is also unsafe: `RocketTransferService.transferPassengers` moves online
UUIDs named by it, even if they did not board the new flight.

At the pinned W11b2 commit, legacy recovery still has these predicates. `RocketTransferFences.classify/permit`
routes native records away from legacy recovery, and `RocketTransferEscrowRules.flightAllowed` refuses them.
The governed F6 offline-assignment wait remains specified by `W11B2-DESIGN-2.md` section 5.4; that commit's
`EscrowSettlementDisk.observe` also requires an empty disk manifest and no mounted passengers. These are not
permission to release a governed assignment. W80 refuses native-envelope records and escrow/bay-fenced
rockets. It adds no governed-flight acknowledgement step and does not implement F3, F8, N2 or W11b3.

## 2. Storage choice, startup and compatibility (R5, R6)

**Choose the separate checked rejoin file.** Root's preferred option holds if the sidecar commits first,
both files are classified before recovery/passenger actions, and an unfinished sidecar entry is never
removed merely because T disappeared. Atomic replacement of both files together is unnecessary: the
sidecar is a write-ahead obligation, not a duplicate resource authority. Section 3 defines the ordering.

Proposed managed type `ROCKET_REJOINS` owns `data/arce_rocket_rejoins.dat`, schema 1, format epoch
`v1.8.0-rocket-rejoins`. Creation is lazy on the first accepted command. Distinguish missing from unreadable
or invalid presence, including a first-write existence recheck. Ordinary startup/inspection does not create
an empty file. Keep the file after it becomes empty; its presence documents use. Reuse
`CheckedSavedDataPolicy` and the journal's immutable-snapshot, one-active/one-successor, owner-thread
acknowledgement pattern. `RocketJournalWriter` encodes transfer records and W44's outbox; it is not a
multi-file writer. Give the sidecar a small dedicated writer/checkpoint over the same checked-file
primitives. Never rebind the journal writer's fixed target or replace it with a new general framework.

The transfer journal stays **root 3**, with its epoch and W44 introduction constant unchanged. Roots 1/2
still migrate to 3 by adding EMPTY; a root-3 EMPTY, ELECTED or DELIVERED outbox is never reset. The sidecar
is not copied into journal snapshots/migration payloads. Unrelated transfer checksums, native envelopes,
outbox event identity and delivery state survive every W80 operation. The deliberately scrubbed legacy T
in section 3 gets a recomputed checksum; no governed T is rewritten. W11b2's `EscrowTransferEnvelopeTest`
root-3 assertion and historical root-2 fixture remain valid without weakening.

**Bounds and identity.** There are at most 256 passenger obligations and 256 release batches, with four
obligations per player. Each passenger entry is at most 512 encoded bytes; shared batch metadata is at
most 2,048 bytes. The entire encoded root, including framing, is at most 1 MiB, checked before publication
and after encoding. Reader limits are 1 MiB compressed/inflated, 8 MiB accounting, depth 32 and 16,384 tags;
declared lengths must fit the remaining byte budget. This is a separate-file budget, not a larger journal.
Unknown fields/states, duplicates, bad references and overflows reject the whole sidecar without truncation.
Capacity includes completed entries awaiting their other obligation.

A batch has an immutable release UUID, monotonic nonnegative sequence, T/L/authority UUIDs, H1/H2, authority
dimension and pad/region, frozen before/after manifest digests, and before/scrubbed T versions. L is the
logical rocket identity; a T version includes checksum, phase and destination identity. A passenger entry
has key K = (release UUID, player UUID), frozen seat index, independent `placement = PENDING | VERIFIED`
and `seat = PENDING | VERIFIED`, and an optional placement attempt. An attempt has a fresh generation UUID
G, finite x/y/z and PAD or OVERWORLD_SPAWN target kind. The batch supplies the pad dimension. Dimension IDs
are canonical ASCII ResourceLocations of at most 128 bytes. Coordinates obey existing rocket/Level limits;
placement also checks build height and border. Validate UUIDs, sequence overflow, indices and references.
The existing passenger maximum is 16. No original player-file position or resource payload is copied.

**Whole-world startup refusal.** Register the type in installed migration discovery and validation.
`WorldDataMigrationService.discoverSources` and `BetaWorldMigrationEvents` reject an existing future,
malformed, oversized or unreadable root before world startup, preserving its bytes. This lasts until a
compatible jar or validated whole-world repair is used. Emit one bounded startup diagnostic with its stack
per failed attempt; repeated launches have no lifetime log cap. Runtime write failure instead retains
obligations/dirty state and fences affected rockets. If runtime validity is lost, stop W80 mutations. Unknown
affected identities require a global rocket-passenger admission hold and protected login refusal, not
assuming absence. No exception is used as routine save control.

**Downgrade after use is unsupported.** Older jars do not know this managed type and may ignore it rather
than refuse startup. A used world must not run them, even with an empty sidecar. Restore a consistent
pre-use whole-world snapshot or use a separately validated offline whole-world conversion. Never delete
just the sidecar or restore one journal/player/entity file. Worlds that never use the command acquire no
new file, marker or schema change. This avoids revision 1's unconditional root-4 startup failure; it does
not claim an enforceable downgrade fence in an older binary.

## 3. Release and independent seat revocation (R3, R4, R7)

The command is `/arce rocket recover <transfer> release-absent`; authorization is discussed in section 7.
Refuse without mutation for a missing/blocked journal or sidecar, wrong hold, native record, escrow/bay
fence, nonunique authority, unavailable authority entity sections, busy release, empty absent set, full
table, conflicting player obligation or unavailable writer capacity. No arbitrary chunk is loaded. Repeated
submission of the same pending T reports its release status, rather than appending another batch.

For **H1**, M is T's source manifest. For **H2**, M is the unique loaded landed authority's current manifest.
A is exactly the seats in M whose players are offline at admission. Freeze M, A, T's version, authority
identity, dimension, origin, region and resource vector on the server thread. Reserve capacity and establish
a per-L release fence before accepting. Boarding/leaving, remounting, automatic settlement, transfer
passenger movement, launch, disassembly and resource mutation of L consult it. An H2 former rider is not
in A; a newly boarded absent rider is. An unexpected manifest/version change is a conflict, not permission
to recompute A from T or remove every seat of L.

| Stage | Required proof and action |
|---|---|
| **S1** | Write the complete batch and both obligations to the sidecar; obtain its exact checked-generation acknowledgement. T and entity seats remain unchanged before acknowledgement. A login during the write is protected and bypasses ordinary reconnect. Online status changes do not change the accepted A. |
| **E1** | Revalidate the frozen authority and M under the release fence, then remove exactly A from the entity manifest. The intended manifest is M minus A. Reconstruction/remount filters consult the durable batch before moving any player. Fuel, debits, snapshots, escrow and inventories remain unchanged. |
| **B1** | Prove the scrubbed entity manifest through the native entity store, as described below. A Level save notification is not proof. Retain the fence and seat obligations while proof is unavailable. |
| **J1** | With B1 valid and a fresh journal lease, perform narrow legacy settlement. H1 removes T only if every remaining unreleased seat is online; otherwise retain T with the scrubbed manifest. H2 keeps the landed reservation but rewrites its manifests from M minus A, not the obsolete source roster. Scrub both source/destination passenger fields consistently, recompute the checksum, and preserve all nonpassenger resource fields. Await the exact checked journal acknowledgement. |
| **S2** | After installed J1 and entity proof agree, persist `seat = VERIFIED` for this batch's exact keys and acknowledge that sidecar generation. Only then remove the per-L fence and permit new explicit boarding. H1 with another rider now offline remains an ordinary wait on its scrubbed T; a later command may release remaining seats. H2 may settle normally. |

`RocketTransferSavedData.put` rejects a changed checksum for an existing T. J1 therefore needs a narrow,
legacy-only compare-and-replace operation, bound to the acknowledged S1 release and the exact old T version.
It must validate that only the specified passenger fields/checksum change, publish one journal generation,
and retain the ordinary `put` invariant. Do not implement this as separately persisted remove/put calls or
relax checksum checks for other callers. H1 removal uses the existing checked removal path under the fence.

B1 deliberately replaces revision 1's `SAVE_FIRST` approximation. Use W11b2's retained
`EscrowChunkStore.captureAndStore`, own-worker force, stable slot readback and watch/invalidation mechanism
in `EscrowBarrier`; this is **not** an escrow transfer or receipt. Add a narrow passenger-manifest predicate,
not `EscrowSettlementDisk`'s empty-rocket predicate. No new injection is authorized. Capture already-loaded
source/destination/authority entity slots (at most three distinct slots). Prove one authority with the exact
scrubbed manifest and unchanged resources, and no stale logical copy in the other recorded slots. Missing
authority, duplicates, unloaded slots, changed generations or an unknown moved copy retain the obligation.
Do not force-save unrelated terrain. Use `EscrowRuntime.barrier()` and `store(level)` so the existing global
capture budget is shared rather than multiplied. The binding needs review against merged W11b2 APIs, including cross-Level proof
collection and proof lifetime across J1; this note does not claim that adapter already exists.

Keep B1 watches through J1 and S2 acknowledgement. A superseding store invalidates the proof and requires
bounded reverification before S2; an installed J1 is not rolled back. On restart, classify sidecar batches
**before** ordinary T recovery, rocket activation or reconnect. Accept only the recorded before/scrubbed
after versions, reacquire B1, and finish J1/S2 idempotently. An absent T after J1 is continuation work, not
`NOT_FOUND`. H1 reconstruction from old T applies A before remount/save. H2 restart cannot bypass the fence.
A service-owned cursor schedules unfinished batches independently of T and H1's `settledTransfers`, at most
two per tick, with failed attempts at most every 200 ticks. Declined callbacks cannot silently strand work.

**Later boarding is distinct by ordering.** Until S2, later boarding is refused, so a pre-S2 manifest is
only the frozen assignment or its exact scrubbed result. S2 proves both the entity and reconstructible
journal copies clean before a new assignment is allowed. Once S2 is durable, this batch never filters seats
again, even if placement remains pending. A later boarding of the same UUID into the same seat is therefore
not revoked. This avoids changing every flight schema to add assignment tokens. Player save, a loaded
entity, a Level event, or absence from one chunk cannot retire revocation. Foreign copies/whole-world
rollback remain repair cases; proof covers protocol-recorded slots, not an invented global entity scan.

## 4. Protected login and exact placement proof (R1, R2, R7)

**Waiting starts at admission.** A server-lifetime service indexes validated entries and admitted S1 work
before login dispatch. Highest-priority server-player join/login handling installs a session guard before
ordinary reconnect, including login before S1 acknowledges. `EntityJoinLevelEvent` covers earlier entity
admission; `PlayerLoggedInEvent` repeats the check idempotently. Protection is independent of queue capacity
and vanilla login invulnerability. Stop riding, pin the authoritative position with normal teleport
synchronization, clear motion/fall distance at player tick boundaries, cancel damage through Forge living
attack/hurt/damage hooks, and suppress ARCE environmental exposure and boarding/interaction actions.
Do not persist invulnerability, game mode or no-gravity flags. Test low-health, void, fire, suffocation and
atmosphere from the first callback. A supported damage route bypassing this guard blocks implementation;
eventual relocation is not a substitute for protection.

The placement/readback queue has 128 slots and at most four owner-thread actions per tick. Guards use the
admitted player/obligation index, not a queue slot (at most 256 affected players). A full queue leaves the
player protected without repeated offers. Unknown journal/sidecar readiness or identity refuses ordinary
reconnect. Unavailable dimensions, delayed writers, saturation and unsafe positions share a **200-tick
per-login deadline**. At the bound, disconnect with an actionable message while still guarded; release
transient work/tickets, not obligations. Never expose the player at the old seat. A new login retries;
operators can restore a safe pad or repair the store. This bounds connected waiting, not the duration of
a durable obligation. Unknown global runtime state refuses new logins under the same policy, without an
unbounded backlog. An unindexed admission that cannot acquire a guard is disconnected immediately.

**Same safety checks for pad and fallback.** Probe at most 64 deterministic candidate columns per attempt:
32 just outside the recorded footprint, then 32 around the Overworld shared spawn. Use bounded loaded
heightmap lookups and a fixed ring/Y order. Require solid support, a collision-free player AABB, empty
fluids, no fire/lava/cactus/magma or other supported damaging block, build-height/world-border compliance,
and an environment survivable with the player's equipment. Collision/neighbour checks use at most a
3-by-3-by-4 envelope and already-loaded chunks. Recheck before teleport and unguarding. Unknown hazards
fail. `SafeCelestialTravel.prepare` supplies a height, not this proof. Never use beds/anchors, consume
charges, write blocks or grant equipment. No safe candidate means protected waiting, then disconnect.
At most two short-lived tickets per player and 16 globally may cover server-recorded pad/spawn chunks;
all expire with the deadline. Saturation waits without loading elsewhere.

**Deterministic refusal, not an arbitrary winner.** Normally admit only one unresolved placement per UUID.
Multiple PENDING entries, or a different active transfer naming that player, yield `REJOIN_CONFLICT`: do
not choose by collection order/time/UUID, teleport, or retire any entry. The guard/deadline applies. The
released T's own revoked assignment is not a conflict. Refuse new boarding/flight admission for a player
until placement is VERIFIED, so old placement cannot override later travel. Multiple VERIFIED entries may
coexist while seat proof finishes. No forget command or automatic conflict winner is proposed; inconsistent
worlds need whole-world repair. New entries cannot piggyback on a previous save.

**Place with an exact marker.** After S2, choose a safe target and persist attempt (K, G, target kind,
x/y/z) in checked sidecar generation **P1**. After acknowledgement, revalidate safety/session and place
unmounted with zero motion/fall distance. Set the live player's `ForgeData.arce_passenger_rejoin` marker
(schema 1, K and G, at most 160 encoded bytes) via `getPersistentData()`. Vanilla serializes it; W80 never
writes playerdata. Keep the guard at this exact position until proof completes. A failed/changed target
gets a new checked generation before another placement. This prevents a later moving-player save from
being mistaken for the specified placement.

`PlayerEvent.SaveToFile` is **only a read trigger**. In Forge 47.4.10, `PlayerDataStorage.save` calls
`Util.safeReplaceFile` then fires the event; `Util.safeReplaceOrMoveFile` can return normally after failed
backup/replacement. Temporary-write failure can emit no event. Both retain the obligation. Retry on the
next save or login; logout itself proves nothing. No new injection, reflective player-writer access or
offline playerdata writer is proposed.

Read installed `playerdata/<uuid>.dat`, never `.dat_old`, a temporary file or only live NBT. Resolve the
server-owned directory/UUID filename, refuse symlinks/nonregular files, and compare identity/size/timestamps
around a bounded read. Require correct player UUID, exact marker K/G/schema, exact finite dimension/Pos
matching P1, no RootVehicle, zero motion/fall distance and no dead-player state. Limits are 2 MiB
compressed/inflated, 16 MiB accounting, depth 64 and 65,536 tags, including declared-length checks.
Oversize is unverifiable, not deleted. Reuse the bounded SavedData reader's quota approach, not its `data`
wrapper/schema or an unlimited `NbtIo.readCompressed` call. One background read can run with at most one
coalesced pending request per queued player; results use the four-action owner-thread budget. Read errors
or changing files retain the obligation.

Apply a result only to the exact still-current (K, G, attempt, session). A clean logout can finish its
retained attempt offline; later login/death/clone invalidates the old session result and schedules fresh
verification. Duplicate save events coalesce. Successful readback permits checked generation **P2** to
change only that K/G's placement to VERIFIED. After its acknowledgement and a fresh safety check, unguard
the online player. Delete an entry only when **both** placement and seat are VERIFIED. A crash after a
matching installed save but before P2 allows guarded login verification without another teleport. Old
callbacks, changed generations, missing markers and pre-placement saves prove nothing.

The deadline also applies while waiting for a vanilla save. W80 does not force `saveAll` or add access to
protected `PlayerList.save`. Ordinary logout saving may finish proof after timeout; otherwise the next
login retries. This availability cost is explicit: autosave is not assumed to occur within 200 ticks.
Already-dead/respawning players retain their obligation; reclassify/place the cloned session rather than
certifying the old body's save.

**Durability boundary.** Checked sidecar/journal writes and B1 use their existing force contract. Player
readback proves the installed file for process-stop/restart tests, not fsync of vanilla playerdata.
Power loss, filesystem rollback and partial backup restoration are not claimed safe by P2. Closing that
separate gap needs owner approval and evidence; the save event cannot substitute for either.

## 5. Crash cuts and retry oracles

Every restart loads/classifies the sidecar first and restores fences before journal/entity passenger
recovery. Placement remains actionable without T. No cut transfers or refunds resources.

| Cut | Installed state and required continuation |
|---|---|
| C1: before S1 replacement | T/seats are unchanged; no acknowledged release exists. Retry the command. Reconcile UNKNOWN first-write outcomes instead of assuming absence. |
| C2: S1 replaced, before callback/E1 | Obligations and old T/seats exist. Resume the frozen release without another command; protect a joining member of A. |
| C3: after E1, before B1 | Disk entity can be old or scrubbed. Reapply the exact frozen revocation and obtain native-store proof. A Level event retires nothing. |
| C4: B1 obtained, before J1 | Sidecar and old T remain. Reacquire proof and scrub reconstruction before remount; admit no new seat/resource operation. |
| C5: J1 replaced, before callback/S2 | T is absent (completed H1) or scrubbed (waiting H1/H2). Verify installed J1/entity state, then finish S2 independently of the ordinary settled set. |
| C6: S2 replaced, before callback | Both persistent seat sources are clean. Never reapply revocation to later boarding. Placement remains independent. |
| C7: P1 replaced, before placement | Attempt exists but player marker/position need not match. Recheck safety and place protected, or persist a new attempt. |
| C8: placed, before verified save | Player file may still contain the unsafe seat. Retain PENDING and retry protected placement/readback at login. |
| C9: save event after replacement failed | Old file/backup is not proof. Write no P2; retain protection/obligation until the next save/login. |
| C10: matching readback, before P2 | Reverify exact K/G on login, without retiring another entry or choosing by collection order. |
| C11: P2 replaced, before callback/deletion | Placement is VERIFIED; seat status decides whether the remaining entry stays. No repeated relocation is required. Cleanup is idempotent. |

H2 uses S1/S2, J1 and P1/P2 too; it is not a single-generation case. Gate loss queues the same durable
batch, not cancellation. A superseding journal generation, including W44 delivery, needs a fresh lease.
Sidecar-only writes do not invalidate journal-root leases; J1 still does, so governed/W44 retries need tests.

## 6. Risks, logs and proposed ADR text

**No time-based release in v1.8.** A retry deadline disconnects safely; it never releases another absent
rider. Without an operator, H1 remains indefinite. Unverifiable saves, unsafe destinations, conflicts and
full storage can prevent availability indefinitely across attempts. Never prune obligations for capacity.

**Proposed R-035 addition for Root:** A release fences one legacy rocket until checked sidecar creation,
native seat proof and journal settlement agree. It can block that rocket and an affected player's login
until repair; full storage refuses new releases without mutation. A malformed/future sidecar refuses the
whole world's startup until compatible software or validated whole-world recovery is supplied. Runtime
unknown identity can hold all rocket-passenger admission. Preserve bytes/obligations; never delete player
files or restore an isolated store. Back up all Levels, entities, POI, journals, sidecar, playerdata and
metadata consistently before use/downgrade/repair. Player readback covers process stops, not power failure.
B1 replaces W80's unproved entity-save assumption; it does not repair unrelated orphan windows in ordinary
recovery, failBackToSource or disassembly (R-032/R-035).

**Log budget:** One accepted-release WARN and one seat-completion INFO per batch, plus at most one existing
settlement audit. At most one placement, fallback and verified-placement line per attempt. One line per
transition into conflict, I/O-wait, unsafe-wait or timeout, and one reply per refused command. Repeated
same-state retries do not log. Runtime root failure logs once per transition; startup uses section 2's
per-attempt diagnostic. Cap UUID lists at 16 and read-only `rejoins [player]` output at eight entries per
response. No per-tick logging or four-line total is promised. Tell affected players why they are protected/
disconnected and operators which proof stage remains unfinished.

**Proposed ADR-065 note:** A legacy-only operator release records bounded placement and seat-revocation
obligations in a lazy checked managed sidecar, preserving journal root 3 and the governed protocol. Prove
seat removal independently in the native entity store and reconstructible journal before later boarding.
Players rejoin unmounted under bounded protection, at a probed pad or equally checked Overworld fallback.
Retire placement only after exact installed-file marker/dimension/position readback and checked
acknowledgement, never SaveToFile alone. No offline playerdata writer, new injection or automatic release
timer is introduced. R-035 must cover local holds, whole-world startup refusal, downgrade after use, logs,
repair and the player power-loss boundary before implementation is assigned.

## 7. Root decisions and remaining authorization (R8)

Decisions 1-4 are reflected above: separate storage, exact readback, protected bounded waiting, distinct
authoritative absent sets, independent seat proof and conflict refusal. The historical record's save-event,
stale-seat and downgrade claims are superseded by revision 2.

**Decision 5 needs correction before implementation.** Keep the requested `source.hasPermission(3)`
threshold at the Brigadier leaf and execution entry, but do not claim it excludes every function.
`FunctionCommand.runFunction` passes `withMaximumPermission(2)`; in the pinned jar that method returns the
existing source when permission is already 3/4. It is a floor, not a cap. Tick/load/scheduled functions use
`ServerFunctionManager.getGameLoopSender` at level 2; operator-/console-invoked functions can retain higher
authority. `execute as` changes the entity, not the original permission. Level-2 command blocks therefore
cannot acquire access by impersonating an operator, but operator-invoked functions are different.

There is no public `ServerFunctionManager.isInFunction` in this baseline. Entity type is not provenance;
neither is suppressed output: `withSuppressedOutput()` also returns the same object for sources with
`alwaysAccepts()`. No new injection is authorized. Do not add reflection, stack inspection or command
rewriting as an unreviewed workaround.

**Recommendation to Root:** retain level 3 and explicitly allow inherited-authority functions if permission
alone is the desired boundary. If all functions must remain forbidden, keep command implementation blocked
until a separately reviewed origin mechanism is authorized. Pending that decision, this note authorizes
neither a bypass nor an extra injection. Test the actual operator-function result rather than pre-labeling
it refused. An asynchronous clarification was requested; the task record will identify any later response,
not infer approval from silence.

Other recommendations are to keep H2 in scope and governed records excluded; use native-store B1 instead
of a Level save approximation; add no forget command; and accept protected disconnects only with the listed
verification. Root must register the risks/ADR clarification and review the merged W11b2 barrier binding
before assigning implementation.

## Appendix A. Verification plan and cost (not executed)

**A0:** Cover every codec bound, duplicate/reference/unknown-state rejection, first-write race, command
refusal, distinct H1/H2 absent sets, before/after manifest witnesses and independent cleanup. Cover multiple
PENDING entries, newer-transfer conflicts, stale K/G/session results, duplicate saves, logout/clone and
callbacks declined then retried. Assert that the new legacy compare-and-replace refuses native records,
stale versions and every nonpassenger change, while ordinary `put` still rejects checksum replacement.
Fake worlds check support/collision/fluids/hazards/border/atmosphere and
candidate/ticket/queue budgets. Separately test temporary-write failure, normally returning replacement/
backup failure, and successful installed-file proof. A synthetic SaveToFile alone is never a success oracle.
New Minecraft-dependent test classes bootstrap and run alone.

**Migration/integration:** Check no-use startup without sidecar creation; journal roots 1/2 to 3; root-3
EMPTY/ELECTED/DELIVERED round trips; independent sidecar schema-1 round trips; and installed startup refusal
for malformed/future sidecars with unchanged bytes across repeated attempts. Interleave S1/S2/P1/P2 and J1
with W44 election/delivery and governed journal writes. Assert unrelated checksums/envelopes, full outbox
preservation and lease retry. Older-jar no-use fixtures remain compatible; used-world downgrade is explicitly
unsupported, not claimed automatically blocked.

**GameTests:** Plan about 18 focused tests plus parameterized cases: two absent riders; mixed online/offline;
H2 departed original/new absent rider; login before S1 acknowledgement; E1/B1/J1 gate loss and retry; full
queue; delayed writer/unloaded pad; low-health obstructed/void login; hazardous spawn/no-safe timeout;
disassembly then login; player-first/entity-first load order; remaining rider logout; later same-UUID/seat
boarding; multiple/newer-transfer conflict; repeated saves/logout-before-ack; real temporary-write and
replacement failure; command provenance matrix; and unaffected ordinary reconnect. Command cases include
level-2/3/4 players, console, RCON, command blocks, tick/load/scheduled/operator-invoked functions and nested
execute for each. The operator-function policy awaits section 7's explicit decision.

**Native restarts:** Extend W63's runner/probe only in later code tasks. Use gated checked-writer cuts
distinguished by sidecar/journal payload/stage, plus existing native-store cuts. Inject real filesystem
failures in disposable fixtures, not merely events. Inspect exact sidecar K/G/states, journal/outbox/
checksums, entity rosters/copies, player dimension/Pos/marker/RootVehicle/health, unrelated players' positions
and unchanged fuel vectors. Create prerequisites in the first counted JVM. H2 must reach landing and cut
in that same session, so setup does not eliminate its hold by restarting.

| Campaign | Counted starts |
|---|---:|
| Basic H1 two-rider release and disassembly/rejoin retain the original seven plus six stages. | 13 |
| C1-C11 each use one seed-and-cut start plus two recovery starts. | 33 |
| H2 C2/C3/C5/C6/C10 each use one live-landing/cut start plus two recovery starts. | 15 |
| Temporary-write, backup/replacement failure and successful save each use three starts. | 9 |
| Player-first/entity-first, later same-seat boarding, login-before-S1, multiple/newer conflict and queue/hazard timeout each use three starts. | 18 |
| No-use old-jar, malformed-root repeated rejection, future-root repeated rejection and W44/governed interleaving each use three starts. | 12 |
| **Total planning allowance.** | **100** |

Using revision 1's unmeasured assumption of 20 seconds/start plus 15 minutes of setup, with 1.5 allowance,
the estimate is `(100 * 20 + 900) * 1.5 / 3600 = 1.21` heavy-lock hours. Allow about 0.4 additional hours
for one runData and one GameTest run; setup/review/failure diagnosis can increase this. The former 43-start
estimate does not cover revision 2. Unit tests need no heavy lock. No game, Gradle or GameTest ran here.

## Appendix B. Implementation leaves after review

- **W80-storage (planned):** Add the sidecar model, bounded checked writer, lazy startup integration and
  codecs. Depend on Root's risk/ADR registration and independent review. Verify A0, no-use migration and
  malformed startup without enabling command admission.
- **W80-release (planned):** Bind frozen H1/H2 planning, revocation fences, B1/J1/S2 and retry scheduling
  against merged W11b2. Verify load-order/later-boarding cuts, resource conservation and W44/governed
  interleavings before enabling release.
- **W80-rejoin (planned; command policy unresolved):** Add protection, safe placement, installed-file proof,
  exact retirement and command adapters. Depend on both prior leaves and section 7's decision; run the
  combined unit/GameTest/native matrix and obtain two-real-client evidence later.

These are v1.8 suggestions, not delegated tasks or completion claims. This revision changes only this note
and [W80.md](W80.md); it edits no code, tests, ADR, risk register, status, ledger or build file.
