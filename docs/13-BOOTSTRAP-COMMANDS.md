# 13 — Bootstrap Commands / 推荐建仓命令

> 先确认 `PROJECT-CONFIG.md`。以下使用推荐默认仓库名；实际 owner 不同时替换。

## 1. 新建本地仓库

```bash
mkdir AdvancedRocketry-Community
cd AdvancedRocketry-Community
git init -b main
```

把本规划包内容复制到仓库根目录后：

```bash
git add .
git commit -m "docs: establish community rewrite governance and roadmap"
```

## 2. 使用 GitHub CLI 新建私有仓库

```bash
gh repo create sunthemoon/AdvancedRocketry-Community \
  --private \
  --source=. \
  --remote=origin \
  --push \
  --description "Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers."
```

先 private 完成 `v0.0.1`。公开前执行清单：

```text
docs/09-GITHUB-REPOSITORY-SETUP.md
```

然后再从 GitHub 设置或 CLI 改为 public。

## 3. 添加只读上游 remote

```bash
git remote add upstream-ar https://github.com/Advanced-Rocketry/AdvancedRocketry.git
git fetch upstream-ar 1.12
git remote set-url --push upstream-ar DISABLED
```

不要执行：

```bash
git merge upstream-ar/1.12
```

上游用于审计，不直接合并到 `main`。

## 4. 开始第一个 Codex 分支

```bash
git switch -c docs/v0.0.1-governance
```

将 `codex-prompts/00-initialize-repository.md` 作为任务输入。

## 5. 后续 worktree 示例

```bash
git worktree add ../arce-v002 -b codex/v0.0.2-forge-bootstrap main
```

一个 worktree/分支只处理一个版本或一个 PR 切片。

## 6. 发布前常用命令

```bash
./gradlew clean build
./gradlew test
./gradlew runData
git diff --exit-code
./gradlew runGameTestServer
sha256sum build/libs/*.jar
```

Windows PowerShell 的 SHA-256：

```powershell
Get-FileHash .\build\libs\*.jar -Algorithm SHA256
```

## 7. 不要在第一步做的事

```text
- 不 fork 后直接改旧 build.gradle
- 不复制旧 src/main
- 不导入所有 assets
- 不把原 JAR 放进仓库
- 不创建“正式版” release
- 不把仓库一开始描述为 official port
```
