# 06 — Release and Acceptance Gates / 发布与验收门槛

## Gate G0 — Identity, License, Provenance

必须：

- `LICENSE` 存在且保留原 `Copyright (c) 2017`；
- `NOTICE.md` 和 README 有非官方声明；
- Minecraft 非官方声明可见；
- 导入文件有来源、commit、路径、hash、license；
- 未混入未审核社区 fork 内容；
- `mods.toml` license/credits/description 与仓库一致。

证据：

```text
license scan
provenance report
README screenshot
mods.toml excerpt
```

## Gate G1 — Reproducible Build

必须：

```bash
./gradlew clean build
```

在干净环境、Java 17 成功；JAR 命名和版本正确；无凭据进入产物。

证据：

```text
CI run
build log
JAR SHA-256
Java/Gradle/Forge versions
```

## Gate G2 — Data and Generated Resources

必须：

```bash
./gradlew runData
git diff --exit-code
```

且：

- JSON 可解析；
- 模型、纹理、声音引用存在；
- 无大小写冲突；
- 注册对象有对应数据；
- 生成结果稳定。

## Gate G3 — Automated Behavior

必须：

```bash
./gradlew test
./gradlew runGameTestServer
```

当前版本指定测试全部通过；失败和跳过均有解释。

## Gate G4 — Dedicated Server and Sides

必须：

- 专服启动；
- 玩家可加入；
- 无客户端类加载错误；
- 目标流程可在专服完成；
- 两名玩家状态一致；
- 可选客户端模组缺失不影响服务端。

## Gate G5 — Persistence and Recovery

适用于有持久化状态的版本：

- 保存/重启后数据一致；
- schema 版本正确；
- 旧格式迁移有测试；
- 崩溃恢复不会复制或丢失；
- 不支持的未来/降级格式明确拒绝。

## Gate G6 — Security and Authority

适用于网络、火箭、站点、库存功能：

- C2S 不信任客户端结果；
- 权限、距离、状态和区块加载检查存在；
- 超大/恶意请求安全失败；
- 无任意区块加载；
- 无已知复制；
- 请求有合理频率限制或天然幂等。

## Gate G7 — Performance

当前版本预算通过：

- 世界扫描有硬预算；
- 最大允许结构可处理；
- 无每 tick 全量扫描；
- 无无界缓存；
- 无持续强制加载；
- 性能报告包含环境与采样结果。

## Gate G8 — Manual Player Flow

当前版本的人工测试清单全部执行：

- 截图/录像；
- 构建 hash；
- 测试人和日期；
- 预期与实际；
- 已知问题。

## Gate G9 — Documentation and Release

必须：

- README 状态准确；
- Changelog；
- 安装与依赖；
- 存档兼容说明；
- Known Issues；
- 发布证据；
- checksums；
- GitHub Release 为 pre-release 或 stable 的标记正确；
- 不把开发构建误称 stable。

## Gate 状态格式

```yaml
version: v0.6.0
build: 1.20.1-0.6.0-alpha.1
commit: <sha>
gates:
  G0: PASS
  G1: PASS
  G2: PASS
  G3: PASS
  G4: PASS
  G5: PASS
  G6: PASS
  G7: PASS
  G8: READY_FOR_HUMAN_REVIEW
  G9: NOT_STARTED
overall: READY_FOR_AUDIT
```

## 豁免

Required Gate 不应常规豁免。确需豁免时：

```text
ADR
原因
风险
负责人
用户影响
临时缓解
到期版本
自动失败提醒
```

以下不可豁免：

- 原许可证声明；
- 核心复制漏洞；
- 已知存档损坏；
- 远程崩服；
- 客户端完全决定服务端结果；
- 发布 JAR 包含来源不明资产。

## 发布批准

建议两级确认：

1. Codex/开发者生成证据并标记 `READY_FOR_AUDIT`；
2. 人工审查证据后标记 `PASSED`；
3. 只有 `PASSED` 才创建 tag/release。

单人项目也应保留第二步，避免同一执行会话自证完成。
