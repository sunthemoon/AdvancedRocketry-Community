# TASK W11b2-C09-review: independent read-only review of continuation 09 (settlement steps)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: you change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w11b2-rocket-integration`, commits `88c2272f`, `2def2668`, `f3828a81`, `ce9bb346` (code) and `e96a3223`, `93af6e1d` (record), on top of `f83103fa` |
| Contract | `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); Root's decision in `TASK-W11B2-CONTINUE-09.md` (`f83103fa`) |
| Record under review | `records/W11B2.md`, "Continuation 09" and "Continuation 09 resumed" |
| Your folder | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/` (create it). Make a detached worktree of `ce9bb346` there, or use `git show` and `git diff` from any checkout without writing |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

A new implementer session (continuation 10) is building steps 3 to 7 on this branch now. Your review
covers only continuation 09's committed steps, so findings can reach continuation 10 early. This is a
persistence and save-refusal slice (AGENTS.md section 13): the review must come from a session other
than the implementer.

## What to check

1. **Root's prefix decision** (`88c2272f`): rocket endpoints share one prefix identity across PRIMARY
   and OXIDIZER; `Unit` stays on each link; other endpoint kinds keep unit-aware keys; endpoint record
   equality and the serialized endpoint fields do not change. Check the endpoint inventory in the
   record against the code.
2. **O4 pruning** (`2def2668`): exact-group acknowledgement, the bounded cursor, restart
   reconstruction, and the exclusion of unresolved and UNKNOWN groups.
3. **F3, F5 and M2** (`f3828a81`, `ce9bb346`):
   - the one-use neutral-survivor normalization after acknowledged O1 and before O2;
   - mandatory native bindings;
   - UNKNOWN receipts, with `loss_known` and `audited_loss` refused;
   - the schema replacement (escrow root, operation and intent to 2, receipt to 3, typed floor to 2),
     with pre-change data refused and its raw file preserved, and no migration chain;
   - the closed short-key receipt dictionary and `scripts/inspect_escrow_receipt.py`;
   - the startup baseline readback;
   - the pre-L1 settlement-space reservation, with every byte, node and depth bound unchanged.
4. **AGENTS.md section 3.3:** every new refusal of saved data has a stated scope, duration, log volume
   and recovery. Name any that R-031, R-035 or the ADR-065 notes do not cover yet; Root writes them.
5. **Tests:** do they prove what the record claims? Look for assertions weakened or removed, the
   `StationWorldMigrationTest` schema expectation change, and the
   `RocketTransferFenceGameTests` floor-field regeneration.
6. **Rerun** the targeted unit tests named in the record (`*resourceescrow.*`,
   `*StationWorldMigrationTest`, `*EscrowSettlementFormatTest`, `*EscrowNativeBankPrefixTest`,
   `*EscrowSettlementPruningTest`) in your own detached worktree. Do not run GameTests or native runs,
   and take no heavy-JVM lock. Set `JAVA_HOME` to `C:/Program Files/Java/jdk-17.0.7`.

## Output format

- A verdict: MERGE-READY-AS-PART, FIX-FIRST, or BLOCK.
- Findings, each with an ID, severity (Critical, High, Medium, Low, Info), file and line at
  `ce9bb346`, the failure scenario, and a suggested fix.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.

## Not in this task

- Any edit in a repository worktree, any commit, push or merge, and any other agent.
