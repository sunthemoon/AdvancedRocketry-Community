# W80: releasing a rocket held by offline passengers

**Revision 1**, 2026-10-16. **Proposed design, not implementation, risk acceptance or Gate evidence.**
Assignment: [W80](../TASK-W80-OFFLINE-PASSENGER-RECOVERY-DESIGN.md). Evidence of the hold: [W63](W63.md),
case F2_TWO; the review that named the limitation: [W02F](W02F.md). Authority:
[ADR-006](../../../decisions/ADR-006-ROCKET-CROSS-DIMENSION-TRANSFER.md),
[ADR-044](../../../decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md),
[ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md) and R-035 in the
[risk register](../../../11-RISK-REGISTER.md). Root owns approval, the ADRs and the register.

**Baselines.** The shared branch is read at `58d90a3f`, this worktree's base. W11b2 is read through Git only,
at `4b4fa530`, the head of `codex/v1.8.0-w11b2-rocket-integration` when this note was written (its last
code commit is `8ed38aa5`). Forge facts come from the compiled classes of the build's Forge 47.4.10 mapped
jar, read with `javap`; no game, Gradle or GameTest run took place.

**Notation.** `J` is `src/main/java/io/github/sunthemoon/advancedrocketrycommunity`; line numbers are at
`58d90a3f` unless they name `4b4fa530`. T is the held transfer record, L its logical rocket, and A its
**absent passengers**: the recorded seats whose players are offline when the operator acts. R(p, T) is the
proposed **rejoin entry** for p in A. G1 to G3 are checked generations of the existing journal writer, E1
and E2 their dependent effects, X1 to X7 the forced-stop cuts of section 3, and H1 and H2 the two holds.

## 1. Where the hold comes from

**H1, the persistent hold (W63's F2_TWO).** A PREPARED record whose flight stopped before
DESTINATION_SPAWNED is recovered to its source. `recover` keeps or rebuilds the source and makes it
stationary from T (`J/rocket/server/RocketTransferRecoveryService.java:302-337`), then calls
`settleRecoveredSource` (398-419). Line 406 removes T only `if (allPassengersOnline(server, record))`;
otherwise lines 417-418 mark T settled and return `WAITING_FOR_PASSENGERS`. The reconnect path,
`finishSourceSettlementIfComplete`, applies the same test at line 452. The predicate (469-472) requires
**every** seat of `record.sourceFlightData().passengers()` to have an online `ServerPlayer` at the same
moment, so a restart, with nobody online, never ends it. W63 observed this: each of two passengers
reconnected in a distinct JVM while the other was absent, and T stayed PREPARED through six starts.

While T exists the rocket is locked. `RocketTransferService.resourceMutationAllowed` (405-422) permits
mutation only with no record or a settled COMMITTED stationary arrival, so hand exchange, the item loader,
disassembly (`RocketDisassemblyService.java:115`) and launch (`RocketTransferService.java:73-81`) refuse.
T also holds one of the 64 journal slots (`RocketFlightLimits.java:12`) and its destination region
(`RocketTransferSavedData.java:608-621`). The destination pad was already released when recovery returned
WAITING (`RocketTransferRecoveryService.java:389-390`), and warps proceed (ADR-044 lines 228-231).

**H2, a session-only hold.** After landing, `RocketDestinationCompletion.cleanupLandedIfComplete` settles a
live COMMITTED record only when every seat of the landed entity's manifest is online
(`J/rocket/server/RocketDestinationCompletion.java:150-156`); until then resources stay refused. A restart
ends it, because restart recovery settles a landed authority without that test
(`RocketTransferRecoveryService.java:371-373`).

**W11b2 at `4b4fa530`.** The legacy lines are unchanged (`RocketTransferRecoveryService.java:407`, `:453`,
`:470-472`; `RocketDestinationCompletion.java:150-152`). Governed records, schema 3 with a native envelope,
never reach them: the recovery step filters them out (`RocketTransferRecoveryService.java:106`) and
`RocketTransferEscrowRules.java:20` refuses legacy actions on them. Their passenger wait is designed but not
yet coded. Design 2, section 5.4, row F6 (`records/W11B2-DESIGN-2.md:265` at `4b4fa530`) keeps the entry
ARMED while "assigned-passenger authority, including an offline passenger" is unresolved, and line 355 keeps
destination resources locked "indefinitely if an assigned passenger remains offline". Continuation 19 builds
F0 to F8. The coded settlement proof already requires an empty manifest and no mounted passenger on the disk
rocket (`J/resourceescrow/EscrowSettlementDisk.java:63-65` at `4b4fa530`).

**Why the hold exists.** ADR-006 lines 73-76: seats live in the entity and the journal record, and
"offline passengers rejoin at the authoritative rocket or its safe pad; the system never edits or deletes
playerdata". The record is the only pointer a login follows, because `tryReconnect` scans journal records
only (`RocketTransferRecoveryService.java:220-235`). A passenger's saved file holds the position where they
rode, up to 48 blocks above the source pad during ascent and transit (`RocketTransferEntities.java:335-342`,
`RocketFlightLimits.java:35`). It holds no rocket: `RocketEntity.hasExactlyOnePlayerPassenger` returns false
(`J/rocket/entity/RocketEntity.java:430-434`, asserted at `RocketPassengerPersistenceGameTests.java:79`).
Removing T while a passenger is offline would **leave them behind**, in mid-air or beside a pad the rocket
has left. Keeping T also prevents **duplication**: recovery elects one authority (`keepOne`, `discardAll`)
before any passenger moves, and a reconnecting passenger is moved once, to that authority only (249-279).

## 2. The operator action

**Command.** `/arce rocket recover <transfer> release-absent`, beside the existing `recover`
(`J/rocket/command/RocketCommands.java:76-78`, permission level 2 at line 63). An operator player or the
server console, RCON included, may run it; command blocks and functions are refused, because it changes
other players' state. Like `recover`, it answers at once and reports the outcome in the log.

**Refusals.** Each refusal changes nothing and sends one reply.

| Code | Condition |
|---|---|
| `NOT_FOUND`, `JOURNAL_BLOCKED` | No record T, or the journal is not operational. |
| `GOVERNED_RECORD` | `T.nativeTransfer()` is present (W11b2 schema 3); its F6 wait stays W11b2's (question 2). |
| `NOT_WAITING` | T is neither H1 (PREPARED, settled and not live in this session, the state `settleRecoveredSource` leaves when it returns WAITING) nor H2 (live, COMMITTED, with a loaded replaceable landed authority). An unclassified record needs plain `recover` first. |
| `RETRY_LATER` | The journal is not acknowledged current (`retryChecked`), or `canQueueEffect()` is false; T is fenced or `fences.permit` refuses; the bay gate refuses (`RECOVER_BY_ID`, or `bays.source(T)` is set); `RocketTransferEscrowRules.flightAllowed(..., SETTLE_SOURCE)` refuses; or the authority's end is not loaded (`recoveryEntityChunksLoaded`, which adds bounded tickets at recorded positions only). |
| `AUTHORITY_NOT_UNIQUE` | Not exactly one loaded match on the authority side. |
| `NOTHING_TO_RELEASE` | A is empty. |
| `SAVE_FIRST` (H1 only) | No vanilla save of the authority's Level, observed by Forge's `LevelEvent.Save`, since this session made the authority stationary. The reply says to run `/save-all` (section 3, X4). |
| `REJOIN_TABLE_FULL` | The table would exceed 256 entries, or one player would exceed 4. |

**Data.** The journal root gains a bounded list `released_passengers`, so its schema moves from 3 to 4
(always, as W44 did for root 3), with a migration that adds an empty list. An entry R(p, T) holds the
player, the transfer, the logical rocket, the authority's dimension, snapshot origin and `RocketRegion`, the
release's Overworld game time, and a state, PENDING or PLACED. An entry is at most 512 encoded bytes, and the
list at most 256 entries with 4 per player, so the root bound grows by 131,072 bytes. Unknown keys,
duplicates and oversize refuse the root, which keeps its bytes as today
(`RocketTransferSavedData.java:131-133, 149-153`). An older jar sees a future root and holds every transfer;
R-035 already requires a whole-world backup before a downgrade. The list shares the root because the writer
has one fixed file (`RocketTransferSavedData.java:543`) and X2 below needs T and R in one generation.

**What it does.** One server-thread action, with no refusal between mutation and queueing (P1-N2's lesson):
1. Insert R(p, T) as PENDING for each p in A and call `journal.afterAcknowledged(server, L, effect)`. This is
   G1, an ordinary generation of the checked writer; T is not changed.
2. After G1's exact acknowledgement, effect E1 rechecks the gates and calls the existing settlement,
   `settleRecoveredSource` for H1 or the landed cleanup for H2, changed in two ways only. It first removes
   from the authority's manifest the seats whose entry for T is still PENDING. Its predicate becomes "every
   seat is online **or** named by an R entry for T". When it holds, H1 removes T in the existing generation
   G2, whose existing effect E2 clears the session sets, releases the pad and logs `ARCE_TRANSFER_RECOVERY
   ... status=RECOVERED`; H2 settles T in RAM, where it stays as the normal landed reservation. For
   `SAVE_FIRST`, the recovery service records each Level's save count when it leaves T waiting.

`finishSourceSettlementIfComplete` gets the same two changes. `tryReconnect` skips T for a player with an R
entry for T, and `remountOnlinePassengers` skips such players. A rocket joining the world queues removal of
seats that have a PENDING entry for its logical rocket, at most 16 per tick, while the journal is
acknowledged current. PLACED entries never remove a seat: a placed player may board again.

**Effects.**
- **Rocket and resources:** nothing moves; the fuel vector, debits and escrow section are untouched. After G2
  (H1) or settling (H2), `resourceMutationAllowed` permits mutation by the existing rule.
- **Transfer record:** H1 removes it through the existing settlement; H2 keeps it as the landed reservation.
- **Absent passenger's riding state:** the player file holds no rocket, so a login never restores a ride.
  The seat is removed, so `canAddPassenger` refuses them (`RocketEntity.java:436-441`) until they board again.
- **Absent passenger's saved position:** never edited (ADR-006). At the next login, before `tryReconnect`
  and while the journal is acknowledged current, a PENDING entry places the player:
  - **Probe:** the cells next to the recorded region's horizontal bounds, at most 64 columns of three cells
    (floor, feet, head), in loaded chunks only. An unloaded pad gets a ticket and retries through the
    existing reconnect queue (4 attempts per tick, 200 ticks).
  - **Placement:** `stopRiding`, then a teleport to the first safe cell, with zero motion and fall distance
    and no mount. Restored blocks fail the probe, so nobody is left inside a disassembled rocket.
  - **Fallback:** if no cell is found or the queue expires, the Overworld shared spawn at surface height, as
    `SafeCelestialTravel.prepare` computes it for Earth, with no block writes.
  - **Retirement:** only after Forge's `PlayerEvent.SaveToFile` for that player. `PlayerDataStorage.save`
    fires it only after `Util.safeReplaceFile` succeeds (bytecode offsets 85 and 97); a failed save fires
    nothing. `PlayerList.remove` saves after the logout event, so a player who leaves before an autosave is
    still covered. G3 deletes the player's entries whose T is gone and marks the rest PLACED; PLACED entries
    whose T has gone are later pruned in bulk, with no dependent effect.

**Logs and display.** The command logs one WARN when accepted (`ARCE_TRANSFER_PASSENGER_RELEASE_REQUESTED`,
with the transfer, logical rocket, actor, H1 or H2, up to 16 absent UUIDs and the pad), one WARN at E1
(`ARCE_TRANSFER_PASSENGERS_RELEASED`) and the existing recovery line at E2. Each released player adds one
`ARCE_PASSENGER_REJOIN_PLACED` line, a WARN when the fallback is used, and one `ARCE_PASSENGER_REJOIN_RETIRED`
line. A stale seat removed at join and a refused command each log one INFO line; nothing logs per tick. The
placed player sees one translated chat line. `inspect` gains `absent=` and `released=` counts, and a
read-only `/arce rocket rejoins [player]` lists at most 8 entries. `RocketTransferRecoveryService` already
has 502 lines, so the planner, table, placement adapter and command go in new classes.

## 3. Crash safety

G1 adds the R entries, G2 is the existing settlement's removal of T, and G3 retires the entries. Each is
captured whole, staged, forced, read back and atomically replaced by the existing writer, and its effect
waits for the exact acknowledgement. The release adds no acknowledgement order to the journal, and none to
W11b2's governed flights, which it refuses. Its one new dependency is that G3 waits for the vanilla player
store, observed through `SaveToFile`; the save before G1 is a precondition, not an acknowledgement.

| Cut | Disk state | What a restart finds |
|---|---|---|
| X1: before G1's move | T, no R. | The hold, unchanged. The operator saves and reruns. |
| X2: after G1's move, before its callback | T and R. | Recovery rebuilds the stationary source from T and removes the PENDING seats. When every recorded seat was absent (F2_TWO), it queues G2 at once: **the release completes without the operator**. A passenger who was online at the command is not released, so T then waits only for that passenger's ordinary reconnect. |
| X3: after E1, before G2's move | T and R. | As X2. The entity's disk state does not matter while T exists, because recovery re-derives it from T. |
| X4: after G2's move, before E2 | R only. | The rocket is usable. Its entity on disk is the stationary copy that `SAVE_FIRST` made vanilla queue, still with the A seats, which the join filter removes. **Residual:** `ServerLevel.save` queues entity sections before posting `LevelEvent.Save` (offsets 60, 70 and 76), but the IOWorker write itself is unobservable (R-032). If it never landed, the entity keeps its pre-recovery flight state and active transfer T: an orphan that cannot fly, which ADR-044 lines 236-238 already accept. Diagnostic only, not a pass claim. |
| X5: after placement, before the player save | R PENDING; old player file. | The next login places the player again. |
| X6: after `SaveToFile`, before G3's move | R PENDING; player file at the pad. | The next login places the player at the pad once more; harmless. |
| X7: after G3's move, before its callback | R retired or PLACED. | Done. |

For H2, G1 is the only generation. X1 is unchanged; after G1's move, restart recovery settles the landed
authority anyway, and the join filter removes stale seats while R is PENDING.

Placement precedes the player save, and the save precedes retirement; either reverse order can lose the
safe position. A stale seat survives only if a crash lands between E1 or G2 and the entity's next save
**and** the player is placed and saved before that rocket next loads. Its effect is bounded. If the player
is online at that rocket's next transfer, the transfer moves them as it moves any online seat holder
(`RocketTransferService.java:602-624`); if offline, the next record waits and the operator releases again.
The ordinary reconnect settlement has the same X4 window today (question 8).

## 4. Time-based release

**Recommendation: none in v1.8.** The hold costs one rocket's availability, never player data, and the
operator command now ends it. A timer would release players without a human decision; game time stops
while the server is down, and wall-clock time is part of no saved authority; and the timer would be a second
automatic writer of the rejoin table.

**Risk of this answer.** Without an operator, H1 stays indefinite for its one rocket, as R-035 already
states, but now with a recovery path. The protections that refuse are `REJOIN_TABLE_FULL`, which refuses
without mutation, and the future-root hold on downgrade; neither drops or isolates a player file. A timer,
if Root chose one, would need its period, its clock, one log line per release and a rule that it never
fires while a recorded passenger is online, all in R-035.

## 5. Tests

**A0**, unit tests with `MinecraftBootstrap`, each new class also run alone:
- the codec: round trip; 512 bytes, 256 entries, 4 per player, duplicate and unknown keys, dimension length;
  the migration from root 3 to 4; a root 4 read as FUTURE and preserved;
- the planner: every refusal of section 2 and the absent set;
- the predicate: online, offline, released, and released while online;
- the probe, over a fake world: order, the 64-column bound, footprint exclusion, fallback;
- retirement only after a save event that follows placement, with PLACED kept while T exists.

**GameTests**, one class of about nine tests:
- two offline passengers: `SAVE_FIRST` first; after a Level save, the release removes T, records two entries,
  empties the manifest, unlocks hand exchange and keeps the fuel vector exact;
- one mounted online passenger and one absent: only the absent one is released;
- every refusal, including a pre-filled table, a non-operator and a command block;
- login through an embedded channel, as W02F's `85191c6c` does: placed and not riding; the entry kept until
  `SaveToFile`, then retired; a second login does not move the player;
- disassembly after the release, then login: placed outside the restored blocks, with no damage;
- H2: the landed rocket settles, T is kept, and the released player is not moved into the rocket;
- the unchanged reconnect of a passenger who was not released;
- the join filter on a reloaded entity with a stale seat;
- a refused G1 commit through the gated failure policy: no effect, T kept, then a retry.

**Native restarts**, in W63's style. New cases go in `scripts/run_v180_resource_restarts.py` and new stages
in `ResourceRestartReleaseProbe`. Gated writer-thread halts before and after the moves of G1, G2 and G3 join
`RocketJournalFailurePolicy`; each generation is told apart by comparing staged and target payloads, as
W11b2's `SR_` mode does at `4b4fa530`. Further gated halts follow placement and `SaveToFile`. After every
stop the inspector reads the journal entries, the player files (position, dimension, no `RootVehicle`), the
entity manifests and the fluid totals.

| Case | Stages or cut | JVM starts |
|---|---|---|
| F2_RELEASE | seed, check, release (`/save-all`, then the command), check, rejoin-first, rejoin-second, final check | 7 |
| F2_RELEASE_DISASSEMBLED | seed, check, release, owner disassembly, rejoin, check | 6 |
| X1 to X7 | each cut, then two restarts | 21 |
| H2-X1, H2-X2 | a live landing with one passenger offline, the release, the cut and two restarts | 6 |
| X4-DIAG | X4 with the `SAVE_FIRST` gate bypassed; diagnostic only | 3 |

The 43 starts need about 0.75 lock hours on W11b3's cost basis: about 20 seconds per start plus 15 minutes
of setup, times 1.5. One runData for the chat line and one GameTest run (15 to 18 minutes) add about 0.4
hours under the heavy-JVM lock. The unit suite needs no lock.

## 6. Proposed text for Root, and open questions

**R-035, proposed addition** (Root writes it into the register):

> **Offline passengers (W80).** *Scope:* one legacy transfer record and its rocket. A PREPARED record
> recovered to its source waits until every recorded passenger is online at once, through restarts; until
> then the rocket's resources, launch and disassembly are refused, and one of 64 journal slots and the
> destination region stay held. A landed wait ends at the next restart. Governed (schema 3) flights keep
> W11b2's F6 wait. *Duration:* until the passengers overlap or an operator runs
> `/arce rocket recover <transfer> release-absent`. That command moves no resources and edits no player
> file. It records one rejoin entry per absent passenger in the checked journal (root 4; at most 256, and 4
> per player; a full table refuses without mutation), removes their seats, and lets the existing settlement
> remove the record. At next login the player is placed, unmounted, on a probed safe cell beside the
> recorded pad, or at the Overworld spawn; the entry retires after vanilla has saved that player's file.
> *Logs:* two WARN lines per release, two lines per released player, one per refused command, none per
> tick. *Residuals:* the command needs a Level save first. A crash after the record's removal and before
> vanilla's entity write lands can still leave an orphaned entity (as ADR-044 accepts) or stale seats,
> which are filtered when the rocket loads while the entry is pending. *Recovery:* rerun after X1; a restart
> completes X2 and X3; an orphan needs R-035's whole-world offline repair. A root-4 journal holds every
> transfer on an older jar, so back up the whole world before a downgrade.

**ADR-065, proposed implementation note:**

> W80 adds an operator release for a legacy transfer that waits for offline passengers.
> `/arce rocket recover <transfer> release-absent` (operators and the console) records a bounded rejoin
> entry per absent passenger in the checked transfer journal (root 4), removes their seats, and lets the
> existing settlement remove the record. No resources move. ADR-006's rule is refined, not replaced: the
> system still never edits or deletes playerdata. A released passenger rejoins unmounted at the released
> rocket's safe pad, and the entry retires only after Forge reports that player's file saved. Governed
> records are refused; their F6 wait stays with W11b2. There is no time-based release. R-035 records the
> scope, duration, logs and recovery.

| # | Question | Recommended answer |
|---|---|---|
| 1 | Cover H2, which a restart already ends? | Yes: the same command, at small cost, with no restart. |
| 2 | Governed records? | Refuse now. After the W11b2 merge, a follow-up feeds the release into design 2's checked passenger action (lines 69-71 at `4b4fa530`) at F6. Root decides it, because it changes a governed hold. |
| 3 | Who may run it? | Level-2 operators and the console, RCON included; not command blocks or functions. |
| 4 | Where does a released passenger rejoin? | Beside the released rocket's pad (ADR-006), not at their own saved position. |
| 5 | Fallback position? | The Overworld shared spawn at surface height; never a bed or anchor, whose resolution consumes anchor charge and can fail. |
| 6 | Capacity, and a `forget` command? | 256 entries and 4 per player, refused when full. No `forget` until a table actually fills. |
| 7 | `SAVE_FIRST`, or W11b2's barrier? | The save event now. A joint own-store barrier after the W11b2 merge only if Root wants proof; it costs one barrier per release. |
| 8 | The ordinary reconnect settlement has X4's window. | Record it under R-035; a separate small leaf may give `settleRecoveredSource` and `finishSourceSettlementIfComplete` the same save gate. |
| 9 | Nearby gaps W80 leaves open. | `failBackToSource` removes T while passengers may be offline, up to 48 blocks above the pad (`RocketTransferService.java:637-667`). Disassembly ignores offline seat holders, whose saved seat lies inside the restored blocks. Reuse the rejoin table in the N2 and F8 leaves, each with its own crash table. |
| 10 | When to implement? | After the W11b2 merge, whose tests assert journal root 3 and which edits `RocketTransferRecoveryService`. First this note's read-only review (AGENTS.md section 13); then one leaf for the model, codec and A0, and one for the command, adapters, GameTests and native matrix. |

Time-based release is answered in section 4. W80 delivers this note and its record only. It changes no
code, tests, ADR, register, ledger, status, build or workflow file, and it satisfies no Gate.
