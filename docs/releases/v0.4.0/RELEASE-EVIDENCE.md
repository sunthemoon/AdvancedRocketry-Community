# RELEASE-EVIDENCE — v0.4.0

## Scope delivered

This milestone adds server-authoritative vacuum survival, a four-piece space
suit with finite chest oxygen, one powered Oxygen Vent, incremental sealed-room
scanning, a bounded volume index, invalidation, restart-safe Vent state, and a
display-only client HUD.

It does not add multiple gases, pressure simulation, terraforming, unlimited
room scans, rockets, satellites, or runtime dimension creation.

## Evidence inventory

| Area | Canonical record |
|---|---|
| Artifact identity and repeat builds | [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json) |
| Exact JAR entries | [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json) |
| Automated and security summary | [`evidence/automated/summary.json`](evidence/automated/summary.json) |
| Packaged Forge baseline | [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json) |
| Electrolyzer regression | [`evidence/machine-regression/summary.json`](evidence/machine-regression/summary.json) |
| Celestial regression | [`evidence/celestial-regression/summary.json`](evidence/celestial-regression/summary.json) |
| Atmosphere restart/performance | [`evidence/atmosphere-server/summary.json`](evidence/atmosphere-server/summary.json) |
| Two-client manual bundle | [`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json) |
| Performance interpretation | [`PERFORMANCE.md`](PERFORMANCE.md) |
| Exact checksum inventory | [`checksums.txt`](checksums.txt) |

Full machine-local logs include installation paths and are intentionally not
committed. The lifecycle extracts retain the assertions, hashes, timestamps,
state transitions, and restart result needed for review.

## Reproducible artifact

The client-tested JAR and two subsequent `clean build` outputs are exactly
466,433 bytes and share SHA-256:

```text
05279656dfae21f682ca45a000517628dfcf706ebc4cce9ce2fe16e0723e96f1
```

All server and client copies use that same hash. The content manifest contains
346 entries. Rebuilding after merge must reproduce the same bytes before the
milestone is considered integrated.

## Gate decision

All technical Gates have local evidence, and repository owner `sunthemoon`
approved the provenance, visible player flow, and release documentation on
2026-09-01. The snapshot remains `READY_FOR_AUDIT` until the PR's complete CI
matrix is recorded and merged. See [`GATE-STATUS.md`](GATE-STATUS.md).
