# TASK W11b1, continuation 01: finish the escrow core's open items

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B1](TASK-W11B1-ESCROW-CORE.md) first. You are a new session. Your context is
the W11b1 record, [records/W11B1.md](records/W11B1.md) (at `6a1efe15`): read it in full.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b1-escrow-core` (continue on top of `6a1efe15`) |
| Branch | `codex/v1.8.0-w11b1-escrow-core` (local only; Root merges) |
| Record | Add a "Continuation 01" section to `records/W11B1.md`. Keep the existing text. |

## Root's decisions on the record's open items

| Item | Decision |
|---|---|
| 1 Benchmark | **Do it.** Saturate the terrain and entity region caches (256 regions each). Measure main-thread cost separately from IO latency, with autosave and competing operations, for both `sync-chunk-writes` settings, against the unchanged `docs/17` budgets. Report p50, p95 and p99. If a budget fails, lower the cadence as the design says. Never drop a force or a readback, and never raise a budget. Record the machine, and say that it is not the docs/17 reference machine. |
| 2 E5 | **Do it.** Add the automatic fair reconciliation scheduler and the persisted three-failure STALLED recovery, following the design's reason-specific transition table. Then run the E5 matrix: blocked and unloaded work, recovery, and fairness across multiple restarts. |
| 3 E1 and E3 | **No code.** They stay diagnostic, not PASS. This is the residual that the design and Root decision 1 accepted: no Mixin, no extra seam. Root registers it. |
| 4 At-limit tests | **Do it.** Add explicit at-limit unit tests for the remaining capture and scheduling bounds, and for the count and byte combinations of each codec. Native at-limit runs are not required. |
| 5 Same-slot proof | **Do it.** Add joint proof and retirement for several endpoints in one terrain slot, and retirement of only a per-endpoint prefix (review C5 and L2). Add a native chain case in which the two operations share a source chunk, with partial live-marker retirement. |
| 6 Residual | **No code.** Root registers it with item 3. |
| 7 Review | Root dispatches the independent actual-diff review after this continuation. |
| 8 Cleanup | **Done by Root.** The generated `build/gametest` copy was removed on 2026-10-13. |

For cleanup, use plain commands that end by themselves. If the tool policy rejects a
deletion, record it and continue. Do not try another deletion method.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts you add or change, plus a final rerun of the whole native selection on
  the final source. Keep the evidence within the 100 MB limit.

## Not in this task

- Production admission.
- The W11b2 scope.
- The risk register and ADRs (Root).
- Status files and the ledger.
