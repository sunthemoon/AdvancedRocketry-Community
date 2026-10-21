# TASK W129: design note and proposed ADR, off-thread checked writers for the satellite registry and the transaction journal

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [records/W128.md](records/W128.md) (the
benchmarks and the stop), `docs/17-V1PLUS-QUALITY-BUDGETS.md`, ADR-050 (section 2 and its W27 implementation note),
[records/W119.md](records/W119.md) (the registry's epoch rule) and [records/W123.md](records/W123.md) (the transaction
journal's checked acknowledgement), first. You are a new session. **This task is a design note and a proposed ADR only:
change no Java, script or test file.**

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w129` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w129` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W129-DESIGN.md`, a proposed ADR under `docs/decisions/` (status **Proposed**, the next free number), and `records/W129.md` |

## Why (Root decision R51)

W128 measured, on the development host at the bounds: the satellite registry's checkpoint proving 32 registrations at a
16.7 MB root, P95 448 ms and P99 893 ms; its coalesced flush and autosave about 460 ms; the rocket transaction journal's
checkpoint at its 64 MiB bound with 256 records, about 4.1 s (one record: 26 ms). CPU time is close to wall time: the
cost is server-thread encoding. docs/17's budgets are 50 and 100 ms. Under R49 (Q6) W27d2 cannot start until an ADR for
an off-thread registry writer, and D0 cannot request a 4-second synchronous checkpoint. These costs also exist today in
ordinary autosave at those sizes. The bay chain waits for this design.

## What the note and the ADR must contain

1. **What stays on the server thread**: capturing an immutable snapshot (the registry's root and session sequence, the
   journal's records), at what cost at the bounds (measure with W128's benchmark classes or reason from them), and how
   that capture avoids re-encoding. If the capture itself misses the budget, say what bounds it (copy-on-write roots,
   incremental structures) or which bound must change.
2. **What moves off the thread**: encoding, the NC1 digest, staging, force, readback, replacement and the directory
   force, reusing `RocketJournalWriter`'s and `EscrowLedgerWriter`'s checked off-thread pattern (one writer per root, one
   in-flight candidate, exact acknowledgement on the owner thread, definite failure and UNKNOWN, shutdown drain).
3. **The acknowledgement semantics kept exactly**: W119's epoch rule (E advances once to the carried epoch only when a
   capture commits with no mutation since; UNKNOWN reserves its epoch; no delayed readback advances E) and W123's
   session-local acknowledgement and B14 absence query, now with an asynchronous completion; how vanilla autosave and
   `save(File)` interact with an in-flight write; the stop and crash windows.
4. **Effects**: on the bay protocol (D0's checkpoint becomes a request answered later), on satellites and missions
   (anything that today assumes the save finished when `save` returns), and on R-039 and R-040.
5. **Tests and benchmarks** that would prove the implementation: the server-thread cost of each operation at the bounds
   within docs/17 P95 and P99, the acknowledgement matrices unchanged, and the crash cuts.

Verify claims against the code at the commit you start from. You may run existing unit tests and W128's benchmark
classes read-only (run a benchmark only under the heavy-JVM lock, released in the `finally` of the same detached script,
WORKER-RULES); run no GameTest, native server or `runData`. Check that 6 GiB of memory is free before any Gradle run.

## After the note

Root has it reviewed by an independent read-only session; the ADR's acceptance and the implementation follow as their
own steps. Not in this task: any code, W27d, ADR acceptance, the risk register, status files and the ledger (Root).
