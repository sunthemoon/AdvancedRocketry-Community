# TASK W199: the I6 cold-preparation cost and the proof-window tick spikes

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w199` (new; from the shared head named in your prompt, which includes W197) |
| Branch | `codex/v1.8.0-w199` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W199.md` (new; the design note at its top if production code changes) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W199/` |
| Measurement | `docs/work/v1.8.0-root-queue/records/W197.md` and its evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W197/`; the harness `scripts/run_w197_i6_cost.py` |
| Budgets | [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) |

**Root decision R169.** W197 measured I6 at its worst case (64 queued, maximum snapshots, 16 columns per end):
- cold preparation exceeds docs/17's mean 25 ms, P95 50 ms and P99 100 ms per tick; `keepLoaded` alone reached
  239 ms in one tick, with a median of 80 ms;
- ten proof-window ticks exceeded 500 ms, the largest 5,795 ms, of which the named I6 methods took about 1 ms and the
  server's native core the rest.

## What to do

1. **Attribute the cost.** Profile the worst ticks (for example with JFR) on the W197 workload. Say what the 5.8 s
   ticks spend their time on: chunk loading or generation caused by I6's tickets, saving, the GC, or something else,
   and whether the fixture's world (ungenerated terrain) inflates it beyond a real server.
2. **Propose a bounded remedy** in the record before any code, for example a per-tick budget for the columns that
   cold preparation tickets, a cap on concurrent cold preparations, or staggering them across ticks, within the
   existing bounds (at most 16 columns per end) and without weakening any proof or readiness check.
3. **Implement it** if the cause is in this mod's code, with a test that pins the new per-tick bound, then rerun W197's
   measurement on the same workload and compare. If the cost is vanilla's own chunk work that the mod cannot shape
   further, say so with the data and stop.
4. Do not change a docs/17 budget, a proof bound or a timeout.

## Verification

- the new tests alone, failing first; the whole unit suite;
- W197's native measurement before and after, under the heavy-JVM lock;
- the I6 and sealed batches three times.

Root's hosted run on the pushed branch stands in for a complete local GameTest. An independent review follows a
production change.

## Not in this task

The combined terrain matrix (W198), other performance work, ADRs, status files and the ledger.

## Review findings under Root decision R171 (2026-10-29)

The review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W199-review/REVIEW.md` (SHA-256 `90232f45…7453`), accepted the
cold-preparation change and returned BLOCK on the write-back change. Its probe is in `review-tests/` in the same folder.
A fresh session continues on `codex/v1.8.0-w199` from `74aa6acbd`, one commit per finding:

1. **W199-R1.** The candidate-only validation assumes every resident record was checked by its encoder, which `load`
   does not establish. Use the shortcut only with a bounded, lifecycle-correct witness that the residents encode
   canonically (a successful checked encode can supply it, and validated mutations keep it), and keep the exact old
   whole-root validation for an unproven resident set. Do not raise a limit or change the load or quarantine policy.
   Start from the review's probe; cover `validateWriteBack` and `validateNewRecord`.
2. **W199-R2.** Pace `SealedDisassemblyGameTests` with the established real-tick pacing, keeping its 100-tick proof
   deadline, 200-tick retry, 400-tick timeout and assertions, so the two undeclared DEADLINE WARNs no longer occur. Add
   no manifest entry.
3. **W199-R3.** Make the shared escrow test bootstrap's ordering explicit in the test infrastructure, so the package
   selection the review ran passes in any order.

The residual 138.57 ms admission burst and 341.16 ms write-back tick are a separate follow-up (W200). Verification: the
new tests alone, failing first; the whole unit suite; the sealed and I6 batches three times; W197's measurement once
again. A short fresh review follows.
