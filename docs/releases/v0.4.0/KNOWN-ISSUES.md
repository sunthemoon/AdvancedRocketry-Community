# KNOWN-ISSUES — v0.4.0

## Release boundary

- Atmosphere has only `BREATHABLE` and `VACUUM`; there is no pressure, gas
  mixture, toxicity, temperature, or terraforming simulation.
- Oxygen Vent energy uses redstone as the milestone's visible test input; a
  general power network is outside this slice.
- Moon and Space retain placeholder generation and operator travel from v0.3.
  Rockets begin in later milestones.
- Derived breathable-volume caches are rebuilt rather than persisted. They fail
  closed after restart until a powered Vent successfully rescans.
- Rooms that cross unloaded chunks remain `PENDING`; chunks are not force-loaded.
- Rooms over the hard cell limit remain vacuum and report `TOO_LARGE`.
- Test worlds are disposable through `v0.4.x`.

## Runtime observations

- One Forge authentication-key request was reset by the external service during
  a client restart. Retry succeeded and no project linkage failure occurred.
- Controlled operator teleports emitted vanilla `moved too quickly` warnings;
  atmosphere state, player state, persistence, and restart assertions remained
  correct.
- Some screenshots contain the vanilla tutorial prompt. It does not overlap the
  life-support HUD state being reviewed.
- The two-client evidence server used offline mode only on loopback. Public
  offline-server security and arbitrary modpack compatibility are not claimed.

## Reproducibility boundary

The client-tested JAR and two clean Windows rebuilds are byte-identical. Linux
CI separately audits content and runtime behavior; cross-platform byte equality
is not claimed unless its uploaded artifact hash also matches.

## Revalidation triggers

Repeat the affected tests and owner review if the JAR, atmosphere limits, Vent
schema, network payload, generated resources, screenshots, animation, filtered
logs, provenance record, or checksum inventory changes.
