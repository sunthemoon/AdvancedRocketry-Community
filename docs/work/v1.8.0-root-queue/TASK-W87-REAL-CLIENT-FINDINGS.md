# TASK W87: fix the open real-client findings outside the rocket path (parallel bugs)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Do not start
subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w87-client-findings` (from the shared branch) |
| Branch | `codex/v1.8.0-w87-client-findings` (local only; Root merges) |
| Report A | `D:/GitHub/ARCE-Task-Evidence/pre-v18-client-20261006/REPORT.md` (SHA-256 `4bcee6e655da2a5cf50ab61bae324cb8c72398224dc6dd40bfbf205377b30aa3`), section 4, findings F1 to F10, with screenshots under `evidence/` |
| Report B | `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/REPORT.md` (SHA-256 `6b20e44ccf171de2c9372e788c24475e4227eb6ec26b3390e9f7dbf81c0dc85a`), section 4, findings 1 and 2 |
| Record | `docs/work/v1.8.0-root-queue/records/W87.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W87/` |

Both reports came from real clients on this machine. Their findings are not yet in the repository's documents. First
check that each finding still exists at your base commit; if one is already fixed, record where and skip it.

## What to do (one commit per finding, each with its tests)

1. **A-F1, railgun effects never reach clients.** `RailgunBlockEntity.triggerEvent` returns true only on the client, so
   `ServerLevel` never broadcasts the block event. Return true on the server for the launch and arrival events only,
   and add a GameTest that the server accepts both events and refuses an unknown ID.
2. **A-F4, the accretion disc is invisible from above.** Make `AccretionDiscRenderer` draw the ring visibly from both
   sides without changing its look from below.
3. **A-F5, A-F6 and B-1, text overflow at GUI scale 2 and 3 in English.** The railgun, black hole generator, laser
   drill, laser target, satellite terminal, satellite assembly and solar generator screens. Prefer a shared, tested
   helper that keeps a line inside its box (shorten with an ellipsis or wrap, with the full text in a tooltip) over
   moving each label by hand; shorten English text where the report shows it is too long. Keep every reason code
   visible. Unit-test the helper's width arithmetic.
4. **A-F7, refusals hidden behind open screens.** A refusal or progress message sent to the action bar while one of
   this mod's screens is open must also be readable inside that screen. Client side only; the server's decision and
   its message stay the same.
5. **A-F10, small items:** hide the life-support HUD while the F3 debug screen is open; show the orbital laser drill's
   body as its translated name, not the raw ID; replace developer wording (such as `coordinate_space=absolute`) in
   player-facing rocket assembly failure text, if that text is outside the rocket code (see below).
6. **B-2, the disabled jackhammer gives no feedback.** When the server has disabled it, tell the player once when they
   start to break a block (rate-limited), not only when a break completes.

## Write scope

The classes and screens these findings name, a new client helper and its tests, new GameTests, and appended or
changed entries in `en_us.json` and `zh_cn.json` for these strings only. **Do not touch** anything under the `rocket`
or `resourceescrow` packages, `ModEntities`, or any saved format: W11b2 is rewriting that code. If a fix needs them
(for example the rocket entity's name in `/data`), record it as deferred.

## Commands

- `gradlew.bat --offline compileJava`, targeted unit tests and the new GameTests while you work.
- Once at the end, under the heavy-JVM lock: the whole unit suite, `runData` with `git diff --exit-code`, and
  `runGameTestServer` with `python -B scripts/check_gametest_log.py` on its log.
- `python -B scripts/validate_repository.py --require-approved-identity`. Link only to files Git tracks; write other
  paths as code spans.
- Commit to the branch. Do not push.

The screen changes are client-visible. Record which ones need a V1 check in the final campaign; do not claim one.

## Not in this task

- A-F2 (Moon landing height, waits for W83), A-F3 (rocket position sync), A-F8 (Cygnus X-1 appearance), A-F9 (the
  Moon's fixed time, an owner question), B-3 to B-5, and the first-connection timeout.
- ADRs, the risk register, status files and the ledger (Root).
