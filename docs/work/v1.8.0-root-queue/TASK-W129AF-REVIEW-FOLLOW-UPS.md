# TASK W129aF: W129a's review follow-ups (W129a-R1 to R3) under Root decision R61

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W129a](TASK-W129A-WRITER-CORE-TRANSACTION-JOURNAL.md),
W129a's record (`docs/work/v1.8.0-root-queue/records/W129a.md` on your branch), the design note's section 6.1
([records/W129-DESIGN.md](records/W129-DESIGN.md)) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W129a-review/REVIEW.md` (SHA-256 `d2da5ec4…dbec70`) first. You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w129a`, branch `codex/v1.8.0-w129a` at `66b27c04`; continue on that branch |
| Record | append a "W129aF" section to `docs/work/v1.8.0-root-queue/records/W129a.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129aF/` (keep W129a's evidence unchanged) |

## Root decision R61 (2026-10-22)

W129a-review blocks the merge on the measurement contract, not on the code: the writer core, the capture, the moved
codec (exact text equality), W123's rules and B14, the stop path, the native cuts and the logging all hold, nothing was
weakened, and the merge preview with the shared head has no conflict. The review agrees with R60 that no stated number
missed. So:

1. **First, merge the shared head into your branch** (`git merge --no-ff` the shared commit named in your prompt; the
   review's preview at `74c17a86` had no conflict, and the manifest must keep both sides' entries and W129a's pinned
   ERROR total), and commit it.
2. **W129a-R1, the complete B3a workload.** Section 6.1 requires B3a's overlap to run with the resident registry,
   journal **and other live roots, plus existing writer traffic**. Add representative, explicitly sized, nonempty
   resident roots for the escrow ledger and the bay receipt store, with their existing writers' paced mutation, write
   and publication traffic during the rounds, beside the bound-sized transaction and transfer journals you already
   have. Document the composition and operations. Then rerun B3a (at least 50 rounds, success and failure/readback
   paths, the monotonic 50 ms surrogate) and the transfer-only comparison under the unchanged budgets and Xmx4G. W128's
   stop rule applies: if a figure misses or a measurement cannot be made, stop and report; add no permit and change no
   budget yourself.
3. **W129a-R2, a fail-closed verdict.** The RSS gate must require a MEASURED, in-budget result; FAILED, TIMEOUT and
   UNAVAILABLE statuses produce a stop-rule verdict with the reason. Add small verdict tests for each unavailable or
   failed status and for the measured boundary.
4. **W129a-R3:** add a correction file for `driver.log`'s recorded hash (the complete file's SHA-256 and why the old
   value covered only its first 29 lines), keeping the original evidence unchanged; hash logs only after their producers
   exit.
5. **The v1.0 transaction forced-stop harness** (`run_v100_transaction_forced_stop.py`), which neither W129a nor the
   review reran: run it on your committed source if its baseline summary can be produced from the repository; if it
   cannot, say exactly what is missing.

No saved format, schema, hash, bound, budget or timeout changes; no existing assertion is relaxed. ADR-074 stays
PROPOSED; its acceptance is the owner's decision and the merge waits for it.

## Verification

Each new or changed test class alone, then the whole unit suite; one full GameTest with the unchanged log checker on the
merged source; then B3a, its comparison and the v1.0 harness, all under the heavy-JVM lock with the atomic pattern and
the hand-off rule in WORKER-RULES, releasing it in the `finally` of the same detached script that took it and deleting
every server copy and `build/gametest/world`. Check C: and D: above 10 GiB and that 6 GiB of memory is free before each
Gradle run, benchmark or native start.

## Not in this task

W129b, W129c, W27d, the Q6 ground preflight, ADR-074's status, the risk register, status files and the ledger (Root).
