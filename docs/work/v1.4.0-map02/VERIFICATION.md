# V140-MAP-02 — planetary worlds and physical travel

Date: 2026-09-27. Development slice only; no candidate, tag or Gate approval.

## Scope and identities

- Branch: `codex/v1.4.0-planetary-expansion`.
- Base: `d41e42fb2118a060a7df4c0480e0887079e0d38e`.
- Java 17.0.7, Forge 47.4.10, build `1.20.1-1.4.0-dev`.
- [ADR-033](../../decisions/ADR-033-PLANETARY-WORLDS-AND-SURFACE-ADMISSION.md)
  records the recommended design under standing maintainer authorization,
  following independent draft review. It is not a new numbered manual approval.
- Root alone writes tracked files. Reviewers use read-only source access and
  separate Temp reports, with explicit exclusive Gradle execution periods.
  The untracked user documentation bundle is excluded.

The slice adds two startup datapack worlds, Mars and Venus, with distinct
authored noise/surface/biome rules, three schema-2 definitions, five routes and
five English/Chinese labels. The gas giant has an orbit anchor but no physical
surface. Twenty additive generated files leave old generated resources intact.
No upstream art, resource JSON or implementation is copied; provenance is in
[development metadata](../../provenance/v1.4.0-development-metadata.md).

Quotes and launches now check actual source Level, saved body/Level/typed
target, snapshot dimension and committed station UUID/region/orbit. Only a
mapped surface or committed station is a physical source. The established
station `space` body alias remains valid; arbitrary mismatches do not.
Destination Levels must already exist; quotes never scan terrain. Player
station creation resolves the actual mapped surface rather than Earth/Moon.

The surface selector retains eight candidates, 16 footprint chunks per
candidate and 2048-column/block limits. New surfaces also inspect at most 2048
bottom-block supports per candidate, require at least one sturdy support and
reject fluid beneath any bottom block. Border/height/occupancy still apply.
Selection and pre-spawn availability share these checks. A changed support
returns the source with its original fuel ledger, under the existing transfer
policy. Earth/Moon/Space compatibility, public API 1.7, display protocol 2 and
rocket/station/binding save schemas are unchanged. The reverse-binding error
now names both IDs without claiming a sorted new alias was the historical owner.

## Commands actually executed

Working directory is the repository root; `JAVA_HOME=C:/Program Files/Java/jdk-17.0.7`.

```text
gradlew.bat test --tests '*PlanetaryBindingsTest' --offline --no-daemon --console=plain
gradlew.bat runData --offline --no-daemon --console=plain
gradlew.bat test --tests '*Planetary*' runGameTestServer --offline --no-daemon --console=plain
gradlew.bat runGameTestServer --offline --no-daemon --console=plain
gradlew.bat runGameTestServer --offline --no-daemon --console=plain
gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
gradlew.bat build --offline --no-daemon --console=plain
```

The diagnostic-only command passed 18 tests in 25 seconds. Initial DataGen
created 20 files in 29 seconds. The first combined targeted run passed 45 JUnit
tests but failed two of 216 required GameTests. The next two standalone
GameTest runs each failed one test; see the retained dispositions below.
The full clean/build/DataGen/GameTest command then exited **0** in **185 seconds**:
**840 JUnit**, zero failures/errors; **220 required GameTests passed**. Repeat
DataGen wrote zero files. A final indentation-only cleanup was rebuilt with
`build`, exit **0**, **17 seconds**, before freezing the artifacts used below.

Root Python uses `PYTHONUTF8=1` and `D:/python/pyenv/pyenv-win/shims/python.bat -B`:

```text
python -B -m unittest tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
```

Final root run: **24 tests passed**, **0.449 seconds**, exit **0**. Earlier
diagnostic runs and their test counts remain separate in the archive.

`python -B scripts/validate_repository.py --require-approved-identity` exited
**0**, with **45 passed / 0 pending / 0 warnings / 0 failed**. `git diff --check`,
`git diff --cached --check` and the staged-worktree `git diff --exit-code`
checkpoint exited **0**. Later document/evidence additions receive their own
final staged-byte, ZIP/manifest and local-link checks; they do not alter runtime
inputs or retroactively approve the version Gates.

The seven new GameTests exercise actual terrain at four columns per planet,
Earth/Mars/Venus/Earth transfer, player station creation/limits, Space source
rejection, repeated quotes without new loaded chunks, invalid physical sources,
missing destination Level, support-loss return and synchronous fluid rejection.
New JUnit data/binding checks cover packaged definitions/routes, additive
bindings, ID collisions, ambiguous mapping and the corrected diagnostic.

## Packaged upgrade, travel and restart

The final harness invocation used a fresh libraries-only local server:

```text
python -B scripts/run_v140_planetary_worlds_smoke.py <server-native-c>
  --baseline-jar <MAP01-artifacts>/advancedrocketry-community-1.20.1-1.4.0-dev.jar
  --host-jar <MAP02-artifacts>/advancedrocketry-community-1.20.1-1.4.0-dev.jar
  --evidence-dir <native-c> --java <Java17>/bin/java.exe --accept-eula
```

The exact absolute command inputs and JVM arguments are archived. No SSH,
external server, real player, online-authentication experiment or load campaign
was used. The harness binds only a local empty offline-mode server and uses
property-gated operator fueling/launch; this does not certify player fuel-loader
or real-client gameplay.

1. The preceding MAP-01 artifact creates a real world, Moon station and fueled
   six-block rocket carrying 17 diamonds and 64 iron ingots. Mars, Venus and gas
   Levels are absent. The server stops cleanly; its complete world backup is kept.
2. The new artifact starts the **same** world. Old rocket NBT and station record
   agree with the prior process; three bindings append without remapping. Mars
   and Venus are started Levels; gas remains absent. Operator creation records
   exact new Mars/gas station UUIDs, owners and orbit IDs. Gas surface launch
   rejects unchanged. Three real legs complete: fuel **2000 → 1552 → 1019 → 504**,
   debits **448, 533, 515**, one logical rocket and unchanged six-block cargo.
3. A clean restart retains identical rocket authority, all three committed
   stations, binding data and final COMMITTED transfer authority. The immutable
   payload and exactly-once fuel ledger match native saved data.

Harness and all three JVM exits are **0**. Native logs retain **25/10/10** warnings
and zero ERROR/FATAL/project warnings; no client-linkage failure. Initial Forge
config defaults and offline-mode warnings are not relabeled as a warning-free
run. Artifact/schema/command/entity/transfer/station/binding/configuration/Level
inputs are retained. The active properties comparison excludes only Java's
generated timestamp line, not other settings.

Native entity regions and transfer/station/binding files are decoded and
compared; the six vacated block positions and nearby drops are checked by live
commands, **not** an independent persisted block-palette/BlockEntity scan. Raw
`level.dat` and the harness identity marker demonstrate this controlled upgrade
and restart; they are not a whole-world byte identity or complete historical
upgrade proof. New generator data applies only to new chunks.

## Failures and review corrections retained

- Initial GameTest failure: the old celestial command test expected three
  packaged bodies; its exact oracle now expects six, while Moon/Earth travel
  assertions remain. The unrelated adapter fault test detected pre-existing
  pointed-dripstone item entities; a diagnostic rerun recorded their item,
  position and age. Fixture setup now records/removes only items present before
  operations. All subsequent zero-drop assertions remain intact. A later run
  observed those same old drops before the adapter request.
- Independent source review found missing pre-spawn support recheck (P2) and
  water-fixture spread beyond owned cells (P3). Availability now includes support;
  AIR remains an actual transfer regression, WATER is checked/restored in one
  callback before fluid ticks. Owned cleanup does not clear unrelated records.
- The new support regression initially expected a destination debit on rejected
  spawn. Existing source-transit authority and the old blocked-pad test require
  unchanged source fuel. The regression now compares the complete original fuel
  ledger; no production fuel policy or test timeout/budget was changed.
- Native attempt **a** stopped its baseline JVM with exit 0 but failed the new
  reader on legitimate zero-byte entity-region handles. Attempt **b** completed
  baseline and three upgraded-world flights, then failed because empty handles
  were counted against the material-region budget. Neither is called a passing
  run. Final reader keeps all raw empty handles, rejects nonzero partial headers,
  requires one native rocket and caps material regions at **16**, empty handles
  at **32**, decoded chunks at **128**. The decoded-storage/production budgets
  were not increased; separate regression cases reject 17 material/33 empty files.
- Independent runner review required exact owner/body/target/position and full
  transfer bindings, mandatory landed journal, and station UUID/owner/orbit
  receipts. Positive and mutated-authority tests cover the resulting checks.
- Final staged-resource inventory initially found four DataGen cache files
  alongside the 20 resources. The v1.4 cache root now has the same ignore rule
  as previous roots, and only those newly staged cache entries were unstaged.
  Packaged content already excluded `.cache`; no artifact/runtime bytes changed.

## Independent verification

Independent contract, source and runner reports preserve original findings and
their dispositions. The final independent commands were:

```text
gradlew.bat test --tests '*Planetary*' --tests '*RocketTargetFlightPlanner*'
  --tests '*LegacyFlightTargetMigrator*' --tests '*BodyContextResolver*'
  --rerun-tasks --offline --no-daemon --no-build-cache --console=plain
gradlew.bat runGameTestServer --offline --no-daemon --no-build-cache --console=plain
python -B -m unittest tests.test_v140_celestial_schema_smoke tests.test_v140_planetary_worlds_smoke -v
```

All exited **0**: **61 JUnit / 7 suites**, no failures/errors/skips, **44.294 s**,
all 16 tasks executed; **220 required GameTests**, **102.604 s**, reusing the
existing test world without `clean`; **24 Python tests**. All **1284** inspected
source/build inputs and all three JAR identities were unchanged before/after.
Existing intentional GameTest fault-injection ERROR logs remain in the archive.
The independent GameTest run retains a 2322 ms / 46-tick lag warning and 13
intentional Precision-fault ERROR lines; it is not performance acceptance.
Final independent report SHA256:
`356f14cb94373c4febb86d7f21bc8238a4aab7c37f35a5ac501949c00f464fb8`.
Its 36-member manifest is preserved inside the review archive.

Independent raw readback verified **16/90/145** manifest members for native
attempts a/b/c. The final artifact/command/log/native-entity/transfer/station/
binding/configuration observations agree. An additional reviewer helper's
whole-captured-region ItemEntity equality assertion failed because unrelated
background seeds/flowers moved or merged far from the six-block fixture. Its
failure and subsequent raw observations are retained; no cargo item was found.
Neither the slice nor the corrected scoped review asserts whole-world item
immutability. No unresolved concrete production finding remains in the reviewed
scope; this is independent development verification, not release approval.

## Evidence and remaining scope

The [source identities](source-identity.json), [resource inventory](generated-resources.json)
and [development artifacts](development-artifacts.json) bind this slice.
[Root checks](root-checks.zip) retain actual logs/XML, including the failures;
[counts](root-checks.json) distinguish final checks from earlier attempts.
[Native restart](native-restart.zip) and its [summary](native-summary.json) are
separate from failed [attempt a](native-attempt-a.zip) and
[attempt b](native-attempt-b.zip). [Independent reports](independent-review.zip)
remain unchanged. Each archive has a matching `*-files.json` with member hashes;
[SHA256SUMS](SHA256SUMS.txt) binds the final local evidence documents and archives.
[Local link checks](links.json) check file existence, not remote URLs or anchors.
Public API bytes remain identical to MAP-01/v1.3. Complete client/GPU/S2,
remote/Linux, historical migration and long-load acceptance stays open under
ADR-018. New environment protection, star map/navigation, discovery, custom
sky/fog/sound and remaining v1.4 integration are not implemented by this slice.

All current-version Required Gates remain **IN_PROGRESS**, not PASS. Continue
the current version's environmental response/protection slice; no v1.5 work.
