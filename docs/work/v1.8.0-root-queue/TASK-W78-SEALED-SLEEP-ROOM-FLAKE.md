# TASK W78: the intermittent sealed sleep-room GameTest

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w78-sealed-sleep-room-flake` (from the shared branch) |
| Branch | `codex/v1.8.0-w78-sealed-sleep-room-flake` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W78.md` (new) |
| Evidence | The failure appeared in W11b2 continuations 09 and 14 ("The large room was not pending after one production scan tick"); their logs are `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09/resumed-01/gametest-latest.log` and `W11b2-C14/delivery/` (the checker log names the failure) |
| Related | `records/W74.md` on branch `codex/v1.8.0-w74-natural-beds-flake` (being merged): the natural-beds fixture's one-tick false readiness after `placePreparedVent`'s manual vent tick, and its two-CPU reproduction method |

`SleepRespawnGameTests.sealedBreathableRoomsAdmitSleepButAPendingRoomDoesNot` (`gametest/SleepRespawnGameTests.java`
line 148, batch `sleep_rooms`, `timeoutTicks = 60`) builds two supplied rooms on the Moon and in space, then a
large Moon room whose 343 cells exceed one scan task's 256-observation budget, and expects that room to be
PENDING on the next test tick (line 229). It fails intermittently.

## What to do

1. **Find why the large room can be other than PENDING on that tick.** Candidates to check:
   - W74's cause: the manual vent tick makes a room look supplied before the vent's own block entity ticks
     (`LevelChunk.isTicking` needs loaded entity sections), and the next atmosphere tick drops the vent;
   - more or fewer than one production scan tick between placing the large room and the assertion;
   - the pending chunk's readiness under load.
2. **Reproduce it** with W74's method if you can (the GameTest JVM pinned to two logical CPUs).
3. **Fix the cause in the test fixture.** Do not raise a timeout, weaken an assertion, or split the test to hide
   its cost (AGENTS.md section 5). Production behaviour stays unchanged; if the only honest fix is in production
   code, stop and report.
4. Check the other `placePreparedVent` users that wait for a supplied room, as W74's record suggests, and list
   which share the same false readiness. Fix only this test; list the others for Root.
5. Say what you tried and what each run showed.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the heavy-JVM
  lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- The natural-beds tests (W74), the first-Moon-landing and spawn-egg tests (W76), production behaviour,
  workflows, status files, the ledger and ADRs.
