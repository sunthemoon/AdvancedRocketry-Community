# TASK W11b1: D3-A escrow core — coordinator, ledger, barrier (no production admission)

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b1-escrow-core` |
| Branch | `codex/v1.8.0-w11b1-escrow-core` (local only; Root merges) |
| Design | [records/W11A-DESIGN.md](records/W11A-DESIGN.md) (round 2, `a411454c`), the slice row "W11b1" in its section 8 |
| Design review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11a2-review/REVIEW.md`, SHA-256 `e54fd579dd5dee437c16c5d385a5fe5254280e080c78f1c661d2d3231cb68dc2`, verdict APPROVE-WITH-CHANGES |
| Record | `docs/work/v1.8.0-root-queue/records/W11B1.md` |

This is a persistence slice (AGENTS.md section 13). The reviewed design is its design note,
and an independent read-only review of your actual diff follows before Root merges it.

## Goal

Implement exactly the W11b1 row of the design's section 8, with the review's changes C1 to
C6 applied. Production admission stays disabled: no production block, item or command can
create an escrow record.

## Scope

1. **Code:**
   - the coordinator model;
   - the bounded codecs;
   - the per-type checked writer policy (C6: it must accept the transfer journal's existing
     bound of about 140 MiB);
   - the immutable single ledger writer with exact generation acknowledgements (C2);
   - the barrier's own store (C1, C5);
   - the Access Transformer entries (append to `META-INF/accesstransformer.cfg`; no Mixin);
   - the stable Anvil reader, with the endpoint-record size probe (C6);
   - the test-only endpoints (C4).
2. **`RESOURCE_ESCROW` pre-start policy** and the G1 root tests: a future or invalid root
   refuses world start (C3). Ledger creation is lazy, so no production world creates the
   file while admission is disabled.
3. **Tests and evidence**, as the section 8 row lists:
   - A0 tests of the generation, prefix, reason and codec rules;
   - A1 tests of the real store and barrier;
   - S2 cuts K0 to K10, C1 and C2, E1 to E5 and G3, all with the test-only endpoints;
   - the benchmark, with saturated caches, measuring main-thread cost separately from IO;
   - the C1, C2 and C4 tests that the review names.

   If the C4 fault seam cannot inject E1 or E3 without a Mixin, record E1 and E3 as
   diagnostics and say so. Do not add a Mixin.
4. **Record every change C1 to C6 in your record.** For each, give the file and line, and
   the test that covers it.

## Rules for this slice

- Classes stay under 500 lines where possible. A class over 800 lines needs an ADR, so
  split it instead.
- No static world state; the coordinator is server-scoped.
- Every bound in the design is enforced and has a test at the limit.
- Do not edit the risk register or the ADRs. Root adds the startup-scope risk entry and the
  ADR-065 implementation note before the merge (C3). Write their proposed text in your
  record.
- The fault-injection hooks are test-only. They are off unless a test or release-test
  property turns them on, as with the existing `releaseTestHooks`.

## Commands

- The whole unit suite once.
- `runData` if anything generated changes.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- The S2 runs on a native server with the existing release-test harness style. Keep their
  logs and results in your evidence directory, within the 100 MB limit.

## Not in this task

These belong to W11b2 or later:
- rocket entity schema 3;
- the mutation, mount and movement interlocks;
- floors and reservations against real rockets;
- operator commands and settlement;
- the risk-register entry;
- the loader, the linker, and any production admission.

Status files and the ledger are also out of scope.
