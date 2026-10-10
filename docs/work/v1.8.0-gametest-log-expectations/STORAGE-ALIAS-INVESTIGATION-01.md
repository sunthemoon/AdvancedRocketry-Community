# v1.8.0：托管区块保存异常的引用调查

日期：2026-10-11，Asia/Taipei。状态：`INVESTIGATED_CAUSE_UNRESOLVED`。
这是既有托管失败的只读调查记录，不是产品修复、日志豁免或发布验收。

## 被测源码与原始证据

托管 [run 38064322640](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38064322640)
的被测提交是 `52a29c1e76977033a1b7e85baf32ca5d19c7414c`。独立调查冻结在干净的
`3e93c2c3d5e8fd1242c83d99184ef4b3466abd36`；其产品、脚本、测试及工作流与该合入源码相同。
后续 Main `5c308c873f7d8f39205efb3641aea9e888c27018` 只更新记录。

所有者通过本次会话提供的原始包位于 `D:/GitHub/ARCE-Task-Evidence/ci-run-38064322640`。
README 和具名日志、原始 ZIP 的哈希已核对；原文件未改动。ZIP 的 SHA-256 是
`a8ed53f146acac4da4eb7da193c40f0b9d1222dffda063cdb4e4f3b644459db1`，与先前独立下载一致。
完整 job 日志 SHA-256 是 `e91b728537bf87f6ca81cc7992dae951dca4e31c10ac643c2588225e0e930998`；
服务端 latest.log 是 `238daf620bec219143d9f23ba2f90328026dd89695377fd088c1a56df8f68673`。

597 项必需原生测试通过，Gradle 原生步骤成功，随后对账退出 1。63 ERROR、161 WARN、
零 FATAL 中，原有 62 条错误全部匹配，新增的存档错误被正确拒绝。latest.log 2326–2342
显示 `IO-Worker-6` 保存 `[-13, 7]` 时，在 HashMap key iterator / CompoundTag.write /
NbtIo / RegionFileStorage / IOWorker 中抛出 `ConcurrentModificationException`。
批次 `endgame_gravity_station` 开始后 151 ms 的时间关系，不证明写入者、维度或区块存储类型。

## 调查结论与限制

1. **未找到此次异常的实际修改者。** 三份独立调查覆盖 BlockEntity/实体保存、Save/Load
   监听器及夹具、固定 Forge 47.4.10 的原生适配边界。没有把时间上的相邻批次当作来源。
2. **存在真实但尚未关联到事故的嵌套引用。** CombustionGenerator、SolarGenerator、Pump、
   PressurizedTank、FuelLoader、SatelliteBuilder、MicrowaveReceiver、SatelliteTerminal
   的超限隔离数据保存分支会发布保留的原始引用。RecipeSignatureMigration 还会通过
   Electrolyzer、PrecisionAssembler、RollingMachine 的旧状态保存分支发布保留引用。
   这些是既有数据保留策略；没有证明合法后续操作在异步写入期间修改了同一对象。
   不应直接复制任意超限数据、丢弃数据或改变保存拒绝规则来消除日志。
3. **所查项目回调没有展示外层区块 NBT 的延迟修改。** 已注册的 Save/Load 消费者同步读取，
   或只排队坐标、UUID、布尔和集合摘要；未找到将外层事件 CompoundTag 留在字段/队列后再修改
   的项目回调。文本扫描和具名调用图不排除外部能力提供者、动态注册或未核实的原生行为。
4. **固定 Forge 边界确实传递同一引用。** ChunkDataEvent 构造/getData 不复制；ChunkMap
   补丁将同一序列化结果传给 Save 事件，再交给原生 write。能力序列化的新外层也可直接包含
   提供者返回的子引用。完整 vanilla IOWorker、ChunkStorage、RegionFileStorage 和 NbtIo
   写入方法体未在获准的现有源码材料中取得；未反编译 JAR 或生成额外映射源码。因此，
   后续是否复制、原生 pending-read 所有权、具体失败对象和真实修改调用仍未证明。
5. **终端掉落的条件候选被已查调用者阻止。** `carriedDeliveryData` 会修改刚保存的子数据，
   但 `SatelliteTerminalBlock.getDrops` 只在非 blocked 状态调用它；blocked 数据选择
   `carriedData`，不能安全携带时拒绝。该分支不能据此认定为本次事故原因。

原生单层 CompoundTag.write 栈与外层遍历相符，但完整写入方法体尚未核实；不能把这一推断
升级为具体对象归因。CME 也不单独证明修改发生在第二个线程。

## 分开保留的发现

- `ClassicSaveProtection` 有处理方法，但没有自动订阅注解，扫描未找到显式注册；类注释说明
  它等待集成。不能把仅存在的方法当作已运行的保护器，也不在本次调查中擅自启用它。
- `ChunkSaveWatcher` 两处调用只在成功序列末尾注销：LaserTargetGameTests 493–506 和
  RailgunGameTests 95–155。提前失败会留下保存监听器、Level 引用和继续累积的 tick 历史。
  watcher 不持有 NBT，因此这是单独的夹具生命周期问题，不是本次 CME 的原因证明。
  Root 复查了 watcher 和两个调用段；尚未实施或运行故障清理修复测试。
- EndgameMenuGameTests 的直接 `chunkMap.read(...).join()` 夹具只读取数量，不修改或保留返回
  NBT；是否具备其断言文字所称的磁盘完成边界，仍须核实准确原生存储契约。

## 独立报告与实际检查

外部调查包：`D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-alias-investigation-20261011-01`。
其中 ROOT-OBSERVATIONS.md、reads.jsonl 及 reviews/ 保存完整引用图、文件/行号、输入哈希、
失败的探索命令、实际检查和限制，不执行任何证据包内的助手。

| 报告 | 只读截止 UTC | 报告 SHA-256 |
|---|---|---|
| BE/实体 | 2026-10-10T16:23:25.724Z | e35d75259055a1b5bc7920b27fc8b95e90b72b75549ccc92eff28a0a4bf6778e |
| 保存回调/夹具 | 2026-10-10T16:24:17.8214709Z | d3697281583d36cfe12a4e99b085422af36c3cf5a933e0d400d577135e542a85 |
| 固定原生适配边界 | 2026-10-10T16:28:02.529682Z | 0f38fc3620c86aef773c578ee06be334279e8ceaf6f2c18146ab994e0c4ba7fc |

三名审核者均确认冻结 HEAD 不变且工作区干净。其独立保守读取计账分别为 14,917,034、
57,525,349 和 18,796,330 字节，各自在事前 64 MiB 范围内；这是调查读取计账，不是发布
traced/native 内存资格。原生边界报告还核对了固定 Forge sources.jar 与官方不可变发布校验值。

本次没有启动 JVM、Gradle、服务器、数据生成或 CI 重跑；没有新增产品测试、改变断言、
超时或预期日志清单。原始失败、R-021、全部 Required Gate 和此前严格校验/完整 Python
资格问题保持开放。先前 Root 日志审核的累计读取超限也没有因这个新分配而重新合格。

## 后续验证

在 v1.8 内取得合法的准确原生交接契约，再用一个可丢弃区块及相关 BE 的最小夹具采集
存储类型、Level、区块、保存序号、对象/子对象身份、发布/写入边界和真实修改调用。
独立审核实际因果证据后再修改产品。观察器须在失败、批次结束和服务器停止时注销，
计数和日志有事前硬上限，不打印玩家完整 NBT。不加入此异常的白名单，不挑选重跑绿灯。
