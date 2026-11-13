# Owner decisions of 2026-11-13: an R-044 sign-off draft, fluid accounting and the uncovered rows first

Channel: the owner's Claude Code Root session, 2026-11-13. The owner had the Chinese summary of the filled per-switch
report ([C3-SWITCH-REPORT](C3-SWITCH-REPORT.md), Root decision R431) and Root's list of open decisions: accept R-044 or
not, enable each of the five switches or not, schedule X01 to X03 or not, and when the full real-client campaign
starts. After the machine restarted for Windows Update, the owner wrote, verbatim:

> R-044 的旧版降级限制先按完整备份恢复方案提出具体签核文本，保留失败结果；五个开关暂不开启，先补流体记账和未覆盖项。请分别给出 X01～X03 的隔离测试方案和预计工作量，破坏性测试执行前再让我确认。最终实机战役先准备计划，联机和性能仍放最后。

## What the owner decided

| Item | The owner's words | Recorded meaning and scope |
|---|---|---|
| R-044 | R-044 的旧版降级限制先按完整备份恢复方案提出具体签核文本，保留失败结果 | Root drafts concrete sign-off text for R-044's downgrade limit, with restoring the complete pre-downgrade backup as the recovery. The failed downgrade results stay failed. The draft is a proposal: R-044 is not accepted until the owner signs it. |
| The switches | 五个开关暂不开启 | Production admission stays off for the bay and all four cargo kinds. |
| What comes first | 先补流体记账和未覆盖项 | First, implement per-fluid signed accounting (R-035) and cover the rows that C3 left NOT_COVERED. |
| X01 to X03 | 请分别给出 X01～X03 的隔离测试方案和预计工作量 | Root gives a separate isolated test plan and an effort estimate for each of X01, X02 and X03. |
| Destructive tests | 破坏性测试执行前再让我确认 | No destructive test runs until the owner confirms it. |
| The campaign | 最终实机战役先准备计划，联机和性能仍放最后 | Root prepares the plan for the final real-client campaign. Multiplayer and performance stay last. |

The choice accepts no residual risk and approves no Gate, version or release. The escrow fuel loader stays a separate
matter (2026-11-09).

## Root's follow-up decisions

| ID | Decision | Reasons |
|---|---|---|
| R432 | **The owner's answer is recorded as above.** QUEUE, the status file and the per-switch report point to this record. | Section 10 of AGENTS.md. |
| R433 | **Root's R-044 sign-off draft is [R044-SIGNOFF-DRAFT](R044-SIGNOFF-DRAFT.md).** It states the scope, the trigger, what changes, the duration, the recovery from the complete backup, the evidence, the conditions and what the signature does not do. `cargo-G2-REUSE` rows 3 to 5 and `w336-downgrade` stay FAIL in every record, before and after a signature. Root changes the register, the ADR and the status file only after the owner signs, and then records the signature verbatim. | The owner asked for concrete sign-off text, with the failures kept. |
| R434 | **W357 (Codex) writes the design note for per-fluid signed accounting**, with no production code. It covers what is recorded for each fluid and each settlement step, the schema change and migration, size bounds, UNKNOWN and REPAIR, the fuel loader and both fluid cargo directions, the effect on old JARs (R-044), and the tests. A different engine reviews it read-only (AGENTS.md section 13) before the implementation task is written. | R-035 lists it as needed before admission; it changes a saved form, so the design note and a read-only review come first. |
| R435 | **W358 (Codex) builds the uncovered bay rows natively.** It builds gated cases for B6.8, B10.6, B11.8, B11.9 and CAP.3, which W331 found reachable. It checks whether a deterministic gated control can hold B11.7's UNKNOWN copy and keep one group of B17.3 unloaded, and builds those cases if one can. It gives the argument for why B11.2 and B17.5 cannot be reached under production rules. Changes are test-side only: gated release-test fixtures, runners and campaign families. | The owner asked to cover the NOT_COVERED rows first; W331 recorded how most are reachable. |
| R436 | **X01 to X03 have their own isolated plans with estimates: [X01-X03-ISOLATION-PLAN](X01-X03-ISOLATION-PLAN.md).** No destructive step runs before the owner confirms it. The steps that need confirmation are named in the plan: enabling a virtual machine host, hard power-offs, and fault injection. Building the harness and running dry runs on undamaged disks are preparation and may start. | The owner's instruction. |
| R437 | **W359 (a read-only Claude session) drafts the final real-client campaign plan**, `records/FINAL-CAMPAIGN-PLAN.md`. It lists every deferred real-client, dedicated-server, V1, V2 and reference-node item from v1.0 to v1.8, in phases with single-player first and multiplayer and performance last, the owner's resources, the steps the owner performs, time estimates and the evidence layout. Root reviews it before it goes to the owner. The campaign does not start. | The owner asked for the plan first; multiplayer and performance stay last. |
| R438 | **The other uncovered items go to W360 (Codex), after a Codex slot frees.** These are the item before-write and store-veto cuts in both directions, R-031's future or invalid ledger root, the vanilla-dismount row of `r042-L_LEDGER`, a gated presence-unknown seam after the pre-start check if one can be deterministic, and an item-device hold GameTest. Ordinary `LF_S6_F9_RESPAWN` stays NOT_COVERED (W211 R203). R-043's shared budget and W90's cold-destination cost are performance and stay last. When W357's implementation, W358 and W360 have merged, the next hosted build is candidate C4. Every family then runs again on C4 (R386), and the report is refilled from C4's results. | One candidate JAR for the final evidence (R386); the fixtures and the accounting change production paths. |

Production admission stays off for fuel, every cargo kind and the bay. No residual risk is accepted, and no Gate,
version or release is marked passed.
