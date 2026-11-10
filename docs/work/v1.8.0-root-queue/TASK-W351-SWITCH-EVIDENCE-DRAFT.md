# TASK W351: draft the per-switch risk and evidence report for candidate C2

Root: Claude. Date: 2026-11-11. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w351`, branch `codex/v1.8.0-w351`, created by Root from the shared head |
| Decision | Root decision R422 (QUEUE row W351) |
| Owner request | [OWNER-DECISIONS-20261109](records/OWNER-DECISIONS-20261109.md): the owner asked for the bay and the four cargo kinds to be tested together on one candidate JAR, each scenario reported as passed, failed or not covered with the remaining work, then the applicable risks and evidence for each switch separately, and checks of R-042's server-wide effects, R-043's waiting times and R-044's attribution to each device |
| Output | `docs/work/v1.8.0-root-queue/records/C2-SWITCH-REPORT.md` (the draft), committed on the branch |

## Why

Candidate C2 (the hosted build of `ade1538c3`, JAR SHA-256 `1eea72ac...`) is in its 58-family rerun now; Root will
fill in its results. The report's other parts do not depend on that run and can be prepared now: which scenarios each
switch has, what evidence each scenario has had since C1, which C2 family covers it, and which risks apply. W327
drafted a first version at C1 (`D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md`); much has changed
since (W328 to W350).

## What to write

One section per switch, in this order: the satellite bay's deployment admission (`block_variant:loader/1`), then
`ITEM_CARGO_LOAD`, `ITEM_CARGO_UNLOAD`, `FLUID_CARGO_LOAD` and `FLUID_CARGO_UNLOAD`. The fuel loader is out of scope.
For each switch:

1. **Scenarios.** Every row of [W325-MATRIX](records/W325-MATRIX.md) (bay) or [W326-MATRIX](records/W326-MATRIX.md)
   (cargo) that evidences this switch, plus the rows added since C1 (W331's bay rows, W332's R-042 cases, W336's
   timing, saturation and downgrade observations, B6.5 from W342 and W346). For each: its C1 result, what changed
   since (the task and record), the C2 campaign family that covers it (family names from
   `scripts/run_v180_integrated_campaign.py`), and a C2 result cell left as `PENDING` for Root. Rows with no C2
   family stay NOT_COVERED with the reason from their record.
2. **Applicable risks.** The risk register entries (`docs/11-RISK-REGISTER.md`) that apply to this switch, each with
   its scope (one object, a chunk, a Level or the whole server), duration, recovery, and the evidence for it.
3. **Remaining work.** What is still not covered, and what the owner deferred (multiplayer, performance, the
   real-client campaign).

Then one section for the owner's three checks:

- **R-042, server-wide effects:** what holds server-wide when receipts or the ledger are present but not loaded, or
  their presence is unknown, and how it recovers, with W327's findings and W332, W343 and W345's native observations.
- **R-043, waiting times:** each wait, whether it is bounded, estimated or measured, with W336's figures (they come
  from the development host, not the reference node; say so).
- **R-044, attribution per device:** which risks belong to one device and which are shared by every protected device
  (the 256-coordinate pool, the downgrade rewrite).

## Rules

- State only what the records and code show, with the record or file for each claim. Do not mark anything accepted,
  passed for release, or approved; production admission stays off, and the owner decides.
- Use full sentences in plain English. Define an internal code name before using it, or name the file instead.
- Links point only to tracked files; use a code span for files outside the repository.
- No code, status file, QUEUE or risk register change. Read only, apart from the one output file.

## Verification

- `python -B scripts/validate_repository.py --require-approved-identity` passes with the new file.

Commit on the branch; do not push. Root reviews, fills in C2's results and merges.
