# v1.8 residual-risk signing drafts, with production admission off

Date: 2026-11-25. Status: **DRAFT ONLY. Every signature below is blank.**
Authority: [TASK W473](../TASK-W473-RESIDUAL-RISK-SIGNOFF-DRAFTS.md), Root decision R689,
and the [owner's delegation of 2026-11-24](OWNER-DECISIONS-20261124.md). Root may decide on
the owner's behalf; this worker supplies evidence-based drafts, not decisions.

## 1. Baseline, terminology and decision boundary

- **C6** is the tested candidate commit `c8fa16e16be87554f26c402546733df6555d15e6`.
  Its hosted release JAR SHA-256 is `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
  [C6-SWITCH-REPORT section 2.1](C6-SWITCH-REPORT.md#21-candidate-c6) supplies the identity and results.
  This draft's shared-branch baseline is `412cc0323424c3951e971c20b75a1a235aa1f7b6`.
  `git diff C6 HEAD -- src/main` is empty at that baseline. The only `src/test` difference is
  four lines in `src/test/resources/escrow-probe-cases.tsv`, not a product-source change.
- **A1** means hosted GameTest evidence; **S2** means native forced-process-stop/restart evidence;
  **V1** means real-GPU client evidence; **V2** means two real clients; **REF** means the
  4-vCPU/8-GiB Debian reference node; **SOAK** means the four-hour reference workload.
  These are different evidence classes, not interchangeable passes.
- **L** is a logical rocket identity; **T** is its transfer record. **D0-D7** are the bay
  deployment steps defined in [W27A-DESIGN](W27A-DESIGN.md). **K02**, **K11**, **K12** and
  **K15-K18** name preparation packages in the [campaign preparation breakdown](CAMPAIGN-PREP-BREAKDOWN.md),
  not campaign results. Campaign IDs A01-J29 refer to the [final campaign plan](FINAL-CAMPAIGN-PLAN.md)
  and [checklists A](CAMPAIGN-CHECKLISTS-A.md), [B](CAMPAIGN-CHECKLISTS-B.md),
  [C](CAMPAIGN-CHECKLISTS-C.md), [D](CAMPAIGN-CHECKLISTS-D.md), [E](CAMPAIGN-CHECKLISTS-E.md),
  [F](CAMPAIGN-CHECKLISTS-F.md), [G](CAMPAIGN-CHECKLISTS-G.md), [H](CAMPAIGN-CHECKLISTS-H.md),
  [I](CAMPAIGN-CHECKLISTS-I.md) and [J](CAMPAIGN-CHECKLISTS-J.md).
- All six production admissions stay off under R682 and [ADR-062 revision 8](../../../decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md#revision-8--production-admission-deferred-to-v190-2026-11-24).
  The off-state decisions are implemented in [BayDeploymentSwitches](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/BayDeploymentSwitches.java),
  [EscrowCargoSwitches](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCargoSwitches.java)
  and [EscrowCoordinator](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowCoordinator.java).
  [EscrowTestEndpoints](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowTestEndpoints.java)
  and [EscrowLoaderAdmission](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowLoaderAdmission.java)
  separately restrict testing. Ordinary placement does not open an admission switch.
- Off-state protection is not off-state absence: existing files are loaded and recovered without a new-admission
  permission. A production server can receive a previously tested or imported whole world. These drafts cover
  only the expressly named off-state protections and ordinary workflows, not authorization to create new
  managed work. R682's deferred admission risk scopes remain outside them.
- R-044's downgrade text already has its separate disposition, recorded in
  [R044-SIGNOFF-DRAFT section 5](R044-SIGNOFF-DRAFT.md#5-revised-text-for-both-format-boundaries-draft-2026-11-19).
  Nothing below re-signs, broadens or replaces that text.

## 2. Summary for Root

These are recommendations for the individual scopes, not a proposal to close every paragraph of a register row.
`SIGN_NOW` means the narrow residual decision can be made independently of still-open release Gates.
`SIGN_AFTER` requires the named evidence, not merely a scheduled run. Publication proposals are in section 14.

| Scope | Reach in v1.8 with six switches off | Recommendation | Preconditions before signing |
|---|---|---|---|
| R-021: ordinary machines' save refusal, including classic controllers/hatches and solar arrays. | Live on ordinary worlds with non-admissible retained roots; no ledger or switch is required. | SIGN_AFTER | Section 4's native refusal/repair evidence, plus I02/I05 and the operator disclosure. |
| R-030: registry refusal/quarantine and ground-survey sizing. | Live ordinary satellite/research and startup code. | SIGN_AFTER | G01, J19, A09 and A08/I10; explicit canonical-refusal carry-over review. |
| R-035: ordinary journal/sealed/rejoin effects and existing-ledger recovery/migration. | Ordinary journal/rejoin work is live; escrow recovery needs existing history, not a new switch. | SIGN_AFTER | A04/K02 disposition, A10/A17, J10/J11/J17 and A09/A08/I10; section 6's uncovered items remain explicit. |
| R-039: receipt storage, insertion fencing and startup presence checks. | Presence checks affect any world; retained-file effects need that file; new receipts need a test admission. | SIGN_NOW | none. |
| R-040: off-thread transaction writer and shutdown wait. | Live ordinary assembly/disassembly, including worlds without bays or ledgers. | SIGN_AFTER | A04/A10/A17, REF A09 and A08/I10, including writer drain and near-bound costs. |
| R-041: off-thread registry, terminal locks and epoch exhaustion. | Live ordinary satellite terminal/research workflows; no bay admission is required. | SIGN_AFTER | G01/G04/G07, A13/J19 and A09/A08/I10. |
| R-042: retained receipts/ledger holds and shared pending quotas. | Retained files or a post-preflight access race can affect ordinary rockets server-wide. | NEEDS_KNOWN_ISSUE | Publish proposed KI-V180-HOLD-01 and its operator instructions; retain section 10's coverage limits. |
| R-043: retained-receipt recovery, duplicate boundary and unbounded waits. | Existing-receipt recovery runs on production servers; new D0 remains test-only. | SIGN_AFTER | J01-J05/J09, A09/A08/I10; larger REF queues and section 11's client/coverage dispositions. |
| R-044: unsupported fuel-loader roots. | Craftable/placeable loader protection is live without filling admission. | SIGN_NOW | none. |
| R-044: shared 256-coordinate denial pool. | Live for all participating protected families, not just the six deferred devices. | NEEDS_KNOWN_ISSUE | Publish proposed KI-V180-GUARD-01 and the common save-refusal procedure. |

## 3. Evidence rules and open items shared by the drafts

1. C6's integrated native attempt 2 remains **74 PASS / 4 FAILED**. The separate X03 repair rerun gives
   **75 PASS / 3 FAILED** across the selected families; it does not overwrite the first attempt. The three
   old-JAR families stay failed. C6 hosted run `44770334930`, attempt 1, reports 1,886 GameTests.
   [W466](W466.md) resolves the earlier missing-shard gap for **89 rows / 86 distinct methods / 106 mappings**,
   including W329's item devices. Its hosted A1 evidence is not a native, V2 or REF result.
2. [C6 report sections 13.2-13.5](C6-SWITCH-REPORT.md#13-x01-to-x03-c6-results-recorded-dispositions-and-residuals)
   keep X01 **FAIL** (10 of 82 late-loss states fail), [W451](W451.md) **OBSERVED**, not PASS, and X02's original
   **NOT_CAUGHT/INCOMPLETE/NOT_APPLICABLE** verdicts alongside reruns. In particular, RESOURCE_ESCROW-STALE
   remains NOT_CAUGHT; the original bay corruption pair and satellite read-fault pair are not promoted by their
   diagnostic/oracle explanations. The four inapplicable shapes remain uncovered.
3. W451's next load, unload, disassembly or launch after lost history is untested. R641/R682 place that test
   before later enablement, not as a new v1.8 admission obligation. These drafts do not accept that lost-history
   admission scope. X01/X02 do not simulate real power loss, OS/filesystem cache recovery, torn sectors, all
   region/entity writes, player/POI/metadata, plain SavedData or simultaneous multi-store faults.
4. [OWNER-FINAL-PACKET-DRAFT section 7](OWNER-FINAL-PACKET-DRAFT.md#7-还没写进本包的内容) keeps K02 partial:
   [W461](W461.md) covers 89 sealed-disassembly cuts, but four older W437 cuts have no matching cut, and passenger/
   client work is separate. [W465](W465.md) covers seven flight checkpoints with the rocket chunk kept loaded;
   destination-spawn, passenger-transfer and source-removal checkpoints are unreachable by C6's pause mechanism
   ([W462](W462.md)). [W464](W464.md) explains request expiry/unloaded-survivor behavior. Neither a diagnostic
   nor a later loaded-chunk success fills those three checkpoint gaps.
5. Intermittent items stay open: [W350](W350.md) retains two `EscrowProofRetryGameTests` failures and
   `SealedSpecialHostAutomationGameTests.tankAndBayStaySealedUnderTickingAutomation`, although a branch run and
   later aggregate runs passed. Their cause was not established. A18 and KI-V180-CONNECT-01 retain first-connection
   timeouts with no tolerable-failure ratio. W196's narrow-package initialization failure W196-R21 remains history
   in R-043; an aggregate pass does not explain it. No signature asserts these never recur.
6. REF/SOAK and V1/V2 are still open. A09 needs its own run with only `releaseTestHooks` (R648), not the
   instrumented soak as its distribution. K15's proxies do not establish all cache/ticket counters. K10's
   development-host railgun throughput (about 6/s versus 16/20 ticks) is a separate H07 finding: if reproduced on
   REF it needs a fix or reviewed waiver ADR before P11, not a signature here. All failed budget results remain
   failed under [docs/17](../../../17-V1PLUS-QUALITY-BUDGETS.md).

## 4. R-021: machine-root refusal and unrelated terrain rollback

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **26**, with the classic extension
at lines **28-56** and solar-array extension at **58-72**: a rejected root prevents its whole terrain chunk
from saving, and the shared pool can widen this to every later terrain save in that Level while other stores save.

**Shipping reach and code.** The [GuardedChunkSaves](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/GuardedChunkSaves.java)
event bridge and [ChunkSaveDenials](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/ChunkSaveDenials.java)
have no admission gate. [SolarProtection](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/solar/SolarProtection.java),
[TankProtection](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/tank/TankProtection.java)
and [CombustionProtection](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/combustion/CombustionProtection.java)
therefore remain live in ordinary worlds. Unsupported retained roots, corruption or operator/third-party changes
can trigger them; normal valid roots do not. A bounded future/partial classic controller root is quarantined and
retained, not automatically a save refusal. Missing-all-root fresh initialization is not deletion of an existing
root. [ADR-064 section 11](../../../decisions/ADR-064-CLASSIC-MACHINES-FLUIDS-AND-COMPONENTS.md#11-persistence-and-migration)
and [W01G](W01G.md) establish those distinctions.

**Evidence and open work.**
[GuardedChunkSaveGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/GuardedChunkSaveGameTests.java)
tests `rejectedChunkRefusesLaterEmptySerialization` and `denialSurvivesReplacementOfTheLiveChunkObject` pin sticky
denial, not real disk recovery. [W17](W17.md)/[W17F](W17F.md) retain solar-array scope and the native-restart gap.
C6's six `w336-saturation-*` families establish the common pool through fuel/bay/cargo fixtures, not every
original machine's repaired resource state. I02 and I05 have K05 machine-layout preparation, but no final ordinary
machine unload/restart/conservation result is established here. Do not infer original-machine native recovery
from those six saturation fixtures or hosted aggregate counts.

**Proposed signing text (Chinese; not effective until signed).**

> 1. **范围。** 本文仅针对 C6 中 R-021 的储罐、泵、燃烧发电机、既有加工机器、classic 控制器及 hatch、太阳能发电机与阵列的保留根保护。
> 2. **会发生什么。** 存在但不符合相应保存界限或原生表示要求的根，使整地形区块拒存；受限且可保留的未知根可能只隔离设备。拒存会使无关地形改动回退，与分别保存的实体、玩家及全局数据产生分歧，不是世界原子暂停。
> 3. **范围与持续时间。** 拒存粘连至 Level 关闭；共享池的第 257 个不同区块坐标可扩大为该 Level 后续全部地形拒存。重启重建内存状态，不修复原根。保存重试、ERROR 和堆栈没有累计上限。
> 4. **恢复。** 停止玩家操作并停服，保留原件、日志、坐标和完整世界哈希；只在完整副本中修复，核对库存、银行、FE、进度与身份，完成两次保存及重启并证实 region writer 成功，再整体替换。不得删除根、单独回滚文件或在线清除保护。
> 5. **运营要求。** 公布整区块与整 Level 影响，监控拒存与磁盘日志量，备有包含各维度地形、实体、POI、玩家、全局数据及元数据的一致完整备份。
> 6. **失败保留。** 证据和预算失败原样保留；本文不把 GameTest 当原生恢复，不放宽断言，也不证明跨存储零损失。
> 7. **不包括。** 不接受其他设备族的独立风险，不开启任何生产开关，不批准 Gate、版本或发布；未完成的原生恢复与性能检查仍必须完成。
> 8. **复审。** 最迟 v1.9.0 发布前复审；根范围、保存判定、共享容量、生命周期、日志或恢复行为变化，或出现受支持普通操作导致损失的新证据时，本文失效，须重新评审。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers only C6's R-021 machine families and retained-root protection. A non-admissible
present root can refuse the whole terrain chunk; bounded unsupported roots can instead quarantine one device.
The 257th distinct denied chunk coordinate can refuse later terrain saves throughout the Level until it closes,
while separate stores continue saving; retries and ERROR stacks have no lifetime cap. Stop play and the server,
keep a hashed complete original, repair a whole copy, verify resources/identities and actual writer success through
two saves/restarts, then replace the whole world. Publish the scope and monitor logs; never delete a root, reset
the guard online or restore isolated files. Failures stay failed and GameTests do not establish native recovery.
No other family, switch, Gate, version or release is authorized. Review before v1.9.0; changed protection/recovery
semantics or new supported-operation loss invalidates the text.

**Preconditions.** Attach committed-source native refusal, repair, writer-success and two-restart resource evidence
for the original machine/classic/solar scopes the register still marks unverified, or an explicit mapping to
existing equivalent evidence. I02/I05 must supply the ordinary unload/restart/conservation observations. Publish
section 14's guard procedure. Neither the register nor C6 report supplies the missing family mapping.

**Recommendation.** `SIGN_AFTER` with the preceding native evidence, I02/I05 and disclosure. An unexplained loss in
supported operation requires a fix or waiver ADR, not this signature.

## 5. R-030: registry startup refusal, isolation and sizing cost

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **95**: invalid/future/over-bound
registry data can block world startup; inconsistent job/instance pairs can remain isolated, and sizing has
startup and exceptional full-root costs not established on REF.

**Shipping reach and code.** Ordinary ground surveys and satellite missions remain enabled.
[GroundSurveys](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/mission/GroundSurveys.java),
[CodecRecordSizer](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/persistence/CodecRecordSizer.java)
and [SatelliteRegistryPayload](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/persistence/SatelliteRegistryPayload.java)
do not depend on a cargo/bay/fuel switch. Current-root decode calls `finishRestore` then the canonical encoder;
[BetaWorldMigrationEvents](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/migration/BetaWorldMigrationEvents.java)
can refuse the world before Levels/logins. Invalid retained data requires imported/corrupt/operator input, not
new bay admission. Normal survey capacity refusal can happen in an ordinary busy world.

**Evidence and open work.** [ADR-051's W21b note](../../../decisions/ADR-051-RESOURCE-MISSION-INSTANCES-AND-DELIVERY.md#implementation-note-ground-survey-origins-and-registry-schema-4-w21b-2026-10-13),
[W216](W216.md) and [W224](W224.md) define the recovery and costs. W224's eight native starts prove canonical
refusal, preserved source bytes and repaired/normal reload on `5e33ce834`, not on C6. A comparison to C6 finds no
changes to its payload/record codecs, canonical exception or count-only size helper; the satellite branch in
`ManagedSavedDataPayloadValidator` is unchanged. The migration adapter and startup event do have later escrow/
diagnostic changes, so the whole startup stack is not byte-identical and its integration still needs review.
W216's Windows START median reduction (286.2 to 1.4 ms) is not a percentile Gate; UNKNOWN sizing still falls back.
[GroundSurveyRegistryGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/GroundSurveyRegistryGameTests.java)
includes `savedRegistryCompletesAndCreditsExactlyOnce`. This is a test reference, not a newly audited C6 named pass.
G01's K09 load sets and J19's K14 controller scenes are preparation; REF restore cost, UNKNOWN fallback frequency,
concurrent work, long-run allocation, actual client behavior and A09/A08/I10 remain open.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 普通卫星注册表 schema 4 与地面勘测的载入、隔离、容量拒绝和计量成本，不包括卫星舱的新部署准入。
> 2. **会发生什么。** 未来、损坏、计数或规范编码超限的数据可阻断整个注册表并拒绝开服；跨记录冲突只隔离相关任务、实例和依赖项，不发研究点、资源或退款。段预算耗尽拒绝新任务，不驱逐已有记录。
> 3. **范围与持续时间。** 开服拒绝持续至每次检查通过；隔离没有到期时间。已知驻留计量减少普通变更成本，但未知计量仍可能执行昂贵整根检查；载入与迁移承担逐记录计量和规范编码，不承诺启动或每 tick 耗时上限。
> 4. **恢复。** 停服备份完整世界，在副本中一致修复所有关联 UUID、认领、快照及账户，或整体恢复匹配备份，完成校验与两次保存重启。ACTIVE 隔离对、重复处理器认领只能离线修复；不得只删点名记录或恢复单个权威文件。
> 5. **运营要求。** 保留首个超限诊断及汇总日志，按现有预算运行；规范编码必须实际落入上限。生命周期日志没有累计上限，开发机计时不替代参考节点。
> 6. **失败保留。** W213 的历史超时、W224 的失败尝试和后续任何预算失败继续保留，不由优化中位数或本文改为通过。
> 7. **不包括。** 不保证未知外部键或所有后续生命周期增长，不扩容，不开启任何生产开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；schema、编码器、修复规则、计量或容量策略改变，或受支持记录被新证据证明丢失时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers C6's ordinary schema-4 registry and ground-survey loading, isolation, capacity
and sizing, not new bay admission. Invalid/future/canonically oversized data can refuse the whole world; linked
inconsistencies isolate only affected records without rewards or refunds, while section exhaustion refuses new
jobs without eviction. Refusal lasts until validation succeeds, isolation has no expiry, and UNKNOWN measurement
can still require a costly full-root check; startup pays eager sizing and canonical encoding. Back up the whole
stopped world, repair linked identities/claims/accounts together on a copy, validate and save/restart twice, or
restore a complete consistent backup; never delete a single authority file. Keep diagnostics and budgets; Windows
medians are not REF results. Historical failures stay failed. Unknown external keys, all future lifecycle growth,
larger bounds, switches, Gates and release are excluded. Review before v1.9.0; changed codecs/repair/sizing policy
or new supported-record loss invalidates the text.

**Preconditions.** G01's startup/load/flush measurements and restart comparisons, J19's survey/analysis/rebind
observations, A09 and A08/I10; Root must record that W224's unchanged satellite branch still reaches the same
canonical-refusal/preservation behavior through C6's changed startup wrapper, by review plus candidate validation
or a candidate native rerun. Preserve separate extended-1,000-mission results and UNKNOWN cases.

**Recommendation.** `SIGN_AFTER` with those named records/results. A failed REF budget needs a fix or reviewed ADR.

## 6. R-035: ordinary transfer/sealed/rejoin behavior and retained escrow history

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **100**: checked transfer writes,
sealed materialization, player rejoin and existing-ledger migration/recovery can pause effects, refuse startup or
retain repair locks; authority, storage and partial-history exclusions are broader than a single device.

**Shipping reach and code.**
[RocketTransferSavedData](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/RocketTransferSavedData.java),
[SealedDisassemblyCoordinator](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/SealedDisassemblyCoordinator.java)
and [RocketRejoinSavedData](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/rejoin/RocketRejoinSavedData.java)
serve ordinary worlds. Baseline journal writes occur even without rockets. Ordinary flight, destination preparation,
landed disassembly, legacy passenger release and first-event progression do not require managed admission.
[EscrowRuntime](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowRuntime.java)
and [WorldDataMigrationService](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/migration/WorldDataMigrationService.java)
load existing history; root-2 to root-3 migration is active under
[EscrowAccountingFormat](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowAccountingFormat.java),
independent of admission. New managed fills/cargo/governed work still need the test gate. The existing-world
recovery scope cannot be dismissed by calling every production world NO_FILE.

**Evidence and open work.** [ADR-065](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md)
records W91/W98/W152/W164, cargo protection and rejoin behavior. C6's `cargo-B3-S1/S4/S5/S6-REUSE`, cargo
settlement families, activated accounting families and both-origin X03 rerun cover their named test-world shapes
([C6 sections 8 and 12-13](C6-SWITCH-REPORT.md#8-rows-shared-by-the-four-cargo-switches)). They are not ordinary
flight checkpoint coverage. Section 3's K02 and intermittent items remain open, including ordinary
`LF_S6_F9_RESPAWN` NOT_COVERED; the governed F9 case is not its replacement. [W465](W465.md)'s seven checks require
the survivor chunk loaded. The supported retry behavior is already in KI-V180-DISASSEMBLY-01.
[W373B](W373B.md) and C6's accounting upgrade family retain interrupted migration/backup-cap behavior, now
KI-V180-UPGRADE-01. X03's approved narrow repair ([operator guide](../../../ENDGAME-OPERATOR-GUIDE.md#whole-world-repair-after-a-floor_conflict-fence))
does not reconstruct missing history or permit a general refund; standalone unactivated root-2 X03 is NOT_COVERED.
J10's first-use/cold-destination costs, J11's first-event flush, J17's real-client advancements, A10/A17 and the
rejoin/passenger observations still need their campaign packages. J11 exceeding REF budget requires the designed,
independently reviewed asynchronous-save task specified by R245; this draft cannot accept that failure away.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 普通转移日志、密封拆解、遗留乘员重入与已有托管历史的防护、迁移和恢复，不接受六项关闭准入的新增业务风险，也不重新签核 R-044 降级。
> 2. **会发生什么。** 当前记录未获确切写入确认时依赖效果等待；写入未知、日志不可读或会话基线未确认可暂停全部转移。已有账本、sidecar 或关联权威无效可拒绝开服或扣留对象，UNKNOWN_BASELINE 不当作零，密封载体不能凭缺失证明自动解封或退款。
> 3. **范围与持续时间。** 普通等待可涉及单条记录，UNKNOWN 或阻断可涉及全服务器；组件、收据、墓碑、乘员和容量的保留按原规则，可跨重启且无自动到期。重试通常按 200 tick，不能据此承诺完成期限。日志按状态输出但累计无上限，地形、实体和玩家并非原子保存。
> 4. **恢复。** 可重试存储故障修复后按原重试或正常重启恢复；持续权威冲突只在完整世界副本中离线修复并核对守恒、两次保存重启及新屏障，再整体替换。X03 仅可按已公布的 C6 单记录静止形态流程使用，不用于历史重建。保留迁移备份，已有五份时停服移出旧目录归档。
> 5. **运营要求。** 停服做完整世界备份，包含各维度、实体、POI、玩家、日志、账本、sidecar、全局数据和元数据；不要删记录、改单文件或把 save-all flush 当一致备份。遵守拆解再次请求、在线与距离条件，不能把保持区块加载的测试说成自然卸载保证。
> 6. **失败保留。** X01、X02、旧版降级、原始 X03 失败和 K02 未覆盖项原样保留，W451 的被动观察不是下一次操作通过；预算失败、偶发失败及未测的乘员检查不被本文关闭。
> 7. **不包括。** 不覆盖断电、逐文件回滚、未观察的外部副本、第三方存储绕过或玩家数据原子耐久，不扩大修复权限；不开启开关，不批准 Gate、版本或发布，不豁免 Required Gate。
> 8. **复审。** 最迟 v1.9.0 发布前复审；日志、账本、sidecar、恢复权威、保存顺序、加载票据或准入变化，或受支持操作出现复制、丢失或不能恢复的新证据时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers only C6 ordinary journals, sealed disassembly, legacy rejoin and protection/
migration/recovery of existing escrow history, not new managed admission or the separately decided downgrade.
Unacknowledged effects wait; UNKNOWN, unreadable journals or an unproved baseline can stop all transfers, and
invalid linked authority can refuse startup or hold objects. Holds/receipts/tombstones and capacity can persist
across restarts without a deadline; 200-tick retries are not completion guarantees and separate stores are not
atomic. Repair retryable storage, otherwise repair a hashed whole-world copy, verify conservation and two
saves/restarts/fresh barriers, then replace the whole world. X03 is only its published one-record stationary
repair; archive older migration backups when the five-folder limit is reached. Back up all stores after stopping,
respect disassembly retry/online/distance rules, and never restore isolated authority files. Every failure and
uncovered checkpoint remains as recorded; passive W451 observations prove no next operation. Power loss, unseen
copies, bypass storage and atomic player durability are excluded, as are switches, Gates and release. Review
before v1.9.0; changed authority/formats/save/ticket/admission behavior or new supported-operation loss voids it.

**Preconditions.** A04/K02 must have a Root disposition explicitly separating the 89 sealed cuts, seven loaded-
chunk flight passes, three unreachable pauses, four unmatched historical cuts and player/client gaps. Any
unmet Required Gate needs actual coverage or an approved, bounded waiver ADR; a residual signature is not that
waiver. Complete A10/A17, J10/J11/J17 and A09/A08/I10, including rejoin/player cases and R245's J11 rule.
J22 must retain the native and X01/X02 history. Section 3's intermittent findings need an explicit still-open
disposition, not deletion because a later run passed.

**Recommendation.** `SIGN_AFTER` with those named results/dispositions. R682's new-admission risks remain deferred.

## 7. R-039: receipt-file and pre-start fail-closed behavior

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **104**: receipt root/presence
failures can refuse world startup or fence package/registry work; retained receipts and unavailable writers
can consume capacity indefinitely.

**Shipping reach and code.** [BayReceipts.start/holdsRocket](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/BayReceipts.java)
indexes an existing file without creating one. Preflight in
[WorldDataMigrationService](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/migration/WorldDataMigrationService.java)
checks every managed path before reads; only a no-follow missing-file result proves absence. Thus a storage
access failure can refuse an ordinary world with no receipts; a later race can leave NOT_LOADED for the session.
Root validation, capacity, retirement and blocking-receipt insertion rules need retained receipts or a gated test
that creates them. New production D0 remains off. D3 registry checkpointing is implemented, not absent as the
historical pre-W27d2 sentence in the row might suggest.

**Evidence and open work.** C6 `r042-U_BAY/U_LEDGER`, `r042-X_BAY/X_LEDGER`, `r042-N_BAY` and the forced receipt
families are recorded in [C6 sections 3 and 8.3-9.1](C6-SWITCH-REPORT.md#91-r-042-effects-on-the-whole-server).
[W343](W343.md)/[W345](W345.md) explain the negative controls and the preservation boundary: managed files unchanged,
vanilla LastPlayed/`level.dat_old` rotation allowed. The U/X real status-failure mechanism is Windows-specific,
and N_BAY injects load failure over valid bytes, not real corruption. [W466](W466.md) supplies the named shared
authority A1 mappings. The unreachable dismount, P_OWNER's untested other-owner bay, large-queue REF and real
clients remain open under R-042/R-043; they are not needed to authorize this narrow protective refusal policy.
No promise about retry latency or acceptance of new admission is proposed here.

[SatelliteBayReceiptGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/SatelliteBayReceiptGameTests.java)
names the startup and insertion contracts in `theReceiptIndexWasLoadedAtServerStartingBeforeTheFirstTick` and
`insertionIsRefusedThroughEverySurfaceWhileABlockingReceiptExists`. These are source test references; this draft
does not add a named C6 pass beyond W466's audited mappings.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 关闭新准入时已有卫星舱收据的保存、索引、放入保护和所有世界的受管文件存在性检查。
> 2. **会发生什么。** 无法证明路径存在性或根无效可拒绝整世界启动；预检后才失败的索引保持 NOT_LOADED，拒绝包裹放入与依赖准入。ARCE 不更改被拒的受管文件，但原版可能已更新 LastPlayed 并轮换 level.dat_old。
> 3. **范围与持续时间。** 开服拒绝持续至修复；会话内索引失败持续至可成功载入的重启。收据保留上限为全服 4,096、所有者 128、每条 1 KiB；待处理并集为 64/全服、8/火箭、16/所有者，没有运维豁免或到期驱逐。UNKNOWN 保留候选和名额，200 tick 重试不是期限。
> 4. **恢复。** 修复存储后由原重试或检查代恢复写入；索引失败修复后重启。根冲突按完整世界离线副本修复，不得删除、移动或单独恢复收据文件；在线退役仅适用全部现有守卫通过的已确认 REJECTED。
> 5. **运营要求。** 保留启动原因、文件和完整世界备份；日志有逐次或状态转换限制，但累计没有上限。区分 LOAD_FAILED 与 PRESENCE_UNKNOWN，不把未找到记录当作可安全重建。
> 6. **失败保留。** 原始失败、NOT_COVERED 和 X01/X02 分类保持原状，不因保护性拒绝得到签核而成为通过。
> 7. **不包括。** 不接受新部署、乘员行为、跨存储断电耐久或有限等待承诺，不开启任何生产开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；路径预检、根、索引、写入结果、容量、退役守卫或准入改变时，或出现被拒文件被覆盖的新证据时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers C6 existing receipt storage/index/insertion protection and all-world presence
checks with new admission off. Unknown presence or invalid roots can refuse startup; a later index failure holds
NOT_LOADED. Managed files remain unchanged, but vanilla can update LastPlayed and rotate metadata. Refusal lasts
until repair, session holds until a healthy restart; retained capacity is 4,096/server, 128/owner, 1 KiB/receipt,
with the shared pending union 64/server, 8/rocket, 16/owner and no expiry or operator bypass. UNKNOWN retains its
candidate and quotas; 200 ticks is a retry interval, not a deadline. Repair storage or a consistent whole-world
copy; never delete/move/restore the receipt file alone, and retire only a fully guarded confirmed REJECTED receipt.
Keep reasons, backups and logs with no lifetime log cap. Failures and uncovered cases stay unchanged. New
deployment, passengers, power-loss durability, bounded waits, switches, Gates and release are excluded. Review
before v1.9.0; changed checks/formats/index/writes/quotas/retirement/admission or overwrite evidence voids it.

**Preconditions.** none. Section 14's explanatory publication is still recommended before P11; it is not a missing
test for this limited refusal-policy decision.

**Recommendation.** `SIGN_NOW` for this scope only, without closing R-042/R-043 or accepting deferred admission.

## 8. R-040: transaction-writer lag and unbounded shutdown join

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **105**: ordinary transaction
writes are asynchronous, flush can return stale, failure blocks safe effects, and a stuck IO join can hold the
world indefinitely at shutdown even though the drain allows only three attempts.

**Shipping reach and code.**
[RocketTransactionSavedData](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/persistence/RocketTransactionSavedData.java),
[RocketTransactionWriter](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/persistence/RocketTransactionWriter.java)
and [CheckedRootWriter](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/CheckedRootWriter.java)
apply to ordinary assembly/disassembly. No ledger or admission switch is needed for disk-full, unreadable-file,
permission or shutdown faults. Whole-root encoding moved off the file-writer capture, not off every journal
operation: W210 admission/load sizing still has synchronous costs. Bay D0's wait needs a gated admission or
existing recovery context; it is not the only consumer of this journal.

**Evidence and open work.** [ADR-074](../../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md),
[W129a](W129a.md), [W129bc's clock correction](W129bc.md#continuation-results-under-r120-and-r122-2026-10-27)
and [W210](W210.md) establish attempts, capture/publication and corrected clock
binding. `CheckedRootWriter` and `RocketTransactionWriter` have no diff from W129bc's corrected integration
`6ff566ebe` to C6; this supports mechanism carry-over, not unchanged admission or whole-load cost. W129a's eight
native cases are historical and predate the clock/admission follow-ups. C6's forced-A/W331-T and shared native
journal-dependent cuts supplement them, not a rerun of all eight writer cases. A04/K02, A10 and A17 need final
ordinary cases. W210's near-bound preparation window retained a 5,463.814-ms maximum; a small ordinary-work
sample does not erase it. REF CPU contention/GC, normal final drain, watchdog interruption, A09 and A08/I10
remain unqualified; [the operator guide](../../../ENDGAME-OPERATOR-GUIDE.md#saves-backups-and-downgrades) already
distinguishes flush, drain and complete backups.

[RocketTransactionProofGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/persistence/RocketTransactionProofGameTests.java)
contains `serverCheckpointAcknowledgesTheOverworldTransactionFile`,
`autosaveAndSaveAllRequestAndPublishOnLaterTicks` and `failedUnknownAndBlockedWritesRefuseAbsence`.
They identify the proof contracts, not a newly audited set of C6 named passes or native shutdown results.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 普通火箭事务日志的异步检查写入、不可读保护、同步准入计量和停服排空。
> 2. **会发生什么。** save-all flush 只请求写入，不保证该文件最新；未证明时依赖效果等待，不可读或阻断时拒绝装配与拆解并不覆盖原文件。FAILED 保留脏状态，UNKNOWN 清除证明，成功的覆盖确认后才恢复。
> 3. **范围与持续时间。** 每世界一份日志。运行期尝试间隔至少 10 tick，失败后至少 200 tick；停服总共三次尝试，但 IO join 无时间上限，可一直占用世界。最终 RAM 状态不保证落盘，看门狗中断时甚至可能没有排空日志；累计错误输出没有上限。
> 4. **恢复。** 修复存储后请求或重启恢复；损坏文件须在完整世界副本中一致修复或整体恢复可信备份。IO 仍运行时不得复制或重开世界；必须结束进程后才能处理副本，结束进程不等于成功保存。
> 5. **运营要求。** 正常停服并核对 rocket_transactions 的 DRAINED 覆盖最终目标后再备份完整世界；DRAIN_INCOMPLETE、强制结束或无日志不是一致备份证明，运行中复制可能跨文件撕裂。保留实际编码、载入、GC 与近上限成本，不只报告捕获成本。
> 6. **失败保留。** 历史失败、准备尖峰和任何参考预算失败照实保留，不扩大超时、不降低断言或预算。
> 7. **不包括。** 不承诺断电或多文件原子耐久，不保证关闭时长，不开启开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；写入顺序、证明、排空、线程结束、计量或存储策略变化，或出现受支持流程数据丢失时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers C6 ordinary transaction writing, unreadable-root protection, admission sizing
and shutdown drain. Flush requests do not prove freshness; effects wait for proof, and unreadable roots are not
overwritten. The journal is world-wide; attempts are spaced by 10 ticks/200 after failure, but three shutdown
attempts do not bound IO join or guarantee final RAM durability, and watchdog interruption may leave no drain
line. Repair storage or a consistent whole-world copy; never reopen/copy while IO owns the world, and killing
the process is not a successful save. Back up the whole normally stopped world only after the final target is
covered by DRAINED; keep sizing, load, GC and preparation costs. Failures stay failed. Power-loss/multi-file
atomicity, bounded shutdown, switches, Gates and release are excluded. Review before v1.9.0; changed writer/
proof/drain/join/sizing policy or new supported-workflow loss invalidates the text.

**Preconditions.** A04/A10/A17 must include ordinary journal recovery and final drain comparisons; A09 and A08/I10
must measure near-bound/admission/startup work and overlapping IO/GC on REF, not just capture/publication.
Retain section 3's K02/intermittent dispositions and any unresolved writer-cut integration difference.

**Recommendation.** `SIGN_AFTER` with those rows and the unchanged-budget comparison.

## 9. R-041: registry writer, terminal locks and exhausted epochs

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **106**: unacknowledged registry
work can lock ordinary terminals, defer discovery and leave flush stale; IO join can stall shutdown, and epoch
exhaustion can persistently refuse mutations without automatic recovery.

**Shipping reach and code.**
[SatelliteRegistryWriter](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/persistence/SatelliteRegistryWriter.java),
[SatelliteMissionSavedData](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/persistence/SatelliteMissionSavedData.java)
and [TerminalPendingEffects](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/terminal/TerminalPendingEffects.java)
are ordinary terminal/mission/research services. They need no bay/managed-cargo switch. Slot mismatch can arise
from raw operator/third-party replacement; storage failure and unloaded terminals need no test world. An epoch
near `Long.MAX_VALUE` is structurally possible but no record establishes a practical natural-world time-to-exhaustion;
do not invent one. Bay D3 has its own gated/new versus retained-receipt reach.

**Evidence and open work.** [W129bc](W129bc.md) retains R3/R4/DISCOVERY on corrected `6ff566ebe`, the earlier eleven
native cases and their separate histories. The writer core and `TerminalPendingEffects` have no diff to C6;
`SatelliteMissionSavedData` and `SatelliteManager` do change, so only those specific mechanisms carry over
unchanged. C6's ten timing families and X02 satellite fault cases exercise candidate persistence, with the
READ_ERROR/READ_CORRUPT exact-byte failures still NOT_CAUGHT and limited to one satellite/no missions.
[SatelliteTerminalBarrierGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/satellite/persistence/SatelliteTerminalBarrierGameTests.java)
tests paid-discovery and moved-chip acknowledgement boundaries; no new named C6 audit of these methods is claimed.
G01/G04/G07 (K09/K18), A13/J19, REF overlap/GC and SOAK remain open. A simulated reconnect is not two-client replay
or REGISTRY_PENDING display evidence.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 普通卫星注册表的异步写入、确认后效果、终端保护及保存纪元耗尽。
> 2. **会发生什么。** 变更先在 RAM 中出现，提交覆盖后才消耗包裹、处理芯片或发布发现；终端答 REGISTRY_PENDING，表满或冲突答 REGISTRY_BUSY。发射与退役等待锁定包裹及芯片槽，失配或终端缺失可保持至会话结束。
> 3. **范围与持续时间。** 每世界一份注册表、最多 256 个等待终端、每 tick 最多完成 32 项，只处理已加载终端且不加票。存储失败等待无上限；停服丢弃 RAM 等待而不执行效果，重启按幂等规则重放。纪元耗尽跨重启拒绝变更，不回绕、不重置；停服三次尝试不限制 IO join 时长，日志累计无上限。
> 4. **恢复。** 修复存储并载入终端后完成已确认效果，或正常重启后按幂等路径处理尚在槽中的包裹；失配按完整世界副本修复。纪元耗尽必须另行审核迁移或运维决定，修磁盘或重启不能恢复，不能直接归零。
> 5. **运营要求。** save-all flush 不是最新文件保证；正常停服核对 satellite_registry 和事务日志 DRAINED 后备份完整世界。保留终端原因、提交序号、纪元及计量日志，不用原始替换解除锁。
> 6. **失败保留。** X02 两条读故障失败、历史失败与任何参考预算失败继续保留，开发主机的 GC 或吞吐结果不替代参考节点。
> 7. **不包括。** 不覆盖断电、逐文件回滚或原始替换的物品原子保存，不自动批准纪元修复，不开启任何开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；纪元、确认、重放、终端锁、配额或排空策略变化，或普通受支持操作出现重复支付、物品丢失的新证据时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers ordinary C6 registry writing, acknowledged effects, terminal protection and
epoch exhaustion. RAM changes precede acknowledgement; package/chip/discovery effects wait, and pending/busy
answers protect inventory. At most 256 terminal entries and 32 completions/tick use only loaded terminals with
no tickets; fault waits have no deadline, shutdown drops RAM waits without effects and restart uses idempotent
replay. Exhausted epochs persist across restart and require a separately reviewed migration/operator decision,
not reset or disk repair; shutdown join is unbounded and logs have no lifetime cap. Repair storage/load terminals
or repair a complete copy; back up the whole normally drained world, not a live flush snapshot. Original X02 and
budget failures remain failed. Power-loss/single-file/raw-replacement atomicity, epoch repair authority, switches,
Gates and release are excluded. Review before v1.9.0; changed epochs/proofs/replay/locks/quotas/drain policy or
new ordinary duplication/loss invalidates the text.

**Preconditions.** G01/G04/G07 must supply candidate restart, mission and terminal/replay comparisons; A13/J19
must show pending/busy and actual research behavior; A09/A08/I10 must establish REF and SOAK IO/GC costs.
Use the changed service adapters in those runs rather than treating W129bc as a complete C6 rerun.

**Recommendation.** `SIGN_AFTER` with those named results.

## 10. R-042: server-wide protective holds and shared pending slots

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **107**: retained receipt/ledger
state or NOT_LOADED can freeze ordinary rockets server-wide; released/REPAIR receipts can retain shared pending
slots and logical locks across every restart even after landing.

**Shipping reach and code.** [BayReceipts.holdsRocket](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/BayReceipts.java),
[RocketEscrowGate](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/RocketEscrowGate.java)
and [EscrowRuntime](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowRuntime.java)
apply protective reads and startup indexing without checking new admission. A retained file or imported test
world can cause the holds on a production server. Pre-start unknown presence can affect any world, and the
post-check race can produce a session hold without proving a file exists. New D0 and quota acquisition require
the test gate; NO_FILE on a healthy ordinary world does not manufacture a receipt/ledger. Known-file protection
is not permission to delete that file to obtain NO_FILE.

**Evidence and open work.** C6's ten `r042-*` families include N_BAY/L_LEDGER, P_SERVER/P_OWNER, U_BAY/U_LEDGER,
R031_FUTURE/R031_INVALID and X_BAY/X_LEDGER. [W466](W466.md) gives the F2/F3/F4/RT/N2 and W329 hosted mappings,
including item loader/unloader protection. [C6 section 9.1](C6-SWITCH-REPORT.md#91-r-042-effects-on-the-whole-server)
separates per-rocket, server-wide and cross-restart effects. NOT_COVERED remains for vanilla dismount under ledger
NOT_LOADED (reasoned unreachable, not executed), another owner's bay in P_OWNER, and real corruption in the
valid-byte N fixtures. Quotas 64/8/16 do not guarantee service: four owners can fill the server; valid old
over-quota histories are retained. J01-J04/J07/J27-J29 and REF/SOAK remain open campaign work, not evidence that
the off-state safeguards are absent. The signature below accepts only disclosed conservative holds, not new
admission, passenger safety or a latency guarantee.

The W466 mappings include
[EscrowLedgerNotLoadedGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow/EscrowLedgerNotLoadedGameTests.java)
methods `notLoadedRocketIsNotIdleAndKillLeavesIt`, `notLoadedLogicalRocketIsLocked` and
`notLoadedItemLoaderIsHeld`. These hosted checks are separate from the ten native families and their exclusions.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 关闭六项新准入时，已有收据、账本与存在性未知触发的保护性锁定；不接受新部署、装卸或加注业务。
> 2. **会发生什么。** 索引可用时持有指定火箭；收据或账本 NOT_LOADED 时可持有全服务器每枚火箭，拒绝发射、拆解、资源变更、登乘、命令击杀及传送，装置拒绝普通移除。原版下马不被取消，不能把此保护理解为乘客防坠落。
> 3. **范围与持续时间。** NOT_LOADED 持续本次会话；REPAIR 或已释放收据、逻辑锁及共享 64/全服、8/火箭、16/所有者名额可跨重启永久保留，着陆不释放。四个所有者仍能占满全服，旧超额世界不驱逐记录，没有服务期限。原因变化日志有界，累计输出无上限。
> 4. **恢复。** 存储访问修复后成功载入或确证缺失的重启可清除 NOT_LOADED；仍存在的权威冲突及不可在线退役收据只按完整世界离线副本流程修复，核对两次保存重启，不能删文件或伪造释放标记。
> 5. **运营要求。** 发布 KI-V180-HOLD-01，保存启动 WARN、inspect 输出、备份及身份。玩家看到的 TRANSFER_JOURNAL_UNPROVED/BLOCKED 不一定点名失败存储，须查启动日志；同一服务器不得以管理员身份绕过名额。
> 6. **失败保留。** 未执行的下马、另一所有者卫星舱和真实客户端场景仍未覆盖，原始失败不改为通过。
> 7. **不包括。** 不保证乘员安全、有限等待、未观察副本或跨存储断电耐久，不开启开关，不批准 Gate、版本或发布，不改变 R682 的延期范围。
> 8. **复审。** 最迟 v1.9.0 发布前复审；持有触发、保护性读取、名额、退役、恢复或准入变化，或普通无历史世界出现可重复的非存储原因锁死时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers only off-state protective holds from retained receipts/ledgers or unknown
presence. A loaded index holds named rockets; NOT_LOADED can hold every rocket and rooted device on the server,
blocking launch/disassembly/resources/boarding/kill/teleport/removal while vanilla dismount remains possible.
Session load holds end at a healthy restart; REPAIR/released receipts and shared 64/server, 8/rocket, 16/owner
slots can persist indefinitely after landing and across restarts, without fair-service guarantees. Repair access
and load/prove absence on restart, otherwise repair a complete world copy and verify twice; never delete stores
or forge release flags. Publish KI-V180-HOLD-01 and use startup logs to interpret generic journal refusals; no
operator quota bypass exists and lifetime logs are unbounded. All failed/uncovered dismount, other-owner and
real-client cases stay unchanged. Passenger safety, bounded latency, unseen copies, power-loss durability, new
admission, switches, Gates and release are excluded. Review before v1.9.0; changed holds/reads/quotas/recovery/
admission or new repeatable non-storage lockup in a no-history ordinary world voids it.

**Preconditions.** Publish KI-V180-HOLD-01 and the section 14 operator text, including the reach beyond test worlds
and generic-refusal limitation. Client/REF work remains required for its own Gates; it must not be advertised as
covered by this limited policy signature.

**Recommendation.** `NEEDS_KNOWN_ISSUE` with proposed KI-V180-HOLD-01 in section 14.

## 11. R-043: retained bay recovery, bounded search but unbounded waiting

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **108**: retained deployment recovery
can wait indefinitely, miss never-observed external copies, retain locks after flight and expose runtime hold
reasons only in production logs; player-exit protection is not permanent seat release or atomic player saving.

**Shipping reach and code.** Existing-receipt recovery in
[BayTransferRecovery](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayTransferRecovery.java),
[BayReleasedFlight](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayReleasedFlight.java)
and [BayReleasedResume](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayReleasedResume.java)
runs on ordinary servers if such receipts exist. [BaySourceProof](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BaySourceProof.java),
[BayCensusColumns](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayCensusColumns.java)
and [BayProofTurns](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/bay/deployment/BayProofTurns.java)
bound proof work, not its total waiting time. New D0, deliberate fault controls and active exit fixtures need the
test/campaign gate. A normal no-receipt world cannot naturally create a claim with admission off. Imported history,
storage faults and raw entity movement can reach recovery/hold code without reopening admission.

**Evidence and open work.** [W170](W170.md), [W185](W185.md), [W196](W196.md), [W254](W254.md)/[W262](W262.md)
provide historical design, conflict/released-flight and exit cuts. C6's forced-A/CD, forced-W358/W363, `released`,
exit-family and ten `w336-timing-*` results are named in [C6 sections 3 and 9.2](C6-SWITCH-REPORT.md#92-r-043-waiting-times);
[W466](W466.md) supplies the mapped hosted proof/flight rows, not V2. B11.2 and B17.5 remain reasoned-unreachable
NOT_COVERED. W336 and C6's ten timing profiles use a development host, at most three slots and two queued claims;
1/36-minute estimates are not measured upper bounds. K12 supplies eight five-column claims and a wait-23 isolated
world, but 34-slot/64-claim and other REF timing qualifications remain uncovered. J05 must report all 23 waits;
J09 must measure shared cargo-work/capture competition (rows 6/14), not infer fairness. J02/J03/J04 need K11's
marked two-client/host-swapped-LAN route and retain its cap/connected-controller gaps. R298's marked gate exists;
the older statement that the test gate necessarily rejects all real clients is no longer the whole rule.
Physical exit still leaves the player assigned in T; W254/W262 restarts do not prove permanent release or atomic
player persistence. Section 3's intermittent/history and X01/X02 exclusions apply.

[BayHeldProofGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayHeldProofGameTests.java)
names `outsideCopyDuringPartialCaptureHoldsOnlyItsClaim` and `escrowConflictBeforeRetirementHoldsOnlyItsClaim`.
This is a source inventory for the proof boundary, not an additional audited C6 result or real-client evidence.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 已有卫星舱收据的恢复、证明、受限续行与保留锁，不接受关闭的新部署准入或未验证的乘员安全。
> 2. **会发生什么。** 一致且可释放的收据组可确切确认后继续原飞行，但不退役收据或解锁；其他冲突扣留。普查外、其他 Level 中从未被观察的旧副本，以及逐文件回滚、断电可破坏恰好一次结果；本文不把这些情况声明为零损失。
> 3. **范围与持续时间。** 至多 256 认领、每枚 8 成员、每 tick 32 排队步骤、64 普查身份和每端 16 列，并不限制等待期限。D3-D7、共享货物预算、证明轮次与持续位置写入可无限等待；着陆后锁与名额可永久存在。票据续订依阶段而定，停止续订后沿用 400 tick 到期，不能从认领存在推断所有票据仍续订。日志累计无上限。
> 4. **恢复。** 可重试存储故障按现有重试处理；一致的可释放组只继续已有飞行。物理或权威冲突、未知持久原因和已释放但没有 T 的情况须完整世界离线副本修复；不得单独回滚收据、注册表或日志，也不得靠改释放标记解除锁。
> 5. **运营要求。** 公布实际等待和只在日志可见的持有原因，不把约一分钟或三十六分钟当上限。原版下马不取消；物理离开不永久退出指定座位，重启丢失内存保护，后续仍可能协调乘客。保留完整备份、两客户端证据与实际票据观测。
> 6. **失败保留。** X01/X02、原生及偶发失败保留，未覆盖的副本、乘员、LAN、队列极值和竞争不改为通过，开发计时不代替 REF。
> 7. **不包括。** 不接受新准入、外部未观察副本与断电损失，不保证完成期限或玩家数据原子耐久，不开启开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；恢复路由、释放、证明、普查、预算、公平性、票据、乘员或准入规则变化，或普通受支持恢复出现复制或丢失时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers retained-receipt recovery/proofs/limited continuation and retained locks, not
new admission or unverified passenger safety. An exactly acknowledged coherent released group may continue its
existing flight without retiring receipts/unlocking; conflicts hold. Unseen external copies, per-file rollback
and power loss are not zero-loss cases. Limits of 256 claims, 8 members/rocket, 32 queued steps/tick, 64 census
identities and 16 columns/end bound work, not duration; writer/work-budget/proof/movement contention can wait
forever. Tickets renew by phase and expire under the existing 400-tick rule after renewal ends; claims do not
prove renewal. Retry storage or repair a whole consistent copy, never one authority file or release flag. Publish
log-only reasons and real waits, not estimated maximums; physical exit does not permanently remove the assigned
seat, and restart loses RAM safeguards. Every failed/uncovered case remains. New admission, unseen-copy/power-loss
safety, latency and atomic player durability, switches, Gates and release are excluded. Review before v1.9.0;
changed recovery/proofs/budgets/tickets/passengers/admission or new supported recovery loss invalidates the text.

**Preconditions.** J01-J05 and J09 must produce real candidate observations, including two clients, LAN host swap,
all 23 REF waits, more than three slots/two claims, capture/work contention, and a disposition of the unprepared
34-slot/64-claim extreme. A09/A08/I10 must retain whole-tick execution cost and unfinished waits separately.
Publish the retained-recovery/log-only/seat caveats of section 14. Record the K11 client/control gaps and
unreachable rows explicitly; no signature converts them to coverage.

**Recommendation.** `SIGN_AFTER` with those named rows and explicit coverage dispositions, excluding later admission.

## 12. R-044: one unsupported fuel-loader root

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **109**, excluding its separately
decided downgrade paragraph: unsupported fuel-loader data disables/protects the device, and observed native
incompatibility denies its entire terrain chunk even after replacement or unload.

**Shipping reach and code.** The loader is registered/craftable; its
[FluidFuelLoaderBlockEntity](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/fuel/loader/FluidFuelLoaderBlockEntity.java)
retains raw data and records observed incompatibility on load, while
[FluidFuelLoaderProtection](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/fuel/loader/FluidFuelLoaderProtection.java)
checks serialized unloaded roots. Neither guard requires filling admission, receipts or a ledger. Future/invalid/
unknown retained roots and raw operator/third-party replacement are reachable inputs on an ordinary world;
normal valid roots do not trigger this. Oversized runtime data alone is not native incompatibility: losslessly
keepable roots and roots beyond the bounded-check work budget are retained without refusal.

**Evidence and open work.** [ADR-065's W176 note](../../../decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md#w176-implementation-note-the-fluid-fuel-loaders-native-save-protection-merged-7c9e5f7e9)
and [W176](W176.md) establish the policy. C6 `cargo-G2-REUSE` remains a failed family because of its downgrade
rows, not an all-green native result; its non-downgrade loader-root observations must be read separately.
C6 `w336-saturation-fuel` supplies the candidate fuel-root control, mixed-family protection and repeated
unsupported-root retention ([C6 section 9.3](C6-SWITCH-REPORT.md#93-r-044-which-risks-belong-to-one-device-and-which-are-shared)).
[FluidFuelLoaderProtectionGameTests](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/FluidFuelLoaderProtectionGameTests.java)
is the corresponding protection test class, not a V1 claim. J23-J29/K04/K11 fuel UI/ownership/competition remain
unexecuted; the proposed signature is only for retaining/refusing unsupported input, not a managed fill or pool
saturation acceptance. No generic offline repair tool is established by these fixtures.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 液体燃料装载器的不受支持根保护与单区块原生保存拒绝；共享池扩大影响另行签核，降级按原有独立文本处理。
> 2. **会发生什么。** 未来、未知或畸形根停用装置并拒绝普通破坏、爆炸与提取；原始标签保留。只有观察到原生不能无损保存才拒绝整地形区块，能保留或超出有界检查工作预算的根不因本条拒存。命令或第三方原始替换仍是可检测残余，不保证阻止。
> 3. **范围与持续时间。** 设备停用至支持该数据的版本或离线修复；拒存至 Level 关闭，卸载或替换不清除。实体、玩家和全局数据仍单独保存，可与该地形分歧；载入 WARN 有界，反复载入与保存错误累计无上限。
> 4. **恢复。** 停服保留完整原世界与哈希，在完整副本中用可信工具修复，核对身份和资源、两次保存重启及实际 writer 成功后整体替换。仅重启、删除根或只恢复一个文件不是修复，不授权通用退款或重建。
> 5. **运营要求。** 保留根、坐标、版本与日志，维护一致完整世界备份，发布原生拒存与装置停用说明；合成或放置装置不表示可开启托管加注。
> 6. **失败保留。** W176 和 C6 的降级失败及其他失败保留，不将 cargo-G2-REUSE 改称全族通过，不放宽断言。
> 7. **不包括。** 不接受共享池饱和、托管加注、原始替换的零损失或断电保证，不开启任何生产开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；原生检查、保留根、装置保护、保存或准入策略变化，或出现有效普通数据被丢弃时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers only C6 unsupported fuel-loader roots and their single-chunk native-save
refusal, not shared-pool saturation or the separate downgrade. Unsupported roots disable/protect the device
and stay raw; only observed native incompatibility denies terrain saving, not merely size or a check-budget
overflow. Raw replacement remains a detectable residual. Device protection lasts until compatible support or
offline repair; denial lasts until Level close despite unload/replacement, while separate stores can diverge
and total retry/load logs are unbounded. Keep a hashed whole-world original, repair a whole copy, verify resources,
identities and two successful saves/restarts, then replace the world; no single-file deletion/refund is authorized.
Publish the limits and retain backups. All downgrade/other failures stay failed, including the failed G2 family.
Pool saturation, managed filling, raw-replacement/power-loss guarantees, switches, Gates and release are excluded.
Review before v1.9.0; changed preservation/check/save/admission behavior or loss of valid ordinary data voids it.

**Preconditions.** none. Root should publish section 14's loader wording before P11. This decision does not close
the separate shared-pool or original-machine recovery scopes.

**Recommendation.** `SIGN_NOW` for the unsupported-root refusal policy only.

## 13. R-044: shared denial pool across protected device families

**Register line and risk.** [Risk register](../../../11-RISK-REGISTER.md), line **109**, with references from
R-021/R-035/R-038: the denial pool is one per ServerLevel, not one per device family, and its 257th distinct
chunk coordinate refuses all later terrain saves in that Level until it closes.

**Shipping reach and code.**
[ChunkSaveDenials.deny/reason](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/ChunkSaveDenials.java)
uses `MAX_CHUNKS = 256`, keyed by `ChunkPos.toLong`, not a BlockEntity count. One denied chunk containing several
devices still occupies one coordinate. [GuardedChunkSaves](../../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/persistence/GuardedChunkSaves.java)
attaches this state per server Level and refuses future terrain serialization, independent of all admissions.
The approximately 15 protected families listed in [C6 section 9.3](C6-SWITCH-REPORT.md#93-r-044-which-risks-belong-to-one-device-and-which-are-shared)
share it. An ordinary world with unsupported/non-admissible roots can saturate it without any managed ledger or
new operation. GameTest-only reset is not a production recovery command.

**Evidence and open work.** [C6 section 9.3](C6-SWITCH-REPORT.md#93-r-044-which-risks-belong-to-one-device-and-which-are-shared)
records the W327 audit's M5 finding; [W336](W336.md) distinguishes shared capacity from local protection.
C6's `w336-saturation-bay/item-load/item-unload/fluid-load/fluid-unload/fuel` families pass their named cases:
255/256 allow an unrelated terrain write; the 257th refuses it, 257 roots stay byte-identical, and reloading
unchanged roots re-saturates after restart. Five controls use fuel denials and the fuel control uses bay denials;
the negative-control JAR is rejected. This establishes shared mechanics, not repaired conservation for all 15
families or all-world atomicity. R-021's native recovery gap and final I02/I05/A08/I10 remain separate. No
KNOWN-ISSUES entry currently explains the Level-wide pool, despite its ordinary-world reach.

**Proposed signing text.**

> 1. **范围。** 本文仅针对 C6 所有调用共同 GuardedChunkSaves 的设备族所共享的每 ServerLevel 拒存容量，不替代各设备根的独立风险签核。
> 2. **会发生什么。** 容量按不同区块坐标计算，共享 256 项，重复坐标不扩容；第 257 个使该 Level 后续全部地形保存拒绝，包括没有受保护设备的区块。不是只丢弃第 257 个设备，也不是全世界原子暂停。
> 3. **范围与持续时间。** 影响该 Level 至关闭，无生产在线清除；其他 Level 与实体、玩家、全局文件仍各自保存，可能发生跨存储分歧。重启清空内存，但未修复根重新载入会再次饱和。ERROR、堆栈和重试累计无上限。
> 4. **恢复。** 停止玩家操作并停服，保留完整原件及哈希，只修复完整世界副本；核对受影响与无关地形、实体、玩家及资源，确认两次实际保存重启不再拒存，再整体替换。不得在线重置池、删除根或单独回滚 Level。
> 5. **运营要求。** 发布 KI-V180-GUARD-01，记录所有拒存坐标和设备族，监控日志量及空间，备有一致完整世界备份；不得把 Level 关闭或第一个干净重启当作数据已修复。
> 6. **失败保留。** 各设备、原生恢复、性能与偶发失败原样保留；共享池测试通过不代表每个设备族已通过恢复验收。
> 7. **不包括。** 不接受其他设备独立数据风险、任意丢弃或跨存储零损失，不开启开关，不批准 Gate、版本或发布。
> 8. **复审。** 最迟 v1.9.0 发布前复审；参与族、容量键、饱和、保存事件、生命周期、日志或恢复规则改变，或出现支持范围内的新资源分歧证据时，本文失效。
>
> 负责人：所有者。签核：＿＿＿＿。签核日期：＿＿＿＿

**English rendering.** This covers the common per-ServerLevel denial pool, not each family's independent data
risk. It tracks 256 distinct chunk coordinates; duplicates do not consume more, and the 257th denies every
later terrain save in that Level, including unrelated chunks. The hold lasts until Level close with no
production reset; other Levels/stores save separately, logs/retries are unbounded, and unchanged roots can
re-saturate after restart. Stop play/server, preserve a hashed complete original, repair a whole-world copy,
compare related and unrelated stores/resources through two real saves/restarts, then replace it. Publish
KI-V180-GUARD-01, log coordinates/families and monitor disk space; restart alone is not repair. All family-specific
failures and gaps remain. Data discard, zero-loss cross-store guarantees, switches, Gates and release are
excluded. Review before v1.9.0; changed participants/keys/capacity/guard/lifecycle/log/recovery policy or new
supported-scope resource divergence invalidates the text.

**Preconditions.** Publish KI-V180-GUARD-01 and the section 14 common operator procedure with the actual shared
chunk-coordinate unit. Preserve R-021's separate unresolved native family-repair evidence.

**Recommendation.** `NEEDS_KNOWN_ISSUE` with proposed KI-V180-GUARD-01 below.

## 14. Publication proposals, not edits or decisions

The [operator guide's save section](../../../ENDGAME-OPERATOR-GUIDE.md#saves-backups-and-downgrades) already covers
asynchronous flush/drain, whole-world backups, migration leftovers and downgrade. Its X03 section is already
approved for a narrow shape. [KNOWN-ISSUES](../../../releases/v1.8.0/KNOWN-ISSUES.md) already has SAVE-01,
DISASSEMBLY-01, UPGRADE-01, DOWNGRADE-01 and ADMISSION-01; do not create duplicates or describe those as undecided.
The following missing explanations should be published by Root, without claiming that a draft signature exists.

### Proposed KI-V180-GUARD-01 — 受保护设备可能使整区块或整 Level 地形拒存

> 不受支持或损坏的受保护设备根，在违反对应保存界限或无法由原生格式原样保存时，可能使其整地形区块拒存；具体判定依设备族而异，并非所有未知根都拒存。所有设备族共用每 ServerLevel 的 256 个不同拒存区块坐标，第 257 个使该 Level 后续全部地形保存拒绝，包括无关区块。保护持续至 Level 关闭，没有生产在线重置；重启不修复数据，原根重载后会再次触发。实体、玩家、POI 和全局数据并非同步暂停，可能与未保存地形分歧。反复保存的错误与堆栈没有累计上限。
>
> 出现拒存后停止玩家操作并停服，保留完整世界原件、日志、坐标与哈希，只在完整副本上离线修复，核对资源及无关存储，经两次实际保存与重启后整体替换。不要删除根、只恢复单个文件或把干净重启当修复成功。燃料装载器、卫星舱和货物装置即使生产准入关闭仍有此保护；它们可以合成、放置，不表示新增业务已开放。

Operator addition: document the affected root/family and first denied chunk, stop players promptly, retain normal
logs and available-space observations, and verify writer success plus neighboring container/player conservation
on the repaired copy. The GameTest cleanup API must never appear as an operator reset. No generic repair tool is
promised; an unprovable repair stays offline. R-021's family-native evidence remains required.

### Proposed KI-V180-HOLD-01 — 旧收据或存储不可用会锁定普通火箭

> 六项新增准入关闭，不会关闭已有收据、账本与转移的恢复。收据或账本无法载入时，服务器可能锁定全部火箭与持根装置，即使火箭没有卫星舱；存在性在开服前就无法确认时，世界会拒绝启动。预检后的访问故障可使锁定保持整个会话。已释放或 REPAIR 收据在着陆后仍保留逻辑锁与共享名额，可跨重启持续，不能在线自动退役。64 个全服名额、每火箭 8 个与每所有者 16 个限制不保证等待期限。
>
> 菜单的 TRANSFER_JOURNAL_UNPROVED/BLOCKED 可能没有点名失败存储；查看启动时 ARCE_BAY_RECEIPTS_NOT_LOADED 或 ARCE_ESCROW_LEDGER_NOT_LOADED 的原因。先修复存储访问并重启；真实权威冲突按完整世界离线副本流程处理，不要删除收据、账本或日志来解除锁。卫星舱恢复持有原因在普通生产服上可能只见于日志。原版下马不受取消，物理离开也不永久取消指定座位，不能当作安全防坠或永久退出功能。

Operator addition: distinguish `LOAD_FAILED`, `PRESENCE_UNKNOWN`, invalid-root startup refusal, ordinary journal
pending and retained receipt locks. Preserve startup diagnostics and exact identities. An offline repair must
reconcile all linked stores; a merely accessible restart cannot repair persistent conflicts. Document the
log-only bay reason, unbounded waits, phase-dependent tickets and no online retirement of released/REPAIR rows.

### Registry refusal and epoch-exhaustion operator addition

Proposed text: "A canonical satellite-root refusal can prevent the entire world from starting. Keep the named
record and every linked authority file; repair the complete offline copy so canonical encoding actually fits.
Adding namespaces alone does not shrink an oversized representation. A terminal can remain pending through an
unloaded chunk or failed write; load it after storage repair or use the documented idempotent restart path.
An exhausted save epoch has no supported online reset; stop and request a separately reviewed migration or
operator decision. Do not set it to zero or delete the registry. Keep original files, backup hashes and refusal
logs; process exit code 0 is not evidence that startup succeeded."

This extends the existing backup guidance; it does not invent a repair command or a new public KI identifier.

## 15. Findings for Root; no central document was changed

1. **R-035, line 100: production-history reach is stated too absolutely.** Its opening mitigation says production
   worlds have no ledger, and its NO_FILE paragraph calls them all production worlds with admission off. C6's
   unconditional existing-file lookup/migration, the later paragraphs of the same row, R-043's all-server recovery
   and C6 section 9.1 admit an imported/retained-history production world. Correct the distinction to "no new
   ledger is created by ordinary off-state admission" rather than "a production server cannot have one."
2. **R-035, line 100: pre-implementation cargo wording is stale.** The paragraph beginning "货运范围" still says
   "尚未实现". C6 carries the four devices, recipes, protection and recovery with admission off, and ADR-062
   revision 8 explicitly defers admission rather than code/registration. Keep it as labelled history or update
   the current description; do not use it to infer no shipping protective behavior.
3. **R-035, line 100: W80 delivery placeholder remains.** It says implementation will later supply commits/evidence,
   although earlier in the same paragraph the W157-W160 integration commits are already given. This is record
   inconsistency, not evidence of unimplemented rejoin. Root should reconcile the delivery sentence without
   closing player/rejoin Gate gaps.
4. **R-039, line 104: D3's historical sentence needs an explicit historical label.** "目前没有生产代码调用…D3…尚未实现"
   is followed by the implemented W27d2 behavior. C6 executes D3 for retained recovery, while new admission stays
   gated. The two eras should not be read as simultaneous C6 behavior. This is a misleading temporal statement,
   not a new functional defect.
5. **R-043, line 108: the blanket real-client test-gate statement is superseded.** The statement that V2 still needs
   an activity route because the release gate rejects real clients predates R298/W269. C6's
   `EscrowTestEndpoints.enabled` accepts the authenticated marked campaign route. The route exists, while K11
   gaps and actual J02-J04/V2 runs remain open; do not change "not run" to PASS.
6. **R-043, line 108: 64/8 in the summary is incomplete alone.** Later text correctly adds 16 per validated owner
   and preserves valid over-quota histories. Carry all three numbers in a current summary, rather than suggesting
   either unbounded new single-owner admission or guaranteed global service.
7. **No newly established contradiction for R-021, R-030, R-040, R-041 or R-044 is claimed.** Their old measurements
   are labelled history and do not substitute for REF. R-044's 12/11 versus W336's 13/12 removed registry entries
   are JAR-dependent observations, not one universal count; this distinction is already in the downgrade draft.
8. **Related report/checklist prose also needs reconciliation.** W466 supersedes only the 89 mapped A1 coverage
   gaps; old missing-shard prose and section 12.2's PARTIAL cell remain. R681/R682/R679 supersede older report
   statements about pending X03 approval, production switch choices and downgrade signing. These stale sentences
   are not reasons to reopen the decisions or erase failures. The present draft cites the later scoped records.

Root's permitted next work is to review these ten scopes, publish missing disclosures, obtain the named v1.8
campaign evidence or valid waivers, and then decide individual signatures before P11. No version/Gate/release
conclusion follows from this document.
