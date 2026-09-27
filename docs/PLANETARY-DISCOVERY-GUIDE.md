# Discovering planetary destinations

Mars, Venus and the gas giant require discovery before a new rocket arrival.
Earth, Moon and shared Space retain their existing travel rules. Body names and
environment information are public: a visible star-map node is not permission
to land there.

## Research a destination

1. Assemble a Data Satellite in a powered Satellite Terminal with a chassis,
   solar module, data storage unit and unbound control chip.
2. Keep its bound control chip, select the destination, and launch the package.
   An already launched satellite can start another mission with the same chip.
3. Wait for the mission to complete (200 running-server ticks with built-in
   data), then claim its result at the terminal.
4. The shared discovery opens that body's eligible destinations. The open
   rocket console refreshes within 20 ticks; select a destination and explicitly
   launch as usual. Discovery does not supply fuel or reserve a landing pad.

The built-in mission awards 120 research points and costs 100 if the body was
not recorded as discovered when the mission began. The result is 20 net points
for such a mission, or 120 for a later mission started after discovery. These
values and target choices come from the satellite definition. Concurrent
missions each retain their start-time fee; one player's completion does not
change another mission's captured price. Repeated claims do not award twice.

## Shared knowledge, private resources

Discovery is shared by everyone in the world, including players who join later.
Research points, lifetime totals, satellites and claim permissions remain with
their owner. Discovery does not grant access to another player's terminal,
rocket or private station. An authoritative surface visit also records that
body; entering shared Space records Space, not a station's orbit body.

Locked surface nodes use muted star-map markers and a research-required message.
Station quotes apply the same orbit-body lock in addition to membership checks.
The gas giant never has a surface Level or landing target, even when discovered;
the satellite terminal displays its discovery state. Existing permitted orbit
stations are separate destinations. A prepared flight is not cancelled by a
later discovery-rule reload, and departure does not require a source discovery.

## Data packs and existing worlds

In a schema-2 celestial definition, set `"discovery_required": true` to require
a recorded discovery for new arrivals. Missing or false retains unrestricted
arrival, subject to the other travel checks. Only a literal JSON boolean is
accepted; schema-1 definitions cannot include the field. For a researchable
custom body, also include its ID in an active satellite definition's
`allowed_targets` (at most 16 targets per definition), with a valid catalog body
and travel route. Merely enabling discovery does not create a research mission.

The server evaluates the final destination, not intermediate graph anchors.
Removing a definition makes it unavailable without deleting its saved progress;
restoring the same identity restores its discovery. Existing records, research
accounts and mission prices are not reset. Historical discovery storage retains
at most 128 body IDs, including removed IDs. At capacity, a new claim remains
pending after its one research award/fee; it does not unlock or evict an old ID.
Repeated claims cannot fix capacity. Keep a backup and ask the server operator
to resolve blocked data rather than deleting unknown SavedData files.

Client/server builds must match **flight protocol 8**. Celestial display protocol
2 and existing save schemas are unchanged. Use pre-upgrade backups when rolling
back: older strict schema-2 builds do not understand `discovery_required`.

Normal save/restart and repeat-claim handling do not constitute a guarantee
against every power-loss point across the separate celestial and mission files.
Do not restore only one file from a different backup generation.
