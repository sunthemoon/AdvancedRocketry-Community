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
| [W01](TASK-W01-HATCH-KERNEL.md) | Hatch family, generic controller, lathe | `arce-v180-w01-hatch-kernel` / `codex/v1.8.0-hatch-kernel` | `1568699e` | `01a12b82-77da-7592-85d8-e201b34241f9` | running since 2026-10-11 |
| [W02](TASK-W02-PROPULSION.md) | C17a propulsion components and typed fuel modes | `arce-v180-w02-propulsion` / `codex/v1.8.0-propulsion` | `1568699e` | `01a12b82-8061-7e13-94b8-a29fc73ef9f0` | running since 2026-10-11 |
| [W03](TASK-W03-EQUIPMENT.md) | C18b workstation, pressure tanks, modules | `arce-v180-w03-equipment` / `codex/v1.8.0-equipment` | `1568699e` | `01a12b82-8895-7573-8f65-3fb05884bbf1` | running since 2026-10-11 |

Hosted CI runs on each implementer branch after Root pushes it (workflow change
`2e7b214e`); Root first merges the shared branch into it so that the new trigger applies.

## Next candidates

- C16b arc furnace and cutting machine, then the four C16c machines, all on the W01
  kernel.
- C17a loader variants, monitoring station and linker (ADR-065 section 4).
- C17b station controls, landing pad and force field (ADR-065 sections 5 and 6).
- C17c beacon and finder, satellite bay, solar array (ADR-065 sections 7 to 9).
- C18a scrubber, charger and seals (ADR-066 sections 3 and 4).
- C18b motion upgrades and jetpack (ADR-066 section 5.2), and the laser gun.
- C18c observatory and advancements (ADR-066 section 6).
- C18d presentation and client configuration (ADR-066 section 7).
- Parallel bugs:
  - the hosted chunk-save CME (run 38064322640);
  - the unpaced multi-leg planetary flight test (run 38084795112).

## Parked

- `test/v1.8.0-native-proto-promotion` (`0fcd67b4`): the previous Root's opt-in native
  diagnostic and its handoff snapshot. It is not merged and not a prerequisite.
- ADR-068: stays PROPOSED and is not needed for the kernel.
