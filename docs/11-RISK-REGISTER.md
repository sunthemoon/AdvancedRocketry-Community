# 11 — Risk Register / 风险登记

| ID | 风险 | 概率 | 影响 | 触发信号 | 缓解 | 阻断版本 |
|---|---|---:|---:|---|---|---|
| R-026 | 宇航服工作站拒绝保存超限或非规范根时，无关区块改动可能回退 | 未量化 | 严重 | 保留的 `arce_suit_workstation` 根未通过 `BoundedNbt.fits` 原生写入预检：超过 64 KiB、深度 16 或 1,024 节点；或任意大小、但原生写入无法规范重现，例如元素类型不是 TAG_End 的空列表、超过 65,535 个修改版 UTF 字节的字符串、非规范 NaN 浮点数、元素类型不一致的列表或没有已证明写入方式的标签类型。这类根只来自保留的不受支持或损坏的已加载数据，普通安装或取出不会产生（W06 按 W03 审核 L1 更正） | W03 按 ADR-066 第 2 节复用 `GuardedChunkSaves`，不在 BlockEntity 中抛保存异常。影响整区块；256 个拒存坐标饱和后影响该 ServerLevel 后续地形保存。拒绝持续至 Level 关闭，不能在线清除；保留原始根并阻止普通破坏。保护层不新增日志，现有 EventBus/ChunkMap 每次失败仍可能记录 ERROR，重试日志总量没有上限。备份后离线修复，重启并核对保存及物品守恒；独立存储不保证同步耐久。Root 合入前需独立只读审核，不代表风险或 Gate 已获所有者接受。详见 ADR-066 的 W03 实施说明、`records/W03.md` 及 `records/W03-REVIEW.md` 的 L1 | `v1.8.0` |
| R-001 | 资产或代码来源不清 | 中 | 严重 | 无 commit/hash/license | provenance Gate；先隔离再导入 | 所有 |
| R-002 | 项目被误认官方 | 中 | 高 | README/发布页无声明 | 独立 mod id、重复非官方声明、新 Logo | `v0.0.1` |
| R-003 | LibVulpes 复刻导致范围爆炸 | 高 | 高 | 先写大量抽象无玩家功能 | 只做机器垂直切片；二次复用后再抽取 | `v0.2.0` |
| R-004 | 动态维度在 1.20.1 不稳定 | 高 | 严重 | 尝试运行时注册任意 Level | 固定 Moon/Space；逻辑天体与维度解耦 | `v0.3.0` |
| R-005 | 大气扫描卡服 | 高 | 严重 | 同 tick 大 flood fill | dirty 队列、节点预算、最大体积 | `v0.4.0` |
| R-006 | 火箭组装复制/丢失 | 高 | 严重 | 先删方块后生成失败 | 事务、锁、快照、回滚、计数不变量 | `v0.5.0` |
| R-007 | 第三方 BlockEntity 不可安全移动 | 高 | 高 | 任意复制 BE NBT | 默认拒绝，适配器白名单，大小限制 | `v0.5.0` |
| R-008 | 跨维度崩服造成双火箭/无火箭 | 高 | 严重 | 源/目标切换非原子 | recovery journal + 幂等恢复 | `v0.6.0` |
| R-009 | 乘客掉线后卡死/丢失 | 中 | 严重 | 飞行中断线 | UUID passenger state、安全回归点 | `v0.6.0` |
| R-010 | 客户端伪造请求 | 中 | 严重 | 客户端提交质量/燃料/NBT | 只提交意图；服务端重算和校验 | `v0.5+` |
| R-011 | 火箭结构网络包过大 | 中 | 高 | 完整 NBT 单包发送 | 分块、压缩、限制、跟踪范围 | `v0.5.0` |
| R-012 | 空间站区域重叠 | 中 | 高 | 简单坐标递增无 journal | 区域分配器、占用索引、测试 | `v0.7.0` |
| R-013 | 卫星任务强制区块加载 | 中 | 高 | 每 tick 查实体/方块 | SavedData 任务计时，不依赖活跃区块 | `v0.8.0` |
| R-014 | 存档格式频繁破坏 | 高 | 高 | NBT 无 schema | 独立版本、迁移链、备份 | `v0.3+` |
| R-015 | 专服加载客户端类 | 中 | 高 | `net.minecraft.client` 出现在 common | 分包、专服 smoke、静态扫描 | `v0.0.2+` |
| R-016 | AI 生成代码未经验证 | 高 | 高 | 报告无命令/日志 | AGENTS 输出协议、独立审计会话 | 所有 |
| R-017 | 内容规模压垮核心闭环 | 高 | 高 | 提前导入数百方块/机器 | 版本硬边界、非目标清单 | 所有 |
| R-018 | 兼容最新 Forge 破坏基线 | 低/中 | 中 | 自动升级构建 | 47.4.10 固定基线，最新仅 CI lane | 所有 |
| R-019 | 名称/品牌异议 | 低/中 | 高 | 原维护者联系或用户误认 | 可替换名称与 mod id、沟通记录 | 公开发布 |
| R-020 | 性能测试不可复现 | 中 | 中 | 只写“不卡” | 固定环境、世界、数量、采样时长 | `v0.4+` |
| R-021 | 保存检查拒绝整地形区块；饱和后该 ServerLevel 的后续地形写盘均被拒绝，无关改动可能回退，独立存储可能分歧 | 未量化 | 严重 | 储罐、泵、燃烧发电机及轧制机、精密装配机、电解机的根违反字节、深度、节点或原生无损检查，或输出 BE 列表超过物理上限；太阳能发电机根已按限定决定在开发源码中纳入保护，原生停服与重启验证未完成；记满 256 个不同拒存坐标后，第 257 个使该 Level 饱和；重复坐标不扩容 | 已提交代码的 `GuardedChunkSaves` 在 `ChunkDataEvent.Save` 中抛异常，使 ChunkMap 返回 false；保留脏标记不阻止卸载，也不保证跨存储耐久。拒绝随 Level capability 粘连，无生产在线清除；新 Level 状态重建不等于修复旧数据。历史六次拒存各有 EventBus/ChunkMap ERROR 对及堆栈，保护层没有日志或重试总量上限。详见 [ADR-064 说明修订提案](work/v1.8.0-c16a-save-guard/ADR-064-SAVE-REFUSAL-EXPLANATION-PROPOSAL-01.md)及 [2026-10-06 太阳能限定决定](work/v1.8.0-c17c-solar-generator/OWNER-DECISION-01.md)。原生范围、日志量、备份离线修复及服主处置仍待验证；未接受风险，未选择隔离、重置、丢弃或停服策略 | `v1.8.0` |

### R-021：classic 控制器与 hatch 的实施范围补充（2026-10-12）

本补充登记 classic 控制器（方块实体 `advancedrocketrycommunity:lathe`、`arc_furnace` 与
`cutting_machine`）的 `arce_classic_machine`、
`arce_classic_resources`、`arce_recipe_signature` 与被保留的旧 process/journal/machine
根，以及五种 hatch/plug 的 `arce_classic_hatch` 根。仅**存在**但超过各根字节、深度、
节点上限，或不能由原生 NBT 无损表示的根触发拒存。缺失 hatch 根恢复为空的未绑定
hatch。只有上述六种控制器根全部缺失时，控制器才恢复为新建、未成形控制器；这时没有
可保留的数据，该路径不丢弃任何内容。只缺部分根（例如资源根仍在而机器根缺失，或机器
根仍在而资源根缺失）时，与受限的未知、损坏或未来 schema 一样进入需修复的隔离状态：
现存根原样保存，缺失的根保持缺失，不重置、不丢弃资源，也不拒存。隔离只影响该控制器：
它不运行、不暴露 hatch 能力，控制器及其 hatch 不能被普通拆除；状态持续到管理员离线
修复，重启不解除；隔离本身不写日志，控制器界面显示 REPAIR；恢复按下文流程进行。
（2026-10-12 W01G 更正：此前版本写成缺失任一必需控制器根即恢复为新建控制器，会覆盖
仍然存在的资源根，已取消。）

拒存影响整个地形区块，不限于该对象；无关容器、方块和实体改动可能回退，与独立保存
的玩家库存可能分歧。沿用 256 个不同坐标上限，第 257 个使整个 ServerLevel 的地形
保存被拒绝。状态持续整个 Level 生命周期，没有生产在线清除。每次保存重试仍可能
产生 EventBus/ChunkMap ERROR 与堆栈，重试次数及累计日志量均无总上限。脏标记不能
保证跨存储耐久，重新启动不能修复根；本补充不表示已验证资源守恒或接受剩余风险。

处置时停止玩家操作并停服，保留错误日志、坐标、版本和提交标识，先离线备份完整世界
（包括 region、实体、玩家数据及 SavedData）。只在备份副本中使用可信 NBT 工具检查
上述根；依据最后可信快照修复字节/结构/表示问题，核对控制器 UUID、位置、资源银行、
进度、claim 和 plug FE，未经明确管理员授权不重置或丢弃资源。用修复副本启动专服，
执行保存和正常停服，确认实际 region writer 成功、无拒存及新增相关 ERROR，再重启
核对根、银行、FE、无关容器与玩家库存；验证失败时保持备份并继续离线修复。原生 writer
及重启证据仍待 Root 验证，不能以 GameTest 的内存 NBT 往返替代。

## 风险处理规则

### R-023 — Named landing-pad malformed-root preservation

Landing-pad index/BE roots that fail schema or bounds checks retain their original
data and disable pad operations. An oversized index refuses only its own file
write, keeps the previous file and dirty state, and logs once per loaded instance.
An oversized BE root refuses its whole terrain chunk through `GuardedChunkSaves`
for the Level lifetime; the existing 256-coordinate saturation and unbounded
save-retry log accumulation remain applicable. Probability is unquantified and
the terrain-save impact is High. This blocks v1.8.0 acceptance until native
writer/restart evidence and operator review. Scope, duration and offline backup/
repair procedure are recorded in ADR-065's W13 implementation note. No risk is
accepted or closed by this record.

| ID | 风险 | 概率 | 影响 | 触发信号 | 缓解 | 阻断版本 |
|---|---|---:|---:|---|---|---|
| R-027 | W14 氧气通风口（含 schema-2 洗涤器库存）、洗涤器 facade 与大气探测器的超限或原生非无损根拒绝整区块保存；拒存集合饱和后影响整个 Level | 未量化 | 严重 | 拥有的根超出 64 KiB、深度 16、1,024 节点或不能原生无损保存 | 原始 Tag 保留，停止使用与普通破坏，沿用 `GuardedChunkSaves` 至 Level 结束。既有 256 坐标饱和、脏标记与卸载边界、每次重试 ERROR 对及无日志总量上限均不改变。必须停服备份和离线修复，再验证重启、保存与资源守恒；无玩家文件拒存。详见 ADR-066 的 W14 实施说明，独立持久化审核和风险接受仍未完成 | `v1.8.0` |
| R-022 | 力场或投影器的超限原生根拒绝整区块保存；清理根超限时拒绝该独立文件保存，跨存储可能分歧 | 未量化 | 严重 | W04 原生根超过字节、深度或节点预算 | 原始 Tag 保留并停止功能和普通删除。区块拒存沿用 `GuardedChunkSaves`，持续至 Level 结束，既有饱和与无日志总量上限仍适用。清理文件拒存不抛异常，保留旧文件和脏标记，每次载入最多一条诊断。必须备份、停服并离线修复；重启本身不是修复。范围、恢复方法见 ADR-065 的 W04 实施说明；原生停服、重启与风险接受未完成 | `v1.8.0` |
| R-028 | 行星信标索引根无法解码时，整个服务器的信标数据被隔离：所有信标无法登记，所有探测器收不到标记；单个信标的方块实体根无法解码时，该信标停用 | 未量化 | 中 | `advancedrocketrycommunity_planetary_beacons` 根为未知、未来、畸形或超出 ADR-065 第 7 节上限；或 `arce_planetary_beacon` 根无法解码 | 两者都不拒绝保存、不在序列化中抛异常。索引根能读成 NBT 但无法解码时，按引用保留并原样写回，不删除其中任何记录；隔离持续到离线修复为止，用同一文件重启仍会再次隔离，每次载入记一条 WARN；备份后离线修复 `.dat` 并重启，已加载的信标在下次校验时重新登记。`.dat` 文件本身无法读取（如 gzip 截断）时，原版 `DimensionDataStorage` 记一条 ERROR 并以空索引启动，第一次登记就会覆盖该文件；这是项目范围的原版残余风险，ARCE 的 SavedData 都没有防护，损失是软性的：信标在所在区块加载后重新登记。索引不可用期间被拆除的信标会留下旧记录，其所有者会看到"最后已知"标记；索引恢复后，所有者的探测器在该记录的位置已加载且不含该信标时删除它（W18F）。方块实体根保留并原样写回，该信标保持停用、不登记、不记日志，运维只能逐个使用信标（回复为数据无效）来找到它；它在根失效前写入的记录保留最后的 active 标志，所有者可能看到"最后已知"标记，信标拆除后由探测器按同一规则删除该记录。该信标不持有物品、流体或能量，普通拆除允许，只丢弃无法读取的标识和名称。自 W18F 起，只有创造模式的管理员能通过物品 NBT 写入该根。范围与恢复方法见 ADR-065 的 W18 实施说明；原生停服、重启与风险接受未完成 | `v1.8.0` |

- Critical/High 风险必须有测试或明确的结构性缓解；
- “发生概率低”不能替代安全边界；
- 风险关闭需证据，不因代码存在而自动关闭；
- 每个版本审计更新本表；
- 新风险应分配首次阻断版本；
- 接受风险必须 ADR，并写明到期版本。
