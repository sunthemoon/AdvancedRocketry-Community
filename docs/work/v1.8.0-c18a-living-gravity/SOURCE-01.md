# C18a leaf A: living-gravity source checkpoint

Date: 2026-10-07. Status: narrow Low-oracle correction ready for independent
successor review after the two initial source reviews;
Java compilation, JUnit, native execution and acceptance are **UNVERIFIED**.
This is not a delivery receipt or a version/Gate decision.

## Assignment and identity

The delegated worker implemented only living-tick leaf A of the integrator's
committed [TASK-01.md](D:/GitHub/arce-v180-living-gravity-20261007/docs/work/v1.8.0-c18a-living-gravity/TASK-01.md),
adopted after independent REVIEW-03. Worktree:
`D:/GitHub/arce-v180-living-gravity-20261007`; branch:
`codex/v1.8.0-living-gravity-20261007`; unchanged base/HEAD:
`a336c5357eeaeec6a65d5aefd2720c70a94c54ae`.
Live Root AGENTS was read; its SHA256 is
`1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`.
Project, product, porting, roadmap/version, test/Gate, v1+ quality, parallel and
remote-verification rules were read. Accepted ADR-066 section 3.3 and the
ADR-061 disablement requirement remain the boundaries. No upstream source or
art was copied; no new platform interception or persistence exception is added.

The integrator owns commits, pushes, central config/event wiring, task contract,
ledger and status. The worker neither acquired a main HEAD/index hold nor
changed Git. This record and the following three files are the complete write
scope; all other existing work remains untouched.

| File | Bytes / lines | SHA256 |
| --- | --- | --- |
| [CelestialGravityController.java](D:/GitHub/arce-v180-living-gravity-20261007/src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/service/CelestialGravityController.java) | 5682 / 125 | `35ac0c92f3517c2456732abc76e9226ceacc9d9de65542a959231332eb089ff0` |
| [CelestialGravityControllerTest.java](D:/GitHub/arce-v180-living-gravity-20261007/src/test/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/service/CelestialGravityControllerTest.java) | 10042 / 193 | `e667fa8590efe1d2ebf6b3ab4a8ed5360f3440420827ecc916ea55dd7193942f` |
| [LivingGravityGameTests.java](D:/GitHub/arce-v180-living-gravity-20261007/src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/LivingGravityGameTests.java) | 29896 / 506 | `07f13a0ccaf2068a493429174cdeb7dc8054e9983fd80b71ca3b59722d9991b1` |

These are uncommitted development identities, not accepted/tested source SHAs.
The integrator must associate subsequent evidence with its actual committed
combined source.

## Implemented behavior and design

The existing living-tick controller now accepts logical-server/main-thread
living events. It skips missing gravity attributes without adding one. All
three old constructors and the owned UUID remain; the additive fourth
constructor is `(CelestialEnvironmentService, PositionGravity, PlayerGravity,
BooleanSupplier)`. Old constructors default the new non-player behavior to
enabled. One resolution method retains player field -> position -> actual
Level -> default 1, including creative, spectator and FakePlayer semantics.
Non-players never query either player layer. Disabled non-player ticks resolve
to 1 and clear only the owned UUID; re-enable reads current state.

Attribute changes preserve base and foreign UUIDs. Factor 1 cleanup precedes
same-amount checks. Wrong operations are replaced. Matching owned permanent
residue is removed through the actual `removePermanentModifier(UUID)` API and
re-added transient; matching valid transients remain the same instance without
new dirty callbacks. Finite 0..4 validation remains before mutation. The
controller adds no world scan, collection/cache, ticket, chunk load, movement
rewrite, client dependency or saved root. Native slow-falling, no-gravity and
hurt handling are not replaced.

The AttributeInstance/AttributeMap APIs and serialization facts were supplied
by the separately completed [primary native inspection](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-gravity-attribute-native-facts-20261007/REPORT-01.md).
That report pins Forge 1.20.1-47.4.10 mapped archive SHA256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
This source assignment did not open an archive or execute Java.

## Tests added, not executed

The existing two JUnit method bodies and all assertions are unchanged after
normalizing repository line endings.
Seven added methods cover missing attributes; invalid-factor nonmutation and
factor 4; factor-1 zero residues across operations/permanence; wrong-operation
replacement; matching permanent conversion and dirty-callback idempotence;
all foreign operations/base preservation; and constructor dependencies.
Total: nine controller JUnit methods. Actual entity/supplier dispatch is
covered by the following native tests, not claimed from the attribute helper.

The new registered GameTest holder has nine tests, each with a 100-tick budget:

- Native `Cow.tick()` dispatch through Forge's existing hook and the production
  listener in Earth, Moon, Mars, Venus and Space, preserving the base.
- Native `Cow.travel(Vec3.ZERO)` twice in air: Moon/Earth displacement ratio,
  zero-gravity Space and native no-gravity behavior.
- Production disable/re-enable, foreign permanent/base retention and unchanged
  legacy player gravity while the new switch is false.
- Actual `Cow.saveWithoutId`/`load` round-trips: initially matching owned
  permanent residue becomes transient, owned UUID is absent from entity NBT,
  unrelated permanent UUID/base survives, and native tick recomputes in Moon,
  Mars and Earth. This is entity NBT, not a disk/restart result.
- Three explicitly labelled controller fixtures for non-player callback
  isolation/current catalog/default/supplier and old-constructor behavior;
  player precedence in survival/creative/spectator; and missing-attribute skip.
- Actual active gravity-field and checked station fixtures: the production
  player event resolves their local gravity while a native mob at the same
  location retains its Level gravity. The field case also compares loaded
  chunks and existing bounded `TicketCounts.near` observations after setup;
  that helper excludes its documented fixture/transient ticket types.

Cows are not enrolled in world ticking. Their tick/travel calls are synchronous
native method routes, not claimed natural-scheduler observations. Player
registered-event posts and direct controller calls are separately labelled.
Explicit chunk acquisition is fixture setup only. In-memory config overrides
are synchronous and cleared in unconditional finally cleanup, never retained
over scheduled ticks. Field/player/station cleanup is limited to the newly
created fixtures. No shared fixture or existing test is edited; no assertion
or timeout was relaxed.

## Independent source review and narrow correction

The actual focused [GAME-TEST-REVIEW-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-independent-review-20261007/GAME-TEST-REVIEW-01.md)
and full [WORKER-REVIEW-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-independent-review-20261007/WORKER-REVIEW-01.md)
identified Low F1/L1: the missing-getter fixture's forced-null postcondition
could not observe hidden native AttributeMap changes. They identified no
additional scoped Critical/High/Medium finding; neither executed Java/native
tests or accepted the source. Both predecessor reports remain unchanged.

After their pins were released, Root assigned only this GameTest and source
record for correction. The fixture still masks the entity getter and preserves
all three throwing early-return callbacks. It now independently obtains the
actual native backing map/instance, seeds a nondefault base plus foreign
permanent/transient modifiers, and checks two cases: owned UUID initially
absent, then an existing owned transient. Each call must preserve map/instance
identity, every gravity modifier's UUID/object identity and count, exact base,
and the serialized instantiated-attribute map. The complete modifier snapshot
includes transient modifiers, which serialization alone would miss. Thus the
postconditions can observe ordinary native addition/removal/replacement even
while the entity getter is forced null.

This is a masked-getter/early-return compatibility fixture with an existing
registered backing gravity attribute. It does not construct or prove an
actually absent AttributeSupplier, inspect mutation history that ends in the
same state, or establish production dispatch. No new helper, reflection,
platform interception, schema or controller behavior is introduced. Controller
and JUnit source, nine declarations and all timeouts remain unchanged.
The holder now exceeds 500 lines (506). Its responsibility was checked: it
remains one gravity-test holder with the already assigned native/local fixtures
and helpers, without production/domain responsibilities or additional features.
The Low correction's actual successor review and native execution remain pending.

## Central integration dependency

Root separately implements `CommonConfig.CLASSIC_GRAVITY_ENABLED`, COMMON
`environment.classicGravityEnabled` default true, its `classicGravityEnabled()`
accessor and test-switch membership, and binds that accessor to the fourth
constructor in the single existing event registration. These symbols are not
in the worker base: the new native test file intentionally depends on the
combined integration and cannot be compiled as an isolated checkpoint.
Root's separately owned `ClassicGravityConfigTest` was read only; its three
unexecuted methods cover stable path/default, loaded disable/re-enable and
ephemeral switch independence. None of these central files was edited here.

## Actual commands, results and limits

- Read-only `git status --short`, `git worktree list`, `git rev-parse HEAD` and
  `git rev-parse --abbrev-ref HEAD` confirmed the assigned clean base before
  source edits and the unchanged branch/base after edits. Literal PowerShell
  reads and bounded `rg` examined the mandatory documents, accepted contract,
  controller/callers, attribute report, existing native tests and fixture APIs.
  Tool outputs `ae2e1e`, `9ecb26`, `58dc7c`, `51a868`, `07279d`, `e11f4e` and
  `d3d563` hold those reads. Truncated combined reads were supplemented for the
  required version and remote document sections.
- `git diff --check` passed with no whitespace diagnostic (`91b5b4`,
  `c4a595`, `85ffd8`); `git diff --cached --quiet` passed (`c4a595`), confirming
  no index edits. Tracked diff was 52 insertions/22 deletions in the controller
  and 147 JUnit insertions. New untracked files are not included in that Git
  diff count.
- Tool `c4a595`, exit 0: an in-memory bounded Python stdlib audit with
  `D:/python/pyenv/pyenv-win/versions/3.13.15/python.exe -B -` confirmed source
  base, the three then-modified source paths, four constructors, both original
  JUnit methods unchanged, nine JUnit/nine GameTest methods, native tick/travel/
  NBT call sites, no fall implementation, and the missing central integration
  dependency. These are literal/static controls, not Java tests. A later
  import-only ordering change altered the JUnit hash; `85ffd8`, exit 0,
  recorded the final three source hashes above and reran whitespace checks.
- Discovery failures are retained, not counted as successful reads:
  `f71709` used a nonexistent `endgame/forge/GravityFieldBlockEntity.java`;
  `a04355` exited 1 on nonexistent `endgame/service/EndgameDevices.java`;
  `67a619` exited 1 while probing the not-yet-created source record;
  `5744fd` probed nonexistent `docs/adr`; `5995d1` exited 1 on a guessed version
  filename. Correct named sources were then read from `endgame/gravity`,
  `endgame/device` and `docs/decisions`. These were read failures, not runtime
  failures or source changes.
- Free-space observation `91b5b4`: C 7,463,358,464 bytes; D 322,523,607,040
  bytes. C is below 10 GB. **No JVM, javac, Gradle, GameTest, native server,
  install, network, class/archive read, cache scan, cleanup or child agent was
  started.** Python bytecode is disabled; TEMP/TMP/TMPDIR were set to the owned
  `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-20261007`.
  No copied build/cache/JAR/source export or C temporary script was created.
- Narrow correction reads `b46419` and `b1accf`, both exit 0, examined the
  actual two source-review reports and refreshed live AGENTS/source/disk
  identities. The first combined output truncated part of WORKER-REVIEW-01;
  the second read supplied that report in full. `846002`, exit 0, checked the
  four original frozen hashes and bounded public-API context. `d02733` and
  `07b803`, both exit 0, recorded the amended native-test bytes/count/hash and
  reran `git diff --check`. These are source/static observations, not executed
  witness outcomes. C remained below 10 GB; no newly prohibited execution,
  Git mutation, evidence-report edit, source export or cleanup occurred.
- Tool `3a0bd7`, exit 1, stopped at a static inverse-diff assertion: the audit
  incorrectly assumed CRLF for the original new GameTest's two replaced lines.
  `f7cfbd`, exit 0, observed the actual LF-only file and restored only that
  fixture body in memory; its reconstructed SHA256 exactly matches the
  original reviewed `bbfddc908f8225a6d92695769796536edbbec60b67933760fa57d353f9aa44fa`.
  This was an audit newline error, not a Java/native test failure; no source
  changed during those reads.

## Remaining work and release

Independent review must inspect this actual diff and combined central files.
Fixed committed combined source still needs Java 17 clean build/full unit
suite, deterministic repeated DataGen with cleanliness checks, unfiltered
native tests and the mandatory existing GravityField/StationOrbit/
PlanetaryExposure regressions, plus packaged/restart evidence. Compilation,
native movement/API behavior, global-switch cleanup and real entity save/reload
are not certified by the static controls. No version Required Gate is met by
this checkpoint alone.

Leaf B fall semantics/M1 remains unresolved and unassigned; no fall listener,
damage helper or passenger policy was implemented. Equipment, atmospheric
damage/spawn, non-living gravity and later-version work remain excluded. Both
parent ledger units remain unaccepted until committed runtime evidence and
Root's independent disposition support delivery. No additional major choice
or exception was introduced by leaf A.

At handoff the worker freezes these four files and releases its own source
read/write pins. Root may review and integrate them. No main HEAD/index hold
exists, and this worker performs no commit or push.
