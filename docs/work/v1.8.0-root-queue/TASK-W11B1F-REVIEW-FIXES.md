# TASK W11b1F: W11b1 review fixes (findings 1 to 4 first)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b1-escrow-core` (W11b1 at `19ab684a`) |
| Branch | `codex/v1.8.0-w11b1-escrow-core` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b1-review/REVIEW.md`, SHA-256 `e3d1fd91d6f113dfc4ead6a148251ea6fa33aac3741a2355c9099130f4927e3a`. Verdict MERGE-WITH-FOLLOWUP |
| Record | Add a "Review fixes (W11b1F)" section to `records/W11B1.md`. Keep the existing text |

Read the whole review, including its probe appendix. Fix each finding in its own commit,
together with its regression test. Root merges W11b1 only after these fixes land, so the
shared branch never carries the latent defects. W11b2 is being built on `19ab684a` and will
merge this branch in afterwards.

## Root's decisions

1. **Finding 1 (High): writer stuck after an ambiguous L2 replacement.** When an ambiguous L2
   replacement's proof is later invalidated, the ledger writer must not wedge, and shutdown
   must always finish. Fix it as the review describes, and add a regression test that
   reproduces the probe.
2. **Finding 2 (High): retirement without its operation.** `retire()` must reject an endpoint
   proof that does not contain the operation it retires. Add a regression test that
   reproduces the probe.
3. **Finding 3 (Medium): drain time grows with uptime.** The shutdown drain must not restart
   the writer clock at `retryAt`, so drain time no longer grows with server uptime. Add a
   test at a high server tick count.
4. **Finding 4 (Medium): quadratic capture cost.** Entity capture must not re-check the whole
   list after every entity, so main-thread cost is linear. Add a scale test with an explicit
   bound.
5. **Lows 5 to 8, 10 and 11:** fix them, each small:
   - the crash path quiesce, drain and join (5);
   - `retire()` callbacks final on UNKNOWN (6);
   - barrier reason classification (7);
   - the at-limit test for one operation per device (8);
   - the record's C1 to C6 line references (10);
   - the startup refusal hint scoped to the escrow type (11).
6. **Low 9:** document in the record that the benchmark's main-thread cost is a lower bound,
   and log state changes as the design requires.
7. **Native evidence (review condition 2).** On the final source:
   - re-run G3, G3L2 and E4L2_UNKNOWN;
   - add a cut in which a newer save lands between an ambiguous L2 attempt and its retry.

   Use the existing harness, which pins the source commit and the jar SHA-256. Keep the
   evidence under 100 MB.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.
- The native cuts in item 7.

## Not in this task

- The risk register and ADRs (Root adds the C3 startup-scope entry and the ADR-065 note at
  merge).
- The W11b2 scope.
- Production admission.
- Status files and the ledger.
