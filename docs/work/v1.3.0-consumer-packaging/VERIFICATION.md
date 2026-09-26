# V130-ROCKET-03A: independent classifier consumer

## Scope

Build the existing API-only fixture as an independent ForgeGradle project,
consume the actual published API classifier, and load its reobfuscated JAR with
the normal host on a disposable dedicated server. Shared fixture source does
not mean a shared host compilation or an independently authored integration.

This is the packaged-loading part of V130-ROCKET-03. External inventory restart,
missing-provider recovery and cross-dimension behavior remain later work. No
long-load, remote server, real-client or full acceptance campaign is included.

- Baseline: `e0ef86d2b9983321d04bfb50285535d4acf856eb`.
- Fixture observation/API compatibility guard: `c108d547dcfdd1a62c9f19e2f2843d2c9533d368`.
- Standalone build: worker `3d142a82fb230031e23ffa285322ae85e20f39c2`, integrated as
  `e2391891e101a7e2e31493b1923a49466c793c8f`.
- API contract remains ADR-022 / API 1.1. No host production source, public
  signature, save schema, network protocol, recipe or asset changed.

## Implementation and decisions

The standalone `compat-test-mod` project has its own settings/build and uses
Java 17, ForgeGradle 6.0.54 and Forge 47.4.10. It resolves only the published API
classifier plus platform dependencies for compilation. The host is not a Gradle
project dependency, included build, source directory or compiled classpath entry.
The existing fixture source/resources are reused without copying host classes.

Checks inspect the actual compiler classpath, local publication provenance,
raw/remapped classifier surfaces, a deliberate internal-import rejection and
the independently reobfuscated fixture archive. The negative probe's real
internal type is checked in the published host archive without putting that
archive on the compiler classpath. Reports include coordinates, hashes and source
inputs; a requested dependency string alone is not treated as provenance.

The fixture checks API major/minimum minor compatibility and logs its actual
registration count/version after `event.register` returns. This marker is not
by itself startup evidence: the same packaged process must complete loading.
The fixture is not a gameplay mod and remains excluded from host/API/sources JARs.

The first worker build failed because exclusive repository routing also excluded
ForgeGradle's synthetic mapped dependency. The repository is now group-filtered
without exclusive routing, while exact raw publication hash checking remains.
The failure and successful follow-up are retained, not rewritten as a first-pass
success. No test expectation or timeout was weakened.

## Execution status

Windows / Oracle Java 17.0.7 / Gradle 8.8 / Forge 47.4.10. The complete source
state for the final fixture is integration commit `e2391891`.

| Command | Actual result |
|---|---|
| Root `gradlew.bat clean build test runData runGameTestServer publishMavenJavaPublicationToLocalProjectRepositoryRepository --offline --no-daemon --no-build-cache` | Exit 0, 3m17s; 130 suites / 663 JUnit, 0 failures/errors/skips; all 134 Required GameTests passed; DataGen written 0; local publication regenerated |
| `git diff --exit-code -- src/generated` | Exit 0; generated resources unchanged |
| Worker standalone `clean build` with the disposable publication property | Initial repository-resolution failure retained; corrected build exit 0 / 34s; strengthened boundary rerun exit 0 / 23s |
| Root `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache` | Exit 0, 21s; all 10 tasks executed, including compilation, reobfuscation and both verification tasks |

Host captures: [complete Gradle output](host-integration.txt),
[native GameTest log](gametest-native.txt), [full JUnit XML](junit-full.zip).
The deliberate existing save-fault GameTest logs remain visible. Host artifacts
are byte-identical to ROCKET-02 because these changes affect only the separate
fixture/build. No new JUnit methods are claimed for the standalone project:
its `test` task is `NO-SOURCE`; its two verification tasks actually executed.

Standalone evidence: [worker handoff and original failures](../v1.3.0-consumer-build/HANDOFF.md),
[integrated command output](consumer-integration.txt),
[resolved compiler inputs](consumer-reports/classpath.json),
[negative diagnostic and existing internal-class check](consumer-reports/negative-import.json),
[final fixture archive](consumer-reports/fixture-artifact.json).
The actual compiler classpath has 114 dependency JARs, exactly one host artifact:
the remapped API classifier. The final fixture has five fixture classes / 11
entries, with license/metadata and no bundled host, API or platform classes.

The worker artifact used its baseline fixture. The integrated build was separately
executed against the final API guard/registration observation source; its JAR,
not the worker's earlier artifact, is used for packaged verification.

| Final artifact | SHA-256 |
|---|---|
| Runtime host | `3f8be1e0adce70b1c25fc1eeeffeac5b7f398016abf6670af11b42c52c84c200` |
| Raw API classifier | `06568c596efb623c80eddfbdd41afd695451f47ceb935b165d149349157e4020` |
| Host sources | `7b1e565e5ce326605d739effd5d37721d65e6fb41f4dba859ede53233c4d755f` |
| Reobfuscated fixture, 19,304 bytes | `0432704f79ea3c85d5af64bba4aedd1f0a5fb7dfafeb7ee83ff99468e23e70a8` |

### Independent review and rerun

An independent reviewer inspected the actual consumer build and fixture diff;
no unresolved finding remained in this scope. The reviewer ran
`gradlew.bat -p compat-test-mod verifyConsumerBoundary --offline --no-daemon --no-build-cache`:
exit 0 / 18.995s, nine actionable tasks, five executed and four up-to-date.
Both verification tasks and jar/reobf tasks executed; compilation was up-to-date.
No JUnit or Minecraft execution is inferred from that command.

The reviewer compared 775 tracked source/build files before/after, found no source
changes, and verified unchanged hashes for all four JARs. Actual ForgeGradle cache
`.input` fields matched the raw classifier SHA-1 and the selected mapping ZIP
SHA-1. The remapped classifier SHA-256 was
`c946cd8b063ceca49fd1a40500462d6e6ea82670c59dcc6e09de7cc8d6683730`.
This checks actual remap inputs, not just a similarly named cached artifact.

Evidence: [command](independent/command.txt), [output](independent/gradle.txt),
[independent observations](independent/independent-summary.json),
[cache proof](independent/remap-cache-proof.json),
[cache input](independent/mapped-api.jar.input), and
[independent manifest](independent/SHA256SUMS).
The first archive postcheck encountered a PowerShell array-shape error in the
three-host-JAR pre-run record. The original record was retained, its captured
hashes normalized and rechecked; the erroneous grouped `bytes=3` is not used as
size evidence. [Postcheck notes](independent/postcheck-notes.txt) preserve this
distinction. Gradle was not rerun to replace an unsuccessful test result.

### Independent packaged loading

A separate verifier prepared a fresh world/runtime and installed exactly the
final host and fixture JARs. Only hash-checked runtime libraries were copied from
the existing runtime. Both normal `forgeserver` processes, initial startup and
same-world restart, exited 0 after clean save/stop. Binding was loopback-only,
offline, with no clients; the test port was closed afterward.

Both complete stdout/debug captures and Minecraft status responses associate
the expected IDs/versions with the two actual `mods/*.jar` files. Each process
records exactly one successful registration with payload 1 / API 1.1 / event 1
and then completes startup. The fixture resource pack was created from its JAR.
No inventory callback execution is inferred from those loading observations.

Both runs had zero ERROR/FATAL/linkage findings and no tick-lag warning. Ordinary
Forge library metadata, first-config creation, union URL, terminal and deliberate
offline warnings are retained. This is not a performance benchmark.

Evidence: [packaged verification](packaged-loading/VERIFICATION.md),
[actual commands](packaged-loading/commands.json),
[observations](packaged-loading/summary.json),
[installed artifacts](packaged-loading/installed-artifacts.json), and
[original checksum manifest](packaged-loading/checksums.txt).
The captured runner is one-off test evidence, not a new public product tool.

ROCKET-03A is verified within this scope. [Static checks](static-checks.txt) cover
planning, local links, generated-resource and whitespace consistency; full
historical repository validation was not rerun. The [slice manifest](SHA256SUMS)
preserves archived evidence bytes without rewriting the original failed captures.
The first staged whitespace check flagged blank-line spaces produced by Groovy's
JSON formatter in the raw classpath report. Scoped capture attributes preserve
those original report bytes, matching the imported-bundle policy; source formatting
rules were not changed. The [initial static capture](static-checks-initial.txt)
is retained, and the repeated check passed.
Both full packaged debug logs are explicitly tracked despite the repository's
general log-ignore rule; manifest checks include the actual staged Git bytes.

Initial local publication:

```powershell
.\gradlew.bat publishMavenJavaPublicationToLocalProjectRepositoryRepository --offline --no-daemon --no-build-cache
```

Exit 0 / 14s. The POM identity and all three published JAR hashes match the actual
build artifacts. See [publication output](local-publication.txt) and
[publication inspection](published-artifacts.txt). No remote publication occurred.
An exact-byte disposable copy of this repository let the isolated worker build
while root's clean build regenerated its own build directory.

## Boundaries

- `fg.deobf` remaps an API dependency for development; its output need not be
  byte-identical to the raw SRG classifier. The host's raw classifier must remain
  byte-identical to its exported runtime classes.
- Ordinary production Forge disables development GameTest registration. Packaged
  loading uses normal production launch flags; no development flag is substituted
  to claim packaged GameTest execution.
- Startup/restart does not prove external inventory persistence, callback recovery,
  provider uninstall/reinstall or cross-dimension conservation.
- Existing callback returned-time checks do not preempt non-returning code or
  sandbox arbitrary same-JVM side effects.
- v1.3 remains `IN_PROGRESS`; G0-G9 and inherited acceptance are not all satisfied.
  No release tag, remote upload or human approval is created.

Next: V130-ROCKET-03B's bounded external payload persistence and missing-provider
recovery, followed by the remaining provider APIs in the
[implementation log](../v1.3.0-implementation-log.md).
