# TASK W73: the optional clearance cells, and the intermittent pad-cancellation GameTest

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w73-clearance-pad-flake` (from the shared branch) |
| Branch | `codex/v1.8.0-w73-clearance-pad-flake` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W73.md` (new) |

Two independent parts. Give each part its own commits.

## Part 1: the `optional` clearance cells for the C16c and beacon patterns

W55 (W01 review L2) wrapped the arc furnace's null cells in the closed `optional` matcher, so that a
block from another mod that reports `isAir()` no longer unforms it (`records/W55.md` section 4). The
C16c patterns (`V180C16cMachineData`) and the planetary beacon pattern still use the bare clearance
tag.

- **Root's decision:** wrap their null cells the same way. Do not touch the research patterns
  (`ResearchPatterns`, frozen by W21a and pinned by the `observatory.json` fixture).
- Regenerate with `runData` under the lock. Only the affected `machine_patterns` files should change.
- Test:
  - for each changed pattern, an untagged `isAir()` block on a null cell forms the machine, and another
    mod's block with a block entity still refuses, as `ArcCuttingDataTest` does;
  - the packaged recipe graph still models the patterns (`PackagedRecipeGraph`).

## Part 2: the intermittent `cancellationReleasesExactlyOneTransferClaim`

`LandingPadGameTests.cancellationReleasesExactlyOneTransferClaim` (`rocket/server/LandingPadGameTests.java`
line 156) failed once on hosted run 38400162712 at `01e6b457` ("Pad fixture/flight did not become
ready") and passed on rerun.

- Find out why. The tick pacing root cause of 2026-10-10 (GameTest ticks run unthrottled against
  asynchronous chunk loading; `GameTestTickPacer`) is a likely suspect.
- Reproduce it under load, if you can: run the batch alone several times while another JVM is busy.
- Fix the cause. Do not extend a timeout or weaken an assertion (AGENTS.md section 5); W64's
  `GameTestTickPacer` change shows the accepted pattern.
- If you cannot reproduce it, say what you tried, and make only a change you can justify from the code.

## Commands

- The whole unit suite once.
- `runData` under the lock (part 1).
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- The research patterns, workflows, status files, the ledger and ADRs.
