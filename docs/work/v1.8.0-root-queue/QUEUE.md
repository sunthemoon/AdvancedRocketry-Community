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

| Slice | Scope | Pipeline state (2026-10-12 17:45) |
|---|---|---|
| [W03](TASK-W03-EQUIPMENT.md) | C18b workstation, pressure tanks, modules | **Merged** `1e36af58` |
| [W01](TASK-W01-HATCH-KERNEL.md), [W01F](TASK-W01F-REVIEW-FIXES.md), [W05](TASK-W05-ARC-FURNACE-CUTTING.md), [W01G](TASK-W01G-REVIEW-FIXES.md) | Hatch family, generic controller, lathe, arc furnace, cutting machine | **Merged** `ab5d9808`. Follow-up Lows L1–L3 are open |
| [W02](TASK-W02-PROPULSION.md), [W02F](TASK-W02F-REVIEW-FIXES.md) | C17a propulsion components and typed fuel modes | **Merged** `ebf4c07b`. Follow-ups: native restart checks; a passenger who never returns locks the rocket; F3 goes to the loader task |
| [W04](TASK-W04-FORCE-FIELD.md), [W04F](TASK-W04F-REVIEW-FIXES.md) | C17b force field projector, API 1.9 | **Merged** `17fb7e15` (hosted run 38207662335). Follow-ups N1–N6 go to W04G |
| [W06](TASK-W06-MOTION-UPGRADES.md), [W06F](TASK-W06F-REVIEW-FIXES.md) | C18b jetpack, motion and visor upgrades | **Merged** `d8352a27` (hosted run 38217298302; W06F review MERGE). Limitation: with allow-flight=true vanilla trusts client flight |
| [W07](TASK-W07-STATION-CONTROLS.md), [W07F](TASK-W07F-REVIEW-FIXES.md), [W07G](TASK-W07G-QUOTE-VISIBILITY.md) | C17b station controls, orbital distance and orientation, `classic_controls` channel | **Merged** `0fc2aed4` (hosted runs 38226376205 and 38229462409). Open: §5.3 notices; V1/V2, dedicated-server and restart evidence |
| [W08](TASK-W08-IOWORKER-MIXIN.md) | ADR-073 Mixin copy of pending IOWorker reads | **Merged** `88b16cef` (hosted run 38221050014; review MERGE-WITH-FOLLOWUP). Open: start the packaged SRG jar on a dedicated server |
| [W09](TASK-W09-PACED-ROCKET-FIXTURES.md) | Paced entity readiness for the rocket fixtures (test code only) | **Merged** `487da4e6` (hosted run 38217303450; Root review: no assertion or timeout lost) |
| [W04G](TASK-W04G-FOLLOWUPS.md) | Force field N1–N3 | **Merged** `f557e5c0` (hosted run 38220027274; re-review MERGE-WITH-FOLLOWUP). Optional N4–N6 stay open |
| [W10](TASK-W10-C16C-MACHINES.md) | C16c crystallizer, chemical reactor, laser etcher, centrifuge | **Merged** `7324a7dd` (hosted run 38224855115; review MERGE-WITH-FOLLOWUP, open Low W10-R1). All seven classic machines are now on the kernel |
| [W11a](TASK-W11A-D3-ESCROW-DESIGN.md) | D3-A escrow coordinator design (design only) | Designer (Claude) running since 15:45 |
| [W12](TASK-W12-ADVANCEMENTS.md) | C18c classic advancements and reachability guide | **Merged** `667fa40c` (hosted run 38229336877; Root review). Follow-ups: model the machine recipe types in `PackagedRecipeGraph` and restore its strict failure; crystallizer and dilithium advancements; the world-first milestones after rocket journal schema 3 (W11) |
| Root fix | Cutting machine model pointed at a missing `machine_casing` texture (found by W08's `validate_repository.py` run) | **Fixed** `0596d124` |
| [W13](TASK-W13-LANDING-PADS.md) | C17b landing pads as named flight targets | Implementer (Codex) running since 16:54 |
| [W14](TASK-W14-LIFE-SUPPORT-DEVICES.md) | C18a CO2 scrubber, gas charger, seals | Implementer (Codex) running since 16:55 |
| [W16](TASK-W16-PRESENTATION.md) | C18d HUD, client configuration, sounds | Implementer (Claude) running since 17:22 |

The shared branch `667fa40c` holds every merge above. Hosted run 38206390869 passed at
`ebf4c07b`, before the force field merge. Run 38208930066 passed at `17fb7e15`; run 38219141393 checks `487da4e6`.

## Queued

| Task | Scope | Dispatch when |
|---|---|---|
| [W18](TASK-W18-BEACON-FINDER.md) | C17c planetary beacon and beacon finder | Next free slot |
| [W19](TASK-W19-ACQUISITION-GRAPH.md) | C16d: close the strict graph's 18 acquisition gaps (code-backed fluids, Mars, dust sources) | Next free slot |

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
