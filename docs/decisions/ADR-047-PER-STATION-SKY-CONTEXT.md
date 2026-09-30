# ADR-047 — Per-station sky context (ORBIT-03)

```yaml
status: ACCEPTED
revision: 3
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-30
acceptance_basis: maintainer direction to finish v1.5 with recommended solutions, after independent contract review of revision 1 ("accept with changes"); all required changes applied in this revision
target_version: v1.5.0
development_dependency: ADR-036, ADR-040, ADR-041, ADR-043, ADR-044, ADR-046
amends: ADR-036 (a per-station orbit context and one server context message are now allowed, for Space only), ADR-046 UI-01 (celestial channel protocol 2 -> 3 in the pinned table)
implements: V150-ORBIT-03 (per-station sky/UI synchronization and lifecycle)
network_protocol: celestial_snapshot 2 -> 3 (one added S2C message); snapshot payload schema stays 2; other channels unchanged
```

## Context

Stations are cells in the one shared Space Level (ADR-040), and each orbits one body
(ADR-041). Warp changes that body (ADR-044). The server derives a station's effective
environment from the orbit body, and `/arce station environment` shows it to anyone
standing in the region: the orbit ID (even when unavailable), solar intensity and sun
angle.

ADR-036 gave Space one generic sky and ruled out "a fabricated per-station orbit
context" and "a new server context packet". ADR-041 and ADR-044 left the per-station
sky to ORBIT-03. ADR-046 UI-01 pins every channel and allows ORBIT-03 a bounded S2C
context only under its own ADR and a protocol bump.

What the client already has:
- the celestial display snapshot (`celestial_snapshot` channel, message 0). It lists
  every catalog body with its visual profile ID, solar intensity and pressure,
  including undiscovered bodies;
- local sky profiles from resources.

## Decision

### What is sent

A **station sky context** is exactly one of:
- **none**;
- **orbit**: the ID of the body the station the player stands in currently orbits.

It carries no station UUID, name, owner, member list, balance or region. `environment`
already shows the orbit ID to anyone in the region, and the snapshot lists every
body. The only new aspect is an automatic push at a 10-tick resolution, which lets a
visitor see a warp commit as it happens (review F11).

### Server side (authority)

- **Source:** each online player's context comes from:
  - their own server Level and `player.blockPosition()`;
  - one indexed `StationRegistrySavedData.findAt` lookup (O(1), no chunk access, no
    scan of stations).

  A client never supplies a position, station or body.
- **Registry states:**
  - a blocked (non-operational) registry gives "none";
  - a quarantined registry still resolves stations, as every read does, because
    quarantine blocks checked updates only.
- **When:**
  - every 10 server ticks, the server computes every online player's context and
    sends it only when it differs from the last one sent to that player;
  - the last-sent map is keyed by player UUID, because a respawn creates a new player
    object;
  - a login starts with nothing sent, so a player standing in a station receives the
    context within 10 ticks;
  - respawn and Level change: see Lifecycle below;
  - a warp commit, an expansion or a deletion shows at the next pass;
  - a catalog change reaches the client through the snapshot, which the client
    resolves against.
- **Budget:**
  - at most one message per player per 10 ticks, and none while nothing changes;
  - each pass is O(online players);
  - the map holds one entry per online player, pruned every pass and cleared at stop.
- **Wiring:**
  - one `StationSkyContextService` is built at mod construction with the celestial
    channel's sender;
  - it reads `StationRegistrySavedData.get(server)` on each pass, not a runtime
    bridge, so it survives integrated-server world changes;
  - dependencies run station → celestial only.

### Lifecycle (one rule)

The client clears its context on logout and on `ClientPlayerNetworkEvent.Clone`,
which fires on a respawn or a Level change. The server forgets the player on
`PlayerRespawnEvent` and `PlayerChangedDimensionEvent`, so the next pass re-sends the
current context after the client's reset. The client applies a context only while
its Level is the Space Level; anywhere else the stored value is ignored.

**Main-thread invariant** (revision 3, final review B2). Transmission order alone is
not enough: vanilla defers the respawn handling, which fires `Clone`, to the client
main thread. Message 1 must therefore be handled on the main thread
(`consumerMainThread`), so it is queued behind that respawn handling. Handling it on
the network thread could apply a re-sent context and then have `Clone` erase it,
while the server believes it is sent. `NetworkProtocolPinTest` requires
main-thread handling for every registered message.

**Dispositions** (revision 3, final review B3 on revision-1 F8):
- The pass has no per-player isolation. Nothing in it throws for a valid registry,
  because the station record already bounds orbit IDs.
- No defensive clear at login is added: the client clears on logout, and a new
  session starts with nothing sent.

### Protocol

- `celestial_snapshot` protocol `2` → `3`. It adds message 1,
  `StationSkyContextPacket`, `PLAY_TO_CLIENT`.
  - The snapshot payload schema stays 2.
  - The pin table (`src/test/resources/network-protocols.txt`),
    `CelestialSnapshotV2Test` and ADR-046 UI-01's wording are updated with this
    ADR.
  - No other channel changes, and there are still four channels.
- **Encoding:**
  - one strict boolean byte (0 or 1);
  - when 1, one resource location of at most 128 characters (the
    `BoundedCelestialCodecs` bound, as for station orbit IDs);
  - at most 131 bytes after the message index.
- **Decoding rejects:**
  - a presence byte other than 0 or 1;
  - a longer or invalid ID;
  - trailing bytes;
  - more than 131 bytes.

  A malformed message is a decoding error and closes the connection, as for every
  other ARCE message.
- Protocol-2 and protocol-3 clients and servers refuse each other at login (exact
  version match), as every ARCE channel already does.

### Client side (presentation only)

- **Selection:** in the Space Level with an "orbit" context whose body is in the
  snapshot, the Space sky keeps its own profile (sky colour, stars, fog). It gains:
  - **the orbited body:** a disc of 30° angular radius. Its centre is 25° below the
    horizon toward +X, not at the nadir. The Space Level's fixed time (18000) puts the
    sun at the nadir (sun angle π), so the disc centre is 65° from the sun, beyond the
    disc radius plus the largest sun halo (2.5 × 12° = 30°). The disc is drawn after
    the stars and before the sun halo and sun, so it covers stars and never the sun.
  - **the sun:** it follows the orbited body's solar intensity from the snapshot,
    with or without a sky profile, as the server's environment does. Its radius comes
    from `SkyMath.sunRadius`; it is hidden only at intensity 0. There is no graded
    opacity.
- **Colour** (`OrbitalAppearance`). A surface sky's day colour is the sky seen from the
  ground, and Earth and the gas giant have no surface profile, so packaged bodies have
  explicit colours:

  | Visual profile | Colour | Bodies |
  |---|---|---|
  | `advancedrocketrycommunity:earth` | `#3E6FB0` | Earth |
  | `advancedrocketrycommunity:moon` | `#8C8C8C` | Moon |
  | `advancedrocketrycommunity:mars` | `#B0583A` | Mars, Tau Ceti e |
  | `advancedrocketrycommunity:venus` | `#D9C28A` | Venus |
  | `advancedrocketrycommunity:gas_giant` | `#C8A06A` | gas giant |

  Any other body uses its profile's day colour when that colour's relative luminance
  is at least 0.2; otherwise, or without a profile, it uses the neutral `#6E8FB4`.
  Colours are provisional and are tuned in V1.
- **Fallback:** a body missing from the snapshot, no context, or any Level other than
  Space gives exactly the existing Space sky.
- **Caching:** the context is part of `SkySelection`'s single-entry cache key, by value.
  The body lookup runs only on a cache miss.
- **Rendering:**
  - no new mesh: the disc reuses the existing sun disc mesh, turned and scaled;
  - no textures, no per-frame uploads and no profile-dependent geometry;
  - release on logout and on resource reload, as before.
- **No UI** (ADR-046's no-screen decision). Also out of scope: per-station sun
  elevation (ADR-041's sun angle), rings, moons of the orbited body and night-side
  lighting. They need V1 evaluation first.

### Verification

| Plan item (V1.5.0 §) | Evidence |
|---|---|
| §6 ORBIT-03 synchronization and lifecycle | GameTest `theSkyContextIsServerDerivedSentOnChangeAndFollowsEveryChange` covers: two players in two stations, send on change only, warp, expansion ring, respawn and Level-change re-send, blocked registry, deletion, other Level, map cleanup, no chunk loads, no identity |
| §11.1 orbited body, star, sun hints | JUnit `OrbitalSkyTest` (disc clear of sun and halo; every packaged orbitable body visible against space), `SkySelectionTest` (context, fallback, cache) |
| §11.3 station data does not mix | Same GameTest: two stations and two players at once |
| §12.4 region coordinates never load chunks | Same GameTest: loaded-chunk counts unchanged in every Level |
| UI-01 protocol pin | `NetworkProtocolPinTest` at `celestial_snapshot` 3, messages 0 and 1; `CelestialSnapshotV2Test` |
| Codec bounds | JUnit `StationSkyContextPacketTest` |
| §11.4 real client, cache and position; §11.5 GUI scale; multiplayer | Open |

**Visual evidence:**
- **V0** (Xvfb/LLVMpipe) was planned in COMPLETION-PLAN C4. It cannot run on the
  Windows development host of this slice, so it is recorded as not run; the deviation
  is noted in the plan and the log.
- **V1** (real GPU) and **V2** (two real clients) stay open for ACC-02 with these
  cases:
  - the disc for each packaged body;
  - disc and sun at the fixed Space time;
  - a warp seen from inside the station;
  - leaving and entering a region;
  - respawn in a station;
  - resource reload;
  - two clients in two stations;
  - no OpenGL errors in the client log;
  - frame time against a baseline without a station;
  - sky state released on logout (revision 3, final review B3).

  A client launched unattended by an agent is not V1 evidence.

ORBIT-03 therefore stays `implemented-unverified`. Automated tests are not visual
acceptance (AGENTS.md §8).

## Consequences

- Players see the body their station orbits, and the sky follows a warp without any
  station identity reaching the client.
- A protocol bump: mixed protocol-2 and protocol-3 clients and servers cannot connect.
  This is acceptable during v1.5 development and is recorded in the CHANGELOG.
- One small periodic server pass is added: at 100 players, 100 indexed lookups every
  10 ticks, and a message only on change.

## Alternatives considered

- **Client-side derivation from the region grid:** the client would need the station
  registry, which discloses identities and adds a large synchronized structure.
  Rejected.
- **A new channel:** adds a channel for one message. Extending the display channel,
  which already owns the snapshot, is smaller and keeps four channels.
- **Sending the full environment:** duplicates snapshot data and invites the client to
  treat it as authority.
- **Tinting with the surface sky's day colour** (revision 1): the Moon would be
  invisible, and Earth and the gas giant would show nothing.

## Rollback

Remove the message, the service and the client selection hook, and return the
protocol to 2 (or bump it again). No save data is involved.

## Review history

- **Revision 1:** an independent contract review found it "accept with changes": 3
  High (disc colour source, the disc covering the sun, a stale context after respawn),
  4 Medium (the undeclared ADR-036/046 amendments, the ID bound, the sun disagreeing
  with the server, verification gaps), 3 Low (wiring, Space-only selection,
  wording) and 3 Info (disclosure confirmed, the channel choice confirmed, the working
  tree ahead of the ADR).
- **Revision 2** applies every required change:
  - explicit orbital colours and a visibility test;
  - a fixed disc direction clear of the sun and a separation test;
  - the single lifecycle rule and GameTests for it;
  - the declared amendments;
  - the 128-character, 131-byte, strict-boolean codec;
  - the sun following the snapshot's solar intensity;
  - the extended GameTest and the traceability table;
  - the recorded V0 deviation;
  - the wiring statements and the wording fixes.

  The report is archived in `docs/work/v1.5.0-orbit-sky/`. Acceptance is not a Gate
  approval.
- **Revision 3** (2026-09-30), from the final independent v1.5 review (area B):
  - the main-thread invariant is stated and pinned (B2);
  - the F8 dispositions are recorded (B3);
  - the V1 cases are completed (B3).

  In tests, a GameTest now drives the production-registered service through a real
  respawn, Level round trip and logout (B1). Quarantine and stop-clearing are tested
  (B3). The report is archived in `docs/work/v1.5.0-closure/`.
