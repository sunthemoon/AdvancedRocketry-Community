# 三台代表机器的存档与迁移报告

```yaml
task: V120-MIG-03
status: verified
scope: representative_world_report_not_full_recovery_matrix
reported_at: 2026-09-26
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
tested_implementation_commit: null
uncommitted_worktree: true
artifact_version: 1.20.1-1.1.1-dev
artifact_sha256: 6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a
```

## 范围与制品

本报告汇总 v1.2.0 三台代表机器的真实世界输入、资源、身份、schema 和短时
恢复结果。三组新版专服证据的 JAR SHA-256 均为上列值；基线提交不包含这些
未提交改动，不能把 `3dbe688` 描述为已测试的完整实现提交。本报告也不创建
候选制品、版本标签或发布批准。

| 机器 | 原始世界来源 | 本次新版验证 | 独立存档根 |
|---|---|---|---|
| Electrolyzer | 原 Beta 制品 `fbddf669…` 的完整 ZIP，含 v1.0/1.1 继续兼容的 schema 1；不是冒充 v1.0 构建的世界 | 真实旧世界副本加载、完成在途配方、正常保存重启 | `arce_machine` 1 保留，新增 `arce_process` 1 |
| Rolling Machine | 新候选 `6077fd10…` 创建的世界；历史区域已缺失，未重建历史哈希 | 保存后强停、原进度恢复、完成一次、正常重启 | lifecycle / binding / port / process 均为 1 |
| Precision Assembler | 原 `e339deda…` 制品、测试提交 `ea654f6…` 的四 chunk 世界 | `6077fd10…` 将 7 个物理 Item 根迁到控制器，保存后正常重启 | 新 controller resources 1、port migration 1；旧 port 根保留为 inert shadow |

详细来源和边界分别见 [Electrolyzer 输入](ELECTROLYZER-BETA-FIXTURE.md)、
[Electrolyzer 重放](ELECTROLYZER-BETA-REPLAY.md)、
[Rolling 世界](ROLLING-WORLD-FIXTURE.md)、[Precision 输入](WORLD-FIXTURE.md)与
[Precision ADR-019 实施验证](../v1.2.0-precision-assembler/PREC04B-VERIFICATION.md)。
旧版证据没有被改为新候选的证据。

## 资源、进度和身份

| 场景 | 迁移/恢复前 | 迁移/恢复后 | 重启身份检查 |
|---|---|---|---|
| 电解暂停机 | 2 空罐、1000 水、0 FE、进度 40 | 资源与进度完全保留；已支付 800 FE、revision 0 | 固定维度/坐标/BE ID、完整资源和 process 根相等 |
| 电解运行机 | 8 空罐、4000 水、19500 FE、进度 25 | 4 氢罐 + 4 氧罐、0 水/输入、12000 FE，revision 4 | 最后事务 `12101332-e03c-43f8-9c79-107ae3f1c560` 不变 |
| 轧制机 | 暂停 11/100、2 铁锭、500 水、3780 FE，revision 6 | 强停重启报告完全相等；继续后 8 铁栏杆、400 水、2000 FE，revision 7 | controller/4 ports 同 UUID `c070794b-4e9c-4bf8-9001-ba991274a598`；最终事务不变 |
| 精密装配机 | 5 空输入、1 高级电路 + 2 红石火把输出、800 FE，revision 5 | controller 7 slots 与旧物理端口逐项相等；7 markers 齐全、phase ACTIVE | UUID `63aa184d-c81c-497e-9d8d-54f2680053b9`、原 process/事务不变，Energy 仍在物理端口 |

Electrolyzer 原执行进度已付费，不在升级时重新扣能；Rolling 强停发生在
`save-all flush` 后；Precision 重放的是已完成配方的旧世界，不能据此宣称
覆盖所有 PREPARING/ACTIVE 交错写入时点。完整精密装配机迁移/拆除范围另见
[移除验证](../v1.2.0-precision-assembler/PREC04B-REMOVAL-VERIFICATION.md)。

## 可复核证据与命令

- [Electrolyzer 实际升级/重启摘要](electrolyzer-beta-restart/summary.json)：保存两份
  原始 chunk、两次完整进程日志、首次配置失败日志。两次通过进程均退出 0。
- [Rolling 新世界摘要](rolling-restart/scenario/summary.json)：强停子进程退出 1，
  后两次进程退出 0；[SHA256SUMS](rolling-restart/SHA256SUMS)覆盖 11 份执行证据。
- [Precision 旧世界迁移摘要](../v1.2.0-precision-assembler/packaged-restart/prec04b/legacy-removal/summary.json)：
  两次进程退出 0，7 个 Item/marker、旧影子、Energy 和 process 均已核对。
- 三机有界 chunk fixtures 分别位于 `fixtures/electrolyzer-beta`、
  `fixtures/rolling`、`fixtures/precision-final`；只读校验同时核对 hash 和语义。

```powershell
python -B scripts/v120_electrolyzer_world_fixture.py --verify
python -B scripts/v120_rolling_world_fixture.py --verify
python -B scripts/v120_precision_world_fixture.py --verify
python -B -m unittest tests.test_v120_electrolyzer_beta_migration_smoke tests.test_v120_electrolyzer_world_fixture tests.test_v120_rolling_world_fixture tests.test_v120_precision_world_fixture -v
```

根集成已独立复跑全部 **40 个 Python 用例**；只读审核者另复跑电解/精密相关
24 个用例。聚合检查核对三份新版运行摘要的 JAR hash 相同、各自来源 fixture
hash 对应、前后状态相等、Rolling 11 文件清单、电解两份完整日志和原 ZIP 不变。
记录见 [聚合输出](verification/aggregate.txt)与 [Python 输出](verification/python-tests.txt)。
短周期 Gradle/仓库检查记录见[实施日志](../v1.2.0-implementation-log.md)。
集成后 `clean build test runData runGameTestServer --offline --no-daemon` 通过：
600 JUnit / 112 Required GameTest，DataGen written 0，JAR hash 未变；仓库
严格校验 45 checks 通过。`git diff --exit-code` 仍退出 1（未提交改动），不是
干净发布 Gate 通过。新增迁移文档及日志的 91 个链接校验通过。
独立只读审核另核对两份报告、三组制品和电解/Rolling 原始日志 hash，无事实性
发现；没有把读取证据记成另一次 Java/专服运行。

## 失败处理与限制

首次电解重放因克隆配置的 function 权限 2 无法编译旧采集数据包而失败。
恢复该已归档数据包原要求的权限 4，并以新的世界副本重跑；没有忽略日志错误、
改机器数据、放宽资源断言或增加等待超时。失败证据保留供审查。

本报告只完成代表世界报告，不完成以下开放项：

- `V120-PREC-04B` 全部跨 chunk 崩溃/迁移阶段组合；实际掉电耐久性和方块到
  ItemEntity 的原子事务不在已有保证内。
- 历史 Rolling 世界跨版本升级（原区域缺失）；这里只有候选保存/同制品重启。
- Electrolyzer 所在旧世界的火箭、维度、空间站、卫星等对象的完整升级验收。
- JEI-present 实际客户端、真实 GPU、双客户端、管道兼容及长时负载。
- Python NBT reader 不保留数值标签宽度；Java codec 的严格类型检查不可替代。

完整测试仍按 ADR-018 在全部原有机械和维度完成实现后执行。v1.2.0 状态仍是
`IN_PROGRESS`，全部 Required Gate 尚未满足；本报告的 verified 仅指上述
代表样本、资源账本和已执行的短时重启证据。
