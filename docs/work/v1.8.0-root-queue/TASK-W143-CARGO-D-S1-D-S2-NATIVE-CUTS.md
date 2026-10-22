# TASK W143: cargo S2 cut families D-S1 and D-S2 for item cargo

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 6 from "S2 cut families that apply" through the "Changed-boundary and reuse mapping" table, the cut definitions
in [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), [records/W132.md](records/W132.md) (D-E1, with its 24 native item
cuts and the typed oracle), [records/W134.md](records/W134.md) (D-E2) and [records/W136.md](records/W136.md) (D-D's S1
harness) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w143` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w143` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W143.md` (new; a short plan at its top with the cut list you will run) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W143/` |

S2 evidence for item cargo on the integrated build; no production change unless a cut finds a defect; admission stays
disabled. One cut is one forced stop followed by two restarts; a before/after pair counts as two cuts; an after-write cut
means replacement before callback, not a graceful stop.

## The cuts

| Family | Cuts | Required outcome |
|---|---|---|
| D-S1, items | cut-K2, cut-K3, cut-K4, cut-K5 and cut-K9 per direction; a partial move stopped after apply and after one side is stored (12 cuts) | Exactly `n` items move; the remainder is intact |
| D-S2, items | cut-R3 for a changed container and LootTable (2); O1 after (1); O2 before/after each typed item-device and rocket proof on unload (4); O3 after (1); O3J after (1); O4 after (1); pruning after wrapper store (1); neutral normalization after publication and its veto (2); each ABANDON survivor (2); one P1-F8 disassembly, one rebuild refusal and cut-G1 item root (3) (18 cuts) | Typed NBT, counts and remainders identical to the frozen choice through readback, durable restart, release and pruning; O3-after continues through O4 and a governed launch with no live replacement choice; quantity 0 is not read as empty cargo or known zero loss |

Compare item stacks with their NBT tag types (W132-R3's typed oracle), not only counts. Pin the source commit, the JAR's
SHA-256 and the hooks for every cut. A gated release-test hook you add must be inert unless the release-test property and
its case are set, with a unit test. Stop and report at the first cut whose outcome contradicts its row.

## Verification

Each new or changed test class alone, the whole unit suite, your runner's Python tests and one full GameTest with the
unchanged log checker; then the cuts in order D-S1, D-S2, on committed source under the heavy-JVM lock with the atomic
pattern and the hand-off rule (per family or per few cuts), releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it. If the full GameTest fails only in the
intermittent discarded-launch family W139 investigates, record it and continue. Keep the evidence under 100 MB.

## Not in this task

The fluid families (W138, W142), enabling admission, ADRs, the risk register, status files and the ledger (Root).
