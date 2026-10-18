# TASK W91N: P1-F3's native timing, with gated observation and a selected-record hold (Root decision R27)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91G-P1-F3-NATIVE-TIMING](TASK-W91G-P1-F3-NATIVE-TIMING.md),
[records/W91.md](records/W91.md) (its W91F and W91G sections, in particular W91G's "Part 2 preflight" blocker) and
[records/W86-DESIGN.md](records/W86-DESIGN.md) section 8 ("S2 and native") first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91n-native` (new; from the W91 branch at W91G's record `1ee010d0`) |
| Branch | `codex/v1.8.0-w91n-native` (local commits only; Root merges it after W91) |
| Inputs | R23 to R26 as the earlier W91 tasks name them; W91G's part 1 (`e23fa320`) |
| Record | `records/W91N.md` (new; do not edit `records/W91.md`, which the W91 branch keeps changing) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/native-w91n-20261018/` |

## Root decision R27 (recorded here)

W91G found that W86 section 8's native run cannot be made with the hooks that exist: `RocketJournalFailurePolicy`
only throws a persistent `IOException` (`ADMISSION`, `ADVANCEMENT`), no hook holds one record's write and releases it
online, and no log gives per-tick decisions, snapshot sequences, writer completions or queue ages. Root decides:

1. **A selected-record hold is authorized** as a new mode of the existing gated `RocketJournalFailurePolicy` (or a
   sibling class in the same package), under the same gate: the policy's property, `EscrowTestEndpoints.HOOKS` and
   `EscrowTestEndpoints.requireEnabled(server)`. In this mode the checked committer, on the writer thread, waits before
   its atomic replacement when the staged generation contains a change to the selected record, until it is released
   online through a gated release-test command or until a bounded maximum (at most 1,200 ticks' worth of time; a
   property may only lower it) passes, and then commits normally. It never throws, never drops a write and never
   changes what is written. Selection and release use the existing release-test command family.
2. **A gated observation probe is authorized** for the native timing: off unless the same gate is set; it only reads
   state at the existing decision points and never changes a decision, loads a chunk or takes a lock. Its memory is
   bounded by fixed caps (records, events per record and ticks), and it writes one JSON file into the server directory
   at server stop or on a gated release-test command, with at most one INFO line at start and one when written. When
   the gate is unset its cost is one constant check per decision point.
3. **Define the hard criterion first.** Before any measurement, write into the record the exact definition of "a tick
   in which an actionable flight did not step" in terms of the code (for example: a tick and a record such that
   `actionable(L)` held when the dispatcher visited the record, the record had due work, and the visit returned before
   its per-record step for a reason that L's own record does not own). The independent reviewer checks this
   definition against the code.
4. **The three whole-root consumers may run in separate sessions**, each under the same eight-flight ordinary
   traffic, if the existing escrow probes cannot run in one session (`EscrowRuntime` takes a single `escrowCase`).
   Where the only existing probe pauses for a process cut (pre-F2), a non-pausing variant in the same gated probe family
   is allowed. State the number of starts.
5. **Not allowed:** any change to a predicate, decision, saved format, acknowledgement order, proof, lease or the replay
   exclusion; any ungated behaviour; a relaxed assertion, limit, budget or timeout. Stop and report if one seems
   needed. If the hard criterion is not 0, keep the evidence and report; do not change a predicate to reach 0.

## What to do

1. **The hold mode and the probe** (R27 items 1 and 2), with unit tests: the default policy is unchanged when the
   properties are unset; the hold waits, releases online and auto-releases at its bound; the probe's caps hold; the
   probe changes no decision (the same scheduling test passes with it on and off).
2. **The runner** `scripts/run_v180_transfer_concurrency.py`, based on `scripts/run_v180_rocket_journal_failures.py`,
   with its unit test. Then the native sessions on a packaged dedicated server, each with one restart check: eight
   ordinary flights staggered and simultaneous, a variant with one selected record's write held, and the consumer
   sessions (R27 item 4). Report every item that W86 section 8 lists for the eight-transfer run, as TASK-W91G part 2
   repeats it, including the hard criterion, the three-write step bound, the continuation latency (p50, p95, max), the
   writer timing, the largest queue, per-tick main-thread time against docs/17 (mean at most 25 ms, P95 at most 50 ms,
   P99 at most 100 ms), writes per cycle against W11b2's 48, and for each consumer its attempts, refusals or stalls,
   completion latency and whether it completed, with the longest interval with no journal mutation. Record the number
   of other Java processes at each timing start; if a main-thread budget misses while others ran, say so.
3. **`RocketJournalBenchmark`, rerun unchanged**, compared with W11b2's figures as the W11b2 record gives them.
4. **Regression on committed source:** the whole unit suite, `python -B scripts/validate_repository.py
   --require-approved-identity`, and one full GameTest with the unchanged log checker (the probe and hold are off
   there). Say whether you add a GameTest batch (378 distinct batch names now; about 384 reshuffles the run order).

## Write scope

`RocketJournalFailurePolicy` (or its new sibling), the new probe class and its call sites at the existing decision
points in `rocket/server` and `rocket/flight/persistence` (read-only observation calls only), the release-test command
registration for hold, release and probe dump, the escrow probe's non-pausing variant if needed, the new runner and its
test, new unit tests, and `records/W91N.md`. Keep the call-site edits small: another worker will merge the shared
branch into the W91 branch and port W92's landed-disassembly code there.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W92F, W90a) also run tests; take the heavy-JVM lock for every GameTest and
native run, and release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free
before each heavy start (C: has about 13 GiB). After each native session, delete its server copy and exported tree and
keep only logs, results and manifests. When your heavy runs end, remove your own lock and your `build/gametest/world`
with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in your
record and final answer at once.

Not in this task: the F3 and N2 kill/restart cuts (a later leaf, which will use this task's hold mode), the merge with the shared branch and W92's port (W91H), R23
item 7's independent review, W90a and W90b, W92F, W94B, production admission, ADRs, the risk register, status files and
the ledger (Root).
