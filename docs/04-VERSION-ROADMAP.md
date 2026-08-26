# 04 — Version Roadmap / 版本路线

## 1. 版本格式

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

## 2. 阶段定义

| 阶段 | 版本 | 公开定位 |
|---|---|---|
| Planning | `v0.0.1` | 文档和治理 |
| Technical bootstrap | `v0.0.2–v0.2.0` | 开发者预览 |
| Core systems alpha | `v0.3.0–v0.5.0` | 不承诺长期世界 |
| Playable alpha | `v0.6.0–v0.8.0` | 可测试核心玩法 |
| Beta | `v0.9.0` | 稳定性和兼容性 |
| Stable MVP | `v1.0.0` | 正式社区版 |

## 3. 里程碑总览

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

## 4. 强制顺序

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

## 5. 版本状态

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

## 6. 通过记录

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

## 7. 版本回退

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
