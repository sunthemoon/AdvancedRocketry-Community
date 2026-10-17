# TASK W11b2-fix-review-flight: independent read-only review of continuation 46 (riders, passengers, transient observations, executor holds)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change nothing in any repository worktree.

The final independent review of W11b2 part B (the governed flight path F0 to F8) returned FIX-FIRST. Root answered its
questions with R17 and R18. Continuation 46 (Codex) implemented R17 items 1, 2, 5 and 6 with R18 and review F3 and F7;
continuation 47 implemented R17 items 3 and 4 and review F8 to F10 and is reviewed separately
([TASK-W11B2-FIX-REVIEW-LEASE](TASK-W11B2-FIX-REVIEW-LEASE.md)). This review comes from a different session and model
than continuation 46's implementer (AGENTS.md section 13). It decides whether part B's F1, F2, F3, F6 and F7 are closed.

| Item | Value |
|---|---|
| Subject | The integrated W11b2 head `2ed7f18e`. Continuation 46's own change is `git diff 40164da6..d7e2cebe`; Root merged it as `858c148e` (no conflicts) and added the log expectation `2ed7f18e` |
| Your checkout | `D:/GitHub/arce-v180-w11b2-fix-review-flight`, a detached worktree at `2ed7f18e`. Read it and run tests there; do not commit, switch or reset it |
| Part B review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-B/REVIEW.md` (SHA-256 `60581547d83db3bf70b1b1d3d17eecf140df73afcaaa74144f930e8d2b30b0bd`), especially F1, F2, F3, F6, F7, I1 to I7 and its section 3.3 list |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`), sections 5.1, 5.2, 5.4 and 5.5; R10 to R16 (paths in `TASK-W11B2-FINAL-REVIEW-B`); **R17** `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/ROOT-DECISION-R17.md` (SHA-256 `c31254f82e023247b8d9d1df5a89d6919cec87bfce0635e5a7ad79f5e6e664c1`); **R18** `W11b2-C46/ROOT-DECISION-R18.md` (SHA-256 `00e93c1ad32f1ef423eaf86ee99f415641fb4657c4b0ef980352b5652406a0d1`) |
| Records | `records/W11B2-C46.md` and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-fix-review-flight/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **R18 and part B F1.** Every governed comparison of a native rocket envelope from F1 to F8 (the ARMED digest, F2's
   check of T, the F3 binding and frame, the F3 transitions, the restart clock, F4, F5, F6, the returns and the pre-F2
   cancellation) uses the rider-normalized envelope (`EscrowGovernedEnvelope`), and nothing else is normalized: the
   manifest and every other field stay exact. Live riders are checked as a duplicate-free subset of the assigned
   passengers. Uncrewed digests are unchanged. Name any governed comparison that still uses the raw envelope, and any
   ordinary settlement or reconciliation digest that changed.
2. **R17 item 2 and part B F2.** An online assigned passenger at rest reaches F5's checked move; `passengersPending`
   short-circuits only for an offline assigned passenger; CANCELLED requires each assigned passenger online, not riding
   another vehicle and in the source's Level; HANDED_OFF still requires every assignment aboard the destination; no
   return code seats or moves a passenger.
3. **R17 item 5 and part B F6.** Only motion and plan positions that the entity's own tick normalizes are transient
   (`EscrowGovernedFrame`, the moved `setDeltaMovement` in `RocketEntity.tick`); a persistent position change at rest
   is a hold with one WARN per flight. Check that the `RocketEntity.tick` reordering changes no other behaviour.
4. **R17 item 6.** A non-transient executor hold logs one WARN per (flight, reason)
   (`EscrowGovernedExecutorHolds`), stops refreshing the flight tickets, and never supplies action or recovery
   authority; its state is bounded and has the server runtime's lifetime.
5. **Part B F3.** The validated, normalized envelope expansions are cached per immutable record instance; no action
   permit or acknowledgement is cached; F5's warm skip cannot bypass a permit check. Name any per-tick full capture or
   rehash that remains on a healthy descent or landed wait.
6. **Part B F7 and the tests.** Do the four departure GameTests, the crewed native probes and the unit tests prove what
   the record claims? Look for weakened or removed assertions (continuation 46 changed rider-inclusive expectations
   to R18's rule; check each one), changed runner oracles, and probes that do the product's work. Continuation 46's
   crewed `PF_ARM_REPLACED` failed; Root's R19 (`W11b2-C48/ROOT-DECISION-R19.md`) assigns that oracle to continuation
   48, so do not review the PF runner change itself, but say whether R19's reasoning matches the code.
7. **AGENTS.md section 3.3.** Update part B's section 3.3 list for items 3, 5, 6 and 8 as the code now stands.
8. **Rerun** in your checkout, with `JAVA_HOME` set to `C:/Program Files/Java/jdk-17.0.7`:
   `gradlew.bat --offline test --tests '*EscrowGoverned*' --tests '*EscrowFlight*' --tests '*EscrowTransferEnvelope*' --tests '*RocketGoverned*' --tests '*EscrowPreF2*' --tests '*EscrowSourceClock*' --console=plain`
   and `python -B -m unittest discover -s tests -p "test_v180_escrow*.py"`. Do not run GameTests, native runs or the
   whole suite, and take no heavy-JVM lock.

## Output format

- A verdict: MERGE-READY, MERGE-WITH-FOLLOWUP, FIX-FIRST, or BLOCK.
- For each of part B's F1, F2, F3, F6 and F7: CLOSED, PARTLY CLOSED or OPEN, with the evidence.
- New findings, each with an ID, a severity (Critical, High, Medium, Low, Info), the file and line at `2ed7f18e`, the
  failure scenario and a suggested fix.
- The updated section 3.3 items from item 7.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.

## Not in this task

Any edit, commit, push or merge in a repository worktree; any other agent; GameTests, native runs and benchmarks;
continuation 47's lease and writer changes and continuation 44's settlement fixes (the other review).
