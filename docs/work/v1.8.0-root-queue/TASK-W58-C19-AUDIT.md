# TASK W58: C19 audit: what is still needed to finish the validator chain (audit only)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w58-c19-audit` (from the shared branch) |
| Branch | `codex/v1.8.0-w58-c19-audit` (local only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W58.md` (new). No code, test, status or ledger change |
| Input | `docs/status/COMPLETION-PLAN.md` section "C19 矩阵、审核与交付"; `docs/work/v1.8.0-c19-strict-validator/`; `docs/versions/v1.8.0.md`; `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md` |

C19 started before the Root handover. Its records list many validator and fixture candidates
(tasks 11 to 139) that were committed on side branches but never merged into the shared branch,
mostly because the full Python suite and the strict validator timed out after 180 seconds on this
Windows machine.

Since then, the shared branch's "Repository governance" workflow (`.github/workflows/repository-docs.yml`,
added for the shared branch in `fc194a8e`) runs the full Python suite and the validators on hosted CI.
Run 38613529659 at `ce9d190a` ran 1,427 tests in 167 seconds; it failed only on the v0.8.0 evidence
test that W49F fixes.

## What to do

1. **List every open C19 item** in the completion plan (each `[~]` and `[ ]` line, including
   R11-OPS01, R14-OPS01, the validator chain `82f82b1f` and `d605be33`, and tasks 16, 34, 49, 64, 73,
   86, 92, 97, 101, 108, 114, 119, 124, 132 and 139). For each:
   - its branch and commit, and whether that commit's change is already on the shared branch
     (`git merge-base --is-ancestor`, or the same change by another commit: compare the diff);
   - the problem it solves, in one sentence;
   - a class:
     - **A, still needed:** the defect still exists on shared. Show it with a failing command or a
       file and line;
     - **B, superseded:** shared already solves it another way (name the commit), or the hosted
       governance run makes it unnecessary (say exactly why);
     - **C, operational record only:** a local cleanup or evidence item with no code to merge;
   - for class A, what merging would take: conflicts against current shared (try a scratch merge in
     your worktree and abort it), tests to run, and whether it touches a historical record.
2. **The C19 exit criteria.** Read `docs/versions/v1.8.0.md` and the gates document. List what C19
   must deliver for v1.8 (for example `PORTING_MATRIX`, ledger `--closure`, evidence references,
   the strict validator passing). For each, say whether it can be done now, what blocks it (owner
   decision, PLANNED rows, REVIEW assets), and how large it is.
3. **Run the checks** that the hosted workflow runs, as far as they finish here in a bounded time.
   Name each command and its result. Do not run the full Python suite locally (it takes over 30
   minutes here); read the hosted log instead:
   `D:/GitHub/ARCE-Task-Evidence/root-queue/ci/38613529659/` holds the failed job's log.
4. **Propose an ordered plan** of at most six slices that would finish C19, each with its write scope
   and test, for Root to dispatch.

## Commands

- `git` read commands, `python -B scripts/validate_v180_content_ledger.py --require-accepted` and
  `--closure`, and other validators that finish in a few minutes. Record each command and result.
- No Gradle run is needed.

## Not in this task

- Any merge into the shared branch, any change to code, tests, status files, the ledger, ADRs or
  sealed evidence. Scratch merges are aborted before you finish (`git merge --abort`), and your
  branch ends with only the record commit.
