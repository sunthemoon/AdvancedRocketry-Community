# TASK W28: audit GameTests for scheduling from inside scheduled callbacks

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first, especially the
rule "No GameTest scheduling from inside a scheduled callback".

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w28-gametest-scheduling` |
| Branch | `codex/v1.8.0-w28-gametest-scheduling` (local only; Root merges) |
| Kind | Parallel bug fix (test code only) |
| Record | `docs/work/v1.8.0-root-queue/records/W28.md` |

## Problem

Vanilla 1.20.1 `GameTestInfo.tickInternal` iterates its fastutil schedule map while it runs the
`runAtTickTime` callbacks. These calls insert into that map:
- `helper.onEachTick`, which inserts one entry per remaining tick;
- `runAtTickTime`;
- `runAfterDelay`;
- `succeedWhen` and the other scheduling helpers.

Calling any of them from inside such a callback can rehash the map. The server then crashes
with `NullPointerException ... "this.wrapped" is null` in `GameTestInfo.tickInternal`. W21c's
hosted run 38353212060 crashed this way. Whether it happens depends on timing and map size, so a
test can pass for a long time and then crash CI. W21cF reported another candidate:
`ForceFieldUnloadGameTests.poll` calls `runAfterDelay` from a `runAfterDelay` callback.

## Scope

1. **Audit.** Check every GameTest under `src/main/java` for any scheduling call reachable from
   inside a scheduling callback, including through helper methods.
   - List each occurrence in your record, with file and line.
   - Read the mapped jar's `GameTestHelper` and `GameTestInfo` with `javap` to confirm which
     helper methods insert into the map, and cite them.
2. **Fix each occurrence.**
   - Restructure it to one callback registered at test start, which moves between steps with a
     mutable state variable.
   - Keep every assertion, timeout and log expectation unchanged, and the test's meaning
     identical.
   - Keep the `GameTestTickPacer` pacing where it exists.
3. **Guard.** Add a check that fails when a new nested scheduling call is introduced:
   - Preferred: a small test-side scheduler wrapper that throws when it is called re-entrantly
     during a tick callback, adopted by the fixed tests.
   - Otherwise, a static source scan in a unit test.

   Record which you chose.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- Run the assertion audit idea yourself: list in your record every changed line that contains an
  assertion, and show it is unchanged or stricter.

## Not in this task

- Production code.
- Status files and the ledger.
