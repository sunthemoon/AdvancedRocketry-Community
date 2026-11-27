# TASK W498: let the harness launch the empty baseline clients for the frame-time captures

Root: Claude. Date: 2026-11-27. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w498`, branch `codex/v1.8.0-w498` from the shared branch |
| Decision | Root decision R720 (QUEUE row W498), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [W491](records/W491.md) and [W496](records/W496.md) with their packages `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w491/` and `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w496/` (read only), QUEUE row W496 (R719), the launch path under `scripts/campaign_client/`, its README and tests, and [W494](records/W494.md) for the last harness change |
| Outputs | Commits on the branch: the harness change, its tests and README; outside Git, the empty Forge profile in `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/phase2/``authoring-w498/` with an inventory; the record `docs/work/v1.8.0-root-queue/records/W498.md` |
| Time | Deliver by **21:00** local time |

## Why

W491 authored frame-time captures that compare the C6 client with an empty Vanilla 1.20.1 client and an empty Forge
client without this mod or JEI. W496 prepared the Vanilla profile. Two things are missing (R719):
- The harness's launch mode requires a cell that carries the candidate JAR, so it cannot start an empty baseline.
- The empty Forge profile must use Forge 47.4.10, the build the campaign client runs and W491's profile check
  expects, not 47.4.22, which TASK W496 named in error.

## What to do

1. **Baseline launch.**
   - Add a launch kind for a baseline profile that carries no candidate: Vanilla 1.20.1, or Forge 47.4.10 with an
     empty mods folder.
   - It must:
     - identify the profile by the hashes of its version files and its empty mods folder;
     - write the same READY receipt and process identity as a candidate launch;
     - start a fresh disposable world;
     - refuse anything that is neither the candidate cell nor one of these two baselines.
   - Keep every candidate launch exactly as it is.
2. **Empty Forge profile.**
   - Build the Forge 47.4.10 profile from the campaign's existing runtime on this machine, with an empty mods
     folder, into `authoring-w498/`.
   - Download nothing, copy no account or launcher login, and change no installed folder.
   - Record each file's SHA-256.
3. **Test it.**
   - Write pure tests of the new identity and refusal logic, runnable on Linux.
   - Keep all 160 existing harness tests passing unchanged.
   - Update the README.
4. **Record:**
   - the launch kind and what it checks;
   - the profile and its hashes;
   - the changed files and the commands;
   - what a first live launch must confirm;
   - the result of `python -B scripts/validate_repository.py`, whose `Summary:` line must show `0 failed`.

## Rules

- Do not change or delete an existing test, or loosen an existing check (AGENTS.md section 5).
- Do not start Minecraft, Gradle, PresentMon or any other JVM. A live launch happens later, at night.
- Never write inside rounds 01 to 07, `authoring-w491/`, `authoring-w496/` or the judgment packages, and do not
  touch the worktree `D:/GitHub/arce-v180-w489`, which runs tonight.
- Run Python only as `python -B <script file>` or `python -B -m unittest`. Never pipe a heredoc into `python -`, and
  leave no process running when you finish.
- Commit on the branch in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
