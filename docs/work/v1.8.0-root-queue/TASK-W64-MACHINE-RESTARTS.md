# TASK W64: restart tests for the four machines without one (W62 slice S1)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w64-machine-restarts` (from the shared branch) |
| Branch | `codex/v1.8.0-w64-machine-restarts` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W64.md` (new) |
| Specification | `records/W62.md` section 7, row S1 |

Add the restart GameTests that W62 section 4 found missing:

- **The crystallizer, the chemical reactor and the precision laser etcher.** Save mid-job, load and
  continue, with exact resources, FE and progress, as `ArcCuttingGameTests` line 189 and
  `C16cMachineGameTests` line 135 do.
- **The atmosphere detector.** A mode-root round trip and a measurement, plus separate happy and
  failure GameTests.
- **Optional:** a real chunk unload and reload for one classic multiblock, as
  `ForceFieldUnloadGameTests` line 26 does, if it fits.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker. A new expected WARN
  or ERROR needs its own manifest entry with an exact count and a reason.
- At the end, delete your generated `build/gametest/world`, native copies and old jars with a plain
  `rm -rf`, and make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths;
  Root deletes them.

## Not in this task

- Workflows, `build.gradle`, status files, the ledger and ADRs (Root). If the slice needs a workflow
  line, write the exact line in your record; Root adds it.
- Any production behaviour change. If a test finds a production defect, stop and report it with the
  evidence. The exception is a fix of a few obvious lines: give it its own commit and say so.
