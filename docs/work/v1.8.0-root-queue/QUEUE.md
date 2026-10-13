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

| Slice | Scope | Pipeline state (2026-10-13 13:25) |
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
| [W11a](TASK-W11A-D3-ESCROW-DESIGN.md) | D3-A escrow coordinator design (design only) | **Design merged** `c9514352`. Round 1 drew REDESIGN (Codex review, SHA-256 `1cfcbb8f…`, F1 to F8 accepted). Round 2 ([TASK-W11A2](TASK-W11A2-D3-REDESIGN.md), Codex) drew **APPROVE-WITH-CHANGES** (Claude review, SHA-256 `e54fd579…`): W11b1 applies C1 to C6; W11b2 needs a design note closing P1 to P5 and its own review first |
| [W12](TASK-W12-ADVANCEMENTS.md) | C18c classic advancements and reachability guide | **Merged** `667fa40c` (hosted run 38229336877; Root review). Follow-ups: model the machine recipe types in `PackagedRecipeGraph` and restore its strict failure; crystallizer and dilithium advancements; the world-first milestones after rocket journal schema 3 (W11) |
| Root fix | Cutting machine model pointed at a missing `machine_casing` texture (found by W08's `validate_repository.py` run) | **Fixed** `0596d124` |
| [W13](TASK-W13-LANDING-PADS.md) | C17b landing pads as named flight targets | **Merged** `19da6240` (hosted run 38248887802; W13F re-review MERGE-WITH-FOLLOWUP). The legacy selector's R1 pattern is fixed by W24. Open follow-ups: N1 loaded-only discovery residual; N2 three weak GameTest assertions; N3 station-wide refusal diagnostics |
| [W14](TASK-W14-LIFE-SUPPORT-DEVICES.md) | C18a CO2 scrubber, cartridges, seals (the gas charger waits for W11) | **Merged** `a859d2f4` (hosted run 38249148243; W14F re-review MERGE-WITH-FOLLOWUP). Follow-ups: L1 an empty orphaned association leaves an unbreakable facade; L2 (pre-existing) a facade bound to a vanished generation; L3 a record sentence. The gas charger waits for W11b3 |
| [W16](TASK-W16-PRESENTATION.md) | C18d HUD, client configuration, sounds | **Merged** `6a20a674` (hosted run 38240401114; Root review). Follow-up: fold the `advancedrocketrycommunity_v180` sound namespace into the main namespace |
| [W15](TASK-W15-RECIPE-GRAPH-MACHINES.md) | C16d: machine recipe types in `PackagedRecipeGraph`, strict failure restored, crystallizer and dilithium advancements | **Merged** `f5038f2f` (hosted run 38239083487; Root audit: every changed assertion is stricter). The graph reports 18 acquisition gaps, which go to W19 |
| [W17](TASK-W17-SOLAR-ARRAY.md) | C17c solar array | **Merged** `b9ace0cd` (hosted run 38283733951; review BLOCK, closed by W17F; Root reviewed the F1 claim diff). ADR-065 W17 note extended (F5, panel exclusivity, operator-only NBT) |
| [W18](TASK-W18-BEACON-FINDER.md) | C17c planetary beacon and beacon finder | **Merged** `804be2ab` (hosted run 38283811217; review MERGE-WITH-FOLLOWUP; W18F fixes F1 to F3 and F5; Root wrote [records/W18F.md](records/W18F.md)). Follow-ups: F4, F6, F7 (finder strip overlaps the boss bar), F8 |
| [W19](TASK-W19-ACQUISITION-GRAPH.md) | C16d: close the strict graph's 18 acquisition gaps | **Merged** `3d499253` (hosted run 38261140501; Root review). ADR-064 §10.2 implementation note added for the five optional cross-mod dusts |
| [W20](TASK-W20-LASER-GUN.md) | C18b basic laser gun (ADR-066 section 5.3) | **Merged** `284709bf` (hosted run 38289378640; Root review; no persistence) |
| [W21a](TASK-W21A-OBSERVATORY-DESIGN.md) | C18c observatory and processor: design note (ADR-051 amendment, registry schema 4, instance schema 2, frozen patterns) | **Design merged** `fa890675`. Independent review (Codex) APPROVE-WITH-CHANGES (SHA-256 `0c76c2d4…`): W21b applies R2, R4 and R5; W21c applies R1 and R3, and needs a Root decision on the power-plug owner access |
| [W21b](TASK-W21B-REGISTRY-SCHEMA4.md) | Satellite registry schema 4, ground survey jobs, migration and invariants (no blocks) | **Merged** `a7b0af22` (hosted run 38344780650; review MERGE-WITH-FOLLOWUP; W21bF fixed F1 to F4, F6 and F8). ADR-051 W21b note and R-030 added. Open: F9, a station-scale timing miss under machine load |
| W21c | C18c observatory and data processor: blocks, menus, runtime; R1, R3, R4; the Root power-plug decision | Delivered through `5e75d1a2` (Codex; both controllers extend `ClassicControllerBlockEntity` with research profiles, and the plug stays the only FE owner; 2,763 unit tests, 863 GameTests, 8 native restart and fault cycles). Root is merging the shared branch with W21bF into it. Independent review (Claude, task ready in `root-queue/W21c-review/`) starts at the next free Claude slot |
| [W22](TASK-W22-STATION-NOTICES.md) | C17b section 5.3 station notices: translatable warp notices, visitor notices, the offline member-notice SavedData | **Merged** `286bdfe9` (hosted run 38337117540; review MERGE-WITH-FOLLOWUP; W22F fixed F2, F3 and F7). The ADR-065 W22 note and R-029 were added. Open follow-ups: F8 (a new member is shown the outcome from before they joined), F9 (default names contain the creator's name), F10 (two tests) |
| [W23](TASK-W23-OPERATOR-NBT-AUDIT.md) | Parallel bug: operator-only item NBT for every block entity that stores an owner, identity or guarded root | **Merged** `e8f4d02a` (hosted run 38328763897; Root review: additions only) |
| [W24](TASK-W24-LEGACY-PAD-OCCUPANCY.md) | Parallel bug: the legacy landing-pad selector's occupancy (W13 review R1, legacy half) | **Merged** `58d763d4` (hosted run 38336046793; Root review) |
| [W25](TASK-W25-SCRUBBER-STALE-BINDINGS.md) | Parallel bug: release provably stale scrubber facade bindings (W14F review L1 and L2) | Implementer (Claude) running since 12:27 |
| [W26](TASK-W26-BEACON-FOLLOWUPS.md) | Parallel bug: beacon F4 (load-time inactive flicker), F6 (tests), F7 (finder strip versus the boss bar) | Implementer (Codex) running since 13:23 |
| [W11b1](TASK-W11B1-ESCROW-CORE.md) | D3-A escrow core: coordinator, ledger writer, barrier, test endpoints, S2 cuts; no production admission | Delivered through `19ab684a`. Independent review (Claude) MERGE-WITH-FOLLOWUP (SHA-256 `e3d1fd91…`): two High and two Medium latent defects confirmed by probe (a writer stuck after an invalidated ambiguous L2 replacement, retirement without its operation, drain time that grows with uptime, quadratic entity capture). W11b1F (Claude, since 10:56) fixes them with regression tests and native re-runs. Root merges W11b1 after W11b1F, with the C3 startup-scope risk entry and the ADR-065 note |
| [W11b2 design](TASK-W11B2-DESIGN-NOTE.md) | Design note closing P1 to P5 of the round-2 review before W11b2 code | **Merged** `811a5796`. Independent review (Codex) **APPROVE-WITH-CHANGES** (SHA-256 `8ec318a3…`): W11b2's implementation applies R1 to R6 (R1 and R2 High: a failed checked journal write stops dependent transfer effects; fenced retries stay classified, ticket-free and leave unrelated warps available). W11b2 starts after W11b1 merges |
| W11b2 | D3-A rocket integration with R1 to R6 (admission stays disabled) | Checkpoint `9502a056` (Codex): neutral schema 3, interlocks, checked-journal acknowledgement; 2,716 unit tests, 855 GameTests; not integration-ready. Continuation 01 (a new Codex session, since 11:22): recovery fences, floors, settlement, relocation and startup policy, native S1 and S2, journal benchmarks. It merges the W11b1F fixes when they land |
| Root fix | Mixin refmap lost on a Gradle build-cache hit (W19's `MixinPackagingTest` failure); a release jar could ship without the ADR-073 refmap | **Merged** `798a2465`: the refmaps and mappings are declared `compileJava` outputs; verified miss then hit; hosted run 38258700306 passed |
| Root incident | C: filled to 0 GB: a W16 worker's background bare `python` REPL wrote 24.9 GB | Cleaned 21:40; WORKER-RULES now forbids interactive or unbounded background commands |

The shared branch `a859d2f4` holds every merge above. Hosted run 38251946851 passed at `a859d2f4`. Hosted run 38206390869 passed at
`ebf4c07b`, before the force field merge. Run 38208930066 passed at `17fb7e15`; run 38219141393 checks `487da4e6`.

## Queued

| Task | Scope | Dispatch when |
|---|---|---|

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
  - the 5 ms wall-clock budget per atmosphere-boundary callback (recurred on 2026-10-13 in W11b1's first native selection: `force_field_boundary` was rejected at startup under load)
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
