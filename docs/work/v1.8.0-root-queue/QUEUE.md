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
| [W01](TASK-W01-HATCH-KERNEL.md) | Hatch family, generic controller, lathe | `arce-v180-w01-hatch-kernel` / `codex/v1.8.0-hatch-kernel` | `1568699e` | `01a12b82-77da-7592-85d8-e201b34241f9` | Delivered `3a0b29c5`; hosted run 38155835990 passed; independent review BLOCK (two High, two Medium: `D:/GitHub/ARCE-Task-Evidence/root-queue/W01-review/REVIEW.md`, SHA-256 `59540643…d388a`). Fixes run as W01F on the same branch |
| [W02](TASK-W02-PROPULSION.md) | C17a propulsion components and typed fuel modes | `arce-v180-w02-propulsion` / `codex/v1.8.0-propulsion` | `1568699e` | `01a12b82-8061-7e13-94b8-a29fc73ef9f0` | Delivered `2cde1ee3`; candidate `2260e575` (Root merge fixes); independent review BLOCK (two Critical, two Medium: `D:/GitHub/ARCE-Task-Evidence/root-queue/W02-review/REVIEW.md`, SHA-256 `4b9127e6…401d7a`). Fixes queued as W02F |
| [W03](TASK-W03-EQUIPMENT.md) | C18b workstation, pressure tanks, modules | `arce-v180-w03-equipment` / `codex/v1.8.0-equipment` | `1568699e` | `01a12b82-8895-7573-8f65-3fb05884bbf1` | Merged as `1e36af58` on 2026-10-12 after hosted run 38155101250 and the [independent review](records/W03-REVIEW.md) (MERGE-WITH-FOLLOWUP: M1 and L1 queued) |
| [W04](TASK-W04-FORCE-FIELD.md) | C17b force field projector, API 1.9 | `arce-v180-w04-force-field` / `codex/v1.8.0-w04-force-field` | `42321f8b` | `01a12bc4-62f1-75b1-b0e9-4d989e1b4fd2` | running since 2026-10-12 |
| [W05](TASK-W05-ARC-FURNACE-CUTTING.md) | C16b arc furnace and cutting machine on the W01 kernel | `arce-v180-w05-arc-cutting` / `codex/v1.8.0-w05-arc-cutting` | W01 candidate `7fb1c1a3` | `01a12bcf-600a-7731-a836-d0b8a2cec0fc` | Delivered `41468d8e` (JUnit 346/346, GameTest 665/665). Waits for W01F; Root then builds one W01+W01F+W05 candidate for CI and one combined independent review |
| [W01F](TASK-W01F-REVIEW-FIXES.md) | Fix W01 review findings R1–R4 | `arce-v180-w01-hatch-kernel` / `codex/v1.8.0-hatch-kernel` | `83fcf0ba` | `01a12bde-eca0-73f2-b227-844ce2ca38de` | running since 2026-10-12 (critical path, temporary fourth implementer) |
| [W02F](TASK-W02F-REVIEW-FIXES.md) | Fix W02 review findings F1, F2 and F4 (F3 goes to the C17a loader task) | `arce-v180-w02-propulsion` / `codex/v1.8.0-propulsion` | `ddc80a48` | `01a12c02-7529-7852-90ac-dc00a5156fa6` | running since 2026-10-12 |

## Queued

| Task | Scope | Dispatch when |
|---|---|---|
| [W06](TASK-W06-MOTION-UPGRADES.md) | C18b jetpack, motion and visor upgrades; W03 review M1 and L1 | Now, as a headless Claude implementer |

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
  - the hosted chunk-save CME (runs 38064322640 and 38156329125 attempt 2, both at
    batch `endgame_gravity_station`). Root cause, from the bytecode of the mapped
    Forge 47.4.10 jar:
    - vanilla 1.20.1 `IOWorker.loadAsync` returns the pending write's `CompoundTag`
      without a copy;
    - `ChunkStorage.upgradeChunkTag` then puts and removes `__context` on that
      top-level tag;
    - so when a chunk is unloaded and quickly reloaded, the main thread modifies the
      tag while the IO thread is writing it.

    The single `CompoundTag.write` frame under `NbtIo` shows the top-level tag. Later
    Minecraft versions copy the pending data. The fix is the owner's choice: a Mixin
    backport (needs an ADR), or a bounded ERROR expectation (needs a change to the log
    ADR's exact-count rule);
  - the unpaced multi-leg planetary flight test (run 38084795112);
  - the 5 ms wall-clock budget per atmosphere-boundary callback
    (`AtmosphereBoundaryRegistry.MAX_CALLBACK_NANOS`). Under machine load, the adapter
    test mod's provider exceeded it and mod loading failed (W01 record, crash at
    2026-10-12 00:18). Changing the budget needs an ADR.
  - a HotSpot C2 compiler crash on the hosted runner: SIGSEGV in
    `BoolNode::Ideal`, Temurin 17.0.20.1. It happened in run 38156329125 attempt 1, the
    shared branch after the W03 merge, during the batch `flight_concurrency`, and exit
    code 134 ended the GameTest server. The job was re-run. If the crash recurs, pin the
    JDK or exclude the method from compilation.

## Parked

- `test/v1.8.0-native-proto-promotion` (`0fcd67b4`): the previous Root's opt-in native
  diagnostic and its handoff snapshot. It is not merged and not a prerequisite.
- ADR-068: stays PROPOSED and is not needed for the kernel.
