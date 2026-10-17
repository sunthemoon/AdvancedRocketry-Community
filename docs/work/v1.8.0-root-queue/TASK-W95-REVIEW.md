# TASK W95-review: independent read-only review of B3-E (the loader's escrow adapter)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. Both Codex slots are busy, so this is a fresh Claude
session with no context from the implementer.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w95-b3e`, commits `8eede7a9`, `8b4ab2d8`, `ed20c5f1`, `a733c2cc`, `eea18fd4` (code) and `b890a98f` (record `records/W95.md`), on top of the shared branch at `d6d14cf5` |
| Task it answers | [TASK-W95-B3E-LOADER-ADMISSION](TASK-W95-B3E-LOADER-ADMISSION.md) |
| Design (frozen) | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 2 to 4 and section 5's B3-E row |
| Checkout | `D:/GitHub/arce-v180-w95-review`, detached at `b890a98f`, made for you; do not commit in it |
| Evidence to check | `D:/GitHub/ARCE-Task-Evidence/root-queue/W95/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W95-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Production admission stays disabled.** `productionAdmissionEnabled()` stays false; only the release-test gate
   (`EscrowTestEndpoints.enabled`) and the system property the record names reach the admission path. No production
   world can create a ledger, queue a fill or change a loader's or rocket's resources through this code.
2. **Ledger creation and its baseline** (section 2): created only at a world's first gated fill, behind an empty
   baseline; what happens on a failed or UNKNOWN baseline write, a stop between creation and baseline, and a restart.
   No file is rewritten or discarded outside the design.
3. **The fair queue and `prepareFill`:** every refusal compares both resource envelopes and the queue and count state
   and changes nothing; the five-marker limit; Root-R6 headroom and Root-R8 charging; competing devices.
4. **The loader endpoint adapter, its signature registration and the proof validators:** they accept exactly the
   design's unit and type, refuse others, and never weaken W11b2's proofs, acknowledgement orders or leases.
5. **Production and restart reconciliation** replacing `RECONCILIATION_UNIMPLEMENTED`: is it safe, bounded and
   fail-closed, and does it use real endpoints, not fixtures?
6. **Saved data:** any new or changed saved record, key, schema or root, its version and size limits, and whether the
   record's statement that no saved format changed beyond the design is true.
7. **Tests and evidence:** the A1 GameTests, the native forced-stop and restart run (including the first tick after
   start, given W93's bind at `ServerStartingEvent`), the new WARN expectation, the risk the record names about a
   creation GameTest failing midway, and the claimed counts. Rerun the escrow and loader unit tests
   (`gradlew.bat --offline test --tests '*resourceescrow*' --tests '*rocket.fuel.loader*'`) in your own checkout only
   when `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` does not exist; take no lock and run no GameTest or server.
8. **The record's open items and choices** (its sections 6 to 8): which need Root's decision before the merge.

## Output format

- A verdict: MERGE, MERGE-WITH-FOLLOWUP, or FIX-FIRST.
- Findings, each with an ID (W95-R1, ...), a severity, the file and line, the problem, and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
