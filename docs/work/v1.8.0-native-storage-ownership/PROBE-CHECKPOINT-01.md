# v1.8.0：原生存档引用探针检查点

日期：2026-10-11，Asia/Taipei。状态：`EXPERIMENT_OBSERVED_CAUSE_UNRESOLVED`。
本记录不合入实验源码、不接受产品修复、不放行 Gate。

## 完成范围与源码身份

实验分支 `test/v1.8.0-native-storage-ownership` 的正常推送提交为
`23729a00d71deddcc8b1ecd981a6b324287b1c4d`，父提交为
`64c295e777ae902d98a5b1b886c9067102393e8e`，树为
`bdf10895cdd26a7fbc92b3e5a6f999d0d2f53c1b`。
它只增加 NativeStorageOwnershipProbeTest.java 和自身的准备记录。
源码 SHA-256 为 `14b9bc725398bc0506537072297a981bd9231c2070c736df559ebdd852c96f5f`。
主开发分支不包含实验源码；全回归被测身份仍是 `52a29c1e`，不能以该单项结果替换它。

## 实际执行与观察

在干净且身份核对后的实验 worktree 执行了唯一一次命令：

```text
C:\Windows\System32\cmd.exe /d /c gradlew.bat --offline --no-daemon --max-workers=2 test --tests io.github.sunthemoon.advancedrocketrycommunity.testsupport.NativeStorageOwnershipProbeTest
```

Java 17.0.7。命令退出 0，原始阶段用时 52.3183852 秒，独立退出/排空阶段用时
0.0586525 秒。stdout / stderr 为 1382 / 41770 字节；均完整 EOF，无溢出或存活的
捕获读取者。组合回执 `ok=true`，原生私有 Job 的提交内存上限在恢复进程前读回为
6 GiB，目录监测无错误并已结束。这是执行上限，不是 RSS、traced/native 峰值或网络隔离资格。
编译和定向测试成功，不等于 `clean build` 或全部 JUnit 已运行。

保留 XML 为一项测试，零失败、错误和跳过，与清理前原件逐字节相同。
四个固定、很小、无环、提交后不再修改的根/子样本均观察到：

- 初次读取存在，返回根和直接子节点与提交对象分别为同一对象。
- 后续读取的根/子对象不同；原对象 copy 回调计数均为零，write 回调各一次。
- 实际 write 回调线程不是调用者线程。两个 worker 关闭返回，重新打开后四条标记记录通过。

正向对象身份和序列化回调支持这些样本/API 的实际观察。零 copy 计数、不同对象或
worker 关闭不能外推为全面复制策略、确定的 pending 窗口、整条原生区块保存所有权契约
或物理持久性。没有修改提交后的 NBT，没有确定托管事故的维度、实际修改者或因果顺序。
编译移除警告、Gradle 警告和 XML 终端警告均保留，不纳入 GameTest 预期日志分类。

## 独立审核、证据与清理

外部封存包：`D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-storage-ownership-20261011-01`。
26 个证据文件合计 159059 字节，不含清单；SHA256SUMS.txt 为
`a915f85b3699d7bf7ffc9b15782a07ada8ef7ff4fb65901b93c1dec70cef634a`。
独立报告 RESULT-REVIEW-03.md 为
`d9631ddce6a1ec99e6801465be905bb8c4f10708308fd1bb5dad1470178b3627`；
保留 XML 为 `8d83848f32891ed6bfe8c577e0aa1854f9097c27ed5d3fb0e84892040891241e`。
报告核对提交、原始流、四行观察、原生回执和原件 XML，没有重跑目标。
Low 的退出相邻元数据来源问题已修正：即时数字仅是 Root 的工具观察；后来保留的
postflight.json 有独立时间，不冒充原始退出时刻。原报告和更正后的 RESULT.md 均保留。

结果审核结束后，只清理本次自建 worktree 的 build：4435 项、57713679 字节。
删除前核对绝对范围、原件 XML、无重解析点和无引用该 worktree 的进程。
包内空 runtime-temp 只作非递归删除。两项预存 Gradle JVM、其他 worktree、缓存和
原始世界未控制或清理。封存包中的助手不再执行。

Root 这次调查保守读取储备为 45 MiB / 64 MiB，不是精确测量或内存资格，也不修复
此前日志审核累计读取超限。记录准备的路径探查曾指向不存在的工作目录，原工具错误
保留；实际先前调查位于 v1.8.0-gametest-log-expectations/STORAGE-ALIAS-INVESTIGATION-01.md。

## 相关审核与未完成范围

独立客户端交付审核在 `64c295e7` 截止：pacing 的 `f9bdc800` 已由 `0f91c6ee` 合入，
Root 的 loader `30ca28ef` 已由 `ba680996` 合入，不需要再次合并。
审核报告 SHA-256 为 `8f0b430c09826169bb08d1180176d14440810f661c821413abd2ba31f09c1354`，
保存在 `D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-storage-records-20261011-01/CLIENT-REVIEW.md`。
它保留一项 Low：Tau 的“至少十秒”注释超过代码保证的 9.9 秒等待间隔；没有新的
Critical/High/Medium 来源发现。旧客户端 UI、配置、火把、天空和首次连接观察没有被
这些测试夹具修复；没有新 V1/V2、截图或原生回归资格。

另一 worktree 的 ChunkSaveWatcher 清理候选仍未提交，不能标为已交付。
十项纯状态测试和新增原生夹具均未执行；AfterBatch 与测试附加终态监听器的先后关系
正在修订，原生失败/超时和实际 EventBus 注销仍未证明。该候选不保留 NBT，不归因于 CME。

此实验的完整构建、全部 JUnit、runData、runGameTestServer、专服、客户端和重启
均为 NOT_RUN。主开发源码的既有托管结果仍是 597 个必需测试通过、日志对账退出 1，
63 ERROR / 161 WARN / 0 FATAL；新增区块存档 CME 不加入白名单。
完整 Python 资格、严格校验、R-021 和全部 Required Gate 不变，版本保持 IN_PROGRESS。
后续仅在 v1.8 内独立验证 watcher 修订，并采集实际原生保存对象及修改调用的因果证据。
