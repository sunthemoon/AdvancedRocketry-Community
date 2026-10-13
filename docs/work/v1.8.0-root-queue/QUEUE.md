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

| Slice | Scope | Pipeline state (2026-10-13 23:40) |
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
| [W14](TASK-W14-LIFE-SUPPORT-DEVICES.md) | C18a CO2 scrubber, cartridges, seals (the gas charger waits for W11) | **Merged** `a859d2f4` (hosted run 38249148243; W14F re-review MERGE-WITH-FOLLOWUP). L1 and L2 fixed by W25. The gas charger waits for W11b3 |
| [W16](TASK-W16-PRESENTATION.md) | C18d HUD, client configuration, sounds | **Merged** `6a20a674` (hosted run 38240401114; Root review). Follow-up: fold the `advancedrocketrycommunity_v180` sound namespace into the main namespace |
| [W15](TASK-W15-RECIPE-GRAPH-MACHINES.md) | C16d: machine recipe types in `PackagedRecipeGraph`, strict failure restored, crystallizer and dilithium advancements | **Merged** `f5038f2f` (hosted run 38239083487; Root audit: every changed assertion is stricter). The graph reports 18 acquisition gaps, which go to W19 |
| [W17](TASK-W17-SOLAR-ARRAY.md) | C17c solar array | **Merged** `b9ace0cd` (hosted run 38283733951; review BLOCK, closed by W17F; Root reviewed the F1 claim diff). ADR-065 W17 note extended (F5, panel exclusivity, operator-only NBT) |
| [W18](TASK-W18-BEACON-FINDER.md) | C17c planetary beacon and beacon finder | **Merged** `804be2ab` (hosted run 38283811217; review MERGE-WITH-FOLLOWUP; W18F fixes F1 to F3 and F5; Root wrote [records/W18F.md](records/W18F.md)). F4, F6 and F7 fixed by W26. Open: F8 (provenance in the same commit as the assets) |
| [W19](TASK-W19-ACQUISITION-GRAPH.md) | C16d: close the strict graph's 18 acquisition gaps | **Merged** `3d499253` (hosted run 38261140501; Root review). ADR-064 §10.2 implementation note added for the five optional cross-mod dusts |
| [W20](TASK-W20-LASER-GUN.md) | C18b basic laser gun (ADR-066 section 5.3) | **Merged** `284709bf` (hosted run 38289378640; Root review; no persistence) |
| [W21a](TASK-W21A-OBSERVATORY-DESIGN.md) | C18c observatory and processor: design note (ADR-051 amendment, registry schema 4, instance schema 2, frozen patterns) | **Design merged** `fa890675`. Independent review (Codex) APPROVE-WITH-CHANGES (SHA-256 `0c76c2d4…`): W21b applies R2, R4 and R5; W21c applies R1 and R3, and needs a Root decision on the power-plug owner access |
| [W21b](TASK-W21B-REGISTRY-SCHEMA4.md) | Satellite registry schema 4, ground survey jobs, migration and invariants (no blocks) | **Merged** `a7b0af22` (hosted run 38344780650; review MERGE-WITH-FOLLOWUP; W21bF fixed F1 to F4, F6 and F8). ADR-051 W21b note and R-030 added. Open: F9, a station-scale timing miss under machine load |
| W21c | C18c observatory and data processor | **Merged** `5e75f835` (hosted run 38368323736; review MERGE-WITH-FOLLOWUP; W21cF fixed the CI crash and L1, L2, L4 to L6, I2). ADR-066 W21c note, R-027 scope and R-033 added. Open: V1 of the menu texts; the optional M1 pause on an over-reserved restored root |
| [W22](TASK-W22-STATION-NOTICES.md) | C17b section 5.3 station notices: translatable warp notices, visitor notices, the offline member-notice SavedData | **Merged** `286bdfe9` (hosted run 38337117540; review MERGE-WITH-FOLLOWUP; W22F fixed F2, F3 and F7). The ADR-065 W22 note and R-029 were added. Open follow-ups: F8 (a new member is shown the outcome from before they joined), F9 (default names contain the creator's name), F10 (two tests) |
| [W23](TASK-W23-OPERATOR-NBT-AUDIT.md) | Parallel bug: operator-only item NBT for every block entity that stores an owner, identity or guarded root | **Merged** `e8f4d02a` (hosted run 38328763897; Root review: additions only) |
| [W24](TASK-W24-LEGACY-PAD-OCCUPANCY.md) | Parallel bug: the legacy landing-pad selector's occupancy (W13 review R1, legacy half) | **Merged** `58d763d4` (hosted run 38336046793; Root review) |
| [W25](TASK-W25-SCRUBBER-STALE-BINDINGS.md) | Parallel bug: release provably stale scrubber facade bindings (W14F review L1 and L2) | **Merged** `06f1a898` (hosted run 38354701151; Root review) |
| [W26](TASK-W26-BEACON-FOLLOWUPS.md) | Parallel bug: beacon F4 (load-time inactive flicker), F6 (tests), F7 (finder strip versus the boss bar) | **Merged** `7fb7e50c` (hosted run 38369606917; Root review). Open: V1 of the strip position |
| [W27a](TASK-W27A-SATELLITE-BAY-DESIGN.md) | C17c-02 satellite bay: design note (receipt store and sequence on the D3-A core) | **Design merged** `882204fe` (rounds 1 to 3; round-2 review APPROVE-WITH-CHANGES, SHA-256 `c9ef87a1…`; round 3 `049b0689` fixed N1 and N2, accepted by Root). The section 8.1 hook goes into W11b2 continuation 05. Open owner decisions before W27e: the neutral-source limit, and the passenger-exit permit with the per-owner limit (ADR-065 section 10) |
| [W28](TASK-W28-GAMETEST-SCHEDULING-AUDIT.md) | Parallel bug: audit and fix GameTests that schedule from inside scheduled callbacks, plus a guard | **Merged** `c29be3be` (hosted run 38389630543; Root fixes `4b116fae` and `ffa7250f`). From now on, the guard fails any new nested GameTest registration |
| [W29](TASK-W29-ANVIL-SLOT-STABILITY.md) | Escrow follow-up: per-slot readback stability (the native E2 `BARRIER_UNSTABLE`) | **Merged** (hosted run 38382392596). The W11b1 open item E2 `BARRIER_UNSTABLE` is closed in the ADR-065 note. Open: the intermittent first-attempt joint-proof GameTest, which now names its reason |
| [W30](TASK-W30-STATUS-RECONCILIATION.md) | Status reconciliation proposal for Root: COMPLETION-PLAN C16 to C19, the ledger, pre-handover items, CURRENT_VERSION | **Merged** `0622b982`. Applied by Root: COMPLETION-PLAN `eed1c6e3`, CURRENT_VERSION `6ef115b0` (history moved to the log) |
| [W31](TASK-W31-LEDGER-EVIDENCE.md) | Per-batch delivery evidence files and a ready-to-apply ledger patch | **Merged** `777b8b5d`. Root applied the test-fixture patch `ce593e67` and the ledger patch `065c0c36` (146 units delivered, 40 PLANNED; validator PASS; ledger tests 58 OK). Root fix `9dbaa637`: the C16c and research controller models used a missing `machine_casing` texture |
| [W32](TASK-W32-CHEMICAL-SPECIAL-RECIPES.md) | Chemical reactor special recipes: `space_breathing` application, lossless cartridge refresh | **Merged** `58ca2269` (hosted run 38390433422; the W28 guard passes with the shared branch merged in) |
| [W33](TASK-W33-TORCHES.md) | C18a combustion torches, unlit torches and fire starters (ADR-066 section 3.2) | Delivered through `3c006f12` (implementation by Claude, finished by Codex; 2,895 unit tests; 903 of 906 GameTests, with all 11 W33 tests passing). The three failures are the joint-proof flake (W36) and two W25 frontier tests ("No loaded frontier available"), possibly a W33 fixture interaction. Shared branch merged in (config count 95). Branch CI 38423864085 on `084cdb74`. Independent review (Codex) running since 23:39 |
| [W34](TASK-W34-NONPLAYER-ATMOSPHERE.md) | C18a non-player atmosphere effects, `atmosphere_bypass`, spawn admission, `vacuumDamage` | **Merged** `179452d7` (hosted run 38398138235; Root review) |
| [W35](TASK-W35-C16D-REMAINING.md) | Finish C16d: tracking circuit, graph without Tau Ceti, rebalance and CHANGELOG | **Merged** (hosted run 38419018623; Root review). C16d is complete apart from the ledger update for the tracking circuit |
| [W36](TASK-W36-JOINT-PROOF-FLAKE.md) | Parallel bug: diagnose and fix the joint-proof `SUPERSEDING_SAVE` flake (the first fix `4b116fae` did not hold) | Claude, running since 23:03 |
| [W37](TASK-W37-SLEEP-RESPAWN.md) | C18a sleep admission and the `allowPlanetRespawn` and `forcePlanetRespawn` options (ADR-066 section 3.3; the pre-handover owner decisions are inputs) | Implementer (Claude) running since 23:28 |
| [W11b1](TASK-W11B1-ESCROW-CORE.md) | D3-A escrow core: coordinator, ledger writer, barrier, test endpoints, S2 cuts; no production admission | **Merged** `f1eb5d83` (admission disabled; review MERGE-WITH-FOLLOWUP; W11b1F fixed all 11 findings; native 24/24; Root fix `548f0c3f` paces the escrow GameTests; hosted run 38360139403). ADR-065 W11b1 note, R-031 and R-032 added. E2 `BARRIER_UNSTABLE` closed by W29 |
| [W11b2 design](TASK-W11B2-DESIGN-NOTE.md) | Design note closing P1 to P5 of the round-2 review before W11b2 code | **Merged** `811a5796`. Independent review (Codex) **APPROVE-WITH-CHANGES** (SHA-256 `8ec318a3…`): W11b2's implementation applies R1 to R6 (R1 and R2 High: a failed checked journal write stops dependent transfer effects; fenced retries stay classified, ticket-free and leave unrelated warps available). W11b2 starts after W11b1 merges |
| W11b2 | D3-A rocket integration with R1 to R6 (admission stays disabled) | Continuation 04 at `211a21f9`: shared branch merged, 2,951 unit tests, 902 GameTests (one joint-proof flake), 5 native cases. Continuation 05 (Codex, since 23:02): the section 8.1 `RocketTransferBayRecoveryGate` hook and the shared admission contract, then credited reconciliation, settlement and the native matrix |
| Root fix | Mixin refmap lost on a Gradle build-cache hit (W19's `MixinPackagingTest` failure); a release jar could ship without the ADR-073 refmap | **Merged** `798a2465`: the refmaps and mappings are declared `compileJava` outputs; verified miss then hit; hosted run 38258700306 passed |
| Root incident | C: filled to 0 GB: a W16 worker's background bare `python` REPL wrote 24.9 GB | Cleaned 21:40; WORKER-RULES now forbids interactive or unbounded background commands |

Hosted run 38400162712 passed on the shared branch at `01e6b457` (on rerun), which holds every merge above except the documents merged since. Hosted run 38206390869 passed at
`ebf4c07b`, before the force field merge. Run 38208930066 passed at `17fb7e15`; run 38219141393 checks `487da4e6`.

## Queued

| Task | Scope | Dispatch when |
|---|---|---|

Hosted CI runs on each implementer branch after Root pushes it (workflow change
`2e7b214e`); Root first merges the shared branch into it so that the new trigger applies.

## Next candidates

Source: W30's verified reconciliation, `records/W30-STATUS-PROPOSAL.md` on branch
`codex/v1.8.0-w30-status` (`2bc87cee`), section 5. Dispatch order: progress first, then the
parallel bugs.

- **Escrow chain:**
  - W11b3: `fluid_fuel_loader` and the linker's rocket-binding half, with packaged S2 evidence
    (also W02 F3's refill);
  - W11c: cargo Fluid loaders and unloaders;
  - W11d: item cargo loaders and unloaders;
  - W11e: the full linker and the monitoring station;
  - the satellite bay, after W27a's review (ADR-065 sections 4.2, 4.3 and 8).
- **C16d** (ADR-064 section 10):
  - the tracking circuit;
  - the graph run with the Tau Ceti Levels removed;
  - the rebalance and its CHANGELOG entries.
- **C16a-07b:** one formation GameTest per motor tier (ADR-064 section 4.1).
- **Chemical reactor special recipes:**
  - the `space_breathing` enchantment application (ADR-066 section 5.1);
  - the lossless carbon-cartridge refresh (section 3.1).
- **C18a environment rules** (ADR-066 sections 3.2 and 3.3), in three or four tasks:
  - torches and fire starters;
  - non-player atmosphere effects, `atmosphere_bypass` and spawn admission;
  - `vacuumDamage`;
  - sleep and the two respawn options;
  - underwater breathing;
  - the space safety return;
  - gravity-scaled fall damage, after its M1.
- **The gas charge pad** (ADR-066 sections 4 and 9, D4). Needs the **owner's decision** first:
  prove the player-file save ordering, or approve an alternative interaction.
- **World-first advancements** `one_small_step` and `first_station_warp` (ADR-066 section 6.2,
  D3), after W11b2.
- **C18d** (ADR-066 section 7.1):
  - client rendering of the black-hole sky;
  - the elevator capsule effect, which needs an S2C carrier;
  - producers for the `lathe`, `rolling_machine` and `machine_large` sounds;
  - the sound-namespace fold;
  - the manual listen;
  - OBJ loading, once the assets are imported.
- **Per-asset closure** of the 154 REVIEW assets (ADR-066 section 7.3).
- **C19:**
  - merge the validator chain (`82f82b1f`, `d605be33`);
  - the full Python suite and the strict validator;
  - the evidence references;
  - `PORTING_MATRIX`;
  - the ADR-068, ADR-070 and ADR-071 decisions;
  - R32-03;
  - the ledger closure.
- **S1, V1 and V2** for every merged slice, plus W08 R02. These are for the final campaign.
- **Parallel bugs** (open follow-ups):
  - W01 L1 to L3;
  - W02F (native restart; the locked rocket);
  - W04 N4 to N6;
  - W10-R1;
  - W13 N1 to N3;
  - W18 F8;
  - W21b F9;
  - W21c M1;
  - W22 F8 to F10;
  - the intermittent `cancellationreleasesexactlyonetransferclaim` GameTest ("Pad fixture/flight did not
    become ready"; hosted run 38400162712 failed once at `01e6b457` and passed on rerun);
  - the 5 ms atmosphere-boundary budget, which needs an ADR (it recurred on 2026-10-13);
  - the HotSpot C2 crash, if it recurs.

## Parked

- `test/v1.8.0-native-proto-promotion` (`0fcd67b4`): the previous Root's opt-in native
  diagnostic and its handoff snapshot. It is not merged and not a prerequisite.
- ADR-068: stays PROPOSED and is not needed for the kernel.
