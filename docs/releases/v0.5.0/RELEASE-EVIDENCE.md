# RELEASE-EVIDENCE — v0.5.0

## Scope delivered

This milestone adds bounded rocket structure scanning, canonical schema-1
snapshots, server-recomputed statistics, region-locked transactional assembly
and disassembly, restart recovery, a thin same-dimension `RocketEntity`, and
bounded cached client rendering.

It does not add launch, fuel consumption, flight, destination selection,
cross-dimension transfer, orbital physics, stations, or satellites.

## Evidence inventory

| Area | Canonical record |
|---|---|
| Artifact identity and repeat builds | [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json) |
| Exact JAR entries | [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json) |
| Post-merge exact reproduction | [`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json) |
| Automated and authority summary | [`evidence/automated/summary.json`](evidence/automated/summary.json) |
| Packaged Forge lifecycle | [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json) |
| Rocket persistence and recovery | [`evidence/rocket-server/summary.json`](evidence/rocket-server/summary.json) |
| Maximum-structure measurements | [`evidence/performance/summary.json`](evidence/performance/summary.json) |
| Visible client acceptance | [`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json) |
| Performance interpretation | [`PERFORMANCE.md`](PERFORMANCE.md) |
| Exact checksum inventory | [`checksums.txt`](checksums.txt) |

Machine-local full logs contain installation paths and are intentionally not
committed. Filtered extracts preserve artifact hashes, lifecycle assertions,
join/disconnect events, entity identity, recovery state, and clean-stop results.

## Reproducible artifact

Two clean Windows builds and the packaged-server copy are exactly 703,307 bytes
and share SHA-256:

```text
45782780eeec54f1710cee4425f96b4d0152d29590559f519130ca9f227f0ba0
```

The sources JAR is 357,173 bytes with SHA-256
`a1220e5066c487e009edad46311f912430b8bd2ef39881e46bb79d96c9afc7eb`.
The audited main JAR contains 497 entries.

## Gate decision

G0–G9 are `PASS`. Repository owner `sunthemoon` approved provenance, the
visible player flow, and the release documentation on 2026-09-01. PR #9 passed
all three checks and merged as
`90587983b78920ed1f62621c11825dfc11dd901b`. A cache-disabled clean build from
that merge reproduced the accepted JAR and sources JAR hashes plus the exact
497-entry content manifest. The v0.5.0 milestone is therefore `PASSED`.
