# ADR-007 — v0.6.0 visual evidence attestation

- **Status:** Accepted
- **Date:** 2026-09-01
- **Owner:** `sunthemoon`
- **Applies to:** v0.6.0 only
- **Expires:** when v0.7.0 validation begins

## Context

The v0.6.0 manual plan requested additional screenshots and a two-player video.
The exact candidate was instead exercised with two simultaneously visible Forge
clients connected to one packaged dedicated server. Filtered client and server
logs bind both players to the same server marker, celestial generation, source
commit, and candidate JAR hash. No screenshot or video was captured.

## Decision

For the v0.6.0 internal milestone, the repository owner accepts the two-client
log bundle and direct owner attestation as the G8 record. G4 remains based on
the actual two-client execution; this ADR does not replace automated, packaged
server, persistence, authority, or conservation evidence. G9 is approved only
after the complete checksum-bound release bundle validates.

## Risk and user impact

The evidence proves simultaneous client connectivity and shared server state,
but it cannot independently demonstrate every rendered frame, sound, particle,
or GUI detail. This changes audit media quality only; it does not change game
behavior or relax runtime validation.

## Mitigation

- Keep the filtered logs and machine-readable multiplayer summary in the
  release bundle.
- Disclose that both clients were Forge user-development launches from the
  exact tested implementation commit while the server used the packaged JAR.
- Bind the approval to one commit and one SHA-256 candidate.
- Keep Critical/High findings at zero before acceptance.

## Recollection condition

New screenshots or video are required if the candidate JAR changes, this
evidence is reused for another version, a public release claims visual media,
or a later regression disputes the accepted client observations.

## Automatic failure reminder

`validate_v060_release_evidence.py` requires this ADR, the disclosed no-media
limitation, the exact candidate hash, and the owner attestation. Validators for
later versions must not accept this v0.6-scoped record as their G8 evidence.
