# TASK W157F: fix W157-R1 (a strict native check) and the early-shutdown NullPointerException it hid

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W157](TASK-W157-W80-STORAGE.md), W157's
record (`records/W157.md` on the branch) and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W157-review/REVIEW.md`, SHA-256 `bd9845f8…876b`, verdict BLOCK on W157-R1),
with its probes in that folder, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w157` (W157's; it has finished) |
| Branch | `codex/v1.8.0-w157` at `a7be23e4` (local commits only; Root merges) |
| Record | append a "W157F" section to `docs/work/v1.8.0-root-queue/records/W157.md`; do not rewrite W157's sections |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W157F/` (W157's evidence stays as it is) |

## What to fix (each in its own commit, with a test that fails before it)

1. **W157-R1, the native check.** `scripts/run_w157_rejoin_startup.py` must reject unrelated failures. Unused starts
   get the existing strict native scanner. Refused starts get an exact, case-specific expectation list for the
   intentional refusal and the Forge and vanilla lines it must propagate, with logger, severity, message and count.
   Anything else (an ERROR, a FATAL, a project WARN) fails the case. Add committed tests for the runner: a clean log,
   the expected refusal, a duplicate or missing refusal, changed sidecar bytes, a ready marker that appears too early,
   and an injected unrelated ERROR or WARN. Register the runner in `scripts/inventory_escrow_probe_cases.py` only if
   it passes an escrow case property.
2. **The early-shutdown NullPointerException.** `ResearchRuntime.service` and `ResearchRuntime.stopped` (lines 39 and 51
   at the reviewed commit) throw when the server stops before it finished starting, which every refused start does. Fix
   the lifecycle so a stop that follows a refused or failed start is clean, with a unit test. Do not catch or
   whitelist the exception. If another project class throws on the same path, fix it the same way and list it.
3. **The vanilla early-shutdown null-Level exception** in the same logs: if it comes from vanilla code and cannot be
   avoided without a Mixin, list its exact line in the refusal expectations with the reason. Otherwise fix it.
4. **Rerun the native validation** (the six starts) with the strict check, and record the result in the W157F
   section. Do not change W157's sealed evidence.

## Verification

Each fix's tests alone, the runner's Python tests, the whole unit suite, and one complete GameTest with the unchanged
log checker, under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. Check C: and D: above
10 GiB and 6 GiB free memory first. The W157 reviewer re-reviews afterwards.

## Not in this task

W80-release, W80-rejoin, other ResearchRuntime behaviour, ADRs, the risk register, status files and the ledger (Root).
