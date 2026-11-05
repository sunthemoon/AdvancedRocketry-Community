# TASK W306: two tests the pad selection review asked for

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w306`, branch `codex/v1.8.0-w306`, created by Root from the shared head |
| Decision | Root decision R351 (QUEUE row W306) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W291R/REVIEW.md`, notes N1 and N2 (with their mutants M9 and M4) |
| Change it follows | [W291](records/W291.md), merged at `a88dd3207` |
| Record | `docs/work/v1.8.0-root-queue/records/W306.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W306/` |

## What to add

1. **N1.** No test checks that the server menu puts the indexed pad name into the flight plan packet it actually
   sends: replacing `padName(plan)` with `""` at `rocket/menu/RocketFlightMenu.java` around line 156 (mutant M9) passes
   every test, and the only GameTest name check calls `menu.padName(...)` directly. Add a test that fails on that
   mutant: for example, move the packet construction into a small method that `broadcastChanges` calls (a pure
   extraction, no behaviour change) and assert in `LandingPadSelectionGameTests.remoteUnloadedChoiceIsRecordedWithoutLoadingOrClaiming`
   (or a unit test) that the packet's `padName()` is the chosen pad's name.
2. **N2.** The decode-side name bound in `RocketFlightPlanPacket.decode` is not tested on its own: replacing
   `readUtf(LandingPadEntry.MAX_NAME_LENGTH)` with `readUtf()` (mutant M4) passes, because the record constructor
   rejects the long name afterwards. Add one decode case whose frame declares a name longer than the bound and
   expects the bounded read itself to fail (a `DecoderException` from `readUtf`), so that a later loosening of the
   constructor's check would not hide a loosened read.

Show each new test failing on its mutant (apply the mutant on a throwaway change, never committed) and passing on the
real code. No production behaviour, packet layout or protocol version changes.

## Verification

The new tests; the landing pad, flight menu and network unit tests and the `pad_selection` GameTests; `./gradlew
build` with all unit tests; `python -B scripts/validate_repository.py --require-approved-identity`. A full GameTest
run is needed only if you change a GameTest or main-source class (then under the heavy-JVM lock with
`scripts/check_gametest_log.py`).

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Notes N3 to N7 (done or no change needed), the ledger, the status file and QUEUE.
