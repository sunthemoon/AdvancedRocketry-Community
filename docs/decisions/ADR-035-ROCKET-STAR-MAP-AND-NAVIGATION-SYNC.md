# ADR-035 — Rocket star map and navigation synchronization

```yaml
status: ACCEPTED
date: 2026-09-27
owner: sunthemoon
scope: v1.4.0 navigation
authority_basis: standing maintainer authorization to use recommended implementation decisions
```

## Context

The console has typed server quotes but cycles through bodies, keeps opening-only
station names and does not explain unavailable targets. Celestial display data
already has a bounded, generation-tagged snapshot. Navigation must join these
views without treating client layout or stale quotes as launch authority.

## Decision

- Add a schematic, interactive star-map view inside the existing flight menu,
  not a second independently active container. Show parent relationships,
  body/environment details, selected surface and accessible orbit stations.
  Preserve the compact console, boarding, cancellation and typed launch flow.
  Selection is not a launch; the ordinary explicit Launch button remains.
  Editable selection follows stable target IDs, not station list indices.
  Cancellation always uses the synchronized active plan, including after station
  removal or while catalog refresh is pending.
- Body metadata is public under the existing catalog policy. Non-landable gas
  bodies remain inspectable but acquire no surface target. Discovery/research
  policy remains V140-DISC; navigation does not invent or unlock hidden routes.
  Stations remain private: only the existing VISIT policy's bounded list is
  sent, with ID, validated name and orbit body. Revoked/removed entries disappear
  on the next refresh and cannot be launched from a stale view.
- Capture one planetary generation for each quote batch. Use the same physical
  admission and route/fuel planner as launch. Check rocket control authority,
  source-station access and current state before advertising launch readiness.
  Explain ready/current/unavailable route, components, thrust, capacity, fuel,
  invalid fuel state, invalid flight state and lack of authority with bounded
  server status codes. Do not scan terrain, load chunks or reserve landing pads
  while preparing a map. A quote is not a landing guarantee.
- Quote status wire IDs are explicit, never Java ordinals:

  | ID | Meaning |
  |---:|---|
  | 0 | READY |
  | 1 | CURRENT |
  | 2 | NO_ROUTE |
  | 3 | MISSING_COMPONENTS |
  | 4 | INSUFFICIENT_THRUST |
  | 5 | FUEL_STATE_MISMATCH |
  | 6 | INSUFFICIENT_CAPACITY |
  | 7 | INSUFFICIENT_FUEL |
  | 8 | INVALID_STATE |
  | 9 | UNAUTHORIZED |
  | 10 | UNAVAILABLE |
  | 11 | ARITHMETIC_OVERFLOW |

  For an offered target, lack of control/source access takes precedence over
  nonlaunchable flight state, then the existing planner's result. SAME_DESTINATION
  maps to CURRENT and UNSUPPORTED_ROUTE to NO_ROUTE; other planner results map
  directly. Missing targets/runtime data use UNAVAILABLE. Only READY implies
  `canLaunch`, and it requires positive bounded fuel. Unknown wire IDs reject.
- Keep at most 128 body nodes, 32 station summaries and 160 quotes. Retain all
  existing route-search and cache budgets. Refresh menu navigation at most once
  per 20 ticks, with immediate refresh on changed rocket flight data; state and
  active plan continue through the existing menu synchronization. Send a changed
  snapshot only; station/permission/catalog changes appear within 20 ticks.
- Extend the flight-plan S2C message with generation, stable explicit quote
  status IDs and current station summaries. Increment the exact-match
  `rocket_flight` channel from 6 to 7. This changes no public API or save schema;
  pre-NAV clients must use their matching server build. Keep the celestial
  display channel/schema at 2 and its existing 96 KiB payload limit.
- Register the existing full celestial snapshot as an additional S2C message
  on the flight channel for menu recovery. Send it on open/new generation and
  bounded refresh; cache only one encoded generation per server service.
  A player receives at most one such menu-driven full snapshot per 100 ticks
  across menu reopenings. The rate state clears on logout/server stop.
- Reuse the vanilla container-button intent for refresh, accepting only its
  fixed ID, actual viewer/open menu, live same-Level loaded rocket, distance and
  server thread. No client coordinates, fuel, target results, NBT or arbitrary
  catalog selector are accepted. Client mismatch triggers a rate-limited full
  refresh and disables selection-to-launch until generations agree; successful
  reload also uses the existing full-catalog synchronization. Late catalog
  generations cannot roll back a newer cache; malformed input retains the last
  valid data but does not become a coherent launch display.
- Refresh intent ID is 0; all other button IDs reject. Pending server full-sync
  attempts retry at the 20-tick menu refresh until eligible, independently of
  changed-only plan transmission. An incoherent client may request again every
  100 ticks. Closing the menu discards pending work. Per-player cooldown storage
  is capped at the existing 128-player intent budget, prunes expired entries and
  refuses extra entries rather than growing unbounded; logout/stop clear it.
- The extended plan frame uses fixed 32-bit container/entity IDs, strict optional
  plan flag and typed target, canonical quote count, each typed quote's 32-bit
  bounded fuel and one-byte status, nonnegative 64-bit catalog generation,
  canonical station count, and station UUID/name/orbit ID entries. Minimum is
  19 bytes; maximum is 31,419 bytes, below 32 KiB. Names retain the existing 48
  UTF-16-character / 144 UTF-8-byte bound; IDs retain 128 canonical ASCII chars.
  Duplicate quote/station IDs, invalid values, truncation and trailing data
  reject. The separate full-catalog envelope retains its existing payload bound.
- Layout is display-only, deterministic and bounded by the 128-node catalog.
  Rebuild it on changed data, not each frame. Clip drawing/hit testing to the
  viewport; clamp pan/zoom and provide deterministic previous/next navigation so
  every body remains inspectable. Preserve the existing teal/orange console
  style, use original code-drawn geometry and localized text, and import no art.

## Compatibility and non-goals

The existing C2S launch checks remain authoritative for owner/access/distance,
physical targets, state, current route and fuel. No client status creates a
permission or travel destination. Existing typed target IDs, station schemas,
rocket journals, terrain, API 1.7 and environmental rules are unchanged.
No warp, discovery persistence, new generic orbit arrival, world generation,
new engine tier or asset import belongs to this slice. Rollback uses matched
pre-NAV client/server artifacts; no save migration is needed for menu state.

## Verification

Check status/DTO bounds, strict wire decoding, generation retention/coherence,
dynamic station visibility and selection, deterministic 100/128-node layout and
viewport limits. GameTests exercise real server quote/control/access rejection,
reload refresh, menu validity/rate enforcement and no chunk loading or resource
mutation. Run short build/DataGen/GameTests, packaged server/restart regression
and independent review/reruns. Keep UI/manual cases explicit; do not infer V1/V2
from pure layout tests. Full client/GPU/load acceptance remains under ADR-018.

Platform references: [Forge menus](https://docs.minecraftforge.net/en/1.20.1/gui/menus/),
[screens](https://docs.minecraftforge.net/en/1.20.1/gui/screens/) and
[SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/).
Implementation follows existing repository patterns; no platform code is copied.
