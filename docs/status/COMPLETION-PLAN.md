# 至完成的分会话计划

更新：2026-10-05。活动开发版本为 v1.8.0，并行推进 C16a 基础设施、C18c 教程和 C18a 分析仪；
验收游标仍在 v1.0.0。分支为 `codex/v1.8.0-classic-content`。
已验证的开发进度按功能提交和非强制推送，不把提交当作 Gate 批准。
本计划只记录现状；历史运行与失败保留在各版本实施日志及证据中。

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
  - [ ] 完整 hash/frame/native codec 和 GuardTicket 消费端。
  - [ ] 02 生命周期、首存观察、最终卸载和保存 writer。
  - [ ] 服务端 creative 输入的复制/覆盖前拦截与真实客户端、FE 守恒证明。
  - [ ] 03/04 物理 hatch、capability、controller 与首台车床。
- [x] C16a-04：五种流体和气罐开发切片。
- [x] C16a 储罐、泵和容量开发切片。
- [x] C16a-07a：电机和机壳组件定义。
- [x] C16a-R021-DOC01：仅更正 R-021 并提出 ADR 说明；独立事实审核，不是风险接受。
- [ ] C16a-07b：全部电机等级的机器形成验证。
- [ ] C16b：电弧炉、车床、切割机等机器家族；共享依赖先完成。
- [ ] C16c：结晶器、化学反应器、激光蚀刻机、离心机等。
- [ ] C16d：钢的初始来源、组件/controller/hatch 获取、配方图和进阶平衡。

已确认 controller 统一保存资源、流体 hatch 破坏后保留库存；带电插口在物品中
保留 FE。无法守恒的带电操作拒绝，受支持零电量沿用原版。技术库存审核已完成，
不替代未实现的服务端拦截、保存或 native 验证。

### C17 推进与站点设备 `[ ]`

契约有条件接受，真实燃料/氧化剂、装卸器、监控、站点连续旋转与逻辑高度、
灯与力场、卫星舱、太阳能和相应持久事务仍须实现、逐项验证。
[轨道模型资格审核](../work/v1.8.0-c17-contract/ORBITAL-PHASE-ELIGIBILITY-DISPOSITION-01.md)
有一项 Medium：精确 D2-A checked-transition/shared-wire 前置接受未被所列证据证明。
模型提案未采用、未授权源码；[完整前置状态](../work/v1.8.0-c17-contract/SHARED-PREREQUISITES-STATUS-01.md)
已记录开放提案的独立审核。完整 typed 候选独立提案审核无新增 C/H/M/L，
T1–T7 仍是实质性冻结前置项。[推进数值审核](../work/v1.8.0-c17-contract/NUMERIC-REVIEW-DISPOSITION-01.md)
有一项 Low：已排除的历史算法说明漏写第二次 binary32 舍入，另版修订已分配。
22 项独立有限控制通过；影响资源消耗的数值选择已询问维护者，未推断答复。
完整原生资源 frame 的独立提案审核已完成，无 C/H/M/L；26 项有限控制及 15 项
源码控制通过，Root 校验 50 项审核载荷 /348,435 字节 /51 项校验和。私有 NC1
检查/编码、完整 typed 哈希与运行时 codec 的前置条件分别记录。
`C17a-NC1-01` 的独立私有契约与任务审核完成，限定范围内无未解决 C/H/M/L；
Root 分别校验 55 项审核载荷 /341,877 字节 /56 项校验和及 25 /209,006 字节 /26。
[狭义采纳记录](../work/v1.8.0-c17-contract/NC1-PRIVATE-ADOPTION-01.md)仅接受两个
未调用的计算/测试文件契约；源码分配、实际 Java、源码审核及提交复跑仍未开始。
完整 typed 哈希、运行时 codec 和保存契约未采用。
完整 typed O3 的独立审核完成，
另有一项 Low：已确认提交但内存发布失败的处理被误写成维护者问题的原话，
实际只是待采用的技术扩展；文字修订、共同契约及保存实现仍开放。
C17b candidate03 的独立审核已解决旧版 Medium
零值预算和 Low 字节等价说明问题，仅关闭提案文本问题；O1–O7 和完整 typed
火箭契约仍未冻结，不能以 A0 标签放行。

### C18 生命支持、研究与呈现 `[ ]`

契约有条件接受；辅助氧气储量、有限节省、装备工具、独立地面勘测、首次事件、
科技树、声音/模型/GUI 等仍须实现。D4 未证明，不推断接受。

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
  打包原生重启和台账交付仍开放。最新整包回归通过，前次分析仪失败仍保留历史证据。
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

台账剩余 **186 PLANNED /154 REVIEW**。内容可达性、来源资产复核、代表科技流程、
整包专服/客户端/性能及 G0–G9 均未结束。

## 当前自动回归与风险

[最新自动证据](../work/v1.8.0-c18a-atmosphere-analyzer/VERIFICATION-02.md)：
Source27 是已提交、推送 `a0873a30` 的 3,039 个列明输入检查批次，非全仓快照。
禁用构建缓存后真实执行 1,807 JUnit /339 suites 和全部 477 必需 GameTest，均通过。
DataGen 781 文件、重复零改动，八项限定静态校验和制品差异检查通过，API 字节不变。
61 条 ERROR 不作整体豁免，台账 closure 和原始 dirty diff 仍失败。
新批次[独立结果审计](../work/v1.8.0-c18a-atmosphere-analyzer/RESULT-REVIEW-02.md)
未发现额外回执/源码/制品不一致；未重跑 Java 或接受功能。493 成员薄包已逐项核验，
自动通过不替代原生/客户端或版本 Gate。
前次 Source26 四项失败及其独立结果审计保留；不把旧失败改写为通过。
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
