# TASK W107: W11b3's S2 cut families B3-S4 (storage failures) and B3-S5 (recovery) for the fluid loader

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md), [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md)
section 5 (the S2 leaves table, the paragraph before it on control totals and pinned evidence, and the B3-S4 and B3-S5
rows), [records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 7 (the cut-E and cut-R rows), ADR-065's R-032 boundary
on unobservable native failures, and [records/W97.md](records/W97.md) and `records/W105.md` (B3-S1 to B3-S3, the harness,
W97's K5 finding and W97B) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w107` (new; from the shared branch at the commit Root names in the launch prompt, which has W105 merged) |
| Branch | `codex/v1.8.0-w107` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W107.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W107/` |
| Base harness | `scripts/run_v180_loader_cuts.py`, `scripts/loader_cut_oracle.py` and the gated `EscrowLoaderCutProbe` (W97, W97B, W105) |

## What to do

0. **First, W105's two B3-S3 coverage gaps** (added by Root on 2026-10-20 after W105 merged as `bd1c78b9`; see
   `records/W105.md`, "C1 coverage gap" and "D2-C4-before coverage gap"). Both cuts recovered to the control totals
   on both restarts, but their stopped snapshots were not the required persisted states:
   - C1: the durable unequal prefix (both loader tanks at 4,000 with their markers, the rocket bank at 1,000 at
     epoch 1, both L1 records). At the pause, disk still held the state before both fills;
   - D2-C4-before: the one-sided stored snapshot (tank 5,000 at epoch 0 without a marker, bank 1,000 at epoch 1 with
     its marker, one L1 record). Both stores were already AFTER.

   Find which save makes each native store durable relative to the pause, then add a gated stop point after the
   exact durable store if one is needed, under the rule below. Rerun those two cuts with two restarts each. If you
   change the chain checkpoint, rerun C2 and the three C2_PARTIAL cuts too; if you change the C4 selection, rerun
   D2_C4_AFTER. If the existing driver cannot produce a persisted state without a new failure injection or a changed
   save order, report it as a coverage finding with evidence, as W97's K5 did. Never change the oracle to accept the
   observed snapshot.
1. **B3-S4, storage failures** (about 11 cuts, 33 starts; real loader, mono-propellant):
   - cut-E1: an observed own-store failure and an observed superseding-store failure;
   - cut-E2: a newer save, a newer generation or an unstable header, an external `.mcc`, and decoded bounds;
   - cut-E4: definite and UNKNOWN outcomes of L1 and of L2, an overlapping autosave, and a stale callback.
   ADR-065's and R-032's boundary holds: an unobservable native failure is a diagnostic residual, **not** a PASS
   guarantee, and an observable failure must block retirement. Use only the failure injections that already exist
   (the gated fault selectors of W11b2 and W97B); **no new injection is authorized**. A cut that the existing injections
   cannot produce is a coverage finding to report with evidence, as W97's K5 did, not a reason to add one.
2. **B3-S5, recovery** (about 10 cuts, 30 starts; real loader, mono-propellant):
   - cut-R1: 255 and 256 floor saturation, distinct and coalesced keys, and an UNKNOWN L1 with unload and restart;
   - cut-R3: a missing, replaced or behind ledger, and a foreign, missing, OTHER or relocated loader endpoint.
   No historical fallback and no forced chunk loads.
3. Every cut stops on each side of a durable acknowledgement where the row names one (including after replacement
   before the callback), restarts **twice**, and compares native raw quantities, markers and epochs with the control
   totals (A 5,000 to 4,000 mB and bank 0 to 1,000 mB), not only status strings. There is no tentative withdrawal at
   any cut. Pin the source commit, the JAR and the hooks in each result. A difference from the table's expected
   outcome is a finding to report, not to adjust.
4. Run the harness's tests and `python -B scripts/validate_repository.py --require-approved-identity`; if you change
   Java, also the whole unit suite and one full GameTest with the unchanged log checker, all on committed source.
   Known open item to report only: a local full GameTest under load can log more host-lag notices than the checker's
   ceiling (8).

If a cut needs a new stop point or fixture (not a new failure), add it only in the existing gated loader and escrow
release probe family (off unless the release-test gate and its property are set; it observes or pauses and never
changes a decision, a saved format, an acknowledgement order, a proof or a lease).

## Write scope

Harness extensions and their tests, gated stop points and fixtures in the existing loader and escrow release probes,
new unit tests for them, and the record. No production behaviour change, no new failure injection, and no relaxed
assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers also run heavy JVMs: take the heavy-JVM lock for every native run and GameTest,
and release it between cuts (not only between leaves) so another waiting worker can run. Check that C: and D: have
more than 10 GiB free before each heavy start (C: has about 13 GiB). Delete each cut's server copy and exported tree
afterwards; keep only logs, results and manifests. When your heavy runs end, remove your own lock and any
`build/gametest/world` with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses,
write the paths in the record and final answer at once.

Not in this task: B3-S6 (reused families), B3-B, B3-V, cargo, P1-N3, production admission, ADRs, the risk register,
status files and the ledger (Root).
