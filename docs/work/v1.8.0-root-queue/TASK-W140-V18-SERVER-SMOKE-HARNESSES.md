# TASK W140: the dedicated-server smoke and the v1.0 transaction forced-stop harness cannot run against v1.8 builds

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) and the W129aF section of `records/W129a.md` on
branch `codex/v1.8.0-w129a` (item 5) first. You are a new session. This is a parallel bug: take it in a free slot that
does not delay progress tasks.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w140` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w140` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W140.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W140/` |

## The defect

W129aF tried to run `scripts/run_v100_transaction_forced_stop.py` on a v1.8 build and found that neither it nor its
baseline can run:

- `scripts/run_dedicated_server_smoke.py` treats every project WARN as blocking, and a fresh v1.8 server logs
  `ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED` once per start (since `6edef9da`), so the
  smoke exits 1 and writes no `summary.json`;
- the v1.0 harness applies the same blocking-WARN check after every clean stop;
- the v1.0 harness looks for `root_schema=2` in the operator report, which `37e4be62` renamed to `default_root_schema=`,
  so it never matches a v1.8 server.

## What to do

1. Give the smoke and the v1.0 harness an explicit, exact list of expected v1.8 startup lines (logger, level, exact
   message pattern and count, with the commit and reason that introduced each), as the GameTest log manifest does, and
   keep every other project WARN or ERROR blocking. Do not accept WARNs by prefix or by level.
2. Match the operator report's current field (`default_root_schema=`) while keeping the check as strict as before for
   the schema value.
3. Run the smoke to produce its baseline summary, then the v1.0 transaction forced-stop harness, on committed source, and
   report each case. If a case fails for a reason other than these two, stop and report it.
4. Search the other `scripts/run_v1*.py` native harnesses for the same two assumptions and list them in the record (fix
   them only if the change is the same mechanical update; otherwise list them for Root).

No assertion relaxed beyond the exact expected lines; Python unit tests for the new allowance (an unexpected WARN still
blocks, the expected line beyond its count blocks, the renamed field matches only the right value).

## Verification

The Python tests, then the smoke and the harness under the heavy-JVM lock with the atomic pattern and the hand-off rule
in WORKER-RULES, releasing it and deleting every server copy in the `finally` of the same detached script that took it.
Check C: and D: above 10 GiB and that 6 GiB of memory is free before each native start.

## Not in this task

W129a's merge, production code, status files and the ledger (Root).
