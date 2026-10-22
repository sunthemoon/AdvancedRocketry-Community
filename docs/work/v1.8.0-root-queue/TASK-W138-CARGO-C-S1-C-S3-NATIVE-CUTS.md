# TASK W138: cargo S2 cut families C-S1 to C-S3 for fluid cargo (normal, reconciliation and storage, recovery and roots)

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 6 from "S2 cut families that apply" through the "Changed-boundary and reuse mapping" table, the cut definitions
in [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) (cut-K, cut-E, cut-R and cut-G), and the records of the leaves
whose native harnesses you reuse: [W97](records/W97.md) (B3-S1 and B3-S2), [W124](records/W124.md) (C-E2's native
cuts), [W131](records/W131.md) (C-E3) and [W132](records/W132.md) (D-E1's 24 item cuts). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w138` (new; from the shared branch at the commit named in your prompt, which has C-E1 to C-E3 and C-D's S1 checks merged) |
| Branch | `codex/v1.8.0-w138` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W138.md` (new; a short plan at its top with the cut list you will run) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W138/` |

This task produces S2 evidence for fluid cargo on the integrated build. It does not change production code unless a cut
finds a defect, and it does not enable admission. The design's control totals apply: a 5,000 mB device and an empty
64,000 mB cargo tank, moving 1,000 mB; for unloads, the reverse. One cut is one forced stop followed by two restarts
(three JVM starts); a before/after pair counts as two cuts; an after-write cut means replacement before callback, not a
graceful stop.

## The cuts

| Family | Cuts | Required outcome |
|---|---|---|
| C-S1, normal | cut-K0 to cut-K10 for a load and for an unload, 14 cuts per direction as B3-S2 counts them, with the cut-K0 stop taken during preparation (28 cuts) | Raw mixed-save totals recorded while both endpoints are locked; after reconciliation exactly one transfer is spendable and the total is conserved; both directions in full |
| C-S2, reconciliation and storage | D2-C2, D2-C5, D2-C6 and D2-C7 per direction; cut-E1 (own store); cut-E2 (a newer save, and a rocket record near the inline-sector limit); cut-E4 (definite L1, UNKNOWN L1 and UNKNOWN L2); one cut-E3 and one cut-E5 representative (16 cuts) | Recomputed roll-forward; observable failures block retirement; unobservable ones stay diagnostic residuals |
| C-S3, recovery and roots | cut-R1 with a composite floor at 255 and 256 (2); cut-R2 with a cargo-mutated rocket and a stale duplicate (1); cut-R3 with a missing cursor, an OTHER payload and a relocated device (3); cut-G1 for a future device root and an invalid tank payload (2); cut-G3 with cargo L1 in flight and L2 UNKNOWN (2) (10 cuts) | FLOOR_CONFLICT or REPAIR without overwrite or historical fallback; roots preserved |

Pin the source commit, the JAR's SHA-256 and the hooks for every cut, and compare raw native quantities, markers and
epochs, not only totals. Where a cut needs a gated release-test hook that does not exist yet, add it (inert unless the
release-test property and its case are set) with a unit test that it is inert. Report each cut's outcome against the
row, and stop and report at the first cut whose outcome contradicts the row instead of adjusting the oracle.

## Verification

Each new or changed test class alone, then the whole unit suite and the Python tests of your runner; one full GameTest
with the unchanged log checker; then the cuts in order C-S1, C-S2, C-S3, all on committed source under the heavy-JVM lock
with the atomic pattern and the hand-off rule in WORKER-RULES (take the lock per family or per few cuts, not for the
whole task), releasing it and deleting every server copy and `build/gametest/world` in the `finally` of the same
detached script that took it. Check C: and D: above 10 GiB and that 6 GiB of memory is free before each Gradle run or
native start. Keep the evidence under 100 MB (AGENTS section 9).

## Not in this task

C-S4 and C-S5, the item families D-S1 and D-S2, enabling admission, ADRs, the risk register, status files and the ledger
(Root).
