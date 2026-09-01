# ADR-009 — v0.7.0 visual evidence attestation

- **Status:** Accepted
- **Date:** 2026-09-01
- **Owner:** `sunthemoon`
- **Applies to:** v0.7.0 only
- **Expires:** when v0.8.0 validation begins

## Context

The v0.7.0 plan requests screenshots or video for the station player flow and
a two-player station video. The exact candidate was instead exercised with two
simultaneously visible Forge user-development clients connected to one
packaged dedicated server. Filtered logs bind both clients to the same server
marker, two distinct station owners, the tested source commit, and the
candidate JAR SHA-256. The packaged station run separately binds ten regions,
two station arrivals, Earth and Moon returns, restart persistence, ownership
transfer, and isolated deletion. No screenshot or video was captured.

## Decision

For the v0.7.0 internal milestone, the repository owner accepts the bounded
two-client logs, packaged station evidence, automated authority matrix, and
direct owner attestation as the G8 record. G4 remains based on the actual
two-client execution. This decision does not replace the build, GameTest,
packaged-server, persistence, authority, performance, or checksum gates. G9 is
accepted only when the checksum-bound release bundle validates.

## Risk and user impact

The records prove server-authoritative station allocation, shared multiplayer
state, approved-pad travel, and clean client shutdown. They do not
independently prove every rendered frame, model, tooltip, sound, particle, or
screen interaction. This changes audit media quality only; it does not relax
runtime validation.

Local dual-window Forge user-development launches also exhibited intermittent
login handshake timeouts while another OpenGL client rendered in the
foreground. Minimizing the connected client removed the contention in the
accepted run. That run contains two simultaneous successful clients and clean
departures, but the environmental observation remains disclosed in
`KNOWN-ISSUES.md`.

## Mitigation

- Keep the filtered two-client/server logs and machine-readable station runs.
- Bind approval to one implementation commit and one candidate SHA-256.
- State explicitly that no screenshot or video is claimed.
- Keep Critical/High station authority or region-destruction findings at zero.
- Recollect media if this build is promoted as a public visual release.

## Recollection condition

New screenshots or video are required if the candidate JAR changes, this
attestation is reused for another version, a public release claims visual
media, or a later regression disputes the accepted observations.

## Automatic failure reminder

`validate_v070_release_evidence.py` requires this ADR, the disclosed no-media
limitation, the exact candidate hash, and the owner attestation. Later-version
validators must not accept this v0.7.0-scoped record.
