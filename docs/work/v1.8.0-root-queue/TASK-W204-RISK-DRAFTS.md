# TASK W204-risk-drafts: exact R-035 text for the named-pad fix

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w204-review` (W204's review checkout at `9a1c77e1a`; its `docs/11-RISK-REGISTER.md` is the shared head's) |
| Inputs | finding W204-R1 of `D:/GitHub/ARCE-Task-Evidence/root-queue/W204-review/REVIEW.md` (SHA-256 `71d16af6…6e1b7dc0`); `docs/work/v1.8.0-root-queue/records/W204.md` at `9a1c77e1a`; R-035 in `docs/11-RISK-REGISTER.md` |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W202-review/input/W202-drafts.md` and its audit script |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204-review/input/W204-drafts.md`, plus an audit script and its JSON result in the same folder |

R-035 is one Markdown table row, in Chinese. Write exact `Target` / `Replace exactly` / `With` replacements for what
W204-R1 asks:

1. Qualify "着陆台释放、返回日志、请求完成和恢复报告在移除确认之后执行。" as the ordinary retirement continuation's
   ordering, and record that `LandingPadService.claim` lacked that guard until W204 (`db87ec57a`), with the finding
   (N2_PAD_MOVED, `after_atomic_move`, acknowledged 0) as history.
2. Add the four points the review lists: the acknowledged-absence rule; an unavailable proof refuses without changing
   ownership and can hold a claim indefinitely under persistent IO failure, unreadable authority or a stopped journal,
   with no timeout or force-release; restart reconciliation is local and on demand, and a stale release cannot erase a
   successor's claim; loaded absence grants only this pad check.
3. Make "未确认前不发布成功，也不推进发射、阶段、清理、迁移、替换或重连等依赖效果。" distinguish writer-dependent
   effects from the narrow loaded-pad-absence proof.
4. Keep the N2 reservation-refusal sentence, now supported by W204's campaign (N2_REFUSED and N2_STOPPED, two restarts
   each), and say N3-C's cuts ran.

R-042 needs no change (the review says so). Keep the row on one line with the same column count, in complete Chinese
sentences, without closing the risk or any Gate. The audit script checks that every anchor is unique in the current
file, that nothing overlaps, and that the row keeps its column count; run it and keep its JSON result.

## Not in this task

Any tracked file, ADRs, status files and the ledger.
