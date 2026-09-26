# V130-ROCKET-01: adapter failure containment

## Scope and implementation

This development prerequisite hardens existing internal rocket adapters before
external registration is added. It does not export a new API, change vanilla
adapter IDs, snapshot/journal schemas, hashes, tags, packets or recipes. API
metadata remains 1.0 and the mod remains `1.20.1-1.3.0-dev`.

- Baseline: `c15bd7e140bac66bffe49091a83c3296de49713e`.
- Independent test contribution: `4f69871b1e176a71b7c7b836b7faca16eb0b43b3`,
  integrated as `43ae22d` without changing its assertions.
- Production and pure tests: `01bcb9222a436be6e4224274459db612d7880fe4`.

Changed production classes are `RocketBlockEntityAdapters`,
`ServerLevelRocketTransactionWorld`, `RocketTransactionWorld`,
`RocketTransactionFaultProbe`, both assembly/disassembly transactions,
`RocketTransactionRecoveryService` and its lifecycle owner `RocketManager`.
All changes are original community work; no assets or upstream source copied.

### Decisions

1. Catch runtime exceptions from supports/capture/restore. Captured payloads must
   identify their registered adapter; successful restore must capture back the
   exact expected payload. Callback exception messages are not echoed as data.
2. A failed placement returns false only after checking no new block/BlockEntity
   remains. Unconfirmed cleanup/removal throws a rollback-failed signal; the
   transaction retains its full recovery record even for an untracked mutation.
   Rollback exceptions no longer escape and skip the remaining bounded cleanup.
3. Check block/tag availability and adapter IDs before disassembly or recovery
   mutation. Missing providers preserve the original entity, opaque payload and
   journal. Availability does not promise that a present provider will restore.
4. Before block-authoritative recovery, inspect the entire bounded target and
   confirm entity removal before exposing restored inventories. A runtime restore
   failure can leave the full journal, rather than an entity, as recovery authority.
   Assembly rollback similarly waits for confirmed entity removal before placing
   blocks. Neither path treats an uncertain removal as success.
5. Keep one loaded transaction attempt per tick. A lifecycle-owned UUID cursor
   rotates sorted journal entries so a conflict cannot indefinitely monopolize
   attempts. Clearing the manager clears the cursor; unloaded regions stay deferred.

## Finite tests and observed failures

The independent [baseline report](../v1.3.0-adapter-fault-tests/REPORT.md) records
one pre-fix run: 132 Required GameTests, **124 passed / 8 failed**, Gradle exit 1
(GameTest process exit 8), 1m51s. All eight failures are new regressions; all
121 pre-existing tests passed. Original failure captures and hashes are retained.

Added tests:

- Eleven real-world GameTests and one local fixture cover callback exceptions,
  payload identity/readback, partial-placement cleanup, rollback failure and
  recovery, unavailable providers, NBT preservation and two-record fairness.
- Six `RocketTransactionExceptionTest` methods cover uncertain block mutations,
  rollback exceptions, unavailable dependencies and entity removal false / throw
  before discard / throw after discard. Failed outcomes retain journals and
  release operation locks.
- Five `RocketBlockRecoveryTest` methods cover entity-removal rejection/exceptions,
  target conflicts, retry and entity-before-block ordering.

Independent source review found two issues in intermediate changes: placing
recovery blocks before confirmed entity removal, and continuing assembly rollback
placement after an entity-removal exception. Both were corrected before the
passing integrated run; the new pure tests cover their failure boundaries.

## Executed integration checks

Windows / Oracle Java 17.0.7 / Gradle 8.8 / Forge 47.4.10.

| Command | Observed result |
|---|---|
| `gradlew.bat clean build test runData runGameTestServer --offline --no-daemon --no-build-cache` | Exit 0, 2m12s; 127 suites / 623 JUnit, 0 failures/errors/skips; all 132 Required GameTests passed; DataGen written 0 |
| `git diff --exit-code -- src/generated` | Exit 0; no generated-resource changes |
| `git diff --check` and staged whitespace check | Exit 0 |
| `python scripts/validate_v1plus_planning.py` | Exit 0; 11 plans / 33-input inventory |
| Focused `validate_repository.check_markdown_links` | Exit 0; local Markdown targets resolve |

Captures: [Gradle output](clean-build-gametest.txt),
[native GameTest output](gametest-native.txt),
[complete JUnit XML archive](junit-full.zip).
The deliberate Precision Assembler save-failure injections remain in the native
log; the final required-test success marker is present. No assertion, timeout or
budget was relaxed. The full XML archive precedes the focused independent rerun.
[Static check output](static-checks.txt) records the planning/link/whitespace and
generated-resource checks. Full historical repository validation was not rerun.
[SHA-256 manifest](SHA256SUMS) covers this slice's captured evidence.
The first staged whitespace check reported CRLF/trailing spaces in raw captures.
Scoped evidence attributes follow the existing capture policy: preserve those
bytes instead of normalizing logs to satisfy a source-format check. Production
source/document formatting rules are unchanged; the repeated staged check passed.

Runtime JAR SHA-256:
`d9687101d868b3dca5ce45d263547835faed3a333cdcc1ad50ff7b6d6ed01394`.
API classifier SHA-256:
`8602715a614476a1c2faa428187cebbb14daa3939ee8d9c69ae1fe1aaf722df5`;
the classifier is unchanged from V130-API-01.

### Independent review and rerun

An independent reviewer inspected the actual production diff and all eleven
GameTests, then ran:

```powershell
.\gradlew.bat test --tests '*RocketTransactionExceptionTest' --tests '*RocketBlockRecoveryTest' --tests '*RocketTransactionsTest' --tests '*RocketTransactionRecoveryReadinessTest' --tests '*RocketRecoveryDecisionTest' --offline --no-daemon --no-build-cache
```

Exit 0, 25.533 seconds; **5 suites / 38 tests**, no failures/errors/skips.
`:test` executed rather than being restored from cache. Main/API/sources JAR
hashes were unchanged. No unresolved finding remained in the reviewed slice.
The reviewer did not independently rerun GameTest or start Minecraft.

Evidence: [independent command output](independent-junit.txt),
[focused JUnit XML](junit-independent.zip).
Output SHA-256: `002bce69c5739cee7d15a9f0765d2b91f399f5365b26834728bc5d9295aab431`.

### Independent packaged restart

A separate verifier used the same final runtime JAR with a copied local Forge
runtime and a fresh disposable world. No installer, old world, remote server,
credentials or client was used. Binding was loopback-only, offline mode.

Five Java processes exited 0: baseline fresh start and same-world restart,
followed by the existing `run_v050_rocket_server_smoke.py` assembly, entity
restart and staged-recovery restart. All runner commands exited 0. Entity UUID,
snapshot hash and complete authority SNBT matched across restart; four blocks
and the vanilla Chest's 17 diamonds / 64 iron ingots were restored.

Because the historical runner's journal-cleared summary flag is unconditional,
the verifier separately read the actual saved journal after clean stop. The
bounded NBT inspector confirmed DataVersion 3465, journal schema 2,
`format_epoch=v0.9.0-beta` and an empty transactions list. The 108-byte saved
journal fixture is retained. This is S1 clean/staged recovery, not S2 power loss.

All five full logs had zero ERROR/FATAL/client-linkage/project-logger findings;
the preserved warnings cover ordinary Forge startup and deliberate offline mode.
The port was closed after the runs. Exact commands, assertions and limits are in
the [independent packaged report](packaged-restart/VERIFICATION.md); its
[24-file checksum manifest](packaged-restart/checksums.txt) matches the imported
bytes. The captured helper is one-off evidence, not a new product tool.

## Boundaries, remaining work and rollback

- This is not a callback sandbox or execution-time preemption mechanism. Java
  Errors and arbitrary external world/thread/I/O side effects are not compensated.
- Cleanup rejection can require later recovery or operator inspection. Retaining
  a journal does not quarantine arbitrary already-exposed resources or prove
  cross-chunk crash atomicity. No such general guarantee is made by this slice.
- GameTest opaque-data coverage is an in-memory SavedData save/load round trip;
  absent-provider coverage is a local registry omission, not a real external-mod
  uninstall/reinstall. Drop checks are bounded synchronous local observations.
- Public registration, provider-owned payload versions, compatibility-mod consumer,
  external-provider restart, other v1.3 provider APIs and full acceptance remain
  unfinished. No new supported public contract is frozen here.
- No long-duration load, remote Linux, multiplayer, real GPU or full original-feature
  acceptance was run. ADR-018 scheduling and inherited Gates remain unchanged.
- G0-G9 as a whole are **not satisfied**; version status stays `IN_PROGRESS`.
  No release tag, remote publication or human approval is created.
- Reverting the development code commit and its fault tests restores the preceding
  behavior without a save migration, but reintroduces the demonstrated failures.

Continue with `V130-ROCKET-02`: freeze and integrate the smallest public adapter
registration contract; then add its independent compatibility consumer/restart
coverage in `V130-ROCKET-03`.
