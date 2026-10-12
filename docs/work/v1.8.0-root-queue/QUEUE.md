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

Every implementer and reviewer runs as an independent process through
`D:/GitHub/ARCE-Task-Evidence/root-queue/launch_worker.ps1`. Their run directories are in
`D:/GitHub/ARCE-Task-Evidence/root-queue/<task>/run-*/`. A slice merges after its hosted
CI run and its independent review both pass. The reviews are kept in
`D:/GitHub/ARCE-Task-Evidence/root-queue/<task>-review/REVIEW.md`, and the merge commits
give their SHA-256 values.

| Slice | Scope | Pipeline state (2026-10-12 13:07) |
|---|---|---|
| [W03](TASK-W03-EQUIPMENT.md) | C18b workstation, pressure tanks, modules | **Merged** `1e36af58`. Its M1 and L1 are fixed in W06 |
| [W01](TASK-W01-HATCH-KERNEL.md), [W01F](TASK-W01F-REVIEW-FIXES.md), [W05](TASK-W05-ARC-FURNACE-CUTTING.md), [W01G](TASK-W01G-REVIEW-FIXES.md) | Hatch family, generic controller, lathe, arc furnace, cutting machine | **Merged** `ab5d9808`. Follow-ups: review Lows L1–L3 (generation-0 conflict, modded air in `machine_clearance`, plug wording) |
| [W02](TASK-W02-PROPULSION.md), [W02F](TASK-W02F-REVIEW-FIXES.md) | C17a propulsion components and typed fuel modes | **Merged** `ebf4c07b`. Follow-ups: native restart checks for F1/F2; a passenger who never returns leaves the rocket locked (admin recovery); F3 goes to the loader task |
| [W04](TASK-W04-FORCE-FIELD.md), [W04F](TASK-W04F-REVIEW-FIXES.md) | C17b force field projector, API 1.9 | W04F (Codex) delivered `874bba5c` (674/674). Re-review (Claude) running |
| [W06](TASK-W06-MOTION-UPGRADES.md) | C18b jetpack, motion and visor upgrades, W03 M1 and L1 | Candidate `fcc03f37` passed hosted run 38199855784. Review (Codex) running |
| [W07](TASK-W07-STATION-CONTROLS.md) | C17b station controls, orbital distance and orientation | Delivered `035e78f8` (Claude, 656/656). Review (Codex) running; Root merges the shared branch into it afterwards |
| [W08](TASK-W08-IOWORKER-MIXIN.md) | ADR-073 Mixin copy of pending IOWorker reads | Implementer (Claude) running since 13:06 |

The shared branch after the W01 and W02 merges is `ebf4c07b`. Its hosted run is
38206390869.

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
  - the hosted chunk-save CME (being fixed by W08 under ADR-073) (runs 38064322640 and 38156329125 attempt 2, both at
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
