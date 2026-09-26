# API artifact test handoff

Date: 2026-09-26. Status: `READY_FOR_REVIEW`; execution remains `NOT_RUN`.

## Implemented scope and decisions

- The exact classifier allowlist now contains the three original version types
  and the three ADR-022 rocket types. Runtime archive scanning covers the whole
  `api/` package and compares every exported class byte-for-byte after reobf.
- Existing metadata consumers retain classifier-only compilation; the successful
  metadata consumer still executes with a platform-parent classloader. Existing
  internal-atmosphere negative diagnostics are retained.
- The platform consumer implements and invokes all three adapter methods,
  consumes the registrar and event registration methods, constructs a harness
  event, and uses its Forge `Event` and `IModBusEvent` contracts. Its compilation
  explicitly receives only the classifier and platform dependency JARs.
- Before either platform fixture compiles, every dependency must be a regular
  JAR, contain no ARCE class entries (including multi-release locations), and
  originate from a nonempty classpath entry. Direct main/test outputs, host JARs
  and API duplicates cannot enter as platform dependencies.
- A negative platform fixture attempts an existing internal rocket adapter
  import. The test requires the relevant missing-package diagnostic, not merely
  any compiler failure.
- Exported class bytes are checked for direct internal and client-only package
  references; metadata types additionally reject Minecraft/Forge references.
  This is not a sandbox or a substitute for the independent source compilation.
- Exact host version checks move to API 1.1; tests preserve the accepted API 1.0
  requirement and verify the current 1.1 and unsupported future 1.2 requirements.

## Files and tests

- Modified `ApiArtifactTest.java`, `ApiVersionsTest.java` and `ApiConsumer.java`.
- Added `RocketApiConsumer.java` and `RocketInternalConsumer.java` fixtures.
- Added this task directory's `TASK.md` and `REPORT.md` only.
- The two JUnit classes contain 16 test methods: seven artifact tests and nine
  version tests. Four test methods are new. This is source inventory, not an
  execution count.

## Actual commands and results

- `git status --short`: clean at task start; afterward only the declared test
  and task-document scope changed.
- `git branch --show-current`: `codex/v1.3.0-adapter-api-tests`.
- `git rev-parse HEAD`: base `b0ea71f8ffa5465dc9f94ad84e5fa66bcbf65bba`.
- Read ADR-022, existing API tests/fixtures and relevant build/adapter sources;
  used `rg` for test-method and classpath inventory.
- `git diff --check`: exit 0 after the test changes.
- No Gradle, Java compiler, JUnit, GameTest, server or client was run. The frozen
  production API and build property are not present on this task's base, and
  the delegated scope explicitly defers execution until integration.

## Remaining scope, risks and evidence

The integrator must add the production API 1.1 types, six-class packaging,
`arce.apiPlatformClasspath` and independent API compilation gate, then execute
and archive the command in `TASK.md`. The test compilation can still expose
mapping/dependency issues when first run; no successful result is predicted.

The platform fixture is a compile-boundary check, not the complete external mod,
Forge runtime compatibility, non-cancellable event behavior, registry lifecycle,
uninstall/reinstall or restart recovery evidence. Existing deferred Required
Gates remain unchanged. No current version Required Gate is approved.

Evidence for this delegation is the committed test diff and these task records;
there is no runtime evidence directory or fabricated test output. The next
action is integration and finite API test execution within V130-ROCKET-02,
followed by the separately scoped V130-ROCKET-03 external consumer work.
