# TASK W358: native cases for the satellite bay rows that C3 left uncovered

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w358`, branch `codex/v1.8.0-w358`, at the shared head |
| Decision | Root decision R435 ([OWNER-DECISIONS-20261113](records/OWNER-DECISIONS-20261113.md); QUEUE row W358) |
| Record | `docs/work/v1.8.0-root-queue/records/W358.md` |
| Write scope | Gated release-test fixtures and probes under `src/main` that do nothing without the release-test property, the bay native runners under `scripts/`, the campaign driver `scripts/run_v180_integrated_campaign.py` (new families only), their tests under `tests/`, and the record. No production behaviour, register, ADR, status file, QUEUE, build or workflow change. |

## Why

The owner asked to cover the rows that candidate C3 left NOT_COVERED before deciding on any switch (2026-11-13). For
the bay these are eight native rows of [W325-MATRIX](records/W325-MATRIX.md) and the UNKNOWN part of B11.7
([C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md), sections 2.4 and 3). [W331](records/W331.md), in its table of rows
not reached, says how each could be reached.

## What to do

1. **Build and smoke these five rows**, which W331 found reachable:
   - **B6.8:** a non-neutral source refused, packages byte-equal, the menu reason. It needs a real ledger binding from
     a fluid-loader fill before the launch, not the withdrawn epoch-only fixture.
   - **B10.6:** incoherent signature authority stays frozen, through a gated variant of `seed-repair` that writes an
     incoherent REPAIR row. Read the released-authority coherence rules first, and state in the record which rule
     the case exercises.
   - **B11.8:** a REJECTED re-arm is refused at full capacity and later reuses its operation, through a gated owner
     fill and unfill pair (`BayReceiptTestHooks.put` and `remove`) and two relaunches.
   - **B11.9:** mixed consumers, 63 escrow and 1 bay, and the reverse, through 63 live escrow operations or a typed
     seed of the escrow ledger file.
   - **CAP.3:** the owner cap refuses a loader before native capture, through a gated fluid-loader probe beside W325's
     seeded owner-full root.
2. **Check feasibility, then build if possible:**
   - **B11.7's UNKNOWN part:** a gated control that holds the receipt writer's UNKNOWN copy deterministically.
   - **B17.3:** a gated control that keeps one group's end unloaded while another group runs, with no race.
   If either cannot be made deterministic, explain why in the record and leave it NOT_COVERED.
3. **Unreachable rows.** For B11.2 and B17.5, write the argument for why production rules cannot reach them natively
   without forged state (W331 gives the starting point). Name the GameTest that covers each, or say that none does.
4. **Campaign families.** Add a family to the campaign driver for each new case, in the style of the `forced-W331-*`
   families, with the candidate commit and JAR SHA-256 arguments and the production-equality check that W331 added.
5. **Smokes.** Run each new case natively on a locally built JAR, with the forced stop and both restarts, under the
   heavy-JVM lock (WORKER-RULES). A smoke is not candidate evidence. Where practical, show that each new oracle
   rejects a scratch JAR with the protection disabled.
6. **Production equality.** Show with `git diff --stat` over `src/main`, `src/generated` and the Gradle files that only
   gated release-test classes changed, and that each new hook does nothing without the release-test property.

## Rules

- Do not change a production rule, an existing oracle's expectation or an existing case's selection. Do not delete
  tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Run the targeted unit tests and the Python tests you touched. Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W358/`, and delete server copies after each run.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not
  push.
- The record gives each row's result (PASS in smoke, NOT_COVERED with the reason, or unreachable with the argument),
  the commands and the evidence paths. Its last line, and the last line of your answer, is the path of
  `records/W358.md`.
