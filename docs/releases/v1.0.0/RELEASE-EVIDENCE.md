# v1.0.0 development evidence handoff

**IN_PROGRESS — not a release candidate or stable release.** Read this index
to assess development evidence and identify the checks still required before
release. The approved public build remains [v0.9.0 Beta 1](../v0.9.0/RELEASE-EVIDENCE.md).
ARCE is an unofficial community rewrite, not an official Minecraft product.

## Identity snapshot: 2026-09-06

- Target: Minecraft 1.20.1, Java 17, Forge 47.4.10; compatibility lane 47.4.23.
- Development version: `1.20.1-1.0.0-dev`, branch `codex/v1.0.0-stable-core`.
- Base commit: `34b2e99b48a33f4ba8905b6a69a38efee1649d3f`.
- Development JAR: 1,278,474 bytes, SHA-256
  `569f41ab59d953e3b4fad3c1b00588705b260fc0b73f1deaf829024bc603e381`.
- Source identity: 747 inputs in the
  [logout regression inventory](../../work/v1.0.0-passenger-logout/source-inventory.json).
- Candidate commit, candidate JAR, tag, release approver and independent
  reviewer: **not assigned**. The base commit alone does not identify the
  modified development tree. This snapshot does not follow later builds.

## Scope

Stable Core preserves the accepted Earth/Moon/space, life-support, block-built
rocket, station and basic satellite gameplay. Development fixes cover recovery
ordering, passenger persistence/reconnection and server-synchronized flight
console behavior. See [CHANGELOG](../../../CHANGELOG.md).

This is not classic feature parity, an expansion kernel, direct 1.12.2 world
import or a promise of universal modpack compatibility. Test development builds
only on copies of backed-up worlds. Flight protocol is 5; peers must match.
Persisted identities and schema boundaries are not renamed for the version label.

## Review entry points

| Area | Record |
|---|---|
| Required Gates and approval boundary | [GATE-STATUS](GATE-STATUS.md) |
| Build, Java, GameTest, data and compatibility | [TEST-REPORT](TEST-REPORT.md) |
| Beta-world upgrade | [MIGRATION-REPORT](MIGRATION-REPORT.md) |
| Transaction and passenger recovery | [RECOVERY-MATRIX](RECOVERY-MATRIX.md) |
| Two-client observations | [MULTIPLAYER-REPORT](MULTIPLAYER-REPORT.md) |
| Rendering and GUI observations | [VISUAL-VALIDATION-REPORT](VISUAL-VALIDATION-REPORT.md) |
| Installation and player-flow acceptance | [MANUAL-TEST](MANUAL-TEST.md) |
| Performance requirements and missing measurements | [PERFORMANCE](PERFORMANCE.md) |
| Authority and provenance review | [SECURITY-REVIEW](SECURITY-REVIEW.md) |
| Unresolved behavior and support limits | [KNOWN-ISSUES](KNOWN-ISSUES.md) |
| Exact report/JAR associations | [evidence-index.json](evidence-index.json) |
| Integrity of this handoff | [checksums.txt](checksums.txt) |

Every older-artifact report is supporting development evidence, not a pass for
the JAR above. The JSON index pins report hashes; referenced reports retain
their own raw evidence and checksums. Handoff checksums are not distributable
JAR checksums or release approval.

## Release sequence still required

1. Resolve remaining passenger-loading and connection findings; assign a
   release owner, independent reviewer and uninvolved installation tester.
2. Freeze a reviewed candidate commit and bind each G0-G9 requirement to that
   candidate's evidence. Complete outstanding dedicated, migration, multiplayer,
   visual, security and performance acceptance; document any approved ADR.
3. Rebuild the candidate in a clean environment and inspect its distributable
   metadata, license/source manifests and exact checksum. Finish installation
   and independent review, then obtain explicit human approval.
4. Only after complete evidence and approval, create the immutable tag, rebuild
   from it, compare the artifact and verify the published download. No such
   tag or publication is authorized by this document.
