# TASK W499: review W498's baseline launch independently

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w499`, branch `codex/v1.8.0-w499` from the shared branch |
| Decision | Root decision R720 (QUEUE rows W498 and W499), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Under review | Commit `bfb6e4b7e` on `codex/v1.8.0-w498`, against its parent `48de443ec`; the worker's record is commit `76cdd91d8`. Read them with `git show` and `git diff` in this worktree, which shares the repository |
| Sources | [TASK W498](TASK-W498-BASELINE-LAUNCH.md), the harness under `scripts/campaign_client/` and its tests, and the profile in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/authoring-w498/` (read only) |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W499.md` only |
| Time | Deliver by **21:00** |

## Why

The harness launches the campaign's clients and sends them input. A Codex session added a second launch kind for
empty baseline clients, used by the frame-time captures. A mistake here could start the wrong profile, reuse a
non-disposable world or change how candidate sessions launch. Review it independently before Root merges it.

## What to check

1. **Candidate sessions are unchanged.** Every session without `launch_kind`, or with `launch_kind` set to
   `candidate`, must validate, launch and record exactly as before.
2. **Baselines are pinned.** A baseline launch refuses:
   - any profile whose version files, libraries or mods folder differ from the pinned hashes;
   - any mods folder that is not empty;
   - any attach mode;
   - any world that is not fresh and disposable.

   Name any input that gets through, with file and line.
3. **Receipts.** The READY receipt and the summary are written in the same form as a candidate launch's and contain
   only serialisable values. Check the new `process` and `game_dir` fields.
4. **Profile.** The empty Forge 47.4.10 profile in `authoring-w498/` was built from local files only, with no account
   or launcher login. Its recorded hashes match the files.
5. **Tests.**
   - Run `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` on the commit under review.
   - Check that the new tests are Linux-safe.
   - Check that no earlier test changed.
6. **README.** Check that the README matches the code.

## Outputs

The record lists:
- each finding, with file, line, a concrete input that goes wrong, and its severity;
- the commands and their results;
- a verdict: merge, merge after named fixes, or reject.

## Rules

- Do not change the code under review or any file outside your record.
- Do not start Minecraft, Gradle or any other JVM.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit the record on your branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
