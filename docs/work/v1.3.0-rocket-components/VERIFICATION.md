# V130-COMP-01 — Declarative rocket components

## Scope and identity

Development baseline: `180fb81`, branch `codex/v1.3.0-public-api`.
Contract: `ee7dce7`, [ADR-026](../../decisions/ADR-026-ROCKET-COMPONENT-DEFINITIONS.md).
Production, regression and independent fixture: `3eb7cad`; packaged runner:
`d86c3aa`. Root owns tracked writes; independent review uses a separate session
and Temp-only reports. The user-supplied documentation bundle is unchanged.

API **1.4** adds `RocketComponentDefinition`, `RocketComponentRegistrar` and
`RegisterRocketComponentsEvent`, bringing the isolated classifier to sixteen
exported types. The external fixture registers five vanilla blocks without host
internal imports or copied assets. The host now uses declared mass, thrust,
capacity and roles in actual scans, assembly, seat anchors and flight validation.

Registration is owner/thread-bound, atomic and loading-only. Duplicate, unknown,
air, reserved and oversized claims fail; immutable numeric mappings have no
runtime callbacks or world references. Explicit definitions precede legacy role
tags, but do not grant movement, BlockEntity permission or chunk loading.
Aggregate capacity above 2,048,000 is rejected before extraction.

Snapshot schema 1, entity/flight schema 2 and network protocols are unchanged.
Saved numeric values and anchors remain authoritative when registrations change
or disappear; disassembly followed by new assembly uses the new definitions.
The existing sixteen-passenger and route-fuel limits remain unchanged.
Custom fuel items/remainders and loader migration remain **V130-COMP-02**.
No new propulsion physics, fluid system, machinery, dimension or visual asset is added.

## Commands and observed results

Windows, Java 17.0.7, Forge 47.4.10 and the repository Gradle wrapper;
Python 3.13.15 from `D:/python/pyenv/pyenv-win/shims/python.bat`, `PYTHONUTF8=1`, `-B`.
Raw command output, consumer provenance, final GameTest logs and source diff are
in [build evidence](build-evidence.zip). [Full JUnit XML](junit-full.zip) was copied
before the independent focused rerun could replace Gradle reports.

| Actual command | Observed result |
|---|---|
| `gradlew.bat test --tests '*RocketComponentRegistryTest' --tests '*RocketComponentDefinitionTest' --tests '*RocketStatsValidatorTest' --tests '*ApiArtifactTest' --tests '*ApiVersionsTest' --console=plain` | Initial focused run, exit 0; 37 s; terminal output only |
| `gradlew.bat clean build runData --console=plain` | Initial integration, exit 0; 61 s; generated two additive locale entries |
| `gradlew.bat runGameTestServer --console=plain` | Initial integration, exit 0; 168 Required passed; 116 s |
| `gradlew.bat clean build runData publishMavenJavaPublicationToLocalProjectRepositoryRepository --console=plain` | First final attempt: 724 tests, one stale language-inventory assertion; exit 1 / 50 s |
| Same clean build/DataGen/publication command after the correction | Exit 0 / 57 s; 724 JUnit in 137 suites, no failures/errors/skips; DataGen written 0 |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache --console=plain` | Exit 0 / 22 s; all ten tasks executed; API-only compile, forbidden internal import and separate reobfuscated JAR checked |
| `gradlew.bat runGameTestServer --console=plain` | Final source: exit 0 / 148 s; all 169 Required passed |
| `python -B -m unittest discover -s tests -p 'test_v130*.py' -v` | Exit 0; 59 checks |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 plans and 33-input inventory |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 checks, 1,345 links at implementation staging |
| `git diff --exit-code -- src/generated`; unstaged/staged `git diff --check` | Exit 0; generated output stable, no whitespace failures |

After evidence staging, the same validator's `check_markdown_links` entry point
passed all 1,381 links, planning validation passed again, and full
`git diff --exit-code` plus the staged whitespace check returned 0.

The failed assertion required the exact historical five-key v1.3 language set.
The new capacity diagnostic made that set six. The test now names all six keys
and retains exact key, locale, placeholder and packaged-byte checks. Its original
XML and output remain in [failure evidence](failed-language-fixture.zip).
No assertion was weakened, timeout increased or resource budget relaxed.

New coverage: fourteen JUnit methods, seven host and two external Required
GameTests, and five Python runner-oracle tests. Cases include numeric limits,
atomic/closed/reentrant registration, immutable catalog, role-tag precedence,
mixed legacy metrics, scan permissions, unloaded chunks, aggregate-capacity
rejection with unchanged blocks/no entity/no origin journal entry, captured
snapshot values, seventeen anchors with sixteen passenger places, and real
production assembly/player-interaction disassembly of API-only components.
The exact language inventory and API minor fixtures are updated, not disabled.

Final GameTest logs retain the existing intentional Precision save-failure
diagnostics and the fresh development server's initial missing-properties error.
Those logs are separate from the normal packaged runs below, which have no
ERROR/FATAL/client-linkage/project-warning observations.

## Short packaged restart verification

[The runner](../../../scripts/run_v130_component_smoke.py) used one fresh,
loopback-only, offline/no-player world and the exact normal host/independent
fixture JARs. It enables existing opt-in operator diagnostics, never fills fuel
or launches a player. Only 104 hash-checked library files were copied from a
previous installation; no previous world, mod or configuration was reused.
See [library preparation](libraries-preparation.json).

```text
python -B scripts/run_v130_component_smoke.py <fresh-libraries-only-server>
  --host-jar <host.jar> --fixture-jar <fixture.jar> --evidence-dir <new-evidence>
  --java <jdk-17.0.7/bin/java.exe> --accept-eula
```

Absolute inputs, launch arguments, installed artifact hashes, status metadata
and saved configuration are retained in [runtime evidence](runtime-evidence.zip)
and its 61-entry SHA-256 manifest. First packaged attempt: exit 0, four clean
process exits, 98.81 seconds total. GameTest and the initial packaged phase used
separate worlds/processes with partially overlapping execution; timings are not
performance acceptance.

| Phase | Seconds | Observation ticks | Saved mass / thrust / capacity |
|---|---:|---:|---|
| Assemble with standard definitions | 34.81 | 20 | 254 / 2,400 / 1,500 |
| Restart without component definitions | 22.94 | 21 | 254 / 2,400 / 1,500 |
| Updated definitions; check old capture, disassemble and reassemble | 20.54 | 21 | 294 / 2,800 / 2,000 |
| Restart with updated definitions | 20.51 | 20 | 294 / 2,800 / 2,000 |

Native captures include each phase's `level.dat`, block/entity Anvil files and
transaction SavedData. The runner checks the actual five-block palette, offsets,
seat anchor, owner/logical/entity identity, schema, hash, capacity, zero fuel,
one entity, absent source blocks, no dropped items and empty transaction journal.
The skipped and final restart preserve the entire previous `RocketEntityData`.
The updated phase first verifies the old captured stats, then restores all five
blocks before new assembly creates fresh transaction/snapshot identities.
See [runtime summary](runtime-summary.json) and [decoded states](runtime-disk-states.json).
Standard Forge/default-configuration, offline-mode and terminal warnings remain
in the raw logs; none are represented as a clean-room performance result.

## Artifact identities and independent review

Exact sizes and SHA-256 values are in [artifact identities](artifact-identities.json).

| Artifact | SHA-256 |
|---|---|
| Normal host JAR | `848b2a28cd6b26bebbdd26c00e1ceb42911827ef004e4d93a47b02b5368128ee` |
| API classifier | `487ac76c7e92b44641460700be4889748f3a62d6f853c0e9a2d131039e98eabd` |
| Sources JAR | `b705a2f5e56e20d6f2d5f961e137778d48db28c8bf190772e61cd30ac547515d` |
| Independent fixture | `d8dfdf962a0f9804697503882df294ae769e31a078e5c08faec2beda93b726db` |

Independent [contract review](independent-review/contract/REVIEW.md) and
source/runner review found no unresolved implementation
finding. Review feedback added direct conflicting-tag precedence and rejected-
origin journal assertions before the final GameTest run. Independent Python
runner tests passed five cases. The six-suite focused Java rerun used
`--rerun-tasks --offline --no-daemon --no-build-cache`: exit 0 / 43.345 s,
all fourteen tasks executed, **37 JUnit** passed with no failures/errors/skips.
All 1,191 inspected source/config identities and four artifacts remained unchanged.

The reviewer separately decoded the raw block/entity Anvil, journal and
`level.dat` captures and recomputed snapshot hashes rather than trusting the
runner's result field. Both frozen-data restart comparisons, the actual new
assembly values/identities, empty journals and finite observation windows match.
The audit checks all 61 manifest files, 104 library files, sixteen exact API types
with normal-host byte identity, 114 actual consumer classpath JARs and seventeen
fixture source digests. No ERROR/FATAL/linkage observation was found in the three
raw log streams; warnings are retained.
See [final independent review](independent-review/final/REVIEW.md),
[native audit](independent-native-audit.json) and
[raw rerun/audit evidence](independent-rerun.zip).
The original contract review and identities are in [contract evidence](contract-review.zip).
Readable metadata/review copies normalize line endings; reviews also trim trailing
whitespace and use repository-relative links. Archives retain the original bytes.
No unresolved finding remains for
this bounded component slice, and no reviewer claim approves a release Gate.

The reviewer's first audit helper treated consumer classpath records as strings;
that diagnostic is retained. Reading their actual `file` fields corrected the
helper, not the product or assertions. Its local full-world `level.dat` reader
uses a 65,536-tag bound with the existing 4 MiB expanded-byte/structural bounds;
the smaller production SavedData parser and its limits were not changed.

## Limits, manual follow-up and Gate status

Static component values are not movement authorization. Removing definitions
does not simulate removing the registered blocks or the integrating mod itself;
ordinary Forge missing-registry/binary-loading rules still apply. This fixture
uses vanilla blocks and zero fuel, not custom propellant or arbitrary mod data.
Large legal mass can remain unflyable under existing route limits.

After original machines/dimensions are implemented, real-client follow-up should
assemble the fixture, inspect reported stats/seat placement, refuel and fly, then
exercise reconnect and changed/omitted registration with two real clients. Check
the new over-capacity diagnostic in both locales. V1/V2, remote Linux, full parity,
forced crash/power-loss and long-load acceptance were not run here (ADR-018).

v1.3 remains `IN_PROGRESS`; inherited and G0-G9 Required Gates are **not all
satisfied**. There is no release, tag, candidate or human Gate approval.
Next implementation is **V130-COMP-02**, real custom fuel consumption/remainders
and loader persistence/migration; environment and satellite APIs remain planned.
