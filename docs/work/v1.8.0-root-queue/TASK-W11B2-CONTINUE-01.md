# TASK W11b2, continuation 01: finish the rocket integration

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is
the W11b2 record, [records/W11B2.md](records/W11B2.md) (at `9502a056`): read it in full.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `9502a056`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 01" section to `records/W11B2.md`. Keep the existing text |

## Finish the incomplete items in the record

All of them are under the original task's contract and the design review's corrections R1 to
R6:
1. **Recovery fences (R2).** FENCED classification stays during the bounded read-only
   re-evaluation. The re-evaluation uses only queries over already-loaded state: no ticketing
   readiness predicate. Classify records before any readiness check that adds tickets. Bound
   the retries per tick and per transfer. Run the R2 test matrix: zero tickets, zero loads, and
   an unrelated warp that stays available.
2. **Floors and reservations.** Reserve at admission, rebuild after restart, collect at
   startup, and report used and reserved slots.
3. **Operator settlement and tombstones.**
4. **The relocation and startup policy:**
   - `/tp`, dimension travel and `/ride` are refused, or detected and turned into a repair
     transition;
   - the managed pre-start policy.
5. **Native S1 and S2.** Run the cuts the design lists (R1 to R3, O1 to O4 and the O-veto, G1
   to G3, and the repeated K and C cuts) on the final source, through the existing harness,
   with source and jar pins.
6. **Journal benchmarks**, as the record lists.

The record says "credited-world recovery remains unsafe". Close that, or state exactly why it
stays unsafe and what blocks W11b3.

## W11b1 fixes

W11b1F is fixing four latent defects in the W11b1 core on branch
`codex/v1.8.0-w11b1-escrow-core`:
- a writer stuck after an invalidated ambiguous L2 replacement;
- retirement without its operation;
- drain time that grows with uptime;
- quadratic entity capture.

When Root tells you, or when that branch has a commit titled with "W11b1F" in its record,
merge it into this branch and adapt. Do not fix those four items yourself.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts in item 5, with evidence under 100 MB.

## Not in this task

- `fluid_fuel_loader`, the linker and production admission (W11b3).
- ADRs and the risk register (Root).
- Status files and the ledger.
