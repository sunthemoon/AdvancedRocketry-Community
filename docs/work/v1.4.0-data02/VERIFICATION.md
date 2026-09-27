# V140-DATA-02 / DATA-03 — bounded paired reload

Date: 2026-09-27. Development slice, **not a release candidate or Gate PASS**.
Status: verified development slice. Root execution, independent key checks and
packaged restart/readback passed; all Required Gates remain open.

## Identity and scope

- Branch: `codex/v1.4.0-planetary-expansion`.
- Base: `4db8b1bc573b6f9f20a20bd2a5a13a0836e055f2`.
- Build: `1.20.1-1.4.0-dev`, Java 17.0.7, Forge 47.4.10.
- Contracts: accepted [ADR-030](../../decisions/ADR-030-V140-DEVELOPMENT-BASELINE-EXCEPTION.md)
  and [ADR-031](../../decisions/ADR-031-PLANETARY-DEFINITIONS-AND-FIXED-LEVELS.md).
- Root is the sole tracked writer; independent review writes only fresh Temp
  outputs. The user-owned untracked documentation bundle was not read or edited.

One reload listener reads both winning resource sets and publishes one immutable
`PlanetaryCatalog` plus generation. Existing environment/station services receive
a read-only celestial projection; attempts to mutate that projection are rejected.
Quotes capture one pair for the whole batch, launches capture one pair, and
display synchronization captures its catalog and generation together. The two
old independent listeners and route owner are removed rather than left as an
alternative publication mechanism.

Preparation uses raw UTF-8 byte limits (body 32768, route 4096), nesting 16,
duplicate-key detection and strict lexical validation. Counts remain 128 bodies,
512 routes, 256 anchors, 64 outgoing edges, 1024 search expansions and 256 cached
plans. Both resource sets share the eight-detail/2048-character error budget.
The existing post-parse size checks remain in the decoders as well.

The paired candidate validates route endpoints against the same celestial data:
surface means mapped and non-gas, orbit means a defined body. Destination flags
do not erase valid source/reverse edges. A rejected candidate retains both catalog
objects, generation and the populated route cache; initial rejection has no
partial ready state. Preparation does not publish before the platform barrier.

The platform hook is server-resource scoped, not a cross-listener transaction;
see [Forge's 1.20.1 event source](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/src/main/java/net/minecraftforge/event/AddReloadListenerEvent.java).
Other listeners and global datapack-sync behavior remain independent. No Forge,
Minecraft or third-party source/art asset was copied. Registry/world identities,
save schemas, public API 1.7 and celestial display protocol 2 are unchanged.

Operator route inspection and authoring limits are documented in the
[data guide](../../CELESTIAL-DATA-GUIDE.md).

## Actual root execution

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17.0.7'
./gradlew.bat clean build runData runGameTestServer --offline --no-daemon --console=plain
```

Exit **0**, **150 seconds**. **815 JUnit tests / 148 suites**, no failures,
errors or skips; `:test` actually executed. **213 required GameTests passed**,
including the three existing planetary authority/departure cases adapted to the
paired owner. DataGen wrote **zero files**; generated resources have no diff.
This adds no generated asset batch; the existing v1.3 provider remains active.

Added **23 JUnit cases**: eight bounded-JSON and fifteen actual file/resource
reload cases. They exercise UTF-8/whitespace byte limits, lexical forms, escaped
duplicates, depth, partial stream failure/closure, resource counts, graph/schema
failure, cache identity, initial failure, barrier ordering, pack precedence,
body-and-route removal and finite 100-body input. After the root full run, the
new fixture was expanded from three selected baseline routes to all four
packaged routes; the final synthetic case has **100 bodies / 101 routes**.
That test-only correction does not change the tested JAR; its final execution
is recorded separately in independent evidence.

Using `PYTHONUTF8=1` and `D:/python/pyenv/pyenv-win/shims/python.bat -B`:

```text
python -B -m unittest tests.test_v140_celestial_schema_smoke -v
python -B scripts/validate_v1plus_planning.py
python -B scripts/validate_repository.py --require-approved-identity
git diff --check
git diff --exit-code -- src/generated
```

Nine Python checks passed (five existing, four added). Planning validation
passed 11 plans / 33 inputs. Strict repository validation passed 45 checks,
zero pending/warnings/failures; it did not receive `--package-root`.
Final local Markdown links and staged/source evidence identities are checked
separately during packaging.

[Root summary](root-checks.json) and [raw checks](root-checks.zip) preserve the
full build/test output, XML, GameTest logs and collection helper. Deprecation
warnings and intentional fault-injection GameTest diagnostics remain visible.
The 100-body check is a finite input-bound test, not a workload/soak result.
The root GameTest log retains a **2949 ms / 58 ticks** lag warning; this run
does not establish performance acceptance.

## Failures and corrections retained

| Observation | Action |
|---|---|
| Initial targeted checks passed, but an added strict-input regression failed: 7 tests / 1 failure | Gson's non-lenient reader still accepted a raw newline inside a string. The bounded lexical guard now validates controls, keyword spelling and allowed escapes, including keys and ignored legacy values. Original failed XML/log retained; no assertion or timeout weakened. |
| Independent source review identified Gson keyword/control/escape permissiveness | Pinned Gson bytecode and review are retained. Final regressions include mixed-case keywords, raw key controls, escaped apostrophes and escaped newline rejection, plus valid escape acceptance. |
| First native harness timed out expecting three routes | Actual packaged baseline already contains four, including `moon_surface_orbit`. Corrected the exact count to four and added that resource to the new reload fixture. Original failed native evidence and exit 1 remain; this attempt is not a clean restart or a product rollback failure. |

The corrected native harness did not widen its timeout or ignore general log
errors. Its error whitelist is the exact six rejection lines observed for the
six explicit faulty candidates; any extra, missing or different finding fails.

## Bounded packaged reload and restart

```powershell
python -B scripts/run_v140_celestial_schema_smoke.py <fresh-libraries-only-server> `
  --host-jar <frozen-1.4.0-dev-main.jar> --evidence-dir <new-evidence-directory> `
  --java 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe' --accept-eula --check-joint-reload
```

Corrected harness exit **0**; both native JVMs exit **0**. The server was
loopback-only, with host/Minecraft/Forge and zero online players. Actual sequence:

1. Generation 1: three bodies/four routes; Earth-Moon distance 50. Create and
   save a real Moon station.
2. Generation 2: schema-2 Moon closed to new landing/orbit, solar 0.5, gas body
   unmapped; Earth-Moon distance 123; four bodies/four routes.
3. Propose solar 0.75/distance 222 together with each of six bad inputs: body
   syntax, route syntax, duplicate key, depth 17, route 4097 bytes, unknown body.
   Every rejection retains generation 2, solar 0.5 and distance 123. Raw faulty
   files, valid companion edits, commands and exact receipts are archived.
4. Remove the faulty input: generation 3 publishes solar 0.75/distance 222
   together. Both capabilities remain closed; new Moon station creation denied.
5. Save/stop/restart the same world: generation restarts at 1 with the final
   values and four bodies/four routes. The complete station `data` compound is
   unchanged from before reload. Fixed Overworld/Moon/Space resolve; gas does not.

This uses read-only `/arce celestial route earth_moon` for actual active route
readback, not a packaged-file inference. It does not prove a real-client quote
or complete flight; those authority cases remain separately unit/GameTest tested.
Raw `level.dat` and same-world hashes are retained, not fully parsed with the
small celestial NBT reader. No physical Level, station record or world directory
is created/deleted by the reload listener itself.

[Native summary](native-summary.json), [successful raw restart](native-restart.zip)
and [failed first attempt](native-attempt-1.zip) remain distinct. Ordinary Forge/
offline warnings are retained; injected rejection ERRORs are expected evidence,
not hidden. This is not a forced-crash, performance or whole historical-world
upgrade result.
The successful native phases contain 26/10 standard WARN lines respectively;
the first includes **2003 ms / 40 ticks** lag. Each also has the terminal-feature
bootstrap warning outside that standard log format. Only the six exact injected
ERRORs occur in the first phase; none occur in the restart. No FATAL, project
WARN or client-linkage finding was accepted.

## Artifact and independent evidence

[Artifact identities](development-artifacts.json) record main/API/sources.
The native main JAR SHA-256 is
`9e90d868bd53fca8188c25cdb054cb2d2b578da6d46f1d00156fe22367db02e5`.
The complete API JAR remains byte-identical to DATA-01 and the v1.3 handoff,
SHA-256 `50cc9ba02bc979c31e1579a840431247ffe8401114aff013ddf478f0765010bf`.
No standalone compatibility-mod rebuild or API publication was performed.

Independent source review found the Gson lexical gap, now corrected and verified.
The affected-consumer command below exited **0** in **50.508 seconds**:

```text
gradlew.bat test --tests '*celestial.*' --tests '*travel.*' --tests '*RocketTargetFlightPlannerTest' --tests '*RocketFlightQuotesTest' --tests '*ServerEnvironmentQueriesTest' --tests '*EnvironmentSnapshotTest' --tests '*ApiArtifactTest' --tests '*ApiVersionsTest' --rerun-tasks --offline --no-daemon --no-build-cache --console=plain
```

It actually executed **167 JUnit tests / 25 suites**, no failures/errors/skips.
After the fixture inventory correction, the final command was:

```text
gradlew.bat test --tests '*celestial.data.*' --rerun-tasks --offline --no-daemon --no-build-cache --console=plain
```

The final **23 reader/reload tests**
passed in **39.324 seconds**, and final **9 Python tests** passed in **0.212
seconds**. The final before/after inventory of 1097 source/build/runner files and
all three JARs was unchanged. During the first targeted execution only the
Python runner's documented route-count correction changed; no Java input changed.

Artifact review verifies all three rebuilt JARs match the frozen root copies,
archive CRC/member uniqueness, exactly 27 API classes with matching main-JAR
bytes, and the complete API identity against v1.3. The raw native audit verifies
40/40 manifest files, each malformed input and valid companion edit, all three
log variants, route/body/generation receipts, station UUID/owner/orbit/pad/region,
three byte-identical station captures and clean save/stop receipts. It found no
unresolved concrete issue within this slice; this is not release approval.

The [unchanged independent reports and execution receipts](independent-review.zip)
and their [per-file identities](independent-review.json) distinguish source,
initial execution and final correction/native readback. The unchanged reports
are `review-1/REVIEW-SOURCE.md` (SHA-256
`742d6672ea958a30930c56425f28256d22586ecd32bc6599611d99d8068e6ac2`)
and `review-2/REVIEW-FINAL.md` (SHA-256
`1c2d063997f9a9befa735396abc88cb830567757b09c22c007b14bfa9f4dd7bc`).
[Source identities](source-identity.json)
include the retired listener/owner deletions. The [checksum manifest](SHA256SUMS.txt)
binds this directory; final packaging verifies working/staged bytes, ZIP CRCs,
unique member names and staged source blobs. [Local links](links.json) check file
existence only. Raw native manifests are not rewritten; the restart archive
also includes the stopped server's final fixture pack/configuration/world marker.

## Remaining scope and Gate status

All v1.4 G0-G9 and inherited acceptance remain open; no candidate, tag, release
approval or v1.5 permission. No new worlds, terrain, environmental effects,
star map, discovery/unlocks or sky/assets were added. Custom body/Level bindings
across restart/removal/re-add still require MAP/MIG policy and implementation
before new-world admission. Existing physical flight scope remains Earth/Moon.

Full remote, real-GPU/two-client, S2, historical-world upgrade and long-load
acceptance remain scheduled by ADR-018 after original mechanics/dimensions are
complete. Continue with `V140-MAP-01`;
see the [implementation log](../v1.4.0-implementation-log.md).
