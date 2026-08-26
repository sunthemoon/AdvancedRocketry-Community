# AGENTS.md — Advanced Rocketry 1.20.1 社区重写执行规约

## 1. 项目目标

本仓库面向 Minecraft 1.20.1 Forge，以原 Advanced Rocketry 1.12.2 项目作为行为和可审计资产基线，进行 **新架构重写**。

“重写”意味着：

- 可以参考旧代码的算法、数值、资源和玩家行为；
- 不以“把旧类修到编译通过”为实施方式；
- 先建立现代 Forge 的数据、服务端权威、持久化和测试边界；
- 每次只交付一个可验证的垂直切片。

## 2. 开始任何任务前必须读取

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

## 3. 不可违反的工程约束

### 3.1 范围

- 一个会话、一个工作分支、一个 PR 只处理一个版本或该版本内一个明确子切片。
- 不得提前实现后续版本的功能；可以预留接口，但不得建立未被当前版本使用的大型框架。
- 不得创建完整 LibVulpes 复刻；仅实现当前垂直切片需要的基础设施。
- `v0.6.0` 前不得实现卫星、跃迁、地球化、轨道激光、空间电梯等高级内容。

### 3.2 来源与授权

- 任何复制或变换自上游的文件，必须先进入来源清单。
- 只允许默认引用原 `Advanced-Rocketry/AdvancedRocketry` 仓库中已确认 MIT 的内容。
- 不得从 Advanced Rocketry Reworked、ARLib、Advanced Rocketry 3 或其他分支/模组复制代码与资产，除非已在 `docs/provenance/` 记录许可、来源 commit 和允许范围。
- 不得复制 Minecraft、Mojang、Microsoft 或 Forge 的官方美术资源。
- 不得删除原 MIT 版权和许可声明。

### 3.3 架构

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

### 3.4 文件规模与依赖方向

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

## 4. 每次实施协议

### 4.1 准备

1. 检查 git 状态、当前分支和上一个版本 Gate。
2. 确认当前版本文档中的 Required Gate 已列明。
3. 创建或更新 `docs/work/<version>-implementation-log.md`。
4. 列出本次明确不做的内容。
5. 检查来源清单，不允许“先复制再补记录”。

### 4.2 实施

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

### 4.3 验证

至少运行当前版本文档指定的命令。通用必跑：

```bash
./gradlew clean build
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
```

`v0.0.1` 例外：该版本明确不创建 Forge/Gradle 工程，因此在 Gradle Wrapper 尚不存在时，将上述 Gradle 命令记录为 `NOT_APPLICABLE`，改为执行：

```bash
python scripts/validate_repository.py --require-approved-identity
git diff --check
```

严格校验因 `PROJECT-CONFIG.md` 尚未人工批准而失败时，必须保留失败并将版本标记为 `BLOCKED`；不得把 `DRAFT` 当作通过。自 `v0.0.2` 建立 Gradle Wrapper 后，恢复执行通用 Gradle 命令。

若版本包含专服、跨维度、网络或持久化功能，还必须完成专用服务端与重启测试。

### 4.4 结束输出

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

## 5. Gate 纪律

- Required Gate 任一失败：版本状态必须为 `BLOCKED` 或 `IN PROGRESS`。
- 不得通过删除测试、放宽断言、扩大超时、忽略异常或降低性能预算来伪造通过。
- 任何豁免都必须写入 ADR，并标注负责人、理由、到期版本和回收条件。
- 版本标签只能在 `docs/releases/<version>/RELEASE-EVIDENCE.md` 完整后创建。
- 自动测试通过不等于发布通过；客户端视觉、专服、持久化和许可证 Gate 必须分别确认。

## 6. 提交与分支

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

## 7. 当前版本识别

读取 `docs/04-VERSION-ROADMAP.md` 中第一个未完成版本。若仓库尚未建立任何状态文件，当前版本固定为 `v0.0.1`。

建议维护：

```text
docs/status/CURRENT_VERSION.md
docs/status/GATE_STATUS.md
docs/work/
docs/releases/
```

Codex 不得自行把版本标记为通过；只能生成证据并建议人工确认。
