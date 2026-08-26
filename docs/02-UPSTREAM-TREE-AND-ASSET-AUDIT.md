# 02 — Upstream Tree and Asset Audit / 上游代码树与资产审计

## 1. 目标

在复制任何旧文件之前，先把原项目变成一份可查询的“行为—源码—资产—风险”地图。

上游 1.12 分支公开展示的核心 Java 根包为：

```text
src/main/java/zmaster587/advancedRocketry/
```

已知领域包括：

```text
advancements  api  armor  asm  atmosphere  backwardCompat
block  cable  capability  client  command  common  dimension
enchant  entity  event  integration  inventory  item  mission
network  recipe  satellite  stations  tile  unit  util  world
```

资源根目录为：

```text
src/main/resources/assets/advancedrocketry/
```

审计不得只依赖目录名。Codex 应在本地克隆的准确 commit 上重新生成完整清单。

## 2. 审计阶段禁止事项

- 不把旧 `src/` 整体复制进新仓库；
- 不先改 package 名再审计；
- 不运行自动“API 替换器”批量迁移；
- 不把 LibVulpes 源码内嵌；
- 不从编译后的 JAR 反编译；
- 不把所有资源先放进新 JAR；
- 不根据文件名猜许可证。

## 3. 必须生成的产物

```text
legacy-manifest/
├─ UPSTREAM_COMMIT.txt
├─ java-files.csv
├─ java-packages.csv
├─ dependency-imports.csv
├─ libvulpes-usage.csv
├─ static-world-state.csv
├─ network-packets.csv
├─ entities.csv
├─ block-entities.csv
├─ registries.csv
├─ recipes.csv
├─ assets.csv
├─ asset-references.csv
├─ missing-asset-references.csv
├─ duplicate-case-paths.csv
├─ large-files.csv
├─ asm-and-coremod.csv
└─ audit-summary.md
```

## 4. Java 审计字段

`java-files.csv` 至少包含：

```csv
path,package,lines,bytes,sha256,primary_domain,imports_libvulpes,imports_client,has_static_mutable_state,has_nbt,has_network,has_dimension_logic,notes
```

额外扫描：

- 超过 500、800、1500 行的类；
- `static Map/List/Set` 等可变集合；
- 数字维度 ID；
- world/player/entity 的静态缓存；
- 客户端类进入 common 代码；
- ASM/coremod；
- 直接线程创建；
- `readFromNBT` / `writeToNBT`；
- 网络包中位置与 NBT；
- 可能加载区块的调用；
- 任意方块实体 NBT 复制；
- 多方块结构匹配；
- 反射使用；
- 与 LibVulpes 的继承和接口耦合。

## 5. 资产审计字段

`assets.csv` 至少包含：

```csv
source_path,kind,bytes,width,height,color_mode,sha256,license_status,source_commit,target_version,target_path,transformation,status,notes
```

`kind` 示例：

```text
texture_block
texture_item
texture_gui
texture_planet
texture_entity
model_json
model_obj
model_mtl
sound_ogg
sound_definition
lang
recipe
advancement
blockstate
other
```

检查：

- 文件路径是否全部小写；
- Windows 下大小写冲突；
- JSON 可解析性；
- blockstate → model → texture 引用链；
- OBJ → MTL → texture 引用链；
- `sounds.json` → OGG；
- 孤立资源；
- 重复哈希；
- 非标准编码；
- PNG 颜色模式和透明通道；
- 资源是否来自 LibVulpes 而非 Advanced Rocketry；
- 文件内是否有第三方作者声明。

## 6. 功能到版本映射

审计结果必须写入 `docs/PORTING_MATRIX.md`，每个功能至少包含：

```text
旧行为
旧源码入口
旧资产入口
旧依赖
已知旧 bug
新目标模块
目标版本
自动测试
人工验收
是否进入 v1.0
```

## 7. 行为金样

在可运行的 1.12.2 环境中建立固定测试世界和记录。至少记录：

- 一台代表性机器的输入、耗时、能耗、输出；
- 月球重力和真空行为；
- 氧气房间密闭/破坏流程；
- 典型火箭结构、质量、推力、燃料与可达目的地；
- 发射、转移、降落和拆解；
- 空间站创建；
- 一种卫星任务；
- 已知失败案例。

产物：

```text
legacy-manifest/golden-behavior/
├─ TEST-CASES.md
├─ values.json
├─ screenshots/
├─ videos/            # 可只存链接和哈希
└─ worlds/README.md   # 不提交未获授权的大体积世界包
```

## 8. 建议实现的审计工具

```text
tools/audit/
├─ audit_java_tree.py
├─ audit_assets.py
├─ verify_resource_references.py
├─ detect_case_collisions.py
├─ generate_porting_matrix.py
└─ verify_provenance.py
```

要求：

- 输入路径和 commit 明确；
- 输出排序稳定；
- 同一输入重复运行不得产生无意义 diff；
- 失败返回非零退出码；
- 可在 CI 验证当前导入文件是否有来源记录。

## 9. 审计通过条件

- 上游 commit 已锁定；
- 所有拟导入文件都有哈希；
- 旧代码主要领域均进入矩阵；
- LibVulpes 依赖点可查询；
- ASM/coremod 点已列出并标记“不迁移”；
- 资产缺失引用、大小写冲突和第三方来源已列出；
- v1.0 范围与推迟范围已明确；
- 审计脚本可重复运行；
- 尚未出现未经记录的上游文件。
