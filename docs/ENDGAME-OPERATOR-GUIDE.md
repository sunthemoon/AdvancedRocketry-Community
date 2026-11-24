# Running the endgame systems

This guide is for server operators. It covers the v1.7 endgame systems: the
orbital laser drill, the railgun, the black-hole generator, the area gravity
field and the space elevator. It explains how to switch them, what they cost the
server, which commands you have, and how saves, backups and broken blocks
affect cargo. The rules come from ADR-054 to ADR-059 in `docs/decisions/`.

Every operator command below needs permission level 2. A command that changes
state answers with a code and writes an audit line; lists print one page of at
most 16 lines.

## Switching systems on and off

Each system has its own switch in the server's
`config/advancedrocketrycommunity-common.toml`. To change a switch reliably,
stop the server, edit that file, and start the server again. Treat changes as
taking effect after a restart; do not rely on editing the file while the server
is running. For singleplayer, fully exit and restart Minecraft instead.

Devices read Forge's loaded configuration, not the TOML file, each time they
act. Forge's file watcher can miss edits, leaving the previous values loaded.
`/reload` reloads data packs, not this common config, and ARCE has no operator
command to reload it. After restarting, use `/arce endgame status` to confirm
the loaded switches before operating a device.

| Key | Default | What turning it off stops |
|---|---|---|
| `endgame.laserDrill.enabled` | `true` | New drill operations |
| `endgame.laserDrill.physicalMining` | `false` | Digging real blocks at laser targets (logical mining is unaffected) |
| `endgame.railgun.enabled` | `true` | New railgun launches |
| `endgame.blackHoleGenerator.enabled` | `true` | Burning fuel |
| `endgame.gravityField.enabled` | `true` | Active gravity fields |
| `endgame.spaceElevator.enabled` | `true` | Binds, rides and new elevator shipments |

A disabled system keeps its blocks, items and saved state, and the world still
loads. Cargo already in flight still arrives and settles, and players can
always unbind an elevator. Disabling a system is the supported way to stop it;
removing the mod is not (see "Backups and downgrades").

The limit keys (`endgame.endpointsGlobal`, `endgame.endpointsPerOwner`,
`endgame.zones`, `endgame.transitRecords`, `endgame.transitPerOwner` and the
per-system `active…` and `…PerTick` keys) can only be lowered from their
defaults: 2,048 endpoints, 64 per owner, 256 zones, 256 transit records and 32
per owner. The `energyPercent` keys scale each system's energy costs or
output. `endgame.intentIntervalTicks` (at least 10) and
`endgame.selectionIntervalTicks` (at least 2) rate-limit players' menu actions.

## Watching the server

- `/arce endgame status` shows the switches and the endgame root:
  - its save epoch, accounted size and whether a write is pending;
  - `exempt_barriers`, the root writes that were not spaced player requests;
  - endpoints, `MISSING` endpoints, young and settled tombstones, zones, and
    registrations waiting for a save (`awaiting_registration`);
  - the ledger's records (`transits`), `stubs`, and the ledger endpoints in
    loaded chunks (`loaded_ledger_endpoints`), with
    `REMOVAL_SETTLEMENT_PENDING` while a removal settlement is unwritten;
  - the audit ring's size.

  A second line shows each system's activity, including elevator rides and
  `ride_tickets`, the arrival tickets held: one per pending ride.
- `/arce endgame timing` reports each system's work per tick over the last
  1,200 ticks: means, percentiles and the root flushes. The ledger, rides,
  field lookups and flushes are listed separately. Use it before raising the
  `energyPercent` or per-tick keys.
- `/arce endgame audit all [page]` or `/arce endgame audit <system> [page]`
  pages through the last 512 audit lines, newest first. Lines name devices
  and players by UUID.

## Protected zones

`/arce endgame zone add <name> <from> <to> [players]` protects a column in the
Level you stand in, up to 4,096 blocks on a side and 256 zones in all. The
listed players (at most 16) are the owners whose devices may still act there.
`zone remove <name>` and `zone list [page]` manage them.

Endgame effects check zones against the owner of the device that causes them.
For an elevator ride this is the owner of the endpoint the rider departs from.

## Endpoints, owners and tombstones

Railguns, laser targets and elevator anchors and terminals are endpoints. Each
is registered in the endgame root once its chunk has saved it.

- `/arce endgame endpoint list [player] [page]` lists them, and
  `/arce endgame device inspect <pos>` shows one device's state.
- `/arce endgame device owner <pos> <player>` gives a device to another player.
- An endpoint whose block vanished without being broken (an editor, a lost
  region file) becomes `MISSING` once a save shows it absent.
  - Its owner can drop it with `/arce endgame endpoint forget <id>`.
  - An operator can retire it with `/arce endgame endpoint retire <id>`, which
    returns its unpaid claims and turns it into a tombstone.
  - `retire` refuses an ID with no index record, such as one just broken, and
    changes nothing. It also refuses while the endpoint's chunk is loaded.
- Tombstones remember removed endpoints, so that a block restored from an old
  save cannot pay twice. A young tombstone settles once a save shows the block
  gone; `/arce endgame tombstone settle <id>` settles it at once.
  `tombstone evict <player>` drops a player's settled tombstones. The oldest
  settled ones beyond 256 per owner or 8,192 on the server are dropped on
  their own, and past 4,096 tombstones housekeeping drops those a chunk load
  has shown absent for 6,000 ticks.
  - A tombstone that a transit record or an elevator pair still names is
    never evicted.

Players cannot break an endpoint that holds cargo or that an elevator pair
names; an operator can. Breaking one settles its claims in the same tick.
Movers and pistons cannot move endpoints.

## Cargo in flight

Railgun and elevator cargo travels through the transit ledger. The source pays
into an outbox, and the transfer becomes a record once its chunk has saved.
The destination claims it into a non-extractable incoming area, which empties
into the receive buffer after the next save. Cargo is never duplicated or lost
by a crash at any point of that path; ADR-054 §11 lists the narrow residuals.

Commands for operators:

| Command | Effect |
|---|---|
| `/arce endgame transfer list [page]` | Records with state, source and destination |
| `transfer inspect <source> <seq>` | One record |
| `transfer redirect <source> <seq> <endpoint>` | Sends a record whose destination is gone to another endpoint. Owners may redirect their own cargo |
| `transfer purge <source> <seq>` | Removes a record and destroys its payload |
| `transfer resettle <source> <seq> incoming\|moved` | Applies a removal settlement that never reached disk, as its log line records |
| `/arce endgame endpoint resolve <id>` | Settles a frozen endpoint's contents item by item. Owners may resolve their own |

Redirects must follow each system's route rule:

- railgun cargo stays within the star system, and only an operator may send
  it to another owner's railgun;
- elevator cargo goes back to its source, or to the endpoint of the original
  destination's kind in the same station's current valid pair.

Owner redirects and resolves share the barrier spacing described below.

Purging has two edge cases:

- A `CLAIMED` record's payload already sits in the paid endpoint's incoming
  area. Purging the record only removes the record, and that endpoint then
  delivers the payload. No command discards such a payload: `endpoint
  resolve` at a registered endpoint acts only on frozen conflicts.
- Purging an outbox entry that has no record needs its source loaded.

## Elevators and stations

An elevator pair joins a station's terminal to an anchor on the surface below.

Binding is spaced:

- all players' binds, owner redirects and owner resolves together get one
  request per 20 ticks;
- a station gets one bind or elevator redirect per 100 ticks;
- a refused request answers `ROOT_BUSY` and changes nothing;
- unbinding is never refused, and it counts toward the next bind's spacing;
- operators are exempt, and their writes are counted in `exempt_barriers`.

A station whose pair exists cannot warp. While a pair or transit cargo still
references a station, it cannot be deleted: deletion answers
`ELEVATOR_REFERENCES` until you unbind and settle them.
`/arce endgame elevator unbind <station>` and `elevator inspect <station>`
work whatever the pair's validity.

Automatic shipments act as the endpoint's owner, never as an operator, even
while that owner is online with operator permissions.

## Saves, backups and downgrades

- **Saves off.** While saving is off (`/save-off`, common during backups), no
  stub is pruned. A stub is the ledger's record of a delivered transfer, kept
  until its endpoint's chunk saves the move. Once live records and stubs reach
  256, every escrow on the server is refused with `TRANSIT_LIMIT` until saving
  resumes. Cargo already in flight still arrives. `status` shows the stub
  count. Keep backups short, or expect railgun and elevator launches to pause.
- **The root is written while saves are off.** `/save-off` stops the world's
  chunk and player saves, not the endgame root: it keeps being written about
  every 5 seconds while it has changes, and at once for binds, removals and
  operator commands. A backup tool will find
  `advancedrocketrycommunity_endgame.dat` changing. That is safe: the file is
  replaced, never rewritten in place, so a copy holds one complete version,
  and a root newer than the region files is a state the ledger recovers from
  (ADR-054 §11). Copy the endgame file after the region files, never before
  them. On Windows, a copy that holds the file open can make one write fail;
  it is logged and retried as described below.
- **Write failures.** The root is
  `world/data/advancedrocketrycommunity_endgame.dat`, written through a checked
  atomic path.
  - A failed write keeps the changes in memory and logs
    `ARCE_ENDGAME_ROOT_WRITE_FAILED` once; the next write retries them.
  - A bind whose write failed is taken back and answers `ROOT_BUSY`.
  - A removal settlement that is still unwritten logs
    `ARCE_ENDGAME REMOVAL_SETTLEMENT_PENDING`, and `transfer resettle` can
    apply it after a crash.
- **Unreadable root.** A malformed or future root is kept byte for byte, and
  the server refuses to start. Restore it, or move it aside knowingly: cargo in
  flight is then lost.
- **Restoring an older endgame file** from a backup is outside the guarantee:
  - entries that sources still hold register again (audited as
    `SEQUENCE_GAP`);
  - payloads delivered after the backup can be delivered a second time;
  - a claim that the ledger now names at another endpoint freezes as
    `REDIRECT_CONFLICT` until you resolve it.

  Restore the endgame file together with the region files it belongs to,
  never alone.
- **Backups while the server runs.** Autosave, `/save-all` and `/save-all flush`
  only request the writes of the satellite registry and the rocket transaction
  journal, and return at once. Each write finishes on its own thread and is
  published on a later tick, so after `/save-all flush` returns these two files
  may still be the previous versions. Atomic replacement keeps each file whole,
  but a live copy can still pair an older registry or journal with newer
  chunks.
  - For a consistent backup, stop the server normally and wait for
    `ARCE_CHECKED_WRITER_DRAINED root=satellite_registry` and
    `ARCE_CHECKED_WRITER_DRAINED root=rocket_transactions` in the log. Copy
    the world after that.
  - A stop that logs `ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE`, or one ended by
    the watchdog or a kill, gives no such guarantee. The files are then the
    last committed or unknown versions, and later changes are lost.
  - If the writer's thread is stuck on storage, the stop can wait without
    limit. End the process before copying or reopening the world, then
    rely on restart recovery or a known-good backup.

  There is no command that drains these writers while the server keeps
  running (ADR-074 item 9).
- **Migration backups.** The first v1.8 start of an older world copies the
  managed SavedData files into `advancedrocketrycommunity-backups/` in the
  world folder before it migrates them. These copies hold only the ledger and
  the transfer journal; they are not a complete backup and cannot be restored
  alone. An interrupted migration leaves its backup folder and, in `data/`,
  staging files named `.…migrating-<uuid>`. The next start migrates again and
  succeeds, and the leftovers stay. At most five backup folders count,
  complete or not: a world that still needs migrating and already has five
  refuses to start with `ARCE-BETA-2004` and is not changed. Stop the server
  and move older backup folders out of the world folder; keep them, do not
  delete them. Move staging files out of `data/` only after a complete backup
  and after the world has finished upgrading (KI-V180-UPGRADE-01).
- **Downgrades.** Downgrades are not supported.
  - A v1.6 server that loads a v1.7 world drops the endgame blocks, which
    become air, and ignores the root. Escrowed and in-flight cargo is lost.
  - A world that v1.8 has run cannot go back to an older JAR. An older JAR
    that does not know v1.8's transfer journal (schema 3) refuses to start,
    but first rewrites `level.dat` and `level.dat_old` and removes the
    registry entries of newer blocks and items. An older JAR that knows escrow
    but not v1.8's ledger (root 3) refuses with `ARCE-BETA-2001` before the
    world loads. Either refusal exits with code 0, so read the log for
    `ARCE-BETA-2001` instead of trusting the exit code.
  - Before upgrading to v1.8, and before any downgrade attempt, stop the
    server and take a complete world backup: every dimension's terrain,
    entity and POI files, global data, the journal, the ledger and receipts,
    player data, and `level.dat` and `level.dat_old`. Every recovery replaces
    the whole world folder with such a backup; never restore single files or
    single stores.
  - To stay on v1.8 after a downgrade attempt, restore the complete backup
    taken before the attempt and start v1.8. A world that an older JAR only
    refused with `ARCE-BETA-2001` can also be started on v1.8 directly. To go
    back to the older version, restore the complete backup taken before the
    upgrade to v1.8; everything done on v1.8 is lost.

  This is R-044's accepted downgrade limit (KI-V180-DOWNGRADE-01).

## Whole-world repair after a FLOOR_CONFLICT fence

This repair is approved for v1.8.0's build and the repair tool of the same
commit, `scripts/repair_v180_rebuild_source.py` in this repository, run with
Python 3 (Root decision R681). Its modes are `plan`, `apply` and `census`;
`--help` lists the arguments.

- **When this applies.** Your server log shows the line
  `ARCE_TRANSFER_FENCED transfer=<id> reason=FLOOR_CONFLICT repair=offline-whole-world`
  for one transfer, and the rocket it names is standing still at its launch
  position. Worlds on v1.8.0, where production cargo admission is off, are
  not expected to reach this state.
- **Before you start.** Stop the server, disable automatic restart and keep
  players out. Do not delete the transfer entry, reset an epoch, restore one
  store or rebuild the historical tank. Use only this tool and this build. You
  need free space for three copies of the world, plus 10 GiB free on each
  drive the tool uses.
- **Qualifying start.** Start the unrepaired world once on this build, with
  players excluded. Load the rocket's chunk and the device's chunk with
  `forceload add`, wait for the fence line for the transfer, keep the server
  running for at least 10 seconds after it has finished starting, remove the
  forced chunks with `forceload remove`, and stop cleanly. If the world was
  last run on an older build, this start also upgrades its saved data; that
  is expected and repairs nothing.
- **Backup and plan.** Make a hash-verified backup of the complete world:
  every dimension, player file, ledger, journal, backup folder and metadata
  file. Run `plan` on the backup with the log of the qualifying start and
  `--profile enabled`. Read its identities, amounts, its account (UNKNOWN
  unless you supplied a verified witness) and its exact edit, which removes
  one journal entry, before you confirm the plan digest.
- **Apply and verify.** `apply` writes a fresh copy and changes only the
  transfer journal. Run `census` on the copy before any start. Then start the
  copy three times in isolation, each a separate server process: load the two
  chunks, run `save-all flush`, wait for its completion message, remove the
  forced chunks, stop cleanly, and run `census --after-start` after each stop.
  No start may log a fence line or a blocked store. These starts advance the
  copy's game time, so the validated copy is slightly ahead of your original.
- **Replace the whole world.** Promote only the entire validated copy while
  the server is stopped, and keep the original and the backup. Never copy only
  the repaired journal into a live or different world. Restarting alone is not
  a repair, and the historical snapshot is not a refund.
- **If the tool refuses.** Your world has another shape, or a step went wrong.
  Keep the server stopped, keep the original and the backup, and ask for a
  separately reviewed repair. Do not work around a refusal. Every refusal
  leaves the original and the backup unchanged; discard a partial copy after
  an `apply` error instead of repairing it piece by piece. The reasons you are
  most likely to meet are:
  - `ROOT_NOT_CURRENT:<store>` at `plan`: expected before the qualifying start
    on a world last run on an older build. Do the qualifying start, take a new
    backup and plan again; if it persists, stop and ask.
  - `PLAN_ROOT_STAMPS_DIFFER`: plan again with `--profile enabled` on this
    build.
  - `FENCE_EVIDENCE_MISSING`: give the log of the qualifying start.
    `FENCE_REASON_DIFFERS` is a different problem: stop and ask.
  - `INSUFFICIENT_SPACE`, `WORLD_LOCKED`, `PATH_OVERLAP`: correct the
    environment and run the step again.
  - `STALE_PLAN`, `INPUT_CHANGED`: make a new backup and plan again.
  - `MIGRATION_DURING_VERIFICATION`, `RAID_FILE_SET_CHANGED`: discard the
    copy and start again from the qualifying start; do not add or delete raid
    files by hand.
  - Any other reason, such as `SECOND_PHYSICAL_ROCKET`,
    `PASSENGER_ASSIGNMENT`, `DESTINATION_IN_TARGET_CHUNK`,
    `RESOURCE_DISCREPANCY` or an authority reason: stop and ask. The tool
    never deletes records to make a world fit.
- **Limits.** It repairs one shape: a `FLOOR_CONFLICT` fence after
  `FF_REBUILD_SOURCE` (a fluid unload whose source was rebuilt), with no
  related escrow authority and an empty fluid-account list. It is not a
  general `FLOOR_CONFLICT` repair, a settlement, a refund or a store restore,
  and it enables no disabled production feature. Its raid check knows the
  nine raid files of the vanilla and mod dimensions; other mods' raid files
  are reported, not judged. After a start the census is semantic, not
  byte-for-byte: it reports every changed file for you to read.

The full refusal table and the evidence are in the
[X03-D record](work/v1.8.0-root-queue/records/X03-D-OPERATOR-GUIDANCE.md).

## Laser drills in physical mode

Physical mining (off by default) digs real blocks at laser targets. Each layer
takes cells without a full collision shape first, then the rest. A cell that a
neighbour reaction in the same layer already destroyed is skipped, so vanilla
drops from such a reaction may lie in the shaft. Each layer writes one audit
line, and at most seven layers run per tick in total.
