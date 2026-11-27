# TASK W493: prepare the fixes for product defects P01 to P13 on a branch that stays unmerged

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w493`, branch `codex/v1.8.0-w493` from the shared branch, whose `src/` is still C6 |
| Decision | Root decision R716 (QUEUE row W493), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [W487](records/W487.md) sections 2 to 7 (P01 to P13; R709 accepted the proposals), [W482](records/W482.md), [W490](records/W490.md), finding F-W487-01, and the ADRs the touched code cites |
| Outputs | Commits on `codex/v1.8.0-w493` only, one per defect, and the record `docs/work/v1.8.0-root-queue/records/W493.md` |
| Time | Deliver by **21:30**. Release the heavy-JVM lock by **22:30** at the latest, because tonight's client run takes the machine from 23:30 |

## Why

Phase 2 found four step failures and nine more product defects in C6 (W487, R709). `src/` stays frozen at C6 for
the campaign (R600), and the fixes wait for one candidate decision after phase 2's first full pass. Preparing them
now on an unmerged branch lets that decision rest on finished, tested changes instead of sketches. Nothing on this
branch reaches the shared branch until Root decides.

## What to do

1. **Fix in W487's ranking order** (its section 7), one commit per defect, with the P number in the message, for
   example `fix(machine): place the precision assembler hull on its structure (P01)`.
   - P01 covers the precision assembler. Apply the same placement rule to the other five formed hulls where W487
     section 2 and F-W487-01 show the same mismatch.
   - P02 to P13 as W487 proposes. Where W487 offers options, take the one it recommends and say so.
2. **Test each commit.**
   - Where the defect is logic, placement or text resolution, add a unit test or GameTest that fails on C6 and passes
     with the fix.
   - For a pure wording change, the language-file checks suffice. Keep English and Chinese in step.
3. **Run the full checks.** Check that C and D have more than 10 GB free, then hold the heavy-JVM lock as
   WORKER-RULES says and run:
   - `./gradlew clean build`;
   - `./gradlew runData`, then `git diff --exit-code`;
   - `./gradlew runGameTestServer`.

   Record each result.
4. **Record:**
   - each defect with its commit, files, test and result;
   - anything W487 proposed that you could not do, and why;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Tracked tests must be Linux-safe.
- Change only what each proposal names. Add no feature, and change no save schema, network protocol or ADR. If a fix
  seems to need one, stop that fix and report it.
- Do not change `scripts/campaign_client/` (W494 changes it today) or any campaign evidence.
- Do not start a Minecraft client.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit on the branch in the style of AGENTS.md section 6. Do not push or merge.
- The last line of your answer is the path of the record.
