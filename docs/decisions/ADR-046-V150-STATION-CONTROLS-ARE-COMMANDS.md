# ADR-046 — v1.5 station, team and warp controls are server commands

```yaml
status: PROPOSED
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.5.0
development_dependency: ADR-040, ADR-041, ADR-044
implements: V150-UI-01..03 disposition
```

## Context

The v1.5 plan asks for server-authoritative UI, feedback and permissions (§6.9),
security tests (§12) and a "multiplayer permission report" (§15), with GUI-scale
and multiplayer visual checks (§11) when a screen exists. The implementation log
splits this into:
- UI-01, server-authoritative actions and a bounded protocol;
- UI-02, status, permission, error and countdown presentation;
- UI-03, stale, forged, unloaded and multi-player authority checks.

Every v1.5 station action is already a Brigadier command under `/arce station`.
Expansion, gravity and warp accept only the connected player's own non-silent
source (ADR-040/041/044). ADR-044 §7 already chose commands as the warp interface
and deferred any screen. No v1.5 feature adds a C2S or S2C packet.

## Decision

1. **UI-01:** v1.5 adds **no screen and no network packet**. The station, team
   and warp controls are the existing server commands. Every input is a
   Brigadier argument that is parsed and bounded on the server (UUIDs, resource
   locations, integers with ranges). Every cost, balance, bound and result is
   computed on the server. No network protocol version changes; the build checks
   that the protocol constants are unchanged. A future screen must bring its own
   ADR, a versioned and bounded payload, and the same server checks.
2. **UI-02:** feedback uses chat and the action bar:
   - every rejection names its reason, with numbers where relevant (cost and
     balance);
   - quotes show the station, bodies, class, cost, balance and warnings;
   - countdowns are announced at 10/5/3/2/1 s to online members, with the
     commit or abort reason;
   - `status` and `environment` are read-only.

   Messages are literal English, like the other station commands. Localizing
   them is deferred and recorded as a known gap.
3. **UI-03:** a consolidated GameTest matrix is the "multiplayer permission
   report". It connects mock players as owner, member, invitee, outsider and
   operator, plus a FakePlayer, the console, `/execute as` and a silent own
   source. It runs every station action against them and logs one
   `ARCE_STATION_PERMISSION_MATRIX` line per cell:
   - list, invite, accept, decline, remove;
   - expand request and confirm;
   - gravity;
   - environment identity;
   - warp request, confirm, cancel and status;
   - admin inspect, delete and transfer.

   It also covers:
   - forged or stale identifiers: an unknown station UUID, another station's
     confirmation, a stale confirmation after a change, an unknown or
     non-orbitable target;
   - no action loading a chunk: the loaded-chunk count is unchanged for commands
     naming far or unloaded regions;
   - two players' concurrent requests staying independent.

   The report is archived with the slice.
4. §11 GUI scale does not apply without a screen. Real-client V1/V2 checks of
   chat and action-bar legibility and of multiplayer stay open for ACC-02.

## Consequences

- No protocol or save-schema change, so nothing to migrate or roll back.
- UI-01 and UI-02 are satisfied by existing behaviour plus this record. UI-03
  needs the matrix GameTest.
- Players without command familiarity have no visual control surface in v1.5.
  That is a known usability gap, not a safety gap.

## Rollback

Documentation only; the matrix test can be removed without behaviour change.
