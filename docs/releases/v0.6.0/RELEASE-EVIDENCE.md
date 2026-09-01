# RELEASE-EVIDENCE — v0.6.0

## Scope delivered

This milestone completes bounded fuel loading, a server-authoritative flight
state machine, fixed Earth-Moon travel, durable transfer/recovery, passenger
identity, safe landing, return, and exact transactional disassembly.

It does not add arbitrary planets, stations, satellites, warp, terraforming,
orbital lasers, space elevators, or stable-save guarantees.

## Evidence inventory

| Area | Canonical record |
|---|---|
| Artifact identity and repeat builds | [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json) |
| Exact JAR entries | [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json) |
| Automated and authority summary | [`evidence/automated/summary.json`](evidence/automated/summary.json) |
| Packaged Forge lifecycle | [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json) |
| 20 round trips and restart matrix | [`evidence/flight-server/summary.json`](evidence/flight-server/summary.json) |
| Exact 40-leg ledger | [`evidence/flight-server/round-trip-ledger.json`](evidence/flight-server/round-trip-ledger.json) |
| Eight restart checkpoints | [`evidence/flight-server/restart-matrix.json`](evidence/flight-server/restart-matrix.json) |
| Two-client packaged-server run | [`evidence/multiplayer/summary.json`](evidence/multiplayer/summary.json) |
| Owner G0/G8/G9 attestation | [`evidence/manual/owner-attestation.json`](evidence/manual/owner-attestation.json) |
| Performance and fixed limits | [`PERFORMANCE.md`](PERFORMANCE.md) |
| Exact checksum inventory | [`checksums.txt`](checksums.txt) |

Machine-local full logs contain installation paths and are intentionally not
committed. Filtered extracts preserve artifact hashes, lifecycle assertions,
two-client join/leave events, flight phases, recovery actions, conservation,
and clean-stop results.

## Reproducible artifact

Two cache-disabled clean Windows builds and the packaged-server copy are
exactly 917,911 bytes and share SHA-256:

```text
cb8d34e797a57e94a1efb595af8dace6f40072cf0d96715a3d8db73a3518668d
```

The audited main JAR contains 591 entries. Cross-platform byte equality is not
claimed; CI separately audits and executes the Linux-built candidate.

## Gate decision

G0-G9 are `PASS`. Repository owner `sunthemoon` approved provenance, the
disclosed two-client/manual record, and release acceptance on 2026-09-01.
ADR-007 scopes the missing screenshot/video exception to v0.6.0 only. PR #10
must still pass its final three checks, merge, and reproduce this exact JAR
from the merge commit before the execution goal is closed.
