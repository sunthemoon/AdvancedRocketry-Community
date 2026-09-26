# V130-ROCKET-02 API artifact boundary tests

## Task and ownership

- Role: delegated test implementation; no production or build changes.
- Milestone: v1.3.0; contract: accepted ADR-022, API 1.1.
- Branch: `codex/v1.3.0-adapter-api-tests`.
- Worktree: `D:/GitHub/arce-v130-api-tests`.
- Base: `b0ea71f8ffa5465dc9f94ad84e5fa66bcbf65bba`.
- Write scope: `src/test/java/**/api/**`, `src/test/resources/api-consumer/**`,
  and this task directory.
- Production API, `build.gradle`, central status and version documentation are
  owned by the integrator.

## Scope

Preserve the three metadata types' classifier-only consumer and negative
internal-import fixture. Extend exact artifact membership/runtime byte checks to
the three ADR-022 rocket types. Compile a separate consumer with the classifier
and explicit platform dependency JARs, checking the entire platform classpath for
host-class leakage before both positive and negative compilation. Cover the
frozen adapter methods, registrar/event registration signatures, event superclass
and mod-bus interface. Check exported class bytes for internal/client-only
references, and assert exact API 1.1 plus compatible 1.0 requirements.

## Non-goals and verification boundary

No runtime registry, callback, persistence, mapping or restart behavior is
implemented here. This is not the independent compatibility mod required by
V130-ROCKET-03. No production, build, source-set or Gate policy is changed.

The integrator must provide `arce.apiPlatformClasspath` using external platform
compile dependencies, retain the JDK-only metadata compilation, and package the
six explicit exported classes from the reobfuscated runtime artifact. Test
execution is deferred until those production/build changes are integrated; no
Gradle, server or client process may be started in this task.

## Suggested integration command

```powershell
.\gradlew.bat test --tests '*ApiVersionsTest' --tests '*ApiArtifactTest' --offline --no-daemon --no-build-cache --rerun-tasks
```

Archive the actual result and compiler diagnostics after execution. Source
presence and static review alone do not prove these tests pass.
