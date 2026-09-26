# V130-ROCKET-04 independent review and focused verification

## Scope and source

- Reviewer: delegated read-only reviewer; implementation was owned by the root agent.
- Repository: D:/GitHub/AdvancedRocketry-Community.
- Reviewed actual changes from da4e49a against accepted ADR-023; final tested HEAD: 094705e31e82f0e0ed3c886aeb1c311fa1566428 (production/tests/resources a8b427a plus runner 094705e).
- No repository source or documentation edits, GameTest server, packaged server, or client were performed by this reviewer. Only the authorized Gradle outputs and this Temp evidence were written.

## Findings

No unresolved correctness finding identified in the reviewed ROCKET-04 scope.

One Low finding was corrected before the final source freeze: repeated unchanged offers reused their original expiry but advertised a fresh ten seconds. RocketDisassemblyService.java:38-47 now computes ceiling seconds from that offer's issuedAt, without extending expiry.

## Reviewed behavior

- RocketDisassemblyConfirmations.java:12-78: at most 64 player-owned transient offers, 200-tick lifetime, unchanged-offer reuse, exact quote replacement, token/player isolation, one-time consumption, bounded expiry and lifecycle clearing.
- RocketDisassemblyService.java:32-124: main-thread rate limit shared by offer and confirmation; current player eligibility/permission/range, loaded entity lookup, legal flight state and pending transaction checks. Quote equality binds owner, snapshot UUID/hash, full immutable flight data, and position. No client-supplied target or fuel amount becomes authoritative.
- RocketDisassemblyService.java:139-172: release-test implicit fueled teardown is refused; explicit amount must match. Entity fuel is not zeroed before transaction. Successful entity removal intentionally disposes the confirmed remainder, reports exact amount and releases the existing landed reservation. Transaction/persistence schemas and recovery code are unchanged.
- RocketDisassemblyCommands.java:15-22 and RocketCommands.java:64-112: player confirmation is outside the operator-only diagnostics subtree; administrative release-test commands retain startup/permission restrictions.
- RocketEntity.java:335-355 and RocketManager.java lifecycle hooks: offhand cannot request or confirm teardown; logout/manager clear invalidate offers.
- New GameTests read: real interaction/offhand, service rate limit, countdown and player-eligibility changes, absence of loaded entity, zero-fuel compatibility, separate non-operator command, stale amount, wrong player/range/replay, save/load plus manager replacement, occupied destination, restoration rollback, and explicit landed-reservation cleanup regression. Fixture managers are isolated; the interaction test uses installed runtime and clears its own random-player offer through logout.
- Additive v1.3 DataGen provider and packaged language test bind both locales and five message IDs, including placeholder counts. Existing generated resources remain inputs.
- Runner now checks implicit/wrong-amount refusals and unchanged entity data/reservation/world ownership before exact operator disposal; source review is not substituted for its actual packaged execution.

## Independently executed command

JAVA_HOME=C:/Program Files/Java/jdk-17.0.7

```text
.\gradlew.bat test --tests *RocketDisassemblyConfirmationsTest --tests *RocketDisassemblyLanguageTest --tests *RocketIntentRateLimiterTest --tests *RocketTransactionsTest --tests *RocketTransactionExceptionTest --rerun-tasks --offline --no-daemon --no-build-cache
```

Exit 0; 42.855556 seconds wall time; Gradle reports 42 seconds and 13/13 tasks executed.

| Suite | Tests | Failures/errors/skips |
|---|---:|---:|
| RocketDisassemblyConfirmationsTest | 8 | 0/0/0 |
| RocketDisassemblyLanguageTest | 1 | 0/0/0 |
| RocketIntentRateLimiterTest | 3 | 0/0/0 |
| RocketTransactionExceptionTest | 6 | 0/0/0 |
| RocketTransactionsTest | 14 | 0/0/0 |
| Total | 32 | 0/0/0 |

`git diff --check` also passed during source review.

Full raw log: gradle.txt. SHA256: a39a26d53999293b1a852e1c78aa52a5bad1b04cc8a9aa89675068b41db92c92.
Actual five JUnit XML files are preserved in junit/. Command/results are recorded in result.json; source and artifact manifests are in before.json, after.json and comparison.json.

## Artifact/source integrity

All 20 selected changed source/build/runner/test/resource files and all four artifact hashes remained identical before/after the independent rerun. Git HEAD remained unchanged. Preexisting user and root evidence directories remain untracked; they were not modified by this reviewer.

| Artifact | SHA256 |
|---|---|
| Main JAR | 65af2c91dc8ed3e4174c5ee58e6d1b8fbcde5ae127f308fee1cc009cf695663b |
| API JAR | 695e5d91cd9a1ffc883163e96d0d8cc581e7ed72827d185231f56273205be5c3 |
| Sources JAR | c72cb3d2738fa4b7e7c02976ea71052a0a4dc84d1f9e3d979504206be337fca0 |
| Independent fixture JAR | 8144ded5d03764cdd6b1eda8ff5e778f7dd87c564ebde3e5dfe20d382629085f |

## Remaining verification boundaries

- The reviewer did not independently rerun GameTests or launch a packaged JVM. Root-reported 672 JUnit/145 Required GameTest remain separate from the 32 independently executed JUnit tests. The completed packaged runner's raw evidence was independently audited below.
- The new missing-entity GameTest removes a loaded entity; it is not an actual chunk-unload/reload test. Pending-journal refusal is source-inspected, not a new dedicated ROCKET-04 test.
- The lifecycle GameTest reloads NBT and replaces/clears manager instances inside one JVM; it is not process-restart evidence.
- Packaged language byte/format checks do not establish real-client rendering or user comprehension. The player command test uses a local Brigadier dispatcher; the installed diagnostics permission check is separate. No V1/V2 claim.
- Existing bounded transaction recovery remains unchanged; this review does not establish new arbitrary crash atomicity or sandbox arbitrary same-JVM provider effects.
- Explicit disposal is not fuel recovery/conservation after teardown. The approved behavior removes the confirmed remainder only on successful disassembly.

The reviewed source and focused verification have no unresolved finding. This is not approval of the full v1.3 Required Gates.

## Independent packaged-evidence readback

Input: C:/Users/Administrator/AppData/Local/Temp/arce-v130-fueled-disassembly-1790442426341. The root agent performed the runtime run; this reviewer only read its immutable captures.

Command: `python -B native_postcheck.py` in this Temp evidence directory. Exit 0, 1.868985 seconds. The helper adapts the prior independent raw-region audit only for the input location, artifact-manifest shape, and output location; its original/adapted hashes are preserved in native-review.json. Additional ROCKET-04 assertions inspect actual commands, log ordering, entity SNBT, transfer inspection, source artifact hashes and the retained ZIP refresh.

| Phase | JVM exit | Observed ticks | Touched chunks | Rocket / cargo BE | Transfer / transaction entries |
|---|---:|---:|---:|---:|---:|
| Moon landing | 0 | 22 | 2 | 1 / 0 | 1 / 0 |
| Earth landing | 0 | 22 | 6 | 1 / 0 | 1 / 0 |
| Explicit disassembly | 0 | 21 | 6 | 0 / 1 | 0 / 0 |
| Native container restart | 0 | 21 | 6 | 0 / 1 | 0 / 0 |

Confirmed from raw Anvil/entity/SavedData files, not merely disk-state.json:

- Exact external inventory: named 17 diamonds and 64 iron ingots; payload version/adapter and relocated snapshot contents preserved through both flights. Logical identity/owner retained; source and destination physical entity IDs differ.
- One initial fill of 1000, two actual debits of 372, balances 628 and 256, exact debit IDs in the saved flight/journal. No return top-up.
- At final landing the implicit command rejects with FUEL_DISPOSAL_REQUIRED and explicit 257 rejects with WORLD_CHANGED. Before any removal, full entity SNBT equals the prior stopped Earth capture and the retained COMMITTED transfer inspection is unchanged. Live ownership checks succeed after both rejections.
- Explicit 256 then produces exactly one fuel-disposal receipt, SUCCESS for five restored blocks, and landed-reservation release. Native cargo BE is identical after the fourth process; both journals are empty. No item entities in bounded touched chunks and no stale rocket blocks at prior origins.
- All four status responses and debug loading records identify the actual host and fixture JARs; their hashes match the independently verified build. Actual old/new ZIP entry comparison shows only META-INF/THIRD-PARTY-NOTICES.md changed in each of the three refreshed host/API/sources artifacts.
- All 81 original manifest files match their checksums. Each of stdout/debug/latest logs has zero ERROR/FATAL/project WARN/linkage findings. Non-project warnings are retained: 25 in the first process and 10 in each later process, including offline/local and Forge bootstrap notices. The loopback endpoint is closed after completion.

Additional files: native-review.json, native-observations.json, native-postcheck-result.json, native-postcheck.txt, native_postcheck.py, adapted-native-postcheck.py.

- native-review.json SHA256: 64f9be6bab43102c6a7be6168b5dc532436ea4f4879c0c2c838db744dca5c2dc
- native-observations.json SHA256: 5490fc47fdb45f922e68bd0689a936c188dac089ff61f04a2fbc1626eb9c1cc8
- Original runtime SHA256SUMS SHA256: 00e38adc23feeedb14a67b82a7eaa40f8dc8f0e8ab57134336d29f48fd43173c

No new finding in this evidence audit. This proves the bounded operator-hook disposal/cargo/restart behavior, not real-player confirmation in a packaged client, multiplayer, arbitrary crash cuts, or fuel recovery after disposal.
