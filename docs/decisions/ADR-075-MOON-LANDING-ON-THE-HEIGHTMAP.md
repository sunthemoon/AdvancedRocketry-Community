# ADR-075 - Moon arrivals land on the heightmap

```yaml
status: ACCEPTED
date: 2026-10-26
owner: sunthemoon
deciders: [sunthemoon]
target_version: v1.8.0
slices: [W156]
related: [ADR-006, ADR-033, ADR-063]
supersedes: on acceptance, and for new Moon surface arrivals only, ADR-033's Moon landing exemption (physical admission and landing, lines 75-76) and ADR-063 section 5's Moon landing rule with section 9's y 80 arrival tests
```

**Revision 2 (2026-10-26).** This revision answers the independent read-only review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W165-review/REVIEW.md` (SHA-256
`b16b3e726bedc18823e5bed0f228e1ac6531940e9a1561934b8c51e45dad3e04`; verdict APPROVE-WITH-CHANGES, findings W165-R1 to
W165-R8). It describes both failure paths of a recheck (section 3) and completes the list of arrivals that keep y 80
behaviour (section 4). It states the recheck cost per evaluation, including the governed path (section 5), and the
terrain refusals (section 7). It adds test cases 11 and 12, sharpens cases 3 and 8 and the native restart case, states
the dismount consequence, and adds the ADR-033 lines 80-81 pointer note. No decision changes. The finding-by-finding
disposition is in the [W165 record](../work/v1.8.0-root-queue/records/W165.md).

**Accepted (2026-10-27).** In the Claude Code Root session the owner answered Root's question "Accept it, including
its named residuals?" on revision 2 (reviewed APPROVE-WITH-CHANGES, all eight findings addressed, merged as
PROPOSED at `0c94e76e9`) with the option "Accept (Recommended)". The acceptance includes the residuals named under
Consequences, among them the fixed dismount offset with no collision check. W156 resumes with the plan in the W165
record. This acceptance approves no Gate. The history of the y 80 floor is in the
[W156 record](../work/v1.8.0-root-queue/records/W156.md); the implementation plan for W156's resumption is in the
[W165 record](../work/v1.8.0-root-queue/records/W165.md).

## Owner decision

On 2026-10-26, in the Claude Code Root session, Root asked: "Moon landings: a landed rocket can float above lower
terrain because of the y=80 floor (finding F2). Keep the floor, or have a landing-rule ADR drafted that lands on the
heightmap?" The owner chose "Draft heightmap ADR (Recommended)". That option's text: a worker drafts the ADR and the
W156 plan, and the owner accepts the ADR before any code. The source is
[TASK-W165](../work/v1.8.0-root-queue/TASK-W165-MOON-LANDING-RULE-ADR.md).

## Context

- **The finding.** F2 of the 2026-10-06 real-client test: a rocket that lands on the Moon stands in the air at y 80
  over lower terrain, and its passengers fall when they leave it.
- **The cause.** `RocketLandingPadSelector.originAtSurface` raises every Moon surface to
  `SafeCelestialTravel.FIXED_FEET_POSITION.getY()`, which is 80. `RocketLandingPadSelector.unavailable` treats the
  Moon, like Earth, as a legacy surface and skips ADR-033's ground-support check.
- **The history** (W156 record). The point (8, 80, 8) comes from v0.3 developer travel, which builds its own 5 × 5
  floor at y 79 before it places a player at y 80. The rocket selector copied the height for ADR-006's Earth-Moon
  pads but never builds a floor. ADR-033 kept the Moon's y 80 and its exemption from ground support so that existing
  Moon arrivals would not move. ADR-063 section 5 kept both over the new Moon terrain (surface y 12 to 36, crater
  rims at most y 44), named the unsupported rocket and the 44-to-76-block fall, and required a separate landing-rule
  ADR that keeps existing Moon pads and recovery identities working.
- **What the floor protects.** Arrival compatibility only. It is not proof of ground: the selector starts at the
  build minimum plus one and, on the Moon, admits a footprint with nothing under it. It is not a void guard either.

## Decision

### 1. Where a new Moon arrival lands

A Moon surface arrival chosen by `RocketLandingPadSelector.select` follows ADR-033's rule for new surfaces, the rule
that Mars, Venus and the Tau Ceti worlds already use.

- **Pads.** The eight fixed candidates around (8, 8), 64 blocks apart and in the same order, are unchanged. Only
  their height changes.
- **Column rule.** For a candidate, take every distinct horizontal column that any rocket block occupies, with the
  footprint centred on the candidate as today. Read `Heightmap.Types.MOTION_BLOCKING_NO_LEAVES` for each column; that
  value is one above the highest block in the column that blocks motion or holds fluid. The surface S is the largest
  of these values and of the build minimum plus one. The rocket's lowest layer is placed at y S. There is no Moon
  floor, so S can lie below, at or above y 80.
- **Support rule.** At least one block of the rocket's lowest layer must have, directly below it, a block whose upward
  face is sturdy (`isFaceSturdy(level, position, Direction.UP)`). No block of the lowest layer may have fluid directly
  below it. Other lowest-layer blocks may overhang air: this is rigid-rocket support, not a flat-footprint rule. The
  Moon is no longer exempt from this check when a candidate is selected.
- **Other checks.** Build height, the world border, the 16-chunk footprint bound, air and no block entity at every
  rocket block, reservations, rocket and structure occupancy, the entity discovery budget and the governed column-set
  check all stay as they are.

Blocks that players placed count as ground, like terrain. A footprint over the developer platform (5 × 5 at y 79)
lands at y 80 on the platform. A footprint under a player-built roof lands on the roof when the roof is the highest
motion-blocking block and supports the rocket; a covered pad is not a landing site.

### 2. A footprint without ground

There is no further search and no raising. A candidate that fails the support rule is refused like any other unsafe
candidate.

- **All columns empty.** When every column reads the build minimum (for example after digging through the bedrock in
  creative mode), S is the build minimum plus one with air below, so the candidate is unsupported.
- **Partly empty columns, crater bowls and slopes.** The rocket rests on the highest column. The candidate is admitted
  only when that column's top lies directly under a lowest-layer block and is sturdy, and there is no fluid under the
  lowest layer. When the highest column lies only under an upper part of the rocket, the candidate is refused.
- **Refusal.** A refused candidate falls through to the next fixed candidate, as today. When no candidate is admitted,
  the launch answers the existing `LANDING_PAD_UNAVAILABLE` with the existing audit-only detail ("all fixed pads are
  occupied or unsafe", no translated key). No journal entry is written and no fuel is debited. The rocket stays on its
  launch site in its pre-launch state, and its existing transfer record T in the journal, if it has one, stays
  unchanged. The governed terminal refusals (the chunk budget and the entity budget) are unchanged.
- **Not allowed.** Raising the rocket into unsupported air, trying other heights or offsets inside a candidate,
  building a platform and clearing terrain (ADR-006, ADR-033).

### 3. The recheck of a recorded destination

The arrival recheck checks the destination recorded in the transfer record; it never chooses a new one. It runs
through `available` and `availableLoaded`: at the PREPARED arrival in `RocketTransferService.spawnDestination` and its
acknowledgement callback, at an ordinary replacement in `RocketOrdinaryLaunch`, and at the governed boundary in
`RocketGovernedSourceDispatcher`. Transfer records carry no landing-rule marker, and this ADR adds none. The Moon
recheck therefore uses the recorded height of the destination's lowest block:

- **Below y 80:** the full recheck for new surfaces, including the support and fluid rule above, as on Mars and Venus.
  A failed recheck returns the rocket to its recorded source through the existing paths, under the existing transfer,
  fuel and identity policy. It never selects another site.
  - At the pre-spawn check in `spawnDestination`, the ordinary path calls `failBackToSource` with
    `DESTINATION_PAD_BLOCKED`.
  - In the acknowledgement callback of the same step, a failed recheck only drops the live transfer. The record stays
    at `DESTINATION_SPAWNED` without a destination entity, and recovery resolves it as `KEEP_SOURCE`, because only the
    source exists.
  - At the governed boundary, `RocketGovernedSourceDispatcher` requests the return with `DESTINATION_PAD_BLOCKED` both
    at PREPARED and at the later `DESTINATION_SPAWNED` phase. When the destination step's own boundary read refuses
    instead, that step holds, and the next visit requests the return if the recheck still fails.

  These are the paths that Mars and Venus already use.
- **At y 80 or above:** the legacy Moon recheck: build height, world border, chunk bound, inspection budget, air and
  occupancy, with no support check. Every Moon destination recorded under the old rule lies at y 80 or above, so it
  keeps the rule it was selected under.

The threshold is the literal y 80, the old floor, held in its own constant. It does not follow any later change to the
developer platform's height. The threshold applies only to rechecks: selecting a new candidate always applies the
support rule of section 1, whatever the candidate's height.

### 4. What stays

- **Pads and claimed sites.** The fixed candidates' horizontal positions and order, the reservations of other in-flight
  destinations and live warm-ups, and the named station pads with their claims (Space only) are unchanged.
- **Recorded destinations and recovery identities.** Every recorded source and destination snapshot keeps its origin.
  The recovery matrix rebuilds at the recorded origin (`RocketTransferEntities.rebuildSource` and
  `createDestination`) without calling the selector, in every phase, as today. A Moon flight that is in flight across
  the upgrade keeps its recorded destination at y 80 or above and lands there.
- **Rockets already landed.** A rocket that landed at y 80 or above before this change stays where it is; nothing
  moves or lowers it. A new arrival may land below it only when the new arrival's own region does not overlap the
  landed rocket's recorded structure region, under the unchanged region-based occupancy check.
- **Stations and other bodies.** Space station arrivals, Earth (spawn-based heightmap without a support check, as
  ADR-033 keeps it), Mars, Venus, the Tau Ceti worlds and every other surface are unchanged.
- **Developer travel.** `/arce celestial goto moon` keeps its fixed platform at y 79 with the player's feet at y 80
  (`SafeCelestialTravel`). It is not a rocket arrival.
- **Arrivals that keep y 80 behaviour, exactly:**
  1. Moon destinations recorded before this change (their lowest block is at y 80 or above), through the arrival
     recheck and every recovery path;
  2. new destinations, selected under this ADR's support rule, whose lowest block is at y 80 or above (on the
     developer platform or on player-built ground): their recheck is the legacy one, without support (section 3);
  3. rockets already landed there;
  4. a rocket that landed at y 80 or above before this change and launches afterwards. Its transfer record holds that
     landed position as its source. A countdown cancel, a `DESTINATION_PAD_BLOCKED` return and every source-side
     recovery action (`KEEP_SOURCE`, `REMOVE_DESTINATION_KEEP_SOURCE`, `REBUILD_SOURCE`) put it back at that recorded
     origin, hovering as before, without calling the selector;
  5. developer travel.

  No new arrival is placed at y 80 by rule. A new arrival lands at y 80 only where the highest motion-blocking block
  in its footprint is at y 79, as on the developer platform.

  Reservations compare whole regions, height included. A new arrival can therefore be selected below an old-rule
  destination that is still in flight at the same candidate, not only below a landed rocket, when the two regions do
  not overlap. The in-flight rocket then lands at its recorded origin, above the newer one.

### 5. Limits

Unchanged: the eight candidates; at most 16 footprint chunks per candidate; at most 2,048 column reads, 2,048
occupied-block checks and 2,048 lowest-layer support reads per candidate (ADR-033's separate support phase); the
reservations; the pad and footprint exclusions; the entity discovery budget; the governed checked column set; passenger
placement, seating and the dismount position; and W83b's arrival-readiness wait for the entity sections of the
destination columns, which depends on columns, not height.

The only added work is that the Moon now runs ADR-033's existing support phase:

- **Selection.** At most 2,048 reads of the block below each lowest-layer block per candidate, so at most
  8 × 2,048 = 16,384 reads per selection, beside the existing column and occupied-block reads.
- **Each recheck of a destination below y 80.** At most 2,048 reads per evaluation of `available` or
  `availableLoaded`. That is no more than the air and block-entity inspection the same evaluation already makes (at
  most 2,048 blocks), so the support phase at most doubles the block reads of each such evaluation.
  - The ordinary path evaluates the recheck twice per arrival: before the spawn and in the acknowledgement. It
    evaluates it once more at an ordinary replacement.
  - The governed dispatcher visits at most 8 records per tick (`VISITS_PER_TICK`). While a record is due, or at
    `DESTINATION_SPAWNED` with a live source, each visit evaluates the recheck in its own check before the
    destination step, and again wherever the destination step reads its boundary. One visit can therefore evaluate it
    more than once.
  - The number and placement of these evaluations are set by existing code, and this ADR changes neither. Per tick,
    the governed path's added reads are at most equal to the reads its Moon rechecks already make.
- **Chunks.** The blocks read lie in the candidate's own columns, in chunks that the candidate has already loaded; the
  governed recheck reads loaded chunks only. There are no new chunk loads, candidates or constants.

Mars and Venus run the same evaluations with the same support phase, and have done since ADR-033. The rule needs no
other inspection.

### 6. Saved data and protocol

Neither changes. Transfer records, flight plans, rocket entities, station and pad records keep their schemas and
fields; warm-ups are not saved; destination snapshots already hold absolute origins. No network channel, packet field,
display protocol, public API, configuration key or translation changes. The refusal reuses the existing code and
detail.

### 7. Player impact

- New Moon landings stand on the regolith, and passengers step off onto the ground. Over a crater the rocket rests on
  its highest footprint column and may overhang the bowl.
- A Moon pad is refused when the rocket's bottom layer does not reach the highest ground under its footprint. This
  happens on natural slopes, at crater rims and in crater bowls, not only after digging or building. An example is a
  rocket whose engines sit under a wider stack of tanks, when the highest column lies only under the tanks. It is the
  ADR-033 behaviour that Mars and Venus already have, but the Moon has never refused a pad for terrain before.
- A refused pad falls through to the next fixed pad, which loads that pad's chunks, within the unchanged bound of eight
  candidates and 16 chunks each. Over rough ground, such a rocket can be refused at all eight pads. The launch then
  receives the existing "landing pad unavailable" answer, as it does when digging leaves no ground under any pad.
  Before this change, the rocket would have hung at y 80.
- Players who built landing platforms at y 79 keep receiving arrivals at y 80 on them. A hangar roof over a pad is
  landed on, not under.
- In chunks explored before the C15b terrain (flat end stone with its surface at y 4), new arrivals land at y 4.

## Tests

### Changed meaning: named test transitions

The two `MoonArrivalGameTests` cases keep their batches (`moon_arrival_highlands`, `moon_arrival_crater`). Each case is
renamed, and its y 80 assertion is replaced by a stronger one. The old names are retired; no assertion is weakened.

| Old name | New name | New assertions |
|---|---|---|
| `aMoonRocketArrivesAtY80OverTheHighestHighlands` | `aMoonRocketLandsOnTheHighestHighlands` | The pad area is shaped flat at y 36 (`MOON_SURFACE_MAX`) as now. The rocket is LANDED on the Moon. Its lowest block is at the largest `MOTION_BLOCKING_NO_LEAVES` value over its footprint columns, which is y 37. At least one block below the lowest layer is moon turf with a sturdy top, and no block below it holds fluid. The lowest block is below y 80. |
| `aMoonRocketArrivesAtY80OverACrater` | `aMoonRocketLandsInACrater` | The crater is cut under the pad as now. The lowest block equals the largest `MOTION_BLOCKING_NO_LEAVES` value over the footprint columns, measured after the crater, and is below the lowland's y 21. The support rule holds. |

### New cases

GameTests, each shaping the pad areas it uses with `MoonPadArea` first and restoring every block it changes:

1. **Mixed columns.** One footprint column higher than the rest, under a lowest-layer block: the rocket rests on it at
   that column's height plus one and overhangs the others.
2. **Empty first pad.** Candidate 0's footprint columns emptied down to the build minimum: the rocket lands on
   candidate 1, supported.
3. **No ground at any pad.** Every candidate's footprint emptied: `LANDING_PAD_UNAVAILABLE` with no translated detail
   key; the rocket's flight data equal to the value read before the request (so its fuel is unchanged and it stays
   FUELED on its launch site); no journal entry for the request; and `RocketEscrowGate.idle(rocket)`. These are the
   checks that `EscrowGovernedMarginGameTests` already makes for a refused launch.
4. **Developer platform.** The platform prepared on the shaped pad: the rocket lands at y 80 on the platform.
5. **Occupied pad.** A rocket parked on the ground at candidate 0: the new arrival lands at candidate 1.
6. **Changed support after selection.** The recorded destination's support removed: the rocket returns to its source
   with its original fuel, as one logical rocket. Water placed below: the recheck refuses. These are the Moon versions
   of `PlanetaryLandingGameTests`.
7. **Passengers.** A seated passenger lands on flat shaped ground, dismounts and stands on the ground within a bounded
   number of ticks, with no damage.
8. **Recorded y 80 destination.** A PREPARED record whose destination's lowest block is at y 80 over lower ground
   lands at its recorded origin under the legacy recheck, with exact fuel and one logical rocket. The flight is
   ordinary (not governed). The record is built with `RocketTransferRecord.create` from a destination snapshot
   relocated to that origin, so that the snapshot, the arrival flight data's origin and the checksum agree. A
   governed record cannot be replaced this way, because its escrow entry binds the record's checksum.
9. **Recovery identity.** The recovery rebuild of a recorded Moon destination at y 80, and of one below y 80, places
   each at its recorded origin.
10. **Unchanged paths.** The existing station-arrival and Earth-arrival cases rerun unchanged.
11. **Highest column under an upper part.** A rocket whose lowest layer does not cover its whole footprint (for
    example, a tank one layer above the engine, over a column that no lowest-layer block covers). At candidate 0, the
    column under that upper block is the highest. Candidate 0 is refused, and the rocket lands, supported, at
    candidate 1. The selection is also asserted directly on the selector with a hand-built snapshot, so the case does
    not depend on the structure scan accepting that shape.
12. **Governed threshold.** Two governed flights, each from a real launch, because a governed record cannot be
    replaced. In the first, the destination is below y 80 and its support is removed during the flight: the governed
    boundary refuses and returns the rocket to its source with `DESTINATION_PAD_BLOCKED`, its original fuel and one
    logical rocket. In the second, the destination is on the developer platform at y 80 and the platform is removed
    during the flight: the governed boundary applies the legacy recheck, and the rocket lands at its recorded origin
    without support, as the first residual under Consequences describes.

Unit tests cover the rule without a running server: the surface below, at and above y 80; all columns empty; mixed
columns; the support decision (no sturdy top, fluid below, one sturdy top with overhang); the recheck threshold at
y 79, 80 and 81; and unchanged Earth, Space and Mars decisions.

A native restart case covers recovery identities across an upgrade. The jar from before this change stops one Moon
flight at PREPARED, with its destination at y 80, and lands another rocket at y 80. The new jar then restarts the same
world. The in-flight rocket lands at its recorded y 80 origin with exact fuel and one entity; the landed rocket keeps
its position; a new launch lands on the ground, and its region overlaps neither the landed rocket's region nor the
in-flight rocket's recorded destination region. A second restart leaves all three unchanged.

Every other GameTest that flies to the Moon runs in one complete run with the unchanged log checker, and no assertion
is weakened. A test whose result depends on what earlier tests left in the pad area shapes that area first. The W165
record lists those tests, the fixtures that call the selector directly, the synthetic records below y 80, and the
native probes and scripts that the GameTest run does not cover.

## Supersession pointer notes on acceptance

Root adds these notes when the owner accepts this ADR. The decisions they point from stay as written, for history.

- **ADR-033, lines 58-59** ("Preserve Earth spawn-based and Moon fixed-pad compatibility"): the Moon's fixed pads keep
  their horizontal positions; their height follows ADR-075.
- **ADR-033, lines 75-76** ("Moon keeps its legacy minimum landing Y=80 and does not acquire this new ground-support
  requirement"): superseded for new Moon arrivals by ADR-075, which applies the heightmap and support rule above to the
  Moon. Destinations recorded at y 80 or above keep the legacy recheck.
- **ADR-033, lines 80-81** ("Earth/Moon/Station pad compatibility and captured recovery identities are unchanged"):
  the Moon pads' horizontal positions and the captured recovery identities stay unchanged; the height of new Moon
  arrivals follows ADR-075.
- **ADR-063 section 5, lines 249-254** (Moon terrain "bounded below the landing height"): the terrain bound stays and
  now protects the developer platform; Moon landings follow the heightmap (ADR-075).
- **ADR-063 section 5, lines 255-262** ("Moon landing unchanged (ADR-033)"): superseded for new arrivals by ADR-075.
- **ADR-063 section 5, lines 309-310** (seams: "the Moon keeps its fixed y 80 rule over lower terrain"): new Moon
  arrivals land on the heightmap (ADR-075).
- **ADR-063 section 5, lines 314-325** ("Moon arrival height (player impact)", including "v1.8 does not make that
  change"): ADR-075 makes that change in v1.8 after the owner's 2026-10-26 decision; recorded destinations and
  pads stay as it requires.
- **ADR-063 section 9, lines 637-639** (Moon landing "at y 80 or above, as before"): replaced by ADR-075's test
  transitions. Developer platform travel over the highest highlands stays.
- **ADR-063 "Revisit when", lines 692-693** (a landing-rule ADR lands Moon rockets on the heightmap): met by ADR-075.

ADR-006 gets no note: its server-owned pad list, authority boundary, recovery matrix and landing checks are unchanged.
ADR-063's alternative A2 stays as a record of the reasoning at the time.

The C15b verification table (`docs/work/v1.8.0-c15b-surfaces/VERIFICATION.md`, line 121) names the two retired test
methods. It is a historical record and is not edited. The table under "Changed meaning" above maps the old names to
the new ones, and W156's record repeats that mapping for anyone who follows the C15b evidence.

## Alternatives

### A. Keep the floor

No code change, but rockets keep hanging 44 to 76 blocks over the Moon terrain and passengers keep falling (F2). The
owner chose the heightmap.

### B. Keep the floor only for columns without ground

This was W156's first suggestion. It raises the rocket into unsupported air, which is not allowed, and an all-empty
footprint would still land.

### C. Search further inside a candidate

Trying lower heights or shifted offsets would create coordinates outside ADR-006's fixed pad list and needs extra
inspection per candidate. The eight fixed candidates already give the fallback.

### D. Support at selection only, with no Moon support recheck

Simpler, but every Moon arrival would then skip the pre-spawn support recheck that ADR-033 requires on other
surfaces. The y 80 threshold limits the legacy recheck to destinations that the old rule could have recorded.

### E. Support recheck for every Moon destination

A flight recorded under the old rule and in flight across the upgrade would fail the recheck and return to its source.
That breaks the requirement that such flights keep working.

### F. A saved landing-rule marker

It would make the recheck exact, but it needs a saved-data schema change, which this decision avoids.

### G. Land under roofs by searching downward for clearance

More inspection per column, and a different rule from ADR-033's. Left to a future decision if the owner wants hangars.

## Consequences

- Positive: Moon landings rest on the ground, passengers no longer fall, and the Moon uses the same landing rule as
  Mars and Venus.
- Residual: a new destination at y 80 or above (only on player-built ground or the developer platform) whose support
  is removed during the roughly 160-tick flight still lands unsupported, as every Moon arrival does today.
- Residual, for the owner to accept explicitly: the dismount position is a fixed offset from the rocket (1.5 blocks
  along x, at the rocket's base height) with no collision check. Under the old rule's open sky it always lay in air.
  On the heightmap, over a rising slope, the dismount column can be higher than the rocket's base: the passenger can
  then be placed inside the higher block and take suffocation damage until they move out. Over a lower column, the
  passenger drops by as much as the ground falls away there. Mars and Venus landings have had the same behaviour since
  ADR-033. Passenger placement is outside this decision; a collision-aware dismount stays under "Revisit when", and
  test case 7 stays on flat ground.
- Residual: a new arrival can land below a rocket left hovering at y 80, or below a recorded y 80 destination that is
  still in flight, when their regions do not overlap.
- Residual: developer travel builds its platform at y 79 regardless of rockets. A rocket landed at the first pad and
  tall enough to reach y 79 (at least 35 blocks over generated terrain, whose highest crater rims reach y 44) could then have
  platform blocks inside its structure region. Developer travel is an operator command.
- Tests: GameTests that fly to the Moon now land at the terrain height. Anything left in the pad columns, such as the
  developer platform from `BootstrapGameTests` or blocks of a disassembled rocket, changes that height. A test that
  needs a specific height shapes the pad area first, without weakening any assertion. Synthetic Moon records below
  y 80 get explicit support (W165 record).

## Implementation note: W156 (merged 2026-10-27)

W156 implemented this decision and merged at `5586e19e6`.

- `SurfaceLandingRule` holds the surface, support and threshold decisions. `MOON_LEGACY_RECHECK_Y` is 80.
- `RocketLandingPadSelector` no longer raises a Moon destination to the old floor.
- Batch `moon_landing_rule` covers new cases 1 to 9 and 11, and `EscrowMoonLandingRuleGameTests` covers case 12.
  The unit tests cover the rule, including y 79, 80 and 81.
- **The native restart case, as implemented.** Recovery keeps an ordinary flight that was PREPARED at the restart
  at its source (`KEEP_SOURCE`). This ADR leaves that rule unchanged, so the PREPARED upgrade case asserts it. A
  separate DESCENT upgrade case covers the in-flight landing at the recorded y 80 origin, with exact fuel and one
  entity, and both cases pass two restarts.
- GameTest cases 8 and 12 cover the y 80 recheck on the ordinary and governed paths.
- The predecessor smoke and all 29 cases of the predecessor matrix pass on the new jar.

This note approves no Gate.

## Revisit when

- the owner wants landings under roofs or into hangars;
- rockets get a collision-aware dismount position;
- the Moon terrain bound or the developer platform's height changes;
- worlds from before this change no longer need to load (the y 80 threshold can then go).
