# 先读我：如何使用这套文档启动项目

这不是一份“建议清单”，而是一套 **按版本设置完成门槛的执行规约**。推荐把整个目录复制到新仓库根目录，再让 Codex 从 `AGENTS.md` 开始工作。

## 最短使用路径

1. 人工检查并填写 [`PROJECT-CONFIG.md`](PROJECT-CONFIG.md)。
2. 在 GitHub 新建空仓库，暂时不要导入原项目源码。
3. 将本目录全部提交为第一个规划提交。
4. 把 [`codex-prompts/00-initialize-repository.md`](codex-prompts/00-initialize-repository.md) 交给 Codex。
5. Codex 只能完成 `v0.0.1`，不得直接开始 Forge 代码。
6. 使用 [`codex-prompts/03-audit-current-version.md`](codex-prompts/03-audit-current-version.md) 审核该版本。
7. 所有 Required Gate 通过后，才使用 `02-implement-next-version.md` 进入下一版。

## 文档阅读顺序

```text
PROJECT-CONFIG.md
AGENTS.md
PRODUCT.md
docs/01-PORTING-PRINCIPLES.md
docs/04-VERSION-ROADMAP.md
docs/versions/<当前版本>.md
docs/05-MASTER-TEST-PLAN.md
docs/06-RELEASE-AND-ACCEPTANCE-GATES.md
```

涉及具体系统时，再读取：

- 天体、维度、火箭、大气、空间站：`docs/03-TARGET-ARCHITECTURE.md`
- 上游代码和资产：`docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md`
- 存档、NBT、网络协议：`docs/07-SAVE-DATA-AND-NETWORK-VERSIONING.md`
- 资产来源与授权：`docs/08-ASSET-LICENSE-AND-PROVENANCE.md`
- GitHub 设置和公开声明：`docs/09-GITHUB-REPOSITORY-SETUP.md`

## 这套方案刻意避免的错误

- 不从 1.12.2 源码开始逐个修编译错误；
- 不先复刻完整 LibVulpes；
- 不一次导入所有方块、机器和纹理；
- 不把“客户端能进游戏”当作版本完成；
- 不允许没有测试证据就勾选完成；
- 不允许 Codex 为了让测试通过而降低验收标准；
- 不承诺首版直接加载 1.12.2 世界；
- 不在 `v0.6.0` 前扩展卫星、跃迁、采矿等外围内容。

## 预期里程碑

最终 `v1.0.0` 的核心体验是：

> 玩家在地球上建造一枚由真实方块组成的火箭，完成燃料与生命保障准备，安全飞往月球、着陆、返回；服务器在飞行或转移中重启后，火箭、乘客和库存仍可恢复，且不存在复制或丢失。

这条闭环未通过前，项目仍是技术原型，不是可发布的 Advanced Rocketry 社区续作。
