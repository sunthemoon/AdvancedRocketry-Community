# V130-COMP-01 independent review and focused verification

## Final findings and disposition

No unresolved concrete correctness, packaging or native-evidence finding identified within V130-COMP-01. The two initial coverage observations below were addressed before the final independent rerun. Source review is against baseline 180fb81 and accepted ADR-026 ee7dce7; reviewed production/runner sources are now committed as 3eb7cad/d86c3aa. Last observed HEAD: d86c3aa695a5e94be0c7960ff279bb1a4c670074. No full Gate or release approval is implied.

### Independently executed tests

Environment: JAVA_HOME=C:/Program Files/Java/jdk-17.0.7, Python=D:/python/pyenv/pyenv-win/shims/python.bat, PYTHONUTF8=1.

~~~powershell
.\gradlew.bat test --tests '*RocketComponentDefinitionTest' --tests '*RocketComponentRegistryTest' --tests '*RocketStatsValidatorTest' --tests '*ApiArtifactTest' --tests '*ApiVersionsTest' --tests '*RocketDisassemblyLanguageTest' --rerun-tasks --offline --no-daemon --no-build-cache
~~~

Exit 0, measured 43.3446659 seconds, Gradle reported 14 actionable tasks / 14 executed. Raw XML: 37 tests, 0 failure/error/skip across six suites: definition 4, registry 8, stats 4, API artifact 11, API versions 9, language 1. Full output/command/timing and XML are retained as focused-gradle.txt, focused-command.txt, focused-result.json, focused-junit.json and focused-xml/. All 1,191 inventoried source/config files and all four packaged JAR hashes are unchanged before/after (focused-before.json, focused-after.json); final source identity was checked again after root committed.

Python command already independently executed: Python -B -m unittest tests.test_v130_component_smoke -v, exit 0, five tests passed in 0.002 seconds (component-python-initial.txt). Read-only git diff HEAD --check passed. No reviewer-launched Minecraft/GameTest server, client, or remote run occurred.

### Artifact and consumer verification

Read actual immutable copies under C:/Users/Administrator/AppData/Local/Temp/arce-v130-comp01-1790472883078/artifacts/ and compared with source build outputs, publication and consumer reports.

| Artifact | SHA-256 |
|---|---|
| Main | 848b2a28cd6b26bebbdd26c00e1ceb42911827ef004e4d93a47b02b5368128ee |
| API | 487ac76c7e92b44641460700be4889748f3a62d6f853c0e9a2d131039e98eabd |
| Sources | b705a2f5e56e20d6f2d5f961e137778d48db28c8bf190772e61cd30ac547515d |
| Independently built fixture | d8dfdf962a0f9804697503882df294ae769e31a078e5c08faec2beda93b726db |

API contains exactly the sixteen accepted exported class files, byte-identical to the corresponding main-JAR entries. No fixture package/resources are bundled in host/API/sources; host role-tag resources do not contain the fixture's conflicting diamond entries. The three new API source entries match current source bytes. Fixture class entries stay within its own package and contain the expected component definitions and conflicting tags.

Actual local publication has the approved lowercase artifact coordinate and matching raw API. Independently checked all 114 recorded compile-classpath JAR hashes and class inventories: only the mapped API artifact contains host classes, and these are exactly the sixteen exports; no main-JAR/internal output directory is present. All seventeen fixture source hashes match. The mapped-cache .input orig SHA-1 matches the actual published classifier (original cache record retained as consumer-mapped-api.input). Negative compilation recorded exit 1 for the existing internal adapter import, while that class exists in the actual host JAR. Standalone build was not independently re-executed in this stage; root's actual consumer build log reports ten executed tasks and its reobfuscated artifact is the one audited/loaded.

### Independently decoded native evidence

Source: C:/Users/Administrator/AppData/Local/Temp/arce-v130-comp01-1790472883078/runtime-evidence. All 61 manifest-listed files and exact manifest membership verified; manifest SHA-256 47fa430ed8ed7a396e681c0f1dfbd369ca3b59e338fd424cf9041a33c8452377. All 104 prepared library file hashes match the preparation record. Phase launch artifact digests match the audited host/fixture copies.

Raw region/entity Anvil, journal and level.dat were decoded, independently of disk-state.json and the runner's verdict. Existing low-level bounded NBT/Anvil structural readers were reused; semantic assertions and snapshot content-hash recomputation are in the retained Temp-only independent_native_audit.py. Full Forge level.dat uses a local 65,536 tag budget rather than the small SavedData reader's 4,096; 4 MiB expanded-byte and other structural bounds remain enforced, and production parser limits are unchanged.

| Native phase | Mass / thrust / capacity | Saved time | Measured observation ticks |
|---|---|---:|---:|
| assemble | 254 / 2400 / 1500 | 91 | 20 |
| skipped | 254 / 2400 / 1500 | 143 | 21 |
| updated | 294 / 2800 / 2000 | 199 | 21 |
| restart | 294 / 2800 / 2000 | 249 | 20 |

All four use seed 1617306729368065995. Each has one rocket, zero item entities in the observed entity chunk, all five source component positions air, only the assembler BlockEntity at the selected positions, empty assembly journal, zero fuel/debits, one seat, and correct owner/logical binding. Exact component palette/positions/anchors and recomputed snapshot hashes match production assembly receipts. Skipped registration preserves the complete original RocketEntityData and entity UUID. Updated registration first observes the old capture, then actual zero-fuel disassembly restores the five ordinary blocks with no rocket/drops, and actual reassembly creates fresh entity/transaction/snapshot identities and changed totals. Final restart preserves that complete new RocketEntityData and UUID.

All four result files record exit 0, corroborated by normal stopping and final save-completion lines in stdout/latest/debug logs. Each log variant has the exact requested registration selection, fresh phase observations and measured tick queries. No ERROR/FATAL/linkage finding in these logs. Warnings are retained, not suppressed: first-world default configuration creation/correction, Forge language-library missing mods.toml notices, union URL warnings and the intentional offline-server warning. Counts per log variant: 25 in first phase, 10 in each later phase. Configuration bytes are stable between phases except the generated server.properties timestamp; server is loopback/offline. Reviewer did not independently launch or attach to those JVMs.

Audit outputs: native-audit.txt, independent-native-observations.json and final-identity-check.json. Initial audit-helper schema error (treating compile_classpath records as strings) is preserved in native-audit-initial.txt; correcting the helper to inspect the actual file field and all JAR contents yielded exit 0. This was not a product/runtime failure. Read-only exploration also encountered and corrected a nonexistent guessed source filename and a Windows long-path cache-read issue; no source or evidence files were altered.

### Remaining boundaries

This covers the static component contract, finite compilation/packaging checks and four clean same-world server processes. Missing registration is a deliberate fixture switch with its JAR still installed, not actual block-mod removal. Restored ordinary blocks are observed live between operations, not retained as a separate stopped-world checkpoint. Changed-catalog pre-disassembly observation binds old hash/stats/capacity, while full native-data equality is demonstrated for skipped and final restart phases. No fueled travel, client interaction, cross-dimension operation, crash/power-loss, long load, arbitrary provider mod, full migration campaign, visual/multiplayer Gate or complete-version acceptance is claimed.

The initial-stage records below are retained chronologically; their then-pending test/artifact/native work is superseded by the executed checks above.

---

## Initial source-review stage

## Findings
No concrete production correctness defect identified in the reviewed initial implementation.

Coverage limitations, not inferred production failures:
- New tests do not directly create conflicting legacy role tags and compare registered/unregistered resolution.
- The oversized-capacity production GameTest checks source block preservation and no entity, but does not compare the assembly journal before/after.
- Exact fuel-capacity maximum is covered by the pure validator; maximum+1 is exercised through a local production manager/scan.
These are observations for finite follow-up, not demands for an expanded framework or full campaign.

## Reviewed
Accepted ADR-026 (ee7dce7), three new API types, component registry/catalog, queued loading dispatch, manager/assembler/scan injection, metrics precedence and aggregate validator, diagnostic generation, classifier/consumer exports, unit tests, host Forge tests and separate API-only fixture.

Numeric contributions remain static immutable values; registration validates ownership/known non-air blocks, rejects duplicates before publication, closes owner handles and frozen builder, and does not grant movement permissions. Existing loaded/forbidden/movable/adapter checks precede metrics. Capacity rejection is in the scan stats validator before snapshot success/transaction extraction. No snapshot/flight codec recomputation, ID foreign key or protocol change was introduced.

The external fixture uses actual administrative assembly commands and owned zero-fuel interaction. Its FakePlayer command is correctly routed through requestAdminAssembler, so it does not depend on a real PlayerList login. The fixture imports only API/platform classes, not host implementation. The native 16x16x16 rocket_test template was independently parsed to assess the bounded local observation area.

## Actual checks / permissions
Read-only git status/diff HEAD/diff --check (exit 0), numbered source reads and rg. Python -B parsed the existing template and wrote only this unique Temp directory's source inventory/report. Untracked source files were explicitly read; the user-supplied untracked development-doc bundle was not read.

No Gradle/JUnit/GameTest/server was executed by reviewer. Root builds were concurrent; the unfinished packaged runner was explicitly excluded from this stage. Artifact inspection, key-test rerun and native restart audit remain pending root authorization/evidence.

## Recommendation
Proceed to the authorized bounded independent key-test rerun after root releases the build directory. No full version Gate or release approval is implied.

## Runner and incremental source follow-up

No new concrete correctness finding in the completed four-process runner or reviewed incremental production/fixture changes. This supersedes the two initial coverage observations: conflicting role tags are now asserted directly, and the capacity rejection case now rejects a journal entry for the tested origin.

Reviewed exact scopes:
- scripts/run_v130_component_smoke.py:55-100 binds native entity UUID, owner/logical ID, schema, source position, palette/blocks, component totals, seat anchor, empty fuel, exact resource authority and empty assembly journal. Skipped and final restart phases compare complete RocketEntityData. Updated phase requires new transaction/snapshot/hash and new numeric totals after actual disassembly/reassembly.
- Runner:120-215 invokes existing production commands, checks original totals before updated-catalog disassembly, verifies all five restored blocks with no rocket/items before reassembly, observes at least 20 ticks per process, flushes then cleanly stops. Fixed coordinates and clear shell remain wholly in chunk 16,16; readiness is sufficient for this single-chunk layout.
- Existing input/process helpers were inspected: new disjoint disposable outputs, linked-path rejection, exact artifact/mod identities, bounded reads/startup/commands/termination and cleanup of the owned process. Saved file capture occurs only after process completion; no NBT/save mutation or verdict-only reconstruction is performed. Config equality ignores only the specifically validated generated Java timestamp, preserving raw files.
- Host RocketComponentGameTests:94-107 now checks actual fallback conflict and explicit precedence; 149-151 checks no journal at the rejected origin. Fixture tags supply the deliberately conflicting diamond block categories only in the test mod.
- RocketDisassemblyLanguageTest now keeps exact key-set checking and adds only the new capacity diagnostic; both generated locale files match this inventory and active provider output.

Actual independently executed command:
~~~
$env:PYTHONUTF8='1'
& 'D:/python/pyenv/pyenv-win/shims/python.bat' -B -m unittest tests.test_v130_component_smoke -v
~~~
Exit 0; 5 tests passed, reported elapsed 0.002 seconds. Raw output: component-python-initial.txt. Source hashes: runner-review-identities.json. A read-only rg invocation using a Windows-invalid glob path returned an IO error; corrected explicit-file and -g searches supplied the requested source and did not affect validation.

Remaining evidence boundaries:
- This independently executes pure oracle tests only. No Gradle, Forge/packaged process, or native runtime capture was run/audited in this follow-up; those remain pending authorization/evidence.
- The runner exercises static numeric snapshots, no fuel or player movement, an omitted component registration with the same installed fixture, and changed-definition reassembly. It does not demonstrate block-mod uninstall, flight, crash cuts, arbitrary dataloaders, or full release acceptance.
- Omitted/final restart comparison binds complete RocketEntityData, not every generic Entity root field; updated-catalog pre-disassembly observation directly binds old content hash/stats/capacity, not a native old-catalog checkpoint before reassembly.
