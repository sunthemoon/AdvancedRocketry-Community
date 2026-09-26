# V130-ROCKET-01 adapter-fault baseline

## Scope and status

- Date: 2026-09-26.
- Production baseline: `c15bd7e140bac66bffe49091a83c3296de49713e`.
- Worktree: `codex/v1.3.0-adapter-fault-tests`.
- Status: `READY_FOR_REVIEW`; this is deliberately retained pre-fix evidence,
  not a passing implementation or version Gate.
- Added [11 GameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RocketAdapterFailureGameTests.java)
  and [one package-private fixture](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RocketAdapterFailureFixture.java).
  No production classes, public API, schema, registry, or template changed.

## Fixture boundary

Each fixture scans four real world blocks: motor, seat, guidance computer and
Chest. The Chest carries 17 diamonds and 3 iron ingots. A local adapter delegates
the ordinary container payload to the existing vanilla adapter, under its own ID,
and injects one selected fault. Adapter collections are per fixture; the global
runtime and default registry are not replaced.

Transactions use actual `ServerLevelRocketTransactionWorld`, `RocketEntity` and
server `RocketTransactionSavedData`. Recovery checks cover the saved snapshot's
complete NBT round trip, item payload, entity identity and repeated recovery.
The fairness case owns two separated four-block rockets and allows two bounded
recovery calls for two records. Fixtures remove only their own records/entities,
blocks and newly created local drops in `finally`. The ordinary `rocket_test`
template and 40-tick per-case timeout are unchanged.

## Actual command and result

One invocation, Java `C:/Program Files/Java/jdk-17.0.7`, Forge `47.4.10`:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
& .\gradlew.bat runGameTestServer --offline --no-daemon *> docs/work/v1.3.0-adapter-fault-tests/baseline-gradle.txt
```

Compilation succeeded without any fixture corrections or rerun. The full suite
completed **132 Required tests: 124 passed, 8 failed**. The eight failures below
are all new tests; the existing 121 and three other new tests passed. Gradle
returned **1**; the GameTest Java process returned **8**. Gradle elapsed time was
**1m 51s**. The native run ended at `22:13:23` local time.

| New GameTest | Baseline | Observed result |
|---|---|---|
| `supportsExceptionIsRejectedBeforeMutation` | FAIL | Injected `supports` exception escaped dispatch. |
| `captureExceptionIsRejectedBeforeMutation` | PASS | Capture rejected; original world payload unchanged. |
| `captureCannotSubstituteAnotherAdapterIdentity` | FAIL | Foreign payload ID accepted. |
| `restoreExceptionAfterMutationLeavesNoPartialPlacement` | FAIL | Injected restore exception escaped after inventory mutation. |
| `restoreFalseAfterMutationLeavesNoPartialPlacement` | PASS | False result removed the partial container without item drops. |
| `restoreTrueRequiresAnEquivalentContainerPayload` | FAIL | Incorrect restored inventory accepted when callback returned true. |
| `disassemblyRestoreExceptionPreservesTheOnlyRocketAuthority` | FAIL | Failed disassembly left a partial source Chest. |
| `failedRollbackRetainsJournalUntilThePayloadCanBeVerified` | PASS | Incomplete cleanup retained journal/snapshot; healthy retry preserved entity authority and removed partial blocks. |
| `assemblyRollbackExceptionRetainsSnapshotForLaterRestoration` | FAIL | Restore exception escaped the assembly rollback. |
| `missingProviderPreservesEntityPayloadAndJournalUntilItReturns` | FAIL | Missing-provider recovery removed the retained rocket entity. |
| `unavailableProviderDoesNotStarveALaterRecoverableTransaction` | FAIL | Earlier unavailable record prevented the later valid record from completing within two recovery calls. |

The native log also contains injected save failures from the existing Precision
Assembler tests. Those are separate passing fault-injection cases, not extra
adapter-fault failures. First-launch `server.properties` absence did not prevent
the GameTest server from starting and completing.

## Evidence and remaining work

- [Gradle capture](baseline-gradle.txt).
- [Native GameTest capture](baseline-gametest-native.txt), copied byte-for-byte
  from `build/gametest/logs/latest.log` after the process exited.
- [SHA-256 manifest](checksums.sha256). Scoped attributes preserve captured bytes;
  report/source files retain the repository's normal text policy.
- `git diff --check` and staged whitespace review: no errors.

The integrator must combine these unchanged assertions with the production
correction, rerun applicable short checks and arrange independent review. This
worker did not execute `clean build`, `runData`, packaged-server/restart, external
compatibility-mod or client/long-duration checks. Missing providers here are local
registry absence plus SavedData codec reload, not a real mod uninstall/restart.
The recovery fairness case is two owned records, not a sustained-load claim.
These tests do not establish bounds for arbitrary hostile callback execution,
external side effects, or every possible Forge block implementation.

No Required Gate or parent-version status is changed by this test-only branch.
