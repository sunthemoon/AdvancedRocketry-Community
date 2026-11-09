# Owner decisions of 2026-11-09: the five loader variants target v1.8, after an integrated test

Channel: the owner's Claude Code Root session, 2026-11-09. At about 10:40 the owner asked "列一下加载器变体的签核清单吧",
and Root answered in the session with a checklist for the five PLANNED ledger rows: `block_variant:loader/1` (the
satellite bay, C17c, bay deployment admission) and `block_variant:loader/3`, `loader/2`, `loader/5` and `loader/4` (the
item loader and unloader and the fluid loader and unloader, C17a, the cargo switches `ITEM_CARGO_LOAD`,
`ITEM_CARGO_UNLOAD`, `FLUID_CARGO_LOAD` and `FLUID_CARGO_UNLOAD`). For each switch it offered A (target enabling it in
v1.8: Root runs the evidence that needs no owner now, and the owner signs a precise per-switch risk scope after the
campaign) or B (keep it disabled and defer the row to v1.9 through an ADR-062 revision), and recommended A for all five.
Shortly after Root's hourly check of 10:55 the owner replied, verbatim:

> 五个变体暂定 A，以 v1.8 开启为目标。请先完成同一候选 JAR 的卫星舱和四种货物整合测试，逐项汇报通过、失败、未覆盖的场景及预计剩余工作。测试期间生产准入保持关闭；发现问题先修复并复测。联机与性能仍按此前决定放在最后，何时启动完整战役再确认。最终请分别提供每个开关适用的风险和证据，届时我再决定是否开放。燃料加注机单独处理。这次选择不代表接受残余风险、批准 Gate 或发布。请同时核对 R-042 的全服务器影响、R-043 的等待时间，以及 R-044 与各设备风险的归属。

## What the owner decided

| Item | The owner's words | Recorded meaning and scope |
|---|---|---|
| Direction | 五个变体暂定 A，以 v1.8 开启为目标 | All five switches tentatively take option A: the target is to enable them in v1.8. The ledger rows stay PLANNED. |
| The integrated test | 请先完成同一候选 JAR 的卫星舱和四种货物整合测试，逐项汇报通过、失败、未覆盖的场景及预计剩余工作 | First, the satellite bay and the four cargo kinds are tested together on one candidate JAR. Root reports each scenario as passed, failed or not covered, with the estimated remaining work. |
| Production admission | 测试期间生产准入保持关闭 | Every production admission switch stays off during the test; the runs use only the existing gated test path. |
| Defects | 发现问题先修复并复测 | A problem found is fixed first and then tested again. |
| The campaign | 联机与性能仍按此前决定放在最后，何时启动完整战役再确认 | Multiplayer and performance stay last, as decided on 2026-11-04; the owner confirms later when the full campaign starts. |
| The decision to enable | 最终请分别提供每个开关适用的风险和证据，届时我再决定是否开放 | At the end Root gives the applicable risks and the evidence for each switch separately, and the owner then decides whether to enable it. |
| The fuel loader | 燃料加注机单独处理 | The escrow fuel loader's admission is handled separately, outside this work. |
| What this is not | 这次选择不代表接受残余风险、批准 Gate 或发布 | The choice accepts no residual risk and approves no Gate, version or release. |
| Three checks | 请同时核对 R-042 的全服务器影响、R-043 的等待时间，以及 R-044 与各设备风险的归属 | Root also checks R-042's server-wide effects, R-043's waiting times, and how R-044 relates to each device's risks. |

## Root's follow-up decisions

| ID | Decision | Reasons |
|---|---|---|
| R385 | **The owner's answer is recorded as above.** The status file's ledger item and the owner-decision table in QUEUE point to this record. The 2026-10-26 fuel acceptance and the per-interaction acceptance row stay as they are. | Section 10 of AGENTS.md. |
| R386 | **Candidate C1 is the hosted build of run 42499122646 at `70dd5d43e`** (artifact `v180-build-70dd5d43e7b24a19cf5c674fe745749a090662a2-1`, `advancedrocketry-community-1.20.1-1.8.0-dev.jar`, SHA-256 `fa5ec1812995ce3cca9d74e447301c66b6d5c9117789c2daae71cac4598e4832`), the commit whose full regression passed with 1,802 GameTests. Every bay and cargo case runs on this file, copied to `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/candidate-c1/`. A run may use a later commit for harness changes only when `src/main`, `src/generated` and the build files equal `70dd5d43e`. If a defect needs a production fix, the fix merges after a green hosted run, that run's build becomes the next candidate, and the whole bay and cargo matrix runs again on it: the final per-switch evidence comes from one JAR, with no reuse across JARs. | The owner asked for one candidate JAR. The hosted build is reproducible and already passed every GameTest; rerunning everything after a fix is machine time only and keeps the evidence on one file. |
| R387 | **W325 runs the satellite bay's integrated evidence on C1**: a requirement-to-evidence matrix committed before any native run, then B1 to B18 of W27A-DESIGN section 9 with each named side and variant and two restarts each, the safe-exit and released-flight cuts, the neutral-source refusal and the per-owner cap, reported case by case. Test-side changes only. | W27e's packaged evidence (W127-PLAN section 3); the bay's per-leaf native cases ran on older JARs. |
| R388 | **W326 runs the four cargo kinds' integrated evidence on C1**: C-S1 to C-S5 and D-S1 to D-S2 of W11CD-DESIGN section 6 (120 cuts, two restarts each), the earlier residual cases of W138, W142 and W143, and the per-owner cap over cargo, with every case mapped to the kinds it evidences. Test-side changes only. | The cargo families ran on older integrated builds (W138, W142 and W143, 2026-10-19 and 2026-10-20); the owner asked for one current candidate. |
| R389 | **W327, a read-only Claude session, audits the owner's three checks** at `70dd5d43e`: R-042's server-wide effects, R-043's waiting times (bounded, estimated or measured), and R-044's attribution to each device; and drafts the per-switch risk and evidence table for the five switches, the fuel loader excluded. Root edits the risk register and the status file from it. | The owner's three checks need a reading of the code and the register that does not depend on the test runs; a different engine from the test implementers. |

The full campaign (real clients V1 and V2, the reference node and the four-hour soak) stays last and is not started.
Production admission stays off for fuel, every cargo kind and the bay. No residual risk is accepted, and no Gate,
version or release is marked passed.
