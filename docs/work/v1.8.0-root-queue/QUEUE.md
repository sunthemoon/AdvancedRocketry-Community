# v1.8 Root task queue

Root: Claude, since 2026-10-11 ([AGENTS.md section 13](../../../AGENTS.md)).
Implementers follow [WORKER-RULES](WORKER-RULES.md). This file lists the current
assignment of each task. Root updates it when it dispatches, merges or re-plans a task.

## Priorities

1. The critical path is the machine kernel, which unblocks the seven C16b and C16c
   machines (W01, then the machine tasks).
2. Content that does not depend on the kernel runs in parallel (W02, W03 and their
   successors).
3. Bug fixes run in parallel only when they do not slow 1 and 2.

On 2026-10-11 the owner chose to build the kernel as the accepted ADR-064 describes,
with Forge events. The proposed ADR-068 and the native/JDI observation work no longer
block any task.

## Active

| Task | Scope | Worktree / branch | Base | Session | State |
|---|---|---|---|---|---|
| [W01](TASK-W01-HATCH-KERNEL.md) | Hatch family, generic controller, lathe | `arce-v180-w01-hatch-kernel` / `codex/v1.8.0-hatch-kernel` | `1568699e` | `01a12b82-77da-7592-85d8-e201b34241f9` | Delivered `3a0b29c5` (GameTest 652/652). Candidate `7fb1c1a3`: hosted CI and independent persistence review running |
| [W02](TASK-W02-PROPULSION.md) | C17a propulsion components and typed fuel modes | `arce-v180-w02-propulsion` / `codex/v1.8.0-propulsion` | `1568699e` | `01a12b82-8061-7e13-94b8-a29fc73ef9f0` | running since 2026-10-11 |
| [W03](TASK-W03-EQUIPMENT.md) | C18b workstation, pressure tanks, modules | `arce-v180-w03-equipment` / `codex/v1.8.0-equipment` | `1568699e` | `01a12b82-8895-7573-8f65-3fb05884bbf1` | Merged as `1e36af58` on 2026-10-12 after hosted run 38155101250 and the [independent review](records/W03-REVIEW.md) (MERGE-WITH-FOLLOWUP: M1 and L1 queued) |
| [W04](TASK-W04-FORCE-FIELD.md) | C17b force field projector, API 1.9 | `arce-v180-w04-force-field` / `codex/v1.8.0-w04-force-field` | `42321f8b` | `01a12bc4-62f1-75b1-b0e9-4d989e1b4fd2` | running since 2026-10-12 |
| [W05](TASK-W05-ARC-FURNACE-CUTTING.md) | C16b arc furnace and cutting machine on the W01 kernel | `arce-v180-w05-arc-cutting` / `codex/v1.8.0-w05-arc-cutting` | W01 candidate `7fb1c1a3` | `01a12bcf-600a-7731-a836-d0b8a2cec0fc` | running since 2026-10-12 |

## Queued

| Task | Scope | Dispatch when |
|---|---|---|
| (none) | | |

Hosted CI runs on each implementer branch after Root pushes it (workflow change
`2e7b214e`); Root first merges the shared branch into it so that the new trigger applies.

## Next candidates

- The four C16c machines (crystallizer, chemical reactor, laser etcher, centrifuge) on
  the W01 kernel, after W05.
- C17a loader variants, monitoring station and linker (ADR-065 section 4).
- C17b station controls and landing pad (ADR-065 sections 5 and 6.1); the landing pad
  waits for W02.
- C17c beacon and finder, satellite bay, solar array (ADR-065 sections 7 to 9).
- C18a scrubber, charger and seals (ADR-066 sections 3 and 4).
- C18b motion upgrades and jetpack (ADR-066 section 5.2), and the laser gun. The same task fixes W03 review M1 (decode refused workstation roots from detached copies) and L1 (R-026 and the ADR-066 note name the noncanonical-NBT trigger).
- C18c observatory and advancements (ADR-066 section 6).
- C18d presentation and client configuration (ADR-066 section 7).
- Parallel bugs:
  - the hosted chunk-save CME (run 38064322640);
  - the unpaced multi-leg planetary flight test (run 38084795112);
  - the 5 ms wall-clock budget per atmosphere-boundary callback
    (`AtmosphereBoundaryRegistry.MAX_CALLBACK_NANOS`). Under machine load, the adapter
    test mod's provider exceeded it and mod loading failed (W01 record, crash at
    2026-10-12 00:18). Changing the budget needs an ADR.

## Parked

- `test/v1.8.0-native-proto-promotion` (`0fcd67b4`): the previous Root's opt-in native
  diagnostic and its handoff snapshot. It is not merged and not a prerequisite.
- ADR-068: stays PROPOSED and is not needed for the kernel.
