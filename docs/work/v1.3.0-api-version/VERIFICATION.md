# V130-API-01: version metadata and isolated consumer

## Scope

Development slice under accepted ADR-020/021. The exported surface is only
`ApiVersion`, `ApiCompatibility` and `ApiVersions` in `api.version`. API version
1.0 is independent of the mod's `1.20.1-1.3.0-dev` build identity. No provider
registration, save, packet, world or gameplay behavior is changed.

The inherited v1.2 implementation/evidence baseline is
`163e713206d1b89eca8b3e369b4503ab0a5a4ceb`. It includes original machine migration
fixtures and capture bytes. Earlier staging normalized 25 raw captures in
`a66f718`; the corrective descendant restores original bytes without changing
checksum values. Independent verification of Git blobs and the new checkout:
115/115 checksum references and 2/2 fixture chunks match. The user-supplied
development documentation directory was not included.

## Design and verification boundaries

- API sources compile independently with the Java 17 toolchain, empty dependency,
  source and annotation-processor paths.
- The classifier takes its three allowlisted classes from the final reobfuscated
  runtime JAR. An exact byte comparison prevents divergent runtime/API artifacts.
  License/notice metadata remains packaged; no Forge mod descriptor is included.
- JUnit invokes the JDK compiler with only the classifier on its classpath,
  an empty source directory and annotation processing disabled. A positive
  consumer compiles and executes under a platform-parent classloader. A negative
  consumer is rejected specifically for an unavailable internal package/type.
- Eight semantic tests cover the current version, exact major/minimum minor,
  major priority, maximum integers, invalid construction, nulls and record values.
  Four artifact tests cover packaging, runtime identity and both consumer cases.
- The metadata test still requires an exact mod version and development notice;
  its expected values move to 1.3.0-dev, rather than weakening the assertions.
- Existing Forge channels, NBT roots and source/asset provenance are unchanged.
  Build-template notices are retained with updated modification dates. New API,
  tests and documentation are original community work; no upstream assets copied.

## Recorded failures and corrections

1. Initial build: 612 tests executed, two new artifact tests failed. The metadata
   allowlist incorrectly expected a Gradle NOTICE file not present in the audited
   repository, and separately compiled API bytes differed from Forge-remapped
   runtime bytes. The actual two supplemental license files remain required;
   the classifier now extracts final runtime classes without relaxing identity.
2. Corrected intermediate build: 612 tests and 121 required GameTests passed,
   2m39s, still using the inherited 1.1.1-dev artifact label. This is not the final
   artifact identity; its build output is retained separately.
3. Updating the development label to 1.3.0-dev exposed the existing exact-version
   metadata fixture. One of 612 tests failed. The fixture now checks the new exact
   version and honest development description. Its failure output is retained.
4. Independent local publication exposed the inherited default Maven artifact ID
   `AdvancedRocketry-Community` instead of the approved `advancedrocketry-community`.
   Commit `e7db5d29ce6b9b441c5111165b00599b2e5c98ba` explicitly sets the publication
   ID. This changes publication coordinates only, not runtime/API class bytes.

## Final verification

Implementation commit: `f6796f285bd5238ae88b8ed63b794dde04879e38`.
Windows / Oracle Java 17.0.7 / Gradle 8.8 / Forge 47.4.10.

| Command | Actual result |
|---|---|
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --no-build-cache` | Exit 0, 2m31s; 125 suites / 612 JUnit, no failures/errors/skips; 121/121 required GameTests; DataGen written 0 |
| `git diff --check` | Exit 0 |
| `git diff --exit-code -- src/generated` | Exit 0; generated resources unchanged |
| `python scripts/validate_v1plus_planning.py` | Exit 0; 11 plans, 33-input inventory |
| `python scripts/validate_v120_machine_resources.py` | Exit 0; 9 machine blocks and associated resources |
| `python scripts/validate_repository.py --require-approved-identity` | 44 passed, 1 failed: four pre-existing report-linked logs absent from a new checkout; see correction below |

The final run disables the build cache; its JUnit results are actual execution.
The GameTest save-error messages are the existing deliberate migration failure
fixtures; the final required-test completion marker is present.

Runtime JAR SHA-256:
`8822d0e20442a2f95bfcf9a33e743d03edb4ecbb6f49fcdbde32bf642be9bc4f`.
API JAR SHA-256:
`8602715a614476a1c2faa428187cebbb14daa3939ee8d9c69ae1fe1aaf722df5`.
Compared with the v1.2 `709f157c` artifact, only the three API classes are added;
only `META-INF/MANIFEST.MF` and `META-INF/mods.toml` change. No existing class or
gameplay resource differs, and no entry is removed.

Original output and machine-readable results are in [evidence](evidence/):
`gradle.txt`, `gametest.txt`, `junit-results.zip`, `junit-summary.json`,
`artifacts.json`, the failed build logs/XML and the intermediate build log.

### Historical evidence backfill

The strict validator's four broken links point to existing v1.0 cancel-diagnostic,
console-presentation, late-frame and station-duo captures that the old checkout
had locally but Git ignored. Independent audit confirms all four match their
existing historical checksums (408,671 bytes total; no credential scan hits).
They are copied byte-for-byte with exact-file Git attributes, without changing
old test results or checksums. The failure remains archived; its link check is
rerun after the backfill: exit 0, 1,141 relative links checked. The complete
historical validator was not rerun; its original 44-pass/1-failure output remains
in `repository.txt`. This is not a new v1.0 test run.

### Independent review

Reviewer `prec04b_readback_review` inspected the actual source/build diff; a
separate child review inspected isolation tests/fixtures. The publication ID
finding above required correction; no API semantic or isolation blocker was
found. Optional tightening of negative diagnostics to exact fixture positions
is not required for this slice: the positive consumer, archive allowlist and
mandatory internal-package diagnostic already guard against missing dependencies.

The reviewer executed
`gradlew.bat test --tests '*ApiVersionsTest' --tests '*ApiArtifactTest' --offline
--no-daemon --no-build-cache`: exit 0, 18.47s, 2 suites / 12 tests, no
failures/errors/skips. This is actual test execution, not a cached suite.
The full 125-suite root results were archived before this focused run replaced
the live test report. The independent run does not re-claim the full GameTest
or metadata-test scope.

Initial local publication completed in 12.60s but exposed the coordinate defect.
After `e7db5d2`, the reviewer reran only
`gradlew.bat publishMavenJavaPublicationToLocalProjectRepository --offline
--no-daemon --no-build-cache`: exit 0, 14.40s. The actual POM and directory names
use `io.github.sunthemoon.advancedrocketrycommunity:advancedrocketry-community:
1.20.1-1.3.0-dev`; main, sources and `api` artifacts match `build/libs` bytes.
Runtime/API hashes above are unchanged. An auxiliary checker initially used
Windows case-insensitive existence checks to distinguish old/new names; checking
actual directory names corrected that checker, with its output retained.

Final independent review: no unresolved finding; `V130-API-01` scoped verified.
No remote Maven upload occurred. [Independent output](evidence/independent.txt)
includes compiler diagnostics, commands, publication checks and final hashes.
The final evidence file list is [SHA256SUMS](evidence/SHA256SUMS).

## Remaining acceptance

No release/tag or Required Gate approval. This is not the complete v1.3
compatibility test mod or provider/adapter implementation. The inherited full
test campaign remains scheduled by ADR-018 after original machines/dimensions
are implemented. No new remote, GPU, multiplayer, packaged-restart or long-load
campaign is claimed for this pure metadata slice. Existing controlled migration
fault-injection ERROR logs must not be described as unexpected runtime failures.

The next v1.3 slice is the bounded rocket-adapter registration/lifecycle contract
and its transaction integration, including partial-placement rollback and
missing-provider preservation before opening external callbacks.
