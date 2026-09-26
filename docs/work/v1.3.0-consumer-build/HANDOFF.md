# V130-ROCKET-03A build handoff

Date: 2026-09-26. Status: `READY_FOR_REVIEW`.

## Completed scope

- Added a standalone `compat-test-mod` settings/build, pinned to ForgeGradle
  6.0.54, Java 17, Forge 47.4.10 and official Minecraft 1.20.1 mappings.
- Reused only `../src/adapterTest/java` and resources. The build has no host
  project dependency, included build, main JAR or main class output dependency.
- Compiled against the published API classifier through `fg.deobf`; produced a
  separate reobfuscated fixture JAR without bundled host/API/platform classes.
- Added repeatable real-classpath/provenance and negative-import verification.
  The negative fixture's internal class is checked against the published normal
  host JAR, which is inspected only and never placed on the compiler classpath.
- Added reader instructions for publication, independent builds, local repository
  selection, reports and runtime limitations. No root build/source/docs changed.

## Actual commands and results

Environment: Windows, Oracle JDK 17.0.7, Gradle 8.8, ForgeGradle 6.0.54.
The shared wrapper was invoked with `-p compat-test-mod`, selecting only the
standalone project. All Gradle commands used `--offline --no-daemon
--no-build-cache` and the following local repository property:

```text
-ParceRepository=C:/Users/Administrator/AppData/Local/Temp/arce-v130-api-repository-d1a44d650adb429a96cd85511796314e
```

The integrator created this immutable copy of the real local Maven publication
so its concurrent root clean build could not remove consumer dependencies.

| Command after `gradlew.bat -p compat-test-mod` | Result |
|---|---|
| `clean build` (initial implementation) | Exit 1 / 18s: `exclusiveContent` restricted the host group to the file repository, preventing ForgeGradle's synthetic mapped dependency from resolving |
| `clean build` (corrected repository filtering) | Exit 0 / 34s; all 10 actionable tasks executed, including source compilation, `reobfJar` and both boundary checks |
| `verifyConsumerBoundary` (after adding the real internal-class archive assertion) | Exit 0 / 23s; 5 tasks executed, 4 up-to-date; both boundary checks executed again |
| `javap -p ...FixtureContainerBlockEntity` on final fixture JAR | Exit 0; persisted/container override methods have production `m_..._` names |
| `git diff --check` | Exit 0 |

The first staged whitespace check reported CRLF and formatter whitespace inside
raw captures. Task-local attributes now preserve these bytes and exempt only
raw `.txt`/`.json` evidence from style checks, matching adjacent task evidence
conventions; hand-authored source and Markdown remain checked. No capture bytes
or verification assertions were changed by that correction.

The first failure is retained in `initial-build.txt`; replacing exclusive
repository ownership with an include-group filter allows ForgeGradle's generated
mapping repository without weakening class or publication-hash checks.
`second-build.txt` and `final-boundary-check.txt` retain both successful outputs.
The `test` task reports `NO-SOURCE`; these are build/audit/javac checks, not a
JUnit result or GameTest execution.

## Artifact and boundary evidence

Requested API coordinate:
`io.github.sunthemoon.advancedrocketrycommunity:advancedrocketry-community:1.20.1-1.3.0-dev:api`.

- Published raw API SHA-256:
  `06568c596efb623c80eddfbdd41afd695451f47ceb935b165d149349157e4020`.
- Actual Forge-remapped API SHA-256:
  `c946cd8b063ceca49fd1a40500462d6e6ea82670c59dcc6e09de7cc8d6683730`.
- Resolved mapped version: `1.20.1-1.3.0-dev_mapped_official_1.20.1`, classifier
  `api`; it is the only host-group artifact among 114 compiler classpath JARs.
- Raw and remapped API archives contain exactly the six frozen exported classes.
  Other dependency JARs contain no ARCE class entries; no output/source directory
  is on the compiler classpath.
- The negative javac probe exits 1 with exactly the absent `rocket.forge`
  package and unresolved `RocketBlockEntityAdapter` diagnostics. Its public
  version reference resolves; the intended internal class exists in the normal
  published host JAR. See `negative-import.json`.
- Fixture: `compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar`,
  18,777 bytes, 11 entries including five fixture classes and license/metadata.
  SHA-256: `1a7e2f83d4c7ae4fe17e57602ce8437edd125d0a917d5db867ec89e9b9e87779`.
  No host/API/platform class is bundled.

`classpath.json` records both API identities, every resolved artifact and classpath
JAR hash, and the eight fixture source/resource hashes. `fixture-artifact.json`
records the final archive entries and identity. The independently inspected SRG
method signatures are in `reobfuscated-signatures.txt`. Raw evidence checksums
are recorded in `SHA256SUMS` with byte-preserving task-local attributes.

## Integration and remaining scope

This artifact uses the fixture at base `e0ef86d2b9983321d04bfb50285535d4acf856eb`.
The integrator owns separate API compatibility/event-log additions to that
fixture. No updated fixture files were copied into this worktree. Rebuild the
independent project after those changes are integrated, and record the resulting
new source/artifact hashes before using that JAR for runtime evidence.

No Minecraft/server/client process was started, no root Gradle task was run,
and no dependency/network installation was performed. The shared Gradle cache
was used offline. Original code only; existing LICENSE and NOTICE are packaged.

Startup, actual registration, inventory round-trip, process restart, provider
uninstall/reinstall and cross-dimension behavior remain separate V130-ROCKET-03
verification. No new API, payload schema or channel protocol was introduced,
and no Required Gate or version status is approved. The next action is review,
integration, and same-artifact runtime verification within the current version.

Rollback consists of reverting the standalone project/task records; there is no
production registration or stored-world migration to reverse.
