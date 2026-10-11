# v1.8 原生存档根对象：逐 hook 与 entry-only 观察

记录日期：2026-10-11。结论：**事件上限使诊断不完整，原生被取消；
后续修改者、原托管 CME 和 R-021 仍开放。**
本记录只取代状态文件中 [上一观察](JDI-SELECTED-EXITS-07.md) 的最新数字。
历史记录与已封存证据不改动，记录提交不是被观察源码。

## 完成范围与未完成范围

完成新的外部观察器、具体源码与启动器的独立审核和修订审核、一次编译、
一次离线代理测试、一次有界原生观察、独立结果与后续设计审核。
没有修改产品、GameTest、CI、构建、schema、预期日志或 Gate。

未完成后续修改者识别、原 CI 分支/对象/线程关联、存档修复、完整采集、
完整原生 cohort、日志对账、异常路径运行资格、专服重启及 V1/V2。
全部 Required G0-G9 和完整 Python 资格继续开放；不接受 ADR、豁免或发布标签。

## 源码、工具与测试

已提交被观察源码：**32cf63eaf6f65fc79753b3e0b010f2902c341efc**。
干净目录 D:/GitHub/arce-v180-storage-jdi-20261011-03，
分支 test/v1.8.0-storage-jdi-hook-cost。执行输入与前一 1bd540e7 相同
只说明输入相等，不是新的产品资格；原产品资格身份不重绑定。

外部证据目录：
D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-jdi-observation-20261011-03。
工具为 StorageJdiCollector.java、StorageJdiCollectorUnitTests.java、
process_observer.py、compile_collector.py、run_tool_units.py、
run_observation.py、jdwp.init.gradle。具体七项哈希在 ROOT-ADOPTION.md、
执行前 custody 和独立修订报告中。

独立源码审核发现继承的快照截断完整性、NBT key 内容读取、原生失败却正常
返回的启动分支，以及逐命令窗口没有累计预算约束的问题。实际修订标记截断为
partial，只保留 key 引用身份，拒绝原生失败完成分支，缩短工具窗口并共享 Git
预检 deadline。原报告和 preimage 保留，修订报告不替代原始失败记录。

一次 javac 原始 exit 0，耗时 1.1394283 秒；产生 19 个类、117,933 字节。
主类 56,655 字节，SHA-256：
6096ac197489f632c3a54cdabb3690bb5404d7b86e4cbc00af23cd94953222b7。
所有类在运行前及独立结果审核时逐一匹配。一次离线测试原始 exit 0，
耗时 0.2563134 秒，17/17 通过，最坏标量摘要 20,455 字节，低于 65,536 字节预留。
测试覆盖 metadata/cache/bounds、拒绝读取、上下文退役、首次失败保留、
EventSet/resume/请求失败计数、key 身份和截断等本地代理边界；
不执行 attach、目标 VM、完整 root-entry 输出或 selected-CME 裁剪集成。

## 关键设计边界

本次明确选用 **entry-only**，不建立 backing-map 的 modCount 修改提案 watchpoint，
保留 handoff 快照和五种 all-thread、instance-filtered CompoundTag 入口。
覆盖损失在 CLI、start、coverage、summary 中声明，不等价于完整 writer 观察。
入口不是已完成修改或序列化；相同 modCount 快照不是内容不变或 happens-before。
没有目标调用、写入、evaluation、pinning、NBT/key 内容、遍历、复制、序列化、
注入并发、ASM、AT 或替换字节码。调试暂停仍会扰动目标。

Native/attached/collector/退役窗口保持 900/600/620/10 秒；
事件 50,000、请求 256、根 8、临时 context 8/10 秒、临时请求 64、
JSONL 128 records/1 MiB、split stream 4 MiB 等上限不扩大。
Root 的命令分配为 static 60 秒及 compile/units/Git 各 40 秒；
原始 phase 收据符合对应分配，但早期 static 是保守计费而非完整重建计时，
不声称通用 OS/JDI 截止保证或项目资源资格。

## 原始运行结果

Collector 原始 exit **2**，138.7568092 秒；终止原因为
dequeued_event_cap_reserved_margin。49,743 events 已 dequeue/处理，
未处理 0；48 lifetime requests、18 temporary requests、3 tracked roots、
0 active contexts，dispose returned，capture complete **false**。
258-event 预留阻止下一 EventSet 越过总上限。

Native 原始结果 **CANCELLED**，177.6182109 秒，original exit **null**；
退役 exit 1 不替代原始结果。外层启动命令 exit 1，失败记录保留，无完成文件。
两个 owned Job 退役后 active process 为 0；完整 stream capture 只表示输出收集，
不是采集、原生测试或 Gate 通过。未启动第二次原生尝试。

607 项测试被宣布，但仅 8 个批次开始，没有完整结果或正常关闭。
日志原始 entries 为 477 INFO /113 WARN /2 ERROR /0 FATAL。
两个 ERROR 的实际 stack/source 指向有意的 Energy-only migration
BindingSaveFailure，坐标 40003,40001，不是原事故的 (-13,7) IOWorker CME。
3800/8691 ms overload 警告保留，不豁免。完整 cohort 不成立，因此不运行对账。

## 对象身份与测量的有效范围

Publication、pending data、runStore、RegionFile handoff、CompoundTag.write
入口分别保留下列同一 root/map 与快照：

| Root / map | Worker | modCount |
|---|---:|---:|
| 26792 /26793 | 19756 | 15 |
| 35578 /35579 | 30601 | 16 |
| 36188 /36189 | 24443 | 16 |

relative directory 是普通 region、Space region、Moon region 的 metadata，
不是独立 dimension/lane 权威，不能关联原托管对象。三个 root 只交付 write entry；
没有捕获后续 put/remove/merge/getAllKeys、完成修改或 durable-write receipt。

六次 invocation-matched normal read return 中五次 empty Optional，
一次磁盘读取在 events 34920/34921 返回 root40297/map40298、worker30601、
modCount16，与先前发布的 root35578/map35579 是不同对象。
没有读取内容，因此不证明内容相等、损坏或同一 published root 的后续修改。
现有 methodExit 不接纳新 read root，conversion/root-entry guard 不能继续跟踪
该对象；这是实际覆盖边界，不是证明该对象发生转换或导致原事故。
六个 context 正常退役，18 个临时请求删除；gaps/失败处理/resume 失败为 0。
pending-read branch 交付 0，conversion 三种 hooks 选中 0。
没有 selected CME，CME 请求类别本身也创建 0；不证明事故事件不存在。

FIXED 交付 49,729，其中 selected 24、rejected 49,705；
本地 handler 合计 23.4479253 秒。read entry/disk local 分别交付
13,654/13,653，合计本地处理 12.5034124 秒。EventSet dequeue 后到 resume 前
合计 23.5872384 秒，resume 调用另计 4.0335394 秒；
handler 内嵌于 EventSet 范围，不能相加当 VM 开销。
request local enable/delete/summary clocks 也不是完整目标请求生命周期。
这不是受控性能比较，不证明移除 watchpoint 后更快或原生延迟原因。

## 独立审核、清理与封存

独立结果报告 SHA-256：
eb7d19df4a695e4e63abb626e3e7ecd56fb02addb5882ff0657d5b916b365c05；
证据检查截止 2026-10-11T00:17:00.1975596Z，finalization 随后记录。
独立设计报告 SHA-256：
038041856b53d5c6184d4c222d35ff776813c7ca4f1f78e9b7ce49fad7a50297；
报告内 assessment 截止为 2026-10-11T00:16:21.5436102Z；
代理交付所报的最终核验为 2026-10-11T00:18:44.9159663Z，不是另存的命令收据。
两者查验实际 source/artifacts/receipts/census，不以 Root 报告代替输入。

全部读取者释放后，Root 单次累计预检 3,993/50,000 项，没有 reparse；
只删除本次新 checkout 的 build/.gradle 与 packet 自有 temp。
删除前原生日志与保留副本哈希一致，之后源码仍干净且同一 SHA。
保留编译类和离线测试证据；原世界、旧包、旧拒绝目标、共享缓存、外来进程、
AGENTS 和 implementation log 及继承的未提交内容均不处理。
清理、封存和本记录不在运行审核截止内，不算独立再次放行。

封存包：**111 个已列文件 /1,348,347 字节**，另有 10,683 字节清单。
SHA256SUMS.txt SHA-256：
b8d7fc66eec8ee969811688951a38ad123593b8e792332ead7383241a26e88ef。
REPORT.md SHA-256：
481d54502cf46a185399495dd41acf4ef99d91832280bfca7e086d3adda07944。
observations.jsonl 49 records/56,901 字节，SHA-256：
e7e79041f299334452e77d81504b3173fa86edef9290029a76e81ec8aed4c2fe。
server.latest.log 300,934 字节，SHA-256：
95ef060bb090b9e225cdbb9ddfb8374ce1f265f4d513da00cdc5a2da0c61c963。

## 当前版本后续范围

先静态核对精确原生成员的 writer 覆盖及 selected read-root 接纳边界。
阶段性退役全局 hooks 是另一个需独立冻结、明确遗漏的候选模式，
不因此授权新运行、不扩大预算、不重跑已结束工具。
v1.8 独立内容审核另指出 detector 的 installed/restart 资格可单独推进，
但物理舱口、真实组件获取、typed propulsion 和有限氧储备仍有实质前提；
不把辅助模型、诊断完成或文档审核当作功能交付。
