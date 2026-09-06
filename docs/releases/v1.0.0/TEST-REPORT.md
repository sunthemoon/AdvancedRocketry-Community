# v1.0.0 automated evidence summary

**Development snapshot, not candidate acceptance.** Artifact identifiers and
report hashes are in [evidence-index.json](evidence-index.json).

## Recorded snapshot: logout cleanup

For JAR `569f41ab59d953e3b4fad3c1b00588705b260fc0b73f1deaf829024bc603e381`:

| Actual command | Result |
|---|---|
| `gradlew.bat clean build --no-daemon` | Exit 0, 29 s |
| `gradlew.bat test runData runGameTestServer --no-daemon` | Exit 0, 1m 32s |
| Java test XML | 406 tests / 79 suites; zero failures, errors or skips |
| Forge GameTest | All 51 required tests pass |
| `git diff --check` | Exit 0 |
| `git diff --exit-code -- src/generated` | Exit 0 |
| `git diff --exit-code` | Exit 1: modified development tree, not a clean candidate |

[Full result and retained failures](../../work/v1.0.0-passenger-logout/VERIFICATION.md)
include the failing-before/passing-after logout regression. The initial
FakePlayer channel error is a retained fixture failure, not a product
reproduction. The clean GameTest bootstrap's missing-properties log is not
presented as a strict packaged-server scanner pass.

## Supporting evidence on older artifacts

- [Forge/JEI matrix](../../work/v1.0.0-compatibility/VERIFICATION.md): two
  completed four-cell runs on `cf077ef7...`; Forge 47.4.10/47.4.23 with client
  JEI absent/present. Dedicated servers do not install JEI. This is not proof
  for protocol 5 or the recorded logout artifact.
- [CI integration](../../work/v1.0.0-ci-integration/VERIFICATION.md): local
  workflow checks and consumer execution. It is not a hosted CI run for a
  reviewed final candidate.

Before release, run the version's required commands against the frozen
candidate, retain all failures/skips, bind hosted CI and compatibility results
to that identity, and audit JAR contents. Do not add historical test totals
together and label the sum as one current-candidate test run.
