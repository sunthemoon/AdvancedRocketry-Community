# v1.8.0：存档根对象的遍历冲突

日期：2026-10-11，Asia/Taipei。状态：`STATIC_ROOT_MAP_LOCALIZED_RUNTIME_ATTRIBUTION_OPEN`。
这次只读调查继续审核运行 38064322640 的原始异常；没有产品修复、JVM 执行、
CI 重跑、日志豁免或 Gate 批准。

## 源码与原始失败

事故源码为 `52a29c1e76977033a1b7e85baf32ca5d19c7414c`，调查现状固定在
`726a5c319fe179c5ff0e28134c72070043027515`。两者之间只有六个 main Java
文件变化，全部是 GameTest/watcher；产品 BlockEntity、实体和保存监听器未改变。
完整原始包仍在 `D:/GitHub/ARCE-Task-Evidence/ci-run-38064322640`，未修改。
Root 重新核对 latest.log 的 SHA-256：
`238daf620bec219143d9f23ba2f90328026dd89695377fd088c1a56df8f68673`。
原生测试通过而严格对账拒绝新增存档异常的结论不变，不把它加入预期清单。

## 新确认的定位边界

新的独立原生审核解析了本地 Minecraft 1.20.1 / Forge 47.4.10 mapped 指定 class
成员，并核对原始日志 2326–2342 行。`CompoundTag.write:133` 遍历自身
`tags.keySet()`；`NbtIo.writeUnnamedTag:139` 直接调用其输入的 `Tag.write`。
栈中只有一个 `CompoundTag.write`，随后直接是 NbtIo、RegionFileStorage 和 IOWorker。
在保留栈完整且托管加载实现与所查成员对应的条件下，失败的是存档最外层 compound
的 key map，而不只是某个 BE/实体的嵌套 compound。没有取得托管加载 class 的摘要，
不能把本地成员对应条件省略。

根对象或根 map 的身份、存储目录、维度、terrain/entity 分支、被改的键、实际修改者
及线程仍未知。嵌套引用共享是另一项风险；它本身不足以解释这个根层迭代器栈。

## 可达的原生修改途径，不是个案归因

独立审核重新确认：pending 读取直接返回 `PendingStore.data`，与待写对象共享根。
普通 `ChunkMap.readChunk` 把该结果交给后台 executor 的转换回调；转换会在当前根
加入 `__context`，随后从结果删除它。对不先替换输入的版本分支，上下文注入就是
一个可达的根层修改者；当键原先不存在时，加入它会改变 key map 的结构。
这些方法之间没有“转换已完成”后才写 pending 对象的屏障。

这是共享对象和具体根修改操作的静态可达证据，不证明事故执行了 pending 分支，
也不证明注入与迭代重叠。转换结果身份、executor 的实际线程和旧 schema 分支不能
一概而论。实体存储也使用同类 worker，但其选定生产方法新建外层根，选定转换路径
没有 terrain 的 `__context` 操作；日志的 IO-Worker 名称不能据此判定存储种类。

兼容 lane 本地 Forge 47.4.23 的选定 `IOWorker.class` 与 47.4.10 逐字节相同，
均为 19314 字节，SHA-256：
`708e7cda93822cf5bf9fb421857613633346ce6b4d5c54f15255618992c3759b`。
这不是整包对比、托管加载证明或升级即可修复的证据。

## 项目回调与夹具

独立事件审核没有在已安装保存/加载监听器及其观察、验证辅助中找到 event NBT
的结构修改。Endgame 异步队列和 terminal 观察保留解析后的标量/集合，而非 event
根对象。ClassicSaveProtection 在所查注册调用中没有安装，不能把其方法存在视为
实际订阅。历史 watcher 生命周期问题不保留 NBT，后续修订也不是 CME 修复。

独立 producer 审核也没有在所查生产者中找到已发布存档根 map 的后续修改。
多项拒绝/无界 BE 保存分支保留并输出嵌套 raw 子对象，仍有引用共享风险；所查
分支没有找到内部后续编辑。这不能替代根层冲突的实际修改者证据，也不批准改变
玩家数据保留或保存拒绝策略。继承的能力序列化及任意外部 provider 不由项目源码
检查证明安全。该报告收到原生 peer 的栈层定位作为跨范围证据，不是盲审原生方法体。

Root 另核对两个相邻夹具：站内重力控制器坐标来自 1024 格网中心加 4，其直接
controller chunk 坐标为 64 的倍数，不是 `[-13,7]`；但玩家加入/传送可能触发其他
区块工作。precision 卸载夹具约在 880000 坐标，`unload/reload` 方法通知领域
formation manager，并不是原生物理卸载/重载。批次标签和夹具名称都不能替代
对象交接证据。

## 独立报告与证据

本次外部包为
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/ci-storage-cause-audit-20261011-04`。
完整报告、读取台账、失败命令与 Root 观察均保留，封存 helper 不执行。

| 独立范围 | UTC 读取截止 | 报告 SHA-256 |
|---|---|---|
| 原生交接、writer 栈、兼容成员 | 2026-10-10T19:57:40.773018Z | `df7b8e2d0e6737c7de9fae61f24d9eca70969d885167506fce0d8cf77218e8a2` |
| 注册、event 回调和测试发布 | 2026-10-10T19:56:19Z | `abf1b9c14a3efeb6c3241456fe35dc05143e829d2b46336fc60b1d2c37328438` |
| BE/实体生产者的引用交接 | 2026-10-10T19:58:31.570086Z | `25164e512f1bc3a8a3c7fbb2cb0a741794f1672ad9bdfcab1badb684ade6082a` |

三份报告内容读取分别计 12000017、21849186 和 22981021 字节，各低于其 32 MiB 上限；
bootstrap 顺序、截断和失败命令保留，不能冒称全程无操作偏差或进程内存资格。
Root 的初始读取采用保守上界而非精确逐次仪表计账，不重新资格验证旧超额任务。

## 验证与剩余工作

只读 git/source/member/log 检查已执行；构建、单测、DataGen、GameTest、专服和重启
这次均未重新运行，先前结果仍只归属原被测提交。没有新增或修改产品测试。
用户原有 149 项工作区状态、空 index 和两个受保护文件哈希在记录写入前不变；
共享实施日志、原始世界、其他进程和旧拒绝清理目标未接手。

后续需要把普通存储分支、根/map 身份和实际修改线程关联起来。Forge Save 事件
没有覆盖 pending 读取及转换内部时段，不能用 event-only 观察或再次绿灯关闭原因。
外部包给出尚未实施的有界 JDI 诊断设计；使用调试器会扰动调度，正样本也不能
倒推原托管执行。实施前仍须冻结具体工具、源 SHA、事件/输出/时间上限与失败清理。
没有引入全量复制、AT/ASM、保存保护或隔离玩家数据作为推测修复。
R-021、因果调查和所有 Required Gate 保持开放，v1.8 仍是 IN_PROGRESS / IMPLEMENTING。
