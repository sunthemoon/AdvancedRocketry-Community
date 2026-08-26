# PROJECT-CONFIG — 开工前唯一需要人工确认的项目变量

> 状态：`APPROVED`
>
> 使用方法：第一次交给 Codex 前，确认本页；后续代码、文档、构建脚本、发布名均以本页为准。任何身份变量变更都必须同步更新 `README.md`、`NOTICE.md`、`mods.toml`、Maven 坐标和资源命名空间。

## 1. 推荐默认值

| 变量 | 推荐值 | 说明 |
|---|---|---|
| GitHub owner | `sunthemoon` | 可替换为个人账号或新组织 |
| repository | `AdvancedRocketry-Community` | 新建独立仓库，不默认使用 GitHub Fork |
| display name | `Advanced Rocketry: Community Edition` | 暂定公开显示名 |
| short name | `ARCE` | 文档与日志缩写 |
| Minecraft | `1.20.1` | 唯一首发目标 |
| loader | `Forge` | 不同时维护 Fabric/NeoForge |
| Java | `17` | Forge 1.20.1 开发基线 |
| Forge baseline | `47.4.10` | 推荐版本，作为可复现开发/发布基线 |
| Forge latest CI lane | `47.4.23` | 仅作为兼容性测试通道，不自动替换基线 |
| mod id | `advancedrocketrycommunity` | 避免在未沟通前直接占用原项目 `advancedrocketry` |
| legacy namespace | `advancedrocketry` | 只用于审计与资源映射，不直接作为新注册命名空间 |
| Maven group | `io.github.sunthemoon.advancedrocketrycommunity` | GitHub owner 变化时同步修改 |
| Java package root | `io.github.sunthemoon.advancedrocketrycommunity` | 禁止继续使用原 `zmaster587.*` 包名 |
| artifact id | `advancedrocketry-community` | 生成 JAR 的基础名 |
| code license | `MIT` | 保留原项目 MIT 声明，并对新增代码采用 MIT |
| default branch | `main` | 受保护分支 |
| release version format | `1.20.1-<semver>` | 例如 `1.20.1-0.6.0-alpha.1` |

## 2. 必须人工决定的两项

### 2.1 是否继续使用 “Advanced Rocketry” 名称

默认方案允许使用，但必须：

- 明确标注 **非官方社区续作/重写**；
- 不使用原项目作者或 Mojang/Microsoft/Forge 的官方身份暗示；
- 不把问题提交到原仓库；
- 若原作者提出合理的名称或品牌异议，准备更名；
- 在 `NOTICE.md`、README 顶部、发布页和模组描述中重复非官方声明。

更保守方案是另起主品牌，例如 `Rocketry Reignited`，并在副标题中写 “inspired by Advanced Rocketry”。选择更保守方案时，应在 `v0.0.1` 完成前一次性修改本页。

### 2.2 是否沿用原 `advancedrocketry` mod id

默认：**不沿用**。

原因：

- 防止与未来官方版本或其他社区版直接冲突；
- 避免玩家误认官方；
- 迫使迁移脚本显式记录资源转换，而不是悄悄复用；
- 1.20.1 没有需要直接加载的原版世界，因此短期兼容收益较低。

只有在原维护者明确同意、并完成冲突评估后，才可通过 ADR 改为原 mod id。

## 3. 版本兼容承诺

| 阶段 | 世界存档承诺 |
|---|---|
| `v0.0.x–v0.4.x` | 测试世界可随时废弃 |
| `v0.5.x–v0.8.x` | 尽量迁移，但允许在发布说明中声明一次性重置 |
| `v0.9.x` | 同一 Beta 主版本间必须提供迁移或明确阻止加载 |
| `v1.0.0+` | 1.x 内不得无迁移破坏存档；所有持久化对象必须带 schema 版本 |

## 4. 配置确认记录

在首次提交前，将以下内容改为实际值：

```yaml
reviewed_by: "<GitHub 用户名>"
reviewed_at: "YYYY-MM-DD"
identity_status: "APPROVED"
```

当前值：

```yaml
reviewed_by: "sunthemoon"
reviewed_at: "2026-08-26"
identity_status: "APPROVED"
```
