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

v1.0+ 任务额外读取 `docs/16-POST-1.0-VERSION-ROADMAP.md` 和
`docs/17-V1PLUS-QUALITY-BUDGETS.md`。涉及并行任务时读取
`docs/14-PARALLEL-DEVELOPMENT-AND-WORKTREE-COORDINATION.md`；涉及远程
Linux 或视觉验证时读取 `docs/15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md`。

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
- 会拒绝保存、丢弃或隔离玩家数据的保护机制，必须先在
  `docs/11-RISK-REGISTER.md` 登记风险，并在 ADR 中写明影响范围（单个对象、区块或
  Level）、持续时间、日志量和恢复方式；不得用抛出异常作为常规的保存控制手段而不加说明。

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

- 验证通过的切片在开始下一个切片前提交并推送（非强制推送）；不得把多个切片的
  改动长时间留在未提交的工作区。
- 证据包的被测源码以提交 SHA 标识。未提交快照只能作为开发中间记录，不能作为
  交付、接受或台账变更的依据。
- ADR 的接受、台账的交付标记和状态文件的结论，与它们所依据的代码和证据在同一
  提交或后续提交中入库。
- 提交前用 `git diff --cached --stat` 核对暂存内容，只提交本任务的文件。

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

## 8. v1.0+ 与并行执行

- `v1.0.0` 是功能冻结的 Stable Core MVP；目的地/BodyContext 扩展属于
  `v1.1.0`，机器/多方块内核属于 `v1.2.0`，不得混入稳定化任务。
- 后续计划见 `docs/16-POST-1.0-VERSION-ROADMAP.md` 和各版本文档。
  规划、只读研究和测试设计不等于功能实现；独立 worktree 不豁免第 3.1 节。
- 同一版本的独立写入任务使用独立 worktree，声明互不重叠的 `write_scope`。
  公共契约先冻结；中央注册、协议、构建和状态文件由集成者独占。
- 实现者只更新自身任务记录；独立审核者审查实际 diff 并复跑关键测试。
  版本 `status` 沿用主路线枚举，契约/集成阶段另记 `phase`。
- v1.0 起新增公开/持久化 ID 必须稳定命名并有明确迁移策略。
- Xvfb/LLVMpipe 只算 `V0` 视觉冒烟，不能放行真实 GPU Gate。
  客户端可见变化需要 `V1` 报告，多人变化需要至少两个真实客户端的 `V2` 证据。
- 远程 Linux 开发不得以 root 运行 Codex、Gradle 或 Minecraft；不得为不可信
  公共 PR 安装带长期凭据的高权限常驻 self-hosted runner。
- tmux 只负责进程连续性；Git、任务记录、日志和 fixtures 才是恢复依据。
- 以上规则不改写历史 Gate，也不延长已批准 ADR 的到期豁免。

## 9. 空间与证据体积

- 启动完整构建、GameTest 或原生服务器前检查磁盘可用空间；低于 10 GB 时先清理，
  不得继续写入。
- 每次运行结束后删除服务器副本、导出树、副本构建目录和临时压缩包，只保留日志、
  结果和清单；源世界与已入库证据不删除。
- 提交进仓库的单个文件不超过 50 MB（GitHub 在 50 MB 告警、100 MB 拒绝）；单个切片的
  证据目录合计不超过 100 MB。
- 证据只保留复核所需：命令、日志、结果、清单、补丁和哈希。已有提交可以复现的源码，
  不得再整份打包进证据；大型产物放 Git LFS、Release 附件或外部存储，并在记录中写明
  位置和 SHA-256。

## 10. 记录卫生

- 验证通过的切片及时提交并推送。台账和状态文件只能把已提交的工作标为已交付；
  不得累积多个切片的未提交改动。
- 已封存的证据包（带 `SHA256SUMS.txt` 或 `checksums.txt`）不得改动原有文件。更正时
  新增独立的更正文件，并重算清单、写明原因。
- 校验清单只能列证据包内或已提交的文件，不得引用 `build/`、Temp 等会被覆盖的路径。
- `CURRENT_VERSION.md` 和 `COMPLETION-PLAN.md` 只写当前状态，同一指标只保留一个当前
  数字；历史检查点移入实施日志或证据包。
- 记录使用完整句子和正常的空格、标点。内部代号（如 `source19`、`native16`）先定义再
  使用，或改用提交、文件或证据包名称。
- 所有者的决定和授权记录原话或可核对的来源（日期、渠道）及适用范围；“有条件授权”
  必须写明条件和覆盖的 ADR 与修订。
- 待决事项有了决定后，同步更新所有引用它的状态文件；被取代的数值或决定在旧记录处
  注明取代关系。
- 证据包中的开放项（如偶发失败）同步写入状态文件，直到有证据关闭。

## 11. 多代理协作

- 多个代理（如 Codex、Claude）可在同一仓库协作，目的是获得独立观点：一个代理的
  实现或记录由另一个代理独立审查，审查者只读、在自己的 Temp 目录工作并复跑关键检查。
- 开始写入前检查 `git status`、`git worktree list` 和正在运行的其他代理。存在他人
  未提交的工作时，不得修改、暂存、提交、删除或接手，先请所有者分配。
- 每个写入任务声明自己的 `write_scope`，只修改该范围内的文件；中央文件（注册、构建、
  状态文件、台账）由当时的集成者独占。
- 移动 HEAD（提交、切换分支）前，确认不会打断其他代理以当前 HEAD 为基准的进行中工作。
- 只清理本代理自己创建的临时目录与进程。

## 12. 代理间命令行派活与追问

Codex 和 Claude 可以直接调用对方的无界面命令行来派活和追问，不需要所有者转述会话。
派活、追问、分叉命令和返回字段于 2026-10-08 在本机实际运行验证（Claude Code 2.1.292，
codex-cli 0.160.1）；`--allowedTools`、`--add-dir` 和 `workspace-write` 只核对了帮助文本。

- 命令行只是传话通道。任务、授权和结论仍以 TASK、ASSIGNMENT、REPORT 等文件和证据包
  为准；派活方在任务记录中写明对方的会话 ID，便于追问和复核。
- 被派活的代理从零开始，看不到派活方的对话。提示词只写任务文件路径和一句要求，
  详细内容放进任务文件，也避免 Windows 下长参数的引号问题。
- 被派活的代理不得再向其他代理派活。所有者的决定和授权不能由代理转述代替，仍按
  第 10 节记录原话或可核对来源。
- 默认每个任务开新会话，上下文通过 TASK、上一轮 REPORT 和审查意见等文件传递。恢复
  （resume）和分叉（fork）每一轮都把整个会话历史重新作为输入发送，只用于派活后不久、
  会话还小时的简短追问。不得分叉长时间运行的大会话（如 Root 会话）来派活，改为写交接
  文件；独立审查必须开新会话，避免继承被审方的上下文。
- 不得恢复正在交互界面中打开的会话，交互界面看不到命令行追加的轮次。
- 派活方检查退出码和结果中的错误标记；长任务放到后台运行并设置超时。调用方在沙箱内
  运行时，对方的命令行需要网络并写入各自的用户目录，被拦截时按调用方的审批流程申请。
- 收回结果后用 `git status` 核对被派活方的工作目录没有超出 TASK 写入范围的改动。

### 12.1 Codex 派活给 Claude

在分配给 Claude 的检出目录中运行：

```bash
claude -p "读取并执行 <TASK 路径>，按其中的格式交付，最后一行写 REPORT 路径。" \
  --output-format json --permission-mode acceptEdits --add-dir <leaf 目录>
```

- 返回的 JSON 中 `session_id` 是会话 ID，`result` 是最终回答，`is_error` 为 `true`
  表示失败。
- `acceptEdits` 只自动批准文件编辑。TASK 允许的命令用 `--allowedTools` 逐项放行，例如
  `--allowedTools "Bash(git diff:*)"`；该选项接收多个值，放在提示词之后。未放行的命令
  在无界面模式下直接被拒绝，不会卡住。
- 追问（会话 ID 不变）：
  `claude -p -r <session_id> "<追问或新 TASK 路径>" --output-format json --permission-mode acceptEdits --add-dir <leaf 目录>`。
- 沿用已有会话的上下文另开会话：在追问命令中加 `--fork-session`，返回新的 `session_id`。

### 12.2 Claude 派活给 Codex

```bash
codex exec --json -s read-only -C <分配的目录> -o <输出文件> \
  "读取并执行 <TASK 路径>，按其中的格式交付，最后一行写 REPORT 路径。"
```

- JSONL 第一行 `{"type":"thread.started","thread_id":"…"}` 中的 `thread_id` 是会话 ID；
  `-o` 把最终回答写入文件。
- 只读审查用 `-s read-only`；需要写入时用 `-s workspace-write`，并用 `-C` 和
  `--add-dir` 限定可写目录。不得使用 `--dangerously-bypass-approvals-and-sandbox`。
  不在 Git 仓库中运行时加 `--skip-git-repo-check`。
- 追问（`thread_id` 不变）：在原目录中运行
  `codex exec resume <thread_id> -c 'sandbox_mode="read-only"' --json -o <输出文件> "<追问>"`。
  `resume` 没有 `-C` 和 `-s` 选项，沙箱用 `-c sandbox_mode=...` 显式指定。
- 沿用已有会话的上下文另开会话：`codex exec fork <thread_id> "<提示词>"`。

## 13. Root 与简化流程（2026-10-11 起）

所有者于 2026-10-11 在 Claude Code 交互会话中决定由 Claude 接任 Root，原话依次为：

1. "考虑root换人，我安排codex完成手动工作，记录情况然后 由你确认进度后主导 可以并发多个codex以加块推进mod开发进度"
2. "授权范围全部，提及的这些都由你处理 root 相关启动 流程需要简化"
3. "完成后可以并发处理bug等等问题，但是如果进度没有完成，那么还是会被卡进程，所以考虑优先推进进度"

前任 Root 的交接记录是 `docs/work/v1.8.0-session-handoff-20261008.md` 中 2026-10-11 的快照
（提交 `0fcd67b4`）。本节与前文或其他记录冲突时，以本节为准。

- **Root（Claude）**：维护任务队列，派活，审核实际 diff，复跑定向测试，合入共享分支并正常
  推送，维护状态文件和台账，可以运行 JVM、Gradle 和 GameTest。打标签、合入 main、强制推送
  和标记版本或 Gate 通过，仍由所有者决定。
- **实现者（Codex 工作会话）**：
  - 每个任务开一个新会话，使用一个 worktree 和一个分支，用
    `codex exec -s workspace-write -C <worktree>` 启动；
  - 只修改 TASK 列出的范围，提交到自己的分支并正常推送；
  - 不合入共享分支，不再向其他代理派活。
- **普通切片的流程**：
  1. Root 写 TASK，写明目标、写入范围、验收测试和不做的事；
  2. 实现者实现，跑定向测试，提交；
  3. Root 审核 diff，复跑定向测试，然后合入；
  4. 每合入一批，跑一次完整回归（托管 CI，或本机跑一次）。

  不再要求多轮提案、单独的复审记录或独立的验证提交。每个切片只写一份简短记录：
  提交、命令、结果和未完成项。
- **存档、迁移、网络协议或保存拒绝相关的切片**：实现前写一页设计说明；合入前由另一个
  会话做一次只读审核。
- **中央文件**：TASK 可以允许实现者在注册类、语言文件和数据生成入口追加本切片的条目，
  合并冲突由 Root 解决。状态文件、台账、构建脚本和工作流只由 Root 修改。
- **本机资源**：
  - 同一时间最多两个 Gradle 编译或单元测试；
  - 完整 GameTest、runData 和原生服务器同一时间只运行一个，由 Root 安排。
- **优先级**：先推进 v1.8 的内容进度；bug 修复只在不拖慢进度时并行。
- 第 5 节的规定不变：不删测试、不放宽断言，豁免必须写 ADR。
