# CL18D-SKY-BINDING-01: bootstrap mapping tests, author handoff

Date: 2026-10-08. Version: v1.8.0. Author: delegated Claude implementation worker (not Root).
Task: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-binding-claude-author-20261008-01/TASK-01.md`.
Checkout: `D:/GitHub/arce-v180-claude-sky-binding-20261008`, branch
`codex/v1.8.0-claude-sky-binding-20261008`, stated base `2e397a49`. HEAD was not checked
because Git was not allowed.

Status: authored, not compiled and not run. No Required Gate, ledger delivery or acceptance is claimed.

## 1. Actual files

Both files are new. No other repository file was written.

- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/client/sky/SkyEffectsRegistrationTest.java`
- `docs/work/v1.8.0-c18d-sky-switches/BINDING-HANDOFF-01.md` (this file)

Nothing was written to the evidence leaf. No production, central, build, config, asset, ADR,
status, ledger or AGENTS file was touched.

## 2. Implementation approach

- **Actual handler.** Each test builds an empty `HashMap`, wraps it in Forge's own
  `RegisterDimensionSpecialEffectsEvent(Map)` and calls the real
  `ClientBootstrap.onRegisterDimensionEffects`. It then reads only what the handler stored. There is
  no duplicate factory, source-text match, reflection or test-built expected registration map.
- **Conventions.** `MinecraftBootstrap.initialize()` runs in `@BeforeAll`, as in `PlanetaryEffectsTest`.
  `ClientConfig.SPEC.setConfig(null)` runs in both `@BeforeEach` and `@AfterEach`, following
  `ClientConfigTest`. Settings are changed with `CommentedConfig.inMemory()` and `BooleanValue.set`.
  No TOML file, rendering, GPU or packaged JAR is involved.
- **Observable category check (`assertCategory`).** Each registered value must be a
  `PlanetaryDimensionEffects`. Its `overridesSky()` must equal the expected switch. The pinned sky
  type and ground flag must hold: `NORMAL`/true for `planetary` and `END`/false for
  `planetary_space`. Its lightmap, ambient, foggy-at, fog colour (three brightness values) and sunrise
  (four times) must match a fresh `OverworldEffects` or `EndEffects`. When enabled, the cloud height is
  NaN; when disabled, it equals the fallback's height. The three weather hooks (`renderClouds`,
  `renderSnowAndRain`, `tickRain`) must return the switch value. When disabled they reach Forge's inert
  default fallback with null arguments. `renderSky` is deliberately not called, because the enabled
  path touches `PlanetarySkyClient` client state.

### Test methods (5)

1. `handlerRegistersExactlyTheTwoExistingCategoryIds`: checks the literal IDs
   `advancedrocketrycommunity:planetary` and `advancedrocketrycommunity:planetary_space`, that they
   equal the `SkyProfiles` constants, the exact key set, a size of 2, the adapter type and two distinct
   instances.
2. `handlerLeavesEveryVanillaEffectsEntryInTheEventMapUntouched`: pre-seeded `minecraft:overworld`,
   `minecraft:the_nether` and `minecraft:the_end` entries are kept as the same instances, and exactly
   two entries are added.
3. `unloadedDefaultsEnableBothAndEachIdKeepsItsOwnFallbackPolicy`: with the config unloaded, both
   switches default to true and the category checks pass. With both switches loaded and false, the
   fallback policy still holds per ID.
4. `fourIndependentSettingCombinationsDriveTheSameRegisteredInstances`: one registration, then the
   sequence (T,T), (F,T), (T,F), (F,F), (T,T). At each step the map keeps the same instances and a
   size of 2, and the category checks pass. The last step re-enables both switches.
5. `registrationCapturesNoValueSoReloadAndReenableNeedNoNewRegistration`: registers while both are
   false, then:
   - unloads the config, giving the true defaults;
   - reloads with a config that sets only the planet to false;
   - reloads with a config that sets only the station to false;
   - sets the station back to true.

   The same two instances are checked throughout, with no re-registration.

### How the required mutations are caught

| Mutation of the handler | Failing assertion |
|---|---|
| Planet and station suppliers swapped | Test 4, step (F,T): `planetary` `overridesSky` is true but false was expected. Test 5 fails as well. |
| IDs swapped (Overworld adapter under `planetary_space`) | Tests 3–5: the sky type is `END` under `planetary`, but `NORMAL` was expected. |
| Wrong fallback (both Overworld or both End, or Nether) | Sky type, ground, lightmap, fog colour, sunrise and disabled cloud height checks for one ID. |
| Both categories reading the same switch | Test 4 steps (F,T) and (T,F). |
| `() -> true`, an inverted supplier, or a value captured at registration | Disabled steps in tests 3–4. Test 5's first unload expects true after registering while false. |
| Raw vanilla effects registered without the adapter | `assertInstanceOf(PlanetaryDimensionEffects.class, …)`. |
| A missing or extra registration, or a vanilla key overwritten | Exact key set and size checks, and test 2's `assertSame` checks. |
| `SkyProfiles` ID strings changed | Test 1's literal ID equality. |

## 3. Tests designed but not run

All five methods are unverified. No compiler, JVM, Gradle, Git or shell was used, as the task
requires. Root's committed-source Gradle `test`/`build` run, plus an independent review, decide
whether these tests compile and pass.

Root may run `./gradlew test --tests '*SkyEffectsRegistrationTest'`. The existing `ClientConfigTest`
and `PlanetaryEffectsTest` should be rerun unchanged.

## 4. Required reads

**Completed in full:**

- The live `D:/GitHub/AdvancedRocketry-Community/AGENTS.md` (§1–12).
- `PROJECT-CONFIG.md`, `PRODUCT.md`.
- `docs/01`, `04`, `05`, `06`, `14`, `16`, `17`.
- `docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md`.
- The checkout's `docs/work/v1.8.0-c18d-sky-switches/TASK-01.md`.
- The checkout sources: `ClientBootstrap.java`, `PlanetaryDimensionEffects.java`,
  `ClientConfig.java`, `SkyProfiles.java`, `testsupport/MinecraftBootstrap.java`,
  `PlanetaryEffectsTest.java` and `ClientConfigTest.java`.
- The context file `SKY-CONTINUATION-01.md`.

**Read in part:**

- ADR-066: §7.2 only (lines 840–860 of the live copy).
- `ModIdentity.java`: a grep for `MOD_ID` and `id(` only.
- An existing-tests grep confirming that plain JUnit already constructs Forge events (for example
  `TagsUpdatedEvent`) and that no test loaded `ClientBootstrap` before.

**Not read:**

- `docs/15`: no remote Linux or visual validation was in scope.
- `UPSTREAM.md`, `NOTICE.md`, `docs/02` and `docs/08`: no upstream file was used.
- `SOURCE-01.md`, `SOURCE-INTEGRATION-01.md`, RESULT-30/31 and the v1.8 implementation log.
- Forge or vanilla source. I relied on Root's primary-API statement in TASK-01 and fetched nothing.

## 5. Impact and risks

- **Impact.** This adds test code only. Runtime behaviour, IDs, config, assets and the network are
  unchanged.
- **Constructor visibility (compile risk).** The test assumes the
  `RegisterDimensionSpecialEffectsEvent(Map)` constructor is accessible from test code. Root's
  primary-API check says it takes the map; an `@ApiStatus.Internal` marker would not block compilation.
  If the constructor is not public, compilation fails.
- **First test to load `ClientBootstrap` (linking risk).** Class verification may load, without
  initializing, referenced client types such as `RenderType`, `FlowingFluid` and the menu screens. The
  test calls only the dimension-effects handler. A `NoClassDefFoundError` or `VerifyError` in plain
  JUnit would surface as a test error, not a product defect.
- **Forge default hooks.** The disabled-mode weather checks assume that Forge's default
  `renderClouds`, `renderSnowAndRain` and `tickRain` on the vanilla Overworld and End effects return
  false and ignore null arguments. If that is wrong, the disabled-step assertions fail.
- **Shared config state.** `ClientConfig.SPEC` is a static singleton shared with `ClientConfigTest`.
  Resetting it before and after each test follows the existing convention, but does not protect
  against parallel test execution if that is ever enabled.
- **Still unproved:**
  - real Forge mod-bus dispatch through `@EventBusSubscriber(value = Dist.CLIENT)`;
  - real TOML editing and reload;
  - the rendered sky, fog handlers and ambience;
  - which `dimension_type` JSON reaches the JAR (continuation finding 3);
  - V1/V2 client evidence.

## 6. Release

I made no commits, staging, stash, branch, HEAD or index operations. I ran no shell, Git, JVM,
`javac`, Gradle, server or client, started no process, and dispatched no agent. Only Read, Grep, Glob
and Write were used, and I wrote only the two files listed in section 1.

I release all write, read, HEAD, index and process interests in this checkout, in the evidence leaf
and in the main checkout. Root captures, hashes and independently verifies the exact bytes.
