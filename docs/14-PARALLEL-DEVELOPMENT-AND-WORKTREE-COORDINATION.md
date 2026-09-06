# 14 — 并行开发、worktree 与进度协作

## 1. 适用范围与权威

本规约用于把一个版本拆为可独立验证的任务，不替代
[AGENTS](../AGENTS.md)、已批准 ADR 或版本 Required Gate。
版本状态见[主路线](04-VERSION-ROADMAP.md)和
[Gate 记录](status/GATE_STATUS.md)；后续能力依赖见
[v1.0 后路线](16-POST-1.0-VERSION-ROADMAP.md)。

- 版本集成与发布按顺序；同一版本任务可以按依赖图并行。
- 后续版本可提前做只读审计、测试设计和数据样例，不提前实现功能。
  独立 worktree 不豁免 AGENTS 的版本范围限制。跨版本提前实现需要先由
  维护者批准治理变更，不能从本文推导许可。
- v1.0 是功能冻结的 Stable Core MVP，不夹带 v1.1 扩展重构。
- 只有人工维护者可把版本标为 PASSED 或 RELEASED。
- 并行是可选方式；原有单版本实施日志继续有效。没有实际并行需求时，
  不建立调度平台、控制目录、数据库或另一套重复进度表。

## 2. 角色和权限

| 角色 | 责任 | 权限边界 |
|---|---|---|
| 维护者 | 身份、许可、ADR、范围、发布与 Gate 豁免 | 批准版本；保留签署记录 |
| 协调者 | 任务依赖、契约、写入范围、在制任务数量 | 不代替实现或审核证据 |
| 契约负责人 | 数据、接口、schema、错误模型及契约测试 | 冻结前记录未决项；变更需提案 |
| 实现者 | 一个任务的代码、测试、进度和交接 | 不越界；最多 READY_FOR_REVIEW |
| 测试者 | 独立用例、边界输入、运行记录 | 不随实现降低测试期望 |
| 独立审核者 | 从任务契约、实际 diff 和测试判断 | 给出 findings；不预设结论 |
| 集成者 | 中央文件、合并、冲突解决、完整回归 | 最多推进版本至 READY_FOR_AUDIT |

同一人可承担多个角色，但同一任务的实现和最终审核使用不同会话。
审核者必须独立运行适用关键命令；环境不能运行的检查明确保留为未验证。

## 3. 并行启动条件

- 每个任务有稳定 ID、可观察结果、依赖、非目标和验证方法。
- 依赖已有可用证据；公共接口、持久化 ID、schema、网络协议、错误码和
  生命周期边界在下游开始写入前冻结。
- 一个写入任务对应一个分支和一个独立 Git worktree。
- 活动写入范围互不重叠；同一火箭/跃迁核心事务只有一个主要写入者。
- 有唯一集成者和足够的测试资源。并发上限取决于依赖与宿主机资源，
  不取决于可打开多少 agent；资源建议见[远程开发规约](15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md)。

只读分析可按架构、测试、来源、性能等独立范围拆分。需要委派时，任务说明
包含身份、范围、读取权限、写入边界、命令权限和报告格式，不提供预期发现
或固定结论。模型选择以实际可用客户端和账户为准，不要求特定型号。

写入型子任务使用独立 worktree；无法隔离时只返回建议或 patch，由唯一
写入者审查后应用。共享 checkout 的只读子任务不得私自启动写入实现。

## 4. 状态模型

版本 status 只使用主路线定义的值：

~~~text
PLANNED → IN_PROGRESS → READY_FOR_AUDIT → PASSED → RELEASED
                  ↘ BLOCKED
~~~

CONTRACT_FROZEN、IMPLEMENTING、INTEGRATING、FEATURE_FREEZE 和
RELEASE_CANDIDATE 可记录为 phase，不是新的版本状态。
契约冻结、独立审核通过和发布批准不能混为一项。

可选任务包状态：

~~~text
PLANNED → READY → CLAIMED → IN_PROGRESS → READY_FOR_REVIEW
                              ↑                ↓
                       CHANGES_REQUESTED ← 独立审核
                                               ↓
                         VERIFIED → INTEGRATION_PENDING → MERGED
~~~

阻断、取消或替代分别记录 BLOCKED、CANCELLED、SUPERSEDED 及原因。
VERIFIED 由独立审核者判断；MERGED 由集成者核对真实合并提交。
单版本任务树可以继续使用原有 planned/verified 标签，但不得将子任务完成
当作版本批准。复选框勾选需要对应范围的实际验证证据。

## 5. 进度与任务包

单窗口使用 `docs/work/<version>-implementation-log.md`。启用多写入任务时，
可增设以下协调文件；缺失时先定义任务，不假装它们已存在：

~~~text
docs/control/
  PROJECT_STATE.md        # 引用权威版本状态，不另维护版本批准
  ACTIVE_CONSTRAINTS.md   # 适用约束和未决项
  DEPENDENCY_GRAPH.md     # 任务 DAG
  PATH_OWNERSHIP.md       # 独占范围、任务、负责人、开始/释放时间
  INTEGRATION_QUEUE.md    # 待审/待合并 commit 和依赖
docs/work/<task-id>/
  TASK.md
  PROGRESS.md
  HANDOFF.md
  REVIEW.md
  PROPOSED-CHANGE-001.md   # 仅需要变更时创建
~~~

中央控制文件由协调/集成者维护，实现者只更新自己的任务目录。
项目索引可以生成，但 Git 与任务 Markdown 是事实源，不能依赖不可重建的
数据库、聊天历史或 tmux 屏幕恢复状态。

任务 ID 在项目内唯一且不重用；沿用既有 ID。新增任务可使用
`V100-<capability>-<sequence>`、`V110-<capability>-<sequence>` 等格式。

### TASK.md 字段

| 字段 | 要求 |
|---|---|
| identity | task_id、milestone、owner、reviewer、task_type |
| checkout | branch、worktree、base_branch、base_commit |
| outcome | 可观察结果、范围、非目标 |
| dependencies | depends_on、blocks、contract_owner/status |
| ownership | write_scope、exclusive_paths、forbidden_paths |
| validation | acceptance criteria、required_tests/gates、命令和证据位置 |
| lifecycle | status、created/claimed/updated 时间、实际提交 |

任务领取后契约原则上冻结。一个任务应能独立审查、验证和回退；涉及无关
系统、多个独立环境或大部分仓库写入范围时继续拆分，不用一个大任务掩盖缺口。

### PROGRESS / HANDOFF / REVIEW

- PROGRESS：已验证结果、进行中工作、最后命令及退出码、失败、未决项、
  实际 commit、未提交文件、下一项具体操作。领取、检查点、阻断、契约变更、
  会话结束或交接时更新，不报告无证据百分比。
- HANDOFF：完成/未完成范围、接口和使用方式、schema、修改文件、测试、
  原始日志、来源/存档/网络影响、合并依赖与回退方法。
- REVIEW：审核 commit、独立执行命令、契约/范围符合性、findings（按严重度）、
  未验证项、建议状态和后续动作；不复制实现者结论作为审核结果。
- 阻断记录首次时间、复现、影响、仍可继续的部分和需要谁做何种决定。

## 6. 契约变更与中央文件

涉及多个任务的 record/interface、registry ID、SavedData key、NBT 字段、
网络方向/协议、状态机、任务所有权和天体身份，先建立契约与测试。
变更记录原因、受影响任务、迁移、测试和回退；持久化、网络或重大架构变更
必须经 ADR。下游不自行修改公共语义。

中央文件默认由集成者独占，具体清单从实际代码确认：

~~~text
build.gradle / settings.gradle / gradle.properties / gradle/**
src/main/resources/META-INF/mods.toml
src/main/resources/pack.mcmeta
src/main/java/**/AdvancedRocketryCommunity.java
src/main/java/**/registry/**
src/main/java/**/network/**            # 公共注册/协议入口
AGENTS.md / PROJECT-CONFIG.md / LICENSE / NOTICE.md
docs/control/** / docs/status/**
~~~

可为领域适配器或测试声明更精确的独占范围，但不为了分工而提前创建大型
模块框架。发现范围不足时提案，不以小修复为理由跨范围写文件。

## 7. Git worktree 与集成

以下是人工选择任务和基线后的示例，不自动创建、提交或推送分支：

~~~bash
git status --short
git branch --show-current
git rev-parse HEAD
git worktree list
git worktree add ../arce-v100-release-docs -b docs/v1.0.0-release-docs main
~~~

记录实际 base commit，确认没有覆盖已有分支/worktree。只提交任务拥有的
文件。同步由集成者选择 merge/rebase；不得私自重写共享历史。

进入集成队列需要独立审核、确定 head commit、完整交接、依赖可用、来源
记录、无阻断 finding 和未解决契约差异。按模型/验证、服务逻辑、持久化、
Forge、网络、客户端、数据资产、测试和证据的依赖集成，以任务 DAG 为准。

- 每任务合并后：相关单测、编译、GameTest、资源校验。
- 每批集成后：test/build、领域 GameTest，涉及服务端时专服 smoke。
- 版本集成完成后：全部 Required Gate，独立审核，再提交人工批准。
- 合并冲突不能通过删除测试、吞异常或静默改变契约处理。
- worktree 清理只在证据归档、分支已合并、工作区无未保存修改后执行；
  核对完整目录与所属任务，不使用强制删除掩盖未提交工作。

## 8. v1.0 与后续任务的使用方式

v1.0 可分为版本/发布文档、迁移恢复、专服性能、真实客户端视觉、缺陷修复
和独立审核；准确范围在[v1.0 版本计划](versions/V1.0.0-COMMUNITY-MVP.md)。
后续版本按[能力路线](16-POST-1.0-VERSION-ROADMAP.md)各自建任务，不能把
未来目录建议当作已经存在的架构。

执行入口统一放在 codex-prompts/，不在本文维护重复提示词：

- [单窗口当前版本](../codex-prompts/02-implement-next-version.md)
- [v1.0 稳定验证](../codex-prompts/05-v1.0-release-validation.md)
- [v1.1+ 任务实施](../codex-prompts/06-implement-post-1.0-version.md)
- [独立审核](../codex-prompts/07-audit-post-1.0-version.md)
- [远程环境审计](../codex-prompts/08-remote-linux-environment-audit.md)

以后需要任务索引工具时，再从 Markdown 派生依赖、范围冲突、状态、分支
和更新时效检查。本文不要求实现尚不存在的 project_progress.py，也不要求
为文档整合创建一套新控制系统。
