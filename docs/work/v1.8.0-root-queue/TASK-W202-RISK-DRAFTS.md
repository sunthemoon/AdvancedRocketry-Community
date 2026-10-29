# TASK W202-risk-drafts: exact R-035 text for W202's write-back sizing

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Inputs | section 4, "R-035 integration guidance for Root", of `D:/GitHub/ARCE-Task-Evidence/root-queue/W202-review/REVIEW.md`; `docs/work/v1.8.0-root-queue/records/W202.md` on `codex/v1.8.0-w202` at `69a6abdc0`; R-035 in `docs/11-RISK-REGISTER.md` on the shared head |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W200-review/input/W200-drafts.md` and its audit script |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W202-review/input/W202-drafts.md`, plus an audit script and its JSON result in the same folder |

R-035 in `docs/11-RISK-REGISTER.md` is one Markdown table row, in Chinese. Write exact `Target` / `Replace exactly` /
`With` replacements for what the review's section 4 asks:

1. Qualify the four named W200-era sentences as historical observations of the W200 run, keeping their figures and
   meaning.
2. Add one W202 paragraph that names `f232b0909` and `7c331f41a`, the paired figures (callback-inclusive P99 168.25 to
   63.83 ms; worst proof tick 242.56 to 63.99 ms; proof ticks over 100 ms three to zero), that sizing still uses the
   real writer without the unused buffer, and that insertion uses the codec's same-invocation checked-root count. It is
   not a durable witness or a cache.
3. Keep every statement the review lists as unchanged, and keep "最坏证明/准备 tick 成本及 docs/17 参考负载资格仍开放"
   or an equivalent open statement: the reference load, restart, soak and real terrain and client validation stay open.
   Do not close the risk or any Gate.

Keep the row on one line with the same column count, in complete Chinese sentences. The audit script checks that every
anchor is unique in the current shared-head file, that nothing overlaps, and that the row keeps its column count; run
it and keep its JSON result.

## Not in this task

Any tracked file, ADRs, status files and the ledger.
