# TASK W152K-review: independent read-only review of the refused-window follow-ups

Root: Claude. Date: 2026-10-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152k` at `96d9cf542`, on `4ce7ec2a0`: the design paragraph `d10cd3f0b`, the R5 fix `08425dcaa`, the R3 fix `1c049ff99`, the R6 cleanup `06ecb7503` and the record `96d9cf542`. Read `git diff 4ce7ec2a0..96d9cf542`. Review worktree `D:/GitHub/arce-v180-w152k-review`, a detached checkout of `96d9cf542` |
| Task it answers | [TASK-W152K](TASK-W152K-REFUSED-WINDOW-FOLLOW-UPS.md) (Root decision R119) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152J-rereview/REVIEW.md` (SHA-256 `8d9e848b…0c4f`, APPROVE-WITH-CHANGES with W152J-R3 to R6), with its probes under `probe-src/` |
| Design | ADR-065's N3-B2 proof-admission bullet (R109, R110, R112, R114 and R119) and R-035's N3-B2 part |
| Record | the W152K section of `docs/work/v1.8.0-root-queue/records/W152.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152K/` (with `SHA256SUMS.txt`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152K-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W152J-R3 is closed.** Only an eligible claim spends the window: a first capture, `removeUnproved`, or a
   native-window `BUSY` that keeps its queue place. Check that an ineligible hold does none of the following:
   - extend the window;
   - keep the turn open past the window's own expiry;
   - renew a token.

   Rerun the previous review's R3 sequences in both callback orders, including the crew-waiting `tickLive` arrival
   sorted ahead of the refused caller.
2. **W152J-R5 is closed.** The expiry is an absolute server tick: the last refusal plus `ATTEMPT_TICKS + 2`, cut at
   sealed completion to the earlier of that and completion plus 2. Check that it holds when ordinary service stops.
   - Production must bind the server tick before any sealed work exists. Check every construction path.
   - The fallback that counts ordinary service ticks must be unreachable in production.
3. **W152J-R6 and R4.** `git diff --check 4ce7ec2a0..96d9cf542` exits 0. The record states R4's coverage accurately:
   - covered: the pad-blocked producer in `tickLive`, recovery and reconnect nudges in the window;
   - not covered: a refused one-shot countdown cancel.
4. **The new renewal rule.** After an ineligible hold limits the turn, every pending logical is refused until the next
   sealed completion. Can that refuse a caller indefinitely when sealed work never completes? Can it starve the
   announcing caller?
5. **The new sealed delay.** The record measures the next sealed owner starting 4 ticks after its native opportunity in
   one order, against 2 at the base. It says this equals RR3's worst case. Check that no sequence exceeds R112's bound
   or reopens W152H-R1.
6. **Nothing weakened.** None of these changed:
   - quotas, the 100-tick proof deadline or the 200-tick retry;
   - the 64-pending bound or the inspection allowance;
   - saved formats or the protocol.

   Also check that the earlier unit classes are unchanged apart from R6's whitespace, and that the new tests fail on
   `4ce7ec2a0` where the record says so. The eight native cases must have run on `06ecb7503` or later, with no
   production source change after it.
7. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and
   with W164 (`codex/v1.8.0-w164`), which also changes `RocketOrdinaryRetirement` and `SealedDisassemblyCoordinator`.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- `SealedRefusedWindowTest`, `SealedRefusedWindowHandoffTest` and the previous review's probes;
- the W152 to W152J and R112 classes, then the `resourceescrow`, `rocket.server` and `rocket.sealed` packages.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; other workers
share it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`, `.gradle`
and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152K-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
