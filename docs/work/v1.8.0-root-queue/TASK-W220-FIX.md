# TASK W220-fix: bind the publication veto to the selected fixture's identity

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session; this file hands one
review finding of W220 to you.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w220` (W220's; continue on its branch) |
| Branch | `codex/v1.8.0-w220`, now at `abfbd9e8d` (pushed by Root; local commits only from here, Root pushes and merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W220.md`: append a section "R209 review correction" |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W220-review/REVIEW.md` (SHA-256 `8a0dade4…3515e82e7`), finding W220-R1 |

**Root decision R209.** W220's review approved with one Low finding: `EscrowLoaderS6PublicationProbe` (lines 60 to 71)
selects the journal's single governed row and takes its expected destination from that row, without comparing the
row's transfer, source and fixed destination with the selected loader fixture's own identity; a different valid
governed flight that is the only row in the selected case and round would be vetoed. Test-side only.

## What to do

Bind the probe to the selected fixture's transfer, source and fixed-destination identity taken from its checked F2
capture; for recovery, carry or recover that expected identity through the harness and check it against the checked
native authority before arming the listener. A join whose identity does not match is ignored before any probe state
changes or any event is cancelled. Add a regression that fails first: another individually valid singleton governed
transfer, with the same hooks, case, round and phase enabled, is not vetoed. Do not change the executor's decisions or
seed authority. The native rows are not repeated; state in the record why the fix does not change their retained
results (the six images bind to the intended flight, as the review found).

## Verification

The new regression alone, failing first; `EscrowLoaderS6PublicationProbeTest`, `EscrowLoaderS6FlightProbeTest` and the
`resourceescrow` package; the loader S6 Python tests. No native run, GameTest or full build.

## Not in this task

Native reruns, other findings, production changes, ADRs, status files and the ledger.
