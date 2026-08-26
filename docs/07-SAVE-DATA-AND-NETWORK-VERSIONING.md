# 07 — Save Data and Network Versioning / 存档与网络版本

## 1. 版本必须分离

不要把模组版本直接当作数据版本。

```java
MOD_VERSION
WORLD_SCHEMA_VERSION
CELESTIAL_SCHEMA_VERSION
ROCKET_SNAPSHOT_VERSION
ROCKET_TRANSACTION_VERSION
STATION_SCHEMA_VERSION
SATELLITE_SCHEMA_VERSION
NETWORK_PROTOCOL_VERSION
```

初始值建议：

```text
WORLD_SCHEMA_VERSION = 1
CELESTIAL_SCHEMA_VERSION = 1
ROCKET_SNAPSHOT_VERSION = 1
ROCKET_TRANSACTION_VERSION = 1
STATION_SCHEMA_VERSION = 1
SATELLITE_SCHEMA_VERSION = 1
NETWORK_PROTOCOL_VERSION = "1"
```

## 2. 持久化位置

跨维度全局状态：

```text
Overworld SavedData
```

适合：

- 天体发现状态；
- 空间站注册表；
- 火箭跨维度恢复 journal；
- 卫星任务；
- 区域分配器。

单维度局部状态：

- Atmosphere volume/index；
- 局部缓存；
- 维度特定运行状态。

BlockEntity：

- 仅保存自身必要状态；
- 不复制全局注册表；
- 不把大型火箭快照塞进多个副本。

## 3. 迁移链

只允许逐步迁移：

```text
v1 → v2 → v3
```

禁止长期维护每个旧版本到最新版本的直接转换。

每个 migration：

- 纯函数优先；
- 有单元测试；
- 原始数据先备份；
- 失败不覆盖；
- 日志说明旧/新版本；
- 迁移完成后标记 dirty。

## 4. 不支持降级

默认不支持新版本存档回到旧版本。

检测到未来 schema：

- 停止加载相关数据；
- 给出可读错误；
- 不用默认值覆盖；
- 指示恢复备份或升级模组。

## 5. 火箭结构格式

必须包含：

```text
schemaVersion
snapshotId
sourceDimension
sourceOrigin
boundingBox
blockPalette
relativeBlocks
approvedBlockEntityData
passengerAnchors
massInputs
createdAtGameTime
contentHash
```

限制：

- 方块数；
- 包围盒体积；
- BlockEntity 数；
- 单 BE NBT；
- 总 NBT；
- palette 大小；
- 坐标范围；
- 压缩后/解压后大小。

## 6. 恢复 journal

跨维度和组装事务记录：

```text
transactionId
type
phase
sourceRef
destinationRef
snapshotRef
passengerUuids
startedAt
lastUpdatedAt
checksum
```

事务 phase 必须幂等。重启恢复不得简单“再执行一次全部流程”。

## 7. 网络策略

`SimpleChannel` 协议只表达客户端/服务端包格式兼容，不等于世界 schema。

建议：

```text
protocol = "1"
accept exact match during pre-alpha
```

后续可允许同一 minor 协议兼容，但需要明确包能力协商。

## 8. 包大小

- C2S 请求只传意图和最小参数；
- 客户端不得提交完整火箭/站点 NBT；
- S2C 大快照应分块、压缩、校验和；
- 接收端先验证声明长度；
- 解压后大小也必须限制；
- 只同步给跟踪玩家或实际需要的界面。

## 9. 数据同步模型

区分：

```text
definition snapshot
runtime state
visual state
```

例如火箭：

- 结构定义：组装后一次或变化时同步；
- 飞行状态：低频或变化同步；
- 插值位置：使用实体同步；
- 库存：只在菜单权限范围内同步；
- 恢复 journal：服务端专用，永不发客户端。

## 10. 1.12 世界兼容

`v1.0.0` 不承诺直接加载 1.12.2 世界。

可提供的兼容层优先级：

1. `planetDefs.xml` 导入；
2. 配置/数值转换；
3. 独立离线资产/数据工具；
4. 选择性结构导入；
5. 最后才考虑世界级转换。

不得让旧世界兼容阻塞核心重写。

## 11. 备份要求

首次加载新 schema 前：

- 检测版本；
- 推荐/执行世界数据备份；
- 备份文件名包含时间和旧 schema；
- 限制备份数量；
- 不在失败后删除旧备份。

Beta 和 stable 发布说明必须写明升级前备份。
