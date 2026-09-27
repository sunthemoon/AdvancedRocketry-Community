# V140-DATA-01 — schema and capability-aware consumers

Date: 2026-09-27. **Development slice; not a candidate or Required Gate approval.**
Status: verified development slice. Final-source independent execution and the
bounded native restart passed; G0-G9 remain open.

## Identity and completed implementation

- Branch: `codex/v1.4.0-planetary-expansion`.
- Source base: `8947fac9641dc11ce2dce1ed8a3877984be044ab`.
- Development authorization and contract: accepted
  [ADR-030](../../decisions/ADR-030-V140-DEVELOPMENT-BASELINE-EXCEPTION.md) and
  [ADR-031](../../decisions/ADR-031-PLANETARY-DEFINITIONS-AND-FIXED-LEVELS.md).
- Build identity: `1.20.1-1.4.0-dev`, Java 17.0.7, Forge 47.4.10. API remains 1.7.
- Root is the sole tracked writer. The independent reviewer used read-only
  source access and fresh Temp reports, with an exclusive coordinated Gradle
  slot. The user-owned untracked documentation bundle was not read or changed.

The production decoder/model now accepts schema 2 with optional Level mappings,
explicit landable/orbitable/gas capabilities and bounded solar/radiation
metadata. JSON and NBT round trips use the real Codec. Legacy built-ins retain
their original values/defaults and original generated resource bytes through a
checked explicit legacy encoder. Unknown fields, malformed optional values,
explicit nulls, fractional integers, coerced booleans and invalid capability or
finite-range combinations are rejected.

Only mapped bodies enter the Level index. Flight planning, quotes, actual
launch authority, station creation and legacy-target migration consume the
optional mapping/flags. Arrival restrictions do not erase a still-valid mapped
source or committed station's authorized exit. Shared Space remains a station
host, not a new surface. Configured but unavailable catalogs fail closed rather
than falling back to legacy enum flight rules.

The celestial snapshot/channel are version 2 with exact matching. The display
snapshot carries optional mapping and configured environment/capabilities;
128 bodies, 128-character IDs and the 96 KiB limit remain unchanged. Malformed,
old-schema, oversized and trailing data cannot replace a good client cache.
No client rendering, new physics, exported API type, SavedData or transfer
journal schema was changed.

Reader usage and boundaries are in the [data guide](../../CELESTIAL-DATA-GUIDE.md).
The new metadata adaptation is recorded in
[provenance](../../provenance/v1.4.0-development-metadata.md). No new upstream
asset/code was imported; historical approval digests/notices are unchanged.

## Test coverage and retained failures

Added 23 JUnit cases in `CelestialSchemaV2Test`, `CelestialSnapshotV2Test` and
`PlanetaryFlightAdmissionTest`. They cover actual old JSON resources, JSON/NBT
round trips, constructors, strict values/fields/null presence, fixed mappings,
unmapped gas bodies, closed-source exits, closed-orbit station departures,
shared-host refusal and bounded packet/cache replacement. The maximum 128-body
snapshot uses 128-character identifiers without increasing the payload budget.

Three new `PlanetaryAdmissionGameTests` use private catalogs without replacing
the global runtime. Live tests cover quote/launch/new-station refusal with
unchanged fuel and saved authority; legitimate closed-source launch/cancel;
and a live committed station's departure with ownership and membership checked.
Only fixture-owned stations, entities, requests and chunk tickets are cleaned.

Five Python tests cover the native runner's pack data preservation, no fake gas
Level, exact native station comparison, bounded NBT expansion and retained
spawn/startup failures. These tests are not substitutes for the actual native
processes.

Failures were preserved, not converted to passes:

| Observation | Resolution |
|---|---|
| First targeted run: 167 JUnit cases, 1 failure | The old test-only Mars JSON used shared Space as a placeholder. Its mapping now uses an unregistered test-only Mars key; the original successful data-driven planning assertion remains. A separate test rejects shared Space surface use. This does not grant physical Mars admission. |
| Initial full command failed compilation | New GameTest assertions referred to a planner code instead of the existing request-code mapping. Corrected to `INVALID_DESTINATION`; production codes unchanged. |
| Next full command: 792 JUnit passed, 213 GameTests with 1 failure | The synthetic Space rocket was not yet visible to `ServerLevel.getEntity`; intents returned `ENTITY_UNAVAILABLE`. The fixture now waits for actual visibility with a bounded ticket. Production authority was not relaxed. |
| Independent Medium: explicit JSON null looked like an absent field | DFU JsonOps maps null to missing on `get`. Presence now uses raw entries; independent null cases cover an unmapped body's Level, a root's parent and legacy reserved/version fields. |
| Independent Medium: an async fixture timeout could skip cleanup | Initialization and each terminal wait/assertion outcome clean only owned resources. The fixture fails/cleans by tick 80, before the unchanged 100-tick test timeout. |
| Independent Low: native failure phase lacked a structured result | The runner now retains result/error/actual exit code even when process creation fails; covered by Python regressions. |
| First native attempt stopped cleanly but its post-stop checker failed | The small celestial SavedData reader rejected the registry-heavy whole `level.dat` at its unchanged 4096-tag bound. The corrected runner resolves actual started Levels with commands and retains raw `level.dat`/world hashes without misrepresenting them as fully decoded. All station NBT comparisons and parser limits remain unchanged. |

No test was removed, assertion weakened, exception ignored, timeout enlarged or
budget raised. The earlier successful root GameTest pass predates the final
cleanup-only correction; the independent final-source command verifies that
correction separately.

## Actual root checks

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17.0.7'
./gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
```

Root command exit **0**, **141 seconds**: **792 JUnit tests in 146 suites**, zero
failures/errors/skips, newly executed rather than restored from the test cache;
**213 required GameTests passed**. DataGen reported **zero written files** and
`git diff --exit-code -- src/generated` returned 0. The normal provider still
targets the existing v1.3 generated-language directory; this slice adds no new
generated resource batch.

Python uses `PYTHONUTF8=1` and
`D:/python/pyenv/pyenv-win/shims/python.bat -B`:

```text
python -B -m unittest tests.test_v140_celestial_schema_smoke -v
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_repository.py --require-approved-identity
git diff --check
```

All returned 0: **5** runner tests, **11** version plans / **33** source inputs,
and **45** strict repository checks (zero pending/warnings/failures).
Repository validation did not receive `--package-root`. Final new-file/local
links and staged identities are checked separately when evidence is packaged.

[Root check summary](root-checks.json) and [unchanged raw logs](root-checks.zip)
retain full successful/failed Gradle outputs, failed/fresh JUnit XML, GameTest
logs, Python/planning/repository logs and the collection helper. Forge
deprecation/bootstrap warnings remain visible. Older fault-injection GameTests
intentionally emit ERROR diagnostics; their passing assertions are not evidence
that arbitrary ERROR output can be ignored in a native run.

## Independent and native checks

The independent final-source command returned 0 in **150.613 seconds**:
**792 JUnit / 146 suites**, newly executed, **213 required GameTests passed**,
zero DataGen writes, and the five Python regressions passed. Its 1,237-input
before/after inventory was unchanged. A **3196 ms / 63 ticks** lag warning is
retained; this is not a performance acceptance result.

The full API JAR equals the v1.3 handoff byte-for-byte, SHA-256
`50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf`; all 27 API
classes also equal their corresponding main-JAR entries. Packaged original
Earth/Moon/Space JSON equals the baseline's tracked blobs. No standalone
external consumer rebuild/publication was performed for this unchanged API.

[Artifact identities](development-artifacts.json) and
[source identities](source-identity.json) bind the actual outputs/inputs.
The independently rebuilt main JAR used for native testing has SHA-256
`62b187661dcd8328db141a7ea276457797743cd06061929421595bca7dbcf8f9`.

```powershell
python -B scripts/run_v140_celestial_schema_smoke.py <fresh-libraries-only-server> `
  --host-jar <frozen-1.4.0-dev-main.jar> --evidence-dir <new-evidence-directory> `
  --java 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe' --accept-eula
```

Corrected harness exit **0**, both dedicated Java processes exit **0**. The
first creates a real Moon station, reloads schema-2 closed Moon plus an unmapped
gas body, and denies new Moon station creation. The second restarts the same
world with that pack. Both show the same capabilities, retain the complete
native station `data` compound, resolve Overworld/Moon/Space, and reject the gas
body as an actual Level. Original station UUID/orbit/ownership/region/pad and
reservations are checked through native NBT and live command receipts.

The native logs contain zero ERROR/FATAL/project-warning/client-linkage
findings; ordinary Forge/offline warnings remain recorded, including a
**2117 ms / 42 ticks** startup/reload lag warning. These are clean
stops with no online player, not crash, client, complete-flight or whole-world
upgrade proofs. `level.dat` is retained raw with same-world identity hashes,
not parsed by the small SavedData reader. Client snapshot behavior is covered
by JUnit, not a real-client native connection.

[Native summary](native-summary.json), [raw restart evidence](native-restart.zip)
and the [retained failed first attempt](native-attempt-1.zip) distinguish these
observations. The Python-only correction happened after the independent Java
build; it did not change the tested JAR. The final runner delta/Python rerun and
native raw-evidence readback are retained with the independent reports.

[Unchanged independent reports and execution receipts](independent-review.zip)
include `review-1/REVIEW-SOURCE.md`, `review-2/REVIEW-INCREMENTAL.md`,
`review-3/REVIEW-EXECUTION.md` and `review-4/REVIEW-NATIVE.md`.
The [independent manifest](independent-review.json) records every member's
size/hash and the archive identity. The final execution report SHA-256 is
`2aad1cd9a67baf0262b5b8ac9bc80ccbf85bd90e6480873065face14b07a0926`;
the final native report SHA-256 is
`8cf6face5dd8ddedd09a9bc090e9143946420dad33953859408b73564925f047`.
The reviewer reports no unresolved concrete finding within this slice, not
release approval. Earlier findings and helper-format errors remain in the
archive alongside their corrections.

## Evidence packaging

The [checksum manifest](SHA256SUMS.txt) covers the documentation, summaries and
raw evidence in this directory. Final checks compare working and staged
evidence bytes, ZIP CRCs/member uniqueness and the staged implementation blobs
against [source identities](source-identity.json). The [local-link result](links.json)
checks file existence only, not external URLs or Markdown anchors. No JAR,
runtime world or user-owned documentation bundle is staged as a release.

## Remaining work and Gate status

- `V140-DATA-02`: bounded raw-resource reader and one coherent celestial/route
  generation. The existing preparer can still skip malformed JSON before
  project validation; separate catalog publication is not atomic.
- `V140-DATA-03`: joint reload/restart verification after DATA-02.
- MAP/MIG: durable custom body/Level bindings and old-world initialization
  before new surface worlds/admission. This slice cannot prevent arbitrary
  custom remapping across restart.
- New planetary terrain/worlds, environment effects, navigation/discovery and
  sky/assets remain unimplemented. No new generic-orbit flight is enabled.
- Native checks do not replace authentic historical-world upgrades, forced
  crashes, online clients, real-GPU V1, two-real-client V2, Linux/remote,
  reference-load or soak acceptance. Full testing remains deferred under
  ADR-018 until the original mechanical/dimensional scope is complete.

All v1.4 Required G0-G9 and inherited acceptance remain open. There is no
candidate commit, version tag, release approval or permission for v1.5 work.
Continue with DATA-02 in the
[implementation log](../v1.4.0-implementation-log.md).
