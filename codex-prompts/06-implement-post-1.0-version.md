# Codex Prompt — 实施下一个 v1.1+ 版本任务

先读取：

```text
AGENTS.md
docs/16-POST-1.0-VERSION-ROADMAP.md
docs/17-V1PLUS-QUALITY-BUDGETS.md
docs/14-PARALLEL-DEVELOPMENT-AND-WORKTREE-COORDINATION.md
docs/status/CURRENT_VERSION.md
docs/status/GATE_STATUS.md
自己的 docs/work/<TASK>/TASK.md
直接依赖任务的 HANDOFF.md
对应 docs/versions/<VERSION>.md
```

## 强制规则

这是一个受委派的实现任务；按任务包定义执行，不承担根会话身份。
命令权限由任务包声明，默认不改系统配置、不发布、不推送；需要委派时为
子任务明确身份、读取/写入范围、命令权限和报告格式。

- 确认当前目录是该任务的独立 Git worktree。
- 核对 `base_commit`、branch、write_scope、exclusive_paths 和 forbidden_paths。
- 不修改未授权中央文件。
- 不实现后续版本功能。
- 不改变公共契约；发现需要改变时创建 `PROPOSED-CHANGE-*.md` 并阻塞等待集成决策。
- 保存、网络和公开 ID 的变化必须同时实现迁移和测试。
- 客户端不可决定服务端结果。
- 扫描、NBT、packet、任务和缓存必须有上限。
- Linux 软件渲染只可报告 `V0`，不得把真实 GPU Gate 标为通过。
- 每完成一个可验证步骤更新 `PROGRESS.md`。
- 形成完整 `HANDOFF.md`；只有任务明确授权提交时才创建原子 commit。

## 执行循环

```text
读取契约
→ 检查现状和测试
→ 实现最小垂直切片
→ 运行局部测试
→ 修复
→ 运行任务 Gate
→ 更新 PROGRESS/HANDOFF
→ 提交
→ READY_FOR_REVIEW
```

## 完成输出

```markdown
# Task Result

## Task/base/branch
## Contract compliance
## Implemented
## Not implemented
## Files changed
## Save/network/API impact
## Tests added
## Commands actually run and exit codes
## Server/headless result
## Visual tests still HUMAN_REQUIRED
## Known limitations
## Commit
## Handoff path
```
