# TASK W220-integrate: merge the shared head into W220's branch after W221

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w220` (W220's; continue on its branch) |
| Branch | `codex/v1.8.0-w220`, now at `e36c37f70` (pushed; local commits only from here, Root pushes and merges) |
| Merge in | `origin/codex/v1.8.0-classic-content` at `4d4de4801` (fetch first), which contains W221's merge `8260b2b17` |
| Record | `docs/work/v1.8.0-root-queue/records/W220.md`: append a short section "Integration after W221" |

**Root decision R212.** W220 and W221 are both reviewed and passed their branch runs; W221 merged first. A trial merge
of W220 on top conflicts in four files, all because each slice added its own S6 cases at the same places:
`scripts/run_v180_loader_s6.py` (imports, `CASES`, `HARNESSES` and `HOOKS`, `check_log`, the pause marker, the image
inspection and `raw_check`), `src/test/java/.../resourceescrow/EscrowProbeRoutingTest.java` (the pinned count),
`src/test/resources/escrow-probe-cases.tsv` (generated) and `tests/test_escrow_probe_inventory.py` (the pinned count).

## What to do

1. Merge the shared head into `codex/v1.8.0-w220` (a merge commit, no rebase).
2. Resolve every conflict as the **union** of both sides: W221's named-pad cases and W220's publication cases both
   stay, each routed exactly as on its own branch. In the runner's image inspection, `named_pad.inspect` applies to the
   named-pad cases and `flight.inspect` to `flight.CASES + publication.CASES`, with the other branches unchanged; keep
   W221's unexplained-image check. Change no behaviour of either side.
3. Regenerate `escrow-probe-cases.tsv` with `scripts/inventory_escrow_probe_cases.py` (never by hand), and set both
   pinned counts to the regenerated row count (611 before either slice, plus seven and two, is 620; if the script
   gives another number, find out why before changing anything), keeping both comments and the W220 `containsAll`
   assertion.
4. Run: the loader S6, named-pad, publication and escrow inventory Python tests; `EscrowProbeRoutingTest`,
   `EscrowRelaunchClassesTest`, the `resourceescrow` and `rocket.server` packages; the whole unit suite once;
   `python -B scripts/validate_repository.py --require-approved-identity`. No native run or GameTest.
5. Commit the merge, then the record section.

## Not in this task

Any change beyond resolving these conflicts, native reruns, ADRs, status files and the ledger.
