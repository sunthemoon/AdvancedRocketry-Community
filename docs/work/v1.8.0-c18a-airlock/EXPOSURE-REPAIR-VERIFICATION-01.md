# C18a atmosphere exposure repair verification

Date: 2026-10-08. Status: verified development defect slice; v1.8 remains
IN_PROGRESS / IMPLEMENTING. This record does not approve any Required Gate or
deliver the full airlock, life-support or classic-content scope.

## Completed scope and identity

The server adapter no longer treats full SKY brightness as proof of an open
vacuum boundary. Native `canSeeSky` reads light, which can pass through glass or
lag a roof edit. The existing loaded-chunk heightmap and bounded geometric scan
remain. There is no new traversal, chunk ticket, client authority or scan budget.
Door, tags, external providers, fluids and collision classification are unchanged.

Four required native regressions exercise transparent enclosure with native
full skylight, immediate opaque-roof closure, immediate roof/side openings and
disabled exposed-sky policy. Each fixture owns at most 27 loaded, BE-free cells
and restores prior states on its terminal callback. It does not mutate the
light engine. Timeout/failure cleanup branches were reviewed, not separately
failure-injected. Original airlock cases, assertions, deadlines and budgets
are byte-identical to the base.

| Identity | Commit |
|---|---|
| Base | `4bc59ed692deef5ec48c6e0756fd162aed8653dc` |
| New regressions without repair | `369fc1c6907f875d90c26aaddbe49cb516712100` |
| Actual repaired/tested source | `23bcb6b141da88a7a9548c2aa8f3de5438a09e68` |
| Integrated, non-force pushed source | `ee77c51aef7613753a85a156a84cd817adfb2529` |

Complete source tree `cc2d92e033bd3952f7fda703f1e096be0e883908` and seven Gradle
inputs are identical between tested and integrated commits. The seven are
build.gradle, settings.gradle, gradle.properties, both wrappers and both
gradle/wrapper files. Equality is not a rerun of the merge commit.

No registry, stable ID, network, schema, persistence policy, asset or upstream
copy is introduced. Rollback needs only a source revert; no player-data or ID
migration is required. User AGENTS.md and inherited untracked records are
excluded from the implementation and evidence commits. No Claude is called.

## Actual commands and results

Java 17.0.7, Gradle 8.8, Forge 47.4.10, task-local TEMP/TMP/java.io.tmpdir and
offline caches were used. Both drives exceeded 10 GiB before build/server runs.
Root and a separate read-only Codex reviewer ran isolated pinned checkouts.

| Command | Root | Independent Codex |
|---|---|---|
| `gradlew.bat clean build --no-daemon --offline` | exit 0 | exit 0 |
| Actual JUnit XML | 378 suites, 2,179 testcase nodes, 0 F/E/S | same counts |
| `gradlew.bat runGameTestServer --no-daemon --offline` | exit 0; all 546 required tests pass | exit 0; all 546 required tests pass |
| `gradlew.bat runData --no-daemon --offline`, twice | exits 0 / 0 | exits 0 / 0 |
| `git diff --exit-code`, after each DataGen | exits 0 / 0, empty | exits 0 / 0, empty |
| `python -B scripts/validate_v180_content_ledger.py --require-accepted` | exit 0 | exit 0 |
| `python -B scripts/validate_bootstrap_provenance.py` | exit 0 | exit 0 |
| `python -B scripts/validate_repository.py --require-approved-identity` | exit 1; 44 passed / 1 inherited-link category failed | same result |
| `git diff --check` | exit 0 | exit 0 |

At the regression-only commit Root's full native run exits 1: 546 tests
complete, exactly two required failures, both new enclosure regressions.
They return OPEN after one inspection where SEALED is asserted. The other
new regressions and original airlock batch have no terminal failure in that
cohort. This is retained red evidence, not an airlock reproduction claim.
Repaired logs each include all four new cases and all seven unchanged airlock
cases, 62 ERROR headers and zero FATAL. The ERROR records remain unwaived;
test success is not an error-free native log or production save-policy approval.

Initial Root and reviewer CMD launchers fail before Java because a Windows
executable path used forward slashes. Both failed cohorts are retained; separate
corrected invocations produce the results above. Root's initial terminal-line
collector missed uppercase native markers. Original results are unchanged;
the separate audit reads complete raw logs and derives their terminal counts.
The original runner overall exit 1 reflects strict validation, not native
failure. Full commands, exit codes, times and raw logs are inside the archives.

Independent actual-diff review reports no introduced Critical/High/Medium/Low
finding. Root directly inspected the final report/results and verified every
sealed payload hash, archived JUnit count and native terminal before recording
this result. The review does not independently rerun the pre-repair negatives.

## Portable evidence

The new archives contain only the original sealed retained payloads plus their
manifests, not full source trees, build products, temporary directories or worlds.
Every copied payload remains byte-identical. Each ZIP is below 1 MB; both
uncompressed retained packets together are below 14 MB and the 100 MB budget.

- [Root packet](EXPOSURE-REPAIR-ROOT-01.zip): 35 entries, 704,217 ZIP bytes.
  SHA-256 `f23f64a9a050e1ff5e109f8f4968906c8b12be6239f0e9d855816e557bd2902f`.
  Inner manifest SHA-256 `e838c4f30015bd5bf48913454951b6622821608e227f83a1374d8f05c7f0840d`;
  REPORT.md SHA-256 `fae16dc5e442ea5df75109538172cb0f70d0e15d0574d8a08747ef47ffe5e583`.
- [Independent packet](EXPOSURE-REPAIR-INDEPENDENT-01.zip): 54 entries, 675,944 ZIP bytes.
  SHA-256 `175224c0309c16f33eb669c5078185b9de9637f11fc9c139c0330c12c0430a85`.
  Inner manifest SHA-256 `5ad5700ac65293e316ecd7ba797283eb7988b263dee2070ed9ebb8e86b3dee8b`;
  REPORT.md SHA-256 `a26ce323e65e602ddbdafd4d2893d137c629e622dd7e118b374ca7e2d37b43e1`.

Original local packet locations, kept without modification:

```text
D:/GitHub/ARCE-Task-Evidence/v1.8.0/atmosphere-exposure-root-20261008-01
D:/GitHub/ARCE-Task-Evidence/v1.8.0/atmosphere-exposure-independent-20261008-01
```

Both builds emit the same development main JAR SHA-256
`d87c90e394c13b54382d6496f2c15709b7df06444dad9e8321e92d6b10b9cea1`
and API JAR SHA-256
`aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b`.
They are not a packaged S1 or frozen release candidate.

## Open obligations and risks

Both new cleanup commands were rejected before execution. There is no retry,
alternate-tool deletion or claimed reclaimed space. Read-only inventories
record Root's own 271,728,172 logical bytes in build/local .gradle/run-data,
plus retained task TEMP/source, and the reviewer's 1,355,597,504 bytes in owned
clone/build/server/temp outputs. These disposable directories are outside the
portable retained packets. The review allocation still exceeds the evidence
directory budget until its cleanup is permitted; this limitation is not waived.

Historical required airlock failures are not individually traced at scan time.
The unchanged installed-airlock test also passes in the new pre-repair cohort.
The defect is independently reproduced, but its repair does not prove a unique
cause or permanent removal of every historical timing failure. Old failed
packets remain failed and immutable.

Strict inherited-link failure, all unwaived ERROR records, R-021, physical
hatch/save qualification, packaged/restart/crash recovery, prior-world
migration, natural installed continuation, V1/V2, performance, progression and
license/content closure remain open. The content ledger is unchanged at
186 PLANNED /154 REVIEW assets. No new survival/client manual execution is
claimed. Continue the [current C16-C19 plan](../../status/COMPLETION-PLAN.md)
and airlock acceptance, not v1.9 implementation. Full v1.8 Required Gates
are not satisfied and no version, ADR, ledger delivery, tag or release is approved.
