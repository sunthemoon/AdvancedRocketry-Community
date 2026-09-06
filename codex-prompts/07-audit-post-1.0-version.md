# Codex Prompt — 独立审核 v1.1+ 版本

这是独立审核任务，不延续原实现会话。产品代码只读；可运行任务允许的
验证命令并写入指定审核报告目录，不修改系统、生产文件或版本批准记录。

读取：

```text
AGENTS.md
docs/05-MASTER-TEST-PLAN.md
docs/06-RELEASE-AND-ACCEPTANCE-GATES.md
docs/14-PARALLEL-DEVELOPMENT-AND-WORKTREE-COORDINATION.md
docs/16-POST-1.0-VERSION-ROADMAP.md
docs/17-V1PLUS-QUALITY-BUDGETS.md
对应版本文档
所有 TASK/PROGRESS/HANDOFF
候选 diff、测试和 release evidence
```

## 审核顺序

1. 锁定 base、candidate、JAR hash 和证据身份。
2. 将实际变更范围与版本目标、非目标和验收项逐项对照。
3. 检查公共契约、保存、网络、ID 和迁移。
4. 检查服务端权威、权限、复制、chunk loading、上限和异常恢复。
5. 重新运行可在环境中运行的关键测试，不只阅读日志。
6. 检查 Dedicated Server sidedness。
7. 检查性能报告是否使用规定负载。
8. 检查视觉环境分类；LLVMpipe 不得冒充真实 GPU。
9. 检查来源、JAR、文档和 PORTING_MATRIX。
10. 输出 findings，按 Critical/High/Medium/Low 排序。

## 结论权限

你可以标记：

```text
CHANGES_REQUESTED
BLOCKED
READY_FOR_AUDIT
```

你不能标记：

```text
PASSED
```

上列为审核建议；权威版本 status 仍使用主路线枚举，由集成者维护。
结论由实际证据决定，不预填 findings、风险等级或通过结果。

## 输出

```markdown
# Independent Audit — <version>

## Candidate identity
## Scope/contract verdict
## Findings
### Critical
### High
### Medium
### Low
## Tests independently rerun
## Evidence credibility
## Save/network/security verdict
## Performance verdict
## Visual/multiplayer HUMAN_REQUIRED status
## Provenance/release verdict
## Final review state
## Required next actions
```
