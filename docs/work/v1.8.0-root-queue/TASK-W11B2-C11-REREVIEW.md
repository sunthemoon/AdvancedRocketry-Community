# TASK W11b2-C11-rereview: check the continuation 11 fixes (read-only)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a
reviewer: change nothing in any repository worktree.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w11b2-rocket-integration`, commits `1b775d57` to `eb80b7de` (fixes) and `afdce472` (record), on top of `71bda5aa` |
| Review being closed | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/REVIEW.md`, SHA-256 `c5e829e233128d4029213579a5296d6ab42a2f8c6bb7a3eaf924c1710760f8c6` |
| Root decisions | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`, SHA-256 `2a87993811b2889b7c2a062465a4614632ef1c8c862e16d301f9d4b2f8ab9869` |
| Checkout | `D:/GitHub/arce-v180-w11b2-c11-rereview`, detached at `afdce472`, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C11-rereview/REVIEW.md` (create the folder), and its SHA-256 on the last line of your answer |

## What to check

For each finding of the review (H1, H2, M1 to M5, L1, L3, I1, I4):
- the commit that fixes it;
- whether the fix does what Root's decision says, at the cited file and line;
- whether its test fails without the fix. Revert the fix in a scratch copy of the checkout, or reason
  from the test;
- any new defect the fix introduces.

Also review `eb80b7de` ("keep exact-candidate retries active after transient writer failure"), which no
finding asked for. Say what it fixes and whether it is right.

**Check M1 and M2 together.**
- The admission limit is 5, and it counts the retired markers.
- The decode bound stays 8 in every decoder.
- No saved root that decoded before is refused now.

**Check M4.** The baseline readback runs in the pre-start managed-data check. A later failure never
throws through a tick handler, and its log line is bounded.

**Rerun** the targeted unit tests of the fixes in your checkout. Set `JAVA_HOME` to
`C:/Program Files/Java/jdk-17.0.7`. Also rerun the Python tests that import the changed scripts. Take
no heavy-JVM lock, and run no GameTests or native runs.

## Output format

- A verdict: CLOSED, CLOSED-WITH-FOLLOWUP, or REOPEN.
- One row per finding: the fixing commit, closed or not, and evidence.
- New findings, each with an ID, a severity, the file and line at `afdce472`, the failure scenario and a
  suggested fix.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
