# TASK W172: read-only inventory of the work left for v1.8.0

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and read-only: do not
commit, change nothing in any repository or worktree, and do not start other agents.

| Item | Value |
|---|---|
| Repository | `D:/GitHub/AdvancedRocketry-Community`, shared branch `codex/v1.8.0-classic-content` at the commit named in your prompt |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md`, and its SHA-256 on the last line of your answer |

Do not read `AdvancedRocketry-Community-v1plus-Development-Docs/` or any file under it.

## Why

Root dispatches progress work from [QUEUE](QUEUE.md). Its ready progress rows are nearly used up. The bay chain (W170)
and N3-B (W164) are in flight. [COMPLETION-PLAN](../../status/COMPLETION-PLAN.md) is long. Many of its items are
marked `[ ]` or `[~]` from earlier campaigns, and some are probably already done, superseded or waiting on the owner.
Root needs a reliable list of what still stands between the branch and the v1.8.0 release evidence, and of what can
be dispatched now.

## What to do

Read:
- COMPLETION-PLAN's v1.8.0 sections (C14 to C19, the "后续执行" section and the regression section);
- `docs/versions/` for v1.8.0, [docs/06](../../06-RELEASE-AND-ACCEPTANCE-GATES.md) and `docs/status/GATE_STATUS.md`;
- QUEUE, including its "Next candidates" list.

For every open item (`[ ]` or `[~]`, or a queue row not merged):

1. **Find its real state in git.** Is it merged, on which commit or branch, and is it superseded by a later task?
   Cite the commit, file or record.
2. **Classify it as exactly one of:**
   - **A:** done, and only the plan's mark is stale;
   - **B:** progress work that can be dispatched now, with no owner decision and no unmerged prerequisite;
   - **C:** waits on an in-flight task (name it);
   - **D:** waits on an owner decision (quote what is needed);
   - **E:** evidence for the final campaign (real clients, multiplayer, GPU, reference node), not code;
   - **F:** obsolete, or superseded by a later decision (cite it).
3. **For each B item,** give:
   - a one-paragraph scope that a TASK could use;
   - its prerequisites;
   - whether it is a persistence, protocol or save-refusal slice, which needs a design note and a review;
   - the files or packages it would touch, and a rough size.

Then recommend a dispatch order for the B items: progress first, then the parallel bugs that block hosted
regressions. Also list the D items as owner questions, each with a short recommendation.

## Output format

`INVENTORY.md` holds:
- a summary table (item, source line, state, class, evidence);
- the B-item scopes;
- the dispatch order;
- the owner questions;
- the commands you ran.

Keep it factual: every class must cite evidence. The SHA-256 of `INVENTORY.md` goes on the last line of your answer.
