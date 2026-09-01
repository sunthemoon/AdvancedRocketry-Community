# RELEASE-EVIDENCE — v0.7.0

## Scope delivered

This milestone adds persistent, non-overlapping, permissioned stations in the
single fixed Space Level: bounded allocation, transactional platform creation,
owner/member access, region protection, accessible station destinations,
approved-pad rocket arrival, restart recovery, transfer, inspection, and
isolated deletion.

It does not add one dimension per station, moving/resizable stations, warp,
satellites, terraforming, complex orbital mechanics, space elevators, or
cross-server stations.

## Evidence inventory

| Area | Canonical record |
|---|---|
| Artifact identity and repeat builds | [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json) |
| Exact JAR entries | [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json) |
| Automated, security, and authority summary | [`evidence/automated/summary.json`](evidence/automated/summary.json) |
| GameTest result | [`evidence/automated/gametest.txt`](evidence/automated/gametest.txt) |
| Permission/intent matrix | [`evidence/automated/authority-matrix.json`](evidence/automated/authority-matrix.json) |
| Packaged Forge lifecycle | [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json) |
| Ten-station/restart/travel run | [`evidence/station-server/summary.json`](evidence/station-server/summary.json) |
| Region allocation map | [`evidence/station-server/station-map.json`](evidence/station-server/station-map.json) |
| Exact station/ordinary flight ledger | [`evidence/station-server/flight-ledger.json`](evidence/station-server/flight-ledger.json) |
| Restart and neighbor deletion | [`evidence/station-server/restart-and-deletion.json`](evidence/station-server/restart-and-deletion.json) |
| Two-client packaged-server run | [`evidence/multiplayer/summary.json`](evidence/multiplayer/summary.json) |
| Owner G0/G8/G9 attestation | [`evidence/manual/owner-attestation.json`](evidence/manual/owner-attestation.json) |
| Performance and fixed limits | [`PERFORMANCE.md`](PERFORMANCE.md) |
| Exact checksum inventory | [`checksums.txt`](checksums.txt) |
| Exact post-merge reproduction | [`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json) |

Machine-local full logs contain installation paths and are intentionally not
committed. Filtered extracts preserve artifact identity, station allocation,
flight phases, restart state, two-client join/leave and shared marker events,
deletion isolation, and clean stops.

## Reproducible artifact

Two cache-disabled clean Windows builds and the packaged-server copy are
exactly 1,009,631 bytes and share SHA-256:

```text
4c049a4e0c2a74f78d383af7bc56ad31d746f8b7f8872cbc7258c58981d9c068
```

The audited main JAR contains 636 entries. Cross-platform byte equality is not
claimed; CI separately audits and executes its Linux-built candidate.

## Gate decision

G0-G9 have technical evidence and owner approval. ADR-009 scopes the missing
screenshot/video record to v0.7.0 only. PR #11 passed all four checks and
merged as `b75e301f6cd77cfc1c1ade0e9b16c485f736c93b`; a forced cache-disabled
clean build from that exact commit reproduced the accepted JAR and 636-entry
content manifest byte-for-byte. The version is `PASSED`.
