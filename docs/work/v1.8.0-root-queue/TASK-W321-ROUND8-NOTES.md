# TASK W321: round 8's small findings

Root: Claude. Date: 2026-11-07. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w321`, branch `codex/v1.8.0-w321`, created by Root from the shared head |
| Decision | Root decisions R379 and R380 (QUEUE row W321) |
| Background | W278 round 8 on the JAR of `b35b8d087` (the tree of `dcc0baa82`): `D:/GitHub/ARCE-Task-Evidence/v18-sp-20261104/REPORT.md`, section "第 8 轮", findings 8-1 to 8-3, with screenshots under `evidence/r08-ds2/` |
| Record | `docs/work/v1.8.0-root-queue/records/W321.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W321/` |

## What to do

1. **Finding 8-2 (low).** `/arcepad list` on a deleted or unknown station, or one the player may not visit, replies with
   the registration refusal text (owner or operator, a valid name and so on). The shared `refused` key is used at
   `station/pad/LandingPadCommands.java` lines 83 to 86. Give `list` its own refusal that says the station is unknown or
   not visitable, as W291 did for `select`. Use the existing data generator for the language keys (en_us and zh_cn),
   and add a test.
2. **Finding 8-3 (information).** With the warp controller screen open during a countdown, the screen's notice bar stays
   at "in 10 s" instead of counting down, and lingers about a second after Cancel; at GUI scale 3 it covers the
   panel's bottom border, though no text.
   - Find out why the mirrored countdown does not update (for example, whether repeated action-bar messages replace the
     mirrored one), and make it follow the latest message if that is a small client change.
   - Keep the bar clear of the panel's border at GUI scale 3.
   - No packet or protocol change; if one would be needed, say so in the record instead.
3. **Finding 8-1, Root decision R379.** W311 sized the rocket's box from its structure, as upstream's rocket was. A
   block placed inside that box, beside a narrow body within the fin span, cannot be used by hand from outside: up close
   the click opens the rocket console, and from farther away nothing opens.
   - Root keeps this trade-off; it matches upstream.
   - Add one short paragraph to the player documentation that covers rockets (find the right file under `docs/`):
     place loaders, chests and other devices outside the rocket's outline, because the rocket takes clicks inside it.
     Do not change the box.

## Verification

- The new test for item 1, failing before the change.
- A unit or layout test for item 2, if you change it.
- The landing pad command tests and the station controller tests.
- `./gradlew build` with all unit tests.
- runData, then `git diff --exit-code` after committing the generated files.
- The full GameTest run with `scripts/check_gametest_log.py`, under the heavy-JVM lock (backslash lock path, as in
  WORKER-RULES).
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

The rocket's box (8-1 stays as decided), the railgun tooltip's body path (8-4, by design under ADR-054), the elevator
refusal's rule name (needs a menu-data change), the ledger, the status file and QUEUE.
