# PORTING_MATRIX — 功能搬运矩阵

> 状态值：`NOT_AUDITED / AUDITED / PLANNED / IN_PROGRESS / BLOCKED / PASSED / DEFERRED / REJECTED`

| 领域 | 1.12 主要位置 | 旧依赖/风险 | 1.20.1 目标 | 目标版本 | 最低验收 | 状态 |
|---|---|---|---|---|---|---|
| 仓库与授权 | 根 LICENSE/README | 名称、原 notice | LICENSE/NOTICE/UPSTREAM/provenance | `v0.0.1` | G0 | PASSED |
| Forge 初始化 | `build.gradle`、`src/main/java/zmaster587/advancedRocketry/AdvancedRocketry.java` | 旧 ForgeGradle/Java、集中初始化 | Java 17、Forge 47.4.10、CI | `v0.0.2` | G1/G4 | PASSED |
| 注册系统 | `AdvancedRocketry.java:474–875`、`init/AdvancedRocketryBlocks.java`、`init/AdvancedRocketryItems.java` | `RegistryEvent`、`GameRegistry`、LibVulpes 注册和数字时代名称 | `registry/ModBlocks.java`、`ModItems.java`、`ModSounds.java`、`ModCreativeTabs.java` 的 DeferredRegister/RegistryObject | `v0.1.0` | build + 3 registry/content GameTests | PASSED |
| 语言 | `assets/advancedrocketry/lang/en_US.lang`、`zh_CN.lang` | `.lang`、旧键名和旧 namespace | DataGen `assets/advancedrocketrycommunity/lang/en_us.json`、`zh_cn.json` | `v0.1.0` | JSON/key audit + 双语言客户端截图 | PASSED |
| 方块/物品纹理 | provenance 清单中的 `textures/blocks/{machinewarning,machinestorage,machinevent}.png` 与 `textures/items/{siliconwafer,basiccircuit,advancedcircuit,datastorageunit}.png` | 路径复数、大小写、来源 | 单数目录、新 namespace、逐文件 source/target hash | `v0.1.0` | 37-resource validator + client no-missing-texture review | PASSED |
| OBJ/MTL 模型 | `assets/advancedrocketry/models/**/*.obj`、`**/*.mtl`（43 OBJ / 20 MTL） | loader、引用、性能 | 复杂模型按后续垂直切片逐个审计；v0.1.0 仅用 JSON 模型 | 分批 | visual + ref validation | DEFERRED |
| 声音 | `assets/advancedrocketry/sounds/buttonblipa.ogg`、`sounds.json` | 来源、旧事件 ID | `advancedrocketrycommunity:ui_select` + DeferredRegister/DataGen | `v0.1.0+` | OGG header/hash + packaged-client interaction | PASSED |
| 普通配方 | `assets/advancedrocketry/recipes/*.json`（157 条已索引） | 旧格式、内容规模 | 当前五个最小配方由 DataGen 生成，其余按版本引入 | `v0.1.0+` | runData clean + JSON/reference audit | PASSED |
| 基础机器 | `tile/multiblock/machine/TileElectrolyser.java`、`recipe/RecipeElectrolyser.java`、`recipes/hydrogenoxygen.json` | 巨型 LibVulpes 多方块基类、隐式配方/能力状态 | 单方块 Electrolyzer；纯 Java tick model + 具体 BlockEntity/Menu/Screen | `v0.2.0` | item/fluid/FE process + 50-cycle conservation + restart/automation | PASSED |
| 多方块 | `tile/multiblock/machine/*` + LibVulpes | 结构匹配、区块、端口和生命周期由外部基类隐式承载 | bounded `MultiblockPattern` + controller/part binding；[v1.2.0 审计](work/v1.2.0-machine-audit.md) | `v1.2.0` | rotation/mirror/diagnostic/unloaded/restart | PLANNED |
| 天体定义 | dimension/api/XML | 数字维度 ID、静态 manager | Codec + datapack + SavedData | `v0.3.0` | roundtrip/cycle validation | PASSED |
| XML 行星 | Template.xml / XML reader | DOM 耦合 | import-only adapter | `v0.3.0` | fixture conversion | PASSED |
| 月球维度 | dimension/world/client | 动态维度、天空 | fixed Moon Level + profile | `v0.3.0` | dedicated reload | PASSED |
| 空间维度 | stations/dimension | station/level 耦合 | shared Space Level | `v0.3.0` | safe teleport | PASSED |
| 重力 | dimension/entity/event | 全局事件、兼容 | server attribute/effect service | `v0.3.0` | player/entity behavior | PASSED |
| 真空伤害 | atmosphere/armor/event | 装备同步 | life support service | `v0.4.0` | suit/no-suit tests | PASSED |
| 氧气设备 | atmosphere/tile | flood fill | budgeted atmosphere service | `v0.4.0` | sealed/open/perf | PASSED |
| 火箭扫描 | tile assembler/entity | 任意结构、LibVulpes storage | validator + snapshot | `v0.5.0` | limits and diagnostics | PASSED |
| 火箭组装 | entity/tile | 删除/生成非事务 | assembly transaction | `v0.5.0` | rollback/no duplication | PASSED |
| 火箭实体 | EntityRocket | 巨型类、渲染/业务混合 | thin entity + domain state | `v0.5.0` | same-dimension lifecycle | PASSED |
| 火箭燃料 | entity/tile/item | 多系统耦合 | RocketFuelState + loaders | `v0.6.0` | consume exactly once | PASSED |
| 目的地选择 | GUI/network/dimension | 客户端信任 | server-validated plan | `v0.6.0` | forged request rejected | PASSED |
| 跨维度飞行 | EntityRocket/dimension | 玩家卡空中、双实体 | transfer journal | `v0.6.0` | restart matrix/20 trips | PASSED |
| 降落/拆解 | entity/world storage | 方块/库存丢失 | landing + disassembly transaction | `v0.6.0` | exact restoration | PASSED |
| 空间站 | stations/dimension | 每站维度/ID | shared regions + SavedData | `v0.7.0` | no overlap/ownership | PASSED |
| 站点重力/光照 | stations/client | 渲染/逻辑耦合 | profile/state separation | `v0.7.x+` | reload + visual | NOT_AUDITED |
| 研究数据 | unit/item/machine | 旧 GUI/数值 | progression service | `v0.8.0` | deterministic persistence | PASSED |
| 卫星 | satellite/mission | chunk load、计时 | SavedData async mission | `v0.8.0` | no forced chunks | PASSED |
| 多类型卫星与组装 | `tile/satellite/TileSatelliteBuilder.java`、`api/SatelliteRegistry.java`、`satellite/*`、`TileMicrowaveReciever.java` | 静态注册表、每 tick 卫星对象、客户端坐标扫描 | 组件目录、蓝图、卫星组装机、有界扫描与太阳能接收器（ADR-049） | `v1.6.0` | C7 A0/A1 + C9 S1 | IN_PROGRESS |
| 资源任务（小行星/气态巨行星） | `mission/*`、`util/Asteroid.java`、`tile/multiblock/TileObservatory.java` | 火箭托管、`Math.random`、强制加载维度 | 逻辑任务、无坐标实例、绑定终端交付、SplitMix64 可重现奖励（ADR-050/051/052） | `v1.6.0` | C8 A0/A1 + C9 S1/S2 与 500 任务 | PLANNED |
| 火箭卫星舱部署 | `EntityRocket.unpackSatellites`、satellite bay | 火箭携带卫星物品 | v1.6 仅终端逻辑发射（ADR-049 处置） | `v1.8.0` | 独立 ADR | DEFERRED |
| 物理小行星场/天文台/火箭采矿 | `world/ChunkProviderAsteroids.java`、`TileObservatory.java`、`EntityStationDeployedRocket.java` | 动态维度、火箭托管 | v1.6 使用逻辑实例（ADR-051 处置） | `v1.8.0` | 独立 ADR | DEFERRED |
| 生物群系改造卫星 | `satellite/SatelliteBiomeChanger.java` | 全局世界修改 | v1.6 拒绝，随地球化复议（ADR-049） | `v1.x+` | 地球化计划 | DEFERRED |
| 间谍望远镜卫星 | `satellite/SatelliteSpyTelescope.java` | 远程视图 | v1.6 不恢复（ADR-049） | `v1.8.0` | 矩阵复议 | DEFERRED |
| 卫星任务星图叠加 | 星图 GUI | 客户端负载 | v1.6 终端沿用星图天体数据，无叠加层（ADR-049 §10） | `v1.8.0` | UI 批次 ADR | DEFERRED |
| JEI | integration | API 版本 | optional compat | `v0.2.0+` | absent/present startup | PASSED |
| ASM/coremod | asm | 高风险、时代 API | 不迁移 | never unless ADR | no coremod | REJECTED |
| 旧世界直开 | backwardCompat/dimension | ID/格式跨度巨大 | 不属于 v1.0 | `v1.x` research | offline conversion only | DEFERRED |
| 跃迁/多星系 | stations/dimension | 动态天体复杂 | 逻辑轨道重定位（ADR-044）、恒星系即根天体树（ADR-043） | `v1.5.0` | WARP/STAR 证据 + ACC | IN_PROGRESS |
| 空间站/跃迁控制 | stations GUI | 客户端界面与协议 | 服务端命令（ADR-046），无新数据包（四通道固定） | `v1.5.0` | UI-03 A1 矩阵 345/345；S1 待 C3，V2 待 ACC-02 | IN_PROGRESS |
| 空间站/跃迁控制界面 | stations GUI | 需版本化负载 | 屏幕推迟（ADR-046 处置，最晚 v1.8.0 复议） | `v1.8.0` | 独立 ADR + 有界负载 | DEFERRED |
| 地球化 | dimension/world | 全局世界修改 | post-MVP | `v1.x+` | future plan | DEFERRED |
| 终局权限/保护/审计框架 | 旧版各终局设备均无权限检查（审计 §6） | 客户端包、强制加载、跨存储复制 | 统一开关、权限、保护链（含 `EndgameEffectEvent` API 1.8）、速率、能量、端点、运输账本与审计（ADR-054，提案） | `v1.7.0` | C11 A0/A1 + C13 S1/S2 | PLANNED |
| 轨道激光钻（逻辑模式） | `tile/multiblock/orbitallaserdrill/TileOrbitalLaserDrill.java`、`VoidDrill.java` | 无种子 Random、客户端坐标与运行状态、强制加载站点区块 | 站点多方块，按天体数据表与 SplitMix64 产出，付费与产出同在控制器（ADR-055） | `v1.7.0` | C11 A0/A1 + C13 S1 | PLANNED |
| 轨道激光钻（实体挖掘模式） | `MiningDrill.java`、`entity/EntityLaserNode.java` | 强制加载客户端坐标区块、仅保留末块掉落、后台线程改世界 | 默认关闭；拥有者放置的目标信标、同区块 3×3 井、整层原子、保护链与计数器结算（ADR-055） | `v1.7.0` | C11 A1 + C13 S2 | PLANNED |
| 激光钻线性/螺旋步进 | `TileOrbitalLaserDrill.java` 的 MODE | 客户端步进 | 推迟到 v1.8 UI 批次（ADR-055） | `v1.8.0` | UI 批次 ADR | DEFERRED |
| 轨道炮（货运） | `tile/multiblock/TileRailgun.java` | 永久强制加载、跨维度单 tick 移动、链接器坐标 | 同拥有者端点间经运输账本的货运，同一星系（ADR-054 §11、ADR-056） | `v1.7.0` | C12 A0/A1 + C13 S2 | PLANNED |
| 轨道打击/武器化轨道炮 | — | 破坏与 grief | 安全范围之外，拒绝（ADR-056） | never unless ADR | — | REJECTED |
| 跨星系货运 | `TileRailgun.java` | 跨系统路线 | 推迟（ADR-056，与火箭路线一致） | `v1.8.0`+ | 独立 ADR | DEFERRED |
| 黑洞发电机 | `tile/multiblock/energy/TileBlackHoleGenerator.java` | 恒星黑洞标志、任意物品燃料 | 独立奇点数据与示例黑洞系、燃料表、满时暂停、同一方块实体（ADR-057） | `v1.7.0` | C12 A0/A1 + C13 S1 | PLANNED |
| 黑洞天空渲染 | `client/render/multiblocks/RenderBlackHoleGenerator.java` 等 | 渲染负载 | 推迟到 v1.8 视觉批次（ADR-057） | `v1.8.0` | 视觉批次 | DEFERRED |
| 区域重力控制器 | `tile/multiblock/TileAreaGravityController.java`、`util/GravityHandler.java` | 每 tick 全实体扫描、推力、静态 WeakHashMap | 玩家重力属性场（0.10–2.00 g，半径 2–16）与分区块索引（ADR-058） | `v1.7.0` | C11 A0/A1 | PLANNED |
| 站点重力控制器方块 | `tile/station/TileStationGravityController.java` | 第二条站点写入路径 | v1.5 命令保留；方块推迟到 v1.8 UI 批次（ADR-058、ADR-046） | `v1.8.0` | UI 批次 ADR | DEFERRED |
| 重力场作用于非玩家实体与旧重力 API | `GravityHandler.java`、`api/IGravityManager.java` | 实体扫描、静态状态 | 推迟到 v1.8 矩阵（ADR-058） | `v1.8.0` | 矩阵复议 | DEFERRED |
| 定向推拉重力 | `TileAreaGravityController.java` | grief、困住玩家 | 拒绝（ADR-058） | never unless ADR | — | REJECTED |
| 空间电梯 | `tile/multiblock/TileSpaceElevator.java`、`entity/EntityElevatorCapsule.java` | 物流/跨维度、按需初始化维度 | v1.5 端点契约与只读校验（ADR-045）；v1.7 锚点/终端端点、绑定与解绑、经账本货运、倒计时乘客传送（ADR-059） | `v1.5.0`/`v1.7.0` | ELEVATOR 校验 JUnit/GameTest；C12 A0/A1 + C13 S2 | IN_PROGRESS |
| 电梯舱实体与动画 | `entity/EntityElevatorCapsule.java` | 跨维度实体 | 推迟到 v1.8 视觉批次（ADR-059） | `v1.8.0` | 视觉批次 | DEFERRED |
| 电梯芯片 | `item/ItemSpaceElevatorChip.java` | 无界位置列表 | 拒绝，由端点记录替代（ADR-059） | never unless ADR | — | REJECTED |
| 力场投影器 | `tile/TileForceFieldProjector.java` | 方块延伸 | 非终局系统，归 v1.8 矩阵审计（v1.7 审计 §8） | `v1.8.0` | 矩阵审计 | AUDITED |

## 使用规则

v1.0 后的目标版本和最终对等分类见
[后续路线](16-POST-1.0-VERSION-ROADMAP.md)。本表的实施状态不自动等于
`PASSED_EQUIVALENT` 或 `PASSED_REDESIGNED`；对等审计必须保留证据和批准。
2026-09-05 的[核心基线审计](work/v1.0.0-core-baseline-audit.md)将十条滞后的
核心行与已有 v0.5/v0.6/v0.8/v0.9 人工接受记录、实现和测试对应。
这些历史 `PASSED` 不表示 v1.0 已验收，也不扩展旧视觉豁免或经典内容范围。

- 完成上游审计后，把“主要位置”替换成准确类/资产路径；
- 每行必须最终指向自动测试和人工用例；
- 状态不能因“代码存在”直接从 PLANNED 跳到 PASSED；
- 新发现功能需增加行，不要塞进“其他”；
- 被推迟的功能不得在当前版本偷偷实现基础框架。
