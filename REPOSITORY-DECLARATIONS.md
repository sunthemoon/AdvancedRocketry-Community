# REPOSITORY-DECLARATIONS — 可直接使用的仓库声明文案

> 使用前把 owner/repository 改为实际值。项目名仍为暂定时，不要删除 “unofficial”。

## 1. GitHub Repository Description

```text
Unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge. Pre-alpha; not supported by the original maintainers.
```

较短版本：

```text
Unofficial Advanced Rocketry community rewrite for Forge 1.20.1.
```

## 2. README 第一屏

```markdown
# Advanced Rocketry: Community Edition

> **Unofficial community rewrite for Minecraft 1.20.1 Forge.**
>
> This project is not an official continuation and is not maintained or supported by the original Advanced Rocketry maintainers.
>
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
```

中文补充：

```markdown
> 本项目是面向 Minecraft 1.20.1 Forge 的非官方社区重写，不是 Advanced Rocketry 官方续作，也不由原维护者提供支持。
```

## 3. 上游归属段落

```markdown
## Attribution

This project may contain audited portions derived from the MIT-licensed
Advanced-Rocketry/AdvancedRocketry project. The original MIT notice
(`Copyright (c) 2017`) is preserved in this repository.

Every imported or transformed upstream file is recorded with its source
repository, branch, commit, path, hash, license, and transformation.

Do not report Community Edition bugs to the original Advanced Rocketry maintainers.
```

## 4. 当前状态

规划阶段：

```markdown
## Status

**Pre-alpha / planning and architecture phase. No playable public release is available yet.**
```

技术原型：

```markdown
## Status

**Developer preview. Test worlds are disposable and no save compatibility is promised.**
```

Alpha：

```markdown
## Status

**Playable alpha. Core systems are under active testing; back up worlds before every update.**
```

Beta：

```markdown
## Status

**Beta. Core gameplay is feature-frozen while save migration, multiplayer, performance, and compatibility are being validated.**
```

Stable：

```markdown
## Status

**Stable community MVP for Minecraft 1.20.1 Forge. Review Known Issues and back up worlds before upgrading.**
```

## 5. GitHub Release 顶部

```markdown
> This is an unofficial community build and is not supported by the original Advanced Rocketry maintainers.
>
> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
```

Alpha/Beta 必加：

```markdown
This is a pre-release. Do not use it on an irreplaceable world without a tested backup.
```

## 6. Issue Tracker 提示

```markdown
This issue tracker is for Advanced Rocketry: Community Edition only.
Do not forward these reports to the original Advanced Rocketry project.
```

## 7. `mods.toml` credits

```toml
credits="Based on the MIT-licensed Advanced Rocketry project. Unofficial community rewrite; not supported by the original maintainers."
```

## 8. `mods.toml` description

```toml
description='''
An unofficial community rewrite of Advanced Rocketry for Minecraft 1.20.1 Forge.
Build block-based rockets, survive vacuum, travel to the Moon, and establish space infrastructure.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
'''
```

## 9. 支持范围

```markdown
## Support

Supported target:

- Minecraft 1.20.1
- Forge 47.4.10 baseline
- Java 17
- Dedicated server and client

Not supported:

- Fabric or NeoForge builds
- Direct loading of Advanced Rocketry 1.12.2 worlds
- Unverified third-party forks
- Large modpack reports without a minimal reproduction
```

## 10. 许可证简述

```markdown
## License

Code and audited imported portions are distributed under the MIT License.
The original Advanced Rocketry MIT notice is preserved. See LICENSE, NOTICE.md,
UPSTREAM.md, and the provenance records for details.
```

## 11. 不应使用的文案

```text
Official Advanced Rocketry 1.20.1 port
Authorized official continuation
Maintained by the Advanced Rocketry team
Fully compatible with the original
All old worlds supported
All assets are free to use
```

这些表述要么不真实，要么超出当前验证范围。
