# TASK W223: a brief of every owner decision still open for v1.8

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. Read only: write the
brief outside the repository, change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w223` (new; a detached checkout of the shared head named in your prompt) |
| Sources | `docs/work/v1.8.0-root-queue/QUEUE.md` ("Owner decisions pending" and every row that names an owner question, including D1 to D14 in W172's inventory section 5, D15 in W173's row, D16 in W191's, D17 in W183's, D18 in the machine-operations entry of 2026-10-29 22:40); `docs/status/CURRENT_VERSION.md` ("What is still open"); `docs/status/GATE_STATUS.md` and `docs/versions/v1.8.0.md` (the Required Gates); `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`; `docs/11-RISK-REGISTER.md`; the ADRs and records each decision cites |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W223/OWNER-DECISIONS.md` (in Chinese, the owner's language, with identifiers left as they are) and `OWNER-DECISIONS.json` (the same, structured) |

The owner has asked Root to drive v1.8 to completion in this session. Root needs, in one place, every decision that
only the owner can make and that still blocks any v1.8 Required Gate, the ledger, an admission switch or the release.

## What to do

For each open decision (skip any the records show as decided; list those separately with the decision and its date):

1. **ID and one-line question**, in plain words.
2. **What it blocks** for v1.8: which Gate, ledger rows, admission switch or task; and whether v1.8 can be released
   without it (for example by keeping a feature disabled).
3. **The options** as the records state them (two to four), each with its consequence.
4. **Root's or the records' recommendation**, quoted with its source; if none exists, say so and propose one with
   reasons.
5. **Evidence still missing** before the decision can be made, if any (for example the final campaign).

Then group them: (a) decisions that unblock work an agent can do now; (b) decisions that only accept or waive
residual risk; (c) decisions that need the owner's own action (listening to sounds, real clients, the reference
machine). Order each group by how much of v1.8 it unblocks. Finish with the list of v1.8 Required Gates and, for
each, what evidence exists, what is missing and which decision or campaign it waits on.

## Not in this task

Any tracked file, any decision itself, ADR status changes, the ledger.
