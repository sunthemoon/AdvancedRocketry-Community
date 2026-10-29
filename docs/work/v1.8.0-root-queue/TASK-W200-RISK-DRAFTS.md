# TASK W200-risk-drafts: R-035 text and a record correction for W200

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Inputs | `D:/GitHub/ARCE-Task-Evidence/root-queue/W200-review/REVIEW.md` (SHA-256 `753d8bad…3266`), findings W200-R1 and W200-R2; `records/W200.md` on `codex/v1.8.0-w200` at `5592f0f79` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W200-review/input/W200-drafts.md`, plus an audit script and its JSON result in the same folder |

1. **R-035 (W200-R2).** Write the exact replacements the review lists for `docs/11-RISK-REGISTER.md`, in the same
   `Target` / `Replace exactly` / `With` format as `D:/GitHub/ARCE-Task-Evidence/root-queue/W199-review/input/W199-risk-drafts.md`,
   so R-035 describes W200 as the current state: admission preflight paced on server ticks, the checked candidate
   encoding reused, the burst at 23.34 ms and the write-back maximum at 166.67 ms, still over docs/17's 100 ms. Keep the
   row on one line with the same column count, in complete Chinese sentences. Do not close the risk or any Gate.
2. **The record correction (W200-R1).** Write a short English section, "Root's correction at merge (W200-R1)", for Root to
   append to `records/W200.md`: the work cohort leaves out every residual slow callback tick, so the statement that the
   measured cohorts meet the targets holds only for that cohort; name the slow ticks and their sizes from the evidence.
3. Write an audit script that checks every R-035 anchor is unique in the current shared-head file, that nothing
   overlaps, and that the row keeps its column count; run it and keep its JSON result.

## Not in this task

Any tracked file, ADRs, status files and the ledger.
