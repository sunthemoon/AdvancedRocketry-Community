# Final campaign checklists C: machine and multiblock kernel

Status: planned, not executed. Covers C01-C08 of
[FINAL-CAMPAIGN-PLAN](FINAL-CAMPAIGN-PLAN.md), section 4.3. Read
`CAMPAIGN-CHECKLISTS-A.md` for candidate identity,
restart/evidence protocol, `[H]` harness and `[P]` human markers and result metadata.

## Shared fixture and environment

P1 must pin C4 (task identity `659bbcb6a`). P2 supplies **the many-machine
layout** and **the reference workload world of docs/17 section 4**. The
three representative machines are the Electrolyzer, Rolling Machine and
Precision Assembler, as described by the
[machine guide](../../../MACHINE-KERNEL-GUIDE.md). Rolling is 5x3x2 with four
orientations and no mirroring; Precision is 3x3x4, four orientations and
mirroring, five input/two output Item ports and one Energy input. Use the
tracked [Rolling pattern](../../../../src/main/resources/data/advancedrocketrycommunity/machine_patterns/rolling_machine.json)
and [Precision pattern](../../../../src/main/resources/data/advancedrocketrycommunity/machine_patterns/precision_assembler.json),
not an improvised shell. P2 must record anchors, orientation, mirror, all chunks,
resource stores/owners, and layout/pack hashes. Keep original fixtures unchanged.

Every row uses Java 17, Minecraft 1.20.1 and matching C4 client/server on Forge
**47.4.10**, **JEI absent unless stated otherwise**. Additional compatibility
cells use Forge 47.4.23 and JEI 15.56.0.205 and are separately identified.
All are ordinary disposable worlds, **not O2's marked world**; keep switches
off and JVM test properties absent. DS has one real client; V1 has a real GPU;
V2 uses real authenticated accounts A/B with the online whitelist settings of
checklist A. Do not replace V1/V2 with actor or capability-only unit tests.

Primary source: [v1.2.0](../../../versions/V1.2.0-MACHINE-MULTIBLOCK-KERNEL.md),
sections cited below. [Precision manual cases](../../v1.2.0-precision-assembler/MANUAL-TEST.md)
add slot/energy and stale-menu checks. All actual/result fields remain blank.

### Reproducible process examples

- The tracked [Rolling iron-bars recipe](../../../../src/generated/v1.2/resources/data/advancedrocketrycommunity/recipes/rolling_iron_bars.json)
  consumes **2 iron ingots and 100 mB water**, **100 ticks at 20 FE/t**,
  and produces **8 iron bars**.
- The tracked [Precision control-circuit recipe](../../../../src/generated/v1.2/resources/data/advancedrocketrycommunity/recipes/precision_control_circuit.json)
  consumes **2 iron ingots in input 0 and 2 redstone in input 1**,
  **20 ticks at 40 FE/t (800 FE)**, and produces **1 Advanced Circuit in
  output 0 and 2 redstone torches in output 1**.
- Confirm those recipes are effective on C4 before using their numeric oracles.
  A pack override is a separately named fixture, not grounds to silently edit
  the expected numbers. The sources do not name every active-machine recipe.
- Feed resources through normal slots/typed capabilities. Do not edit observed
  inventories or process progress through NBT to make a result match.

## C01 - Idle/active machine reference load

**Header:** Phase 5; REF; accepted normal-user Debian 12 node, 4 vCPU/8 GiB,
two workload accounts/equivalent under O7; ordinary world, switches off,
no properties. **Sources:** v1.2 sections 10.1, 13.1 and 13.5.

**Preconditions:** P2 many-machine layout with **100 idle machines plus the
defined active-machine count**; baseline Forge/JEI absent; heap <=4 GiB.
Root must define the active count, machine mix and recipes before the run;
the cited version says defined quantity but supplies no number. Use the
[quality budgets](../../../17-V1PLUS-QUALITY-BUDGETS.md), section 4.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Capture the initial layout/resource projection. | Exactly 100 idle machines and the declared active set are identifiable. |
| 2 | [H] Start tick/RSS/CPU/GC and structure-work sampling. | Raw timestamped samples under the stated full load. |
| 3 | [H] Observe the 100-machine idle interval. | Low idle cost; no per-tick unnecessary world scans. No separate numeric idle-cost threshold is stated. |
| 4 | [H] Start the declared active recipes. | Correct bounded processing and exact recipe resource accounting. |
| 5 | [H] Observe the declared active interval. | Mean <=25 ms, P95 <=50 ms, P99 <=100 ms MSPT; sustained RSS <=5.5 GiB; no OOM/watchdog or unexpected forced chunks. |
| 6 | [H] Compare final resources and structure-work samples. | No duplicated output, unbounded revalidation or unexplained loss; record spikes against the general 500 ms rule. |

**Evidence to keep:** `phase5-ref/<run>/C01/` layout and active-count manifest,
recipe hashes, all raw samples/intervals, CPU/cache/scan observations and ledgers.
Do not lower active count after seeing a failing result.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C02 - Running machines through unload and restart

**Header:** Phase 3; DS; development PC and client/account A; ordinary copy,
switches off, no properties. **Source:** v1.2 section 10.2; Precision PREC-M-04.

**Preconditions:** P2 many-machine-layout copy with all three representative
machines running a recorded batch; baseline/JEI absent. Include a multiblock
whose ports span a chunk boundary. Manifest gives loaded footprint, process
progress, exact Item/Fluid/Energy, controller/part identities and any explicit
fixture tickets. Set no production chunk-loading bypass for the case.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Capture each running machine's authoritative state. | Exact before-state, including pending journals and resource owner. |
| 2 | [H] Move the last observer outside the recorded unload range. | Record actual unload rather than assuming distance guarantees it. |
| 3 | [H] Verify the relevant chunks have unloaded. | No unintended tickets; incomplete multiblock knowledge is waiting, not destructive teardown. |
| 4 | [H] Return the observer to reload the chunks. | Existing progress/resources/parts recover; no duplicate recipe completion. |
| 5 | [H] Allow each recorded batch to complete. | One exact output per accepted batch; elapsed work/energy reconciles. |
| 6 | [H] Begin another recorded batch. | Establish a new in-progress saved state. |
| 7 | [H] Save/stop the same copy. | Clean exit and complete state. |
| 8 | [H] Reopen the copy. | Machines continue with preserved resources and identity. |
| 9 | [H] Compare live/saved state and completion. | No reimported legacy shadow resources or replayed output. |
| 10 | [H] Repeat steps 7 to 9 for the second restart. | Same conservation on reopen two. |

**Evidence to keep:** `phase3-ds/C02/` chunk/ticket observations, initial,
unloaded, reloaded and restarted state, logs/exits and exact batch ledgers.
Unknown/unloaded state must preserve inventory; a mere startup is insufficient.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C03 - Two clients share a controller and port

**Header:** Phase 4, sitting 1; V2; two authenticated real clients/accounts
A/B; ordinary copy, switches off, no properties. **Source:** v1.2 section 10.3;
Precision PREC-M-06.

**Preconditions:** Baseline/JEI absent; P2 many-machine-derived formed
Precision fixture, both players authorized and in normal interaction range.
Use the control-circuit batch above; capture both menu generations and all seven
Item slots. O5 supplies actual concurrent input, not a single-client claim.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Open the same controller on A. | Correct machine/menu identity. |
| 2 | [P] Open it on B. | Same authoritative inventory/progress. |
| 3 | [P] Shift-transfer inputs from A on the coordinated cue with B's port operation. | Resource deltas serialized once; both views agree with the server. |
| 4 | [H] Supply the recorded 800 FE through the Energy input. | One control-circuit batch, not two competing outputs. |
| 5 | [P] Attempt another input move while the batch is active. | Locked inputs cannot be consumed/extracted twice. |
| 6 | [H] Break the fixture's designated removable part. | Old-generation menus become invalid; no inventory duplication. |
| 7 | [H] Restore that same part. | Formed machine has a new valid lifecycle generation. |
| 8 | [P] Attempt an operation from the retained old menu. | Old menu closes/refuses without changing the rebuilt machine. |
| 9 | [P] Reopen both menus. | Both see the actual rebuilt inventory and state. |

**Evidence to keep:** Sitting-1 both-client video/logs, simultaneous input
times, menu IDs/generations, server inventory projections and batch accounting.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C04 - Structural damage, teardown and collision

**Header:** Phase 2 V1 and phase 3 DS; development PC, real GPU/client A;
ordinary disposable copies, switches off, no properties. **Sources:** v1.2
sections 10.4 and 11.3.

**Preconditions:** P2 many-machine-layout variants of formed Rolling and
Precision structures with recorded resources; baseline/JEI absent. Use a fresh
copy for each piston, explosion and player-break case. Manifest specifies
damage location, piston setup and explosion source/power; the version gives
none of those coordinates or values. Preserve documented resource-protection
limits rather than assuming explosions guarantee arbitrary item recovery.

| Step | Action, independently for each damage mode and machine | Expected observation |
|---|---|---|
| 1 | [H] Capture the formed model, collision and resource state. | Geometry/collision matches the formed structure. |
| 2 | [H] Apply only the selected piston/explosion/player-break action. | Record whether the attempted mutation succeeds or is protected; no false claim that a refused piston movement broke the structure. |
| 3 | [H] Observe controller/part lifecycle and resources. | Safe teardown or documented protected refusal; no running invalid machine, duplicate drops or second resource authority. |
| 4 | [H] Capture the resulting model and collision. | Actual formed/unformed state agrees with visible geometry and collision. |
| 5 | [H] Restore the permitted structure from its recorded layout. | Valid reformation, no stale capabilities/menus. |
| 6 | [H] Process one exact recorded batch. | Usable restored machine, resources conserved according to the documented damage boundary. |
| 7 | [P] Review before/after model and collision video. | No invisible blocking part or falsely formed model. |

**Evidence to keep:** Per-mode phase-2 video/screenshots; `phase3-ds/C04/`
damage commands/input receipts, collision observations, resource/drop projections
and any refusal. A protection that prevents a damage mode leaves the requested
actual-break variant NOT_COVERED until Root supplies a supported fixture.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C05 - Third-party pipes and capabilities: disposition under O6

**Header:** Phase 3; DS; development PC, one real client/account A; ordinary
world, switches off, no properties. **Source:** v1.2 section 10.5 and campaign
decision O6. **Preconditions:** Baseline/JEI absent; P2 many-machine layout.
O6 records this row as **NOT_COVERED in v1.8**, reason D9's support scope,
unless the owner names specific third-party mods. It is not a passed test.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [H] Check the owner's recorded third-party-mod selection. | If none is named, record NOT_COVERED with O6/D9 and stop this row. |
| 2 | [H] If specifically authorized, pin that mod's version/hash and supported cell. | No undeclared pipe mod silently added to the Forge x JEI release claim. |
| 3 | [H] Connect the selected pipe to a permitted input side. | Items/fluids/FE enter only through documented input capabilities. |
| 4 | [H] Connect its output path to the permitted output side. | Exact recipe products leave once; resources reconcile. |
| 5 | [H] Attempt transfer on a forbidden side/direction. | No bypass or duplication. |
| 6 | [H] Revalidate/unload/reload the tested structure. | Old capability references remain invalid; new handlers respect current state. |

**Evidence to keep:** `phase3-ds/C05/` O6/non-coverage reason, or explicit
mod authorization/identity, pipe layout, before/after resource projections,
capability observations and logs. This checklist does not appoint a mod or waive
the deferred v1.9 modpack work.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C06 - Three machine screens and diagnostics

**Header:** Phase 2; V1; development PC real GPU/client A; ordinary world,
switches off, no properties. **Sources:** v1.2 sections 11.1, 11.2 and 11.5;
Precision PREC-M-05.

**Preconditions:** P2 three representative machines; baseline/JEI absent;
English/Chinese, GUI scales **2/3/4**, windowed/fullscreen. Precision has seven
numbered Item slots, FE gauge, progress/status and a missing-casing variant.
Pack fixtures must define long recipe names and out-of-range-number cases;
source supplies no exact long string or invalid numeric value.

| Step | Action, for every machine/language/scale/window cell | Expected observation |
|---|---|---|
| 1 | [H] Set the recorded display/language cell. | Effective resolution/scale/language recorded. |
| 2 | [H] Open the normally formed machine. | All controls, numbered slots, gauges, progress and status readable. |
| 3 | [H] Run the recorded valid recipe. | Displayed amounts/progress match authoritative slot/energy state. |
| 4 | [H] Remove the designated missing-part fixture cell. | Feedback identifies the actual missing part/location. |
| 5 | [H] Open the corresponding failure interface. | Diagnostic remains readable and actionable, not clipped. |
| 6 | [H] Load the declared long-name/extreme-number fixture. | Valid long text remains usable; invalid data is rejected clearly or valid extremes render without overflow. |
| 7 | [P] Review all native cell screenshots. | No critical control/text inaccessible; no stale formed state. |

**Evidence to keep:** Per-cell native screenshots and actual resolutions,
recipe/diagnostic fixture bytes, server values and client/server logs in phase 2.
Do not substitute arbitrary NBT corruption for a specified numeric display case.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C07 - JEI display agrees with execution

**Header:** Phase 2; V1 with JEI; development PC real GPU/client A; ordinary
world, switches off, no properties. **Source:** v1.2 section 11.4 and the
machine guide's recipe examples.

**Preconditions:** P2 three machines, Forge **47.4.10**, **JEI 15.56.0.205**;
same effective server recipe data on client. Use the exact Rolling/Precision
examples above plus the manifest's Electrolyzer recipe. Capture language/scale;
repeat English/Chinese scales 2/3/4 within the campaign V1 pass.

| Step | Action, for each representative recipe | Expected observation |
|---|---|---|
| 1 | [H] Open the recipe view from the item's JEI Recipes control. | Correct machine category, ingredients, counts, fluid, time, energy and outputs. |
| 2 | [H] Open JEI Uses for the corresponding input/catalyst. | Expected recipe and catalyst linkage, no missing icon/name. |
| 3 | [H] Open the machine's JEI click-area entry. | Same category/recipe reachable from the production screen. |
| 4 | [H] Execute one displayed batch through normal machine inputs. | Rolling: 2 iron/100 mB water, 100 ticks x20 FE/t ->8 bars; Precision: 2 iron/2 redstone, 20 ticks x40 FE/t ->1 circuit/2 torches. |
| 5 | [H] Compare native before/after resources with JEI. | Exact displayed amounts match actual execution; no extra or missing product. |
| 6 | [P] Review each recipe/usage view and comparison. | Readable across the tested language/scale cells. |

**Evidence to keep:** JEI recipe/usage/catalyst/click-area screenshots,
effective recipe hashes, batch ledger and logs in the phase-2 round. An old
en_us/scale-2 scoped client result is not this full C4 result.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## C08 - Unauthorized and out-of-range menus

**Header:** Phase 4, sitting 1; V2; authenticated real clients A/B, B non-OP;
ordinary copy, switches off, no properties. **Source:** v1.2 section 12.2.

**Preconditions:** P2 protected machine/port fixture, A authorized and B not;
baseline/JEI absent. Manifest supplies actual permission boundary and normal
menu distance. Root must provide a bounded retained-menu/intent method where
normal UI closure prevents the requested operation; no fabricated packets or
unspecified coordinates are assumed to exist in the harness.

| Step | Action | Expected observation |
|---|---|---|
| 1 | [P] Attempt B's interaction with the protected controller. | Unauthorized open/action refused without resource mutation. |
| 2 | [P] Open A's authorized menu. | Normal in-range access succeeds as a positive control. |
| 3 | [P] Move A beyond the fixture's allowed menu distance. | Menu closes or becomes invalid. |
| 4 | [H] Submit the bounded old-menu operation through the approved harness method. | Refusal; no remote resource change or arbitrary chunk load. |
| 5 | [H] Compare machine/port state and loaded chunks. | No privilege, progress or resource bypass. |
| 6 | [P] Reopen in range as A. | Valid interaction still works after the rejected attempts. |

**Evidence to keep:** Sitting-1 both-client media/logs, positions/distances,
permission/menu identities, bounded intent description and before/after resources
and chunk observations. An unprovided intent method is NOT_COVERED, not proof
from seeing a closed screen alone.

Actual:
Screenshot or video:
Tester:
Date (UTC):
Build hash (commit and JAR SHA-256):

## Open questions for Root

- **C01:** Define the active-machine count, mix, recipes, measurement interval
  and many-machine layout before sampling; the version provides only 100 idle.
- **C02/C04:** Supply coordinates, chunk/unload verification, safe damage
  fixtures and documented resource-protection expectations. Distinguish a
  protected refusal from actual structural teardown for each damage mode.
- **C03:** Confirm O5's coordinated input and the normal permission fixture.
- **C05:** O6 is already decided: retain NOT_COVERED unless the owner names
  mods. No agent needs to choose a pipe mod or call this result passed.
- **C06:** Supply long-name and extreme-number fixture values and supported
  minimum window dimensions. No cited source gives those exact values.
- **C08:** Name the permission boundary, actual allowed distance and bounded
  stale/out-of-range intent tool. UI closure alone does not exercise rejection
  of an intent that actually reaches the server.
