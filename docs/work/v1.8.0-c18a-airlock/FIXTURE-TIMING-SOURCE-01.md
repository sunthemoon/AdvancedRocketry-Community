# C18a-AIRLOCK-FIXTURE-02 source handoff

Version: v1.8.0. Source base: `cbbb78e1fdc8dd88128162d43d88125c7aee9b60`.
This is a fixture lifecycle experiment, not an established production repair.

## Owned files and behavior

The author owns only `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/AirlockDoorGameTests.java`
and this record. Root owns the assignment, integration and Git publication.
The other six GameTests, production code, providers and resources are outside
the write scope.

The installed-supply subject retains its six mirror/phase cases. A finite
sequence builds each owned shell and door pair, then installs and first observes
its vent one ordinary GameTest/world tick later. All six delayed callbacks are
registered before world edits, so a running callback does not mutate the native
scheduled-runnable map. The existing subject deadline remains 40 ticks; its
initial/recovery limits remain 32 controlled passes and its no-republication
limit remains eight passes. No scan retry, direct dirty call, queue clearing or
manager replacement is introduced.

## Deferred ownership

One private fixture sequence retains at most one active fixture and a force
ownership flag for one chunk. A pre-existing forced chunk is never unforced.
The same native GameTestInfo receives a terminal listener before world edits.
Success, synchronous/deferred assertion or exception, and framework timeout
all attempt fixture restoration and release of newly owned force state.
Cleanup failures are attached to the original failure when one exists; otherwise
they remain failures. Retired callbacks are inert. Native restoration can itself
fail, and source structure alone does not prove world restoration.

The existing read-only reflection helper locates `GameTestHelper.testInfo`.
This is version-bound fixture-only inspection, not a new atmosphere authority.
No reflective writes or native manager replacement are allowed.

## API evidence and verification

The exact Forge 1.20.1-47.4.10 mapped JAR is 19,355,242 bytes, SHA-256
`95eecc5985233d83a6571299f89f02de034267646da171f7b36a5be2d394d71e`.
Only the approved GameTestHelper, GameTestInfo and GameTestListener class members
were read in memory. Their declarations confirm public `runAfterDelay(long,
Runnable)`, `addListener(GameTestListener)` and `getError()`, the helper's private
final `testInfo`, and the three terminal/structure listener callbacks. The
selected native control flow marks timeout failure and dispatches `testFailed`
from GameTestInfo.tick; listener and scheduled-runnable exceptions are not a
native rollback promise.

Author evidence resides in
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-fixture-timing-author-20261007-c16-27b9e1`.
The final report supplies exact hashes, diff, static commands and preserved
failure qualifications. No local Java, compilation, GameTest or native replay
is permitted or claimed. Independent actual-diff review and committed-source
hosted execution remain required. The previous initial-supply failure is not
closed by this source change, and no version Gate is accepted.
