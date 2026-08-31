# RELEASE-EVIDENCE — v0.2.0

## Acceptance summary

```yaml
version: v0.2.0
status: PASSED
build: 1.20.1-0.2.0-dev
tested_implementation_commit: adc505fdd471eaccacb73a5e1247d60be83dd808
artifact_sha256: a8356cbeafdaffbd1192628c414c6996c402a757f6211c857d87e8ead52a2598
human_approved_by: sunthemoon
human_approved_at: 2026-08-31
public_release: false
tag_created: false
```

## Gate evidence map

| Gate | Result | Bound evidence |
|---|---|---|
| G0 provenance/license | PASS | [`docs/provenance/v0.2.0-electrolyzer.md`](../../provenance/v0.2.0-electrolyzer.md), generated-resource manifest, owner review |
| G1 build/artifact | PASS | Two byte-identical clean builds, JAR manifest, artifact summary, checksums |
| G2 data/resources | PASS | 15-file v0.2 DataGen inventory, zero `runData` diff, frozen v0.1 bytes |
| G3 automated tests | PASS | 12 JUnit, 12 GameTests, Python suite, JAR and boundary validators |
| G4 dedicated server | PASS | Matching-client first join/leave and same-world restart rejoin/leave |
| G5 persistence | PASS | Progress 40 state equality before/after restart and exact completion |
| G6 authority/security | PASS | Server-authoritative tick, bounded serializer/data, no custom result C2S, two viewers |
| G7 performance | PASS | 20 idle machines, bounded work, no world scan or log spam |
| G8 packaged client | PASS | Mods page, single-player entry, menu, GUI scales 1–4, dedicated screenshots/logs |
| G9 docs/human acceptance | PASS | This bundle, installation/known issues/manual report, owner approval |

## Evidence inventory

- [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json)
- [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json)
- [`evidence/automated/summary.json`](evidence/automated/summary.json)
- [`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json)
- [`evidence/client/logs/client-log-review.json`](evidence/client/logs/client-log-review.json)
- [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json)
- [`evidence/machine-restart/summary.json`](evidence/machine-restart/summary.json)
- [`evidence/provenance/human-review.json`](evidence/provenance/human-review.json)
- [`checksums.txt`](checksums.txt)

All committed evidence files are individually hashed. The evidence validator
checks safe relative paths, duplicate JSON keys, file bounds, screenshot PNG
dimensions, exact screenshot set, server/machine contracts, artifact equality,
human approval, and checksum inventory completeness.

## Release classification

This is an accepted milestone and unreleased developer preview, not a stable
release. No tag or GitHub Release is created. The next implementation milestone
is v0.3.0 celestial data and fixed dimensions.
