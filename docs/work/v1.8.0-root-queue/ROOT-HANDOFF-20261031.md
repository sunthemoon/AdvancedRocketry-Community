# Root handoff, 2026-10-31 11:50

Written by the outgoing Root session (Claude), which took over from [ROOT-HANDOFF-20261030](ROOT-HANDOFF-20261030.md) on
2026-10-30 08:20, for the next one. [QUEUE.md](QUEUE.md) and [CURRENT_VERSION](../../status/CURRENT_VERSION.md) stay
the sources of truth; this file adds the in-flight state, the decisions since the last handoff and the habits that
proved useful. Read ROOT-HANDOFF-20261030 sections 2, 5 and 6 as well: the mode, the merge flow and the tools are
unchanged.

## 1. Start here

1. Read [AGENTS.md](../../../AGENTS.md) section 13, [WORKER-RULES](WORKER-RULES.md), ROOT-HANDOFF-20261030 and this file.
2. Run `git status`, `git worktree list`, and check for running `codex.exe` and the heavy-JVM lock
   (`D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt`; absent means free).
3. Check the shared regression in section 3 and record it.

## 2. Mode and numbers

- **R163 still holds:** Codex-only workers (implementers and reviewers), at most two at a time; Root reads reports and
  diffs and reruns locally only for persistence or production changes; short turns with an hourly check.
- The last Root decision is **R212**; the next is **R213**.
- Root never tags, merges to main, force-pushes or marks a Gate or version passed; ADR acceptance needs the owner.

## 3. In flight

| Item | State | Next step |
|---|---|---|
| Shared regression 41165651590 on `3cf396a8` (W221 and W220 merged) | Passed on its first attempt (71.0 minutes) and recorded in CURRENT_VERSION by the outgoing session | Nothing; recent jobs take 57 to 73 of their 75 minutes (owner question D18). |
| Codex workers | None running | — |

## 4. What happened since the last handoff

Merged on the shared branch (each with its review, branch run and trial checks recorded in its QUEUE row): W209,
W207, W208, W210, W212, W213 (record only), W214, W215, W216, W217, W218, W211 (B3-S6, with R206's dependency
listing), W221 and W220; W219 refreshed CURRENT_VERSION's open items. W167 was closed as unnecessary (796 lines).
B3-S6 is complete as far as the product allows: A, B and C pass with two restarts each, the eleven ordinary F9, P1-F8
and N2 forms are out of reach for loader-funded rockets (R205), and their governed analogues are covered by W220 and
W221. Open residuals are listed in CURRENT_VERSION (W90's two first-use cases at about 103 ms, R-040's untested
availability cases, R-030's unknown-size fallback, the first BI O-veto attempt, E4, the S1 downgrade rows).

## 5. Next work

Nothing in the queue is ready. Candidates that need no owner decision:

1. The first BI O-veto attempt's residual (sixteen extra guarded ChunkMap save attempts before the selected denial in
   W211's restart logs; the second attempt passed): a diagnosis task, test-side unless a production cause appears.
2. W90's two full-observation first-use cases (new strip 102.789 ms, generated contained 103.517 ms) after W215: either
   a further measured remedy or an owner disposition.
3. The rest of the v1.8 acceptance work waits on the owner (the "Owner decisions pending" table, D1 to D18) or on the
   final unified campaign (S1, reference workload, joint soak, V1 and V2 on the owner's machines).

## 6. Lessons from this session

- `launch_worker.ps1` writes `run.cmd` as ASCII; it now refuses non-ASCII prompts. Write worker prompts in English.
- `merge_check.ps1 -Tests '*'` fails (the Java launcher expands `*`); for a whole-suite run, call Gradle directly in the
  trial worktree. Unit runs do not need the heavy-JVM lock, and waiting for it lost 40 minutes once.
- The governance workflow (the whole Python suite) runs only on the shared branch and main; worker branches get only
  the regression run. A worker's local Python discovery has to be compared with its base on this machine.
- Cancel a superseded run when two shared regression runs queue behind each other (the later one covers both).
- W217's `EscrowRelaunchClassesTest` flags any mod class newly reachable from the relaunch path; a new release-gated
  probe linked into a writer chain needs a reviewed entry in `src/test/resources/relaunch-existing-dependencies.txt`
  (R206).
- Two slices that each add S6 cases conflict in the runner, the routing test, the generated inventory TSV and its
  pinned counts; resolve as a union and regenerate the TSV with its script (R212).
- For long native campaigns, an interim independent review partway through found real oracle false positives before
  more runs depended on them (W211); the R190 and R201 renewal rule kept fixture defects from stopping every session.
- After a worker finishes, compare against its true base (`git merge-base`), not the shared commit made after its
  worktree was cut.
