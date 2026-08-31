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

Two clean Windows builds and the packaged-server copy are exactly 703,103 bytes
and share SHA-256:

```text
0e232ace303912d8487c0b26853341801c9ffe4468d2a73ae322cfce049ff42b
```

The sources JAR is 356,986 bytes with SHA-256
`6bf36e40cec68595a71762c9b1c797f558ae1aee42868313448a32c48f525b0e`.
The audited main JAR contains 497 entries.

## Gate decision

Local technical evidence supports G0–G9. Repository owner `sunthemoon`
approved provenance, the visible player flow, and the release documentation on
2026-09-01. The candidate remains `READY_FOR_AUDIT` until pull-request checks
pass, it is merged, and the same artifact is reproduced from the accepted tree.
