# UPSTREAM.md — 上游来源和参考边界

## 1. 主上游

```text
repository: https://github.com/Advanced-Rocketry/AdvancedRocketry
primary_branch: 1.12
license: MIT
upstream_commit: c5cd5af62fc07cd4e0d24f06a16033f181c47c04
verified_at: 2026-08-26
```

`1.12` 分支承担三种角色：

1. 行为基线：旧版玩家实际看到的功能和流程；
2. 数值基线：配方、燃料、推力、重力、机器时间等；
3. 可审计资产来源：纹理、模型、声音、语言和数据。

它不承担新架构基线。

## 2. 次级参考

原项目曾存在 1.16.5 构建和相关代码历史。它可以用于理解原作者曾如何适应较新的 Minecraft API，但必须：

- 先定位准确分支或 commit；
- 确认该文件仍属于原项目 MIT 范围；
- 只作为语义参考；
- 不以该分支作为 Gradle 升级起点；
- 不因为代码“更接近现代”就跳过重写和测试。

## 3. 默认禁止复制的来源

除非单独完成许可审核：

- Advanced Rocketry - Reworked；
- ARLib；
- Advanced Rocketry 3；
- 未声明许可证的 LibVulpes 内容；
- 其他 GitHub fork；
- CurseForge/Modrinth 发布包内无来源说明的资产；
- 反编译 JAR。

## 4. 本地仓库建议

新项目保持独立 Git 历史，并将上游作为只读远程或相邻目录：

```bash
git remote add upstream-ar https://github.com/Advanced-Rocketry/AdvancedRocketry.git
git fetch upstream-ar 1.12
git remote set-url --push upstream-ar DISABLED
```

禁止直接 merge 上游 1.12 到 `main`。

推荐使用：

```text
../AdvancedRocketry-upstream/        # 只读审计副本
./legacy-manifest/                   # 审计结果，不存整份旧源码
./docs/provenance/                   # 实际导入记录
./tools/import/                      # 可复现转换脚本
```

## 5. 每次导入必须记录

```yaml
target_path:
source_repository:
source_branch:
source_commit:
source_path:
source_sha256:
target_sha256:
license:
transformation:
reviewer:
reviewed_at:
```

没有记录的文件不得进入发布 JAR。
