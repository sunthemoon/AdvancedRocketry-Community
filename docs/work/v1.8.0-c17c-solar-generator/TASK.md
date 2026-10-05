# C17c-SOLAR-01: single solar generator

Date: 2026-10-06. Milestone: v1.8.0. Integrator/committer: Root.
Status: READY for isolated source authoring; implementation and runtime checks remain open.
Owner decision: [preservation scope](OWNER-DECISION-01.md), not whole R-021 acceptance.

## Outcome, authority and dependencies

Deliver one obtainable generator/panel under accepted ADR-065 sections 2 and 9.
Root selects the exact single-leaf rules in the
[original specification](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17c-solar-leaf-20261006-e6d3a1/PROPOSAL-01.md)
(20,614 bytes, SHA-256 `a02669f1a314b6fb63b4731b9d1d502a3c330362d566ecf4aca47d88fdbfb307`).
The [original independent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/solar-leaf-contract-review-20261006-61af03/REVIEW-01.md)
has one Medium: exact Space exposure. The separate
[exposure amendment](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17c-solar-up-exposure-20261006-c2189b/UP-EXPOSURE-ADDENDUM-01.md)
is 13,691 bytes, SHA-256 `8f983d3af119e1fd23b186e181e8a6a368bdf777c1d0f0eb15dac1c5cb14be73`.
Its [independent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17c-solar-exposure-contract-review-20261006-f249d8/REVIEW-01.md)
(SHA-256 `a98298407aa715b2ca6094de670d1ee80d321fd4b76a68dcce0dd9d7f97e15da`)
identifies no Critical/High/Medium and one Low: the installed-owner check needs
the clarification below. Root adopts this bounded technical rule for source
authoring under the user's authorization to implement reviewed contracts with
no unresolved Critical/High/Medium. The original proposal, amendment and review
stay immutable; their static findings are not native execution results.

ADR-062 section 7 permits a smaller v1.8 leaf. This single uses the actually
existing silicon-wafer quartz/redstone recipe, copper and glass, plus read-only
existing station lookup. It implements no C17a propulsion, beacon circuits or
new station transition. Full C17c's C17a/C16d dependency row is unchanged;
C16d, arrays, beacons, bay, shared hatches and guarded resource writers remain
outside this task. Root adopts no dependency waiver or batch completion.

## Selected technical rules

- Block+item IDs `solar_generator`, `solar_panel`; BE/Menu ID `solar_generator`.
  Metal cubes, strength 5/6, correct stone-tier pickaxe; generator piston BLOCK.
  Plain panel has no BE, tick or capability. Supported ordinary drops carry no FE.
- Panel recipe `GGG/SSS/CCC`: Forge glass, existing silicon wafer, Forge copper.
  Generator recipe `PPP/CGC/CCC`: panel, glass, copper. Each produces one and
  uses the same-ID recipe-book OR unlock. No existing recipes/tags are replaced.
- One collector, 10,000 FE, six-side output-only ENERGY, no resource slots.
  Shared push/pull quota 1,000 FE/device/tick; simulation preserves quota/FE,
  six non-loading neighbor checks, epoch/current-BE checks and reentry lock.
- Intensity 0..16; multiplier integer 1..4/default1. Request
  `floor(2 * multiplier * effectiveIntensity)` (0..128), credit only room.
  Surface native isDay/canSeeSky(top); operational station orbit resolver,
  no Space-global day/pressure/angle factor, existing missing-body fallback.
  Station direct-UP predicate must use the reviewed amendment, not canSeeSky.
  It queries only the generator's existing own FULL chunk and its cached
  `getSkyLightSources().getLowestSourceY(x & 15, z & 15)`. The cutoff must be
  `Integer.MIN_VALUE` or within inclusive build bounds; other values are
  unavailable. For a legal generator Y, `(long)y + 1 >= cutoff` is exposed;
  a top coordinate equal to the exclusive maximum build height is legal.
  Native light/face semantics decide transparent and partial-block exposure.
  No cache initialization, column scan, heightmap substitution or chunk request
  is permitted. Ordinary no-skylight load/reload and cache updates still need
  executed tests.
- Surface weather attenuation `(1 - 0.5*rain)*(1 - 0.5*thunder)`, native
  samples once at partialTick1. Finite levels clamp0..1; invalid inputs refuse.
  Display permille `floor(1000*attenuation + 0.5)` never feeds generation.
  Station/N/A weather is1000. Countdown is allowed; next tick samples committed
  orbit after existing synchronous warp. No new TRANSIT or penalty.
- COMMON `energy.solarGeneratorMultiplier` and `machines.solarGeneratorEnabled`.
  Disable stops credit/export but retains FE and clears stale feedback without
  world queries. Night/roof/context loss allow export of stored FE.
- Root `arce_solar_generator`: schema1, exactly int schema1 and int energy0..10000,
  4,096 bytes/depth16/nodes1024. Bounded faithful unsupported roots pass through
  unchanged with operation/removal blocked; non-admissible input retains its
  original Tag reference and triggers the existing chunk-save protection.
  Sticky chunk refusal can affect unrelated edits, expands at257 distinct
  chunks to Level, has unbounded paired ERROR retries, and needs backup/offline
  repair/new Level. R-021 is open; no writer/durability acceptance follows.
- Seven scalar menu fields: energy, actual credit, reason, sky, day, context,
  weather permille. Fixed open payload VarInt(-1),VarInt(1), no trailing data.
  Opening/stillValid check connected/live/non-spectator/non-FakePlayer, current
  loaded BE, same Level and8-block distance. No action C2S, inventory mutations
  or quick-move. Unknown codes fail closed.
- Reason IDs0..7 remain GENERATING, DISABLED, BUFFER_FULL, SKY_BLOCKED,
  NOT_DAYLIGHT, CONTEXT_UNAVAILABLE, NO_IRRADIANCE, REPAIR_REQUIRED.
  Precedence: repair, disabled, unavailable, sky, daylight, no irradiance,
  full, generating. Context0..3: unavailable, surface, station, orbit fallback;
  day0..2: not-day, day, not-applicable. Non-generating credit resets to zero.

## Ownership and internal integration

After admission, one isolated worktree author may create only:
`machine/solar/{SolarGeneration,SolarExposure,SolarGeneratorBlock,
SolarGeneratorBlockEntity,SolarPorts,SolarSave,SolarProtection,SolarGeneratorMenu}.java`,
`client/SolarGeneratorScreen.java`, `datagen/{V180SolarData,V180SolarLanguage}.java`,
four named tests `SolarGenerationTest`, `SolarSaveTest`, `SolarMenuWireTest`,
`V180SolarDataTest`, one `gametest/SolarGeneratorGameTests.java`, and this task's
`PROGRESS-01.md`/`HANDOFF-01.md`. Source root is the existing Java package.
No worker changes to existing files or generated resources.

SolarExposure is one initially inactive lifecycle handle, no world collection
or new public API. Startup acquires committed station lookup once; reads are
loaded-only, server/current-owner checked and use one captured catalog; stopping
closes matching ownership. Root registration injects BE/menu/config/exposure
suppliers and block BE factory; no supplier starts a world handle.
Owner validation is explicitly two-stage, resolving the review's Low L1:
wrong thread/server/Level/position or removed-owner metadata rejects before any
chunk query. Otherwise obtain at most one own chunk with `getChunkNow`, then
compare `chunk.getBlockEntities().get(pos) == currentOwner`. A missing chunk or
identity mismatch returns unavailable before catalog/cache sampling. This map
lookup neither creates a BE nor scans the map. A same-position orphan cannot
be detected from metadata alone, so it is not promised zero chunk queries.
The ceiling remains one non-loading own-chunk query and one cached column.
Root alone owns lifecycle, registries, config, data-generator/client binding,
shared tags/languages, all generated resources, risk/provenance/status/ledger,
source checkpoints, commit and normal push. The
[NEW origin declaration](../../provenance/v1.8.0-c17c-solar-new-resources.md)
must be committed before grids/source authoring.

## Required checks and boundaries

Independent actual-diff review; actual named JUnit and registered GameTests for
formula/schema/wire, crafting/loot, FE quota/reentry/epoch, environment/roof,
stock no-skylight Space exposure/cache updates, fallback/reload and unsupported
raw retention. New code declarations are not test results. Run committed clean
build/test, DataGen twice with zero repeated writes/clean output, unfiltered
GameTest and asset/provenance/client-boundary checks. S1 supported energy and
open/blocked Space columns need ordinary stop/reload; malformed/oversized
refusal must retain literal stopped bytes and retrigger after new Level.
Real GPU/multiplayer and all G0-G9 remain open; no old guard results substitute.

Local C is below10GB; no local full build/GT/native server. All scratch and
process temp stays in own directories under `D:/GitHub/ARCE-Task-Evidence/v1.8.0`.
Use hosted non-root CI for heavy checks. Worktree/base/head and exact method
signatures are recorded in the later assignment, not invented here.
