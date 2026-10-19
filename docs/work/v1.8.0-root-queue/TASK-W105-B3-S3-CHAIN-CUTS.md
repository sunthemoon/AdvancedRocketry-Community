# TASK W105: W11b3's S2 cut family B3-S3 (chains and reconciliation) for the fluid loader

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md), [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md)
section 5 (the S2 leaves table and the paragraph before it on control totals and pinned evidence),
[records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 7 (the cut-C rows), [records/W11B2-DESIGN-2.md](records/W11B2-DESIGN-2.md)
section 5.3 (D2-C0 to D2-C8) and [records/W97.md](records/W97.md) (B3-S1 and B3-S2, their harness, the K5 finding and
W97B) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w105` (new; from the shared branch at `fe25244b`, the W97B merge) |
| Branch | `codex/v1.8.0-w105` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W105.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W105/` |
| Base harness | `scripts/run_v180_loader_cuts.py`, `scripts/loader_cut_oracle.py` and the gated `EscrowLoaderCutProbe` (W97, W97B) |

## What to do

1. **B3-S3, chains and reconciliation** (about 18 cuts, 54 starts), as the design's table gives it:
   - cut-C1 and cut-C2: a two-device chain (two competing real loaders on one rocket), unequal endpoint prefixes (rocket
     prefix 1, the second device's prefix 2), the latest-frontier proof, partial retirement (only a jointly proved
     contiguous prefix retires) and an opposite-direction request (refused);
   - D2-C0 to D2-C8: production endpoint reconciliation on both sides of L1, the barrier proofs (own store, force,
     readback), L2 and the D2-C8 release, each cut as section 5.3 says (before and after replacement, after replacement
     before the callback, and after the callback where it releases a lock);
   - bipropellant on one chain and on one D2-C7 cut; mono-propellant elsewhere.
2. Every cut restarts twice and compares native raw quantities, markers and epochs with the design's control totals
   (A 5,000 to 4,000 mB and bank 0 to 1,000 mB; for the chain, A = B = 4,000 and bank 2,000), not only status strings.
   There is no tentative withdrawal at any cut. Pin the source commit, the JAR and the hooks in each result. A
   difference from the table's expected outcome is a finding to report, not to adjust; W97's K5 shows how to explain a
   coverage gap with evidence instead of weakening a check.
3. Run the harness's tests and `python -B scripts/validate_repository.py --require-approved-identity`; if you change
   Java, also the whole unit suite and one full GameTest with the unchanged log checker, all on committed source.
   Known open items you will meet and should only report: `RocketJournalScheduleMatrixTest` can time out on this
   machine under load (W104), and a local full GameTest under load can log more host-lag notices than the checker's
   ceiling (8 on the shared branch since `497c702b`).

If a cut needs a new stop point or a second-loader fixture, add it only in the existing gated loader and escrow release
probe family (off unless the release-test gate and its property are set; it observes or pauses and never changes a
decision, a saved format, an acknowledgement order, a proof or a lease). No new failure injection is authorized here;
storage failures are B3-S4.

## Write scope

Harness extensions and their tests, gated stop points and fixtures in the existing loader and escrow release probes,
new unit tests for them, and the record. No production behaviour change; no relaxed assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W98, W101, W100F) and Root's W102 verification also run heavy JVMs; take the
heavy-JVM lock for every native run and GameTest and release it between cuts when another worker waits. Check that C:
and D: have more than 10 GiB free before each heavy start (C: has about 13 GiB). Delete each cut's server copy and
exported tree afterwards; keep only logs, results and manifests. When your heavy runs end, remove your own lock and any
`build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses,
write the paths in the record and final answer at once.

Not in this task: B3-S4 to B3-S6, B3-B, B3-V, cargo, P1-N3, production admission, ADRs, the risk register, status
files and the ledger (Root).
