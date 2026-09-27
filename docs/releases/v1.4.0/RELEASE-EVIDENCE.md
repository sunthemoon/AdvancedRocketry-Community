# v1.4.0 development evidence handoff

**IN_PROGRESS — not a release candidate, stable release or Gate approval.**
Use this index to locate implemented planetary capabilities, artifact-bound
checks and outstanding acceptance. This is an unofficial community rewrite.

## Development identity

- Development checkpoint: `d33552a5374272b3b0669090ec4466075662ee4c`, branch
  `codex/v1.4.0-planetary-expansion`, build `1.20.1-1.4.0-dev`.
- Production code/resource checkpoint: `a9143c2b62d5daab412cefedcee198f1fd032588`.
  Later MIG-02/03 add verification, not changed production artifacts. This
  documentation-only handoff likewise preserves all three host JARs.
- Minecraft 1.20.1, Java 17, Forge 47.4.10. The separate 47.4.23 compatibility
  lane is **not certified for these artifacts**.
- API 1.7; flight channel 8, celestial display channel 2. See the separate
  [save/menu contracts](MIGRATION-REPORT.md) rather than inferring compatibility
  from the mod version.
- Exact bytes: [development-artifacts.json](development-artifacts.json).
  Historical slice runs remain tied to their own artifacts, not the latest branch.
- Candidate commit/artifact, release tag, final candidate reviewer and human
  release approver: **not assigned**.

[ADR-030](../../decisions/ADR-030-V140-DEVELOPMENT-BASELINE-EXCEPTION.md)
permits this development from the recorded v1.3 baseline, not a passed v1.3
release or v1.5 production work. The [release-acceptance cursor](../../status/CURRENT_VERSION.md)
remains v1.0; inherited Gates and expired exceptions are not rewritten here.

## Implemented capabilities

Fixed Mars/Venus surfaces and an unmapped gas giant extend existing Earth/Moon/
Space content. Schema-2 definitions, jointly published routes, persistent
body/Level bindings and bounded landing checks retain server authority. Opt-in
environment hazards, a schematic console star map, resource-pack sky/fog/sound
profiles and shared discovery/private research complete this development scope.
Research/discovery recovery uses ordered checked writes and bounded replay.

Start with the [celestial data guide](../../CELESTIAL-DATA-GUIDE.md),
[discovery guide](../../PLANETARY-DISCOVERY-GUIDE.md) and
[sky-profile guide](../../PLANETARY-SKY-GUIDE.md). These features do not implement
runtime dimension registration, per-station orbit skies, radiation damage,
full terraforming, multi-star warp, direct 1.12.2 saves or all classic content.

## Review entry points

| Question | Record |
|---|---|
| Which requirements have scoped evidence? | [REQUIREMENT-MAP](REQUIREMENT-MAP.md) |
| What still prevents release approval? | [GATE-STATUS](GATE-STATUS.md) |
| What actually ran, on which artifacts? | [TEST-REPORT](TEST-REPORT.md) |
| How should operators upgrade, restore or remove data? | [MIGRATION-REPORT](MIGRATION-REPORT.md) |
| Which authority/input limits were checked? | [SECURITY-REVIEW](SECURITY-REVIEW.md) |
| Which bounds are not performance measurements? | [PERFORMANCE](PERFORMANCE.md) |
| Which real-player scenarios remain? | [MANUAL-TEST](MANUAL-TEST.md) |
| Where are the missing V1/V2 records? | [VISUAL-VALIDATION-REPORT](VISUAL-VALIDATION-REPORT.md) |
| What limitations matter to operators? | [KNOWN-ISSUES](KNOWN-ISSUES.md) |
| Original report/metadata identities | [evidence-index.json](evidence-index.json) |
| Independent handoff review | [independent-review.json](independent-review.json) |
| This handoff's integrity | [checksums.txt](checksums.txt) |

Original failures, logs, fixtures and reviews stay in their per-slice archives.
The index verifies their outer manifests; it does not relabel historical runs
as current-artifact or final candidate tests. Checksums are evidence integrity,
not approval to publish a download.

## Installation and remaining sequence

Build with Java 17 and the repository Gradle wrapper. Install the normal host
JAR, not its API/sources classifiers, on matching Forge clients and server. Use
a complete backed-up world copy, including data packs/configuration. Compatibility
fixtures are disposable test tools, not normal gameplay dependencies.

The next content milestone is v1.5 Orbital, Station & Warp Systems; its development-order and
persistent/public contracts need a separate decision. This handoff neither
implements it nor satisfies its prerequisite Gates. Continue finite regression
for concrete changes; run the full campaign only after original machinery and
dimensions are implemented, per [ADR-018](../../decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md).

Before v1.4 candidate freeze, recover ADR-030's inherited acceptance obligations
or obtain a separate precise disposition. Bind the remaining S2/V1/V2, migration,
reference-load, compatibility and clean release-build results to that candidate.
Then complete independent final audit, uninvolved installation, human approval
and publication verification. None of those approvals is supplied by this handoff.
