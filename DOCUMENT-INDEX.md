# DOCUMENT-INDEX — 文档索引与用途

## 最先交给 Codex 的文件

| 文件 | 用途 |
|---|---|
| `PROJECT-CONFIG.md` | 唯一项目变量和人工决策入口 |
| `AGENTS.md` | Codex 的长期工程约束、Gate 和输出格式 |
| `00-READ-ME-FIRST.md` | 人工启动顺序 |
| `MASTER-EXECUTION-PLAN.md` | 完整单文件开发计划；分文件与当前 ADR/状态冲突时以后者为准 |
| `codex-prompts/00-initialize-repository.md` | 第一次执行，只完成 v0.0.1 |

## 产品与总体方案

| 文件 | 用途 |
|---|---|
| `PRODUCT.md` | 产品是什么、v1.0 核心体验、非目标 |
| `docs/01-PORTING-PRINCIPLES.md` | 为什么重写、如何使用旧代码/资产 |
| `docs/03-TARGET-ARCHITECTURE.md` | 1.20.1 包结构和系统拆分 |
| `docs/04-VERSION-ROADMAP.md` | 12 个版本里程碑和强制顺序 |
| `docs/PORTING_MATRIX.md` | 旧系统到新模块/版本/测试的映射 |
| `docs/11-RISK-REGISTER.md` | 核心风险、触发与缓解 |

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
| `docs/releases/v0.9.0/evidence/migration/` | Beta schema-1 备份、迁移、同世界重启与诊断 checkpoint |
| `docs/BETA-SUPPORT-POLICY.md` | v0.9.x 支持运行时、存档升级、可选模组和问题报告边界 |

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

## 决策记录

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

ADR-000、ADR-001、ADR-002、ADR-004、ADR-005、ADR-006、ADR-007、ADR-008 和 ADR-009 已由
维护者接受；ADR-003 仍保留 `PROPOSED` 状态。ADR-006 记录 v0.6.0 固定
落点、跨维度权威切换、乘客和四种崩服恢复策略；ADR-007 仅接受本版
无截图/录像的双客户端日志证据，并要求后续版本重新取证；ADR-008
固定 v0.7.0 的站点网格、创建事务、集中式权限和坐标无关火箭意图；ADR-009
仅接受 v0.7.0 的无媒体双客户端与所有者证明，并要求后续版本重新取证。
ADR-010 与 ADR-011 固定 v0.8.0 卫星与视觉证据边界；ADR-012 已由仓库
所有者接受，固定 v0.9.0 Beta 的功能冻结、存档升级和兼容边界。
