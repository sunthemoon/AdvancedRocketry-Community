# W230: seat-retaining safe exit from a bay-held rocket

Date: 2026-10-31. Design only, for Root and independent review; not an accepted ADR,
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
| Detach/grace state | One slot/player, at most 1,024 slots/server (64 existing T slots times 16 assignments), with explicit overflow refusal and no eviction of a live slot. An unauthenticated/T-less local exit cannot exceed this cap either. |
| Cached positions | At most 128 endpoint hints, one per endpoint of the 64 active T records; invalidate on identity/hash/Level change and at server stop. No T means no cache entry. |

All exits share the counters for a server tick, including callbacks and menu
requests; nested calls cannot reset them. Candidate order is deterministic; another
exit can exhaust the global allowance. There is no retry/world-search task hidden
in a player tick. Repeated exit calls in one detach reuse its result, not another
budget. Memory and work are bounded; these bounds do not bound a receipt hold.

## 4. No safe candidate: exact fallback and its limitation

For an actual completed detach, leave the passenger at the last server-observed
rider position, rather than applying the old unchecked `+1.5 X` displacement or
teleporting to an unproved anchor. Zero inherited velocity/fall distance. The
location hook returns that position; if vanilla bypasses the hook, a one-shot
end-of-player-tick completion handles the captured detach only if the same player
is still alive, online, in the same Level and unmounted. It must not undo a newer
teleport, portal transition, mount or death. Never cancel or reverse the detach.

Propose a RAM-only **one-descent fall safeguard**, not a potion or persistent
invulnerability/no-gravity flag. For a proved current T/seat and actual departing
rider, authorize cancellation of at most the first ordinary `LivingFallEvent`
within 200 ticks. It is valid only in the captured Level, unmounted, no higher
than exit Y + 1, and within eight blocks horizontally of the captured exit point.
Clear it on the first landing/contact with support or fluid (consume a landing
fall event before tick-end cleanup), death/clone, logout, dimension change,
remount, excessive upward/lateral movement or expiry. It never cancels combat,
fire, lava, suffocation, vacuum, void or kill damage. Do not refresh it on repeated
requests or internal remounts; remember its used/deadline state for this player/T
until T ends, within the same bounded slot table.

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
invalidates it. Expire unused detach contexts by the next player tick. Keep only
the separate, narrower section 4 grant for its stated lifetime.

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
before detach and clear it in `finally`; they must not run the voluntary-exit
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
| RAM exit service | Detach context, bounded hint, permit and possible fall grant. | Only those bounded transient records plus proof invalidation; clear on lifecycle end. |

The immutable binding is `(L,T,player UUID,seat index)` checked against T and the
recognized endpoint manifest, not the physical mount list. Two exits cannot take
one another's seat, free capacity, authorize payload consumption or change the
manifest/checksum proof. No exit tombstone or durable declined-travel flag exists.

| Lifecycle/cut | Required behavior |
|---|---|
| Normal exit during INTENT/REGISTERED/REPAIR hold | Place self if proved safe; keep T and the receipt/seat. Another crew member remains aboard. D5 never collects this unmounted player merely because a seat exists. |
| Later READY_SOURCE or released passenger phase | Existing authority still applies. An online assigned player can be moved/remounted at the acknowledged passenger phase; no exit flag suppresses it. A later physical dismount again uses safe placement, without renewing an already used same-T fall grant. |
| Logout before/after detach or placement | Drop the context/grant; never wait for a player save or release T. A late callback must not act on a disconnected instance. Vanilla's last saved position may precede or follow exit. |
| Login while still held | Keep current held-login routing/queue and its 200-tick expiry. Do not remount just because W230 observed an earlier exit, infer an opt-out from saved Pos, or extend the reconnect queue. Expiry leaves the UUID/seat authoritative. |
| Death and respawn/clone | Invalidate the old instance and safeguard. Do not move a dead entity or move its replacement from a stale callback. T's seat stays; later existing recovery may route a living replacement, and released recovery may instead hold at DUPLICATE_PASSENGERS under its existing rule. No resurrection/automatic seat cleanup is added. |
| Dimension/portal change | Invalidate the same-Level permit/grant and skip the old completion. No cross-dimension return or chunk load; existing travel authority decides the transition. T remains unchanged. |
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
   witness. Simultaneous exits must not place both boxes in the same space. Try an
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
> same-Level one-descent safeguard; it is not guaranteed on every refusal and is
> lost on logout/restart/crash. Void, hazardous terrain, unavailable authority and
> saturation therefore remain explicit risks. D5's physical-rider-only rule and
> held-login queue's 200-tick limit remain. The mitigation is not a persistent
> passenger opt-out or an exactly-once player-position protocol.

Add the exact reviewed constants, scope and outcomes from sections 3-6, plus the
owner's actual decision/date/channel; do not write "accepted" before it exists.
Scope is only the departing player's position/fall grant, not chunk/Level save
refusal. A detach context lasts at most one player tick and a fall grant at most
200 ticks; the seat/receipt hold retains its existing potentially unbounded
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
| A0 fallback | One descent/event only, 200-tick boundary, first support/fluid contact, eight-block horizontal and exit-Y bounds, no refresh, table capacity/no eviction, death/logout/Level change/remount cleanup; all non-fall damage remains unchanged. |
| A1 placement | Real held source at altitude with a valid ground platform, alternate candidate, blocked headroom/support/hazard, border and unloaded neighbor, two passengers contending for space, a loaded gantry and released/landed endpoint. Assert safe placement or exact fallback, manifest/receipt/resource equality and zero added tickets/forced chunk loads. |
| A1 adapter | Vanilla sneak and existing-menu LEAVE both detach self without manifest removal; cancelled Forge event does not place; hook-bypass completion cannot undo a later transition. Actual internal D5/released transfers are not voluntary exits, and a reentrant failed mount cannot leave a bypass. |
| A1 proof/recovery | Detach invalidates relevant proofs before D7 replacement; unrelated claims still progress. An unmounted exit is not D5-remounted, but the later ordinary/released passenger phase still reconciles its assignment. NOT_LOADED, missing T and conflicting authority grant no distant teleport or repair. |
| S1/S2 | Packaged dedicated and LAN restart/forced-stop cuts from section 6: capture-before-detach, detach-before-placement, placement-before-player-save, saved-player-before-D7, and durable PASSENGERS_TRANSFERRED before its effect. Two subsequent starts verify seat/payload conservation and no false persisted opt-out; fallback crash loss remains disclosed. |
| V2 | The two-real-client dedicated/LAN checks of section 7, including host/guest reversal, actual coordinates/health/messages and later remount. |

Keep test classes isolated; Minecraft-dependent unit classes initialize
`MinecraftBootstrap` in `@BeforeAll` and run alone once before the full unit suite.
GameTests use a single pre-registered tick state machine, never schedule callbacks
from inside scheduled callbacks. Native cuts operate on disposable fixture worlds,
not the source world. Include a fair multi-exit budget test, without implementing
or selecting W231's per-owner quota.

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
| W230-S2 | Server permits, local/checked endpoint selection, one-descent state and physical self-exit. New small `rocket/server/BayPassengerExit*` classes and one event adapter; narrow delegation in `RocketEntity`, `RocketRuntime`, `RocketManager`, `RocketFlightService`, `RocketTransferEntities`, `BayTransferRecovery`, `BayPassengerRouting`, `BayReleasedResume` and `BayReleasedFlight`; own new unit/GameTest files; EN/zh feedback through a small v1.8 language provider and its datagen registration/output. About 800-1,200 new main lines across 5-7 classes, at most 80 changed integration lines; 700-1,000 test lines. Depends on S1 plus Q1/Q2 resolution. | Identity/fallback/adapter A0 and A1 rows, actual ordinary and released passenger paths, unchanged authority assertions, no-load counters, full unit suite, runData determinism, one full GameTest and log checker. |
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
| Q1 | Owner, through Root: accept the finite section 4 fallback and its unprotected authority/capacity/crash/void/hazard cases, or require stronger rescue guarantees and authorize a revised mechanism? D1b authorized design, not acceptance of this residual. | Keep production admission disabled. Do not implement the fallback as an accepted safety guarantee. |
| Q2 | Root: confirm that the proposed menu physical-exit adapter and both EN/zh messages describe the required seat-retaining semantics, including later authoritative remount. If the product requirement is permanent stay-behind, return that separate authority change to the owner. | Do not silently add an opt-out or change T/playerdata/reconnect behavior; retain the existing refusal of mutation-style held LEAVE until the reviewed adapter exists. |

Root's design reading, independent design review, implementation, independent
actual-diff recovery review and the specified evidence are still required. The
design does not decide W231, disposition R-043's broader risks, or pass any Gate.

## 12. Work performed and verification record

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
