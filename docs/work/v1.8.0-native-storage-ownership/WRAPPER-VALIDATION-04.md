# v1.8.0：原生包装器实验与普通调用者边界

日期：2026-10-11，Asia/Taipei。状态：`TEST_SOURCE_INTEGRATED_CAUSE_AND_GATES_OPEN`。
本记录取代[实现检查点](WRAPPER-IMPLEMENTATION-03.md)中的“运行未执行”现状，
不改写该提交的历史，也不把旧 IOWorker 探针并入 Main。

## 源码和集成

基线为 `ba89575fd05b595af3426227124c37683e7bf925`。原始实现提交 `e36f2b7c`
只执行构建；独立源码审核发现 Low：`closed=true` 比公共 close 返回所能证明的范围更强。
后续提交 `2881679a42a19b0208ef3308b483ac68bf25143b` 将标签改为
`close_returned=true` 并更正记录；没有改变控制流、断言或期限。
修订独立审核未发现新的源码问题，截止为 `2026-10-10T19:00:28.8648921Z`。
报告 SHA-256 为 `9a3ec8e29c784b36ff5a3e8000712b835b254124cd5faca66491a65380bfa263`。

修订版被测树为 `2c3aac7790e0d37382dafef968d3d7f0da9492b9`，正常推送至
`test/v1.8.0-native-wrapper-contract`，再合入并正常推送 Main
`5db6d1b7ff7d0536f634c688c2882e7a534cd1c7`。集成提交的完整树与被测树相同。
实际源码 diff 只有一个 169 行 GameTest 类和其实现记录；产品保存策略、schema、
注册、预期日志清单、既有测试断言和预算均未改变。Main 原有 149 项所有者状态和
两个受保护文件的哈希不变，混合实施日志未编辑或暂存。

## 实际原始命令

以下命令均在独立实验 worktree 中针对已提交、干净的修订版执行。Gradle 命令共同前缀
为 `cmd.exe /d /c gradlew.bat --offline --no-daemon --max-workers=2`。

| 命令 | 原始退出 / 秒 | 结果 |
|---|---:|---|
| `clean build` | 0 / 173.4162401 | 382 XML，2202 个实际 testcase，零失败、错误、跳过 |
| `runData` | 0 / 25.1258795 | 随后 `git diff --exit-code` 为 0，工作区干净 |
| `runGameTestServer` | 0 / 247.6551805 | 602 个 Required 原生测试通过，正常保存和关闭 |
| Python 3.13.15 `-I -X utf8 -B scripts/check_gametest_log.py <新完整日志>` | 0 / 0.1233449 | 38 条原规则匹配，62 ERROR / 161 WARN / 0 FATAL |

三个 Gradle 原始期限各为 900 秒，对账为 180 秒，退出后捕获窗口各为 10 秒，
每流上限 4 MiB。所有完整流均 EOF，无溢出、捕获错误或残留读取线程。
`:test` 实际执行；部分编译任务来自缓存，不是 clean-room 发布构建。
Java 17.0.7、Xmx1G 工具选项、既有 Gradle Xmx3G 设置及私有 6 GiB Job Object
承诺上限/启动前读回、空间 guard 均有原始回执。这些是隔离和配置观察，不是峰值
内存、参考 Xmx4G 性能或发布 Gate 证明。日志的一次 2383 ms / 47 tick 卡顿仍保留。

原始 e36 构建保留在 `D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-wrapper-runtime-20261011-02`，
未执行 DataGen、原生测试或对账。其清单 SHA-256 为
`5b3f09610c1a166b2d2a88ab67bdf7ffe9b63c93dc4387f8d518e4e1610944cb`。
修订版完整原始证据在 `D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-wrapper-runtime-20261011-03`，
清单 SHA-256 为 `e089e723f88be0aa32ab8347fbfaafe19f11679314d6487f800dceeb7439105f`。
封存后的 helper 仅是证据，不能再次执行。

## 独立结果复验和实际身份

独立审核核对真实源码、全部 XML testcase 子项、完整原生日志与原始回执；382 个
XML 和新日志的保留副本逐个与仍存在的原件哈希相同。新日志为 1864125 字节，
SHA-256 `41c4d86182c14329e46b75a56fb678925af8431677e5f61b0ed0aedc2feb5f52`。
审核者独立归类全部 ERROR/WARN，再对固定脚本作一次有界重放，原始退出 0，
0.1230934 秒，完整捕获。报告未发现新的可操作问题。
读取截止 `2026-10-10T19:14:48.0309369Z`，报告 SHA-256 为
`ab62a70b8169ae5a92c5238081a2eaf8c84888cf23f12ba436f5cde16c53a5c1`。
报告、截止、有限审计和原始重放输出的原件/副本哈希均核对一致。
审核中的截断、错误聚合、超大生成文件拒绝读取及计账 bootstrap 限制均保留；
最终修正是新增文件，不替换失败记录。不能据此声称所有审计步骤无偏差。

- 四个预先构造且发布后不修改的小标记树：首次读取根和直接子对象均是提交对象；
  flush 后及新包装器 reopen 的根/子对象均不同，值断言全部通过。
- 独占、未发布输入的转换：DataVersion 3465，`minecraft:overworld`，generator key
  明确缺省；结果根仍是独占输入，另一个 context probe 被注入，原序列化值不变，
  结果不泄漏 `__context`。使用真实已初始化的 DataFixer，只检查已加载原生测试区块。

两个新增 GameTest 各为独立批次、100 tick，future 读取为 5 秒；flush/close 内部 join
另由原始进程期限约束。身份是这次调度的观察，不是强迫 pending 分支的断言。
没有对已发布输入调用转换或注入并发写者。公共 close 返回且没有 close 失败日志，
也不证明所有物理句柄释放。普通 reopen 不是崩溃/断电耐久性，当前版本且缺省
generator 的转换也不是迁移矩阵；序列化后的有界检查不限制任意序列化器本身的工作量。

## 普通原生调用者的只读事实

另一个独立调查只读解析本地 MC 1.20.1 / Forge 47.4.10 official-mapped 指定成员，
没有复制上游实现入仓库、启动 JVM、反编译、导出整个 JAR 或验证完整包真实性。
报告 SHA-256 为 `d38c810c77119d2d7161a8f93bf009a3ab24fbd080a21e588da9db058d3d2961`。

公共 `ChunkStorage.read` 只交给 worker，不转换。普通 `ChunkMap.readChunk` 随后的
`thenApplyAsync` 选择 `Util.backgroundExecutor`，把 Optional 中的 tag 交给 private
upgrade；它传入实际维度、Overworld 的 DimensionDataStorage supplier 和 generator key。
之后 `scheduleChunkLoad` 选择 mainThreadExecutor 进行 `ChunkSerializer.read`。
backgroundExecutor 有 direct-executor 分支，选择 executor 不等于实际独立线程证明。

pending 读取路径直接返回 `PendingStore.data`；store、该读取和写入持有相同可变引用，
没有防御性复制。上下文 helper 会修改传入根对象。读取 future 的完成与 pending 写入
调度之间不存在“转换已完成”屏障。这些静态交接事实说明可行的共享和交错，仍没有
绑定托管失败对象、分支、线程、维度、生产者或修改者。实验使用服务器线程和独占转换，
没有执行普通异步 read→conversion 组合，不能称为已复现或修复 hosted CME。

## 保留、托管观察与开放项

派活、原始/修订审核、调用者调查、结果审核、失败记录、复制核对、退役回执和
后续记录审核在 `D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-wrapper-contract-20261011-02`。
读取截止后的一次新任务自有输出退役完成：累计 6343 项，无 junction，退出 0；
仅移除本任务新建 build/run-data/logs 和临时目录，源码、原始证据、源世界、全局缓存、
其他 JVM、所有者文件和旧拒绝清理目标均未删除或接手。

[托管运行 38079333190](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38079333190)
固定在集成 SHA，已成功完成。官方 API 于 `2026-10-10T19:34:09.2773320Z` 核对运行和
job `114292828860`；构建、制品审计、DataGen、包含严格对账的 GameTest 步骤均成功。
较早 in_progress 观察保留在外部元数据 01–04，后续结果为新增元数据 05；没有覆盖旧记录。
尚未下载原始托管输出或核对其计数，不把本地计数冒称为 Linux 原始样本结果。
一次成功不关闭先前失败运行 38064322640 的异常；该异常
仍开放，不加入预期清单，也不启用保存保护、全量复制、AT/ASM 或隔离数据作为修复。
watcher 原生失败/超时清理和实际 EventBus 注销仍是 Medium 覆盖缺口。
R-021、完整 Python/resource 资格、专服/重启/恢复、性能和真实 V1/V2 证据，以及
全部 Required Gate 仍开放；v1.8 保持 IN_PROGRESS / IMPLEMENTING。
