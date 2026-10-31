# Owner decisions of 2026-10-31

Channel: the owner's Claude Code Root session, 2026-10-31, through four AskUserQuestion prompts that Root wrote from
W223's brief (`D:/GitHub/ARCE-Task-Evidence/root-queue/W223/OWNER-DECISIONS.md`, read-only, at `bf2928248`). Before
the prompts the owner wrote: "在当前会话继续吧，完成推进v1.8". Each decision below quotes the option label the owner
selected, verbatim, with the question it answered. Each authorizes only what it says; none accepts a risk outside its
own scope, approves a Gate or a version, or turns an admission switch on.

| ID | Question (as asked) | Owner's choice (verbatim) | What it authorizes |
|---|---|---|---|
| D2 | 上游有七种旧译文（de_DE、es_ES、fi_FI、fr_FR、pt_br、ru_RU、ua_UA）。v1.8 怎么处理？ | "v1.8 只保留英中（推荐）" | The seven translation rows become EXCLUDE through an ADR-062 revision; v1.9 may reopen them; v1.8 does not claim the seven languages. |
| D3 | AR 16x 贴图的作者来源不确定（10 条 class D、21 条 class E）。怎么处理？ | "保留已画的 NEW 替代，排除旧文件（推荐）" | W49's NEW replacements stay; the old files get EXCLUDED findings; the class E rows are excluded; the appearance difference is published. NEW art still needs its human provenance review (D7). |
| D6 | 是否批准 ADR-062 修订，把 81 条没有运行时消费者的 class E 资产移为 EXCLUDE（其中 45 条天空与 D4 相关）？ | "批准，按逐项无消费者证据排除（推荐）" | Root drafts the exact ADR-062 revision and per-row list from AUDIT-01's no-consumer evidence and re-pins the allowlist; the owner's approval of that drafted text is this decision. |
| D8 | K1 到 K5 共 30 条上游资产（先是离心机 OBJ 和贴图）因为历史里出现了上游仓库自己的 URL 而触发来源审查。怎么解除？ | "批准一条经审核的窄规则（推荐）" | A reviewed rule that references to the upstream repository's own URLs are not a foreign origin; every file still gets its history, hash, format budget and reference checks; licence checks unchanged; the centrifuge pair first. |
| D1a | 卫星舱（W27e）是否只接纳中性源火箭，拒绝曾经由 escrow loader 加注过的火箭？ | "接受中性源限制（推荐）" | W27e keeps the neutral-source limit, tells players `BAY_SOURCE_PROTECTED`, and leaves protected-source support to W27f. |
| D1b | 卫星舱等待期间，乘客可以按原版方式下船，但可能从高空跌落、座位也仍被保留。怎么处理？ | "先设计安全退出位置与许可（简报推荐）" | A design (then implementation and review) of a server-side safe exit position and permit, passenger identity and recovery, without changing playerdata authority or cancelling dismount, before bay admission. |
| D1c | 卫星舱是否增加每个所有者的 pending 限额？现在只有全服 64、每火箭 8，没有按所有者的上限。 | "增设每所有者限额，数值等 Root 给出数据后再定（简报推荐）" | Root measures capacity and fairness and proposes a per-owner pending cap and its full-cap refusal; the owner then sets the number; ADR-065 section 10 is revised with mixed-owner fairness and saturation tests. |
| D9 | v1.8 的兼容范围（版本文档第 10.5 节的模组组合 smoke、第 13.5 节的 modpack soak）定为什么？ | "v1.8 只承诺 Forge × JEI（推荐）" | A revision of the version document's 10.5 and 13.5 scope to Forge × JEI, with the support boundary published; a full modpack moves to v1.9; the reference workload and the four-hour soak still run. |
| D15 | 已经能载入、但规范化后再也保存不下的卫星注册表根（只有被编辑或外部生成的存档会这样），升级后怎么处理？ | "A：拒绝启动并给出具体诊断（推荐）" | W173's option A: re-encode on load, block the world's start with one bounded diagnostic naming the record when it cannot be written, keep the original file; risk registration, implementation and review follow. |
| D16 | 是否接受 PROPOSED ADR-025-R1（附魔原版护甲的氧气行为增补修订）？它描述的是已经交付并审核过的行为。 | "接受这八条规则（推荐）" | ADR-025-R1 is accepted as written; no code change; the older note is changed to refer to it. |
| D17 | 是否接受 PROPOSED ADR-076？它对 vanilla 和 Forge 自己在数据包重载时逐文件写出的 ERROR 日志给予限期豁免（本模组自己的日志仍然有界）。 | "接受精确站点的限期豁免（推荐）" | ADR-076 is accepted for exactly its listed vanilla and Forge sites, expiring at the v1.9.0 Gate; the two platform joined-list messages' unbounded length stays a recorded risk. |
| PAD-D4 | 充气台在单人游戏里给房主充气时，可能遇到区块和 level.dat 没有一起保存的崩溃窗口。选哪种交互？ | "O1：单人房主用有限罐交换（推荐）" | ADR-066 D4 resolves to W174's O1 for the single-player host: a finite 1,000 mB canister exchange; dedicated servers and LAN guests keep the verified automatic protocol; L0 risk registration, implementation, review and S2 follow. |
| D18 | GitHub 上的 v1.8 回归任务现在要跑 57 到 73 分钟，上限是 75 分钟，其中 GameTest 约 60 分钟。怎么处理？ | "拆分 GameTest 为多个 job（推荐）" | Root changes the workflow to split the GameTests into several jobs, keeping every case's timeout, budget, the complete inventory, the log check, artifact identity, the union of results and failure propagation. |
| D11 | 马、驴、骡、羊驼、骆驼及其乘客在其他星球上仍承受原版、未按重力缩放的跌落伤害（这些生物不发 LivingFallEvent）。v1.8 怎么处理？ | "接受为已知限制，不加 Mixin（推荐）" | A v1.8 known issue for these species and their passengers, published; no new Mixin; the damage is confirmed in the real-client session. |
| D12 | 月球目前固定为午夜外观。v1.8 要保留吗？ | "v1.8 保留固定午夜（推荐）" | The fixed-midnight Moon stays for v1.8 as a published appearance and time rule, confirmed on a real client. |
| D4 | 不使用旧贴图、由代码绘制几何体的天空（星球、黑洞、地球圆盘），是否作为 v1.8 的正式外观方向？ | "接受代码绘制外观，V1 实机再确认（推荐）" | The code-drawn sky is v1.8's direction; the 45 sky rows are excluded under D6; the real-GPU session confirms the sky, black hole and Earth disc. |

Not decided in this sitting (still open): D5 (the listen of the ten NEW sounds), D7 (the human provenance and licence
review of the fourteen pending records), W203-Q4 and D14 (the per-interaction and release risk dispositions, after
their evidence exists), D13 (a log sweep Root does first), D14-log (at release review), the final unified campaign
and the release itself.
