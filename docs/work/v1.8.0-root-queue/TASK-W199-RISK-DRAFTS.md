# TASK W199-risk-drafts: exact R-035 text for W199's I6 cost fixes

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Inputs | the "Risk-register instructions for Root" section of `D:/GitHub/ARCE-Task-Evidence/root-queue/W199-review/REVIEW.md`; `docs/work/v1.8.0-root-queue/records/W197.md` (on the shared head) and `records/W199.md` on `codex/v1.8.0-w199` at `f896db873` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W199-review/input/W199-risk-drafts.md`, plus an audit script and its JSON result in the same folder |

R-035 in `docs/11-RISK-REGISTER.md` is one Markdown table row, in Chinese. Write exact replacements, in the same
`Target` / `Replace exactly` / `With` format as `D:/GitHub/ARCE-Task-Evidence/root-queue/W196-review/input/W196-risk-drafts.md`,
for what the review asks:

1. The sentence ending "共享每 tick 至多四次开始的限制" stays a true shared upper bound, but gains the I6-specific
   qualification: at most one cold-preparation pair per tick, through asynchronous ticket-only activation; the other
   sites keep their prior behaviour.
2. "最坏证明/准备 tick 成本及 docs/17 参考负载资格仍开放" stays. Mark the R162 sentences (6 of 64, 257.33 ms) as
   historical, superseded for the current worst case by W197 and W199, and state W199's figures and the residuals that
   W200 carries: the 138.57 ms admission burst and the 341.16 ms write-back tick, and the unverified reference load
   and soak. Do not close the risk or any Gate.
3. Add that the transfer journal's insertion shortcut runs only with a canonical-encoding witness, and that an unproven
   resident set keeps the exact whole-root validation (W199-R1's fix).

R-040 needs no change (the review says so). Keep the row on one line with the same column count, in complete Chinese
sentences. Write an audit script that checks every anchor is unique in the current shared-head file, that nothing
overlaps, and that the row keeps its column count; run it and keep its JSON result.

## Not in this task

Any tracked file, ADRs, status files and the ledger.
