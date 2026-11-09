# TASK W337: one parametrized driver for the C2 bay and cargo rerun

Root: Claude. Date: 2026-11-10. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w337`, branch `codex/v1.8.0-w337`, created by Root from the shared head |
| Decision | Root decision R408 (QUEUE row W337), for the C2 rerun under R386 |
| Builds on | W325's bay runners and [W325-MATRIX](records/W325-MATRIX.md), W331's cases ([W331](records/W331.md)), W326's and W330's cargo wrapper `scripts/run_v180_cargo_integrated.py`, and the C1 driver `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/bay/run-campaign.ps1` (read it; do not edit it) |
| Record | `docs/work/v1.8.0-root-queue/records/W337.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W337/` |

## Why

The owner asked for the bay and four cargo kinds to be tested together on one candidate JAR, and for every production
change to lead to a new candidate and a full rerun with no mixing of JARs (R386). C1's bay campaign ran from a
PowerShell driver outside the repository with C1's commit, JAR and family list written into it, and the cargo
campaign ran separately. C2 needs one driver, in the repository, that takes the candidate as arguments, covers W325's
bay families, W331's new cases and the cargo wrapper's families, and can show its complete plan before any JAR exists.
W332 and W336 will add their families later; Root adds those rows when they merge.

## What to do

1. **Plan first** (`records/W337.md` section 1, committed before the code): the family table, the order, which
   runner and arguments each family uses, and how each W325-MATRIX row, W331 case and cargo case maps to a family.
2. **`scripts/run_v180_integrated_campaign.py`**, a Python port of the C1 driver's behaviour:
   - Required arguments: `--candidate-commit`, `--jar`, `--jar-sha256`, `--template` (the source server), and
     `--evidence-root` (a new, empty directory). Optional: `--only` (family names), `--suffix`, `--dry-run`.
   - Before each family: verify the JAR's SHA-256, that `git diff --quiet <candidate> HEAD` shows no change under
     `src/main`, `src/generated`, `build.gradle`, `settings.gradle` and `gradle.properties`, a clean worktree, and over
     10 GB free on C: and D:. Then take the heavy-JVM lock as the C1 driver does (the backslash path with
     `cmd /c mkdir`, the owner token, no removal of another owner's lock, a three-hour wait limit), and keep at least
     90 seconds between a release and the next acquisition.
   - Pass the candidate to each runner in the form that runner now requires (W331 added `--jar-sha256` and
     `--candidate-commit` to the bay runners; the cargo wrapper has its own form). Write one `invocation.json` per
     family with the command, the source and candidate commits, the JAR hash, the production-diff result and the lock
     owner.
   - After a family fails, skip the remaining families that share its runner and record them as NOT_RUN, as C1 did.
     Write a campaign summary JSON after every family and a done marker at the end. Never remove a server copy that
     a runner did not report as its own.
   - `--dry-run` prints and writes the full plan with every command, without taking the lock or starting Java.
3. **Families:** every family of the C1 driver, W331's cases grouped as its record groups them, and the cargo
   wrapper's families (`--inventory` lists them). Name families so that W332 and W336 can add theirs without changing
   the existing ones.
4. **Tests** (`tests/test_run_v180_integrated_campaign.py`): the plan covers every W325-MATRIX row that W325 or W331
   reached natively and every cargo wrapper family; the preconditions refuse a hash mismatch, a production diff, a
   dirty worktree and low disk space; the lock helper never removes another owner's lock; a failed family stops its
   runner's later families; the dry run starts nothing. Use fakes for git, the lock, disk space and the runners.
5. **A dry run** against a placeholder JAR path and the current head, saved under the logs directory. No real
   campaign: no runner starts a server in this task.

## Verification

- The new tests and every existing bay and cargo runner test pass, unchanged.
- `python -B scripts/inventory_escrow_probe_cases.py --check` and
  `python -B scripts/validate_repository.py --require-approved-identity` pass.
- No Java change is expected; if one is needed, stop and say why in the record instead.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Building or choosing C2, running any native case, changing any runner's oracle, W332's and W336's families,
production code, the status file, QUEUE and the risk register.
