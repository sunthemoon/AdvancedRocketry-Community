# W21CF: W21c CI crash, review follow-ups and re-verification

Task: `TASK-W21CF-FIXES.md`. Worktree `D:/GitHub/arce-v180-w21c-observatory`, branch `codex/v1.8.0-w21c-observatory`, base
`8814951b` (the task commit on top of `368cae1b`, W21c with the shared branch, W21b and W21bF). Implementer: Claude (Opus 5.5),
a headless session. Review: `root-queue/W21c-review/REVIEW.md`, SHA-256 `8917c9fa…3f5126` (checked). Commits are local only;
nothing was pushed or merged, and no other agent was started.

## What was done

Each item is its own commit, with its tests.

| Commit | Item | Change |
|---|---|---|
| `6c8abaf5` | CI crash | The research GameTests register one `onEachTick` at test start and move between steps through a mutable step index (`ResearchGameTests.steps`). No step schedules. The environment test's nested `onEachTick` (the crash in hosted run 38353212060) is gone. Every assertion stays. |
| `c288b4d8` | L6 | `GroundSurveys.version()` counts job-record changes. The directory rebuilds its retained view only when that section object or its count changes, and indexes the devices that hold ACTIVE or RUNNING work. The ticker path does local checks and one index lookup; only a device with work enters the budgeted queue. An idle controller's status is computed in the menu path. Only the observatory and processor types get a ticker. |
| `9b2d0662` | L1 | R4 split: a quarantine reads OFFLINE_REPAIR_REQUIRED only when `recoveryHint` names an offline-only case, otherwise QUARANTINED ("an operator can release it"). Registry codes map through an exhaustive switch; the dead `valueOf`/catch branch is removed. INVARIANT_FAILED reads as offline repair; IDEMPOTENT as the job's barrier state; JOB_NOT_FOUND, CANCELLED, IDENTITY_CONFLICT and SOURCE_MISMATCH get appended statuses. An intent's answer is held for 40 ticks so the idle ticker does not replace it before the menu shows it. `ground-survey inspect` names the online release for a releasable quarantine. |
| `d8c5f122` | L2 | SOURCE_MISSING is produced: an idle controller's menu shows it when its displayed ACTIVE job's source, or RUNNING job's processor, is another device that is not loaded and canonical. New GameTest for the design's "break". |
| `737050ba` | L5 | GameTests for the production path: 40 natural ticks pay exactly once per tick through the ticker and the END-tick scheduler; and a real ticket change takes a Moon processor's footprint chunk out of FULL while the queued controller stays loaded and formed. |
| `09552ad7` | I2 | A post-publication debit shortfall no longer throws. The advance stays published, one ERROR is logged, and the controller refuses paid research (OFFLINE_REPAIR_REQUIRED) until it reloads. A GameTest forces the case once; its ERROR has an exact expectation and the pinned ERROR total goes from 80 to 81. |
| `abfc6eb5` | L4 | The native harness records git HEAD, refuses an unclean tree, accepts `--tested-commit` only when it names HEAD, and with `--build-candidate` builds the jar and records its SHA-256 when the build ends (reusing W21bF's `fb9e470a` functions). The heavy wrapper always builds the native candidate. |
| `84e50d1a` | L2, L5 fix | The first development GameTest run failed both new ticker tests (see below). The break test now lets natural ticks run before reading statuses; the Moon test forces one remote chunk so the Moon ticks block entities. The step runner gains a cleanup for failed steps. |
| `7da6df48` | L4 fix | The native run on `84e50d1a` failed in cycle 2 because the opt-in hook's snapshot threw while the restarted server's forced chunks were still loading (a W21c race; its earlier 8-cycle pass depended on timing). The snapshot now reports `ready=false` for a fixture that is still loading. |

The final tested source is `7da6df48`. This record is the commit after it and changes no code.

## Files changed, by area

- Research runtime: `ResearchService`, `ResearchDeviceDirectory`, `ResearchControllerBlockEntity`, `ResearchControllerMenu`,
  `ResearchStatus`, `ResearchCommands`.
- Classic kernel: `ClassicControllerBlock` (ticker only for research types) and `ClassicControllerBlockEntity` (I2; 468 lines).
- Registry: `GroundSurveys.version()` and its two increments; no rule, schema or mutation changes.
- DataGen: `V180ResearchLanguage` and the generated `en_us.json` and `zh_cn.json` (four appended statuses, two changed texts).
- GameTests: `ResearchGameTests`, `ResearchEnvironmentGameTests`, `ResearchClaimGameTests`, `ResearchFixture` (ticket helpers).
  Four new tests: `productionTickerAndSchedulerPayEachNaturalTick`, `breakingAnActiveSourceKeepsThePausedJobWithoutRefundOrDuplicate`,
  `realFootprintChunkUnloadPausesWithoutPaymentOrLoading` and `postPublicationShortfallStopsPaidResearchWithoutThrowing`.
  There are now 17 research GameTests.
- Native verification hook: `ResearchReleaseCommands` (snapshot readiness only).
- Unit tests: new `ResearchDirectoryRefreshTest` and `ResearchStatusMappingTest`; one new test in `ResearchIdentityTest`.
- Scripts and Python tests: `run_w21c_research_restart.py`, `run_w21c_heavy.ps1`, `gametest_expected_log.json`,
  `tests/test_w21c_research_restart.py`, `tests/test_check_gametest_log.py`.

## Choices where the review or design left room

- **L1 statuses.** I appended four statuses rather than reuse misleading ones. The wire is unchanged: slot 0 carries the
  ordinal, and `fromCode` already bounds it. INVARIANT_FAILED maps to OFFLINE_REPAIR_REQUIRED because the load plan
  quarantines the touched records at the next load, and only then can release apply.
- **L1 answer hold.** Before this change, the idle ticker replaced an intent's answer in the same tick, so a refusal was
  never visible. The answer is now held for 40 ticks (two seconds). Work and pause statuses still replace it at once.
- **L2.** An unloaded device and a broken one look the same from another controller, and rebinding is the owner's remedy
  for both. The text therefore reads "Job's device is not loaded or was removed; the owner can rebind here". Nothing
  pauses differently: a job without its device never paid.
- **L3.** I kept the code and corrected the text (below): a collision copy and its claimed plugs refuse ordinary removal,
  and an identity-less controller gets a synthesized root.
- **L6.** A blocked registry still yields an empty retained view, as before; it is now built once per state change.
- **I2.** The flag is transient and per block entity, so reloading the device clears it. The ERROR is logged once,
  because the flag refuses every later paid advance on that device.

## Not done

- ADR-066, the risk register, status files and the ledger (Root, at merge). The proposed texts are below.
- The advancements.
- The W21c review's optional M1 follow-up (a status that pauses paid advances while the reservations cannot bound the
  root) was not in this task.
- V1 (real GPU) and V2 (two real clients) checks of the menu texts and the 40-tick answer hold.

## Commands and results

Gradle ran with `--offline` from this worktree. Heavy runs took the machine-wide lock through `scripts/run_w21c_heavy.ps1`;
another worker (W26) held it before each of my runs, and I waited rather than touch it. C: had at least 22 GiB and D: at
least 390 GiB free before each heavy run.

| Command | Result |
|---|---|
| `compileJava` after each change | Passed. One ambiguous overload in `ResearchService` was fixed before its commit. |
| `test --tests` for the research unit classes during development | Passed. Each new class also ran alone once: `ResearchDirectoryRefreshTest` 1 of 1, `ResearchStatusMappingTest` 2 of 2. |
| `python -B -m unittest discover -s tests -p "test_*gametest_log*.py"` | 59 passed (after the pinned total became 81). |
| `python -B -m unittest discover -s tests -p "test_w21c_research_restart.py"` and `python -B -m unittest tests.test_run_w21b_registry_upgrade` | 5 and 5 passed. |
| Development `runGameTestServer` on `09552ad7`'s Java sources (it compiled at `abfc6eb5`, which changed only Python and PowerShell; evidence `W21cF/dev-gametest-01`) | 875 tests, 2 required failures, both new: the break test read the replacement's initial UNFORMED status before its ticker had ever run, and the Moon processor never paid ("natural ticks 20 paid 0"). Cause of the second: `ServerLevel.tick` skips block entities after 300 ticks in a Level with no players and no Forge-forced chunks, and the Moon has neither during a GameTest run. The natural-tick, debit and restructured environment tests passed. Fixed in `84e50d1a`. |
| Full verification on `84e50d1a` (evidence `W21cF/verification-84e50d1a`) | Unit suite 2,803 of 2,803; runData clean; GameTest 875 of 875 and the log checker passed. The native run failed in cycle 2 (the hook race above). Fixed in `7da6df48`. |
| `git diff --check 8814951b HEAD` | Passed. |

### Re-verification on the final source (review merge condition 1)

All on `7da6df48ef771005b665db1e4867b86bd0e629d4`, with a clean tree. Evidence:
`D:/GitHub/ARCE-Task-Evidence/root-queue/W21cF/verification-7da6df48` (11 MB: logs, results and manifests, no source copy).

| Command | Result |
|---|---|
| `gradlew.bat --offline test --console=plain` (whole unit suite) | Passed: 2,803 tests in 495 suites, 0 failures, errors or skips (`unit-summary.json`, `unit-results/`). The four W21c classes: `ResearchIdentityTest` 5, `ResearchMenuWireTest` 2, `ResearchWorkQueueTest` 2 and `V180ResearchDataTest` 2, all passed. The two new classes passed: 1 and 2. |
| `runData`, then `git diff --exit-code` and `git status --porcelain` | Passed with no change and nothing untracked (`datagen-diff-check.log`). The hand-written generated language lines therefore equal the generator's output. |
| `runGameTestServer` | All 875 required tests passed, including all 17 research batches (the original 13 and the 4 new ones); no "failed!" line (`GameTest-console.log`, `gametest-latest.log`). |
| `python -B scripts/check_gametest_log.py` on that log | Passed: FATAL 0, ERROR 81, WARN 114, all within the 78 expectations (`gametest-checker.log`). The forced debit shortfall logged exactly once. |
| `run_w21c_heavy.ps1 -Target Native` (the harness with `--build-candidate`) | Passed all 8 restart and fault cycles (`native/summary.json`). It recorded HEAD `7da6df48` and a clean tree, built the jar with the Gradle `jar` task, and recorded SHA-256 `f4fd1562f8cb67efad49ed38b84f05ade13f2ccada69a05d75363908e0683947` at build and again before PASS. No ERROR or FATAL line in any cycle; the temporary server was removed. |

The native checkpoints equal W21c's: mid-survey restart 300/0 with 4,000 FE on each device; acknowledged pre-completion
save 999; completion cut READY 1,000, restored to ACTIVE 999 with FE 20; mid-analysis restart 50; rebind kept 50; credit cut
PROCESSED 200 with balance 120, restored to READY 199 with balance 0; final PROCESSED 200 and balance 120, unchanged on
replay and restart. The failed START flush, CLAIM and REBIND barriers each stayed WAITING_SAVE without payment.

## Known risks

- The 40-tick answer hold and the new texts have not been seen on a real client (V1).
- The GameTests now prove the ticker path, but GameTest Levels other than the overworld tick block entities only while
  something forces a chunk. The Moon test forces one; any future research GameTest outside the overworld needs the same.
- Other GameTests still schedule from inside scheduled callbacks, for example `ForceFieldUnloadGameTests.poll`, which
  calls `runAfterDelay` from a `runAfterDelay` callback. That can hit the same fastutil crash by chance. I did not change
  them; they are outside this task.
- W21bF's remaining cost path still applies: on a restored root whose reservations exceed 16 MiB, each paid advance
  encodes the whole root, and W21c's budget counts 32 advances per tick, not time. Only a legacy root above its section
  budgets can be in this state (review M1, optional follow-up).
- Plug FE and registry progress are saved separately; the two crash directions are spelled out in the proposed ADR note.
- Collision copies, their claimed plugs and identity-less controllers refuse ordinary removal until an operator acts, as
  the proposed texts now state.

## Proposed text for Root (section 4 of the task)

These replace the three proposals in `records/W21C.md`. They include the corrections that the W21c review asks for in L3, I1 and I4, and they describe the W21cF behaviour. Root adds them at merge; this task does not edit the ADR or the risk register.

### ADR-066 implementation note (for section 6.1)

> ### W21c implementation note on research device identity, roots and power (2026-10-13)
>
> The observatory and the astrobody data processor are classic controllers with research profiles. Each holds only a
> schema-1 identity root, `arce_observatory` or `arce_astrobody_data_processor`: the owner and device UUIDs, the
> immutable home Level and position, and a sticky collision flag. Every job, progress, completion and credit lives in
> the satellite registry (ADR-051). The identity authority is the immutable home, corroborated by every retained
> registry source and processor claim, including READY and PROCESSED jobs. A bounded server directory tracks loaded
> devices independently of the live-work indexes: at most 16,384 devices, refused rather than evicted at capacity. It
> lives in the overworld capability and is cleared at Level unload and server stop. It rebuilds its retained view only
> when the registry's ground section changes, and an idle controller costs one index lookup per tick.
>
> A device whose root disagrees with its actual Level and position, with a retained registry record, or with an
> already loaded canonical device receives the collision flag on its own root. The flag survives unload, reload and
> restart, and no online command clears it. A flagged device cannot start, claim, rebind, cancel or pay. Collision
> detection never rewrites the original's jobs, instances, progress or research account, and never completes,
> transfers, deletes or credits anything. Without a retained registry record, the device that loads second is the one
> flagged. An operator who edits a device root (item NBT or `/data`) can therefore make an edited copy load first and
> quarantine the idle original; with a retained record, the registry decides instead.
>
> A flagged copy refuses ordinary removal (player break, explosion and entity destruction), and so does every power
> plug it has claimed, because the kernel treats a flagged controller as an unavailable owner. Both stay until an
> operator removes them with a command or the world is repaired offline. A research controller holds no item or fluid
> bank, and a plug's FE stays in the plug either way, so this refusal protects no resource; it is the kernel's removal
> policy applied to an unavailable owner.
>
> An unsupported or malformed bounded root is retained unchanged and the device stays inert in offline repair. A
> controller that has a kernel root but no research root, for example one placed by `/setblock` (no placing player) and
> then saved and reloaded, is given a synthesized empty research root. It then stays OFFLINE_REPAIR_REQUIRED and refuses
> ordinary removal until an operator command or offline repair. An outgoing root above 4,096 bytes, depth 8 or 64
> nodes uses section 2's existing `GuardedChunkSaves` refusal; R-027 records that scope.
>
> Moving ACTIVE or RUNNING work is an owner-authorized registry rebind to a formed, idle, canonical device. Progress is
> kept, and an analysis rebind takes a new claim epoch, so nothing pays until that claim is flushed. Breaking a device
> keeps its job and refunds nothing; the job pauses, and the owner's other devices show SOURCE_MISSING for it until the
> owner rebinds it. Collision detection writes no per-tick log; menus show the refusal. R4's offline-only cases, a
> quarantined ACTIVE job with its PENDING instance and duplicate RUNNING claims, read OFFLINE_REPAIR_REQUIRED in menus
> and print their repair in `ground-survey inspect` and `release`. Any other quarantined job reads QUARANTINED, and
> `ground-survey inspect` names the online `ground-survey release` command for it.
>
> The existing power plug stays the only physical FE owner, and Forge extraction still returns 0. A paid advance checks
> every binding and the aggregate FE, publishes the registry advance, and then debits the plugs on the same server
> thread. If a debit ever fell short after publication, the advance stays published, one ERROR names the device, and
> that controller refuses paid research until it reloads; no exception leaves the server tick. The registry is flushed
> on its coalesced 100-tick schedule and plug FE is saved with its chunk, so after a crash either store can be the newer
> one. If the registry is older, progress or credit since its last flush is lost while the FE paid for it stays debited.
> If the plug's chunk is older, progress or credit is kept and the plug returns to its last saved FE, so the player gets
> back FE already paid, up to the time since that chunk was saved. Neither direction duplicates FE or makes it
> exportable. This is ADR-016's accepted independently persisted energy; no crash-atomic power guarantee is claimed.
>
> This is a development implementation note, not owner acceptance or a Gate result.

### R-027 scope addition

Proposed additions to the existing R-027 row (risk register columns in order):

- **风险:** append "；以及 W21c 天文台和天体数据处理器的设备身份根".
- **触发信号:** append "；`arce_observatory` 或 `arce_astrobody_data_processor` 根超出 4,096 字节、深度 8 或 64 节点".
- **缓解:** append "W21c 的两个设备根沿用同一 `GuardedChunkSaves` 整区块拒存、256 坐标饱和与重试日志行为，不新增拒存机制，也不在序列化中抛异常。有界但不受支持或畸形的根原样保留，设备停用并保持离线修复。有内核根而无研究根的控制器（例如 `/setblock` 放置后保存并重新加载）会写入合成的空研究根，并保持离线修复、拒绝普通破坏，需要操作员命令或离线修复。详见 ADR-066 的 W21c 实施说明".

In English: the two research device roots join R-027 at 4,096 bytes, depth 8 and 64 nodes each. Oversized roots reuse the same whole-chunk refusal, 256-coordinate saturation and uncapped retry logging; no new mechanism or serialization exception is added. A bounded unsupported root is retained unchanged and inert. An identity-less controller is given a synthesized empty root rather than retaining one, and then stays in offline repair and refuses ordinary removal.

### Physical-collision risk entry

Proposed new row; Root assigns the number.

> | R-0xx | 研究设备身份根被复制（操作员 `/clone`、第三方搬运或操作员编辑 NBT）时，冲突的天文台或处理器被隔离，并连同其已认领的电力插头一起拒绝普通移除 | 未量化 | 低 | 设备 UUID 相同但不在不可变的归属 Level 与位置；与保留的注册表来源或处理器认领不一致；与已加载的规范设备不一致 | 隔离只作用于被标记的方块实体根：碰撞标记持久保存，卸载、重载和重启后仍在，没有在线清除方式。被标记设备不能开始、认领、重新绑定、取消或付费；碰撞检测不改写原设备的任务、实例、进度和研究账户，不完成、转移、删除或记分任何记录，不阻断注册表，不按 tick 记日志，菜单显示拒绝原因。没有保留任务时，后加载的一台被标记，因此操作员编辑设备根可能使先加载的副本成为规范设备而隔离原设备；有保留任务时以注册表为准。被标记的副本及其已认领的电力插头拒绝普通破坏与爆炸（研究控制器不持有物品或流体，插头 FE 留在插头内，此拒绝不保护任何资源），直到操作员用命令移除或离线修复。`/setblock` 放置后重新加载的无身份控制器获得合成的空研究根，同样保持离线修复且不可普通破坏。正常转移工作使用所有者授权的重新绑定。恢复：停服备份，离线修复冲突的设备根后重启核对。ACTIVE 隔离对和重复 RUNNING 认领仍按 R-030 只能离线修复。详见 ADR-066 的 W21c 实施说明 | `v1.8.0` |

In English: copies are isolated at the flagged block entity root only; the flag persists; a flagged device cannot start, claim, rebind, cancel or pay; nothing in the registry is rewritten, completed, transferred, deleted or credited; there is no registry block and no per-tick log. Without a retained job the second device to load is flagged, so an operator edit can quarantine the original. A flagged copy and the plugs it claimed refuse ordinary removal, protecting no resource, until an operator command or offline repair. An identity-less controller reloaded after `/setblock` gets a synthesized empty root with the same result. Recovery is a backup and an offline repair of the conflicting device root.
