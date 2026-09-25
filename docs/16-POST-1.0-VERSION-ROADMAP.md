# v1.0 之后的完整迁移路线

## 1. 路线定义

`v1.0.0` 是稳定 Core MVP，不是旧版完整迁移。后续版本分成四层：

```text
第一层：稳定核心
  v1.0.0 Core MVP Stable

第二层：扩展基础设施
  v1.1.0 Expansion Kernel
  v1.2.0 Machine & Multiblock Kernel
  v1.3.0 Public API & Compatibility

第三层：经典玩法恢复
  v1.4.0 Planetary Expansion
  v1.5.0 Orbital / Station / Warp
  v1.6.0 Satellites / Resource Missions
  v1.7.0 Endgame Systems
  v1.8.0 Classic Content Completion

第四层：完整迁移稳定化
  v1.9.0 Parity Beta Hardening
  v2.0.0 Classic Feature Parity Stable
```

## 2. 版本总览

| 版本 | 核心目标 | 玩家可见结果 | 主要技术门槛 |
|---|---|---|---|
| `v1.0.0` | 稳定现有闭环 | 可稳定完成地月往返、空间站与基础卫星 | 真实多人、迁移、恢复、发布可重现 |
| `v1.1.0` | 解除天体/目的地硬编码 | 新增测试行星无需改 Java 枚举 | `TravelTarget`、数据路线、`BodyContext` |
| `v1.2.0` | 通用机器和多方块 | 多种机器共享可靠框架 | 端口、配方、结构、旋转、重启 |
| `v1.3.0` | 公共 API | 兼容测试模组可注册扩展内容 | API 版本、适配器、安全边界 |
| `v1.4.0` | 多行星 | 火星/金星等可探索天体与星图 | 数据包、环境、天空、导航 |
| `v1.5.0` | 轨道/空间站/跃迁 | 任意天体空间站与多星系旅行 | 轨道上下文、跃迁事务与恢复 |
| `v1.6.0` | 卫星与资源任务 | 多种卫星、小行星和气体任务 | 离线调度、实例、资源守恒 |
| `v1.7.0` | 终局系统 | 轨道激光、黑洞、重力等 | 权限、性能、破坏边界、恢复 |
| `v1.8.0` | 内容补齐 | 经典机器、物品、配方和资产接近完整 | 来源批次、内容矩阵、平衡 |
| `v1.9.0` | 对等 Beta | 大型模组包测试与全链路回归 | 迁移链、兼容矩阵、长时压力 |
| `v2.0.0` | 功能对等稳定版 | 经典核心功能均有现代实现 | 全 Gate、长期支持承诺 |

## 3. 依赖图

```text
v1.0.0
  │
  ▼
v1.1.0 Expansion Kernel
  │
  ├──────────────┐
  ▼              ▼
v1.2.0          v1.3.0
Machine Kernel  Public API
  │              │
  └──────┬───────┘
         ▼
      v1.4.0 Planetary Expansion
         │
         ▼
      v1.5.0 Orbital / Station / Warp
         │
         ├──────────────┐
         ▼              ▼
      v1.6.0          v1.7.0
      Missions        Endgame
         │              │
         └──────┬───────┘
                ▼
             v1.8.0
                │
                ▼
             v1.9.0
                │
                ▼
             v2.0.0
```

此图表示能力依赖，不改变主路线的版本集成顺序。`v1.2` 与 `v1.3` 的分析
可以并行；功能实现仍只处理当前版本，两者都应在大规模内容恢复前通过。

## 4. 合并门禁

后续版本允许提前只读研究、测试设计和数据样例；功能实现仍受 AGENTS
版本范围限制。当前版本内部可按第 14 号规约使用独立 worktree。集成必须满足：

```text
只有当前最早未通过、依赖已通过、公共契约已冻结、迁移路径已存在的版本，
才可进入 integration/<version>。
```

例如 `v1.2` 尚在集成时，可以提前：

- 审计旧行星资产；
- 编写 `v1.4` 数据 schema 样例；
- 设计天空视觉测试；
- 研究旧卫星类型；

但不得把依赖未冻结的生产代码合入主线。

## 5. “完整迁移”的判定方法

不以文件数量或版本号判断。`docs/PORTING_MATRIX.md` 每个旧功能必须最终归入：

| 状态 | 含义 |
|---|---|
| `PASSED_EQUIVALENT` | 已有现代实现并通过行为验收 |
| `PASSED_REDESIGNED` | 玩法目标保留，但实现/交互有明确重设计 |
| `DEFERRED` | 经 ADR 明确推迟，不计入当前发布承诺 |
| `REJECTED` | 安全、维护或平台原因明确不迁移 |
| `MISSING` | 尚未实现，阻断对等发布 |
| `UNKNOWN` | 尚未完成上游审计，阻断对等发布 |

以上是目标对等结论，不能直接覆盖现有矩阵的实施状态。已有 `PASSED` 仅表示
原垂直切片通过，不自动等于完整旧功能对等；`NOT_AUDITED` 等未审计行仍是
未决项。进入对等审计时逐项补充对等结论、来源和证据。

`v2.0.0` 不允许存在 `MISSING` 或 `UNKNOWN`，也不得漏掉现有未审计行。
`DEFERRED/REJECTED` 必须有公开批准 ADR 和玩家影响说明。

[ADR-018](decisions/ADR-018-PARITY-FIRST-FULL-ACCEPTANCE-SCHEDULING.md) 将完整的
长时负载、远程专服、真实 GPU、双客户端及全机械/全维度集成验收排到原有
机械和维度全部实现之后；仅标记 `DEFERRED`/`REJECTED` 不触发完整测试。
各实现切片仍必须运行定向测试、构建以及其
新增行为对应的守恒、迁移、安全和恢复检查；该排期不产生任何 Gate PASS。

## 6. 存档兼容路线

```text
v0.9 save
  ↓ verified migration
v1.0 save
  ↓
v1.1 destination/body-context migration
  ↓
v1.2 machine/multiblock migration
  ↓
v1.3 API metadata migration
  ↓
v1.4+ content schema migrations
  ↓
v2.0
```

每个版本必须：

- 保留上一个稳定版本的代表性 fixture；
- 只迁移副本，不覆盖原始 fixture；
- 迁移可重复执行或明确拒绝二次执行；
- 在失败时给出可恢复错误；
- 记录 schema、DataVersion、network protocol；
- 不把“世界能打开”当作迁移完成。

1.12.2 世界直接升级不是默认承诺。若后续研究旧世界迁移，优先做**离线转换器**：

```text
只读旧世界副本
  ↓
生成迁移报告和中间清单
  ↓
转换受支持的方块/资产/玩家数据
  ↓
输出新的 1.20.1 世界副本
```

## 7. 发布节奏

建议：

```text
v1.x.0      新能力或内容里程碑
v1.x.y      仅兼容、稳定、迁移和安全修复
v1.x.0-rcN  候选版
v1.x.0-betaN 大范围玩家验证版
```

每个 `.0` 版本记录以下执行阶段（`phase`，不是版本 `status`）：

```text
CONTRACT_FROZEN
→ IMPLEMENTING
→ INTEGRATING
→ FEATURE_FREEZE
→ RELEASE_CANDIDATE
→ 交由独立审核
```

版本状态仅沿用主路线的 `PLANNED / IN_PROGRESS / BLOCKED / READY_FOR_AUDIT /
PASSED / RELEASED`；阶段完成不构成版本通过。

## 8. 最低证据规则

每个版本必须提交：

```text
docs/releases/<version>/
├─ RELEASE-EVIDENCE.md
├─ TEST-REPORT.md
├─ MANUAL-TEST.md
├─ MIGRATION-REPORT.md
├─ PERFORMANCE.md
├─ SECURITY-REVIEW.md
├─ KNOWN-ISSUES.md
├─ checksums.txt
└─ GATE-STATUS.md
```

含视觉内容的版本另加：

```text
VISUAL-VALIDATION-REPORT.md
screenshots/
videos-or-links.md
```

## 9. 版本声明边界

| 版本 | 推荐公开称呼 |
|---|---|
| `v1.0.0` | Stable Core MVP |
| `v1.4.0` | Multi-Planet Preview/Release |
| `v1.8.0` | Classic Content Complete Candidate |
| `v1.9.0` | Feature Parity Beta |
| `v2.0.0` | Classic Feature Parity Stable |

在 `v2.0.0` 之前，不使用“完整移植完成”作为版本宣传主标题。
