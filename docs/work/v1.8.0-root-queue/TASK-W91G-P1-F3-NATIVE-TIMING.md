# TASK W91G: P1-F3's discard-callback test, the eight-transfer native timing and the writer benchmark

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91F-P1-F3-TESTS](TASK-W91F-P1-F3-TESTS.md),
[records/W91.md](records/W91.md) (its W91E and W91F sections) and [records/W86-DESIGN.md](records/W86-DESIGN.md)
section 8 first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91F's record `469ff503` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Inputs | R23 to R26 as the earlier W91 tasks name them; W91F's regression on `b43522f8` (3,806 unit tests and all 1,199 GameTests passed) |
| Record | `records/W91.md` (extend it) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/native-w91g-20261018/` |

This task has three parts, in this order. Commit after each part. The native F3 and N2 kill/restart cuts are the next
task (W91H), not this one.

## What to do

1. **The A0 discard-callback test that W91F left open** (W86 section 8, A0, W86R-2): the governed launch's discard
   callback releases the pad while another record is dirty, and does not release it while T exists. Exercise the real
   callback (W91F's note: not a copy of its predicate in a fake completion, and not a compiler-generated lambda by
   name). If that needs a small test seam in production code, keep it package-private, give it no behaviour, and say so
   in the record. Run the class alone.
2. **The eight-transfer native timing** (W86 section 8, "Eight-transfer timing"; W86R-6, W86R-7 and W86R-15), with a
   new runner `scripts/run_v180_transfer_concurrency.py` based on `scripts/run_v180_rocket_journal_failures.py`, on a
   packaged dedicated server. Two sessions with one restart check each (4 starts): eight ordinary flights staggered and
   simultaneous, and a variant in which one record's write is held through the existing
   `-Dadvancedrocketrycommunity.rocketJournalFailure` policy. Report every item that section 8 lists:
   - each flight's phase timeline, with the snapshot sequence that first held each change and the writes completed
     between the change and its acknowledgement;
   - **the ticks in which an actionable flight did not step: this must be 0** (the hard criterion). If it is not 0,
     stop, keep the evidence and report; do not change a predicate to reach 0;
   - the step bound (at most three write latencies per step without a failure); a step beyond it is a finding;
   - continuation latency (p50, p95, max), writer timing, the largest queue, and per-tick main-thread time against
     docs/17 (mean at most 25 ms, P95 at most 50 ms, P99 at most 100 ms);
   - writes per cycle against W11b2's baseline of 48 acknowledged writes for eight transfers;
   - the whole-root consumers: during traffic, one governed F1 admission, one pre-F2 cancellation and one settlement
     through the existing escrow release-test probes, each with its attempts, refusals or stalls and completion
     latency, and the longest interval with no journal mutation against the measured barrier and ledger-write
     durations. State plainly whether each consumer completed. Root decides W86's open question 2 on these numbers.
   Other workers compile and test on this machine. Record the number of other Java processes at each timing start. If a
   main-thread budget misses while other Java processes ran, say so; Root decides whether a quiet rerun is needed. Do
   not raise a budget or a timeout.
3. **`RocketJournalBenchmark`, rerun unchanged** as the writer baseline, and compare it with W11b2's figures as the
   W11b2 record gives them.

Then run the unit test of the new runner (if you add one), `python -B scripts/validate_repository.py
--require-approved-identity`, and the GameTest log checker tests if you change an expectation. A full GameTest is
needed only if part 1 changes production code or adds a GameTest; say whether it does, and whether you add a GameTest
batch (378 distinct batch names now; about 384 reshuffles the run order).

## Write scope

The new test and any package-private seam for part 1, the new runner and its test, `scripts/gametest_expected_log.json`
only for a line your new code drives, and the record. No production behaviour change, saved-format change, change to an
acknowledgement order, a proof, a lease, or the replay exclusion; no relaxed assertion, limit, budget or timeout. Stop
and report if one seems needed.

## Commands and not in this task

As TASK-W91C says. Other workers (W94B, W92F, W90a) also run tests; take the heavy-JVM lock for every GameTest and
native run, and release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free
before each heavy start. After each native session, delete its server copy and exported tree and keep only logs,
results and manifests. When your heavy runs end, remove your own lock and your `build/gametest/world` with a plain
`rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in your record and
final answer at once.

Not in this task: the F3 and N2 cuts (W91H), R23 item 7's independent review, W90a and W90b, W92F, W94B, production
admission, ADRs, the risk register, status files and the ledger (Root).
