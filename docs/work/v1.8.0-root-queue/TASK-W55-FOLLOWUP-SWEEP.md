# TASK W55: sweep the open small review follow-ups (parallel bugs)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w55-followup-sweep` (from the shared branch) |
| Branch | `codex/v1.8.0-w55-followup-sweep` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W55.md` (new) |
| Source | `QUEUE.md`, "Next candidates", the "Parallel bugs" list |

[QUEUE.md](QUEUE.md) lists open follow-ups from earlier reviews. Their sources are the slice records
and reviews under `docs/work/v1.8.0-root-queue/records/` and `D:/GitHub/ARCE-Task-Evidence/root-queue/`.
This task sweeps these:
- W01 L1 to L3;
- W04 N4 to N6;
- W10-R1;
- W13 N1 to N3;
- W18 F8;
- W21b F9;
- W21c M1;
- W22 F8 to F10;
- `GroundSurveyScaleTest.startOnARootNearTheBoundStaysWithinTheSpikeBudget`, which once measured
  565 ms against its 500 ms budget under full-build load (`records/W11B2.md`, continuation 08).

## What to do

1. **Before code**, write a table in your record: one row per finding.
   - Quote the finding with its source, file and line.
   - Check it against the current shared branch, because many files changed since.
   - Classify it:
     - **FIX:** small, safe and in scope;
     - **ALREADY-FIXED:** say by which commit;
     - **NEEDS-DESIGN:** it touches persistence, protocol or save refusal, or needs an ADR;
     - **NEEDS-OWNER.**
2. **Fix every FIX finding** in its own commit, with a test that would have caught it. Don't drift
   into unrelated cleanup.
3. **For the GroundSurvey timing test.** Do not raise the budget; AGENTS.md section 5 forbids it.
   Find out whether the test measures something load-sensitive, and make the measurement robust in a
   way that keeps its intent, or classify it NEEDS-DESIGN with the evidence.
4. **For NEEDS-DESIGN and NEEDS-OWNER rows**, write what is needed, one paragraph each. Root turns them
   into tasks.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if generated data changes.
- `runGameTestServer` under the lock, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf` and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- W02F (the native restart and the locked rocket), the intermittent pad-cancellation GameTest, the
  5 ms atmosphere budget (needs an ADR) and the HotSpot crash.
- Status files and the ledger (Root).
