# V130-ATM-02 — External suit equipment and oxygen

## Scope and identity

Development baseline: `0257163`, branch `codex/v1.3.0-public-api`; equipment
contract frozen in `f13c613` under [ADR-025](../../decisions/ADR-025-SUIT-OXYGEN-PROVIDERS.md).
Production and regression commit: `4b95d23`; external fixture/runner commit:
`74def6e`. Root is the sole tracked-source writer; independent review uses a separate session
and Temp-only reports. The user-supplied documentation bundle remains unmodified.

API **1.3** adds `SuitOxygenProvider`, `SuitEquipmentRegistrar` and
`RegisterSuitEquipmentEvent`; the complete classifier contains thirteen explicitly
exported classes. The API-only integration registers vanilla leather armor with
its own oxygen payload, without host internal imports or copied visual assets.

Implemented scope:

- Owner/thread-bound atomic registration, fixed item/slot mappings and closed
  loading handles; bounded immutable catalog, reserved built-in suits.
- Detached bounded oxygen callbacks, item-local unsupported data, provider-session
  fault isolation, exact readback and host-only resource commit.
- Real player life support, mixed suit pieces, existing vacuum cadence and
  whole-canister use. Failed debit recomputes exposure at the original phase.
- Native external ItemStack envelope, missing/future/malformed preservation and
  unchanged built-in suit schema, capacity and S2C display protocol.
- Independent consumer fixture, finite Forge player tests and packaged restart
  runner with native saved chest/oxygen/canister readback.

No generic capability/fluid/energy adapter, variable capacity, inventory tank
search, new hazard, old-suit migration, new packet, later-version content or
release approval is introduced.

## Tests and commands actually executed

Local environment: Windows, Java 17.0.7, Forge 47.4.10, repository Gradle wrapper;
Python from `D:/python/pyenv/pyenv-win/shims/python.bat` with `PYTHONUTF8=1` and `-B`.
Command logs and generated reports are retained separately from narrative results.
The [raw build archive](build-evidence.zip) contains original command output,
final GameTest logs, consumer provenance reports and the implementation review diff.
[Full JUnit XML](junit-full.zip) was captured before the independent focused rerun
replaced build-directory reports. Readable copies normalize line endings/trailing
whitespace and make repository links relative; archives retain original bytes.

| Command | Observed result |
|---|---|
| `gradlew.bat compileJava compileAdapterTestJava --console=plain` | Exit 0; initial integration compile, 17 s |
| `gradlew.bat test --tests '*SuitEquipmentRegistryTest' --tests '*SuitOxygenAccessTest' --tests '*ApiArtifactTest' --tests '*ApiVersionsTest' --console=plain` | Initial run: 40 tests, one failed version fixture; see correction below |
| `gradlew.bat clean build --console=plain` | Exit 0; 710 JUnit, 135 suites, no failures/errors/skips; 45 s |
| `gradlew.bat runGameTestServer --console=plain` | Initial integration: exit 0, all 160 Required GameTests; 112 s |
| `gradlew.bat clean build runData publishMavenJavaPublicationToLocalProjectRepositoryRepository --console=plain` | Final production bytes: exit 0; 710 JUnit, unchanged generated resources, local publication; 50 s |
| `gradlew.bat -p compat-test-mod clean build --offline --no-daemon --no-build-cache --console=plain` | Exit 0; all ten tasks executed, thirteen-class API provenance/isolation and reobfuscated fixture verified; 18 s |
| `gradlew.bat runGameTestServer --console=plain` | Final production bytes: exit 0, all 160 Required GameTests; 107 s |
| `python -B -m unittest discover -s tests -p 'test_v130*.py' -v` | Exit 0; 54 checks |
| `python -B scripts/validate_v1plus_planning.py` | Exit 0; 11 version plans, 33-input source inventory |
| `python -B scripts/validate_repository.py --require-approved-identity` | Exit 0; 45 checks passed, 1,324 Markdown links checked at implementation staging |
| `git diff --exit-code -- src/generated`; `git diff --check`; staged diff check | Exit 0; no generated changes or whitespace failures |

The only initial JUnit failure was the isolated metadata consumer still asserting
exact API minor 1.2. It was updated to the now-frozen 1.3, not made permissive; its
original XML/log is retained. Pure registry/oxygen tests passed that first run.
See [initial failure evidence](failed-fixture-evidence.zip).
No assertion was weakened, timeout extended or resource budget increased.

Before final validation, self-review made client chest prediction read only
immutable metadata rather than the server's disabled-provider set, and guarded
life-support cleanup before mutating state. Test expectations for a rejected new
armor payload inspect absence of the owned root, not absence of vanilla `Damage`.

New coverage: 8 registry tests, 13 payload/failure tests, one isolated API consumer
test, 8 host Forge equipment scenarios, 2 external fixture GameTests and 5 Python
runner-oracle tests. Existing built-in suit, packet and room tests remain enabled.
Expected fault-injection diagnostics (including existing Precision migration tests)
appear in GameTest logs; they are not silently ignored production server failures.

## Short packaged verification

The bounded runner passed four clean
local JVMs, the same normal host/independent fixture JARs and one disposable world.
It enables only the fixture's console test commands, not host release-test hooks.
Each phase dispatches twenty production LivingTick events for an unconnected
FakePlayer, stores the authoritative chest item and counts in a vanilla chest,
then saves and stops. Auxiliary helmet/leggings/boots are fixture setup each time.

Actual invocation (absolute input/output paths and Java executable are retained
in the [runtime summary](runtime-summary.json) and archived launch records):

```text
python -B scripts/run_v130_suit_equipment_smoke.py <libraries-only-server>
  --host-jar <host.jar> --fixture-jar <fixture.jar> --evidence-dir <new-evidence>
  --java <jdk-17.0.7/bin/java.exe> --accept-eula
```

Exit 0 on the first packaged attempt. Total phase time: 80.12 seconds. Each phase
adds twenty explicit player events plus the independent server observation window:

| Phase | Seconds | Server observation ticks | Saved chest oxygen | Full / empty canisters |
|---|---:|---:|---:|---:|
| setup | 27.58 | 21 | 999 | 1 / 1 |
| restart | 17.47 | 21 | 998 | 1 / 1 |
| provider skipped | 17.62 | 23 | 998 | 1 / 1 |
| provider restored | 17.44 | 24 | 1,997 | 0 / 2 |

The restored phase consumes one unit and then uses the remaining whole canister.
At every saved phase, chest units + 1,000 times full canisters + cumulative consumed
units equals the initial 2,000; full + empty canisters stays two. The skipped
phase preserves the entire saved chest inventory exactly, refuses refill and the
fixture observes one vacuum-damage interval. Native item identity, schema,
provider/version, vanilla `Damage` and unrelated marker remain intact.

[Raw runtime evidence](runtime-evidence.zip) contains 54 files including its
53-entry manifest: commands, launch/status/configuration, stdout/native logs,
results, four raw Anvil/level.dat captures and checksums. [Decoded observations](runtime-disk-states.json)
are separate convenience views, not a replacement for those raw saves. All four
JVMs exited 0; raw logs have zero ERROR/FATAL/client-linkage/project-warning counts.
The disposable local port was 60520 and is closed after shutdown. Forge setup
warnings remain recorded rather than counted as project failures.

The [104 library-file preparation checks](libraries-preparation.json) reuse only
the prior local Forge libraries, never a prior world. [Artifact identities](artifact-identities.json)
bind normal host, classifier, sources and independent fixture JARs; no JAR is
published/tagged or committed as a new release artifact here.

This is native **chest ItemStack and canister quantity** persistence evidence, not
all-four-armor identity, actual connected-player save/death, real-time player
cadence, networking/HUD, arbitrary mod uninstall or power-loss evidence.

## Independent review and remaining acceptance

Contract review introduced item-local `OptionalInt.empty()`, disabled mapping
semantics, wearable-item responsibility and bounds before mutated-input equality.
It also qualified old-host preservation with loader/binary compatibility.
Source review clarified the limited identity claim in the packaged fixture.
Independent rerun executed all 14 Gradle tasks with `--rerun-tasks --offline
--no-daemon --no-build-cache`: **43 JUnit tests passed** (registry 8, access 13,
legacy oxygen 3, artifact 10, version 9), plus **5 Python runner checks**. The
reviewer confirmed 1,173 source/support hashes and all four immutable JAR hashes
unchanged, exactly thirteen API exports byte-identical to the normal host, and no
fixture classes in the three host artifacts.

Independent native audit verified all 53 runtime-manifest entries and separately
decoded the raw Anvil and level.dat captures. It confirmed the resource table,
unchanged skipped inventory, identical WorldGenSettings/seed, saved times
47/94/142/192, startup registration selections, clean save/stop and zero raw
ERROR/FATAL/linkage/provider-disable observations. The thirteen independently
compiled consumer source hashes also match the implementation.

The first reviewer-only native decoder used the legacy celestial SavedData
parser's 4,096-tag limit for vanilla level.dat and rejected that larger file.
Its temporary full-world metadata reader uses 65,536 tags only for level.dat,
retaining 4 MiB/depth/string/collection bounds and restoring the original limit
before other reads. A supplemental consumer-report lookup also initially used
the wrong archive directory; the actual nested directory was then read. Both
diagnostics are retained. No production/parser source,
game assertion, payload budget or timeout was changed for that correction.
See [independent final review](independent-review/final/REVIEW.md) and
[raw rerun/audit archive](independent-rerun.zip), including exact commands, focused
XML, source/artifact inventories, raw-NBT helper and its initial diagnostic.
No unresolved finding remains in this bounded equipment slice.
The final restored 1,997-unit chest is inspected after clean stop, not loaded by
a fifth process; earlier restart phases cover the persisted intermediate states.
The first final-document link check rejected absolute local links in the copied
review. Only the readable copy now uses repository-relative links; the original
review and its SHA-256 remain intact inside the raw archive.

Same-JVM provider purity and returning within a budget are integration obligations,
not a sandbox or preemption guarantee. Invalid/future/absent data is retained but
cannot supply oxygen. Removing the item/integration mod can still invoke Forge's
missing-registry or binary-loading behavior. Capacity stays fixed at 2,000 units.

Manual follow-up after original machine/dimension completion: on a real client,
equip the independent leather suit, refill on Earth, visit vacuum, observe the
existing oxygen HUD and partial/empty feedback; repeat reconnect and missing-
provider behavior with two real clients. Those V1/V2, full migration, remote,
power-loss and long-load scenarios have **not** been passed by this work.

v1.3 remains `IN_PROGRESS`. G0–G9 and inherited Gates are not all satisfied;
there is no candidate, release, tag or human Gate approval. Remaining v1.3 work
is fuel/engine/component, environment/body-context, satellite extensions and
the rest of compatibility/acceptance coverage.
