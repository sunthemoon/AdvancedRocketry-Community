# RELEASE-EVIDENCE — v0.8.0

## Scope delivered

This milestone adds one complete server-authoritative progression loop: craft
satellite components, assemble and launch a UUID-bound logical data satellite,
finish its mission without players or chunks loaded, claim research exactly
once, and persist a celestial discovery. A powered Satellite Terminal exposes
bounded status and failure messages while operators retain bounded inspect,
recover, and cancel commands.

It does not add satellite entities, asteroid mining, terraforming, microwave
power, orbital weapons, payload bays, dynamic dimensions, a full research
tree, or a LibVulpes/ARLib recreation.

## Evidence inventory

| Area | Canonical record |
|---|---|
| Artifact identity and repeat builds | [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json) |
| Exact JAR entries | [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json) |
| Automated, security, and authority summary | [`evidence/automated/summary.json`](evidence/automated/summary.json) |
| GameTest result | [`evidence/automated/gametest.txt`](evidence/automated/gametest.txt) |
| Authority matrix | [`evidence/automated/authority-matrix.json`](evidence/automated/authority-matrix.json) |
| Packaged Forge lifecycle | [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json) |
| Satellite/restart/stress run | [`evidence/satellite-server/summary.json`](evidence/satellite-server/summary.json) |
| Exact claim ledger | [`evidence/satellite-server/mission-ledger.json`](evidence/satellite-server/mission-ledger.json) |
| Initial two-client run | [`evidence/multiplayer/summary.json`](evidence/multiplayer/summary.json) |
| Post-restart two-client reconnect | [`evidence/multiplayer-reconnect/summary.json`](evidence/multiplayer-reconnect/summary.json) |
| Scheduler JFR summary | [`evidence/performance/summary.json`](evidence/performance/summary.json) |
| Candidate screenshots and owner decision | [`evidence/manual/owner-attestation.json`](evidence/manual/owner-attestation.json) |
| DataGen provenance | [`../../provenance/v0.8.0-generated-resources.json`](../../provenance/v0.8.0-generated-resources.json) |
| Exact checksum inventory | [`checksums.txt`](checksums.txt) |

Machine-local complete logs and JFR data contain installation paths and are
intentionally not committed. Filtered extracts preserve candidate identity,
mission transitions, scheduler passes, shared-state markers, join/leave and
reconnect events, and clean stops.

## Reproducible artifact

Two cache-disabled clean Windows builds and the packaged-server copy are
exactly 1,166,061 bytes and share SHA-256:

```text
0ce6c6bf9eb603f5973f35c19a47b295454a1f8c74ee74a6a99af3c2627a1937
```

The audited main JAR contains 723 entries. Cross-platform byte equality is not
claimed; CI separately audits and executes its Linux-built candidate.

## Gate decision

G0-G9 have technical evidence and owner approval. ADR-011 limits the accepted
ordered-screenshot substitution to v0.8.0 and preserves the residual visual
risk. The version remains `READY_FOR_AUDIT` until the pull request passes all
required checks, merges, and its exact merge commit reproduces the candidate
JAR and content manifest byte-for-byte. No tag or public release is implied.
