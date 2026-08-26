# 08 — Asset License and Provenance / 资产授权与来源

## 1. 资产状态

每个文件只能处于：

```text
NEW
UPSTREAM_AR_MIT
THIRD_PARTY_APPROVED
GENERATED
QUARANTINED
REJECTED
```

`QUARANTINED` 和 `REJECTED` 不得进入 `src/main/resources` 或发布 JAR。

## 2. 来源记录

推荐每个导入批次一个 YAML/JSON：

```yaml
target: src/main/resources/assets/advancedrocketrycommunity/textures/block/rocket_motor.png
status: UPSTREAM_AR_MIT
source_repository: Advanced-Rocketry/AdvancedRocketry
source_branch: 1.12
source_commit: "<sha>"
source_path: src/main/resources/assets/advancedrocketry/textures/blocks/rocketmotor.png
source_sha256: "<hash>"
target_sha256: "<hash>"
license: MIT
transformation:
  - rename namespace
  - rename path to lowercase singular folder convention
reviewer: "<name>"
reviewed_at: "YYYY-MM-DD"
```

## 3. 可复现转换

优先写脚本：

- `.lang` → JSON；
- namespace 改写；
- 大小写规范化；
- recipe 格式转换；
- model/texture 引用重写；
- OBJ/MTL 路径修复。

不要只手工复制后提交。脚本和输出都应被审查。

## 4. 原项目 MIT 的实际操作

原仓库根 LICENSE 为 MIT，并要求在软件副本或重要部分中保留版权和许可声明。因此：

- 根 `LICENSE` 保留原 notice；
- `NOTICE.md` 指向原仓库；
- 导入记录保留来源；
- 二进制发布包含 LICENSE/NOTICE；
- 不删除源文件中已有作者或版权头。

## 5. LibVulpes 边界

原 Advanced Rocketry 深度依赖 LibVulpes，但不应把“依赖关系”误认为“可自动复制”。

在确认 LibVulpes 对应 branch/commit 的许可证前：

- 只记录 API 使用方式；
- 不复制类；
- 不复制 GUI、模型或声音；
- 用新实现替代当前所需能力。

若许可证无法明确，保持 clean-room 风格：依据行为和接口需求重新实现。

## 6. 其他社区项目

下列项目必须各自审计：

- Advanced Rocketry - Reworked；
- ARLib；
- Advanced Rocketry 3；
- 任何模组包 fork；
- 社区修复包；
- 社区汉化；
- Wiki 截图和素材。

“能下载”“公开 GitHub”“作者也是社区成员”都不等于可复制。

## 7. Minecraft 和其他官方资产

不得随 JAR 分发：

- Minecraft 原版纹理、声音、模型或字体副本；
- Mojang/Microsoft Logo；
- Forge Logo（除非遵循其明确使用条件）；
- 从游戏 JAR 提取后略改的资产。

可以通过合法的资源引用、标签、配方和运行时 API 使用原版内容，而不是复制文件。

## 8. 模型策略

### JSON 模型

优先用于普通方块/物品，便于 DataGen 和引用校验。

### OBJ/MTL

复杂机器可暂时保留，但必须验证：

- loader 支持；
- MTL 和纹理引用；
- 坐标、朝向、缩放；
- 专服不加载客户端模型类；
- 许可证和来源；
- 渲染性能。

逐步将简单 OBJ 转换为 JSON/Blockbench，而不是一次性重制所有美术。

## 9. 声音

检查：

- OGG 可解码；
- 单/立体声是否符合使用场景；
- `sounds.json` ID；
- 音量与循环；
- 来源；
- 不包含第三方音乐或未许可录音。

## 10. 发布前自动验证

CI 至少验证：

```text
所有资源有 provenance
所有 provenance target 存在
hash 匹配
无 QUARANTINED/REJECTED 文件进入 JAR
JSON 可解析
引用存在
无大小写冲突
LICENSE/NOTICE 被打包
```

## 11. 争议处理

发现来源不清：

1. 立即移入 quarantine；
2. 从发布分支移除；
3. 替换为临时原创占位；
4. 记录已发布版本是否包含；
5. 必要时撤下 release；
6. 取得许可或原创重制后再恢复。
