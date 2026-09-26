# V130-ROCKET-02 external registration fixture

- Task type: implementation subtask; independent review remains required.
- Version: v1.3.0, scoped to ADR-022 registration and production integration.
- Branch: `codex/v1.3.0-registration-fixture`.
- Worktree: `D:/GitHub/arce-v130-registration-fixture`.
- Base commit: `b0ea71f8ffa5465dc9f94ad84e5fa66bcbf65bba`.
- Write scope: `src/adapterTest/**` and this task directory only.
- Forbidden writes: production code, Gradle, canonical ADR/status/version files.
- Commands: read-only inspection and static fixture validation allowed; no Gradle
  or Minecraft process in the delegated worktree. Integration owns compilation
  and real GameTest execution after the API implementation is available.

## Observable outcome

A separate Forge mod, `arce_adapter_test`, registers its own non-vanilla
two-slot container and public API adapter. Dedicated GameTests inspect the actual
mod-bus registration, assemble a four-block rocket through the host command,
inspect the saved external payload, and disassemble through normal owner entity
interaction. No host internal imports, replacement Runtime, or manually
constructed host registries/managers are permitted.

## Integration contract

- Source set `adapterTest`: `src/adapterTest/java` and `src/adapterTest/resources`.
- Compile only against the explicit API output/classifier and Minecraft/Forge
  platform dependencies; do not include main output/internal classes.
- Load its source set as mod `arce_adapter_test` only in `gameTestServer` runs.
  Keep it outside the main JAR, API classifier, client and ordinary server runs.
- Tests reuse the host template with `templateNamespace =
  "advancedrocketrycommunity"`, `template = "rocket_test"`.
- The tag resource appends `arce_adapter_test:cargo_container` to the host
  `rocket_movable` tag without replacing its existing values.
- No additional JVM property, client asset, release-test hook or production
  command is required.

## Non-goals and evidence limits

This fixture does not establish complete V130-ROCKET-03 compatibility-mod
coverage, third-party uninstall/reinstall, cross-dimension transport, power-loss
recovery, real clients, or any Required Gate approval. Entity NBT inspection is
finite serialization evidence, not a packaged-server restart test.

Status: READY_FOR_REVIEW. Static inspection is recorded in `REPORT.md`;
compilation/runtime results must come from the integration run.
