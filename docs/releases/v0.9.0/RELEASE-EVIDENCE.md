# RELEASE-EVIDENCE — v0.9.0 Beta 1

## Scope delivered

This milestone freezes the accepted v0.8.0 gameplay and hardens it for a small
Beta dedicated server. It adds transactional schema-1-to-2 world-data backup
and migration, bounded startup/operator diagnostics, stricter flight-intent
framing, isolated optional JEI display, Forge baseline/latest coverage,
forced-stop recovery, resource/accessibility auditing, and a two-hour maximum
combined workload.

It does not add content systems, direct 1.12.2 upgrades, downgrade support,
dynamic dimensions, universal modpack compatibility, or a stable v1.0 API/save
promise.

## Evidence inventory

| Area | Canonical record |
|---|---|
| Repeated artifact identity | [`evidence/artifact/artifact-summary.json`](evidence/artifact/artifact-summary.json) |
| Exact JAR entries | [`evidence/artifact/jar-content-manifest.json`](evidence/artifact/jar-content-manifest.json) |
| Automated result summary | [`evidence/automated/summary.json`](evidence/automated/summary.json) |
| GameTest result | [`evidence/automated/gametest.txt`](evidence/automated/gametest.txt) |
| Security and authority audit | [`evidence/automated/security-audit.json`](evidence/automated/security-audit.json) |
| Packaged Forge lifecycle | [`evidence/dedicated-server/summary.json`](evidence/dedicated-server/summary.json) |
| Five-root migration and backup | [`evidence/migration/summary.json`](evidence/migration/summary.json) |
| Migrated-world gameplay continuation | [`evidence/continuation/`](evidence/continuation/) |
| Process-kill recovery | [`evidence/recovery/summary.json`](evidence/recovery/summary.json) |
| Forge/JEI four-cell matrix | [`evidence/compatibility/summary.json`](evidence/compatibility/summary.json) |
| Two-hour combined workload | [`evidence/performance/summary.json`](evidence/performance/summary.json) |
| Localization/resource audit | [`evidence/resources/summary.json`](evidence/resources/summary.json) |
| Owner G0/G8/G9 decision | [`evidence/manual/owner-attestation.json`](evidence/manual/owner-attestation.json) |
| Public Beta notes | [`RELEASE-NOTES.md`](RELEASE-NOTES.md) |
| Exact checksum inventory | [`checksums.txt`](checksums.txt) |
| Post-merge reproduction and pre-release | [`evidence/artifact/post-merge-reproduction.json`](evidence/artifact/post-merge-reproduction.json) |

Machine-local complete logs are not committed because they contain installation
paths. Filtered extracts, source log hashes, CSV samples, lifecycle transitions,
backup hashes, and exact authority ledgers preserve the reviewable results.

## Reproducible candidate

Two independent clean Windows builds and the packaged-server copy are exactly
1,225,536 bytes and share SHA-256:

```text
fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad
```

The audited JAR contains 758 entries. Cross-platform byte equality is not
claimed; Forge CI separately audits and executes its Linux-built artifact.

## Gate decision

G0-G9 are `PASS`. Repository owner `sunthemoon` approved G0, G8, and G9. PR
#13 final head `7841dcc0d30b26a207ee221b0efbd1e25d459ed3` passed all four
required checks and merged as `a7196ff9b22220c344071a1af69a663036f76aef`;
all four merge-commit checks also passed. A cache-disabled clean `main` build
reproduced the accepted JAR and content manifest byte-for-byte. The same
download-verified JAR is published as GitHub pre-release
[`v0.9.0-beta.1`](https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1).
This document does not call the Beta stable.
