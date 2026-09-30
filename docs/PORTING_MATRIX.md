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
| JEI | integration | API 版本 | optional compat | `v0.2.0+` | absent/present startup | PASSED |
| ASM/coremod | asm | 高风险、时代 API | 不迁移 | never unless ADR | no coremod | REJECTED |
| 旧世界直开 | backwardCompat/dimension | ID/格式跨度巨大 | 不属于 v1.0 | `v1.x` research | offline conversion only | DEFERRED |
| 跃迁/多星系 | stations/dimension | 动态天体复杂 | 逻辑轨道重定位（ADR-044）、恒星系即根天体树（ADR-043） | `v1.5.0` | WARP/STAR 证据 + ACC | IN_PROGRESS |
| 空间站/跃迁控制 | stations GUI | 客户端界面与协议 | 服务端命令（ADR-046），无新数据包（四通道固定） | `v1.5.0` | UI-03 A1 矩阵 345/345；S1 待 C3，V2 待 ACC-02 | IN_PROGRESS |
| 空间站/跃迁控制界面 | stations GUI | 需版本化负载 | 屏幕推迟（ADR-046 处置，最晚 v1.8.0 复议） | `v1.8.0` | 独立 ADR + 有界负载 | DEFERRED |
| 地球化 | dimension/world | 全局世界修改 | post-MVP | `v1.x+` | future plan | DEFERRED |
| 黑洞/轨道激光 | 多处 | 高内容/渲染/兼容 | 终局系统 | `v1.7.0` | v1.7 计划 | PLANNED |
| 空间电梯 | 多处 | 物流/跨维度 | v1.5 端点契约与只读校验（ADR-045，已实现运维检查命令）；v1.7 结构与物流 | `v1.5.0`/`v1.7.0` | ELEVATOR 校验 JUnit/GameTest；v1.7 计划 | IN_PROGRESS |

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
