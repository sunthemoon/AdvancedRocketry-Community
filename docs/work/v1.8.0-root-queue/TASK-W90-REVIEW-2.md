# TASK W90-review-2: fresh independent read-only review of the readiness design, revision 2

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. You did not review revision 1.

| Item | Value |
|---|---|
| Subject | [records/W90-DESIGN.md](records/W90-DESIGN.md), revision 2, and [records/W90.md](records/W90.md), merged into the shared branch at `f754dfca` (the author's commits are `474ee3c5` and `e055cf2d`) |
| Task it answers | [TASK-W90R-READINESS-REVISION-2](TASK-W90R-READINESS-REVISION-2.md) |
| Inputs | The first review `D:/GitHub/ARCE-Task-Evidence/root-queue/W90-review/REVIEW.md` (SHA-256 `498009e8bfcaa586a0177373a059c9546e7e9583b2779a437a0a67b3fb00328d`) and Root decision R21 `D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R21.md` (SHA-256 `1322ab425a6aa87397f42abf49aac11f444beba6c89c38f5e61ddd49db1b91ee`) |
| W11b2 code | Read it only through Git at `c420e521` (the integrated W11b2 head, which contains continuation 53's pending pad reservations): `git show c420e521:<path>`, `git grep <pattern> c420e521`. Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` or `D:/GitHub/arce-v180-w11b2-c54`, where continuation 54 is working |
| Checkout | `D:/GitHub/arce-v180-w90-review-2`, detached at this task's commit, made for you; do not commit in it |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W90-review-2/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Every first-review finding (W90-R1 to W90-R5) and every R21 item** is answered correctly in revision 2 (its
   section 8 maps them).
2. **The checked column set and the `DISCOVERY_MARGIN` hold** (section 3): one definition for readiness, admission,
   refresh and release; the origin column requirement; what happens to a recorded invalid end, its log and recovery.
3. **The warm-up (section 7)** keeps the full F1 and F2 census and every resource proof, and is bounded: when it starts
   and after which validation, the cap of 8, one per rocket, the 600-tick non-renewable lifetime (check the claim that
   re-adding a vanilla ticket resets its age, and what non-renewal therefore needs), repeated requests, the pad held
   alongside continuation 53's pending reservations (read `EscrowFlightCheckpoint` and `RocketTransferService` at
   `c420e521`), the refusal-and-relaunch request flow and the replay rules, every release path (including stop,
   disconnect, rocket removal and the handover after T's first ACTIVE refresh), and fresh revalidation before F1.
   Can a warm-up leak tickets, hold a pad forever, let two launches take one pad, or let a launch skip the census?
4. **Q2 for governed launches only** (section 6): every pad path and F1's source, the new `LAUNCH_CHUNK_BUDGET` code and
   its English and Chinese text, and that legacy launches keep today's behaviour.
5. **No saved-format change** (section 9), and **nothing that must not change** (section 4): the acknowledgement order,
   the proofs, the leases, the holds, the replay exclusion, and that ordinary reconciliation, pruning and held repair
   add no tickets.
6. **Tests and estimates (section 10), the proposed R-035 and ADR-065 text (section 11), and each open question in
   section 12**: say whether you agree with the recommended answer.

Take no heavy-JVM lock and run no Gradle build, GameTest or native server: continuation 54's benchmark needs a quiet
machine. You may run `javap` only while `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` does not exist; otherwise read
the sources jar with an unzip listing and say what you could not verify.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or REDESIGN.
- Findings, each with an ID (W90-S1, W90-S2, ...), a severity, the section and line, the problem, and a suggested change.
- Your answer to each open question in section 12.
- The commands you ran.
- The SHA-256 of REVIEW.md on the last line of your answer.
