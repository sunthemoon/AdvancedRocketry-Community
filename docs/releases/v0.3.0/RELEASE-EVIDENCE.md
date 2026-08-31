# RELEASE-EVIDENCE — v0.3.0

## Acceptance summary

```yaml
version: v0.3.0
status: PASSED
build: 1.20.1-0.3.0-dev
tested_implementation_commit: 63d159ef3d9e489862b0d517b76bcc523df852c9
artifact_sha256: 920425eaeb8cf8b6e94f23ed3086ca290ae734315059bbcf8eea100272d8bdfb
human_approved_by: sunthemoon
human_approved_at: 2026-08-31
public_release: false
tag_created: false
```

## Gate evidence map

| Gate | State | Bound evidence |
|---|---|---|
| G0 provenance/license | PASS | Exact MIT upstream XML fixture record, generated-resource inventory, JAR notices, owner approval |
| G1 build/artifact | PASS | Two byte-identical clean builds, JAR manifest, artifact summary |
| G2 data/resources | PASS | Seven-file v0.3 DataGen inventory and zero `runData` diff |
| G3 automated tests | PASS | 50 JUnit, 15 GameTests, 580/581 Python with one justified skip, strict repository validation |
| G4 dedicated server | PASS | Fixed Levels, invalid-reload recovery, two exact-JAR clients, restart rejoin |
| G5 persistence | PASS | Exact celestial SavedData report/hash before and after restart; machine regression |
| G6 authority/security | PASS | Operator-only travel, bounded serializer/NBT/XML, no result-bearing C2S or arbitrary chunk load |
| G7 performance | PASS | Constant-time catalog lookup, finite snapshot, 100-write travel budget, no persistent force load |
| G8 packaged client | PASS | Mods, Moon, Space, Earth return, both restart-rejoin screenshots/logs, owner approval |
| G9 docs/human acceptance | PASS | Installation, known issues, manual report, evidence map, checksums, owner approval, and 3/3 PR checks |

## Evidence inventory

- [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json)
- [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json)
- [`evidence/celestial-server/summary.json`](evidence/celestial-server/summary.json)
- [`evidence/client/manual-evidence.json`](evidence/client/manual-evidence.json)
- [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json)
- [`evidence/machine-regression/summary.json`](evidence/machine-regression/summary.json)
- [`evidence/persistence/comparison.json`](evidence/persistence/comparison.json)
- [`evidence/xml-import/import-report.json`](evidence/xml-import/import-report.json)
- [`checksums.txt`](checksums.txt)

The v0.3 validator checks bounded safe paths, duplicate JSON keys, exact
screenshot/log sets, PNG dimensions and hashes, artifact-copy equality,
packaged-server contracts, persistence equality, provenance, and checksum
inventory completeness.

Pull request [#7](https://github.com/sunthemoon/AdvancedRocketry-Community/pull/7)
passed all three checks on technical checkpoint `9a7f4db`: the
[Forge baseline and advisory run](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33379022611)
and the [repository governance run](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/33379022601).
Repository owner `sunthemoon` approved G0/G8/G9 on 2026-08-31 against the
accepted JAR and committed evidence hashes.

## Release classification

This is an accepted milestone and unreleased developer preview, not a stable
release. No tag or GitHub Release is created. The next implementation
milestone is v0.4.0 vacuum and life support.
