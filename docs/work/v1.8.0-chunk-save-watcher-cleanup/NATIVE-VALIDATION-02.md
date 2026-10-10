# Watcher native fixture feasibility and implementation 02, ordering revision 03

Date: 2026-10-11. Status: READY_FOR_REVIEW with explicitly incomplete native
coverage. Base and unchanged HEAD:
`64c295e777ae902d98a5b1b886c9067102393e8e`.
Branch: `fix/v1.8.0-chunk-save-watcher-cleanup`.
Worktree: `D:/GitHub/arce-v180-watcher-cleanup-20261011-01`.

The additional allocation and its ordering revision permit changes only to
`ChunkSaveWatcherGameTests.java` and this record. The six previously reviewed
source/test/doc inputs remain read-only. No changes are made to callers,
assertions, timeouts, log expectations, build or registry files, world data,
public IDs, schemas, native performance budgets or other worktrees. This is
not an attribution or repair of the hosted CompoundTag CME or a native
IOWorker storage-ownership test.

## Observation ordering revision

The preceding revision put decisive terminal assertions in AfterBatch. This
revision moves those assertions to an additional ordinary required checking
test in the same success batch. The checking test remains active while
`thenWaitUntil` waits for the driver's real terminal witness; a missing,
pending or failed witness cannot count as success. Its own still-running
helper then checks closure, exact Level identity, a distinct native tick,
post-terminal bus history and the labelled stale callback probe before
`thenSucceed` can complete it.

AfterBatch now performs release only. It does not decide whether the watcher
passed a lifecycle check and does not assume it runs after listeners appended
while the driver was running. The live required checker keeps the batch
incomplete until the actual driver witness is available and all decisive
assertions have executed. Immediate validation errors close the owned slot;
pending-witness timeouts remain genuine required-test failures and release
through normal batch cleanup or matching stop. Native ordering is still
unexecuted, so this is an implementation mechanism rather than passing evidence.

## Actual facilities inspected

The local published Forge 47.4.10 sources archive is 1,621,504 bytes with
SHA-256 `918a11bdfceace2752d4c29bddbdf327981e1f6a1e1f0675f23e5fbf01e226c0`.
Only six unique members were read, totalling 13,670 bytes:

- `net/minecraftforge/event/level/ChunkDataEvent.java`: documents the save event
  and its explicit chunk/Level/tag constructor.
- `net/minecraftforge/gametest/ForgeGameTestHooks.java`: shows ordinary holder
  method scanning and registration.
- `patches/net/minecraft/gametest/framework/GameTestRegistry.java.patch` and
  `GameTestServer.java.patch`: show registration and server-start integration,
  not a nested lifecycle driver or subscriber-removal API.
- `net/minecraftforge/event/server/ServerLifecycleEvent.java` and
  `ServerStoppingEvent.java`: establish server identity storage and the
  supported event constructor, including no null rejection in that constructor.

No decompilation, disassembly or full generated engine-source read was used.
EventBus 6.0.5 was found only as a local binary artifact; no published local
EventBus source cache was found. The published Forge excerpts inspected here
do not contain a GameTestInfo implementation or a supported listener-list
inspection facility. No API or field name is guessed for those missing parts.

Adjacent tests establish the development-only helper `testInfo` reflection,
native `runAfterDelay`, terminal listeners, AfterBatch ownership and typed
server-stop listener cleanup. Existing `LaserTargetGameTests.chunkSaved`
establishes native serialization followed by a posted save event. The new
fixture uses `getChunkNow` first and never loads a missing chunk.

Production listener inspection found that `ClassicSaveProtection` rejects a
Level/chunk identity mismatch and quiesces Levels on a server-stopping event.
`EndgameService` also consumes save observations. Therefore foreign-Level
save events and synthetic server stopping are not posted to the global bus.
They are limited to explicit watcher callback probes.

## Declared native checks and evidence boundaries

Three ordinary required GameTests are declared, each with a 40-tick timeout.
The installation driver and terminal checker share the success batch; the
identity probe has a separate batch. None is intentionally failed, optional, retried, or a
disguised nested expected-failure test. No log-producing negative case or
logger-rule change is introduced. All are NOT_RUN in this worker stage.

| Boundary | What the new source can demonstrate when run | What it does not demonstrate |
|---|---|---|
| Installation | `start(helper, pos)` on a normal native helper, then a matching save event posted through `MinecraftForge.EVENT_BUS` produces the expected observation. | A durable disk save, storage ownership or restart recovery. |
| Automatic success | The ordinary driver succeeds through `helper.runAfterDelay(1, helper::succeed)`, not by calling a watcher terminal method. A live required checker awaits the independent witness and asserts closure before it can succeed or batch cleanup can release the slot. | Assertion/timeout failure cleanup or all GameTest callback ordering. |
| Post-terminal bus delivery | The live required checker posts a later matching save event at a distinct game tick and checks unchanged closed history before its own success. | Actual subscriber removal: an inert subscriber can also preserve history. |
| Stale callback authority | A labelled direct `onSave` probe observes unchanged closed history. | Native delivery or event-bus detachment. |
| Foreign native Level | Direct `onSave` with the registered Moon Level does not record a save, without posting the mismatched Level/chunk pair to production handlers. | Native global-bus delivery of that foreign event. |
| Foreign/null server identity | A direct stopping callback with null leaves the watcher open. Null is explicitly a malformed identity, not a second MinecraftServer. | Behavior with a second real native server or real foreign-server notification. |
| Matching server identity | A direct stopping callback using the owned native server closes the watcher, followed by idempotent explicit cleanup. | A genuine server-stop event dispatch or native shutdown. |
| Assertion/timeout failure | No native failure fixture is added because no inspected supported lifecycle-driving facility establishes a bounded isolated test without altering required outcomes or logs. | Native failure/timeout evidence remains unqualified. |
| Actual subscriber detachment | No listener-list inspection is added because its relevant local published API/source is unavailable under this allocation. | EventBus removal remains unqualified even if the closed-history probes pass. |

The original pure-state tests remain useful but are not substitutes for the
unqualified native boundaries above. Runtime execution can qualify only the
boundaries actually driven and observed; source presence is not passing evidence.

## Bounded reflection and lifetime justification

The success witness reads `GameTestHelper.testInfo` once for its owning helper,
as adjacent fixtures already do, only to attach its own terminal witness. It
does not alter engine time, error state, task queues, structures, registries or
listener collections directly.

The fixture reads exactly one private `observations` field from each watcher
that it created. This is read-only observation of this repository's owned
test-support object and allows `isClosed()` to distinguish genuine closure
from same-tick duplicate history. It adds no production accessor, AT, coremod,
event-bus interception or broad engine reflection.

The only static mutable field is one `TerminalObservation` slot owned by the
success driver and observed by its required checker. Its acquisition rejects
a second owner. The slot retains one Level, watcher, state, position and scalar
terminal result, but no helper or growing collection. AfterBatch releases the
slot through `close`, which unregisters its own server-stop fallback and clears
the slot in a finally block. Partial installation and decisive validation failures
close only the created watcher/fixture, retaining primary and cleanup errors.
Pending waits are not prematurely treated as validation failures; their
40-tick terminal/batch cleanup remains mandatory. Matching native server stop
is the abort fallback. The identity test has method-local ownership and
finally cleanup.

Normal global save probes serialize only an already loaded, matching native
chunk and dispatch an ordinary save event, as existing fixtures do. There are
at most two such posts per test. They are observation events, not disk writes.
No saved tag is retained by the fixture; no player/world state, forced chunk,
native service root or admission guard is reset. Native failure injection,
second-server construction and event-bus internals are deliberately absent.

## Actual source checks and unexecuted work

Published archive inspection belongs to the preceding allocation; this
revision uses only bounded named source/manifest reads and `apply_patch`.
The preceding source-only checks exited 0. The revision Python checker also
exited 0 and independently verified all six frozen hashes, two-path write
scope, unchanged HEAD/branch, three ordinary declarations with unchanged
per-test bounds, checker-before-success source structure, cleanup-only
AfterBatch, exact installation/identity/helper preservation, and postimage
sizes/hashes/whitespace. `git diff --check` exited 0. A minimal actual revision
patch is kept instead of retaining full temporary preimage copies. Source-
shape checks do not prove Java syntax or native outcomes; actual commands
and any errors are recorded in the fresh revision evidence leaf.

No JVM, Gradle, compilation, server, network, nested delegation, Claude,
OpenPet, staging, commit or HEAD movement is performed. Java compilation,
the three declared GameTests, ordering, AfterBatch cleanup, native save-bus behavior and all
unqualified boundaries are NOT_RUN, not passed. No version, delivered-state
or Required Gate conclusion is changed. Root separately schedules runtime
validation after independent actual-diff review under a RUN-ALLOCATION.

Prior source-only evidence remains immutable in
`C:/Users/Administrator/AppData/Local/Temp/arce-watcher-native-fixture-20261011-01/`;
its old source hashes identify the superseded ordering revision, not these
postimages. Revision evidence:
`C:/Users/Administrator/AppData/Local/Temp/arce-watcher-native-ordering-20261011-01/`.
The next action is review and bounded validation of this same v1.8 fixture
slice, with native failure/timeout and actual removal evidence still needing
a supported independent facility or a precisely recorded feasibility gap.
