# TASK W359: draft the plan for the final real-client campaign

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new, read-only session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w359`, branch `codex/v1.8.0-w359`, at the shared head |
| Decision | Root decision R437 ([OWNER-DECISIONS-20261113](records/OWNER-DECISIONS-20261113.md); QUEUE row W359) |
| Deliverable | `docs/work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md` |
| Write scope | That file only. Do not run Minecraft, Gradle or any test. |

## Why

The owner asked for the final real-client campaign to be planned now. It does not start yet, and multiplayer and
performance stay last (2026-11-13). The owner decided on 2026-10-06 that every real-client and reference-node
acceptance item from v1.0 on runs once, in one final campaign on the final candidate. The owner has two independent
premium Minecraft accounts (for online-mode V2), a machine with a second GPU category (for V1), and a Debian 12 or
equivalent reference node (for the four-hour soak and the budgets). Account credentials never pass through an agent:
the owner signs in.

## Sources to read

- `docs/05-MASTER-TEST-PLAN.md`, `docs/06-RELEASE-AND-ACCEPTANCE-GATES.md`, `docs/15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md`,
  `docs/16-POST-1.0-VERSION-ROADMAP.md`, `docs/17-V1PLUS-QUALITY-BUDGETS.md` and `docs/versions/` for v1.0 to v1.8.
- `docs/status/CURRENT_VERSION.md` and `docs/status/GATE_STATUS.md`, if present, and `docs/releases/`.
- The owner-decision records in `docs/work/v1.8.0-root-queue/records/` (OWNER-DECISIONS-*), especially 20261104,
  20261109 and 20261113, and OWNER-BRIEF-20261103.
- [C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md) (V1, V2 and the reference node, and the deferred cargo session and
  benchmark), [INTEGRATED-C1-REPORT-20261109](records/INTEGRATED-C1-REPORT-20261109.md) section 6, and any record that
  defers an item to V1, V2, the real client, a dedicated server or the reference node. Search for these words.
- The existing real-client harness, outside Git: `D:/GitHub/ARCE-Task-Evidence/v18-client-20261010/`, its `PLAN.md`,
  `REPORT.md` and `scripts/`. It starts a Forge 47.4.22 dedicated server and real clients, drives them with scripted
  input and takes screenshots. Read it; do not run it.

## What the plan contains

1. **Inventory.** Every deferred item, one row each: an ID, the source (version, record and row), what is checked, the
   level (V1 one real client, V2 two real clients, dedicated server, reference node, soak), and whether the scripted
   harness can drive it or the owner must play it by hand. Group the rows by version and by system.
2. **Phases, in this order:**
   1. preparation (candidate, worlds, harness, checklists);
   2. single-player V1 on the real GPU;
   3. dedicated server with one real client, including restarts and persistence;
   4. multiplayer V2 with two real clients;
   5. performance: the reference node, the budgets of docs/17 and the four-hour soak.
   Phases 4 and 5 come last.
3. **Per phase:**
   - the environment;
   - the steps the owner performs, and those Root or a scripted harness performs;
   - the entry and exit criteria;
   - how a failure is handled (fix, new candidate, which phases repeat);
   - a time estimate for the owner and for machine time.
4. **Evidence layout.** Folders, file names, what each result row records (PASS, FAIL or NOT_COVERED, with a
   screenshot or log reference), checksums, and the size limits of AGENTS.md section 9.
5. **Prerequisites.** What must be finished first: the open work of R434 to R438, candidate C4, the owner's decisions
   on the five switches, R-044 and X01 to X03. Also the items that need an owner decision before the campaign.
6. **Totals.** The owner's hours per phase and the overall calendar estimate.

Mark every estimate as an estimate. Give the source of every inventory row. Do not claim that anything has passed.

## Rules

- Read-only apart from the deliverable. Commit it on your branch with a message in the style of AGENTS.md section 6.
  Do not push.
- English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files; use code spans for paths
  outside Git.
- The last line of your answer is the path of `records/FINAL-CAMPAIGN-PLAN.md`.
