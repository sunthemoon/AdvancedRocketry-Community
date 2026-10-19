# TASK W107B: the remaining B3-S3 to B3-S5 loader cuts (C1, E1, E2, E4, R1, R3) under Root decision R36

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W107](TASK-W107-B3-S4-S5-FAILURE-RECOVERY-CUTS.md),
[records/W107.md](records/W107.md) (merged as `9f6668aa`; especially "Unfinished scope and known risks"),
[records/W105.md](records/W105.md), [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 5 and
[records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 7 (the C1, cut-E and cut-R rows) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w107b` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w107b` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W107B.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W107B/` |
| Base harness | `scripts/run_v180_loader_cuts.py`, `scripts/loader_cut_oracle.py`, the gated `EscrowLoaderCutProbe`, `EscrowLoaderStorageProbe` and `EscrowReleaseProbe` |

## Root decision R36 (recorded here, 2026-10-20)

W107 stopped at the boundary of TASK-W107's "no new failure injection" rule. Root decides:

1. **C1 keeps its oracle.** W11A section 7 defines C1 as "rocket prefix 1 / B prefix 2", so the required stopped state
   (A 4,000, B 4,000, bank 1,000; epochs 1/1/1; one marker each; two L1 records) stands; A ahead of the rocket, which
   W107 observed, is a different prefix. A **gated fixture save** at the C1 stop point is allowed: write B's terrain
   chunk and the rocket's entity chunk with the existing `EscrowReleaseProbe.newerSave` pattern (serializer, Save
   event, `ChunkMap.write`) or its entity-chunk equivalent. It models an autosave arriving at that moment, a schedule
   vanilla can produce; it is not a failure and changes no production order. Record the exact writes.
2. **Offline fixture states are fixtures, not injections.** Between the forced stop and a restart, the harness may
   edit only the disposable server copy: remove the ledger file, put back an older copy of it captured earlier in the
   same run (behind), or rewrite a loader endpoint's NBT into a foreign, missing, OTHER or relocated state with the
   repository's bounded NBT tools. Keep the original bytes and record every edit with before and after SHA-256. This
   covers R3, and R1's named-transfer floor fixtures (255 and 256 floors, distinct and coalesced keys).
3. **New gated test-only faults are authorized for E1, E2 and E4** where no existing selector exists: an observed
   own-store failure and an observed superseding-store failure (E1); an external `.mcc` chunk and a record over the
   decoded bounds (E2; an oversized natural chunk or an offline-written copy is preferred over a code fault); a stale
   writer callback delivered after its successor (E4). Each fault lives in the existing gated release probe family,
   is off unless both the release-test hook property and its case property are set, never changes a decision, a saved
   format, an acknowledgement order, a proof or a lease, and has a unit test showing it is inert when the gate is off.
4. Unchanged: R-032's boundary (an unobservable native failure is a diagnostic residual, not a PASS), no historical
   fallback and no forced chunk loads by recovery, two restarts per cut, raw native comparisons against the control
   totals, and no relaxed oracle, limit or timeout.

## What to do

1. C1 under R36 item 1; if C2 or the partial cuts share the changed code, rerun them.
2. B3-S4: E1 (both), E2 (external `.mcc`, decoded bounds; the newer generation is covered by W107's `E2_CAPTURE`, and an
   unstable header if the reader distinguishes it), E4 (stale callback).
3. B3-S5: R1 (255 and 256 floor saturation, distinct and coalesced keys) and R3 (missing, replaced and behind ledger;
   foreign, missing, OTHER and relocated loader endpoint).
4. Each cut stops on each side of a durable acknowledgement where the row names one, restarts twice, and compares raw
   quantities, markers and epochs with the control totals. A difference from the expected outcome is a finding to
   report, not to adjust.
5. Run the harness tests, the unit suite if Java changes, one full GameTest with the unchanged log checker on committed
   source, and `python -B scripts/validate_repository.py --require-approved-identity`.

## Write scope and not in this task

Harness, oracle and their tests; gated fixtures and faults in the existing loader, storage and release probes and
their unit tests; the record. No production change: if a cut shows a production defect, stop and report it. Take the
heavy-JVM lock for every native run and GameTest and release it between cuts, since other workers wait; check that
C: and D: have more than 10 GiB free first; run long heavy jobs as detached processes. Delete each cut's server copy
afterwards and keep only logs, results, manifests and the recorded fixture hashes, under 100 MB. Not in this task:
B3-S6, B3-B, B3-V, cargo, P1-N3, production admission, ADRs, the risk register, status files and the ledger (Root).
