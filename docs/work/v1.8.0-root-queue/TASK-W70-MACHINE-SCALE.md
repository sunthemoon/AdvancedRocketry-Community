# TASK W70: many machines, idle and active (W62 slice S6)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w70-machine-scale` (from the shared branch) |
| Branch | `codex/v1.8.0-w70-machine-scale` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W70.md` (new) |
| Specification | `records/W62.md` section 7, row S6 (version document 10.2 and 13.1) |

## What to do

1. **Idle cost.** Form 16 controllers of each classic machine kind and let them sit idle. Check, per
   tick, that no recipe search and no pattern revalidation runs.
   - Use counters, as `BootstrapGameTests` line 558 does.
   - If a counter is missing, add a package-private counter seam in `ClassicMachineManager` that
     production never reads. Keep it gated and inert.
2. **Active work across unload.** Set half of the controllers working. While they run, unload and
   reload their chunks: a real unload, as `ForceFieldUnloadGameTests` line 26 does.
   - Check that every job resumes and completes.
   - Check that inputs, outputs, FE and progress are conserved, with nothing duplicated or lost.
3. **No timing assertion.** Record the measured tick costs in your record as observations only.
4. Put the tests in a new `src/main/java/.../machine/classic/runtime/ClassicScaleGameTests.java`, in
   their own batch.
5. If a controller loses or duplicates work across the unload, or re-searches recipes while idle,
   stop and report it with the evidence. Do not change production behaviour beyond the counter seam.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker. A new expected WARN
  or ERROR needs its own manifest entry with an exact count and a reason.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- Workflows, status files, the ledger and ADRs.
