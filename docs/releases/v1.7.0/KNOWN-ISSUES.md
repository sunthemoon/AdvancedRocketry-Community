# v1.7.0 known issues (development)

Known limitations of the v1.7 development build (`1.20.1-1.7.0-dev`). This is not a
release note and not a Gate approval. Operators should also read the
[endgame operator guide](../../ENDGAME-OPERATOR-GUIDE.md).

## Compatibility and upgrade

- **New endgame file.** v1.7 adds `world/data/advancedrocketrycommunity_endgame.dat`
  (root schema 1, ADR-054 §10). It is created at the first endgame change; a v1.6 world
  opens unchanged until then. A malformed or future root is kept byte for byte, and the
  server refuses to start until an operator restores or moves it.
- **Downgrade.** A v1.6 host drops the endgame blocks (missing registry entries become
  air) and ignores the endgame file. Escrowed and in-flight cargo is lost. Downgrades are
  not supported.
- **Network.** v1.7 adds the `advancedrocketrycommunity:endgame` channel (protocol 1,
  server to client device views). Clients and servers must run the same development
  build.
- **API 1.8.** The public API gains `EndgameEffect` and `EndgameEffectEvent` (ADR-054
  §5.1). Apart from `ApiVersions`, which reports 1.8, every earlier API class is
  unchanged (the C11a classifier comparison); C12 and C13 leave the API classes
  byte-identical.
- **Direct 1.12.2 saves** remain unsupported.

## Endgame cargo (ADR-054 §11)

- **Restoring an older endgame file** from a backup is outside the guarantee (R2-I4):
  entries that sources still hold register again (`SEQUENCE_GAP`), and payloads
  delivered after the backup can be delivered a second time. Restore it only together
  with the region files it belongs to.
- **Saves off.** While saving is off, no stub is pruned, and once live records and stubs
  reach 256 every escrow is refused with `TRANSIT_LIMIT` until saving resumes (R4-L4).
  Cargo already in flight still arrives.
- **Stated residuals.** ADR-054 §11 lists the narrow loss cases that remain (for
  example a chunk write lost after its save event when the acknowledgement was already
  flushed): each loses a payload and none duplicates one.
- **Purging a claimed record** removes only the record: the payload already held in the
  paid endpoint's incoming area is delivered there. No command discards such a payload
  (C12R-I4, C12R2-I2).
- **A receipt acknowledged elsewhere after a file restore** is dropped without a freeze
  or an audit line; an incoming payload held with it freezes as `REDIRECT_CONFLICT`, so
  nothing is paid twice (C12R-I4).
- **Redirected-back elevator cargo.** Cargo redirected back to an anchor source that is
  then removed too names no station any more; the station's rebuilt pair refuses it and
  an operator can only purge it (C12R2-I6).
- **ADR-054 §11 rows 6 and 10 read differently from §9.1.** Row 6 says a destination
  whose removal was flushed but whose chunk was not saved "re-claims once"; row 10 says a
  restored source's stale entry is dropped as `OUTBOX_STALE_DROPPED`. The code follows
  §9.1 and §9: a removed ID is retired, and the restored block entity is
  `ENDPOINT_RETIRED` and frozen with its contents. The record is delivered once through a
  redirect (row 6) or as registered (row 10), and `endpoint resolve` destroys the frozen
  copy (`INCOMING_DESTROYED`) or discards the entry (`OUTBOX_DISCARDED`). Exactly once
  holds; the native C13 cuts check these outcomes. The row wording is for the maintainer
  to align (C13 review R2-N1); the accepted ADR is not edited here.
- **Endpoints in chunks held below FULL.** A chunk within 13 chunks of a forced chunk
  stays in memory after its own tickets go, below FULL and never unloaded. The ledger
  leaves an endpoint there alone until its chunk is FULL again or unloads, because
  changes there would not be saved; cargo for it waits meanwhile (C13 harness finding).
- **Copied endpoints.** Creative pick-block with NBT, structure blocks, `/clone` or
  movers that keep block-entity data can copy an endpoint with its ID. The copy is inert
  (`ENDPOINT_POSITION_CONFLICT`) and its chunk saves never count for the original
  (C12R-L3). Creative and operator tools stay outside the contract.

## Elevators (ADR-059)

- **Ride across a torn save.** A ride's energy lives in the departure's chunk and the
  rider in the player file. Vanilla saves players before chunks, so a crash between the
  two writes after a commit can give one free ride, or charge one ride the rider did
  not keep. The native forced stops during a countdown and right after a commit (with
  saves off) left neither (C13).
- **Bind spacing.** Player binds share a 20-tick server-wide spacing and a 100-tick
  per-station cooldown; a refused request answers `ROOT_BUSY`. An admitted request uses
  its slot even when its write then fails (C12R2-I3).
- **Untested by automation:** `ARRIVAL_UNLOADED` (the ride's own ticket loads the
  arrival chunk before the commit) and a bind whose root write fails (I5).

## Laser drills (ADR-055)

- **Physical mode** is off by default. A cell that a neighbour reaction in the same
  layer already destroyed is skipped, so vanilla drops from that reaction may lie in the
  shaft (C11R2-I1). One audit line is written per layer, at most seven layers per tick.

## Performance

- The numbers in [PERFORMANCE](PERFORMANCE.md) are from the Windows development host
  and are provisional (ADR-054 §7). Reference-hardware measurements are `[H]`.
- **The flush budget is not reliably met under load on this host.** The C13 closure run
  stayed within 60 ms (at most 45.8 ms in 55 flushes), but earlier loaded runs had single
  flushes of 116 to 562 ms, stalls in `FileChannel.force`; a typical flush is mostly
  server-thread CPU (encoding, compression and the mandated read-back). ADR-054 §7
  requires the follow-up ADR that moves the write off the server thread; its scope and
  whether reference hardware also exceeds 60 ms are the maintainer's decisions.
- **Railgun throughput.** ADR-054 §7's reference load asks for 16 launches every 20 ticks.
  The ledger's persistence gates (at most four outbox entries per railgun, each waiting
  for its chunk's save and 40 ticks) allow about 3 escrows a second whatever the root's
  content; the reference load measured about 2.1 escrows a second at the reference root. The deviation
  is recorded for the maintainer (C13 review R2-N2).

## Visual and multiplayer

- V0 was not run: the development host is Windows, with no Xvfb or LLVMpipe. V1 on real
  GPUs and V2 with two real clients are open (`[H]`): device screens, the laser, railgun
  and generator effects, the tether, and the gravity field feel.
