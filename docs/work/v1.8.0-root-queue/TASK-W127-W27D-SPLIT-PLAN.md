# TASK W127: W27d's split plan, the bay deployment protocol in verifiable sub-leaves (plan only)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. **This task is a plan
only: change no Java, script or test file.**

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w127` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w127` (local commits only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W127-PLAN.md` (the plan) and `records/W127.md` (a short record) |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md), frozen: section 4 (the sequence, with 4.1 coordinator-owned altitude recovery), section 8 and 8.1 (the interlocks and the W11b2 hook), the W27d row of section 9 with its F5 matrix and the S2 cuts B1 to B18, and section 10's risk entry C and residual D |
| Merged prerequisites | W27b (the bay block, [records/W27B.md](records/W27B.md)), W27c1 (the receipt store, [records/W27C1.md](records/W27C1.md)), W27c2 (the registry proof, [records/W119.md](records/W119.md)), W27c3 (the transaction journal, [records/W123.md](records/W123.md)), and W11b2's section 8.1 hook (`RocketTransferBayRecoveryGate`, the claimed-transfer set, `RocketTransferBayGameTests`) |

## Why a plan first

The W27d row covers section 4.1's protocol behind the 8.1 hook, the TRANSIT wait, D0 to D7, the journal rewrite and its
barriers, duplicate-absence proofs, narrow permits and restart dispatch, status, messages and audit, A0 for every D1 to
D7 prefix, A1 with real rockets, data and solar, benchmarks and native S2 for all of B1 to B18. That is too large for one
reviewable slice. Root splits it, as it split C-E.

## What the plan must contain (two to three pages)

1. **An inventory** of what the merged code already provides for each part of section 4 and the W27d row (with file and
   line), and what is missing.
2. **Two to four sub-leaves** (W27d1, W27d2, ...), each a reviewable persistence slice: its exact scope (which D steps,
   which permits, which recovery paths), what it leaves disabled, its dependencies on the others, its A0 and A1 tests,
   which of B1 to B18 it can run natively, and an honest size estimate (classes, lines, GameTest and native runs). Every
   sub-leaf keeps deployment admission disabled; W27e enables it after evidence.
3. **The order** and what each sub-leaf proves on its own, so that a merged sub-leaf never leaves a world in a state the
   next sub-leaf must repair.
4. **Risks and open questions** for Root: where section 4 is ambiguous against the merged code, what needs a Root or
   owner decision, and what section 10's risk entries C and D require Root to register before which sub-leaf.

Verify claims against the code at the commit you start from. You may run existing unit tests read-only; run no
GameTest, native server or `runData`, and take no heavy-JVM lock. Check that 6 GiB of memory is free before any Gradle
run.

## After the plan

Root reviews it, decides the split and queues the sub-leaves. Not in this task: any code, W27e, ADRs, the risk register,
status files and the ledger (Root).
