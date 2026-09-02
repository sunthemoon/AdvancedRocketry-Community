# ADR-011 — v0.8.0 visual evidence sequence and owner attestation

- **Status:** Accepted
- **Date:** 2026-09-03
- **Owner:** `sunthemoon`
- **Applies to:** v0.8.0 only
- **Expires:** when v0.9.0 validation begins

## Context

The v0.8.0 plan requests a complete gameplay video. This internal developer
milestone instead has a seven-frame integrated-client sequence covering the
Satellite Terminal, component insertion, assembly, active mission, server
restart, ready state, exact-once claim, research, and discovery. Two additional
screenshots come from final-candidate clients simultaneously connected to the
packaged dedicated server. Filtered logs and machine-readable records bind the
same flow to the exact candidate JAR, two restarts, two logical owners, two
client identities, and a 100-mission scheduler run.

The terminal sequence was captured from the implementation working tree before
the final candidate commits. A subsequent client-only layout fix separated the
target/discovery telemetry and resized the buttons. The final candidate was
then compiled, audited, run through unit/GameTest, exercised by two visible
clients, and captured in multiplayer, but the complete terminal sequence was
not recorded again as video.

## Decision

For v0.8.0 only, the repository owner accepts the ordered screenshot sequence,
final-candidate multiplayer screenshots, filtered client/server logs,
GameTests, packaged restart run, and explicit owner attestation as the G8
record. The absence of a continuous gameplay video is disclosed rather than
represented as completed media.

This decision changes only the media format for the internal milestone. It
does not replace the build, DataGen, unit, GameTest, packaged-server,
persistence, authority, stress, checksum, pull-request, or post-merge artifact
requirements.

## Risk and user impact

The evidence proves the observed states but does not preserve every transition
as continuous footage. It therefore has less visual auditability for animation,
intermediate frames, input timing, sound, and the final terminal text layout.
No runtime permission, persistence, duplication, or scheduler limit is waived.

## Mitigation

- Preserve all screenshots as original PNG files with SHA-256 checksums.
- Label terminal screenshots as pre-candidate rather than implying otherwise.
- Bind final runtime acceptance to one implementation commit and JAR hash.
- Preserve two-client join, shared-state, disconnect, server restart, and
  reconnect logs from the final candidate.
- Keep all satellite authority and lifecycle assertions in automated and
  packaged-server tests.

## Recollection condition

A new continuous recording is required before a public release claims a full
v0.8.0 gameplay video, if this record is reused for another version, if the
candidate gameplay code changes, or if a visual regression disputes an
accepted observation.

## Automatic failure reminder

`validate_v080_release_evidence.py` must require this ADR, the labeled
pre-candidate metadata, both final-candidate screenshots, the exact candidate
hash, and the owner attestation. Later-version validators must not accept this
v0.8.0-scoped decision.
