# W230: seat-retaining safe exit from a bay-held rocket

Date: 2026-10-31; Revision 1: 2026-11-01. Design only, for Root and independent review; not an accepted ADR,
implementation, risk acceptance or Gate result. Source inspected:
`b6e13ac7821d33163420532566235e71b0f48a99`, on `codex/v1.8.0-w230` in
`D:/GitHub/arce-v180-w230`. The only write scope is this file, which also serves as
the task record. No other worktree was modified and no agent was dispatched.

## 1. Decision and boundary

Propose an unconditional physical dismount with a server-selected, loaded-only
landing position, authorized by a one-use passenger-placement permit. **Exiting
does not release the seat, cancel the flight, or promise that the passenger stays
behind.** T (the existing transfer record) retains the UUID/seat assignment.
Existing acknowledged passenger-transfer and reconnect rules may move that player
again. A persistent opt-out is not inferred from being unmounted.

When no safe candidate can be proved within the budget, complete dismount without
a speculative teleport and offer a bounded safeguard for that descent, described
in section 4. This mitigates falling, not every possible hazard. Owner decision
Q1 is required before this can support production admission; D1b did not accept
that residual. There is no universally safe fallback in an arbitrary edited world
under loaded-only, no-terrain-change and unconditional-dismount constraints.

Authority: [owner decision D1b](OWNER-DECISIONS-20261031.md), Root decision R223 in
[the task](../TASK-W230-BAY-SAFE-EXIT-DESIGN.md), and
[ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md).
Revision authority is Root decision R231 in `TASK-W230-FIX.md`, supplied from the
shared tree. The external review is
`D:/GitHub/ARCE-Task-Evidence/root-queue/W230-review/REVIEW.md`, SHA-256
`bd2246a79700ef78bf7bc38d952613d91702ae9335221ac320f13894d89127b1`.
This revision addresses its five findings; it is not the reviewer's recheck.
The older [W27A design](W27A-DESIGN.md), sections 4, 8, 8.1 and 10, supplies the
payload/recovery boundaries. Its risk C statement that dismount is cancelled is
historical and incorrect for this source; [W127 Q2](W127-PLAN.md) and ADR-065's
W27d1/W11b2 notes establish the implemented rule. This record does not edit them.

Explicit non-goals: production/test code; W231's per-owner pending cap; enabling
bay admission; cancelling dismount; releasing/reassigning seats; playerdata or
RootVehicle authority changes; a new save root, schema or packet; inventory,
payload, receipt or journal mutations; terrain construction; arbitrary chunk
loading; changing status files, the ledger, ADR decisions or accepted risk text.

## 2. Facts checked against the source

Paths below are relative to `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`.

| Existing component | Observed boundary |
|---|---|
| [RocketEntity](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/entity/RocketEntity.java), lines 585-625 | `hasExactlyOnePlayerPassenger()` stays false: the rocket is world/journal-owned, not serialized as player-owned RootVehicle. The dismount location is currently `position + (1.5, 0, 0)`, without a support check. |
| [RocketEscrowInterlocks](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/RocketEscrowInterlocks.java), lines 46-65 | Dismount invalidates the flight proof and returns without cancellation. The mount exception is scoped; the bay lock does not become globally idle. |
| [RocketFlightService](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketFlightService.java), `request`, `leave` | Menu LEAVE is a different operation: it requires a stationary, mutation-eligible rocket and removes an assignment. It is currently refused during the hold. It must not be reused unchanged for a physical safe exit. |
| [RocketPassengerManifest](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketPassengerManifest.java) and [RocketFlightLimits](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketFlightLimits.java) | Assignment identity is UUID plus unique seat index, at most 16; ascent altitude is 48 blocks and active transfer capacity is 64. There is no separate persistent crew roster. |
| [BayPassengerRouting](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayPassengerRouting.java) | D5 moves only living, online, assigned riders of a recognized copy being removed, into its exact survivor. An unmounted passenger is not remounted by that D5 operation. |
| [RocketTransferService](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferService.java), `transferPassengers`; [BayReleasedResume](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayReleasedResume.java), `resume` | A later ordinary acknowledged passenger phase moves online assignments. Released recovery at PASSENGERS_TRANSFERRED also reconciles unmounted assignments. A physical exit is not a durable stay-behind instruction. |
| [RocketTransferRecoveryService](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferRecoveryService.java), `tryReconnect`; [RocketPassengerReconnectQueue](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketPassengerReconnectQueue.java) | Held logins are retained as applicable; the existing queue is 128 players, four attempts/tick, 200-tick wait. Expiry does not remove T's assignment. Released reconnect has its own checked authority. |
| [RocketRejoinProtection](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/rejoin/RocketRejoinProtection.java) | Existing durable release/rejoin guards have earlier lifecycle and interaction authority. Safe exit must not bypass them or use their records as an exit marker. |

Local bytecode inspection, not a runtime test: Java 17 `javap -p -c` on the cached
Forge 47.4.10 mapped jar, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`, confirms that
`Entity.removeVehicle` posts Forge's dismount check before clearing the vehicle;
`LivingEntity.stopRiding` invokes dismount placement only after successful detach,
on the server. Its private `dismountVehicle` normally calls the vehicle's public
location hook, but bypasses it for a removed entity or portal case. Thus the hook
alone cannot observe every detach, and checking `player.getVehicle() == rocket`
inside that hook would be too late. No ASM or Mixin is proposed.
`ServerPlayer.dismountTo` uses `removeVehicle` and `setPos`; the same-Level
connection teleport can synchronize it without a ticket. In contrast, the
`teleportTo(ServerLevel, ..., Set<RelativeMovement>, ...)` overload starts with a
chunk-ticket operation and must not be used by this exit adapter.

## 3. Position selection

### 3.1 Eligible endpoint and finite candidate order

Use the actual departing rider and the actual loaded vehicle, never a client
coordinate or the satellite receipt's orbit string. A ground anchor requires an
unambiguous current T for L (logical rocket ID), a current logical acknowledgement
and actionable lease, the exact recognized physical endpoint and allowed snapshot
hash, its owner and manifest, and the endpoint's existing Level. For a held source
the anchor is T's source origin, not its altitude Pos. For a recognized released
destination it is T's destination origin. Do not mistake a destination for a source.

If T is absent/unproved, a receipt index is NOT_LOADED, or the physical authority
is ambiguous, do not derive a distant ground anchor or elect/reconstruct a copy.
Only local placement about the actual departing rocket may be attempted. This
also covers a landed rocket whose released receipt still locks it after T is gone.
An existing rejoin/release placement guard takes precedence; it gets no exit bypass.

The ordered list has at most **32 candidate feet positions**:

1. One last successful position for this exact (T, endpoint UUID, snapshot hash),
   if present in a server-lifetime cache. It is only a hint, fully revalidated.
2. Four local gantry positions: actual rocket Pos plus `(2,0,0)`, `(0,0,2)`,
   `(-2,0,0)`, `(0,0,-2)`, in that order. Keep the actual feet Y; no ground is
   assumed at altitude.
3. If the checked endpoint anchor is available, nine columns at offsets
   `(0,0)`, `(1,0)`, `(1,1)`, `(0,1)`, `(-1,1)`, `(-1,0)`, `(-1,-1)`,
   `(0,-1)`, `(1,-1)`. For each column try feet Y `origin.y`, `origin.y + 1`,
   `origin.y - 1`, in that order, at X/Z block centers.

Deduplicate identical positions, preserving order; return the first safe one.
Do not extend the radius, descend a heightmap, scan to the bottom of the world,
search another dimension, fall back to spawn/bed, or activate an unloaded column.
The old altitude position is not a safe result merely because it is empty.

### 3.2 Safety predicate

All checks run on the logical server thread immediately before use. Accept only
finite coordinates in the same Level and a supported standing-player box (width
at most 1 block, height at most 2 blocks). Larger modded dimensions fail closed;
do not force crouching or alter the player's dimensions to pass.

- The entire player box, its support and one-block hazard collar are inside build
  height and the world border. Check the full box, not just its center.
- Every intersected chunk column for those checks is already FULL, entity-loaded
  and ticking. Use non-creating chunk lookups before reading any block. A missing
  column rejects that candidate, including a missing neighbor across a chunk edge.
- The actual standing collision box is clear of block collision shapes and other
  entities, including rocket copies and a player just placed by another exit.
  Use a capped loaded-section entity visitor, not an unbounded entity-list query
  followed by truncation. An incomplete collision query is not evidence of safety.
  Supply block-shape/support helpers with the bounded loaded-only read view;
  neighbor reads outside that view refuse, rather than calling a loading Level
  accessor or disguising an unavailable block as air.
- The whole feet footprint is supported by a stable, full upper collision face
  immediately below it. Reject partial/uncertain support, falling blocks, moving
  pistons and open trapdoors; do not treat the rocket's visual structure as terrain.
- There is no nonempty fluid state in the body, support or collar. Reject fire,
  soul fire, campfires, lava, cactus, magma, berry bushes, powder snow, wither rose,
  pointed dripstone and portal/gateway blocks in that same bounded neighborhood.
  A known hazardous or unclassifiable contact state fails closed. Keep the exact
  vanilla/project hazard classifier centralized and covered by table-driven tests.

This is instantaneous **placement/contact/fall safety**, not guaranteed breathable
air, PvP immunity, protection from future terrain edits or arbitrary third-party
damage callbacks. Existing atmosphere, equipment, station and combat rules stay
in force; a player still needs life support. Message and risk text must say so.

On a safe result, place only the detached passenger, preserve yaw/pitch, zero
velocity and fall distance, and synchronize with the ordinary server-player
same-Level position correction. Do not invoke a teleport helper that takes a
ticket, loads a chunk or calls a respawn search. Do not move the rocket.

### 3.3 Hard work and memory bounds

Proposed fixed ceilings, not permission to raise an existing system's budget:

| Resource | Ceiling and exhaustion behavior |
|---|---|
| Candidates | 32/exit, 128/server tick across all exits; stop before exceeding either. |
| Block-state reads | 96/candidate including collision/support/collar, 4,096/server tick. Reuse the bounded read window for all predicate checks; an oversized window or insufficient remaining allowance refuses the candidate without further reads, not an unchecked result. |
| Entity collision visits | 16/candidate, 256/server tick, including non-colliding visited entities; exhaustion makes that candidate unproved. Never materialize an unbounded list. |
| Live exit state | One slot per ServerPlayer instance, at most 1,024/server, holding a detach context, placement reservation or live descent/landing token, never multiple grants. A replacement instance cannot inherit it. Local/T-less exits count too. Refuse new state on overflow; never evict live state. |
| Anti-refresh history | A separate server-lifetime table: at most 1,024 `(player UUID, transfer UUID T)` rows, grouped under at most 64 T identities. A row stores its immutable first-exit tick/deadline and closed/issued status, not a player object. Several Ts for one UUID occupy separate rows. Retention and acknowledged end-of-T cleanup are in section 4.3. Overflow refuses a new grant, not dismount or an existing history. |
| Observation work | At most 1,024 constant-size live-slot samples and 1,024 history-row cleanup checks/server tick. Contact probes use the same 128 candidate-work units and 4,096 block reads as placement, at most 96 reads/probe; no separate world-read allowance. At most 32 bounded stack inspections/tick, at most 64 frames each. Missing allowance revokes the affected grant/token immediately, without a deferred retry. |
| Cached positions | At most 128 endpoint hints, one per endpoint of the 64 active T records; invalidate on identity/hash/Level change and at server stop. No T means no cache entry. |

All exits and descent contact probes share the counters for a server tick,
including callbacks and menu requests; nested calls cannot reset them. Repeated
callbacks for one detach reuse its result, not another budget. There is no
retry/world-search task hidden in a player tick. Memory and work are bounded;
these bounds do not bound a receipt hold or RAM history retention.

### 3.4 Competing exits: ordering, not a fairness guarantee

Use logical-server execution order. At the start of each actual selection attempt
assign the next server-lifetime attempt sequence. Finish its bounded decision and
reserve the selected standing box before another attempt may select. The normal
location hook then applies that result. Until application is observed, other exits
treat its reserved box as occupied; after application actual entity occupancy
replaces the reservation. A stale/failed context releases only its own reservation.
Reservations live in the capped live slots, expire with the context, and their
collision visits count toward the same 16/candidate and 256/tick entity-visit caps.
Do not scan an unlimited reservation or entity list.

A reentrant call for that same detach reads the existing outcome. A different
detach nested inside an active decision gets no nested selection or priority:
complete dismount with the bounded refusal/fallback; do not reset counters. For
hook-bypass contexts, drain a bounded FIFO at server-tick END, after synchronous
callbacks and before resetting that tick's counters. FIFO order is the capture
sequence, with one entry/live slot; no context survives beyond the next such drain.
Its attempt sequence is allocated only when it runs. Capture reserves neither a
safe box nor a future tick's budget. A stale completion is discarded before taking
a candidate unit. There are no equal sequences: simultaneous network requests are
ordered when the server executes them, not by client timestamps or seat priority.

Here **deterministic** means identical ordered server observations, world state
and starting counters produce identical results. Reversed execution order may
reverse the winner. This is not scheduling-independent fairness, round-robin
service, a bounded-wait guarantee, or W231's per-owner quota. A delayed completion
cannot preempt an already selected/reserved box. Budget exhaustion yields the
section 4 fallback immediately, not a retry on the next tick.

## 4. No safe candidate: exact fallback and its limitation

For an actual completed detach, leave the passenger at the last server-observed
rider position, rather than applying the old unchecked `+1.5 X` displacement or
teleporting to an unproved anchor. Zero inherited velocity/fall distance. The
location hook returns that position; if vanilla bypasses the hook, a one-shot
server-tick-END completion handles the captured detach only if the same player
is still alive, online, in the same Level and unmounted. It must not undo a newer
teleport, portal transition, mount or death. Never cancel or reverse the detach.
The adapter's one same-position connection correction only synchronizes that
checked fallback position; it is not a teleport to another unproved location.
It takes no chunk ticket and is completed before capturing a grant's baseline.

Propose a RAM-only **one-descent fall safeguard**, not a potion or persistent
invulnerability/no-gravity flag. It may suppress **one damage attempt whose exact
damage-type key is `DamageTypes.FALL`**, at the first verified ordinary landing of
the captured descent. It never cancels `LivingFallEvent`: that event has no source,
and `PointedDripstoneBlock.fallOn` also calls `causeFallDamage`, with STALAGMITE.
Falling-block, stalagmite, fly-into-wall and every other non-FALL type remain
unchanged, as do combat, fire, lava, suffocation, vacuum, void and kill damage.
Do not use a broad damage tag or zero the fall-event multiplier/distance.

### 4.1 Source-aware landing adapter

Observe `LivingFallEvent` only to close the live descent at its first landing,
including cancelled/non-damaging falls; leave other gravity/boot listeners intact.
At the first proved landing, replace the live descent with a one-use pending
landing token in the same slot. A `LivingAttackEvent` listener may cancel only
when its source is exactly FALL and that token passes all identity, continuity,
deadline and contact checks. A non-FALL landing closes the offer without any
cancellation. A second landing/event cannot obtain another token. Fluid contact,
unsafe/unknown support or a non-damaging landing ends the descent too; none earns
protection on a later fall. An unused token dies at tick END or any intervening
movement/lifecycle observation other than the paired attack's own revalidation.
A cancelled attack is never uncancelled.

Do not treat an arbitrary `hurt(FALL, ...)`, a client on-ground bit, a synthetic
fall event, or `causeFallDamage` invoked in mid-air as a genuine landing. Require
the previous sample to be airborne/descending, current feet to meet fully loaded
ordinary support within 0.01 block, no fluid/hazard/unknown contact, and the same
captured descent. The contact probe uses the section 3.2 bounded read view and
classifier; it does not require a distant safe destination or load a block.

To bind cancellation to the actual synchronous landing rather than a loose
same-tick token, use a small, capped Java 17 `StackWalker` check at fall observation
and damage cancellation. Require the ordered vanilla call frames
`LivingEntity.causeFallDamage(FF,DamageSource)`, `Block.fallOn(...)` and
`Entity.checkFallDamage(...)`, with `ServerPlayer.doCheckFallDamage(DDDZ)` as the
outer player landing boundary. At attack time the causeFallDamage frame must
still be on that call stack. Match declaring class, mapped method name and
descriptor, not substring names or an arbitrary plugin frame. Missing, truncated,
reentrant/ambiguous or unrecognized frames refuse protection and close the offer.
This also prevents an unused token from protecting a direct FALL attack after a
zero-damage landing. The token is not damage authority outside that call chain.

Resolve the small signature allowlist once per server using pinned mappings and
Forge's name remapping, in both development and packaged runs; do not retain
stacks or install a general interception framework. No ASM, Mixin, coremod or
Access Transformer is proposed. Landing overrides which do not delegate through
the recognized `Block.fallOn` path, including vanilla hay/bed/slime special cases
and third-party overrides, get no extra safeguard; their existing mitigation is
unchanged. Do not silently widen the signature allowlist. S2 must prove this
restrictive adapter in the mapped and packaged environments before Q1's
mechanism is usable.

### 4.2 Descent identity and continuity

The descent identity is `(server lifetime, player UUID, ServerPlayer instance,
T transfer UUID, seat, Level instance, detach sequence, movement generation)`.
It starts only after a completed fallback detach and that operation's own final
position correction. Its deadline is fixed at the history's first-exit tick +
200; valid ticks are strictly before that deadline. Capture the last position,
velocity, sample tick and flight mode in the live slot. At player tick START/END,
server tick END, landing/damage callbacks and before delayed placement, check the
same movement generation and continuity; never infer continuity from the original
eight-block radius or exit-Y ceiling alone.

The movement generation combines a service-local transition serial with the
connection object and its server teleport counter. All project-owned trusted
passenger-transfer entry points increment the serial and irrevocably revoke the
old context/grant/token **before** detach, teleport or mounting is attempted.
Failure before remounting, early return and `finally` cleanup cannot restore it.
Observe Forge teleport intents and Level changes as additional conservative
invalidations, even if the teleport is subsequently cancelled.

Forge teleport events are not a universal callback for `connection.teleport`.
Read the pinned `ServerGamePacketListenerImpl.awaitingTeleport` counter
(`f_9767_`) through one cached, read-only reflective field resolved with
`ObfuscationReflectionHelper.findField`. Never write it or depend on the client
acknowledgement. The inspected connection teleport implementation increments it
even for same-Level/same-position correction; acknowledgement does not reset it.
A changed counter or connection revokes immediately, even inside the radius.
Only W230's own one-shot correction establishes the initial baseline; retries,
later corrections and trusted moves cannot rebaseline an existing descent.
Verify that correction's one expected counter increment, exact result position
and unchanged transition serial before accepting the baseline; reentrant extra
teleports or an ambiguous result revoke instead of capturing a newer baseline.
Resolution/read failure disables granting and discards pending placement, with
one bounded diagnostic per server, not a repeated exception or startup failure.

Continuity is intentionally conservative: no upward displacement greater than
`1e-6` block relative to the **last sample**, no upward velocity above `1e-6`, no
flying/elytra/levitation/climbing/swimming or new mount, finite values, at most
four blocks of downward and one block of horizontal displacement accumulated in
one server tick, at most one tick between required samples, plus the original
exit-Y + 1 ceiling and eight-block horizontal radius. Exceeding any limit closes
the descent, including an upward reposition that is still below exit Y + 1.
Gravity changes, knockback or unusual legitimate motion may therefore revoke it.
Direct position edits without a teleport are rejected when these observations
show a discontinuity; arbitrary unobserved third-party state writes are not
claimed detectable. A supported teleport uses the server connection counter,
not this displacement heuristic.

Uncertain/missing samples, unloaded/unavailable contact reads, unsupported modes,
unknown mappings or exhausted observation/read budgets **revoke**, never suspend
and resume. There is no block loading, deadline extension, second descent or
movement replay. The same generation check prevents a delayed completion from
undoing a newer teleport or an internal transfer that never reaches mounting.

### 4.3 Live capability versus anti-refresh history

Live state belongs to one player instance. History belongs to `(UUID,T)` for this
server lifetime, independently of the current player object, endpoint, T phase
or record digest. On the first authority-checked completed voluntary detach for
that pair, atomically reserve a history row before offering protection. Fix its
first-exit tick/deadline and mark the offer closed, or issued if the fallback can
grant. Even successful safe placement closes that first offer: a later exit is
not a fresh opportunity. Cancelled/uncompleted detaches create no used history.
If either table or the 64-T bucket limit is full, refuse new protection; never
evict, overwrite the latest T, or forget a retained row to make room. A refused
attempt which admitted no row issued no grant; later capacity availability is
not renewal of a previous grant.

Landing, fluid/support contact, expiry, excessive motion, teleport, trusted
transfer, remount, logout, death/clone, Level change, failed placement, lost
authority and live-slot release all destroy the live capability **but retain
every admitted history row and its original deadline**. Expiry is not history
garbage collection. T-A then T-B then T-A therefore retains two rows; returning
to T-A, logging in as a new instance, or respawning cannot reissue T-A's offer.
Re-entry may still perform bounded safe placement. No new player/T history is
serialized, and no player-instance reference is retained by a history row.

End-of-T cleanup requires a loaded, operational, clean/acknowledged transfer
journal, no pending journal effects/reservations, T absent from both its current
and acknowledged view, and no live context/grant/token for T. A final phase,
landing, missing/unloaded index, uncertain lookup, release flag, player leaving
the manifest, or RAM removal awaiting acknowledgement is **not** end-of-T. Use
the existing `clean()`, current-record lookup and `logicalGuard(L)` acknowledged
record/absence checks; no journal write or new save root. Remove that T's rows
only after those checks on the server thread. Sweep at most the capped 1,024
rows/tick; retained unproved ends can saturate the table, which refuses new
offers rather than evicting history. A different transfer UUID for the same L
is a different T. Reusing an ended transfer UUID is not a supported new flight.
Server stop/restart/crash clears both tables; this RAM-only anti-refresh rule
does not promise cross-restart history or preservation of a live safeguard.

If T/seat authority or a free state slot is unavailable, still dismount and try
only the local safe candidates; grant no wider movement or damage privilege.
Report `NO_SAFE_EXIT`, `EXIT_BUDGET_EXHAUSTED`, `EXIT_AUTHORITY_UNAVAILABLE` or
`EXIT_CAPACITY` as applicable. These are transient exit outcomes, **not new
receipt reasons**. Send the passenger a concise EN/zh message explaining no safe
place was found, any actual fall safeguard, and the retained seat. Safe placement
also explains that the seat remains assigned and later flight/reconnect can move
the player. Never report safe success for this fallback.

Why propose this: it removes the unchecked lateral move, usually prevents the
48-block waiting-rocket fall, requires no world edits/loading, does not trap a
passenger aboard or create indefinite immunity, and keeps payload/playerdata
authority unchanged. It does **not** make a void/lava/blocked launch area safe;
the RAM safeguard is also lost on disconnect/restart/crash. A budget/capacity or
authority refusal can leave even the fall unprotected. These are real residuals,
not a successful safe placement or a release Gate waiver. **Q1 asks the owner
whether that finite fallback is acceptable. Until decided, admission stays off.**
If absolute survival is required, this design must be revised with separately
authorized rescue infrastructure/authority; it cannot obtain that guarantee by
silently cancelling dismount, forcing chunks or inventing durable exit intent.

## 5. Permit and player permissions

The proposed `BayPassengerExitPermit` is a coordinator-owned, non-serializable,
one-action capability. It never makes `RocketEscrowGate.idle()` true and is not a
`MOVE_ASSIGNED_PASSENGER` mount permit. Mint from an observed dismount while the
player actually rides the exact loaded rocket, capturing the server instance,
player UUID **and ServerPlayer instance**, physical rocket UUID/object, Level,
L, T/record digest and logical lease where available, owner, snapshot ID/hash,
seat index, claim/receipt generation and the pre-detach position/tick. Local-only
rescue explicitly has no T-derived movement or fall-safeguard capability.

Capture the before-detach association in `EntityMountEvent` without cancelling
it. No placement, grant consumption or durable effect occurs unless detach is
actually observed. Cancelled events expire without effect. Revalidate the scope
at location selection and at any tick-end completion; consume once. A changed
player incarnation, seat, endpoint, lease/claim, Level or reentrant callback
invalidates it. Expire unused detach contexts at the next server-tick-END drain.
Keep only the separate, narrower section 4 grant for its stated lifetime.

| Actor | Authority |
|---|---|
| Owner riding an assigned seat | May physically exit self, without owner-specific extra rights. |
| Crew riding an assigned seat | Same self-exit right; crew means T's UUID assignment, not name or client claims. Owner presence/approval is unnecessary. |
| Operator riding an assigned seat | Same checks; operator status does not override T, loadedness, collision or budget. |
| Owner/operator standing nearby | Cannot use this capability to teleport self or eject another passenger. No remote administrative rescue command is added. |
| Unexpected physical rider or missing assignment | Dismount is still not cancelled. Only loaded local placement may help; no checked-anchor teleport, fall privilege, seat creation or assignment rewrite. Diagnose the authority mismatch without repairing it here. |

The existing menu LEAVE action can gain a narrow held-self-exit adapter before its
manifest-removal branch, preserving its sender/menu, alive/non-spectator/non-fake,
distance, same-Level, loaded-entity and rate/replay checks. Its successful held
result only calls `stopRiding`; it does not call `withPassengers(remove(...))`.
A menu sender who is not actually aboard gets no exit move. Vanilla sneak exit
requires no open menu and no owner/operator privilege. The existing ordinary
stationary LEAVE behavior outside a bay hold is unchanged. No new C2S payload,
coordinates, UUID targets or channel are needed.

Internal D5/released/ordinary passenger moves also call `stopRiding`. Give these
trusted synchronous moves an explicit coordinator-owned internal-detach scope
before detach and clear it in `finally`; entering it first revokes any prior
exit/grant/token and advances the transition serial, even if the move fails
before mounting. They must not run the voluntary-exit
placement/grant or send its message. Do not guess intent from the event alone or
leave an exemption active after a failed mount. Existing dismount proof
invalidation remains; explicitly invalidate any bay D6/D7 passenger-sensitive
proof before placement. Recompute through the existing proof queue, without
resetting its budgets, accepting stale tokens or writing a new receipt outcome.

## 6. Identity, stores and recovery

Read and write sets for **the exit operation**, distinguished from ongoing bay
deployment/flight work:

| Existing authority | Reads | Exit writes |
|---|---|---|
| T in `data/advancedrocketrycommunity_rocket_transfers.dat` | Exact L/T, owner, phase, source/destination UUIDs and snapshots, source/destination flight manifests, checked logical digest/lease. | None: no put/rewrite/phase advance/settlement/removal, no seat removal. |
| Loaded `RocketEntity` and its entity-chunk save | Exact physical identity/Pos, neutral/escrow/release state, flight/manifest, recognized before/after hashes. | Passenger relationship changes normally; no rocket snapshot, flight-data, fuel, escrow wrapper or UUID mutation. Existing save/proof observation remains active. |
| `data/advancedrocketrycommunity_bay_receipts.dat`, claims/index | Whether and how L is held/released; exact association when available. | None: no phase/reason/released/attempt change, retirement, reservation release or extension. |
| Transaction journal, escrow ledger/floors and release/rejoin guards | Existing authority/conflict predicates only, using loaded runtime services. | None. Do not create an absent root merely to serve an exit. |
| Vanilla playerdata and LAN host metadata | No direct disk read. Online player identity/state comes from the server. | No direct write, custom tag, RootVehicle injection, inventory edit, forced checkpoint or readback. Vanilla may later save the changed Pos/Motion normally, in guest playerdata or host `level.dat` Player data. That save is not an acknowledged exit record. |
| RAM exit service | Detach context/reservation, bounded hint, permit, descent/landing token and separate UUID/T history. | Only bounded transient state plus proof invalidation. Lifecycle events clear the live capability, not history; history ends only at acknowledged end-of-T or server lifetime end (section 4.3). |

The immutable binding is `(L,T,player UUID,seat index)` checked against T and the
recognized endpoint manifest, not the physical mount list. Two exits cannot take
one another's seat, free capacity, authorize payload consumption or change the
manifest/checksum proof. There is no durable exit tombstone or declined-travel
flag; the RAM anti-refresh history has no routing or playerdata authority.

| Lifecycle/cut | Required behavior |
|---|---|
| Normal exit during INTENT/REGISTERED/REPAIR hold | Place self if proved safe; keep T and the receipt/seat. Another crew member remains aboard. D5 never collects this unmounted player merely because a seat exists. |
| Later READY_SOURCE or released passenger phase | Existing authority still applies. An online assigned player can be moved/remounted at the acknowledged passenger phase; no exit flag suppresses it. A later physical dismount again uses safe placement, without renewing an already used same-T fall grant. |
| Logout before/after detach or placement | Drop live context/reservation/grant/token; retain every UUID/T history and its deadline. Never wait for a player save or release T. A late callback must not act on a disconnected instance. Vanilla's last saved position may precede or follow exit. |
| Login while still held | The new instance inherits no live grant and cannot refresh a retained UUID/T offer. Keep current held-login routing/queue and its 200-tick expiry. Do not infer an opt-out from saved Pos or extend the queue. Expiry leaves the UUID/seat authoritative. |
| Death and respawn/clone | Invalidate old live state, retain UUID/T histories, and give the replacement no fresh same-T offer. Do not move a dead entity or its replacement from a stale callback. T's seat stays; later existing recovery may route a living replacement, and released recovery may instead hold at DUPLICATE_PASSENGERS under its existing rule. No resurrection/automatic seat cleanup is added. |
| Dimension/portal change | Invalidate the same-Level permit/grant/token, retain history, and skip the old completion. No cross-dimension return or chunk load; existing travel authority decides the transition. T remains unchanged. |
| Newer same-Level teleport, upward reposition or trusted transfer attempt | Irrevocably close the old descent and skip its pending placement, even inside the original radius/ceiling or when remount fails. Keep histories/deadlines. Internal-scope cleanup never restores the capability (section 4.2). |
| Clean restart | No W230 state survives. Existing receipt startup indexing and T-based recovery run before ordinary bay actions. No playerdata position is promoted to journal or rocket authority. |
| Crash after capture, before detach | No acknowledged exit intent exists. Reloaded T/entity/player state follows existing recovery. No pending request is replayed. |
| Crash after detach/placement, before player save | The older player position/mount state may return; T still has the same assignment. Existing recovery may route it; W230 cannot claim exactly-once player placement or preserved fall protection. |
| Crash after player save but before D7 or a later passenger effect | Saved safe position is merely vanilla player state; T/receipt proof remains authoritative. Recovery may later move the player again. D7 has no new dependency on passenger saves. |
| No T, NOT_LOADED or conflicting records | No derived anchor, reconstruction, election, widened mount permit or journal repair. Permit only local physical rescue when its own identity checks pass; otherwise ordinary detach and the explicit fallback outcome. Existing hold/offline-repair rules remain. |

These cuts do not broaden R-043's S2 claim to atomic saves of playerdata, host
`level.dat`, entities and journals. Per-file rollback, OS/power/hardware failures
and unobserved historical copies retain their existing exclusions. Operators
still use the whole-world offline procedure; deleting one player's data or one
receipt/journal file is not a W230 recovery step.

## 7. Dedicated server, LAN and required evidence

Run the same server-authoritative placement service on dedicated and integrated
servers. A LAN host has no extra exit rights; its UUID/instance gets the same
checks as a guest. Do not introduce the host/guest player-save checkpoint protocol
from another feature: W230 relies on neither form of player save for authority.
All new common/server classes must remain free of `net.minecraft.client.*`.

V2 requires two **real** clients, on both a packaged dedicated server and a LAN
world, with a pinned implementation commit/JAR SHA-256, Forge/configs, client
identities/roles, GPU details, timestamps and matching server logs. Test through
the existing fully gated fixture while admission is disabled; changing an
admission property is not evidence of production approval.

1. Owner and crew occupy different seats in one rocket held at 48 blocks. Crew
   exits by vanilla sneak, then owner exits through the menu; repeat with roles
   swapped. Both clients must see detach, the same safe ground positions and
   retained-seat message, with no fall damage on successful placement.
2. Keep one client aboard while the other leaves; ensure no forced eject of the
   witness. Record the actual ordered attempt sequences for competing exits and
   the losing passenger's next candidate/fallback, not only non-overlap. Try an
   unmounted owner/operator's menu replay and confirm no movement/seat mutation.
3. Remove safe support or obstruct every candidate. Record the actual fallback
   position, reason and whether the one-descent grant was available. Demonstrate
   its bounded fall-only protection and exhaustion/expiry, not a claim of general
   survival. Do not perform destructive cases on the owner's source world.
4. Disconnect the exiting client before and after placement, reconnect during a
   held claim, and repeat after release/transfer. Observe the retained seat and any
   later authoritative remount on both clients. Include death/respawn and portal
   transition with no stale same-Level placement.
5. On LAN, repeat with the host exiting and the guest witnessing, then reverse.
   Save/restart and selected forced-stop cuts must not treat host `level.dat` Player
   data as acknowledged exit state. Record any returned old position explicitly.
6. Confirm unauthorized remote requests, unchanged packages/fuel/manifest hashes,
   no additional chunk tickets/loads, normal D6/D7 retries and a later flight that
   can continue without requiring the exited passenger to save or connect first.

The recording shows health, mount state, positions and feedback on both clients;
server-side bounded diagnostics establish L/T/seat, selected candidate/outcome and
unchanged authority. Bots, two fake players, GameTest and V0 are useful automation,
not V2. No such real-client evidence was collected by this design task.

## 8. Risk-register proposal, not an edit or acceptance

In [R-042](../../../11-RISK-REGISTER.md), replace only the passenger paragraph
beginning with "原版下马从不被取消" and ending with "在 W27e 之前另行设计。" after
implementation/review and Q1/Q2 resolution. Proposed replacement meaning:

> Vanilla dismount is never cancelled. An actual rider may physically exit self
> through the server's bounded loaded-only position predicate and one-use permit;
> owner, crew and operator use the same safety limits. This does not release T's
> UUID/seat or any receipt/shared reservation. Later acknowledged flight/reconnect
> may move that passenger again. Successful placement resets velocity/fall
> distance but does not promise life support or immunity to subsequent hazards.
> With no proved candidate, budget, capacity or authority, detach still completes
> without a speculative teleport. A proved T/seat may receive the finite,
> same-Level one-descent safeguard for exact FALL damage on a verified supported
> landing path, not STALAGMITE or other damage through causeFallDamage. Its fixed
> deadline, legitimate horizontal/upward movement, any newer teleport or trusted
> transfer attempt, uncertain observation and unsupported landing paths can end
> protection before landing. There is no fresh offer after an admitted first
> same-T exit, including after remount, login or death/clone. Live protection is
> lost on logout/restart/crash; RAM anti-refresh history survives logout/clone but
> not server restart/crash. Void, hazardous terrain, unavailable authority and
> saturation therefore remain explicit risks. Life support remains necessary.
> D5's physical-rider-only rule and
> held-login queue's 200-tick limit remain. The mitigation is not a persistent
> passenger opt-out or an exactly-once player-position protocol.

Add the exact reviewed constants, scope and outcomes from sections 3-6, plus the
owner's actual decision/date/channel; do not write "accepted" before it exists.
Scope is only the departing player's position/fall grant, not chunk/Level save
refusal. A detach context lasts through the next server-tick-END drain at most,
and a fall grant less than 200 ticks from the original history tick. History can
remain until acknowledged end-of-T for the server lifetime (section 4.3), even
when its live capability has expired; retention can refuse new grants at the cap.
The seat/receipt hold retains its existing potentially unbounded
duration. No new save denial, startup refusal or persistence exception is added.

Logging proposal: one bounded INFO outcome per completed exit at most, with L/T
when known, player UUID, seat, outcome and candidate ordinal (no packet/raw NBT).
Duplicate requests do not repeat it. Aggregate transient budget/capacity refusals
into one server INFO summary per 200 ticks; cap summaries/fields, not by dropping
an existing error. There is no lifetime log cap. Passenger feedback is likewise
deduplicated per detach. Add exact deliberate WARN/ERROR expectations only if an
implementation actually introduces them; do not loosen generic log limits.

R-043 needs a cross-reference stating that a physically exited passenger remains
assigned and may be reconciled by released flight, and that W230 proves no atomic
playerdata save. Its copy, payload, authority-conflict, offline-repair and S2
residuals are otherwise unchanged. Correct the historical W27A risk-C claim with
a subsequent clarification, not by presenting the old design as delivered code.
Do not change the pending-cap paragraphs: they belong to W231.

## 9. Tests the implementation must add

These are test requirements, not tests added or run in W230.

| Layer | New coverage and observable assertions |
|---|---|
| A0 candidate/budget | Exact 32-entry order and deduplication, cache revalidation, first-safe choice, all-unsafe, full-box border/build bounds, every hazard/fluid/support row, larger dimensions, cross-chunk collar, no unloaded read, 96-read/16-entity candidate limits and shared 128/4,096/256 tick limits, reentrant and simultaneous calls. Every incomplete predicate refuses. |
| A0 identity/permit | Owner/crew/op self, unassigned rider, remote/non-rider, stale T/hash/lease/claim/seat, changed endpoint, same UUID/new instance, expiry/reuse, other server, off-thread, cancelled detach, internal moves and nested scope cleanup. Assert no field/store mutation and no new mount permission. |
| A0 fallback | One descent/damage attempt, exact deadline boundary (`now < firstExitTick + 200`), first support/fluid contact, relative-to-last-sample continuity as well as eight-block/exit-Y bounds, uncertain-read/sample/budget revocation; separate live/history cap and retention tests below. No restored token after invalidation. |
| A1 placement | Real held source at altitude with a valid ground platform, alternate candidate, blocked headroom/support/hazard, border and unloaded neighbor, two passengers contending for space, a loaded gantry and released/landed endpoint. Assert safe placement or exact fallback, manifest/receipt/resource equality and zero added tickets/forced chunk loads. |
| A1 adapter | Vanilla sneak and existing-menu LEAVE both detach self without manifest removal; cancelled Forge event does not place; hook-bypass completion cannot undo a later transition. Actual internal D5/released transfers are not voluntary exits, and a reentrant failed mount cannot leave a bypass. |
| A1 proof/recovery | Detach invalidates relevant proofs before D7 replacement; unrelated claims still progress. An unmounted exit is not D5-remounted, but the later ordinary/released passenger phase still reconciles its assignment. NOT_LOADED, missing T and conflicting authority grant no distant teleport or repair. |
| S1/S2 | Packaged dedicated and LAN restart/forced-stop cuts from section 6: capture-before-detach, detach-before-placement, placement-before-player-save, saved-player-before-D7, and durable PASSENGERS_TRANSFERRED before its effect. Two subsequent starts verify seat/payload conservation and no false persisted opt-out; fallback crash loss remains disclosed. |
| V2 | The two-real-client dedicated/LAN checks of section 7, including host/guest reversal, actual coordinates/health/messages and later remount. |

Concrete regression cases are required at the real adapter boundary, not only
synthetic event/model tests:

- **W230-R1 ordinary landing** (`bay_exit_ordinary_fall_only`): a survival
  ServerPlayer falls onto ordinary solid
  support through `doCheckFallDamage`/`Block.fallOn`/`causeFallDamage`; observe
  exact FALL, one cancellation, unchanged fall-event distance/multiplier apart
  from existing gravity/boot behavior, and no protection on a second fall.
  **Dripstone landing** (`bay_exit_dripstone_damage_unchanged`): observe
  STALAGMITE and its unchanged health effect, with the offer closed.
  **Non-FALL causeFallDamage** (`bay_exit_non_fall_cause_fall_damage_unchanged`):
  drive that method with a
  non-FALL source through a landing fixture, and also directly; no cancellation.
  Add direct FALL `hurt` and direct airborne `causeFallDamage(FALL)` negatives,
  a zero-damage landing followed by direct FALL damage in the same tick, fluid
  contact, cancelled fall and unsupported/over-64-frame paths. Assert no token
  leak and retain normal armor/other-listener behavior.
- **W230-R2 continuity** (`bay_exit_inside_radius_teleport_revokes`): issue a
  same-Level connection teleport to an airborne
  point inside the radius; protection is irrevocably gone even if the point and
  velocity otherwise match. In `bay_exit_below_ceiling_upward_reposition_revokes`,
  reposition upward relative to the last sample while still below exit Y + 1;
  close the descent. In `bay_exit_transfer_before_mount_failure_revokes`, begin
  a real trusted passenger move,
  then refuse it after teleport but before mounting (also before teleport);
  neither early return nor `finally` restores protection. Test delayed placement
  against all three, a reentrant extra teleport during W230's own correction,
  cancelled teleport intent, connection replacement, mapping/read failure,
  missed observation and legitimate motion exceeding each limit. Packaged S1
  must check mapped signature and `f_9767_` resolution, not only unit mocks.
- **W230-R3 history** (`bay_exit_history_t_a_t_b_t_a`): T-A -> T-B -> T-A keeps
  two history rows and T-A's original
  deadline/closed status. `bay_exit_history_logout_new_instance` and
  `bay_exit_history_death_clone_reentry`
  each clear only live state; no new T-A grant. Repeat for every cleanup event
  listed in section 4.3, deadline expiry and safe-placement-first. Fill each table
  and the T-bucket cap; refusal preserves all older rows. A terminal phase or
  unacknowledged removal retains history; only proved current-and-acknowledged
  absence with no pending effects/live references removes it. Restart loss is
  tested and reported as a limitation, not counted as within-lifetime refresh.

For **W230-R4 arbitration**, use an A0 counting safety port with the same 31
distinct candidates C1..C31 for each exit (no cache hint, four local plus 27
anchor candidates). Seed closed histories for both players/Ts, so no live grant
or contact probe adds work to these selection/arbitration fixtures. Each fully
evaluated candidate in this fixture consumes
exactly one candidate unit, one block read and one entity/reservation visit,
including unsafe results. Begin with zero use unless specified. Pin these exact
outcomes; A1 repeats the geometry through real predicates and checks actual
instrumented read/visit counts against the limits rather than assuming one:

| Scenario | Ordered outcomes | Added candidate/read/visit counts |
|---|---|---|
| Only C1 is available; A then B | A reserves/uses C1. B rejects occupied C1, tries C2..C31, then `NO_SAFE_EXIT` fallback. | A `(1,1,1)`; B `(31,31,31)`; total `(32,32,32)`. |
| C1 and C2 are available; A then B | A uses C1; B's exact next safe result is C2, not overlap or fallback. | A `(1,1,1)`; B `(2,2,2)`; total `(3,3,3)`. |
| Last candidate unit: 127 used, reads/visits have room; A then B | A uses C1 with unit 128. B evaluates no candidate and uses `EXIT_BUDGET_EXHAUSTED` fallback. | A `(1,1,1)`; B `(0,0,0)`; final candidate use 128. |
| Reverse request execution (B then A), for the one-box and last-unit rows | B now wins C1; A takes exactly the losing outcome above. | Swap A/B counts, unchanged totals. Client send time gives no priority. |
| D captured earlier but delayed; A runs before the END drain | A wins C1. D's later attempt rejects C1 and exhausts C2..C31, or evaluates zero candidates if A took unit 128. | Same `(32,32,32)` total in the one-box fixture, or `(1,1,1)` added at the last-unit boundary. Capture used zero candidate/read/visit units. |

Also pin a still-reserved/not-yet-applied C1, same-drain FIFO capture order,
stale delayed completion (zero work), nested-call refusal and last block-read or
entity-visit allowance. Insufficient remaining read/visit allowance makes a
candidate unproved, never a safe result. Record attempt/capture order, exact
losing outcome and all three counters in each test; non-overlap alone is not
an arbitration test. No fairness assertion or owner quota is introduced.

Keep test classes isolated; Minecraft-dependent unit classes initialize
`MinecraftBootstrap` in `@BeforeAll` and run alone once before the full unit suite.
GameTests use a single pre-registered tick state machine, never schedule callbacks
from inside scheduled callbacks. Native cuts operate on disposable fixture worlds,
not the source world. Include the ordered multi-exit budget tests above, without
implementing or selecting W231's per-owner quota.

## 10. Proposed implementation leaves and size checks

All leaves are **planned**, not authorized or delivered by this record. Root must
read this design and obtain its independent review before implementation begins.
The scopes below are sequential assignments; shared integration files have only
one writer at a time. Each leaf records its real commit/commands/results, and the
recovery-facing implementation gets another session's read-only review before
merge. Production admission stays disabled throughout.

| ID | Observable result, dependencies, write scope and estimated size | Acceptance/verification |
|---|---|---|
| W230-S1 | Pure deterministic candidate, safety-port and shared-budget model. New `rocket/bay/exit/` classes and matching `src/test/.../rocket/bay/exit/` tests only; 3-4 main classes, about 400-650 lines total, 400-600 test lines, each class below 400. Depends on design review. No Forge hook or gameplay entry. | Candidate/budget/hazard A0 rows, each new test class alone, full unit suite and repository validator. |
| W230-S2 | Server permits, local/checked endpoint selection, separate live/history state, bounded source-aware landing and read-only teleport-generation observers, and physical self-exit. New small `rocket/server/BayPassengerExit*` classes and one event adapter; narrow delegation in `RocketEntity`, `RocketRuntime`, `RocketManager`, `RocketFlightService`, `RocketTransferEntities`, `BayTransferRecovery`, `BayPassengerRouting`, `BayReleasedResume` and `BayReleasedFlight`; own new unit/GameTest files; EN/zh feedback through a small v1.8 language provider and its datagen registration/output. Revised estimate: 1,000-1,500 new main lines across 7-9 classes, at most 80 changed integration lines; 900-1,300 test lines. Depends on S1 plus Q1/Q2 resolution. | Identity/fallback/adapter A0 and A1 rows including W230-R1..R4, actual ordinary and released passenger paths, unchanged authority assertions, no-load counters, full unit suite, runData determinism, one full GameTest and log checker; packaged mapped-observer smoke is required before use, with S3 evidence. |
| W230-S3 | Recovery/evidence fixture additions and two-client checklist. Own `scripts/run_v180_bay_exit_*.py`, corresponding Python tests and narrowly scoped release-test hooks/fixture and GameTest additions, plus its task record; about 400-650 harness lines, 300-500 test/fixture lines. Root alone applies the reviewed R-042/R-043/ADR clarification and any status/ledger changes. Depends on reviewed S2. | S1/S2 cut matrix with two subsequent starts, dedicated/LAN V2 checks when scheduled, manifest/resource/hash and chunk-load evidence. Record every missing campaign item; never turn on admission or approve a Gate. |

Measured line counts at the inspected SHA (including comments/blank lines):

| Class | Lines | Integration constraint |
|---|---:|---|
| `RocketTransferSavedData` | 800 | No W230 change; read existing logical lease/digest APIs. No schema work. |
| `RocketTransferService` | 796 | No W230 logic added here; scope internal passenger movement through a small extracted adapter/delegation below this layer. |
| `RocketFlightService` | 772 | Add only a short held-self-exit delegation before the old LEAVE branch; no candidate/permission state machine here. |
| `RocketEntity` | 727 | Delegate the location hook; keep permits, scans and transient tables out of the entity. |
| `RocketTransferEntities` | 628 | Keep the existing checked move semantics; any internal-detach wrapping is a tiny delegation, not another rescue service. |
| `RocketTransferRecoveryService` | 613 | No new recovery authority or reconnect behavior; tests must verify the existing rules. |
| `BayTransferRecovery` | 540 | Already exceeds 500; use a separately owned exit helper with narrow lifecycle/query methods. |
| `RocketManager`, `RocketRuntime` | 420, 416 | Wire lifetime/delegation only; review 500-line proximity after changes. |
| `BaySourceProof`, `BayReleasedFlight`, `BayReleasedResume`, `BayPassengerRouting` | 370, 321, 185, 68 | Preserve each existing proof/released/routing responsibility; do not fold exit state into them. |

Recount actual diffs. Above 500 lines requires a responsibility review; exceeding
800 requires an ADR or extraction before merge. Estimates do not authorize
crossing either threshold. These are not tasks for changing build scripts or
raising performance budgets.

Implementation verification commands: `gradlew.bat --offline test --tests <new-class>`
for isolated tests, then `gradlew.bat --offline test`; under the worker heavy-JVM
lock and with at least 10 GB free on C: and D:, serialize
`gradlew.bat --offline clean build`, `gradlew.bat --offline runData` followed by
`git diff --exit-code` after generated output is committed, and
`gradlew.bat --offline runGameTestServer`, then
`python -B scripts/check_gametest_log.py build/gametest/logs/latest.log`.
Native runs use that same lock and worker-owned cleanup. Root's integrated hosted
regression and final Required Gates remain separate from these slice checks.

## 11. Rejected alternatives and open decisions

Rejected alternatives:

- Cancel sneak/dismount or immediately remount to enforce the hold: contrary to
  D1b and ADR-065, and prevents the requested physical exit.
- Treat exit as `manifest.remove`, add an exit flag to T/receipts/playerdata, or
  use saved Pos as an opt-out: changes identity/recovery and hash authority; it
  would need a separately authorized durable protocol and migration.
- Globally unlock a held rocket, reuse a mount permit, or trust owner/op status:
  authorizes payload/launch/repair actions unrelated to passenger placement.
- Unchecked `+1.5 X`, unchecked launch origin or a heightmap/spawn/bed search:
  empty air is not support, a former pad may have changed, and a helper can load
  chunks or change dimensions outside this capability.
- Persistent invulnerability, indefinite hover, rescue terrain/platforms, or
  kicking a passenger as a safety fallback: these create new gameplay, lifecycle,
  save/recovery or availability obligations. The existing rejoin guard is not a
  general-purpose exit-rescue service to repurpose.
- Automatically recover/reconstruct a rocket or settle/remove T in an exit
  callback: confuses physical passenger safety with the existing payload proof.
- Claim that a successful clean restart, GameTest, fall-distance reset or grace
  effect proves crash-safe player placement or V2: those provide different evidence.

Open decisions, all explicit admission prerequisites rather than assumed approvals:

| ID | Decision owner and exact question | Conservative behavior until decided |
|---|---|---|
| Q1 | Owner, through Root: accept the finite section 4 fallback with the residual brief below, or require stronger rescue guarantees and authorize a revised mechanism? D1b authorized design, not acceptance of these residuals. | Keep production admission disabled. Do not implement the fallback as an accepted safety guarantee. |
| Q2 | Root: approve the proposed held-menu physical-exit adapter and EN/zh **message semantics**, including retained seat, possible later authoritative remount, actual outcome/protection and life-support limitation. This is not approval of absent final copy: check both language strings during implementation and V2. If the product requirement is permanent stay-behind, return that separate authority change to the owner. | Do not silently add an opt-out or change T/playerdata/reconnect behavior; retain the existing refusal of mutation-style held LEAVE until the reviewed adapter exists. |

Q1's normal-play residual brief must accompany the question, not just the hazard
examples: a long or low-gravity descent may outlast the fixed 200-tick deadline;
legitimate horizontal movement/knockback, any upward motion beyond tolerance or
a flight-mode change can end protection. A newer same-Level teleport or trusted
transfer attempt, including failure before remount, irrevocably ends the old
descent. Missing/uncertain observations, depleted probe budgets or unavailable
mapped observers fail closed. Only exact FALL on the verified landing call path
can be suppressed: dripstone/non-FALL damage and unsupported landing overrides
are unprotected by W230. The admitted first same-T exit closes the offer even
when placement was safe; later exits after remount, logout/login or death/clone
receive no fresh safeguard. UUID/T history is retained in RAM until acknowledged
T end; saturation refuses new grants, and restart/crash forgets the history as
well as all live protection. No authority/free capacity can mean no grant at all.
Void/lava/blocked terrain and crash-restored old positions remain risks. Existing
life-support/equipment/atmosphere rules apply after both placement and fallback;
neither guarantees breathable air or survival. Accepting this brief is not
accepting broader damage immunity, refreshing history, a playerdata authority
change, or enabling admission without the remaining implementation/review/Gates.

Root's design reading, independent design review, implementation, independent
actual-diff recovery review and the specified evidence are still required. The
design does not decide W231, disposition R-043's broader risks, or pass any Gate.

## 12. Work performed and verification record

The following is the initial W230 record of 2026-10-31, retained as history.
Revision 1's changes and actual checks are recorded separately below.

Completed: inspected the pinned implementation, the named designs, ADR/risk and
owner sources; specified position/permit/identity/recovery, dedicated/LAN evidence,
risk changes, tests, rejected alternatives, open decisions and implementation
leaves. Modified files: this design/task record only. New/modified tests: none.
No upstream code/assets were copied or transformed; provenance is unchanged.

Actual commands and results:

- `git status --short`, `git branch --show-current`, `git rev-parse HEAD`,
  `git worktree list`, and a read-only `Get-CimInstance Win32_Process` filter:
  clean assigned worktree on `codex/v1.8.0-w230`, exact requested base; observed
  Root and W229-review running in other worktrees, with no W230 write overlap.
- `Get-Content`, `rg`, bounded `Select-Object` reads and Java-file line counts:
  inspected the governing documents and actual adjacent code/tests. The attempted
  lowercase version-document name and an exploratory `RocketLandingSafety.java`
  lookup did not exist; the actual uppercase version plan and current placement
  code were then inspected. No files were changed by these reads.
- `Get-FileHash` plus `javap -p -c` for `Entity`, `LivingEntity` and `ServerPlayer`: successful
  local bytecode inspection with the mapped-jar hash recorded in section 2.
- `python -B scripts/validate_repository.py --require-approved-identity` before
  editing: exit 0; **45 passed, 0 pending, 0 warnings, 0 failed**.
- The same repository validator after writing the design: exit 0;
  **45 passed, 0 pending, 0 warnings, 0 failed**.
- `git add -- docs/work/v1.8.0-root-queue/records/W230-DESIGN.md`,
  `git diff --cached --stat`, `git diff --cached --name-only`,
  `git diff --cached --numstat` and the full staged design diff: one new design
  document only; no production/test, central or unrelated files staged.
- `git diff --check` and `git diff --cached --check`: exit 0, no whitespace errors.
  The latter checks the new file after staging, not merely the empty tracked diff.

Gradle build/unit/GameTest/runData, native servers and V2 are **not run**:
the task explicitly requires design only and the repository validator. None of
the proposed behavior is claimed to be implemented or tested. Required Gates
G0-G9 for v1.8 are not satisfied by W230; the inspected v1.7 Gate record also stays
IN_PROGRESS, and no status/ledger file is changed. Evidence for this task is this
committed file and its Git diff/history, not a sealed runtime evidence packet.

Next recommendation: Root reads this design, requests its independent review and
resolves Q1/Q2, then assigns the reviewed v1.8 implementation leaves with admission
still disabled. This recommendation does not authorize a push or shared-branch merge.

## 13. Revision 1 — 2026-11-01 (Root decision R231)

Write scope: this design/task record only, continuing the clean local branch
`codex/v1.8.0-w230` from `8ff38121c9a208109971c933a9003ae76d227aee`.
No code, tests, central status/ledger, ADR, other worktree or upstream asset is
changed. The five findings are addressed in text, pending Root reading and the
same reviewer's recheck; no owner decision or review verdict is inferred.

Changes, one per finding:

1. **W230-R1:** sections 4/4.1 now permit only exact FALL damage at a verified
   synchronous landing, not cancellation of source-less `LivingFallEvent`.
   Separate contact consumption, a source-aware attack listener and a bounded
   landing-stack check prevent broader damage immunity or a reusable same-tick
   token. Section 9 names ordinary, dripstone and non-FALL causeFallDamage adapter
   cases and negatives. Unsupported landing paths fail closed and are disclosed.
2. **W230-R2:** section 4.2 defines descent identity, connection teleport-counter
   observation, trusted-transfer invalidation before side effects, relative
   vertical/flight continuity, uncertainty refusal and immutable deadlines.
   Section 9 adds inside-radius teleport, below-ceiling upward reposition and
   failed-before-mount transfer cases, including stale delayed completion.
3. **W230-R3:** sections 3.3/4.3 separate capped per-instance live state from
   UUID/T histories, retain multiple Ts through every live cleanup, and require
   acknowledged end-of-T for cleanup. Overflow refuses rather than evicts.
   Lifecycle/risk text and T-A/T-B/T-A, reconnect and clone cases now agree.
4. **W230-R4:** section 3.4 defines server execution sequences, reserved boxes,
   delayed FIFO completion and reentrancy without scheduling-independent fairness.
   Section 9 pins exact losing outcomes/counter totals for a single safe box,
   next candidate, last unit, reversed order and delayed contention. W231 is
   unchanged. Context lifetime is consistently the next server-tick-END drain.
5. **W230-R5:** Q1 and the proposed risk text explicitly include normal-play
   expiry/movement, lost protection after transfers/teleports, no fresh same-T
   offer, unsupported damage/landing observations, RAM history/capacity limits
   and unchanged life support. Q2 requests adapter/message-semantics approval,
   not approval of absent EN/zh copy. Implementation estimates include the new
   small observers and their tests without waiving class-size rules.

Evidence and commands for this revision (read-only unless stated):

- `git status --short`, `git branch --show-current`, `git rev-parse HEAD`,
  `git worktree list`, `git diff --exit-code`, `git diff --cached --exit-code`
  and read-only `Get-CimInstance Win32_Process` inventory: correct assigned
  branch/base, clean worktree and index at entry; other sessions observed only.
- `Get-Content`, bounded range/heading reads, `rg` and `Get-ChildItem`: read the
  supplied task/rules, review, governing/version/Gate documents, owner D1b,
  ADR-065 W27d3/W27d4 passages, existing design, and movement/journal/gravity
  code. Broad early outputs were truncated and relevant passages reread in
  bounded ranges. An exploratory `rg` treated PowerShell's `-ErrorAction` as
  its own flag and failed; cache discovery continued with native cmdlets.
- `Get-FileHash -Algorithm SHA256` verified the review hash cited in section 1
  and the mapped Forge jar hash in section 2. Local Java 17 `javap -p [-c]`
  inspected `Entity`, `LivingEntity`, `ServerPlayer` and
  `ServerGamePacketListenerImpl`: landing call chain, source propagation,
  private counter visibility, increment on teleport and acknowledgement handling.
  Read official 1.20.1 mapping/TSRG files to resolve awaitingTeleport as `f_9767_`.
  No cached file or class was changed, and bytecode inspection is not a runtime
  observer test.
- Official Forge API source cross-checks used `LivingAttackEvent.java`,
  `LivingFallEvent.java`, `EntityTeleportEvent.java` under
  `https://github.com/MinecraftForge/MinecraftForge/tree/1.20.x/src/main/java/net/minecraftforge/event`,
  and `ObfuscationReflectionHelper.java` under
  `https://github.com/MinecraftForge/MinecraftForge/tree/1.20.x/fmlcore/src/main/java/net/minecraftforge/fml/util`.
  These corroborate source-aware attack cancellation, source-less fall events,
  specialized teleport intents and remapped read-only reflection. The local
  pinned jar, not the moving branch, supplied the exact runtime facts above.
  No Forge implementation text was imported.
- `python -B scripts/validate_repository.py --require-approved-identity` before
  editing: exit 0, **45 passed, 0 pending, 0 warnings, 0 failed**.
- `apply_patch` changed only this document. One attempted context-only patch
  failed to match and made no change; the actual revision patches succeeded.

The revised-document validator also returned exit 0, **45 passed, 0 pending,
0 warnings, 0 failed**. `git diff --check` and the source/scripts/tests/build/
status-only `git diff --exit-code` returned 0. Final staged-diff checks and local
commit identify this one-document revision in Git history. No production/test code is added. Gradle unit,
build, runData, GameTest, native and V2 runs are not performed for this design-only
task; section 9 remains requirements, not passing tests. Required Gates G0-G9
are not satisfied by this revision, and v1.7/v1.8 Gate/status files are unchanged.
Known risks are the finite fallback and open Q1/Q2 brief, with mapping-sensitive
observers and actual runtime behavior still unverified. Evidence is this record
and its committed Git diff. Next within v1.8: Root reads Revision 1 and obtains
the same reviewer's finding recheck before sending Q1 to the owner. No push,
integration, admission or Gate approval is authorized by this local revision.
