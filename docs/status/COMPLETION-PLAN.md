# 至完成的分会话计划

更新：2026-10-08。活动开发版本为 v1.8.0，后续由 Root 直接实施剩余设备和原生失败修订；
验收游标仍在 v1.0.0。分支为 `codex/v1.8.0-classic-content`。
已验证的开发进度按功能提交和非强制推送，不把提交当作 Gate 批准。
本计划只记录现状；历史运行与失败保留在各版本实施日志及证据中。
本会话结束现有切片并提交交接，不再派新任务；新会话从
[交接记录](../work/v1.8.0-session-handoff-20261008.md)恢复项目状态，不恢复本大会话。

## 固定规约

1. 读取 AGENTS.md 要求的文件和当前实施日志；公共契约先审核、接受，再实现。
2. 同版本任务使用独立 worktree 和互不重叠的 write_scope；中央文件由集成者独占。
3. 构建、GameTest、专服前检查空间，低于 10 GB 不启动。保留可复核的命令、
   日志、结果、补丁和清单，不重复打包完整源码；大型制品用明确外部位置和 SHA。
   临时脚本和输出放 `D:/GitHub/ARCE-Task-Evidence`；新验证子进程的 TEMP/TMP
   使用其任务目录，不修改全局环境或注册表。
4. 实际失败不改成通过，不放宽断言或预算；清理只涉及自己的已结束临时副本。
5. 已验证的阶段及时提交、推送；未知归属的未提交文件不接管，版本通过由维护者决定。

`[x]` 表示列明开发范围已有验证和提交，不表示整版发布通过；
`[~]` 表示实施中，`[ ]` 表示未完成，`[H]` 表示需要维护者或真实硬件。

## 已提交的前序开发范围

详细状态和证据分别见 [v1.5 日志](../work/v1.5.0-implementation-log.md)、
[v1.6 日志](../work/v1.6.0-implementation-log.md)、
[v1.7 日志](../work/v1.7.0-implementation-log.md)。继承的发布 Gate 仍开放。

- [x] C1：v1.5 审核修订。
- [x] C2：电梯端点和权限矩阵的开发切片。
- [x] C3：限定原生身份与恢复矩阵。
- [x] C4：按站点天空的开发实现；真实视觉未验收。
- [x] C5：v1.5 开发交付记录。
- [x] C6：v1.6 契约。
- [x] C7：卫星组件与代表类型。
- [x] C8a：任务预算、资源表和持久化策略。
- [x] C8b：资源任务与交付。
- [x] C9：v1.6 限定恢复和开发交付。
- [x] C10：v1.7 契约。
- [x] C11：激光、重力和权限审计框架。
- [x] C12：黑洞能源、电梯物流和目标系统。
- [x] C13：v1.7 破坏边界和限定恢复；参考硬件与刷写预算验收仍开放。

## v1.8.0 经典内容补齐

依据 [版本范围](../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md)、ADR-060–066
和 [实施日志](../work/v1.8.0-implementation-log.md)。诊断工具提交为
`f945dc1c33f09199da9d47663e891acf1cf4a475`，已推送；Java 制品绑定见最新证据。

### C14 移植清单与批次 `[x]`

处置、来源和批次契约已接受；不等于剩余内容已实现。

### C15 材料与行星开发切片 `[x]`

C15a 材料、小型压板机及其获取；C15b 地表、地物与行星矿石；C15c 系外世界、
合法非正方形火箭的着陆覆盖和审核修订已有提交。真实客户端与视觉验收未完成。

### C16 机器与科技依赖 `[~]`

- [x] C16a-01 源码范围：配方签名、旧任务暂停保留和菜单反馈。
  - [ ] 完整 S1、恶意回调、拆除与原生保存资格验证。
- [x] C16a-02：燃烧发电机开发切片。
- [x] C16a-03a：银行领域与有界模型 codec；仅模型资格。
- [~] C16a-03b：物理适配和共享保存。
  - [x] 01 数据范围：不可变值、原始根保留、固定容量。
  - [~] [原始根无损预检修正](../work/v1.8.0-c16a-hatches/RAW-FIDELITY-TASK-01.md)：
    [四文件源码](../work/v1.8.0-c16a-hatches/RAW-FIDELITY-SOURCE-01.md)经独立审核后
    提交、合并并推送至 `a34de0ad`；作者及独立 Java 定向验证各 75/75 通过。
    精确提交的全部 JUnit 通过；该提交整批 GameTest 曾失败，最新结果见下方回归记录，
    原始字节与计数已由 Root 独立审核。不开放保存 writer，不宣告物理 hatch 交付。
  - [x] K1：复合键排序的纯计算。
  - [x] K2：有界规范字节的私有计算、实际完整自动回归及独立证据审计。
  - [x] K3：私有 SHA-256 计算；源码审核、完整自动回归、独立证据审计与代码推送。
  - [x] 既有保存保护的有界诊断脚本及真实卸载事件观测器，已审核、提交和推送。
  - [x] 测试副本出生点迁移的设定源码，已审核、提交和推送；不作专服运行交付。
  - [x] SETUP05 固定专服诊断：首次真实卸载/实时恢复、第二主机重启拒存和字节保留，
    70.746109 秒实际通过、独立结果审核完成；四次原始失败不改写。
  - [~] [C16a-S1-GUARD-OBS04](../work/v1.8.0-c16a-save-guard/STATE-OBSERVATION-TASK-04.md)：
    固定只读状态观测、完整自动回归和独立结果审核已存证；原 native04 仍失败，清理被策略拒绝。
  - [x] [C16a-S1-GUARD-SETUP05](../work/v1.8.0-c16a-save-guard/SETUP05-VERIFICATION-01.md)：
    测试副本三处近邻标记隔离源码审核、集成与 Root 137 Python 通过；
    仅固定两主机原生诊断及独立结果审核已验证，不改 Java/保存行为、不开放新 writer；清理仍被拒绝。
  - [~] 完整 hash/frame/native codec、GuardTicket 与真实 owner 生命周期：
    owner 与比较器的双 LOAD 记录、EMIT 比较接入已独立审核并组合，
    相关测试已在终态托管回归实际执行；不替代已安装权限、完整 hatch、
    原生重新准入或失败处置验证。中央注册、物理接入和保存运行集成未完成，候选尚未交付。
    出站比较/保留观察提案的[原审核](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-seam-independent-review-20261007/REPORT-01.md)
    保留历史 Medium；LOAD 记录接口的另版补充已完成[独立复审](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-load-join-independent-review-20261007/REPORT-01.md)，
    提案文本补全记录接口和见证时序，无新增 C/H/M/L。
    [限定实施记录](../work/v1.8.0-c16a-hatches/LOAD-JOIN-IMPLEMENTATION-01.md)已按条件授权
    采纳私有技术补充；owner、精确根比较及真实区块观察器已在三个独立工作区写成，
    分别完成限定范围源码审核；[原私有检查点](../work/v1.8.0-c16a-hatches/PRIVATE-SOURCE-CHECKPOINTS-01.md)保留为历史。
    后继[未启用保存源码](../work/v1.8.0-c16a-hatches/GUARDED-SAVE-SOURCE-INTEGRATION-01.md)
    已独立核验组合和真实类型依赖，固定源码 139 个生产文件的有界编译通过；
    171 个输入无漂移，生成 189 个 class、保留一项警告；该局部编译不执行测试，
    而[终态托管结果](../work/v1.8.0-ci/RESULT-21.md)实际通过对应全部机器测试。
    49 个实际改动文件合入 `8f7e1d10` 并正常推送，注册/入口不变；
    物理/放置/拆除接入、完整生命周期处置与原生验证仍开放，不据此开放 writer。
  - [~] [公共保存接口](../work/v1.8.0-c16a-hatches/COMMON-GUARD-BRIDGE-SOURCE-01.md)：
    三个方法与五项新 GameTest 已独立审核、提交并正常推送至 `826f5f20`；
    限定缓存编译和五项原有领域单测通过；新五项 GameTest 已在精确提交上执行，
    前序回归的 Planetary 和 Tau 失败保留；最新批次通过不证明唯一原因。
    停服/最终保存/重启资格及物理机器交付未完成。
  - [x] [车床不可变配方行](../work/v1.8.0-c16a-hatches/RECIPE-ROWS-SOURCE-01.md)：
    三个精确冻结 record 经独立审核后提交、推送至 `60f15645`；固定提交单测 9/9，
    独立开发复跑 13/13。完整配方、native 解析、注册和首台车床仍未完成。
  - [x] [共享资源守卫数据与单测](../work/v1.8.0-c16a-hatches/GUARDED-RESOURCE-CHECKPOINT-01.md)：
    五个精确源码后像经独立审核后提交、推送至 `8d521406`；完整托管单测实际执行
    新增八项与原有 39 项资源用例并通过。三次本地失败不改写，作者已清理自有结束输出。
    仅限数据源码/单测范围；整体回归仍因 Tau required 失败，不开放物理 owner、保存 writer，
    不作台账交付或 Gate 批准。
  - [x] [有界原始区块元数据](../work/v1.8.0-c16a-hatches/CHUNK-METADATA-CHECKPOINT-01.md)：
    两个精确后像经源码及集成审核后提交、推送至 `3e2f6f1b`；固定提交定向测试 10/10，
    独立开发复测另有 10/10。只读取不可变坐标/type 记录，不安装 FULL 观察、owner 或保存接口。
    [精确源码完整回归](../work/v1.8.0-ci/RESULT-18.md)已结束，十项新单测也全部通过；
    整批仍因 required Tau 往返失败，不复用前一提交的结果。
  - [ ] [02 真实空 hatch 生命周期](../work/v1.8.0-c16a-hatches/EMPTY-LIFECYCLE-TASK-01.md)（in-progress）：
    [独立空 hatch 权限衔接](../work/v1.8.0-c16a-hatches/EMPTY-REMOVAL-GUARD-IMPLEMENTATION-01.md)
    的[源码检查点](../work/v1.8.0-c16a-hatches/EMPTY-REMOVAL-GUARD-INTEGRATION-01.md)
    已完成独立审核、修正回调后的实际方块状态检查并提交、推送至 `7d7b474e`；
    精确提交的[终态回归](../work/v1.8.0-ci/RESULT-22.md)已通过，新增权限边界测试实际执行。
    这不是物理生命周期交付。保存前置保护、生成/磁盘 Proto 观察、放置/取消/拆除
    期望记录、真实接入、最终卸载、保存 writer 与重启仍未实现或验证。
    [ADR-068](../decisions/ADR-068-V180-CLASSIC-NATIVE-OUTER-OPERATIONS.md)仅为提案：
    固定普通玩家的一个放置、两个拆除调用点，保留原版交互与异常语义。
    用户已于 2026-10-07 [选择限定拦截](../work/v1.8.0-c16a-hatches/OUTER-HOOK-OWNER-DECISION-01.md)，
    条件是独立审核、保存/异常恢复契约及实际验证后才开放物理 hatch。
    ADR 第 2 版的限定架构/决定范围已独立审核，未发现可操作问题，仍为 PROPOSED；
    具体契约、依赖和风险处置仍待审核，不安装 hook、不接受 R-021。
    [完整私有操作研究](../work/v1.8.0-c16a-hatches/PRIVATE-OPERATION-RESEARCH-01.md)
    已按固定源码形成提案；[独立分配审核](../work/v1.8.0-c16a-hatches/PRIVATE-OPERATION-REVIEW-01.md)
    留有两项 Medium：真实放置/结果认证和 provisional LOAD/可用性/终态绑定。
    未知结果与覆盖失败政策、源物品边界及跨存储守恒也未决定或证明；
    [原生放置事实](../work/v1.8.0-c16a-hatches/PLACEMENT-ENTRY-FACTS-01.md)已明确真实 pre-write context
    与同 context 直接调用边界，但未证明来源认证。完整后继契约与拦截兼容事实
    并行处理，不分配未接入的操作 helper。
  - [x] [带电插口格式数据检查](../work/v1.8.0-c16a-hatches/POWER-CARRIER-FORMAT-SOURCE-01.md)：
    三个精确后像经独立源码审核后提交、正常推送并集成至 `257e7b3a`；
    独立开发和固定已发布提交的缓存 javac/Jupiter 各 13/13 通过，后者 49 个输入无漂移。
    只检查稳定私有根的有界格式，不赋予 Item、ticket、放置/掉落或资源使用权限。
    Root 两份结束输出已清理，原统计与另版更正保留；完整回归与实际 FE 守恒未完成。
  - [ ] 服务端 creative 输入的复制/覆盖前拦截与真实客户端、FE 守恒证明。
  - [ ] 03/04 物理 hatch、capability、controller 与首台车床。
- [x] C16a-04：五种流体和气罐开发切片。
- [x] C16a 储罐、泵和容量开发切片。
- [x] C16a-07a：电机和机壳组件定义。
- [x] C16a-R021-DOC01：仅更正 R-021 并提出 ADR 说明；独立事实审核，不是风险接受。
- [ ] C16a-LOAD-BINDING-20：status: in-progress。独立只读资格复核确认两个既有
  Medium 接入前置仍开放。限定 LOAD/可用性/终态绑定的三份文档已返回，
  原字节提交、正常推送至独立草案分支 `932600d9`；未独立审核或接受，未合入主分支。
  U1–U7 与 O1/O2/O3 仍未解决，不接管旧作者文件，不授权 Java 或物理 hatch。
- [ ] C16a-07b：全部电机等级的机器形成验证。
- [ ] C16b：电弧炉、车床、切割机等机器家族；共享依赖先完成。
- [ ] C16c：结晶器、化学反应器、激光蚀刻机、离心机等。
- [ ] C16d：钢的初始来源、组件/controller/hatch 获取、配方图和进阶平衡。
  - [x] C16d-FORWARD-15：status: verified，仅测试侧数学源码/单测。Root 在不同代理限定审核后
    [采纳测试侧纯可达性接口](../work/v1.8.0-c16d-forward-reachability/ADOPTION-01.md)。
    仅两个新增算法/测试文件；[源码任务16](../work/v1.8.0-claude-cli-coordination/DISPATCH-16.md)
    已实际返回，三文件原字节提交、推送至 `00749afa`，经独立源码审核后
    [合入并正常推送](../work/v1.8.0-c16d-forward-reachability/SOURCE-INTEGRATION-19.md)至 `3815db7d`。
    原 Gradle 配置阶段停止和诊断偏差保留；另行事前授权的直接 javac/Jupiter
    实际执行 17 项单测，零失败、错误或跳过。清理被执行前拒绝，61,868 B 类文件仍保留，未重试。
    完整 src/Gradle 输入等同于 Root 被测候选 `e91ddc16`；该批 2,179 项单测通过，
    但有一项 required 气闸失败。独立纯 Java 成功不解决配置、原生或整版 Gate。
    提取器、上下文、循环诊断、
    真实配方覆盖和整份图契约均未完成，不改变 C16d 台账状态。

已确认 controller 统一保存资源、流体 hatch 破坏后保留库存；带电插口在物品中
保留 FE。无法守恒的带电操作拒绝，受支持零电量沿用原版。技术库存审核已完成，
不替代未实现的服务端拦截、保存或 native 验证。

### C17 推进与站点设备 `[~]`

契约有条件接受，真实燃料/氧化剂、装卸器、监控、站点连续旋转与逻辑高度、
力场、卫星舱和相应持久事务仍须实现；灯与单台太阳能仍须原生、重启和客户端验证。
[单台太阳能任务](../work/v1.8.0-c17c-solar-generator/TASK.md) 与原创来源预登记
已独立审核、提交并推送；精确 Space 缓存谓词和两阶段已安装对象校验已选定。
18 个源码/记录、12 项中央绑定及 20 项资源已完成独立实际审核，并由 Root
分别提交和非强制推送；[源码检查点](../work/v1.8.0-c17c-solar-generator/SOURCE-INTEGRATION-01.md)
记录固定已发布提交的定向编译和 36 项 JUnit 通过。独立开发复跑另有 21 项太阳能
和 15 项配置检查通过。历史太阳能地表环境失败保留在 RESULT-09。
仅修正自有测试夹具的天空前提，
不修改生产判定或 40-tick 上限；[夹具源码](../work/v1.8.0-c17c-solar-surface-fixture/SOURCE-INTEGRATION-01.md)
已独立审核、提交并推送至 `e0c601e4`，一次缓存 javac 通过。
该精确源码的[完整回归](../work/v1.8.0-ci/RESULT-10.md)已结束并经 Root 原始字节审核，
保留为历史失败批次；最新 Tau 观察源码批次见下文。
太阳能地表测试不再失败，不证明历史独特原因、整机恢复或真实客户端验收。
原生/重启、视觉、台账及整个 C17c 依赖仍开放。
用户的保存选择仅覆盖太阳能数据，R-021 仍开放。
- [~] [C17-TAU-OBSERVATION-02](../work/v1.8.0-tau-observation/ADOPTION-01.md)：
  限定诊断任务已采纳并发布；[一文件源码](../work/v1.8.0-tau-observation/SOURCE-INTEGRATION-01.md)
  经独立静态审核后已合并、推送至 `c6d60282`。保持原延时、全部断言和另外两项
  地面测试；不是生产修复。新精确源码[完整回归](../work/v1.8.0-ci/RESULT-11.md)
  已经 Root 原始字节审核，仍为 FAILED。PRE/POST 均为 PREPARED/source TRANSIT、
  destination UNASSIGNED 与未就绪实体标记，不能推断唯一原因。
[普通空间站灯](../work/v1.8.0-c17b-station-light/TASK.md) 的限定契约与原创资产
预登记、源码、中央注册和原创资源均已独立审核、提交并推送。8 个 JUnit 实际
通过，重复 Forge DataGen 与已提交资源一致。前序灯批次的两项 required 失败
保留为历史；限定夹具修订已独立审核、提交并推送，最新完整复跑没有灯失败
标题，但整批仍失败；完整 native/
客户端/台账交付仍开放。
[轨道模型资格审核](../work/v1.8.0-c17-contract/ORBITAL-PHASE-ELIGIBILITY-DISPOSITION-01.md)
有一项 Medium：精确 D2-A checked-transition/shared-wire 前置接受未被所列证据证明。
模型提案未采用、未授权源码；[完整前置状态](../work/v1.8.0-c17-contract/SHARED-PREREQUISITES-STATUS-01.md)
已记录开放提案的独立审核。完整 typed 候选独立提案审核无新增 C/H/M/L，
T1–T7 仍是实质性冻结前置项。[推进数值审核](../work/v1.8.0-c17-contract/NUMERIC-REVIEW-DISPOSITION-01.md)
原有一项 Low：已排除的历史算法说明漏写第二次 binary32 舍入；另版成对文字
修订已完成独立审核，仅解决提案文本。22 项原独立有限控制通过。
维护者已在异步回复中选择“采用建议规则：binary64 舍入＋按支持比例节省（推荐）”；
[决定记录](../work/v1.8.0-c17-contract/NUMERIC-OWNER-DECISION-01.md)仅关闭所询问的
数值选择。[C17a-NUM-01 私有计算任务](../work/v1.8.0-c17-contract/NUMERIC-PRIVATE-TASK-01.md)
经不同代理契约审核后，Root 限定采纳未调用计算器的表示、算术域和错误规则；
[源码验证](../work/v1.8.0-c17-contract/NUMERIC-SOURCE-VERIFICATION-01.md)：两文件已在
`9b50488aa987fa47b0683a57ce8fec93f29c0e5c` 提交并非强制推送，独立源码审核无未解决
C/H/M/L；独立已提交 A0 与 Root 固定提交复跑各23/23、零失败/中止/跳过/容器失败。
临时 class/home 已清理，完整集成检查未运行；精确源码已在
[私有源码检查点](../work/v1.8.0-c17-contract/PRIVATE-INTEGRATION-CHECKPOINT-01.md)引入开发分支，完整集成验证与交付仍待完成。
完整适用性、阶段及 schema 的技术接受仍待完成。
完整原生资源 frame 的独立提案审核已完成，无 C/H/M/L；26 项有限控制及 15 项
源码控制通过，Root 校验 50 项审核载荷 /348,435 字节 /51 项校验和。私有 NC1
检查/编码、完整 typed 哈希与运行时 codec 的前置条件分别记录。
`C17a-NC1-01` 的独立私有契约与任务审核完成，限定范围内无未解决 C/H/M/L；
Root 分别校验 55 项审核载荷 /341,877 字节 /56 项校验和及 25 /209,006 字节 /26。
[狭义采纳记录](../work/v1.8.0-c17-contract/NC1-PRIVATE-ADOPTION-01.md)仅接受两个
未调用的计算/测试文件契约，已以 `3fd5df73` 提交并非强制推送。
[独立源码任务](../work/v1.8.0-c17-contract/NC1-SOURCE-ASSIGNMENT-01.md)已分配，隔离
worktree 以该提交为基础，只新增两个 Java 文件。现有修订
`451bddb6450819e1438f516989b68239529ac44a` 已在任务分支提交并非强制推送。
[源码验证记录](../work/v1.8.0-c17-contract/NC1-SOURCE-VERIFICATION-02.md)保留原4264
的 Medium 与六项 NPE 失败；修订在三种公开 null-backed 数组固定拒绝范围内
完成独立审核，无未解决 C/H/M/L。独立已提交 A0 和 Root 固定提交复跑各23/23，
0失败/中止/跳过/容器失败；原样诊断六项观察、零违规。Root 校验68审核载荷
/286,077字节/69校验项。精确更正源码已在同一私有源码检查点引入开发分支；
完整适用集成检查和交付仍未完成。
新外部执行器的原两项 Medium 与失败记录保留；另版 a003 的独立更正审核完成，
Root 读全文并核对原93 /219,545 字节 /94 和更正141 /394,259 字节 /142。
Root 工具资格采纳与后续单类开发/审核执行授权分别记录，不扩大到运行时。
完整 typed 哈希、运行时 codec 和保存契约未采用。
完整 typed O3 的独立审核完成。原 Low 将已确认提交但内存发布失败的处理
误写成维护者问题的原话；[独立审核的版本化文字更正](../work/v1.8.0-c17-contract/O3-ATTRIBUTION-DISPOSITION-01.md)
已仅在替换文本中解决该归属问题，原证据不改动。该处理仍是待采用的技术扩展；
共同契约、技术策略采纳及保存实现仍开放。
C17b candidate03 的独立审核已解决旧版 Medium
零值预算和 Low 字节等价说明问题，仅关闭提案文本问题；O1–O7 和完整 typed
火箭契约仍未冻结，不能以 A0 标签放行。

### C18 生命支持、研究与呈现 `[ ]`

契约有条件接受；辅助氧气储量、有限节省、装备工具、独立地面勘测、首次事件、
科技树、声音/模型/GUI 等仍须实现。D4 未证明，不推断接受。

- [~] C18a-THERMITE-01：status: implemented-unverified。
  普通热剂/火把契约第 4 版经独立审核后限定采纳；Claude 四文件源码、Root 中央接入、
  外部标签适配夹具、九项原生测试及原创生成资源已分别提交和正常推送。
  [源码已合入并正常推送](../work/v1.8.0-c18a-thermite/SOURCE-INTEGRATION-09.md)至 `a6675a4e`，
  与独立被测 `230afbf0` 的源码及 Gradle 输入完全相同。实际 clean build 通过
  2,146 项单测；两次 runData 和差异检查通过；540 项 required GameTest 全部通过。
  最终不同代理审核未发现修订引入的缺陷；编译使用缓存，但测试实际执行。
  62 ERROR / 零 FATAL 尚未放行；历史运行偏差和失败证据保留，不追认授权。
  严格仓库校验因既有缺失或不安全的证据链接失败；台账、bootstrap 来源和空白检查通过。
  自有结束 build/run-data 清理被工具策略拒绝，仍待正常允许的清理，不改称已清理。
  两个已释放的干净源码副本已正常移除，不等于清理被拒绝的构建或世界输出。
  生存菜单补测的前序 `f535a130` 已随修订后继合入；其原两项 Low、作者规约偏差和
  前序运行证据保留在实施日志与原封存包，不作为未合入的现状。
  生命周期修订和 Root 错误分支补充的最终 `4fedf295` 独立审核无新增 C/H/M/Low，
  2,146 项单测、542 项原生测试通过，已[合入并推送](../work/v1.8.0-c18a-thermite/SOURCE-INTEGRATION-13.md)
  至 `ead0ece2`；正常缓存清理已实际执行，异常注入和平台全部引用释放未证明。
  Root 对集成提交 `ead0ece2` 实际完成 clean build、2,146 项单测、两次 DataGen/干净差异；
  编译使用缓存，测试实际执行。原生测试仍绑定独立被测、源码等价的 `4fedf295`；
  S1、重启、真实 V1/V2、美术和来源批准及整版 Gate 仍开放。

- [ ] [C18a-AIRLOCK-01](../work/v1.8.0-c18a-airlock/TASK-01.md)：
  status: implemented-unverified。双半气闸门第二版技术契约经独立复审，无未解决 C/H/M；
  放置接口及 null-to-FAIL 原生顺序已另行核对。Root 按已有有条件授权限定采纳，
  原两项 Medium 和原版证据保留。原创来源与独立工作树写入范围在实现前登记，
  六个新增文件经 Root 提交至 `c263f7d9` 并原样 fast-forward 集成。
  行为、provider 测试、中央接入及资源的独立静态审核均未发现可操作缺陷；
  [集成记录](../work/v1.8.0-c18a-airlock/SOURCE-INTEGRATION-01.md)保留精确关联与限制。
  完整源码与资源已正常推送至 `61ae5001`，精确提交 hosted CI 已启动；自有结束工作树已清理。
  精确提交的十四项新单测及重复原生生成通过；七项原生测试执行但两项失败，
  失败记录保留；两文件夹具修订已经独立审核、提交并正常推送至 `01521d6c`，
  精确提交完整复跑仍失败，不改变断言、期限或预算。有限采样不再列为失败；
  供气仅增诊断，状态为 OPEN、资源仍在，原因未定。
  完整 GameTest、重启或 V1/V2 未交付，不依赖未接受的物理 hatch 接入。
- [ ] [C18a-AIRLOCK-FIXTURE-02](../work/v1.8.0-c18a-airlock/FIXTURE-TIMING-TASK-01.md)：
  status: implemented-unverified。仅做有限的建房与首轮安装/扫描 tick 分离实验；
  六组断言、原期限及预算不变，异步结束、异常及超时清理必须独立审核。
  两文件经独立实际差异审核后，[原字节提交、集成和正常推送](../work/v1.8.0-c18a-airlock/FIXTURE-TIMING-INTEGRATION-01.md)
  至 `22f7d1ca`；独立 126 项源/有限模型和 16 项映射标量检查通过。
  自有结束工作树已正常清理；`22f7d1ca` CI 七项气闸均未列为失败，固定源码在成功前
  执行六组原始供气/撤销/恢复断言和正常清理。[独立原始审核](../work/v1.8.0-ci/RESULT-34.md)已完成且一致；
  无独立 native PASS XML、异常/超时清理或崩服恢复证据，不宣告生产原因或修复。
  后续 `10eb561a` 完整回归再次失败在下半门/phase 1 的首次供气，后续组合未获验证。
- [~] [C18a-AIRLOCK-SUPPLY-DIAG-01](../work/v1.8.0-c18a-airlock/SUPPLY-DIAGNOSTIC-TASK-01.md)：
  status: implemented-unverified。仅在原供气失败分支观察保留扫描结果、有限邻接分类和两半门状态，
  不改变生产服务、原断言、期限、预算或六组顺序。独立实际源码审核完成；
  [四文件原字节提交与正常推送](../work/v1.8.0-c18a-airlock/SUPPLY-DIAGNOSTIC-INTEGRATION-01.md)
  至 `2a59cfac`。自有已结束工作树正常移除，源码与 D 盘薄证据保留；
  精确提交 hosted CI 的构建/单测/重复生成通过，完整 GameTest 失败；
  原供气失败诊断分支已触发，但唯一原因、修复与全部 Gate 仍未证明。
- [~] [气闸当前种子输入补充](../work/v1.8.0-c18a-airlock/SEED-INPUTS-TASK-02.md)：
  status: implemented-unverified。只在旧诊断之后增加有界种子身份、sky/height 原生标量，
  原断言、期限、预算及六组顺序不变。不同代理实际五文件审核与独立静态检查完成后，
  [原字节提交、正常推送和自有工作树移除](../work/v1.8.0-c18a-airlock/SEED-INPUTS-INTEGRATION-02.md)
  已完成；精确源码 b8136f0b 的托管构建/单测/重复生成通过，完整 GameTest 仍失败。
  新当前种子标量分支已触发，不宣告唯一原因、生产修复或 Gate。
- [~] Tau 冷目标调查：生产与夹具独立读证一致，失败停在目标实体就绪前，
  源火箭和 PREPARED 事务仍在。原生静态事实已核对正常 tick 与就绪处理调用关系，
  实际加载 future/inbox 尚未观测，未证明唯一原因或修复；
  原延时、就绪谓词与完整往返测试保留，不据此勾选功能或 Gate。
- [ ] [装载器掉落实测数量诊断](../work/v1.8.0-regression-observation/FUEL-DROP-DIAGNOSTIC-TASK-01.md)：
  status: implemented-unverified。仅给现有失败断言补已查询列表的数量，不新增世界查询、
  过滤实体或更改原守恒断言。独立实际审核无新增问题后，[两文件原字节发布](../work/v1.8.0-regression-observation/FUEL-DROP-DIAGNOSTIC-INTEGRATION-01.md)
  至 `10eb561a`，自有结束工作树已正常清理。[精确源码终态](../work/v1.8.0-ci/RESULT-36.md)
  的构建/单测/两次生成通过，完整 GameTest 失败在 Tau 与气闸；装载器未列为失败，
  未触发数量消息，不能据此声明复现诊断、唯一原因或修复。[独立原始审核](../work/v1.8.0-ci/RESULT-37.md)
  已完成且一致、输入无漂移；仅诊断源码/检查/回归/审核部分已发布。

- [ ] [C18d-SKY-01](../work/v1.8.0-c18d-sky-switches/TASK-01.md)：status: implemented-unverified。
  仅实施 ADR-066 已接受的 planet/station 两项 CLIENT 开关。按既有 effects 注册分类，
  关闭后还原对应 fallback hook、云高度与雾处理，不更改原始选择、环境或环境音。
  独立工作树四项适配/测试文件与 Root 三项配置/注册文件互不重叠；
  五个 worker 文件经 Root 提交、原字节 cherry-pick；三项中央绑定也已经独立审核，
  [完整集成](../work/v1.8.0-c18d-sky-switches/SOURCE-INTEGRATION-01.md)保留源码关联。
  完整组合已正常推送至 `cbbb78e1`，[精确提交 CI](../work/v1.8.0-ci/RESULT-30.md)的
  构建、十项适配/配置单测和重复原生生成通过；完整 GameTest 仍失败。
  Root 与[独立原始审核](../work/v1.8.0-ci/RESULT-31.md)一致且输入无漂移。
  自有已结束工作树已正常清理，
  client 实际分发及 V1/V2 未验证。

- [~] [C18d-HUD-ENV-O2-01](../work/v1.8.0-c18d-hud-layout/TASK-01.md)：
  status: implemented-unverified，源码已审核并提交、正常推送至 `f9f2d9d2`，固定提交完整 CI 失败，原始结果已独立核验。
  只覆盖环境/氧气八项 CLIENT 布局设置；
  原契约三个 Medium 已经独立后续审核处理，投影的测试矩阵 Medium 已另行修订；
  [技术采纳记录](../work/v1.8.0-c18d-hud-layout/ADOPTION-01.md)保留原证据及清洁性文字更正。
  [事后交接登记](../work/v1.8.0-c18d-hud-layout/ASSIGNMENT-01.md)绑定 Claude 外部三个新文件；
  [源码检查点](../work/v1.8.0-c18d-hud-layout/SOURCE-INTEGRATION-03.md)记录原 patch 按原字节应用。
  用户转交交互会话报告；未事前登记/隔离工作树和禁止的作者 JVM 检查仍记录，不追认授权。
  [后继技术补记](../work/v1.8.0-c18d-hud-layout/ADOPTION-02.md)关联独立源码审核并明确接受两个边界常量和内部候选入口。
  Root 已补独立候选生成器和现有 HUD/config 绑定；合并后六个源码/测试后像经不同代理审核，
  Root 未提交时的 28 项定向 JUnit 仅作中间检查；不同代理在干净固定提交另跑 28/28 通过。
  固定提交完整 CI 已执行并失败；真实文件重载、Font/pose 客户端、V1/V2 和 Gate 均开放。

- [~] [C18a 生物重力与摔落](../work/v1.8.0-c18a-living-gravity/TASK-01.md)：
  A 的[完整源码](../work/v1.8.0-c18a-living-gravity/SOURCE-INTEGRATION-01.md)已独立审核、
  提交并正常推送至 `1b6071d1`；测试观察 Low 修复后独立复审通过。
  新增七项 controller、三项 config 单测及九项 GameTest。
  原批次的 controller/config 单测通过，但配置总数检查失败，DataGen/GameTest 未执行。
  总数夹具的严格修订经独立审核后已提交、推送至 `8b3fdca9`，新单测与重复 DataGen 通过。
  九项新增原生测试已执行，没有列入终态失败；完整 GameTest 仍因既有 Tau 往返失败。
  完整回归、实际维度转移、自然 tick、打包/客户端和重启资格仍开放；自有已结束 worktree 已清理。
  原生事实已证明载具向乘客传入修改后的参数，但一次缩放的补救与 Medium 未解决，
  B 未分配摔落监听器；两项台账与整版 Gate 不变。

- [~] C18 D1 辅助氧气储量：保留已接受的 API/HUD 2,000 工作缓冲。
  [原独立提案审核](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-oxygen-reserve-proposal-review-20261007/REVIEW-01.md)
  的两项 Medium 保留为历史；[后继独立复审](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-oxygen-reserve-successor-independent-review-20261007/REVIEW-01.md)
  已在提案文本中补全元数据/空罐保真和拒绝充气路由规则，无 C/H/M，但有一项 Low：
  回调变化的拒绝承诺大于列明的可观察输入。另立的[可观察见证措辞修订](../work/v1.8.0-c18-contract/OXYGEN-WITNESS-CLARIFICATION-01.md)
  已完成独立复审，无新增 C/H/M/L，Root 仅采纳技术措辞；原 Low 和证据保留为历史。
  四档容量/配置和配对充气仍待已有维护者选择，未授权装备运行时。
  - [x] C18b-RESERVE-15：status: verified，仅未调用的纯计算源码。不同代理审核无 C/H/M/Low 后，Root
    [限定采纳纯计算接口](../work/v1.8.0-c18b-reserve-transition/ADOPTION-01.md)。
    只允许新增未调用的参数化储量转换及测试，不修改 engine、API/HUD 或保存。
    [独立 Claude 源码任务16](../work/v1.8.0-claude-cli-coordination/DISPATCH-16.md) 已返回；
    原字节提交、推送至 `707c389b`，另版仅注释/证据措辞修正至 `78347054`。
    [实际源码审核和定向21 JUnit](../work/v1.8.0-c18b-reserve-transition/SOURCE-INTEGRATION-17.md)
    无 C/H/M/Low、零 F/E/S，精确后像合入并正常推送至 `eb90c643`。
    验证只限 A0 计算；装备原生适配、保存和交付仍未完成，不改变内容台账。
  新工作台的拒存与 R-021 扩展也未接受，不把文本模型检查记作原生交互证明。
- [~] [C18a-SEAL-01](../work/v1.8.0-c18a-seal-detector/ADOPTION-01.md)：
  ADR-067、限定任务与 NEW 来源已独立审核并发布于 `513f2ffb`。
  独立固定 worktree 的十三个新增源码/测试文件及 Root 的六个中央绑定文件和新增资源测试
  已完成独立实际源码静态审核，该范围未发现新增 C/H/M/L。
  [源码与资源](../work/v1.8.0-c18a-seal-detector/SOURCE-INTEGRATION-01.md)已分别提交并正常推送
  至 `9cd5890b`；限定五源码编译和普通 Java 生成成功，六项资源经独立像素/JSON 审核。
  限定本地生成不等于完整物品适配验证；原 tooltip 单测夹具失败保留为历史。
  [限定测试修正](../work/v1.8.0-c18a-seal-detector/SOURCE-UNIT-FIX-01.md)
  经独立实际差异审核后已提交并正常推送至 `33a3156e`；原断言及产品不变。
  新精确回归已有原始结果审核，修正单测通过；完整物品的额外运行时覆盖、
  原生/重启、真实客户端和台账交付仍未完成。
  自建 D 生成目录清理在启动前被策略拒绝，目录保留，未重试或换机制。
  已安装 runtime 的自定义边界规则验证明确未运行，不以局部 catalog 测试替代。
  完整自动检查、打包原生、真实客户端和台账交付仍开放。

- [ ] 首次事件：source/receipt/ACK 和完整 typed 着陆字段尚未冻结。
  维护者于 2026-10-05 在异步答复中选择“保护首次记录，暂停不确定窗口的旧数据重写（推荐）”。
  仅首次事件相关的不确定窗口暂停旧数据重写，校验重载或修复后恢复；其他动作必须证明一致。
  限定 ADR 修订、风险登记、独立审核和实际恢复验证仍未完成，不据此开放运行时或接受 R-021。
- [x] Task04 其余私有报告字段：status: verified，仅私有单行字段实现。
  独立契约审核无未解决 C/H/M/L；[私有任务](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-TASK-04-REPORT-STAGES.md)
  与[限定处置](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-04-REPORT-STAGES.md)
  冻结字段与范围，已在 `6bddc0326247ba79df845bcd8c0c606f447854d2` 提交并推送。
  四个新文件已独立实际源码审核，无新增 C/H/M/L，200 测试及 14 项附加控制通过。
  Root 原样提交、推送 `5582e3c49d548c5c002ef6c7cd45b1296f2f2703`，
  [固定提交复跑](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-PYTHON-VERIFICATION-05-REPORT-STAGES.md)
  35 +40 +52 +39 +34 测试 /0FES，24 个指定输入未改变；新 D 测试夹具已清理。
  独立审核的原清理失败仍保留；另行核验的更正已移除其两个新临时目录。
  作者四份 55,219 字节副本和工作树仍保留：原清理预检定位错误，另版 native 脚本被执行策略拒绝，
  两次均未删除文件；不改执行策略、换 shell 重删或强制退役工作树。
  这不授予文件读取、cohort、ownership、receipt、停服或原生权限，不完成 C18c。

- [ ] 水下生命支持：互斥 LivingTick/Breathe/END 方案已独立技术审核；
  全部已连接存活玩家的同 tick 延后采样待维护者确认。未连接 ServerPlayer
  保留旧立即模式的说明修订尚待定稿；未授权或开始源码实现。

- [~] [C18c-01](../work/v1.8.0-c18c-tutorial-inventory/TASK.md)：六项库存教程成就，
  精确审核源码已提交、推送；前序限定自动回归与实际库存监听已有记录，
  打包原生重启和台账交付仍开放。前序 Source27 通过保留为历史；最新回归失败见下文。
  旧离线文件所有权及构造/join 资格问题保留历史证据。
  [第三版独立复审](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-03.md)
  有两项 Medium：原生启动重写配置哈希、重启重复强制加载命令拒绝。
  两项冲突的[第四版限定处置](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-04.md)
  经独立审核无 C/H/M/L，契约已在 `d04a116e` 提交并推送；旧第三版仍未接受。
  [phase01 精确源码](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-PHASE01.md)
  已独立审核无 C/H/M/L，四文件原样提交推送 `d0f9cbde`；Root
  [已提交 Python 回归](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-PYTHON-VERIFICATION-01.md)
  旧 phase01 检查通过并获限定独立结果复核，CLI2 仍明确拒绝完整驱动。工具/原失败保留，119 成员薄包已核验。
  其余文件角色/live 读取、JSON/NBT/transport/Java、适用干净主机和原生执行仍待验证。
  [phase02a 只读文件契约](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-REVIEW-DISPOSITION-04-PHASE02A.md)
  已按独立0 C/H/M/L限定接受，61成员证据核验；仅四新private路径、静止副本有界properties读取。
  契约已提交并推送 `b6299c86`，四文件隔离实现经独立实际源码审核无 C/H/M/L，
  Root 原样提交推送 `254af9e4` 并完成固定提交 34 + 39 方法 /0FES；
  [限定采用](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-PHASE02A.md)
  仅为静止副本的私有 properties 读取，不赋予 live、ownership、JSON/NBT、receipt 或 native 权限。
  Root 新夹具已清理 99,375 字节；首次清理预检失败和修订记录分别保留。
  [纯 JSON 任务](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-TASK-04-JSON.md)
  已独立审核无未解决 C/H/M/L 并冻结，契约提交推送为 `e091f1b0`。
  四文件源码经独立审核无新增 C/H/M/L，125 测试及 14 项独立控制通过；
  Root 原样提交推送 `7affd485`，固定提交复跑 52 + 39 + 34 测试 /0FES。
  [限定源码采用](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-JSON.md)
  与[实际结果](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-PYTHON-VERIFICATION-03-JSON.md)
  仅证明私有纯字节解析；不授予文件/live/native 权限。Root 新夹具已清理
  99,375 字节和六个别名；其他字段、JSON 读取、NBT 和完整驱动仍未实现。
  - [x] [终态报告字段](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-SOURCE-REVIEW-DISPOSITION-04-REPORT-TERMINALS.md)：
    私有 READY_FOR_STOP/FAILED 四新文件已独立审核无新增 C/H/M/L；
    Root 原样提交推送 `3c5f20dc`，固定提交复跑 40 + 52 + 39 + 34 测试 /0FES。
    [实际结果](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-PYTHON-VERIFICATION-04-REPORT-TERMINALS.md)
    仅验证私有字段叶；Root 新 D 夹具已清理 99,375 字节和六个别名。
    只观察字段形状，不授予完成、停服、receipt 或 native 权限。
    [临时副本清理](../work/v1.8.0-c18c-tutorial-inventory/NATIVE-TEMP-CLEANUP-04-REPORT-TERMINALS.md)
    已删除作者四份源副本并正常非强制移除干净工作树；首次统计预检失败保留，旧拒删欠项不变。
- [~] [C18a-01](../work/v1.8.0-c18a-atmosphere-analyzer/TASK.md)：单项手持大气分析仪的
  只读契约经独立审核无 C/H/M/L，并按既有授权限定接受；原创资源已登记，
  精确源码及八项中央集成已独立审核，真实独立 19 单测通过。Root 已集成，
  中间检查后已提交并推送；原完整 GameTest 的四项分析仪失败保留在历史证据中。
  [单 GT 精确修复](../work/v1.8.0-c18a-atmosphere-analyzer/FIX-REVIEW-DISPOSITION-01.md)
  已独立复核无新增 C/H/M/L；真实独立 477 GT 和另行禁用缓存的 19 JUnit 通过。
  Root 已提交并推送三个原样后像，新 Source27 完整回归通过；原失败、断言和预算不改写。
  打包原生使用/重启、真实客户端与台账交付仍开放，其他生命支持持久事务不在此项范围。

### C19 矩阵、审核与交付 `[ ]`

最新[组合候选回归17](../work/v1.8.0-claude-cli-coordination/REGRESSION-17.md)
在 `e91ddc16` 实际通过 clean build、2,179 JUnit、两次 DataGen 和空 diff，
GameTest 完成542项但有一项 required 气闸失败。原始失败/63 ERROR 不改写；
[新的 Claude 分析任务18](../work/v1.8.0-claude-cli-coordination/DISPATCH-18.md) 已返回、
两份原字节文档提交推送至 `c7b99a2d`，独立审核已完成，零 C/H/M、两项 Low。
后继三份观察规范提交推送至 `5bbad2b5`，独立审核也为零 C/H/M、两项 Low；
两版均不是原因确认、修复或已接受的实施契约，不把报告当作原生执行。
Root 清理本任务新输出272,079,187字节，独立储量审核另清理62,445,360字节；
不计重复副本，不重试历史拒删项，也未改用户 AGENTS。

台账剩余 **186 PLANNED /154 REVIEW**。内容可达性、来源资产复核、代表科技流程、
整包专服/客户端/性能及 G0–G9 均未结束。

### 后续执行与已取回的 Claude 输入 `[ ]`

根据 2026-10-08 用户最新指示，后续由 Root 直接继续 v1.8，实现、验证、提交后再做另一切片。
不再按先前约三分之二的 Claude 分配计划自动派活；必要时才安排独立审核。
[旧分工建议与逐项清单](../work/v1.8.0-claude-parallel-handoff.md)保留为历史输入，
其分配数不代表当前领取或完成比例；原分配数字已移入实施日志。
已有源码的续验、待复核资产和 C19/Gate 工作没有被删除或宣告完成。
Root 继续负责共享保存/hatch、typed 推进、站点核心、生命支持权威、真实旅行首次事件与中央集成/提交。
HUD Root 绑定仍处于 implemented-unverified 源码检查点。以下是已登记任务的返回记录，
不是新的并发领取或运行指令。
独立工作树、逐文件范围和公共契约须先冻结；仅既有库存/天空/来源只读续验可直接准备，
已事前登记三个独立工作树和各三个新增文档的契约任务：
[配方图 10 项](../work/v1.8.0-c16d-components-graph/TASK-01.md)、
[音效 10 项](../work/v1.8.0-c18d-audio/TASK-01.md)、
[装备模块 20 项](../work/v1.8.0-c18b-equipment/TASK-01.md)。
只允许契约/测试设计/交接文档。九份原稿按原字节归档；第 1 版独立审核已归档，
具体历史发现见 Root 审核关联；第 2 版的当前发现及状态见下文。
[Root 审核关联与 TASK-02](../work/v1.8.0-claude-return-review/REVIEW-01.md) 已事前登记三组后继文档范围；
作者工作树与原封存包不改，音效历史封存缺口保留。装备生产实现仍 DEPENDENCY_BLOCKED。
20 份旧配方正文已独立核验；用户已选择显式验证下界入口及标签互操作/数据包额外配方。
这只解决输入和两项选择，不冻结整份契约、接受新 ADR 或交付内容。
此前 Root 按用户授权直接派发有范围、时间和成本估算上限的小 Claude 会话；
不授予广泛生产写入、不建未使用框架，不复用原大会话。
[CLI 实际回执](../work/v1.8.0-claude-cli-coordination/REPORT-01.md)记录新会话和短追问成功、
第 2 版九份原字节归档及独立发现：图 1 High/3 Medium/1 Low、装备 2 Medium/1 Low、
音效 6 Medium/4 Low，均仍 PROPOSED/CHANGES_REQUESTED。天空两项只读续验已取回；
实际 bootstrap 绑定测试作者返回两份新增文件，候选 493df490 经独立审核无新增发现，
已于 d2b4324f 合入并正常推送；[源码/证据关联](../work/v1.8.0-c18d-sky-switches/BINDING-INTEGRATION-01.md)
证明合并后 src/构建输入等同于被测候选，不声称重新执行了合并提交。
另行授权的定向执行在该 SHA 通过 20 个实际 JUnit 用例（新增 5/适配器 7/配置 8）；
真实客户端、打包和完整回归仍未资格确认，不作内容交付。
较小的 [图访问](../work/v1.8.0-c16d-components-graph/TASK-03.md)、
[音效生命周期](../work/v1.8.0-c18d-audio/TASK-03.md)、
[装备完整输出边界](../work/v1.8.0-c18b-equipment/TASK-03.md)草案任务已直接并发派发，
[实际会话登记](../work/v1.8.0-claude-cli-coordination/DISPATCH-03-01.md)关联三个新会话；
三组均已返回、写入范围/终态检查通过，但作者自述未读完必读治理文件；
配方图交接的交互会话身份也与实际新 CLI 回执不符。偏差保持 OPEN，
不因 I/O 检查通过而豁免规约。[第三版限定审核](../work/v1.8.0-claude-cli-coordination/REVIEW-03-01.md)
已完成：图 3 Medium/1 Low、音效 4 Medium/1 Low、装备 3 Medium/1 Low，均 CHANGES_REQUESTED；
先前整份契约的问题仍开放。九份原稿已在三个分支提交并推送，但没有合入或接受。
三个更小的双文档 TASK-04 已实际派发、返回，原字节分别提交推送；
[直接派发记录](../work/v1.8.0-claude-cli-coordination/DISPATCH-04-01.md)保留会话、范围及 EOF 计数更正。
[第四版限定审核](../work/v1.8.0-claude-cli-coordination/REVIEW-04-01.md)与源码采纳分开，
不作内容交付。铝热后继草稿 a8133ec1 已单独提交推送并独立审核：0 C/H/M、1 Low；
原 2 Medium/1 Low 仅在替换提案文字中处理，原稿/遗漏不追改。
T12 输入范围说明、原生放置、模型/掉落和真空夹具仍须核验；不授权源码实施或共享保存。
Root 选择新鲜着陆位置重力查询与受控供气空间输入，精确端口/协议仍须审核。
其他包保持规划状态；共享保存/hatch、装备持久化、资产和全部 Gate 的依赖不解除。
环境/氧气八项的外部 helper 返回、实际作者基线/位置、执行偏差与待审核状态见 HUD ASSIGNMENT-01；
原 CSV 的 readiness 不自动升级。上述任务以已发布 TASK 为执行边界，禁止 JVM/缓存/中央文件写入；
其余任务和后续源码实施仍须分别登记，不以三组文档授权代替实现契约。

## 当前自动回归与风险

最新源码 `f9f2d9d2c5eb0de2c9f5d28160ab7804fc57eaef` 已正常推送，
CI run 37653204825 /attempt 1 在 16:47:12 UTC 观察到终态失败；
该时间为监测记录的观察时间，不是 job 的完成时间。
[Root 原始推导](../work/v1.8.0-ci/RESULT-44.md)与
[不同代理审核](../work/v1.8.0-ci/RESULT-45.md)一致、输入无漂移。
374 XML /2,129 实际 JUnit 全通过，构建、两次 DataGen 和干净检查通过；
525 GameTest 完成、524 通过、1 项气闸 lower/phase-1 阶段供气前置失败。
canonical 63 ERROR/零 FATAL 的资格处置仍开放；不把缺席的旧失败认定为永久修复。
自有已结束 HUD build 副本的清理命令被工具策略在执行前拒绝，清理仍待允许的正常处理；
未创建 C 临时脚本，不删除其他代理的旧目录。
独立定向审核者同样遭遇执行前清理拒绝；其自有 62,083,720 B 输出仍保留，未绕过策略。
上一次完成回归的源码 `b8136f0b8a41e64d51798f18e03127ebdd35693f` 的
[失败终态与 Root 原始推导](../work/v1.8.0-ci/RESULT-42.md)绑定 run 37628727531 /attempt 1
/job 112817138488；13:37:34 UTC 完成失败。2,104 JUnit、构建/审计、重复原生生成与
tracked/untracked 检查通过；525 GameTest 完成，Tau、初始气闸供气和目的地实体就绪失败。
canonical 65 ERROR/零 FATAL 未豁免；[新不同代理原始审核](../work/v1.8.0-ci/RESULT-43.md)
已完成且与 Root 一致，输入零漂移。诊断分支记录空气种子
在 y180、sky=true、高度182、y比较=false；只提供当前取证，不证明唯一原因、修复、
原生 getter 无副作用或 Gate。装载器未在新失败清单中，旧失败不因此关闭。
[较早运行时间点](../work/v1.8.0-ci/RESULT-41.md)保持原样，不借用前序结果。
前一源码 `2a59cfac2e5c6713a5e6039149d0b4f2830ff8d7` 的
[失败终态与 Root 原始推导](../work/v1.8.0-ci/RESULT-39.md)绑定 run 37618804027 /attempt 1
/job 112783570613。372 XML/2,104 JUnit 零失败错误跳过；构建/审计、两次原生生成与
tracked/untracked 干净检查通过。525 GameTest 完成，Tau、气闸首次供气与装载器掉落/放置
三项 required 失败；canonical 65 ERROR/零 FATAL 未豁免。气闸保留结果与种子格为 OPEN、
六个邻格为 SEALED，两半门均关闭；装载器失败数量为 0。这些只是失败时观测，
不证明唯一原因或修复。[不同代理原始审核](../work/v1.8.0-ci/RESULT-40.md)已完成且一致、
输入无漂移，完整回归仍失败；[运行中观察](../work/v1.8.0-ci/RESULT-38.md)
保留为同一源码的较早时间点。打包恢复、真实客户端和全部 G0-G9 均开放。
上一完成源码 `10eb561a` 的[失败终态](../work/v1.8.0-ci/RESULT-36.md)与
[独立原始审核](../work/v1.8.0-ci/RESULT-37.md)保留各自计数和来源，失败在 Tau 与气闸。
该批次装载器数量消息未触发；气闸第二组前置供气失败，后续撤销/恢复与四组未获验证。
更早 `22f7d1ca` 的[失败终态](../work/v1.8.0-ci/RESULT-33.md)与
[不同代理审核](../work/v1.8.0-ci/RESULT-34.md)保持历史关联，不能证明异常清理或永久修复。
前序源码的[独立审核](../work/v1.8.0-ci/RESULT-31.md)保留原来源与计数。
前序源码的[两项失败终态](../work/v1.8.0-ci/RESULT-28.md)、
[四项失败回归](../work/v1.8.0-ci/RESULT-26.md)和
[运行中观察](../work/v1.8.0-ci/RESULT-25.md)保留为历史，原计数与来源不重绑；
目标就绪、装载器掉落/放置及未豁免日志仍开放。
原供气失败的唯一原因未证明，不据新源码的一次聚合通过重写旧结果。

[失败时观察源码](../work/v1.8.0-regression-observation/SOURCE-INTEGRATION-01.md)
已经独立实际审核、分别提交并合并，非强制推送至
`35a146fbbe1f2de94160f82307d041d2cd26e472`；单次有界缓存编译通过。
[前序成功配方行自动证据](../work/v1.8.0-ci/RESULT-15.md)保留其原始计数、
结果及独立历史 JAR 字节审核，不把旧指标重绑到新源码；错误未获豁免。
前序 Planetary/Tau 失败保留在 RESULT-14；单次通过不关闭偶发就绪性风险。原 tooltip 失败在
[RESULT-12](../work/v1.8.0-ci/RESULT-12.md)，不改写为通过；其测试专用修正已实际通过。
原 40/270 tick 上限未改变，Tau 的两次未就绪观察不说明唯一原因。
新的[服务失败诊断](../work/v1.8.0-tau-ceti-readiness/SERVICE-DIAGNOSTICS-SOURCE-01.md)
经独立实际审核后已提交并正常推送至 `ca217afa`；19 项独立开发及固定提交
pure-helper 单测分别通过，
断言、时限和保存行为不变；其单独历史失败保留在 RESULT-16。
前序[等待历史诊断源码](../work/v1.8.0-transfer-wait-history/SOURCE-INTEGRATION-01.md)
经独立源码审核后正常提交、推送并集成至 `046fc477`；只替换一处观察调用，
保留全部十九项旧测试并新增十一项。独立开发与固定提交 javac/Jupiter 各 30/30 通过，
后者十七个输入无漂移；自有结束输出已清理。该诊断不是飞行或原生就绪修复。
该提交的[完整失败结果](../work/v1.8.0-ci/RESULT-20.md)保留原始计数和 required Tau
往返失败，不把旧批次数字重绑到新源码。
新的[缺失时 holder 观察](../work/v1.8.0-tau-holder-observation/SOURCE-INTEGRATION-01.md)
经独立源码审核、固定提交文本单测 9/9 及独立原始结果核验后，合入并正常推送至
`7ab1b087`。十八个输入无漂移；旧断言、时限、ticket 和 2048/512 输出不变。
单次新原生查询只在缺失分支，标签为 POST_LOOKUP；不是飞行或原生就绪修复。
自有七个 class 和九个目录已清理，原统计和另版更正保留；定向测试不执行 native fixture。
该前序源码的[终态结果](../work/v1.8.0-ci/RESULT-21.md)保留为历史，不重绑其指标。
前序空 hatch 权限源码 `7d7b474e` 的[终态回归](../work/v1.8.0-ci/RESULT-22.md)
保留其独立审核的通过指标、产物身份和未豁免错误；不重绑到生物重力源码。
一次成功不证明就绪修复、缺失分支执行或物理机器可用，原失败仍保留。
生物重力 `1b6071d1` 的[原始终态回归](../work/v1.8.0-ci/RESULT-23.md)保留其配置总数失败。
后继严格夹具修订 `8b3fdca9` 的[终态结果](../work/v1.8.0-ci/RESULT-24.md)绑定
run 37580341390 /attempt 1 /job 112658310596，2026-10-07T06:23:59Z 捕获 completed failure。
clean build、369 XML /2,083 单测 /零失败错误跳过、产物检查、两次 DataGen 与干净检查通过；
518 GameTest 完成，一项既有 Tau 往返失败，新增九项 living-gravity 批次未列入终态失败。
canonical 63 ERROR /零 FATAL 未豁免。Root 与独立后继原始审计一致，输入无漂移；
原失败不改成通过，完整回归、实际维度转移/自然 tick、打包重启和全部 Gate 尚未证明。
[前序格式源码结果](../work/v1.8.0-ci/RESULT-19.md)保留两项 required 失败；
目的地夹具在新批次不再出现失败标题，不据此认定修复。
[元数据源码结果](../work/v1.8.0-ci/RESULT-18.md)仍是其自己提交的历史事实，
不复用前序数字，不把诊断认定为生产修复。
资源源码 `8d521406` 的[前序失败结果](../work/v1.8.0-ci/RESULT-17.md)保留为历史。
先前观察源码的 runner 分配失败及其已执行失败重跑分别保留在
[原记录](../work/v1.8.0-ci/RESULT-08.md)和历史 RESULT-09 中；不再记为运行中。
原断言、时限和清理保留，诊断不是生产修复。两个干净工作树和审核者的
25 个 class 输出已按各自限定记录清理；原失败与旧拒删欠项保留。
原始结果独立审核验证本批次的字节、计数和失败事实，不是太阳能源代码自审。
旧诊断换行修正不改变原始字节，旧失败结果保留。
历史观察批次没有激光、目标就绪或重力失败标题，不等于完整功能或原生重启交付。
重力历史调查仍开放；激光夹具的 25/300 tick 和后续行为断言保持不变。
[前序批次](../work/v1.8.0-ci/RESULT-03.md) 的编译、灯夹具失败保留；
断言、预算和测试选择不放宽。
[更早重复生成批次](../work/v1.8.0-ci/RESULT-02.md)及独立原始结果审核保留为历史。
失败批次的 JAR 上传跳过；前序配方行 JAR 字节审核不替代 API 兼容或原生运行证明。
台账 closure 与本地用户 dirty diff 仍未通过。
前序 Source27 的成功、重复 DataGen 和独立审计，以及 Source26 四项失败保留为
各自提交上的历史证据，不把它们重绑到新失败批次。自动检查不替代原生、客户端或 Gate。
四次历史复制世界专服仍失败：原始复票后实时恢复失败，后续均缺少真实卸载事件。
新的 SETUP05 仅移除测试副本近邻标记，保留目标与远端；真实卸载/恢复、停服及
第二主机重启拒存、原始字节保留实际通过且独立复核。它不建立唯一生产原因、
完整 S1、首存 writer 或任意崩溃证明。原 native04 状态/记录仍属失败后的观察。
停止后的原生记录保留仅属限定观察，原始失败全部保留；已识别副本资格缺口，不推断唯一生产原因。
真实客户端、崩溃恢复和新的 guarded writer 资格仍开放。
超限 NBT 整区块保存拒绝可能影响无关改动，诊断、修复和发布处置仍开放。
原保存审核的两项 Medium 缺失事实已在 R-021 和经审核的说明提案中处理：
第 257 个不同区块拒存会扩展到整个 ServerLevel；ERROR 成对出现且无保护层日志
总量上限。已接受 ADR 不变，提案仍为 PROPOSED；卸载、跨存储和修复证明缺失，
R-021 未接受或关闭，不能据此开放新 guarded 保存功能。
用户于 2026-10-04 仅授权同步 R-021 和提出 ADR 说明修订；不改变保存行为、
不接受风险、不改其他条目。该事实文档修订和独立审核已完成，
见 [限定处置](../work/v1.8.0-c16a-save-guard/DISCLOSURE-REVIEW-DISPOSITION-01.md)，不属于 K3 计算交付。
历史超限证据包与外部存储整理尚未完成，不把代码推送当作这部分通过。
用户要求后续临时文件放项目父目录，已改用 `D:/GitHub/ARCE-Task-Evidence/v1.8.0`；
测试子进程的 TEMP/TMP/TMPDIR 和 Java 临时目录也只使用此处的自建子目录。
C 盘已识别脚本和新专服副本的检查后删除命令均被工具策略拒绝，尚未删除；
新副本清理欠项为 209,666,732 字节，原始清理失败有单独记录。其他代理、旧日志和封存证据不改。
新的 native05 副本也因策略拒绝未删除，额外 211,865,779 字节；不把原生诊断通过称为清理通过。
[新的只读清单](../work/v1.8.0-c16a-save-guard/TEMP-CLEANUP-STATUS-02.md)确认原 11 份
C 脚本仍为普通文件，共 24,930 字节且 SHA 不变；不把清单核对称为已清理。

## 后续版本：只保留既有计划

- [ ] C20（v1.9）：全系统、迁移链、模组包和复制/恢复回归。
- [H] C21（v1.9）：长时 soak、真实多人和多 GPU/OS。
- [ ] C22（v1.9）：教程、已知问题和支持政策。
- [ ] C23（v2.0）：完整对等矩阵、可重现发布和人工批准。

不得提前实现后续版本。当前继续 C16a 的共享保存与物理依赖；完成依赖后再并发
C16b/c 机器。实际状态保持 IN_PROGRESS /IMPLEMENTING，不创建标签或批准发布。
