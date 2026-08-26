# Advanced Rocketry 1.20.1 Community Port — Master Execution Plan

> **用途：** 单文件总览，便于人工审阅或在不能方便读取多文件目录时交给 Codex。
>
> **权威来源：** 同目录的分文件文档包。发生冲突时，以 `PROJECT-CONFIG.md`、`AGENTS.md`、当前版本文件及最新 ADR 为准。
>
> **目标：** Minecraft 1.20.1 / Forge / Java 17，采用分版本 Gate 驱动的社区重写。
>
> **状态：** 规划与执行规约；不包含实际 Forge 模组源码。

## 使用规则

1. 首次执行只处理 `v0.0.1`。
2. Codex 最多把版本推进到 `READY_FOR_AUDIT`，不能自行标记 `PASSED`。
3. 当前版本 Required Gate 未通过，不得进入后续版本主实现。
4. 上游代码或资产导入前必须记录来源、commit、路径、hash、许可证和转换。
5. 本单文件便于阅读；实际落仓时必须保留原始目录结构。

---



---

## Source file: `00-READ-ME-FIRST.md`

## 先读我：如何使用这套文档启动项目

这不是一份“建议清单”，而是一套 **按版本设置完成门槛的执行规约**。推荐把整个目录复制到新仓库根目录，再让 Codex 从 `AGENTS.md` 开始工作。

### 最短使用路径

1. 人工检查并填写 [`PROJECT-CONFIG.md`](PROJECT-CONFIG.md)。
2. 在 GitHub 新建空仓库，暂时不要导入原项目源码。
3. 将本目录全部提交为第一个规划提交。
4. 把 [`codex-prompts/00-initialize-repository.md`](codex-prompts/00-initialize-repository.md) 交给 Codex。
5. Codex 只能完成 `v0.0.1`，不得直接开始 Forge 代码。
6. 使用 [`codex-prompts/03-audit-current-version.md`](codex-prompts/03-audit-current-version.md) 审核该版本。
7. 所有 Required Gate 通过后，才使用 `02-implement-next-version.md` 进入下一版。

### 文档阅读顺序

```text
PROJECT-CONFIG.md
AGENTS.md
PRODUCT.md
docs/01-PORTING-PRINCIPLES.md
docs/04-VERSION-ROADMAP.md
docs/versions/<当前版本>.md
docs/05-MASTER-TEST-PLAN.md
docs/06-RELEASE-AND-ACCEPTANCE-GATES.md
```

涉及具体系统时，再读取：

- 天体、维度、火箭、大气、空间站：`docs/03-TARGET-ARCHITECTURE.md`
- 上游代码和资产：`docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md`
- 存档、NBT、网络协议：`docs/07-SAVE-DATA-AND-NETWORK-VERSIONING.md`
- 资产来源与授权：`docs/08-ASSET-LICENSE-AND-PROVENANCE.md`
- GitHub 设置和公开声明：`docs/09-GITHUB-REPOSITORY-SETUP.md`

### 这套方案刻意避免的错误

- 不从 1.12.2 源码开始逐个修编译错误；
- 不先复刻完整 LibVulpes；
- 不一次导入所有方块、机器和纹理；
- 不把“客户端能进游戏”当作版本完成；
- 不允许没有测试证据就勾选完成；
- 不允许 Codex 为了让测试通过而降低验收标准；
- 不承诺首版直接加载 1.12.2 世界；
- 不在 `v0.6.0` 前扩展卫星、跃迁、采矿等外围内容。

### 预期里程碑

最终 `v1.0.0` 的核心体验是：

> 玩家在地球上建造一枚由真实方块组成的火箭，完成燃料与生命保障准备，安全飞往月球、着陆、返回；服务器在飞行或转移中重启后，火箭、乘客和库存仍可恢复，且不存在复制或丢失。

这条闭环未通过前，项目仍是技术原型，不是可发布的 Advanced Rocketry 社区续作。


---

## Source file: `PROJECT-CONFIG.md`

## PROJECT-CONFIG — 开工前唯一需要人工确认的项目变量

> 状态：`DRAFT / MUST REVIEW`
>
> 使用方法：第一次交给 Codex 前，确认本页；后续代码、文档、构建脚本、发布名均以本页为准。任何身份变量变更都必须同步更新 `README.md`、`NOTICE.md`、`mods.toml`、Maven 坐标和资源命名空间。

### 1. 推荐默认值

| 变量 | 推荐值 | 说明 |
|---|---|---|
| GitHub owner | `sunthemoon` | 可替换为个人账号或新组织 |
| repository | `AdvancedRocketry-Community` | 新建独立仓库，不默认使用 GitHub Fork |
| display name | `Advanced Rocketry: Community Edition` | 暂定公开显示名 |
| short name | `ARCE` | 文档与日志缩写 |
| Minecraft | `1.20.1` | 唯一首发目标 |
| loader | `Forge` | 不同时维护 Fabric/NeoForge |
| Java | `17` | Forge 1.20.1 开发基线 |
| Forge baseline | `47.4.10` | 推荐版本，作为可复现开发/发布基线 |
| Forge latest CI lane | `47.4.23` | 仅作为兼容性测试通道，不自动替换基线 |
| mod id | `advancedrocketrycommunity` | 避免在未沟通前直接占用原项目 `advancedrocketry` |
| legacy namespace | `advancedrocketry` | 只用于审计与资源映射，不直接作为新注册命名空间 |
| Maven group | `io.github.sunthemoon.advancedrocketrycommunity` | GitHub owner 变化时同步修改 |
| Java package root | `io.github.sunthemoon.advancedrocketrycommunity` | 禁止继续使用原 `zmaster587.*` 包名 |
| artifact id | `advancedrocketry-community` | 生成 JAR 的基础名 |
| code license | `MIT` | 保留原项目 MIT 声明，并对新增代码采用 MIT |
| default branch | `main` | 受保护分支 |
| release version format | `1.20.1-<semver>` | 例如 `1.20.1-0.6.0-alpha.1` |

### 2. 必须人工决定的两项

#### 2.1 是否继续使用 “Advanced Rocketry” 名称

默认方案允许使用，但必须：

- 明确标注 **非官方社区续作/重写**；
- 不使用原项目作者或 Mojang/Microsoft/Forge 的官方身份暗示；
- 不把问题提交到原仓库；
- 若原作者提出合理的名称或品牌异议，准备更名；
- 在 `NOTICE.md`、README 顶部、发布页和模组描述中重复非官方声明。

更保守方案是另起主品牌，例如 `Rocketry Reignited`，并在副标题中写 “inspired by Advanced Rocketry”。选择更保守方案时，应在 `v0.0.1` 完成前一次性修改本页。

#### 2.2 是否沿用原 `advancedrocketry` mod id

默认：**不沿用**。

原因：

- 防止与未来官方版本或其他社区版直接冲突；
- 避免玩家误认官方；
- 迫使迁移脚本显式记录资源转换，而不是悄悄复用；
- 1.20.1 没有需要直接加载的原版世界，因此短期兼容收益较低。

只有在原维护者明确同意、并完成冲突评估后，才可通过 ADR 改为原 mod id。

### 3. 版本兼容承诺

| 阶段 | 世界存档承诺 |
|---|---|
| `v0.0.x–v0.4.x` | 测试世界可随时废弃 |
| `v0.5.x–v0.8.x` | 尽量迁移，但允许在发布说明中声明一次性重置 |
| `v0.9.x` | 同一 Beta 主版本间必须提供迁移或明确阻止加载 |
| `v1.0.0+` | 1.x 内不得无迁移破坏存档；所有持久化对象必须带 schema 版本 |

### 4. 配置确认记录

在首次提交前，将以下内容改为实际值：

```yaml
reviewed_by: "<GitHub 用户名>"
reviewed_at: "YYYY-MM-DD"
identity_status: "APPROVED"
```

当前值：

```yaml
reviewed_by: ""
reviewed_at: ""
identity_status: "DRAFT"
```


---

## Source file: `AGENTS.md`

## AGENTS.md — Advanced Rocketry 1.20.1 社区重写执行规约

### 1. 项目目标

本仓库面向 Minecraft 1.20.1 Forge，以原 Advanced Rocketry 1.12.2 项目作为行为和可审计资产基线，进行 **新架构重写**。

“重写”意味着：

- 可以参考旧代码的算法、数值、资源和玩家行为；
- 不以“把旧类修到编译通过”为实施方式；
- 先建立现代 Forge 的数据、服务端权威、持久化和测试边界；
- 每次只交付一个可验证的垂直切片。

### 2. 开始任何任务前必须读取

1. `PROJECT-CONFIG.md`
2. `PRODUCT.md`
3. `docs/01-PORTING-PRINCIPLES.md`
4. `docs/04-VERSION-ROADMAP.md`
5. `docs/versions/<当前版本>.md`
6. `docs/05-MASTER-TEST-PLAN.md`
7. `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`

若任务涉及上游文件，额外读取：

- `UPSTREAM.md`
- `NOTICE.md`
- `docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md`
- `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`

### 3. 不可违反的工程约束

#### 3.1 范围

- 一个会话、一个工作分支、一个 PR 只处理一个版本或该版本内一个明确子切片。
- 不得提前实现后续版本的功能；可以预留接口，但不得建立未被当前版本使用的大型框架。
- 不得创建完整 LibVulpes 复刻；仅实现当前垂直切片需要的基础设施。
- `v0.6.0` 前不得实现卫星、跃迁、地球化、轨道激光、空间电梯等高级内容。

#### 3.2 来源与授权

- 任何复制或变换自上游的文件，必须先进入来源清单。
- 只允许默认引用原 `Advanced-Rocketry/AdvancedRocketry` 仓库中已确认 MIT 的内容。
- 不得从 Advanced Rocketry Reworked、ARLib、Advanced Rocketry 3 或其他分支/模组复制代码与资产，除非已在 `docs/provenance/` 记录许可、来源 commit 和允许范围。
- 不得复制 Minecraft、Mojang、Microsoft 或 Forge 的官方美术资源。
- 不得删除原 MIT 版权和许可声明。

#### 3.3 架构

- 使用 Java 17、Forge 1.20.1。
- 使用 `DeferredRegister`、`RegistryObject`、`ResourceLocation`、`ResourceKey<Level>`。
- 不使用数字维度 ID 作为持久化身份。
- 世界状态不得保存在无生命周期约束的全局静态集合中。
- 跨维度状态放在服务端 `SavedData` 或明确的持久化服务中。
- 客户端不做火箭合法性、燃料、密闭空间、目的地或库存的最终判定。
- 任何 C2S 包都必须校验玩家、距离、权限、状态、范围和区块是否已加载。
- 不得因客户端提供的位置而强制加载任意区块。
- 不迁移 ASM/coremod；如确有必要，必须先写 ADR 并证明 Forge 事件或 Access Transformer 无法解决。
- 任何可能遍历世界方块的算法必须有硬上限和每 tick 预算。
- 任何可变长度网络/NBT 数据必须有大小限制。
- 持久化对象必须带独立 schema 版本。
- 复杂领域对象不得集中到巨型 Entity/BlockEntity；Minecraft 对象只做适配和生命周期桥接。

#### 3.4 文件规模与依赖方向

推荐依赖方向：

```text
model/api
   ↓
domain services
   ↓
persistence + validation
   ↓
Forge adapters
   ↓
client rendering/screens
```

约束：

- common/server 代码不得引用 `net.minecraft.client.*`。
- 单类超过 500 行时必须检查是否混合多个职责。
- 单类超过 800 行时，除生成代码外必须有 ADR 说明。
- 不建立双向模块依赖。
- 领域计算优先写为可脱离 Minecraft 启动的纯 Java 代码。

### 4. 每次实施协议

#### 4.1 准备

1. 检查 git 状态、当前分支和上一个版本 Gate。
2. 确认当前版本文档中的 Required Gate 已列明。
3. 创建或更新 `docs/work/<version>-implementation-log.md`。
4. 列出本次明确不做的内容。
5. 检查来源清单，不允许“先复制再补记录”。

#### 4.2 实施

按最小可验证顺序：

1. 数据模型与验证；
2. 服务端领域逻辑；
3. 持久化；
4. Forge 注册与适配；
5. 网络；
6. 客户端显示；
7. 数据生成与资产；
8. 自动测试；
9. 人工测试步骤；
10. 文档和发布证据。

#### 4.3 验证

至少运行当前版本文档指定的命令。通用必跑：

```bash
./gradlew clean build
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
```

若版本包含专服、跨维度、网络或持久化功能，还必须完成专用服务端与重启测试。

#### 4.4 结束输出

每次 Codex 执行结束必须输出：

```text
完成范围
未完成范围
关键设计决定
修改文件
新增/修改测试
实际执行命令与结果
验收证据路径
已知风险
是否满足当前版本全部 Required Gate
下一步建议（只能指向当前版本剩余项或下一版本）
```

不得只说“实现完成”而不提供实际测试结果。

### 5. Gate 纪律

- Required Gate 任一失败：版本状态必须为 `BLOCKED` 或 `IN PROGRESS`。
- 不得通过删除测试、放宽断言、扩大超时、忽略异常或降低性能预算来伪造通过。
- 任何豁免都必须写入 ADR，并标注负责人、理由、到期版本和回收条件。
- 版本标签只能在 `docs/releases/<version>/RELEASE-EVIDENCE.md` 完整后创建。
- 自动测试通过不等于发布通过；客户端视觉、专服、持久化和许可证 Gate 必须分别确认。

### 6. 提交与分支

分支名：

```text
codex/v0.3.0-celestial-codec
codex/v0.5.0-rocket-snapshot
fix/v0.6.0-transfer-recovery
docs/v0.0.1-governance
```

提交信息：

```text
feat(celestial): add validated body definitions
test(rocket): cover assembly rollback
docs(release): add v0.5.0 evidence
fix(network): reject unloaded destination chunks
```

每个提交应可解释、可回滚，不把生成文件、架构重写、资产导入和测试修复混为一个不可审查提交。

### 7. 当前版本识别

读取 `docs/04-VERSION-ROADMAP.md` 中第一个未完成版本。若仓库尚未建立任何状态文件，当前版本固定为 `v0.0.1`。

建议维护：

```text
docs/status/CURRENT_VERSION.md
docs/status/GATE_STATUS.md
docs/work/
docs/releases/
```

Codex 不得自行把版本标记为通过；只能生成证据并建议人工确认。


---

## Source file: `PRODUCT.md`

## PRODUCT.md — Advanced Rocketry: Community Edition

### 一句话说明

一个面向 Minecraft 1.20.1 Forge 的非官方 Advanced Rocketry 社区重写：优先恢复“由方块建造真实火箭、解决生命保障、往返行星与空间站”的核心体验，再逐步恢复卫星、研究和高级太空工业。

### 为什么不做逐行移植

Minecraft 1.12.2 到 1.20.1 跨越了注册、维度、世界数据、网络、BlockEntity、渲染、配方和资源体系的多轮重构。原项目还将火箭、动态维度、大气扫描、多方块机器和 LibVulpes 深度耦合。

逐行修编译错误很容易得到：

- 能进游戏，但服务器状态不可靠；
- 单人可用，多人复制或丢失；
- 火箭跨维度后乘客卡死；
- 密闭扫描造成 tick 卡顿；
- 存档结构无法演进；
- 一次修改会波及大量旧抽象。

因此本项目把 1.12.2 版本视为 **行为、数值和资产基线**，而不是目标架构。

### v1.0.0 核心体验

玩家能够：

1. 制作核心材料和少量必要机器；
2. 建造生命保障设备与基础宇航服；
3. 使用真实方块组装火箭；
4. 为火箭加注燃料并选择目的地；
5. 从地球发射；
6. 在月球着陆并活动；
7. 返回地球；
8. 建立一座基础空间站；
9. 在服务器重启、玩家掉线和区块卸载后继续使用；
10. 全程不存在已知的方块、库存或火箭复制漏洞。

### 首版不追求的内容

以下内容不进入核心闭环，默认推迟到 `v1.x`：

- 任意运行时动态创建新维度；
- 完整旧科技树和全部机器；
- 黑洞、地球化、空间电梯；
- 跃迁到多恒星系统；
- 轨道激光、轨道炮、铁路炮；
- 完整小行星手动/自动采矿；
- 与 IC2、Galacticraft 等旧模组的兼容；
- 直接加载 1.12.2 世界；
- Fabric/NeoForge 多加载器支持。

### 产品原则

#### 1. 核心闭环优先

先保证地球—月球往返可靠，再扩大内容。

#### 2. 服务端权威

客户端表达意图，服务端判定结果。火箭、燃料、库存、大气、目的地和权限都不得由客户端最终决定。

#### 3. 数据可演进

天体、配方和内容定义数据驱动；世界运行状态有 schema 版本和迁移链。

#### 4. 可解释失败

火箭不能组装、房间不能密闭、机器不能运行时，应给玩家明确原因，而不是无响应或只写日志。

#### 5. 有边界的自由建造

允许玩家使用大量普通方块造火箭，但第三方 BlockEntity、超大结构、危险方块和超大 NBT 必须有安全策略。

#### 6. 性能预算是功能的一部分

密闭扫描、火箭扫描、实体渲染、网络同步和卫星任务都必须设置上限，不接受“以后再优化”。

#### 7. 社区续作不冒充官方

公开页面必须说明来源、授权和非官方身份；不要让原作者承担本项目的问题支持。

### 版本路线

| 版本 | 玩家可见成果 |
|---|---|
| `v0.0.1` | 仓库、授权、声明与贡献规则成立 |
| `v0.0.2` | Forge 1.20.1 空模组可构建、可启动、可在 CI 测试 |
| `v0.1.0` | 基础资产、注册、数据生成和来源清单成立 |
| `v0.2.0` | 一台完整机器形成现代 Forge 垂直切片 |
| `v0.3.0` | 地球、月球、空间的天体数据与固定维度成立 |
| `v0.4.0` | 真空、宇航服、氧气与密闭房间可用 |
| `v0.5.0` | 方块火箭可组装、实体化和原地安全拆解 |
| `v0.6.0` | 完成可靠的地球—月球往返 |
| `v0.7.0` | 基础空间站和权限成立 |
| `v0.8.0` | 基础研究与卫星闭环成立 |
| `v0.9.0` | Beta 稳定性、性能和兼容性达标 |
| `v1.0.0` | 社区 MVP 正式发布 |


---

## Source file: `DOCUMENT-INDEX.md`

## DOCUMENT-INDEX — 文档索引与用途

### 最先交给 Codex 的文件

| 文件 | 用途 |
|---|---|
| `PROJECT-CONFIG.md` | 唯一项目变量和人工决策入口 |
| `AGENTS.md` | Codex 的长期工程约束、Gate 和输出格式 |
| `00-READ-ME-FIRST.md` | 人工启动顺序 |
| `codex-prompts/00-initialize-repository.md` | 第一次执行，只完成 v0.0.1 |

### 产品与总体方案

| 文件 | 用途 |
|---|---|
| `PRODUCT.md` | 产品是什么、v1.0 核心体验、非目标 |
| `docs/01-PORTING-PRINCIPLES.md` | 为什么重写、如何使用旧代码/资产 |
| `docs/03-TARGET-ARCHITECTURE.md` | 1.20.1 包结构和系统拆分 |
| `docs/04-VERSION-ROADMAP.md` | 12 个版本里程碑和强制顺序 |
| `docs/PORTING_MATRIX.md` | 旧系统到新模块/版本/测试的映射 |
| `docs/11-RISK-REGISTER.md` | 核心风险、触发与缓解 |

### 上游和资产

| 文件 | 用途 |
|---|---|
| `UPSTREAM.md` | 主上游、次级参考和禁止复制来源 |
| `docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md` | Codex 应生成的代码树/资产清单 |
| `docs/08-ASSET-LICENSE-AND-PROVENANCE.md` | 导入、hash、许可和 quarantine 规则 |
| `docs/templates/SOURCE-PROVENANCE-TEMPLATE.md` | 每个导入文件/批次的来源记录 |
| `codex-prompts/01-run-upstream-audit.md` | 只执行审计，不复制内容 |

### GitHub、声明和治理

| 文件 | 用途 |
|---|---|
| `README.md` | 可直接作为新仓库 README |
| `LICENSE` | 保留原 2017 MIT notice，并覆盖新增工作 |
| `NOTICE.md` | 上游归属、非官方、Minecraft/Forge 声明 |
| `BRANDING_AND_AFFILIATION.md` | 名称、Logo、官方误认边界 |
| `REPOSITORY-DECLARATIONS.md` | 可直接粘贴的 GitHub/发布/mods.toml 文案 |
| `docs/09-GITHUB-REPOSITORY-SETUP.md` | 建仓、规则集、标签、Release 流程 |
| `CONTRIBUTING.md` | 贡献要求 |
| `SECURITY.md` | 复制、包滥用、存档损坏等敏感问题 |
| `CODE_OF_CONDUCT.md` | 社区行为规则 |
| `.github/` | Issue/PR 模板和 CODEOWNERS 示例 |

### 测试、存档和发布

| 文件 | 用途 |
|---|---|
| `docs/05-MASTER-TEST-PLAN.md` | 单元、GameTest、专服、重启、多人、性能 |
| `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md` | G0–G9 的通过门槛 |
| `docs/07-SAVE-DATA-AND-NETWORK-VERSIONING.md` | schema、迁移、journal、包大小 |
| `docs/templates/TEST-REPORT-TEMPLATE.md` | 自动测试报告 |
| `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` | 人工测试用例 |
| `docs/templates/PERFORMANCE-REPORT-TEMPLATE.md` | 性能报告 |
| `docs/templates/RELEASE-EVIDENCE-TEMPLATE.md` | 每版最终证据 |

### 分版本执行文件

```text
docs/versions/V0.0.1-REPOSITORY-BASELINE.md
docs/versions/V0.0.2-FORGE-BOOTSTRAP.md
docs/versions/V0.1.0-ASSET-REGISTRY-BASELINE.md
docs/versions/V0.2.0-MACHINE-VERTICAL-SLICE.md
docs/versions/V0.3.0-CELESTIAL-DATA-AND-DIMENSIONS.md
docs/versions/V0.4.0-VACUUM-LIFE-SUPPORT-ATMOSPHERE.md
docs/versions/V0.5.0-ROCKET-ASSEMBLY.md
docs/versions/V0.6.0-EARTH-MOON-ROUNDTRIP.md
docs/versions/V0.7.0-SPACE-STATION.md
docs/versions/V0.8.0-PROGRESSION-SATELLITES.md
docs/versions/V0.9.0-BETA-HARDENING.md
docs/versions/V1.0.0-COMMUNITY-MVP.md
```

每个版本都包含：

```text
目标
玩家可见结果
前置 Gate
范围
明确不做
实施顺序
自动测试
人工/专服测试
通过确认
证据
PR 拆分
失败回退
Codex 报告格式
```

### Codex 日常提示

| 文件 | 用途 |
|---|---|
| `codex-prompts/02-implement-next-version.md` | 实现当前未通过版本 |
| `codex-prompts/03-audit-current-version.md` | 使用独立会话做怀疑式审核 |
| `codex-prompts/04-release-gate.md` | 只跑 Gate 和证据，不扩功能 |

### 决策记录

`docs/decisions/` 内提供四份初始 ADR：

- 项目身份与 namespace；
- 固定 Moon/Space 维度；
- 火箭事务；
- 大气扫描预算。

它们默认 `PROPOSED`，需在对应版本前由人工接受。


---

## Source file: `REPOSITORY-DECLARATIONS.md`

## REPOSITORY-DECLARATIONS — 可直接使用的仓库声明文案

> 使用前把 owner/repository 改为实际值。项目名仍为暂定时，不要删除 “unofficial”。

### 1. GitHub Repository Description

```text
Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.
```

较短版本：

```text
Unofficial Advanced Rocketry community rewrite for Forge 1.20.1.
```

### 2. README 第一屏

```markdown
# Advanced Rocketry: Community Edition

> **Unofficial community rewrite for Minecraft 1.20.1 Forge.**
>
> This project is not an official continuation and is not maintained or supported by the original Advanced Rocketry maintainers.
>
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
```

中文补充：

```markdown
> 本项目是面向 Minecraft 1.20.1 Forge 的非官方社区重写，不是 Advanced Rocketry 官方续作，也不由原维护者提供支持。
```

### 3. 上游归属段落

```markdown
## Attribution

This project may contain audited portions derived from the MIT-licensed
Advanced-Rocketry/AdvancedRocketry project. The original MIT notice
(`Copyright (c) 2017`) is preserved in this repository.

Every imported or transformed upstream file is recorded with its source
repository, branch, commit, path, hash, license, and transformation.

Do not report Community Edition bugs to the original Advanced Rocketry maintainers.
```

### 4. 当前状态

规划阶段：

```markdown
## Status

**Pre-alpha / planning and architecture phase. No playable public release is available yet.**
```

技术原型：

```markdown
## Status

**Developer preview. Test worlds are disposable and no save compatibility is promised.**
```

Alpha：

```markdown
## Status

**Playable alpha. Core systems are under active testing; back up worlds before every update.**
```

Beta：

```markdown
## Status

**Beta. Core gameplay is feature-frozen while save migration, multiplayer, performance, and compatibility are being validated.**
```

Stable：

```markdown
## Status

**Stable community MVP for Minecraft 1.20.1 Forge. Review Known Issues and back up worlds before upgrading.**
```

### 5. GitHub Release 顶部

```markdown
> This is an unofficial community build and is not supported by the original Advanced Rocketry maintainers.
>
> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
```

Alpha/Beta 必加：

```markdown
This is a pre-release. Do not use it on an irreplaceable world without a tested backup.
```

### 6. Issue Tracker 提示

```markdown
This issue tracker is for Advanced Rocketry: Community Edition only.
Do not forward these reports to the original Advanced Rocketry project.
```

### 7. `mods.toml` credits

```toml
credits="Based on the MIT-licensed Advanced Rocketry project. Unofficial community rewrite; not supported by the original maintainers."
```

### 8. `mods.toml` description

```toml
description='''
An unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge.
Build block-based rockets, survive vacuum, travel to the Moon, and establish space infrastructure.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
'''
```

### 9. 支持范围

```markdown
## Support

Supported target:

- Minecraft 1.20.1
- Forge 47.4.10 baseline
- Java 17
- Dedicated server and client

Not supported:

- Fabric or NeoForge builds
- Direct loading of Advanced Rocketry 1.12.2 worlds
- Unverified third-party forks
- Large modpack reports without a minimal reproduction
```

### 10. 许可证简述

```markdown
## License

Code and audited imported portions are distributed under the MIT License.
The original Advanced Rocketry MIT notice is preserved. See LICENSE, NOTICE.md,
UPSTREAM.md, and the provenance records for details.
```

### 11. 不应使用的文案

```text
Official Advanced Rocketry 1.20.1 port
Authorized official continuation
Maintained by the Advanced Rocketry team
Fully compatible with the original
All old worlds supported
All assets are free to use
```

这些表述要么不真实，要么超出当前验证范围。


---

## Source file: `README.md`

## Advanced Rocketry: Community Edition

> **Unofficial community rewrite for Minecraft 1.20.1 Forge.**
>
> This project is not an official continuation and is not maintained or supported by the original Advanced Rocketry maintainers.
>
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

### Status

**Pre-alpha / planning and architecture phase. No playable public release is available yet.**

Current target:

- Minecraft `1.20.1`
- Forge baseline `47.4.10`
- Java `17`
- License `MIT`

See [`docs/04-VERSION-ROADMAP.md`](docs/04-VERSION-ROADMAP.md) for the implementation sequence and [`docs/status/GATE_STATUS.md`](docs/status/GATE_STATUS.md) once development begins.

### What this project is

Advanced Rocketry: Community Edition aims to rebuild the core Advanced Rocketry experience on a maintainable Forge 1.20.1 foundation:

- rockets constructed from real blocks;
- Earth, Moon, and space travel;
- vacuum and life support;
- basic space stations;
- later, research and satellites;
- server-authoritative multiplayer behavior;
- versioned save data and automated tests.

The original 1.12.2 project is treated as a behavior and asset reference. This repository is not a line-by-line compilation port.

### MVP definition

The first stable release is complete only when a player can:

1. build and fuel a block-built rocket;
2. survive vacuum with life support;
3. launch from Earth;
4. land on the Moon;
5. return safely;
6. recover correctly after disconnects and server restarts;
7. do so without known inventory, block, passenger, or rocket duplication.

### Roadmap

| Version | Goal |
|---|---|
| `v0.0.1` | Repository, attribution, governance |
| `v0.0.2` | Forge 1.20.1 build foundation |
| `v0.1.0` | Asset and registry baseline |
| `v0.2.0` | One complete machine vertical slice |
| `v0.3.0` | Celestial data and fixed dimensions |
| `v0.4.0` | Vacuum, suits, oxygen, sealed rooms |
| `v0.5.0` | Transactional rocket assembly |
| `v0.6.0` | Reliable Earth–Moon round trip |
| `v0.7.0` | Basic space station |
| `v0.8.0` | Progression and satellites |
| `v0.9.0` | Beta hardening |
| `v1.0.0` | Stable community MVP |

### Attribution

This project may include audited portions derived from the MIT-licensed original Advanced Rocketry repository. The original license notice is preserved in [`LICENSE`](LICENSE), with additional details in [`NOTICE.md`](NOTICE.md), [`UPSTREAM.md`](UPSTREAM.md), and the provenance ledger.

Do not report Community Edition bugs to the original Advanced Rocketry maintainers.

### Contributing

Read:

1. [`CONTRIBUTING.md`](CONTRIBUTING.md)
2. [`AGENTS.md`](AGENTS.md)
3. [`docs/04-VERSION-ROADMAP.md`](docs/04-VERSION-ROADMAP.md)
4. the document for the current target version.

A feature is not complete until its required automated, dedicated-server, persistence, performance, and manual acceptance gates pass.

### Support policy

During pre-alpha:

- test worlds may be reset;
- APIs may change;
- binary releases may be withheld;
- unsupported mod combinations are not investigated unless a minimal reproduction is provided.

Security-sensitive duplication, arbitrary chunk loading, packet abuse, or save corruption reports should follow [`SECURITY.md`](SECURITY.md).

### License

MIT. See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).


---

## Source file: `NOTICE.md`

## NOTICE

### Unofficial community project

**Advanced Rocketry: Community Edition is an unofficial community rewrite/continuation.**

It is not maintained, approved, endorsed, or supported by the original Advanced Rocketry maintainers. Bugs and support requests for this project must be reported to this repository, not to the original project.

### Original project attribution

This project may contain code, data, documentation, models, textures, sounds, or other files derived from:

- Project: `Advanced-Rocketry/AdvancedRocketry`
- Upstream branch used as primary reference: `1.12`
- Upstream license: MIT
- Original license notice preserved: `Copyright (c) 2017`

Every imported or transformed upstream file must be recorded in the provenance ledger described in `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`.

The presence of an upstream reference does not mean every file is automatically imported. Files are added only after source and license review.

### New community work

Newly authored portions are licensed under the repository's MIT License and attributed to the Advanced Rocketry: Community Edition contributors.

### Minecraft disclaimer

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Minecraft is a trademark of Microsoft Corporation. This project uses the Minecraft name only to describe compatibility.

### Forge disclaimer

This project targets Minecraft Forge but is not an official Forge project and is not endorsed or supported by the Forge maintainers.

### Name and branding

Use of “Advanced Rocketry” identifies the upstream inspiration and compatibility lineage. It must not be presented as evidence of official continuation. The community project name and logo may be changed if a branding conflict is identified.

### Third-party projects

Do not assume code or assets from the following are available under the same terms as the original repository:

- Advanced Rocketry - Reworked
- ARLib
- Advanced Rocketry 3
- LibVulpes forks
- Modpack-specific forks
- Community texture packs

Their content may be used only after an explicit license and provenance review.


---

## Source file: `UPSTREAM.md`

## UPSTREAM.md — 上游来源和参考边界

### 1. 主上游

```text
repository: https://github.com/Advanced-Rocketry/AdvancedRocketry
primary_branch: 1.12
license: MIT
upstream_commit: <由 v0.0.1 / v0.1.0 审计填写>
```

`1.12` 分支承担三种角色：

1. 行为基线：旧版玩家实际看到的功能和流程；
2. 数值基线：配方、燃料、推力、重力、机器时间等；
3. 可审计资产来源：纹理、模型、声音、语言和数据。

它不承担新架构基线。

### 2. 次级参考

原项目曾存在 1.16.5 构建和相关代码历史。它可以用于理解原作者曾如何适应较新的 Minecraft API，但必须：

- 先定位准确分支或 commit；
- 确认该文件仍属于原项目 MIT 范围；
- 只作为语义参考；
- 不以该分支作为 Gradle 升级起点；
- 不因为代码“更接近现代”就跳过重写和测试。

### 3. 默认禁止复制的来源

除非单独完成许可审核：

- Advanced Rocketry - Reworked；
- ARLib；
- Advanced Rocketry 3；
- 未声明许可证的 LibVulpes 内容；
- 其他 GitHub fork；
- CurseForge/Modrinth 发布包内无来源说明的资产；
- 反编译 JAR。

### 4. 本地仓库建议

新项目保持独立 Git 历史，并将上游作为只读远程或相邻目录：

```bash
git remote add upstream-ar https://github.com/Advanced-Rocketry/AdvancedRocketry.git
git fetch upstream-ar 1.12
```

禁止直接 merge 上游 1.12 到 `main`。

推荐使用：

```text
../AdvancedRocketry-upstream/        # 只读审计副本
./legacy-manifest/                   # 审计结果，不存整份旧源码
./docs/provenance/                   # 实际导入记录
./tools/import/                      # 可复现转换脚本
```

### 5. 每次导入必须记录

```yaml
target_path:
source_repository:
source_branch:
source_commit:
source_path:
source_sha256:
target_sha256:
license:
transformation:
reviewer:
reviewed_at:
```

没有记录的文件不得进入发布 JAR。


---

## Source file: `BRANDING_AND_AFFILIATION.md`

## BRANDING_AND_AFFILIATION.md

### 必须使用的公开口径

推荐首段：

> Advanced Rocketry: Community Edition is an unofficial community rewrite for Minecraft 1.20.1 Forge, based on the MIT-licensed Advanced Rocketry project. It is not an official continuation and is not supported by the original maintainers.

中文：

> Advanced Rocketry: Community Edition 是面向 Minecraft 1.20.1 Forge 的非官方社区重写，基于 MIT 授权的 Advanced Rocketry 项目。它不是官方续作，也不由原维护者提供支持。

Minecraft 声明：

> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

### 声明放置位置

必须出现于：

- README 顶部；
- GitHub About/Description 可见范围内的简化表述；
- `NOTICE.md`；
- 发布页；
- CurseForge/Modrinth 项目描述（如未来发布）；
- 官网或 Wiki 首页；
- `mods.toml` description/credits；
- Discord 或社区页面置顶说明。

### 禁止表述

- “官方 1.20.1 移植”
- “原作者授权续作”（除非取得可公开证明的授权）
- “Advanced Rocketry 官方团队”
- 使用原作者头像、签名或身份作为项目背书
- 要求玩家向原项目仓库报告本项目问题
- 使用 Mojang/Microsoft/Minecraft 官方 Logo 作为项目 Logo

### Logo 建议

- 使用新绘制的火箭/行星图形；
- 不直接复用 Minecraft 官方包装、美术或 Logo；
- 若使用原项目 Logo 或图形，必须把它作为资产单独列入来源清单；
- 在公开前保留可替换性，避免 Logo 与 mod id 深度绑定。

### 项目名称风险处理

如收到原维护者或权利人异议：

1. 暂停新公开发布；
2. 保存沟通记录；
3. 将 display name 改为独立品牌；
4. 保留“兼容/灵感来源”说明；
5. 修改仓库名、模组名、Logo 和发布页；
6. 不必因此删除已合法使用的 MIT 代码，但应重新检查品牌表现。

本文件不是法律意见；重大公开发布或商业化前可寻求专业法律审查。


---

## Source file: `CONTRIBUTING.md`

## CONTRIBUTING.md

### Before contributing

Read, in order:

1. `PROJECT-CONFIG.md`
2. `AGENTS.md`
3. `docs/04-VERSION-ROADMAP.md`
4. the current version document
5. `docs/05-MASTER-TEST-PLAN.md`
6. `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`

### Scope rule

Contributions must fit the current milestone. A technically good implementation may still be rejected if it introduces future-version systems early or recreates unnecessary legacy abstractions.

### Issue first

Open or claim a porting task before substantial work. The issue must state:

- target version;
- player-visible behavior;
- upstream behavior/source references;
- explicit non-goals;
- test plan;
- save/network impact;
- asset/license impact.

### Pull request requirements

Every PR must include:

- a single focused purpose;
- tests appropriate to the change;
- actual commands run and results;
- screenshots/video for visual or player-flow changes;
- provenance records for imported assets/code;
- migration notes for persistent data;
- no unrelated generated or formatting churn.

### Required local checks

```bash
./gradlew clean build
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
```

Additional checks are defined by the target version.

### Source and asset policy

Do not copy code or assets from another community fork merely because it is available online. Provide source repository, commit, path, license, hash, and transformation record.

### Compatibility reports

A compatibility bug must include a minimal mod list. Reports from large modpacks without reduction may be closed as not actionable.

### Commit messages

Use conventional scopes where practical:

```text
feat(rocket):
fix(atmosphere):
test(celestial):
docs(release):
chore(build):
```

### AI-assisted contributions

AI-generated code is allowed, but the contributor is responsible for:

- validating sources and licenses;
- reviewing generated code;
- running tests;
- explaining architectural choices;
- ensuring no accidental code was copied from an incompatible source;
- not presenting unverified AI output as test evidence.

### Review expectations

Reviewers check behavior, architecture, server authority, save safety, test quality, scope, and provenance. Compilation alone is insufficient.


---

## Source file: `SECURITY.md`

## SECURITY.md

### Supported versions

Until `v1.0.0`, only the latest published pre-release is considered for security-sensitive fixes. Old test builds may be closed without patches.

### Report privately when possible

Security-sensitive issues include:

- item, fluid, block, passenger, rocket, or station duplication;
- remote crash or packet amplification;
- arbitrary chunk loading;
- bypass of station ownership or launch permissions;
- oversized NBT/packet memory exhaustion;
- save corruption with a reliable reproduction;
- server-side acceptance of forged client state.

Do not publish a working exploit before maintainers have had an opportunity to investigate.

### Include

- exact mod version and Forge version;
- dedicated server or singleplayer;
- minimal mod list;
- steps to reproduce;
- logs/crash report;
- world backup or minimal GameTest structure when possible;
- whether the exploit survives restart;
- estimated impact.

### Non-security reports

Visual issues, ordinary crashes without an exploit, balance concerns, and feature requests should use the normal issue templates.

### Disclosure handling

Maintainers should:

1. acknowledge the report;
2. reproduce with a minimal environment;
3. classify severity;
4. add a regression test;
5. fix without silently weakening validation;
6. publish release notes after a patched build exists.

No response-time guarantee is made for this volunteer project.


---

## Source file: `CODE_OF_CONDUCT.md`

## CODE_OF_CONDUCT.md

### Community standard

Participants must be respectful, constructive, and focused on improving the project.

Unacceptable behavior includes:

- harassment, threats, discrimination, or targeted insults;
- publishing private information;
- knowingly submitting copied code or assets without permission;
- pressuring original Advanced Rocketry maintainers to support this fork;
- repeatedly bypassing review, test, or license requirements;
- exploiting security issues against public servers.

### Enforcement

Maintainers may edit, hide, lock, or remove contributions and may temporarily or permanently restrict participation when needed to protect the project and community.

### Technical disagreement

Disagree with evidence:

- reproduce the behavior;
- reference the relevant version requirement;
- show test results;
- write an ADR for durable architectural decisions.

Status, seniority, or amount of generated code does not replace evidence.


---

## Source file: `docs/01-PORTING-PRINCIPLES.md`

## 01 — Porting Principles / 搬运与重写原则

### 1. 项目定义

本项目不是“把 Advanced Rocketry 1.12.2 修到 1.20.1 能编译”，而是：

> 以原版的玩家行为、数值和经审计资产为基线，在 Forge 1.20.1 上重新建立可测试、服务端权威、可迁移存档的实现。

### 2. 信息优先级

发生冲突时按以下顺序判断：

1. 当前版本文档中的验收标准；
2. 已批准 ADR；
3. `PRODUCT.md` 的核心体验；
4. 原版 1.12.2 的可复现玩家行为；
5. 原版代码实现细节；
6. 社区记忆、Wiki 或视频说明。

旧代码不是天然正确答案。旧版已有 bug、隐式状态和时代 API 限制，不应原样固化。

### 3. 上游内容的三种使用方式

#### 3.1 Behavior reference / 行为参考

观察原版并记录：

- 玩家操作步骤；
- 成功和失败反馈；
- 数值；
- 多人行为；
- 保存和重载结果；
- 已知 bug。

这类信息进入 `PORTING_MATRIX.md` 和测试用例。

#### 3.2 Algorithm reference / 算法参考

可以参考：

- 推力与质量计算；
- 天体关系；
- 密闭空间判定意图；
- 火箭结构统计；
- 轨道或任务时间计算。

新实现必须拆分职责、加边界和测试，不得因为算法来自上游就跳过验证。

#### 3.3 Asset import / 资产导入

纹理、模型、声音、语言和数据必须经过：

```text
来源确认 → 许可证确认 → 哈希记录 → 路径转换 → 引用校验 → 人工视觉检查
```

### 4. 功能分层

#### Tier 0 — 工程和法律基线

- 许可证、来源和公开声明；
- 构建、注册、CI、数据生成；
- 测试框架和发布门槛。

#### Tier 1 — v1.0 核心闭环

- 必要机器；
- 天体定义；
- 月球和空间；
- 真空、宇航服、供氧、密闭房间；
- 方块火箭组装；
- 地月往返；
- 基础空间站；
- 基础研究与卫星。

#### Tier 2 — v1.x 扩展

- 更多机器、卫星和任务；
- 多天体系统；
- 小行星采矿；
- 轨道能源；
- 高级推进；
- 数据包扩展能力。

#### Tier 3 — 长期/实验

- 运行时任意动态维度；
- 地球化；
- 跃迁；
- 黑洞；
- 空间电梯；
- 高风险跨模组移动方块实体。

### 5. 现代化必须发生在哪里

| 旧模式 | 新模式 |
|---|---|
| 整数维度 ID | `ResourceKey<Level>` / `ResourceLocation` |
| 静态全局世界状态 | 每世界服务 + `SavedData` |
| 巨型 `EntityRocket` | 结构、统计、状态机、传输、渲染拆分 |
| 客户端直接改变机器/火箭 | C2S 请求 + 服务端验证 |
| 一次性 flood fill | dirty 队列 + 每 tick 预算 |
| 复制任意 BlockEntity NBT | 白名单/适配器 + 大小限制 + 事务 |
| 全部 LibVulpes 抽象 | 当前切片所需的最小内部基础设施 |
| 运行时注册任意维度 | 固定维度 + 数据驱动逻辑天体 |
| 网络同步整个对象 | 版本化最小快照、增量或分块同步 |
| 手工维护所有 JSON | DataGen + 引用审计 |

### 6. 垂直切片原则

每个版本必须交付完整链路，而不是只堆底层类。

例如 `v0.2.0` 的机器切片必须同时包含：

```text
注册
→ 方块/BlockEntity
→ 能量/物品/流体
→ 配方
→ 持久化
→ 菜单与屏幕
→ 网络同步
→ DataGen
→ GameTest
→ 专服重载
→ 玩家可理解的错误状态
```

如果只完成其中三项，它仍是内部实验，不是该版本完成。

### 7. Definition of Done

一个功能只有同时满足以下条件才算完成：

- 行为符合当前版本定义；
- 服务端是最终权威；
- 数据大小和世界扫描有边界；
- 关键数据可保存、重载和迁移；
- 自动测试覆盖成功与失败路径；
- 专服测试通过；
- 人工流程可复现；
- 日志没有新的项目来源 ERROR；
- 相关资产有来源记录；
- 文档、配置和已知限制已更新；
- 生成了版本验收证据。

### 8. 不接受的“完成证明”

- “IDE 没有红线”；
- “客户端能启动”；
- “我手动试过一次”；
- “旧版就是这样写的”；
- “单人模式没问题”；
- “以后再补测试”；
- “失败时不会常见”；
- “Codex 说实现完成”。

### 9. 重大决定使用 ADR

以下变更必须写 ADR：

- 改用原 `advancedrocketry` mod id；
- 引入新的基础库或外部内容依赖；
- 支持动态维度；
- 移动第三方 BlockEntity；
- 修改存档兼容承诺；
- 使用 Access Transformer 或字节码技术；
- 改变网络协议兼容策略；
- 放宽安全上限；
- 推迟当前版本 Required Gate。


---

## Source file: `docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md`

## 02 — Upstream Tree and Asset Audit / 上游代码树与资产审计

### 1. 目标

在复制任何旧文件之前，先把原项目变成一份可查询的“行为—源码—资产—风险”地图。

上游 1.12 分支公开展示的核心 Java 根包为：

```text
src/main/java/zmaster587/advancedRocketry/
```

已知领域包括：

```text
advancements  api  armor  asm  atmosphere  backwardCompat
block  cable  capability  client  command  common  dimension
enchant  entity  event  integration  inventory  item  mission
network  recipe  satellite  stations  tile  unit  util  world
```

资源根目录为：

```text
src/main/resources/assets/advancedrocketry/
```

审计不得只依赖目录名。Codex 应在本地克隆的准确 commit 上重新生成完整清单。

### 2. 审计阶段禁止事项

- 不把旧 `src/` 整体复制进新仓库；
- 不先改 package 名再审计；
- 不运行自动“API 替换器”批量迁移；
- 不把 LibVulpes 源码内嵌；
- 不从编译后的 JAR 反编译；
- 不把所有资源先放进新 JAR；
- 不根据文件名猜许可证。

### 3. 必须生成的产物

```text
legacy-manifest/
├─ UPSTREAM_COMMIT.txt
├─ java-files.csv
├─ java-packages.csv
├─ dependency-imports.csv
├─ libvulpes-usage.csv
├─ static-world-state.csv
├─ network-packets.csv
├─ entities.csv
├─ block-entities.csv
├─ registries.csv
├─ recipes.csv
├─ assets.csv
├─ asset-references.csv
├─ missing-asset-references.csv
├─ duplicate-case-paths.csv
├─ large-files.csv
├─ asm-and-coremod.csv
└─ audit-summary.md
```

### 4. Java 审计字段

`java-files.csv` 至少包含：

```csv
path,package,lines,bytes,sha256,primary_domain,imports_libvulpes,imports_client,has_static_mutable_state,has_nbt,has_network,has_dimension_logic,notes
```

额外扫描：

- 超过 500、800、1500 行的类；
- `static Map/List/Set` 等可变集合；
- 数字维度 ID；
- world/player/entity 的静态缓存；
- 客户端类进入 common 代码；
- ASM/coremod；
- 直接线程创建；
- `readFromNBT` / `writeToNBT`；
- 网络包中位置与 NBT；
- 可能加载区块的调用；
- 任意方块实体 NBT 复制；
- 多方块结构匹配；
- 反射使用；
- 与 LibVulpes 的继承和接口耦合。

### 5. 资产审计字段

`assets.csv` 至少包含：

```csv
source_path,kind,bytes,width,height,color_mode,sha256,license_status,source_commit,target_version,target_path,transformation,status,notes
```

`kind` 示例：

```text
texture_block
texture_item
texture_gui
texture_planet
texture_entity
model_json
model_obj
model_mtl
sound_ogg
sound_definition
lang
recipe
advancement
blockstate
other
```

检查：

- 文件路径是否全部小写；
- Windows 下大小写冲突；
- JSON 可解析性；
- blockstate → model → texture 引用链；
- OBJ → MTL → texture 引用链；
- `sounds.json` → OGG；
- 孤立资源；
- 重复哈希；
- 非标准编码；
- PNG 颜色模式和透明通道；
- 资源是否来自 LibVulpes 而非 Advanced Rocketry；
- 文件内是否有第三方作者声明。

### 6. 功能到版本映射

审计结果必须写入 `docs/PORTING_MATRIX.md`，每个功能至少包含：

```text
旧行为
旧源码入口
旧资产入口
旧依赖
已知旧 bug
新目标模块
目标版本
自动测试
人工验收
是否进入 v1.0
```

### 7. 行为金样

在可运行的 1.12.2 环境中建立固定测试世界和记录。至少记录：

- 一台代表性机器的输入、耗时、能耗、输出；
- 月球重力和真空行为；
- 氧气房间密闭/破坏流程；
- 典型火箭结构、质量、推力、燃料与可达目的地；
- 发射、转移、降落和拆解；
- 空间站创建；
- 一种卫星任务；
- 已知失败案例。

产物：

```text
legacy-manifest/golden-behavior/
├─ TEST-CASES.md
├─ values.json
├─ screenshots/
├─ videos/            # 可只存链接和哈希
└─ worlds/README.md   # 不提交未获授权的大体积世界包
```

### 8. 建议实现的审计工具

```text
tools/audit/
├─ audit_java_tree.py
├─ audit_assets.py
├─ verify_resource_references.py
├─ detect_case_collisions.py
├─ generate_porting_matrix.py
└─ verify_provenance.py
```

要求：

- 输入路径和 commit 明确；
- 输出排序稳定；
- 同一输入重复运行不得产生无意义 diff；
- 失败返回非零退出码；
- 可在 CI 验证当前导入文件是否有来源记录。

### 9. 审计通过条件

- 上游 commit 已锁定；
- 所有拟导入文件都有哈希；
- 旧代码主要领域均进入矩阵；
- LibVulpes 依赖点可查询；
- ASM/coremod 点已列出并标记“不迁移”；
- 资产缺失引用、大小写冲突和第三方来源已列出；
- v1.0 范围与推迟范围已明确；
- 审计脚本可重复运行；
- 尚未出现未经记录的上游文件。


---

## Source file: `docs/03-TARGET-ARCHITECTURE.md`

## 03 — Target Architecture / 1.20.1 目标架构

### 1. 顶层原则

- 单一 Forge 1.20.1 模组；
- Java 17；
- 首版不拆独立 LibVulpes 替代库；
- 领域模型尽量脱离 Minecraft 生命周期；
- Forge 对象是适配层，不是业务总控；
- 服务端权威；
- 所有持久化和网络格式有版本；
- 所有世界遍历和可变数据有硬上限。

### 2. 推荐包结构

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

### 3. 天体系统

#### 定义数据

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

#### 世界状态

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

#### XML 兼容

旧 `planetDefs.xml` 只作为导入格式：

```text
XML → Legacy DTO → 规范模型 → 验证 → JSON/Datapack/SavedData
```

运行时系统不得继续依赖 XML DOM。

### 4. 大气系统

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

### 5. 火箭系统

#### 领域拆分

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

#### 飞行状态

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

#### 组装事务

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

#### BlockEntity 策略

默认：

- 普通 `BlockState` 可移动；
- 原生容器通过受控适配器移动；
- 第三方 BlockEntity 默认拒绝；
- 逐个集成适配器开放；
- 设置单 BE NBT 和总 NBT 上限；
- 禁止命令方块、传送门、区块加载器等危险类型。

### 6. 跨维度转移

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

### 7. 机器系统

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

### 8. 多方块系统

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

### 9. 网络

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

### 10. 客户端

- common 代码不引用客户端包；
- 火箭渲染使用缓存后的结构 mesh/baked model；
- 结构不变时不得每帧重新烘焙；
- 天空、行星和 GUI 使用独立客户端注册；
- 专服测试必须验证无客户端类加载。

### 11. 默认安全上限

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

### 12. 依赖策略

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


---

## Source file: `docs/04-VERSION-ROADMAP.md`

## 04 — Version Roadmap / 版本路线

### 1. 版本格式

构建版本：

```text
1.20.1-<semantic-version>
```

示例：

```text
1.20.1-0.5.0-alpha.1
1.20.1-0.9.0-beta.2
1.20.1-1.0.0
```

Git tag：

```text
v0.5.0-alpha.1
v1.0.0
```

### 2. 阶段定义

| 阶段 | 版本 | 公开定位 |
|---|---|---|
| Planning | `v0.0.1` | 文档和治理 |
| Technical bootstrap | `v0.0.2–v0.2.0` | 开发者预览 |
| Core systems alpha | `v0.3.0–v0.5.0` | 不承诺长期世界 |
| Playable alpha | `v0.6.0–v0.8.0` | 可测试核心玩法 |
| Beta | `v0.9.0` | 稳定性和兼容性 |
| Stable MVP | `v1.0.0` | 正式社区版 |

### 3. 里程碑总览

| 版本 | 核心交付 | Required Gate |
|---|---|---|
| `v0.0.1` | 仓库、许可证、声明、贡献流程、上游边界 | Legal / Governance |
| `v0.0.2` | Forge 1.20.1 + Java 17 可复现构建和 CI | Build / Dedicated smoke |
| `v0.1.0` | 资产审计、注册、DataGen、最小内容 | Provenance / Asset validation |
| `v0.2.0` | 一台完整机器垂直切片 | Unit / GameTest / Persistence |
| `v0.3.0` | 天体模型、月球与空间固定维度、XML 导入 | Codec / World reload / Dedicated |
| `v0.4.0` | 真空、宇航服、氧气、预算化密闭扫描 | GameTest / Performance / Multiplayer |
| `v0.5.0` | 火箭结构、统计、事务组装与安全拆解 | Rollback / Duplication / Limits |
| `v0.6.0` | 燃料、飞行状态机、地月往返与崩服恢复 | 20 trips / Restart matrix / Security |
| `v0.7.0` | 共享空间维度中的基础空间站 | Ownership / Region allocation / Reload |
| `v0.8.0` | 基础研究、数据卫星、离线任务 | No forced chunks / Persistence |
| `v0.9.0` | Beta 稳定化、性能、兼容和迁移 | Soak / Compatibility / No critical bugs |
| `v1.0.0` | 文档、发布、稳定存档承诺 | All release gates |

### 4. 强制顺序

```text
v0.0.1
  ↓
v0.0.2
  ↓
v0.1.0
  ↓
v0.2.0
  ↓
v0.3.0
  ↓
v0.4.0
  ↓
v0.5.0
  ↓
v0.6.0
  ↓
v0.7.0
  ↓
v0.8.0
  ↓
v0.9.0
  ↓
v1.0.0
```

允许同一版本拆多个 PR，但不允许后续系统在前置 Gate 未通过时成为主线功能。

### 5. 版本状态

每个版本只能处于：

```text
PLANNED
IN_PROGRESS
BLOCKED
READY_FOR_AUDIT
PASSED
RELEASED
```

Codex 可以把版本推进到 `READY_FOR_AUDIT`，不能自行标记 `PASSED` 或 `RELEASED`。

### 6. 通过记录

建议建立：

```text
docs/status/CURRENT_VERSION.md
docs/status/GATE_STATUS.md
docs/releases/<version>/
├─ RELEASE-EVIDENCE.md
├─ TEST-REPORT.md
├─ MANUAL-TEST.md
├─ PERFORMANCE.md
├─ KNOWN-ISSUES.md
└─ checksums.txt
```

### 7. 版本回退

若已发布版本出现：

- 可稳定复制；
- 存档破坏；
- 任意区块加载；
- 远程崩服；
- 火箭或乘客不可恢复丢失；

应：

1. 标记 release 为有问题；
2. 暂停下载推荐；
3. 新增回归测试；
4. 在同一里程碑发补丁；
5. 不通过删除验证或临时吞异常“修复”。


---

## Source file: `docs/05-MASTER-TEST-PLAN.md`

## 05 — Master Test Plan / 总测试方案

### 1. 测试层次

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

### 2. 通用构建命令

每个版本至少：

```bash
./gradlew clean build
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
```

`runData` 后出现 diff，说明生成内容未提交或生成不稳定，Gate 失败。

### 3. Pure Java 单元测试

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

### 4. Codec/NBT 测试

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

### 5. GameTest 分类

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

### 6. 专用服务端

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

### 7. 重启矩阵

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

### 8. 复制/丢失不变量

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

### 9. 网络与恶意输入

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

### 10. 性能测试

#### 大气

- 节点访问受 `maxAtmosphereNodesPerTick` 限制；
- 16 个活跃 vent、每房间不超过 4096 方块时，服务端持续运行；
- 打开墙体后不进行无界同步重扫；
- 未加载区块不被强制加载。

#### 火箭

- 最大允许结构的扫描和快照不会冻结服务器到 watchdog；
- 渲染缓存只在结构变化时重建；
- 同步数据分块或压缩，不发送到无关玩家；
- NBT 大小在限制内。

#### 空间站/卫星

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

### 11. 人工验收

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

### 12. Forge 版本矩阵

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

### 13. 缺陷严重度

| 级别 | 示例 | 发布影响 |
|---|---|---|
| Critical | 复制、存档损坏、远程崩服、火箭永久丢失 | 阻断 |
| High | 核心闭环不可完成、多人状态严重不同步 | 阻断 |
| Medium | 次要机器/GUI 功能异常，有绕过 | Beta 可记录，1.0 通常阻断 |
| Low | 文案、轻微视觉、非核心体验 | 可记录发布 |

### 14. 测试证据不可伪造

不接受：

- 未实际运行却填写 PASS；
- 用“理论上可用”代替专服测试；
- 删除失败用例；
- 把断言改为仅打印日志；
- 用无限超时掩盖死锁；
- 用人工修改世界恢复结果；
- 只保留最后一段日志而无构建哈希。


---

## Source file: `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`

## 06 — Release and Acceptance Gates / 发布与验收门槛

### Gate G0 — Identity, License, Provenance

必须：

- `LICENSE` 存在且保留原 `Copyright (c) 2017`；
- `NOTICE.md` 和 README 有非官方声明；
- Minecraft 非官方声明可见；
- 导入文件有来源、commit、路径、hash、license；
- 未混入未审核社区 fork 内容；
- `mods.toml` license/credits/description 与仓库一致。

证据：

```text
license scan
provenance report
README screenshot
mods.toml excerpt
```

### Gate G1 — Reproducible Build

必须：

```bash
./gradlew clean build
```

在干净环境、Java 17 成功；JAR 命名和版本正确；无凭据进入产物。

证据：

```text
CI run
build log
JAR SHA-256
Java/Gradle/Forge versions
```

### Gate G2 — Data and Generated Resources

必须：

```bash
./gradlew runData
git diff --exit-code
```

且：

- JSON 可解析；
- 模型、纹理、声音引用存在；
- 无大小写冲突；
- 注册对象有对应数据；
- 生成结果稳定。

### Gate G3 — Automated Behavior

必须：

```bash
./gradlew test
./gradlew runGameTestServer
```

当前版本指定测试全部通过；失败和跳过均有解释。

### Gate G4 — Dedicated Server and Sides

必须：

- 专服启动；
- 玩家可加入；
- 无客户端类加载错误；
- 目标流程可在专服完成；
- 两名玩家状态一致；
- 可选客户端模组缺失不影响服务端。

### Gate G5 — Persistence and Recovery

适用于有持久化状态的版本：

- 保存/重启后数据一致；
- schema 版本正确；
- 旧格式迁移有测试；
- 崩溃恢复不会复制或丢失；
- 不支持的未来/降级格式明确拒绝。

### Gate G6 — Security and Authority

适用于网络、火箭、站点、库存功能：

- C2S 不信任客户端结果；
- 权限、距离、状态和区块加载检查存在；
- 超大/恶意请求安全失败；
- 无任意区块加载；
- 无已知复制；
- 请求有合理频率限制或天然幂等。

### Gate G7 — Performance

当前版本预算通过：

- 世界扫描有硬预算；
- 最大允许结构可处理；
- 无每 tick 全量扫描；
- 无无界缓存；
- 无持续强制加载；
- 性能报告包含环境与采样结果。

### Gate G8 — Manual Player Flow

当前版本的人工测试清单全部执行：

- 截图/录像；
- 构建 hash；
- 测试人和日期；
- 预期与实际；
- 已知问题。

### Gate G9 — Documentation and Release

必须：

- README 状态准确；
- Changelog；
- 安装与依赖；
- 存档兼容说明；
- Known Issues；
- 发布证据；
- checksums；
- GitHub Release 为 pre-release 或 stable 的标记正确；
- 不把开发构建误称 stable。

### Gate 状态格式

```yaml
version: v0.6.0
build: 1.20.1-0.6.0-alpha.1
commit: <sha>
gates:
  G0: PASS
  G1: PASS
  G2: PASS
  G3: PASS
  G4: PASS
  G5: PASS
  G6: PASS
  G7: PASS
  G8: READY_FOR_HUMAN_REVIEW
  G9: NOT_STARTED
overall: READY_FOR_AUDIT
```

### 豁免

Required Gate 不应常规豁免。确需豁免时：

```text
ADR
原因
风险
负责人
用户影响
临时缓解
到期版本
自动失败提醒
```

以下不可豁免：

- 原许可证声明；
- 核心复制漏洞；
- 已知存档损坏；
- 远程崩服；
- 客户端完全决定服务端结果；
- 发布 JAR 包含来源不明资产。

### 发布批准

建议两级确认：

1. Codex/开发者生成证据并标记 `READY_FOR_AUDIT`；
2. 人工审查证据后标记 `PASSED`；
3. 只有 `PASSED` 才创建 tag/release。

单人项目也应保留第二步，避免同一执行会话自证完成。


---

## Source file: `docs/07-SAVE-DATA-AND-NETWORK-VERSIONING.md`

## 07 — Save Data and Network Versioning / 存档与网络版本

### 1. 版本必须分离

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

### 2. 持久化位置

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

### 3. 迁移链

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

### 4. 不支持降级

默认不支持新版本存档回到旧版本。

检测到未来 schema：

- 停止加载相关数据；
- 给出可读错误；
- 不用默认值覆盖；
- 指示恢复备份或升级模组。

### 5. 火箭结构格式

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

### 6. 恢复 journal

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

### 7. 网络策略

`SimpleChannel` 协议只表达客户端/服务端包格式兼容，不等于世界 schema。

建议：

```text
protocol = "1"
accept exact match during pre-alpha
```

后续可允许同一 minor 协议兼容，但需要明确包能力协商。

### 8. 包大小

- C2S 请求只传意图和最小参数；
- 客户端不得提交完整火箭/站点 NBT；
- S2C 大快照应分块、压缩、校验和；
- 接收端先验证声明长度；
- 解压后大小也必须限制；
- 只同步给跟踪玩家或实际需要的界面。

### 9. 数据同步模型

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

### 10. 1.12 世界兼容

`v1.0.0` 不承诺直接加载 1.12.2 世界。

可提供的兼容层优先级：

1. `planetDefs.xml` 导入；
2. 配置/数值转换；
3. 独立离线资产/数据工具；
4. 选择性结构导入；
5. 最后才考虑世界级转换。

不得让旧世界兼容阻塞核心重写。

### 11. 备份要求

首次加载新 schema 前：

- 检测版本；
- 推荐/执行世界数据备份；
- 备份文件名包含时间和旧 schema；
- 限制备份数量；
- 不在失败后删除旧备份。

Beta 和 stable 发布说明必须写明升级前备份。


---

## Source file: `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`

## 08 — Asset License and Provenance / 资产授权与来源

### 1. 资产状态

每个文件只能处于：

```text
NEW
UPSTREAM_AR_MIT
THIRD_PARTY_APPROVED
GENERATED
QUARANTINED
REJECTED
```

`QUARANTINED` 和 `REJECTED` 不得进入 `src/main/resources` 或发布 JAR。

### 2. 来源记录

推荐每个导入批次一个 YAML/JSON：

```yaml
target: src/main/resources/assets/advancedrocketrycommunity/textures/block/rocket_motor.png
status: UPSTREAM_AR_MIT
source_repository: Advanced-Rocketry/AdvancedRocketry
source_branch: 1.12
source_commit: "<sha>"
source_path: src/main/resources/assets/advancedrocketry/textures/blocks/rocketmotor.png
source_sha256: "<hash>"
target_sha256: "<hash>"
license: MIT
transformation:
  - rename namespace
  - rename path to lowercase singular folder convention
reviewer: "<name>"
reviewed_at: "YYYY-MM-DD"
```

### 3. 可复现转换

优先写脚本：

- `.lang` → JSON；
- namespace 改写；
- 大小写规范化；
- recipe 格式转换；
- model/texture 引用重写；
- OBJ/MTL 路径修复。

不要只手工复制后提交。脚本和输出都应被审查。

### 4. 原项目 MIT 的实际操作

原仓库根 LICENSE 为 MIT，并要求在软件副本或重要部分中保留版权和许可声明。因此：

- 根 `LICENSE` 保留原 notice；
- `NOTICE.md` 指向原仓库；
- 导入记录保留来源；
- 二进制发布包含 LICENSE/NOTICE；
- 不删除源文件中已有作者或版权头。

### 5. LibVulpes 边界

原 Advanced Rocketry 深度依赖 LibVulpes，但不应把“依赖关系”误认为“可自动复制”。

在确认 LibVulpes 对应 branch/commit 的许可证前：

- 只记录 API 使用方式；
- 不复制类；
- 不复制 GUI、模型或声音；
- 用新实现替代当前所需能力。

若许可证无法明确，保持 clean-room 风格：依据行为和接口需求重新实现。

### 6. 其他社区项目

下列项目必须各自审计：

- Advanced Rocketry - Reworked；
- ARLib；
- Advanced Rocketry 3；
- 任何模组包 fork；
- 社区修复包；
- 社区汉化；
- Wiki 截图和素材。

“能下载”“公开 GitHub”“作者也是社区成员”都不等于可复制。

### 7. Minecraft 和其他官方资产

不得随 JAR 分发：

- Minecraft 原版纹理、声音、模型或字体副本；
- Mojang/Microsoft Logo；
- Forge Logo（除非遵循其明确使用条件）；
- 从游戏 JAR 提取后略改的资产。

可以通过合法的资源引用、标签、配方和运行时 API 使用原版内容，而不是复制文件。

### 8. 模型策略

#### JSON 模型

优先用于普通方块/物品，便于 DataGen 和引用校验。

#### OBJ/MTL

复杂机器可暂时保留，但必须验证：

- loader 支持；
- MTL 和纹理引用；
- 坐标、朝向、缩放；
- 专服不加载客户端模型类；
- 许可证和来源；
- 渲染性能。

逐步将简单 OBJ 转换为 JSON/Blockbench，而不是一次性重制所有美术。

### 9. 声音

检查：

- OGG 可解码；
- 单/立体声是否符合使用场景；
- `sounds.json` ID；
- 音量与循环；
- 来源；
- 不包含第三方音乐或未许可录音。

### 10. 发布前自动验证

CI 至少验证：

```text
所有资源有 provenance
所有 provenance target 存在
hash 匹配
无 QUARANTINED/REJECTED 文件进入 JAR
JSON 可解析
引用存在
无大小写冲突
LICENSE/NOTICE 被打包
```

### 11. 争议处理

发现来源不清：

1. 立即移入 quarantine；
2. 从发布分支移除；
3. 替换为临时原创占位；
4. 记录已发布版本是否包含；
5. 必要时撤下 release；
6. 取得许可或原创重制后再恢复。


---

## Source file: `docs/09-GITHUB-REPOSITORY-SETUP.md`

## 09 — GitHub Repository Setup / GitHub 建仓与公开声明

### 1. 新仓库还是 Fork

#### 推荐：新建独立仓库

推荐名称：

```text
sunthemoon/AdvancedRocketry-Community
```

原因：

- 本项目是架构重写，不是准备回合并到 1.12 分支的补丁；
- 保持干净的 Java 17/Forge 1.20.1 历史；
- 避免大量旧构建文件和旧 issue 语义；
- 所有导入必须经过清单，能防止无意识搬入；
- 公开身份可明确为社区项目。

通过 `UPSTREAM.md`、`NOTICE.md` 和 provenance 维持来源关系。

#### 可选：GitHub Fork + orphan branch

仅当你非常重视 GitHub fork network 展示时使用。缺点是：

- 历史与新架构混杂；
- compare/PR 默认指向上游会造成噪声；
- 更容易让人误认是官方升级分支；
- 可能限制账号内同网络 fork 管理。

### 2. 建仓顺序

1. 创建空仓库，不自动添加 GitHub LICENSE/README；
2. 先保持 private，完成 `v0.0.1` 审计；
3. 提交本规划包；
4. 设置 About；
5. 添加规则集和 Issue 模板；
6. 运行许可证/来源检查；
7. 准备好 README 非官方声明后再 public；
8. public 后发布一个 “Planning only / no binaries” 的首个说明，而不是可玩 release。

### 3. Repository About

推荐：

```text
Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.
```

Topics：

```text
minecraft
minecraft-mod
forge
forge-mod
minecraft-1-20-1
advanced-rocketry
space
java
community-edition
```

不要在 About 中写 “official port”。

### 4. README 顶部必须声明

```text
Unofficial community rewrite for Minecraft 1.20.1 Forge.
Not an official continuation and not maintained or supported by the original Advanced Rocketry maintainers.
NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
```

### 5. LICENSE / NOTICE / UPSTREAM

根目录至少：

```text
LICENSE
NOTICE.md
UPSTREAM.md
BRANDING_AND_AFFILIATION.md
```

GitHub 只有检测到标准 LICENSE，不代表第三方资产已经合规；provenance 仍是独立 Gate。

### 6. `mods.toml` 声明建议

```toml
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"
issueTrackerURL="https://github.com/sunthemoon/AdvancedRocketry-Community/issues"

[[mods]]
modId="advancedrocketrycommunity"
version="${file.jarVersion}"
displayName="Advanced Rocketry: Community Edition"
displayURL="https://github.com/sunthemoon/AdvancedRocketry-Community"
authors="Advanced Rocketry: Community Edition contributors"
credits="Based on the MIT-licensed Advanced Rocketry project. Unofficial community rewrite; not supported by the original maintainers."
description='''
An unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge.
NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
'''
displayTest="MATCH_VERSION"
features={java_version="[17,)"}
```

依赖：

```toml
[[dependencies.advancedrocketrycommunity]]
modId="forge"
mandatory=true
versionRange="[47.4.10,48)"
ordering="NONE"
side="BOTH"

[[dependencies.advancedrocketrycommunity]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.20.2)"
ordering="NONE"
side="BOTH"
```

### 7. 分支策略

推荐 trunk-based：

```text
main                      # 受保护、始终可构建
codex/v0.4.0-atmosphere-* # 功能分支
fix/v0.6.0-*              # 修复
docs/*                    # 文档
```

不建立长期 `develop`，减少个人/Codex 多会话合并成本。

### 8. Ruleset

`main`：

- 必须 PR；
- 禁止 force push；
- 禁止删除；
- 必须通过 build、test、GameTest、data/provenance checks；
- 要求分支为最新可选；
- 至少一次人工批准：个人仓库可在版本 tag 前人工确认，不必强制 GitHub reviewer；
- 线性历史可选；
- release tag 仅维护者创建。

### 9. Labels

```text
type:bug
type:feature
type:porting
type:test
type:docs
system:build
system:asset
system:machine
system:celestial
system:atmosphere
system:rocket
system:station
system:satellite
risk:save
risk:duplication
risk:performance
risk:license
gate:blocked
gate:ready-for-audit
status:needs-reproduction
good-first-issue
```

### 10. Issue 模板要求

Bug：

- ARCE、Forge、Java 版本；
- 单人/专服；
- 最小模组列表；
- 重现步骤；
- 日志；
- 世界是否可重现；
- 是否涉及复制/存档；
- 客户端与服务端是否一致。

Porting task：

- 目标版本；
- 原行为；
- 上游文件；
- 新模块；
- 非目标；
- 自动测试；
- 人工验收；
- provenance；
- save/network 影响。

### 11. Release 规则

发布 JAR 必须：

- 来自已标记 commit；
- 带 LICENSE/NOTICE；
- 通过版本 Gate；
- 附 `checksums.txt`；
- 附测试报告和 Known Issues；
- alpha/beta 正确标为 pre-release；
- 不附未经审计的整包资源；
- 不把 GitHub Actions 临时 artifact 当正式 release。

### 12. GitHub Security/社区设置

开启：

- Dependabot alerts；
- Secret scanning（公开仓库可用时）；
- Private vulnerability reporting（可用时）；
- Discussions 可等 `v0.6.0` 后；
- Wiki 可暂缓，文档先跟代码同仓库；
- Issues；
- branch/tag protection。

### 13. 初次公开前检查

- [ ] README 第一屏有非官方声明
- [ ] 原 MIT notice 保留
- [ ] GitHub 显示 MIT license
- [ ] 仓库描述不含 official
- [ ] 没有未经审计的上游二进制或资产
- [ ] issue 模板不把用户导向原仓库
- [ ] SECURITY、CONTRIBUTING、CODE_OF_CONDUCT 存在
- [ ] `PROJECT-CONFIG.md` 已从 DRAFT 改为 APPROVED
- [ ] 当前状态明确写“无可玩发布”


---

## Source file: `docs/10-CODEX-EXECUTION-RUNBOOK.md`

## 10 — Codex Execution Runbook / Codex 执行手册

### 1. 为什么按版本驱动

Advanced Rocketry 的系统耦合很强。一次给 Codex “全部搬到 1.20.1”会产生：

- 大量未验证占位代码；
- 自创 API 与旧行为混杂；
- 多个系统同时不完整；
- 测试无法定位；
- 会话过长后丢失约束；
- Git diff 难以审查。

因此每次只处理当前版本或一个 PR 切片。

### 2. 第一次执行

使用：

```text
codex-prompts/00-initialize-repository.md
```

预期只完成：

- 项目身份确认检查；
- 仓库治理文件；
- 状态目录；
- v0.0.1 Gate 证据；
- 不创建 Forge 代码。

### 3. 上游审计

在可访问上游源码的环境执行：

```text
codex-prompts/01-run-upstream-audit.md
```

要求 Codex：

- 锁定 commit；
- 生成 manifest；
- 不复制源码；
- 更新 Porting Matrix；
- 提交审计工具和结果；
- 说明无法确认的许可。

### 4. 实施下一版本

使用：

```text
codex-prompts/02-implement-next-version.md
```

Codex 应自动读取第一项未 PASSED 版本，不要让提示词重新描述所有需求。

若任务过大，先拆 Issue/PR：

```text
v0.5.0:
PR1 model + snapshot
PR2 validation + limits
PR3 transaction + rollback
PR4 entity/render + sync
PR5 tests + release evidence
```

### 5. 审计当前版本

使用：

```text
codex-prompts/03-audit-current-version.md
```

审计会话应与实现会话分开，优先寻找：

- 测试是否真的运行；
- 验收是否被降低；
- 客户端权威；
- 静态世界状态；
- 任意区块加载；
- NBT/网络无上限；
- 未记录资产；
- 保存/重启遗漏；
- 专服客户端类；
- 未来版本越界。

### 6. 发布 Gate

使用：

```text
codex-prompts/04-release-gate.md
```

该提示只生成证据和结论，不应顺便实现新功能。失败时输出阻断项和最小修复计划。

### 7. 会话控制

建议一个会话不超过：

- 一个 PR；
- 一个领域子问题；
- 一次 Gate 审计；
- 一次发布准备。

会话结束前必须更新：

```text
docs/work/<version>-implementation-log.md
docs/status/GATE_STATUS.md
```

不要依赖 Codex 会话本身作为长期记忆。

### 8. 工作树隔离

并行会话推荐 git worktree：

```bash
git worktree add ../arce-v030 -b codex/v0.3.0-celestial
git worktree add ../arce-v040 -b codex/v0.4.0-atmosphere
```

但后续版本不得在前置版本未合并时开始主实现。可并行的通常是：

- 文档；
- 审计工具；
- 独立测试设计；
- 原版行为记录；
- 原创资产草案。

### 9. Codex 不得自行做的决定

- 更换许可证；
- 改项目名/mod id；
- 取消版本 Gate；
- 引入受限许可证依赖；
- 支持动态维度；
- 放宽复制/安全限制；
- 宣布正式发布；
- 删除来源声明；
- 把测试世界兼容承诺升级为稳定承诺。

这些需要人工确认或 ADR。

### 10. 失败时的处理

Codex 遇到无法实现的测试或 API：

1. 保留失败；
2. 定位最小问题；
3. 查询当前 Forge 1.20.1 官方 API；
4. 写风险和备选；
5. 不使用旧 API 猜测；
6. 不通过 mock 掩盖缺失的集成行为；
7. 当前回复尽量完成可验证的部分；
8. 将版本标记 BLOCKED，而不是假定通过。

### 11. 推荐产出格式

```markdown
# Execution Result

## Scope completed
## Scope not completed
## Design decisions
## Files changed
## Tests added
## Commands actually run
## Results
## Gate status
## Evidence
## Risks
## Next action
```

### 12. 何时允许进入下一版

只有：

```text
current version = PASSED
```

不是：

```text
build passes
```

也不是：

```text
most code exists
```


---

## Source file: `docs/11-RISK-REGISTER.md`

## 11 — Risk Register / 风险登记

| ID | 风险 | 概率 | 影响 | 触发信号 | 缓解 | 阻断版本 |
|---|---|---:|---:|---|---|---|
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

### 风险处理规则

- Critical/High 风险必须有测试或明确的结构性缓解；
- “发生概率低”不能替代安全边界；
- 风险关闭需证据，不因代码存在而自动关闭；
- 每个版本审计更新本表；
- 新风险应分配首次阻断版本；
- 接受风险必须 ADR，并写明到期版本。


---

## Source file: `docs/13-BOOTSTRAP-COMMANDS.md`

## 13 — Bootstrap Commands / 推荐建仓命令

> 先确认 `PROJECT-CONFIG.md`。以下使用推荐默认仓库名；实际 owner 不同时替换。

### 1. 新建本地仓库

```bash
mkdir AdvancedRocketry-Community
cd AdvancedRocketry-Community
git init -b main
```

把本规划包内容复制到仓库根目录后：

```bash
git add .
git commit -m "docs: establish community rewrite governance and roadmap"
```

### 2. 使用 GitHub CLI 新建私有仓库

```bash
gh repo create sunthemoon/AdvancedRocketry-Community \
  --private \
  --source=. \
  --remote=origin \
  --push \
  --description "Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers."
```

先 private 完成 `v0.0.1`。公开前执行清单：

```text
docs/09-GITHUB-REPOSITORY-SETUP.md
```

然后再从 GitHub 设置或 CLI 改为 public。

### 3. 添加只读上游 remote

```bash
git remote add upstream-ar https://github.com/Advanced-Rocketry/AdvancedRocketry.git
git fetch upstream-ar 1.12
```

不要执行：

```bash
git merge upstream-ar/1.12
```

上游用于审计，不直接合并到 `main`。

### 4. 开始第一个 Codex 分支

```bash
git switch -c docs/v0.0.1-governance
```

将 `codex-prompts/00-initialize-repository.md` 作为任务输入。

### 5. 后续 worktree 示例

```bash
git worktree add ../arce-v002 -b codex/v0.0.2-forge-bootstrap main
```

一个 worktree/分支只处理一个版本或一个 PR 切片。

### 6. 发布前常用命令

```bash
./gradlew clean build
./gradlew test
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
sha256sum build/libs/*.jar
```

Windows PowerShell 的 SHA-256：

```powershell
Get-FileHash .\build\libs\*.jar -Algorithm SHA256
```

### 7. 不要在第一步做的事

```text
- 不 fork 后直接改旧 build.gradle
- 不复制旧 src/main
- 不导入所有 assets
- 不把原 JAR 放进仓库
- 不创建“正式版” release
- 不把仓库一开始描述为 official port
```


---

## Source file: `docs/PORTING_MATRIX.md`

## PORTING_MATRIX — 功能搬运矩阵

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

### 使用规则

- 完成上游审计后，把“主要位置”替换成准确类/资产路径；
- 每行必须最终指向自动测试和人工用例；
- 状态不能因“代码存在”直接从 PLANNED 跳到 PASSED；
- 新发现功能需增加行，不要塞进“其他”；
- 被推迟的功能不得在当前版本偷偷实现基础框架。


---

## 分版本实施、测试与验收文件


---

## Source file: `docs/versions/V0.0.1-REPOSITORY-BASELINE.md`

## v0.0.1 — 仓库、身份、许可证与治理基线

### 1. 版本目标

在没有任何 Forge 游戏代码的前提下，建立可公开、可追溯、不会冒充官方的社区项目仓库。

#### 玩家可见结果

玩家暂时不会获得可运行 JAR；但仓库能清楚说明项目是谁、从哪里来、当前做到什么程度以及问题该报给谁。

### 2. 前置 Gate

- [ ] 人工检查 `PROJECT-CONFIG.md`
- [ ] 确认准备使用新建独立仓库或记录选择 Fork 的原因

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 创建根目录治理文件：README、PRODUCT、LICENSE、NOTICE、UPSTREAM、BRANDING、CONTRIBUTING、SECURITY、CODE_OF_CONDUCT、AGENTS。
- 建立 `docs/status/`、`docs/work/`、`docs/releases/` 目录和初始状态。
- 设置项目名称、仓库名、display name、mod id、Maven group、包名。
- 记录原 Advanced Rocketry 主上游、branch 和许可证；锁定计划审计的准确 commit。
- 建立 GitHub Issue/PR 模板、标签清单和 branch/ruleset 操作说明。
- 记录一次面向原维护者的礼貌沟通草稿或 outreach 记录；该沟通不是 MIT 使用的法律前置，但有助于名称和社区协调。
- 明确公开前状态为 planning/pre-alpha/no binaries。

### 4. 明确不做

- 不下载或复制旧源码和资产到新项目。
- 不创建 Forge MDK，不生成 JAR。
- 不承诺原作者已批准。
- 不发布 CurseForge/Modrinth 页面。
- 不宣布任何可玩版本。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 检查并将 `PROJECT-CONFIG.md` 的 `identity_status` 从 DRAFT 改为 APPROVED，填写 reviewer/date。
2. 在 README 第一屏放置非官方社区重写声明和 Minecraft 非官方声明。
3. 根 LICENSE 保留原 `Copyright (c) 2017`，并添加社区新增工作声明。
4. 在 NOTICE 中说明：原项目 MIT、导入须 provenance、问题不要提交到原仓库。
5. 在 UPSTREAM 中填入准备审计的 commit SHA；若暂时无法联网，标为 BLOCKED，不猜测 SHA。
6. 创建 `.github/ISSUE_TEMPLATE` 和 PR 模板。
7. 建立 `docs/status/CURRENT_VERSION.md`，当前值为 v0.0.1；建立 Gate 状态。
8. 根据 GitHub 设置文档配置 About、topics、Issues、Security 和 main ruleset。
9. 在 `docs/work/v0.0.1-implementation-log.md` 记录实际设置与截图路径。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] `python` 或简单脚本检查必需文件存在。
- [ ] 检查 README/NOTICE/BRANDING 中均含 `unofficial` 或对应中文非官方声明。
- [ ] 检查 LICENSE 中同时包含原 2017 notice 和 MIT 标准许可段。
- [ ] 检查仓库没有 `.jar`、旧 `src/main/java/zmaster587`、大体积未审计资产。
- [ ] 检查所有 Markdown 内部相对链接可解析。
- [ ] 若建立 provenance validator，空清单应通过，未知导入文件应失败。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 打开 GitHub 首页，确认第一屏不会让访问者误认为官方项目。
- [ ] 确认 About 描述包含 unofficial/community 和 1.20.1 Forge。
- [ ] 确认 GitHub 能识别 LICENSE 为 MIT。
- [ ] 从匿名/未登录视角检查 Issues、Security、Contributing 链接。
- [ ] 检查任何联系原作者的文字礼貌且没有要求对方承担支持。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] `PROJECT-CONFIG.md` 已 APPROVED。
- [ ] README、NOTICE、UPSTREAM 和 LICENSE 之间无冲突。
- [ ] 原 MIT notice 未被删除或改写。
- [ ] 没有来源不明的旧源码、资源或二进制。
- [ ] 所有公开声明明确非官方且不把支持导向原维护者。
- [ ] GitHub About、规则和模板已设置或留下可验证的人工待办证据。
- [ ] G0 通过；版本状态可标为 READY_FOR_AUDIT，人工审核后 PASSED。

### 9. 必须归档的证据

- `docs/releases/v0.0.1/RELEASE-EVIDENCE.md`
- GitHub 首页和 About 截图
- GitHub License detection 截图
- 必需文件检查输出
- 上游 commit 记录
- 项目身份人工确认记录

推荐目录：

```text
docs/releases/v0.0.1/
```

### 10. 推荐 PR 拆分

- PR 1：根治理文档和身份变量
- PR 2：GitHub 模板、状态文件与 Gate 证据

### 11. 失败与回退

若名称、mod id 或声明有争议，在任何代码发布前修改 PROJECT-CONFIG、README、NOTICE 和 GitHub 仓库名；此阶段没有存档或二进制兼容成本。

### 12. Codex 完成报告

```markdown
# v0.0.1 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.0.1
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.0.2-FORGE-BOOTSTRAP.md`

## v0.0.2 — Forge 1.20.1 工程与可复现构建

### 1. 版本目标

建立干净的 Java 17 / Forge 1.20.1 工程，使客户端、专用服务端、DataGen、GameTest 和 CI 都有可运行入口。

#### 玩家可见结果

玩家只能看到一个内容极少的开发者构建，但它能被客户端和专服正确识别，且公开说明没有实际玩法。

### 2. 前置 Gate

- [ ] v0.0.1 PASSED
- [ ] 项目身份变量冻结
- [ ] JDK 17 可用

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 使用 Forge 1.20.1 MDK 新建工程，不升级旧 Gradle。
- 固定 Forge 47.4.10 为发布基线，建立 47.4.23 兼容测试通道。
- 配置 Java 17 toolchain、Gradle wrapper、Maven 坐标和 JAR manifest。
- 创建模组入口、注册聚合器、日志、基础配置和空 GameTest。
- 创建正确的 `META-INF/mods.toml`、`pack.mcmeta`、logo 占位（原创或纯文本生成）。
- 建立 GitHub Actions：build、unit、runData clean、GameTest；专服 smoke 可先脚本化。
- 建立客户端/服务端代码分包和静态检查。
- 将 LICENSE/NOTICE 打包进 JAR。

### 4. 明确不做

- 不导入原版纹理、机器、行星或火箭代码。
- 不创建完整内容注册表。
- 不接入 JEI、Curios、Patchouli 等依赖。
- 不建立 LibVulpes 替代层。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 从官方 Forge 1.20.1 MDK 生成全新 build 文件；删除示例代码。
2. 设置 `minecraft_version=1.20.1`、`forge_version=47.4.10`、Java toolchain 17。
3. 实现 `AdvancedRocketryCommunity` 入口，仅注册事件和配置。
4. 创建 `registry/ModRegistries` 聚合器，即便当前注册为空也能编译。
5. 创建 `client/ClientBootstrap`，只在 Dist.CLIENT 初始化。
6. 设置 `mods.toml` 中 license、authors、credits、display name、description、Java feature 和依赖范围。
7. 设置资源过滤将版本写入 `mods.toml`。
8. 创建最小 GameTest namespace 和一条 sanity 测试。
9. 创建 CI 缓存但不得缓存敏感文件；在 Linux JDK17 运行构建。
10. 创建脚本验证 JAR 中包含 LICENSE/NOTICE 且不包含开发凭据。
11. 创建专服 smoke 指令或 run configuration，验证无 client class linkage。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] `./gradlew clean build`。
- [ ] `./gradlew test`。
- [ ] `./gradlew runData` 后 `git diff --exit-code`。
- [ ] `./gradlew runGameTestServer`。
- [ ] 解包 JAR 检查 mods.toml、LICENSE、NOTICE、pack metadata。
- [ ] 扫描 common 源码中是否导入 `net.minecraft.client`。
- [ ] CI 分别在 Forge baseline 和 latest lane 编译；baseline 为阻断，latest 记录兼容。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 运行客户端开发配置，进入主菜单和 Mods 列表，确认名称、版本、描述和 credits。
- [ ] 创建测试世界，确认没有 ERROR。
- [ ] 启动专用服务端，接受测试 EULA 后完成启动、保存、停止和重启。
- [ ] 客户端连接专服，确认 mod mismatch 规则符合预期。
- [ ] 检查发布 JAR 名称和 GitHub artifact 不带 `NONE`/`unspecified` 版本。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] Java 17 干净环境可构建。
- [ ] baseline Forge 47.4.10 全部自动任务通过。
- [ ] 专服不加载客户端类且能接受玩家连接。
- [ ] `runData` 结果稳定、工作树无 diff。
- [ ] JAR 内有 LICENSE/NOTICE，mods.toml 声明准确。
- [ ] 日志无项目来源 ERROR；可解释 WARN 已记录。
- [ ] 不含任何未经审计上游资产或功能代码。

### 9. 必须归档的证据

- CI run 链接/日志
- 客户端 Mods 页面截图
- 专服启动与重启日志
- JAR SHA-256 和内容清单
- Java/Gradle/Forge 版本输出
- 静态 side 检查输出

推荐目录：

```text
docs/releases/v0.0.2/
```

### 10. 推荐 PR 拆分

- PR 1：MDK、Gradle、mod metadata
- PR 2：测试入口、CI、JAR 审计和专服 smoke

### 11. 失败与回退

若 MDK 或构建基线错误，直接重建工程骨架；不在旧 build 文件上连续打补丁。Forge latest lane 失败不自动改 baseline。

### 12. Codex 完成报告

```markdown
# v0.0.2 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.0.2
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.1.0-ASSET-REGISTRY-BASELINE.md`

## v0.1.0 — 上游审计、资产来源、注册与 DataGen 基线

### 1. 版本目标

完成原项目代码树/资产的可重复审计，并只导入能支撑后续开发的最小内容集。

#### 玩家可见结果

玩家能看到少量基础材料、占位机器外壳或开发物品，所有显示资源完整，但尚无完整机器玩法。

### 2. 前置 Gate

- [ ] v0.0.2 PASSED
- [ ] 上游 1.12 源码可在本地只读访问
- [ ] 准确 upstream commit 可记录

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 实现上游 Java/资产审计工具，生成 `legacy-manifest/`。
- 完成核心领域与 LibVulpes 使用点清单。
- 建立 provenance 数据格式和 CI 校验。
- 导入最小原创或上游 MIT 审计资产：例如 2–4 个材料、机器外壳、开发图标。
- 建立 Blocks、Items、Sounds、Creative Tab 等 DeferredRegister。
- 建立 DataGen：语言、模型、blockstate、loot、recipes、tags。
- 转换少量 `.lang` 或模型引用以验证迁移流水线。
- 建立 `legacy_namespace -> new_namespace` 可复现转换脚本。

### 4. 明确不做

- 不一次导入全部纹理、OBJ、声音和配方。
- 不实现机器处理、维度、真空或火箭。
- 不复制 LibVulpes 类或资源。
- 不追求旧物品 ID/名称完整兼容。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 锁定上游 branch/commit 并写入 UPSTREAM。
2. 实现 Java 文件、包、行数、imports、static state、NBT、network、ASM 扫描。
3. 实现资产类型、hash、尺寸、引用、大小写冲突和孤立资源扫描。
4. 生成 `audit-summary.md`，指出巨型类、核心系统、核心风险和 v1.0 映射。
5. 建立 `docs/provenance/schema.json` 或等价验证格式。
6. 选择最小导入批次，逐文件登记来源与转换。
7. 所有注册使用 DeferredRegister/RegistryObject。
8. 所有可生成 JSON 通过 DataGen 产生；手写数据需说明原因。
9. 建立 asset validator 并加入 CI。
10. 更新 PORTING_MATRIX 中已审计行。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 审计工具对同一上游 commit 运行两次输出一致。
- [ ] 所有导入 target 均有 provenance，hash 匹配。
- [ ] JSON parse、模型/纹理/声音引用检查。
- [ ] 大小写冲突和 Windows 路径冲突检查。
- [ ] `runData` clean。
- [ ] 注册 sanity GameTests 或启动时 registry asserts。
- [ ] JAR 内容检查：无 QUARANTINED/REJECTED 文件。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 在客户端创意栏查看每个导入方块/物品，无紫黑缺失纹理。
- [ ] 放置、破坏方块，确认模型、粒子、掉落和物品模型。
- [ ] 切换 GUI scale/语言，确认基础语言键正常。
- [ ] 检查一个 OBJ/MTL（若本版选择验证）方向、纹理和性能；也可以明确推迟 OBJ。
- [ ] 人工抽查至少 10 条 provenance 与实际源文件一致。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 准确 upstream commit 已记录。
- [ ] 审计工具和完整 manifest 已提交或以可重复方式生成。
- [ ] 所有发布资源有来源状态和 hash。
- [ ] 无缺失引用、大小写冲突、无来源文件。
- [ ] 注册和 DataGen 基线在客户端/专服均可加载。
- [ ] 导入范围保持最小，没有内容爆炸。
- [ ] PORTING_MATRIX 已从猜测路径升级为准确入口。

### 9. 必须归档的证据

- `legacy-manifest/audit-summary.md`
- manifest CSV/JSON
- provenance CI 输出
- runData clean 输出
- 客户端资源截图
- 随机 provenance 抽查记录

推荐目录：

```text
docs/releases/v0.1.0/
```

### 10. 推荐 PR 拆分

- PR 1：上游审计工具与 manifest
- PR 2：provenance validator
- PR 3：最小注册与 DataGen
- PR 4：首批审计资产导入

### 11. 失败与回退

任何来源不清或引用异常的资产移入 quarantine，并用原创占位替代；不因资产好看而放宽 Gate。

### 12. Codex 完成报告

```markdown
# v0.1.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.1.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.2.0-MACHINE-VERTICAL-SLICE.md`

## v0.2.0 — 单台机器完整垂直切片

### 1. 版本目标

用一台代表性机器验证现代 Forge 的注册、能力、配方、菜单、同步、持久化和自动测试，不复刻完整 LibVulpes。

#### 玩家可见结果

玩家可建造并使用一台基础机器，例如电解机：提供能量、输入物品/流体，经过可见进度得到输出。

### 2. 前置 Gate

- [ ] v0.1.0 PASSED
- [ ] 机器选择和配方在 PRODUCT/版本文档中固定

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 选择一台简单但覆盖物品、流体、能量的机器（推荐 Electrolyzer；若范围过大，可先物品+能量再在补丁加入流体）。
- 实现 MachineBlock、BlockEntity、RecipeType/Serializer、Menu/Screen。
- 实现 `IItemHandler`、`IFluidHandler`、`IEnergyStorage` 能力。
- 实现进度、耗能、输入锁定、输出空间、红石/启停基础行为。
- 实现保存重载和客户端最小同步。
- 建立内部最小 machine base，只抽取已出现两次的共性。
- 可选接入 JEI，但 JEI 缺失时必须正常启动。

### 4. 明确不做

- 不实现数十台旧机器。
- 不建立通用可视化 GUI DSL。
- 不实现所有多方块结构。
- 不做火箭燃料链完整平衡。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 先写纯 Java recipe/progress 状态模型和验证。
2. 实现 recipe serializer，拒绝非法时间、能耗、输出或流体量。
3. 实现 BlockEntity tick：idle 时不做全量查找，输入变化时缓存 recipe。
4. 明确 capability 方向和 side；避免每次查询创建新 wrapper。
5. 实现菜单的 quick-move 边界，防止 shift-click 复制。
6. 网络只同步 GUI 所需字段，不同步完整 NBT。
7. 保存 progress、energy、fluid、inventory；重载后恢复或按明确策略回退。
8. DataGen 产生配方、模型、loot、语言。
9. 添加一个最小多方块模式接口仅当所选机器确实需要；否则推迟。
10. 为所有失败状态提供日志或 UI 原因。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 纯 Java：recipe 校验、进度、能耗、输出容量、暂停/恢复。
- [ ] Codec/serializer：合法/非法 JSON。
- [ ] GameTest：合法配方产出正确数量。
- [ ] GameTest：能量不足不产出且不吞输入。
- [ ] GameTest：输出满时暂停。
- [ ] GameTest：保存重载保持 inventory/fluid/energy/progress。
- [ ] GameTest：漏斗/管道式自动输入输出边界。
- [ ] GameTest：破坏方块掉落一次，不复制。
- [ ] 专服：两玩家同时打开 GUI 不产生不同状态。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 生存模式完整制作/放置/供能/输入/取出。
- [ ] 断电、输出堵塞、中途退出 GUI、离开区块后回来。
- [ ] 服务器重启后继续处理。
- [ ] GUI 1x–4x scale 检查；进度、能量、流体和错误提示可读。
- [ ] 安装/不安装 JEI 两种启动（如本版接入）。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 处理结果和消耗精确，重复 50 次无复制/丢失。
- [ ] 重启后状态符合文档。
- [ ] GUI 不决定服务端处理结果。
- [ ] 专服无客户端类错误。
- [ ] idle 机器没有明显无界查找或日志刷屏。
- [ ] 机器基础类没有为了未来内容过度抽象。
- [ ] 所有必要自动测试和人工证据完成。

### 9. 必须归档的证据

- 机器 GameTest 报告
- 重启前后 NBT/状态记录
- GUI 截图/短视频
- 50 次处理物料守恒统计
- 性能采样或 tick 说明
- JEI present/absent 日志（若适用）

推荐目录：

```text
docs/releases/v0.2.0/
```

### 10. 推荐 PR 拆分

- PR 1：recipe/domain model
- PR 2：Block/BE/capabilities/persistence
- PR 3：menu/screen/network
- PR 4：DataGen、GameTests、证据

### 11. 失败与回退

若机器基类过重，保留具体实现并删除未使用抽象；优先确保一个机器可靠，而不是维护漂亮但未验证的框架。

### 12. Codex 完成报告

```markdown
# v0.2.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.2.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.3.0-CELESTIAL-DATA-AND-DIMENSIONS.md`

## v0.3.0 — 天体数据、固定月球/空间维度与旧 XML 导入

### 1. 版本目标

建立不依赖数字维度 ID 的天体模型、固定 Moon/Space Level、世界状态和可验证的 XML 导入。

#### 玩家可见结果

开发/测试玩家可通过受控命令进入月球和空间，看到正确环境配置；尚不能用火箭前往。

### 2. 前置 Gate

- [ ] v0.2.0 PASSED
- [ ] 天体/维度 ADR 通过
- [ ] Moon/Space 固定维度策略确认

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- CelestialBodyDefinition、AtmosphereDefinition、OrbitDefinition 等 Codec。
- 数据包 registry 或加载器及验证器。
- Overworld/Moon/Space 三个天体映射。
- Celestial SavedData：发现状态、首次访问等最小状态。
- 固定 Moon 和 Space 维度/维度类型、基础 worldgen/void 方案。
- 基础重力服务和真空 profile 接口（伤害推迟到 v0.4.0）。
- 服务端诊断命令和最小客户端天体快照。
- `planetDefs.xml`/Template.xml 导入器，输出规范 JSON 或验证报告。

### 4. 明确不做

- 不运行时任意注册新维度。
- 不生成完整随机行星。
- 不实现火箭旅行。
- 不实现完整天空渲染或所有环境危险。
- 不承诺所有旧 XML 标签首版支持。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 定义天体 ID 与 Level key 分离的模型。
2. 实现父子存在性、环、重复 ID、无效重力/轨道/大气的验证。
3. 注册/加载固定 Moon 和 Space 数据。
4. 跨维度全局状态存 Overworld SavedData，并带 schemaVersion。
5. 提供 `/arce celestial validate/list/goto` 开发命令；goto 仅 op/开发环境。
6. 实现服务端定义快照并仅同步客户端显示所需字段。
7. XML importer 使用 fixture，不把 DOM 对象泄漏到领域层。
8. 未支持 XML 字段输出明确 warning/error 和路径。
9. Data reload 时定义改变的策略：允许安全显示数据 reload，不在运行中替换已注册 Level。
10. 记录旧数字 dimension ID 只作为导入元数据，不作为新身份。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] Codec JSON round trip。
- [ ] 缺 parent、循环 parent、重复 ID、非法 gravity/ResourceLocation 被拒绝。
- [ ] SavedData 保存/重启保持状态。
- [ ] XML fixtures：最小地月、嵌套 moon、未知字段、非法数字、重复 id。
- [ ] GameTest/集成：Moon/Space Level 可取得且 key 稳定。
- [ ] 专服启动/重启后命令 list/goto 正常。
- [ ] 客户端只接收有限快照；未来 schema 安全失败。
- [ ] 静态扫描禁止持久化数字 dimension id。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 进入 Moon，检查重力基础效果、出生/安全平台策略、时间/天空占位。
- [ ] 进入 Space，确认不会掉入无穷死亡循环；有安全返回命令。
- [ ] 两玩家在不同维度，服务端状态和天体列表一致。
- [ ] 修改合法/非法数据包，确认重载或重启错误可读。
- [ ] 导入一份真实旧 planetDefs.xml，检查转换报告。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] Earth/Moon/Space 使用 ResourceKey 稳定识别。
- [ ] 坏天体数据不会静默生成错误世界。
- [ ] SavedData 重启后准确恢复。
- [ ] 固定维度专服可加载，无客户端类。
- [ ] XML 导入输出可验证规范数据，未知字段明确列出。
- [ ] 没有运行时动态维度或数字 ID 依赖。
- [ ] 性能无每 tick 全天体重建/同步。

### 9. 必须归档的证据

- Codec/validation test report
- Moon/Space 专服截图和日志
- XML fixture 转换结果
- SavedData 重启前后 dump
- 天体快照包大小记录
- ADR-天体维度策略

推荐目录：

```text
docs/releases/v0.3.0/
```

### 10. 推荐 PR 拆分

- PR 1：celestial models/codecs/validation
- PR 2：SavedData 和命令
- PR 3：Moon/Space fixed dimensions
- PR 4：XML importer 与集成测试

### 11. 失败与回退

若动态数据包 registry 方案在 1.20.1 行为不稳，退回自有 Codec loader + 固定 Level；不为追求任意行星破坏稳定基线。

### 12. Codex 完成报告

```markdown
# v0.3.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.3.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.4.0-VACUUM-LIFE-SUPPORT-ATMOSPHERE.md`

## v0.4.0 — 真空、生命保障与预算化密闭空间

### 1. 版本目标

实现服务端权威的真空生存、基础宇航服/氧气和不会无界卡服的密闭房间检测。

#### 玩家可见结果

玩家进入月球/空间时需要生命保障；在正确密闭且供氧的房间内可以安全呼吸，打破墙后恢复真空。

### 2. 前置 Gate

- [ ] v0.3.0 PASSED
- [ ] 大气扫描上限和环境 profile 已确定

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 环境 Breathability 查询和真空伤害。
- 基础宇航服（可先四件套）与氧气罐/容量。
- 氧气 Vent/设备及最小能耗/供氧。
- 分 tick flood fill、dirty queue、sealed volume index。
- 方块密闭标签/接口和门等基础边界规则。
- 玩家 HUD/状态提示（最小）。
- 区块卸载、结构改变和服务器重启策略。

### 4. 明确不做

- 不实现完整多气体混合。
- 不实现压力、毒气、热力学全模型。
- 不扫描无限大基地。
- 不强制加载区块完成密闭检查。
- 不实现地球化。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 定义 VACUUM/BREATHABLE 两态 profile。
2. 服务端每 tick 查询玩家所在 volume；客户端只显示同步结果。
3. 宇航服/氧气状态保存在服务端，伤害与消耗由服务端计算。
4. 方块变化只标记 dirty，不同步全量扫描。
5. VolumeScanTask 保存 queue/visited/bounds/budget/cancel state。
6. 遇未加载 chunk 返回 UNKNOWN/PENDING，不加载 chunk。
7. 超过 maxAtmosphereVolume 明确失败并提示玩家。
8. volume 结果按 chunk section/position 索引并在边界变化时失效。
9. 设备停止/断电/拆除后，breathable 状态按文档延迟或立即失效。
10. 防止多个 vent 重复扫描同一空间。
11. 提供 debug 命令显示 volume id、状态、节点数、失败原因。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] GameTest：5×5×5 密闭房间供氧后在限定 tick 内 breathable。
- [ ] GameTest：打破一面墙后在限定 tick 内 vacuum。
- [ ] GameTest：开门/关门边界。
- [ ] GameTest：超过最大体积返回 TOO_LARGE，不继续增长。
- [ ] GameTest：遇未加载 chunk 不强制加载。
- [ ] GameTest：两个相邻房间分隔/连通。
- [ ] GameTest：两个 vent 不复制 volume 或倍增氧气。
- [ ] GameTest：无宇航服受伤，完整装备+氧气安全，氧气耗尽后受伤。
- [ ] 重启测试：设备、氧气和 volume 缓存按策略恢复/重建。
- [ ] 性能：节点数每 tick 不超过配置预算。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 在 Moon/Space 无装备、部分装备、完整装备分别测试。
- [ ] 搭建房间，观察供氧进度和失败原因。
- [ ] 打破墙、开门、离开区块、重启服务器。
- [ ] 两玩家同时进出同一房间，状态一致。
- [ ] 16 个 vent 压力场景运行至少 5 分钟，记录 TPS/日志。
- [ ] 客户端 HUD 在不同 GUI scale 可读。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 所有真空判定由服务端完成。
- [ ] 无任意 chunk load。
- [ ] 扫描节点有硬预算和总体积上限。
- [ ] 密闭/破坏转换在文档限定 tick 内完成。
- [ ] 16 vent 场景不出现持续 watchdog 风险或无界队列。
- [ ] 重启后无永久错误 breathable 区域。
- [ ] 玩家能理解 TOO_LARGE、OPEN、PENDING、NO_POWER 等失败。

### 9. 必须归档的证据

- GameTest 结构和报告
- 节点预算/队列性能记录
- 房间开关短视频
- 两玩家专服测试
- 重启前后 volume debug 输出
- HUD 截图

推荐目录：

```text
docs/releases/v0.4.0/
```

### 10. 推荐 PR 拆分

- PR 1：environment/life support domain
- PR 2：vent + scan scheduler
- PR 3：volume index/invalidation
- PR 4：HUD、GameTests、性能证据

### 11. 失败与回退

若增量 volume 缓存不可靠，可在有预算的前提下回退为 dirty 后重扫；不得回退为每 tick 全房间扫描或强制加载区块。

### 12. Codex 完成报告

```markdown
# v0.4.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.4.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.5.0-ROCKET-ASSEMBLY.md`

## v0.5.0 — 火箭结构、统计与事务式组装/拆解

### 1. 版本目标

将世界方块安全地转换为同维度 RocketEntity，并可恢复原结构；此版不跨维度飞行。

#### 玩家可见结果

玩家可用组装机验证一枚方块火箭、看到质量/推力/错误，组装成实体并在原地安全拆解。

### 2. 前置 Gate

- [ ] v0.4.0 PASSED
- [ ] 火箭安全上限批准
- [ ] 第三方 BlockEntity 默认拒绝策略确认

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- RocketStructureValidator、Snapshot、Stats。
- 可移动/不可移动/引擎/燃料箱/座椅/导航等 tags。
- RocketAssemblyTransaction 和区域锁。
- 受控 BlockEntity 适配器，首版支持原生容器或项目自有方块。
- RocketEntity 最小生命周期和缓存渲染。
- 同维度组装、移动极小范围/静态展示、原地拆解。
- 可读失败诊断和 debug dump。
- 快照 NBT schema、hash 和大小限制。

### 4. 明确不做

- 不发射、不燃料旅行、不跨维度。
- 不支持任意第三方 BlockEntity。
- 不支持超大飞船或空间站整体移动。
- 不实现复杂物理碰撞。
- 不由客户端提交结构快照。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 从组装机/扫描器定义可控扫描范围和边界。
2. 扫描只访问已加载区块，限制方块数和包围盒体积。
3. 构建 palette + relative positions + approved BE data。
4. 服务端重算质量、推力、燃料容量、座位等统计。
5. ValidationResult 包含 code、位置、参数和本地化键。
6. 事务顺序：validate→snapshot→lock→extract→spawn→commit。
7. 注入可测试失败点：snapshot 后、提取中、spawn 失败、commit 前。
8. 回滚恢复 BlockState、受控 BE 数据、容器物品并清理 partial entity。
9. 拆解也使用事务，目标区域被占用时拒绝或选择明确安全策略。
10. RocketEntity 只持有 snapshot ref/state，不承担全部业务。
11. 渲染结构缓存按 snapshot hash 重建，不每帧烘焙。
12. 限制网络同步到 tracking players；大结构数据分块/校验。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 合法最小火箭验证成功。
- [ ] 无引擎/无座椅/超界/含禁用方块返回准确错误。
- [ ] 扫描未加载 chunk 安全失败且不加载。
- [ ] Snapshot NBT round trip 和大小边界。
- [ ] 容器物品组装/拆解前后守恒。
- [ ] 每个注入失败点均完整回滚。
- [ ] spawn 失败不删除世界结构。
- [ ] 拆解目标被占用不覆盖其他方块。
- [ ] 重复/重放 assemble 请求幂等或安全拒绝。
- [ ] 客户端伪造 stats/blocks 被忽略。
- [ ] 最大结构扫描受预算/时间边界。
- [ ] 专服两玩家同时请求同一区域，只有一个事务成功。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 构建多种合法/非法火箭，检查高亮错误位置和说明。
- [ ] 带箱子、机器外壳、座椅组装/拆解。
- [ ] 中途断开玩家、卸载区块、停止服务端后检查恢复策略。
- [ ] 两玩家同时点击组装。
- [ ] 观察最大允许结构的扫描和渲染。
- [ ] 客户端重新连接后火箭模型和 stats 一致。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 100 次最小火箭组装/拆解无方块或物品差异。
- [ ] 所有故障注入点无复制/丢失。
- [ ] 第三方 BE 默认拒绝，错误可读。
- [ ] 结构和 NBT 上限实际生效。
- [ ] 服务端是 snapshot/stats 唯一来源。
- [ ] 同一区域并发事务不会双重提取。
- [ ] 专服重启不会留下无法处理的永久锁或 partial entity。

### 9. 必须归档的证据

- 物料守恒统计
- 故障注入测试报告
- snapshot NBT 示例与大小
- 并发组装日志
- 最大结构性能记录
- 火箭渲染截图/视频

推荐目录：

```text
docs/releases/v0.5.0/
```

### 10. 推荐 PR 拆分

- PR 1：snapshot/stats/validator
- PR 2：assembly transaction + rollback
- PR 3：RocketEntity + rendering/sync
- PR 4：disassembly、并发、证据

### 11. 失败与回退

若 RocketEntity 渲染复杂，可先用简化轮廓/方块缓存；不得牺牲事务安全。对不安全 BE 继续拒绝，不为兼容强行复制 NBT。

### 12. Codex 完成报告

```markdown
# v0.5.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.5.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.6.0-EARTH-MOON-ROUNDTRIP.md`

## v0.6.0 — 可靠的地球—月球往返

### 1. 版本目标

完成项目最关键垂直闭环：燃料、发射、跨维度转移、降落、乘客和崩服恢复。

#### 玩家可见结果

玩家可以给方块火箭加注燃料，从地球发射、在月球着陆并返回，服务器重启或玩家掉线后仍能恢复。

### 2. 前置 Gate

- [ ] v0.5.0 PASSED
- [ ] Moon/Space 和真空系统稳定
- [ ] 跨维度事务 ADR 批准

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- RocketFuelState、FuelLoader 和最小燃料配方。
- RocketFlightPlan 和服务端目的地验证。
- 飞行状态机：ASSEMBLED→FUELED→COUNTDOWN→ASCENT→TRANSIT→DESCENT→LANDED。
- 服务端跨维度 RocketTransferTransaction + RecoveryJournal。
- 乘客/座位 UUID 状态、掉线和重连。
- 目的地选择 GUI 与服务器反馈。
- 着陆点检查、阻挡处理、安全回归。
- 降落后拆解、返航和燃料精确消耗。
- 核心声音/粒子/屏幕反馈。

### 4. 明确不做

- 不实现任意行星、跃迁、多恒星。
- 不实现真实轨道物理。
- 不支持大规模载具战斗。
- 不接纳不安全第三方 BE。
- 不扩展高级卫星/空间站玩法。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 服务端根据 stats、fuel、source/destination profile 计算可达性。
2. 客户端仅提交 destination id 和 launch intent。
3. 倒计时期间锁定关键操作；取消/中断有明确回退。
4. 飞行状态机集中列出合法转移和错误。
5. 持久化 flight state、plan、passenger UUID、fuel 和 transaction id。
6. 跨维度前写 recovery journal，目标实体成功后再删除源。
7. 每个事务 phase 幂等，可在重启后恢复。
8. 为 source-only、destination-only、both、neither 四种恢复情况写策略。
9. 乘客掉线时保持座位关联或安全转移到恢复点。
10. 着陆区域必须已加载/安全准备，不因客户端坐标加载任意 chunk。
11. 目的地无法着陆时选择预定义安全 pad 或返回 orbit state，不直接覆盖世界。
12. 燃料只在明确 commit 点消耗一次。
13. 提供 `/arce rocket recover/inspect` 管理命令和审计日志。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 纯 Java：全部合法/非法状态转移。
- [ ] 燃料不足、质量超限、目的地不可达被拒绝。
- [ ] 燃料在成功旅行中只扣一次，失败/回滚不错误扣除。
- [ ] 地球→月球→地球集成/GameTest 可分阶段自动化。
- [ ] 在每个关键状态保存/重启后恢复。
- [ ] journal 四种实体存在组合恢复。
- [ ] 玩家倒计时/上升/转移/下降时掉线重连。
- [ ] 两个乘客座位和 UUID 映射。
- [ ] 两个火箭同时旅行事务互不污染。
- [ ] 重复 launch packet 不重复生成实体。
- [ ] 远距离/无权限/非法 destination 请求拒绝。
- [ ] 目的地区块未加载不由客户端请求强加载。
- [ ] 降落拆解物料守恒。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 专服完整地球→月球→地球 20 次连续往返。
- [ ] 至少 5 次在不同阶段停止服务器并重启。
- [ ] 两名玩家同乘、分别掉线/重连。
- [ ] 两枚火箭同时起飞。
- [ ] 月球着陆点阻挡、返回 pad、取消倒计时、燃料不足。
- [ ] 观察 GUI、声音、粒子、错误提示和服务器日志。
- [ ] 核对每次旅行前后方块、库存、燃料、乘客和实体统计。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 20 次连续往返零永久丢失、零复制、零玩家滞留高空。
- [ ] 关键状态重启矩阵全部通过。
- [ ] 任何恢复路径最终只有一个权威火箭。
- [ ] 乘客掉线不会导致删除 playerdata 才能登录。
- [ ] 燃料和物品守恒。
- [ ] 非法/重放客户端请求不能生成或移动火箭。
- [ ] 核心流程在专服两玩家环境可完成。
- [ ] Known Issues 中没有 Critical/High 核心闭环问题。

### 9. 必须归档的证据

- 20 次往返台账
- 状态重启矩阵报告
- journal 恢复日志
- 双人/双火箭视频
- 物料和燃料守恒统计
- 网络恶意输入报告
- JAR hash 与专服配置

推荐目录：

```text
docs/releases/v0.6.0/
```

### 10. 推荐 PR 拆分

- PR 1：fuel + flight model/state machine
- PR 2：destination UI/server validation
- PR 3：transfer transaction/journal
- PR 4：passengers/landing/disassembly
- PR 5：restart/security/manual evidence

### 11. 失败与回退

若连续跨维度实体飞行动画不可靠，可将 TRANSIT 设计为服务端状态/加载界面，但事务、乘客和物料安全不得退让。发生 Critical 时撤回 pre-release。

### 12. Codex 完成报告

```markdown
# v0.6.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.6.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.7.0-SPACE-STATION.md`

## v0.7.0 — 共享空间维度中的基础空间站

### 1. 版本目标

在单一 Space Level 中创建互不重叠、可持久化、带权限的空间站区域，并通过火箭访问。

#### 玩家可见结果

玩家可创建一座基础空间站、乘火箭进入、扩建、重启后返回；其他玩家的访问和操作受权限控制。

### 2. 前置 Gate

- [ ] v0.6.0 PASSED
- [ ] 共享空间维度策略稳定
- [ ] station region/ownership ADR 通过

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- StationState、StationRegistry SavedData。
- 站点 region allocator 和占用索引。
- 创建站点的物品/流程和基础平台模板。
- 站点所有者、成员和操作权限。
- 火箭目的地选择中的站点条目。
- 站点安全 spawn/landing pad。
- 基础重力/环境 profile；太阳方向或旋转可先为简单状态。
- 删除/孤儿站点的管理策略。

### 4. 明确不做

- 不一站一维度。
- 不实现完整跃迁船。
- 不实现复杂轨道力学。
- 不实现空间电梯。
- 不实现跨服务器站点。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. StationId 使用 ResourceLocation/UUID，不使用递增维度 ID。
2. region allocator 以固定间距或网格分配，持久化 occupied regions。
3. 创建事务：reserve→generate platform→write state→commit；失败释放。
4. 权限检查集中在 StationAccessService。
5. 站点目的地由服务端列出可访问项。
6. 火箭只着陆到站点批准 pad/安全区域。
7. 站点状态包括 owner、members、region、orbitBody、createdAt、schema。
8. 区块未加载时用明确 ticket 策略仅为实际旅行短期加载，不长期泄漏。
9. 管理员命令可 inspect、recover、transfer ownership、delete。
10. 删除站点需备份/确认，避免误删其他 region。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 创建两个/多个站点 region 不重叠。
- [ ] 重启后 allocator 不重复分配。
- [ ] 并发创建只有唯一 region。
- [ ] 无权限玩家不能修改/选择私有站点。
- [ ] 成员权限生效且移除后立即失效。
- [ ] 火箭到达正确 landing pad。
- [ ] 失败生成回滚 reserved region。
- [ ] 删除站点不影响邻站。
- [ ] SavedData schema/migration round trip。
- [ ] 区块 ticket 在旅行完成后释放。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 两名玩家分别创建站点并扩建。
- [ ] 邀请、拒绝、移除成员。
- [ ] 地球↔站点、月球↔站点旅行。
- [ ] 服务器重启、站点区块卸载后返回。
- [ ] 检查安全出生、真空/重力和基础视觉。
- [ ] 管理员恢复/转移所有权流程。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 至少 10 个站点分配无重叠。
- [ ] 重启/并发不会重复 region。
- [ ] 权限在客户端绕过尝试下仍由服务端阻止。
- [ ] 火箭不会落入其他站或虚空不可恢复位置。
- [ ] 无永久 chunk ticket 泄漏。
- [ ] 站点和所有权存档可靠。
- [ ] 没有 Critical/High 权限或区域破坏问题。

### 9. 必须归档的证据

- region allocation map/dump
- 10 站点测试报告
- 权限矩阵
- ticket 释放记录
- 重启前后 StationState
- 双人站点视频

推荐目录：

```text
docs/releases/v0.7.0/
```

### 10. 推荐 PR 拆分

- PR 1：station state/registry/allocator
- PR 2：creation transaction/platform
- PR 3：permissions/destination/landing
- PR 4：admin/recovery/tests/evidence

### 11. 失败与回退

若自由 station region 布局不稳，可先固定网格和单 landing pad；不回退到每站动态维度。

### 12. Codex 完成报告

```markdown
# v0.7.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.7.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.8.0-PROGRESSION-SATELLITES.md`

## v0.8.0 — 基础研究、数据与卫星任务

### 1. 版本目标

恢复 Advanced Rocketry 的基础科技推进感，提供至少一种可发射卫星和不强制区块加载的异步任务。

#### 玩家可见结果

玩家可生产研究数据、组装/发射数据卫星，等待任务完成并获得用于解锁或发现内容的结果。

### 2. 前置 Gate

- [ ] v0.7.0 PASSED
- [ ] 卫星/任务 SavedData 设计批准
- [ ] 基础机器链可支撑配方

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- Progression/ResearchData 服务。
- SatelliteDefinition、SatelliteState、MissionState。
- 至少一种 Data Satellite；可选第二种 Solar/Scanner 仅在范围允许时。
- 卫星容器/组装/发射/回收或地面接收流程。
- 基于 game time 的离线任务，不依赖实体持续 tick。
- 基础 GUI、状态和失败说明。
- 与天体发现/目的地列表的最小联动。
- 必要机器/配方最小扩展。

### 4. 明确不做

- 不恢复全部旧卫星。
- 不实现复杂小行星采矿。
- 不实现地球化卫星。
- 不让任务强制加载目标区块。
- 不一次恢复完整研究科技树。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 定义数据/研究资源的明确来源和消耗。
2. SatelliteDefinition 数据驱动，运行状态独立 SavedData。
3. MissionState 记录 start/end、目标、payload、owner、status、schema。
4. 服务端按时间差结算，不每 tick 遍历所有任务；使用优先队列或分桶。
5. 世界时间回拨/命令修改时使用单调或防负差策略。
6. 任务结果只 commit 一次，重复请求幂等。
7. 玩家离线时任务可按文档继续；不依赖玩家/区块在线。
8. 目的地发现只修改服务端 CelestialState，再同步。
9. 卫星丢失/损坏有管理员 inspect/recover。
10. 配方和 GUI 保持最小，不扩展所有旧机器。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] MissionState Codec/NBT round trip。
- [ ] 任务开始/完成/取消状态机。
- [ ] 重启前后完成时间一致。
- [ ] 玩家离线完成后只领取一次。
- [ ] 重复领取/重放 packet 不复制。
- [ ] 100 个任务不强制加载 chunk。
- [ ] 世界时间回拨不会产生负数或无限任务。
- [ ] 无权限玩家不能领取他人任务。
- [ ] 天体发现状态只在合法结果后改变。
- [ ] 可选卫星类型 absent/present 数据验证。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 完整制造→组装→发射→等待→领取流程。
- [ ] 发射后退出服务器、重启、重新登录。
- [ ] 两玩家各自任务和权限。
- [ ] 同时运行大量任务观察 TPS/内存。
- [ ] 查看天体选择界面更新。
- [ ] 故意断电/无接收器/错误目标，检查提示。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 基础卫星闭环可完成。
- [ ] 任务不持有永久 chunk ticket。
- [ ] 100 个任务压力测试无全量每 tick 扫描。
- [ ] 重启/离线/重复领取无复制。
- [ ] 研究与天体发现由服务端权威。
- [ ] 新增机器/内容没有破坏 v0.6 地月闭环。
- [ ] 范围仍是基础系统，不是旧版全内容复刻。

### 9. 必须归档的证据

- 任务调度性能报告
- 100 任务 chunk/tick 记录
- 离线/重启领取测试
- 重复领取安全测试
- 完整玩法视频
- 新增配方/资产 provenance

推荐目录：

```text
docs/releases/v0.8.0/
```

### 10. 推荐 PR 拆分

- PR 1：research/satellite models
- PR 2：mission scheduler/persistence
- PR 3：launch/receive/gameplay
- PR 4：UI、stress、evidence

### 11. 失败与回退

若卫星实体在轨表现复杂，首版使用 SavedData 中的逻辑卫星和 UI 表示；不为视觉实体引入 chunk/tick 依赖。

### 12. Codex 完成报告

```markdown
# v0.8.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.8.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V0.9.0-BETA-HARDENING.md`

## v0.9.0 — Beta 稳定化、性能、兼容和迁移

### 1. 版本目标

停止扩大核心范围，集中消除阻断缺陷、验证多人专服、性能预算、存档迁移、配置和基础兼容。

#### 玩家可见结果

获得可用于独立测试服或小型模组包的 Beta，已知限制明确，核心闭环应稳定。

### 2. 前置 Gate

- [ ] v0.8.0 PASSED
- [ ] 功能冻结清单批准
- [ ] Beta 支持策略明确

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 冻结 v1.0 功能，修复 Critical/High。
- 跨版本存档迁移和备份。
- Dedicated server 多人 soak。
- 最大结构/房间/站点/任务压力测试。
- Forge baseline/latest 兼容。
- JEI 等已承诺可选兼容。
- 配置、日志、诊断、错误消息。
- 语言、可访问性和资源完整性。
- 发布/崩溃报告模板和 Known Issues。
- 安全审计：复制、包滥用、权限、chunk loading。

### 4. 明确不做

- 不新增跃迁、地球化、黑洞等卖点。
- 不因 Beta 反馈随意重写全部架构。
- 不承诺所有模组包兼容。
- 不支持 1.12 世界直接升级。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 建立 release candidate 分支/标签规则。
2. 对 v0.5–v0.8 持久化格式建立 migration fixtures。
3. 启动时检查旧 schema、备份、迁移、失败回滚。
4. 运行静态依赖/side/packet/provenance 检查。
5. 优化实际 profile 中热点，不做无证据微优化。
6. 检查所有配置范围、默认值和服务器同步。
7. 整理日志级别，默认不刷 debug。
8. 完成 JEI present/absent、Forge baseline/latest 和最小兼容矩阵。
9. 对所有核心错误提供玩家可读消息和诊断 ID。
10. 整理安装、服务端、备份、报告 bug 文档。
11. 进行外部 Beta 反馈时要求 build hash 和最小复现。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 全量 unit/GameTest/data/provenance。
- [ ] 所有 migration fixture。
- [ ] baseline/latest build and GameTest。
- [ ] JAR reproducibility/contents/checksum。
- [ ] 静态 common-client import。
- [ ] 恶意 packet corpus。
- [ ] 最大 NBT/结构/volume 限制。
- [ ] 核心 v0.6 回归套件。
- [ ] 站点/卫星权限和 chunk ticket 回归。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 至少 2 小时专服 soak，推荐 4 名玩家或多客户端模拟。
- [ ] 地月往返、站点和卫星全流程重复。
- [ ] 最大允许火箭、16 vents、10 stations、100 missions 组合场景。
- [ ] 从上一 Alpha 测试存档备份→迁移→继续玩法。
- [ ] 客户端中/低配置视觉与 GUI scale。
- [ ] Forge 47.4.10 和 47.4.23 两套实例。
- [ ] JEI 安装/不安装。
- [ ] 断电式停止/强杀测试仅在备份测试世界进行。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] 零已知 Critical/High。
- [ ] 2 小时 soak 无存档损坏、watchdog、持续内存增长或 ticket 泄漏。
- [ ] 核心地月闭环回归通过。
- [ ] 上一支持 Alpha/Beta schema 可迁移或被明确安全拒绝。
- [ ] baseline Forge 全通过；latest lane 结果记录。
- [ ] 公开 Known Issues、备份和不兼容说明准确。
- [ ] 所有发布资产来源清晰。

### 9. 必须归档的证据

- Beta RC test report
- 2 小时 soak metrics/logs
- migration backup/restore report
- compatibility matrix
- security audit
- Known Issues
- release candidate checksum

推荐目录：

```text
docs/releases/v0.9.0/
```

### 10. 推荐 PR 拆分

- PR 1：migration/backup
- PR 2：security/performance fixes
- PR 3：compatibility/config/docs
- PR 4：RC evidence and release preparation

### 11. 失败与回退

发现 Critical 时冻结 RC、撤下推荐、从最后安全存档 schema 修复；不在同一 RC 中加入新功能抵消问题。

### 12. Codex 完成报告

```markdown
# v0.9.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v0.9.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## Source file: `docs/versions/V1.0.0-COMMUNITY-MVP.md`

## v1.0.0 — 社区 MVP 正式发布

### 1. 版本目标

发布具有明确稳定承诺、完整归属、可重复构建和核心闭环证据的第一个正式版本。

#### 玩家可见结果

获得可在 Minecraft 1.20.1 Forge 小型/中型服务器使用的稳定社区版核心体验。

### 2. 前置 Gate

- [ ] v0.9.0 PASSED
- [ ] 所有 v1.0 范围冻结
- [ ] 发布负责人完成人工 Gate 审核

前置版本未 `PASSED` 时，本版本只能进行文档、测试设计或不产生主线依赖的审计工作。

### 3. 本版范围

- 最终 README、安装、服务端、备份、配置、玩法和问题报告文档。
- 稳定版本号、changelog、Git tag、GitHub Release。
- JAR、sources（如发布）、checksums、LICENSE/NOTICE。
- v1.0 支持/存档兼容承诺。
- 最终 G0–G9 证据归档。
- 确认下载渠道和唯一 issue tracker。
- 发布后补丁流程。

### 4. 明确不做

- 不在 release commit 添加新功能。
- 不宣称与所有模组兼容。
- 不宣称原项目官方续作。
- 不承诺 1.12 世界兼容。
- 不同时发布 1.20.1 NeoForge/Fabric。

任何“不做”项若确需提前，必须单独 ADR，并说明为什么不破坏当前版本收敛。

### 5. 实施顺序

1. 将版本改为 `1.20.1-1.0.0`，确认 manifest/mods.toml。
2. 生成最终 changelog：新增、已知限制、存档、依赖、升级路径。
3. 从干净 checkout 构建 release JAR。
4. 验证 JAR 内容、LICENSE/NOTICE/provenance。
5. 运行最终 full test + dedicated + manual smoke。
6. 生成 SHA-256。
7. 创建 signed/annotated tag（可用时）。
8. GitHub Release 不标 pre-release；附所有证据链接。
9. 更新 README 状态为 stable MVP，但保留非官方声明。
10. 建立 `1.0.x` bugfix policy 和下一个 `1.1` 路线，不在同一 release 中实现。

Codex 应将这些步骤拆成小提交，不应在一个不可审查提交中同时完成模型、网络、渲染、资产和测试。

### 6. 自动测试

- [ ] 干净 checkout full build/test/runData/GameTest。
- [ ] baseline/latest lanes。
- [ ] release JAR content/provenance/checksum。
- [ ] 迁移 fixtures。
- [ ] 全量安全回归。
- [ ] 核心地月、站点、卫星自动套件。
- [ ] Git tag 与 JAR version 一致检查。

所有打勾项必须有实际命令、日志或测试报告，不以代码存在代替运行结果。

### 7. 人工/专服测试

- [ ] 新建纯净客户端/专服安装。
- [ ] 新世界完整地月往返。
- [ ] 创建站点和基础卫星。
- [ ] 重启/重连。
- [ ] 从最近 Beta 存档升级。
- [ ] 核对 README 安装步骤由未参与开发者执行。
- [ ] 检查 release 页面第一屏非官方声明和 Known Issues。

使用 `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` 记录构建 hash、步骤、预期、实际和证据。

### 8. 通过确认

版本只有全部满足下列条件，才可由人工标记 `PASSED`：

- [ ] G0–G9 全部 PASS。
- [ ] 零 Critical/High 已知问题。
- [ ] 干净安装与 Beta 升级均通过。
- [ ] 核心玩家流程有最终证据。
- [ ] 发布 JAR 可从 tag 重建。
- [ ] LICENSE/NOTICE/来源完整。
- [ ] 存档兼容和支持范围写明。
- [ ] 发布页无官方误导。
- [ ] release 后问题有明确接收位置。

### 9. 必须归档的证据

- `docs/releases/v1.0.0/` 全套文件
- 最终 CI runs
- release JAR SHA-256
- tag/commit
- 完整玩法视频
- Beta migration report
- 公开 release 页面截图

推荐目录：

```text
docs/releases/v1.0.0/
```

### 10. 推荐 PR 拆分

- PR 1：release docs/version freeze
- PR 2：final evidence/checksums
- 人工步骤：tag 与 GitHub Release

### 11. 失败与回退

若发布后发现 Critical，立即将 release 标记有问题/撤下推荐，发布安全说明并准备 v1.0.1；不要修改已有 tag 对应文件。

### 12. Codex 完成报告

```markdown
# v1.0.0 Execution Result

## Implemented
## Not implemented
## Explicit non-goals preserved
## Design decisions / ADRs
## Files changed
## Tests added
## Commands actually run
## Test results
## Dedicated/manual results
## Provenance changes
## Save/network changes
## Gate status
## Evidence paths
## Blocking risks
```

### 13. 版本状态

```yaml
version: v1.0.0
status: PLANNED
commit: ""
build: ""
required_gates: []
human_approved_by: ""
human_approved_at: ""
```


---

## 初始架构决策记录（ADR）


---

## Source file: `docs/decisions/ADR-000-PROJECT-IDENTITY.md`

## ADR-000 — Project identity and namespace

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.0.1
```

### Context

The original project is MIT-licensed, but a community rewrite must avoid appearing official or colliding with a future official port. Reusing the original mod id would simplify asset paths but increases identity and compatibility ambiguity.

### Decision

Default identity:

```text
repository: AdvancedRocketry-Community
display name: Advanced Rocketry: Community Edition
mod id: advancedrocketrycommunity
legacy namespace: advancedrocketry
Java package: io.github.sunthemoon.advancedrocketrycommunity
```

Use the original name only with visible unofficial attribution. Do not use the original mod id unless a later ADR is supported by maintainer communication and conflict analysis.

### Consequences

- Asset import scripts must rewrite namespace/path.
- Existing 1.12 IDs are not silently treated as compatible.
- The project can be renamed with lower technical cost.
- Datapacks written for a hypothetical official `advancedrocketry` 1.20.1 mod are not automatically compatible.

### Validation

- [ ] README/NOTICE/About consistent
- [ ] mods.toml consistent
- [ ] no original package root
- [ ] provenance records legacy namespace


---

## Source file: `docs/decisions/ADR-001-FIXED-DIMENSIONS.md`

## ADR-001 — Fixed Moon and Space dimensions for the MVP

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.3.0
```

### Context

Advanced Rocketry historically supports dynamically defined planets. Minecraft/Forge 1.20.1 dynamic registries and world loading make arbitrary runtime dimensions a high-risk foundation, especially for saves and dedicated servers.

### Decision

The MVP registers fixed Moon and Space dimensions through data. Celestial bodies are logical domain objects and may map to a Level or a region in a shared Level.

No arbitrary runtime Level registration before a post-v1.0 ADR and prototype.

### Consequences

- Earth–Moon–Space core loop can be made stable.
- `planetDefs.xml` imports definitions but cannot create arbitrary runtime dimensions.
- Future planets may use generated datapacks requiring restart or shared-dimension instances.


---

## Source file: `docs/decisions/ADR-002-ROCKET-TRANSACTIONS.md`

## ADR-002 — Transactional rocket assembly and transfer

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.5.0
```

### Context

A block-built rocket moves world blocks, inventories, passengers, and entities. Partial failure can duplicate or destroy data. Cross-dimension transfer adds crash windows.

### Decision

Both assembly/disassembly and dimension transfer use explicit phase-based transactions with durable recovery journals where needed.

The client never supplies the authoritative snapshot. Third-party BlockEntities are denied unless an adapter is approved.

### Consequences

- More implementation work before flight is visible.
- Failure injection and restart testing are required.
- Critical duplication bugs have a defined invariant and recovery path.


---

## Source file: `docs/decisions/ADR-003-ATMOSPHERE-BUDGET.md`

## ADR-003 — Budgeted atmosphere scanning

```yaml
status: PROPOSED
date: 2026-08-26
target_version: v0.4.0
```

### Context

Flood-filling sealed rooms can visit very large volumes and freeze a server. Rechecking every tick is unnecessary and dangerous.

### Decision

Block changes mark regions dirty. Scans run as resumable tasks with per-tick node budgets, a maximum total volume, and no forced chunk loads.

Initial states are VACUUM/BREATHABLE; complex gas simulation is deferred.

### Consequences

- Atmosphere changes may take several ticks.
- UI must expose PENDING/TOO_LARGE/OPEN states.
- Cache invalidation and chunk-boundary tests are core functionality.


---

## Codex 执行提示词


---

## Source file: `codex-prompts/00-initialize-repository.md`

## Codex Task 00 — Initialize the repository governance baseline

Read these files first:

1. `PROJECT-CONFIG.md`
2. `AGENTS.md`
3. `PRODUCT.md`
4. `docs/09-GITHUB-REPOSITORY-SETUP.md`
5. `docs/versions/V0.0.1-REPOSITORY-BASELINE.md`
6. `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`

Your task is to complete **only v0.0.1**.

Requirements:

- Inspect the current repository before editing.
- Do not create Forge/Java source code.
- Do not copy upstream source or assets.
- Validate that project identity fields are approved; if still DRAFT, report it as a human blocking item and complete all non-conflicting work.
- Create/update status, work log, release evidence, internal link checks, required GitHub templates, and license/notice consistency checks.
- Record the exact upstream `1.12` commit only if you can verify it. Never invent a SHA.
- Check that README, NOTICE, UPSTREAM, BRANDING, LICENSE, and GitHub templates do not imply official status.
- Add a deterministic repository-doc validation script if practical.
- Run every check you add.
- Stop at `READY_FOR_AUDIT`; do not mark PASSED or create a release.

End with the exact report format required by `AGENTS.md`.


---

## Source file: `codex-prompts/01-run-upstream-audit.md`

## Codex Task 01 — Run the upstream code and asset audit

Read:

- `AGENTS.md`
- `UPSTREAM.md`
- `docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md`
- `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`
- `docs/PORTING_MATRIX.md`
- `docs/versions/V0.1.0-ASSET-REGISTRY-BASELINE.md`

Use an exact local checkout of `Advanced-Rocketry/AdvancedRocketry` branch `1.12`.

Tasks:

1. Verify and record the exact upstream commit.
2. Build deterministic audit scripts under `tools/audit/`.
3. Generate the complete `legacy-manifest/` outputs required by the audit document.
4. Identify LibVulpes imports, ASM/coremod points, mutable global world state, integer dimension IDs, large classes, network packets, NBT and client/common coupling.
5. Audit assets, references, case collisions, duplicate hashes, model/texture/sound chains, and possible third-party origins.
6. Update `docs/PORTING_MATRIX.md` with exact class/file paths and target versions.
7. Do not copy source or assets into the new mod in this task.
8. Clearly quarantine anything whose license cannot be confirmed.
9. Run the tools twice and verify stable output.
10. Produce `legacy-manifest/audit-summary.md` and a v0.1.0 work log.

Do not claim the entire v0.1.0 milestone is complete; this task covers the upstream-audit slice only.


---

## Source file: `codex-prompts/02-implement-next-version.md`

## Codex Task 02 — Implement the next incomplete version

Read:

1. `PROJECT-CONFIG.md`
2. `AGENTS.md`
3. `docs/status/CURRENT_VERSION.md`
4. `docs/status/GATE_STATUS.md`
5. `docs/04-VERSION-ROADMAP.md`
6. the document for the first version that is not `PASSED`
7. `docs/05-MASTER-TEST-PLAN.md`
8. `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`
9. system-specific architecture/provenance documents

Implement only that version, or one reviewable PR slice of it.

Before coding:

- inspect the repository and existing implementation;
- list completed, missing, and contradictory requirements;
- list explicit non-goals;
- state save/network/provenance impact;
- split the work if the whole version is too large.

During implementation:

- preserve server authority and hard limits;
- do not copy unapproved sources;
- add tests with the feature, not later;
- do not introduce future-version frameworks;
- keep common/client sides separated;
- update work logs and status evidence.

At the end:

- run all feasible required commands;
- record actual outputs;
- leave failed tests visible;
- mark only `READY_FOR_AUDIT`, never `PASSED`;
- use the `AGENTS.md` completion report format.


---

## Source file: `codex-prompts/03-audit-current-version.md`

## Codex Task 03 — Audit the current version independently

Act as a skeptical reviewer. Read the current version document and all claimed evidence.

Do not begin by fixing code. First verify:

- requirements were not silently weakened;
- commands were actually run;
- tests assert meaningful behavior;
- dedicated server and restart claims have evidence;
- client requests are not treated as authority;
- no arbitrary chunk loading exists;
- variable NBT/network/world scans have limits;
- persistent objects have schema versions;
- rollback/recovery paths preserve blocks, items, fluids, entities, and passengers;
- common code does not load client classes;
- imported files have exact provenance;
- no later-version scope was added;
- no Critical/High issue remains.

Re-run relevant commands and inspect diffs/logs.

Output:

1. findings ordered by severity, with file/line references;
2. missing tests and evidence;
3. Gate-by-Gate result;
4. minimal remediation plan;
5. recommendation: `BLOCKED`, `IN_PROGRESS`, or `READY_FOR_HUMAN_APPROVAL`.

Do not mark the version PASSED and do not create a release.


---

## Source file: `codex-prompts/04-release-gate.md`

## Codex Task 04 — Execute the release gate

Read:

- `AGENTS.md`
- `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`
- current version document
- `docs/templates/RELEASE-EVIDENCE-TEMPLATE.md`
- all current work/test/manual/performance reports

This is a verification task, not a feature task.

Actions:

1. Verify repository is clean and commit/build identity is known.
2. Run the full required build/test/DataGen/GameTest suite.
3. Verify dedicated-server, persistence/recovery, security, performance, manual and provenance evidence.
4. Inspect the built JAR and calculate SHA-256.
5. Verify version, tag plan, README status, Known Issues and license statements.
6. Fill `docs/releases/<version>/RELEASE-EVIDENCE.md`.
7. Update Gate status truthfully.
8. Do not hide or fix unrelated failures in this same task; report them as blockers.
9. Recommend either `BLOCKED` or `READY_FOR_HUMAN_APPROVAL`.
10. Do not create or move a Git tag unless the human explicitly performs/approves that separate step.


---

## 执行与验收模板


---

## Source file: `docs/templates/ADR-TEMPLATE.md`

## ADR-XXX — <Decision title>

```yaml
status: PROPOSED|ACCEPTED|SUPERSEDED|REJECTED
date:
deciders:
target_version:
supersedes:
```

### Context

<问题、约束、风险和为什么现在必须决定>

### Decision

<明确选择>

### Alternatives

#### A. <方案>

- 优点
- 缺点
- 风险

#### B. <方案>

- 优点
- 缺点
- 风险

### Consequences

#### Positive

- ...

#### Negative

- ...

### Validation

- [ ] 自动测试
- [ ] 专服测试
- [ ] 性能/安全检查
- [ ] 回退方案

### Revisit when

<哪些条件出现时重审>


---

## Source file: `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md`

## MANUAL TEST CASE — <ID / title>

```yaml
version:
build:
commit:
tester:
date:
environment:
singleplayer_or_dedicated:
players:
mod_list:
world:
```

### Purpose

<要验证的风险/行为>

### Preconditions

- <条件>

### Steps

1. <步骤>
2. <步骤>

### Expected

- <可观察结果>
- <守恒/权限/性能结果>

### Actual

<实际结果>

### Evidence

```text
screenshots:
video:
logs:
state dumps:
```

### Result

```yaml
status: PASS|FAIL|BLOCKED
issue:
notes:
```


---

## Source file: `docs/templates/PERFORMANCE-REPORT-TEMPLATE.md`

## PERFORMANCE REPORT — <version/build>

### Environment

```yaml
cpu:
memory:
os:
java:
forge:
mod:
commit:
jvm_args:
```

### Scenario

```text
world:
players:
duration:
rocket_blocks:
active_vents:
room_volume:
stations:
missions:
```

### Metrics

| Metric | Mean | P95 | Max | Budget | Result |
|---|---:|---:|---:|---:|---|
| Server tick ms | | | | | |
| Atmosphere nodes/tick | | | | | |
| Heap used | | | | | |
| Network bytes/player/s | | | | | |
| Loaded chunks/tickets | | | | | |

### Profile findings

1. ...
2. ...

### Conclusion

```yaml
performance_gate: PASS|FAIL
blocking_findings: []
```


---

## Source file: `docs/templates/RELEASE-EVIDENCE-TEMPLATE.md`

## RELEASE-EVIDENCE — <version>

### Identity

```yaml
version:
build:
commit:
tag:
minecraft:
forge_baseline:
forge_compat_lane:
java:
built_at:
built_by:
jar_sha256:
```

### Gate summary

| Gate | Status | Evidence |
|---|---|---|
| G0 License/Provenance | | |
| G1 Build | | |
| G2 Data/Assets | | |
| G3 Automated Behavior | | |
| G4 Dedicated/Sides | | |
| G5 Persistence/Recovery | | |
| G6 Security/Authority | | |
| G7 Performance | | |
| G8 Manual Flow | | |
| G9 Docs/Release | | |

### Commands actually run

```bash
# paste exact commands
```

### Build result

```text
# summary and log path
```

### Automated tests

```text
test count:
passed:
failed:
skipped:
report:
```

### Dedicated server

```text
environment:
startup:
player_join:
restart:
errors:
log:
```

### Manual tests

See `MANUAL-TEST.md`.

### Performance

See `PERFORMANCE.md`.

### Provenance

```text
new imported files:
validator result:
unresolved:
```

### Save and migration

```text
source schema:
target schema:
backup:
migration result:
downgrade behavior:
```

### Security

```text
duplication:
packet abuse:
permissions:
chunk loading:
```

### Known issues

See `KNOWN-ISSUES.md`.

### Final recommendation

```yaml
recommended_status: READY_FOR_AUDIT
blocking_reasons: []
reviewed_by:
reviewed_at:
```


---

## Source file: `docs/templates/SOURCE-PROVENANCE-TEMPLATE.md`

## SOURCE PROVENANCE — <batch or file>

```yaml
target_path:
status: NEW|UPSTREAM_AR_MIT|THIRD_PARTY_APPROVED|GENERATED|QUARANTINED|REJECTED
source_repository:
source_branch:
source_commit:
source_path:
source_sha256:
target_sha256:
license:
copyright_notice:
transformation: []
reviewer:
reviewed_at:
notes:
```

### Verification

- [ ] Source exists at exact commit
- [ ] License applies to this file
- [ ] Existing notices preserved
- [ ] Transformation reproducible
- [ ] Target references valid
- [ ] No Minecraft official asset copied
- [ ] Included in CI provenance validation


---

## Source file: `docs/templates/TEST-REPORT-TEMPLATE.md`

## TEST-REPORT — <version/build>

### Environment

```yaml
os:
cpu:
memory:
java:
minecraft:
forge:
mod:
commit:
```

### Automated command results

| Command | Exit | Duration | Report |
|---|---:|---:|---|
| `./gradlew clean build` | | | |
| `./gradlew test` | | | |
| `./gradlew runData` | | | |
| `git diff --exit-code` | | | |
| `./gradlew runGameTestServer` | | | |

### Tests

| ID | Layer | Result | Notes |
|---|---|---|---|
| | Unit | | |
| | Codec/NBT | | |
| | GameTest | | |
| | Dedicated | | |
| | Restart | | |
| | Security | | |
| | Performance | | |

### Failures and skips

Every failure/skip must have:

```text
test:
reason:
owner:
blocking:
follow-up:
```

### Log review

```text
project ERROR count:
project WARN count:
accepted warnings:
```

### Conclusion

```yaml
automated_gate: PASS|FAIL
blocking_issues: []
```


---

## Source file: `docs/templates/UPSTREAM-MAINTAINER-OUTREACH.md`

## UPSTREAM MAINTAINER OUTREACH TEMPLATE

> 沟通是社区礼貌和名称协调，不是使用 MIT 代码的强制许可请求。不要要求原维护者承诺支持或参与。

### English

**Subject / issue title**

```text
Heads-up: unofficial Forge 1.20.1 community rewrite plan
```

**Message**

```text
Hi,

I am preparing an unofficial community rewrite of Advanced Rocketry for
Minecraft 1.20.1 Forge.

The plan is to use the original MIT-licensed repository as a behavior and
audited asset reference, preserve the original copyright and license notice,
use a distinct mod id, and direct all support and bug reports to the new
community repository. It will be clearly labeled as unofficial and not
supported by the original maintainers.

No action or support is required from you. I wanted to give you a respectful
heads-up and ask whether you have any preference regarding the use of the
“Advanced Rocketry: Community Edition” working name, project branding, or
coordination with any existing continuation work.

I am happy to adjust the public name or wording if it could cause confusion.

Thank you for creating Advanced Rocketry.
```

### 中文记录摘要

```text
已告知原维护者：
- 目标为 Forge 1.20.1 非官方社区重写；
- 基于原 MIT 仓库；
- 保留版权与许可证；
- 使用独立 mod id；
- 问题不转交原维护者；
- 不要求对方支持；
- 征询名称、品牌和既有续作协调意见。
```

### 记录

```yaml
channel:
url_or_reference:
sent_by:
sent_at:
response:
follow_up:
name_or_brand_constraints:
```


---

## Source file: `docs/templates/VERSION-PLAN-TEMPLATE.md`

## vX.Y.Z — <版本名称>

### 1. 版本目标

<一句话目标>

#### 玩家可见结果

<本版完成后玩家能做什么>

### 2. 前置 Gate

- [ ] <前置版本 PASSED>
- [ ] <ADR/配置/资产准备>

### 3. 本版范围

- <功能>
- <功能>

### 4. 明确不做

- <非目标>
- <非目标>

### 5. 实施顺序

1. 数据模型与验证
2. 服务端领域逻辑
3. 持久化与迁移
4. Forge 注册/适配
5. 网络
6. 客户端
7. DataGen/资产
8. 自动测试
9. 人工测试
10. 发布证据

### 6. 自动测试

- [ ] Unit
- [ ] Codec/NBT
- [ ] GameTest
- [ ] Data/asset
- [ ] Dedicated server
- [ ] Security
- [ ] Performance

### 7. 人工测试

- [ ] <用例>

### 8. 通过确认

- [ ] <可测量标准>
- [ ] 无 Critical/High
- [ ] Required Gate 全通过
- [ ] 证据完整

### 9. 证据

```text
docs/releases/vX.Y.Z/
```

### 10. 回退

<失败时如何恢复，什么不能牺牲>

### 11. 状态

```yaml
version: vX.Y.Z
status: PLANNED
commit: ""
build: ""
human_approved_by: ""
human_approved_at: ""
```


---

## 外部参考来源


---

## Source file: `docs/12-SOURCES.md`

## 12 — Sources / 参考来源

> 资料核对日期：2026-08-26
>
> 本页用于让开发者和 Codex知道哪些外部事实需要重新核对。版本、许可证和平台规则可能变化，公开发布前应再次检查。

### Original Advanced Rocketry

- Repository: https://github.com/Advanced-Rocketry/AdvancedRocketry
- Primary reference branch: https://github.com/Advanced-Rocketry/AdvancedRocketry/tree/1.12
- License: https://github.com/Advanced-Rocketry/AdvancedRocketry/blob/1.12/LICENSE
- Java tree: https://github.com/Advanced-Rocketry/AdvancedRocketry/tree/1.12/src/main/java/zmaster587/advancedRocketry
- Asset tree: https://github.com/Advanced-Rocketry/AdvancedRocketry/tree/1.12/src/main/resources/assets/advancedrocketry

Observed at review time:

- default branch shown as `1.12`;
- root repository is detected as MIT;
- original MIT notice contains `Copyright (c) 2017`;
- repository describes rockets, planets/moons, XML planet configuration, atmospheres, stations, satellites, asteroid mining and terraforming.

The local audit must still lock an exact commit.

### Forge 1.20.1

- Downloads and versions: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
- Getting started / Java 17: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
- Mod files / `mods.toml`: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/
- Registries: https://docs.minecraftforge.net/en/1.20.1/concepts/registries/
- SavedData: https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/
- Codecs: https://docs.minecraftforge.net/en/1.20.1/datastorage/codecs/
- Networking SimpleImpl: https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/
- Data generation: https://docs.minecraftforge.net/en/1.20.1/datagen/
- GameTest: https://docs.minecraftforge.net/en/1.20.1/misc/gametest/

At review time:

- recommended Forge 1.20.1: `47.4.10`;
- latest Forge 1.20.1: `47.4.23`;
- Forge 1.20.1 prerequisites specify Java 17;
- `DeferredRegister` is the recommended registration approach;
- dynamic registry objects are generally data-driven rather than arbitrary runtime registration.

### GitHub licensing and repository setup

- Licensing a repository: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository
- Adding a license: https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/adding-a-license-to-a-repository
- MIT overview: https://choosealicense.com/licenses/mit/

Important operational point:

- a public repository without an explicit license is not automatically open source;
- MIT requires preservation of the copyright and license notice.

### Minecraft naming and disclaimer

- Usage Guidelines: https://www.minecraft.net/en-us/usage-guidelines
- EULA: https://www.minecraft.net/en-us/eula

The Usage Guidelines request a prominent disclaimer similar to:

> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

They also restrict presenting “Minecraft” as the primary/dominant project brand and using official branding in a way that appears official.

### Recheck checklist before public release

- [ ] Original upstream license and branch still match
- [ ] Exact upstream commit recorded
- [ ] Forge recommended/latest versions rechecked
- [ ] Forge documentation still targets Java 17 for 1.20.1
- [ ] Minecraft Usage Guidelines rechecked
- [ ] Every third-party dependency and asset license recorded
- [ ] Public README/description statements remain accurate
