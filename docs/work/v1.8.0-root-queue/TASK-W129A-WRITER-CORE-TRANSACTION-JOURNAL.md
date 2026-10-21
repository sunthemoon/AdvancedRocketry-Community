# TASK W129a: the off-thread writer core and the rocket transaction journal writer (ADR-074, slice 1)

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), the design note
[records/W129-DESIGN.md](records/W129-DESIGN.md) revision 2 (sections 0 to 3, 4.4 to 4.6, 5.3, 5.4, 6 and section 7
item 1), the proposed [ADR-074](../../decisions/ADR-074-OFF-THREAD-REGISTRY-AND-TRANSACTION-WRITERS.md), the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W129R-review/REVIEW.md` ("Conditions for Root's acceptance and
implementation"), [records/W123.md](records/W123.md) (the transaction journal's acknowledgement and B14) and
[records/W128.md](records/W128.md) (the synchronous baseline and the stop rule) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w129a` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w129a` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W129a.md` (new; the one-page plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129a/` |

**Status of the decision.** ADR-074 is PROPOSED. The owner has not accepted it yet. Development of this slice is
allowed now; **its merge waits for the owner's acceptance** and for the B2 and B3a gates below. Do not change the ADR's
status or text; if the implementation shows the ADR or the note is wrong, stop that item and report it in the record.

This is a persistence slice: commit the one-page plan at the top of the record before any code; an independent
read-only review follows before merge.

## What to do, each in its own commit with a test that fails before the change where a test can show it

1. **The shared writer core** (section 3) with an executor seam: at most one capture in flight; a request while in
   flight leads to a fresh capture at the next start; publication only on the owner thread, a foreign completion
   refused; an `Error` on the writer thread recorded as UNKNOWN; the 10-tick cadence and the 10/200 backoff on every
   start path, which repeated requests cannot reset (Q5).
2. **The transaction journal's capture, encoder and publication** (sections 2.2, 2.3 and 4.4). The encoder of a
   capture is byte-identical to today's synchronous `save(CompoundTag)` on fixtures, including W128's 256-record
   fixture at the 64 MiB bound.
3. **WRITE_PENDING in B14** (section 4.4): non-operational status and matching RAM records are checked before proof,
   then the current acknowledgement, then WRITE_PENDING; neither a request nor a stale completion proves absence;
   the W123 rules (session-local exact acknowledgement, COMMITTED outcome, exact current sequence, sticky UNKNOWN,
   NOT_COMMITTED keeping old evidence without authority) unchanged.
4. **Autosave, `/save-all` and `save(File)`** (section 4.5) and **stop, crash and kill** (section 4.6) for the journal:
   the final-target drain with one three-attempt budget that includes the in-flight attempt; shutdown suppresses
   effects for both flag combinations; close and abort join before world ownership is released, including an
   interrupted join; abort publishes nothing; a request after close is refused.
5. **Logging** (section 5.3, R-040): one ERROR at the first failed or unknown outcome of a streak and one INFO when a
   later write commits; one ERROR per unexpected writer `Throwable`; one WARN when a stop drain gives up, with the
   target and committed sequences and the reason. Each line a GameTest drives gets its exact manifest entry.

The registry writer (W129b), the barrier callers (W129c), the bay (W27d) and the Q6 ground preflight are not in this
task. The existing transfer writer stays unchanged.

## Tests

- A0, section 6.2's writer-core list in full, for the journal root: capture s, a mutation at s + 1, an older COMMITTED,
  UNKNOWN or NOT_COMMITTED, then a fresh final capture; an older success leaves the journal dirty and cannot finish the
  drain; budget exhaustion reports the failure and the last committed sequence without resetting or claiming success;
  the stalled-IO and interrupted-join seams, each released by the test itself, not by production timeouts.
- **The W123 matrix unchanged**: `RocketTransactionProofTest` keeps every case and assertion, awaited where the
  publication is now asynchronous; the new cases of section 6.2 (WRITE_PENDING masking a stale NOT_COMMITTED or
  UNKNOWN, a mutation during the flight keeping the journal dirty, sticky UNKNOWN across a later pre-replacement
  failure, a refused request on an unreadable or blocked journal).
- A1: `RocketTransactionProofGameTests` with awaited publication; autosave and `/save-all` make requests that publish
  on later ticks.
- Native cuts (section 6.4), two restarts each: T1 (a D0-style request in flight, then kill), T2 (journal staged), T3
  (moved before publication), and the journal's R6 and R7 variants (normal stop with a stale capture before logout
  mutations; a thrown tick exception with running=true and stopped=true; interrupted teardown kept distinct from a
  successful drain).

## The B2 and B3a gates (section 6.1; required before merge)

Standalone `main` classes under `src/test`, Xmx4G, under the heavy-JVM lock, on W128's fixtures; W128's synchronous
classes stay as the baseline. **B2**: the request and publication with one record and with 256 records at the 64 MiB
bound, server-thread time against docs/17 (P95 at most 50 ms, P99 at most 100 ms), writer time and peak heap. **B3a**:
at least 50 rounds of the new journal writer overlapping the existing transfer writer at their bounds, with resident
registry, journal and other live roots and existing writer traffic, on real ticks or the monotonic 50 ms surrogate the
note describes; peak heap and RSS, retained graphs, allocation, GC pauses and stalls, missed deadlines, writer CPU and
latency, success and failure/readback paths. Report hardware, JVM flags, collector and fixture composition.

**Stop rule** (W128's): if any figure misses, or a measurement cannot be made, stop and report; do not add a permit,
change a budget or shrink a fixture yourself. Root chooses the remedy (for example the fair IO-side permit of Q8) and
the changed configuration is measured again.

## Constraints

No saved field, schema, filename, format-epoch literal, digest algorithm or bound changes; no existing assertion is
relaxed or removed; `RocketTransferService` must not grow; new classes stay under 500 lines. The risk-register entries
of section 5.3 are Root's: list in the record exactly which new protections, holds and log lines your code introduces,
so Root can register them before the merge. Stop and report if any constraint seems to need breaking.

## Verification

Each new or changed test class alone, then the whole unit suite. Then, on committed source, under the heavy-JVM lock
with the atomic pattern and the hand-off rule in WORKER-RULES, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it: one full GameTest with the unchanged
log checker (plus your entries), B2 and B3a, and the native cuts. If the checker refuses only the host-overload warning
count, record the count and the number of agent sessions running. Check C: and D: above 10 GiB and that 6 GiB of memory
is free before each Gradle run, benchmark or native start.

## Not in this task

W129b, W129c, W27d, the Q6 ground-preflight leaf, ADR-074's status, other ADRs, the risk register, status files and the
ledger (Root).
