# v1.8 原生存档根对象：限定调用返回观察

记录日期：2026-10-11（UTC 运行日期为 2026-10-10）。
结论：**有界诊断失败且不完整；后续修改者与原托管 CME 原因仍开放。**
本记录取代状态文件中 [首次 JDI 观察](JDI-OBSERVATION-06.md) 的最新数字；
旧记录和已封存证据保留为历史，不重跑旧工具、不改旧文件。

## 完成范围与未完成范围

在新的干净 worktree 上，替换持续启用的 class-wide method-exit 为选定调用期间的
临时 class/thread 请求，加入有界计数和首次关联聚合。完成具体工具的独立源码审核、
修订审核、一次编译、一次保持原测试 cohort 的本地诊断，以及独立结果和后续设计审核。
编译与运行工具只在外部证据目录；没有改产品、测试、CI、构建、schema 或日志预期。

未完成：后续根修改者识别、原 CI 分支/对象/线程关联、存档修复、完整采集、完整原生
cohort、日志对账、异常观察路径运行资格和发布/性能/重启/视觉/多人验收。
完整 Python 资格及 R-021 仍开放，不接受 ADR、豁免、版本 Gate 或发布标签。

## 源码、工具与独立修订审核

被观察的已提交源码为 **`1bd540e77f8614aacd9dde38db2afc22043451ee`**，新目录为
`D:/GitHub/arce-v180-storage-jdi-20261011-02`，分支为
`test/v1.8.0-storage-jdi-selected-exits`。其 src/scripts/CI/Gradle 输入与前一次
d16775e 相同，仅说明输入相等；不是新的完整回归，也不重绑定既有产品验收身份。
本记录后续的文档提交不是本次被测源码。

采纳的外部工具哈希：

| 文件 | SHA-256 |
|---|---|
| StorageJdiCollector.java | `e1cbc22acc7063faa81efbf75bba4d13c73c54ae5882e9b263b85cc04e318c29` |
| process_observer.py | `e34ed16f1bf74aa4713f6a36436d8b682598e7580aff693faf6e2723854a828d` |
| compile_collector.py | `6e909b273feeecdfaccce98f61fd8668f717408edd2cb9bbccad039f4564602e` |
| run_observation.py | `8bd06567bd5c55e66eb4c6ed19a4d236f080337f748af92ef9ccbbde3c944407` |
| jdwp.init.gradle | `07089fa40d604e6924c9f52db342f3d2169ab4fb84dbcc421cd8ca7b46611704` |

编译器审核 F1 指出继承的 JDK_JAVAC_OPTIONS/CLASSPATH 和注解处理会增加条件性输入。
修订移除这两个变量并使用 `-proc:none`；运行器移除变量仅为确定性处理，不声称已发现
实际污染。独立后续审核按以上编译器/运行器哈希确认源码问题已处理。

采集器审核 R-SRC-01 指出清理异常可能覆盖首个观察失败。Root 第一次修订 4ee03acb
处理内层 handler 和退役处，但遗漏 EventSet 级预检查；独立复审保留这一不足。
第二次修订 e1cbc22a 在 finally-resume 之前保留外层首个失败，随后独立审核确认
报告范围内的源码问题已处理。两次前像和各次意见均保留。没有在运行中注入双失败，
因此源码修订不是该异常组合的运行资格。675 行外部诊断类的职责/规模审核也已保留。

编译前核对工具哈希，编译后记录 **12 个 class / 71,519 字节**，运行前再次绑定全部
工具与 class；独立结果和设计审核分别复核清单。主 class SHA-256 为
`18ce998ff030346899245b44da46c8f6ed85c50fa83b26d08c30b8c3cb2115df`。
不声称密闭环境、完整 JDK 来源审核、通用 JDI RPC 超时或全天候进程身份保证。

## 实际命令与结果

Root 是唯一 JVM/Gradle/原生操作方；没有派活给 Claude。使用固定 CPython 3.13.15
的 `python.exe -I` 调用新证据包中的 compile_collector.py 和 run_observation.py。
原始 receipts 保留完整 argv/cwd、创建句柄结果、Job 限制与分离流状态。

| 操作 | 实际结果 |
|---|---|
| 固定 JDK 17 javac，release 17、jdk.jdi、`-proc:none` | 原始退出 0，1.020147 秒，空的完整 stdout/stderr，退役 Job 活跃进程为 0。 |
| `gradlew.bat --offline --no-daemon --max-workers=2 --no-build-cache --rerun-tasks ... runGameTestServer` | 一个新的完整 cohort 尝试，因采集失败而 CANCELLED；639.891317 秒，原始退出为 null，退役后的退出 1 不是原始原生结果。 |
| 固定 JDK 的外部 JDI 采集器 | 600.239145 秒，原始退出 2，attached_window_cutoff；没有到达 620 秒外部进程期限。 |
| 原生日志对账 | 未执行；没有完整 cohort/正常 shutdown 终端，不能接受日志。 |
| clean build、runData、完整单测、专服重启及视觉/多人/发布性能 Gate | 此诊断切片未执行，不标通过。 |
| Root 封存/散列复核、精确范围 diff/链接与提交检查 | 独立审核截止后执行，属于记录完整性检查，不是运行或 Gate 放行。 |

原生期限保持 **900 秒**，附着窗口 **600 秒**，采集器外部期限 **620 秒**，退役
期限 **10 秒**。本次不是 900 秒原生超时。原生 Job 6 GiB、采集器 Job 512 MiB/
Xmx256m，第一次 resume 前核对限制；磁盘余量大于 10 GiB，Gradle workers 不超过 2。
循环地址 `127.0.0.1:51438` 的 listener 归属在附着前与新 Job 作逐点核对。
没有第二次运行、加超时、改 cohort/断言、扩大资源上限或放宽日志规则。

## 实际观察及其边界

最终 JSONL **30,396 字节 / 31 条记录**，SHA-256 为
`151608d442dd62b6c7ab3f973523c1ce455b78152af2022dabc208ea3de736e3`。
终端报告 16,227 个 dequeue 事件、16,226 个已处理、收到但未处理的 1 个事件；
33 个总请求、12 个临时请求、1 个根对象，0 个活跃临时上下文，dispose 已返回。
`capture_complete=false` 且覆盖非穷尽，不能根据未见事件证明没有修改。

根对象 **26561**、HashMap **26562**、worker **19142**、storage **19718** 和 pending
对象 **26568** 在 publication、pending-data、runStore、RegionFileStorage handoff、
CompoundTag.write **入口**处存在对应记录，modCount 快照为 15。目录为
`.\world\region`，逻辑维度明确保持 unknown。4 次选定的普通读取返回 empty Optional。
这证明记录点的对象关联及正常返回形状，不证明调用完成、成功发布、完成修改、序列化
完成、保存持久化、happens-before 或并发重叠。没有捕捉到后续修改者、conversion
返回、完成的 put/remove/merge 或选定 CME，也不把不同线程的事件顺序当作事故归因。

固定断点交付 **16,216 次**：选中 12、拒绝 16,204。所有本地 handler 时间累计
**14.6859825 秒**；统计合并十个固定 hook，不能给单个 hook 排名，更不包括全部
VM/JDWP/队列/resume/JIT/目标运行成本。可选 map watchpoint 安装后没有交付事件，
不证明其 VM 成本为零。临时出口仅交付 4 次且正常退役，不证明全部异常/截止路径，
也没有证据将停滞归因于临时出口、启动或特定方法。

完整保留的 server.latest.log **88,831 字节**，SHA-256 为
`f7ebacc10c61d608c68dae30aff875ae3aa6066cc44c15c600f07d6ac840afcb`，清理前再次与
源日志全字节散列相等。日志宣布 **607 项测试**，只含 **6 个批次开始**，无完整 cohort
或正常 shutdown。原始 header 计数是 165 INFO / 113 WARN / 2 ERROR，不是验收对账。
两个 ERROR 为 chunk 40003,40001 的有意 energy migration binding-save 夹具；
不是原 (-13,7) 的 CME。3 条 overload WARN 保留，不作性能豁免或成本原因推断。

原 CI 38064322640 在 52a29c1e 上原生 597/597 成功而严格对账因额外保存 CME 失败的
结论不变。原栈和根 key map 的条件性定位见 [根对象审核](ROOT-KEY-ITERATOR-AUDIT-05.md)。
本地字节相等、单次入口关联或未见 CME 都不能关闭原事故。

## 独立审核、证据与清理

外部包：`D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-jdi-observation-20261011-02`。
其中 REPORT.md、全部原始 receipts/流/JSONL、class 清单、惰性前像、作者与三个阶段
源码报告、独立结果/设计报告及计量账均已保留。

- 结果审核按原始流/终端先于 Root 派生分析的顺序核对，完整共享释放截止为
  **2026-10-10T23:16:49.0568843Z**；报告 SHA-256 为
  `a456e14e0459bf902e598e994be124ea5b599391e9d2d3cfd46c6c3007e5eb01`。
- 设计审核没有实施或运行，完整源码/HEAD/共享输出/清理兴趣释放截止为
  **2026-10-10T23:18:56.6100344Z**；报告 SHA-256 为
  `95429f6b63e342cf30bc0157956412bf34a106c346cd7d090ef214792e8beab5`。
- 两份审核不覆盖之后 Root 的清理、封存和仓库记录；不将这些操作写成独立运行资格。

Root 在全部读者释放后，仅删除新 worktree 的 build/.gradle 和新包的
owned-temp/__pycache__。一次累计清理预检查 **3,930 / 50,000 项**，无 reparse point；
成功时四个本次 PID 均 missing，原始 Job 退役计数均为 0，源码仍干净。
日志全字节相等先于删除；保留所有 class、证据与源码检出，不删原世界/输入、旧拒绝
清理目标、共享缓存或其他 worktree，不控制既有 Java 进程。准备时两次清理调用分别
在 UTC 类型比较和瞬时 PID 数字存在处停止，均在枚举/删除之前；这些失败不隐去。
显示截断、元数据探测与不足修订等偏差保留于 ROOT-TOOL-NOTES.md/审核计量账。

封存共 **109 个清单文件 / 949,195 字节**，另有 **10,439 字节**的 SHA256SUMS.txt。
清单 SHA-256 为 `520aaf032533cb96b492989c3981af10e11c91b13120ecd01ca7f9465264e0e1`；
Root REPORT.md SHA-256 为
`7e43357cf6f92ff9d50b7c861e91ccd262967eaa1505dd58c912879afce32d35`。
所有清单项在包内且已复核，不列易覆盖的 build/Temp；原封存包不改，封存工具不重跑。
记录阶段另存 `D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-jdi-records-20261011-02`。

## 关键决定、已知风险与当前版本建议

保留失败，区分完整字节流/退役与完整诊断/原生/Gate；不预选修改者，不把入口或字段
赋值提议写成完成的修改。原托管对应、未处理事件、嵌套 tag/直接 map 变更、历史写者、
异常观察路径、全 VM 成本和存档持久化仍存在证据缺口。

最小的保覆盖候选是有界逐 hook、EventSet/resume、请求生命期统计，以及保留 stale
调用清理的快速拒绝与精确字段读取简化。先冻结实际改动并独立审核，再单独界定执行。
可选 watchpoint-off 诊断会丢失直接 backing-map 结构变更提议；receiver/window/
handoff-only 还会损失其他接收者、窗口外或正常完成返回证据，不能暗中替换原契约。
本次不能证明成本原因或承诺候选改动使诊断完成，更不能为诊断改产品同步或保存内容。

全部 v1.8 Required G0-G9：**未满足**。版本保持 **IN_PROGRESS / IMPLEMENTING**，
不调整生产验收身份、R-021、性能预算、日志预期、既有批准或用户决定。
