# TASK W294: check the information findings against the design and fix what deviates

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w294`, branch `codex/v1.8.0-w294`, created by Root from the shared head |
| Decisions | Root decisions R327 and R331, with round 5 finding 5-5 and round 6 finding 6-4 (QUEUE row W294) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, rounds 3 to 6 (in Chinese; screenshots and logs named there under `evidence/`) |
| Record | `docs/work/v1.8.0-root-queue/records/W294.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W294/` |

## The items

For each, find what the governing ADR or design says, state in the record whether the behaviour matches, and change
only what deviates, with a test that fails before the change. Where the behaviour is as designed, say so and change
nothing (a wording fix in a player-visible message counts as a change and needs the translations).

1. **Atmosphere status (3-7).** `/arce atmosphere status` reports `VACUUM` on Venus (9.2 atm) and on Tau Ceti g
   (1.4 atm), where the analyzer and the HUD tell those cases apart.
2. **Warp targets (3-8).** The warp target list offers `space` and an undiscovered `cygnus_x1`.
3. **Star map fuel (3-8).** The star map quotes abstract fuel in mB.
4. **Venus without a helmet (3-8).** For about a second without a helmet, Venus (heat, pressure, sunlight, no oxygen)
   cost no more health than Mars.
5. **Laser gun recipe (W276R note L1).** Review the laser gun's recipe against the design's progression (ADR-066 and
   the C18 equipment records).
6. **Elevator audit lines (4-4).** The space elevator's bind and ride audit lines always write `owner=-`
   (`ElevatorPairs` around line 210 passes null) where ADR-054's format has the owner.
7. **Laser drill after a restart (5-5).** `device inspect` shows `settled=false` after a restart in logical mode,
   because `linkSettled` is not saved; no data is lost. Decide whether it should be saved or recomputed, as the design
   intends; a saved field needs a schema version and a migration test, and then a short design note at the top of the
   record (say so in your answer; Root has it reviewed).
8. **Standing on a 0 % station platform (6-4).** A player standing on a station platform at 0 % gravity reports
   `OnGround 0b`, so jumping does nothing (by design) and walking works by air control. Check whether anything that
   depends on standing on the ground misbehaves there (fall damage, block interaction, sneaking at edges, the safety
   return's support check, suit motion, seats) and fix only a real fault.

## Verification

The new tests; the existing atmosphere, warp, star map, elevator, laser drill, equipment and station tests touched;
`./gradlew build` with all unit tests; `runData` with no unexpected diff if any text changed; the full GameTest run
with `scripts/check_gametest_log.py` under the heavy-JVM lock if any main-source class changed; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Rider position (W292), screen layout (W293), new content, the ledger, the status file and QUEUE.
