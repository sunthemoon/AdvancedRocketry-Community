# TASK W222: the BI O-veto attempt's sixteen extra guarded save errors

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w222` (new; from the shared head named in your prompt, which contains W211, W220 and W221) |
| Branch | `codex/v1.8.0-w222` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W222.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W222/`; W211's in `D:/GitHub/ARCE-Task-Evidence/root-queue/W211/` (read only), especially `r199/veto-renewed/` |
| Background | `records/W211.md`, section "R199 veto results and abandon design" (line 1737 onward) and its later mentions of the BI O-veto residual; the S6 chain veto probe (`EscrowLoaderS6ChainProbe`) and `scripts/loader_s6_chain.py` |

**Root decision R213.** W211's first bipropellant O-veto attempt failed its log check: on both restarts, vanilla
`ChunkMap` made sixteen guarded save attempts (sixteen EventBus and ChunkMap refusal pairs) before the selected
own-store attempt, where the log oracle expects one ERROR; its raw images pass the held-state oracle, and the second
attempt passed. The residual is open in CURRENT_VERSION.

## What to do

1. **Diagnose from the retained logs and code:** why sixteen guarded saves happen before the selected one on that
   attempt (which chunks or saves, from what trigger: a periodic autosave, the restart's chunk unloading, the veto's
   own guard on more chunks than the oracle assumes), whether they can happen in any run of this row, and whether the
   product's behaviour is right (each refusal guarded, nothing written that should not be).
2. **A test-side cause** (the oracle's single-ERROR expectation is narrower than the product's correct behaviour, or
   the fixture leaves an extra save window): correct the oracle or fixture so that it states exactly which guarded
   refusals are allowed and still rejects an unguarded write, an unexpected ERROR and any write to the guarded store,
   with tests that fail first on the retained log; replay the failed attempt's logs and images through the correction.
   The failed attempt stays FAILED.
3. **A production cause:** describe it with the evidence and stop for Root; no production change.
4. If the correction needs native confirmation, run the BI O-veto row once (two attempts at most, two restarts each)
   under the heavy-JVM lock on a JAR built from committed source.

## Verification

The changed tests alone, failing first; the loader S6 and chain Python tests; `EscrowLoaderS6ChainProbeTest` and the
`resourceescrow` package; the replay; the native row if run. Root's hosted run stands in for a complete GameTest.

## Not in this task

Other rows, production changes, budgets, timeouts, ADRs, status files and the ledger.
