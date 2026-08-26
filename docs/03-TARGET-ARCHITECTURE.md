# 03 — Target Architecture / 1.20.1 目标架构

## 1. 顶层原则

- 单一 Forge 1.20.1 模组；
- Java 17；
- 首版不拆独立 LibVulpes 替代库；
- 领域模型尽量脱离 Minecraft 生命周期；
- Forge 对象是适配层，不是业务总控；
- 服务端权威；
- 所有持久化和网络格式有版本；
- 所有世界遍历和可变数据有硬上限。

## 2. 推荐包结构

```text
io.github.sunthemoon.advancedrocketrycommunity/
├─ AdvancedRocketryCommunity.java
├─ registry/
├─ config/
├─ api/
├─ model/
├─ validation/
├─ persistence/
├─ celestial/
│  ├─ model/
│  ├─ service/
│  ├─ datapack/
│  └─ legacy/
├─ dimension/
├─ atmosphere/
├─ life_support/
├─ machine/
│  ├─ base/
│  ├─ multiblock/
│  └─ recipe/
├─ rocket/
│  ├─ structure/
│  ├─ assembly/
│  ├─ stats/
│  ├─ flight/
│  ├─ transfer/
│  ├─ entity/
│  └─ persistence/
├─ station/
├─ satellite/
├─ progression/
├─ network/
├─ command/
├─ compat/
└─ client/
   ├─ screen/
   ├─ renderer/
   ├─ sky/
   └─ particle/
```

## 3. 天体系统

### 定义数据

由数据包/Codec 描述“天体是什么”：

```java
record CelestialBodyDefinition(
    ResourceLocation id,
    Optional<ResourceLocation> parentId,
    ResourceKey<Level> levelKey,
    double gravityMultiplier,
    AtmosphereDefinition atmosphere,
    OrbitDefinition orbit,
    ResourceLocation visualProfile
) {}
```

### 世界状态

由 `SavedData` 描述“本世界中发生了什么”：

```java
record CelestialBodyState(
    boolean discovered,
    long firstVisitedGameTime,
    int researchLevel,
    double terraformingProgress
) {}
```

首版固定维度：

```text
minecraft:overworld
advancedrocketrycommunity:moon
advancedrocketrycommunity:space
```

“天体”不等于“独立维度”。后续逻辑天体可以映射到共享维度实例区域。

### XML 兼容

旧 `planetDefs.xml` 只作为导入格式：

```text
XML → Legacy DTO → 规范模型 → 验证 → JSON/Datapack/SavedData
```

运行时系统不得继续依赖 XML DOM。

## 4. 大气系统

```text
AtmosphereService
├─ dirty region queue
├─ scan scheduler
├─ sealed volume index
├─ chunk-section lookup
└─ player breathability query
```

`VolumeScanTask` 必须包含：

- 最大访问节点；
- 每 tick 节点预算；
- 未加载区块策略；
- 最大包围盒；
- 取消/失效机制；
- 明确失败原因。

首版气体模型：

```text
VACUUM
BREATHABLE
```

多气体成分、压力梯度和温度危险后续扩展。

## 5. 火箭系统

### 领域拆分

```text
RocketStructureSnapshot
RocketStructureValidator
RocketAssemblyTransaction
RocketStats
RocketFuelState
RocketFlightPlan
RocketFlightStateMachine
RocketTransferTransaction
RocketRecoveryJournal
RocketEntity
RocketRenderer
```

### 飞行状态

```text
ASSEMBLED
FUELED
COUNTDOWN
ASCENT
TRANSIT
DESCENT
LANDED
DISASSEMBLED
FAILED_RECOVERABLE
```

状态转移必须集中验证，不用多个布尔值隐式组合。

### 组装事务

```text
validate
→ snapshot
→ lock
→ extract
→ spawn entity
→ commit
```

任一步失败：

```text
rollback world blocks
rollback inventories/fluids
remove partial entity
release lock
write diagnostic
```

### BlockEntity 策略

默认：

- 普通 `BlockState` 可移动；
- 原生容器通过受控适配器移动；
- 第三方 BlockEntity 默认拒绝；
- 逐个集成适配器开放；
- 设置单 BE NBT 和总 NBT 上限；
- 禁止命令方块、传送门、区块加载器等危险类型。

## 6. 跨维度转移

由服务端事务处理：

```text
prepare source snapshot
write recovery journal
spawn destination rocket
transfer passengers
commit destination
remove source
close journal
```

崩服后根据事务 ID 判断：

- 源存在、目标不存在：恢复源；
- 源不存在、目标存在：完成目标；
- 两者都存在：按 journal 选择唯一权威并删除副本；
- 两者都不存在：从 journal 重建到安全位置并记录严重错误。

## 7. 机器系统

先做最小内部框架：

```text
MachineBlock
MachineBlockEntity
MachineRecipe
MachineRecipeSerializer
MachineMenu
MachineScreen
MachineDataSync
```

使用 Forge capability：

- `IItemHandler`
- `IFluidHandler`
- `IEnergyStorage`

不要为了未来可能复用而预建完整模块化 GUI 框架。

## 8. 多方块系统

```java
interface MultiblockPattern {
    ValidationResult validate(
        ServerLevel level,
        BlockPos controller,
        Direction facing
    );
}
```

必须支持：

- 旋转；
- 可选镜像；
- 方块或标签匹配；
- 必需/可选端口；
- 明确错误位置；
- 成型与解体；
- 区块未加载时不强制加载；
- 结构大小上限。

## 9. 网络

包分方向：

```text
C2S:
- RequestAssembleRocket
- RequestDisassembleRocket
- RequestLaunch
- SelectDestination
- ConfigureMachine
- CreateStation

S2C:
- SyncCelestialSnapshot
- SyncRocketState
- SyncAtmosphereState
- SyncStationState
- OpenDestinationScreen
- OperationFailure
```

每个 C2S 处理器必须：

- enqueue 到主线程；
- 检查 sender；
- 检查距离和权限；
- 检查区块已加载；
- 检查对象类型；
- 检查状态机；
- 检查频率和数据大小；
- 不直接信任客户端 NBT、燃料、质量或目的地可达性。

## 10. 客户端

- common 代码不引用客户端包；
- 火箭渲染使用缓存后的结构 mesh/baked model；
- 结构不变时不得每帧重新烘焙；
- 天空、行星和 GUI 使用独立客户端注册；
- 专服测试必须验证无客户端类加载。

## 11. 默认安全上限

以下是初始值，后续只能通过基准和 ADR 调整：

```properties
maxRocketBlocks=2048
maxRocketBoundingVolume=32768
maxRocketBlockEntityCount=128
maxRocketBlockEntityNbtBytes=262144
maxRocketTotalNbtBytes=1048576
maxAtmosphereVolume=65536
maxAtmosphereNodesPerTick=2048
maxMultiblockVolume=32768
maxClientRequestBytes=65536
```

客户端请求不得携带完整火箭快照。

## 12. 依赖策略

首版硬依赖：

- Minecraft 1.20.1
- Forge 47.4.x

可选兼容：

- JEI，待 `v0.2.0` 基础机器稳定后接入。

不得在核心逻辑中硬依赖：

- Create；
- Mekanism；
- Curios；
- Patchouli；
- ARLib；
- LibVulpes。

需要时使用独立 compat 包和条件加载。
