# V120-PREC-04B-U — unload notification lifecycle

```yaml
status: verified
date: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
uncommitted_worktree: true
artifact_sha256: 92821ec3273a7b87c2892003ca5321f8cf65105a41572a3f730c3582d67c2000
environment: Windows 11, Java 17.0.7, Forge 47.4.10
decision: ADR-019
```

## Scope

Two short GameTests call the production manager's `onChunkUnloading` and
`onChunkChanged` for a port chunk separate from the controller. The physical
chunks intentionally remain loaded: these are deterministic notification
tests, not actual chunk eviction, BE replacement, Forge event-dispatch,
no-force-load, disk-fault or new-process restart evidence.

No production code changed in U. Comparing every uncompressed JAR entry with
the previously tested `96962cc9…` artifact found only four added unload-test
classes, no changed or removed entries. Previous packaged migration/restart
and S2 evidence retain their original artifact identities; no new S2 is claimed.

## Cases and fixture boundaries

| Case | Observations |
|---|---|
| ACTIVE | Start a real recipe at progress 3; pause; unload notification gives WAITING, preserves all seven Items, process/journal, identity/generation/bindings and physical Energy; old optional and resolved handlers cannot operate, fresh queries are absent; load notification re-forms with fresh handlers; remaining 17 ticks produce exactly one circuit and two torches, leaving 200 FE; retired output still cannot extract |
| PREPARING | Seed central Items and matching old shadows with three migration markers; WAITING preserves the complete snapshot and denies access; revalidation completes migration with seven correct owner/channel markers; fresh insertion changes central inventory, not shadow; a second unload/load preserves the change without reimporting the shadow |

Fixtures use separate batches, four expiring chunk tickets and an explicit
clear of the external redstone position before setup. A local real manager
loads the actual bounded pattern, observes the controller and drains initial
work before snapshots. The exercise runs synchronously in one GameTest
callback; the global runtime manager is never replaced, and local cleanup is
in `finally`. No timing budget or existing assertion was relaxed.

Full controller and all eight port NBT tags are compared, allowing only the
expected formation state, phase and newly added migration markers. PREPARING
fixture construction itself retires its old Item views via `port.load`, so
the ACTIVE case supplies the independent unload-driven Item-epoch evidence.

## Actual commands and results

Gradle used `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`.

| Command | Result |
|---|---|
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon` | exit 0, 2m52s; 123 suites / 600 JUnit, zero failures/errors/skips; all 121 Required GameTests; DataGen written 0 |
| Independent `gradlew.bat runGameTestServer --offline --no-daemon` | exit 0, 1m44s; all 121 Required; both U batches ran; existing world reused without clean; compilation/resources up-to-date; JAR hash unchanged |
| `python -B scripts/validate_v120_machine_resources.py` | PASS, 9 blocks and associated resources |
| `python -B scripts/validate_v1plus_planning.py` | PASS, 11 plans / 33 inputs |
| `python -B scripts/validate_repository.py --require-approved-identity` | exit 0, 45 checks passed; run during documentation integration, followed by an 8-document / 161-local-link check after status edits, zero missing |
| `git diff --check` | exit 0 |
| `git diff --exit-code --stat` and generated-resource-only equivalent | both exit 1: uncommitted implementation and two existing generated language additions; not a clean-tree Gate pass |

Both GameTest logs include intentional save-fault exceptions and disk mismatch
refusals from existing migration cases. U added no failure. Deprecation warnings
remain; neither the log nor the check claims an exception-free fault-injection run.

Independent source review and rerun found no new issue within U's scope and
recommended `verified`. The finite ADR-019 evidence audit can therefore mark
the parent persistence slice verified, without approving the version Gates.

## Evidence and remaining limits

- [Clean build and fresh-world GameTest log](unload-lifecycle/clean-build-gametest.txt)
- [Independent reused-world run](unload-lifecycle/independent-gametest.txt)
- [JUnit suite summary](unload-lifecycle/junit-summary.json)
- [JAR entry comparison](unload-lifecycle/artifact-comparison.json)
- [Repository validation](unload-lifecycle/repository-validation.txt)
- [SHA-256 manifest](unload-lifecycle/SHA256SUMS)

The [parent report](PREC04B-VERIFICATION.md) retains physical-I/O, power-loss,
Item-entity removal and external Energy boundaries. Long-duration, full
remote/GPU/two-client and whole-content acceptance remain under ADR-018.
This does not mark v1.2.0 or any inherited Required Gate passed.
