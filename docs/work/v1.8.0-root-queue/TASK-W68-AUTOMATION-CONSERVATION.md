# TASK W68: automation conservation GameTests (W62 slice S5)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w68-automation-conservation` (from the shared branch) |
| Branch | `codex/v1.8.0-w68-automation-conservation` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W68.md` (new) |
| Specification | `records/W62.md` section 7, row S5 (version document 10.6 and 12.2: long automated production without duplication; automation interfaces do not duplicate) |

## What to do

1. **Find the machines.** List every v1.8 machine and device that exposes an item, fluid or energy
   capability: the classic machines, the kernel machines, the small plate press, the fluid devices and
   the generators. Give each one's capabilities with file and line.
2. **Write one GameTest per machine kind** in a new
   `src/main/java/.../gametest/AutomationConservationGameTests.java`.
   - Feed the machine through vanilla hoppers and droppers, or through a tank and the mod's pump for
     fluids. Supply power through the mod's own generator or a test energy source the existing tests
     already use.
   - Run 20 batches.
   - Save and load the machine's block entity once mid-run, the way the existing restart GameTests do.
   - Count before and after: input items, fluid in mB and FE, the outputs, and the consumption the
     recipe states.
   - Assert exact conservation: inputs = outputs + stated consumption, with no item, fluid or FE
     created or lost.
   - Insert and extract from more than one side if the machine allows it, and try an extraction from
     an input slot that the capability must refuse.
3. **If a machine duplicates or loses anything,** stop and report it with the evidence. It is a
   Critical defect under docs/17. Do not fix production code in this task.
4. **Stay within budget.** Keep each test inside its own batch and within the existing GameTest time
   budget patterns. Use no new log expectation unless a machine already logs by design. In that case,
   add an exact count and a reason.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- At the end, delete your generated `build/gametest/world` with a plain `rm -rf`, and make sure the
  heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes them.

## Not in this task

- Production changes, workflows, status files, the ledger and ADRs.
