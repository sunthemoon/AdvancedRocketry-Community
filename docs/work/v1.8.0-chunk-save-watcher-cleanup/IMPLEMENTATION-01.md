# Chunk save watcher lifecycle implementation 01

Date: 2026-10-11. Task status: READY_FOR_REVIEW, source-only and uncommitted.
Base source: `64c295e777ae902d98a5b1b886c9067102393e8e`.
Branch: `fix/v1.8.0-chunk-save-watcher-cleanup`.
Worktree: `D:/GitHub/arce-v180-watcher-cleanup-20261011-01`.

## Scope and non-goals

This change gives the existing `ChunkSaveWatcher` an explicit test lifetime and
a bounded observation history. The two existing caller sequences change only
their `start` argument from the Level to the owning `GameTestHelper`.

This does not diagnose, attribute or repair the CompoundTag storage CME from
CI run 38064322640. It does not alter block entities, world saves, save tags,
endgame persistence, log expectations, chunk loading, native timeout or
performance budgets, public IDs, schemas, registries, build files or status
files. No upstream code or assets are imported.

The active development version remains v1.8 under accepted ADR-060; inherited
release acceptance remains v1.0 and the v1.7 handoff remains IN_PROGRESS.
Required Gates G0-G9 are not advanced by this source implementation.

## Design

`start(GameTestHelper, BlockPos...)` attaches a `GameTestListener` to the owner
before registering the Forge subscriber. It uses the same development-only
`GameTestHelper.testInfo` reflection already used by the adjacent atmosphere,
load-ordering and thermite fixtures. Partial installation failures close the
observer and retain cleanup failures as suppressed errors on the primary error.

The owning test's pass or failure callback closes the observer; the failure
callback also covers timeout termination. A HIGHEST-priority
`ServerStoppingEvent` callback closes only for the watcher's own server. Normal
terminal cleanup removes the observer without a static AfterBatch collection.
If execution bypasses the terminal callback, server stopping is the fallback.
The native ordering and reflection are still pending scheduled validation.

The pure-Java `ChunkSaveWatchState` owns history and detachment. Its synchronized
closure marks the observer closed before unregistering it, so an already
dispatched or reentrant callback cannot extend history. Cleanup is idempotent;
failed-test cleanup retains the original throwable, and an otherwise unowned
cleanup failure remains visible. If the Forge unregister API itself throws,
the observer is inert but native subscriber removal cannot be guaranteed.

One observer accepts at most two supplied positions/chunks and retains at most
2,048 distinct save ticks per chunk. Both existing caller timeouts are at most
900 ticks. Multiple saves in one tick are represented once: this preserves the
existing `any` and inclusive `between` existential meaning without adding a
save-count contract. There is no eviction and no inferred save. An overflow
latches an error and closes the observer without throwing into a chunk-save
callback; subsequent observation queries and explicit `stop` reject the
incomplete history. Both existing successful sequence tails retain that
explicit `stop` check. The limit only concerns test observations, never player
or world data, and introduces no save rejection mechanism.

Save callbacks check exact Level identity, record the same `level.getGameTime`
as before, and neither inspect nor retain event NBT. Server-stop callbacks check
exact server identity. No world edits, force loads, global world-state tracker,
batch reset or new protocol are introduced.

## Changed files

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/ChunkSaveWatcher.java`.
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/ChunkSaveWatchState.java`.
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/LaserTargetGameTests.java`: one caller line.
- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/RailgunGameTests.java`: one caller line.
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/ChunkSaveWatchStateTest.java`.
- This task-owned implementation record.

## Tests added

Ten deterministic JUnit methods cover inclusive save intervals, per-chunk
separation, ignored unwatched saves, success/terminal idempotence, frozen
history after stale callbacks, primary and suppressed cleanup failures,
reentrant callback authority, duplicate ticks, exact capacity on both chunks,
overflow rejection and input/query bounds. These tests exercise the pure state
and cleanup action, not a native Forge event bus or actual GameTest timeout.
They have not been executed in this worker stage.

## Commands and actual results

- `git status --short`, `git branch --show-current`, `git rev-parse HEAD` and
  `git worktree list`: confirmed the assigned clean checkout and base before
  implementation; HEAD has not moved.
- Bounded `Get-Content`, `rg` and metadata reads: inspected required governance,
  active-version and preceding-handoff records, watcher callers, adjacent
  lifecycle fixtures and existing JUnit conventions.
- `apply_patch`: wrote only allocated source/test/doc paths and fresh private
  evidence files.
- `git diff --check`: exit 0 before implementation and after source edits.
- `git diff --stat`, `git diff -- <three tracked source files>`,
  `git status --short`: source review confirmed only the allocated edits;
  unstaged new helper/test files are also included in the private hash manifest.
- `Get-FileHash -Algorithm SHA256`: recorded all five source/test inputs.
- `python -B C:/Users/Administrator/AppData/Local/Temp/arce-watcher-cleanup-20261011-01/static_checks.py`:
  exit 0. The source-only checker verifies HEAD/branch, allocated paths,
  unchanged caller bodies except the two start arguments, required source
  guards, the absence of adapter NBT/world edits, the pure helper and hard
  constants, ten declared tests, source hashes and the 1 MiB per-file limit.
  These are source-shape checks, not a compiler or runtime test.

Exploration failures are retained in the private command record: the assumed
lowercase version filename and assumed AT filename did not exist, one command
used invalid PowerShell brace syntax, and two oversized source-output cohorts
were truncated by the tool. Correct paths or smaller reads were used afterward.
No files were changed by those failures.

No JVM, Gradle, build, server, network, Claude, nested delegation, commit,
staging or HEAD movement was performed. `clean build`, `test`, `runData`,
`git diff --exit-code` after DataGen and `runGameTestServer` are NOT_RUN in this
stage, not NOT_APPLICABLE or passed. Root schedules reviewed runtime validation.
There is no dedicated/restart, visual, multiplayer or performance result here.

## Evidence and remaining concerns

Private evidence is in
`C:/Users/Administrator/AppData/Local/Temp/arce-watcher-cleanup-20261011-01/`:
`static_checks.py`, `static-checks.log`, `static-results.json`,
`static-checks-final.log`, `static-results-final.json`, `COMMANDS.md`
and `REPORT.md`. The manifest identifies actual source bytes; it does not turn
an uncommitted snapshot into delivered, accepted or Gate evidence.

The independently reviewed actual diff, Java compilation, JUnit execution and
native tests remain required. Native validation must confirm reflected terminal
listener installation, success/failure/timeout cleanup, matching and foreign
server/Level behavior, and subscriber detachment. The independent reviewer
must also inspect history-overflow handling and lifetime fallback rather than
infer native behavior from the source checker. Preserve strict log expectations
and all original assertions during that validation.

The implementation is not committed or integrated. The next work is the
current v1.8 slice's independent review and scheduled validation; no later
version is started and no Required Gate is marked passed.
