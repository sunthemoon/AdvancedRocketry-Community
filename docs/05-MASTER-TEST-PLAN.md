# 05 — Master Test Plan / 总测试方案

## 1. 测试层次

| 层次 | 目标 | 是否自动化 |
|---|---|---|
| Pure Java unit | 数值、状态机、验证、迁移 | 是 |
| Codec/NBT round trip | 数据格式完整性 | 是 |
| Forge GameTest | 方块、机器、多方块、房间、火箭 | 是 |
| Data/asset validation | JSON、模型、纹理、来源 | 是 |
| Dedicated server smoke | 物理端隔离、启动、网络 | 尽量自动 |
| Persistence/restart | 保存、重启、恢复 | 半自动/自动 |
| Multiplayer | 并发、权限、掉线 | 半自动 |
| Client visual/manual | GUI、模型、反馈、流程 | 人工 |
| Performance/soak | tick、内存、网络、长时间稳定 | 半自动 |
| Compatibility | Forge 版本、可选模组、最小冲突集 | 半自动 |

## 2. 通用构建命令

每个版本至少：

```bash
./gradlew clean build
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
```

`runData` 后出现 diff，说明生成内容未提交或生成不稳定，Gate 失败。

`v0.0.1` 尚未建立 Forge/Gradle 工程，以上命令在该版本记录为 `NOT_APPLICABLE`。治理基线改为运行：

```bash
python scripts/validate_repository.py --require-approved-identity
git diff --check
```

自 `v0.0.2` 建立 Gradle Wrapper 后，通用构建命令恢复为必跑项。

## 3. Pure Java 单元测试

优先覆盖：

- 火箭质量、推力、燃料和可达性；
- 飞行状态合法转移；
- 天体父子关系和环检测；
- 轨道/任务时间；
- 数据迁移；
- XML 导入映射；
- 结构限制；
- 权限和范围验证；
- 恢复事务决策。

要求：

- 成功路径；
- 边界值；
- 非法输入；
- 超大输入；
- 缺字段；
- 重复 ID；
- 旧 schema；
- 不可逆状态。

## 4. Codec/NBT 测试

所有持久化对象必须测试：

```text
object → encode → decode → equivalent object
```

以及：

```text
old schema → migration → current schema
```

重点对象：

- `CelestialBodyDefinition`
- `CelestialBodyState`
- `RocketStructureSnapshot`
- `RocketFlightState`
- `RocketTransferJournal`
- `StationState`
- `SatelliteMissionState`

必须测试：

- 缺失字段；
- 未知字段；
- 非法 ResourceLocation；
- 超大列表；
- 损坏 NBT；
- 未来版本；
- 降级加载；
- 默认值。

## 5. GameTest 分类

命名：

```text
<system>_<behavior>_<expected>
```

示例：

```text
machine_valid_recipe_produces_output
machine_restart_preserves_progress
celestial_invalid_parent_rejected
atmosphere_sealed_room_becomes_breathable
atmosphere_open_room_stays_vacuum
rocket_valid_structure_assembles
rocket_failed_spawn_rolls_back
rocket_disassembly_restores_inventory
station_allocator_avoids_overlap
```

每个版本文档列出最低必需测试，不得用一条大测试覆盖全部行为。

## 6. 专用服务端

必须验证：

- 无图形环境启动；
- common 代码不加载客户端类；
- 玩家加入和同步；
- 两玩家同时操作；
- 玩家掉线；
- 区块卸载；
- 世界保存；
- 服务器停止并重启；
- 配置不一致时给出清晰错误；
- 没有可选客户端模组时仍启动。

日志判定：

- 项目来源 `ERROR` 为失败；
- 重复且无法解释的 `WARN` 为失败；
- 已接受警告必须记录到版本 Known Issues。

## 7. 重启矩阵

对持久化功能，在关键状态执行：

```text
save
→ stop server
→ restart
→ assert state
→ continue action
```

火箭 `v0.6.0` 至少在：

```text
ASSEMBLED
FUELED
COUNTDOWN
ASCENT
TRANSIT_PREPARED
DESTINATION_SPAWNED
DESCENT
LANDED
```

分别测试。

## 8. 复制/丢失不变量

操作前后统计：

```text
world blocks
container items
fluid amount
energy where applicable
rocket snapshot contents
passengers
entities
transaction journal
```

除明确消耗（燃料、配方材料）外：

```text
before = after
```

任何“偶尔多一份/少一份”都是阻断级问题。

## 9. 网络与恶意输入

至少测试：

- 远距离请求；
- 未加载区块坐标；
- 不存在对象；
- 错误 BlockEntity 类型；
- 超长字符串；
- 超大列表/NBT；
- 重放请求；
- 状态机不匹配；
- 无权限玩家；
- 高频请求；
- 客户端谎报燃料/质量/目的地。

预期：

- 安全拒绝；
- 不加载区块；
- 不崩服；
- 不修改世界；
- 记录有限且不刷屏的诊断。

## 10. 性能测试

### 大气

- 节点访问受 `maxAtmosphereNodesPerTick` 限制；
- 16 个活跃 vent、每房间不超过 4096 方块时，服务端持续运行；
- 打开墙体后不进行无界同步重扫；
- 未加载区块不被强制加载。

### 火箭

- 最大允许结构的扫描和快照不会冻结服务器到 watchdog；
- 渲染缓存只在结构变化时重建；
- 同步数据分块或压缩，不发送到无关玩家；
- NBT 大小在限制内。

### 空间站/卫星

- 不因任务计时强制加载区块；
- 100 个离线任务不逐 tick 扫描全部世界；
- 区域索引查询有边界。

性能报告必须写明：

- CPU、内存、JVM；
- Forge 和模组版本；
- 测试世界；
- 实体/方块数量；
- 采样时长；
- 平均、P95、最大 tick；
- 分配/GC 观察；
- 结论和预算。

## 11. 人工验收

人工测试不是随意游玩。每个用例必须有：

```text
ID
前置条件
步骤
预期
实际
截图/录像
测试人
日期
构建哈希
```

视觉验收包括：

- 无缺失纹理；
- GUI 缩放正常；
- 错误原因可读；
- 模型方向和碰撞合理；
- 发射/着陆状态可理解；
- 专服两名玩家看到一致状态。

## 12. Forge 版本矩阵

发布基线：

```text
Forge 47.4.10
```

兼容通道：

```text
Forge 47.4.23
```

Gate 规则：

- 基线失败：阻断发布；
- 最新通道失败：必须调查并记录；若是 Forge 回归可暂时标记不支持，但需证据；
- 不在未评估的 Forge 版本上自动改变发布基线。

## 13. 缺陷严重度

| 级别 | 示例 | 发布影响 |
|---|---|---|
| Critical | 复制、存档损坏、远程崩服、火箭永久丢失 | 阻断 |
| High | 核心闭环不可完成、多人状态严重不同步 | 阻断 |
| Medium | 次要机器/GUI 功能异常，有绕过 | Beta 可记录，1.0 通常阻断 |
| Low | 文案、轻微视觉、非核心体验 | 可记录发布 |

## 14. 测试证据不可伪造

不接受：

- 未实际运行却填写 PASS；
- 用“理论上可用”代替专服测试；
- 删除失败用例；
- 把断言改为仅打印日志；
- 用无限超时掩盖死锁；
- 用人工修改世界恢复结果；
- 只保留最后一段日志而无构建哈希。
