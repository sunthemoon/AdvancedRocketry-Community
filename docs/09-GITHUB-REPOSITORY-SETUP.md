# 09 — GitHub Repository Setup / GitHub 建仓与公开声明

## 1. 新仓库还是 Fork

### 推荐：新建独立仓库

推荐名称：

```text
sunthemoon/AdvancedRocketry-Community
```

原因：

- 本项目是架构重写，不是准备回合并到 1.12 分支的补丁；
- 保持干净的 Java 17/Forge 1.20.1 历史；
- 避免大量旧构建文件和旧 issue 语义；
- 所有导入必须经过清单，能防止无意识搬入；
- 公开身份可明确为社区项目。

通过 `UPSTREAM.md`、`NOTICE.md` 和 provenance 维持来源关系。

### 可选：GitHub Fork + orphan branch

仅当你非常重视 GitHub fork network 展示时使用。缺点是：

- 历史与新架构混杂；
- compare/PR 默认指向上游会造成噪声；
- 更容易让人误认是官方升级分支；
- 可能限制账号内同网络 fork 管理。

## 2. 建仓顺序

1. 创建空仓库，不自动添加 GitHub LICENSE/README；
2. 先保持 private，完成 `v0.0.1` 审计；
3. 提交本规划包；
4. 设置 About；
5. 添加规则集和 Issue 模板；
6. 运行许可证/来源检查；
7. 准备好 README 非官方声明后再 public；
8. public 后发布一个 “Planning only / no binaries” 的首个说明，而不是可玩 release。

## 3. Repository About

推荐：

```text
Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.
```

Topics：

```text
minecraft
minecraft-mod
forge
forge-mod
minecraft-1-20-1
advanced-rocketry
space
java
community-edition
```

不要在 About 中写 “official port”。

## 4. README 顶部必须声明

```text
Unofficial community rewrite for Minecraft 1.20.1 Forge.
Not an official continuation and not maintained or supported by the original Advanced Rocketry maintainers.
NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
```

## 5. LICENSE / NOTICE / UPSTREAM

根目录至少：

```text
LICENSE
NOTICE.md
UPSTREAM.md
BRANDING_AND_AFFILIATION.md
```

GitHub 只有检测到标准 LICENSE，不代表第三方资产已经合规；provenance 仍是独立 Gate。

## 6. `mods.toml` 声明建议

```toml
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"
issueTrackerURL="https://github.com/sunthemoon/AdvancedRocketry-Community/issues"

[[mods]]
modId="advancedrocketrycommunity"
version="${file.jarVersion}"
displayName="Advanced Rocketry: Community Edition"
displayURL="https://github.com/sunthemoon/AdvancedRocketry-Community"
authors="Advanced Rocketry: Community Edition contributors"
credits="Based on the MIT-licensed Advanced Rocketry project. Unofficial community rewrite; not supported by the original maintainers."
description='''
An unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge.
NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
'''
displayTest="MATCH_VERSION"
features={java_version="[17,)"}
```

依赖：

```toml
[[dependencies.advancedrocketrycommunity]]
modId="forge"
mandatory=true
versionRange="[47.4.10,48)"
ordering="NONE"
side="BOTH"

[[dependencies.advancedrocketrycommunity]]
modId="minecraft"
mandatory=true
versionRange="[1.20.1,1.20.2)"
ordering="NONE"
side="BOTH"
```

## 7. 分支策略

推荐 trunk-based：

```text
main                      # 受保护、始终可构建
codex/v0.4.0-atmosphere-* # 功能分支
fix/v0.6.0-*              # 修复
docs/*                    # 文档
```

不建立长期 `develop`，减少个人/Codex 多会话合并成本。

## 8. Ruleset

`main`：

- 必须 PR；
- 禁止 force push；
- 禁止删除；
- 必须通过 build、test、GameTest、data/provenance checks；
- 要求分支为最新可选；
- 至少一次人工批准：个人仓库可在版本 tag 前人工确认，不必强制 GitHub reviewer；
- 线性历史可选；
- release tag 仅维护者创建。

## 9. Labels

```text
type:bug
type:feature
type:porting
type:test
type:docs
system:build
system:asset
system:machine
system:celestial
system:atmosphere
system:rocket
system:station
system:satellite
risk:save
risk:duplication
risk:performance
risk:license
gate:blocked
gate:ready-for-audit
status:needs-reproduction
good-first-issue
```

## 10. Issue 模板要求

Bug：

- ARCE、Forge、Java 版本；
- 单人/专服；
- 最小模组列表；
- 重现步骤；
- 日志；
- 世界是否可重现；
- 是否涉及复制/存档；
- 客户端与服务端是否一致。

Porting task：

- 目标版本；
- 原行为；
- 上游文件；
- 新模块；
- 非目标；
- 自动测试；
- 人工验收；
- provenance；
- save/network 影响。

## 11. Release 规则

发布 JAR 必须：

- 来自已标记 commit；
- 带 LICENSE/NOTICE；
- 通过版本 Gate；
- 附 `checksums.txt`；
- 附测试报告和 Known Issues；
- alpha/beta 正确标为 pre-release；
- 不附未经审计的整包资源；
- 不把 GitHub Actions 临时 artifact 当正式 release。

## 12. GitHub Security/社区设置

开启：

- Dependabot alerts；
- Secret scanning（公开仓库可用时）；
- Private vulnerability reporting（可用时）；
- Discussions 可等 `v0.6.0` 后；
- Wiki 可暂缓，文档先跟代码同仓库；
- Issues；
- branch/tag protection。

## 13. 初次公开前检查

- [ ] README 第一屏有非官方声明
- [ ] 原 MIT notice 保留
- [ ] GitHub 显示 MIT license
- [ ] 仓库描述不含 official
- [ ] 没有未经审计的上游二进制或资产
- [ ] issue 模板不把用户导向原仓库
- [ ] SECURITY、CONTRIBUTING、CODE_OF_CONDUCT 存在
- [x] `PROJECT-CONFIG.md` 已从 DRAFT 改为 APPROVED
- [ ] 当前状态明确写“无可玩发布”
