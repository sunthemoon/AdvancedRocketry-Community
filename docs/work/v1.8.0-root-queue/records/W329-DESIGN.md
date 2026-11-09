# W329 design note: protective reads while the escrow ledger is NOT_LOADED

Task: [TASK-W329](../TASK-W329-LEDGER-NOT-LOADED-GATES.md), Root decision R393, from section 4 of
[W328-DESIGN](W328-DESIGN.md).

**Terms.** `existing` is `ResourceEscrowSavedData.existing(server)`, the load-only lookup. The PROPULSION delegate is
`EscrowRuntime.propulsion(server)`. NO_FILE means the startup lookup proved the ledger file absent. NOT_LOADED means the
file exists but did not load, or its presence is unknown (W328, `EscrowLedgerStartup`). In both cases `existing`
returns null. A *protective read* is a read whose null answer permits work that the ledger could forbid.

## 1. Inventory

Every production read of `existing` outside GameTest classes, probes, cut drivers and fixtures, at base `f847da444`.
Paths are under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/`; `re/` is `resourceescrow/` and
`rs/` is `rocket/server/`.

### 1.1 Changed: protective reads

| Site | Null today means | Weakens a protection under NOT_LOADED | Change |
|---|---|---|---|
| `re/RocketEscrowGate.java:90` `escrowIdle` | Idle when the rocket's own wrapper names no ledger. | Yes. Armed flights, records naming the rocket, coordinator logical locks, settlement tombstones and the authority head are skipped, so an ordinary rocket reads idle. Launch, boarding, fuel, cargo, disassembly, kill and the teleport interlocks rely on it. | Not idle. |
| `re/RocketEscrowGate.java:31` `retirementUnprotected` | Unprotected: the landed record may retire before a fill. | Yes. It also calls `idle`, so the `escrowIdle` change already refuses; this is a second line. | Protected (false). |
| `re/RocketEscrowGate.java:51` `bayNeutral` | A neutral bay source. | Yes. It also calls `escrowIdle`; second line, as above. | Not neutral. |
| `re/RocketEscrowGate.java:75` `flightFrozen` | Not frozen. | Yes. An armed flight is ignored, so `RocketEntity.updateFlightData` accepts a change. | Frozen. |
| `re/RocketEscrowGate.java:118` `escrowLogicalLocked` | Not locked. | Yes. Coordinator logical locks and settlement tombstones are skipped. Legacy transfer recovery and fences, assembly recovery, the legacy flight rule, the bay release flight and the release world rely on it, directly or through `logicalLocked`. | Locked. |
| `re/RocketCargoDeviceHold.java:20` `locked` | Locked only when the device's own wrapper names a ledger. | Yes. An unresolved record, frozen identity or endpoint lock naming the device is skipped, so the fluid and item cargo devices allow removal, explosions, drops and port and container exchange. | Locked. |
| `re/EscrowLoaderAdmission.java:270` `progress` | `ESCROW_PENDING` only for a marked wrapper, else null. | Yes. `FluidFuelLoaderBlockEntity.escrowLocked` is `progress != null` for an unmarked loader, so the same holds as for cargo devices are skipped. | `ESCROW_PENDING` ("Resources are locked until escrow confirms them durably"). `LEDGER_UNAVAILABLE` would claim that no fuel moved. |
| `re/EscrowOperatorDiagnostics.java:17` `status` | `ledger=absent held=0 released=0 ...`. | Not a protection, but it tells the operator that no ledger exists while one may hold records. | `ledger=not-loaded` with unknown counts. |

### 1.2 Unchanged: admission already refuses NOT_LOADED

These read the delegate's state when `existing` is null and refuse unless it is NO_FILE. The TASK keeps admission
unchanged, and the inventory found no gap.

| Site | Null today means | Under NOT_LOADED |
|---|---|---|
| `re/EscrowLoaderAdmission.java:127` `admit` | A first fill on a virtual empty root, only when NO_FILE. | `LEDGER_UNAVAILABLE`. |
| `re/EscrowFluidCargoAdmission.java:100` and `:207`, `re/EscrowItemCargoAdmission.java:119` and `:240` | The same for cargo, and the capture's same-ledger check. | `LEDGER_UNAVAILABLE`; the check fails. |
| `re/ResourceEscrowSavedData.java:67` `createForFill` | Creation needs NO_FILE. | Refused. |
| `re/EscrowLoaderAdmission.java:86` `cargoPending` | No cargo pending. | Used only by fuel admission and its eligibility display; `admit` refuses at its delegate check. |
| `re/EscrowDeviceTurns.java:13` `refresh` | No turn registration, so `allows()` is true. | Only orders admissions between devices; cargo admission refuses before the turn. |
| `re/EscrowResourceQueue.java:42` | Nothing to commit. | Nothing to commit. |

### 1.3 Unchanged: null already refuses, holds or grants nothing

| Site | Null answer |
|---|---|
| `re/EscrowLoaderAdmission.java:99` `chainable` | Not chainable. |
| `re/EscrowFillSelfGuard.java:19` `allows` | No self-guard permission. |
| `re/EscrowTerminalSuccessor.java:17` `released` | No F7 release. |
| `re/EscrowFlightCheckpoint.java:69` and `:108` | F1 refused (`NO_LEDGER`). |
| `re/EscrowGovernedPreparation.java:38` and `:63` | Governed launch refused. |
| `re/EscrowGovernedDispatch.java:61` `classify` | `HOLD GOVERNED_LEDGER_MISSING`. |
| `re/EscrowGovernedSourceFlight.java:42`, `EscrowGovernedDestination.java:55`, `EscrowGovernedPhases.java:222`, `EscrowGovernedArrival.java:84`, `EscrowGovernedReturn.java:173`, `EscrowGovernedTerminal.java:62` | Held, or no authority. |
| `rs/RocketGovernedCleanupDispatcher.java:25` | No F8 cleanup. |
| `re/EscrowSettlementOperator.java:92`, `re/EscrowOperatorDiagnostics.java:59` `inspect` | The command is refused ("No escrow ledger ..."). |
| `re/EscrowSettlementPruning.java:189` and `:208` | Markers kept; refused. |
| `re/EscrowReconciliationPruning.java:136`, `:166`, `:182`, `:197` | No pruning; markers stay. |

### 1.4 Unchanged: observation and lifecycle

| Site | Null answer |
|---|---|
| `re/RocketEscrowAudit.java:55` `observe` | No diagnosis; there are no visible records, and nothing reconciles. |
| `re/EscrowSettlementLifecycle.java:28` | No chunk-monitor marks. |
| `re/EscrowRuntime.java:338` `tick` | No ledger tick, scheduler, settlement or pre-F2 cancellation: "nothing reconciles" (W328). |
| `re/EscrowRuntime.java:460` and `:495` | Nothing to stop or abort. |
| `re/EscrowRuntime.java:273` `starting` | W328's startup lookup; not in this task. |

### 1.5 Unchanged: covered by a changed gate

| Site | Why no change |
|---|---|
| `rs/RocketTransferFences.java:36`, `:223`, `:237` | `classify`, `loadedReason` and `actionAllowed` call `logicalLocked` or `escrowLogicalLocked` before any ledger read and fence with `ESCROW_PENDING_OR_TOMBSTONE`. `state()` at `:223` reads an unbound rocket as a valid endpoint, but every path that uses it is behind that lock. |
| `rs/RocketTransactionRecoveryService.java:85` | `knownPending \|\| logicalLocked` fences through `logicalLocked`. |
| `rs/RocketTransferEntities.java:324` `currentLedgerBinding` | False withholds recognition, the conservative answer; the fences decide every action. |
| `rocket/monitor/RocketMonitorBinding.java:62` | Identification only: without a head it uses a unique loaded hint, with the owner check. Every monitor launch goes through the flight service's idle gates. |

Test seams, not production reads: `re/EscrowRuntime.java:259` `replaceLedgerForTests` and
`re/ResourceEscrowSavedData.java:79` `forTests`.

`RocketEntity.updateFlightData` throws while `flightFrozen` is true. Every production caller that I found first checks
`idle`, `resourcesIdle` (through `resourceMutationAllowed`) or the legacy flight rule (`flightAllowed`, which checks
`escrowIdle` and `escrowLogicalLocked`), and under NOT_LOADED those refuse first. So frozen and not-idle stay
consistent, as they already are for an armed flight, and no tick path reaches the throw.

## 2. Shape of the fix

A new record `EscrowLedgerLookup(State state, ResourceEscrowSavedData data)` in `resourceescrow`:

- `LOADED`: `existing` returned a root, carried as `data`.
- `NO_FILE`: no root, and the delegate is NO_FILE.
- `NOT_LOADED`: no root after the startup classification, and the delegate is not NO_FILE. A delegate that is LOADED
  without a root in storage also counts, conservatively. A missing root read off the server thread, or with no
  runtime, also answers NOT_LOADED (fail closed, like `BayReceipts.blocksInsertion`).
- `UNCLASSIFIED`: no root before the startup classification (section 3).

**Correction (W328F, 2026-11-09).** As delivered in `41b85e98d`, the lookup called vanilla storage before it checked
the thread, and it did not check for an Overworld (review check 5). Since W328F item 3, off the server thread or without
an Overworld it answers NOT_LOADED before it touches storage, so an off-thread read answers NOT_LOADED even when a root
is loaded. Every production caller runs on the server thread.

`EscrowRuntime` gains one flag, set in `starting` right after `EscrowLedgerStartup.start`, and `missingLedger(server)`,
which reads the flag and the delegate. W328's lookup itself does not change. Each changed read becomes:

```java
var lookup = EscrowLedgerLookup.of(server);
if (lookup.notLoaded()) { return PROTECTIVE; }
var data = lookup.data(); // null for NO_FILE and UNCLASSIFIED: today's code continues unchanged
```

NO_FILE and UNCLASSIFIED keep today's answers, so a world without a ledger is unaffected.

## 3. Before ServerStartingEvent

The delegate is NOT_LOADED from registration until `EscrowRuntime.starting` (ServerStartingEvent, normal priority).
Protective reads that can run earlier:

- `EntityJoinLevelEvent` while `prepareLevels` loads the spawn and forced chunks. `RocketEscrowInterlocks.joined` calls
  `RocketReleaseAccess.joined`, which reads `idle` and, when a released passenger must be filtered out,
  `updateFlightData` and so `flightFrozen`. It also calls `RocketTransferRecoveryService.onRocketJoined`, whose
  `RocketTransferFences.classify` reads `logicalLocked`, and so `escrowLogicalLocked`.
- ServerStartingEvent handlers at the same priority, in no fixed order. `RocketManager.onServerStarting` runs at LOW,
  after the classification.

If UNCLASSIFIED answered protectively, every world, including every NO_FILE world, would fence each legacy journal
record whose rocket loads at startup, log `ARCE_TRANSFER_FENCED`, and hold its recovery for `RETRY_TICKS` (1,200
ticks). The release manifest filter would also be skipped. So UNCLASSIFIED keeps today's answers. This is harmless:

1. Before the classification, a loadable ledger is read through vanilla's lazy load. Only a ledger that will be
   NOT_LOADED reads as absent then.
2. No login, command or tick runs before ServerStartingEvent.
3. The early reads only classify fences and filter a manifest. Every action rechecks the gates after startup:
   recovery's `actionAllowed`, `flightAllowed`, and the launch, fill, cargo and disassembly gates. Those then answer
   protectively.

**Correction (W328F, 2026-11-09).** Point 3 does not hold for the manifest filter, which is a stated exception (review
check 5). `RocketReleaseAccess.joined` changes the rocket at once. If the filter removes passengers and
`RocketEscrowGate.idle` answers idle, which it does under UNCLASSIFIED for a rocket with no history in RAM, it calls
`updateFlightData` with the filtered manifest, and nothing rechecks that change later. The filter removes only passengers
whose release the rejoin sidecar already records: a release batch for this rocket with a pending seat entry, or the
installed release coordinator's own filter. It changes only the passenger manifest. It moves no fuel or cargo and grants
no transfer. This is the existing behaviour, kept on purpose so that joins in a NO_FILE world behave as before. W328F found
no unit-level way to pin it without a new production seam (W328F's record, item 4).

This is the bay's precedent: `BayReceipts.holds` is false before the receipts start (R390).

## 4. What the hold means for players (AGENTS.md section 3.3)

| Item | Hold under NOT_LOADED |
|---|---|
| Scope | The server: every rocket, and every fuel loader and cargo device that holds a root. Rockets cannot launch (player, monitor or admin), board or leave through the seat actions, take or lose fuel or cargo, be disassembled or retired, be killed by command, or be teleported by command. Their flight data cannot change. Legacy flights in progress pause where they are, and legacy transfer and assembly recovery fence their records. Bay sources are not neutral. Devices show "Resources are locked until escrow confirms them durably" and refuse removal, explosions, drops, port and container exchange, and linking. Players can still dismount with the vanilla control. No file is changed by the hold, and saving continues as usual. |
| Duration | The session. |
| Logs | No new log line. W328 logs one WARN per start (`ARCE_ESCROW_LEDGER_NOT_LOADED`). Existing fence WARNs fire at most once per legacy transfer record (64 at most) and once per pending assembly transaction (256 at most) when recovery reaches them. Their text says `repair=offline-whole-world`, although a restart that loads the ledger clears them. |
| Recovery | Fix the storage or permissions and restart. A start that loads the ledger or proves it absent restores every answer. |

**Correction (W328F, 2026-11-09): files.** "No file is changed by the hold" means that the hold writes no ledger: there
is no root in storage, creation needs NO_FILE, and vanilla saves only the roots it holds. Normal world saving goes on and
writes every other file as usual: chunks and entities, including the held rockets and devices, player data, and the other
SavedData roots such as the transfer journal and the bay receipts (review check 9).

**Correction (W328F, 2026-11-09): transfer-fence WARNs.** The Logs row's transfer-fence bound is wrong (review F3). Root
decision R397 keeps the deduplication and asks for its real behaviour. `ARCE_TRANSFER_FENCED`
(`rocket/server/RocketTransferFences.java` lines 260 to 262) is logged once per change of a record's stored fence reason,
not once per record. Both callers compare the new reason with the stored one: `fence` (lines 253 to 258) and `reevaluate`
(lines 136 to 149, which logs at line 143). A record's reason changes on these events:

- **First classification** (`classify`, line 69), only while the record has no stored reason. Under NOT_LOADED every
  ordinary record is fenced here with `ESCROW_PENDING_OR_TOMBSTONE`, because `logicalLocked` is true.
- **A join or leave of the record's rocket.** `RocketEscrowInterlocks.joined` and `left` (lines 21 and 28) both call
  `RocketRuntime.onRocketJoined`, which reaches `RocketTransferFences.joined` (lines 158 to 170). For an ordinary record
  that is already fenced, it fences again with `ENDPOINT_JOIN_PROOF_REQUIRED` (line 166). A further join or leave while
  that reason is stored logs nothing.
- **A reevaluation** (`reevaluate`, line 138): at most `RETRIES_PER_TICK` (2) observations per server tick across all
  records, operator retries included, and at most one per record per `RETRY_TICKS` (1,200 ticks). Under NOT_LOADED it
  observes `ESCROW_PENDING_OR_TOMBSTONE`, or `BAY_HELD` at a bay boundary, and never permits the record.
- **A final action guard** (`actionAllowed`, line 247) fences only a record that was permitted first, which does not
  happen to a fenced record under NOT_LOADED. Governed records go through `classifyGoverned` (line 95) and the same
  `fence` deduplication.

So a stable reason logs once, and a reason that alternates logs on each change. Under NOT_LOADED an ordinary record
alternates between `ENDPOINT_JOIN_PROOF_REQUIRED` after a join or leave of its rocket and `ESCROW_PENDING_OR_TOMBSTONE`
after its next reevaluation. After its first fence, that is at most two WARNs per record per 1,200-tick reevaluation
interval, and only when its rocket joins or leaves a level in between, for example when its chunk loads or unloads or it
is teleported. With at most 64 records (`MAX_ACTIVE_TRANSFERS`, `rocket/flight/RocketFlightLimits.java` line 12), the
worst case is 128 WARNs per 1,200 ticks server-wide, and the reevaluation WARNs are also limited to two per tick. There
is no per-session total: the count grows with the number of joins and leaves.
`RocketTransferFencesTest.fenceWarnsRepeatOnEachReasonChangeButNotForAStableReason` pins this behaviour (W328F item 5).

The assembly fence is a separate mechanism. `ARCE_ROCKET_RECOVERY_FENCED` is logged once per pending transaction while
it stays fenced (`RocketTransactionRecoveryService.fence`, lines 141 to 147, through `fencedTransactions.add`), so the
Logs row's 256 bound for assembly transactions stands. That once-per-transaction rule does not apply to transfer fences.

**Correction (W328F, 2026-11-09): bay-claim WARNs, which this section omitted.** Under NOT_LOADED `bayNeutral` is false,
so a bay claim that reaches one of these steps is held for offline repair:

- `BaySourceResume.resume` refuses a loaded source with `SOURCE_AUTHORITY_CONFLICT` (line 117) and a rebuilt one with
  `BAY_SOURCE_PROTECTED` (`rebuild`, line 168).
- The released-flight resume returns `BAY_SOURCE_PROTECTED` (`BayReleasedResume` line 41 and its later checks), and
  `BayReleasedFlight.step` holds the claim with it (lines 107 to 113).
- The consumption and copy steps refuse the same way (`BayConsumptionSteps` line 62; `BayCopySteps` lines 60, 70, 91
  and 94).
- At a bay boundary, the transfer fences report `escrowLogicalLocked` as a conflict (`RocketTransferFences` lines 180 to
  181 and 246), and `BayTransferRecovery.escrowConflict` holds the claim with `ESCROW_AUTHORITY_CONFLICT` (lines 162 to
  165).

`BayClaim.repair` (`rocket/bay/deployment/BayClaim.java` lines 177 to 182) puts the claim in REPAIR for the rest of the
session and latches both its first repair reason and its first reason that cannot release flight.
`BayTransferRecovery.audit` (lines 490 to 502) logs `ARCE_BAY_CLAIM ... state=REPAIR reason=...` as a WARN once per
change of the claim's state and reason, deduplicated against the claim's previous line (line 496). None of the reasons
above can release flight, so once one latches the claim's line no longer changes, and the refusal adds at most one WARN
per claim. A claim already held for `REGISTRY_BLOCKED` or `REGISTRY_SIGNATURE_CONFLICT` logs one more when the new reason
latches. There are at most 256 claims (`BayClaimBook.LIMIT`), and claim steps run at most 32 per tick server-wide
(`BayStepQueue.STEPS_PER_TICK`). A group check that finds the source not neutral (`BayTransferRecovery` line 301) rejects
the claim's bays instead and logs at INFO.

After W328, NOT_LOADED is reachable only when a status check or load fails after the pre-start check.

## 5. Proposed risk-register text (Root edits the register)

**R-035, escrow ledger paragraph:** "W329（R393）：账本处于 NOT_LOADED（文件存在但未能载入，或其存在性无法证明）时，只读保护判定一律给出保护值：火箭不视为空闲、不可在加注前退役、不作为中性卫星舱来源、逻辑锁成立、飞行状态冻结；持有根数据的燃料加注器和货物装置一律视为托管锁定。范围为整个服务器的全部火箭和这些装置：本次会话内火箭不能发射、经座位操作登乘或离开、加注、装卸货、拆解、退役，也不能被命令击杀或传送；进行中的普通转移暂停，普通转移与装配恢复对其记录设围栏；装置拒绝拆除、爆炸、掉落、端口与容器交换及链接。玩家仍可用原版操作下车。保持不改动任何文件，保存照常进行。持续到本次会话结束。不新增日志：W328 每次启动一条 WARN `ARCE_ESCROW_LEDGER_NOT_LOADED`；恢复触及时，每条普通转移记录（至多 64 条）和每个待恢复装配事务（至多 256 个）各有一条已有的围栏 WARN，其文字仍写 repair=offline-whole-world，但重启即可恢复。恢复方式：修复存储或权限后重启，能载入账本或证明其不存在即恢复全部判定。ServerStartingEvent 分类之前（出生区块载入实体时）保持原有判定，与卫星舱收据（R390）相同。NO_FILE 世界（生产准入关闭时的全部生产世界）不受影响。"

**Correction (W328F, 2026-11-09).** Three parts of this proposed text are wrong or incomplete; see the corrections in
sections 3 and 4. "每条普通转移记录（至多 64 条）……各有一条已有的围栏 WARN" (one fence WARN per transfer record) is wrong:
the transfer-fence WARN repeats on each change of a record's reason, at most two per record per 1,200 ticks under
NOT_LOADED, and the omitted bay-claim repair WARNs add at most one per claim for these refusals (256 claims at most).
The assembly bound stands. "保持不改动任何文件，保存照常进行" should say that the hold writes no ledger while normal saving still writes every
other file. "保持原有判定" before ServerStartingEvent should name the manifest filter as an immediate change that is not
rechecked later: it removes only passengers whose release is already recorded and moves no fuel or cargo.

## 6. Tests

- **Unit:** `EscrowLedgerLookupTest` covers the classification: UNCLASSIFIED before the flag (without reading the
  delegate), NO_FILE for a NO_FILE delegate, NOT_LOADED for a NOT_LOADED or LOADED delegate, and the record's
  invariant. A delegate test covers the test seam below.
- **GameTest:** two new batches, so that no other test runs while the server-wide state is changed.
  - `w329_ledger_not_loaded` puts the server into NOT_LOADED. It sets vanilla's cache entry for the ledger to null,
    which is the cached miss that a failed load leaves, and returns the PROPULSION delegate to NOT_LOADED through a
    new package-private `Delegate.unloadForTests`. The GameTest calls it only after `EscrowTestEndpoints.requireEnabled`.
    So the shared authority answers UNAVAILABLE, as in production. `@AfterBatch` restores the ledger, or NO_FILE,
    through `replaceLedgerForTests`. Each changed read has one test with an ordinary rocket or device, which has no
    history in RAM. This is the rocket whose history lives only in the ledger. The read answers its protective value,
    and the operation it guards is refused: `kill` leaves the rocket (`escrowIdle`); `updateFlightData` throws
    (`flightFrozen`); `resourceMutationAllowed`, which disassembly's validation reads, is false
    (`retirementUnprotected`); `logicalLocked` is true (`escrowLogicalLocked`); the bay source predicate is false
    (`bayNeutral`); the device refuses removal and reports `ESCROW_PENDING` (cargo hold and loader progress); and the
    status line says `ledger=not-loaded`.
  - `w329_ledger_no_file` detaches any ledger to NO_FILE (`replaceLedgerForTests(server, null)`) and restores it
    afterwards. The same reads keep today's answers for an ordinary rocket or device.
- Each new NOT_LOADED test fails without its fix: I will revert the production change and run the batch, then restore.
