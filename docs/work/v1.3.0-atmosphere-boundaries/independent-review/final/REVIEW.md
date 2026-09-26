# V130-ATM-01 independent review and verification

## Findings

No unresolved concrete finding in the final reviewed scope.

The earlier medium test-isolation finding is corrected: the API-only fixture no longer uses persistent forceload state or removes another fixture's tickets. Its BlockPos-owned region ticket expires at 140 ticks against the unchanged 120-tick test window. moon.resetEmptyTime() makes the existing unoccupied dimension tick during that window; it neither expands timeout nor changes production services. The earlier startup-only registration-count observation gap is also closed: runner lines 189-190 validate complete-process registration logs after clean stop.

Registry ownership/thread/freeze/reentrancy, atomic compiled-state publication, numerical cardinality/timing limits, callback-free lookup, legacy guard/priority preservation, synchronous affected-scan cancellation, reload-wide server-thread invalidation, and common-setup-before-service initialization were reviewed against ADR-024. No new save schema or packet is introduced. Same-JVM callbacks remain non-sandboxed and timing checks are after-return only, as documented.

## Exact reviewed source

Started against e62346d768284d75581a3c84ca64f50d73f3ff7b plus the actual implementation working tree. Root committed the same bytes during execution; ending HEAD 1e1bd4658b40f354096067ea0275d4aa48b27478 (production 3ba450c, runner/tests 1e1bd46). All 1139 captured source/build/test files remained byte-identical before and after independent execution. Tracked source is clean at the final inspection; remaining root changes are documentation. See before.json, after.json, tracked-diff.patch and verification.json.

## Independently executed commands

Environment: JAVA_HOME=C:/Program Files/Java/jdk-17.0.7; PYTHONUTF8=1; cwd D:/GitHub/AdvancedRocketry-Community.

    .\gradlew.bat test --tests *AtmosphereBoundaryRegistryTest --tests *ApiArtifactTest --tests *ApiVersionsTest --tests *VolumeScanCoordinatorTest --rerun-tasks --offline --no-daemon --no-build-cache

Exit 0, 53.795 seconds wall time; all 14 Gradle tasks executed. Actual XML: **37 tests, 0 failures, 0 errors, 0 skipped**:
- AtmosphereBoundaryRegistryTest: 13.
- ApiArtifactTest: 9.
- ApiVersionsTest: 9.
- VolumeScanCoordinatorTest: 6.

    D:\python\pyenv\pyenv-win\shims\python.bat -B -m unittest tests.test_v130_atmosphere_boundary_smoke -v

Exit 0, **5/5 tests**, 0.001 s unittest time (0.711 s process wall time).

git diff --check: passed. No independent GameTest server or packaged Java process was launched.

## Artifact verification

All four artifacts remained unchanged by the independent focused rerun:
- advancedrocketry-community-1.20.1-1.3.0-dev-api.jar: 5ac0cde39396e97e7b95fa41c44b563e79edf574b3901624eec7a31482359436.
- advancedrocketry-community-1.20.1-1.3.0-dev-sources.jar: b9de91b1b33a4a8894eb8a431f8a5b14358832a16cd4adda0003afd0dd79f1ba.
- advancedrocketry-community-1.20.1-1.3.0-dev.jar: 9f93fc3e60a2af02c9b506fa372c2bce47db0fcb6ea0bbd1c25d1e4a70d4eb1d.
- arce-adapter-compat-test-1.0.0.jar: b8178c4cda9890cb6daeb7d248e9ee2fef2363cb1597a66a640b482164747593.

Actual ZIP inspection confirms exactly ten exported API classes, each byte-identical to the corresponding main-JAR entry, and no fixture package/resources in the main JAR. Executed ApiArtifactTest also verifies isolated consumer compilation and rejection of the existing internal atmosphere registry.

## Independent existing-runtime readback

Input: C:/Users/Administrator/AppData/Local/Temp/arce-v130-atm01-1790444703294/runtime-evidence.
The reviewer did not start these four servers. Their existing native evidence was independently inspected after termination.

Executed the Temp-only native_postcheck.py: exit 0, 1.589 seconds. Verified all **55 SHA256SUMS entries**, with complete file-set equality; manifest SHA256 604034da9cc05ca7ab0e4ebc76e68294bf236523272615c7f0ee076606938829. Actual immutable and installed JAR digests/mod metadata match the launch receipts and independently verified artifacts above.

The supplemental checker reads actual Anvil chunks and gzip level.dat, rather than accepting disk-state.json. It reuses bounded low-level NBT/region decoding, but implements independent block-palette, fixture, resource, event, log, provenance and result assertions. For full level.dat only, finite parser limits are 4 MiB expanded, depth 64, 200,000 tags and 100,000 collection entries (not the smaller celestial SavedData profile).

| Phase | O2 | FE | O2 phase | Saved vent lit | Recorded exit | Observation |
|---|---:|---:|---:|---|---:|---:|
| setup-reload | 2997 | 38600 | 0 | false | 0 | 22 ticks |
| open-restart | 2997 | 38600 | 0 | false | 0 | 22 ticks |
| provider-skipped | 2996 | 37920 | 14 | true | 0 | 22 ticks |
| provider-restored | 2996 | 37920 | 0 | false | 0 | 22 ticks |

- All four captures retain the exact open custom BlockState and one native vent at (264,220,264). Independently checked all 150 room/shell cells in each chunk (16,16).
- Actual level.dat retains the same WorldGenSettings/seed, increasing game time, the fixture mod and the custom datapack. The final retained world marker matches its recorded hash.
- Raw setup log contains closed/open/reclosed/replaced transitions, two actual reload messages and accepted generations 2/3, then sealing-tag override and restored-rule transitions in order. There is one startup registration event over the complete process, not an additional event on reload.
- Restart commands do not rebuild the room or top up resources. The registration-skipped process contains the explicit JVM property and skipped event; fixture JAR/block remains installed. This is provider absence, not mod uninstall.
- The skipped cycle consumes 680 FE and one O2 between stopped snapshots; inactive restart/restoration preserves FE/O2 and resets the existing inactive O2 phase to zero. This is positive finite supply/resource persistence evidence, not an assertion that the two sampled 22-tick endpoints cover every active tick.
- Each raw stdout/debug/latest log independently has zero ERROR/FATAL, linkage failures and project-logger WARN. Non-project warnings remain: stdout has 26/11/11/11 WARN-containing lines including the terminal-library warning; structured debug/latest counts are 25/10/10/10.
- Native save/stop messages and recorded process exit 0 appear for every phase. Loopback port 50881 was independently observed closed.

## Limits and disposition

Scoped implementation, focused tests and these four existing native captures support the ATM-01 slice. This is not full v1.3 Required Gate approval.

Not independently rerun: root's entire 688-JUnit/150-Required-GameTest suites, DataGen, complete consumer build, repository/planning validators, or the four packaged Java launches themselves. This review does independently run the selected 37 JUnit and 5 Python tests, inspect actual classifier bytes, and reread the existing packaged raw evidence.

No real player/client, physical-client rendering, provider-uninstall world behavior, arbitrary callbacks, arbitrary rooms/chunk topology, long load, multiplayer or equipment API acceptance is claimed. The packaged fixture observes eventual production vent LIT outcomes; synchronous authority revocation is supported by source review and focused scanner tests, with its level-service GameTest execution owned by root.

Repository source/docs were not edited. Allowed writes were Gradle-generated outputs and this Temp review directory. Full logs, four XML files, commands/timing, source/artifact manifests, native checker source and raw-derived observations are retained here.
