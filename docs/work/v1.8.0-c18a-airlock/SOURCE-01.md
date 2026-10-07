# C18a-AIRLOCK-01 source handoff

Implementation worktree: `D:/GitHub/arce-v180-airlock-20261007`, fixed base
`0f9f46a5cb201b76cb17c80354b3d879a9abdd2b`. Status: READY_FOR_REVIEW of an
uncommitted source candidate, not an executed-test, delivery or Gate claim.

## Exclusive write scope

Only these six new files are worker-owned:

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/content/AirlockDoorBlock.java`
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180AirlockData.java`
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/AirlockDoorGameTests.java`
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/content/AirlockDoorBlockTest.java`
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/V180AirlockDataTest.java`
- `docs/work/v1.8.0-c18a-airlock/SOURCE-01.md`

Root owns all existing source, registration, runtime lifecycle/bridge, adapter,
configuration bindings, language/tag hooks and generated files. The required
bridge is `AtmosphereRuntime.invalidateDoorBoundary(ServerLevel, BlockPos,
BlockPos)`. It must use the live installed manager, server thread/live-Level
checks and bounded nonloading position guards; these new files cannot supply
that authority. Registration is the ordinary matching `ModBlocks.AIRLOCK_DOOR`
and `ModItems.AIRLOCK_DOOR`. The provider exposes `language(boolean)` for the
Root-owned locale hook and never writes locales or shared tags itself.

All code and three pixel grids are original NEW/MIT work under the committed
pre-authoring provenance. Native model parents are referenced by ID only.
No existing grid or official asset is imported, traced or reused.

## Verification boundaries

Local C has less than 10 GB free. No Java, Gradle, native server or client runs
are permitted in this authoring task. Tests are declarations until actual
qualified execution. Root must bind the central dependencies, independently
review the exact source, and execute hosted clean build, twice DataGen and full
GameTests. Native restart, survival clients and V1/V2 remain open.

The installed-manager GameTests may read private identities by reflection:
`AtmosphereRuntime.manager`, `AtmosphereManager.levels`,
`AtmosphereLevelService.coordinator` and `VolumeScanCoordinator.completed`.
They do not write these fields, install/replace a manager, clear shared queues,
change inherited budgets or call `markDirty`. Bounded existing scan methods
establish controlled installed-service witnesses; these are not proof of a
natural server-tick schedule. Registered native door callbacks perform the
revocation before any subsequent manager/service tick.

## Implemented behavior and resource scope

`AirlockDoorBlock` delegates ordinary iron-door placement only when the supplied
switch is true. Both-half consistency is checked without loading: opposite HALF,
same block/FACING/HINGE/POWERED/OPEN, closed pairs SEALED, open pairs TRAVERSABLE;
wrong/missing/mismatched pairs OPEN and unavailable positions UNLOADED. Mixed
OPEN states are mismatched, never sealed. The five specified native mutations
invalidate local and state-designated counterpart before their single delegate;
`setOpen` does so only for an actual supplied-state OPEN change. No query invokes
the runtime bridge and no mutation reads the counterpart to choose dirty cells.

`V180AirlockData` owns 16 outputs: one 32-variant blockstate, eight native-template
ID-reference models, one inventory model, three original 16x16 RGBA textures,
lower-half self/explosion loot, six-iron/three-door recipe and iron-OR-recipe
unlock. The two block grids are opaque; the independently drawn inventory grid
has transparency. The existing pixel/PNG encoder is reused, not its artwork.
The Root-owned locale hook consumes the one bilingual name from `language`.
Native model render orientation, native provider equality and acquisition are
not established by the static controls.

## Added tests, all unexecuted

Four JUnit methods in `AirlockDoorBlockTest` cover the false gate before context,
symmetric closed/open pairs across all state combinations, malformed pairs and
loaded/build-height-before-read guards. Its stdlib LevelReader proxy rejects any
unlisted query; it is a query fixture, not a fake installed manager or native
world proof. Five provider JUnit methods cover all variants/paths, literal
recipe/unlock/loot, fresh results/bilingual names, RGBA/CRC/palette and all four
provider flag modes with deterministic repeat output.

Seven native GameTest declarations cover:

1. Target/player-scoped actual EntityPlaceEvent cancellation around native
   registered item use in survival/creative, unchanged source/tag/both halves
   and selected surrounding states, plus ordinary creative source conservation.
   The temporary listener is removed in `finally` and never uncancels events.
2. Native false-mode placement rejection, true-mode ordinary placement/debit,
   and disabled existing sealing/open/close/removal.
3. Native redstone and support repair of both halves with the switch false.
4. Registered loaded adapter priority, malformed pairs, no-loading query,
   read-only metric checks and every native BlockState roundtrip.
5. Native survival/creative removal of either half and exact ordinary drops.
6. Exact crafting and 64 finite seeded lower/upper native explosion-loot cases.
7. Both opposite-half mirrors of installed cached/in-flight/unconsumed-completed
   scan revocation behind an unrelated dirty backlog, eight subsequent controlled
   ticks without stale supply, and repair/close followed by installed supplied-air
   recovery within 32 controlled service ticks.

The seventh test requests one allocated Moon fixture chunk, removes only its own
temporary force flag, and retains any pre-existing force flag. Each sequential
case owns at most 160 BE-free saved cells, creates its own vent and 25 native door
pairs for a backlog beyond 256 but below 8192, and restores every saved state on
success or failure. It reads the existing installed service; direct scheduling
and phase ticks are bounded test setup (one or 64 inspections), not natural
server tick evidence. It never clears another service/queue or changes budgets.
Fixture item drops are observed in a local box, preserve earlier UUIDs and are
removed only as task-created synchronous drops; cleanup retains/rethrows the
first error after attempting remaining restorations. FakePlayer instances are
detached test actors, not real-client evidence.

The unavailable-counterpart branch is covered by the interface-only JUnit
fixture. Vertical halves share X/Z and therefore one native chunk; this handoff
does not pretend to unload them independently. Real restart and V1/V2 are open.

## Actual author commands and failures

Evidence leaf:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18a-airlock-author-20261007-c16-5b806e`.
PowerShell reads and Git status/HEAD/worktree reads preceded writing. Git reads
confirm fixed HEAD, empty index and no pre-existing tracked-file changes.
`git diff --check` exits 0 but does not inspect these untracked additions;
the static helper separately checks new-file whitespace and terminal LF.

`python -B check01.py static01` exits 1 due solely to a guessed version-document
locator (`docs/versions/v1.8.0.md`). Original helper and stdout/stderr/exit are
retained. A fresh `check02.py` changes only that locator to the actual
`docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md`; `python -B check02.py static02`
exits 0 with 32 finite static controls and nine JUnit/seven native declarations.
These are not executed Java tests, PNG generation, compiled API or native proof.
Final postcheck identities/commands and thin baseline patch are external.

Two earlier Get-Content probes used absent guessed filenames
`MinecraftLoadedWorldAdapter.java` and `IndexedVolume.java`. Their original tool
failures are conversation/tool observations, not independently exported raw
process logs. Actual adapter/service/coordinator files were subsequently read;
no source verdict or behavior was inferred from those locator failures.

## Remaining integration and Required Gates

Root must supply the exact central runtime/registration/adapter/data/language/tag
joins, independently review this source and execute the exact committed source.
No worker stage/commit/push, generated write, registry edit, dependency install,
Java or native execution occurred. Native compile/GameTests, native generator
repeat/equality, cancelled-place and drop assertions, server/restart, actual
survival clients, GPU and multiplayer remain unverified. No whole-version
Required Gate is satisfied by this handoff. The next task is the current-v1.8
independent actual-source review and qualified hosted execution, not delivery.
