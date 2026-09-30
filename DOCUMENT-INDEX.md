# DOCUMENT-INDEX — 文档索引与用途

## 最先交给 Codex 的文件

| 文件 | 用途 |
|---|---|
| `PROJECT-CONFIG.md` | 唯一项目变量和人工决策入口 |
| `AGENTS.md` | Codex 的长期工程约束、Gate 和输出格式 |
| `00-READ-ME-FIRST.md` | 人工启动顺序 |
| `MASTER-EXECUTION-PLAN.md` | v0.x–v1.0 初始单文件快照；后续规划以分文件、ADR/状态为准 |
| `codex-prompts/00-initialize-repository.md` | 第一次执行，只完成 v0.0.1 |
| [至完成的分会话计划](docs/status/COMPLETION-PLAN.md) | 从当前到 v2.0 的分会话执行块、每块做法与“下一块”指针 |

## 产品与总体方案

| 文件 | 用途 |
|---|---|
| `PRODUCT.md` | 产品是什么、v1.0 核心体验、非目标 |
| `docs/01-PORTING-PRINCIPLES.md` | 为什么重写、如何使用旧代码/资产 |
| `docs/03-TARGET-ARCHITECTURE.md` | 1.20.1 包结构和系统拆分 |
| `docs/04-VERSION-ROADMAP.md` | v0.0.1–v2.0.0 里程碑和集成/验收顺序 |
| `docs/PORTING_MATRIX.md` | 旧系统到新模块/版本/测试的映射 |
| [行星天空资源包](docs/PLANETARY-SKY-GUIDE.md) | 天空、太阳、星空、雾与环境音配置及兼容边界 |
| `docs/11-RISK-REGISTER.md` | 核心风险、触发与缓解 |
| [并行与 worktree](docs/14-PARALLEL-DEVELOPMENT-AND-WORKTREE-COORDINATION.md) | 任务 DAG、所有权、契约与独立审核 |
| [远程 Linux 开发](docs/15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md) | Debian 12、tmux、专服、V0/V1/V2 证据边界 |
| [v1.0 后路线](docs/16-POST-1.0-VERSION-ROADMAP.md) | 扩展内核到 v2.0 经典功能对等 |
| [v1.0+ 质量预算](docs/17-V1PLUS-QUALITY-BUDGETS.md) | 缺陷、性能、迁移、扫描/网络与视觉门槛 |
| [外部技术来源](docs/18-V1PLUS-SOURCES.md) | 工具与远程流程参考；不是资产授权 |

## 上游和资产

| 文件 | 用途 |
|---|---|
| `UPSTREAM.md` | 主上游、次级参考和禁止复制来源 |
| `docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md` | Codex 应生成的代码树/资产清单 |
| `docs/08-ASSET-LICENSE-AND-PROVENANCE.md` | 导入、hash、许可和 quarantine 规则 |
| `docs/templates/SOURCE-PROVENANCE-TEMPLATE.md` | 每个导入文件/批次的来源记录 |
| `codex-prompts/01-run-upstream-audit.md` | 只执行审计，不复制内容 |

## GitHub、声明和治理

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

## 测试、存档和发布

机器实现开发入口：[机器内核接入指南](docs/MACHINE-KERNEL-GUIDE.md)，包含现有
三机适配、配方/结构样例和资源持久化边界；不构成第三方稳定 API 承诺。

| 文件 | 用途 |
|---|---|
| `CHANGELOG.md` | 玩家与服主可见的版本变化；未发布版本必须明确标注状态 |
| `docs/05-MASTER-TEST-PLAN.md` | 单元、GameTest、专服、重启、多人、性能 |
| `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md` | G0–G9 的通过门槛 |
| `docs/07-SAVE-DATA-AND-NETWORK-VERSIONING.md` | schema、迁移、journal、包大小 |
| `docs/templates/TEST-REPORT-TEMPLATE.md` | 自动测试报告 |
| `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` | 人工测试用例 |
| `docs/templates/PERFORMANCE-REPORT-TEMPLATE.md` | 性能报告 |
| `docs/templates/RELEASE-EVIDENCE-TEMPLATE.md` | 每版最终证据 |
| [v1.0+ 发布证据模板](docs/templates/POST-1.0-VERSION-EVIDENCE-TEMPLATE.md) | 扩展版本契约、迁移和 G0–G9 |
| [远程运行报告](docs/templates/REMOTE-SERVER-RUN-REPORT-TEMPLATE.md) | 实测主机、负载、退出码和采样 |
| [视觉验证报告](docs/templates/VISUAL-VALIDATION-REPORT-TEMPLATE.md) | 候选、GPU、场景和真实多人 |
| `docs/releases/v0.0.2/` | Forge bootstrap 的自动、人工、产物和风险证据 |
| `docs/releases/v0.0.2/INSTALLATION.md` | 未发布开发预览的环境、客户端/服务端安装和存档边界 |
| `docs/releases/v0.1.0/` | 资产/注册基线的来源、构建、客户端、专服和人工验收证据 |
| `docs/releases/v0.1.0/GATE-STATUS.md` | v0.1.0 全部 Required Gate 的接受快照与 CI 绑定 |
| `docs/releases/v0.1.0/INSTALLATION.md` | v0.1.0 开发预览的安装、内容与存档边界 |
| `docs/releases/v0.2.0/` | 电解机垂直切片的产物、自动测试、客户端、专服、重启和人工验收证据 |
| `docs/releases/v0.2.0/GATE-STATUS.md` | v0.2.0 全部 Required Gate 的接受快照与 CI 绑定 |
| `docs/releases/v0.2.0/INSTALLATION.md` | v0.2.0 开发预览的安装、机器行为和存档边界 |
| `docs/releases/v0.3.0/` | 天体 Codec、固定 Moon/Space、XML 导入、双客户端和重启证据 |
| `docs/releases/v0.3.0/GATE-STATUS.md` | v0.3.0 Required Gate 审核状态与最终 CI/PR 绑定 |
| `docs/releases/v0.3.0/INSTALLATION.md` | v0.3.0 开发预览的安装、命令、固定维度和存档边界 |
| `docs/releases/v0.4.0/` | 真空、宇航服、氧气 Vent、预算扫描、双客户端、重启与性能证据 |
| `docs/releases/v0.4.0/GATE-STATUS.md` | v0.4.0 Required Gate 审核状态与构建/人工批准绑定 |
| `docs/releases/v0.4.0/PERFORMANCE.md` | 16-Vent GameTest 与五分钟专服采样结果 |
| `docs/releases/v0.4.0/INSTALLATION.md` | v0.4.0 开发预览安装、内容与存档边界 |
| `docs/releases/v0.5.0/` | 火箭扫描、事务组装/拆解、恢复、性能、客户端与确定性产物证据 |
| `docs/releases/v0.5.0/GATE-STATUS.md` | v0.5.0 Required Gate、PR/CI、合并与复现接受快照 |
| `docs/releases/v0.5.0/INSTALLATION.md` | v0.5.0 开发预览安装、火箭边界与存档说明 |
| `docs/releases/v0.6.0/` | 地月往返、跨维度恢复、40 航段、8 状态重启、双客户端与确定性产物证据 |
| `docs/releases/v0.6.0/GATE-STATUS.md` | v0.6.0 Required Gate、所有者批准、PR/CI 与验收例外绑定 |
| `docs/releases/v0.6.0/INSTALLATION.md` | v0.6.0 开发预览安装、固定地月路线与存档边界 |
| `docs/releases/v0.7.0/` | 空间站分配、权限、旅行、重启、双客户端和确定性产物证据 |
| `docs/releases/v0.7.0/GATE-STATUS.md` | v0.7.0 Required Gate、所有者批准、PR/CI 与合并后复现状态 |
| `docs/releases/v0.7.0/INSTALLATION.md` | v0.7.0 开发预览安装、共享 Space Level 与存档边界 |
| `docs/releases/v0.8.0/` | 研究、逻辑数据卫星、重启、双客户端、压力与验收证据 |
| `docs/releases/v0.8.0/GATE-STATUS.md` | v0.8.0 Required Gate、PR/CI、合并与精确复现记录 |
| `docs/releases/v0.9.0/` | Beta 产物、迁移、恢复、兼容、两小时 soak、安全、人工与发布证据 |
| `docs/releases/v0.9.0/GATE-STATUS.md` | v0.9.0 Required Gate、所有者批准、PR/CI、合并复现与预发布状态 |
| `docs/releases/v0.9.0/INSTALLATION.md` | Beta 运行时、匹配 JAR、v0.8 存档升级和备份步骤 |
| `docs/releases/v0.9.0/RELEASE-NOTES.md` | 面向玩家和服主的 Beta 1 变更、安装与限制 |
| `docs/BETA-SUPPORT-POLICY.md` | v0.9.x 支持运行时、存档升级、可选模组和问题报告边界 |
| `docs/provenance/v0.9.0-beta-hardening.md` | Beta 零复制来源审查、可选 JEI 依赖与分发边界 |

## 实施记录

| 文件 | 用途 |
|---|---|
| `docs/work/v0.0.1-implementation-log.md` | 仓库治理基线实施记录 |
| `docs/work/v0.0.2-implementation-log.md` | Forge 工程初始化、来源和验证记录 |
| `docs/work/v0.0.2-test-machine-handoff.md` | 换机继续客户端与玩家连接验收的命令和证据要求 |
| `docs/work/v0.1.0-implementation-log.md` | 上游审计、最小资产批次、注册/DataGen 与验证记录 |
| `docs/work/v0.2.0-implementation-log.md` | Electrolyzer 领域、Forge 适配、持久化、客户端和验收记录 |
| `docs/work/v0.3.0-implementation-log.md` | 天体 Codec、固定维度、SavedData、XML 导入与验收进度 |
| `docs/work/v0.4.0-implementation-log.md` | 真空、宇航服、氧气 Vent、预算化密闭扫描与验收进度 |
| `docs/work/v0.5.0-implementation-log.md` | 火箭快照、事务、实体、渲染与验收记录 |
| `docs/work/v0.6.0-implementation-log.md` | 燃料、飞行、跨维度恢复、乘客与验收任务树 |
| `docs/work/v0.7.0-implementation-log.md` | 站点模型、分配、权限、旅行、恢复与验收任务树 |
| `docs/work/v0.8.0-implementation-log.md` | 研究、卫星任务、双客户端、压力与验收任务树 |
| `docs/work/v0.9.0-implementation-log.md` | Beta 迁移、兼容、安全、soak 与发布任务树 |
| `docs/work/v0.9.0-feature-freeze.md` | v0.9.0 功能冻结范围与所有者批准记录 |
| [v1.0 实施记录](docs/work/v1.0.0-implementation-log.md) | 文档整合、决策、实跑验证和剩余 Gate |
| [v1plus 整合验证](docs/work/v1.0.0-planning-integration/VERIFICATION.md) | 本地检查结果、原始日志与发布验收边界 |
| [v1plus 输入来源](docs/provenance/v1.0.0-v1plus-planning.md) | 用户提供规划包的来源、哈希和整合边界 |
| [v1.2.0 实施记录](docs/work/v1.2.0-implementation-log.md) | 机器与多方块内核任务树、前置 Gate 和验证记录 |
| [v1.2.0 机器审计](docs/work/v1.2.0-machine-audit.md) | 当前实现、上游机器分类和三台代表机器选择 |
| [v1.2.0 契约草案](docs/work/v1.2.0-contract-draft.md) | process、port、pattern、controller 与迁移边界草案 |
| [v1.2.0 测试设计](docs/work/v1.2.0-test-design.md) | 自动、恢复、安全、性能和人工验收矩阵 |
| [v1.2.0 审计验证](docs/work/v1.2.0-audit/VERIFICATION.md) | 上游哈希、JSON 样例、规划/仓库校验与基线单测证据 |
| [v1.2.0 process core 验证](docs/work/v1.2.0-process-core/VERIFICATION.md) | 纯 Java 模拟、边界、事务顺序和中断恢复证据 |
| [v1.2.0 port core 验证](docs/work/v1.2.0-port-core/VERIFICATION.md) | 端口策略、方向、Forge wrapper 与 capability GameTest 证据 |
| [v1.2.0 pattern core 验证](docs/work/v1.2.0-pattern-core/VERIFICATION.md) | schema、变换、诊断与未加载区块 GameTest 证据 |
| [v1.2.0 lifecycle core 验证](docs/work/v1.2.0-lifecycle-core/VERIFICATION.md) | controller/part 状态、原子 binding、dirty queue 与重建 GameTest 证据 |

## 分版本执行文件

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
docs/versions/V1.1.0-EXPANSION-KERNEL.md
docs/versions/V1.1.1-MAINTENANCE.md
docs/versions/V1.2.0-MACHINE-MULTIBLOCK-KERNEL.md
docs/versions/V1.3.0-PUBLIC-API-COMPATIBILITY.md
docs/versions/V1.4.0-PLANETARY-EXPANSION.md
docs/versions/V1.5.0-ORBITAL-STATION-WARP.md
docs/versions/V1.6.0-SATELLITE-RESOURCE-MISSIONS.md
docs/versions/V1.7.0-ENDGAME-SYSTEMS.md
docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md
docs/versions/V1.9.0-PARITY-BETA-HARDENING.md
docs/versions/V2.0.0-CLASSIC-FEATURE-PARITY.md
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

## Codex 日常提示

| 文件 | 用途 |
|---|---|
| `codex-prompts/02-implement-next-version.md` | 实现当前未通过版本 |
| `codex-prompts/03-audit-current-version.md` | 使用独立会话做怀疑式审核 |
| `codex-prompts/04-release-gate.md` | 只跑 Gate 和证据，不扩功能 |
| [v1.0 发布验证](codex-prompts/05-v1.0-release-validation.md) | 功能冻结与最终候选验证 |
| [v1.1+ 任务实施](codex-prompts/06-implement-post-1.0-version.md) | 一个有范围/契约的任务包 |
| [v1.1+ 独立审核](codex-prompts/07-audit-post-1.0-version.md) | 独立复跑、findings 与人工批准前审核 |
| [Linux 环境审计](codex-prompts/08-remote-linux-environment-audit.md) | 只读检查，不自动安装或改变服务器 |

## 开发辅助脚本

v1.0 审核入口：[开发证据与未完成验收项](docs/releases/v1.0.0/RELEASE-EVIDENCE.md)。
该资料区分不同开发制品，不代表候选版或发布批准。

- [Debian 节点检查](scripts/check-debian12-dev-host.sh)：诊断报告，不安装软件或执行 wrapper。
- [tmux 布局](scripts/tmux-arce-layout.sh)：检查目录后创建/附着工作会话。
- [软件视觉冒烟](scripts/start-xvfb-visual-smoke.sh)：私有 X display 与 loopback VNC；不放行真实 GPU Gate。
- [规划一致性校验](scripts/validate_v1plus_planning.py)：版本清单、Gate、入口、链接与来源覆盖。

## 决策记录

- [公共 API 接入指南](docs/PUBLIC-API-GUIDE.md)：compile-only classifier、版本查询及七类扩展用法。
- [API 兼容与支持矩阵](docs/API-COMPATIBILITY.md)：公开类型、最低版本、平台边界、移除行为和已验证范围。
- [独立兼容测试模组](compat-test-mod/README.md)：实际 Maven API 消费者、独立构建与打包边界检查。
- [v1.3 实施日志](docs/work/v1.3.0-implementation-log.md)：API 切片及其实际验证范围。
- [v1.3 开发交接](docs/releases/v1.3.0/RELEASE-EVIDENCE.md)：已实现能力、制品身份、迁移/网络边界与未完成验收，不代表发布批准。
- [v1.3 开发基线例外](docs/decisions/ADR-020-V130-DEVELOPMENT-BASELINE-EXCEPTION.md)。
- [v1.4 实施日志](docs/work/v1.4.0-implementation-log.md)：行星扩展任务、依赖与实际验证范围。
- [v1.4 开发交接](docs/releases/v1.4.0/RELEASE-EVIDENCE.md)：已实现行星能力、精确制品、短测与恢复边界、尚未完成的 G0–G9；不是发布批准。
- [v1.4 准备验证](docs/work/v1.4.0-preparation/VERIFICATION.md)：数据样例、独立契约审核及未改动运行时的检查。
- [v1.5 实施日志](docs/work/v1.5.0-implementation-log.md)：空间站、轨道环境、多恒星与跃迁的任务及验证范围。
- [v1.5 准备验证](docs/work/v1.5.0-preparation/VERIFICATION.md)：站点契约、文档样例与未改动运行时的检查。
- [v1.5 开发基线例外](docs/decisions/ADR-039-V150-DEVELOPMENT-BASELINE-EXCEPTION.md)：本版开发与继承发布验收分离。
- [空间站区域与迁移](docs/decisions/ADR-040-STATION-REGIONS-AND-MIGRATION.md)：稳定区域、有界扩建、独立 schema 与备份契约。
- [空间站轨道环境](docs/decisions/ADR-041-STATION-ORBIT-ENVIRONMENT.md)：站点区域有效重力、重力设置（冷却）、环境显示、太阳范围与非目标。
- [STATION-04 原生覆盖范围](docs/decisions/ADR-042-STATION-04-NATIVE-SCOPE.md)：火箭身份与缺失轨道天体原生用例移交 MIG-01，ACC-02 前到期。
- [恒星系即根天体树](docs/decisions/ADR-043-STAR-SYSTEMS-AS-ROOT-TREES.md)：恒星系身份、航线不跨系、已知判定与示例星系。
- [空间站跃迁为逻辑重定位](docs/decisions/ADR-044-STATION-WARP-LOGICAL-RELOCATION.md)（第 3 版，已接受）：能量只存于站点注册表、充能定期入账、单次受控写入扣费、无状态核心、在途火箭规则、根 schema 4。
- [太空电梯端点契约（v1.5 最小版）](docs/decisions/ADR-045-SPACE-ELEVATOR-ENDPOINT-CONTRACT.md)（PROPOSED）：端点对、只读校验规则、不持久化、跃迁与后续实现的固定规则。
- [v1.5 空间站控制即服务端命令](docs/decisions/ADR-046-V150-STATION-CONTROLS-ARE-COMMANDS.md)（PROPOSED）：无新界面与网络包、反馈呈现、权限矩阵报告。
- [v1.4 开发基线例外](docs/decisions/ADR-030-V140-DEVELOPMENT-BASELINE-EXCEPTION.md)：只允许本版开发，不提前通过继承 Gate。
- [行星定义与固定维度](docs/decisions/ADR-031-PLANETARY-DEFINITIONS-AND-FIXED-LEVELS.md)：schema 2、可选映射、能力与联合重载契约。
- [天体绑定持久化](docs/decisions/ADR-032-PERSISTENT-PLANETARY-BINDINGS.md)：首次接入、保留已移除身份与重载提交顺序。
- [行星世界与物理准入](docs/decisions/ADR-033-PLANETARY-WORLDS-AND-SURFACE-ADMISSION.md)：火星/金星固定世界、不可着陆气态天体、地面支撑与站点来源。
- [行星世界开发证据](docs/work/v1.4.0-map02/VERIFICATION.md)：实际地形、旅行、上一切片存档升级和短程重启的独立验证范围。
- [行星暴露与防护](docs/decisions/ADR-034-PLANETARY-EXPOSURE-AND-PROTECTION.md)：兼容旧数据的环境效果启用、被动装备防护与有限供能密闭房间。
- [环境响应开发证据](docs/work/v1.4.0-env/VERIFICATION.md)：温度、压力、日照、防护与氧气分离的验证结果和覆盖边界。
- [公共 API 版本政策](docs/decisions/ADR-021-PUBLIC-API-VERSION-POLICY.md)。
- [火箭适配器注册契约](docs/decisions/ADR-022-ROCKET-ADAPTER-REGISTRATION.md)。

`docs/decisions/` 内提供持续编号的 ADR：

- 项目身份与 namespace；
- 固定 Moon/Space 维度；
- 火箭事务；
- 大气扫描预算；
- 私有仓库下的 v0.0.1 G8 证据接受与公开前复查条件；
- v0.0.2 bootstrap 范围内的 G4 适用性判断；
- v0.6.0 固定落点、跨维度权威切换和崩服恢复。
- v0.6.0 双客户端日志与所有者可视化验收例外。
- v0.7.0 共享 Space Level 的固定网格 region、站点所有权与安全落点。
- v0.7.0 双客户端日志、所有者证明与无截图/录像的可视化验收例外。
- v0.8.0 有界卫星任务、持久化、权限和调度模型。
- v0.8.0 有序截图与最终双客户端证据的范围化验收决定。
- v0.9.0 Beta 功能冻结、存档升级、可选兼容和发布契约。
- v0.9.0 复用未变化核心视觉证据的范围、风险、所有者接受与 v1.0 回收条件。
- v1.1.0 typed `TravelTarget`、数据路线、位置化 `BodyContext` 与两阶段迁移契约。
- v1.1.0 从未正式发布的 v1.0 实现提交继续开发的限期风险接受；不豁免发布 Gate。
- v1.1.1 在不改协议、存档和玩法的前提下拆分火箭管理器与集成测试。
- v1.2.0 过程事务、端口、多方块 pattern、controller/part 和电解机迁移候选契约。
- v1.2.0 在 v1.1.0 尚未通过时允许先做生产实现的开发基线例外。
- 原有机械和维度完成后再执行完整集成验收的跨版本测试排期决定。

ADR-000、ADR-001、ADR-002、ADR-004、ADR-005、ADR-006、ADR-007、ADR-008 和 ADR-009 已由
维护者接受；ADR-003 仍保留 `PROPOSED` 状态。ADR-006 记录 v0.6.0 固定
落点、跨维度权威切换、乘客和四种崩服恢复策略；ADR-007 仅接受本版
无截图/录像的双客户端日志证据，并要求后续版本重新取证；ADR-008
固定 v0.7.0 的站点网格、创建事务、集中式权限和坐标无关火箭意图；ADR-009
仅接受 v0.7.0 的无媒体双客户端与所有者证明，并要求后续版本重新取证。
ADR-010 与 ADR-011 固定 v0.8.0 卫星与视觉证据边界；ADR-012 已由仓库
所有者接受，固定 v0.9.0 Beta 的功能冻结、存档升级和兼容边界；ADR-013
仅允许 v0.9.0 引用真实且未变化的核心视觉基线，并要求 v1.0 重新取证。
ADR-014 已由维护者接受并冻结 v1.1.0 公共契约；ADR-015 仅允许从固定的
v1.0 实现提交继续 v1.1.0 生产开发，不把 v1.0 标记为发布，也不豁免
v1.1.0 的发布 Gate。ADR-016 与 ADR-017 已由维护者接受，分别冻结 v1.2.0
Framework 契约，并允许从固定基线开展生产实现；两者均不改变前置发布
Gate，也不得据此创建候选或发布标签。
ADR-018 已由维护者接受，将昂贵的完整负载、远程、真实 GPU、双客户端和
全内容回归排到原有机械与维度补全后；切片级测试、构建和新增行为专项验证
仍然保留，且不得据此标记任何 Gate 通过。
