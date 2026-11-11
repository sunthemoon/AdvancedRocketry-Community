# TASK W355: keep the out-of-reach ordinary F9 row out of the campaign's B3-S6 family

Root: Claude. Date: 2026-11-11. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w355`, branch `codex/v1.8.0-w355`, created by Root from the shared head |
| Decision | Root decision R427 (QUEUE row W355) |
| Evidence to read | `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/campaign/cargo-B3-S6-REUSE-attempt1/` (outside Git): `runner.log`, `native/results.json` and `native/LF_S6_F9_RESPAWN/start-0.log` |
| Earlier result | [W211](records/W211.md), sections "R203 delivery (2026-10-31)" and "R203 structural stop checkpoint (2026-10-31)" |
| Record | `docs/work/v1.8.0-root-queue/records/W355.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W355/` |

## Why

Candidate C2's campaign family `cargo-B3-S6-REUSE` (runner `scripts/run_v180_loader_s6.py`, selected in
`scripts/run_v180_cargo_integrated.py` as `REUSE["B3-S6-REUSE"] = (loader_s6, loader_s6.CASES)`) ran 88 of its 97
cases: 87 passed and `LF_S6_F9_RESPAWN` failed at its first start. The gated probe threw "Rejected ordinary respawn
changed authority or source" (`EscrowLoaderS6OrdinaryProbe.java:115`) after the log showed
`ARCE_TRANSFER_RETIREMENT_PROVED ... site=RECOVERED_SOURCE` and `ARCE_TRANSFER_RECOVERY ... phase=COMMITTED
source_count=1 destination_count=0 action=KEEP_SOURCE status=RECOVERED`. The runner's first-contradiction stop then
skipped the nine later cases (`loader_s6_publication.CASES` and `loader_s6_named_pad.CASES`), which passed at their
own merges (W220 and W221).

This is the result W211 already recorded. Its two R203 attempts on `4e3abffc5` failed the same way:
`RocketTransferRecoveryDecision.decide` keeps the source whenever only a source exists, so a seeded historical
COMMITTED record beside a settled source is retired and no destination respawn is ever attempted. W211 called this
the product's preservation policy, not a product defect, and stopped. `docs/status/CURRENT_VERSION.md` records the
ordinary F9 form as out of reach for loader-funded rockets and not a pass. The campaign family took
`loader_s6.CASES` whole, so it ran a row that has never passed and cannot pass. That is a selection error in the
campaign, not a new finding. `B3-S1-REUSE` in the same table already leaves out `N1` the same way.

## What to do

1. **Change the selection, not the row.** In `scripts/run_v180_cargo_integrated.py`, make `B3-S6-REUSE` select
   `loader_s6.CASES` without the ordinary F9 row, through a named constant for the excluded cases with a short
   comment that cites W211 R203. Leave `loader_s6.CASES`, `scripts/loader_s6_ordinary.py`, the probe and every oracle
   unchanged, so W211's own runner and record keep the row as they are.
2. **Keep the exclusion visible.** The campaign must not drop the row silently. Make the excluded case and its
   reason (structurally out of reach, W211 R203, not a pass) appear in the campaign plan that
   `scripts/run_v180_integrated_campaign.py` writes (`plan.json`) and in the family's row there, so a report built
   from the plan lists it as not covered. Choose the smallest form that the existing plan schema allows, and say
   what you chose.
3. **Audit the other 57 families.** For each family in the campaign plan, compare its case list with the records and
   the status file for cases that are recorded as structurally out of reach, kept as FAILED residuals (for example
   W176's three downgrade rows) or never passed natively. List any you find in your record with the evidence (record,
   section, commit). Do not change those selections; Root decides each one.

## Verification

- The tests for anything you change, including a test that `B3-S6-REUSE` leaves out `LF_S6_F9_RESPAWN`, still
  contains every other case of `loader_s6.CASES` in order (including the nine later cases), and that the plan names
  the excluded case with its reason.
- `python -B -m unittest discover -s tests` once before you commit (the escrow probe inventory test checks case
  tokens), and `python -B scripts/validate_repository.py --require-approved-identity`.
- A dry-run plan from the campaign driver (its `--dry-run` mode, with the arguments from C2's
  `D:/GitHub/ARCE-Task-Evidence/root-queue/C2/run-20261110-230154/run.cmd` and a scratch evidence root under your log
  directory), showing 58 families and the B3-S6 family with 96 cases and the named exclusion. No native run, no JAR
  build and no heavy-JVM lock: Root's C2 campaign holds the machine.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production code, the gated probes, `loader_s6.CASES` and the other runners' oracles; the other families'
selections; the C2 worktree and its evidence; the status file, QUEUE and the risk register.
