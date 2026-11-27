# TASK W494: let the pointer gate decide a fullscreen window, so that the fullscreen display cells can run

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w494`, branch `codex/v1.8.0-w494` from the shared branch |
| Decision | Root decision R716 (QUEUE row W494), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | `scripts/campaign_client/gates.py`, its callers and tests, the gate section of `scripts/campaign_client/README.md`, [TASK W484](TASK-W484-HARNESS-COMMAND-ERROR-REPLIES.md) and [W484](records/W484.md) for the last harness change, and finding F-W488-25 in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/round-06/w488/FINDINGS.md` |
| Outputs | Commits on the branch: the harness change, its tests and the README; the record `docs/work/v1.8.0-root-queue/records/W494.md` |
| Time | Deliver by **20:00** |

## Why

The harness reads whether the game holds the pointer from the pointer clip that GLFW sets. A window that covers the
whole desktop clips to the desktop either way, so `gates.py` refuses that case as undecidable (F-W488-25). This
blocks the fullscreen cells of B06, C06, A13 and E05, which checklist A's display-cell rule requires (R707).

## What to do

1. **Find a second signal.** It must be a Windows signal that tells a held pointer from a shown one for a
   desktop-covering window, and the harness must be able to read it without sending any input. One candidate is
   whether the cursor is shown (`GetCursorInfo`) while the game window is in front. Cite GLFW's source or
   documentation for what its disabled-cursor mode sets.
2. **Use it only where the clip cannot decide.** Keep every other case exactly as it is. If the second signal is
   missing or contradicts the clip, refuse as now: fail closed.
3. **Test it.**
   - Write pure tests of the decision for every combination of inputs, runnable on Linux.
   - Keep all 134 existing harness tests passing unchanged.
   - Update the README paragraph.
4. **Record:**
   - the signal and its source;
   - the decision table;
   - the changed files and the commands;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not change or delete an existing test, or loosen an existing check (AGENTS.md section 5).
- Do not start Minecraft, Gradle or any other JVM. Do not touch round 07 or any other evidence.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
