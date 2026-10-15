# TASK W62: map the v1.8 version checklist to existing tests and evidence (audit only)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w62-test-inventory` (from the shared branch) |
| Branch | `codex/v1.8.0-w62-test-inventory` (local only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W62.md` (new). No code, test, status, ledger or ADR change |
| Input | `docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md` sections 9 to 15; `docs/05-MASTER-TEST-PLAN.md`; `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`; `docs/17-V1PLUS-QUALITY-BUDGETS.md`; the content ledger `docs/work/v1.8.0-content-ledger.csv` |

The version document's sections 9 to 13 list checkboxes that the final campaign must close, and
section 15 lists the evidence `docs/releases/v1.8.0/` must hold. None is checked yet. Root needs to
know, for each checkbox, what already exists and what is missing, so that the remaining work can be
dispatched before the final campaign.

## What to do

1. **For each checkbox** in sections 9, 10, 12 and 13, and each evidence item in section 15:
   - what already covers it on the shared branch: test classes, GameTest batches, Python validators,
     hosted workflow steps, native runners, with file and line;
   - whether hosted CI runs it ("v1.8 development regression" or "Repository governance";
     `.github/workflows/`);
   - what is missing, in one sentence;
   - a class:
     - **A:** automatable now. Name the slice: write scope, test, size;
     - **B:** needs the final campaign (dedicated server, real GPU, two clients, reference node).
       Say exactly what the campaign must run;
     - **C:** needs an owner decision. Name it.
2. **Section 9, "每台机器最少 happy/failure/restart tests":** list every machine and multiblock the
   ledger delivers in v1.8, with its happy, failure and restart tests. A restart test may be a native
   two-restart run or a GameTest that saves and reloads. Name the machines that lack any of the three.
3. **Section 9, "全内容 Dedicated Server sidedness":** find what checks that common and server code
   never loads client classes (for example a class-loading or bytecode scan). If nothing does, propose
   the smallest check.
4. **Section 15:** propose the file layout of `docs/releases/v1.8.0/`, which existing generators or
   tests produce each report (for example the recipe graph report under `build/reports/`), and what
   still has no generator.
5. **Propose an ordered list** of at most eight class A slices for Root to dispatch, each with its
   write scope and test.

## Commands

- Read commands, `git grep`, and fast validators. Record each command and result.
- No Gradle run is needed. You may read existing `build/reports/` output in this worktree only if you
  generate it yourself with `gradlew test --tests <one class>`; say so.

## Not in this task

- Any code, test, status, ledger, ADR or workflow change.
