# V130-ATM-02 independent review

## Findings
No unresolved correctness finding identified in the scoped implementation and tests.

Resolved low evidence-text issue: the original runner docstring claimed that only setup creates armor/canisters. The fixture recreates auxiliary armor for each FakePlayer and reconstructs full/empty stacks from counted authority. The final [runner description](../../../../../scripts/run_v130_suit_equipment_smoke.py#L4) now expressly limits native continuity to the saved chest armor and canister quantities. It does not claim all four armor stacks survive unchanged.

## Reviewed scope and decisions
- Accepted ADR-025 on HEAD f13c6136e513508459f94bbaf81d2062fec45018 plus the staged implementation diff; source-byte inventories bracket the independent run. No source/docs edits by reviewer.
- The three exported API types and 1.3 minor bump preserve older signatures. Owner/thread-bound registration preflights all mappings, reserves built-in suits, and publishes an immutable catalog only after validation.
- [SuitOxygenPayloads](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/compat/atmosphere/SuitOxygenPayloads.java#L27) bounds tree depth/nodes and serialized byte output before copying saved/returned payloads. Cyclic/deep/wide inputs reject; exact four-key envelope identity/version applies only to external data.
- [SuitOxygenAccess](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/compat/atmosphere/SuitOxygenAccess.java#L29) separates item-local OptionalInt.empty from session provider faults, uses independent copies and post-write exact readback, re-bounds mutated read arguments, enforces returned-time budgets and guards reentrancy. Diagnostics omit payload/exception text; clear resets disabled IDs.
- [SuitEquipmentService](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/compat/atmosphere/SuitEquipmentService.java#L67) prepares detached writes, rechecks actual worn chest and owned root, and rechecks held canister object/count/identity before commit/spend. Metadata-only isChest does not read server-session disabled state on the client.
- [PlayerLifeSupportService](../../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/server/PlayerLifeSupportService.java#L90) recomputes failed debit with zero usable oxygen and original cadence, after recalculating disabled-provider armor contributions. Built-in key/schema and packet/capacity/engine rules remain unchanged.
- The independent fixture imports API rather than implementation and invokes actual item use/Forge life-tick events. Those are deliberately dispatched FakePlayer events, not logged-in player timing or player save evidence.
- Runner inheritance retains new disjoint disposable directories, exact JAR identities/mod set, owned-process termination/finite waits and per-phase failure records. Native inventory oracle requires complete expected slot list, payload, unrelated marker and full/empty counts.

## Actual independent commands/results
Environment: JAVA_HOME=C:/Program Files/Java/jdk-17.0.7; PYTHONUTF8=1.

~~~powershell
.\gradlew.bat test --tests '*SuitEquipmentRegistryTest' --tests '*SuitOxygenAccessTest' --tests '*SpaceSuitOxygenTest' --tests '*ApiArtifactTest' --tests '*ApiVersionsTest' --rerun-tasks --offline --no-daemon --no-build-cache
D:\python\pyenv\pyenv-win\shims\python.bat -B -m unittest tests.test_v130_suit_equipment_smoke -v
~~~

- Gradle exit 0, wall 40.0350352 s (Gradle reports 39 s), 14 tasks executed: **43 JUnit, 0 failures/errors/skips**. Registry 8, oxygen access 13, legacy oxygen 3, artifact 10, version 9. Actual five-suite XML copied to focused-junit/.
- Python exit 0, 5 tests, unittest 0.001 s / wall 0.7465122 s.
- All 1,173 inventoried source/support files and all four JAR bytes unchanged before/after. Host/API/sources and fixture match root's immutable runtime input copies.
- Python ZIP read: API exact 13 classes, all byte-identical to main; no fixture package/resources in main/API/sources; fixture class entries stay in fixture namespace.
- Read-only git status/rev-parse/diff HEAD/diff --check, numbered Get-Content and rg; diff --check exit 0. An exploratory rg included nonexistent scripts/run_v130_api_server_smoke.py and returned missing-file diagnostic; the actual shared helper scripts/run_dedicated_server_smoke.py was then inspected. No network/Gradle server/Java game process started by reviewer beyond the authorized focused JUnit command.

## Artifact hashes
- build/libs/advancedrocketry-community-1.20.1-1.3.0-dev-api.jar: 346c9d776ec50d74e89fafbc44e470bbd75f88d0e28d5df9bcf6ff8c7b974668
- build/libs/advancedrocketry-community-1.20.1-1.3.0-dev-sources.jar: 4da9cee0ffd9c2f45a6632c087287758e680b00c0024cb7d92f5d1f8177eff95
- build/libs/advancedrocketry-community-1.20.1-1.3.0-dev.jar: bcc24106952dd520e040208ee14a1174359689824ca22f14becb48a4ee4c0da5
- compat-test-mod/build/libs/arce-adapter-compat-test-1.0.0.jar: 2e6aae7a916f2cae2f0b92ff6b1a5ea57030de320bd22c7e7424c7e851c8669a

## Evidence
The raw logs, exact commands/results, focused-junit/, before-rerun.json, after-rerun.json, and reviewed-staged-diff.patch reside beside this report.
- gradle.txt SHA-256: db8e174287a8f5060db37a49abad5db96527247f4b3c16cdba1fdd0eb078fe04
- python.txt SHA-256: 350b9a9cb80c0f7d78904df3ea61c8ac830a2c0e3b9c7f723fbf508a481090f3

## Independent native capture audit
Read only C:/Users/Administrator/AppData/Local/Temp/arce-v130-atm02-1790447854009/runtime-evidence. The retained helper independent_native_audit.py parses raw Anvil chunk (16,16), verifies the block at (264,220,264) and complete chest inventory, and decompresses/parses each actual level.dat. Low-level NBT/Anvil parser code is reused, but no runner semantic oracle or decoded disk-state.json determines expectations.

~~~powershell
D:\python\pyenv\pyenv-win\shims\python.bat -B C:/Users/Administrator/AppData/Local/Temp/arce-v130-atm02-independent-j7e3g_fs/independent_native_audit.py
~~~

- Final helper exit 0; wall 0.6797395 s. Verified all **53** manifested files and exact manifest coverage.
- Complete native chest inventories match oxygen **999 -> 998 -> 998 -> 1997**, full/empty canisters **1/1 -> 1/1 -> 1/1 -> 0/2**. The skipped-provider inventory is semantically identical to its prior capture. Unrelated marker, Damage, item/count/slot and provider/schema/payload version remain exact. The 2,000-unit starting resource balance equals saved oxygen + full-canister oxygen + cumulative consumed units (1/2/2/3).
- Same WorldGenSettings and seed **6957600196533924981**; native Time **47 -> 94 -> 142 -> 192**. Configuration TOMLs are byte-identical across all four captures, with loopback/offline settings verified from actual properties.
- All launch records bind the same immutable host/fixture JAR hashes. Only skipped includes skipSuitEquipment=true; all include suitSmoke=true and omit releaseTestHooks. Each command transcript contains exactly one phase command; only setup creates the chest block.
- Read all three raw log variants per phase. Each has exactly one correct registration/skip receipt, one phase assertion success, and orderly save/stop completion. No ERROR/FATAL, class/method/field linkage error, provider-disable diagnostic, or project warning found. Native process exit 0 is from per-phase process records, corroborated by shutdown logs rather than independently relaunched processes.
- Actual server observation windows: **21/21/23/24 ticks**; these differ from the separately asserted **20 manually dispatched FakePlayer life ticks** per phase. Health/protection assertions are fixture runtime checks, not persisted FakePlayer measurements.
- All 1,173 source/support hashes still match. Main/API/sources/fixture hashes match immutable runtime artifacts; source JAR bytes match relevant production source. Archived standalone consumer report's 13 fixture-source hashes and classifier/fixture artifact hashes match actual files. No independent standalone consumer rebuild is claimed.
- Manifest SHA-256: d8b84352a6305c43a37b66207bfdc6828c2bb11140452bed008e4b95a769564a.
- Read-only Get-NetTCPConnection for port 60520 and Java process query returned no entries after completion.

The first supplemental metadata parse stopped because the celestial SavedData reader's 4,096-node bound is smaller than full Forge level.dat. The Temp-only helper now uses a bounded 65,536-node allowance only for full level.dat, retaining 4 MiB/depth/string/collection bounds and restoring the original limit before other reads. The initial error is preserved in native-audit-initial-parser-bound.txt; this did not change project source, runtime assertions or captures. A subsequent supplemental provenance read initially used the wrong archive directory (consumer-reports/classpath.json rather than consumer-reports/consumer/classpath.json); its error is retained in native-audit-report-location.txt and the final helper uses the actual directory.

## Remaining unverified boundaries
Parent-reported complete suite/GameTests are not independent executions by this reviewer. No GameTest, consumer Gradle build or packaged server was independently rerun. Native final restored ItemStack is inspected after clean stop, but no fifth process reloads that final 1,997-unit result; the preceding restart/skipped/restored processes establish the covered persisted states.


This is not real-client HUD/network V1/V2, real-player login/save, arbitrary foreign capability compatibility, hard power-loss/crash-cut, non-returning/Java Error sandbox, full hardware/load campaign, or full v1.3 Required Gate acceptance. No new API beyond the frozen equipment slice is proposed.

## Final review identity and scoped recommendation
Root committed the same reviewed source during the audit; observed final HEAD 74def6e1510c3a3cc98357c4e7c32b6503bc986e. Inventoried source bytes remained identical. No unresolved scoped finding; evidence supports V130-ATM-02 independent scoped verification, **not full v1.3 Gate approval**. Continue only current-version remaining acceptance work.
