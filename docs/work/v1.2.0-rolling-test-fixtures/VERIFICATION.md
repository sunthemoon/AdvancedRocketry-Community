# V120-TST-02 — Rolling GameTest fixture extraction

Date: 2026-09-26. Scope: existing test organization only; no production change,
new scenario, relaxed assertion, timeout change or expanded acceptance matrix.
Task status: verified. This is not version acceptance.

## Source and design

Baseline: `3dbe6884e6d8bff3c676c4f7cebee0824b507602`, branch
`codex/v1.2.0-precision-assembler`, with the pre-existing uncommitted v1.2
implementation retained. This is not a release or clean-commit artifact.

- [RollingMachineGameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RollingMachineGameTests.java)
  decreases from 819 to 763 lines. Its eleven test methods, annotations, order,
  class name and class-level registration annotations are unchanged.
- [RollingMachineGameTestFixtures](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RollingMachineGameTestFixtures.java)
  is an 85-line, package-private final utility. It contains the five immutable
  positions and seven original helpers, with a private constructor and no test
  registration annotations. The old class imports these twelve members explicitly.
- Structure placement order remains casing, four typed ports, then NORTH-facing
  controller. Port-list order remains unchanged for index-paired NBT reloads;
  `remainingPorts` still excludes the removed Fluid port. Helpers fetch the
  current BlockEntity on each call and create fresh NBT; no mutable global
  world state or fixture cache was added.

The remaining 763-line class was reviewed under the >500-line rule. It is a
single-machine integration-test scenario container, covering formation, NBT,
capabilities, process and menu behavior; it is not a single-feature test class.
World construction, object lookup and shared assertions now live separately.
Further repartitioning would change test registration containers without helping
this bounded extraction. Neither class exceeds 800 lines; no size waiver is used.

## Verification

Environment: Windows, JDK 17.0.7, Minecraft 1.20.1, Forge 47.4.10.
`JAVA_HOME` explicitly selected `C:\Program Files\Java\jdk-17.0.7`.

| Command/check | Actual result |
|---|---|
| Python comparison against `git show HEAD:<original file>` | PASS; eleven full annotated methods byte-identical after LF normalization; seven helpers and five constants identical except removal of `private` |
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon` | exit 0, 2m40s; 600 JUnit in 123 suites, no failures/errors/skips; 121/121 Required GameTests; DataGen written 0 |
| JAR entry hash comparison | PASS; one test class changed, one fixture class added, no other entry changed or removed |
| `python scripts/validate_v120_machine_resources.py` | exit 0; 9 machine blocks |
| `python scripts/validate_v1plus_planning.py` | exit 0; 11 plans / 33-input inventory |
| `python scripts/validate_repository.py --require-approved-identity` | exit 0; 45 passed, 0 pending/warnings/failed during document integration |
| Independent source review | No findings; independent Python comparison and `git diff --check` exit 0 |
| Independent `gradlew.bat runGameTestServer --offline --no-daemon` | exit 0, 1m27s; 121/121 Required, existing world reused; no unexpected failure |
| `git diff --check` | exit 0 |
| `git diff --exit-code --stat` | exit 1; outstanding implementation/evidence, not a clean release Gate |
| `git diff --exit-code --stat -- src/generated` | exit 1; the two pre-existing Precision removal language entries remain; this run's DataGen wrote 0 |

Compilation and JUnit actually executed in this build, not FROM-CACHE. GameTests
ran in the fresh test world created after `clean`. The original eleven Rolling
registrations remain within the unchanged 121-test suite; the Forge console
reports aggregate/batch results rather than a per-case success list.

The root log retains controlled Precision migration save-failure injection ERROR
stacks. The final Required GameTest result is PASS; no exception was ignored or
test expectation changed by this extraction.

Independent reviewer `/root/prec04b_readback_review` compared the original source
directly, without using the root comparison JSON as its oracle, and reran the
GameTest command. It also independently compared the new JAR with the retained
JEI installation's old JAR and read all 123 archived JUnit XML suites. No findings
remain in this extraction. Independent execution did not rerun JUnit, DataGen or
packaged-server/client tests.

## Artifact and evidence

- Previous JAR SHA-256:
  `92821ec3273a7b87c2892003ca5321f8cf65105a41572a3f730c3582d67c2000`.
- New JAR SHA-256:
  `709f157c49f25b006b09f1c52908a42c0ef6c4bbdaa215e68451ce6f906a27c2`.
- [Extraction comparison](extraction-comparison.json) records each original
  test name, annotation and normalized-text hash.
- [JAR comparison](jar-comparison.json) lists all changed/added/removed entries.
- [Root Gradle output](gradle.txt), [GameTest log](gametest-root.log),
  [JUnit summary](junit-summary.json), [JUnit XML archive](junit-results.zip).
- [Independent Gradle output](gradle-independent.txt),
  [independent GameTest log](gametest-independent.log).
- [Repository validation](repository.txt), [final checks](checks.json),
  [evidence hashes](SHA256SUMS.txt).

Previous packaged restart, migration and JEI evidence retains its original
artifact identity. No packaged-server, native-client, remote or long-load run
was repeated for this test-only change.

## Remaining scope and Gate status

The extraction introduces no save, network, recipe, asset or public API change.
It does not prove full machine/dimension parity or close inherited acceptance
items. Complete visual, multiplayer, remote and load matrices remain scheduled
under ADR-018. Version status stays `IN_PROGRESS`, phase `INTEGRATING`; G0–G9
are not all satisfied and no release approval/tag is assigned.
