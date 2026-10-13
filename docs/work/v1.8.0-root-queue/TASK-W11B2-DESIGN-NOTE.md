# TASK W11b2-design: design note closing P1 to P5 before W11b2 code

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first. This is
design work only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-design` |
| Branch | `codex/v1.8.0-w11b2-design` (local only; Root merges) |
| Base design | [records/W11A-DESIGN.md](records/W11A-DESIGN.md) (round 2), sections 4 and 5, and the W11b2 row of section 8 |
| Round-2 review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11a2-review/REVIEW.md`, SHA-256 `e54fd579dd5dee437c16c5d385a5fe5254280e080c78f1c661d2d3231cb68dc2`. Read P1 to P5, H2, H3, M3, M4 and L3 to L6 |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN.md` (two to four pages) and a short record `records/W11B2-DESIGN-RECORD.md` |

## Why

The round-2 review approved W11b1, which is being implemented. It also found that W11b2's
scope, the rocket integration, is not ready. AGENTS.md section 13 requires a design note and
one read-only review before any W11b2 code. This note must close the review's entry
conditions.

## Close each of these, citing code

1. **P1 (H2): a checked-durable `ROCKET_TRANSFERS` state.**
   - Specify the mechanism that gives admission a checked-durable transfer-journal state,
     including when the world has no journal file.
   - It may reuse W11b1's per-type checked writer policy (C6, about 140 MiB). Say exactly
     how the transfer journal adopts it, and what that changes for existing journal writes.
2. **P2 (H3): fenced transfer records.**
   - Give the ADR-044 classification of a transfer record that the fence refuses to recover.
   - Give an online settlement for a conflict on a retired floor.
   - Give bounded retries without tickets.
   - A fenced record must not block every station warp on the server.
3. **P3 (M3): assembly.**
   - Admission again requires the `RocketManager` assembly-journal check.
   - Fence assembly and disassembly transaction recovery
     (`RocketTransactionRecoveryService`), so that it cannot discard a credited rocket.
4. **P4 (M4): guard layers.**
   - The coordinator check goes into `RocketTransferService.resourceMutationAllowed` and
     covers launch.
   - List every refused entry point, and give the A1 interlock-matrix test that covers them.
5. **P5: the Lows against W11b2** (L3 to L6), including the additions to the risk entry.
6. **The rest of the W11b2 row** (section 8):
   - rocket schema-3 migration;
   - mount and movement interlocks;
   - floors and reservations;
   - operator settlement and tombstones;
   - the managed pre-start policy hand-off from W11b1;
   - the risk-entry text and the ADR-065 implementation-note text, which Root adds.

   For each, state what W11b2 implements, its tests and its S2 cuts.

Check every vanilla claim with `javap` against the mapped Forge jar, and cite the class
and method.

## Not in this task

- Code and tests.
- ADR and risk-register edits. Write the proposed text in your note instead.
- Status files and the ledger.
