# 真实 Beta 电解机升级与重启

```yaml
task: V120-MIG-03B3
status: verified
scope: two_electrolyzers_only
executed_at: 2026-09-26
environment: S1 / Windows / Java 17.0.7 / Forge 47.4.10
implementation_base_commit: 3dbe6884e6d8bff3c676c4f7cebee0824b507602
tested_implementation_commit: null
uncommitted_worktree: true
artifact_sha256: 6077fd10695ef387edfda8de6543e51cc4b9deb94d0005fe398dc23ad8ada63a
```

## 输入与执行边界

输入来自[真实 Beta fixture](ELECTROLYZER-BETA-FIXTURE.md)，不是在新版原子注入
旧 NBT 的合成场景。[脚本](../../../scripts/run_v120_electrolyzer_beta_migration_smoke.py)
验证原 ZIP 哈希后，逐项校验全部 59 个成员的名称、大小、SHA-256；共
21,832,756 字节。拒绝目录穿越、重复/大小写冲突、链接、额外成员和超限展开。
只向全新隔离目录写文件，不调用 ZIP 的 `extractall`，也不改原 ZIP。

复制已有 Forge libraries，不复制源专服的世界；安装实际工作树 JAR 后，将
完整旧世界写入副本并再次核对原始 manifest。服务只绑定 `127.0.0.1:56061`，
离线模式、512–1024 MiB 堆。两次启动均检查模组版本，保存并正常停止后才读取
Anvil；重启使用同一个目录，不重新提取世界。这里只验收两个电解机，其他
世界内容虽被 Minecraft 正常加载，其变化不属于本报告的验收结论。

## 实际观察

| 电解机 | 原 Beta 输入 | 新版保存及再次重启 |
|---|---|---|
| `(0,100,0)` 暂停机 | 2 空罐、1000 mB 水、0 FE、进度 40 | `arce_machine` 完全相等；`waiting_energy`，进度 40、已支付 800 FE、revision 0 |
| `(4,100,0)` 运行机 | 8 空罐、4000 mB 水、19500 FE、进度 25 | 4 氢罐 + 4 氧罐，水/输入清空，12000 FE；idle、revision 4 |

运行机原批次已完成 25/100 tick，剩余四批的耗能为
`(4 × 100 − 25) × 20 = 7500 FE`，故剩余 `19500 − 7500 = 12000 FE`。
暂停机采用真实配方签名，不保留迁移占位签名；已支付进度没有重新扣费。

两机继续保存兼容资源根 `arce_machine` schema 1，新增独立 `arce_process`
schema 1；两次保存都没有 journal。运行机最后事务 UUID
`12101332-e03c-43f8-9c79-107ae3f1c560` 在重启后不变。Electrolyzer 不在 process
根保存 machine UUID，不能把事务 UUID 当作机器身份；这里核对固定维度/坐标、
BlockEntity ID，以及完整机器根在第二次保存后的相等性。

实际 [summary](electrolyzer-beta-restart/summary.json)、两份原始压缩 chunk、完整
日志和失败记录均保留在 [证据目录](electrolyzer-beta-restart/)。

| 对象 | SHA-256 |
|---|---|
| 不变的原 ZIP | `9022b2fdf4537168bc33f1db06f96c8d5ce150c221177cd4e0890b037f9bed1c` |
| 升级后 chunk | `0b470c84ddd68aed9378fdcc14fb33b935ca09038bc73e59dae1aca664d9b551` |
| 重启后 chunk | `8cc0c4f4e1d0ab75b7db0862e421e85d56f019e19d3873ecaffff1d778bebb42` |
| 运行摘要 | `225042946496ccd95b50da84ef73bafa07d11806188ef0402f1c1e9e92659f68` |

chunk 哈希不同是世界 tick 等外围数据变化；两个机器根的比较仍为完全相等，
没有把整个 chunk 字节相等作为重启条件。

## 命令、失败及修正

```powershell
python -B scripts/run_v120_electrolyzer_beta_migration_smoke.py `
  --runtime .gradle/mig03b-before-clean-20260926/dedicated-server-smoke/prec04b-removal-legacy-20260926 `
  --session build/mig03b-electrolyzer-beta-v2 `
  --artifact build/libs/advancedrocketry-community-1.20.1-1.1.1-dev.jar `
  --evidence-dir docs/work/v1.2.0-migration/electrolyzer-beta-restart `
  --tested-commit 3dbe6884e6d8bff3c676c4f7cebee0824b507602 --tested-worktree `
  --java 'C:/Program Files/Java/jdk-17.0.7/bin/java.exe' --port 56061
python -B -m unittest tests.test_v120_electrolyzer_beta_migration_smoke tests.test_v120_electrolyzer_world_fixture tests.test_v120_precision_world_fixture -v
```

- 第一次使用 `build/mig03b-electrolyzer-beta`，退出 1：测试副本把 function 权限
  设为 2，旧 capture 数据包内 `save-all`/`stop` 无法编译，日志扫描拒绝通过。
  [完整失败日志](electrolyzer-beta-restart/first-attempt-full.txt)保留，没有忽略错误。
- 恢复原 capture 工具明确使用的权限 4，在全新副本重跑，升级和重启均退出 0；
  未调用旧 capture function，也未改写数据包。另在首次检查中纠正了测试脚本
  的枚举拼写，使用运行时代码定义的 `waiting_energy` / `insufficient_energy`。
- 实际捕获后进一步收紧 revision/subject 断言，补齐所有退出分支的自启 JVM
  清理与完整日志复制；最终脚本的严格断言重新读取两份捕获 chunk，测试通过。
- 8 个新增 Python 测试覆盖 ZIP 成员边界、真实原始清单、实际迁移/重启 chunk、
  资源/进度/签名/revision/subject/journal 篡改，以及 Ctrl+C 清理自启进程。
  加上既有 fixture 用例共 **24 项通过**；独立只读审核者复跑亦为 24 项通过。
  独立审核没有运行新的 Java 进程，不把复核读取说成另一次专服测试。

## 未证明的范围

未修改 Java、配方、schema 或玩家行为。本项不证明其他世界对象完整升级，
不证明任意崩溃点、掉电、跨模组管道或并发玩家操作。Python NBT 比较不保留
数值标签宽度；严格 tag 类型仍由 Java codec 测试覆盖。长期、远程、真实 GPU、
双客户端和完整内容验收按 ADR-018 延后，本项不放行任何版本全部 Required Gate。
