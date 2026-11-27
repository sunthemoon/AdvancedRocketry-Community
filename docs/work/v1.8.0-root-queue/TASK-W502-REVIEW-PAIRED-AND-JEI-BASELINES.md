# TASK W502: review W501's paired and JEI-only baselines independently

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w502`, branch `codex/v1.8.0-w502` from the shared branch |
| Decision | Root decision R725 (QUEUE rows W501 and W502), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Under review | Commit `124d6699a` on `codex/v1.8.0-w501`, against its parent `51284e7da`; the worker's record is commit `d88fa8bd6`. Read them with `git show` and `git diff` in this worktree, which shares the repository |
| Sources | [TASK W501](TASK-W501-PAIRED-AND-JEI-BASELINES.md), [W498](records/W498.md), [W499](records/W499.md), the harness under `scripts/campaign_client/` and its tests, and the profile and examples in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/authoring-w501/` (read only) |
| Outputs | The record `docs/work/v1.8.0-root-queue/records/W502.md` only |
| Time | Deliver by **21:30** |

## Why

W501 lets a baseline session name the candidate world it is paired with, so that the fresh vanilla-only world takes
that world's seed, generator settings, spawn, time, game rules and viewpoint, and it adds a Forge 47.4.10 baseline
with only the campaign's JEI JAR. A mistake could import modded or player data into a baseline world, start the wrong
profile, change how candidate or unpaired baseline sessions launch, or make a frame-time comparison look paired when
it is not. Review it independently before Root merges it.

## What to check

1. **Nothing else changes.** Candidate sessions, and baseline sessions without `paired_world`, validate, launch and
   record exactly as before W501.
2. **The pairing copies only what it says.** Read `baseline_pairing.py` against a candidate `level.dat`:
   - only the listed state, game rules, seed and vanilla overworld generator reach the new world;
   - no chunk, modded dimension, data pack, inventory, effect, UUID or capability does;
   - the candidate world is pinned by the hash of the very bytes decoded, and is never written.

   Name any input that gets through or is refused wrongly, with file and line.
3. **The JEI-only profile is pinned.** It refuses any mods folder other than exactly the pinned JEI JAR, and any
   other file in the game directories. The profile in `authoring-w501/` was built from local files only, with no
   account or launcher login, and its recorded hashes match the files.
4. **Receipts.** The READY receipt and summary stay serialisable, including the new `paired_world` identity.
5. **Tests.**
   - Run `python -B -m unittest discover -s tests -p 'test_campaign_client*.py' -q` on the commit under review.
   - Check that the new tests are Linux-safe.
   - Check that no earlier test changed.
6. **README and record.** The README matches the code. The record's table of W500's stopped sessions says
   truthfully what is unblocked.

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
