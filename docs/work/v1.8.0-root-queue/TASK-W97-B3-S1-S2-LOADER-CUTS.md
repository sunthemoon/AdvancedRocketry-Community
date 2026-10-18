# TASK W97: W11b3's S2 cut families B3-S1 (ledger creation) and B3-S2 (normal transaction) for the fluid loader

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md)
section 5 (the S2 leaves table, and the paragraph before it on control totals and pinned evidence),
[records/W95.md](records/W95.md) (B3-E, the loader's escrow adapter behind its gated switch, and its W95F
follow-ups) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w97` (new; from the shared branch at `8bc9206f`) |
| Branch | `codex/v1.8.0-w97` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W97.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W97/` |
| Base harness | `scripts/run_v180_fluid_loader_restart.py` and `EscrowLoaderReleaseProbe` (W95's native forced-stop run, `eea18fd4`), and the other `scripts/run_v180_escrow_*.py` cut runners |

## What to do

1. **B3-S1, creation** (7 cuts, 21 starts), exactly as the design's table gives it: cut-N0 (stop after creation and
   queueing, before the baseline write), cut-N1 (after the baseline file is replaced, before its callback), cut-N2 (a
   baseline that fails definitely, and one that ends UNKNOWN), cut-N3 (a refused first attempt: SERVER_FULL from a test
   bay index, a preflight refusal, and UNAVAILABLE while NOT_LOADED). After each restart: no ledger or a valid empty
   one, the tank and the bank unchanged, no sequence issued, no reservation left, and the next attempt succeeds; the
   player sees `PREPARING` while the baseline is pending and `LEDGER_UNAVAILABLE` after three unresolved failures.
   Mono-propellant only.
2. **B3-S2, normal transaction** (14 cuts, 42 starts): cut-K0 to cut-K10 as the table lists them, with the real loader;
   bipropellant at cut-K2, cut-K6 and cut-K9.
3. Every cut restarts twice and compares native raw quantities, markers and epochs (the design's control totals: A
   5,000 to 4,000 mB and bank 0 to 1,000 mB; with B = 5,000, A = B = 4,000 and bank 2,000), not only status strings.
   Pin the source commit, the JAR and the hooks in each result. A difference from the table's expected outcome is a
   finding to report, not to adjust.
4. Run the harness's tests and `python -B scripts/validate_repository.py --require-approved-identity`; if you change
   Java, also the whole unit suite and one full GameTest with the unchanged log checker, all on committed source.

If a cut needs a new stop point, add it only in the existing gated release-test probe family (off unless the
release-test gate and its property are set; it observes or pauses and never changes a decision, a saved format, an
acknowledgement order, a proof or a lease). The design says no new failure injection is authorized for storage
failures (that is B3-S4, not this task).

## Write scope

The new harness or harness extensions and their tests, gated stop points in the existing loader and escrow release
probes if needed, new unit tests for them, and the record. No production behaviour change; no relaxed assertion,
limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W91R, W83b) also run tests; take the heavy-JVM lock for every native run
and GameTest, and release it between cuts when another worker waits. Check that C: and D: have more than 10 GiB free
before each heavy start (C: has about 13 GiB). Delete each cut's server copy and exported tree afterwards; keep only
logs, results and manifests. When your heavy runs end, remove your own lock and any `build/gametest/world` with a plain
`rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in the record and
final answer at once.

Not in this task: B3-S3 to B3-S6, B3-B, B3-V, P1-N3 (W96), production admission, ADRs, the risk register, status files
and the ledger (Root).
