# Registry and provider-envelope unit-test handoff

## Completed scope

Added 36 JUnit methods under the owned `compat/rocket` test package:

| Class | Methods | Coverage |
|---|---:|---|
| `RocketAdapterRegistryTest` | 20 | Accepted registration and legacy availability; exact version availability; owner namespaces and reserved vanilla IDs/types; duplicate adapters and types; atomic rejection including lookup failure; defensive type reservations; null/invalid input; 255/256-character boundaries; replaced handles, freeze, close and foreign threads; 256 adapters, 64 types per adapter and 1024 total external mappings. |
| `RocketAdapterPayloadsTest` | 11 | Exact two-key typed envelope; body/version/adapter preservation; nested defensive copies; exact version compatibility; malformed persisted fields; root identity rejection and allowed nested resource identities; exact 262144-byte envelope limit and first overflow; registry-independent decoding. |
| `ExternalRocketBlockEntityAdapterBudgetTest` | 5 | Fake-clock values below/at/above 5 ms; rejection after return; subsequent slow and fast calls; original callback exception propagation; monotonic-clock signed wraparound. |

The registration provider throws `AssertionError` if invoked: catalog validation
and availability checks must remain independent of world callbacks. Thread tests
own their executor, join it with a bounded wait and shut it down in `finally`.
Budget tests do not sleep and do not claim preemption or a complete tick budget.

## Actual checks and commands

- Read the frozen ADR-022 and applicable repository/version/parallel rules.
- Read the actual primary-worktree registry, payload and callback-guard source
  without copying or modifying those production files.
- PowerShell source inventory: 20 + 11 + 5 `@Test` methods; all three Java files
  are ASCII and below 500 lines.
- An initial inventory command had a PowerShell pipeline syntax error before
  execution; the corrected read-only command exited 0. No Java test ran.
- `git diff --check` / staged whitespace and scope checks are recorded with the
  test-only commit handoff. No Gradle, Minecraft, server or network command ran.

## Integration and remaining verification

The base intentionally does not contain the new production/API classes. Combine
these tests with the integrator's implementation before compiling. Suggested
focused command after integration:

```powershell
.\gradlew.bat test --tests '*RocketAdapterRegistryTest' --tests '*RocketAdapterPayloadsTest' --tests '*ExternalRocketBlockEntityAdapterBudgetTest' --offline --no-daemon --no-build-cache
```

No test is reported as passed. The type-set test checks defensive reservations,
not live Forge dispatch. These tests do not prove actual mod-bus ownership,
BlockEntity matching, logical-side/world checks, warning count, classifier
isolation, callback side-effect compensation, process restart, or missing-mod
uninstall/reinstall behavior. Runtime and consumer verification remain owned by
the integration tasks. No version Required Gate is approved.

Only new tests and this task directory changed. There are no production,
schema, asset, provenance, network or public API edits. The final commit ID is
reported by the worker after committing; reverting that test-only commit removes
the slice without changing runtime behavior.
