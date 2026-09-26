# 机器内核接入指南

本页面向维护仓库内机器实现的开发者，适用于 Java 17 / Forge 1.20.1。
这些类型是内部接口，不是第三方模组的稳定 API。存档与资源归属以
[ADR-016](decisions/ADR-016-MACHINE-MULTIBLOCK-KERNEL.md) 和
[ADR-019](decisions/ADR-019-PRECISION-RESOURCE-OWNERSHIP.md) 为准。

## 选择参考实现

| 实现 | 适合参考的能力 | 必须保留的差异 |
|---|---|---|
| [Electrolyzer](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/electrolyzer/) | 单方块、Item/Fluid/Energy、已有世界升级 | 保留旧 `arce_machine` schema 1 输入和已支付进度；不需要多方块管理器 |
| [Rolling Machine](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/rolling/) | 普通多方块、单 Item 输入/输出、水和能源端口 | 资源位于物理端口；结构 x/y/z 为 5/3/2，支持四朝向、不启用镜像 |
| [Precision Assembler](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/precision/) | 多输入/输出、固定槽位编号、集中 Item 持久化 | 结构 x/y/z 为 3/3/4，支持四朝向和镜像；五输入、两输出、一个能源口 |

## 从定义到一次处理

1. 由机器的有界配方 decoder 构造
   [`ProcessDefinition`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/process/ProcessDefinition.java)。
   定义包含稳定 ID、输入/输出通道、时长和每 tick 能耗；不要把方块扫描或 GUI 对象放进定义。
2. 将真实存储转换成
   [`ProcessResourceSnapshot`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/process/ProcessResourceSnapshot.java)，
   包含 revision、数量和容量。空输出也要提供容量，不能只列非空槽。
3. 调用
   [`ProcessMachineLogic.simulate`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/process/ProcessMachineLogic.java)。
   成功得到不可变的 before/after 计划；失败返回稳定原因。模拟不修改原资源。
4. 服务端适配器检查成型、配方身份、可用资源、红石与能量，调用同类的 `tick`。
   返回值只描述进度和本 tick 消耗，实际扣 FE 和标脏由适配器完成。
5. 完成时通过
   [`ProcessTransactionExecutor`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/process/ProcessTransactionExecutor.java)
   与机器的 `ProcessResourceStore` / `ProcessJournalStore` 提交或恢复。
   资源变化必须匹配计划的 revision、指纹和完整快照；不能从客户端接受产出或完成进度。

最小可执行 Java 示例是
[`ProcessMachineLogicTest.validSimulationReturnsExactImmutablePlanWithoutMutatingInput`](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/machine/process/ProcessMachineLogicTest.java)：
它从定义和快照得到计划，并检查输入快照未变、输出和 revision 正确。
事务调用及重复恢复示例见
[`ProcessTransactionExecutorTest`](../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/machine/process/ProcessTransactionExecutorTest.java)。
这两个例子不需要启动 Minecraft。

## 配方与结构样例

直接阅读已由运行时使用的数据，避免另维护一份会漂移的示意格式：

- [Rolling iron bars](../src/generated/v1.2/resources/data/advancedrocketrycommunity/recipes/rolling_iron_bars.json)：
  2 铁锭、100 mB 水，100 tick × 20 FE/t，产出 8 铁栏杆。
- [Precision control circuit](../src/generated/v1.2/resources/data/advancedrocketrycommunity/recipes/precision_control_circuit.json)：
  输入 0 为 2 铁锭，输入 1 为 2 红石；20 tick × 40 FE/t，输出 1 电路和 2 红石火把。
- [Precision guidance module](../src/generated/v1.2/resources/data/advancedrocketrycommunity/recipes/precision_guidance_module.json)：
  五输入/单输出格式；输入数组顺序对应固定本地槽，不按放置顺序重新编号。
- [Rolling pattern](../src/main/resources/data/advancedrocketrycommunity/machine_patterns/rolling_machine.json) 与
  [Precision pattern](../src/main/resources/data/advancedrocketrycommunity/machine_patterns/precision_assembler.json)：
  schema、尺寸、controller anchor、旋转、镜像和 cell matcher 的完整样例。

修改内置配方时改 DataGen provider，而不是直接编辑 `src/generated`。
新 datapack 配方使用自己的稳定资源 ID；若覆盖现有配方，保持 serializer 支持的字段和限制。
Precision 配方接受 2–5 个定槽输入和 1–2 个输出，不支持任意 Item NBT 输出。
pattern 数据能配置匹配，但不能单独注册一台新机器；BE、端口布局和角色解析仍需适配。

## 端口与多方块生命周期

- 用 [`ProcessPortDefinition`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/port/ProcessPortDefinition.java)
  定义方向、模式和过滤，由 `ProcessPortPolicy` 校验定义集合；通过
  [Forge wrappers](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/port/forge/)
  连接实际资源及动态 `processLocked` / `accessAllowed` supplier。
  失效时同时退休 optional 和已解析的旧 handler，不能让旧引用在重新成型后恢复访问。
- 用 [`MultiblockPatternValidator`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/multiblock/pattern/MultiblockPatternValidator.java)
  和 [`MultiblockLifecycleCoordinator`](../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/multiblock/lifecycle/MultiblockLifecycleCoordinator.java)
  处理有界匹配与 binding。世界适配器只看已加载区块；未知结构保持 `WAITING_UNLOADED`，不清库存。
- 机器 manager 负责服务器生命周期、事件入队和有预算的重验证/处理；不要把同一服务做成无生命周期的静态集合。
  由结构、区块加载及 pattern 重载通知触发有预算的重验证，不每 tick 重扫整台机器。
- 注册、保存、网络与菜单分别接入现有 registry、codec 和服务端权限检查。
  客户端只显示同步摘要；JEI 放在客户端兼容包，不让 common/server 引用 JEI API。

## 持久化边界

共享事务算法本身不提供跨区块磁盘原子性。选择资源所有者后，再决定 journal
与资源如何一起保存；不能把多个 BE 的 `setChanged()` 当成耐久保存屏障。

Precision 的七个 Item 槽与 process/journal 同存控制器，能源仍在物理能源口。
旧 Item 端口通过 PREPARING 快照、保存回读、端口标记和第二次保存回读迁移；
完成后旧端口资源仅为保留影子，不再提供、掉落或重复导入。
迁移未完成或数据不支持时暂停运行并保留原始数据，不能用清空库存修复。
普通拆除钩子受保护，不应把管理员直接替换方块当作安全迁移操作。

Electrolyzer 的单 BE 兼容迁移和 Rolling 的物理端口布局不是上述中央 Item
协议；不得将 Precision 的跨区块 Item 结论自动用于其他机器、Energy、
方块掉落实体或硬件断电。详见[三机器迁移报告](work/v1.2.0-migration/REPRESENTATIVE-MACHINE-MIGRATION.md)。

## 修改后的短检查

从相关 recipe/codec/process 单测开始，再运行 `gradlew clean build`、
`gradlew runData`、`git diff --exit-code`、`gradlew runGameTestServer`。
涉及存档布局的改动还要检查旧输入和同世界重启，保留原始 fixture。
资源校验入口是 `python scripts/validate_v120_machine_resources.py`。

JEI 注册、默认配方显示、催化物与控制台点击入口已通过一次
[限定客户端检查](work/v1.2.0-jei-integration/CLIENT-VERIFICATION.md)。
该检查只覆盖同制品默认数据、en_us、GUI scale 2，不保证服务端独占数据包
重载、其他语言/缩放或完整视觉质量；编译通过本身不能替代可见性验证。
完整视觉、多人和长时负载的执行安排见
[ADR-018](decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md)。
