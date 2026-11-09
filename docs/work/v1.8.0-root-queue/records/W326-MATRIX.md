# W326: C1 requirement-to-evidence matrix

Committed before native execution. Authority: [TASK W326](../TASK-W326-CARGO-INTEGRATED-EVIDENCE.md),
[owner decisions R386/R388](OWNER-DECISIONS-20261109.md) and [W11CD-DESIGN section 6](W11CD-DESIGN.md#6-verification-and-implementation-leaves).
Results and attempts belong in [W326](W326.md). This matrix is a plan, not a passing verdict.

## Candidate, scope and execution

C1 is the hosted run 42499122646 build at `70dd5d43e`, JAR SHA-256
`fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832`.
Every invocation uses the clean committed worker HEAD, proves production-source/resource/build equality to
`70dd5d43e`, and copies that JAR. The wrapper `scripts/run_v180_cargo_integrated.py` selects exactly one family,
retains native bytes before raw decoding, and calls the named existing runner without changing its oracle,
fixture, timeout or two-restart protocol. The detached controller owns and releases the backslash-path machine lock
per invocation and yields at least 90 seconds before trying again. No build or replacement candidate is allowed.

Write scope: this matrix, W326's record, the integrated test-side wrapper and its Python tests, and new external
cargo evidence. No production, generated resource, status, ledger, build, risk, bay or other-task file is writable.
Production admission remains disabled. Real clients, reference-node performance, soak, fuel-loader enablement and
all Gate approvals are explicitly excluded.

## Native cuts: 90 fluid and 30 item

Runner aliases: F = `scripts/run_v180_cargo_cuts.py`; S = `scripts/run_v180_cargo_s4_s5_cuts.py`;
I = `scripts/run_v180_item_cuts.py`. Each row requires one cut and two restarts.
Kinds are the actual exercised device direction, not a claim that an unload representative also exercises load.
K2 acknowledgement and K3 debit are separate rows, as the design's 14 cuts per direction require.

| Row | Family | Required cut / probe boundary | Kind evidenced | Runner | Case ID |
|---|---|---|---|---|---|
| N001 | C-S1 | `PREPARING` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_PREPARING` |
| N002 | C-S1 | `K1` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_L1_BEFORE` |
| N003 | C-S1 | `K2` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_L1_REPLACED` |
| N004 | C-S1 | `L1_ACK` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_L1_ACK` |
| N005 | C-S1 | `C2_SOURCE_AFTER` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_C2_SOURCE_AFTER` |
| N006 | C-S1 | `C2_DESTINATION_AFTER` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_C2_DESTINATION_AFTER` |
| N007 | C-S1 | `BARRIER_FORCE_AFTER` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_SOURCE_ONLY_STORE` |
| N008 | C-S1 | `BARRIER_FORCE_AFTER` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_DESTINATION_ONLY_STORE` |
| N009 | C-S1 | `BARRIER_FORCE_BEFORE` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_SECOND_FORCE_BEFORE` |
| N010 | C-S1 | `C6_JOINT_READBACK` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_C6_JOINT_READBACK` |
| N011 | C-S1 | `K8` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_K8` |
| N012 | C-S1 | `K9` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_L2_REPLACED` |
| N013 | C-S1 | `C7_ACKNOWLEDGED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_C7_ACKNOWLEDGED` |
| N014 | C-S1 | `C8_RELEASED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_C8_RELEASED` |
| N015 | C-S1 | `PREPARING` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_PREPARING` |
| N016 | C-S1 | `K1` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_L1_BEFORE` |
| N017 | C-S1 | `K2` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_L1_REPLACED` |
| N018 | C-S1 | `L1_ACK` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_L1_ACK` |
| N019 | C-S1 | `C2_SOURCE_AFTER` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_C2_SOURCE_AFTER` |
| N020 | C-S1 | `C2_DESTINATION_AFTER` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_C2_DESTINATION_AFTER` |
| N021 | C-S1 | `BARRIER_FORCE_AFTER` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_SOURCE_ONLY_STORE` |
| N022 | C-S1 | `BARRIER_FORCE_AFTER` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_DESTINATION_ONLY_STORE` |
| N023 | C-S1 | `BARRIER_FORCE_BEFORE` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_SECOND_FORCE_BEFORE` |
| N024 | C-S1 | `C6_JOINT_READBACK` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_C6_JOINT_READBACK` |
| N025 | C-S1 | `K8` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_K8` |
| N026 | C-S1 | `K9` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_L2_REPLACED` |
| N027 | C-S1 | `C7_ACKNOWLEDGED` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_C7_ACKNOWLEDGED` |
| N028 | C-S1 | `C8_RELEASED` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_C8_RELEASED` |
| N029 | C-S2 | `C2_DESTINATION_AFTER` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_D2_C2` |
| N030 | C-S2 | `BARRIER_FORCE_AFTER` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_D2_C5` |
| N031 | C-S2 | `C6_JOINT_READBACK` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_D2_C6` |
| N032 | C-S2 | `K9` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_D2_C7` |
| N033 | C-S2 | `C2_DESTINATION_AFTER` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_D2_C2` |
| N034 | C-S2 | `BARRIER_FORCE_AFTER` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_D2_C5` |
| N035 | C-S2 | `C6_JOINT_READBACK` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_D2_C6` |
| N036 | C-S2 | `K9` | `FLUID_CARGO_UNLOAD` | F | `FC_UNLOAD_D2_C7` |
| N037 | C-S2 | `E1_OWN_STORE` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E1_OWN_STORE` |
| N038 | C-S2 | `E2_CAPTURE` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E2_CAPTURE` |
| N039 | C-S2 | `E2_ROCKET_EXTERNAL` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E2_ROCKET_EXTERNAL` |
| N040 | C-S2 | `WRITE_FAILED_BEFORE_REPLACE` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E4L1_FAIL` |
| N041 | C-S2 | `WRITE_UNKNOWN` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E4L1_UNKNOWN` |
| N042 | C-S2 | `WRITE_UNKNOWN` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E4L2_UNKNOWN` |
| N043 | C-S2 | `E3_CACHE_CLOSE` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E3_CACHE_CLOSE` |
| N044 | C-S2 | `STALLED_RESTART` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_E5_STALLED` |
| N045 | C-S3 | `R1_L1_REPLACED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_R1_255` |
| N046 | C-S3 | `R1_REFUSED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_R1_256` |
| N047 | C-S3 | `R2_FENCED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_R2_DUPLICATE` |
| N048 | C-S3 | `R3_BEFORE_OFFLINE_EDIT` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_R3_CURSOR` |
| N049 | C-S3 | `R3_BEFORE_OFFLINE_EDIT` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_R3_OTHER` |
| N050 | C-S3 | `R3_BEFORE_OFFLINE_MOVE` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_R3_RELOCATED` |
| N051 | C-S3 | `G1_PRESERVED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_G1_FUTURE` |
| N052 | C-S3 | `G1_PRESERVED` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_G1_INVALID` |
| N053 | C-S3 | `G3_STOPPING` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_G3_L1_INFLIGHT` |
| N054 | C-S3 | `G3_STOPPING` | `FLUID_CARGO_LOAD` | F | `FC_LOAD_G3_L2_UNKNOWN` |
| N055 | C-S4 | `O1_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_O1_BEFORE` |
| N056 | C-S4 | `O1_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_O1_AFTER` |
| N057 | C-S4 | `O_BIND_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_BIND_BEFORE` |
| N058 | C-S4 | `O_BIND_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_BIND_AFTER` |
| N059 | C-S4 | `O2_ROCKET_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_O2_ROCKET_BEFORE` |
| N060 | C-S4 | `O2_ROCKET_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_O2_ROCKET_AFTER` |
| N061 | C-S4 | `O2_DEVICE_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_O2_DEVICE_BEFORE` |
| N062 | C-S4 | `O2_DEVICE_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_O2_DEVICE_AFTER` |
| N063 | C-S4 | `O3_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_O3_BEFORE` |
| N064 | C-S4 | `O3_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_O3_AFTER` |
| N065 | C-S4 | `O3J_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_O3J_BEFORE` |
| N066 | C-S4 | `O3J_REPLACED` | `FLUID_CARGO_UNLOAD` | S | `FS_O3J_AFTER` |
| N067 | C-S4 | `O4_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_O4_BEFORE` |
| N068 | C-S4 | `O4_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_O4_AFTER` |
| N069 | C-S4 | `PRUNE_BEFORE` | `FLUID_CARGO_UNLOAD` | S | `FS_PRUNE_BEFORE` |
| N070 | C-S4 | `PRUNE_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_PRUNE_AFTER` |
| N071 | C-S4 | `O1_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_ABANDON_ROCKET` |
| N072 | C-S4 | `O1_AFTER` | `FLUID_CARGO_UNLOAD` | S | `FS_ABANDON_DEVICE` |
| N073 | C-S4 | `O_VETO` | `FLUID_CARGO_UNLOAD` | S | `FS_VETO_DESTINATION_SAVED` |
| N074 | C-S4 | `NORMALIZATION_VETO` | `FLUID_CARGO_UNLOAD` | S | `FS_BIND_VETO` |
| N075 | C-S5 | `N1` | `FLUID_CARGO_LOAD` | S | `FF_N1` |
| N076 | C-S5 | `F1_AFTER_CARGO` | `FLUID_CARGO_LOAD` | S | `FF_F1_AFTER_CARGO` |
| N077 | C-S5 | `F6_JOINT_PROVED` | `FLUID_CARGO_LOAD` | S | `FF_F6_AFTER_CARGO` |
| N078 | C-S5 | `F8_AFTER_CARGO` | `FLUID_CARGO_LOAD` | S | `FF_F8_AFTER_CARGO` |
| N079 | C-S5 | `DISASSEMBLY_REMOVAL` | `FLUID_CARGO_UNLOAD` | S | `FF_DISASSEMBLY_REMOVAL` |
| N080 | C-S5 | `DISASSEMBLY_RESTORATION` | `FLUID_CARGO_UNLOAD` | S | `FF_DISASSEMBLY_RESTORATION` |
| N081 | C-S5 | `DISASSEMBLY_RELEASE` | `FLUID_CARGO_UNLOAD` | S | `FF_DISASSEMBLY_RELEASE` |
| N082 | C-S5 | `REBUILD_DESTINATION` | `FLUID_CARGO_UNLOAD` | S | `FF_REBUILD_DESTINATION` |
| N083 | C-S5 | `KEEP_SOURCE` | `FLUID_CARGO_UNLOAD` | S | `FF_KEEP_SOURCE` |
| N084 | C-S5 | `REBUILD_SOURCE` | `FLUID_CARGO_UNLOAD` | S | `FF_REBUILD_SOURCE` |
| N085 | C-S5 | `STALE_PREPARED` | `FLUID_CARGO_UNLOAD` | S | `FF_STALE_PREPARED` |
| N086 | C-S5 | `RECONNECT` | `FLUID_CARGO_UNLOAD` | S | `FF_RECONNECT` |
| N087 | C-S5 | `GOVERNED_MISSING` | `FLUID_CARGO_LOAD` | S | `FF_GOVERNED_MISSING` |
| N088 | C-S5 | `GOVERNED_TERMINAL` | `FLUID_CARGO_LOAD` | S | `FF_GOVERNED_TERMINAL` |
| N089 | C-S5 | `TWO_FLIGHTS` | `FLUID_CARGO_LOAD` | S | `FF_TWO_FLIGHTS` |
| N090 | C-S5 | `CANCEL_AFTER_CARGO` | `FLUID_CARGO_LOAD` | S | `FF_CANCEL_AFTER_CARGO` |
| N091 | D-S1 | `K2` (probe `IC_LOAD_L1_REPLACED`) | `ITEM_CARGO_LOAD` | I | `IC_LOAD_K2` |
| N092 | D-S1 | `C2_DESTINATION_AFTER` (probe `IC_LOAD_C2_DESTINATION_AFTER`) | `ITEM_CARGO_LOAD` | I | `IC_LOAD_K3` |
| N093 | D-S1 | `BARRIER_FORCE_AFTER` (probe `IC_LOAD_SOURCE_ONLY_STORE`) | `ITEM_CARGO_LOAD` | I | `IC_LOAD_K4` |
| N094 | D-S1 | `BARRIER_FORCE_AFTER` (probe `IC_LOAD_DESTINATION_ONLY_STORE`) | `ITEM_CARGO_LOAD` | I | `IC_LOAD_K5` |
| N095 | D-S1 | `K9` (probe `IC_LOAD_L2_REPLACED`) | `ITEM_CARGO_LOAD` | I | `IC_LOAD_K9` |
| N096 | D-S1 | `K2` (probe `IC_UNLOAD_L1_REPLACED`) | `ITEM_CARGO_UNLOAD` | I | `IC_UNLOAD_K2` |
| N097 | D-S1 | `C2_DESTINATION_AFTER` (probe `IC_UNLOAD_C2_DESTINATION_AFTER`) | `ITEM_CARGO_UNLOAD` | I | `IC_UNLOAD_K3` |
| N098 | D-S1 | `BARRIER_FORCE_AFTER` (probe `IC_UNLOAD_SOURCE_ONLY_STORE`) | `ITEM_CARGO_UNLOAD` | I | `IC_UNLOAD_K4` |
| N099 | D-S1 | `BARRIER_FORCE_AFTER` (probe `IC_UNLOAD_DESTINATION_ONLY_STORE`) | `ITEM_CARGO_UNLOAD` | I | `IC_UNLOAD_K5` |
| N100 | D-S1 | `K9` (probe `IC_UNLOAD_L2_REPLACED`) | `ITEM_CARGO_UNLOAD` | I | `IC_UNLOAD_K9` |
| N101 | D-S1 | `C2_DESTINATION_AFTER` (probe `IC_LOAD_C2_DESTINATION_AFTER`) | `ITEM_CARGO_LOAD` | I | `IC_PARTIAL_APPLY` |
| N102 | D-S1 | `BARRIER_FORCE_AFTER` (probe `IC_UNLOAD_SOURCE_ONLY_STORE`) | `ITEM_CARGO_UNLOAD` | I | `IC_PARTIAL_STORE` |
| N103 | D-S2 | `BEFORE_OFFLINE_DAMAGE` | `ITEM_CARGO_UNLOAD` | I | `IR_CONTAINER` |
| N104 | D-S2 | `BEFORE_OFFLINE_DAMAGE` | `ITEM_CARGO_UNLOAD` | I | `IR_LOOT` |
| N105 | D-S2 | `O1_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_O1_AFTER` |
| N106 | D-S2 | `O2_ROCKET_BEFORE` | `ITEM_CARGO_UNLOAD` | I | `IS_O2_ROCKET_BEFORE` |
| N107 | D-S2 | `O2_ROCKET_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_O2_ROCKET_AFTER` |
| N108 | D-S2 | `O2_DEVICE_BEFORE` | `ITEM_CARGO_UNLOAD` | I | `IS_O2_DEVICE_BEFORE` |
| N109 | D-S2 | `O2_DEVICE_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_O2_DEVICE_AFTER` |
| N110 | D-S2 | `O3_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_O3_AFTER` |
| N111 | D-S2 | `O3J_REPLACED` | `ITEM_CARGO_UNLOAD` | I | `IS_O3J_AFTER` |
| N112 | D-S2 | `O4_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_O4_AFTER` |
| N113 | D-S2 | `PRUNE_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_PRUNE_AFTER` |
| N114 | D-S2 | `O_BIND_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_BIND_AFTER` |
| N115 | D-S2 | `NORMALIZATION_VETO` | `ITEM_CARGO_UNLOAD` | I | `IS_BIND_VETO` |
| N116 | D-S2 | `O1_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_ABANDON_ROCKET` |
| N117 | D-S2 | `O1_AFTER` | `ITEM_CARGO_UNLOAD` | I | `IS_ABANDON_DEVICE` |
| N118 | D-S2 | `K1` | `ITEM_CARGO_UNLOAD` | I | `IR_DISASSEMBLY` |
| N119 | D-S2 | `REBUILD_REFUSED` | `ITEM_CARGO_UNLOAD` | I | `IR_REBUILD` |
| N120 | D-S2 | `ROOT_PRESERVED` | `ITEM_CARGO_UNLOAD` | I | `IR_ROOT` |

Counts: C-S1 28; C-S2 16; C-S3 10; C-S4 20; C-S5 16; D-S1 12; D-S2 18.
Total: **120 cuts, 360 starts, 240 restarts**.

## Earlier failures and diagnostic residuals

All historical attempts stay immutable. W138's E3/E5/R1_256/R3_CURSOR, W142's F6/GOVERNED_TERMINAL/TWO_FLIGHTS/CANCEL,
and W143's IR_CONTAINER/IR_LOOT are explicitly present above and will be rerun, not reused as C1 evidence.
W138's final R72, W142's final R80 and W143's final R79/R77 already supersede their earlier failures on older JARs;
that does not qualify C1.

| Row | Kind(s) | Coverage plan / limitation |
|---|---|---|
| X01 | All four | NOT_COVERED: host/OS power-loss durability is not simulated by a forced Java-process stop. Needs a separately authorized destructive-storage campaign and owner residual-risk decision. |
| X02 | All four | NOT_COVERED: unobservable storage/cache/close failures remain diagnostic residuals; observable E1/E3 representatives do not prove every device/OS failure. Needs storage design/diagnostics and an owner decision, not an oracle relaxation. |
| X03 | FLUID_CARGO_UNLOAD | FF_REBUILD_SOURCE is a protected FLOOR_CONFLICT result under W142 R76, not a demonstrated operator repair. Offline whole-world repair remains NOT_COVERED. |

## Per-owner pending cap over cargo

| Row | Kind | Runner / coverage plan |
|---|---|---|
| Q01 | FLUID_CARGO_LOAD | NOT_COVERED natively: no C1 cargo cap probe or existing runner drives this direction at 16 owner-pending operations. |
| Q02 | FLUID_CARGO_UNLOAD | NOT_COVERED natively: no C1 cargo cap probe or existing runner drives this direction at 16 owner-pending operations. |
| Q03 | ITEM_CARGO_LOAD | NOT_COVERED natively: no C1 cargo cap probe or existing runner drives this direction at 16 owner-pending operations. |
| Q04 | ITEM_CARGO_UNLOAD | NOT_COVERED natively: no C1 cargo cap probe or existing runner drives this direction at 16 owner-pending operations. |

W238OwnerPendingTest covers the shared authority in A0; BayLoaderSaturationGameTests covers bay/loader A1, not
native cargo-direction admission. These are supporting evidence only. Closing Q01-Q04 needs a gated cargo-cap
fixture in a future candidate (approximately 1-2 engineering days including negative refusal-side-effect tests,
then the whole integrated matrix on that JAR), or a separately justified existing-path harness. This task never
edits src/main or silently claims one consumer proves every kind.

## C-E and D-E A1 mapping

The task supplies hosted run 42499122646 at `70dd5d43e` as passing all 1,802 required GameTests.
Mapped methods below are inspected in C1-equivalent source; they are hosted A1 reuse, not fresh local executions.
All named classes are in the `resourceescrow` package. Before-write failure details without an explicit assertion
remain distinct from S2 cut coverage; the record will not turn a source mapping into native evidence.

| Row | Acceptance case | Kind(s) | Hosted GameTest method or gap |
|---|---|---|---|
| C-E-A01 | Real load/unload; first ledger and disabled-switch side effects | FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD | `FluidCargoAdmissionGameTests.realLoadAndUnloadReachPreparedWithoutPublishingResources` |
| C-E-A02 | Native replay, typed store/readback, L2/C8, competing devices and serialization | FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD | `FluidCargoReconciliationGameTests.realLoadAndUnloadRetireAndServeTheQueuedCompetitor` |
| C-E-A03 | Named F1 fence | FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD | `FluidCargoReconciliationGameTests.namedTransferRemainsPreparedBehindTheExistingF1Fence` |
| C-E-A04 | Unavailable Fluid isolates its component | FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD | `FluidCargoReconciliationGameTests.unavailableSavedFluidHoldsOnlyItsNativeComponent` |
| C-E-A05 | Load neutral normalization and veto; O1-O4 | FLUID_CARGO_LOAD | `FluidCargoSettlementGameTests.heldLoadNormalizesItsNeutralRocketOnlyAfterTheVetoClears` |
| C-E-A06 | Unload both survivor proofs, readback, pruning and READY launch | FLUID_CARGO_UNLOAD | `FluidCargoSettlementGameTests.heldUnloadProvesBothSurvivorsPrunesThemAndLaunchesFromReady` |
| C-E-A07 | Unload neutral source normalization and veto | FLUID_CARGO_UNLOAD | `FluidCargoSettlementGameTests.heldUnloadNormalizesItsNeutralSourceOnlyAfterTheVetoClears` |
| C-E-A08 | ABANDON missing rocket | FLUID_CARGO_UNLOAD | `FluidCargoSettlementGameTests.abandonProvesTheMissingRocketAndTheSurvivingDevice` |
| C-E-A09 | ABANDON missing device, A1 | FLUID_CARGO_UNLOAD | NOT_COVERED: no corresponding fluid GameTest; FS_ABANDON_DEVICE is S2, not A1 |
| C-E-A10 | Latest cargo restored exactly once under retained legacy record | FLUID_CARGO_UNLOAD | `FluidCargoLandedDisassemblyGameTests.retainedLegacyRecordDisassemblesExactlyTheLatestCargoOnce` |
| C-E-A11 | Two cargo-mutated flights, successor mutation and latest disassembly; missing/terminal dispatcher checks | FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD | `FluidCargoFlightGameTests.twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce` |
| C-E-A12 | Real cargo plus stale PREPARED/both physical entities and protected keeper, A1 | FLUID_CARGO_UNLOAD | NOT_COVERED: recognition GameTest is explicit snapshot fixtures, not a real cargo device; FF_STALE_PREPARED supplies S2 only |
| C-E-A13 | Disembarked passenger reconnect after cargo, A1 | FLUID_CARGO_UNLOAD | NOT_COVERED: FF_RECONNECT supplies S2, not a dedicated A1 test |
| C-E-A14 | Exact-source cancellation after cargo, A1 | FLUID_CARGO_LOAD | NOT_COVERED: FF_CANCEL_AFTER_CARGO supplies S2, not a dedicated A1 test |
| D-E-A01 | Native load/unload, partial counts, queued competitor, typed readback and L2/C8 | ITEM_CARGO_LOAD, ITEM_CARGO_UNLOAD | `ItemCargoReconciliationGameTests.realLoadAndUnloadRetireAndServeTheQueuedCompetitor` |
| D-E-A02 | Named F1 fence | ITEM_CARGO_LOAD, ITEM_CARGO_UNLOAD | `ItemCargoReconciliationGameTests.namedTransferRemainsPreparedBehindTheExistingF1Fence` |
| D-E-A03 | Unavailable item isolates its component | ITEM_CARGO_LOAD, ITEM_CARGO_UNLOAD | `ItemCargoReconciliationGameTests.unavailableSavedItemHoldsOnlyItsNativeComponent` |
| D-E-A04 | Mismatched frozen replay leaves endpoints unchanged | ITEM_CARGO_LOAD, ITEM_CARGO_UNLOAD | `ItemCargoReconciliationGameTests.mismatchedFrozenReplayDigestHoldsWithBothEndpointsUnchanged` |
| D-E-A05 | Load neutral normalization and veto; load-role counterpart | ITEM_CARGO_LOAD | `ItemCargoSettlementGameTests.heldLoadNormalizesItsNeutralRocketOnlyAfterTheVetoClears` |
| D-E-A06 | Unload survivor proofs, typed readback, O3J/O4/pruning and READY launch | ITEM_CARGO_UNLOAD | `ItemCargoSettlementGameTests.heldUnloadProvesBothSurvivorsPrunesThemAndLaunchesFromReady` |
| D-E-A07 | Unload neutral source normalization and veto | ITEM_CARGO_UNLOAD | `ItemCargoSettlementGameTests.heldUnloadNormalizesItsNeutralSourceOnlyAfterTheVetoClears` |
| D-E-A08 | ABANDON missing rocket | ITEM_CARGO_UNLOAD | `ItemCargoSettlementGameTests.abandonProvesTheMissingRocketAndTheSurvivingDevice` |
| D-E-A09 | ABANDON missing device | ITEM_CARGO_UNLOAD | `ItemCargoSettlementGameTests.abandonProvesTheMissingDeviceAndSurvivingRocket` |
| D-E-A10 | Unavailable item blocks before O2 normalization/readback | ITEM_CARGO_UNLOAD | `ItemCargoSettlementNativeGameTests.unavailableNativeItemHoldsOnlyItsSettlementBeforeO2` |
| D-E-A11 | Latest item remainder restored exactly once | ITEM_CARGO_UNLOAD | `ItemCargoLandedDisassemblyGameTests.retainedLegacyRecordDisassemblesExactlyTheLatestCargoOnce` |
| D-E-A12 | Two item-mutated flights and latest disassembly | ITEM_CARGO_LOAD, ITEM_CARGO_UNLOAD | `ItemCargoFlightGameTests.twoCargoMutatedFlightsThenTheLatestCargoDisassemblesExactlyOnce` |

The A1 gaps cannot be filled by a Python-only runner against C1: new GameTests/probe code would change src/main
and the candidate. Estimate 1-2 engineering days for those targeted fixtures and review, then a new hosted build
and complete rerun. A1 reconnect is not real-client V2; V1/V2 remain final-campaign work.

## Changed-boundary and reuse mapping

| Design section 6 boundary | Evidence plan / boundary of the claim |
|---|---|
| Ordinary prefix/apply and both save orders | C-S1/C-S2 repeat both fluid directions; D-S1 repeats typed item changes. The mapped reconciliation A1 methods exercise the typed path. Non-item boundaries are shared implementation, not extra native item cuts. |
| Composite binding/floor | C-S3 repeats cargo composite saturation and identity failures. B3-S5 ledger missing/replaced/behind and distinct/coalesced-floor exact integrated native results are NOT_COVERED by these cargo runners; historic fuel-only evidence is not upgraded. |
| Neutral normalization/veto | C-S4/D-S2 native boundaries plus settlement A1 methods above. |
| O2/O3/O3J/O4/pruning | C-S4 all named unload boundaries, D-S2 typed after-write boundaries, and A1 load-role counterpart. |
| Recognition/reconnect/replay/cancellation | C-S5 named native cuts; A1 mapping/gaps above. Additional B3-S6 acknowledgements and P1-N2/P1-F9 exact integrated results are NOT_COVERED here; require the separate shared-infrastructure campaign. |
| Latest-cargo disassembly | Three C-S5 windows and D-S2 item representative; mapped actual-device A1, not adapter-only fixtures. |
| Ledger creation and storage/shutdown | C-S5 N1 and C-S2 E3/E5 run afresh. B3-S1 N0/N2/N3 and remaining B3-S4/B3-S6 exact integrated reuse are NOT_COVERED here; historical implementation equality alone is not a current execution. |
| Protected roots/save scope | C-S3/D-S2 cargo roots run afresh. C-A/D-A protection GameTests are separate hosted tests; G2 and unchanged B3-S6 native lifecycle reuse are NOT_COVERED by this cut set. |
| Non-applicable chains | No cargo chain is admitted; no chain cut is invented. Fuel-chain qualification belongs to its separate work. |

The additional shared-infrastructure native gaps need approximately 0.5-1 engineering day to inventory/pin the
existing B3 runners plus machine execution time, coordinated with Root's fuel work. They do not change the
120-cut denominator and are not marked passing by cargo results.

