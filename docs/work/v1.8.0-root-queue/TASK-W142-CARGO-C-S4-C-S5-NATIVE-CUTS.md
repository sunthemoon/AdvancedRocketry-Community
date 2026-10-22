# TASK W142: cargo S2 cut families C-S4 and C-S5 for fluid cargo (settlement; creation, flight and disassembly)

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 6 from "S2 cut families that apply" through the "Changed-boundary and reuse mapping" table, the cut definitions
in [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), [records/W131.md](records/W131.md) (C-E3, the settlement and
end-to-end cases these cuts repeat natively), and W138's record and runner (`records/W138.md` once merged; C-S1 to
C-S3 on the same harness) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w142` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w142` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W142.md` (new; a short plan at its top with the cut list you will run) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W142/` |

S2 evidence for fluid cargo on the integrated build; no production change unless a cut finds a defect; admission stays
disabled. The control totals, the cut counting (one forced stop and two restarts per cut) and the pinning rules are the
ones TASK-W138 repeats from the design.

## The cuts

| Family | Cuts | Required outcome |
|---|---|---|
| C-S4, settlement | For unload ACCEPT_CURRENT: O1 before/after (2), neutral normalization before/after publication (2), O2 before/after each of the rocket-source and device-destination proofs (4), O3 before/after (2), O3J before/after (2), O4 before/after (2), pruning before/after wrapper store (2); ABANDON omitting each endpoint (2); O-veto with destination saved and a normalization veto (2) (20 cuts) | O3-after restarts from durable data only, reacquires exact O2/O3J proofs, completes O4 and pruning, and performs a governed launch. Every variant keeps fuel-vector plus invariant settlement authority, cargo-sensitive equality and UNKNOWN loss; 6,000 mB never becomes available from 5,000 mB |
| C-S5, creation, flight and disassembly | cut-N1 (1); D2-F1/F6/F8 after cargo (3); P1-F8 before restoration, record removal and release (3); unload followed by REBUILD_DESTINATION, KEEP_SOURCE and REBUILD_SOURCE attempts (3); stale PREPARED with both physical entities (1); disembarked-passenger reconnect (1); governed record with missing and terminal authority (2); successor cargo mutation and checkpoint between two completed ordinary cargo-mutated flights (1); exact-source cancellation after cargo (1) (16 cuts) | Latest cargo materializes once; protected stale copies cannot win or be discarded unchecked; governed records never enter legacy recovery; both flights complete with fresh governed authority; cancellation keeps the current cargo without historical reconstruction |

Pin the source commit, the JAR's SHA-256 and the hooks for every cut; compare raw native quantities, markers and epochs.
A gated release-test hook you add must be inert unless the release-test property and its case are set, with a unit test.
Stop and report at the first cut whose outcome contradicts its row.

## Verification

As TASK-W138: each new or changed test class alone, the whole unit suite, your runner's Python tests and one full
GameTest with the unchanged log checker; then the cuts in order C-S4, C-S5, on committed source under the heavy-JVM lock
with the atomic pattern and the hand-off rule (per family or per few cuts), releasing it and deleting every server copy
and `build/gametest/world` in the `finally` of the same detached script that took it. If the full GameTest fails only in
the intermittent discarded-launch family W139 investigates, record it and continue. Keep the evidence under 100 MB.

## Not in this task

C-S1 to C-S3 (W138), D-S1 and D-S2, enabling admission, ADRs, the risk register, status files and the ledger (Root).
