# PORTING_MATRIX — 功能搬运矩阵

> 状态值：`NOT_AUDITED / AUDITED / PLANNED / IN_PROGRESS / BLOCKED / PASSED / DEFERRED / REJECTED`

| 领域 | 1.12 主要位置 | 旧依赖/风险 | 1.20.1 目标 | 目标版本 | 最低验收 | 状态 |
|---|---|---|---|---|---|---|
| 仓库与授权 | 根 LICENSE/README | 名称、原 notice | LICENSE/NOTICE/UPSTREAM/provenance | `v0.0.1` | G0 | PLANNED |
| Forge 初始化 | build files / mod entry | 旧 ForgeGradle/Java | Java 17、Forge 47.4.10、CI | `v0.0.2` | G1/G4 | PLANNED |
| 注册系统 | block/item/common | 旧注册事件、数字 ID | DeferredRegister/RegistryObject | `v0.1.0` | build + registry tests | NOT_AUDITED |
| 语言 | assets/.../lang | `.lang` | `en_us.json` 等 | `v0.1.0` | JSON + key audit | NOT_AUDITED |
| 方块/物品纹理 | textures/blocks/items | 路径大小写、来源 | 新 namespace + manifest | `v0.1.0` | no missing texture | NOT_AUDITED |
| OBJ/MTL 模型 | models/*.obj/*.mtl | loader、引用、性能 | 复杂模型保留/简单模型转换 | 分批 | visual + ref validation | NOT_AUDITED |
| 声音 | sounds + sounds.json | 来源、ID | 新 sound registry/data | 分批 | decode + client check | NOT_AUDITED |
| 普通配方 | assets recipes | 旧格式 | data recipes/DataGen | `v0.1.0+` | runData clean | NOT_AUDITED |
| 基础机器 | tile/block/recipe + LibVulpes | 巨型基础库耦合 | 最小 machine vertical slice | `v0.2.0` | process/restart/automation | NOT_AUDITED |
| 多方块 | tile + LibVulpes | 结构匹配、区块 | internal MultiblockPattern | `v0.2.0+` | rotation/failure/unloaded | NOT_AUDITED |
| 天体定义 | dimension/api/XML | 数字维度 ID、静态 manager | Codec + datapack + SavedData | `v0.3.0` | roundtrip/cycle validation | NOT_AUDITED |
| XML 行星 | Template.xml / XML reader | DOM 耦合 | import-only adapter | `v0.3.0` | fixture conversion | NOT_AUDITED |
| 月球维度 | dimension/world/client | 动态维度、天空 | fixed Moon Level + profile | `v0.3.0` | dedicated reload | NOT_AUDITED |
| 空间维度 | stations/dimension | station/level 耦合 | shared Space Level | `v0.3.0` | safe teleport | NOT_AUDITED |
| 重力 | dimension/entity/event | 全局事件、兼容 | server attribute/effect service | `v0.3.0` | player/entity behavior | NOT_AUDITED |
| 真空伤害 | atmosphere/armor/event | 装备同步 | life support service | `v0.4.0` | suit/no-suit tests | NOT_AUDITED |
| 氧气设备 | atmosphere/tile | flood fill | budgeted atmosphere service | `v0.4.0` | sealed/open/perf | NOT_AUDITED |
| 火箭扫描 | tile assembler/entity | 任意结构、LibVulpes storage | validator + snapshot | `v0.5.0` | limits and diagnostics | NOT_AUDITED |
| 火箭组装 | entity/tile | 删除/生成非事务 | assembly transaction | `v0.5.0` | rollback/no duplication | NOT_AUDITED |
| 火箭实体 | EntityRocket | 巨型类、渲染/业务混合 | thin entity + domain state | `v0.5.0` | same-dimension lifecycle | NOT_AUDITED |
| 火箭燃料 | entity/tile/item | 多系统耦合 | RocketFuelState + loaders | `v0.6.0` | consume exactly once | NOT_AUDITED |
| 目的地选择 | GUI/network/dimension | 客户端信任 | server-validated plan | `v0.6.0` | forged request rejected | NOT_AUDITED |
| 跨维度飞行 | EntityRocket/dimension | 玩家卡空中、双实体 | transfer journal | `v0.6.0` | restart matrix/20 trips | NOT_AUDITED |
| 降落/拆解 | entity/world storage | 方块/库存丢失 | landing + disassembly transaction | `v0.6.0` | exact restoration | NOT_AUDITED |
| 空间站 | stations/dimension | 每站维度/ID | shared regions + SavedData | `v0.7.0` | no overlap/ownership | NOT_AUDITED |
| 站点重力/光照 | stations/client | 渲染/逻辑耦合 | profile/state separation | `v0.7.x+` | reload + visual | NOT_AUDITED |
| 研究数据 | unit/item/machine | 旧 GUI/数值 | progression service | `v0.8.0` | deterministic persistence | NOT_AUDITED |
| 卫星 | satellite/mission | chunk load、计时 | SavedData async mission | `v0.8.0` | no forced chunks | NOT_AUDITED |
| JEI | integration | API 版本 | optional compat | `v0.2.0+` | absent/present startup | NOT_AUDITED |
| ASM/coremod | asm | 高风险、时代 API | 不迁移 | never unless ADR | no coremod | REJECTED |
| 旧世界直开 | backwardCompat/dimension | ID/格式跨度巨大 | 不属于 v1.0 | `v1.x` research | offline conversion only | DEFERRED |
| 跃迁/多星系 | stations/dimension | 动态天体复杂 | post-MVP | `v1.x` | future plan | DEFERRED |
| 地球化 | dimension/world | 全局世界修改 | post-MVP | `v1.x+` | future plan | DEFERRED |
| 黑洞/空间电梯/轨道激光 | 多处 | 高内容/渲染/兼容 | post-MVP | `v1.x+` | future plan | DEFERRED |

## 使用规则

- 完成上游审计后，把“主要位置”替换成准确类/资产路径；
- 每行必须最终指向自动测试和人工用例；
- 状态不能因“代码存在”直接从 PLANNED 跳到 PASSED；
- 新发现功能需增加行，不要塞进“其他”；
- 被推迟的功能不得在当前版本偷偷实现基础框架。
