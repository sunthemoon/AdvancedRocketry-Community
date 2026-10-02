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

Each system has its own switch in the common config. It is read each time a
device acts, so a change applies without a restart:

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
  - endpoints, `MISSING` endpoints, tombstones, zones and registrations
    waiting for a save;
  - the ledger's records, stubs and pending observations.

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
- **Downgrades.** A v1.6 server that loads a v1.7 world drops the endgame
  blocks, which become air, and ignores the root. Escrowed and in-flight cargo
  is lost. Downgrades are not supported.

## Laser drills in physical mode

Physical mining (off by default) digs real blocks at laser targets. Each layer
takes cells without a full collision shape first, then the rest. A cell that a
neighbour reaction in the same layer already destroyed is skipped, so vanilla
drops from such a reaction may lie in the shaft. Each layer writes one audit
line, and at most seven layers run per tick in total.
