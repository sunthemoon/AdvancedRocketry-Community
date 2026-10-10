# v1.8.0：原生存档包装器的只读审核

日期：2026-10-11，Asia/Taipei。状态：`LOCAL_IMPLEMENTATION_OBSERVED_RUNTIME_CAUSE_OPEN`。
本审核未启动 JVM/Gradle、未实现实验、未修改产品，不接受任何存档恢复或性能 Gate。

## 身份和证据边界

独立审核从 Main `474fe142`、实验 `23729a00`、watcher `37229ce7` 的提交读取具名源码，
以及本地 Forge 47.4.10 / Minecraft 1.20.1 official-mapped 开发包中的指定 class 成员。
相邻输入记录与 userdev 配置用于核对版本；不是重新验证远程包真实性或整个 JAR 哈希。
47.4.23 中选定的 ChunkStorage 方法元数据/Code 字节相同，不代表整个包相同。
没有导出、复制原生实现进仓库，也没有启动 Java、javap、反编译器或执行归档。

读取截止 `2026-10-10T18:12:48.9348535Z`。独立报告 SHA-256 为
`6dd19140e245c914e8395a8c0dbe68ada98e9874a18533cc0ec5ff4ff4f33a98`。
报告、派生方法事实和版本/计账记录保存在
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-wrapper-ownership-20261011-01`，
清单 SHA-256 为 `150f23868d521fbb744aefb345b1ede6161c0c5ad2fa5ca18bf2a55f230c9838`。
归档元数据、具名成员及重复读取均有上限；总内容计账 1770791 字节，最大具名项
174919 字节。解析器错误、编码/显示截断及其另行纠正均保留，不替换成成功命令。

## 实际实现观察

- 公共构造器接收 Path、DataFixer 和 boolean，并创建自己的 IOWorker。
- `read(ChunkPos)` 直接返回 worker 的 load future；`write(ChunkPos, CompoundTag)`
  直接提交传入根对象并丢弃 store future。这两层方法没有新增防御性复制。
- worker 的 pending 路径返回 PendingStore.data 引用；另一读取分支来自 RegionFileStorage。
  这是实际版本化实现的静态事实，不是某次托管调用已经采取 pending 分支的证明。
- `injectDatafixingContext` 在传入根对象加入 `__context`；`upgradeChunkTag` 对自己的
  tag 引用进行上下文注入、真实 DataFixTypes 转换，并从结果移除上下文。旧版本转换
  可能先替换局部引用，最终身份取决于真实 DataFixer 与输入，不能宣称所有 schema 相同。
- `flushWorker` 与 native close 内有 join，没有 API 级期限；写入返回不确认保存完成。
- 选定 ChunkMap.save 实现先序列化、派发 Save 事件，再把其局部根对象提交给 write。
  watcher 本身只读取区块/tick，不持有 event.getData()，不是已确认的 NBT 修改者。

上述事实分别说明引用交接和可变操作；尚未观察到任何实际调用者将 pending 引用交给
转换、转换与写入同时进行、特定区块/维度对应关系或 hosted CME 的因果关系。
不能据此给所有保存对象加复制、隔离数据、忽略异常，或引入 AT/ASM。

## 最小可行后续验证

原 IOWorker 实验没有覆盖公共包装器或转换。另立有界、提交后执行的实验可分别检查：

1. 在独占临时存储目录中使用真实初始化的 DataFixer、公共包装器及固定小样本，
   记录首次/flush 后/reopen 的身份和值；提交后不修改输入，不强迫调度或假定必然走某分支。
2. 只对独占、尚未发布给 I/O 的有效输入检查上下文/转换的身份与变化，
   使用正常运行时的 ResourceKey、真实 DimensionDataStorage supplier 和适当 generator key。

正常 Forge 服务端可提供 getFixerUpper；plain JUnit 的既有反射 bootstrap 并不证明新转换
初始化正确。内部 join 必须由隔离进程期限覆盖，保留原始失败。重新打开有记录不等于
断电/崩溃耐久性。任何调用者/并发归因仍需另立版本化证据，R-021 与所有 Gate 继续开放。
