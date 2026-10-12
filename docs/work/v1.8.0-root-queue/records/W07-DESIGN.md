# W07 design note: station controls, orbital distance and orientation

Scope: ADR-065 sections 5.1, 5.2, 5.2.1 and the C17b rows of section 11.1. Nothing
physically moves; warp cost, route distance and `orbit_body` are unchanged.

## Record schema 3 and registry root 5

- Station record schema 3 adds one required compound, `orbital_controls`, with exactly
  five keys: `format_version` int 1, `distance` int 4..190, `phases` int array of three
  values 0..359,999 (EAST, UP, NORTH, milli-degrees), `rates` byte array of three values
  -60..60 (turns per 72,000 ticks), and `epoch` long >= 0. Every other record field keeps
  its schema-2 meaning, including the environment's gravity, vacuum and solar angle.
- Registry root 5 (format epoch `v1.8.0-station-orbital-controls`) keeps the stations,
  reservations and warp energy lists, and adds the required `orbital_clock` compound:
  `schema_version` int 1 and `logical_tick` long >= 0, nothing else, at most 128 bytes.
  Root 5 without the clock, and older roots with one, are malformed.
- Load refuses the whole root, kept verbatim, when any record's epoch is greater than the
  saved `logical_tick`. Nothing is clamped or repaired.
- The 8,192-byte record bound and the 4 MiB root bound are unchanged.

## Migration

The existing root 1/2/3 to 4 rules run first. Root 4 to 5 then:
- sets `logical_tick` to 0;
- rewrites every record to schema 3 with distance 4, rates 0, epoch 0, and phases
  (solar angle, 0, 0), so the EAST phase reproduces the stored sun angle exactly;
- keeps identifiers, team, geometry, gravity, vacuum, solar angle and warp balances.

A migrated root that no longer fits a bound is refused intact. A current root 5 is a no-op.

## Clock

- A pure `StationOrbitalClock` lives in the station registry SavedData, so it has the
  server's lifetime. It starts at the saved `logical_tick`.
- The station manager advances it once per server END tick, saturating at
  `Long.MAX_VALUE` (then `CLOCK_EXHAUSTED`: new nonzero rates are refused, STOP works).
- It never reads game time, day time, wall time or client time.
- Every checked write samples the clock once. The candidate carries that sample as its
  clock, and validation requires it to be at least the last committed floor and at
  least every epoch. A known commit publishes the record and raises the floor together.
- A healthy ordinary save writes the current tick. It does not raise the floor, because
  an ordinary save is not a disk barrier; only a known checked commit raises it. A
  quarantined registry writes the floor and stops the clock. (Corrected by W07F: this
  sentence first said that an ordinary save raises the floor, which the code never did.)
- While any rate is nonzero, the registry is marked dirty at most once per 20 ticks.
- Phase: `floorMod(phase + (delta % 72000) * rate * 5, 360000)`, with
  `delta = tick - epoch`. Changing a rate, STOP, or a fixed phase samples the old
  phases at the tick into a new epoch.

## Transitions

`StationState` gains `ALTITUDE_ONLY` (only the distance changes) and `ORIENTATION_ONLY`
(only phases, rates and epoch change; the epoch does not go back). Growth, gravity and
relocation now also require unchanged controls. The writes use the existing stage,
fsync, readback, replace and publish path, the 100-tick per-station cooldown (shared
with gravity) and the shared server-wide write budget.

## Payload schema 2 and protocol 4

- `celestial_snapshot` goes from protocol 3 to 4. Index 1 stays `StationSkyContextPacket`,
  now payload schema 2.
- Layout: byte 2, a strict 0/1 presence byte, then the sky kind byte (0 SPACE_FALLBACK,
  1 PLANETARY, 2 SINGULARITY), an optional body ID (at most 128 characters), distance as an
  unsigned short, three phase ints, three rate bytes, the epoch long and the sample long,
  with epoch <= sample. Maximum 167 bytes, bound 256, trailing bytes rejected.
- There is no station UUID, name or owner.
- The pass sends on context change and, while a rate is nonzero, a heartbeat at most once
  per 20 ticks per player.
- A blocked or quarantined registry sends NONE.
- The client extrapolates at most 40 ticks and clears its cache on NONE, a Level change
  or logout.
- The controller menus use the vanilla menu button transport, as W03 does. The
  `classic_controls` channel is not registered by this slice. (Superseded by W07F: Root
  did not accept this deviation, and W07F registers `classic_controls` at protocol 1 as
  ADR-065 section 11.1 lists it and moves the controllers to its intents and views; see
  `records/W07F.md`.)

## Elevator guard

`ElevatorStationGuard` gains `orbitalControls`, which defaults to the deletion rule: a
pair names the station, or a transit endpoint stands in its region. A changed altitude or
orientation is refused (`ELEVATOR_BOUND`, or `ENDGAME_UNAVAILABLE` while the endgame root
is down). An unchanged value stays a no-op. Binding rules and warp/deletion guards are
unchanged.
