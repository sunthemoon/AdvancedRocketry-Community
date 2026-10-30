# Root handoff, 2026-10-30 08:20

Written by the outgoing Root session (Claude) for the next one, because the session had grown large. The state below
is at shared head `21955ec5e`; [QUEUE.md](QUEUE.md) and [CURRENT_VERSION](../../status/CURRENT_VERSION.md) stay the
sources of truth, and this file adds only what they do not hold: the in-flight sessions, the tools and the working
habits.

## 1. Start here

1. Read [AGENTS.md](../../../AGENTS.md) section 13 (Root's authority and the simplified flow),
   [WORKER-RULES](WORKER-RULES.md), and this file.
2. Read the newest rows of [QUEUE.md](QUEUE.md) (W198 to W209) and its "Owner decisions pending" table.
3. Run `git status`, `git worktree list`, and check the two in-flight sessions and the heavy-JVM lock (section 3).
4. Restart a background waiter for each in-flight session (section 3); the old session's waiters do not carry over.

## 2. Mode and standing rules

- **R163 (owner, 2026-10-29):** Claude's quota is short. Every worker, implementer or reviewer, is Codex, at most two
  at a time; an independent review of Codex work is a fresh Codex session. Root reads reports and diffs, relies on the
  hosted run and the reviews, and reruns locally only for persistence or production changes. Short Root turns, check
  in about once an hour (`ScheduleWakeup`). Mixed mode returns only when the owner says the quota has recovered.
- Root never tags, merges to main, force-pushes, or marks a Gate or version passed; ADR acceptance needs the owner.
- Progress first; bug fixes run beside it when a slot is free.
- Decision numbers: the last Root decision is **R180**; the next is R181.

## 3. In flight

| Session | Run folder (under `D:/GitHub/ARCE-Task-Evidence/root-queue/`) | Codex thread | Next step |
|---|---|---|---|
| W207-review | `W207-review/run-20261030-081341` | `01a18a27-bd51-7e83-af87-8342994f5cea` | Read `W207-review/REVIEW.md`. On APPROVE or APPROVE-WITH-CHANGES and a passing branch run on `e2dbbb74c`: production change, so trial-merge with a local unit run, merge by SHA, have Codex draft any R-035 text the review names, apply it, record, push. Then queue a remedy for the remaining checkpoint-bearing P99 (119 to 164 ms, the relaunch request's synchronous F1 work), as W199 followed W197. |
| W208 (second session, R180) | `W208/run-20261030-074004` | `01a18a08-f3c5-7840-85ed-77fb43112303` | R-040 near-bound availability measurement. When it delivers: check the lock owner, read the record, push the branch, start a fresh Codex review. |

Restart the waiters (in the background):
`& 'D:/GitHub/ARCE-Task-Evidence/root-queue/tools/wait_workers.ps1' -Runs '<run folder>'`. A session's result is in
`last.txt` in its run folder; its progress is in `events.jsonl` (read it through a shared handle; its file time lags).

After every worker ends, check `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt`: if the named process has exited,
confirm no world was left, release the lock and delete the export and archive the record names (R180 did this for
W208).

## 4. Next in the queue

1. **W209** (R179): the new intermittent `EscrowMonitorGameTests.disarmingAfterPreparedKeepsTheAcceptedLaunchAndItsRealOutcome`
   (batch `monitor_governed_cutoff`, 2,400-tick timeout) in W206's branch run 40959470440. No TASK file yet; the job
   log and an excerpt are in `ARCE-Task-Evidence/root-queue/W209/`. Write `TASK-W209-…` as a diagnose-and-fix task
   (test-side fix keeping every assertion and timeout, or a production cause described and stopped) and start it in
   the next free slot.
2. **W203-P2, B3-S6** (real fuel-loader reused-family evidence), now unblocked by W204's merge; its scope is in
   `ARCE-Task-Evidence/root-queue/W203/PLAN.md` section 3.
3. The remedy for W207's remaining P99 (above), after W207 merges.
4. Shared regression: confirm the run on the newest shared head (W204's merge at `06f33d02e` and the records after it)
   and record it in CURRENT_VERSION's `latest_hosted_regression`.

## 5. Merge flow

1. **Ordinary slice:** review the diff and record; push the branch (`git -C <worktree> push origin <branch>`); start a
   fresh Codex review (`TASK-Wnnn-REVIEW.md`, a detached review worktree, output in
   `ARCE-Task-Evidence/root-queue/Wnnn-review/`).
2. **Merge rule:** merge when the review approves and the branch run passes, or fails only a case already guarded on
   the shared head or assigned to another task. A new failure needs a recorded Root decision (as R179 did).
3. **Persistence or production change:** trial merge in `D:/GitHub/ARCE-Task-Evidence/Temp/root-<w>-merge`
   (`git worktree add --detach … HEAD`, `git merge --no-ff --no-commit <SHA>`), the slice's Python tests and the whole
   unit suite with `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` (`gradlew.bat --offline --no-daemon --console=plain test`,
   sum the JUnit XML). Test-only slices need a compile at most.
4. **Merge by SHA** in the shared tree (`git merge --no-ff <SHA> -m "Merge Wnnn (<sha>): …"`), then
   `git -C <trial> diff --stat <merge> -- src scripts tests` must be empty.
5. **Risk text:** a fresh Codex session writes exact drafts plus an audit script (see `TASK-W204-RISK-DRAFTS.md`);
   Root reads them, checks the audit JSON's hash, applies them with `tools/apply_drafts.py <drafts.md>` and checks that
   the R-035 row keeps 8 pipes.
6. **Record** in QUEUE (the slice's row; never a newline inside a table cell) and CURRENT_VERSION
   (`latest_code_commit`, `latest_hosted_regression`); run
   `python -B scripts/validate_repository.py --require-approved-identity` (45 checks); commit with the
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` line; push; remove the merged worktrees and Temp logs.

## 6. Tools

Root's helpers are in `D:/GitHub/ARCE-Task-Evidence/root-queue/tools/` (their README lists each one). Workers start
with `D:/GitHub/ARCE-Task-Evidence/root-queue/launch_worker.ps1 -Name Wnnn -Engine codex -Worktree <dir> -Prompt '<text
without double quotes>'`; a prompt names the TASK by its absolute path when the worktree predates the TASK file.

## 7. Lessons from this session

- GitHub sometimes drops the TLS handshake (04:00 on 2026-10-30): pushes and API calls fail while port 443 connects.
  Commit locally and retry later; it recovered within minutes.
- A worker's refused cleanup can leave the heavy-JVM lock, an export and a large archive behind (W208); see section 3.
  Reviewers' cleanup is often blocked too: removing their worktree with `git worktree remove --force` clears it.
- A branch cut before a shared fix fails the case that fix closed (W198 and W196's two-claim case); that does not
  block its merge.
- The hosted regression takes 65 to 73 of its 75 minutes (owner question D18).
- Write files as UTF-8 without BOM and LF only, with absolute paths for .NET file calls; a link in a tracked document
  must point to a tracked file (use a code span for evidence paths and worker branches).
- Record a worker's start time only after launching it (two rows had a guessed time to correct).
- Codex prompts avoid the words "duplication" and "exploit".

## 8. Owner questions still open

The "Owner decisions pending" table in [QUEUE.md](QUEUE.md) (fifteen rows; the newest is W203's question 4, the risk
scope before each admission switch), the W27e bay choices, the v1.8 reference modpack and D4 (gas pad). The numbered
questions sit in the rows that raised them: D1 to D14 in W172's inventory (its section 5), D15 in W173's row, D16 in
W191's, D17 in W183's (ADR-076, from W193), and D18 in the machine-operations entry of 2026-10-29 22:40 (the hosted
job's 75-minute limit).
