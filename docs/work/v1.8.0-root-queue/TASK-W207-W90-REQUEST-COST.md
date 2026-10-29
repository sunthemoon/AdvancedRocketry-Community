# TASK W207: the W90 request costs W206 measured over docs/17

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w207` (new; from W206's branch `codex/v1.8.0-w206` at `24a076f9c`, whose harness you use; Root merges W206 first) |
| Branch | `codex/v1.8.0-w207` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W207.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W207/` |
| Background | `docs/work/v1.8.0-root-queue/records/W206.md`; the W206 review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W206-review/REVIEW.md`, finding W206-R1 and the section "Attribution and remedy guidance"; [records/W90-DESIGN.md](records/W90-DESIGN.md); [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md); `records/W199.md` as a model |

**Root decision R177.** W206 measured, through the real launch entry: new-terrain request ticks of 509 to 933 ms,
of which synchronous pad selection (`RocketLandingPadSelector` loading chunks and choosing by heightmap before the
asynchronous warm-up) is 480 to 900 ms; a 542 ms restart-cut request; a 2,081 ms capacity burst whose refused ninth
allocation alone took 255 ms; and a checkpoint-bearing P99 of 125 to 133 ms, set in every flight by the fresh relaunch
request with its synchronous F1 checkpoints.

## What to do

1. **Attribute first** with JFR on the slow request and relaunch ticks, through W206's harness and entry points, and
   calibrate the observer: buffer the tick rows and export them after measurement (W206-R3), so observer cost is known.
2. **Design note before code.** A bounded remedy for each cause in the mod: for example, pad selection that acquires
   terrain through the existing asynchronous readiness path or a per-tick bounded cursor instead of synchronous loads,
   and a cheap refusal before selection when no warm-up capacity is left, keeping the behaviour for an already-ready
   destination. Say what a player sees while a request waits, and show that no check is skipped, no chunk is forced
   beyond today's, no reservation or pad claim gains authority early, and no cap, lifetime, budget or timeout changes.
3. **Implement**, each change in its own commit with a test that fails first.
4. **Measure** before and after with W206's harness, under the heavy-JVM lock, against docs/17, and add W206-R1's
   fresh request after the process restart (no destination support tickets).

A cause outside the mod (vanilla generation itself) is reported with its share, not hidden.

## Verification

The new tests alone, failing first; the whole unit suite; the warm-up, pad and launch GameTest batches three times;
W206's measurement before and after. Root's hosted run on the pushed branch stands in for a complete local GameTest.
An independent review follows.

W204-fix runs native cases under the same heavy-JVM lock; wait for it, never remove it.

## Not in this task

Caps, lifetimes, budgets, timeouts, the escrow chain's persistence formats, pad claim rules (W204-fix), ADRs, status
files and the ledger.
