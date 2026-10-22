# TASK W130-review: independent read-only review of W130 (R52's D2 b2, retire a landed rocket's transfer record before its first escrow fill) before merge

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents or
subagents. Codex wrote W130, so this is a fresh Claude session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w130` at `e2ded586` (base `6dc96301`). Your review worktree `D:/GitHub/arce-v180-w130-review` is a detached checkout of `e2ded586`; read the diff with `git diff 6dc96301..e2ded586` there |
| Task it answers | [TASK-W130](TASK-W130-D2-B2-RETIRE-BEFORE-FILL.md), with Root decision R52 |
| Design | [records/W126-DESIGN.md](records/W126-DESIGN.md) (R52 at its top), its review `D:/GitHub/ARCE-Task-Evidence/root-queue/W126-review/REVIEW.md` with its probes, and the ordinary retirement proof in [records/W98.md](records/W98.md), [records/W116.md](records/W116.md) and [records/W96-DESIGN.md](records/W96-DESIGN.md) |
| Builds on | W125 with W125F, the F1 named-fill fix ([records/W125.md](records/W125.md)) |
| Record | `docs/work/v1.8.0-root-queue/records/W130.md` on the branch (the plan is at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W130/` (12 native retirement cases, two restarts each, and the seven named controls) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W130-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **No removal without proof.** Walk every path that retires T: the landed rocket's own slot proven at the
   destination, the old launch site's origin slot shown empty, through W98's on-disk proof. Can any order of loading,
   unloading, save, crash or restart remove T while a copy of the rocket still exists at the old site or anywhere else,
   or leave the rocket with neither T nor a proven single copy? Check the unloaded off-origin copies W130 lists as a known
   risk: is that risk real, and is it the same as before W130 or new?
2. **Retirement before fill.** The fill is admitted only after the retirement is acknowledged; a fill can never carry T's
   transfer guard or leave a floor; a refusal or hold leaves fuel, the loader and the rocket unchanged. Check the
   acknowledgement against the journal's W123 rules (session-local exact acknowledgement, COMMITTED outcome, exact
   current sequence).
3. **W126-R1, the on-demand ticket:** placed on the old launch site's origin column and refreshing the destination's,
   only when a loader attempts a fill on a landed rocket that still has T; at most four starts per tick and one attempt
   per rocket per 200 ticks; released on success or after the existing 400-tick lifetime; never at startup on its own; a
   loader's retry does not reset W98's backoff; recreated entries keep their spacing (`50943a97`). Can a player force
   chunk loading at a position they choose, or exceed the budget, through any loader, rebind or menu path?
4. **W126-R2, the discard scan** of both ends before each proof, under the existing discard rules, so a copy that loads
   after the retirement started cannot fail the proof forever, and the retry is reserved before discarding (`6b7388e8`).
5. **W126-R3, the named-fill seam** (`EscrowNamedFillTestHook`): inert unless the release-test hook and its case are set,
   unreachable in production, and which W125 cases use it.
6. **W126-R6, the pad after retirement:** how a later flight's pad choice treats the landed rocket's pad, and the test
   that a flight does not land on it (`eeb11451`).
7. **W126-R9 and D2-4:** logs and oracles do not assume `FLOOR_PROOF_REQUIRED` is the only fence reason; unsupported
   entity storage holds the retirement and never removes T without proof; the held state is local to escrow admission
   (`fa6d1c12`) and survives restarts (`4bef4fea`). Say whether any hold is a new protection that refuses or holds
   player data in the sense of AGENTS section 3.3, which would need a risk-register entry before merge (Root's).
8. **D2-3:** the native evidence reports, for each named cut and restart, resources conserved, refuelling restored, and
   launch allowed or refused, separately. Do the 12 cases cover the review's cut list including its four missing cases?
   The seven named controls still show F3's `INVALID_STATE`, because W130's base predates W133; say what the combined
   rerun (Root's W133F, after this merge) must show.
9. **Nothing weaker:** list every edited pre-existing test or fixture file and say whether any assertion, bound, budget
   or timeout became weaker; no saved format, schema, hash, dictionary, proof, lease, budget or timeout changes;
   `RocketTransferService` stays under 800 lines; new classes stay under 500 lines; any new WARN or ERROR a GameTest
   drives has its own exact manifest entry; production escrow admission stays disabled.
10. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W130's
    base merged W132 (item cargo admission and reconciliation), W133 (F3: `RocketFlightService`, `EscrowGovernedLaunch`
    and the launch predicate) and W131 (fluid cargo settlement). Preview with `git merge-tree`, list every conflict and
    say what the merge must keep, in particular how W130's retirement step and W133's launch promotion combine in
    `RocketFlightService` and the governed launch.
11. **The record** against the code and the evidence.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W130's
new and changed unit classes, each alone (`RocketDestinationRetirementTest`, `RocketLandedRemovalCutTest`,
`EscrowNamedFillTestHookTest`), then the `rocket.server`, `rocket.flight.persistence` and `resourceescrow` packages; and
`python -B -m unittest discover -s tests -p "test_retire_before_fill_cuts.py"`. You may add a probe test to check a
finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of memory is free and
wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock. Delete the
worktree's `build`, `.gradle` and `logs` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W130-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
