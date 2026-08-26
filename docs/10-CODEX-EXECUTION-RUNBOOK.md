# 10 — Codex Execution Runbook / Codex 执行手册

## 1. 为什么按版本驱动

Advanced Rocketry 的系统耦合很强。一次给 Codex “全部搬到 1.20.1”会产生：

- 大量未验证占位代码；
- 自创 API 与旧行为混杂；
- 多个系统同时不完整；
- 测试无法定位；
- 会话过长后丢失约束；
- Git diff 难以审查。

因此每次只处理当前版本或一个 PR 切片。

## 2. 第一次执行

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

## 3. 上游审计

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

## 4. 实施下一版本

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

## 5. 审计当前版本

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

## 6. 发布 Gate

使用：

```text
codex-prompts/04-release-gate.md
```

该提示只生成证据和结论，不应顺便实现新功能。失败时输出阻断项和最小修复计划。

## 7. 会话控制

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

## 8. 工作树隔离

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

## 9. Codex 不得自行做的决定

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

## 10. 失败时的处理

Codex 遇到无法实现的测试或 API：

1. 保留失败；
2. 定位最小问题；
3. 查询当前 Forge 1.20.1 官方 API；
4. 写风险和备选；
5. 不使用旧 API 猜测；
6. 不通过 mock 掩盖缺失的集成行为；
7. 当前回复尽量完成可验证的部分；
8. 将版本标记 BLOCKED，而不是假定通过。

## 11. 推荐产出格式

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

## 12. 何时允许进入下一版

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
