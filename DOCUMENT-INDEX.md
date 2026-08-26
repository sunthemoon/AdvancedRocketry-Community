# DOCUMENT-INDEX — 文档索引与用途

## 最先交给 Codex 的文件

| 文件 | 用途 |
|---|---|
| `PROJECT-CONFIG.md` | 唯一项目变量和人工决策入口 |
| `AGENTS.md` | Codex 的长期工程约束、Gate 和输出格式 |
| `00-READ-ME-FIRST.md` | 人工启动顺序 |
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
| `docs/05-MASTER-TEST-PLAN.md` | 单元、GameTest、专服、重启、多人、性能 |
| `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md` | G0–G9 的通过门槛 |
| `docs/07-SAVE-DATA-AND-NETWORK-VERSIONING.md` | schema、迁移、journal、包大小 |
| `docs/templates/TEST-REPORT-TEMPLATE.md` | 自动测试报告 |
| `docs/templates/MANUAL-TEST-CASE-TEMPLATE.md` | 人工测试用例 |
| `docs/templates/PERFORMANCE-REPORT-TEMPLATE.md` | 性能报告 |
| `docs/templates/RELEASE-EVIDENCE-TEMPLATE.md` | 每版最终证据 |

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

`docs/decisions/` 内提供四份初始 ADR：

- 项目身份与 namespace；
- 固定 Moon/Space 维度；
- 火箭事务；
- 大气扫描预算。

它们默认 `PROPOSED`，需在对应版本前由人工接受。
