# TASK W504: stop the oxygen panel calling a thick atmosphere a vacuum, on the unmerged fix branch

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w504`, branch `codex/v1.8.0-w504`, created from `codex/v1.8.0-w493` at `e031778cd` (W493's fixes for P01 to P13 and W497's corrections) |
| Decision | Root decision R729 (QUEUE row W504), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [W490](records/W490.md) row E04 step 10; [W294](records/W294.md) item 1 and ADR-066 section 3.1, which name the vacuum and the non-breathable atmosphere separately; `datagen/AtmosphereLanguageProvider.java` line 32 and the HUD code that reads `hud.advancedrocketrycommunity.life_support.state.exposed`; their tests |
| Outputs | Commits on the branch: the fix with its test, and the record `docs/work/v1.8.0-root-queue/records/W504.md` |
| Time | Deliver by **21:00**. If you run Gradle, hold the heavy-JVM lock as WORKER-RULES says and release it by 21:00 |

## Why

On Venus (9.2 atm) the oxygen panel reads `ATM // VACUUM` while the action bar of the same frame names heat, high
pressure and intense sunlight (W490, E04 step 10). In English the HUD's `exposed` state is the text `VACUUM`, and in
Chinese it is the text for vacuum exposure, for every exposed case. W294 fixed the same confusion in
`/arce atmosphere status`; the HUD kept it. `src/` stays frozen at C6 for the campaign (R600), so the fix goes on the
unmerged fix branch for the candidate decision (R716).

## What to do

1. Find every place where the HUD or a screen shows the `exposed` state, and what the client knows there.
2. Fix the text so that a non-breathable atmosphere is not called a vacuum, in English and Chinese:
   - If the client already knows whether the ambient pressure is zero, show the vacuum and the non-breathable
     atmosphere separately, with ADR-066's names.
   - Otherwise use one neutral word that is true for both, such as `NO AIR`, and say why.
   - Do not change a network payload, save schema or service rule. If a correct fix needs one, stop and propose it.
3. Change the language provider, regenerate with `runData`, and keep the generated files equal to their providers.
4. Add a test that fails on C6 and passes with the fix. Do not weaken or delete any existing assertion. Update an
   existing text pin only where the fix changes that text, and say so.
5. Run under the lock: the targeted unit tests, `gradlew.bat --offline runData` with a clean diff, and
   `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.
6. Record the cause, the fix, the changed files, the test and the commands.

## Rules

- Work only in this worktree. Do not touch `codex/v1.8.0-w493` or its worktree.
- This TASK lives on the shared branch, not on yours. In your record, name it as the code span `docs/work/v1.8.0-root-queue/TASK-W504-VENUS-VACUUM-LABEL.md`, not as a link, and link only to files that exist on your branch.
- Do not start a Minecraft client. Release the heavy-JVM lock by 21:00, because tonight's client run takes the
  machine from 23:30.
- Run Python only as `python -B <script file>`. Never pipe a heredoc into `python -`, and leave no process running
  when you finish.
- Commit in the style of AGENTS.md section 6, the fix and the record separately. Do not push.
- The last line of your answer is the path of the record.
