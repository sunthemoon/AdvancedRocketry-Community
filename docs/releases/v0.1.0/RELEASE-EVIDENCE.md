# RELEASE-EVIDENCE — v0.1.0

## Identity

```yaml
version: v0.1.0
status: IN_PROGRESS
build: 1.20.1-0.1.0-dev
tested_implementation_commit: ccae3a79242a1901daed0cadf0c15bb058f89c0b
upstream_commit: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
minecraft: 1.20.1
forge_baseline: 47.4.10
forge_compat_lane: 47.4.23
java: 17.0.8
gradle: 8.8
jar_sha256: 07f5c108233ba14dad518a64f4141caa70f2338166b139b31415d6f284b8e6ea
sources_jar_sha256: 33021af81f583752317810cd01f239a5665843b0f91c7085740f440ea514db65
pull_request: PENDING
forge_ci: PENDING
governance_ci: PENDING
tag: NOT_CREATED
release: NOT_CREATED
release_classification_if_created: PRE_RELEASE
human_approved_by: sunthemoon
human_approved_at: 2026-08-30
```

This milestone is an unreleased developer preview. It establishes the audited
asset, registration, and DataGen baseline; it is not a stable or complete
Advanced Rocketry gameplay release.

## Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 Identity/License/Provenance | PASS | Exact upstream commit, deterministic 18-file audit output, ten-entry MIT import ledger, full ten-entry maintainer review |
| G1 Reproducible Build | IN_PROGRESS | Two Windows Java 17 clean builds are byte-identical; blocking Linux CI is pending |
| G2 Data and Generated Resources | PASS | 37 managed resources, 14 checked local references, deterministic DataGen, no case collision or unrecorded resource |
| G3 Automated Behavior | PASS | Java/Python tests and 3/3 Forge GameTests pass locally; final counts are in `TEST-REPORT.md` |
| G4 Dedicated Server and Sides | PASS | First start and same-world restart passed; the same packaged client joined and disconnected in both cycles |
| G5 Persistence and Recovery | NOT_APPLICABLE | v0.1.0 adds no project persistent state |
| G6 Security and Authority | NOT_APPLICABLE | v0.1.0 adds no project network packet or authoritative gameplay transaction |
| G7 Performance | NOT_APPLICABLE | v0.1.0 adds no tick service, world scan, or unbounded cache |
| G8 Manual Player Flow | PASS | Maintainer-approved zh_cn/en_us Mods, creative-tab, item, placement, break, and dedicated reconnect evidence |
| G9 Documentation and Release | PASS | Changelog, installation/save boundary, known issues, evidence, and checksums are present; no stable-release claim |

Only G1 remains open until the exact pull-request head passes blocking CI. No
Required Gate is waived.

## Audited upstream baseline

The audit is bound to the primary MIT-licensed repository and exact commit
`c5cd5af62fc07cd4e0d24f06a16033f181c47c04`.

```yaml
tracked_files: 1429
java_files: 510
asset_files: 898
libvulpes_references: 886
mutable_static_candidates: 35
network_packet_candidates: 17
asm_coremod_findings: 17
historical_missing_asset_references: 540
case_collisions: 0
```

The complete outputs are in [`../../../legacy-manifest/`](../../../legacy-manifest/).
Historical risk findings are indexes, not an import allowlist. No LibVulpes
code or resource was copied.

## Provenance and managed resources

- Imported targets: 10, all `UPSTREAM_AR_MIT` and bound to source/target SHA-256.
- Generated targets: 27, bound by the generated-resource manifest.
- Total managed distributable resources: 37.
- Human sample: all 10 imported targets, approved by `sunthemoon` on
  2026-08-30 with no finding.
- Provenance content digest:
  `d1357ba67a7b8a2029ffe4cd51bc7c7e6413b247d0934f338e8ea2df8739cb79`.

Records:

- [`../../provenance/v0.1.0-minimal-content.json`](../../provenance/v0.1.0-minimal-content.json)
- [`../../provenance/v0.1.0-generated-resources.json`](../../provenance/v0.1.0-generated-resources.json)
- [`evidence/provenance/human-review.json`](evidence/provenance/human-review.json)

## Artifact binding

Two consecutive clean builds produced the same 95,924-byte main JAR:

```text
07f5c108233ba14dad518a64f4141caa70f2338166b139b31415d6f284b8e6ea  build/libs/advancedrocketry-community-1.20.1-0.1.0-dev.jar
```

The source, isolated packaged-client copy, and dedicated-server copy have that
same SHA-256. The artifact contains 112 entries and exactly the managed v0.1.0
resource set. See
[`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json)
and [`checksums.txt`](checksums.txt).

## Dedicated server and matching client

Harness session `v002-bc17a36c59b68bb86a6603fa` uses the historical harness
identifier prefix but records mod version `1.20.1-0.1.0-dev` and the accepted
v0.1.0 JAR hash.

```yaml
first_start: PASS
first_client_join_disconnect: PASS
save_and_clean_stop: PASS
same_world_restart: PASS
restart_client_rejoin_disconnect: PASS
same_player_verified: true
project_error_count: 0
project_warning_count: 0
client_linkage_failure_count: 0
```

See [`evidence/dedicated-server/`](evidence/dedicated-server/) and the two
dedicated-client screenshots under
[`evidence/client/screenshots/`](evidence/client/screenshots/).

## Packaged client

The same JAR was loaded by an isolated Forge 47.4.10 profile. Game-only F2
screenshots prove:

- correct Mods identity, version, logo, MIT license, and description;
- the dedicated creative tab contains the machine casing and four components;
- all block/item textures render without purple/black missing textures;
- the machine casing renders in multiple orientations and can be manually
  broken and placed;
- zh_cn and en_us names and the inert-v0.1.0 tooltip are readable at effective
  GUI scales 3 and 2;
- the packaged client joins the dedicated server and rejoins after restart.

The two language runs cleanly saved and stopped. Project logger
`WARN/ERROR/FATAL` findings and client-class linkage findings are zero. External
Forge/Vanilla/Netty warnings are classified in
[`evidence/client/logs/client-log-review.json`](evidence/client/logs/client-log-review.json).

## Approval boundary

Repository maintainer `sunthemoon` approved G0, G8, and G9 on 2026-08-30. The
approval is bound to the provenance content digest, evidence hashes, and JAR
SHA-256 above. Any changed imported/generated resource, packaged JAR byte, or
visual evidence invalidates the affected approval and requires revalidation.

CI and pull-request URLs will replace the pending identity fields before the
version status changes to `PASSED`.
