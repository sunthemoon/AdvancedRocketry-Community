# v1.8.0：有界 JDI 存档观察未完成

日期：2026-10-11，Asia/Taipei。状态：`ATTACHED_WINDOW_CUTOFF_CAUSE_OPEN`。
这是一次外部诊断工具的编译、独立审核和原生执行记录，不是存档修复、日志豁免或
版本验收。仓库中的产品、测试、构建配置、CI、保存策略和预期日志清单均未改变。

## 源码与执行身份

原始托管事故仍为 run38064322640，源码
`52a29c1e76977033a1b7e85baf32ca5d19c7414c`。其原生通过而对账拒绝新增 CME 的
结论不变，不把异常加入预期清单。本次不同的本地样本固定在已提交源码
`d16775e6ba9e48c775f64f17be6b5926a47b7028`，使用新的独立干净工作树、
Java 17.0.7 / Forge 47.4.10。后续文档提交不替代被观察的源码身份。

外部包：
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/storage-jdi-observation-20261011-01`。
Root 是唯一原生运行操作者，没有向 Claude 派活或接手其他 Java 进程。
调试器会扰动调度，不能重建托管运行的对象、线程或交错，也不提供性能资格。

## 实际命令和结果

| 实际执行 | 原始结果 | 适用范围 |
|---|---|---|
| `javac --release 17 --add-modules jdk.jdi` | 退出 0，0.9147979 秒 | 独立诊断工具编译 |
| 初次启动脚本 | `SystemRoot` 字典键异常，未启动目标 | 保留说明和前置回执；原始 traceback 未入包 |
| execution-01 的 Gradle init | 退出 1，10.3373189 秒，任务尚未注册 | 配置失败，未启动原生 GameTest |
| execution-02 的采集器 | 退出 2，600.2586472 秒 | 达到原定 attached-window 截止，采集未完成 |
| execution-02 的 `runGameTestServer` | 638.1245004 秒时取消，`original_exit=null` | 没有原生用例终态或原始超时结果 |

原生命令使用 offline/no-daemon、两个 worker、rerun-tasks/no-build-cache，
只通过外部 init 给 GameTest JavaExec 配置 JDWP。目标原始上限仍为 900 秒，
采集窗口为 600 秒，采集器外部进程上限为 620 秒；没有扩大预算或追加第二次
原生运行。两次前置启动失败不能算原生重跑或通过。

JSONL 共 8 条、3894 字节；终态记录 6033 个应用层已出队 EventSet 事件、
16 个累计 request、0 个已跟踪根，`dispose_returned`、`capture_complete=false`。
九个选定存储/转换 BCI 的加载方法字节摘要匹配，不能扩大为整 class、整包或
托管加载证明。没有捕获目标区块的 publication、根/map/worker 身份、存储目录、
pending/disk 分支、修改线程/栈或 CME。服务器日志停在 Planetary bindings ready，
没有进入 GameTest 批次，也没有完整用例结果或正常关闭。

本次未执行日志对账；不能从旧包推断对账结果。完整 clean build、单测、DataGen、
打包专服/重启、真实客户端及性能 Gate 均未重新资格验证。先前成功数字仍归属
原被测提交，不改为本次回归指标。

## 进程与证据边界

附着前确认 `127.0.0.1:51437` 的监听 PID 6752 属于新建目标 Job，初始 PID 为
27652。目标 Job 的 6 GiB 和独立采集器 Job 的 512 MiB 提交内存上限均在首次
resume 前读回；采集器使用 Xmx256m。这是限制机制，不是新增峰值测量或预算验收。

原生和采集器回执均为双流 EOF、零 overflow、无捕获错误、reader 已结束，
退役后 Job 活跃进程数为 0；监督线程也已结束。进程流捕获完整和进程退役不能
覆盖 JSONL 的采集失败，也不能把取消后的退出值 1 当成原生断言结果。
26935 字节的完整服务器日志在复制及清理前均与原件匹配，SHA-256 为
`ac6d56187a4e08a993802ca47654062e0795de8ddcc48a1a520d5e0a4ba313b9`。

实际工具为外部包中的以下 postimage；旧 adoption 的 launcher/init 身份被
bootstrap/configuration 更正取代，不重新绑定为实际执行源码：

| 工具 | SHA-256 |
|---|---|
| `StorageJdiCollector.java` | `f1f0ac7101f6d849053f30cf3489423d304e36fb64f421f620744ca888738d89` |
| `execution-02/run_observation.py` | `85d0379b6972a6b74175eb8a3b6ba2d55620e72876aff45b23662f1803a58c8a` |
| `execution-02/process_observer.py` | `e34ed16f1bf74aa4713f6a36436d8b682598e7580aff693faf6e2723854a828d` |
| `execution-02/jdwp.init.gradle` | `80f765015cff99121aff5660c063aeda90f46fc10dda032791eacabd6aad5d2b` |

编译产物、原始流、复制回执、初始 collector 和历次审核均保留；部分早期 launcher/
helper postimage 和首次 raw traceback 不可用，不用说明或哈希补造旧观察。

## 独立审核与后继设计

工具经过独立具体源码审核后执行；初始发现及更正审核保留在外部包。
结果审核的两项 High 是诊断/原生资格阻塞，不是新增产品缺陷归因：未观察到身份
交接，原生被取消而无 cohort/checker 结果。它核对了 postimage、编译和复制身份、
完整流及退役回执，明确不能关闭原因或 Gate。

| 独立报告 | UTC 输入截止 | 报告 SHA-256 |
|---|---|---|
| `results-review/REPORT.md` | 2026-10-10T22:09:12.0660206Z | `22164a0a81dda6fd723ed672cc054414553939d657bafb61dcad039eaeeeddcc` |
| `next-design-review/REPORT.md` | 2026-10-10T22:14:13.9034571Z | `65389849587194a60e854e916baafa82f7f3188c21a57462d973081091234d5d` |

两项累计读取分别为 1940252 和 1037658 字节，低于各自的 16 MiB 上限。
后继设计审核确认两个 class-wide、会暂停事件线程的 MethodExitRequest，在
交付后才过滤无关方法/区块。它建议采用已验证 exact-breakpoint 的较少请求发现，
必要时再为已选调用建立有界 thread-filtered exit；尚未实施或运行。
这是可以减少的事件暴露，不是已测得的启动停滞原因。新 BCI、异常调用上下文
清理、实际成本和有用覆盖仍未知，不能只删 request 而遗留转换状态。

## 封存、记录与剩余项

所有读取者明确释放 source/HEAD/output 后，一次累计预检观察 3856 项（含祖先），
低于冻结的 50000 项上限，清理五个新建的 build/cache/temp 目录。清理命令退出 0，
完整流和零退役进程的原始回执保留。原始世界、旧拒绝清理目标、其他代理工作及
旧封存包均未动，已提交源码工作树保留。清理和 Root 记录晚于上述两个输入截止，
不声称那两项独立审核已覆盖后续操作或封存。

封存清单列 108 个文件、908824 字节，另有 10740 字节清单；逐项哈希复核通过。
`SHA256SUMS.txt` 的 SHA-256 为
`e6287b8758b59ab7b2fe8c88de6de5566786023e89a8e8346ffec6384e33c92a`，
Root `REPORT.md` 为
`d627332f3f4c1b91cbd1b240cee1a5194f682e85cec60bd90643aae33c19923c`。
清单只引用包内保留文件，不引用已删除 build 或原事故包。

Main 写入前仍为 149 行 porcelain、空 index 和原 HEAD；两个受保护所有者文件
哈希不变。共享实施日志不接手，本次只新增此记录及更新两处存档状态引用。
一次大型状态组合输出被截断，之后精确读取存档片段；不把截断显示当作完整审核。

[此前根 map 定位](ROOT-KEY-ITERATOR-AUDIT-05.md)的条件和边界不变；实际修改者、
存储分支、对象和线程仍未关联。后续先冻结并独立审核具体的较少请求采集方案，
再另行界定原生执行。新托管 run38084795112 的原始日志仍未取得，不推断其数量
或原因。R-021、因果调查及 G0–G9 全部开放；v1.8 保持 IN_PROGRESS / IMPLEMENTING。
