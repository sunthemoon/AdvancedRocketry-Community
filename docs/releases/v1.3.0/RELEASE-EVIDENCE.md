# v1.3.0 development evidence handoff

**IN_PROGRESS — not a release candidate, stable release or Gate approval.**
Use this index to review the implemented API kernel, reproduce its bounded
checks and locate acceptance work still required. ARCE is an unofficial
community rewrite, not an official Minecraft product.

## Development identity

- Source checkpoint: `884908ebfb936402bcc7a437791f8d9683c27506`, branch
  `codex/v1.3.0-public-api`; build `1.20.1-1.3.0-dev`.
- Minecraft 1.20.1, Java 17, Forge baseline 47.4.10; 47.4.23 is a separate,
  unverified lane for this handoff's artifact.
- API 1.7, 27 exported class entries. Java API version, mod version, channel
  protocols and save schemas are separate contracts.
- Current artifact identities: [development-artifacts.json](development-artifacts.json).
  This document is bound to that checkpoint, not whatever a later branch builds.
- Candidate commit, candidate artifact, tag, release approver and final
  candidate reviewer: **not assigned**. Per-slice independent reviews are not
  final release approval.

The [acceptance cursor](../../status/CURRENT_VERSION.md) remains at v1.0;
its explicit active-development pointer selects v1.3. The accepted
[ADR-020](../../decisions/ADR-020-V130-DEVELOPMENT-BASELINE-EXCEPTION.md)
allows this development from the recorded v1.2 baseline, not a candidate or
v1.4 production work. Older-version Gate exceptions are not extended here.

## Delivered development scope

The classifier and isolated ForgeGradle consumer expose versioned registration
for rocket inventories, atmosphere boundaries, suit/oxygen equipment, rocket
components, item fuels and satellite payload missions, plus read-only server
environment queries. Production services retain authority; providers cannot
use these APIs to bypass loaded-world, ownership or transaction checks.

See the [API guide](../../PUBLIC-API-GUIDE.md),
[supported-use inventory](../../API-COMPATIBILITY.md) and
[consumer build](../../../compat-test-mod/README.md). These are development
contracts, not universal modpack support, arbitrary fluid/capability adapters,
new planets, complete classic content or direct 1.12.2 world loading.

## Review entry points

| Decision | Record |
|---|---|
| G0-G9 evidence and remaining approval | [GATE-STATUS](GATE-STATUS.md) |
| Version requirements versus actual coverage | [REQUIREMENT-MAP](REQUIREMENT-MAP.md) |
| Commands, fixtures and historical artifact limits | [TEST-REPORT](TEST-REPORT.md) |
| Save roots, missing providers and downgrade | [MIGRATION-REPORT](MIGRATION-REPORT.md) |
| Authority, callbacks and network boundaries | [SECURITY-REVIEW](SECURITY-REVIEW.md) |
| Bounded work versus unmeasured reference load | [PERFORMANCE](PERFORMANCE.md) |
| Unexecuted real-player acceptance scenarios | [MANUAL-TEST](MANUAL-TEST.md) |
| V1/V2 evidence still needed | [VISUAL-VALIDATION-REPORT](VISUAL-VALIDATION-REPORT.md) |
| Operator limitations and retained issues | [KNOWN-ISSUES](KNOWN-ISSUES.md) |
| Pinned original report/metadata identities | [evidence-index.json](evidence-index.json) |
| Scoped independent handoff review | [independent-review.json](independent-review.json) |
| Integrity of this handoff | [checksums.txt](checksums.txt) |

The index pins original reports and available outer checksums; it does not
relabel old runs as current-artifact tests. Original logs, failed attempts,
native data and independent reviews stay in their existing per-slice records.
Checksums here are evidence integrity, not published-download approval.

## Installation boundary

Build with Java 17 and the repository's Gradle wrapper. Install the normal host
JAR, not the API classifier or sources JAR, on matching Forge client/server
installations. Use copies of backed-up development worlds. The compatibility
fixture is for disposable tests only; follow its separate build instructions
and never install both its development source set and packaged JAR together.

Keep host and integration versions matched. Channel versions alone cannot
establish satellite menu compatibility; [migration notes](MIGRATION-REPORT.md)
describe the new menu frame and Fuel Loader schema. No public Maven service,
stable binary support duration or automatic world downgrade is promised.

## Remaining sequence

1. Continue documenting any concrete defect with its smallest short regression;
   preserve the implemented API contracts and unresolved inherited acceptance.
2. Complete original machines/dimensions before the full campaign, as scheduled
   by [ADR-018](../../decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md).
   A single completed intermediate version does not trigger that campaign.
3. Before selecting a v1.3 candidate, satisfy ADR-020's inherited-Gate recovery
   condition or obtain a separate precise approved disposition. Bind each
   required check to the chosen candidate, including actual S2/V1/V2, migration,
   reference-load, compatibility and clean release-build evidence.
4. Complete independent candidate review, uninvolved installation, release
   documentation and human approval. Only then create a tag/release and verify
   its rebuilt and downloaded artifacts. No such action is authorized here.

The next content milestone is v1.4 Planetary Expansion. Its development-order
decision and dimension/environment contract must be resolved separately; this
handoff neither implements it nor changes its prerequisites.
